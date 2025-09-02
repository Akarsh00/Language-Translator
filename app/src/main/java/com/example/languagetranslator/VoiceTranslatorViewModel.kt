package com.example.languagetranslator

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

class VoiceTranslatorViewModel(app: Application) : AndroidViewModel(app) {
    private val _uiState = MutableStateFlow(VoiceTranslatorUiState())
    val uiState: StateFlow<VoiceTranslatorUiState> = _uiState

    private var speechRecognizer: SpeechRecognizer? = null

    // Here this  method initiate audio listening
    fun startListening(onError: (String) -> Unit) {
        val context = getApplication<Application>()
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition not available")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _uiState.update { it.copy(isListening = true) }
                }

                override fun onResults(results: Bundle?) {
                    val spokenText =
                        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    spokenText?.let {
                        _uiState.update { state -> state.copy(inputText = it, isListening = false) }
                        detectLanguageAndTranslate(it)
                    } ?: _uiState.update { it.copy(isListening = false) }
                }

                override fun onError(error: Int) {
                    _uiState.update { it.copy(isListening = false) }
                }

                override fun onEndOfSpeech() {
                    _uiState.update { it.copy(isListening = false) }
                }

                // Unused
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }

        speechRecognizer?.startListening(intent)
    }

    private fun detectLanguageAndTranslate(text: String) {
        _uiState.update { it.copy(isTranslating = true, translatedText = "Detecting...") }

        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { langCode ->
                val sourceLang = if (langCode == "und") TranslateLanguage.ENGLISH else langCode
                translateText(text, sourceLang, TranslateLanguage.HINDI)
            }
            .addOnFailureListener {
                translateText(text, TranslateLanguage.ENGLISH, TranslateLanguage.HINDI)
            }
    }

    private fun translateText(text: String, sourceLang: String, targetLang: String) {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()

        val translator = Translation.getClient(options)
        val conditions = DownloadConditions.Builder().build()

        translator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                translator.translate(text)
                    .addOnSuccessListener { translated ->
                        _uiState.update { it.copy(translatedText = translated, isTranslating = false) }
                    }
                    .addOnFailureListener {
                        _uiState.update { it.copy(translatedText = "Translation failed", isTranslating = false) }
                    }
            }
            .addOnFailureListener {
                _uiState.update { it.copy(translatedText = "Model download failed", isTranslating = false) }
            }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
    }
}