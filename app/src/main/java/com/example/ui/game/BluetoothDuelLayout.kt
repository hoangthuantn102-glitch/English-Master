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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluetooth.BluetoothDeviceItem
import com.example.bluetooth.ConnectionStatus
import com.example.ui.viewmodel.AiDifficulty
import com.example.ui.viewmodel.BluetoothDuelViewModel
import com.example.ui.viewmodel.DuelGameMode
import com.example.ui.viewmodel.DuelQuestion
import com.example.ui.viewmodel.DuelScreenState

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
                    onOpenAiConfig = { showAiConfigDialog = true }
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
            DuelScreenState.BATTLE, DuelScreenState.ROUND_RECAP -> {
                DuelBattleScreen(viewModel = viewModel)
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
    }
}

// --- 1. LOBBY SCREEN ---
@Composable
private fun DuelLobbyScreen(
    viewModel: BluetoothDuelViewModel,
    onBackToMenu: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenAiConfig: () -> Unit
) {
    var pairedDevices by remember { mutableStateOf<List<BluetoothDeviceItem>>(emptyList()) }
    val discoveredDevices by viewModel.discoveredDevices.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val selectedGameMode by viewModel.selectedGameMode.collectAsStateWithLifecycle()

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
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToMenu) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Trở về")
            }
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Đấu Trường 1v1 Bluetooth",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Đối kháng trực tiếp qua Bluetooth & Đấu với AI đa cấp độ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Hero Banner Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⚔️",
                    fontSize = 36.sp,
                    modifier = Modifier.padding(end = 10.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chiến Trận Thời Gian Thực!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Chọn trò chơi phù hợp: Đoán nghĩa, Nghịch đảo, Đúng/Sai hoặc Xếp chữ để hạ gục HP đối phương!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3 Action Buttons: Host, Scan/Join, Solo Practice with AI
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

        Spacer(modifier = Modifier.height(14.dp))

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

        Spacer(modifier = Modifier.height(8.dp))

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

        Spacer(modifier = Modifier.height(14.dp))

        // Bluetooth Devices List (Paired & Discovered)
        Text(
            text = "Chọn máy đối thủ để thách đấu:",
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
                                text = "Bấm 'Tạo Phòng' để chờ bạn bè kết nối, hoặc chọn 'Đấu Với AI' để luyện phản xạ ngay!",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
    val selectedGameMode by viewModel.selectedGameMode.collectAsStateWithLifecycle()

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
        Box(
            modifier = Modifier
                .size(105.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    if (connectionStatus == ConnectionStatus.CONNECTED) Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (connectionStatus == ConnectionStatus.CONNECTED) "🤝" else if (isHost) "👑" else "📡",
                fontSize = 46.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (connectionStatus == ConnectionStatus.CONNECTED)
                "ĐÃ KẾT NỐI VỚI ĐỐI THỦ!"
            else if (isHost)
                "ĐANG CHỜ ĐỐI THỦ KẾT NỐI..."
            else
                "ĐANG KẾT NỐI VỚI CHỦ PHÒNG...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (connectionStatus == ConnectionStatus.CONNECTED)
                "Đối thủ: $opponentName"
            else if (isHost)
                "Bảo bạn của bạn mở mục 'Quét Máy' hoặc bấm tên điện thoại của bạn trong danh sách ghép đôi!"
            else
                "Đang hoàn tất bắt tay Bluetooth...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

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

        Spacer(modifier = Modifier.height(28.dp))

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
                    text = "BẮT ĐẦU TRẬN ĐẤU ⚔️ (5 HIỆP)",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
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
        // Game Mode Header Badge
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
                        Text(
                            text = "HIỆP $currentRound/5",
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

                // Letter tiles representation
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

// --- 5. MATCH OVER SCREEN ---
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isWinner) "🏆" else "💀",
            fontSize = 72.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = if (isWinner) Color(0xFF4CAF50) else Color(0xFFFF5252)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = notification,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Final HP Summary Card
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Chế độ: ${selectedGameMode.title}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Máu còn lại: $playerHp HP (Bạn) - $opponentHp HP (Đối thủ)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text(
                        text = "+$earnedXp XP 🌟",
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFFB300),
                        fontSize = 16.sp
                    )
                    Text(
                        text = "+$earnedCoins Coins 🪙",
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF9800),
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
                containerColor = MaterialTheme.colorScheme.primary
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
            Text("Về Menu Trò Chơi 🏠", fontWeight = FontWeight.Bold)
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
                // Section 1: Choose Difficulty Level
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

                // Section 2: Choose Game Mode
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
