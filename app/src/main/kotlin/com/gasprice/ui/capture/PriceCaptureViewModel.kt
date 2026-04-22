package com.gasprice.ui.capture

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gasprice.data.repository.GasPriceRepository
import com.gasprice.domain.model.*
import com.gasprice.domain.usecase.GasPriceParsing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaptureUiState(
    val isListening: Boolean = false,
    val transcript: String = "",
    val parsedPrice: Double? = null,
    val parsingStatus: ParsingStatus? = null,
    val manualInput: String = "",
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class PriceCaptureViewModel @Inject constructor(
    application: Application,
    private val repository: GasPriceRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(getApplication())) {
            _uiState.value = _uiState.value.copy(error = "Speech recognition not available on this device")
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getApplication()).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    _uiState.value = _uiState.value.copy(isListening = true, error = null)
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull() ?: ""
                    handleTranscript(transcript)
                }
                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        else -> "Speech error ($error)"
                    }
                    _uiState.value = _uiState.value.copy(isListening = false, error = msg)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { _uiState.value = _uiState.value.copy(isListening = false) }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What is the gas price?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _uiState.value = _uiState.value.copy(isListening = false)
    }

    private fun handleTranscript(transcript: String) {
        val parsed = GasPriceParsing.parse(transcript)
        _uiState.value = _uiState.value.copy(
            transcript = transcript,
            parsedPrice = parsed.value,
            parsingStatus = parsed.status,
            manualInput = parsed.value?.toString() ?: ""
        )
    }

    fun onManualInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(manualInput = text)
        val parsed = GasPriceParsing.parse(text)
        _uiState.value = _uiState.value.copy(
            parsedPrice = parsed.value,
            parsingStatus = if (text.isBlank()) null else ParsingStatus.MANUAL
        )
    }

    fun saveObservation(
        stationName: String,
        stationPlaceId: String?,
        stationAddress: String?,
        latitude: Double,
        longitude: Double
    ) {
        val state = _uiState.value
        val price = state.parsedPrice ?: return

        _uiState.value = state.copy(isSaving = true)
        viewModelScope.launch {
            try {
                val obs = GasPriceObservation(
                    stationPlaceId = stationPlaceId,
                    stationName = stationName,
                    stationAddress = stationAddress,
                    latitude = latitude,
                    longitude = longitude,
                    entrySource = if (state.transcript.isNotBlank()) EntrySource.VOICE else EntrySource.MANUAL,
                    rawTranscript = state.transcript.takeIf { it.isNotBlank() },
                    parsedPrice = price,
                    parsingStatus = state.parsingStatus ?: ParsingStatus.MANUAL
                )
                repository.save(obs)
                _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
            } catch (e: Exception) {
                Log.e(TAG, "Save failed: ${e.message}")
                _uiState.value = _uiState.value.copy(isSaving = false, error = "Failed to save")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
    }

    companion object {
        private const val TAG = "PriceCaptureVM"
    }
}
