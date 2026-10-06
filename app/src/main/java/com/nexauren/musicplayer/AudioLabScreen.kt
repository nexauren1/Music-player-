package com.nexauren.musicplayer2

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.media.audiofx.Visualizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi

private data class AudioLabState(
    val crossfadeSeconds: Float = 4f,
    val replayGain: Boolean = true
)

@Composable
fun AudioLabScreen(
    modifier: Modifier,
    vm: PlayerViewModel,
    currentSong: Song?,
    onBack: () -> Unit,
    premium: PremiumSnapshot
) {
    val context = LocalContext.current
    val playing by vm.isPlaying.collectAsState()
    val premiumActive = premium.cloudSynced && premium.verified && when (premium.plan) {
        PremiumPlan.LIFETIME -> true
        PremiumPlan.QUARTERLY -> premium.expiresAtMillis == null || premium.expiresAtMillis > System.currentTimeMillis()
        PremiumPlan.NONE -> false
    }

    val stored = remember { AudioLabStore.load(context) }
    var crossfade by remember { mutableFloatStateOf(stored.crossfadeSeconds) }
    var replayGain by remember { mutableStateOf(stored.replayGain) }
    var tagEditorOpen by remember { mutableStateOf(false) }
    val accent = themeColors(AppearanceStore.load(context).theme, AppearanceStore.load(context).darkMode).primary.toArgb()

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(I18n.t("Audio Lab"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onPrimary)
                        Text(I18n.t("Premium sound, visual effects and smart playback controls."), color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .78f))
                    }
                    TextButton(onClick = onBack) { Text(I18n.t("Close"), color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(I18n.t("Crossfade"), fontWeight = FontWeight.Black)
                    Text(I18n.t("Configurable fade transition between tracks."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(crossfade.toInt().toString() + "s", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Switch(
                            enabled = premiumActive,
                            checked = crossfade > 0f,
                            onCheckedChange = {
                                crossfade = if (it) 4f else 0f
                                AudioLabStore.save(context, crossfade, replayGain)
                                vm.setCrossfadeSeconds(crossfade)
                            }
                        )
                    }
                    Slider(
                        enabled = premiumActive,
                        value = crossfade,
                        onValueChange = {
                            crossfade = it
                            AudioLabStore.save(context, crossfade, replayGain)
                            vm.setCrossfadeSeconds(it)
                        },
                        valueRange = 0f..12f,
                        steps = 11
                    )
                    Text(
                        if (premiumActive) I18n.t("Stable fade transition is used with the current Media3 player engine.")
                        else I18n.t("Premium feature"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(I18n.t("ReplayGain"), fontWeight = FontWeight.Black)
                        Text(I18n.t("Normalize tracks that contain ReplayGain metadata."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        enabled = premiumActive,
                        checked = replayGain,
                        onCheckedChange = {
                            replayGain = it
                            AudioLabStore.save(context, crossfade, it)
                            vm.setReplayGainEnabled(it)
                        }
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(I18n.t("Visualizer"), fontWeight = FontWeight.Black)
                    Text(currentSong?.title ?: I18n.t("Play a track first"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        factory = { AudioVisualizerView(it) },
                        update = {
                            it.setAccentColor(accent)
                            it.attachSession(vm.visualizerSessionId(), playing)
                        }
                    )
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(I18n.t("Tag editor"), fontWeight = FontWeight.Black)
                    Text(currentSong?.let { it.title + " • " + it.artist } ?: I18n.t("Choose a track to edit metadata."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { tagEditorOpen = true }, enabled = currentSong != null, modifier = Modifier.fillMaxWidth()) {
                        Text(I18n.t("Edit tags"))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(I18n.t("Home screen widget"), fontWeight = FontWeight.Black)
                    Text(I18n.t("Add the Music Player widget to your home screen for quick access to the current track."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(I18n.t("Widget support is built into this release."), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(I18n.t("Android Auto"), fontWeight = FontWeight.Black)
                    Text(I18n.t("Your local library is exposed through Android MediaLibraryService."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(if (premium.cloudSynced && premium.verified) I18n.t("Premium audio profile active") else I18n.t("Standard audio profile"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (tagEditorOpen && currentSong != null) {
        TagEditorDialog(
            song = currentSong,
            onDismiss = { tagEditorOpen = false },
            onSaved = {
                tagEditorOpen = false
                vm.scan()
            }
        )
    }
}

private object AudioLabStore {
    private const val PREFS = "audio_lab"

    fun load(context: Context): AudioLabState {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AudioLabState(
            crossfadeSeconds = p.getFloat("crossfade", 4f).coerceIn(0f, 12f),
            replayGain = p.getBoolean("replay_gain", true)
        )
    }

    fun save(context: Context, crossfadeSeconds: Float, replayGain: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("crossfade", crossfadeSeconds.coerceIn(0f, 12f))
            .putBoolean("replay_gain", replayGain)
            .apply()
    }
}

@OptIn(UnstableApi::class)
private class AudioVisualizerView(context: Context) : android.view.View(context) {
    private var visualizer: Visualizer? = null
    private var attachedSessionId = 0
    private var fft = ByteArray(0)
    private var active = false
    private var accentColor = android.graphics.Color.WHITE
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 5f; strokeCap = Paint.Cap.ROUND }

    fun setAccentColor(color: Int) {
        accentColor = color
        paint.color = color
        invalidate()
    }

    fun attachSession(sessionId: Int, playing: Boolean) {
        active = playing
        if (sessionId <= 0) {
            releaseVisualizer()
            invalidate()
            return
        }
        if (attachedSessionId != sessionId) {
            releaseVisualizer()
            attachedSessionId = sessionId
            runCatching {
                visualizer = Visualizer(sessionId).apply {
                    captureSize = Visualizer.getCaptureSizeRange()[1]
                    setDataCaptureListener(
                        object : Visualizer.OnDataCaptureListener {
                            override fun onWaveFormDataCapture(capture: Visualizer, waveform: ByteArray, samplingRate: Int) {
                                postInvalidateOnAnimation()
                            }
                            override fun onFftDataCapture(capture: Visualizer, fftData: ByteArray, samplingRate: Int) {
                                fft = fftData
                                postInvalidateOnAnimation()
                            }
                        },
                        Visualizer.getMaxCaptureRate() / 2,
                        false,
                        true
                    )
                    enabled = true
                }
            }.onFailure { visualizer = null }
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val width = width.toFloat().coerceAtLeast(1f)
        val height = height.toFloat().coerceAtLeast(1f)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        val bars = 30
        val gap = width / bars
        val now = System.currentTimeMillis()
        paint.color = accentColor
        for (i in 0 until bars) {
            val amplitude = if (active && fft.isNotEmpty()) {
                val index = (i * fft.size / bars).coerceIn(0, fft.lastIndex)
                val raw = kotlin.math.abs(fft[index].toInt())
                (raw / 42f).coerceIn(.10f, 1f)
            } else {
                (0.12 + (kotlin.math.sin((now / 280.0) + i * .45) + 1.0) * .22).toFloat()
            }
            val x = i * gap + gap * .5f
            val half = height * (.08f + amplitude * .34f)
            canvas.drawLine(x, height / 2f - half, x, height / 2f + half, paint)
        }
        postInvalidateDelayed(33)
    }

    private fun releaseVisualizer() {
        runCatching { visualizer?.release() }
        visualizer = null
        attachedSessionId = 0
        fft = ByteArray(0)
    }

    override fun onDetachedFromWindow() {
        releaseVisualizer()
        super.onDetachedFromWindow()
    }
}