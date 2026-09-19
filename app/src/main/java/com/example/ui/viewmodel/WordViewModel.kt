package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.UserProfile
import com.example.data.Word
import com.example.data.WordRepository
import com.example.data.GrammarQuestion
import com.example.data.GrammarTopic
import com.example.api.GeminiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val correction: String? = null,
    val isVoiceInput: Boolean = false
)

class WordViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {
    private val repository: WordRepository
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    private val _isTtsSpeaking = MutableStateFlow(false)
    val isTtsSpeaking = _isTtsSpeaking.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WordRepository(database.wordDao())
        tts = TextToSpeech(application, this)
        
        // Populate initial data if empty
        viewModelScope.launch {
            repository.populateInitialDataIfNeeded()
        }
        loadCustomGrammarTopics()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    tts?.setAudioAttributes(audioAttributes)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            isTtsInitialized = true
            setNaturalHumanVoice(Locale("en", "US"), isFemale = true)
            tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isTtsSpeaking.value = true
                }
                override fun onDone(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isTtsSpeaking.value = false
                }
            })
        }
    }

    private fun isVietnameseText(text: String): Boolean {
        val viChars = listOf('ă', 'â', 'đ', 'ê', 'ô', 'ơ', 'ư', 'à', 'ả', 'ã', 'á', 'ạ', 'ằ', 'ẳ', 'ẵ', 'ắ', 'ặ', 'ầ', 'ẩ', 'ẫ', 'ấ', 'ậ', 'è', 'ẻ', 'ẽ', 'é', 'ẹ', 'ề', 'ể', 'ễ', 'ế', 'ệ', 'ì', 'ỉ', 'ĩ', 'í', 'ị', 'ò', 'ỏ', 'õ', 'ó', 'ọ', 'ồ', 'ổ', 'ỗ', 'ố', 'ộ', 'ờ', 'ở', 'ỡ', 'ớ', 'ợ', 'ù', 'ủ', 'ũ', 'ú', 'ụ', 'ừ', 'ử', 'ữ', 'ứ', 'ự', 'ỳ', 'ỷ', 'ỹ', 'ý', 'ỵ')
        val lower = text.lowercase()
        val viWordCount = lower.split(Regex("\\s+")).count { word ->
            word.any { it in viChars } || word in setOf("chào", "bạn", "tôi", "mình", "không", "có", "rất", "vui", "được", "nghe", "nói", "tiếng", "việt", "anh", "giỏi", "quá", "tốt", "nhé", "nha", "đúng", "rồi", "lỗi", "sửa", "hãy", "thử", "lại", "đang")
        }
        return viWordCount >= 1 || lower.contains("chào") || lower.contains("bạn")
    }

    private fun setNaturalHumanVoice(targetLocale: Locale, isFemale: Boolean) {
        if (tts == null) return
        try {
            val langResult = tts?.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                return
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                val voices = tts?.voices ?: return
                val matchingVoices = voices.filter { 
                    it.locale.language.equals(targetLocale.language, ignoreCase = true)
                }
                if (matchingVoices.isNotEmpty()) {
                    val genderKeyword = if (isFemale) "female" else "male"
                    val oppositeKeyword = if (isFemale) "male" else "female"
                    val genderCode = if (isFemale) "-f" else "-m"
                    val oppositeCode = if (isFemale) "-m" else "-f"
                    
                    val genderFiltered = matchingVoices.filter { voice ->
                        val name = voice.name.lowercase()
                        val features = voice.features?.toString()?.lowercase() ?: ""
                        (name.contains(genderKeyword) || name.contains(genderCode) || features.contains(genderKeyword)) &&
                        !name.contains(oppositeKeyword) && !name.contains(oppositeCode)
                    }
                    val pool = if (genderFiltered.isNotEmpty()) genderFiltered else matchingVoices
                    
                    val bestVoice = pool.maxWithOrNull(compareBy<android.speech.tts.Voice> { voice ->
                        var score = 0
                        val name = voice.name.lowercase()
                        val features = voice.features?.toString()?.lowercase() ?: ""
                        
                        if (name.contains("neural") || name.contains("wavenet") || name.contains("natural") || features.contains("natural")) score += 100
                        if (name.contains("network") || name.contains("online") || name.contains("enhanced") || features.contains("network")) score += 60
                        if (name.contains("iom") || name.contains("sfg") || name.contains("tpd") || name.contains("vic") || name.contains("vid") || name.contains("vie")) score += 40
                        
                        if (voice.quality == android.speech.tts.Voice.QUALITY_VERY_HIGH) score += 50
                        if (voice.quality == android.speech.tts.Voice.QUALITY_HIGH) score += 30
                        if (voice.quality == android.speech.tts.Voice.QUALITY_NORMAL) score += 10
                        
                        if (voice.latency == android.speech.tts.Voice.LATENCY_VERY_LOW || voice.latency == android.speech.tts.Voice.LATENCY_LOW) score += 20
                        if (!voice.isNetworkConnectionRequired) score += 15
                        
                        score
                    })
                    if (bestVoice != null) {
                        tts?.voice = bestVoice
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun speak(text: String) {
        if (isTtsInitialized && tts != null) {
            try {
                val isVi = isVietnameseText(text)
                val locale = if (isVi) Locale("vi", "VN") else Locale("en", "US")
                setNaturalHumanVoice(locale, isFemale = _commPartnerGender.value == "female")
                tts?.setPitch(1.0f)
                tts?.setSpeechRate(1.0f)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UtteranceId_" + System.currentTimeMillis())
        }
    }

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
    }

    val allWords: StateFlow<List<Word>> = repository.allWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteWords: StateFlow<List<Word>> = repository.favoriteWords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userProfile: StateFlow<UserProfile> = repository.userProfile
        .map { it ?: UserProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    val reviewQueue: StateFlow<List<Word>> = repository.getWordsForReview(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // UI filters
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedTopic = MutableStateFlow("All")
    val selectedTopic = _selectedTopic.asStateFlow()

    private val _selectedLevel = MutableStateFlow("All")
    val selectedLevel = _selectedLevel.asStateFlow()

    // Combined filtered words
    val filteredWords: StateFlow<List<Word>> = combine(
        allWords, _searchQuery, _selectedTopic, _selectedLevel
    ) { words, query, topic, level ->
        words.filter { word ->
            val matchesQuery = word.word.contains(query, ignoreCase = true) ||
                    word.meaning.contains(query, ignoreCase = true) ||
                    word.topic.contains(query, ignoreCase = true)
            
            val matchesTopic = topic == "All" || word.topic.equals(topic, ignoreCase = true)
            val matchesLevel = level == "All" || word.level.equals(level, ignoreCase = true)
            
            matchesQuery && matchesTopic && matchesLevel
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unique topics list
    val topics: StateFlow<List<String>> = allWords.map { words ->
        listOf("All") + words.map { it.topic }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    // AI Status
    private val _aiState = MutableStateFlow<AiState>(AiState.Idle)
    val aiState = _aiState.asStateFlow()

    private val _selectedTheme = MutableStateFlow(
        application.getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getString("selected_theme", "blue") ?: "blue"
    )
    val selectedTheme = _selectedTheme.asStateFlow()

    fun updateTheme(theme: String) {
        _selectedTheme.value = theme
        getApplication<Application>()
            .getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("selected_theme", theme)
            .apply()
    }

    // --- COINS SHOP & BOOSTER STATES ---
    private val sharedPrefs = getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)

    private val _boosterActionsLeft = MutableStateFlow<Int>(sharedPrefs.getInt("booster_actions_left", 0))
    val boosterActionsLeft = _boosterActionsLeft.asStateFlow()

    private val _streakFreezeCount = MutableStateFlow<Int>(sharedPrefs.getInt("streak_freeze_count", 0))
    val streakFreezeCount = _streakFreezeCount.asStateFlow()

    private val _aiPassCount = MutableStateFlow<Int>(sharedPrefs.getInt("ai_pass_count", 0))
    val aiPassCount = _aiPassCount.asStateFlow()

    private val _unlockedThemes = MutableStateFlow<Set<String>>(
        sharedPrefs.getStringSet("unlocked_themes", setOf("blue", "green", "purple", "orange")) ?: setOf("blue", "green", "purple", "orange")
    )
    val unlockedThemes = _unlockedThemes.asStateFlow()

    // --- STUDY REMINDER STATES ---
    private val _isReminderEnabled = MutableStateFlow<Boolean>(sharedPrefs.getBoolean("reminder_enabled", false))
    val isReminderEnabled = _isReminderEnabled.asStateFlow()

    private val _reminderType = MutableStateFlow<String>(sharedPrefs.getString("reminder_type", "popup") ?: "popup")
    val reminderType = _reminderType.asStateFlow()

    private val _reminderHour = MutableStateFlow<Int>(sharedPrefs.getInt("reminder_hour", 20))
    val reminderHour = _reminderHour.asStateFlow()

    private val _reminderMinute = MutableStateFlow<Int>(sharedPrefs.getInt("reminder_minute", 0))
    val reminderMinute = _reminderMinute.asStateFlow()

    fun updateReminderSettings(enabled: Boolean, type: String, hour: Int, minute: Int) {
        _isReminderEnabled.value = enabled
        _reminderType.value = type
        _reminderHour.value = hour
        _reminderMinute.value = minute
        sharedPrefs.edit()
            .putBoolean("reminder_enabled", enabled)
            .putString("reminder_type", type)
            .putInt("reminder_hour", hour)
            .putInt("reminder_minute", minute)
            .apply()

        if (enabled) {
            com.example.utils.ReminderScheduler.scheduleReminder(getApplication(), hour, minute, type)
        } else {
            com.example.utils.ReminderScheduler.cancelReminder(getApplication())
        }
    }

    fun buyDoubleXpBooster(): Boolean {
        val profile = userProfile.value
        val cost = 50
        if (profile.coins >= cost) {
            viewModelScope.launch {
                val newCoins = profile.coins - cost
                repository.saveUserProfile(profile.copy(coins = newCoins))
                _boosterActionsLeft.value += 5 // Add 5 boosted learning sessions/words
                sharedPrefs.edit().putInt("booster_actions_left", _boosterActionsLeft.value).apply()
            }
            return true
        }
        return false
    }

    fun buyStreakFreeze(): Boolean {
        val profile = userProfile.value
        val cost = 100
        if (profile.coins >= cost) {
            viewModelScope.launch {
                val newCoins = profile.coins - cost
                repository.saveUserProfile(profile.copy(coins = newCoins))
                _streakFreezeCount.value += 1
                sharedPrefs.edit().putInt("streak_freeze_count", _streakFreezeCount.value).apply()
            }
            return true
        }
        return false
    }

    fun buyAiPass(): Boolean {
        val profile = userProfile.value
        val cost = 80
        if (profile.coins >= cost) {
            viewModelScope.launch {
                val newCoins = profile.coins - cost
                repository.saveUserProfile(profile.copy(coins = newCoins))
                _aiPassCount.value += 3 // Buy 3 free premium AI suggestions
                sharedPrefs.edit().putInt("ai_pass_count", _aiPassCount.value).apply()
            }
            return true
        }
        return false
    }

    fun buyTheme(themeId: String, cost: Int): Boolean {
        val profile = userProfile.value
        if (profile.coins >= cost && !_unlockedThemes.value.contains(themeId)) {
            viewModelScope.launch {
                val newCoins = profile.coins - cost
                repository.saveUserProfile(profile.copy(coins = newCoins))
                val newSet = _unlockedThemes.value + themeId
                _unlockedThemes.value = newSet
                sharedPrefs.edit().putStringSet("unlocked_themes", newSet).apply()
            }
            return true
        }
        return false
    }

    fun useAiPass(): Boolean {
        if (_aiPassCount.value > 0) {
            _aiPassCount.value -= 1
            sharedPrefs.edit().putInt("ai_pass_count", _aiPassCount.value).apply()
            return true
        }
        return false
    }

    fun useStreakFreeze(): Boolean {
        if (_streakFreezeCount.value > 0) {
            _streakFreezeCount.value -= 1
            sharedPrefs.edit().putInt("streak_freeze_count", _streakFreezeCount.value).apply()
            return true
        }
        return false
    }

    private val _themeMode = MutableStateFlow(
        application.getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getString("theme_mode", "system") ?: "system"
    )
    val themeMode = _themeMode.asStateFlow()

    fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        getApplication<Application>()
            .getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("theme_mode", mode)
            .apply()
    }

    // --- CHAT AI & CUSTOM API KEY INTEGRATION ---
    private val _userGeminiApiKey = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getString("user_gemini_api_key", "") ?: ""
    )
    val userGeminiApiKey = _userGeminiApiKey.asStateFlow()

    fun updateUserGeminiApiKey(key: String) {
        _userGeminiApiKey.value = key
        getApplication<Application>()
            .getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("user_gemini_api_key", key)
            .apply()
    }

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                content = "Xin chào! Tôi là Trợ lý AI học tiếng Anh. Bạn có thể hỏi bất kỳ điều gì về từ vựng, ngữ pháp, sửa lỗi câu hoặc dịch thuật. Tôi sẽ giải đáp nhanh, chính xác và vào đúng trọng tâm câu hỏi của bạn! 🎯",
                isUser = false
            )
        )
    )
    val chatMessages = _chatMessages.asStateFlow()

    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading = _isChatLoading.asStateFlow()

    fun sendChatMessage(text: String) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(content = text, isUser = true)
        _chatMessages.value = _chatMessages.value + userMsg
        _isChatLoading.value = true

        viewModelScope.launch {
            var apiKey = _userGeminiApiKey.value
            var usingPass = false
            if (apiKey.isBlank()) {
                if (_aiPassCount.value > 0) {
                    usingPass = true
                    apiKey = com.example.BuildConfig.GEMINI_API_KEY
                } else {
                    val errorMsg = ChatMessage(
                        content = "⚠️ Bạn chưa cấu hình API Key và không có Vé AI Gemini. Vui lòng vào Cài đặt để thêm API Key Gemini cá nhân, hoặc đổi Xu Vàng lấy Vé AI Gemini tại Cửa Hàng (nhấp vào biểu tượng Xu trên trang chủ)! 🪙",
                        isUser = false
                    )
                    _chatMessages.value = _chatMessages.value + errorMsg
                    _isChatLoading.value = false
                    return@launch
                }
            }

            // System instruction forcing Gemini to answer directly, in deep detail, and strictly without * or # symbols
            val systemInstructionContent = com.example.api.Content(
                parts = listOf(
                    com.example.api.Part(
                        text = """
                            Bạn là Trợ lý AI chuyên gia tiếng Anh cao cấp, giải thích sâu sắc, cặn kẽ và chuẩn mực.
                            
                            YÊU CẦU CỐT LÕI: TRẢ LỜI NHANH CHÓNG, ĐÚNG TRỌNG TÂM VÀ GIẢI THÍCH THẬT CHI TIẾT, RÕ RÀNG.
                            
                            1. TUYỆT ĐỐI BỎ CÁC KÝ HIỆU * VÀ #:
                               - TUYỆT ĐỐI KHÔNG dùng dấu sao (*) hoặc hai dấu sao (**) để in đậm, in nghiêng hay liệt kê.
                               - TUYỆT ĐỐI KHÔNG dùng dấu thăng (#, ##, ###) để làm tiêu đề.
                               - Chỉ sử dụng văn bản thuần túy (plain text).
                               - Khi liệt kê các ý hoặc ví dụ: dùng dấu gạch ngang (-) hoặc số thứ tự (1, 2, 3...).
                               - Khi muốn làm nổi bật từ vựng hay cụm từ: đặt trong dấu ngoặc kép "..." hoặc viết hoa.
                            
                            2. TRẢ LỜI ĐẦY ĐỦ VÀ RẤT CHI TIẾT:
                               - Không chào hỏi xã giao, không mở bài lan man, không kết bài sáo rỗng. Bắt đầu ngay vào nội dung giải đáp.
                               - Tra từ / cụm từ:
                                 + Định nghĩa chi tiết các nét nghĩa phổ biến nhất.
                                 + Phiên âm IPA chuẩn Anh-Mỹ và từ loại cụ thể.
                                 + Các câu ví dụ sinh động, thực tế kèm dịch nghĩa tiếng Việt đầy đủ.
                                 + Các cụm từ hay đi kèm (collocations), từ đồng nghĩa (synonyms), từ trái nghĩa (antonyms).
                                 + Lưu ý ngữ cảnh sử dụng (văn phong trang trọng hay thân mật, văn viết hay văn nói).
                               - Phân biệt các từ hay nhầm lẫn:
                                 + Phân tích chi tiết bản chất khác biệt của từng từ.
                                 + Các mục đối chiếu rõ ràng kèm các cặp ví dụ so sánh trực quan.
                                 + Mẹo ghi nhớ dễ hiểu để không bị nhầm lẫn.
                               - Sửa lỗi câu:
                                 + Chỉ rõ từng vị trí bị lỗi (từ vựng, thì, cấu trúc, giới từ...).
                                 + Đưa ra câu đã sửa hoàn chỉnh, chuẩn ngữ pháp và văn phong tự nhiên.
                                 + Giải thích chi tiết quy tắc ngữ pháp và lý do tại sao phải sửa như vậy.
                               - Dịch thuật:
                                 + Bản dịch chính xác, tự nhiên và gãy gọn nhất.
                                 + Phân tích chi tiết các từ vựng đắt giá và cấu trúc hay trong câu.
                                 + Gợi ý thêm cách dịch tương đương nếu có ngữ cảnh khác.
                               - Ngữ pháp:
                                 + Trình bày chi tiết công thức, cách dùng, dấu hiệu nhận biết và các trường hợp ngoại lệ.
                                 + Ví dụ phong phú cho từng trường hợp.
                            
                            3. TRÌNH BÀY MẠCH LẠC:
                               - Sử dụng các dòng trống giữa các phần để người đọc dễ theo dõi.
                               - Giải thích bằng tiếng Việt chuẩn mực, giữ nguyên ngữ liệu tiếng Anh đang học.
                        """.trimIndent()
                    )
                )
            )

            // Filter valid messages and ensure history starts from the first user turn, keeping last 8-10 messages for focused context
            val validMessages = _chatMessages.value.filter { 
                it.content.isNotBlank() && !it.content.startsWith("⚠️") && !it.content.startsWith("❌") 
            }
            val firstUserIndex = validMessages.indexOfFirst { it.isUser }
            val dialogueMessages = if (firstUserIndex != -1) {
                validMessages.drop(firstUserIndex).takeLast(8)
            } else {
                listOf(userMsg)
            }

            val history = dialogueMessages.map { msg ->
                com.example.api.Content(
                    parts = listOf(com.example.api.Part(text = cleanMarkdownSymbols(msg.content))),
                    role = if (msg.isUser) "user" else "model"
                )
            }

            val response = com.example.api.GeminiClient.getGeminiChatResponse(
                history = history,
                customApiKey = apiKey,
                systemInstruction = systemInstructionContent
            )
            
            val reply = if (response == "API_KEY_MISSING") {
                "⚠️ API Key hệ thống chưa sẵn sàng. Bạn vui lòng cấu hình API Key cá nhân trong phần Cài đặt để tiếp tục trò chuyện."
            } else if (response.startsWith("ERROR:")) {
                "❌ Lỗi khi gọi Gemini API: ${response.substringAfter("ERROR:")}\n\nVui lòng kiểm tra kết nối mạng hoặc API Key."
            } else {
                if (usingPass) {
                    useAiPass() // Consume 1 pass on success!
                }
                cleanMarkdownSymbols(response)
            }

            _chatMessages.value = _chatMessages.value + ChatMessage(content = reply, isUser = false)
            _isChatLoading.value = false
        }
    }

    private fun cleanMarkdownSymbols(text: String): String {
        var cleaned = text
        // Remove markdown headers like ### or ## or # at line beginnings
        cleaned = cleaned.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        // Convert asterisk bullets "* item" to dash bullets "- item"
        cleaned = cleaned.replace(Regex("(?m)^\\s*\\*\\s+"), "- ")
        // Completely strip all remaining asterisks and hashes
        cleaned = cleaned.replace("*", "").replace("#", "")
        return cleaned.trim()
    }

    fun clearChatHistory() {
        _chatMessages.value = listOf(
            ChatMessage(
                content = "Đã làm mới cuộc trò chuyện. Hãy đặt câu hỏi bất kỳ, tôi sẽ giải đáp nhanh và đúng trọng tâm! 🎯",
                isUser = false
            )
        )
    }

    // --- CHAT AI GIAO TIẾP (COMMUNICATION CHAT) ---
    private val _commPartnerGender = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getString("comm_partner_gender", "female") ?: "female"
    )
    val commPartnerGender = _commPartnerGender.asStateFlow()

    fun updateCommPartnerGender(gender: String) {
        _commPartnerGender.value = gender
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("comm_partner_gender", gender)
            .apply()
        resetCommHistory()
    }

    private val _commPitchMultiplier = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getFloat("comm_pitch_multiplier", 1.0f)
    )
    val commPitchMultiplier = _commPitchMultiplier.asStateFlow()

    private val _commSpeedMultiplier = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getFloat("comm_speed_multiplier", 1.0f)
    )
    val commSpeedMultiplier = _commSpeedMultiplier.asStateFlow()

    private val _commSpeechLanguage = MutableStateFlow(
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getString("comm_speech_language", "auto") ?: "auto"
    )
    val commSpeechLanguage = _commSpeechLanguage.asStateFlow()

    fun updateCommPitchMultiplier(value: Float) {
        _commPitchMultiplier.value = value
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putFloat("comm_pitch_multiplier", value)
            .apply()
    }

    fun updateCommSpeedMultiplier(value: Float) {
        _commSpeedMultiplier.value = value
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putFloat("comm_speed_multiplier", value)
            .apply()
    }

    fun updateCommSpeechLanguage(language: String) {
        _commSpeechLanguage.value = language
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("comm_speech_language", language)
            .apply()
    }

    private val _commMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val commMessages = _commMessages.asStateFlow()

    private val _isCommLoading = MutableStateFlow(false)
    val isCommLoading = _isCommLoading.asStateFlow()

    fun speakWithGender(text: String, isFemale: Boolean) {
        if (!isTtsInitialized || tts == null) return
        try {
            val isVi = isVietnameseText(text)
            val locale = if (isVi) Locale("vi", "VN") else Locale("en", "US")
            setNaturalHumanVoice(locale, isFemale)
            
            // Keep base pitch close to natural 1.0f without artificial digital shifting to preserve real human acoustics
            val basePitch = if (isFemale) 1.02f else 0.98f
            val baseRate = 1.05f // Brisk, responsive conversational speed
            
            val finalPitch = basePitch * _commPitchMultiplier.value
            val finalRate = baseRate * _commSpeedMultiplier.value
            
            tts?.setPitch(finalPitch.coerceIn(0.5f, 2.0f))
            tts?.setSpeechRate(finalRate.coerceIn(0.5f, 2.0f))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UtteranceId_" + System.currentTimeMillis())
    }

    fun resetCommHistory() {
        val isFemale = _commPartnerGender.value == "female"
        val greeting = if (isFemale) {
            "Hi there! I'm Emma, your English speaking partner. How's your day going? Let's talk! If you make any mistakes, I'll help you correct them! 😊"
        } else {
            "Hello! My name is Alex, and I'm thrilled to chat with you today. What would you like to talk about? Don't worry about mistakes, I'll guide you! ⚡"
        }
        _commMessages.value = listOf(
            ChatMessage(
                content = greeting,
                isUser = false
            )
        )
        speakWithGender(greeting, isFemale)
    }

    fun sendCommMessage(text: String, isVoice: Boolean = false) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(content = text, isUser = true, isVoiceInput = isVoice)
        _commMessages.value = _commMessages.value + userMsg
        _isCommLoading.value = true

        viewModelScope.launch {
            var apiKey = _userGeminiApiKey.value
            var usingPass = false
            if (apiKey.isBlank()) {
                if (_aiPassCount.value > 0) {
                    usingPass = true
                    apiKey = com.example.BuildConfig.GEMINI_API_KEY
                } else {
                    val errorMsg = ChatMessage(
                        content = "⚠️ Bạn chưa cấu hình API Key và không có Vé AI Gemini. Vui lòng vào Cài đặt để thêm API Key Gemini cá nhân, hoặc đổi Xu Vàng lấy Vé AI Gemini tại Cửa Hàng (nhấp vào biểu tượng Xu trên trang chủ)! 🪙",
                        isUser = false
                    )
                    _commMessages.value = _commMessages.value + errorMsg
                    _isCommLoading.value = false
                    return@launch
                }
            }

            val isFemale = _commPartnerGender.value == "female"
            val partnerName = if (isFemale) "Emma" else "Alex"
            val partnerGenderStr = if (isFemale) "Female (Nữ)" else "Male (Nam)"

            val systemPrompt = """
                You are a real-life conversation partner named $partnerName ($partnerGenderStr).
                You are having a direct voice conversation with a Vietnamese English learner.
                
                CRITICAL REAL-TIME SPEED & VOICE RULES:
                1. EXTREMELY BRIEF REPLIES: For instant oral conversational speed, your reply MUST be maximum 1 or 2 short sentences (under 12 words total). Never write paragraphs or long explanations.
                2. DYNAMIC LANGUAGE MATCHING:
                   - If user speaks Vietnamese: reply naturally and warmly in Vietnamese (under 12 words). Do NOT correct grammar for Vietnamese inputs.
                   - If user speaks English: reply naturally in English (under 12 words).
                
                3. ENGLISH CORRECTION:
                   - For English inputs only: if user makes a mistake, append:
                   [[CORRECTION]]
                   - Sửa nhanh: [câu đúng] (Lý do cực ngắn dưới 10 chữ)
                   [[/CORRECTION]]
                   - If no mistakes in English, do NOT append [[CORRECTION]].
            """.trimIndent()

            val systemInstructionContent = com.example.api.Content(
                parts = listOf(com.example.api.Part(text = systemPrompt))
            )

            val chatHistory = _commMessages.value
                .filter { it.content.isNotBlank() && !it.content.startsWith("⚠️") }
                .map { msg ->
                    val cleanContent = if (!msg.isUser && msg.content.contains("[[CORRECTION]]")) {
                        msg.content.substringBefore("[[CORRECTION]]").trim()
                    } else {
                        msg.content
                    }
                    com.example.api.Content(
                        parts = listOf(com.example.api.Part(text = cleanContent)),
                        role = if (msg.isUser) "user" else "model"
                    )
                }

            // Keep only the last 2 message turns (1 previous exchange) for ultra-low latency & near-instant response speeds
            val recentChatHistory = chatHistory.takeLast(2)

            val response = com.example.api.GeminiClient.getGeminiFastChatResponse(
                history = recentChatHistory,
                customApiKey = apiKey,
                systemInstruction = systemInstructionContent
            )
            
            val finalReply: String
            var correctionText: String? = null

            if (response == "API_KEY_MISSING") {
                finalReply = "⚠️ API Key hệ thống chưa sẵn sàng. Bạn vui lòng cấu hình API Key cá nhân trong phần Cài đặt để tiếp tục trò chuyện."
            } else if (response.startsWith("ERROR:")) {
                finalReply = "❌ Lỗi khi gọi Gemini API: ${response.substringAfter("ERROR:")}\n\nVui lòng kiểm tra kết nối mạng hoặc API Key."
            } else {
                if (usingPass) {
                    useAiPass()
                }
                
                val startIndex = response.indexOf("[[CORRECTION]]")
                val endIndex = response.indexOf("[[/CORRECTION]]")
                
                if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                    finalReply = response.substring(0, startIndex).trim()
                    correctionText = response.substring(startIndex + "[[CORRECTION]]".length, endIndex).trim()
                } else {
                    finalReply = response.trim()
                }
            }

            _commMessages.value = _commMessages.value + ChatMessage(
                content = finalReply,
                isUser = false,
                correction = correctionText,
                isVoiceInput = isVoice
            )
            _isCommLoading.value = false

            // ONLY automatically speak if the user initiated the interaction via voice/mic
            if (isVoice && !finalReply.startsWith("⚠️") && !finalReply.startsWith("❌")) {
                speakWithGender(finalReply, isFemale)
            }
        }
    }

    sealed interface AiState {
        object Idle : AiState
        object Generating : AiState
        data class Success(val message: String) : AiState
        data class Error(val error: String) : AiState
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedTopic(topic: String) {
        _selectedTopic.value = topic
    }

    fun setSelectedLevel(level: String) {
        _selectedLevel.value = level
    }

    fun addWord(word: Word) {
        viewModelScope.launch {
            repository.insertWord(word)
            // Award simple XP for adding words
            awardXpAndCoins(15, 2)
        }
    }

    fun updateWord(word: Word) {
        viewModelScope.launch {
            repository.updateWord(word)
        }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            repository.deleteWord(word)
        }
    }

    fun deleteAllWords() {
        viewModelScope.launch {
            repository.deleteAllWords()
        }
    }

    fun toggleFavorite(word: Word) {
        viewModelScope.launch {
            repository.updateWord(word.copy(isFavorite = !word.isFavorite))
        }
    }

    fun toggleMastered(word: Word) {
        viewModelScope.launch {
            val newStatus = if (word.learnedStatus == "MASTERED") "NEW" else "MASTERED"
            repository.updateWord(word.copy(learnedStatus = newStatus))
        }
    }

    // Spaced Repetition grade
    fun gradeWord(wordId: Int, grade: Int) {
        viewModelScope.launch {
            repository.gradeWord(wordId, grade)
            // Award rewards: "Again" (+5 XP), "Hard" (+10 XP), "Good" (+15 XP, +1 Coin), "Easy" (+20 XP, +3 Coins)
            val xpReward = when (grade) {
                1 -> 5
                2 -> 10
                3 -> 15
                4 -> 20
                else -> 10
            }
            val coinReward = when (grade) {
                1 -> 0
                2 -> 1
                3 -> 2
                4 -> 4
                else -> 1
            }
            awardXpAndCoins(xpReward, coinReward)
            updateStreak()
        }
    }

    fun syncLevelWithMasteredWords(masteredCount: Int) {
        val profile = userProfile.value
        val targetLevel = (masteredCount / 5) + 1
        if (profile.level != targetLevel) {
            viewModelScope.launch {
                var newCoins = profile.coins
                if (targetLevel > profile.level) {
                    newCoins += (targetLevel - profile.level) * 20
                }
                repository.saveUserProfile(profile.copy(level = targetLevel, coins = newCoins))
            }
        }
    }

    // Gamification rewards helper
    fun awardXpAndCoins(xpAdded: Int, coinsAdded: Int) {
        viewModelScope.launch {
            val profile = userProfile.value
            var finalXpAdded = xpAdded
            if (_boosterActionsLeft.value > 0) {
                finalXpAdded = xpAdded * 2
                _boosterActionsLeft.value -= 1
                sharedPrefs.edit().putInt("booster_actions_left", _boosterActionsLeft.value).apply()
            }
            val newXp = profile.xp + finalXpAdded
            val newCoins = profile.coins + coinsAdded

            repository.saveUserProfile(profile.copy(
                xp = newXp,
                coins = newCoins
            ))
        }
    }

    private fun updateStreak() {
        viewModelScope.launch {
            val profile = userProfile.value
            val today = System.currentTimeMillis()
            val lastActive = profile.lastActiveDate
            
            // Diff in days
            val diffMs = today - lastActive
            val diffDays = (diffMs / (1000 * 60 * 60 * 24)).toInt()
            
            var newStreak = profile.streak
            if (diffDays == 1) {
                // Active consecutive day
                newStreak += 1
            } else if (diffDays > 1) {
                // Streak broken, reset
                newStreak = 1
            } else if (newStreak == 0) {
                newStreak = 1
            }
            
            repository.saveUserProfile(profile.copy(
                streak = newStreak,
                lastActiveDate = today
            ))
        }
    }

    fun resetAiState() {
        _aiState.value = AiState.Idle
    }

    // AI custom sentence generation
    fun generateAiExample(word: Word) {
        _aiState.value = AiState.Generating
        viewModelScope.launch {
            val prompt = """
                Generate a simple, natural English example sentence and its Vietnamese translation for the English word '${word.word}' (pronunciation: '${word.pronunciation}', meaning: '${word.meaning}').
                Return a JSON object with properties:
                {
                  "example": "the generated example sentence",
                  "translation": "bản dịch tiếng Việt"
                }
                Do not include markdown markers or any other text.
            """.trimIndent()

            val customKey = _userGeminiApiKey.value
            val response = GeminiClient.getGeminiResponse(prompt, customApiKey = customKey.ifBlank { null }, jsonMode = true)
            if (response == "API_KEY_MISSING") {
                _aiState.value = AiState.Error("API Key chưa được cấu hình. Vui lòng thêm API Key của bạn hoặc GEMINI_API_KEY trong file .env hoặc Cài đặt.")
                return@launch
            }
            if (response.startsWith("ERROR:")) {
                _aiState.value = AiState.Error(response)
                return@launch
            }

            try {
                val json = JSONObject(response)
                val example = json.getString("example")
                val translation = json.getString("translation")

                repository.updateWord(word.copy(
                    example = example,
                    translation = translation
                ))

                _aiState.value = AiState.Success("Đã cập nhật câu ví dụ cho từ '${word.word}' bằng AI thành công!")
            } catch (e: Exception) {
                _aiState.value = AiState.Error("Không thể phân tích phản hồi AI: ${e.localizedMessage}")
            }
        }
    }

    // AI custom deck generation
    fun generateThemeDeck(topic: String, level: String, count: Int = 5) {
        _aiState.value = AiState.Generating
        viewModelScope.launch {
            val prompt = """
                Generate exactly $count English vocabulary words for the topic '$topic' at '$level' level.
                For each word, return: word, pronunciation, meaning in Vietnamese, example sentence in English, and Vietnamese translation of that example.
                Return only a JSON array of objects like this:
                [
                  {
                    "word": "innovation",
                    "pronunciation": "/ˌɪn.əˈveɪ.ʃən/",
                    "meaning": "sự đổi mới, sáng kiến",
                    "example": "Technical innovation is key to our success.",
                    "translation": "Sự đổi mới kỹ thuật là chìa khóa dẫn đến thành công của chúng tôi."
                  }
                ]
                Ensure the response contains only the JSON array. No markdown backticks.
            """.trimIndent()

            val customKey = _userGeminiApiKey.value
            val response = GeminiClient.getGeminiResponse(prompt, customApiKey = customKey.ifBlank { null }, jsonMode = true)
            if (response == "API_KEY_MISSING") {
                _aiState.value = AiState.Error("API Key chưa được cấu hình. Vui lòng cấu hình GEMINI_API_KEY trong Cài đặt.")
                return@launch
            }
            if (response.startsWith("ERROR:")) {
                _aiState.value = AiState.Error(response)
                return@launch
            }

            try {
                val jsonArray = JSONArray(response)
                val newWords = mutableListOf<Word>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    newWords.add(
                        Word(
                            word = obj.getString("word"),
                            pronunciation = obj.getString("pronunciation"),
                            meaning = obj.getString("meaning"),
                            example = obj.getString("example"),
                            translation = obj.getString("translation"),
                            topic = topic,
                            level = level
                        )
                    )
                }

                repository.insertWords(newWords)
                _aiState.value = AiState.Success("Đã sinh thành công ${newWords.size} từ vựng về chủ đề '$topic' bằng AI!")
                awardXpAndCoins(50, 10) // Extra reward for creating/importing custom deck!
            } catch (e: Exception) {
                _aiState.value = AiState.Error("Không thể phân tích phản hồi AI: ${e.localizedMessage}. Response was: $response")
            }
        }
    }

    // --- GRAMMAR PERSISTENCE ---
    private val _completedGrammarTopics = MutableStateFlow<Set<String>>(
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getStringSet("completed_grammar_topics", emptySet()) ?: emptySet()
    )
    val completedGrammarTopics = _completedGrammarTopics.asStateFlow()

    fun toggleGrammarTopicCompleted(topicId: String) {
        val currentSet = _completedGrammarTopics.value.toMutableSet()
        if (currentSet.contains(topicId)) {
            currentSet.remove(topicId)
        } else {
            currentSet.add(topicId)
            // Reward some points/coins for learning grammar!
            awardXpAndCoins(30, 5)
        }
        _completedGrammarTopics.value = currentSet
        getApplication<Application>()
            .getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putStringSet("completed_grammar_topics", currentSet)
            .apply()
    }

    private val _grammarCheckResult = MutableStateFlow<String>("")
    val grammarCheckResult = _grammarCheckResult.asStateFlow()

    private val _isGrammarChecking = MutableStateFlow(false)
    val isGrammarChecking = _isGrammarChecking.asStateFlow()

    fun checkGrammar(sentence: String) {
        if (sentence.isBlank()) return
        _isGrammarChecking.value = true
        _grammarCheckResult.value = ""
        viewModelScope.launch {
            val prompt = """
                Analyze the following English sentence for grammatical errors, spelling mistakes, typos, and style phrasing:
                Sentence: "$sentence"
                
                Provide your analysis in Vietnamese. Address the following:
                1. Is it correct? (Dùng từ 'Chính xác' hoặc 'Có lỗi sai' ở dòng đầu tiên)
                2. If incorrect, what are the errors? Explain clearly in Vietnamese.
                3. Provide the corrected/improved version(s) in English.
                4. Briefly explain key grammar points used in this sentence.
                Keep the formatting extremely clean, using bullet points and nice spacing, professional, and friendly.
            """.trimIndent()

            val customKey = _userGeminiApiKey.value
            val response = GeminiClient.getGeminiResponse(prompt, customApiKey = customKey.ifBlank { null }, jsonMode = false)
            if (response == "API_KEY_MISSING") {
                _grammarCheckResult.value = "API_KEY_MISSING"
            } else if (response.startsWith("ERROR:")) {
                _grammarCheckResult.value = "Lỗi khi kiểm tra ngữ pháp: ${response.removePrefix("ERROR:")}"
            } else {
                _grammarCheckResult.value = response
            }
            _isGrammarChecking.value = false
        }
    }
    
    fun clearGrammarCheckResult() {
        _grammarCheckResult.value = ""
    }

    // --- CUSTOM DYNAMIC GRAMMAR GENERATION BY AI ---
    private val _customGrammarTopics = MutableStateFlow<List<GrammarTopic>>(emptyList())
    val customGrammarTopics = _customGrammarTopics.asStateFlow()

    private val _isGeneratingGrammar = MutableStateFlow(false)
    val isGeneratingGrammar = _isGeneratingGrammar.asStateFlow()

    private val _grammarGenerationError = MutableStateFlow<String?>(null)
    val grammarGenerationError = _grammarGenerationError.asStateFlow()

    fun loadCustomGrammarTopics() {
        val prefs = getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
        val jsonStr = prefs.getString("custom_grammar_topics", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val list = mutableListOf<GrammarTopic>()
                val jsonArray = org.json.JSONArray(jsonStr)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    
                    val examplesArr = obj.getJSONArray("examples")
                    val examplesList = mutableListOf<Pair<String, String>>()
                    for (j in 0 until examplesArr.length()) {
                        val pObj = examplesArr.getJSONObject(j)
                        examplesList.add(pObj.getString("first") to pObj.getString("second"))
                    }

                    val quizArr = obj.getJSONArray("quiz")
                    val quizList = mutableListOf<GrammarQuestion>()
                    for (k in 0 until quizArr.length()) {
                        val qObj = quizArr.getJSONObject(k)
                        val optArr = qObj.getJSONArray("options")
                        val optList = mutableListOf<String>()
                        for (o in 0 until optArr.length()) {
                            optList.add(optArr.getString(o))
                        }
                        quizList.add(
                            GrammarQuestion(
                                question = qObj.getString("question"),
                                options = optList,
                                correctAnswerIndex = qObj.getInt("correctAnswerIndex"),
                                explanation = qObj.getString("explanation")
                            )
                        )
                    }

                    list.add(
                        GrammarTopic(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            description = obj.getString("description"),
                            level = obj.getString("level"),
                            formula = obj.getString("formula"),
                            usage = obj.getString("usage"),
                            examples = examplesList,
                            quiz = quizList
                        )
                    )
                }
                _customGrammarTopics.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun saveCustomGrammarTopics(list: List<GrammarTopic>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (topic in list) {
                val obj = org.json.JSONObject()
                obj.put("id", topic.id)
                obj.put("title", topic.title)
                obj.put("description", topic.description)
                obj.put("level", topic.level)
                obj.put("formula", topic.formula)
                obj.put("usage", topic.usage)

                val examplesArr = org.json.JSONArray()
                for (p in topic.examples) {
                    val pObj = org.json.JSONObject()
                    pObj.put("first", p.first)
                    pObj.put("second", p.second)
                    examplesArr.put(pObj)
                }
                obj.put("examples", examplesArr)

                val quizArr = org.json.JSONArray()
                for (q in topic.quiz) {
                    val qObj = org.json.JSONObject()
                    qObj.put("question", q.question)
                    
                    val optArr = org.json.JSONArray()
                    for (opt in q.options) {
                        optArr.put(opt)
                    }
                    qObj.put("options", optArr)
                    qObj.put("correctAnswerIndex", q.correctAnswerIndex)
                    qObj.put("explanation", q.explanation)
                    quizArr.put(qObj)
                }
                obj.put("quiz", quizArr)

                jsonArray.put(obj)
            }
            
            getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
                .edit()
                .putString("custom_grammar_topics", jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun generateCustomGrammarTopic(topicName: String, onSuccess: (GrammarTopic) -> Unit = {}) {
        if (topicName.isBlank()) return
        _isGeneratingGrammar.value = true
        _grammarGenerationError.value = null
        viewModelScope.launch {
            val prompt = """
                Bạn là một giáo viên tiếng Anh bản xứ có kinh nghiệm biên soạn giáo trình ngữ pháp. 
                Hãy tạo một bài học ngữ pháp chi tiết theo chủ đề sau: "$topicName".
                
                Yêu cầu trả về một đối tượng JSON duy nhất mô tả bài học ngữ pháp này với cấu trúc chính xác như sau:
                {
                  "title": "Tiêu đề tiếng Việt của bài học, ví dụ: Thì Hiện Tại Đơn (Present Simple)",
                  "description": "Mô tả ngắn gọn về bài học bằng tiếng Việt",
                  "level": "Cấp độ (Beginner hoặc Intermediate hoặc Advanced)",
                  "formula": "Công thức ngắn gọn và rõ ràng của ngữ pháp này, dùng dấu xuống dòng \n để ngắt dòng",
                  "usage": "Các cách dùng chính, viết dưới dạng các gạch đầu dòng và xuống dòng \n",
                  "examples": [
                    {
                      "english": "Ví dụ tiếng Anh 1",
                      "vietnamese": "Dịch nghĩa tiếng Việt 1"
                    },
                    {
                      "english": "Ví dụ tiếng Anh 2",
                      "vietnamese": "Dịch nghĩa tiếng Việt 2"
                    },
                    {
                      "english": "Ví dụ tiếng Anh 3",
                      "vietnamese": "Dịch nghĩa tiếng Việt 3"
                    }
                  ],
                  "quiz": [
                    {
                      "question": "Câu hỏi trắc nghiệm tiếng Anh điền vào chỗ trống, ví dụ: She _______ (go) to school everyday.",
                      "options": ["go", "goes", "going", "went"],
                      "correctAnswerIndex": 1,
                      "explanation": "Giải thích chi tiết bằng tiếng Việt tại sao chọn đáp án này"
                    },
                    {
                      "question": "Câu hỏi trắc nghiệm tiếng Anh 2...",
                      "options": ["A", "B", "C", "D"],
                      "correctAnswerIndex": 0,
                      "explanation": "Giải thích..."
                    },
                    {
                      "question": "Câu hỏi trắc nghiệm tiếng Anh 3...",
                      "options": ["A", "B", "C", "D"],
                      "correctAnswerIndex": 2,
                      "explanation": "Giải thích..."
                    }
                  ]
                }
                
                Lưu ý:
                1. Hãy biên soạn nội dung cực kỳ chính xác và dễ hiểu bằng tiếng Việt.
                2. Chắc chắn trả về định dạng JSON hợp lệ, không chứa markdown backticks hay bất kỳ ký tự thừa nào ngoài JSON.
            """.trimIndent()

            val customKey = _userGeminiApiKey.value
            val response = GeminiClient.getGeminiResponse(prompt, customApiKey = customKey.ifBlank { null }, jsonMode = true)
            
            if (response == "API_KEY_MISSING") {
                _grammarGenerationError.value = "Chưa cấu hình API Key. Vui lòng thêm Gemini API Key trong Cài đặt."
                _isGeneratingGrammar.value = false
                return@launch
            }
            if (response.startsWith("ERROR:")) {
                _grammarGenerationError.value = "Lỗi từ Gemini: ${response.removePrefix("ERROR:")}"
                _isGeneratingGrammar.value = false
                return@launch
            }

            try {
                // Parse generated content
                val obj = JSONObject(response)
                
                val examplesArr = obj.getJSONArray("examples")
                val examplesList = mutableListOf<Pair<String, String>>()
                for (j in 0 until examplesArr.length()) {
                    val pObj = examplesArr.getJSONObject(j)
                    examplesList.add(pObj.getString("english") to pObj.getString("vietnamese"))
                }

                val quizArr = obj.getJSONArray("quiz")
                val quizList = mutableListOf<GrammarQuestion>()
                for (k in 0 until quizArr.length()) {
                    val qObj = quizArr.getJSONObject(k)
                    val optArr = qObj.getJSONArray("options")
                    val optList = mutableListOf<String>()
                    for (o in 0 until optArr.length()) {
                        optList.add(optArr.getString(o))
                    }
                    quizList.add(
                        GrammarQuestion(
                            question = qObj.getString("question"),
                            options = optList,
                            correctAnswerIndex = qObj.getInt("correctAnswerIndex"),
                            explanation = qObj.getString("explanation")
                        )
                    )
                }

                val uniqueId = "custom_${System.currentTimeMillis()}"
                val generatedTopic = GrammarTopic(
                    id = uniqueId,
                    title = obj.getString("title"),
                    description = obj.getString("description"),
                    level = obj.getString("level"),
                    formula = obj.getString("formula"),
                    usage = obj.getString("usage"),
                    examples = examplesList,
                    quiz = quizList
                )

                val updatedList = _customGrammarTopics.value.toMutableList()
                updatedList.add(generatedTopic)
                _customGrammarTopics.value = updatedList
                saveCustomGrammarTopics(updatedList)

                onSuccess(generatedTopic)
            } catch (e: Exception) {
                e.printStackTrace()
                _grammarGenerationError.value = "Không thể phân tích phản hồi AI: ${e.localizedMessage}"
            } finally {
                _isGeneratingGrammar.value = false
            }
        }
    }

    // --- APP VERSION & UPDATE PERSISTENCE ---
    private val _appVersion = MutableStateFlow<String>(
        getApplication<Application>().getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .getString("app_version", "v2.4.2") ?: "v2.4.2"
    )
    val appVersion = _appVersion.asStateFlow()

    fun performUpdateTo(newVersion: String) {
        getApplication<Application>()
            .getSharedPreferences("app_prefs", Application.MODE_PRIVATE)
            .edit()
            .putString("app_version", newVersion)
            .apply()
        _appVersion.value = newVersion
        // Reward some points/coins for updating!
        awardXpAndCoins(100, 20)
    }

    // Excel CSV Import
    fun importFromCsvText(csvText: String): ImportResult = try {
        val lines = csvText.trim().split("\n")
        var successCount = 0
        var duplicateCount = 0
        var errorCount = 0
        
        val existingWords = allWords.value.map { it.word.lowercase() }.toSet()
        val importedWords = mutableListOf<Word>()

        for (line in lines) {
            val parts = line.split("|").map { it.trim() }
            if (parts.size >= 3) {
                val eng = parts[0]
                val ipa = parts.getOrNull(1) ?: ""
                val meaning = parts.getOrNull(2) ?: ""
                val example = parts.getOrNull(3) ?: ""
                val translation = parts.getOrNull(4) ?: ""
                val topic = parts.getOrNull(5) ?: "Imported"
                val level = parts.getOrNull(6) ?: "Beginner"

                if (eng.isEmpty() || meaning.isEmpty()) {
                    errorCount++
                    continue
                }

                if (existingWords.contains(eng.lowercase())) {
                    duplicateCount++
                    continue
                }

                importedWords.add(
                    Word(
                        word = eng,
                        pronunciation = ipa,
                        meaning = meaning,
                        example = example,
                        translation = translation,
                        topic = topic,
                        level = level
                    )
                )
                successCount++
            } else if (line.isNotBlank()) {
                errorCount++
            }
        }

        if (importedWords.isNotEmpty()) {
            viewModelScope.launch {
                repository.insertWords(importedWords)
                awardXpAndCoins(successCount * 5, successCount)
            }
        }

        ImportResult(successCount, duplicateCount, errorCount)
    } catch (e: Exception) {
        ImportResult(0, 0, 1, errorMsg = e.localizedMessage)
    }
}

data class ImportResult(
    val success: Int,
    val duplicates: Int,
    val errors: Int,
    val errorMsg: String? = null
)
