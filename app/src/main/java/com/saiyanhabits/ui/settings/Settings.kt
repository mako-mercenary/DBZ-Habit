package com.saiyanhabits.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.GameConfig
import com.saiyanhabits.data.WishEntity
import com.saiyanhabits.data.WishRepository
import com.saiyanhabits.reminders.Notifications
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val MAX_WISH_LENGTH = 120
private val GrantedDate = DateTimeFormatter.ofPattern("MMM d, yyyy")

class WishesViewModel(private val repository: WishRepository) : ViewModel() {

    /** Every wish: those still waiting first, then the granted ones. */
    val wishes: StateFlow<List<WishEntity>> = repository.wishes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(text: String) {
        viewModelScope.launch { repository.addWish(text) }
    }

    fun remove(id: Long) {
        viewModelScope.launch { repository.removeWish(id) }
    }

    fun grant(id: Long) {
        viewModelScope.launch { repository.grantWish(id) }
    }
}

@Composable
fun SettingsScreen(
    wishes: List<WishEntity>,
    onAddWish: (String) -> Unit,
    onRemoveWish: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val waiting = wishes.filter { it.grantedAtEpochDay == null }
    val granted = wishes.filter { it.grantedAtEpochDay != null }
    var draft by rememberSaveable { mutableStateOf("") }

    SubScreen(title = "Settings", onBack = onBack) {
        SectionLabel("Wishes")
        Text(
            "Write down real-life rewards you'd like. Collect ${GameConfig.WISH_ORBS_NEEDED} wish orbs, one per " +
                "perfect week, then summon the dragon from the home screen and claim one.",
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_WISH_LENGTH) },
                placeholder = { Text("e.g. A weekend away") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onAddWish(draft)
                    draft = ""
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.height(52.dp),
            ) { Text("Add") }
        }
        if (waiting.isEmpty()) {
            Text("No wishes waiting yet.", style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
        }
        waiting.forEach { wish ->
            WishRow(text = wish.text, detail = null) {
                TextButton(onClick = { onRemoveWish(wish.id) }) { Text("Remove", color = SaiyanColors.TextDim) }
            }
        }
        if (granted.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            SectionLabel("Granted")
            granted.forEach { wish ->
                WishRow(
                    text = wish.text,
                    detail = wish.grantedAtEpochDay?.let { "Granted ${LocalDate.ofEpochDay(it).format(GrantedDate)}" },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        NotificationSettings()
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun WishRow(text: String, detail: String?, trailing: @Composable () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SaiyanColors.Panel,
        border = BorderStroke(1.dp, SaiyanColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(text, style = MaterialTheme.typography.titleSmall)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = SaiyanColors.TextDim)
                }
            }
            trailing()
        }
    }
}

@Composable
private fun NotificationSettings() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Notifications.areEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        // The answer may have changed while the system settings were open.
        allowed = Notifications.areEnabled(context)
        onPauseOrDispose { }
    }
    SectionLabel("Notifications")
    Text(
        if (allowed) {
            "Notifications are on. Habit reminders, the morning streak check and the time chamber " +
                "can each be turned off separately in your phone's settings."
        } else {
            "Notifications are off for Saiyan Habits, so reminders, the morning streak check and the " +
                "time chamber alert can't show."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (allowed) SaiyanColors.TextDim else SaiyanColors.Danger,
    )
    OutlinedButton(
        onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Open notification settings") }
}
