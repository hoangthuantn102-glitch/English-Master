package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val pronunciation: String,
    val meaning: String,
    val example: String,
    val translation: String,
    val topic: String,
    val level: String = "Beginner",
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val learnedStatus: String = "NEW", // "NEW", "LEARNING", "REVIEW", "MASTERED"
    val spacedRepetitionInterval: Int = 1, // in days
    val nextReviewTime: Long = System.currentTimeMillis(),
    val easeFactor: Float = 2.5f,
    val repetitions: Int = 0
)
