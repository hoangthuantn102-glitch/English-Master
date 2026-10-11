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
import java.util.UUID

data class BluetoothDeviceItem(
    val name: String,
    val address: String,
    val isBonded: Boolean = false
)

enum class ConnectionStatus {
    IDLE,
    WAITING_FOR_PLAYER, // Host waiting
    CONNECTING,         // Client connecting
    CONNECTED,          // Real Bluetooth connected
    BOT_CONNECTED       // AI Bot practice mode
}

class BluetoothDuelManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothDuel"
        private const val APP_NAME = "EnglishDuel1v1"
        // Standard dedicated UUID for English Duel RFCOMM communication
        val DUEL_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.IDLE)
    val connectionStatus = _connectionStatus.asStateFlow()

    private val _opponentName = MutableStateFlow("Đối thủ")
    val opponentName = _opponentName.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceItem>>(emptyList())
    val discoveredDevices = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    // Incoming packet callbacks
    var onPacketReceived: ((type: String, data: JSONObject) -> Unit)? = null
    var onOpponentDisconnected: (() -> Unit)? = null

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    private var writer: PrintWriter? = null
    private var readerJob: Job? = null
    private var acceptJob: Job? = null
    private var connectJob: Job? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Broadcast receiver for discovery
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

    // HOST MODE: Listen for incoming connections from opponent
    @SuppressLint("MissingPermission")
    fun startHosting(localPlayerName: String) {
        disconnect()
        _connectionStatus.value = ConnectionStatus.WAITING_FOR_PLAYER
        _opponentName.value = "Đang chờ đối thủ..."

        acceptJob = coroutineScope.launch {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, DUEL_UUID)
                val socket = serverSocket?.accept() // Blocking call until client connects
                serverSocket?.close() // Close listening socket once accepted
                serverSocket = null

                if (socket != null) {
                    activeSocket = socket
                    val remoteDevice = socket.remoteDevice
                    val remoteName = try { remoteDevice.name } catch (e: Exception) { null } ?: "Đối thủ"
                    _opponentName.value = remoteName
                    _connectionStatus.value = ConnectionStatus.CONNECTED

                    startIoStreams()

                    // Send handshake with local host name
                    val handshake = JSONObject().apply {
                        put("playerName", localPlayerName)
                        put("isHost", true)
                    }
                    sendPacket("HANDSHAKE", handshake)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hosting failed or cancelled", e)
                if (_connectionStatus.value == ConnectionStatus.WAITING_FOR_PLAYER) {
                    _connectionStatus.value = ConnectionStatus.IDLE
                }
            }
        }
    }

    // CLIENT MODE: Connect to chosen Host device
    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String, localPlayerName: String) {
        disconnect()
        _connectionStatus.value = ConnectionStatus.CONNECTING
        _opponentName.value = "Đang kết nối..."

        connectJob = coroutineScope.launch {
            try {
                stopDiscovery()
                val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                val socket = device?.createRfcommSocketToServiceRecord(DUEL_UUID)
                bluetoothAdapter?.cancelDiscovery()

                socket?.connect() // Blocking connection attempt

                if (socket != null && socket.isConnected) {
                    activeSocket = socket
                    val remoteName = try { device.name } catch (e: Exception) { null } ?: "Chủ phòng"
                    _opponentName.value = remoteName
                    _connectionStatus.value = ConnectionStatus.CONNECTED

                    startIoStreams()

                    // Send handshake with local client name
                    val handshake = JSONObject().apply {
                        put("playerName", localPlayerName)
                        put("isHost", false)
                    }
                    sendPacket("HANDSHAKE", handshake)
                } else {
                    _connectionStatus.value = ConnectionStatus.IDLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _connectionStatus.value = ConnectionStatus.IDLE
            }
        }
    }

    // BOT MODE: Practice alone with simulated AI opponent
    fun startBotMode(botName: String = "AI CyberDuel ⚡") {
        disconnect()
        _opponentName.value = botName
        _connectionStatus.value = ConnectionStatus.BOT_CONNECTED
    }

    private fun startIoStreams() {
        val socket = activeSocket ?: return
        try {
            val outputStream = socket.outputStream
            writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

            val inputStream = socket.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))

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
            // In bot mode, no physical network write needed
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

        _connectionStatus.value = ConnectionStatus.IDLE
    }
}
