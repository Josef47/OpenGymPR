package io.github.josef47.opengympr.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.josef47.opengympr.BuildConfig
import io.github.josef47.opengympr.data.Store
import io.github.josef47.opengympr.data.ThemeMode
import io.github.josef47.opengympr.data.WeightUnit
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SettingsScreen(store: Store, snack: SnackbarHostState, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = MaterialTheme.colorScheme
    var pendingImport by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    fun say(msg: String) = scope.launch { snack.showSnackbar(AppSnack(msg)) }

    // Storage Access Framework: the user picks where the file goes. No storage permission needed.
    fun writer(text: () -> String) = { uri: android.net.Uri? ->
        if (uri != null) runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(text().toByteArray()) } }
            .onSuccess { say("Exported") }.onFailure { say("Export failed") }
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json"), writer { store.exportJson() })
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv"), writer { store.exportCsv() })
    val importJson = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) } }
            .onSuccess { pendingImport = it }.onFailure { say("Could not read that file") }
    }

    val usedExercises = store.sets.map { it.exerciseId }.toSet().size
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Header() }
        item { Section("Settings") }
        item {
            Card {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Setting("Units") { Toggle(WeightUnit.entries, store.unit, { it.label }) { store.changeUnit(it) } }
                    HorizontalDivider(color = c.outline)
                    Setting("Theme") { Toggle(ThemeMode.entries, store.theme, { it.name.lowercase() }) { store.changeTheme(it) } }
                }
            }
        }
        item { Section("Your data") }
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Everything is stored only on this phone. Export a backup now and then — or keep Android backup on, which restores it on a new phone.",
                        color = c.onSurfaceVariant, fontSize = 13.sp)
                    Text("${store.sets.size} sets · $usedExercises exercises", fontSize = 14.sp, modifier = Modifier.padding(vertical = 10.dp))
                    val today = LocalDate.now()
                    Action("Export backup (JSON)") { exportJson.launch("opengympr-backup-$today.json") }
                    Action("Export spreadsheet (CSV)") { exportCsv.launch("opengympr-$today.csv") }
                    Action("Import backup") { importJson.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    OutlinedButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp),
                        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = c.error)) { Text("Delete all data") }
                }
            }
        }
        item { Section("The deal") }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Free. Forever.", "No ads.", "No trackers, no analytics.", "No account. No internet permission — the app can't send your data anywhere.", "Open source (MIT).")
                        .forEach { Row { Text("✓  ", color = c.primary, fontWeight = FontWeight.ExtraBold); Text(it) } }
                }
            }
        }
        item {
            Text("OpenGymPR ${BuildConfig.VERSION_NAME} · e1RM = Epley formula, sets ≤ 12 reps", color = c.onSurfaceVariant, fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
        }
    }

    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace your data?") },
            text = { Text("Importing replaces all sets and exercises on this phone with the backup.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    runCatching { store.importJson(text) }.onSuccess { say("Imported $it sets") }.onFailure { say("That file is not an OpenGymPR backup") }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }
    if (confirmWipe) AlertDialog(
        onDismissRequest = { confirmWipe = false },
        title = { Text("Delete all data?") },
        text = { Text("All sets and custom exercises on this phone will be deleted. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmWipe = false; store.wipe(); say("All data deleted") }) { Text("Delete", color = c.error) } },
        dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
    )
}

@Composable private fun Section(t: String) = Text(t, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun Setting(label: String, control: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f)); control()
    }

@Composable
private fun <T> Toggle(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.clip(RoundedCornerShape(50)).background(c.surfaceVariant).padding(3.dp)) {
        options.forEach { o ->
            val on = o == selected
            Text(label(o), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (on) c.background else c.onSurface,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) c.onSurface else c.surfaceVariant)
                    .clickable { onPick(o) }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) =
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp), shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Text(label, fontWeight = FontWeight.Bold)
    }
