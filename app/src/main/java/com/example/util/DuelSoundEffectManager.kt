package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Sound effects engine for 1vs1 Arena (Đấu trường 1vs1).
 * Generates custom synthesized audio waveforms via AudioTrack without external assets,
 * ensuring rich, crisp, zero-latency sound effects across all Android devices.
 */
object DuelSoundEffectManager {

    private val sampleRate = 22050

    fun playRoundWin(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playMelody(
                listOf(
                    Tone(523.25f, 100), // C5
                    Tone(659.25f, 100), // E5
                    Tone(783.99f, 120), // G5
                    Tone(1046.50f, 250) // C6
                )
            )
        }
        vibrateWin(context)
    }

    fun playRoundLoss(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playMelody(
                listOf(
                    Tone(440.00f, 140), // A4
                    Tone(392.00f, 140), // G4
                    Tone(349.23f, 140), // F4
                    Tone(293.66f, 280)  // D4
                )
            )
        }
        vibrateEncourage(context)
    }

    fun playRoundDraw(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            playMelody(
                listOf(
                    Tone(440.00f, 120),
                    Tone(554.37f, 180)
                )
            )
        }
    }

    fun playMatchChampion(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            // Fanfare victory chords
            val fanfare = listOf(
                Tone(523.25f, 120), // C5
                Tone(523.25f, 120), // C5
                Tone(523.25f, 120), // C5
                Tone(659.25f, 300), // E5
                Tone(587.33f, 120), // D5
                Tone(659.25f, 120), // E5
                Tone(783.99f, 400), // G5
                Tone(1046.50f, 600) // C6 Grand finale
            )
            playMelody(fanfare)
        }
        vibrateChampion(context)
    }

    fun playMatchDefeat(context: Context, scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            val defeatTune = listOf(
                Tone(392.00f, 200), // G4
                Tone(369.99f, 200), // F#4
                Tone(329.63f, 250), // E4
                Tone(261.63f, 450)  // C4
            )
            playMelody(defeatTune)
        }
    }

    fun playFireworkPop(scope: CoroutineScope) {
        scope.launch(Dispatchers.Default) {
            val freq = (800..1600).random().toFloat()
            playToneSweep(freq, freq * 1.5f, 90)
        }
    }

    private data class Tone(val frequencyHz: Float = 440f, val durationMs: Int)

    private fun playMelody(tones: List<Tone>) {
        try {
            tones.forEach { tone ->
                playToneSweep(tone.frequencyHz, tone.frequencyHz, tone.durationMs)
                Thread.sleep(25) // small pause between notes
            }
        } catch (e: Exception) {
            // ignore audio glitch
        }
    }

    private fun playToneSweep(startFreq: Float, endFreq: Float, durationMs: Int) {
        try {
            val numSamples = (sampleRate * (durationMs / 1000f)).toInt()
            if (numSamples <= 0) return

            val buffer = ShortArray(numSamples)
            var currentPhase = 0.0

            for (i in 0 until numSamples) {
                val t = i.toDouble() / numSamples
                val currentFreq = startFreq + (endFreq - startFreq) * t
                val phaseIncrement = 2.0 * PI * currentFreq / sampleRate
                currentPhase += phaseIncrement

                // Apply Hann envelope to prevent clicking
                val envelope = 0.5 * (1.0 - kotlin.math.cos(2.0 * PI * i / numSamples))
                val sampleValue = (sin(currentPhase) * envelope * Short.MAX_VALUE * 0.75).toInt()
                buffer[i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val audioFormat = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val track = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(buffer, 0, buffer.size)
            track.play()

            Thread.sleep(durationMs.toLong())
            track.stop()
            track.release()
        } catch (e: Exception) {
            // audio track unavailable or interrupted
        }
    }

    private fun vibrateWin(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 70, 150), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 100, 70, 150), -1)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun vibrateEncourage(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(180)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun vibrateChampion(context: Context) {
        val vibrator = getVibrator(context) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 50, 120, 60, 200, 80, 300), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 80, 50, 120, 60, 200, 80, 300), -1)
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }
}
