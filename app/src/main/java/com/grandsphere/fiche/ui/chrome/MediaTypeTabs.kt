package com.grandsphere.fiche.ui.chrome

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grandsphere.fiche.domain.model.MediaType

/**
 * Consistent media-type tab strip used across every screen.
 *
 * - Uses [ScrollableTabRow] when more than 4 types are enabled so labels
 *   never truncate on narrow phones.
 * - Carries a full-width [swipeMediaType] modifier so horizontal swipes
 *   anywhere on the tab strip cycle through types.
 * - Shows nothing when [enabledTypes] is empty or only has one entry
 *   (nothing to switch between).
 */
@Composable
fun MediaTypeTabs(
    current: MediaType,
    enabledTypes: List<MediaType>,
    onSelect: (MediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    if (enabledTypes.size < 2) return
    val selectedIndex = enabledTypes.indexOf(current).coerceAtLeast(0)

    val swipeModifier = modifier
        .fillMaxWidth()
        .offset(y = (-6).dp)
        .swipeMediaType(current, onSelect, enabledTypes)

    if (enabledTypes.size > 4) {
        ScrollableTabRow(
            selectedTabIndex = selectedIndex,
            modifier = swipeModifier,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (selectedIndex < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedIndex])
                    )
                }
            }
        ) {
            enabledTypes.forEach { type ->
                Tab(
                    selected = current == type,
                    onClick = { onSelect(type) },
                    text = { Text(type.label) }
                )
            }
        }
    } else {
        TabRow(
            selectedTabIndex = selectedIndex,
            modifier = swipeModifier
        ) {
            enabledTypes.forEach { type ->
                Tab(
                    selected = current == type,
                    onClick = { onSelect(type) },
                    text = { Text(type.label) }
                )
            }
        }
    }
}
