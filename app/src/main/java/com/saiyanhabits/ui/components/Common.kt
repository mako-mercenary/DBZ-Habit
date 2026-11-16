package com.saiyanhabits.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.saiyanhabits.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saiyanhabits.ui.theme.SaiyanColors

/** Small all-caps heading above a group of content. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = SaiyanColors.TextDim,
        modifier = modifier,
    )
}

/** Panel shown where a list would be, with one thing to do next. */
@Composable
fun EmptyPanel(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SaiyanColors.Panel,
        border = BorderStroke(1.dp, SaiyanColors.Outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = SaiyanColors.TextDim,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

/**
 * A full screen reached from a tab: a title bar with a back arrow, scrolling
 * content, and optionally one main button pinned to the bottom.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}

/** The one main action of a screen, pinned above the navigation bar. */
@Composable
fun BottomActionButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(52.dp),
    ) {
        Text(text)
    }
}

/** One option in a row of mutually exclusive choices. */
@Composable
fun ChoiceTile(
    selected: Boolean,
    onClick: () -> Unit,
    title: String,
    caption: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else SaiyanColors.Panel,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) SaiyanColors.Orange else SaiyanColors.Outline,
        ),
        modifier = modifier.semantics {
            role = Role.RadioButton
            this.selected = selected
        },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                caption,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) SaiyanColors.Orange else SaiyanColors.TextDim,
            )
        }
    }
}

