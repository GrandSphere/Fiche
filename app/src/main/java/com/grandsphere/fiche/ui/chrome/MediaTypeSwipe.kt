package com.grandsphere.fiche.ui.chrome

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.grandsphere.fiche.domain.model.MediaType

fun Modifier.swipeMediaType(
    current: MediaType,
    onChange: (MediaType) -> Unit,
    enabled: List<MediaType> = MediaType.entries
): Modifier = pointerInput(current, enabled) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragEnd = {
            val next = when {
                total < -64f -> current.next(enabled)
                total > 64f -> current.previous(enabled)
                else -> null
            }
            if (next != null) onChange(next)
            total = 0f
        },
        onHorizontalDrag = { _, dragAmount -> total += dragAmount }
    )
}
