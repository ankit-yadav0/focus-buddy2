package com.example.ui.helper

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural offline audio synthesizer for deep focus. Generates continuous PCM audio in real time
 * (Brown Noise, 40Hz Gamma Focus Tone, or Soft Rain) with zero external audio assets (zero APK bloat).
 */
object FocusSoundGenerator {

    enum class SoundMode(val displayName: String) {
        OFF("Off"),
        BROWN_NOISE("Brown Noise"),
        GAMMA_40HZ("40Hz Gamma"),
        SOFT_RAIN("Soft Rain");

        val label: String get() = displayName
    }

    private const val SAMPLE_RATE = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playJob: Job? = null

    @Volatile
    private var sessionToken = 0L

    private val _currentMode = MutableStateFlow(SoundMode.OFF)
    val currentMode: StateFlow<SoundMode> = _currentMode.asStateFlow()
    val activeMode: StateFlow<SoundMode> get() = currentMode

    @Synchronized
    fun setMode(mode: SoundMode) {
        if (_currentMode.value == mode && (mode == SoundMode.OFF || playJob?.isActive == true)) return
        _currentMode.value = mode
        val myToken = ++sessionToken
        playJob?.cancel()
        playJob = null
        if (mode != SoundMode.OFF) {
            playJob = scope.launch {
                runSynthLoop(mode, myToken)
            }
        }
    }

    @Synchronized
    fun stop() {
        setMode(SoundMode.OFF)
    }

    private fun runSynthLoop(mode: SoundMode, myToken: Long) {
        var track: AudioTrack? = null
        try {
            val minBufBytes = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufBytes * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val bufferSamples = 2048
            val pcm = ShortArray(bufferSamples)
            var brownState = 0.0
            var pinkB0 = 0.0
            var pinkB1 = 0.0
            var pinkB2 = 0.0
            var phaseCarrier = 0.0
            var phaseMod = 0.0
            val twoPi = 2.0 * PI
            val carrierStep = twoPi * 200.0 / SAMPLE_RATE
            val modStep = twoPi * 40.0 / SAMPLE_RATE
            val rainModStep = twoPi * 0.35 / SAMPLE_RATE

            track.play()

            while (sessionToken == myToken && _currentMode.value == mode) {
                when (mode) {
                    SoundMode.BROWN_NOISE -> {
                        for (i in 0 until bufferSamples) {
                            val white = Random.nextDouble(-1.0, 1.0)
                            brownState = (brownState + 0.035 * white) / 1.035
                            val sample = (brownState * 3.2).coerceIn(-0.9, 0.9)
                            pcm[i] = (sample * 12000).toInt().toShort()
                        }
                    }
                    SoundMode.GAMMA_40HZ -> {
                        for (i in 0 until bufferSamples) {
                            val mod = 0.65 + 0.35 * sin(phaseMod)
                            val sig = sin(phaseCarrier) * mod
                            phaseCarrier += carrierStep
                            if (phaseCarrier > twoPi) phaseCarrier -= twoPi
                            phaseMod += modStep
                            if (phaseMod > twoPi) phaseMod -= twoPi
                            pcm[i] = (sig * 5500).toInt().toShort()
                        }
                    }
                    SoundMode.SOFT_RAIN -> {
                        for (i in 0 until bufferSamples) {
                            val white = Random.nextDouble(-1.0, 1.0)
                            pinkB0 = 0.99765 * pinkB0 + white * 0.0990460
                            pinkB1 = 0.96300 * pinkB1 + white * 0.2965164
                            pinkB2 = 0.57000 * pinkB2 + white * 1.0526913
                            val pink = (pinkB0 + pinkB1 + pinkB2 + white * 0.1848) * 0.08
                            val swell = 0.80 + 0.20 * sin(phaseMod)
                            phaseMod += rainModStep
                            if (phaseMod > twoPi) phaseMod -= twoPi
                            val sample = (pink * swell).coerceIn(-0.9, 0.9)
                            pcm[i] = (sample * 9500).toInt().toShort()
                        }
                    }
                    SoundMode.OFF -> break
                }
                if (sessionToken != myToken) break
                val written = track.write(pcm, 0, bufferSamples)
                if (written < 0) break
            }
        } catch (e: Exception) {
            Log.e("FocusSoundGenerator", "Error generating focus audio", e)
        } finally {
            try {
                track?.stop()
            } catch (e: Exception) {
            }
            try {
                track?.release()
            } catch (e: Exception) {
            }
        }
    }
}
