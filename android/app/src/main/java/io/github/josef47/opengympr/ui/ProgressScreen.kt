package io.github.josef47.opengympr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.josef47.opengympr.data.Store
import io.github.josef47.opengympr.data.bestE1rm
import io.github.josef47.opengympr.data.dailyBest
import io.github.josef47.opengympr.data.fmt

@Composable
fun ProgressScreen(store: Store, modifier: Modifier, onOpen: (Long) -> Unit) {
    val used = store.exercises.mapNotNull { e -> store.setsOf(e.id).takeIf { it.isNotEmpty() }?.let { e to it } }
        .sortedByDescending { (_, list) -> list.last().time }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header() }
        item { Text("Progress", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp)) }
        if (used.isEmpty()) item {
            Text("Nothing logged yet.\nYour PR charts show up here.", textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp))
        }
        items(used, key = { it.first.id }) { (e, list) ->
            Card(Modifier.clickable { onOpen(e.id) }) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, fontWeight = FontWeight.Bold)
                        val best = bestE1rm(list)
                        Text((if (best > 0) "e1RM ${fmt(best, store.unit)} ${store.unit.label}" else "${list.size} sets") + " · ${dateOf(list.last().time)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Sparkline(dailyBest(list).map { it.value }, Modifier.size(96.dp, 36.dp))
                }
            }
        }
    }
}

@Composable
fun Sparkline(values: List<Double>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min(); val span = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = 2f + i * (size.width - 4f) / (values.size - 1)
            val y = size.height - 3f - ((v - min) / span).toFloat() * (size.height - 6f)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, 2.5.dp.toPx(), Offset(size.width - 2f, size.height - 3f - ((values.last() - min) / span).toFloat() * (size.height - 6f)))
    }
}
