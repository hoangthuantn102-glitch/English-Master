package com.example.data

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
