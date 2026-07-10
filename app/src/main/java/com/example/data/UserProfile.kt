package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1, // Only one row
    val xp: Int = 0,
    val coins: Int = 50, // Start with some coins
    val level: Int = 1,
    val streak: Int = 0,
    val lastActiveDate: Long = 0,
    val badges: String = "" // Comma-separated list of unlocked badge IDs
)
