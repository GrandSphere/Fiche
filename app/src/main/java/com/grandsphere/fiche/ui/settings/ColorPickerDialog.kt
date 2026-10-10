package com.grandsphere.fiche.ui.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun ColorPickerDialog(
    title: String,
    initialArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val hsv = remember(initialArgb) {
        val out = FloatArray(3)
        AndroidColor.colorToHSV(initialArgb, out)
        out
    }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    val current = Color(AndroidColor.HSVToColor(floatArrayOf(hue, sat, value)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(current)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
                Spacer(Modifier.height(12.dp))
                SvSquare(hue = hue, saturation = sat, value = value, onChange = { s, v ->
                    sat = s
                    value = v
                })
                Spacer(Modifier.height(12.dp))
                HueBar(hue = hue, onHue = { hue = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current.toArgb()) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SvSquare(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit
) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(160.dp)
            .pointerInput(hue) {
                fun at(pos: Offset) {
                    onChange(
                        (pos.x / size.width).coerceIn(0f, 1f),
                        1f - (pos.y / size.height).coerceIn(0f, 1f)
                    )
                }
                detectTapGestures { at(it) }
            }
            .pointerInput(hue) {
                detectDragGestures { change, _ ->
                    val pos = change.position
                    onChange(
                        (pos.x / size.width).coerceIn(0f, 1f),
                        1f - (pos.y / size.height).coerceIn(0f, 1f)
                    )
                }
            }
    ) {
        val hueColor = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
        drawRect(Brush.horizontalGradient(listOf(Color.White, hueColor)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val cx = saturation * size.width
        val cy = (1f - value) * size.height
        drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
        drawCircle(Color.Black, radius = 8.dp.toPx(), center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun HueBar(hue: Float, onHue: (Float) -> Unit) {
    val hues = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { onHue((it.x / size.width).coerceIn(0f, 1f) * 360f) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    onHue((change.position.x / size.width).coerceIn(0f, 1f) * 360f)
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(hues))
        val x = (hue / 360f) * size.width
        drawCircle(Color.White, radius = 10.dp.toPx(), center = Offset(x, size.height / 2f))
        drawCircle(
            Color.Black,
            radius = 10.dp.toPx(),
            center = Offset(x, size.height / 2f),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
