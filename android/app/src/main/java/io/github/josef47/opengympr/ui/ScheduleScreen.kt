package io.github.josef47.opengympr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.josef47.opengympr.data.ScheduleItem
import io.github.josef47.opengympr.data.Store
import io.github.josef47.opengympr.data.localDay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle as DayStyle
import java.util.Locale

/** Fixed per-exercise colours so the same exercise has the same colour in the list and the week grid. */
private val BLOCK_COLORS = listOf(
    Color(0xFF6C9BF2), Color(0xFFF27D6C), Color(0xFF55C98F), Color(0xFFB98AF0),
    Color(0xFFF2B045), Color(0xFF45C7C7), Color(0xFFEC79AE), Color(0xFFA9B94A),
)
private val ON_BLOCK = Color(0xFF111316)

/**
 * Colour per scheduled exercise, in the order exercises were first planned: distinct for the first 8,
 * then the palette repeats. Adding an exercise never changes the colour of ones already planned.
 */
private fun colorMap(store: Store): Map<Long, Color> =
    store.schedule.sortedBy { it.id }.map { it.exerciseId }.distinct().withIndex().associate { (i, id) -> id to BLOCK_COLORS[i % BLOCK_COLORS.size] }

/** "Bench Press" -> "BP", "Squat" -> "Squ". Used when the full name does not fit a grid cell. */
fun abbrev(name: String): String {
    val words = name.split(' ', '-').filter { it.isNotBlank() }
    return if (words.size >= 2) words.take(3).joinToString("") { it.first().uppercase() } else name.take(3)
}

/**
 * Weekly schedule. Top 3/5: the selected day (starts on today) with check-offs and +.
 * Bottom 2/5: Monday–Sunday grid of colour-coded blocks. The plan repeats every week; check-offs are per date.
 * An item also counts as done when that exercise was logged on that date.
 */
@Composable
fun ScheduleScreen(store: Store, snack: SnackbarHostState, modifier: Modifier, onLog: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()
    var day by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(day)
    var picker by remember { mutableStateOf(false) }
    val c = MaterialTheme.colorScheme

    // (exerciseId, epochDay) pairs that have at least one logged set.
    val logged by remember { derivedStateOf { store.sets.mapTo(HashSet()) { it.exerciseId to localDay(it.time).toEpochDay() } } }
    fun loggedOn(item: ScheduleItem, d: Long) = (item.exerciseId to d) in logged
    fun done(item: ScheduleItem, d: Long) = loggedOn(item, d) || store.isChecked(item, d)

    val colors by remember { derivedStateOf { colorMap(store) } }
    val colorOf: (Long) -> Color = { colors[it] ?: BLOCK_COLORS[0] }
    val items = store.scheduleFor(date.dayOfWeek.value)
    val doneCount = items.count { done(it, day) }

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // ---------- top: the selected day ----------
        Column(Modifier.weight(3f).fillMaxWidth()) {
            Box(Modifier.padding(top = 16.dp)) { Header() }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(dayTitle(date, today), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        date.dayOfWeek.getDisplayName(DayStyle.FULL, Locale.getDefault()) + " · " + date.format(shortDate) +
                            if (items.isNotEmpty()) " · $doneCount/${items.size} done" else "",
                        color = c.onSurfaceVariant, fontSize = 13.sp)
                }
                RoundIcon(Ico.Back, "Previous day", c.surfaceVariant, c.onSurface) { day -= 1 }
                Spacer(Modifier.width(6.dp))
                RoundIcon(Ico.Forward, "Next day", c.surfaceVariant, c.onSurface) { day += 1 }
                Spacer(Modifier.width(10.dp))
                RoundIcon(Ico.Plus, "Add exercise", c.primary, c.onPrimary) { picker = true }
            }
            if (date != today) {
                Text("Back to today", color = c.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { day = today.toEpochDay() }.padding(vertical = 4.dp, horizontal = 2.dp))
            }
            if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("Nothing planned for ${date.dayOfWeek.getDisplayName(DayStyle.FULL, Locale.getDefault())}.\n" +
                        "Tap + to add an exercise. It repeats every week.",
                        textAlign = TextAlign.Center, color = c.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.id }) { item ->
                        val future = date.isAfter(today)
                        ScheduleRow(
                            name = store.exercise(item.exerciseId)?.name ?: "?",
                            color = colorOf(item.exerciseId),
                            sub = when {
                                loggedOn(item, day) -> "Logged"
                                future -> "Planned"
                                else -> "Tap to log"
                            },
                            done = done(item, day),
                            lockedDone = loggedOn(item, day),
                            canCheck = !future,
                            onOpen = { onLog(item.exerciseId) },
                            onCheck = {
                                if (loggedOn(item, day)) {
                                    scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(AppSnack("Done: logged in Log")) }
                                } else {
                                    store.toggleCheck(item, day)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            },
                            onRemove = {
                                val marks = store.removeFromSchedule(item)
                                scope.launch {
                                    snack.currentSnackbarData?.dismiss()
                                    if (snack.showSnackbar(AppSnack("Removed from ${date.dayOfWeek.getDisplayName(DayStyle.FULL, Locale.getDefault())}", "Undo")) ==
                                        SnackbarResult.ActionPerformed) store.restoreSchedule(item, marks)
                                }
                            },
                        )
                    }
                }
            }
        }

        // ---------- bottom: the week ----------
        WeekGrid(store, date, today, Modifier.weight(2f).fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
            done = ::done, colorOf = colorOf, onPick = { day = it.toEpochDay() })
    }

    if (picker) ExercisePicker(store, onDismiss = { picker = false }) { id ->
        picker = false
        if (store.addToSchedule(id, date.dayOfWeek.value) == null) {
            scope.launch { snack.currentSnackbarData?.dismiss(); snack.showSnackbar(AppSnack("Already on this day")) }
        }
    }
}

private fun dayTitle(date: LocalDate, today: LocalDate) = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.dayOfWeek.getDisplayName(DayStyle.FULL, Locale.getDefault())
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(bg).clickable(onClickLabel = desc, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = fg, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ScheduleRow(
    name: String, color: Color, sub: String, done: Boolean, lockedDone: Boolean, canCheck: Boolean,
    onOpen: () -> Unit, onCheck: () -> Unit, onRemove: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    Card {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.weight(1f).fillMaxHeight().clickable(onClick = onOpen).padding(horizontal = 14.dp), verticalArrangement = Arrangement.Center) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (done) c.onSurfaceVariant else c.onSurface)
                Text(sub, fontSize = 12.sp, color = if (lockedDone) c.primary else c.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) { Icon(Ico.Close, "Remove from this day", tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
            val m = Modifier.padding(end = 12.dp).size(32.dp).clip(CircleShape)
            Box(
                (if (done) m.background(c.primary) else m.border(2.dp, if (canCheck) c.onSurfaceVariant else c.outline, CircleShape))
                    .clickable(enabled = canCheck, onClickLabel = if (done) "Mark not done" else "Mark done", onClick = onCheck),
                contentAlignment = Alignment.Center,
            ) { if (done) Icon(Ico.Check, null, tint = c.onPrimary, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun WeekGrid(
    store: Store, selected: LocalDate, today: LocalDate, modifier: Modifier,
    done: (ScheduleItem, Long) -> Boolean, colorOf: (Long) -> Color, onPick: (LocalDate) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val monday = selected.with(DayOfWeek.MONDAY)
    Card(modifier) {
        Row(Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0..6) {
                val d = monday.plusDays(i.toLong())
                val isSel = d == selected
                val isToday = d == today
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                        .background(if (isSel) c.surfaceVariant else Color.Transparent)
                        .clickable(onClickLabel = d.dayOfWeek.getDisplayName(DayStyle.FULL, Locale.getDefault())) { onPick(d) }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.dayOfWeek.getDisplayName(DayStyle.SHORT, Locale.getDefault()).take(3), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = if (isToday) c.primary else c.onSurfaceVariant, maxLines = 1)
                    Text(d.dayOfMonth.toString(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        color = if (isToday) c.primary else c.onSurface)
                    Column(Modifier.fillMaxWidth().padding(top = 4.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        store.scheduleFor(d.dayOfWeek.value).forEach { item ->
                            Block(store.exercise(item.exerciseId)?.name ?: "?", colorOf(item.exerciseId), done(item, d.toEpochDay()))
                        }
                    }
                }
            }
        }
    }
}

/** A colour block in the week grid: full name when it fits in two lines (no word broken), otherwise an abbreviation. */
@Composable
private fun Block(name: String, color: Color, done: Boolean) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, lineHeight = 11.sp)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(if (done) color else color.copy(alpha = 0.28f))
            .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val maxPx = constraints.maxWidth
        val fits = remember(name, maxPx, density) {
            name.split(' ').all { measurer.measure(it, style, maxLines = 1).size.width <= maxPx } &&
                measurer.measure(name, style, constraints = Constraints(maxWidth = maxPx)).lineCount <= 2
        }
        Text(if (fits) name else abbrev(name), style = style, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Clip, color = if (done) ON_BLOCK else MaterialTheme.colorScheme.onSurface)
    }
}
