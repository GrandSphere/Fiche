package com.grandsphere.fiche.ui.chrome

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollapsibleBlock(
    key: String,
    modifier: Modifier = Modifier,
    startExpanded: Boolean = false,
    bubbleColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    headerStartPadding: Dp = 12.dp,
    leading: (@Composable () -> Unit)? = null,
    onHeaderLongClick: (() -> Unit)? = null,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(key) { mutableStateOf(startExpanded) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = bubbleColor
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) {
                    leading()
                }
                val headerModifier = Modifier
                    .weight(1f)
                    .padding(start = if (leading != null) 0.dp else headerStartPadding)
                Row(
                    modifier = if (onHeaderLongClick != null) {
                        headerModifier.combinedClickable(
                            onClick = { expanded = !expanded },
                            onLongClick = onHeaderLongClick
                        )
                    } else {
                        headerModifier.clickable { expanded = !expanded }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    header()
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}
