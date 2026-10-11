package io.github.josef47.opengympr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.josef47.opengympr.data.LiftSet
import io.github.josef47.opengympr.data.Pr
import io.github.josef47.opengympr.data.Store
import io.github.josef47.opengympr.data.bestE1rm
import io.github.josef47.opengympr.data.flagPrs
import io.github.josef47.opengympr.data.fmt
import io.github.josef47.opengympr.data.localDay
import io.github.josef47.opengympr.data.prOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

// ---------- shared bits ----------

class AppSnack(override val message: String, override val actionLabel: String? = null, val pr: Boolean = false) : SnackbarVisuals {
    override val withDismissAction = false
    override val duration = SnackbarDuration.Short
}

@Composable
fun PrSnackbar(data: SnackbarData) {
    val pr = (data.visuals as? AppSnack)?.pr == true
    val c = MaterialTheme.colorScheme
    Snackbar(
        modifier = Modifier.padding(12.dp),
        containerColor = if (pr) c.primary else c.inverseSurface,
        contentColor = if (pr) c.onPrimary else c.inverseOnSurface,
        actionContentColor = if (pr) c.onPrimary else c.primary,
        shape = RoundedCornerShape(14.dp),
        action = data.visuals.actionLabel?.let { { TextButton(onClick = { data.performAction() }) { Text(it, fontWeight = FontWeight.Bold) } } },
    ) { Text(data.visuals.message, fontWeight = FontWeight.Bold) }
}

val shortDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
fun dateOf(t: Long): String = localDay(t).format(shortDate)

fun prLabel(pr: Pr, store: Store) = when (pr) {
    is Pr.Reps -> "Rep PR @ ${fmt(pr.atKg, store.unit)}"
    else -> pr.label
}

@Composable
fun Badge(text: String, gold: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
        color = if (gold) androidx.compose.ui.graphics.Color(0xFF1D1500) else c.onPrimary,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (gold) c.secondary else c.primary).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
        .border(1.dp, c.outline, RoundedCornerShape(16.dp))) { content() }
}

/** A list row for one set: index, weight × reps, PR badge, delete. */
@Composable
fun SetRow(store: Store, lead: String, set: LiftSet, pr: Pr?, gold: Boolean, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(lead, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.width(56.dp))
        Text("${fmt(set.weightKg, store.unit)} ${store.unit.label} × ${set.reps}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        pr?.let { Badge(prLabel(it, store), gold) }
        IconButton(onClick = onDelete) { Icon(Ico.Close, "Delete set", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
    }
}

/** Deletes a set and offers Undo. */
fun deleteWithUndo(store: Store, set: LiftSet, snack: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    store.delete(set)
    scope.launch {
        snack.currentSnackbarData?.dismiss()
        if (snack.showSnackbar(AppSnack("Set deleted", "Undo")) == SnackbarResult.ActionPerformed) store.restore(set)
    }
}

// ---------- Log screen ----------

@Composable
fun LogScreen(store: Store, snack: SnackbarHostState, modifier: Modifier, onOpen: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val exId = store.current
    val hist = store.setsOf(exId)
    val prev = hist.lastOrNull()
    var showPicker by remember { mutableStateOf(false) }

    // Inputs start at the last set of this exercise (re-initialised when the exercise or unit changes).
    var weight by remember(exId, store.unit) {
        mutableStateOf(prev?.let { fmt(it.weightKg, store.unit) } ?: if (store.unit.label == "lb") "45" else "20")
    }
    var reps by remember(exId) { mutableStateOf((prev?.reps ?: 5).toString()) }

    val today = LocalDate.now()
    val todaySets = store.sets.filter { localDay(it.time) == today }
    val groups = todaySets.groupBy { it.exerciseId }

    fun log() {
        val w = weight.replace(',', '.').toDoubleOrNull()
        val r = reps.toIntOrNull()
        if (w == null || r == null || w < 0 || w > 2000 || r < 1 || r > 100) {
            scope.launch { snack.showSnackbar(AppSnack("Check weight and reps")) }; return
        }
        val kg = Math.round(w * store.unit.kgPer * 1000) / 1000.0
        val pr = prOf(io.github.josef47.opengympr.data.LiftSet(0, exId, kg, r, 0), hist)
        val set = store.log(exId, kg, r)
        haptic.performHapticFeedback(if (pr != null) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        scope.launch {
            snack.currentSnackbarData?.dismiss()
            if (pr != null) snack.showSnackbar(AppSnack("🏆 ${prLabel(pr, store)}!  ${fmt(kg, store.unit)} × $r", pr = true))
            else if (snack.showSnackbar(AppSnack("Logged ${fmt(kg, store.unit)} ${store.unit.label} × $r", "Undo")) == SnackbarResult.ActionPerformed) store.delete(set)
        }
    }

    LazyColumn(modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header() }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPicker = true }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(store.exercise(exId)?.name ?: "Pick an exercise", fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("change", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Ico.Down, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    val best = bestE1rm(hist)
                    Text(
                        if (prev == null) "First time — nice."
                        else "Last: ${fmt(prev.weightKg, store.unit)} ${store.unit.label} × ${prev.reps} · ${dateOf(prev.time)}" +
                            if (best > 0) " · Best e1RM ${fmt(best, store.unit)}" else "",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp, start = 2.dp))
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stepper("Weight (${store.unit.label})", weight, KeyboardType.Decimal, Modifier.weight(1f),
                            onStep = { d -> weight = fmtNum(((weight.replace(',', '.').toDoubleOrNull() ?: 0.0) + d * store.unit.step).coerceAtLeast(0.0)) },
                            onChange = { weight = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' }.take(7) }, onDone = ::log)
                        Stepper("Reps", reps, KeyboardType.Number, Modifier.weight(1f),
                            onStep = { d -> reps = ((reps.toIntOrNull() ?: 0) + d).coerceIn(1, 100).toString() },
                            onChange = { reps = it.filter(Char::isDigit).take(3) }, onDone = ::log)
                    }
                    Button(onClick = ::log, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(56.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                        Text("Log set", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
        item { Text("Today", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp)) }
        if (groups.isEmpty()) item {
            Text("No sets yet today.\nPick an exercise, set weight and reps, hit Log set.", textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp))
        }
        groups.forEach { (id, list) ->
            item(key = "g$id") {
                val flags = flagPrs(store.setsOf(id))
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
                        Text(store.exercise(id)?.name ?: "?", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).clickable { onOpen(id) })
                        Text("${list.size} set${if (list.size > 1) "s" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Card { Column { list.forEachIndexed { i, s -> SetRow(store, "${i + 1}", s, flags[s.id], false) { deleteWithUndo(store, s, snack, scope) } } } }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showPicker) ExercisePicker(store, onDismiss = { showPicker = false }) { store.select(it); showPicker = false }
}

private fun fmtNum(v: Double): String {
    val r = (v * 100).roundToInt() / 100.0
    return if (r % 1.0 == 0.0) r.toLong().toString() else r.toString()
}

@Composable
fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
        Text("OpenGym", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Text("PR", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Stepper(label: String, value: String, keyboard: KeyboardType, modifier: Modifier,
                    onStep: (Int) -> Unit, onChange: (String) -> Unit, onDone: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(c.surfaceVariant).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label.uppercase(), fontSize = 11.sp, letterSpacing = 1.sp, color = c.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            StepButton("−", "Less") { onStep(-1) }
            BasicTextField(value, onChange, singleLine = true, cursorBrush = SolidColor(c.primary),
                // Shrink long values (e.g. 102.5) so they fit between the buttons on narrow phones.
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = c.onSurface, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    fontSize = when { value.length <= 3 -> 28.sp; value.length == 4 -> 23.sp; else -> 19.sp }),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.weight(1f))
            StepButton("+", "More") { onStep(1) }
        }
    }
}

@Composable
private fun StepButton(sym: String, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center) { Text(sym, fontSize = 24.sp) }
}

// ---------- exercise picker ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePicker(store: Store, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    var q by remember { mutableStateOf("") }
    val counts = store.sets.groupingBy { it.exerciseId }.eachCount()
    val term = q.trim()
    val list = store.exercises.filter { it.name.contains(term, ignoreCase = true) }
        .sortedWith(compareByDescending<io.github.josef47.opengympr.data.Exercise> { counts[it.id] ?: 0 }.thenBy { it.name })
    val exact = store.exercises.any { it.name.equals(term, ignoreCase = true) }
    val c = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        BasicTextField(q, { q = it.take(60) }, singleLine = true, cursorBrush = SolidColor(c.primary),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                when { term.isNotEmpty() && !exact -> onPick(store.addExercise(term).id); list.isNotEmpty() -> onPick(list.first().id) }
            }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(12.dp)).background(c.surfaceVariant).padding(14.dp),
            decorationBox = { inner -> Box { if (q.isEmpty()) Text("Search or add exercise…", color = c.onSurfaceVariant); inner() } })
        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (term.isNotEmpty() && !exact) item {
                Text("+ Add “$term”", color = c.primary, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(store.addExercise(term).id) }.padding(horizontal = 24.dp, vertical = 14.dp))
            }
            items(list, key = { it.id }) { e ->
                Row(Modifier.fillMaxWidth().clickable { onPick(e.id) }.padding(horizontal = 24.dp, vertical = 14.dp)) {
                    Text(e.name, modifier = Modifier.weight(1f))
                    counts[e.id]?.let { Text("$it sets", color = c.onSurfaceVariant, fontSize = 13.sp) }
                }
            }
        }
    }
}
