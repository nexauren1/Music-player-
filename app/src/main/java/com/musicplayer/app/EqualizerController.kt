package com.musicplayer.app

import android.content.Context
import android.media.audiofx.Equalizer

class EqualizerController(context: Context) {
    private val prefs = context.getSharedPreferences("equalizer_state", Context.MODE_PRIVATE)
    private var equalizer: Equalizer? = null

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        runCatching {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            restore()
        }
    }

    fun bandCount(): Int = equalizer?.numberOfBands?.toInt() ?: 5

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

    fun release() {
        equalizer?.release()
        equalizer = null
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
    }
}
