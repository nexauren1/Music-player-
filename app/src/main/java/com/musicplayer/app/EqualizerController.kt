package com.nexauren.musicplayer2

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer

data class DjEffectState(
    val bassBoost: Float = 0f,
    val surround: Float = 0f,
    val loudness: Float = 0f,
    val reverb: Float = 0f,
    val delay: Float = 0f
)

class EqualizerController(context: Context) {
    private val prefs = context.getSharedPreferences("equalizer_state", Context.MODE_PRIVATE)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var attachedSessionId: Int = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0 || audioSessionId == attachedSessionId && equalizer != null) return
        runCatching {
            release()
            attachedSessionId = audioSessionId
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }

            bassBoost = runCatching {
                BassBoost(0, audioSessionId).apply { enabled = true }
            }.getOrNull()

            virtualizer = runCatching {
                Virtualizer(0, audioSessionId).apply { enabled = true }
            }.getOrNull()

            loudnessEnhancer = runCatching {
                LoudnessEnhancer(audioSessionId).apply { enabled = true }
            }.getOrNull()

            environmentalReverb = runCatching {
                EnvironmentalReverb(0, audioSessionId).apply { enabled = true }
            }.getOrNull()

            restore()
        }
    }

    fun bandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 5

    fun bandFrequencyHz(index: Int): Int =
        runCatching { equalizer?.getCenterFreq(index.toShort())?.div(1000) ?: 0 }.getOrDefault(0)

    fun lowerBound(): Int =
        equalizer?.bandLevelRange?.getOrNull(0)?.toInt() ?: -1500

    fun upperBound(): Int =
        equalizer?.bandLevelRange?.getOrNull(1)?.toInt() ?: 1500

    fun setBand(index: Int, value: Int) {
        runCatching {
            equalizer?.setBandLevel(index.toShort(), value.toShort())
            prefs.edit()
                .putInt("preset", -1)
                .putInt("band_$index", value)
                .apply()
        }
    }

    fun setPreset(index: Short) {
        runCatching {
            equalizer?.usePreset(index)
            val levels = currentLevels()
            val editor = prefs.edit().putInt("preset", index.toInt())
            levels.forEachIndexed { i, level -> editor.putInt("band_$i", level) }
            editor.apply()
        }
    }

    fun applyCustomPreset(normalizedBands: List<Float>) {
        val eq = equalizer ?: return
        val low = lowerBound().toFloat()
        val high = upperBound().toFloat()
        val range = (high - low).coerceAtLeast(1f)

        normalizedBands.take(eq.numberOfBands.toInt()).forEachIndexed { index, value ->
            val level = (low + range * value.coerceIn(0f, 1f)).toInt()
            runCatching { eq.setBandLevel(index.toShort(), level.toShort()) }
            prefs.edit().putInt("band_$index", level).putInt("preset", -1).apply()
        }
    }

    fun currentLevels(): List<Int> {
        val eq = equalizer ?: return emptyList()
        return (0 until eq.numberOfBands).map { band ->
            runCatching { eq.getBandLevel(band.toShort()).toInt() }.getOrDefault(0)
        }
    }

    fun normalizedLevels(): List<Float> {
        val low = lowerBound().toFloat()
        val high = upperBound().toFloat()
        val range = (high - low).coerceAtLeast(1f)
        return currentLevels().map { ((it - low) / range).coerceIn(0f, 1f) }
    }

    fun effectState(): DjEffectState =
        DjEffectState(
            bassBoost = prefs.getInt("bass", 0) / 1000f,
            surround = prefs.getInt("surround", 0) / 1000f,
            loudness = prefs.getInt("loudness", 0) / 10000f,
            reverb = prefs.getInt("reverb", 0) / 1000f,
            delay = prefs.getInt("delay", 0) / 1000f
        )

    fun setBassBoost(normalized: Float) {
        val value = (normalized.coerceIn(0f, 1f) * 1000f).toInt()
        runCatching { bassBoost?.setStrength(value.toShort()) }
        prefs.edit().putInt("bass", value).apply()
    }

    fun setSurround(normalized: Float) {
        val value = (normalized.coerceIn(0f, 1f) * 1000f).toInt()
        runCatching { virtualizer?.setStrength(value.toShort()) }
        prefs.edit().putInt("surround", value).apply()
    }

    fun setLoudness(normalized: Float) {
        val value = (normalized.coerceIn(0f, 1f) * 10_000f).toInt()
        runCatching { loudnessEnhancer?.setTargetGain(value) }
        prefs.edit().putInt("loudness", value).apply()
    }

    fun setReverb(normalized: Float) {
        val value = (normalized.coerceIn(0f, 1f) * 1000f).toInt()
        val level = (-9000 + (9000 * normalized.coerceIn(0f, 1f))).toInt().toShort()
        runCatching { environmentalReverb?.setRoomLevel(level) }
        runCatching { environmentalReverb?.setReverbLevel(level) }
        prefs.edit().putInt("reverb", value).apply()
    }

    fun setDelay(normalized: Float) {
        val value = (normalized.coerceIn(0f, 1f) * 1000f).toInt()
        val milliseconds = (normalized.coerceIn(0f, 1f) * 500f).toInt().coerceIn(0, 500).toShort()
        runCatching { environmentalReverb?.setReflectionsDelay(milliseconds.toInt()) }
        runCatching { environmentalReverb?.setReverbDelay(milliseconds.toInt()) }
        prefs.edit().putInt("delay", value).apply()
    }

    fun resetAll() {
        applyCustomPreset(List(10) { .5f })
        setBassBoost(0f)
        setSurround(0f)
        setLoudness(0f)
        setReverb(0f)
        setDelay(0f)
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudnessEnhancer?.release() }
        runCatching { environmentalReverb?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        environmentalReverb = null
        attachedSessionId = 0
    }

    private fun restore() {
        val eq = equalizer ?: return
        val savedPreset = prefs.getInt("preset", -1)

        if (savedPreset in 0 until eq.numberOfPresets) {
            runCatching { eq.usePreset(savedPreset.toShort()) }
        }

        for (band in 0 until eq.numberOfBands) {
            if (prefs.contains("band_$band")) {
                val saved = prefs.getInt("band_$band", 0)
                runCatching { eq.setBandLevel(band.toShort(), saved.toShort()) }
            }
        }

        setBassBoost(effectState().bassBoost)
        setSurround(effectState().surround)
        setLoudness(effectState().loudness)
        setReverb(effectState().reverb)
        setDelay(effectState().delay)
    }
}
