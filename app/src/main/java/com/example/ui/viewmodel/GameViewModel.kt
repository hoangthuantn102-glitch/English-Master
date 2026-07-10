package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.UserProfile
import com.example.data.Word
import com.example.data.WordRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Locale

class GameViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {
    private val repository: WordRepository
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        val database = AppDatabase.getDatabase(application)
        repository = WordRepository(database.wordDao())
        tts = TextToSpeech(application, this)
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

    // Load user profile to update XP/Coins
    val userProfile: StateFlow<UserProfile> = repository.userProfile
        .map { it ?: UserProfile() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    // All available words to feed into games
    private val _words = MutableStateFlow<List<Word>>(emptyList())
    val words = _words.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allWords.collect {
                _words.value = it
            }
        }
    }

    // --- GAME STATE VARIABLES ---
    private val _currentGameState = MutableStateFlow<GameState>(GameState.Menu)
    val currentGameState = _currentGameState.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score = _score.asStateFlow()

    private val _earnedXp = MutableStateFlow(0)
    val earnedXp = _earnedXp.asStateFlow()

    private val _earnedCoins = MutableStateFlow(0)
    val earnedCoins = _earnedCoins.asStateFlow()

    // General timer for games (e.g., Speed Quiz, Quiz Challenge)
    private val _timeLeft = MutableStateFlow(0)
    val timeLeft = _timeLeft.asStateFlow()
    private var timerJob: Job? = null

    // Game 1: Matching pairs state
    data class MatchingPair(val id: Int, val text: String, val isEnglish: Boolean, val isMatched: Boolean = false)
    private val _matchingPairs = MutableStateFlow<List<MatchingPair>>(emptyList())
    val matchingPairs = _matchingPairs.asStateFlow()
    private val _selectedPairItem = MutableStateFlow<MatchingPair?>(null)
    val selectedPairItem = _selectedPairItem.asStateFlow()

    // Game 2 & 3: Multiple choice question state
    data class QuizQuestion(val word: Word, val correctAnswer: String, val options: List<String>)
    private val _quizQuestions = MutableStateFlow<List<QuizQuestion>>(emptyList())
    val quizQuestions = _quizQuestions.asStateFlow()
    private val _currentQuestionIndex = MutableStateFlow(0)
    val currentQuestionIndex = _currentQuestionIndex.asStateFlow()
    private val _selectedOption = MutableStateFlow<String?>(null)
    val selectedOption = _selectedOption.asStateFlow()

    // Game 4 & 5: Word inputs (Fill in blanks / Scramble)
    private val _currentWord = MutableStateFlow<Word?>(null)
    val currentWord = _currentWord.asStateFlow()
    private val _scrambledLettersOrBlanks = MutableStateFlow("")
    val scrambledLettersOrBlanks = _scrambledLettersOrBlanks.asStateFlow()
    private val _userInput = MutableStateFlow("")
    val userInput = _userInput.asStateFlow()
    private val _isAnswerCorrect = MutableStateFlow<Boolean?>(null)
    val isAnswerCorrect = _isAnswerCorrect.asStateFlow()

    // Game 7: Memory Card Grid
    data class MemoryCard(val id: Int, val text: String, val isEnglish: Boolean, val matchId: Int, val isFaceUp: Boolean = false, val isMatched: Boolean = false)
    private val _memoryCards = MutableStateFlow<List<MemoryCard>>(emptyList())
    val memoryCards = _memoryCards.asStateFlow()
    private val _firstFlippedCard = MutableStateFlow<MemoryCard?>(null)

    // Game 8: Hangman state
    private val _hangmanGuessedLetters = MutableStateFlow<Set<Char>>(emptySet())
    val hangmanGuessedLetters = _hangmanGuessedLetters.asStateFlow()
    private val _hangmanWrongAttempts = MutableStateFlow(0)
    val hangmanWrongAttempts = _hangmanWrongAttempts.asStateFlow()

    // Game 10: Race milestones
    data class Milestone(val id: Int, val name: String, val requiredXp: Int, val isUnlocked: Boolean, val rewardCoins: Int)
    val milestones = userProfile.map { profile ->
        listOf(
            Milestone(1, "Khởi Đầu Thần Tốc", 50, profile.xp >= 50, 10),
            Milestone(2, "Anh Hùng Từ Vựng", 150, profile.xp >= 150, 15),
            Milestone(3, "Chiến Binh Bền Bỉ", 300, profile.xp >= 300, 20),
            Milestone(4, "Bậc Thầy Ngôn Ngữ", 500, profile.xp >= 500, 30),
            Milestone(5, "Huyền Thoại English", 1000, profile.xp >= 1000, 50)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    sealed interface GameState {
        object Menu : GameState
        object PairingGame : GameState
        object MultipleChoiceGame : GameState
        object ListeningGame : GameState
        object FillBlanksGame : GameState
        object WordScrambleGame : GameState
        object SpeedQuizGame : GameState
        object MemoryCardsGame : GameState
        object HangmanGame : GameState
        object QuizChallengeGame : GameState
        object RaceGame : GameState
        object PronunciationGame : GameState
        data class GameOver(val title: String, val message: String) : GameState
    }

    // --- GAME INITIALIZATION & ENGINE ---

    fun backToMenu() {
        timerJob?.cancel()
        _currentGameState.value = GameState.Menu
        _score.value = 0
        _earnedXp.value = 0
        _earnedCoins.value = 0
    }

    private fun checkEnoughWords(required: Int = 4): Boolean {
        return _words.value.size >= required
    }

    // Helper to finish game and reward user
    private fun endGame(title: String, scoreGained: Int, xpMultiplier: Int, coinMultiplier: Int) {
        timerJob?.cancel()
        val xpReward = scoreGained * xpMultiplier
        val coinsReward = scoreGained * coinMultiplier
        
        _earnedXp.value = xpReward
        _earnedCoins.value = coinsReward
        _currentGameState.value = GameState.GameOver(
            title = title,
            message = "Bạn đã hoàn thành phần chơi xuất sắc! Đạt được $scoreGained điểm. Nhận ngay +$xpReward XP và +$coinsReward Coins!"
        )

        viewModelScope.launch {
            val profile = userProfile.value
            var newXp = profile.xp + xpReward
            var newCoins = profile.coins + coinsReward
            var newLevel = profile.level
            
            val targetLevel = (newXp / 100) + 1
            if (targetLevel > newLevel) {
                newLevel = targetLevel
                newCoins += 20 // Level up bonus
            }

            repository.saveUserProfile(profile.copy(
                xp = newXp,
                coins = newCoins,
                level = newLevel
            ))
        }
    }

    // --- GAME 1: TAP TO PAIR / MATCHING ---
    fun startPairingGame() {
        if (!checkEnoughWords(4)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Hãy thêm ít nhất 4 từ vựng để bắt đầu chơi trò này nhé!")
            return
        }
        val selected = _words.value.shuffled().take(4)
        val engItems = selected.map { MatchingPair(it.id, it.word, isEnglish = true) }
        val vietItems = selected.map { MatchingPair(it.id, it.meaning, isEnglish = false) }
        _matchingPairs.value = (engItems + vietItems).shuffled()
        _score.value = 0
        _selectedPairItem.value = null
        _currentGameState.value = GameState.PairingGame
    }

    fun selectPairItem(item: MatchingPair) {
        if (item.isMatched) return
        val currentSelected = _selectedPairItem.value

        if (currentSelected == null) {
            _selectedPairItem.value = item
        } else if (currentSelected == item) {
            _selectedPairItem.value = null // Toggle deselect
        } else {
            // Check match: must be opposite language and have same ID
            if (currentSelected.isEnglish != item.isEnglish && currentSelected.id == item.id) {
                // MATCH!
                _matchingPairs.value = _matchingPairs.value.map {
                    if (it.id == item.id) it.copy(isMatched = true) else it
                }
                _score.value += 10
                _selectedPairItem.value = null
                
                // Check if all matched
                if (_matchingPairs.value.all { it.isMatched }) {
                    endGame("Hoàn Thành Ghép Cặp", _score.value, 1, 1)
                }
            } else {
                // MISMATCH
                _selectedPairItem.value = item // Select the new item instead
            }
        }
    }

    // --- GAME 2 & 3: MULTIPLE CHOICE & LISTENING ---
    fun startMultipleChoiceGame(isListening: Boolean = false) {
        if (!checkEnoughWords(4)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Vui lòng thêm ít nhất 4 từ vựng!")
            return
        }
        val pool = _words.value
        val questions = pool.shuffled().take(5).map { word ->
            val correct = word.meaning
            val incorrect = pool.filter { it.id != word.id }.map { it.meaning }.shuffled().take(3)
            QuizQuestion(word, correct, (listOf(correct) + incorrect).shuffled())
        }
        _quizQuestions.value = questions
        _currentQuestionIndex.value = 0
        _selectedOption.value = null
        _score.value = 0
        _currentGameState.value = if (isListening) GameState.ListeningGame else GameState.MultipleChoiceGame
        if (isListening) {
            speak(questions.first().word.word)
        }
    }

    fun submitOptionSelection(option: String) {
        if (_selectedOption.value != null) return // Already answered this question
        _selectedOption.value = option
        val currentQ = _quizQuestions.value[_currentQuestionIndex.value]
        if (option == currentQ.correctAnswer) {
            _score.value += 20
        }
    }

    fun nextQuestion() {
        val nextIdx = _currentQuestionIndex.value + 1
        if (nextIdx < _quizQuestions.value.size) {
            _currentQuestionIndex.value = nextIdx
            _selectedOption.value = null
            if (_currentGameState.value == GameState.ListeningGame) {
                speak(_quizQuestions.value[nextIdx].word.word)
            }
        } else {
            val gameTitle = if (_currentGameState.value == GameState.ListeningGame) "Hoàn Thành Luyện Nghe" else "Trắc Nghiệm Hoàn Thành"
            endGame(gameTitle, _score.value, 1, 1)
        }
    }

    // --- GAME 4 & 5: FILL IN BLANKS & WORD SCRAMBLE ---
    fun startFillOrScrambleGame(isScramble: Boolean) {
        if (!checkEnoughWords(1)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Vui lòng thêm từ vựng để học!")
            return
        }
        val randomWord = _words.value.random()
        _currentWord.value = randomWord
        _userInput.value = ""
        _isAnswerCorrect.value = null
        _score.value = 0

        if (isScramble) {
            val letters = randomWord.word.toCharArray().toList().shuffled()
            _scrambledLettersOrBlanks.value = letters.joinToString(" ")
            _currentGameState.value = GameState.WordScrambleGame
        } else {
            // Fill in blanks: mask odd letters or 50% letters
            val masked = randomWord.word.mapIndexed { idx, char ->
                if (idx % 2 == 1 && char.isLetter()) '_' else char
            }.joinToString("")
            _scrambledLettersOrBlanks.value = masked
            _currentGameState.value = GameState.FillBlanksGame
        }
    }

    fun setUserInput(input: String) {
        _userInput.value = input
    }

    fun checkTextInputAnswer() {
        val word = _currentWord.value?.word ?: return
        val isCorrect = _userInput.value.trim().equals(word, ignoreCase = true)
        _isAnswerCorrect.value = isCorrect
        if (isCorrect) {
            _score.value = 50
            endGame("Chính Xác!", 50, 1, 1)
        } else {
            _score.value = 0
            endGame("Rất Tiếc", 0, 0, 0)
        }
    }

    // --- GAME 6: SPEED QUIZ (TIME ATTACK) ---
    fun startSpeedQuizGame(seconds: Int) {
        if (!checkEnoughWords(4)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Vui lòng thêm từ vựng!")
            return
        }
        _score.value = 0
        _timeLeft.value = seconds
        _currentQuestionIndex.value = 0
        _selectedOption.value = null
        
        // Generate continuous stream of questions
        val pool = _words.value
        val questions = List(25) {
            val word = pool.random()
            val correct = word.meaning
            val incorrect = pool.filter { it.id != word.id }.map { it.meaning }.shuffled().take(3)
            QuizQuestion(word, correct, (listOf(correct) + incorrect).shuffled())
        }
        _quizQuestions.value = questions
        _currentGameState.value = GameState.SpeedQuizGame

        // Start countdown timer
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_timeLeft.value > 0) {
                delay(1000)
                _timeLeft.value -= 1
            }
            // Time out! Finish game
            endGame("Hết Giờ! Tốc Độ Đột Phá", _score.value, 1, 1)
        }
    }

    fun submitSpeedAnswer(option: String) {
        val currentQ = _quizQuestions.value.getOrNull(_currentQuestionIndex.value) ?: return
        if (option == currentQ.correctAnswer) {
            _score.value += 10
        }
        // Jump to next question instantly!
        val nextIdx = _currentQuestionIndex.value + 1
        if (nextIdx < _quizQuestions.value.size) {
            _currentQuestionIndex.value = nextIdx
        } else {
            // Out of questions, recreate
            val pool = _words.value
            _currentQuestionIndex.value = 0
            _quizQuestions.value = List(25) {
                val word = pool.random()
                val correct = word.meaning
                val incorrect = pool.filter { it.id != word.id }.map { it.meaning }.shuffled().take(3)
                QuizQuestion(word, correct, (listOf(correct) + incorrect).shuffled())
            }
        }
    }

    // --- GAME 7: MEMORY CARDS ---
    fun startMemoryCardsGame() {
        if (!checkEnoughWords(4)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Hãy thêm ít nhất 4 từ vựng!")
            return
        }
        val pool = _words.value.shuffled().take(4)
        val cardList = mutableListOf<MemoryCard>()
        var globalCardId = 1
        pool.forEach { word ->
            cardList.add(MemoryCard(globalCardId++, word.word, isEnglish = true, matchId = word.id))
            cardList.add(MemoryCard(globalCardId++, word.meaning, isEnglish = false, matchId = word.id))
        }
        _memoryCards.value = cardList.shuffled()
        _score.value = 0
        _firstFlippedCard.value = null
        _currentGameState.value = GameState.MemoryCardsGame
    }

    fun flipCard(card: MemoryCard) {
        if (card.isFaceUp || card.isMatched) return
        val cards = _memoryCards.value
        val firstFlipped = _firstFlippedCard.value

        // Flip target card up
        _memoryCards.value = cards.map {
            if (it.id == card.id) it.copy(isFaceUp = true) else it
        }

        if (firstFlipped == null) {
            _firstFlippedCard.value = card
        } else {
            _firstFlippedCard.value = null // reset
            if (firstFlipped.matchId == card.matchId && firstFlipped.isEnglish != card.isEnglish) {
                // MATCH!
                _memoryCards.value = _memoryCards.value.map {
                    if (it.matchId == card.matchId) it.copy(isMatched = true, isFaceUp = true) else it
                }
                _score.value += 25
                if (_memoryCards.value.all { it.isMatched }) {
                    endGame("Chiến Thắng Memory Cards", _score.value, 1, 1)
                }
            } else {
                // NO MATCH - wait 1 second and flip both back
                viewModelScope.launch {
                    delay(1000)
                    _memoryCards.value = _memoryCards.value.map {
                        if (it.id == card.id || it.id == firstFlipped.id) it.copy(isFaceUp = false) else it
                    }
                }
            }
        }
    }

    // --- GAME 8: HANGMAN ---
    fun startHangmanGame() {
        if (!checkEnoughWords(1)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Vui lòng thêm từ vựng!")
            return
        }
        val chosen = _words.value.random()
        _currentWord.value = chosen
        _hangmanGuessedLetters.value = emptySet()
        _hangmanWrongAttempts.value = 0
        _score.value = 0
        _currentGameState.value = GameState.HangmanGame
    }

    fun guessHangmanLetter(char: Char) {
        val letter = char.lowercaseChar()
        if (_hangmanGuessedLetters.value.contains(letter)) return
        
        val updatedGuesses = _hangmanGuessedLetters.value + letter
        _hangmanGuessedLetters.value = updatedGuesses

        val word = _currentWord.value?.word?.lowercase() ?: return
        if (!word.contains(letter)) {
            _hangmanWrongAttempts.value += 1
            if (_hangmanWrongAttempts.value >= 6) {
                // Game over lose
                endGame("Rất Tiếc (Hangman)", 0, 0, 0)
            }
        } else {
            // Check win
            val isWon = word.filter { it.isLetter() }.all { updatedGuesses.contains(it) }
            if (isWon) {
                _score.value = 100
                endGame("Giải Cứu Thành Công (Hangman)", 100, 2, 2)
            }
        }
    }

    // --- GAME 9: QUIZ CHALLENGE (20 QUESTIONS) ---
    fun startQuizChallenge() {
        if (!checkEnoughWords(4)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Vui lòng thêm ít nhất 4 từ vựng!")
            return
        }
        val pool = _words.value
        val questions = List(20) {
            val word = pool.random()
            val correct = word.meaning
            val incorrect = pool.filter { it.id != word.id }.map { it.meaning }.shuffled().take(3)
            QuizQuestion(word, correct, (listOf(correct) + incorrect).shuffled())
        }
        _quizQuestions.value = questions
        _currentQuestionIndex.value = 0
        _selectedOption.value = null
        _score.value = 0
        _currentGameState.value = GameState.QuizChallengeGame
    }

    fun submitChallengeAnswer(option: String) {
        if (_selectedOption.value != null) return
        _selectedOption.value = option
        val currentQ = _quizQuestions.value[_currentQuestionIndex.value]
        if (option == currentQ.correctAnswer) {
            _score.value += 5
        }
    }

    fun nextChallengeQuestion() {
        val nextIdx = _currentQuestionIndex.value + 1
        if (nextIdx < _quizQuestions.value.size) {
            _currentQuestionIndex.value = nextIdx
            _selectedOption.value = null
        } else {
            endGame("Hoàn Thành Thử Thách 20 Câu", _score.value, 2, 2)
        }
    }

    // --- GAME 10: VOCABULARY RACE ---
    fun startRaceGame() {
        _currentGameState.value = GameState.RaceGame
    }

    fun claimMilestoneReward(milestone: Milestone) {
        if (milestone.isUnlocked) {
            viewModelScope.launch {
                val profile = userProfile.value
                val profileBadges = profile.badges.split(",").filter { it.isNotBlank() }.toMutableSet()
                
                val badgeId = "milestone_${milestone.id}"
                if (!profileBadges.contains(badgeId)) {
                    profileBadges.add(badgeId)
                    val newCoins = profile.coins + milestone.rewardCoins
                    repository.saveUserProfile(profile.copy(
                        coins = newCoins,
                        badges = profileBadges.joinToString(",")
                    ))
                }
            }
        }
    }

    // --- GAME 11: PRONUNCIATION CHALLENGE ---
    fun startPronunciationGame() {
        if (!checkEnoughWords(1)) {
            _currentGameState.value = GameState.GameOver("Thiếu Từ Vựng", "Vui lòng thêm ít nhất 1 từ vựng để bắt đầu luyện phát âm!")
            return
        }
        val pool = _words.value.shuffled()
        _quizQuestions.value = pool.take(5).map { word ->
            QuizQuestion(word, word.word, emptyList())
        }
        _currentQuestionIndex.value = 0
        _score.value = 0
        _currentGameState.value = GameState.PronunciationGame
    }

    fun submitPronunciationSuccess() {
        _score.value += 20
    }

    fun nextPronunciationQuestion() {
        val nextIdx = _currentQuestionIndex.value + 1
        if (nextIdx < _quizQuestions.value.size) {
            _currentQuestionIndex.value = nextIdx
        } else {
            endGame("Hoàn Thành Phát Âm 🗣️", _score.value, 1, 1)
        }
    }
}
