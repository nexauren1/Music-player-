package com.nexauren.musicplayer2

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import androidx.media3.extractor.mp3.Mp3InfoReplayGain
import java.util.Locale
import kotlin.math.pow

object ReplayGain {
    private val numberRegex = Regex("""([+-]?\\d+(?:[.,]\\d+)?)\\s*d?b?""", RegexOption.IGNORE_CASE)

    fun gainDb(metadata: Metadata): Float? {
        var trackGain: Float? = null
        var albumGain: Float? = null

        for (index in 0 until metadata.length()) {
            when (val entry = metadata[index]) {
                is Mp3InfoReplayGain -> {
                    val fields = listOf(entry.field1, entry.field2).filterNotNull()
                    fields.firstOrNull { it.name == Mp3InfoReplayGain.GainField.NAME_RADIO }
                        ?.let { trackGain = it.gain }
                    if (trackGain == null) {
                        fields.firstOrNull()?.let { albumGain = it.gain }
                    }
                }

                is VorbisComment -> {
                    when (entry.key.uppercase(Locale.US)) {
                        "REPLAYGAIN_TRACK_GAIN" -> trackGain = parseDb(entry.value)
                        "REPLAYGAIN_ALBUM_GAIN" -> albumGain = parseDb(entry.value)
                    }
                }

                is TextInformationFrame -> {
                    val label = entry.description.orEmpty().uppercase(Locale.US)
                    if (label.contains("REPLAYGAIN_TRACK_GAIN")) {
                        trackGain = parseDb(entry.values.firstOrNull().orEmpty())
                    } else if (label.contains("REPLAYGAIN_ALBUM_GAIN")) {
                        albumGain = parseDb(entry.values.firstOrNull().orEmpty())
                    }
                }

                is CommentFrame -> {
                    val label = entry.description.orEmpty().uppercase(Locale.US)
                    if (label.contains("REPLAYGAIN_TRACK_GAIN")) {
                        trackGain = parseDb(entry.text)
                    } else if (label.contains("REPLAYGAIN_ALBUM_GAIN")) {
                        albumGain = parseDb(entry.text)
                    }
                }
            }
        }

        return trackGain ?: albumGain
    }

    fun multiplier(gainDb: Float): Float =
        10f.pow(gainDb / 20f).coerceIn(0.25f, 1.5f)

    private fun parseDb(value: String): Float? {
        val match = numberRegex.find(value) ?: return null
        return match.groupValues[1]
            .replace(',', '.')
            .toFloatOrNull()
            ?.coerceIn(-24f, 12f)
    }
}
