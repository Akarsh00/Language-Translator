package com.example.languagetranslator

data class VoiceTranslatorUiState(
    val inputText: String = "",
    val translatedText: String = "",
    val isListening: Boolean = false,
    val isTranslating: Boolean = false
)