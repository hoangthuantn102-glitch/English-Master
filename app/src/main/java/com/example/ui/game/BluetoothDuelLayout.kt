package com.example.ui.game

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetooth.BluetoothDeviceItem
import com.example.bluetooth.ConnectionMedium
import com.example.bluetooth.ConnectionStatus
import com.example.bluetooth.WifiRoomItem
import com.example.ui.viewmodel.AiDifficulty
import com.example.ui.viewmodel.BluetoothDuelViewModel
import com.example.ui.viewmodel.DuelGameMode
import com.example.ui.viewmodel.DuelQuestion
import com.example.ui.viewmodel.DuelScreenState
import com.example.ui.viewmodel.QuestionDistributionMode
import com.example.ui.viewmodel.RoundRecapData

@Composable
fun BluetoothDuelLayout(
    viewModel: BluetoothDuelViewModel,
    onBackToMenu: () -> Unit
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    // Bluetooth permission request launcher
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    var permissionsGranted by remember { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionsGranted = results.values.all { it }
    }

    var showAiConfigDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (screenState) {
            DuelScreenState.LOBBY -> {
                DuelLobbyScreen(
                    viewModel = viewModel,
                    onBackToMenu = onBackToMenu,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions) },
                    onOpenAiConfig = { showAiConfigDialog = true },
                    onOpenGuide = { showGuideDialog = true }
                )
            }
            DuelScreenState.WAITING_OPPONENT -> {
                DuelWaitingScreen(
                    viewModel = viewModel,
                    onCancel = { viewModel.cancelWaiting() }
                )
            }
            DuelScreenState.COUNTDOWN -> {
                DuelCountdownScreen(viewModel = viewModel)
            }
            DuelScreenState.BATTLE -> {
                DuelBattleScreen(viewModel = viewModel)
            }
            DuelScreenState.ROUND_RECAP -> {
                DuelRoundRecapScreen(viewModel = viewModel)
            }
            DuelScreenState.MATCH_OVER -> {
                DuelGameOverScreen(
                    viewModel = viewModel,
                    onBackToMenu = {
                        viewModel.exitDuel()
                        onBackToMenu()
                    }
                )
            }
        }

        // AI Duel Difficulty & Mode Selection Dialog
        if (showAiConfigDialog) {
            AiDuelConfigDialog(
                viewModel = viewModel,
                onDismiss = { showAiConfigDialog = false },
                onStartBattle = { diff, mode ->
                    showAiConfigDialog = false
                    viewModel.startPracticeBot(difficulty = diff, mode = mode)
                }
            )
        }

        // Connection Guide Dialog (Bluetooth + Wi-Fi + Hotspot)
        if (showGuideDialog) {
            DuelConnectionGuideDialog(
                onDismiss = { showGuideDialog = false }
            )
        }
    }
}

// --- 1. LOBBY SCREEN ---
@Composable
private fun DuelLobbyScreen(
    viewModel: BluetoothDuelViewModel,
    onBackToMenu: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAiConfig: () -> Unit,
    onOpenGuide: () -> Unit
) {
    val context = LocalContext.current
    var pairedDevices by remember { mutableStateOf<List<BluetoothDeviceItem>>(emptyList()) }
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val selectedGameMode by viewModel.selectedGameMode.collectAsStateWithLifecycle()

    val connectionMedium by viewModel.connectionMedium.collectAsStateWithLifecycle()
    val discoveredWifiRooms by viewModel.discoveredWifiRooms.collectAsStateWithLifecycle()
    val isScanningWifi by viewModel.isScanningWifi.collectAsStateWithLifecycle()
    val localWifiIp by viewModel.localWifiIp.collectAsStateWithLifecycle()

    var showEnterIpDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRequestPermissions()
        pairedDevices = viewModel.getPairedDevices()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onBackToMenu) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Đấu trường 1vs1",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Wi-Fi LAN • Internet • Bluetooth • Đấu AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Guide Button
            FilledTonalButton(
                onClick = onOpenGuide,
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("📖 Hướng Dẫn", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Connection Medium Selector Tab: Bluetooth vs Wi-Fi LAN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            // Bluetooth Tab
            val isBtActive = connectionMedium == ConnectionMedium.BLUETOOTH
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable { viewModel.setConnectionMedium(ConnectionMedium.BLUETOOTH) },
                color = if (isBtActive) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(11.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Bluetooth 1v1",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isBtActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Wi-Fi / Internet Tab
            val isWifiActive = connectionMedium == ConnectionMedium.WIFI_LAN
            Surface(
                modifier = Modifier
                    .weight(1.2f)
                    .clip(RoundedCornerShape(11.dp))
                    .clickable {
                        viewModel.setConnectionMedium(ConnectionMedium.WIFI_LAN)
                        viewModel.startWifiScan()
                    },
                color = if (isWifiActive) Color(0xFF00897B) else Color.Transparent,
                shape = RoundedCornerShape(11.dp)
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📶", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Wi-Fi & Internet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = if (isWifiActive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Bluetooth status alert if Bluetooth tab is selected and Bluetooth is off
        if (connectionMedium == ConnectionMedium.BLUETOOTH && !viewModel.isBluetoothEnabled()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bluetooth đang TẮT!",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Bật Bluetooth hoặc chuyển sang tab 'Wi-Fi & Hotspot' để chơi.",
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                    Button(
                        onClick = {
                            try {
                                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Bật ngay", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Wi-Fi info banner if Wi-Fi tab is selected
        if (connectionMedium == ConnectionMedium.WIFI_LAN) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF00897B).copy(alpha = 0.12f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📶", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mạng Wi-Fi nội bộ, Internet hoặc Hotspot",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF00695C),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "IP của máy: ${viewModel.getLocalIpAddress()} (Tự động quét phòng hoặc Nhập IP/Mã)",
                            color = Color(0xFF00695C).copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Action Buttons Grid depending on Medium
        if (connectionMedium == ConnectionMedium.BLUETOOTH) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.hostRoom() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text("👑", fontSize = 20.sp)
                        Text("Tạo Phòng", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = {
                        if (isScanning) viewModel.stopScan() else viewModel.startScan()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(if (isScanning) "📡" else "🔍", fontSize = 20.sp)
                        Text(if (isScanning) "Dừng Quét" else "Quét Máy", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                Button(
                    onClick = onOpenAiConfig,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9C27B0)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text("🤖", fontSize = 20.sp)
                        Text("Đấu Với AI", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            // Wi-Fi Action Buttons (Host Wi-Fi, Scan Wi-Fi, Enter IP / Code, Solo AI)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.startWifiHosting() },
                    modifier = Modifier.weight(1.1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00897B)
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text("👑", fontSize = 20.sp)
                        Text("Tạo Phòng Wi-Fi", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                }

                Button(
                    onClick = {
                        if (isScanningWifi) viewModel.stopWifiScan() else viewModel.startWifiScan()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(if (isScanningWifi) "📡" else "🔍", fontSize = 20.sp)
                        Text(if (isScanningWifi) "Dừng Quét" else "Quét Phòng", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = { showEnterIpDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text("⌨️", fontSize = 20.sp)
                        Text("Nhập IP / Mã", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION: SELECT DUEL GAME MODE ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎮 Chọn Trò Chơi Thi Đấu:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = selectedGameMode.badge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal game mode picker chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DuelGameMode.values().forEach { mode ->
                val isSelected = mode == selectedGameMode
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectGameMode(mode) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mode.icon, fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = mode.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = mode.badge,
                                fontSize = 10.sp,
                                color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- LIST SECTION: Bluetooth Devices OR Wi-Fi Rooms ---
        if (connectionMedium == ConnectionMedium.BLUETOOTH) {
            Text(
                text = "Chọn máy đối thủ Bluetooth:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Mẹo: Ghép đôi Bluetooth trước trong Cài đặt của máy để kết nối ngay tức thì!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val allDevices = (pairedDevices + discoveredDevices).distinctBy { it.address }

                if (allDevices.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📱", fontSize = 34.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Chưa phát hiện thiết bị Bluetooth",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Bấm 'Tạo Phòng' để mở sảnh, hoặc chuyển sang tab 'Wi-Fi & Hotspot' để kết nối chung mạng siêu dễ!",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = onOpenGuide,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("📖 Xem Hướng Dẫn Kết Nối 2 Máy", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(allDevices) { device ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.joinRoom(device.address) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (device.isBonded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(if (device.isBonded) "🔗" else "📱", fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = device.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (device.isBonded) "Đã ghép đôi" else "Thiết bị lân cận",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                FilledTonalButton(
                                    onClick = { viewModel.joinRoom(device.address) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Thách Đấu ⚔️", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Wi-Fi LAN / Hotspot Rooms List
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Phòng Wi-Fi nội bộ lân cận:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (isScanningWifi) {
                    Text(
                        text = "Đang tìm phòng... 📡",
                        fontSize = 11.sp,
                        color = Color(0xFF00897B),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = "Máy 1 tạo phòng, máy 2 bấm 'Quét Phòng' hoặc 'Nhập IP / Mã' để vào ngay!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (discoveredWifiRooms.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("📶", fontSize = 34.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isScanningWifi) "Đang dò tìm phòng trong mạng Wi-Fi..." else "Chưa thấy phòng Wi-Fi nào mở",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Bảo bạn của bạn bấm 'Tạo Phòng Wi-Fi' trên máy của họ, hoặc bấm 'Nhập IP / Mã' để kết nối trực tiếp!",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(
                                        onClick = { viewModel.startWifiScan() },
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("🔍 Quét Lại", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { showEnterIpDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
                                    ) {
                                        Text("⌨️ Nhập IP / Mã", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(discoveredWifiRooms) { room ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.connectToWifiRoom(room) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF00897B).copy(alpha = 0.15f),
                                        modifier = Modifier.size(42.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("📶", fontSize = 20.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = room.hostName,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (room.roomCode.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF00897B)
                                                ) {
                                                    Text(
                                                        text = "MÃ #${room.roomCode}",
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 9.sp,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "IP: ${room.ip}:${room.port}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.connectToWifiRoom(room) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Vào Đấu ⚔️", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for entering Room IP or Room Code manually
    if (showEnterIpDialog) {
        EnterIpOrCodeDialog(
            defaultSubnet = viewModel.getLocalIpAddress().substringBeforeLast("."),
            onDismiss = { showEnterIpDialog = false },
            onConnect = { target ->
                showEnterIpDialog = false
                viewModel.connectByIpOrCode(target)
            }
        )
    }
}

// --- 2. WAITING FOR OPPONENT SCREEN ---
@Composable
private fun DuelWaitingScreen(
    viewModel: BluetoothDuelViewModel,
    onCancel: () -> Unit
) {
    val isHost by viewModel.isHost.collectAsStateWithLifecycle()
    val opponentName by viewModel.opponentName.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val connectionMedium by viewModel.connectionMedium.collectAsStateWithLifecycle()
    val selectedGameMode by viewModel.selectedGameMode.collectAsStateWithLifecycle()
    val localWifiIp by viewModel.localWifiIp.collectAsStateWithLifecycle()
    val wifiRoomCode by viewModel.wifiRoomCode.collectAsStateWithLifecycle()

    val roundsPerMatch by viewModel.roundsPerMatch.collectAsStateWithLifecycle()
    val questionsPerRound by viewModel.questionsPerRound.collectAsStateWithLifecycle()
    val timePerQuestionSec by viewModel.timePerQuestionSec.collectAsStateWithLifecycle()
    val questionDistributionMode by viewModel.questionDistributionMode.collectAsStateWithLifecycle()

    var questionsInput by remember(questionsPerRound) { mutableStateOf(questionsPerRound.toString()) }
    var timeInput by remember(timePerQuestionSec) { mutableStateOf(timePerQuestionSec.toString()) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val circleColor = when {
            connectionStatus == ConnectionStatus.CONNECTED -> Color(0xFF4CAF50).copy(alpha = 0.2f)
            connectionMedium == ConnectionMedium.WIFI_LAN -> Color(0xFF00897B).copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        }

        Box(
            modifier = Modifier
                .size(105.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when {
                    connectionStatus == ConnectionStatus.CONNECTED -> "🤝"
                    connectionMedium == ConnectionMedium.WIFI_LAN -> "📶"
                    isHost -> "👑"
                    else -> "📡"
                },
                fontSize = 46.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = when {
                connectionStatus == ConnectionStatus.CONNECTED -> "ĐÃ KẾT NỐI VỚI ĐỐI THỦ!"
                connectionMedium == ConnectionMedium.WIFI_LAN && isHost -> "PHÒNG WI-FI ĐANG MỞ..."
                isHost -> "ĐANG CHỜ ĐỐI THỦ BLUETOOTH..."
                else -> "ĐANG KẾT NỐI VỚI CHỦ PHÒNG..."
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = when {
                connectionStatus == ConnectionStatus.CONNECTED -> "Đối thủ: $opponentName"
                connectionMedium == ConnectionMedium.WIFI_LAN && isHost -> "Bảo bạn của bạn mở tab 'Wi-Fi & Hotspot' bấm Quét Phòng hoặc nhập mã phòng bên dưới!"
                isHost -> "Bảo bạn của bạn mở mục 'Quét Máy' hoặc chọn tên điện thoại của bạn trong danh sách ghép đôi!"
                else -> "Đang hoàn tất bắt tay dữ liệu thời gian thực..."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // Show Wi-Fi Room Code & IP Box if Hosting on Wi-Fi
        if (connectionMedium == ConnectionMedium.WIFI_LAN && isHost && connectionStatus != ConnectionStatus.CONNECTED) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF00897B).copy(alpha = 0.15f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "MÃ PHÒNG WI-FI",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = Color(0xFF00695C),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (wifiRoomCode.isNotBlank()) "#$wifiRoomCode" else "Đang tạo...",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF004D40)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Địa chỉ IP Máy Chủ: $localWifiIp:8888",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00695C)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Game Mode Card (Host can change mode anytime before starting)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trò chơi thi đấu:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = selectedGameMode.badge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedGameMode.icon, fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedGameMode.title,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = selectedGameMode.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isHost) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Chủ phòng có thể đổi trò chơi bên dưới:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DuelGameMode.values().forEach { mode ->
                            FilterChip(
                                selected = (mode == selectedGameMode),
                                onClick = { viewModel.selectGameMode(mode) },
                                label = {
                                    Text(
                                        "${mode.icon} ${mode.title}",
                                        fontSize = 11.sp,
                                        fontWeight = if (mode == selectedGameMode) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // --- CÀI ĐẶT TRẬN ĐẤU CỦA CHỦ PHÒNG (Room Settings) ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHost) "Cài Đặt Trận Đấu (Chủ Phòng):" else "Cài Đặt Trận Đấu:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (!isHost) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Đồng bộ từ chủ phòng",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Số hiệp trong 1 trận
                Text(
                    text = "1. Số hiệp đấu: $roundsPerMatch Hiệp",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isHost) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 3, 5, 7).forEach { r ->
                            val sel = r == roundsPerMatch
                            FilterChip(
                                selected = sel,
                                onClick = { viewModel.setRoundsPerMatch(r) },
                                label = { Text("$r Hiệp", fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Số câu hỏi trong 1 hiệp
                Text(
                    text = "2. Số câu hỏi mỗi hiệp: $questionsPerRound câu",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isHost) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(1, 2, 3, 5, 10).forEach { q ->
                            val sel = q == questionsPerRound
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    viewModel.setQuestionsPerRound(q)
                                    questionsInput = q.toString()
                                },
                                label = { Text("$q câu", fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = questionsInput,
                            onValueChange = { input ->
                                val clean = input.filter { it.isDigit() }.take(2)
                                questionsInput = clean
                                clean.toIntOrNull()?.let { num ->
                                    if (num in 1..50) {
                                        viewModel.setQuestionsPerRound(num)
                                    }
                                }
                            },
                            label = { Text("Tùy chỉnh số câu (1 - 50)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (questionsPerRound - 1).coerceAtLeast(1)
                                viewModel.setQuestionsPerRound(next)
                                questionsInput = next.toString()
                            },
                            enabled = questionsPerRound > 1
                        ) {
                            Text("➖", fontSize = 14.sp)
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = (questionsPerRound + 1).coerceAtMost(50)
                                viewModel.setQuestionsPerRound(next)
                                questionsInput = next.toString()
                            },
                            enabled = questionsPerRound < 50
                        ) {
                            Text("➕", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Thời gian cho mỗi câu
                Text(
                    text = "3. Thời gian mỗi câu: $timePerQuestionSec giây",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isHost) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(5, 8, 10, 15, 20, 30).forEach { sec ->
                            val sel = sec == timePerQuestionSec
                            FilterChip(
                                selected = sel,
                                onClick = {
                                    viewModel.setTimePerQuestion(sec)
                                    timeInput = sec.toString()
                                },
                                label = { Text("${sec}s", fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = timeInput,
                            onValueChange = { input ->
                                val clean = input.filter { it.isDigit() }.take(3)
                                timeInput = clean
                                clean.toIntOrNull()?.let { sec ->
                                    if (sec in 3..120) {
                                        viewModel.setTimePerQuestion(sec)
                                    }
                                }
                            },
                            label = { Text("Tùy chỉnh số giây (3 - 120s)", fontSize = 10.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                        FilledTonalIconButton(
                            onClick = {
                                val next = (timePerQuestionSec - 1).coerceAtLeast(3)
                                viewModel.setTimePerQuestion(next)
                                timeInput = next.toString()
                            },
                            enabled = timePerQuestionSec > 3
                        ) {
                            Text("➖", fontSize = 14.sp)
                        }
                        FilledTonalIconButton(
                            onClick = {
                                val next = (timePerQuestionSec + 1).coerceAtMost(120)
                                viewModel.setTimePerQuestion(next)
                                timeInput = next.toString()
                            },
                            enabled = timePerQuestionSec < 120
                        ) {
                            Text("➕", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Cơ chế câu hỏi giữa 2 máy
                Text(
                    text = "4. Câu hỏi giữa 2 máy: ${questionDistributionMode.title}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = questionDistributionMode.subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isHost) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        QuestionDistributionMode.values().forEach { mode ->
                            val isSel = mode == questionDistributionMode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = if (isSel) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setQuestionDistributionMode(mode) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mode.icon, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(mode.title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(mode.subtitle, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (isSel) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (connectionStatus == ConnectionStatus.CONNECTED && isHost) {
            Button(
                onClick = { viewModel.startHostMatch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63))
            ) {
                Text(
                    text = "BẮT ĐẦU TRẬN ĐẤU ⚔️ ($roundsPerMatch HIỆP • $questionsPerRound CÂU • ${timePerQuestionSec}S)",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Hủy và Quay Lại", fontWeight = FontWeight.Bold)
        }
    }
}

// --- 3. COUNTDOWN SCREEN ---
@Composable
private fun DuelCountdownScreen(viewModel: BluetoothDuelViewModel) {
    val count by viewModel.countdownNumber.collectAsStateWithLifecycle()
    val opponentName by viewModel.opponentName.collectAsStateWithLifecycle()
    val selectedGameMode by viewModel.selectedGameMode.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A1C24),
                        Color(0xFF101216)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.12f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedGameMode.icon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedGameMode.title.uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🟢 Bạn", color = Color.White, fontWeight = FontWeight.Bold)
                Text("100 HP", color = Color(0xFF4CAF50), fontSize = 20.sp, fontWeight = FontWeight.Black)
            }

            Text("VS", color = Color(0xFFFF5252), fontSize = 28.sp, fontWeight = FontWeight.Black)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔴 $opponentName", color = Color.White, fontWeight = FontWeight.Bold)
                Text("100 HP", color = Color(0xFFFF5252), fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        }

        Spacer(modifier = Modifier.height(44.dp))

        Text(
            text = if (count > 0) "$count" else "CHIẾN!",
            fontSize = 72.sp,
            fontWeight = FontWeight.Black,
            color = if (count > 0) Color(0xFFFFD54F) else Color(0xFFFF1744)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = selectedGameMode.description,
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// --- 4. BATTLE SCREEN ---
@Composable
private fun DuelBattleScreen(viewModel: BluetoothDuelViewModel) {
    val playerHp by viewModel.playerHp.collectAsStateWithLifecycle()
    val opponentHp by viewModel.opponentHp.collectAsStateWithLifecycle()
    val currentRound by viewModel.currentRound.collectAsStateWithLifecycle()
    val currentQuestionInRound by viewModel.currentQuestionInRound.collectAsStateWithLifecycle()
    val questionsPerRound by viewModel.questionsPerRound.collectAsStateWithLifecycle()
    val currentQuestion by viewModel.currentQuestion.collectAsStateWithLifecycle()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()
    val hasAnswered by viewModel.hasAnsweredCurrentRound.collectAsStateWithLifecycle()
    val timeRemaining by viewModel.roundTimeRemaining.collectAsStateWithLifecycle()
    val notification by viewModel.roundNotification.collectAsStateWithLifecycle()
    val floatingEmote by viewModel.recentFloatingEmote.collectAsStateWithLifecycle()
    val opponentName by viewModel.opponentName.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // HUD Top: Duel HP Bars
            Column {
                // Round Header & Game Mode badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val totalRounds = viewModel.totalRounds
                        val roundLabel = if (questionsPerRound > 1) {
                            "HIỆP $currentRound/$totalRounds • CÂU $currentQuestionInRound/$questionsPerRound"
                        } else {
                            "HIỆP $currentRound/$totalRounds"
                        }
                        Text(
                            text = roundLabel,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        currentQuestion?.let { q ->
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${q.mode.icon} ${q.mode.badge}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // Timer Display
                    val timerColor = when {
                        timeRemaining <= 3 -> Color(0xFFFF1744)
                        timeRemaining <= 6 -> Color(0xFFFF9800)
                        else -> Color(0xFF4CAF50)
                    }
                    Surface(
                        color = timerColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "⏱️ ${timeRemaining}s",
                            color = timerColor,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Dual HP Bars
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Player HP
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Bạn 🟢", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("$playerHp HP", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (playerHp / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = Color(0xFF4CAF50),
                            trackColor = Color(0xFF4CAF50).copy(alpha = 0.2f)
                        )
                    }

                    Text("⚔️", fontSize = 18.sp)

                    // Opponent HP
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("🔴 $opponentName", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$opponentHp HP", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (opponentHp / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = Color(0xFFFF5252),
                            trackColor = Color(0xFFFF5252).copy(alpha = 0.2f)
                        )
                    }
                }

                // Combat Notification Banner
                AnimatedVisibility(visible = notification.isNotBlank()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = notification,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Center Dynamic Question Card based on Game Mode
            currentQuestion?.let { question ->
                when (question.mode) {
                    DuelGameMode.TRUE_FALSE -> {
                        TrueFalseDuelCard(
                            question = question,
                            selectedOption = selectedOption,
                            hasAnswered = hasAnswered,
                            onOptionSelected = { viewModel.submitAnswer(it) }
                        )
                    }
                    DuelGameMode.WORD_SCRAMBLE -> {
                        ScrambleDuelCard(
                            question = question,
                            selectedOption = selectedOption,
                            hasAnswered = hasAnswered,
                            onOptionSelected = { viewModel.submitAnswer(it) }
                        )
                    }
                    else -> {
                        StandardMultipleChoiceCard(
                            question = question,
                            selectedOption = selectedOption,
                            hasAnswered = hasAnswered,
                            onOptionSelected = { viewModel.submitAnswer(it) }
                        )
                    }
                }
            }

            // Bottom Emote Reaction Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("🔥", "⚡", "🛡️", "😎", "😱", "👏").forEach { emoji ->
                    Surface(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .clickable { viewModel.sendEmote(emoji) },
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
        }

        // Floating Emote Animation from Opponent
        floatingEmote?.let { emote ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(80.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(emote, fontSize = 42.sp)
                    }
                }
            }
        }
    }
}

// --- Dynamic Duel Question Layouts ---

@Composable
private fun StandardMultipleChoiceCard(
    question: DuelQuestion,
    selectedOption: String?,
    hasAnswered: Boolean,
    onOptionSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (question.mode == DuelGameMode.REVERSE_MEANING) "Tiếng Anh của từ này là gì?" else "Nghĩa tiếng Việt của từ này là gì?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = question.promptQuestion,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (question.promptSubtitle.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = question.promptSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 4 Options
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.options.forEach { option ->
                val isSelected = selectedOption == option
                val isCorrect = option.trim().equals(question.correctAnswer.trim(), ignoreCase = true)

                val optionColor = when {
                    hasAnswered && isCorrect -> Color(0xFF4CAF50)
                    hasAnswered && isSelected && !isCorrect -> Color(0xFFFF5252)
                    else -> MaterialTheme.colorScheme.surface
                }

                val textColor = when {
                    hasAnswered && isCorrect -> Color.White
                    hasAnswered && isSelected && !isCorrect -> Color.White
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !hasAnswered) {
                            onOptionSelected(option)
                        },
                    colors = CardDefaults.cardColors(containerColor = optionColor),
                    border = if (!hasAnswered) CardDefaults.outlinedCardBorder() else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasAnswered && isCorrect) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Black)
                        } else if (hasAnswered && isSelected && !isCorrect) {
                            Text("✗", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrueFalseDuelCard(
    question: DuelQuestion,
    selectedOption: String?,
    hasAnswered: Boolean,
    onOptionSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "⚡ ĐÚNG HAY SAI THẦN TỐC",
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = question.promptQuestion,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = question.promptSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }

        // Two Big True/False Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isCorrectTrue = question.correctAnswer.equals("ĐÚNG", ignoreCase = true)
            val isSelectedTrue = selectedOption?.equals("ĐÚNG", ignoreCase = true) == true

            val trueBg = when {
                hasAnswered && isCorrectTrue -> Color(0xFF4CAF50)
                hasAnswered && isSelectedTrue && !isCorrectTrue -> Color(0xFFFF5252)
                else -> Color(0xFF4CAF50).copy(alpha = 0.15f)
            }

            val isCorrectFalse = question.correctAnswer.equals("SAI", ignoreCase = true)
            val isSelectedFalse = selectedOption?.equals("SAI", ignoreCase = true) == true

            val falseBg = when {
                hasAnswered && isCorrectFalse -> Color(0xFF4CAF50)
                hasAnswered && isSelectedFalse && !isCorrectFalse -> Color(0xFFFF5252)
                else -> Color(0xFFFF5252).copy(alpha = 0.15f)
            }

            // TRUE Button
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !hasAnswered) { onOptionSelected("ĐÚNG") },
                color = trueBg,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF4CAF50))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("✅", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "ĐÚNG (TRUE)",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = if (hasAnswered && (isCorrectTrue || isSelectedTrue)) Color.White else Color(0xFF2E7D32)
                    )
                }
            }

            // FALSE Button
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(enabled = !hasAnswered) { onOptionSelected("SAI") },
                color = falseBg,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFFF5252))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("❌", fontSize = 28.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "SAI (FALSE)",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = if (hasAnswered && (isCorrectFalse || isSelectedFalse)) Color.White else Color(0xFFC62828)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScrambleDuelCard(
    question: DuelQuestion,
    selectedOption: String?,
    hasAnswered: Boolean,
    onOptionSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "🧩 GIẢI MÃ KÝ TỰ BỊ XÁO TRỘN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = question.promptQuestion,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = question.promptSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 4 Options
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            question.options.forEach { option ->
                val isSelected = selectedOption == option
                val isCorrect = option.trim().equals(question.correctAnswer.trim(), ignoreCase = true)

                val optionColor = when {
                    hasAnswered && isCorrect -> Color(0xFF4CAF50)
                    hasAnswered && isSelected && !isCorrect -> Color(0xFFFF5252)
                    else -> MaterialTheme.colorScheme.surface
                }

                val textColor = when {
                    hasAnswered && isCorrect -> Color.White
                    hasAnswered && isSelected && !isCorrect -> Color.White
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(enabled = !hasAnswered) {
                            onOptionSelected(option)
                        },
                    colors = CardDefaults.cardColors(containerColor = optionColor),
                    border = if (!hasAnswered) CardDefaults.outlinedCardBorder() else null
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (hasAnswered && isCorrect) {
                            Text("✓", color = Color.White, fontWeight = FontWeight.Black)
                        } else if (hasAnswered && isSelected && !isCorrect) {
                            Text("✗", color = Color.White, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

// --- 5. ROUND RECAP SCREEN (HIỆU ỨNG KẾT THÚC MỖI HIỆP) ---
@Composable
private fun DuelRoundRecapScreen(viewModel: BluetoothDuelViewModel) {
    val recap by viewModel.roundRecap.collectAsStateWithLifecycle()
    val playerHp by viewModel.playerHp.collectAsStateWithLifecycle()
    val opponentHp by viewModel.opponentHp.collectAsStateWithLifecycle()
    val opponentName by viewModel.opponentName.collectAsStateWithLifecycle()
    val totalRounds = viewModel.totalRounds

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF1A1C24),
                        Color(0xFF101216)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val roundWon = recap?.playerRoundWon

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.12f)
        ) {
            Text(
                text = "KẾT THÚC HIỆP ${recap?.roundNumber ?: 1} / $totalRounds",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .size(95.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    when (roundWon) {
                        true -> Color(0xFF4CAF50).copy(alpha = 0.25f)
                        false -> Color(0xFFFF9800).copy(alpha = 0.25f)
                        null -> Color(0xFF2196F3).copy(alpha = 0.25f)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = when (roundWon) {
                    true -> "🎉"
                    false -> "💪"
                    null -> "🤝"
                },
                fontSize = 48.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (roundWon) {
                true -> "CHÚC MỪNG CHIẾN THẮNG HIỆP NÀY! 🏆"
                false -> "ĐỐI THỦ VỪA DẪN TRƯỚC! ĐỘNG VIÊN CỐ LÊN! 🛡️"
                null -> "HÒA ĐIỂM CÂN NÃO Ở HIỆP NÀY! 🤝"
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = when (roundWon) {
                true -> Color(0xFF4CAF50)
                false -> Color(0xFFFFB74D)
                null -> Color(0xFF64B5F6)
            }
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = recap?.message ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Damage Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.08f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🟢 Bạn", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${recap?.playerDamageDealt ?: 0} Sát thương",
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                    Text("$playerHp HP còn lại", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }

                Text("VS", color = Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.Black)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔴 $opponentName", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${recap?.opponentDamageDealt ?: 0} Sát thương",
                        color = Color(0xFFFF5252),
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                    Text("$opponentHp HP còn lại", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .width(160.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Đang chuyển sang hiệp tiếp theo...",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 11.sp
        )
    }
}

// --- 6. MATCH OVER SCREEN (HIỆU ỨNG THẮNG CHUNG CUỘC, PHÁO HOA, LỜI CHÚC MỪNG) ---
@Composable
private fun DuelGameOverScreen(
    viewModel: BluetoothDuelViewModel,
    onBackToMenu: () -> Unit
) {
    val isWinner by viewModel.isWinner.collectAsStateWithLifecycle()
    val title by viewModel.finalScoreTitle.collectAsStateWithLifecycle()
    val notification by viewModel.roundNotification.collectAsStateWithLifecycle()
    val earnedXp by viewModel.earnedXp.collectAsStateWithLifecycle()
    val earnedCoins by viewModel.earnedCoins.collectAsStateWithLifecycle()
    val playerHp by viewModel.playerHp.collectAsStateWithLifecycle()
    val opponentHp by viewModel.opponentHp.collectAsStateWithLifecycle()
    val selectedGameMode by viewModel.selectedGameMode.collectAsStateWithLifecycle()
    val roundsPerMatch by viewModel.roundsPerMatch.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    if (isWinner) listOf(Color(0xFF0D1B2A), Color(0xFF1B263B), Color(0xFF000814))
                    else listOf(Color(0xFF210B0E), Color(0xFF140809), Color(0xFF0B0405))
                )
            )
    ) {
        // Fireworks canvas for Match Champion
        if (isWinner) {
            CelebrationFireworksEffect(
                onExplosion = {
                    viewModel.triggerFireworkSound()
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isWinner) "🏆👑🎉" else "💀🛡️",
                fontSize = if (isWinner) 56.sp else 60.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                color = if (isWinner) Color(0xFFFFD700) else Color(0xFFFF5252)
            )

            if (isWinner) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFD700).copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700))
                ) {
                    Text(
                        text = "✨ QUÁN QUÂN ĐẤU TRƯỜNG 1VS1 XUẤT SẮC ✨",
                        color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (isWinner) {
                    "Chúc mừng bạn đã xuất sắc chiến thắng chung cuộc sau $roundsPerMatch hiệp đấu căng thẳng!"
                } else {
                    "Trận đấu rất kịch tính! Thất bại là mẹ thành công, cùng luyện tập và phục thù ở trận kế tiếp nhé!"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = notification,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.12f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Chế độ: ${selectedGameMode.title} • $roundsPerMatch Hiệp",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (isWinner) Color(0xFFFFD700) else Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Máu còn lại: $playerHp HP (Bạn) - $opponentHp HP (Đối thủ)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Text(
                            text = "+$earnedXp XP 🌟",
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFD54F),
                            fontSize = 16.sp
                        )
                        Text(
                            text = "+$earnedCoins Coins 🪙",
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFB74D),
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { viewModel.requestRematch() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isWinner) Color(0xFFFF9800) else MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Đấu Lại Ngay (Rematch) 🔄", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onBackToMenu,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Về Menu Trò Chơi 🏠", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// --- 6. AI DUEL DIFFICULTY & MODE SELECTION DIALOG ---
@Composable
private fun AiDuelConfigDialog(
    viewModel: BluetoothDuelViewModel,
    onDismiss: () -> Unit,
    onStartBattle: (AiDifficulty, DuelGameMode) -> Unit
) {
    var chosenDifficulty by remember { mutableStateOf(viewModel.selectedAiDifficulty.value) }
    var chosenMode by remember { mutableStateOf(viewModel.selectedGameMode.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🤖", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tùy Chọn Đấu Với AI",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "1. Mức Độ Thử Thách AI:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                AiDifficulty.values().forEach { diff ->
                    val isSelected = diff == chosenDifficulty
                    val borderColor = Color(diff.colorHex)

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { chosenDifficulty = diff },
                        color = if (isSelected) borderColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, borderColor) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { chosenDifficulty = diff },
                                colors = RadioButtonDefaults.colors(selectedColor = borderColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = diff.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = borderColor.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = diff.badge,
                                            color = borderColor,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = diff.description,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "2. Chọn Trò Chơi:",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )

                DuelGameMode.values().forEach { mode ->
                    val isSelected = mode == chosenMode

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { chosenMode = mode },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(mode.icon, fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = mode.subtitle,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStartBattle(chosenDifficulty, chosenMode) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("VÀO TRẬN CHIẾN NGAY ⚔️", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}

// --- 7. ENTER IP / ROOM CODE DIALOG ---
@Composable
private fun EnterIpOrCodeDialog(
    defaultSubnet: String,
    onDismiss: () -> Unit,
    onConnect: (String) -> Unit
) {
    var textInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⌨️", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Nhập IP hoặc Mã Phòng",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Nhập Mã phòng (ví dụ: 1588) hoặc Địa chỉ IP của máy chủ để kết nối trực tiếp:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = { Text("Ví dụ: 192.168.1.15 hoặc Mã số") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick buttons for Hotspot default IP
                Text(
                    text = "Gợi ý nhanh:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SuggestionChip(
                        onClick = { textInput = "192.168.43.1" },
                        label = { Text("📱 Hotspot (192.168.43.1)", fontSize = 11.sp) }
                    )
                    SuggestionChip(
                        onClick = { textInput = "$defaultSubnet." },
                        label = { Text("📶 Mạng LAN ($defaultSubnet.x)", fontSize = 11.sp) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (textInput.isNotBlank()) {
                        onConnect(textInput.trim())
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00897B))
            ) {
                Text("KẾT NỐI NGAY ⚔️", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}

// --- 8. CONNECTION GUIDE DIALOG (BLUETOOTH + WI-FI + HOTSPOT) ---
@Composable
private fun DuelConnectionGuideDialog(
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Bluetooth, 1: Wi-Fi LAN, 2: Hotspot

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hướng Dẫn Kết Nối 2 Máy",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tab selector
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Bluetooth", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Wi-Fi & Internet", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Điểm Phát Hotspot", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                when (selectedTab) {
                    0 -> {
                        // Bluetooth instructions
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📡 KẾT NỐI QUA BLUETOOTH", fontWeight = FontWeight.Black, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "1. Bật Bluetooth trên cả 2 điện thoại.\n" +
                                    "2. Vào Cài đặt Bluetooth của máy và Ghép đôi (Pair) 2 máy trước 1 lần duy nhất.\n" +
                                    "3. Máy 1: Chọn tab 'Bluetooth' ➔ Bấm '👑 Tạo Phòng'.\n" +
                                    "4. Máy 2: Bấm '🔍 Quét Máy' hoặc chọn tên Máy 1 trong mục Đã ghép đôi ➔ Bấm 'Thách Đấu ⚔️'.\n" +
                                    "5. Khi cả 2 hiện 'ĐÃ KẾT NỐI', Chủ phòng bấm 'BẮT ĐẦU TRẬN ĐẤU'!",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    1 -> {
                        // Wi-Fi LAN & Internet instructions
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF00897B).copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📶 KẾT NỐI QUA WI-FI NỘI BỘ HOẶC INTERNET", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFF00695C))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "🌐 CÁCH 1 - WI-FI NỘI BỘ (Chung mạng Wi-Fi):\n" +
                                    "1. Cả 2 máy kết nối vào CÙNG 1 MẠNG WI-FI (nhà riêng, trường học, quán cafe).\n" +
                                    "2. Máy 1: Chọn tab 'Wi-Fi & Internet' ➔ Bấm '👑 Tạo Phòng' (hiển thị IP và Mã phòng).\n" +
                                    "3. Máy 2: Bấm '🔍 Quét Phòng', phòng của Máy 1 sẽ xuất hiện ngay ➔ Bấm 'Vào Đấu ⚔️'.\n\n" +
                                    "🌍 CÁCH 2 - QUA INTERNET (Khác mạng Wi-Fi / 4G):\n" +
                                    "1. Máy 1 tạo phòng và xem IP máy (hoặc IP công cộng/VPN).\n" +
                                    "2. Máy 2 bấm '⌨️ Nhập IP / Mã' ➔ Nhập địa chỉ IP của Máy 1 (ví dụ 192.168.x.x hoặc IP Internet) ➔ Bấm 'KẾT NỐI NGAY' là vào trận tức thì!",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFF004D40)
                                )
                            }
                        }
                    }
                    2 -> {
                        // Wi-Fi Hotspot instructions
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFA000).copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📱 KẾT NỐI QUA ĐIỂM PHÁT SÓNG (HOTSPOT)", fontWeight = FontWeight.Black, fontSize = 13.sp, color = Color(0xFFE65100))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "⚡ KHÔNG CẦN INTERNET, KHÔNG TỐN 3G/4G!\n\n" +
                                    "1. Máy 1: Bật 'Điểm phát sóng di động' (Personal Hotspot) trong Cài đặt của điện thoại.\n" +
                                    "2. Máy 2: Bật Wi-Fi và kết nối vào Điểm phát sóng của Máy 1.\n" +
                                    "3. Mở game, vào tab 'Wi-Fi & Hotspot'.\n" +
                                    "4. Máy 1: Bấm 'Tạo Phòng Wi-Fi'.\n" +
                                    "5. Máy 2: Bấm 'Nhập IP / Mã' ➔ Bấm gợi ý '📱 Hotspot (192.168.43.1)' ➔ Bấm 'KẾT NỐI NGAY' là vào trận liền!",
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color(0xFFBF360C)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Đã Hiểu, Bắt Đầu! 👍", fontWeight = FontWeight.Bold)
            }
        }
    )
}
