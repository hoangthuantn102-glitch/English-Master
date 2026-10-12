package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothDeviceItem
import com.example.bluetooth.BluetoothDuelManager
import com.example.bluetooth.ConnectionStatus
import com.example.data.AppDatabase
import com.example.data.UserProfile
import com.example.data.Word
import com.example.data.WordRepository
import com.example.util.DuelSoundEffectManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject

enum class DuelGameMode(
    val title: String,
    val subtitle: String,
    val icon: String,
    val badge: String,
    val description: String
) {
    FAST_CHOICE(
        title = "Đoán Nghĩa Siêu Tốc",
        subtitle = "Từ tiếng Anh ➔ Chọn nghĩa tiếng Việt",
        icon = "⚡",
        badge = "TỐC ĐỘ",
        description = "Chớp nhoáng tìm đúng nghĩa của từ tiếng Anh. Đòn đánh sấm sét nếu trả lời dưới 3 giây!"
    ),
    REVERSE_MEANING(
        title = "Nghịch Đảo Từ Vựng",
        subtitle = "Nghĩa tiếng Việt ➔ Chọn từ tiếng Anh",
        icon = "🔄",
        badge = "PHẢN XẠ",
        description = "Thử thách phản xạ ngược: Nhìn nghĩa tiếng Việt, truy tìm từ tiếng Anh chuẩn xác nhất."
    ),
    TRUE_FALSE(
        title = "Đúng hay Sai Thần Tốc",
        subtitle = "Cặp từ & nghĩa ➔ Phán đoán Đúng/Sai",
        icon = "🎯",
        badge = "THẦN TỐC",
        description = "Mỗi giây đều quyết định! Phán đoán chuẩn xác nghĩa hiển thị là Đúng hay Sai trong nháy mắt."
    ),
    WORD_SCRAMBLE(
        title = "Giải Mã Xếp Chữ",
        subtitle = "Ký tự xáo trộn ➔ Giải mã từ gốc",
        icon = "🧩",
        badge = "TRÍ TUỆ",
        description = "Các chữ cái bị đảo lộn hỗn loạn kèm gợi ý tiếng Việt. Ai giải mã nhanh hơn sẽ ra đòn quyết định!"
    ),
    RANDOM_CHAOS(
        title = "Đấu Trường Hỗn Chiến",
        subtitle = "Biến hóa ngẫu nhiên từng hiệp đấu",
        icon = "🔥🎲",
        badge = "HỖN CHIẾN",
        description = "Mỗi hiệp đổi 1 thể loại bất ngờ (Đoán nghĩa, Nghịch đảo, Đúng/Sai, Xếp chữ). Kịch tính tột cùng!"
    )
}

enum class AiDifficulty(
    val title: String,
    val subtitle: String,
    val badge: String,
    val botName: String,
    val colorHex: Long,
    val minDelayMs: Long,
    val maxDelayMs: Long,
    val accuracyPercent: Int,
    val baseDamage: Int,
    val description: String
) {
    EASY(
        title = "Tập Sự (Dễ)",
        subtitle = "AI phản ứng chậm, phù hợp làm quen",
        badge = "🌱 DỄ",
        botName = "AI Tập Sự 🌱",
        colorHex = 0xFF4CAF50,
        minDelayMs = 4500L,
        maxDelayMs = 7500L,
        accuracyPercent = 55,
        baseDamage = 15,
        description = "AI phản xạ 4.5s - 7.5s, tỷ lệ đúng 55%. Dễ thở để rèn luyện ghi nhớ từ vựng."
    ),
    NORMAL(
        title = "Chiến Binh (Vừa)",
        subtitle = "AI phản xạ vừa phải, đối kháng cân não",
        badge = "⚔️ VỪA",
        botName = "AI Chiến Binh ⚔️",
        colorHex = 0xFF2196F3,
        minDelayMs = 3000L,
        maxDelayMs = 5500L,
        accuracyPercent = 75,
        baseDamage = 20,
        description = "AI phản xạ 3.0s - 5.5s, tỷ lệ đúng 75%. Cạnh tranh cân não ngang ngửa học sinh khá."
    ),
    HARD(
        title = "Cao Thủ (Khó)",
        subtitle = "AI tốc độ chớp nhoáng, chuẩn xác cao",
        badge = "🔥 KHÓ",
        botName = "AI Cao Thủ 🔥",
        colorHex = 0xFFFF9800,
        minDelayMs = 2000L,
        maxDelayMs = 4000L,
        accuracyPercent = 88,
        baseDamage = 25,
        description = "AI phản xạ 2.0s - 4.0s, tỷ lệ đúng 88%. Đòi hỏi tay nhanh như chớp và trí nhớ sắc bén!"
    ),
    NIGHTMARE(
        title = "Siêu Trí Tuệ (Ác Mộng)",
        subtitle = "AI huyền thoại, ra đòn cực hiểm",
        badge = "💀 ÁC MỘNG",
        botName = "AI Siêu Trí Tuệ 💀",
        colorHex = 0xFFE91E63,
        minDelayMs = 1200L,
        maxDelayMs = 2500L,
        accuracyPercent = 96,
        baseDamage = 30,
        description = "AI phản xạ 1.2s - 2.5s, tỷ lệ đúng 96%. Sát thương chí mạng, thử thách đỉnh cao!"
    )
}

enum class QuestionDistributionMode(
    val title: String,
    val subtitle: String,
    val icon: String,
    val badge: String
) {
    IDENTICAL(
        title = "Thứ tự giống hệt nhau",
        subtitle = "Cả 2 máy nhận cùng 1 câu hỏi theo cùng thứ tự",
        icon = "👥",
        badge = "CÔNG BẰNG"
    ),
    SHUFFLED(
        title = "Đảo thứ tự câu hỏi",
        subtitle = "Cùng bộ câu hỏi nhưng thứ tự hiển thị ngẫu nhiên khác nhau",
        icon = "🔀",
        badge = "BẢO MẬT"
    ),
    DIFFERENT_EQUAL(
        title = "Câu hỏi khác nhau (Độ khó tương đương)",
        subtitle = "Mỗi máy 1 bộ từ vựng riêng biệt, cân tài cân sức",
        icon = "⚡🎲",
        badge = "ĐỘC LẬP"
    )
}

data class DuelQuestion(
    val id: Int,
    val mode: DuelGameMode,
    val promptQuestion: String,
    val promptSubtitle: String,
    val correctAnswer: String,
    val options: List<String>,
    val explanation: String = ""
)

data class RoundRecapData(
    val roundNumber: Int,
    val playerRoundWon: Boolean?,
    val playerDamageDealt: Int,
    val opponentDamageDealt: Int,
    val message: String
)

enum class DuelScreenState {
    LOBBY,              // Select Role: Host, Join, or Practice Bot
    WAITING_OPPONENT,   // Host listening or Client connecting
    COUNTDOWN,          // 3..2..1.. Fight!
    BATTLE,             // Realtime round
    ROUND_RECAP,        // Intermission showing round victory/defeat celebration
    MATCH_OVER          // Match Champion / Final Defeat celebration
}

class BluetoothDuelViewModel(application: Application) : AndroidViewModel(application) {

    val duelManager = BluetoothDuelManager(application)
    private val repository: WordRepository
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = application.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val _screenState = MutableStateFlow(DuelScreenState.LOBBY)
    val screenState = _screenState.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost = _isHost.asStateFlow()

    private val _playerName = MutableStateFlow("Người chơi")
    val playerName = _playerName.asStateFlow()

    // Host Configurable Match Settings
    private val _roundsPerMatch = MutableStateFlow(5)
    val roundsPerMatch = _roundsPerMatch.asStateFlow()

    private val _questionsPerRound = MutableStateFlow(1)
    val questionsPerRound = _questionsPerRound.asStateFlow()

    private val _timePerQuestionSec = MutableStateFlow(10)
    val timePerQuestionSec = _timePerQuestionSec.asStateFlow()

    private val _questionDistributionMode = MutableStateFlow(QuestionDistributionMode.IDENTICAL)
    val questionDistributionMode = _questionDistributionMode.asStateFlow()

    // Game Mode Selection
    private val _selectedGameMode = MutableStateFlow(DuelGameMode.FAST_CHOICE)
    val selectedGameMode = _selectedGameMode.asStateFlow()

    // AI Difficulty Selection
    private val _selectedAiDifficulty = MutableStateFlow(AiDifficulty.NORMAL)
    val selectedAiDifficulty = _selectedAiDifficulty.asStateFlow()

    val opponentName = duelManager.opponentName
    val connectionStatus = duelManager.connectionStatus
    val connectionMedium = duelManager.connectionMedium
    val discoveredDevices = duelManager.discoveredDevices
    val isScanning = duelManager.isScanning
    val discoveredWifiRooms = duelManager.discoveredWifiRooms
    val isScanningWifi = duelManager.isScanningWifi
    val localWifiIp = duelManager.localWifiIp
    val wifiRoomCode = duelManager.wifiRoomCode

    // Battle Metrics
    private val _playerHp = MutableStateFlow(100)
    val playerHp = _playerHp.asStateFlow()

    private val _opponentHp = MutableStateFlow(100)
    val opponentHp = _opponentHp.asStateFlow()

    private val _currentRound = MutableStateFlow(1)
    val currentRound = _currentRound.asStateFlow()

    val totalRounds: Int get() = _roundsPerMatch.value

    private val _roundQuestions = MutableStateFlow<List<DuelQuestion>>(emptyList())
    val roundQuestions = _roundQuestions.asStateFlow()

    private val _currentQuestion = MutableStateFlow<DuelQuestion?>(null)
    val currentQuestion = _currentQuestion.asStateFlow()

    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption = _selectedOption.asStateFlow()

    private val _hasAnsweredCurrentRound = MutableStateFlow(false)
    val hasAnsweredCurrentRound = _hasAnsweredCurrentRound.asStateFlow()

    private val _roundTimeRemaining = MutableStateFlow(10)
    val roundTimeRemaining = _roundTimeRemaining.asStateFlow()

    private val _countdownNumber = MutableStateFlow(3)
    val countdownNumber = _countdownNumber.asStateFlow()

    private val _roundNotification = MutableStateFlow("")
    val roundNotification = _roundNotification.asStateFlow()

    private val _roundRecap = MutableStateFlow<RoundRecapData?>(null)
    val roundRecap = _roundRecap.asStateFlow()

    // Track round damage to determine round winner
    private var playerRoundDamageDealt = 0
    private var opponentRoundDamageDealt = 0

    private val _recentFloatingEmote = MutableStateFlow<String?>(null)
    val recentFloatingEmote = _recentFloatingEmote.asStateFlow()

    private val _isWinner = MutableStateFlow(false)
    val isWinner = _isWinner.asStateFlow()

    private val _finalScoreTitle = MutableStateFlow("")
    val finalScoreTitle = _finalScoreTitle.asStateFlow()

    private val _earnedXp = MutableStateFlow(0)
    val earnedXp = _earnedXp.asStateFlow()

    private val _earnedCoins = MutableStateFlow(0)
    val earnedCoins = _earnedCoins.asStateFlow()

    private val _words = MutableStateFlow<List<Word>>(emptyList())

    private var roundTimerJob: Job? = null
    private var botActionJob: Job? = null
    private var roundStartTimeMs: Long = 0L

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WordRepository(database.wordDao())

        viewModelScope.launch {
            repository.allWords.collect {
                _words.value = it
            }
        }

        try {
            @android.annotation.SuppressLint("MissingPermission")
            val btName = duelManager.bluetoothAdapter?.name
            if (!btName.isNullOrBlank()) {
                _playerName.value = btName
            }
        } catch (e: Exception) {
            // ignore
        }

        // Setup packet callbacks
        duelManager.onPacketReceived = { type, data ->
            handleIncomingPacket(type, data)
        }

        duelManager.onOpponentDisconnected = {
            if (_screenState.value == DuelScreenState.BATTLE ||
                _screenState.value == DuelScreenState.COUNTDOWN ||
                _screenState.value == DuelScreenState.ROUND_RECAP
            ) {
                _roundNotification.value = "⚠️ Đối thủ đã rời trận đấu!"
                endMatch(won = true, reason = "Đối thủ ngắt kết nối")
            } else {
                _screenState.value = DuelScreenState.LOBBY
            }
        }
    }

    fun isBluetoothSupported(): Boolean = duelManager.isBluetoothSupported()

    fun isBluetoothEnabled(): Boolean = duelManager.isBluetoothEnabled()

    fun getPairedDevices(): List<BluetoothDeviceItem> = duelManager.getPairedDevices()

    fun startScan() = duelManager.startDiscovery()

    fun stopScan() = duelManager.stopDiscovery()

    // Room Host Settings Configuration
    fun setRoundsPerMatch(rounds: Int) {
        _roundsPerMatch.value = rounds.coerceIn(1, 15)
        syncSettingsToOpponent()
    }

    fun setQuestionsPerRound(qCount: Int) {
        _questionsPerRound.value = qCount.coerceIn(1, 5)
        syncSettingsToOpponent()
    }

    fun setTimePerQuestion(seconds: Int) {
        _timePerQuestionSec.value = seconds.coerceIn(5, 30)
        syncSettingsToOpponent()
    }

    fun setQuestionDistributionMode(mode: QuestionDistributionMode) {
        _questionDistributionMode.value = mode
        syncSettingsToOpponent()
    }

    private fun syncSettingsToOpponent() {
        if (_isHost.value && duelManager.connectionStatus.value == ConnectionStatus.CONNECTED) {
            val payload = JSONObject().apply {
                put("mode", _selectedGameMode.value.name)
                put("roundsPerMatch", _roundsPerMatch.value)
                put("questionsPerRound", _questionsPerRound.value)
                put("timePerQuestion", _timePerQuestionSec.value)
                put("distributionMode", _questionDistributionMode.value.name)
            }
            duelManager.sendPacket("CONFIG_SYNC", payload)
        }
    }

    fun selectGameMode(mode: DuelGameMode) {
        _selectedGameMode.value = mode
        syncSettingsToOpponent()
    }

    fun selectAiDifficulty(difficulty: AiDifficulty) {
        _selectedAiDifficulty.value = difficulty
    }

    fun hostRoom() {
        _isHost.value = true
        _screenState.value = DuelScreenState.WAITING_OPPONENT
        duelManager.startHosting(_playerName.value)
    }

    fun joinRoom(deviceAddress: String) {
        _isHost.value = false
        _screenState.value = DuelScreenState.WAITING_OPPONENT
        duelManager.connectToDevice(deviceAddress, _playerName.value)
    }

    fun setConnectionMedium(medium: com.example.bluetooth.ConnectionMedium) {
        duelManager.setConnectionMedium(medium)
    }

    fun startWifiHosting() {
        _isHost.value = true
        _screenState.value = DuelScreenState.WAITING_OPPONENT
        duelManager.startWifiHosting(_playerName.value)
    }

    fun connectToWifiRoom(room: com.example.bluetooth.WifiRoomItem) {
        _isHost.value = false
        _screenState.value = DuelScreenState.WAITING_OPPONENT
        duelManager.connectToWifiRoom(room.ip, room.port, _playerName.value)
    }

    fun connectByIpOrCode(target: String) {
        _isHost.value = false
        _screenState.value = DuelScreenState.WAITING_OPPONENT
        duelManager.connectByRoomCodeOrIp(target, _playerName.value)
    }

    fun startWifiScan() = duelManager.startWifiDiscovery()

    fun stopWifiScan() = duelManager.stopWifiDiscovery()

    fun getLocalIpAddress(): String = duelManager.getLocalIpAddress()

    fun startPracticeBot(
        difficulty: AiDifficulty = _selectedAiDifficulty.value,
        mode: DuelGameMode = _selectedGameMode.value
    ) {
        _isHost.value = true
        _selectedAiDifficulty.value = difficulty
        _selectedGameMode.value = mode
        duelManager.startBotMode(difficulty.botName)
        startCountdownAndMatch()
    }

    fun cancelWaiting() {
        duelManager.disconnect()
        _screenState.value = DuelScreenState.LOBBY
    }

    // Called when both players are connected (Host hits Start)
    fun startHostMatch() {
        if (!_isHost.value) return
        startCountdownAndMatch()
    }

    private fun startCountdownAndMatch() {
        val targetMode = _selectedGameMode.value
        val rounds = _roundsPerMatch.value
        val distMode = _questionDistributionMode.value

        val pool = _words.value.ifEmpty { getDefaultWordPool() }
        val effectivePool = if (pool.size < 6) getDefaultWordPool() else pool

        val hostQuestions = generateDuelQuestions(rounds, targetMode, effectivePool)
        _roundQuestions.value = hostQuestions

        // Send INIT_MATCH packet to client if connected over Bluetooth / Wi-Fi
        if (duelManager.connectionStatus.value == ConnectionStatus.CONNECTED) {
            val clientQuestions = when (distMode) {
                QuestionDistributionMode.IDENTICAL -> hostQuestions
                QuestionDistributionMode.SHUFFLED -> hostQuestions.shuffled()
                QuestionDistributionMode.DIFFERENT_EQUAL -> {
                    // Equivalent difficulty: separate distinct words from same pool
                    val remainingPool = effectivePool.filterNot { w -> hostQuestions.any { it.id == w.id } }
                    val altPool = if (remainingPool.size >= rounds) remainingPool else effectivePool.shuffled()
                    generateDuelQuestions(rounds, targetMode, altPool)
                }
            }

            val questionsArray = JSONArray()
            clientQuestions.forEach { q ->
                val qObj = JSONObject().apply {
                    put("id", q.id)
                    put("mode", q.mode.name)
                    put("promptQuestion", q.promptQuestion)
                    put("promptSubtitle", q.promptSubtitle)
                    put("correctAnswer", q.correctAnswer)
                    put("explanation", q.explanation)
                    val opts = JSONArray()
                    q.options.forEach { opts.put(it) }
                    put("options", opts)
                }
                questionsArray.put(qObj)
            }
            val payload = JSONObject().apply {
                put("mode", targetMode.name)
                put("roundsPerMatch", rounds)
                put("timePerQuestion", _timePerQuestionSec.value)
                put("questions", questionsArray)
            }
            duelManager.sendPacket("INIT_MATCH", payload)
        }

        runMatchCountdown()
    }

    private fun runMatchCountdown() {
        _screenState.value = DuelScreenState.COUNTDOWN
        _playerHp.value = 100
        _opponentHp.value = 100
        _currentRound.value = 1
        _roundNotification.value = ""
        _roundRecap.value = null
        playerRoundDamageDealt = 0
        opponentRoundDamageDealt = 0

        viewModelScope.launch {
            for (i in 3 downTo 1) {
                _countdownNumber.value = i
                triggerVibrate(60)
                delay(1000)
            }
            _countdownNumber.value = 0
            triggerVibrate(150)
            delay(400)
            startRound(1)
        }
    }

    private fun startRound(roundNum: Int) {
        _currentRound.value = roundNum
        _selectedOption.value = null
        _hasAnsweredCurrentRound.value = false
        _roundNotification.value = ""
        playerRoundDamageDealt = 0
        opponentRoundDamageDealt = 0

        val questions = _roundQuestions.value
        if (roundNum <= questions.size) {
            val q = questions[roundNum - 1]
            _currentQuestion.value = q
            _screenState.value = DuelScreenState.BATTLE
            roundStartTimeMs = System.currentTimeMillis()

            startRoundTimer(q.mode)

            // If in bot mode, schedule bot response
            if (duelManager.connectionStatus.value == ConnectionStatus.BOT_CONNECTED) {
                scheduleBotAction()
            }
        } else {
            // All rounds completed!
            determineMatchResult()
        }
    }

    private fun startRoundTimer(mode: DuelGameMode) {
        roundTimerJob?.cancel()
        val durationSec = _timePerQuestionSec.value
        _roundTimeRemaining.value = durationSec

        roundTimerJob = viewModelScope.launch {
            for (sec in durationSec downTo 1) {
                _roundTimeRemaining.value = sec
                delay(1000)
            }
            _roundTimeRemaining.value = 0

            // Time expired for this round!
            if (!_hasAnsweredCurrentRound.value) {
                handleTimeout()
            }
        }
    }

    fun submitAnswer(option: String) {
        if (_hasAnsweredCurrentRound.value) return
        _hasAnsweredCurrentRound.value = true
        _selectedOption.value = option

        val question = _currentQuestion.value ?: return
        val isCorrect = option.trim().equals(question.correctAnswer.trim(), ignoreCase = true)
        val elapsedMs = System.currentTimeMillis() - roundStartTimeMs

        if (isCorrect) {
            val dmgDealt: Int
            val message: String
            if (elapsedMs <= 3000) {
                dmgDealt = 30
                message = "⚡ CRITICAL HIT! (-30 HP)"
                triggerVibrate(200)
            } else if (elapsedMs <= 6500) {
                dmgDealt = 20
                message = "⚔️ TẤN CÔNG NHANH! (-20 HP)"
                triggerVibrate(120)
            } else {
                dmgDealt = 15
                message = "🎯 ĐÁNH TRÚNG! (-15 HP)"
                triggerVibrate(80)
            }

            playerRoundDamageDealt += dmgDealt
            _roundNotification.value = message
            val newOpponentHp = (_opponentHp.value - dmgDealt).coerceAtLeast(0)
            _opponentHp.value = newOpponentHp

            // Broadcast action to opponent
            val payload = JSONObject().apply {
                put("round", _currentRound.value)
                put("isCorrect", true)
                put("damage", dmgDealt)
                put("elapsedMs", elapsedMs)
                put("remainingHp", _playerHp.value)
            }
            duelManager.sendPacket("PLAYER_ACTION", payload)

            if (newOpponentHp <= 0) {
                viewModelScope.launch {
                    delay(1000)
                    endMatch(won = true, reason = "Hạ gục đối thủ tuyệt đối!")
                }
                return
            }
        } else {
            // Wrong answer recoil
            val recoil = 10
            _roundNotification.value = "💥 SAI RỒI! Bị phản đòn (-10 HP)"
            triggerVibrate(250)

            val newPlayerHp = (_playerHp.value - recoil).coerceAtLeast(0)
            _playerHp.value = newPlayerHp

            val payload = JSONObject().apply {
                put("round", _currentRound.value)
                put("isCorrect", false)
                put("damage", 0)
                put("elapsedMs", elapsedMs)
                put("remainingHp", newPlayerHp)
            }
            duelManager.sendPacket("PLAYER_ACTION", payload)

            if (newPlayerHp <= 0) {
                viewModelScope.launch {
                    delay(1000)
                    endMatch(won = false, reason = "Cạn kiệt máu!")
                }
                return
            }
        }

        // Intermission and finish round
        viewModelScope.launch {
            delay(1600)
            finishCurrentRound()
        }
    }

    private fun handleTimeout() {
        _hasAnsweredCurrentRound.value = true
        _roundNotification.value = "⏰ HẾT GIỜ! Bị trừ 10 HP"
        triggerVibrate(200)

        val newPlayerHp = (_playerHp.value - 10).coerceAtLeast(0)
        _playerHp.value = newPlayerHp

        val payload = JSONObject().apply {
            put("round", _currentRound.value)
            put("isCorrect", false)
            put("damage", 0)
            put("elapsedMs", _timePerQuestionSec.value * 1000L)
            put("remainingHp", newPlayerHp)
        }
        duelManager.sendPacket("PLAYER_ACTION", payload)

        if (newPlayerHp <= 0) {
            endMatch(won = false, reason = "Cạn kiệt máu do hết giờ!")
            return
        }

        viewModelScope.launch {
            delay(1400)
            finishCurrentRound()
        }
    }

    private fun finishCurrentRound() {
        roundTimerJob?.cancel()
        botActionJob?.cancel()

        if (_playerHp.value <= 0) {
            endMatch(won = false, reason = "Hết máu!")
            return
        }
        if (_opponentHp.value <= 0) {
            endMatch(won = true, reason = "Đối thủ đã hết máu!")
            return
        }

        // Determine who won this round
        val roundWon: Boolean? = when {
            playerRoundDamageDealt > opponentRoundDamageDealt -> true
            playerRoundDamageDealt < opponentRoundDamageDealt -> false
            else -> null // draw
        }

        val recapMsg = when (roundWon) {
            true -> "🎉 CHÚC MỪNG BẠN THẮNG HIỆP ${_currentRound.value}! Đòn đánh xuất sắc! ⚔️"
            false -> "💪 ĐỪNG NẢN LÒNG! Cố gắng bứt phá ở hiệp sau nhé! 🛡️"
            null -> "🤝 HIỆP ĐẤU HÒA ĐIỂM CÂN NÃO!"
        }

        _roundRecap.value = RoundRecapData(
            roundNumber = _currentRound.value,
            playerRoundWon = roundWon,
            playerDamageDealt = playerRoundDamageDealt,
            opponentDamageDealt = opponentRoundDamageDealt,
            message = recapMsg
        )

        _screenState.value = DuelScreenState.ROUND_RECAP

        // Play appropriate sound effect based on round result
        when (roundWon) {
            true -> DuelSoundEffectManager.playRoundWin(getApplication(), viewModelScope)
            false -> DuelSoundEffectManager.playRoundLoss(getApplication(), viewModelScope)
            null -> DuelSoundEffectManager.playRoundDraw(getApplication(), viewModelScope)
        }

        // Show round recap for 2.4s then advance or finish
        viewModelScope.launch {
            delay(2400)
            if (_screenState.value == DuelScreenState.ROUND_RECAP) {
                val nextRoundNum = _currentRound.value + 1
                if (nextRoundNum <= _roundsPerMatch.value) {
                    startRound(nextRoundNum)
                } else {
                    determineMatchResult()
                }
            }
        }
    }

    private fun scheduleBotAction() {
        botActionJob?.cancel()
        val difficulty = _selectedAiDifficulty.value
        botActionJob = viewModelScope.launch {
            val minD = difficulty.minDelayMs
            val maxD = difficulty.maxDelayMs
            val botDelayMs = if (maxD > minD) (minD..maxD).random() else minD
            delay(botDelayMs)

            if (!isActive || _hasAnsweredCurrentRound.value) return@launch

            val isBotCorrect = (1..100).random() <= difficulty.accuracyPercent
            if (isBotCorrect) {
                val dmg = if (botDelayMs <= (minD + 1200)) (difficulty.baseDamage + 5) else difficulty.baseDamage
                opponentRoundDamageDealt += dmg
                val newHp = (_playerHp.value - dmg).coerceAtLeast(0)
                _playerHp.value = newHp
                _roundNotification.value = "⚠️ ${difficulty.botName} ra đòn sấm sét! (-$dmg HP)"
                triggerVibrate(150)

                if (newHp <= 0) {
                    endMatch(won = false, reason = "${difficulty.botName} đã hạ gục bạn!")
                }
            } else {
                _roundNotification.value = "🛡️ ${difficulty.botName} đã suy luận sai đáp án!"
            }
        }
    }

    private fun handleIncomingPacket(type: String, data: JSONObject) {
        when (type) {
            "CONFIG_SYNC" -> {
                // Client syncs room settings from Host
                val modeName = data.optString("mode")
                try {
                    val mode = DuelGameMode.valueOf(modeName)
                    _selectedGameMode.value = mode
                } catch (e: Exception) {
                    // ignore
                }

                val rounds = data.optInt("roundsPerMatch", 5)
                val qPerRound = data.optInt("questionsPerRound", 1)
                val timeQ = data.optInt("timePerQuestion", 10)
                val distName = data.optString("distributionMode", QuestionDistributionMode.IDENTICAL.name)

                _roundsPerMatch.value = rounds
                _questionsPerRound.value = qPerRound
                _timePerQuestionSec.value = timeQ
                try {
                    _questionDistributionMode.value = QuestionDistributionMode.valueOf(distName)
                } catch (e: Exception) {
                    // ignore
                }

                _roundNotification.value = "📢 Chủ phòng cập nhật cài đặt: $rounds Hiệp • ${timeQ}s/câu"
            }

            "MODE_CHANGE" -> {
                val modeName = data.optString("mode")
                try {
                    val mode = DuelGameMode.valueOf(modeName)
                    _selectedGameMode.value = mode
                    _roundNotification.value = "📢 Chủ phòng chọn chế độ: ${mode.title}"
                } catch (e: Exception) {
                    // ignore
                }
            }

            "INIT_MATCH" -> {
                // Client received question set, settings and mode from host
                val modeName = data.optString("mode")
                try {
                    if (modeName.isNotBlank()) {
                        _selectedGameMode.value = DuelGameMode.valueOf(modeName)
                    }
                } catch (e: Exception) {
                    // ignore
                }

                val rounds = data.optInt("roundsPerMatch", 5)
                val timeQ = data.optInt("timePerQuestion", 10)
                _roundsPerMatch.value = rounds
                _timePerQuestionSec.value = timeQ

                val questionsArray = data.optJSONArray("questions") ?: JSONArray()
                val list = mutableListOf<DuelQuestion>()
                for (i in 0 until questionsArray.length()) {
                    val qObj = questionsArray.getJSONObject(i)
                    val optsJson = qObj.getJSONArray("options")
                    val opts = mutableListOf<String>()
                    for (j in 0 until optsJson.length()) {
                        opts.add(optsJson.getString(j))
                    }
                    val qModeStr = qObj.optString("mode", _selectedGameMode.value.name)
                    val qMode = try { DuelGameMode.valueOf(qModeStr) } catch (e: Exception) { _selectedGameMode.value }

                    val promptQ = qObj.optString("promptQuestion").ifBlank { qObj.optString("word") }
                    val promptSub = qObj.optString("promptSubtitle").ifBlank { qObj.optString("phonetics") }
                    val correctAns = qObj.optString("correctAnswer").ifBlank { qObj.optString("meaning") }

                    list.add(
                        DuelQuestion(
                            id = qObj.optInt("id", i + 1),
                            mode = qMode,
                            promptQuestion = promptQ,
                            promptSubtitle = promptSub,
                            correctAnswer = correctAns,
                            options = opts,
                            explanation = qObj.optString("explanation")
                        )
                    )
                }
                _roundQuestions.value = list
                runMatchCountdown()
            }

            "PLAYER_ACTION" -> {
                val isCorrect = data.optBoolean("isCorrect")
                val damage = data.optInt("damage")
                val oppRemainingHp = data.optInt("remainingHp")

                _opponentHp.value = oppRemainingHp

                if (isCorrect) {
                    opponentRoundDamageDealt += damage
                    val newMyHp = (_playerHp.value - damage).coerceAtLeast(0)
                    _playerHp.value = newMyHp
                    _roundNotification.value = "⚔️ Đối thủ vừa ra đòn trúng! (-$damage HP)"
                    triggerVibrate(200)

                    if (newMyHp <= 0) {
                        endMatch(won = false, reason = "Bạn đã bị đối thủ đánh bại!")
                    }
                } else {
                    _roundNotification.value = "🛡️ Đối thủ trả lời sai!"
                }
            }

            "EMOTE" -> {
                val emoji = data.optString("emoji")
                _recentFloatingEmote.value = emoji
                viewModelScope.launch {
                    delay(2500)
                    _recentFloatingEmote.value = null
                }
            }

            "REMATCH" -> {
                if (_screenState.value == DuelScreenState.MATCH_OVER) {
                    _roundNotification.value = "🔄 Đối thủ yêu cầu đấu lại!"
                    startCountdownAndMatch()
                }
            }
        }
    }

    fun sendEmote(emoji: String) {
        _recentFloatingEmote.value = emoji
        val payload = JSONObject().apply {
            put("emoji", emoji)
        }
        duelManager.sendPacket("EMOTE", payload)
        viewModelScope.launch {
            delay(2500)
            _recentFloatingEmote.value = null
        }
    }

    fun requestRematch() {
        val payload = JSONObject().apply {
            put("request", true)
        }
        duelManager.sendPacket("REMATCH", payload)
        startCountdownAndMatch()
    }

    private fun determineMatchResult() {
        val myHp = _playerHp.value
        val oppHp = _opponentHp.value
        when {
            myHp > oppHp -> endMatch(won = true, reason = "Chiến thắng với lượng máu áp đảo ($myHp vs $oppHp)!")
            myHp < oppHp -> endMatch(won = false, reason = "Đối thủ nhiều máu hơn ($oppHp vs $myHp)!")
            else -> endMatch(won = true, reason = "Hòa điểm cân não!")
        }
    }

    private fun endMatch(won: Boolean, reason: String) {
        roundTimerJob?.cancel()
        botActionJob?.cancel()

        _isWinner.value = won
        _finalScoreTitle.value = if (won) "🏆 CHIẾN THẮNG CHUNG CUỘC!" else "💀 BẠN ĐÃ THUA TRẬN CHUNG CUỘC"
        _roundNotification.value = reason

        val xp = if (won) 150 else 50
        val coins = if (won) 50 else 15
        _earnedXp.value = xp
        _earnedCoins.value = coins

        _screenState.value = DuelScreenState.MATCH_OVER

        // Play champion fanfare or defeat sound
        if (won) {
            DuelSoundEffectManager.playMatchChampion(getApplication(), viewModelScope)
        } else {
            DuelSoundEffectManager.playMatchDefeat(getApplication(), viewModelScope)
        }

        // Reward user in Room database
        viewModelScope.launch {
            val profile = repository.userProfile.firstOrNull() ?: UserProfile()
            repository.saveUserProfile(
                profile.copy(
                    xp = profile.xp + xp,
                    coins = profile.coins + coins
                )
            )
        }
    }

    fun triggerFireworkSound() {
        DuelSoundEffectManager.playFireworkPop(viewModelScope)
    }

    private fun triggerVibrate(durationMs: Long) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun scrambleWord(word: String): String {
        val clean = word.trim().uppercase()
        if (clean.length <= 3) return clean.toList().joinToString(" ")
        val chars = clean.toList()
        var shuffled = chars.shuffled()
        var attempts = 0
        while (shuffled == chars && attempts < 10) {
            shuffled = chars.shuffled()
            attempts++
        }
        return shuffled.joinToString(" ")
    }

    private fun generateDuelQuestions(
        count: Int,
        rootMode: DuelGameMode,
        customPool: List<Word>? = null
    ): List<DuelQuestion> {
        val pool = customPool ?: (_words.value.ifEmpty { getDefaultWordPool() })
        val effectivePool = if (pool.size < 6) getDefaultWordPool() else pool
        val shuffledWords = effectivePool.shuffled().take(count)

        val concreteModes = listOf(
            DuelGameMode.FAST_CHOICE,
            DuelGameMode.REVERSE_MEANING,
            DuelGameMode.TRUE_FALSE,
            DuelGameMode.WORD_SCRAMBLE
        )

        return shuffledWords.mapIndexed { index, word ->
            val modeForRound = if (rootMode == DuelGameMode.RANDOM_CHAOS) {
                concreteModes[index % concreteModes.size]
            } else {
                rootMode
            }

            when (modeForRound) {
                DuelGameMode.FAST_CHOICE -> {
                    val correct = word.meaning
                    val distractors = effectivePool.filter { it.id != word.id }.map { it.meaning }.shuffled().take(3)
                    val opts = (listOf(correct) + distractors).shuffled()
                    DuelQuestion(
                        id = word.id,
                        mode = DuelGameMode.FAST_CHOICE,
                        promptQuestion = word.word,
                        promptSubtitle = word.pronunciation.ifBlank { "/.../" },
                        correctAnswer = correct,
                        options = opts,
                        explanation = "Nghĩa: ${word.meaning}"
                    )
                }

                DuelGameMode.REVERSE_MEANING -> {
                    val correct = word.word
                    val distractors = effectivePool.filter { it.id != word.id }.map { it.word }.shuffled().take(3)
                    val opts = (listOf(correct) + distractors).shuffled()
                    DuelQuestion(
                        id = word.id,
                        mode = DuelGameMode.REVERSE_MEANING,
                        promptQuestion = word.meaning,
                        promptSubtitle = "Chọn từ tiếng Anh chính xác",
                        correctAnswer = correct,
                        options = opts,
                        explanation = "${word.word} [${word.pronunciation}]: ${word.meaning}"
                    )
                }

                DuelGameMode.TRUE_FALSE -> {
                    val isTrue = (index % 2 == 0)
                    val displayedMeaning = if (isTrue) {
                        word.meaning
                    } else {
                        effectivePool.firstOrNull { it.id != word.id }?.meaning ?: "Nghĩa không chính xác"
                    }
                    val correct = if (isTrue) "ĐÚNG" else "SAI"
                    DuelQuestion(
                        id = word.id,
                        mode = DuelGameMode.TRUE_FALSE,
                        promptQuestion = "${word.word} = $displayedMeaning",
                        promptSubtitle = "Cặp từ và nghĩa này Đúng hay Sai?",
                        correctAnswer = correct,
                        options = listOf("ĐÚNG", "SAI"),
                        explanation = "${word.word} có nghĩa là: ${word.meaning}"
                    )
                }

                DuelGameMode.WORD_SCRAMBLE -> {
                    val correct = word.word
                    val scrambled = scrambleWord(word.word)
                    val distractors = effectivePool.filter { it.id != word.id }.map { it.word }.shuffled().take(3)
                    val opts = (listOf(correct) + distractors).shuffled()
                    DuelQuestion(
                        id = word.id,
                        mode = DuelGameMode.WORD_SCRAMBLE,
                        promptQuestion = scrambled,
                        promptSubtitle = "Gợi ý nghĩa: ${word.meaning}",
                        correctAnswer = correct,
                        options = opts,
                        explanation = "Từ gốc: ${word.word} (${word.meaning})"
                    )
                }

                DuelGameMode.RANDOM_CHAOS -> {
                    DuelQuestion(
                        id = word.id,
                        mode = DuelGameMode.FAST_CHOICE,
                        promptQuestion = word.word,
                        promptSubtitle = word.pronunciation,
                        correctAnswer = word.meaning,
                        options = listOf(word.meaning)
                    )
                }
            }
        }
    }

    private fun getDefaultWordPool(): List<Word> {
        return listOf(
            Word(1, "Resilient", "Kiên cường, bền bỉ", "/rɪˈzɪl.jənt/", "She is a resilient girl.", "Cô ấy là một cô gái kiên cường.", "Từ vựng tính cách"),
            Word(2, "Collaborate", "Hợp tác, cộng tác", "/kəˈlæb.ə.reɪt/", "We need to collaborate together.", "Chúng ta cần hợp tác cùng nhau.", "Từ vựng công việc"),
            Word(3, "Innovative", "Sáng tạo, đổi mới", "/ˈɪn.ə.veɪ.tɪv/", "An innovative idea for learning.", "Một ý tưởng đổi mới trong học tập.", "Từ vựng công nghệ"),
            Word(4, "Perseverance", "Sự kiên trì, bền chí", "/ˌpɜː.sɪˈvɪə.rəns/", "Perseverance leads to success.", "Kiên trì dẫn đến thành công.", "Từ vựng phẩm chất"),
            Word(5, "Triumph", "Chiến thắng vẻ vang", "/ˈtraɪ.əmf/", "They celebrated their triumph.", "Họ ăn mừng chiến thắng vẻ vang.", "Từ vựng chiến thắng"),
            Word(6, "Eloquent", "Hùng biện, lưu loát", "/ˈel.ə.kwənt/", "An eloquent speaker.", "Một diễn giả lưu loát hùng hồn.", "Từ vựng giao tiếp"),
            Word(7, "Courageous", "Dũng cảm, can đảm", "/kəˈreɪ.dʒəs/", "A courageous action in battle.", "Hành động dũng cảm trong trận đấu.", "Từ vựng dũng khí"),
            Word(8, "Brilliant", "Xuất sắc, lỗi lạc", "/ˈbrɪl.jənt/", "A brilliant young student.", "Một học sinh trẻ tuổi xuất sắc.", "Từ vựng học tập")
        )
    }

    fun exitDuel() {
        roundTimerJob?.cancel()
        botActionJob?.cancel()
        duelManager.disconnect()
        _screenState.value = DuelScreenState.LOBBY
    }

    override fun onCleared() {
        duelManager.disconnect()
        super.onCleared()
    }
}
