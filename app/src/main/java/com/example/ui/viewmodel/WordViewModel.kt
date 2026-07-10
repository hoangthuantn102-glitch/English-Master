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
    val timestamp: Long = System.currentTimeMillis()
)

class WordViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {
    private val repository: WordRepository
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WordRepository(database.wordDao())
        tts = TextToSpeech(application, this)
        
        // Populate initial data if empty
        viewModelScope.launch {
            repository.populateInitialDataIfNeeded()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsInitialized = true
        }
    }

    fun speak(text: String) {
        if (isTtsInitialized) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
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
                content = "Xin chào! Tôi là Trợ lý học tập AI. Bạn có thể hỏi tôi bất kỳ điều gì về từ vựng, ngữ pháp tiếng Anh, dịch thuật hoặc cách học hiệu quả. Tôi rất vui được đồng hành cùng bạn! 😊",
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
            val apiKey = _userGeminiApiKey.value
            if (apiKey.isBlank()) {
                val errorMsg = ChatMessage(
                    content = "⚠️ Bạn chưa cấu hình API Key. Vui lòng vào Cài đặt để thêm API Key Gemini cá nhân của bạn để sử dụng tính năng này.",
                    isUser = false
                )
                _chatMessages.value = _chatMessages.value + errorMsg
                _isChatLoading.value = false
                return@launch
            }

            // Convert chat history into com.example.api.Content sequence
            val history = _chatMessages.value.filter { it.content.isNotBlank() && !it.content.startsWith("⚠️") }.map { msg ->
                com.example.api.Content(
                    parts = listOf(com.example.api.Part(text = msg.content)),
                    role = if (msg.isUser) "user" else "model"
                )
            }

            val response = com.example.api.GeminiClient.getGeminiChatResponse(history, apiKey)
            
            val reply = if (response == "API_KEY_MISSING") {
                "⚠️ API Key không hợp lệ hoặc chưa được cấu hình chính xác. Vui lòng kiểm tra lại trong phần Cài đặt."
            } else if (response.startsWith("ERROR:")) {
                "❌ Lỗi khi gọi Gemini API: ${response.substringAfter("ERROR:")}\n\nVui lòng kiểm tra kết nối mạng hoặc tính hợp lệ của API Key."
            } else {
                response
            }

            _chatMessages.value = _chatMessages.value + ChatMessage(content = reply, isUser = false)
            _isChatLoading.value = false
        }
    }

    fun clearChatHistory() {
        _chatMessages.value = listOf(
            ChatMessage(
                content = "Đã xóa lịch sử trò chuyện. Tôi sẵn sàng hỗ trợ bạn câu hỏi mới! 📝",
                isUser = false
            )
        )
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

    // Gamification rewards helper
    fun awardXpAndCoins(xpAdded: Int, coinsAdded: Int) {
        viewModelScope.launch {
            val profile = userProfile.value
            var newXp = profile.xp + xpAdded
            var newCoins = profile.coins + coinsAdded
            var newLevel = profile.level
            
            // Level formula: Level = (XP / 100) + 1
            val targetLevel = (newXp / 100) + 1
            if (targetLevel > newLevel) {
                newLevel = targetLevel
                // Level up bonus
                newCoins += 20
            }
            
            repository.saveUserProfile(profile.copy(
                xp = newXp,
                coins = newCoins,
                level = newLevel
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
