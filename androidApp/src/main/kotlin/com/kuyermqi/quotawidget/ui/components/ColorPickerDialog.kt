package com.kuyermqi.quotawidget.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kuyermqi.quotawidget.R
import java.util.Locale

private val HueBarColors = List(13) { index ->
    Color(AndroidColor.HSVToColor(floatArrayOf(index * 360f / 12f, 0.9f, 1f)))
}

/** Compact HSV picker for the custom theme seed colour. */
@Composable
fun ThemeColorPickerDialog(
    initialColorArgb: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val initialHsv = remember(initialColorArgb) {
        FloatArray(3).also { AndroidColor.colorToHSV(initialColorArgb, it) }
    }
    var hue by remember(initialColorArgb) { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember(initialColorArgb) { mutableFloatStateOf(initialHsv[1]) }
    var value by remember(initialColorArgb) { mutableFloatStateOf(initialHsv[2]) }

    val previewArgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value))
    val previewColor = Color(previewArgb)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_settings_color_picker_title)) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = formatColorHex(previewArgb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .pointerInput(hue) {
                            detectTapGestures { offset ->
                                updateSaturationValue(
                                    offset = offset,
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                ) { s, v ->
                                    saturation = s
                                    value = v
                                }
                            }
                        }
                        .pointerInput(hue) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                updateSaturationValue(
                                    offset = change.position,
                                    width = size.width.toFloat(),
                                    height = size.height.toFloat(),
                                ) { s, v ->
                                    saturation = s
                                    value = v
                                }
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val pureHue = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f)))
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.White, pureHue),
                            ),
                        )
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                            ),
                        )
                        val markerRadius = 10.dp.toPx()
                        val marker = Offset(
                            x = markerRadius + saturation * (size.width - 2 * markerRadius),
                            y = markerRadius + (1f - value) * (size.height - 2 * markerRadius),
                        )
                        drawCircle(
                            color = Color.White,
                            radius = markerRadius,
                            center = marker,
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.25f),
                            radius = markerRadius,
                            center = marker,
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                        drawCircle(
                            color = previewColor,
                            radius = 6.dp.toPx(),
                            center = marker,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HueSlider(
                    hue = hue,
                    onHueChange = { hue = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(previewArgb) }) {
                Text(stringResource(R.string.app_settings_color_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_settings_cancel))
            }
        },
    )
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onHueChange(
                        hueFromPosition(
                            x = offset.x,
                            width = size.width.toFloat(),
                            thumbRadius = 10.dp.toPx(),
                        ),
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onHueChange(
                        hueFromPosition(
                            x = change.position.x,
                            width = size.width.toFloat(),
                            thumbRadius = 10.dp.toPx(),
                        ),
                    )
                }
            },
    ) {
        val thumbRadius = 10.dp.toPx()
        val barHeight = 24.dp.toPx()
        val barWidth = (size.width - 2 * thumbRadius).coerceAtLeast(1f)
        drawRoundRect(
            brush = Brush.horizontalGradient(HueBarColors),
            topLeft = Offset(thumbRadius, (size.height - barHeight) / 2f),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barHeight / 2f, barHeight / 2f),
        )
        val marker = Offset(
            x = thumbRadius + (hue / 360f) * barWidth,
            y = size.height / 2f,
        )
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = marker,
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.2f),
            radius = thumbRadius,
            center = marker,
            style = Stroke(width = 1.5.dp.toPx()),
        )
        drawCircle(
            color = Color(AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))),
            radius = thumbRadius - 3.dp.toPx(),
            center = marker,
        )
    }
}

private fun hueFromPosition(x: Float, width: Float, thumbRadius: Float): Float {
    val barWidth = width - 2 * thumbRadius
    if (barWidth <= 0f) return 0f
    return (((x - thumbRadius) / barWidth) * 360f).coerceIn(0f, 360f)
}

private fun updateSaturationValue(
    offset: Offset,
    width: Float,
    height: Float,
    onUpdate: (saturation: Float, value: Float) -> Unit,
) {
    if (width <= 0f || height <= 0f) return
    val s = (offset.x / width).coerceIn(0f, 1f)
    val v = (1f - offset.y / height).coerceIn(0f, 1f)
    onUpdate(s, v)
}

private fun formatColorHex(argb: Int): String =
    String.format(Locale.ROOT, "#%06X", 0xFFFFFF and argb)
