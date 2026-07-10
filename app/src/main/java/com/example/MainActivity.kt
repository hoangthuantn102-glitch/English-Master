@file:OptIn(ExperimentalLayoutApi::class)

package com.example

import android.os.Bundle
import android.widget.Toast
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Word
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.GameViewModel
import com.example.ui.viewmodel.WordViewModel
import com.example.ui.viewmodel.ImportResult
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val wordViewModel: WordViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val selectedTheme by wordViewModel.selectedTheme.collectAsStateWithLifecycle()
            val themeMode by wordViewModel.themeMode.collectAsStateWithLifecycle()
            
            val isDark = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            
            MyApplicationTheme(
                darkTheme = isDark,
                selectedTheme = selectedTheme
            ) {
                MainContainer(wordViewModel)
            }
        }
    }
}

@Composable
fun MainContainer(wordViewModel: WordViewModel) {
    val gameViewModel: GameViewModel = viewModel()
    val userProfile by wordViewModel.userProfile.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var currentTab by remember { mutableStateOf("home") }
    var showFlashcardsSession by remember { mutableStateOf(false) }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(currentTab = currentTab, onTabSelected = { 
                currentTab = it 
                gameViewModel.backToMenu() // Reset active game state if they leave
            })
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Adaptive Side Navigation for landscape or larger screens (Medium/Expanded)
            // Just standard screen column is excellent for all aspect ratios
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        "home" -> HomeScreen(
                            wordViewModel = wordViewModel,
                            onStartSession = { showFlashcardsSession = true },
                            onPracticeGames = { currentTab = "games" }
                        )
                        "words" -> WordsScreen(wordViewModel = wordViewModel)
                        "grammar" -> GrammarScreen(wordViewModel = wordViewModel)
                        "games" -> GamesScreen(gameViewModel = gameViewModel)
                        "stats" -> StatsScreen(wordViewModel = wordViewModel)
                        "ai" -> ChatAiScreen(
                            wordViewModel = wordViewModel,
                            onNavigateToSettings = { currentTab = "settings" }
                        )
                        "settings" -> SettingsScreen(wordViewModel = wordViewModel)
                    }
                }

                // Spaced Repetition Practice Session Overlays
                if (showFlashcardsSession) {
                    FlashcardsSessionDialog(
                        wordViewModel = wordViewModel,
                        onDismiss = { showFlashcardsSession = false }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(currentTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
        tonalElevation = 3.dp,
        modifier = Modifier.height(80.dp)
    ) {
        val items = listOf(
            Triple("home", "Trang chủ", "🏠"),
            Triple("words", "Từ vựng", "📚"),
            Triple("grammar", "Ngữ pháp", "📝"),
            Triple("games", "Trò chơi", "🎮"),
            Triple("stats", "Thống kê", "📊"),
            Triple("ai", "Chat AI", "🤖"),
            Triple("settings", "Cài đặt", "⚙")
        )

        items.forEach { (route, label, emoji) ->
            val isSelected = currentTab == route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(route) },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 56.dp else 40.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = if (isSelected) 22.sp else 18.sp)
                    }
                },
                label = {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )
        }
    }
}

// --- HOME SCREEN ---
@Composable
fun HomeScreen(
    wordViewModel: WordViewModel,
    onStartSession: () -> Unit,
    onPracticeGames: () -> Unit
) {
    val userProfile by wordViewModel.userProfile.collectAsStateWithLifecycle()
    val words by wordViewModel.allWords.collectAsStateWithLifecycle()
    val reviewQueue by wordViewModel.reviewQueue.collectAsStateWithLifecycle()

    val learnedWords = words.filter { it.learnedStatus == "MASTERED" }.size
    val learningWords = words.filter { it.learnedStatus == "LEARNING" || it.learnedStatus == "REVIEW" }.size
    val totalWords = words.size
    val completionPercent = if (totalWords > 0) (learnedWords.toFloat() / totalWords * 100).toInt() else 0

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ENGLISH MASTER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Chào mừng trở lại! 👋",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                
                // Avatar Badge with level
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("A", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Text(
                        text = "Cấp độ ${userProfile.level}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Gamification metrics grid
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Streak Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFD1E4FF)),
                    border = BorderStroke(1.dp, Color(0xFFBAC7DB)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🔥", fontSize = 24.sp)
                        Text("${userProfile.streak} Ngày", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF001D36))
                        Text("STREAK", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF001D36).copy(alpha = 0.7f))
                    }
                }

                // XP Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
                    border = BorderStroke(1.dp, Color(0xFFD0BCFF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("⭐", fontSize = 24.sp)
                        Text("${userProfile.xp} XP", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF21005D))
                        Text("KINH NGHIỆM", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF21005D).copy(alpha = 0.7f))
                    }
                }

                // Coins Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDDAE)),
                    border = BorderStroke(1.dp, Color(0xFFF0BC85)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🪙", fontSize = 24.sp)
                        Text("${userProfile.coins}", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF2C1600))
                        Text("XU VÀNG", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2C1600).copy(alpha = 0.7f))
                    }
                }
            }
        }

        // Circular progress container (High Density specification)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TIẾN ĐỘ HỌC TẬP",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text(
                                text = "$completionPercent% Mục tiêu",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(140.dp)
                    ) {
                        CircularProgressIndicator(
                            progress = { if (totalWords > 0) learnedWords.toFloat() / totalWords else 0f },
                            modifier = Modifier.fillMaxSize(),
                            strokeWidth = 12.dp,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$learnedWords/$totalWords",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Từ đã nhớ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stats grid bottom row
                    HorizontalDivider(modifier = Modifier.fillMaxWidth(), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Đang học", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$learningWords", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        VerticalDivider(modifier = Modifier.height(30.dp), thickness = 1.dp)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Cần ôn tập", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${reviewQueue.size}", 
                                style = MaterialTheme.typography.titleMedium, 
                                fontWeight = FontWeight.Bold,
                                color = if (reviewQueue.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        // Primary Call to Actions
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onStartSession,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("start_session_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("Bắt đầu ôn tập (Spaced Repetition)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (reviewQueue.isNotEmpty()) "Có ${reviewQueue.size} từ cần ôn tập hôm nay" else "Không có từ nào cần ôn tập, hãy học từ mới!",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Text("➔", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }

                OutlinedButton(
                    onClick = onPracticeGames,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Luyện tập qua Trò chơi ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("🎮", fontSize = 16.sp)
                    }
                }
            }
        }

        // Daily Challenge Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("💡", fontSize = 32.sp)
                    Column {
                        Text("Nhiệm vụ hàng ngày", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Ôn tập 5 từ vựng hoặc chơi bất kỳ game nào để duy trì chuỗi ngày học liên tiếp của bạn!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

// --- WORDS SCREEN ---
@Composable
fun WordsScreen(wordViewModel: WordViewModel) {
    val words by wordViewModel.filteredWords.collectAsStateWithLifecycle()
    val allWordsList by wordViewModel.allWords.collectAsStateWithLifecycle()
    val topics by wordViewModel.topics.collectAsStateWithLifecycle()
    val searchQuery by wordViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTopic by wordViewModel.selectedTopic.collectAsStateWithLifecycle()
    val selectedLevel by wordViewModel.selectedLevel.collectAsStateWithLifecycle()
    val aiState by wordViewModel.aiState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showAiDeckDialog by remember { mutableStateOf(false) }
    var wordToEdit by remember { mutableStateOf<Word?>(null) }
    var selectedDetailWord by remember { mutableStateOf<Word?>(null) }
    var wordToDelete by remember { mutableStateOf<Word?>(null) }

    val context = LocalContext.current

    // Alert or loading feedback
    LaunchedEffect(aiState) {
        if (aiState is WordViewModel.AiState.Success) {
            Toast.makeText(context, (aiState as WordViewModel.AiState.Success).message, Toast.LENGTH_LONG).show()
            wordViewModel.resetAiState()
        } else if (aiState is WordViewModel.AiState.Error) {
            Toast.makeText(context, (aiState as WordViewModel.AiState.Error).error, Toast.LENGTH_LONG).show()
            wordViewModel.resetAiState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Thư viện từ vựng", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { showAiDeckDialog = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text("🤖", fontSize = 18.sp)
                }
                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text("➕", fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { wordViewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Tìm kiếm từ vựng, nghĩa hoặc chủ đề...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { wordViewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = null)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Topic Filters FlowRow / LazyRow
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(topics) { topic ->
                val isSelected = selectedTopic == topic
                FilterChip(
                    selected = isSelected,
                    onClick = { wordViewModel.setSelectedTopic(topic) },
                    label = { Text(topic) }
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Level Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val levels = listOf("All", "Beginner", "Intermediate", "Advanced")
            items(levels) { lvl ->
                val isSelected = selectedLevel == lvl
                FilterChip(
                    selected = isSelected,
                    onClick = { wordViewModel.setSelectedLevel(lvl) },
                    label = { Text(lvl) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Word count summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hiển thị ${words.size}/${allWordsList.size} từ vựng",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (aiState is WordViewModel.AiState.Generating) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                    Text("AI đang tạo...", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Words List
        if (words.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Text(
                        "Không tìm thấy từ vựng nào khớp với bộ lọc.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(words) { item ->
                    WordItemCard(
                        word = item,
                        onClick = { selectedDetailWord = item },
                        onFavoriteToggle = { wordViewModel.toggleFavorite(item) },
                        onSpeak = { wordViewModel.speak(item.word) },
                        onDelete = { wordToDelete = item },
                        onEdit = { wordToEdit = item }
                    )
                }
            }
        }
    }

    // --- DIALOGS FOR ADDS / EDITS / AI CREATIONS ---

    if (showAddDialog) {
        WordAddEditDialog(
            wordToEdit = null,
            onDismiss = { showAddDialog = false },
            onSave = { word ->
                wordViewModel.addWord(word)
                showAddDialog = false
            }
        )
    }

    if (wordToEdit != null) {
        WordAddEditDialog(
            wordToEdit = wordToEdit,
            onDismiss = { wordToEdit = null },
            onSave = { word ->
                wordViewModel.updateWord(word)
                wordToEdit = null
            }
        )
    }

    if (selectedDetailWord != null) {
        WordDetailDialog(
            word = selectedDetailWord!!,
            wordViewModel = wordViewModel,
            onDismiss = { selectedDetailWord = null },
            onGenerateAiExample = {
                wordViewModel.generateAiExample(selectedDetailWord!!)
                selectedDetailWord = null // dismiss detail first to show loader
            }
        )
    }

    if (showAiDeckDialog) {
        AiDeckGenerationDialog(
            onDismiss = { showAiDeckDialog = false },
            onGenerate = { topic, level, count ->
                wordViewModel.generateThemeDeck(topic, level, count)
                showAiDeckDialog = false
            }
        )
    }

    if (wordToDelete != null) {
        AlertDialog(
            onDismissRequest = { wordToDelete = null },
            title = { Text("Xác nhận xóa từ vựng 🗑️") },
            text = { Text("Bạn có chắc chắn muốn xóa từ \"${wordToDelete?.word}\" (${wordToDelete?.meaning}) khỏi kho từ vựng của mình không? Hành động này không thể hoàn tác.") },
            confirmButton = {
                Button(
                    onClick = {
                        wordToDelete?.let {
                            wordViewModel.deleteWord(it)
                        }
                        wordToDelete = null
                        Toast.makeText(context, "Đã xóa từ vựng thành công!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { wordToDelete = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun WordItemCard(
    word: Word,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onSpeak: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = word.word,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = word.pronunciation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = word.meaning,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            text = word.topic,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            text = word.learnedStatus,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onSpeak) {
                    Text("🔊", fontSize = 18.sp)
                }

                IconButton(onClick = onFavoriteToggle) {
                    Text(if (word.isFavorite) "⭐" else "☆", fontSize = 20.sp)
                }

                Box {
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(expanded = expandedMenu, onDismissRequest = { expandedMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Sửa từ vựng") },
                            onClick = {
                                expandedMenu = false
                                onEdit()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Xóa từ vựng", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                expandedMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

// Dialog to Add/Edit word
@Composable
fun WordAddEditDialog(
    wordToEdit: Word?,
    onDismiss: () -> Unit,
    onSave: (Word) -> Unit
) {
    var wordStr by remember { mutableStateOf(wordToEdit?.word ?: "") }
    var pronunciationStr by remember { mutableStateOf(wordToEdit?.pronunciation ?: "") }
    var meaningStr by remember { mutableStateOf(wordToEdit?.meaning ?: "") }
    var exampleStr by remember { mutableStateOf(wordToEdit?.example ?: "") }
    var translationStr by remember { mutableStateOf(wordToEdit?.translation ?: "") }
    var topicStr by remember { mutableStateOf(wordToEdit?.topic ?: "General") }
    var levelStr by remember { mutableStateOf(wordToEdit?.level ?: "Beginner") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (wordToEdit == null) "Thêm từ vựng mới" else "Chỉnh sửa từ vựng",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = wordStr,
                    onValueChange = { wordStr = it },
                    label = { Text("Từ tiếng Anh *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pronunciationStr,
                    onValueChange = { pronunciationStr = it },
                    label = { Text("Phát âm (IPA)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = meaningStr,
                    onValueChange = { meaningStr = it },
                    label = { Text("Nghĩa tiếng Việt *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = exampleStr,
                    onValueChange = { exampleStr = it },
                    label = { Text("Ví dụ minh họa (English)") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = translationStr,
                    onValueChange = { translationStr = it },
                    label = { Text("Dịch câu ví dụ") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = topicStr,
                    onValueChange = { topicStr = it },
                    label = { Text("Chủ đề (Topic)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Level select
                Column {
                    Text("Cấp độ", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Beginner", "Intermediate", "Advanced").forEach { lvl ->
                            val isSelected = levelStr == lvl
                            FilterChip(
                                selected = isSelected,
                                onClick = { levelStr = lvl },
                                label = { Text(lvl) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (wordStr.isNotBlank() && meaningStr.isNotBlank()) {
                                val savedWord = wordToEdit?.copy(
                                    word = wordStr.trim(),
                                    pronunciation = pronunciationStr.trim(),
                                    meaning = meaningStr.trim(),
                                    example = exampleStr.trim(),
                                    translation = translationStr.trim(),
                                    topic = topicStr.trim(),
                                    level = levelStr
                                ) ?: Word(
                                    word = wordStr.trim(),
                                    pronunciation = pronunciationStr.trim(),
                                    meaning = meaningStr.trim(),
                                    example = exampleStr.trim(),
                                    translation = translationStr.trim(),
                                    topic = topicStr.trim(),
                                    level = levelStr
                                )
                                onSave(savedWord)
                            }
                        },
                        enabled = wordStr.isNotBlank() && meaningStr.isNotBlank()
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

// Levenshtein distance helper for speech similarity
fun levenshtein(s: String, t: String): Int {
    if (s == t) return 0
    if (s.isEmpty()) return t.length
    if (t.isEmpty()) return s.length

    val dp = IntArray(t.length + 1) { it }
    for (i in 1..s.length) {
        var prev = i - 1
        dp[0] = i
        for (j in 1..t.length) {
            val temp = dp[j]
            if (s[i - 1] == t[j - 1]) {
                dp[j] = prev
            } else {
                dp[j] = minOf(dp[j - 1] + 1, dp[j] + 1, prev + 1)
            }
            prev = temp
        }
    }
    return dp[t.length]
}

@Composable
fun PronunciationChecker(
    targetWord: String,
    wordViewModel: WordViewModel? = null,
    onCorrectPronunciation: () -> Unit = {}
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var recognizedText by remember { mutableStateOf("") }
    var scoreResult by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            errorMessage = "Hãy cấp quyền Ghi âm để kiểm tra phát âm!"
        }
    }

    val speechRecognizer = remember {
        try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            null
        }
    }

    val recognizerIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    fun calculateMatchScore(spoken: String, target: String): Int {
        val s = spoken.lowercase().trim().replace(Regex("[^a-zA-Z0-9\\s]"), "")
        val t = target.lowercase().trim().replace(Regex("[^a-zA-Z0-9\\s]"), "")
        
        if (s == t) return 100
        if (s.contains(t) || t.contains(s)) {
            val ratio = if (t.length > s.length) s.length.toFloat() / t.length else t.length.toFloat() / s.length
            return (ratio * 100).toInt().coerceIn(50, 95)
        }
        
        val maxLen = maxOf(s.length, t.length)
        if (maxLen == 0) return 0
        val dist = levenshtein(s, t)
        val similarity = (1.0 - dist.toDouble() / maxLen.toDouble()) * 100
        return similarity.toInt().coerceIn(0, 100)
    }

    val listener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
                recognizedText = "Đang lắng nghe..."
                scoreResult = null
                errorMessage = null
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
            }
            override fun onError(error: Int) {
                isListening = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Lỗi âm thanh."
                    SpeechRecognizer.ERROR_CLIENT -> "Lỗi thiết bị hoặc không hỗ trợ."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Thiếu quyền ghi âm."
                    SpeechRecognizer.ERROR_NETWORK -> "Lỗi kết nối mạng."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hết thời gian kết nối."
                    SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận dạng được từ, hãy thử phát âm rõ hơn!"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Hệ thống đang bận."
                    SpeechRecognizer.ERROR_SERVER -> "Lỗi máy chủ phát âm."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy âm thanh."
                    else -> "Lỗi phát âm ($error)"
                }
                errorMessage = msg
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val spoken = matches[0]
                    recognizedText = spoken
                    val score = calculateMatchScore(spoken, targetWord)
                    scoreResult = score
                    if (score >= 80) {
                        onCorrectPronunciation()
                    }
                } else {
                    errorMessage = "Không tìm thấy từ khớp."
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognizedText = matches[0]
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val toggleListening = {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        } else if (speechRecognizer != null) {
            if (isListening) {
                speechRecognizer.stopListening()
                isListening = false
            } else {
                speechRecognizer.setRecognitionListener(listener)
                speechRecognizer.startListening(recognizerIntent)
            }
        } else {
            errorMessage = "Tính năng Speech-to-Text không khả dụng."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Kiểm tra phát âm",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Microphone Button
            IconButton(
                onClick = { toggleListening() },
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (isListening) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    )
            ) {
                Text(
                    text = if (isListening) "🛑" else "🎤",
                    fontSize = 24.sp
                )
            }

            // Status message
            Column {
                Text(
                    text = if (isListening) "Đang nghe... Hãy nói ngay!" else "Nhấn Micro để kiểm tra",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (recognizedText.isNotBlank()) {
                    Text(
                        text = "Bạn nói: \"$recognizedText\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Score display
        scoreResult?.let { score ->
            val color = when {
                score >= 80 -> Color(0xFF4CAF50) // Green
                score >= 50 -> Color(0xFFFFA000) // Orange
                else -> Color(0xFFE53935) // Red
            }

            val feedback = when {
                score >= 95 -> "Tuyệt hảo! Phát âm chuẩn xác 100%"
                score >= 80 -> "Tuyệt vời! Bạn phát âm rất tốt."
                score >= 50 -> "Khá tốt! Hãy cố gắng phát âm rõ hơn nữa."
                else -> "Chưa chính xác, hãy bấm Micro và thử lại nhé!"
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
                border = BorderStroke(1.dp, color),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$score điểm 🏆",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                    Text(
                        text = feedback,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = color
                    )
                }
            }
        }

        // Error display
        errorMessage?.let { err ->
            Text(
                text = "⚠️ $err",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

// Word detail popup dialog
@Composable
fun WordDetailDialog(
    word: Word,
    wordViewModel: WordViewModel,
    onDismiss: () -> Unit,
    onGenerateAiExample: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(word.topic, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                        Text(
                            text = word.level,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(word.word, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                        Text(word.pronunciation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(
                        onClick = { wordViewModel.speak(word.word) },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            .size(48.dp)
                    ) {
                        Text("🔊", fontSize = 22.sp)
                    }
                }

                HorizontalDivider()

                Text("Ý nghĩa:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                Text(word.meaning, style = MaterialTheme.typography.bodyLarge)

                Spacer(modifier = Modifier.height(4.dp))

                Text("Câu ví dụ:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                if (word.example.isNotBlank()) {
                    Text(word.example, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(word.translation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                } else {
                    Text("Chưa có ví dụ.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                }

                Spacer(modifier = Modifier.height(6.dp))

                PronunciationChecker(
                    targetWord = word.word,
                    wordViewModel = wordViewModel,
                    onCorrectPronunciation = {
                        wordViewModel.awardXpAndCoins(10, 1)
                        Toast.makeText(context, "🎉 Phát âm xuất sắc! Nhận ngay +10 XP & +1 Coin!", Toast.LENGTH_SHORT).show()
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))

                // AI Generate Example Button
                Button(
                    onClick = onGenerateAiExample,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤖 AI Tạo câu ví dụ mới")
                    }
                }

                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Đóng")
                }
            }
        }
    }
}

// AI Theme Deck generator dialog
@Composable
fun AiDeckGenerationDialog(
    onDismiss: () -> Unit,
    onGenerate: (topic: String, level: String, count: Int) -> Unit
) {
    var topic by remember { mutableStateOf("Travel") }
    var level by remember { mutableStateOf("Beginner") }
    var count by remember { mutableStateOf(5) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("🤖 AI Sinh Bộ Từ Vựng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    "Hãy nhập chủ đề bạn mong muốn, chọn mức độ và số lượng từ vựng cần tạo. AI Gemini sẽ thiết kế bộ từ vựng cực chất có kèm phát âm, ví dụ & dịch nghĩa hoàn hảo!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it },
                    label = { Text("Nhập chủ đề (ví dụ: Travel, IELTS, TOEIC...)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("Mức độ học", style = MaterialTheme.typography.labelSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("Beginner", "Intermediate", "Advanced").forEach { lvl ->
                            val isSelected = level == lvl
                            FilterChip(
                                selected = isSelected,
                                onClick = { level = lvl },
                                label = { Text(lvl) }
                            )
                        }
                    }
                }

                Column {
                    Text("Số lượng từ vựng cần sinh", style = MaterialTheme.typography.labelSmall)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(3, 5, 10, 15, 20).forEach { num ->
                            val isSelected = count == num
                            FilterChip(
                                selected = isSelected,
                                onClick = { count = num },
                                label = { Text("$num từ") }
                            )
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Hủy") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onGenerate(topic.trim(), level, count) },
                        enabled = topic.isNotBlank()
                    ) {
                        Text("Tạo Ngay 🤖")
                    }
                }
            }
        }
    }
}

@Composable
fun FlashcardsSessionDialog(
    wordViewModel: WordViewModel,
    onDismiss: () -> Unit
) {
    // Overload variant to properly manage nextCard state
    val reviewQueue by wordViewModel.reviewQueue.collectAsStateWithLifecycle()
    val allWords by wordViewModel.allWords.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val studyList = remember(reviewQueue, allWords) {
        if (reviewQueue.isNotEmpty()) reviewQueue else allWords.shuffled().take(5)
    }

    var currentIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (reviewQueue.isNotEmpty()) "Lặp lại ngắt quãng" else "Ôn tập ngẫu nhiên",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(text = "${currentIndex + 1}/${studyList.size}")
                }

                if (studyList.isEmpty()) {
                    Box(modifier = Modifier.height(180.dp), contentAlignment = Alignment.Center) {
                        Text("Kho từ vựng trống. Vui lòng thêm từ vựng trước nhé!")
                    }
                    Button(onClick = onDismiss) { Text("Đóng") }
                } else {
                    val currentWord = studyList[currentIndex]

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .clickable { isFlipped = !isFlipped },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isFlipped) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                            if (!isFlipped) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(currentWord.word, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text("Chạm để lật mặt sau 🔄", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(currentWord.pronunciation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(currentWord.meaning, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    if (currentWord.example.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(currentWord.example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(currentWord.translation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (isFlipped) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Grade buttons mapped with SM-2: 1 = Again, 2 = Hard, 3 = Good, 4 = Easy
                            val buttons = listOf(
                                Triple(1, "Quên", Color(0xFFE53935)),
                                Triple(2, "Khó", Color(0xFFFFA000)),
                                Triple(3, "Nhớ tốt", Color(0xFF4CAF50)),
                                Triple(4, "Dễ", MaterialTheme.colorScheme.primary)
                            )
                            buttons.forEach { (grade, label, color) ->
                                Button(
                                    onClick = {
                                        wordViewModel.gradeWord(currentWord.id, grade)
                                        isFlipped = false
                                        if (currentIndex + 1 < studyList.size) {
                                            currentIndex++
                                        } else {
                                            Toast.makeText(context, "Chúc mừng! Bạn đã hoàn thành buổi học hôm nay!", Toast.LENGTH_LONG).show()
                                            onDismiss()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = color),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(2.dp)
                                ) {
                                    Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        Text("Chạm vào thẻ để xem nghĩa & ví dụ.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

// --- GAMES SCREEN (10 fully interactive games inside a single elegant dashboard!) ---
@Composable
fun GamesScreen(gameViewModel: GameViewModel) {
    val gameState by gameViewModel.currentGameState.collectAsStateWithLifecycle()
    val score by gameViewModel.score.collectAsStateWithLifecycle()
    val earnedXp by gameViewModel.earnedXp.collectAsStateWithLifecycle()
    val earnedCoins by gameViewModel.earnedCoins.collectAsStateWithLifecycle()
    val timeLeft by gameViewModel.timeLeft.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (gameState == GameViewModel.GameState.Menu) {
            Text("Trò chơi trí tuệ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(
                "Luyện tập từ vựng mỗi ngày giúp bạn ghi nhớ nhanh hơn x10 lần!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable Grid of 10 Games
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Game list items
                item {
                    GameSelectionCard(
                        title = "1. Ghép Cặp Từ Vựng",
                        desc = "Nối từ tiếng Anh với nghĩa tiếng Việt tương ứng.",
                        emoji = "🔗",
                        onClick = { gameViewModel.startPairingGame() }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "2. Trắc Nghiệm 4 Lựa Chọn",
                        desc = "Chọn đáp án đúng nhất dựa trên nghĩa từ vựng.",
                        emoji = "🎯",
                        onClick = { gameViewModel.startMultipleChoiceGame(isListening = false) }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "3. Luyện Nghe Chọn Từ",
                        desc = "Nghe âm thanh phát âm mẫu (AI TTS) và tìm nghĩa phù hợp.",
                        emoji = "🎧",
                        onClick = { gameViewModel.startMultipleChoiceGame(isListening = true) }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "4. Điền Chữ Còn Thiếu",
                        desc = "Thử thách viết từ còn trống (ví dụ: a_p_e cho apple).",
                        emoji = "✏️",
                        onClick = { gameViewModel.startFillOrScrambleGame(isScramble = false) }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "5. Sắp Xếp Chữ Cái",
                        desc = "Sắp xếp lại các chữ cái bị xáo trộn để tạo từ hoàn chỉnh.",
                        emoji = "🔠",
                        onClick = { gameViewModel.startFillOrScrambleGame(isScramble = true) }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "6. Đấu Trường Tốc Độ",
                        desc = "Chạy đua với thời gian (30 giây) trả lời nhiều câu hỏi nhất!",
                        emoji = "⚡",
                        onClick = { gameViewModel.startSpeedQuizGame(30) }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "7. Lật Thẻ Tìm Cặp (Memory)",
                        desc = "Lật các thẻ bài tiếng Anh - tiếng Việt trùng khớp để ghi điểm.",
                        emoji = "🃏",
                        onClick = { gameViewModel.startMemoryCardsGame() }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "8. Hangman (Giải Cứu)",
                        desc = "Đoán từng chữ cái để giải cứu người treo cổ bí ẩn.",
                        emoji = "🕵️",
                        onClick = { gameViewModel.startHangmanGame() }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "9. Thử Thách 20 Câu (Quiz)",
                        desc = "Bộ đề trắc nghiệm ngẫu nhiên kiểm tra kiến thức toàn diện.",
                        emoji = "🏆",
                        onClick = { gameViewModel.startQuizChallenge() }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "10. Vocabulary Race (Đường Đua)",
                        desc = "Đường đua tích lũy huy hiệu, đổi phần thưởng Coin siêu khủng!",
                        emoji = "🏁",
                        onClick = { gameViewModel.startRaceGame() }
                    )
                }
                item {
                    GameSelectionCard(
                        title = "11. Luyện Phát Âm (AI Speech)",
                        desc = "Nói trực tiếp qua Micro của bạn để đối chiếu phát âm chuẩn bản xứ.",
                        emoji = "🗣️",
                        onClick = { gameViewModel.startPronunciationGame() }
                    )
                }
            }
        } else {
            // Active Game HUD Panel
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { gameViewModel.backToMenu() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null)
                }
                Text("Điểm số: $score ⭐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (gameState is GameViewModel.GameState.SpeedQuizGame) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("Còn lại: $timeLeft giây", color = Color.White, modifier = Modifier.padding(6.dp))
                    }
                } else {
                    Spacer(modifier = Modifier.width(40.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Game Workspace Screen Switcher
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (val state = gameState) {
                    is GameViewModel.GameState.PairingGame -> PairingGameLayout(gameViewModel)
                    is GameViewModel.GameState.MultipleChoiceGame -> MultipleChoiceGameLayout(gameViewModel, isListening = false)
                    is GameViewModel.GameState.ListeningGame -> MultipleChoiceGameLayout(gameViewModel, isListening = true)
                    is GameViewModel.GameState.FillBlanksGame -> TextInputGamesLayout(gameViewModel, isScramble = false)
                    is GameViewModel.GameState.WordScrambleGame -> TextInputGamesLayout(gameViewModel, isScramble = true)
                    is GameViewModel.GameState.SpeedQuizGame -> SpeedQuizGameLayout(gameViewModel)
                    is GameViewModel.GameState.MemoryCardsGame -> MemoryCardsGameLayout(gameViewModel)
                    is GameViewModel.GameState.HangmanGame -> HangmanGameLayout(gameViewModel)
                    is GameViewModel.GameState.QuizChallengeGame -> QuizChallengeLayout(gameViewModel)
                    is GameViewModel.GameState.RaceGame -> RaceGameLayout(gameViewModel)
                    is GameViewModel.GameState.PronunciationGame -> PronunciationGameLayout(gameViewModel)
                    is GameViewModel.GameState.GameOver -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("🎉", fontSize = 64.sp)
                            Text(state.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                state.message,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(onClick = { gameViewModel.backToMenu() }) {
                                Text("Về Trang Chủ Trò Chơi")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun GameSelectionCard(title: String, desc: String, emoji: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 24.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("➔", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// --- GAME LAYOUT 1: PAIRING GAME ---
@Composable
fun PairingGameLayout(viewModel: GameViewModel) {
    val items by viewModel.matchingPairs.collectAsStateWithLifecycle()
    val selected by viewModel.selectedPairItem.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text(
            "Ghép các cặp từ tương ứng bằng cách chọn từ tiếng Anh và nghĩa tương đương!",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(10.dp)
        ) {
            items(items) { item ->
                val isSelected = selected == item
                Card(
                    modifier = Modifier
                        .height(80.dp)
                        .clickable(enabled = !item.isMatched) { viewModel.selectPairItem(item) },
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            item.isMatched -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = when {
                            item.isMatched -> Color(0xFF4CAF50)
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = item.text,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.isMatched) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }
            }
        }
    }
}

// --- GAME LAYOUT 2 & 3: MULTIPLE CHOICE & LISTENING ---
@Composable
fun MultipleChoiceGameLayout(viewModel: GameViewModel, isListening: Boolean) {
    val questions by viewModel.quizQuestions.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()

    if (questions.isEmpty()) return

    val currentQ = questions[currentIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Câu hỏi ${currentIndex + 1}/${questions.size}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (isListening) {
            // Speak button and prompt
            Button(
                onClick = { viewModel.speak(currentQ.word.word) },
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Text("🔊", fontSize = 32.sp)
            }
            Text("Nghe kỹ phát âm mẫu và lựa chọn nghĩa chuẩn xác!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            // Word display
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentQ.word.word,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(currentQ.word.pronunciation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Options
        currentQ.options.forEach { option ->
            val isCorrect = option == currentQ.correctAnswer
            val isSelected = selectedOption == option

            val cardColor = when {
                selectedOption != null && isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                selectedOption != null && isSelected && !isCorrect -> Color(0xFFE53935).copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }

            val borderColor = when {
                selectedOption != null && isCorrect -> Color(0xFF4CAF50)
                selectedOption != null && isSelected && !isCorrect -> Color(0xFFE53935)
                else -> MaterialTheme.colorScheme.outlineVariant
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = selectedOption == null) { viewModel.submitOptionSelection(option) },
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, borderColor),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            selectedOption != null && isCorrect -> Color(0xFF4CAF50)
                            selectedOption != null && isSelected && !isCorrect -> Color(0xFFE53935)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }

        if (selectedOption != null) {
            Button(
                onClick = { viewModel.nextQuestion() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (currentIndex + 1 < questions.size) "Tiếp Tục ➔" else "Xem Kết Quả")
            }
        }
    }
}

// --- GAME LAYOUT 4 & 5: FILL IN THE BLANK & SCRAMBLE ---
@Composable
fun TextInputGamesLayout(viewModel: GameViewModel, isScramble: Boolean) {
    val currentWord by viewModel.currentWord.collectAsStateWithLifecycle()
    val scrambledLettersOrBlanks by viewModel.scrambledLettersOrBlanks.collectAsStateWithLifecycle()
    val userInput by viewModel.userInput.collectAsStateWithLifecycle()

    if (currentWord == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isScramble) "Sắp Xếp Chữ Cái" else "Điền Chữ Còn Thiếu",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )

        Card(
            modifier = Modifier.padding(10.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Gợi ý nghĩa:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = currentWord!!.meaning,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Display scramble sequence or blanks
        Text(
            text = scrambledLettersOrBlanks,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = userInput,
            onValueChange = { viewModel.setUserInput(it) },
            placeholder = { Text("Nhập từ vựng đáp án...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = { viewModel.checkTextInputAnswer() },
            modifier = Modifier.fillMaxWidth(),
            enabled = userInput.isNotBlank()
        ) {
            Text("Kiểm tra đáp án 🎯")
        }
    }
}

// --- GAME LAYOUT 6: SPEED QUIZ (RAPID-FIRE CHASE) ---
@Composable
fun SpeedQuizGameLayout(viewModel: GameViewModel) {
    val questions by viewModel.quizQuestions.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()

    if (questions.isEmpty()) return
    val currentQ = questions[currentIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Hãy trả lời nhanh nhất có thể!", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = currentQ.word.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        currentQ.options.forEach { option ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.submitSpeedAnswer(option) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Text(option, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- GAME LAYOUT 7: MEMORY CARD GRID ---
@Composable
fun MemoryCardsGameLayout(viewModel: GameViewModel) {
    val cards by viewModel.memoryCards.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Lật các thẻ bài tiếng Anh - Việt khớp với nhau để tìm kiếm các cặp tương thích!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(10.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(cards) { card ->
                val showContent = card.isFaceUp || card.isMatched
                Card(
                    modifier = Modifier
                        .height(90.dp)
                        .clickable(enabled = !card.isMatched && !card.isFaceUp) { viewModel.flipCard(card) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (showContent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (showContent) {
                            Text(card.text, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp))
                        } else {
                            Text("❓", fontSize = 24.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// --- GAME LAYOUT 8: HANGMAN ---
@Composable
fun HangmanGameLayout(viewModel: GameViewModel) {
    val wordObj by viewModel.currentWord.collectAsStateWithLifecycle()
    val guessed by viewModel.hangmanGuessedLetters.collectAsStateWithLifecycle()
    val wrongAttempts by viewModel.hangmanWrongAttempts.collectAsStateWithLifecycle()

    if (wordObj == null) return
    val word = wordObj!!.word.lowercase()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Giải cứu bí ẩn!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)

        // Visual hangman progress
        Card(
            modifier = Modifier.padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
        ) {
            Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Số lượt thử sai: $wrongAttempts/6 ❌", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(6.dp))
                // Simple representation of gallows
                val gallows = when (wrongAttempts) {
                    0 -> " |-----\n |\n |\n |\n |\n==="
                    1 -> " |-----\n |   O\n |\n |\n |\n==="
                    2 -> " |-----\n |   O\n |   |\n |\n |\n==="
                    3 -> " |-----\n |   O\n |  /|\n |\n |\n==="
                    4 -> " |-----\n |   O\n |  /|\\\n |\n |\n==="
                    5 -> " |-----\n |   O\n |  /|\\\n |  /\n |\n==="
                    else -> " |-----\n |   O\n |  /|\\\n |  / \\\n |\n==="
                }
                Text(gallows, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }

        // Hint meaning
        Text("Ý nghĩa từ cần đoán: ${wordObj!!.meaning}", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        // Word blanks representation
        val displayWord = word.map { char ->
            if (char.isLetter() && !guessed.contains(char)) '_' else char
        }.joinToString(" ")

        Text(displayWord, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)

        // Letter alphabet pad
        val alphabet = ('a'..'z').toList()
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            alphabet.forEach { char ->
                val isGuessed = guessed.contains(char)
                IconButton(
                    onClick = { viewModel.guessHangmanLetter(char) },
                    enabled = !isGuessed,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isGuessed) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Text(char.toString().uppercase(), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// --- GAME LAYOUT 9: QUIZ CHALLENGE (20 QUESTIONS) ---
@Composable
fun QuizChallengeLayout(viewModel: GameViewModel) {
    val questions by viewModel.quizQuestions.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    val selectedOption by viewModel.selectedOption.collectAsStateWithLifecycle()

    if (questions.isEmpty()) return
    val currentQ = questions[currentIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Bộ đề Thử thách 20 câu trắc nghiệm", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(
            progress = { (currentIndex + 1).toFloat() / questions.size },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Câu số ${currentIndex + 1}/${questions.size}", style = MaterialTheme.typography.labelMedium)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(currentQ.word.word, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }

        currentQ.options.forEach { option ->
            val isCorrect = option == currentQ.correctAnswer
            val isSelected = selectedOption == option

            val cardColor = when {
                selectedOption != null && isCorrect -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                selectedOption != null && isSelected && !isCorrect -> Color(0xFFE53935).copy(alpha = 0.2f)
                else -> MaterialTheme.colorScheme.surface
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = selectedOption == null) { viewModel.submitChallengeAnswer(option) },
                colors = CardDefaults.cardColors(containerColor = cardColor),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(14.dp)) {
                    Text(option, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedOption != null) {
            Button(
                onClick = { viewModel.nextChallengeQuestion() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Tiếp Tục ➔")
            }
        }
    }
}

// --- GAME LAYOUT 10: VOCABULARY RACE ---
@Composable
fun RaceGameLayout(viewModel: GameViewModel) {
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Đường đua thăng cấp 🏁", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text("Tích lũy XP từ các bài học để cán mốc huy hiệu, nhận ngay phần thưởng xu vàng vô cùng giá trị!", style = MaterialTheme.typography.bodySmall)

        // Progress bar indicators
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Kinh nghiệm hiện tại: ${profile.xp} XP ⭐", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (profile.xp.toFloat() / 1000f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )
            }
        }

        milestones.forEach { milestone ->
            val badgeUnlocked = profile.badges.split(",").contains("milestone_${milestone.id}")
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (milestone.isUnlocked) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (milestone.isUnlocked) "🏆" else "🔒", fontSize = 28.sp)
                        Column {
                            Text(milestone.name, fontWeight = FontWeight.Bold)
                            Text("Yêu cầu: ${milestone.requiredXp} XP", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    if (badgeUnlocked) {
                        Badge(containerColor = Color(0xFF4CAF50)) {
                            Text("Đã nhận thưởng", color = Color.White, modifier = Modifier.padding(6.dp))
                        }
                    } else if (milestone.isUnlocked) {
                        Button(
                            onClick = { viewModel.claimMilestoneReward(milestone) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA000))
                        ) {
                            Text("Nhận +${milestone.rewardCoins} Xu 🪙", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Badge(containerColor = MaterialTheme.colorScheme.outline) {
                            Text("Chưa mở khóa", modifier = Modifier.padding(6.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- CHAT AI SUPPORT SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatAiScreen(wordViewModel: WordViewModel, onNavigateToSettings: () -> Unit) {
    val messages by wordViewModel.chatMessages.collectAsStateWithLifecycle()
    val isLoading by wordViewModel.isChatLoading.collectAsStateWithLifecycle()
    val userApiKey by wordViewModel.userGeminiApiKey.collectAsStateWithLifecycle()
    
    var inputText by remember { mutableStateOf("") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()
    
    // Auto scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Trợ lý học tập AI 🤖",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = if (userApiKey.isBlank()) "Chưa cấu hình API Key trong Cài đặt" else "Đang sử dụng API Key cá nhân của bạn ⚡",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (userApiKey.isBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            
            // Clear history button
            IconButton(
                onClick = { wordViewModel.clearChatHistory() },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Text("🧹", fontSize = 18.sp)
            }
        }
        
        // Warnings if API Key is missing
        if (userApiKey.isBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "⚠️ Yêu cầu API Key để kích hoạt",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Để bảo mật và cho phép trò chuyện không giới hạn, tính năng Chat AI sử dụng API Key Gemini cá nhân của bạn (hoàn toàn miễn phí từ Google).",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Đi đến Cài đặt ⚙️", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
        
        // Chat list area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message)
            }
            
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🤖", fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "AI đang soạn câu trả lời...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Input area
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
            
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Hỏi về từ vựng, dịch câu, viết ví dụ...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() && !isLoading) {
                            wordViewModel.sendChatMessage(inputText.trim())
                            inputText = ""
                            keyboardController?.hide()
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            
            IconButton(
                onClick = {
                    if (inputText.isNotBlank() && !isLoading) {
                        wordViewModel.sendChatMessage(inputText.trim())
                        inputText = ""
                        keyboardController?.hide()
                    }
                },
                enabled = inputText.isNotBlank() && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.primary 
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
            ) {
                Text(
                    text = "✈️", 
                    fontSize = 18.sp,
                    color = if (inputText.isNotBlank() && !isLoading) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ChatBubble(message: com.example.ui.viewmodel.ChatMessage) {
    val isUser = message.isUser
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Text("🤖", fontSize = 20.sp, modifier = Modifier.padding(end = 6.dp, top = 4.dp))
        }
        
        Box(
            modifier = Modifier
                .widthIn(max = 270.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = if (isUser) 16.dp else 0.dp,
                        topEnd = if (isUser) 0.dp else 16.dp,
                        bottomStart = 16.dp,
                        bottomEnd = 16.dp
                    )
                )
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        if (isUser) {
            Text("👤", fontSize = 20.sp, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }
    }
}

// --- GRAMMAR DATA STRUCTURES ---
data class GrammarQuestion(
    val question: String,
    val options: List<String>,
    val correctAnswerIndex: Int,
    val explanation: String
)

data class GrammarTopic(
    val id: String,
    val title: String,
    val description: String,
    val level: String,
    val formula: String,
    val usage: String,
    val examples: List<Pair<String, String>>,
    val quiz: List<GrammarQuestion>
)

val defaultGrammarTopics = listOf(
    GrammarTopic(
        id = "present_simple",
        title = "Thì Hiện Tại Đơn (Present Simple)",
        description = "Thì cơ bản nhất dùng để diễn tả sự thật hiển nhiên, thói quen hoặc hành động lặp đi lặp lại.",
        level = "Beginner",
        formula = "Động từ thường:\n(+) S + V(s/es)\n(-) S + do/does + not + V_inf\n(?) Do/Does + S + V_inf?\n\nĐộng từ tobe:\n(+) S + am/is/are + N/Adj\n(-) S + am/is/are + not + N/Adj\n(?) Am/Is/Are + S + N/Adj?",
        usage = "• Diễn tả một chân lý, sự thật hiển nhiên (The sun rises in the east).\n• Diễn tả một thói quen hoặc hành động xảy ra thường xuyên ở hiện tại (I go to school every day).\n• Diễn tả lịch trình, thời khóa biểu đã có sẵn (The train leaves at 8 AM).",
        examples = listOf(
            "The earth rotates around the sun." to "Trái đất tự quay quanh mặt trời.",
            "He usually drinks coffee in the morning." to "Anh ấy thường uống cà phê vào buổi sáng.",
            "Water boils at 100 degrees Celsius." to "Nước sôi ở 100 độ C."
        ),
        quiz = listOf(
            GrammarQuestion(
                question = "She _______ (work) at a hospital.",
                options = listOf("work", "works", "working", "worked"),
                correctAnswerIndex = 1,
                explanation = "Chủ ngữ là ngôi thứ ba số ít 'She' nên động từ 'work' phải thêm 's' thành 'works' ở thì hiện tại đơn."
            ),
            GrammarQuestion(
                question = "Water _______ at 100 degrees Celsius.",
                options = listOf("boil", "boils", "boiling", "is boiling"),
                correctAnswerIndex = 1,
                explanation = "Nước sôi ở 100 độ C là một sự thật hiển nhiên/quy luật tự nhiên, dùng thì hiện tại đơn với chủ ngữ số ít 'Water' (danh từ không đếm được)."
            ),
            GrammarQuestion(
                question = "They _______ (not study) on Sundays.",
                options = listOf("don't study", "doesn't study", "not study", "aren't study"),
                correctAnswerIndex = 0,
                explanation = "Với chủ ngữ số nhiều 'They' trong câu phủ định thì hiện tại đơn, ta dùng trợ động từ 'don't' (do not) + động từ nguyên thể."
            )
        )
    ),
    GrammarTopic(
        id = "past_simple",
        title = "Thì Quá Khứ Đơn (Past Simple)",
        description = "Dùng để diễn tả một hành động đã xảy ra và kết thúc hoàn toàn trong quá khứ.",
        level = "Beginner",
        formula = "Động từ thường:\n(+) S + V-ed / V2\n(-) S + did + not + V_inf\n(?) Did + S + V_inf?\n\nĐộng từ tobe:\n(+) S + was/were + N/Adj\n(-) S + was/were + not + N/Adj\n(?) Was/Were + S + N/Adj?",
        usage = "• Diễn tả hành động xảy ra và kết thúc hoàn toàn trong quá khứ tại một mốc thời gian xác định (They bought a car yesterday).\n• Diễn tả một chuỗi hành động xảy ra liên tiếp trong quá khứ (She entered the room, turned on the light, and sat down).",
        examples = listOf(
            "I met my best friend ten years ago." to "Tôi đã gặp người bạn thân nhất của mình 10 năm trước.",
            "We watched an amazing movie last night." to "Chúng tôi đã xem một bộ phim tuyệt vời tối qua.",
            "He did not (didn't) call me last week." to "Anh ấy đã không gọi cho tôi tuần trước."
        ),
        quiz = listOf(
            GrammarQuestion(
                question = "We _______ (go) to Paris last summer.",
                options = listOf("go", "goes", "went", "gone"),
                correctAnswerIndex = 2,
                explanation = "'last summer' chỉ mốc thời gian trong quá khứ, ta dùng quá khứ đơn. Động từ bất quy tắc của 'go' ở cột 2 là 'went'."
            ),
            GrammarQuestion(
                question = "Where _______ you go yesterday?",
                options = listOf("do", "did", "was", "were"),
                correctAnswerIndex = 1,
                explanation = "Trong câu hỏi thì quá khứ đơn với động từ thường, ta cần dùng trợ động từ 'did' đặt trước chủ ngữ."
            )
        )
    ),
    GrammarTopic(
        id = "present_perfect",
        title = "Thì Hiện Tại Hoàn Thành (Present Perfect)",
        description = "Diễn tả hành động đã xảy ra trong quá khứ nhưng kết quả vẫn liên quan đến hiện tại.",
        level = "Intermediate",
        formula = "(+) S + have/has + V-ed / V3\n(-) S + have/has + not + V-ed / V3\n(?) Have/Has + S + V-ed / V3?",
        usage = "• Diễn tả hành động xảy ra trong quá khứ kéo dài đến hiện tại và có thể tiếp diễn trong tương lai (I have lived here for 5 years).\n• Diễn tả trải nghiệm, kinh nghiệm cho tới thời điểm hiện tại (I have never been to Japan).\n• Diễn tả hành động vừa mới xảy ra và để lại kết quả ở hiện tại (We have just finished dinner).",
        examples = listOf(
            "They have built a new school near my house." to "Họ vừa xây dựng một ngôi trường mới gần nhà tôi (không rõ thời gian cụ thể, nhấn mạnh kết quả hiện tại có trường mới).",
            "She has worked for this company since 2018." to "Cô ấy đã làm việc cho công ty này kể từ năm 2018 (và hiện tại vẫn đang làm).",
            "Have you ever seen a tiger?" to "Bạn đã bao giờ nhìn thấy một con hổ chưa? (Hỏi về trải nghiệm)."
        ),
        quiz = listOf(
            GrammarQuestion(
                question = "He _______ (write) three books so far.",
                options = listOf("write", "wrote", "has written", "is writing"),
                correctAnswerIndex = 2,
                explanation = "Dấu hiệu 'so far' (cho đến nay) chỉ một hành động kéo dài từ quá khứ đến hiện tại, chia ở thì hiện tại hoàn thành. Chủ ngữ 'He' đi với 'has' và V3 là 'written'."
            ),
            GrammarQuestion(
                question = "I have lived here _______ 2015.",
                options = listOf("for", "since", "in", "ago"),
                correctAnswerIndex = 1,
                explanation = "Ta dùng 'since' trước một mốc thời gian bắt đầu hành động (2015), còn 'for' dùng trước một khoảng thời gian."
            )
        )
    ),
    GrammarTopic(
        id = "passive_voice",
        title = "Thế Bị Động (Passive Voice)",
        description = "Dùng khi muốn nhấn mạnh vào đối tượng chịu tác động của hành động thay vì người thực hiện.",
        level = "Intermediate",
        formula = "Chủ động: S + V + O  ==>  Bị động: S (mới) + be + V-ed / V3 (+ by O mới)",
        usage = "• Khi người thực hiện hành động không quan trọng, không rõ là ai hoặc đã quá hiển nhiên.\n• Để tăng tính trang trọng, khách quan cho câu văn.",
        examples = listOf(
            "This room is cleaned every day." to "Căn phòng này được dọn dẹp mỗi ngày.",
            "The telephone was invented by Alexander Graham Bell." to "Điện thoại được phát minh bởi Alexander Graham Bell.",
            "My bicycle has been stolen!" to "Chiếc xe đạp của tôi đã bị trộm mất rồi!"
        ),
        quiz = listOf(
            GrammarQuestion(
                question = "English _______ (speak) all over the world.",
                options = listOf("speaks", "is spoken", "was spoken", "speaking"),
                correctAnswerIndex = 1,
                explanation = "Tiếng Anh không tự nói được mà phải được nói (bị động). Ở hiện tại đơn bị động, ta dùng 'is/am/are + V3', do đó 'is spoken' là đáp án đúng."
            ),
            GrammarQuestion(
                question = "The package _______ (deliver) yesterday.",
                options = listOf("was delivered", "delivered", "is delivered", "has delivered"),
                correctAnswerIndex = 0,
                explanation = "Câu bị động ở thì quá khứ đơn (có từ 'yesterday') dùng cấu trúc 'was/were + V3'. 'The package' là số ít nên dùng 'was delivered'."
            )
        )
    ),
    GrammarTopic(
        id = "relative_clauses",
        title = "Mệnh Đề Quan Hệ (Relative Clauses)",
        description = "Mệnh đề bổ trợ thông tin cho danh từ đứng trước bằng các đại từ quan hệ Who, Whom, Which, That, Whose.",
        level = "Advanced",
        formula = "N (người) + WHO / WHOM / WHOSE + S + V\nN (vật) + WHICH / THAT + S + V",
        usage = "• WHO: thay thế cho danh từ chỉ người đóng vai trò chủ ngữ.\n• WHOM: thay thế cho danh từ chỉ người đóng vai trò tân ngữ.\n• WHICH: thay thế cho danh từ chỉ vật.\n• THAT: thay thế cho cả người lẫn vật trong mệnh đề xác định.\n• WHOSE: chỉ sự sở hữu của người hoặc vật.",
        examples = listOf(
            "The man who called you yesterday is my uncle." to "Người đàn ông gọi cho bạn hôm qua là chú của tôi.",
            "The book which you lent me is very interesting." to "Cuốn sách bạn cho tôi mượn rất thú vị.",
            "She is the girl whose dog was lost." to "Cô ấy là cô gái có chú chó bị thất lạc."
        ),
        quiz = listOf(
            GrammarQuestion(
                question = "The woman _______ lives next door is a doctor.",
                options = listOf("whom", "which", "who", "whose"),
                correctAnswerIndex = 2,
                explanation = "Đại từ quan hệ bổ nghĩa cho danh từ chỉ người 'The woman' làm chủ ngữ trong mệnh đề quan hệ, do đó ta dùng 'who'."
            ),
            GrammarQuestion(
                question = "The car _______ he bought is very expensive.",
                options = listOf("who", "which", "whose", "whom"),
                correctAnswerIndex = 1,
                explanation = "Đại từ quan hệ bổ nghĩa cho danh từ chỉ vật 'The car' nên ta dùng 'which'."
            )
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarScreen(wordViewModel: WordViewModel) {
    val completedTopics by wordViewModel.completedGrammarTopics.collectAsStateWithLifecycle()
    val isChecking by wordViewModel.isGrammarChecking.collectAsStateWithLifecycle()
    val checkResult by wordViewModel.grammarCheckResult.collectAsStateWithLifecycle()
    val userApiKey by wordViewModel.userGeminiApiKey.collectAsStateWithLifecycle()

    var activeSubTab by remember { mutableStateOf("theory") } // "theory" or "ai_checker"
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf("All") }
    var selectedDetailTopic by remember { mutableStateOf<GrammarTopic?>(null) }
    
    // For AI Grammar checker input
    var userInputSentence by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        Text(
            text = "Ngữ pháp tiếng Anh 📝",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black
        )

        // Tab selection (Pill style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("theory" to "📚 Lý thuyết & Quiz", "ai_checker" to "🤖 AI Sửa lỗi").forEach { (tab, label) ->
                val isSelected = activeSubTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeSubTab = tab }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Contents based on active sub tab
        if (activeSubTab == "theory") {
            // SEARCH BAR
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Tìm kiếm bài học ngữ pháp...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // LEVEL FILTER CHIPS
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val levels = listOf("All", "Beginner", "Intermediate", "Advanced")
                items(levels) { lvl ->
                    val isSelected = selectedLevel == lvl
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedLevel = lvl },
                        label = { Text(lvl) }
                    )
                }
            }

            // LIST OF TOPICS
            val filteredTopics = defaultGrammarTopics.filter { topic ->
                (selectedLevel == "All" || topic.level == selectedLevel) &&
                (searchQuery.isBlank() || topic.title.contains(searchQuery, ignoreCase = true) || topic.description.contains(searchQuery, ignoreCase = true))
            }

            if (filteredTopics.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Không tìm thấy chủ đề phù hợp 📭", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredTopics) { topic ->
                        val isCompleted = completedTopics.contains(topic.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDetailTopic = topic },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Badge(containerColor = when (topic.level) {
                                            "Beginner" -> MaterialTheme.colorScheme.primaryContainer
                                            "Intermediate" -> MaterialTheme.colorScheme.secondaryContainer
                                            else -> MaterialTheme.colorScheme.tertiaryContainer
                                        }) {
                                            Text(
                                                text = topic.level,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }

                                        if (isCompleted) {
                                            Badge(containerColor = Color(0xFFE8F5E9)) {
                                                Text(
                                                    text = "Đã học ✅",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color(0xFF2E7D32),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { wordViewModel.toggleGrammarTopicCompleted(topic.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Text(if (isCompleted) "💚" else "🤍", fontSize = 16.sp)
                                    }
                                }

                                Text(
                                    text = topic.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Text(
                                    text = topic.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { selectedDetailTopic = topic }) {
                                        Text("Học ngay 📖 ➡️", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // AI GRAMMAR CHECKER TAB
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        text = "Trợ lý sửa lỗi ngữ pháp AI 🤖",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Nhập hoặc dán câu tiếng Anh cần kiểm tra lỗi ngữ pháp, chính tả hay cách hành văn bên dưới. Trí tuệ nhân tạo Gemini sẽ ngay lập tức phát hiện lỗi sai, đưa ra cách sửa và giải thích chi tiết cho bạn!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                item {
                    OutlinedTextField(
                        value = userInputSentence,
                        onValueChange = { userInputSentence = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .testTag("grammar_check_input"),
                        placeholder = { Text("Nhập câu ví dụ: She don't like playing games...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                item {
                    // Predefined error sentences for quick testing
                    Text("Câu lỗi sai mẫu (Click để thử nhanh):", style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "He go to school yesterday." to "Quá khứ",
                            "I has lived here since 3 years." to "Hoàn thành",
                            "She don't likes apples." to "Ngôi thứ 3"
                        ).forEach { (sentence, tag) ->
                            AssistChip(
                                onClick = { userInputSentence = sentence },
                                label = { Text("$tag: \"$sentence\"") }
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { 
                                if (userInputSentence.isNotBlank()) {
                                    wordViewModel.checkGrammar(userInputSentence.trim())
                                } else {
                                    Toast.makeText(context, "Vui lòng nhập câu cần kiểm tra!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = userInputSentence.isNotBlank() && !isChecking,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isChecking) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                    Text("AI đang kiểm tra...", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Kiểm tra ngữ pháp 🔍", fontWeight = FontWeight.Bold)
                            }
                        }

                        if (checkResult.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { 
                                    wordViewModel.clearGrammarCheckResult()
                                    userInputSentence = ""
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Xóa 🧹")
                            }
                        }
                    }
                }

                if (checkResult.isNotEmpty()) {
                    item {
                        if (checkResult == "API_KEY_MISSING") {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("⚠️ Yêu cầu cấu hình API Key", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = "Bạn cần lưu API Key Gemini cá nhân trong tab Cài đặt để sử dụng tính năng Sửa lỗi ngữ pháp thông minh hoàn toàn miễn phí.",
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📝 Kết quả phân tích từ AI", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                        IconButton(onClick = {
                                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                            val clip = android.content.ClipData.newPlainText("grammar_analysis", checkResult)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Đã sao chép phân tích!", Toast.LENGTH_SHORT).show()
                                        }) {
                                            Text("📋", fontSize = 16.sp)
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))

                                    // Display checkResult
                                    Text(
                                        text = checkResult,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // GRAMMAR TOPIC DETAIL DIALOG (INCLUDES INTERACTIVE QUIZ)
    if (selectedDetailTopic != null) {
        GrammarTopicDetailDialog(
            topic = selectedDetailTopic!!,
            wordViewModel = wordViewModel,
            completedTopics = completedTopics,
            onDismiss = { selectedDetailTopic = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarTopicDetailDialog(
    topic: GrammarTopic,
    wordViewModel: WordViewModel,
    completedTopics: Set<String>,
    onDismiss: () -> Unit
) {
    val isCompleted = completedTopics.contains(topic.id)
    val scrollState = rememberScrollState()

    // Quiz states
    var isQuizMode by remember { mutableStateOf(false) }
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedOptionIndex by remember { mutableStateOf(-1) }
    var showQuizExplanation by remember { mutableStateOf(false) }
    var quizScore by remember { mutableStateOf(0) }
    var quizFinished by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isQuizMode) "✏️ Luyện tập Quiz" else "📚 Học Lý thuyết",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(10.dp))

                HorizontalDivider()

                Spacer(modifier = Modifier.height(10.dp))

                if (!isQuizMode) {
                    // THEORY CONTENT VIEW
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Formula box
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("📋 CÔNG THỨC (FORMULA)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = topic.formula,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                                    lineHeight = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Usage section
                        Column {
                            Text("💡 CÁCH DÙNG (USAGE)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = topic.usage,
                                style = MaterialTheme.typography.bodyMedium,
                                lineHeight = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Examples list with sound play buttons
                        Column {
                            Text("🌟 VÍ DỤ MINH HỌA (EXAMPLES)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(6.dp))
                            topic.examples.forEach { (eng, vie) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(eng, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                        Text(vie, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    IconButton(
                                        onClick = { wordViewModel.speak(eng) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Text("🔊", fontSize = 16.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Learning complete toggler row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Đánh dấu hoàn thành bài học", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Switch(
                                checked = isCompleted,
                                onCheckedChange = { wordViewModel.toggleGrammarTopicCompleted(topic.id) }
                            )
                        }
                    }

                    // Bottom action row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Đóng")
                        }

                        Button(
                            onClick = { 
                                isQuizMode = true
                                currentQuestionIndex = 0
                                selectedOptionIndex = -1
                                showQuizExplanation = false
                                quizScore = 0
                                quizFinished = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Làm Quiz ✏️", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // QUIZ MODE VIEW
                    Column(
                        modifier = Modifier
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!quizFinished) {
                            val currentQuestion = topic.quiz[currentQuestionIndex]

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Câu ${currentQuestionIndex + 1}/${topic.quiz.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Điểm: $quizScore",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }

                            LinearProgressIndicator(
                                progress = (currentQuestionIndex.toFloat() / topic.quiz.size),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Text(
                                text = currentQuestion.question,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )

                            // Quiz options
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                currentQuestion.options.forEachIndexed { index, option ->
                                    val isSelected = selectedOptionIndex == index
                                    val isCorrect = currentQuestion.correctAnswerIndex == index

                                    val optionColor = when {
                                        showQuizExplanation && isCorrect -> Color(0xFFC8E6C9) // Green for correct answer
                                        showQuizExplanation && isSelected && !isCorrect -> Color(0xFFFFCDD2) // Red for selected wrong answer
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    }

                                    val borderColor = when {
                                        showQuizExplanation && isCorrect -> Color(0xFF4CAF50)
                                        showQuizExplanation && isSelected && !isCorrect -> Color(0xFFF44336)
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(optionColor)
                                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                            .clickable(enabled = !showQuizExplanation) {
                                                selectedOptionIndex = index
                                            }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = option,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )

                                        if (showQuizExplanation) {
                                            if (isCorrect) {
                                                Text("✅", fontSize = 16.sp)
                                            } else if (isSelected) {
                                                Text("❌", fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }

                            if (selectedOptionIndex != -1 && !showQuizExplanation) {
                                Button(
                                    onClick = {
                                        showQuizExplanation = true
                                        if (selectedOptionIndex == currentQuestion.correctAnswerIndex) {
                                            quizScore += 1
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Kiểm tra đáp án")
                                }
                            }

                            if (showQuizExplanation) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("💡 GIẢI THÍCH (EXPLANATION):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(currentQuestion.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Button(
                                        onClick = {
                                            if (currentQuestionIndex + 1 < topic.quiz.size) {
                                                currentQuestionIndex += 1
                                                selectedOptionIndex = -1
                                                showQuizExplanation = false
                                            } else {
                                                quizFinished = true
                                                // Award some XP/Coins if they score high!
                                                if (quizScore == topic.quiz.size && !isCompleted) {
                                                    wordViewModel.toggleGrammarTopicCompleted(topic.id)
                                                }
                                            }
                                        }
                                    ) {
                                        Text(if (currentQuestionIndex + 1 < topic.quiz.size) "Câu tiếp theo ➡️" else "Xem kết quả 🏁")
                                    }
                                }
                            }
                        } else {
                            // QUIZ FINISHED VIEW
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 30.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("🎉 Hoàn thành Quiz! 🎉", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Số câu trả lời đúng:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "$quizScore / ${topic.quiz.size}",
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (quizScore == topic.quiz.size) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (quizScore == topic.quiz.size) "Tuyệt vời! Bạn đã đạt điểm tối đa! 🌟" else "Hãy thử lại để đạt kết quả tốt hơn nhé!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(40.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { isQuizMode = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Học lý thuyết 📚")
                                    }

                                    Button(
                                        onClick = {
                                            currentQuestionIndex = 0
                                            selectedOptionIndex = -1
                                            showQuizExplanation = false
                                            quizScore = 0
                                            quizFinished = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Làm lại Quiz 🔄")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- STATISTICS SCREEN ---
@Composable
fun StatsScreen(wordViewModel: WordViewModel) {
    val words by wordViewModel.allWords.collectAsStateWithLifecycle()
    val profile by wordViewModel.userProfile.collectAsStateWithLifecycle()

    val total = words.size
    val learned = words.count { it.learnedStatus == "MASTERED" }
    val learning = words.count { it.learnedStatus == "LEARNING" || it.learnedStatus == "REVIEW" }
    val newWords = words.count { it.learnedStatus == "NEW" }

    // Grouping by topics
    val topicsCount = words.groupBy { it.topic }
        .mapValues { entry -> entry.value.size }
        .toList()
        .sortedByDescending { it.second }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Thống kê học tập", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

        // General counters cards row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tổng số từ", style = MaterialTheme.typography.labelSmall)
                    Text("$total", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Đã thuộc lòng", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                    Text("$learned", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
            }
            Card(modifier = Modifier.weight(1f)) {
                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Đang luyện", style = MaterialTheme.typography.labelSmall)
                    Text("$learning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Streak & XP High Density panel
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 36.sp)
                    Column {
                        Text("Chuỗi ngày học liên tục", fontWeight = FontWeight.Bold)
                        Text("Tiến độ đều đặn mỗi ngày", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("${profile.streak} Ngày", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            }
        }

        // Topics progression charts bars
        Text("Tiến độ theo Chủ đề", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
        
        if (topicsCount.isEmpty()) {
            Text("Không có dữ liệu chủ đề.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        } else {
            topicsCount.take(5).forEach { (topic, count) ->
                // Calculate percentage of total
                val percent = if (total > 0) count.toFloat() / total else 0f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(topic, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text("$count từ", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { percent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }
}

// --- IMPORT CSV/EXCEL SCREEN ---
fun downloadCsvTemplateToDownloads(context: android.content.Context) {
    try {
        val content = """
apple | /ˈæp.əl/ | quả táo | An apple a day keeps the doctor away. | Mỗi ngày một quả táo giúp tránh xa bác sĩ. | Fruit | A1
banana | /bəˈnæn.ə/ | quả chuối | Monkeys like to eat bananas. | Khỉ thích ăn chuối. | Fruit | A1
car | /kɑːr/ | xe hơi | He drives a new car. | Anh ấy lái một chiếc xe hơi mới. | Transport | A1
""".trimIndent()

        val filename = "tu_vung_mau.txt"
        val mimeType = "text/plain"

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(content.toByteArray(java.nio.charset.Charset.forName("UTF-8")))
                }
                android.widget.Toast.makeText(context, "Đã lưu file mẫu vào mục Tải xuống: Downloads/$filename", android.widget.Toast.LENGTH_LONG).show()
            } else {
                throw Exception("Không thể tạo URI trong bộ nhớ Downloads.")
            }
        } else {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, filename)
            file.writeText(content, java.nio.charset.Charset.forName("UTF-8"))
            android.widget.Toast.makeText(context, "Đã lưu file mẫu vào mục Tải xuống: Downloads/$filename", android.widget.Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Không thể tải file mẫu: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
    }
}

@Composable
fun ImportScreen(wordViewModel: WordViewModel, modifier: Modifier = Modifier) {
    var textInput by remember { mutableStateOf("") }
    var importResultState by remember { mutableStateOf<ImportResult?>(null) }
    val context = LocalContext.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Nhập từ vựng Excel / CSV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(
            "Bạn có thể dễ dàng nhập hàng loạt từ vựng từ file Excel hoặc CSV bằng cách sao chép dữ liệu dạng dòng phân tách bởi dấu gạch đứng | theo mẫu cấu trúc dưới đây:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Download Template Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Tải File Excel/CSV Mẫu 📥",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Tải file định dạng đúng cấu trúc về máy của bạn để biên soạn từ vựng tiện lợi bằng Excel hoặc Google Sheets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            downloadCsvTemplateToDownloads(context)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tải file mẫu 💾", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText(
                                "CSV Template",
                                """
apple | /ˈæp.əl/ | quả táo | An apple a day keeps the doctor away. | Mỗi ngày một quả táo giúp tránh xa bác sĩ. | Fruit | A1
banana | /bəˈnæn.ə/ | quả chuối | Monkeys like to eat bananas. | Khỉ thích ăn chuối. | Fruit | A1
car | /kɑːr/ | xe hơi | He drives a new car. | Anh ấy lái một chiếc xe hơi mới. | Transport | A1
""".trimIndent()
                            )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Đã sao chép cấu trúc mẫu vào Clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Sao chép mẫu 📋", fontSize = 13.sp)
                    }
                }
            }
        }

        // Mock format guide block
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Định dạng cấu trúc dòng:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text(
                    "Từ vựng | Phiên âm (IPA) | Nghĩa tiếng Việt | Câu ví dụ | Dịch ví dụ | Chủ đề | Trình độ",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("Ví dụ thực tế:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                Text(
                    "banana | /bəˈnæn.ə/ | quả chuối | Monkeys like to eat bananas. | Khỉ thích ăn chuối. | Fruit | A1",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        OutlinedTextField(
            value = textInput,
            onValueChange = { textInput = it },
            placeholder = { Text("Dán các dòng từ vựng vào đây...\nVí dụ:\napple | /ˈæpəl/ | quả táo | An apple a day | Mỗi ngày một quả táo | Food\ncar | /kɑːr/ | xe hơi | He has a car | Anh ấy có xe hơi | Transport") },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            maxLines = 15
        )

        Button(
            onClick = {
                if (textInput.isNotBlank()) {
                    val result = wordViewModel.importFromCsvText(textInput)
                    importResultState = result
                    if (result.errorMsg == null) {
                        textInput = ""
                        Toast.makeText(context, "Nhập dữ liệu thành công!", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = textInput.isNotBlank()
        ) {
            Text("Nhập dữ liệu ngay 📂")
        }

        // Display results
        importResultState?.let { res ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kết quả Import dữ liệu:", fontWeight = FontWeight.Bold)
                    Text("✅ Nhập thành công: ${res.success} từ mới")
                    Text("⚠️ Bị trùng lặp: ${res.duplicates} từ đã bỏ qua")
                    Text("❌ Dòng bị lỗi: ${res.errors}")
                    if (res.errorMsg != null) {
                        Text("Lỗi: ${res.errorMsg}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// --- SETTINGS / API CONFIGURATION ---
@Composable
fun SettingsScreen(wordViewModel: WordViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentVersion by wordViewModel.appVersion.collectAsStateWithLifecycle()
    
    val userApiKey by wordViewModel.userGeminiApiKey.collectAsStateWithLifecycle()
    var apiKeyInput by remember(userApiKey) { mutableStateOf(userApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Cài đặt hệ thống", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)

        // API settings informational row
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cấu hình AI Gemini 🤖", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ứng dụng English Master tích hợp trí tuệ nhân tạo Gemini 3.5 Flash để hỗ trợ dịch thuật, sinh ví dụ, đề xuất từ vựng và đàm thoại trực tiếp tại tab Chat AI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Mã API Key Gemini cá nhân") },
                    placeholder = { Text("Nhập mã bắt đầu bằng AIzaSy...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("user_api_key_input"),
                    visualTransformation = if (showApiKey) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Text(if (showApiKey) "👁️" else "🙈")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (apiKeyInput.isNotBlank()) {
                                wordViewModel.updateUserGeminiApiKey(apiKeyInput.trim())
                                Toast.makeText(context, "Đã lưu API Key thành công! 🎉", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Vui lòng nhập API Key trước khi lưu!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Lưu API Key 💾", fontWeight = FontWeight.Bold)
                    }
                    
                    OutlinedButton(
                        onClick = {
                            wordViewModel.updateUserGeminiApiKey("")
                            apiKeyInput = ""
                            Toast.makeText(context, "Đã xóa API Key thành công!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Xóa API Key ❌", fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider()
                
                Text(
                    "Hướng dẫn lấy API Key Gemini miễn phí 📋",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                uriHandler.openUri("https://aistudio.google.com/")
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1. Nhấp vào đây để mở: Google AI Studio 🔗",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "2. Đăng nhập bằng tài khoản Google của bạn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "3. Click chọn nút 'Get API key' ở góc trái màn hình.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "4. Click 'Create API key', chọn tạo khóa mới cho dự án của bạn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "5. Sao chép đoạn mã (chuỗi chữ số bắt đầu bằng AIzaSy...) và dán vào ô ở trên rồi nhấp 'Lưu'.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🔒 Bảo mật: API Key của bạn chỉ được lưu cục bộ trên bộ nhớ máy, đảm bảo an toàn tuyệt đối và không tải lên bất kỳ máy chủ bên ngoài nào.",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Theme mode selection card (Light / Dark / System)
        val selectedMode by wordViewModel.themeMode.collectAsStateWithLifecycle()
        val modes = listOf(
            Triple("light", "Giao diện Sáng ☀️", "Trải nghiệm sáng sủa, dễ nhìn dưới ánh sáng mạnh."),
            Triple("dark", "Giao diện Tối 🌙", "Dịu mắt khi học tập ban đêm và tối ưu pin."),
            Triple("system", "Theo Hệ Thống 📱", "Tự động đồng bộ với chế độ hiển thị của điện thoại.")
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Chế độ hiển thị 🌓", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tùy chỉnh chế độ hiển thị sáng hoặc tối cho phù hợp với sở thích của bạn.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                modes.forEach { (modeId, modeName, modeDesc) ->
                    val isSelected = selectedMode == modeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable {
                                wordViewModel.updateThemeMode(modeId)
                                Toast.makeText(context, "Đã đổi sang $modeName!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = modeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                text = modeDesc,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { 
                                wordViewModel.updateThemeMode(modeId)
                                Toast.makeText(context, "Đã đổi sang $modeName!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // Theme color selection card
        val selectedTheme by wordViewModel.selectedTheme.collectAsStateWithLifecycle()
        val themes = listOf(
            Triple("blue", "Xanh Đại Dương (Ocean Blue) 💙", Color(0xFF1565C0)),
            Triple("green", "Ngọc Lục Bảo (Forest Green) 💚", Color(0xFF2E7D32)),
            Triple("purple", "Tím Hoàng Gia (Sunset Amethyst) 💜", Color(0xFF6A1B9A)),
            Triple("orange", "Cam Nhiệt Huyết (Solar Orange) 🧡", Color(0xFFD84315))
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tông màu chủ đạo 🎨", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tùy chỉnh màu sắc chính của ứng dụng để tăng cảm hứng học tập.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider()
                themes.forEach { (themeId, themeName, themeColor) ->
                    val isSelected = selectedTheme == themeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable {
                                wordViewModel.updateTheme(themeId)
                                Toast.makeText(context, "Đã đổi tông màu sang $themeName!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Color Circle Preview
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(themeColor)
                        )
                        Text(
                            text = themeName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { 
                                wordViewModel.updateTheme(themeId)
                                Toast.makeText(context, "Đã đổi tông màu sang $themeName!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // Embedded Import Card (phần nhập đưa vào cài đặt - Luôn hiển thị đầy đủ)
        Card(
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("📂", fontSize = 28.sp)
                    Column {
                        Text("Nhập từ vựng hàng loạt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Thêm hàng trăm từ vựng qua file Excel/CSV mẫu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                HorizontalDivider()
                
                ImportScreen(wordViewModel = wordViewModel)
            }
        }

        // --- FEATURE: APP VERSION & UPDATE MANAGEMENT ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("app_version_card"),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔄", fontSize = 28.sp)
                    Column {
                        Text("Phiên bản & Cập nhật", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Quản lý phiên bản hệ thống và nhận tính năng mới", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                
                HorizontalDivider()
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Phiên bản hiện tại", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currentVersion, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    
                    Badge(
                        containerColor = if (currentVersion == "v2.5.0") Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = if (currentVersion == "v2.5.0") "Mới nhất ✅" else "Có bản cập nhật mới 🆕",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (currentVersion == "v2.5.0") Color(0xFF2E7D32) else MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Button(
                    onClick = {
                        isCheckingUpdate = true
                        scope.launch {
                            kotlinx.coroutines.delay(1500) // Simulating checking updates from server
                            isCheckingUpdate = false
                            showUpdateDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_update_button"),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isCheckingUpdate
                ) {
                    if (isCheckingUpdate) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text("Đang kết nối server...", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text("Kiểm tra cập nhật ngay 🔄", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dangerous Zone resets
        Text("Vùng nguy hiểm ⚠️", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)

        Button(
            onClick = {
                wordViewModel.deleteAllWords()
                Toast.makeText(context, "Đã xóa toàn bộ từ vựng trong kho của bạn!", Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Xóa Toàn Bộ Kho Từ Vựng 🗑️", fontWeight = FontWeight.Bold)
        }
    }

    if (showUpdateDialog) {
        val latestVersion = "v2.5.0"
        Dialog(onDismissRequest = { if (!isDownloading) showUpdateDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = if (currentVersion == latestVersion) "✨ Bạn Đang Ở Bản Mới Nhất!" else "🎉 Có Phiên Bản Mới v2.5.0!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (currentVersion != latestVersion) {
                        Text(
                            text = "Bản phát hành: $latestVersion (Bản của bạn: $currentVersion)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        HorizontalDivider()
                        
                        Text("🚀 Tính năng mới trong bản v2.5.0:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "🔊 Nâng cấp giọng đọc AI chuẩn bản xứ cực kỳ sinh động.",
                                "📝 Hệ thống phân tích học & sửa lỗi ngữ pháp thông minh cùng AI.",
                                "📊 Bảng phân tích thống kê chi tiết tiến độ học cá nhân hóa.",
                                "⚡ Tối ưu cơ sở dữ liệu giúp tốc độ ứng dụng nhanh hơn 40%."
                            ).forEach { feature ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = feature,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        if (isDownloading) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = downloadProgress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Đang tải gói cập nhật...", style = MaterialTheme.typography.labelSmall)
                                    Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { showUpdateDialog = false },
                                    modifier = Modifier.testTag("cancel_update_button")
                                ) {
                                    Text("Hủy")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        downloadProgress = 0f
                                        scope.launch {
                                            // Simulate smooth download bar
                                            for (i in 1..20) {
                                                kotlinx.coroutines.delay(100)
                                                downloadProgress = i / 20f
                                            }
                                            isDownloading = false
                                            wordViewModel.performUpdateTo(latestVersion)
                                            Toast.makeText(context, "Hệ thống đã cập nhật thành công lên bản v2.5.0! 🎉 Bạn được tặng +100 XP & +20 Coins!", Toast.LENGTH_LONG).show()
                                            showUpdateDialog = false
                                        }
                                    },
                                    modifier = Modifier.testTag("update_now_button"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Cập nhật ngay 🚀")
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Hệ thống của bạn đang hoạt động trên phiên bản v2.5.0 mới nhất với đầy đủ các nâng cấp về bảo mật, trí tuệ nhân tạo Gemini và hiệu năng.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                        
                        HorizontalDivider()
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🛡️", fontSize = 24.sp)
                            Text(
                                "Bạn đang nhận được sự bảo vệ và dịch vụ tối ưu tốt nhất từ máy chủ.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        Button(
                            onClick = { showUpdateDialog = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Đóng")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PronunciationGameLayout(gameViewModel: GameViewModel) {
    val quizQuestions by gameViewModel.quizQuestions.collectAsStateWithLifecycle()
    val currentIndex by gameViewModel.currentQuestionIndex.collectAsStateWithLifecycle()
    
    if (quizQuestions.isEmpty() || currentIndex >= quizQuestions.size) {
        return
    }
    
    val currentQuestion = quizQuestions[currentIndex]
    val currentWord = currentQuestion.word
    
    var hasSucceeded by remember(currentIndex) { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Card header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Câu hỏi ${currentIndex + 1}/${quizQuestions.size}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    text = currentWord.topic,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        // Giant Word Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = currentWord.word,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = currentWord.pronunciation,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
                
                Text(
                    text = currentWord.meaning,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Audio Speaker
                FilledIconButton(
                    onClick = { gameViewModel.speak(currentWord.word) },
                    modifier = Modifier.size(56.dp)
                ) {
                    Text("🔊", fontSize = 24.sp)
                }
                
                Text(
                    text = "Hãy nghe phát âm mẫu trước khi luyện tập!",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        // Embedded Pronunciation Checker
        PronunciationChecker(
            targetWord = currentWord.word,
            onCorrectPronunciation = {
                if (!hasSucceeded) {
                    hasSucceeded = true
                    gameViewModel.submitPronunciationSuccess()
                }
            }
        )
        
        // Next button appears when correct
        if (hasSucceeded) {
            Button(
                onClick = { gameViewModel.nextPronunciationQuestion() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tuyệt Vời! Tiếp Tục ➔", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
