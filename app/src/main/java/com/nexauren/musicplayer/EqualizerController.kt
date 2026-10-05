package com.nexauren.musicplayer

import android.media.audiofx.Equalizer

class EqualizerController {
    private var equalizer: Equalizer? = null

    fun attach(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        runCatching {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
        }
    }

    fun lowerBound(): Int = equalizer?.bandLevelRange?.getOrNull(0)?.toInt() ?: -1500

    fun upperBound(): Int = equalizer?.bandLevelRange?.getOrNull(1)?.toInt() ?: 1500

    fun setBand(index: Int, value: Int) {
        runCatching {
            equalizer?.setBandLevel(index.toShort(), value.toShort())
        }
    }

    fun setPreset(index: Short) {
        runCatching { equalizer?.usePreset(index) }
    }

    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
