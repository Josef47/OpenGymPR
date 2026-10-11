package io.github.josef47.opengympr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.josef47.opengympr.data.DayPoint
import io.github.josef47.opengympr.data.Store
import io.github.josef47.opengympr.data.WeightUnit
import io.github.josef47.opengympr.data.bestE1rm
import io.github.josef47.opengympr.data.dailyBest
import io.github.josef47.opengympr.data.flagPrs
import io.github.josef47.opengympr.data.fmt
import io.github.josef47.opengympr.data.repMaxes
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class Range(val label: String, val days: Long) { M3("3 months", 92), Y1("1 year", 366), ALL("All", Long.MAX_VALUE / 86_400_000L) }

@Composable
fun DetailScreen(store: Store, id: Long, snack: SnackbarHostState, modifier: Modifier, onBack: () -> Unit, onLog: (Long) -> Unit) {
    val ex = store.exercise(id)
    LaunchedEffect(ex) { if (ex == null) onBack() }
    if (ex == null) return
    val scope = rememberCoroutineScope()
    val list = store.setsOf(id)
    val flags = flagPrs(list)
    var range by rememberSaveable { mutableStateOf(Range.ALL) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme
    val now = System.currentTimeMillis()
    val points = dailyBest(list).filter { now - it.time <= range.days * 86_400_000L }
    val rm = repMaxes(list)
    val recent = list.asReversed().take(100)
    val day = DateTimeFormatter.ofPattern("d MMM")

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.clickable(onClick = onBack).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ico.Back, null, tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Text("Progress", color = c.onSurfaceVariant)
            }
            Text(ex.name, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Kpi(fmt(bestE1rm(list), store.unit), "best e1RM", Modifier.weight(1f))
                Kpi(fmt(list.maxOfOrNull { it.weightKg } ?: 0.0, store.unit), "heaviest (${store.unit.label})", Modifier.weight(1f))
                Kpi("${list.size}", "sets", Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Range.entries.forEach { r ->
                    val on = r == range
                    Text(r.label, textAlign = TextAlign.Center, fontSize = 13.sp, color = if (on) c.background else c.onSurface,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (on) c.onSurface else c.surface)
                            .border(1.dp, if (on) c.onSurface else c.outline, RoundedCornerShape(50)).clickable { range = r }.padding(vertical = 7.dp))
                }
            }
        }
        item {
            Card {
                if (points.size < 2) Text("Log this lift on two different days to see a chart.", color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp))
                else E1rmChart(points, store.unit, Modifier.fillMaxWidth().height(200.dp).padding(start = 4.dp, end = 12.dp, top = 12.dp, bottom = 6.dp))
            }
        }
        item { Text("Rep maxes", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp)) }
        item {
            Card {
                Column(Modifier.padding(vertical = 4.dp)) {
                    if (rm.isEmpty()) Text("No sets ≤ 12 reps yet", color = c.onSurfaceVariant, modifier = Modifier.padding(14.dp))
                    rm.forEach { (r, s) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Text("${r}RM", color = c.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("${fmt(s.weightKg, store.unit)} ${store.unit.label}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.4f))
                            Text(dateOf(s.time), color = c.onSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item { Text("History", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 10.dp)) }
        item {
            Card {
                Column {
                    recent.forEach { s ->
                        SetRow(store, io.github.josef47.opengympr.data.localDay(s.time).format(day), s, flags[s.id], true) {
                            deleteWithUndo(store, s, snack, scope)
                        }
                    }
                }
            }
            if (list.size > 100) Text("Showing the latest 100 sets. Export in Settings for the full history.",
                color = c.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        item {
            Button(onClick = { onLog(id) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp), shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.surfaceVariant, contentColor = c.onSurface)) {
                Text("Log ${ex.name}", fontWeight = FontWeight.Bold)
            }
            if (ex.custom) OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.error)) { Text("Delete exercise") }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${ex.name}”?") },
        text = { Text("This also deletes its ${list.size} sets. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; store.deleteExercise(id); onBack() }) { Text("Delete", color = c.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** e1RM per training day. Filled dots mark days that set a new running best. */
@Composable
private fun E1rmChart(points: List<DayPoint>, unit: WeightUnit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()
    val label = TextStyle(color = c.onSurfaceVariant, fontSize = 10.sp)
    val fmtDate = DateTimeFormatter.ofPattern("d MMM yy")
    Canvas(modifier) {
        val vals = points.map { it.value / unit.kgPer }
        var lo = vals.min(); var hi = vals.max()
        val pad = ((hi - lo) * 0.15).takeIf { it > 0 } ?: 2.5
        lo -= pad; hi += pad
        val left = 36.dp.toPx(); val bottom = 18.dp.toPx()
        val w = size.width - left; val h = size.height - bottom
        val t0 = points.first().time; val tSpan = (points.last().time - t0).coerceAtLeast(1)
        fun x(t: Long) = left + (t - t0).toFloat() / tSpan * w
        fun y(v: Double) = ((1 - (v - lo) / (hi - lo)) * h).toFloat()

        for (i in 0..3) {
            val v = lo + (hi - lo) * i / 3
            drawLine(c.outline, Offset(left, y(v)), Offset(size.width, y(v)), 1f)
            val tl = measurer.measure(v.roundToInt().toString(), label)
            drawText(tl, topLeft = Offset(left - tl.size.width - 6.dp.toPx(), y(v) - tl.size.height / 2))
        }
        val line = Path(); val area = Path()
        points.forEachIndexed { i, p ->
            val px = x(p.time); val py = y(vals[i])
            if (i == 0) { line.moveTo(px, py); area.moveTo(px, h); area.lineTo(px, py) } else { line.lineTo(px, py); area.lineTo(px, py) }
        }
        area.lineTo(x(points.last().time), h); area.close()
        drawPath(area, c.primary.copy(alpha = 0.12f))
        drawPath(line, c.primary, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (points.size <= 40) {
            var best = Double.NEGATIVE_INFINITY
            points.forEachIndexed { i, p ->
                val pr = vals[i] > best; best = maxOf(best, vals[i])
                val o = Offset(x(p.time), y(vals[i]))
                drawCircle(if (pr) c.primary else c.background, 3.5.dp.toPx(), o)
                drawCircle(c.primary, 3.5.dp.toPx(), o, style = Stroke(2.dp.toPx()))
            }
        }
        val first = measurer.measure(io.github.josef47.opengympr.data.localDay(t0).format(fmtDate), label)
        val last = measurer.measure(io.github.josef47.opengympr.data.localDay(points.last().time).format(fmtDate), label)
        drawText(first, topLeft = Offset(left, h + 4.dp.toPx()))
        drawText(last, topLeft = Offset(size.width - last.size.width, h + 4.dp.toPx()))
    }
}
