package com.grandsphere.fiche.ui.chrome

import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

/**
 * A consistent 3-dot "overflow" icon button that opens a [DropdownMenu] with
 * the given [items]. Top-level rows show icons only; children open as a
 * side flyout beside the row that opened them.
 */
data class OverflowItem(
    val label: String,
    val enabled: Boolean = true,
    val icon: ImageVector? = null,
    val children: List<OverflowItem> = emptyList(),
    val expandOnArrowOnly: Boolean = false,
    val onLongClick: (() -> Unit)? = null,
    val onClick: () -> Unit = {}
)

@Composable
fun OverflowMenu(items: List<OverflowItem>) {
    if (items.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var openSubmenu by remember { mutableStateOf<String?>(null) }
    var windowOriginOnScreen by remember { mutableStateOf(IntOffset.Zero) }
    val rowScreenBounds = remember { mutableStateMapOf<String, IntRect>() }
    val hostView = LocalView.current
    Box(
        Modifier.onGloballyPositioned { coords ->
            val origin = coords.screenOffset(hostView) - coords.positionInWindow().toIntOffset()
            if (windowOriginOnScreen != origin) windowOriginOnScreen = origin
        }
    ) {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                if (openSubmenu == null) {
                    expanded = false
                }
            }
        ) {
            items.forEachIndexed { index, item ->
                val key = index.toString()
                OverflowMenuEntry(
                    item = item,
                    submenuOpen = openSubmenu == key,
                    onToggleSubmenu = {
                        openSubmenu = if (openSubmenu == key) null else key
                    },
                    onScreenBounds = { rect ->
                        if (rowScreenBounds[key] != rect) rowScreenBounds[key] = rect
                    },
                    onDismiss = {
                        expanded = false
                        openSubmenu = null
                    }
                )
            }
        }
        val submenuKey = openSubmenu
        val submenuItem = submenuKey?.toIntOrNull()?.let { items.getOrNull(it) }
        val rowScreen = submenuKey?.let { rowScreenBounds[it] }
        if (expanded && submenuItem != null && submenuItem.children.isNotEmpty() && rowScreen != null) {
            Popup(
                popupPositionProvider = remember(rowScreen, windowOriginOnScreen) {
                    OverflowSubmenuPosition(rowScreen, windowOriginOnScreen)
                },
                onDismissRequest = { openSubmenu = null },
                properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    tonalElevation = 3.dp,
                    shadowElevation = 3.dp
                ) {
                    Column(Modifier.width(IntrinsicSize.Max)) {
                        submenuItem.children.forEach { child ->
                            DropdownMenuItem(
                                text = { Text(child.label) },
                                enabled = child.enabled,
                                onClick = {
                                    expanded = false
                                    openSubmenu = null
                                    child.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private class OverflowSubmenuPosition(
    private val rowOnScreen: IntRect,
    private val windowOriginOnScreen: IntOffset
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val row = IntRect(
            left = rowOnScreen.left - windowOriginOnScreen.x,
            top = rowOnScreen.top - windowOriginOnScreen.y,
            right = rowOnScreen.right - windowOriginOnScreen.x,
            bottom = rowOnScreen.bottom - windowOriginOnScreen.y
        )
        val gap = 4
        val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)
        val y = row.top.coerceIn(0, maxY)
        val leftX = row.left - popupContentSize.width - gap
        val rightX = row.right + gap
        val x = if (leftX >= 0) {
            leftX
        } else {
            rightX.coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
        }
        return IntOffset(x, y)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun OverflowMenuEntry(
    item: OverflowItem,
    submenuOpen: Boolean,
    onToggleSubmenu: () -> Unit,
    onScreenBounds: (IntRect) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val hasChildren = item.children.isNotEmpty()
    val contentAlpha = if (item.enabled) 1f else 0.38f
    val activate = {
        if (hasChildren && !item.expandOnArrowOnly) {
            onToggleSubmenu()
        } else {
            onDismiss()
            item.onClick()
        }
    }
    val rowModifier = if (item.onLongClick != null) {
        Modifier.combinedClickable(
            enabled = item.enabled,
            onClick = activate,
            onLongClick = {
                onDismiss()
                item.onLongClick.invoke()
            }
        )
    } else {
        Modifier.clickable(enabled = item.enabled, onClick = activate)
    }
    Row(
        modifier = rowModifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 8.dp)
            .then(
                if (hasChildren) {
                    Modifier.onGloballyPositioned { coords ->
                        val pos = coords.screenOffset(view)
                        onScreenBounds(
                            IntRect(
                                left = pos.x,
                                top = pos.y,
                                right = pos.x + coords.size.width,
                                bottom = pos.y + coords.size.height
                            )
                        )
                    }
                } else {
                    Modifier
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasChildren) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = if (submenuOpen) "Hide options" else "More options",
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(enabled = item.enabled) { onToggleSubmenu() },
                    tint = LocalContentColor.current.copy(alpha = contentAlpha)
                )
            }
        }
        if (item.icon != null) {
            Spacer(Modifier.size(4.dp))
            Icon(
                item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(24.dp),
                tint = LocalContentColor.current.copy(alpha = contentAlpha)
            )
        }
    }
}

private fun LayoutCoordinates.screenOffset(view: View): IntOffset {
    val loc = IntArray(2)
    view.getLocationOnScreen(loc)
    val root = positionInRoot()
    return IntOffset(loc[0] + root.x.roundToInt(), loc[1] + root.y.roundToInt())
}

private operator fun IntOffset.minus(other: IntOffset): IntOffset =
    IntOffset(x - other.x, y - other.y)

private fun Offset.toIntOffset(): IntOffset =
    IntOffset(x.roundToInt(), y.roundToInt())
