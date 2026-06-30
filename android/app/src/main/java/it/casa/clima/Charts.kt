package it.casa.clima

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import it.casa.clima.net.HistoryPoint
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** Una serie del grafico multi-linea. */
data class Series(val label: String, val color: Color, val dashed: Boolean, val points: List<HistoryPoint>)

private fun parseEpoch(t: String): Long? =
    try { OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch (e: Exception) { null }

/**
 * Grafico multi-serie su Canvas (niente librerie esterne): assi/unità, griglia tenue,
 * linee neon con riempimento a gradiente sotto la prima serie continua.
 */
@Composable
fun MultiSeriesChart(series: List<Series>, modifier: Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()

    val parsed = series.map { s -> s to s.points.mapNotNull { p -> p.value?.let { v -> parseEpoch(p.time)?.let { it to v } } } }
    val all = parsed.flatMap { it.second }
    if (all.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Dati insufficienti", style = MaterialTheme.typography.bodySmall, color = labelColor)
        }
        return
    }
    var minY = all.minOf { it.second }; var maxY = all.maxOf { it.second }
    if (minY == maxY) { minY -= 1; maxY += 1 }
    val pad = (maxY - minY) * 0.08
    minY -= pad; maxY += pad
    val rangeY = maxY - minY
    val tMin = all.minOf { it.first }; val tMax = all.maxOf { it.first }
    val spanMs = (tMax - tMin).coerceAtLeast(1L)
    val fmt = DateTimeFormatter.ofPattern(if (spanMs > 36L * 3600 * 1000) "dd/MM" else "HH:mm").withZone(ZoneId.systemDefault())
    val labelStyle = TextStyle(fontSize = 9.sp, color = labelColor)

    Canvas(modifier) {
        val leftPad = 46f; val bottomPad = 30f; val topPad = 12f; val rightPad = 16f
        val plotW = size.width - leftPad - rightPad
        val plotH = size.height - topPad - bottomPad
        val ticks = 4
        for (i in 0..ticks) {
            val frac = i.toFloat() / ticks
            val v = minY + rangeY * frac
            val y = topPad + plotH - plotH * frac
            drawLine(gridColor.copy(alpha = 0.5f), Offset(leftPad, y), Offset(leftPad + plotW, y), strokeWidth = 1f)
            val l = measurer.measure("${v.roundToInt()}°", labelStyle)
            drawText(l, topLeft = Offset(leftPad - l.size.width - 6f, y - l.size.height / 2))
        }
        for (i in 0..2) {
            val frac = i / 2f
            val tx = leftPad + plotW * frac
            val l = measurer.measure(fmt.format(Instant.ofEpochMilli(tMin + (spanMs * frac).toLong())), labelStyle)
            drawText(l, topLeft = Offset((tx - l.size.width / 2).coerceIn(0f, size.width - l.size.width), topPad + plotH + 8f))
        }
        fun px(t: Long) = leftPad + plotW * ((t - tMin).toFloat() / spanMs)
        fun py(v: Double) = topPad + plotH - plotH * ((v - minY) / rangeY).toFloat()

        parsed.forEachIndexed { idx, (s, pts) ->
            if (pts.size < 2) return@forEachIndexed
            val sorted = pts.sortedBy { it.first }
            val line = Path()
            sorted.forEachIndexed { i, (t, v) ->
                val x = px(t); val y = py(v)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            // riempimento a gradiente solo per la prima serie continua (la principale)
            if (idx == 0 && !s.dashed) {
                val fill = Path().apply {
                    addPath(line)
                    lineTo(px(sorted.last().first), topPad + plotH)
                    lineTo(px(sorted.first().first), topPad + plotH)
                    close()
                }
                drawPath(fill, brush = Brush.verticalGradient(
                    0f to s.color.copy(alpha = 0.28f),
                    1f to s.color.copy(alpha = 0f),
                    startY = topPad, endY = topPad + plotH,
                ))
            }
            drawPath(line, color = s.color, style = Stroke(
                width = 4f, cap = StrokeCap.Round,
                pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null,
            ))
        }
    }
}
