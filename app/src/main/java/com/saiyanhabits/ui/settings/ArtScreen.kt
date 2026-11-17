package com.saiyanhabits.ui.settings

import com.saiyanhabits.ui.components.DbzSecondaryButton
import com.saiyanhabits.ui.components.PanelStyle
import com.saiyanhabits.ui.components.DbzPanel
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saiyanhabits.art.ArtKey
import com.saiyanhabits.art.ArtRepository
import com.saiyanhabits.art.ArtShape
import com.saiyanhabits.ui.components.ArtImage
import com.saiyanhabits.ui.components.SectionLabel
import com.saiyanhabits.ui.components.SubScreen
import com.saiyanhabits.ui.theme.SaiyanColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArtViewModel(private val art: ArtRepository) : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)

    /** Something to tell the user about the last pick, or null. */
    val message: StateFlow<String?> = _message

    /** Keys with a picked image. */
    val overridden: StateFlow<Set<ArtKey>> = combine(art.version, _message) { _, _ -> art.overriddenKeys() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), art.overriddenKeys())

    fun pick(key: ArtKey, uri: Uri) {
        viewModelScope.launch {
            _message.value = if (art.setOverride(key, uri)) null else "That image could not be read. Try another."
        }
    }

    fun reset(key: ArtKey) {
        viewModelScope.launch {
            art.clearOverride(key)
            _message.value = null
        }
    }
}

/** Settings > Art: every image key, with a picker to replace it from the gallery. */
@Composable
fun ArtScreen(overridden: Set<ArtKey>, message: String?, onPick: (ArtKey, Uri) -> Unit, onReset: (ArtKey) -> Unit, onBack: () -> Unit) {
    // The key being picked for, kept across the picker (and the app being restored behind it).
    var pendingKey by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val key = pendingKey?.let(ArtKey::fromKey)
        pendingKey = null
        if (uri != null && key != null) onPick(key, uri)
    }

    SubScreen(title = "Art", onBack = onBack) {
        Text(
            "Pick an image from your phone for any of these. It is copied into the app and used instead of " +
                "the built-in one. Circle images are cropped to a circle, so keep the subject in the middle.",
            style = MaterialTheme.typography.bodyMedium,
            color = SaiyanColors.TextDim,
        )
        if (message != null) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = SaiyanColors.Danger)
        }
        val groups = listOf(
            "Mentors" to ArtKey.entries.filter { it.name.startsWith("MENTOR_") },
            "You" to ArtKey.entries.filter { it.name.startsWith("PLAYER_") },
            "Bosses" to ArtKey.entries.filter { it.name.startsWith("BOSS_") },
            "Others" to listOf(ArtKey.RIVAL, ArtKey.DRAGON, ArtKey.BACKGROUND_HOME, ArtKey.APP_ICON),
            "Decorations" to listOf(ArtKey.SCOUTER_DEVICE, ArtKey.CLOUD_ORNAMENT, ArtKey.EMBLEM_CAPSULE),
        )
        groups.forEach { (title, keys) ->
            Spacer(Modifier.height(6.dp))
            SectionLabel(title)
            keys.forEach { key ->
                ArtRow(
                    key = key,
                    custom = key in overridden,
                    onPick = {
                        pendingKey = key.key
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onReset = { onReset(key) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ArtRow(key: ArtKey, custom: Boolean, onPick: () -> Unit, onReset: () -> Unit) {
    DbzPanel(
        style = if (custom) PanelStyle.Selected else PanelStyle.Blue,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            val thumb = Modifier.size(56.dp)
            ArtImage(
                key = key,
                contentDescription = null,
                modifier = if (key.shape == ArtShape.CIRCLE) thumb.clip(CircleShape) else thumb.clip(RoundedCornerShape(10.dp)),
                contentScale = if (key.shape == ArtShape.FREE) ContentScale.Fit else ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(key.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${key.width} × ${key.height}" + when {
                        !key.isSwappableInApp -> " · replace the file and rebuild"
                        custom -> " · your image"
                        else -> " · built-in"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (custom) SaiyanColors.Orange else SaiyanColors.TextDim,
                )
            }
            if (key.isSwappableInApp) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    DbzSecondaryButton(onClick = onPick) { Text(if (custom) "Change" else "Pick") }
                    if (custom) TextButton(onClick = onReset) { Text("Reset", color = SaiyanColors.TextDim) }
                }
            }
        }
    }
}
