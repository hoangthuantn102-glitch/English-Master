package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WordDao {
    @Query("SELECT * FROM words ORDER BY dateAdded DESC")
    fun getAllWords(): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Int): Word?

    @Query("SELECT * FROM words WHERE topic = :topic ORDER BY word ASC")
    fun getWordsByTopic(topic: String): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE level = :level ORDER BY word ASC")
    fun getWordsByLevel(level: String): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE isFavorite = 1")
    fun getFavoriteWords(): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE nextReviewTime <= :currentTime ORDER BY nextReviewTime ASC")
    fun getWordsForReview(currentTime: Long): Flow<List<Word>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: Word): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<Word>)

    @Update
    suspend fun updateWord(word: Word)

    @Delete
    suspend fun deleteWord(word: Word)

    @Query("DELETE FROM words")
    suspend fun deleteAllWords()

    // User Profile Dao operations
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfile?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(profile: UserProfile)

    @Update
    suspend fun updateUserProfile(profile: UserProfile)
}
