package com.nexauren.musicplayer2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SleepTimerDialog(
    vm: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val remaining by vm.sleepRemainingMs.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(I18n.t("Sleep timer")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (remaining > 0L) {
                        I18n.t("Playback will stop in ") + (remaining / 60000L).toString() + "m " + ((remaining % 60000L) / 1000L).toString() + "s."
                    } else {
                        I18n.t("Choose when playback should stop.")
                    }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5, 15, 30, 60).forEach { minutes ->
                        AssistChip(
                            onClick = {
                                vm.startSleepTimer(minutes)
                                onDismiss()
                            },
                            label = { Text(minutes.toString() + " " + I18n.t("min")) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = remaining > 0L,
                onClick = {
                    vm.cancelSleepTimer()
                    onDismiss()
                }
            ) { Text(I18n.t("Cancel timer")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(I18n.t("Close")) }
        }
    )
}
