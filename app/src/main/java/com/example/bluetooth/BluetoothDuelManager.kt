package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.*
import java.util.Collections
import java.util.UUID

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isBonded: Boolean = false
)

data class WifiRoomItem(
    val hostName: String,
    val ip: String,
    val port: Int = 8888,
    val roomCode: String = ""
)

enum class ConnectionMedium {
    BLUETOOTH,
    WIFI_LAN
}

enum class ConnectionStatus {
    IDLE,
    WAITING_FOR_PLAYER, // Host waiting
    CONNECTING,         // Client connecting
    CONNECTED,          // Connected over Bluetooth or Wi-Fi
    BOT_CONNECTED       // AI Bot practice mode
}

class BluetoothDuelManager(private val context: Context) {

    companion object {
        private const val TAG = "DuelManager"
        private const val APP_NAME = "EnglishDuel1v1"
        // Standard dedicated UUID for English Duel RFCOMM communication
        val DUEL_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
        const val TCP_PORT = 8888
        const val UDP_DISCOVERY_PORT = 8889
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    // Medium state: Bluetooth or Wi-Fi
    private val _connectionMedium = MutableStateFlow(ConnectionMedium.BLUETOOTH)
    val connectionMedium = _connectionMedium.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _opponentName = MutableStateFlow("Đối thủ")
    val opponentName = _opponentName.asStateFlow()

    // Bluetooth Devices & Scan State
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Wi-Fi Rooms & Scan State
    private val _discoveredWifiRooms = MutableStateFlow<List<WifiRoomItem>>(emptyList())
    val discoveredWifiRooms = _discoveredWifiRooms.asStateFlow()

    private val _isScanningWifi = MutableStateFlow(false)
    val isScanningWifi = _isScanningWifi.asStateFlow()

    private val _localWifiIp = MutableStateFlow("127.0.0.1")
    val localWifiIp = _localWifiIp.asStateFlow()

    private val _wifiRoomCode = MutableStateFlow("")
    val wifiRoomCode = _wifiRoomCode.asStateFlow()

    // Incoming packet callbacks
    var onPacketReceived: ((type: String, data: JSONObject) -> Unit)? = null
    var onOpponentDisconnected: (() -> Unit)? = null

    // Bluetooth sockets
    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null

    // Wi-Fi TCP sockets
    private var tcpServerSocket: ServerSocket? = null
    private var tcpActiveSocket: Socket? = null

    // Common IO streams
    private var writer: PrintWriter? = null
    private var readerJob: Job? = null
    private var acceptJob: Job? = null
    private var connectJob: Job? = null
    private var udpBroadcastJob: Job? = null
    private var udpListenJob: Job? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Broadcast receiver for Bluetooth discovery
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        val name = try { it.name } catch (e: Exception) { null } ?: "Thiết bị Bluetooth"
                        val address = it.address
                        val current = _discoveredDevices.value.toMutableList()
                        if (current.none { d -> d.address == address }) {
                            current.add(BluetoothDeviceItem(name, address, false))
                            _discoveredDevices.value = current
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                }
            }
        }
    }

    private var isReceiverRegistered = false

    fun setConnectionMedium(medium: ConnectionMedium) {
        _connectionMedium.value = medium
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceItem> {
        return try {
            bluetoothAdapter?.bondedDevices?.map {
                BluetoothDeviceItem(it.name ?: "Thiết bị đã ghép đôi", it.address, true)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching paired devices", e)
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (!isBluetoothEnabled()) return
        registerReceiver()
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        try {
            bluetoothAdapter?.cancelDiscovery()
            bluetoothAdapter?.startDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        try {
            bluetoothAdapter?.cancelDiscovery()
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling discovery", e)
        }
        _isScanning.value = false
    }

    private fun registerReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            context.registerReceiver(discoveryReceiver, filter)
            isReceiverRegistered = true
        }
    }

    private fun unregisterReceiver() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(discoveryReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
            isReceiverRegistered = false
        }
    }

    // ==========================================
    // 1. BLUETOOTH MODE (HOST & CLIENT)
    // ==========================================

    @SuppressLint("MissingPermission")
    fun startHosting(localPlayerName: String) {
        disconnect()
        _connectionMedium.value = ConnectionMedium.BLUETOOTH
        _connectionStatus.value = ConnectionStatus.WAITING_FOR_PLAYER
        _opponentName.value = "Đang chờ đối thủ..."

        acceptJob = coroutineScope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, DUEL_UUID)
                val socket = serverSocket?.accept()
                serverSocket?.close()
                serverSocket = null

                if (socket != null) {
                    activeSocket = socket
                    val remoteDevice = socket.remoteDevice
                    val remoteName = try { remoteDevice.name } catch (e: Exception) { null } ?: "Đối thủ Bluetooth"
                    _opponentName.value = remoteName
                    _connectionStatus.value = ConnectionStatus.CONNECTED

                    startIoStreams(socket.inputStream, socket.outputStream, localPlayerName, isHost = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth Hosting failed or cancelled", e)
                if (_connectionStatus.value == ConnectionStatus.WAITING_FOR_PLAYER) {
                    _connectionStatus.value = ConnectionStatus.IDLE
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, localPlayerName: String) {
        disconnect()
        _connectionMedium.value = ConnectionMedium.BLUETOOTH
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _opponentName.value = "Đang kết nối..."

        connectJob = coroutineScope.launch {
            try {
                stopDiscovery()
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                val socket = device?.createRfcommSocketToServiceRecord(DUEL_UUID)
                bluetoothAdapter?.cancelDiscovery()

                socket?.connect()

                if (socket != null && socket.isConnected) {
                    activeSocket = socket
                    val remoteName = try { device.name } catch (e: Exception) { null } ?: "Chủ phòng Bluetooth"
                    _opponentName.value = remoteName
                    _connectionStatus.value = ConnectionStatus.CONNECTED

                    startIoStreams(socket.inputStream, socket.outputStream, localPlayerName, isHost = false)
                } else {
                    _connectionStatus.value = ConnectionStatus.IDLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth connection failed", e)
                _connectionStatus.value = ConnectionStatus.IDLE
            }
        }
    }

    // ==========================================
    // 2. WI-FI / LAN MODE (HOST & CLIENT)
    // ==========================================

    fun getLocalIpAddress(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting IP", e)
        }
        return "127.0.0.1"
    }

    private fun generateRoomCode(ip: String): String {
        val parts = ip.split(".")
        return if (parts.size == 4) {
            val p3 = parts[2].padStart(2, '0')
            val p4 = parts[3].padStart(2, '0')
            "${p3.takeLast(2)}${p4.takeLast(2)}"
        } else {
            (1000..9999).random().toString()
        }
    }

    fun startWifiHosting(localPlayerName: String) {
        disconnect()
        _connectionMedium.value = ConnectionMedium.WIFI_LAN
        _connectionStatus.value = ConnectionStatus.WAITING_FOR_PLAYER
        _opponentName.value = "Đang chờ đối thủ..."
        
        val ip = getLocalIpAddress()
        _localWifiIp.value = ip
        val code = generateRoomCode(ip)
        _wifiRoomCode.value = code

        acceptJob = coroutineScope.launch {
            try {
                tcpServerSocket = ServerSocket(TCP_PORT)
                startUdpBroadcast(localPlayerName, code, TCP_PORT)

                val socket = tcpServerSocket?.accept() // Blocking call until client connects
                tcpServerSocket?.close()
                tcpServerSocket = null
                stopUdpBroadcast()

                if (socket != null && socket.isConnected) {
                    tcpActiveSocket = socket
                    val remoteIp = socket.inetAddress?.hostAddress ?: "Đối thủ Wi-Fi"
                    _opponentName.value = remoteIp
                    _connectionStatus.value = ConnectionStatus.CONNECTED

                    startIoStreams(socket.getInputStream(), socket.getOutputStream(), localPlayerName, isHost = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi hosting failed or cancelled", e)
                if (_connectionStatus.value == ConnectionStatus.WAITING_FOR_PLAYER) {
                    _connectionStatus.value = ConnectionStatus.IDLE
                }
            }
        }
    }

    fun connectToWifiRoom(ip: String, port: Int = TCP_PORT, localPlayerName: String) {
        disconnect()
        _connectionMedium.value = ConnectionMedium.WIFI_LAN
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _opponentName.value = "Đang kết nối tới $ip..."

        connectJob = coroutineScope.launch {
            try {
                stopWifiDiscovery()
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 6000)

                if (socket.isConnected) {
                    tcpActiveSocket = socket
                    _opponentName.value = "Chủ phòng ($ip)"
                    _connectionStatus.value = ConnectionStatus.CONNECTED

                    startIoStreams(socket.getInputStream(), socket.getOutputStream(), localPlayerName, isHost = false)
                } else {
                    _connectionStatus.value = ConnectionStatus.IDLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Wi-Fi connection failed", e)
                _connectionStatus.value = ConnectionStatus.IDLE
            }
        }
    }

    fun connectByRoomCodeOrIp(target: String, localPlayerName: String) {
        val clean = target.trim()
        if (clean.contains(".")) {
            // Entered IP address (e.g. 192.168.1.15 or 192.168.43.1:8888)
            val parts = clean.split(":")
            val ip = parts[0]
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: TCP_PORT else TCP_PORT
            connectToWifiRoom(ip, port, localPlayerName)
        } else {
            // Entered room code (e.g. 0145) - Match with discovered rooms
            val room = _discoveredWifiRooms.value.firstOrNull { it.roomCode.equals(clean, ignoreCase = true) }
            if (room != null) {
                connectToWifiRoom(room.ip, room.port, localPlayerName)
            } else {
                // If not found in broadcast yet, attempt matching by subnet
                val myIp = getLocalIpAddress()
                val sub = myIp.substringBeforeLast(".")
                if (clean.length in 1..3) {
                    val candidateIp = "$sub.$clean"
                    connectToWifiRoom(candidateIp, TCP_PORT, localPlayerName)
                } else if (clean.length == 4) {
                    val p4 = clean.takeLast(2).toIntOrNull() ?: 1
                    val candidateIp = "$sub.$p4"
                    connectToWifiRoom(candidateIp, TCP_PORT, localPlayerName)
                } else {
                    _connectionStatus.value = ConnectionStatus.IDLE
                }
            }
        }
    }

    fun startWifiDiscovery() {
        _isScanningWifi.value = true
        _discoveredWifiRooms.value = emptyList()

        udpListenJob?.cancel()
        udpListenJob = coroutineScope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(UDP_DISCOVERY_PORT)
                socket.broadcast = true
                socket.soTimeout = 3000
                val buffer = ByteArray(1024)

                while (isActive && _isScanningWifi.value) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val jsonStr = String(packet.data, 0, packet.length, Charsets.UTF_8)
                        val json = JSONObject(jsonStr)
                        val hostName = json.optString("name", "Chủ phòng Wi-Fi")
                        val ip = json.optString("ip")
                        val port = json.optInt("port", TCP_PORT)
                        val code = json.optString("code", "")

                        val myIp = getLocalIpAddress()
                        if (ip.isNotBlank() && ip != myIp) {
                            val currentList = _discoveredWifiRooms.value.toMutableList()
                            if (currentList.none { it.ip == ip && it.port == port }) {
                                currentList.add(WifiRoomItem(hostName, ip, port, code))
                                _discoveredWifiRooms.value = currentList
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        // normal timeout, keep listening
                    } catch (e: Exception) {
                        if (isActive) delay(400)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP listen error", e)
            } finally {
                socket?.close()
                _isScanningWifi.value = false
            }
        }
    }

    fun stopWifiDiscovery() {
        _isScanningWifi.value = false
        udpListenJob?.cancel()
    }

    private fun startUdpBroadcast(roomName: String, roomCode: String, port: Int) {
        udpBroadcastJob?.cancel()
        udpBroadcastJob = coroutineScope.launch {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val localIp = getLocalIpAddress()
                val payload = JSONObject().apply {
                    put("name", roomName)
                    put("ip", localIp)
                    put("port", port)
                    put("code", roomCode)
                }.toString().toByteArray(Charsets.UTF_8)

                val broadcastAddr = InetAddress.getByName("255.255.255.255")
                val packet = DatagramPacket(payload, payload.size, broadcastAddr, UDP_DISCOVERY_PORT)

                while (isActive && _connectionStatus.value == ConnectionStatus.WAITING_FOR_PLAYER) {
                    try {
                        socket.send(packet)
                    } catch (e: Exception) {
                        // ignore occasional send glitch
                    }
                    delay(1200)
                }
            } catch (e: Exception) {
                Log.e(TAG, "UDP broadcast error", e)
            } finally {
                socket?.close()
            }
        }
    }

    private fun stopUdpBroadcast() {
        udpBroadcastJob?.cancel()
    }

    // ==========================================
    // 3. BOT PRACTICE MODE
    // ==========================================

    fun startBotMode(botName: String = "AI CyberDuel ⚡") {
        disconnect()
        _opponentName.value = botName
        _connectionStatus.value = ConnectionStatus.BOT_CONNECTED
    }

    // ==========================================
    // 4. COMMON STREAM IO & PACKET PROTOCOL
    // ==========================================

    private fun startIoStreams(
        inputStream: java.io.InputStream,
        outputStream: java.io.OutputStream,
        localPlayerName: String,
        isHost: Boolean
    ) {
        try {
            writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

            // Send initial handshake with player's custom name
            val handshake = JSONObject().apply {
                put("playerName", localPlayerName)
                put("isHost", isHost)
            }
            sendPacket("HANDSHAKE", handshake)

            readerJob = coroutineScope.launch {
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) {
                            parseIncomingMessage(line)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Socket read error / disconnected", e)
                } finally {
                    handleDisconnect()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening socket streams", e)
            handleDisconnect()
        }
    }

    private suspend fun parseIncomingMessage(line: String) {
        try {
            val json = JSONObject(line)
            val type = json.optString("type")
            val data = json.optJSONObject("data") ?: JSONObject()

            if (type == "HANDSHAKE") {
                val opponentName = data.optString("playerName")
                if (opponentName.isNotBlank()) {
                    _opponentName.value = opponentName
                }
            }

            withContext(Dispatchers.Main) {
                onPacketReceived?.invoke(type, data)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing packet: $line", e)
        }
    }

    fun sendPacket(type: String, data: JSONObject) {
        if (_connectionStatus.value == ConnectionStatus.BOT_CONNECTED) {
            return
        }

        coroutineScope.launch {
            try {
                val packet = JSONObject().apply {
                    put("type", type)
                    put("data", data)
                }
                writer?.println(packet.toString())
                writer?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending packet: $type", e)
            }
        }
    }

    private fun handleDisconnect() {
        coroutineScope.launch(Dispatchers.Main) {
            if (_connectionStatus.value == ConnectionStatus.CONNECTED) {
                _connectionStatus.value = ConnectionStatus.IDLE
                onOpponentDisconnected?.invoke()
            }
        }
    }

    fun disconnect() {
        stopDiscovery()
        stopWifiDiscovery()
        stopUdpBroadcast()
        unregisterReceiver()

        acceptJob?.cancel()
        connectJob?.cancel()
        readerJob?.cancel()

        try {
            writer?.close()
        } catch (e: Exception) {
            // ignore
        }
        writer = null

        try {
            activeSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        activeSocket = null

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        serverSocket = null

        try {
            tcpActiveSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        tcpActiveSocket = null

        try {
            tcpServerSocket?.close()
        } catch (e: Exception) {
            // ignore
        }
        tcpServerSocket = null

        _connectionStatus.value = ConnectionStatus.IDLE
    }
}
