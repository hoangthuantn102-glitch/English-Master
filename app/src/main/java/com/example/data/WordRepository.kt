package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.max
import kotlin.math.roundToInt

class WordRepository(private val wordDao: WordDao) {
    val allWords: Flow<List<Word>> = wordDao.getAllWords()
    val favoriteWords: Flow<List<Word>> = wordDao.getFavoriteWords()
    val userProfile: Flow<UserProfile?> = wordDao.getUserProfile()

    fun getWordsByTopic(topic: String): Flow<List<Word>> = wordDao.getWordsByTopic(topic)
    fun getWordsByLevel(level: String): Flow<List<Word>> = wordDao.getWordsByLevel(level)
    fun getWordsForReview(currentTime: Long): Flow<List<Word>> = wordDao.getWordsForReview(currentTime)

    suspend fun insertWord(word: Word): Long = wordDao.insertWord(word)
    suspend fun insertWords(words: List<Word>) = wordDao.insertWords(words)
    suspend fun updateWord(word: Word) = wordDao.updateWord(word)
    suspend fun deleteWord(word: Word) = wordDao.deleteWord(word)
    suspend fun deleteAllWords() = wordDao.deleteAllWords()

    suspend fun getUserProfileOnce(): UserProfile {
        return wordDao.getUserProfile().firstOrNull() ?: UserProfile()
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        wordDao.insertUserProfile(profile)
    }

    // Spaced Repetition (SM-2) algorithm
    // Grade: 1 = Again/Forgot, 2 = Hard, 3 = Good, 4 = Easy
    suspend fun gradeWord(wordId: Int, grade: Int) {
        val word = wordDao.getWordById(wordId) ?: return
        
        var repetitions = word.repetitions
        var easeFactor = word.easeFactor
        var interval = word.spacedRepetitionInterval
        val currentTime = System.currentTimeMillis()

        if (grade >= 2) {
            if (repetitions == 0) {
                interval = 1
            } else if (repetitions == 1) {
                interval = 6
            } else {
                interval = (interval * easeFactor).roundToInt()
            }
            repetitions += 1
        } else {
            // "Again" - Reset repetitions, interval back to 1 day
            repetitions = 0
            interval = 1
        }

        // Adjust Ease Factor (SM-2 algorithm adjustments)
        // EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        // Map grade (1, 2, 3, 4) to SM-2 quality (0, 2, 4, 5) roughly:
        val quality = when (grade) {
            1 -> 0 // Forgot / Again
            2 -> 2 // Hard
            3 -> 4 // Good
            4 -> 5 // Easy
            else -> 3
        }
        easeFactor += (0.1f - (5 - quality) * (0.08f + (5 - quality) * 0.02f))
        if (easeFactor < 1.3f) {
            easeFactor = 1.3f
        }

        val nextReviewTime = currentTime + (interval * 24L * 60 * 60 * 1000)
        val status = when {
            grade == 4 -> "MASTERED"
            interval > 7 -> "MASTERED"
            interval > 1 -> "REVIEW"
            else -> "LEARNING"
        }

        val updatedWord = word.copy(
            repetitions = repetitions,
            easeFactor = easeFactor,
            spacedRepetitionInterval = max(1, interval),
            nextReviewTime = nextReviewTime,
            learnedStatus = status
        )
        wordDao.updateWord(updatedWord)
    }

    suspend fun populateInitialDataIfNeeded() {
        val currentWords = wordDao.getAllWords().firstOrNull()
        if (currentWords.isNullOrEmpty()) {
            val initialWords = listOf(
                Word(
                    word = "apple",
                    pronunciation = "/ˈæpəl/",
                    meaning = "quả táo",
                    example = "I eat an apple every morning.",
                    translation = "Tôi ăn một quả táo mỗi sáng.",
                    topic = "Food",
                    level = "Beginner"
                ),
                Word(
                    word = "explore",
                    pronunciation = "/ɪkˈsplɔːr/",
                    meaning = "khám phá, thám hiểm",
                    example = "We decided to explore the ancient forest.",
                    translation = "Chúng tôi quyết định thám hiểm khu rừng cổ kính.",
                    topic = "Travel",
                    level = "Intermediate"
                ),
                Word(
                    word = "technology",
                    pronunciation = "/tekˈnɒl.ə.dʒi/",
                    meaning = "công nghệ",
                    example = "Modern technology has changed how we communicate.",
                    translation = "Công nghệ hiện đại đã thay đổi cách chúng ta giao tiếp.",
                    topic = "Technology",
                    level = "Intermediate"
                ),
                Word(
                    word = "serendipity",
                    pronunciation = "/ˌser.ənˈdɪp.ə.ti/",
                    meaning = "sự tình cờ may mắn",
                    example = "Finding her old diary was a happy serendipity.",
                    translation = "Tìm lại cuốn nhật ký cũ của cô ấy là một sự tình cờ may mắn.",
                    topic = "Daily Conversation",
                    level = "Advanced"
                ),
                Word(
                    word = "adventure",
                    pronunciation = "/ədˈventʃər/",
                    meaning = "cuộc phiêu lưu",
                    example = "Life is a great adventure.",
                    translation = "Cuộc sống là một cuộc phiêu lưu vĩ đại.",
                    topic = "Travel",
                    level = "Beginner"
                ),
                Word(
                    word = "delicious",
                    pronunciation = "/dɪˈlɪʃəs/",
                    meaning = "thơm ngon",
                    example = "This pho smells absolutely delicious.",
                    translation = "Món phở này có mùi thơm phức ngon tuyệt vời.",
                    topic = "Food",
                    level = "Beginner"
                ),
                Word(
                    word = "innovative",
                    pronunciation = "/ˈɪn.ə.və.tɪv/",
                    meaning = "sáng tạo, đổi mới",
                    example = "The company is known for its innovative designs.",
                    translation = "Công ty nổi tiếng với các thiết kế đổi mới sáng tạo.",
                    topic = "Business",
                    level = "Advanced"
                ),
                Word(
                    word = "colleague",
                    pronunciation = "/ˈkɒl.iːɡ/",
                    meaning = "đồng nghiệp",
                    example = "She discussed the project with a colleague.",
                    translation = "Cô ấy đã thảo luận dự án với một người đồng nghiệp.",
                    topic = "Business",
                    level = "Intermediate"
                ),
                Word(
                    word = "environment",
                    pronunciation = "/ɪnˈvaɪ.rən.mənt/",
                    meaning = "môi trường",
                    example = "We must work together to protect the environment.",
                    translation = "Chúng ta phải cùng nhau bảo vệ môi trường.",
                    topic = "Science",
                    level = "Intermediate"
                ),
                Word(
                    word = "hypothesis",
                    pronunciation = "/haɪˈpɒθ.ə.sɪs/",
                    meaning = "giả thuyết",
                    example = "The scientist proposed a new hypothesis.",
                    translation = "Nhà khoa học đã đề xuất một giả thuyết mới.",
                    topic = "Science",
                    level = "Advanced"
                )
            )
            wordDao.insertWords(initialWords)
        }

        // Ensure user profile exists
        val currentProfile = wordDao.getUserProfile().firstOrNull()
        if (currentProfile == null) {
            wordDao.insertUserProfile(UserProfile())
        }
    }
}
