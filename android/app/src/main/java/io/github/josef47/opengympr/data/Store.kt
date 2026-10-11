package io.github.josef47.opengympr.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

val DEFAULT_EXERCISES = listOf(
    "Bench Press", "Squat", "Deadlift", "Overhead Press", "Barbell Row", "Pull-up", "Incline Bench Press",
    "Romanian Deadlift", "Front Squat", "Dumbbell Bench Press", "Dumbbell Shoulder Press", "Lat Pulldown",
    "Leg Press", "Hip Thrust", "Dips", "Bicep Curl", "Tricep Pushdown", "Leg Curl", "Leg Extension", "Calf Raise",
)

enum class WeightUnit(val label: String, val kgPer: Double, val step: Double) {
    KG("kg", 1.0, 2.5), LB("lb", 0.45359237, 5.0)
}

enum class ThemeMode { AUTO, DARK, LIGHT }

private class Db(ctx: Context) : SQLiteOpenHelper(ctx, "opengympr.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE exercise (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE COLLATE NOCASE, custom INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE TABLE lift_set (id INTEGER PRIMARY KEY AUTOINCREMENT, exercise_id INTEGER NOT NULL REFERENCES exercise(id) ON DELETE CASCADE, weight_kg REAL NOT NULL, reps INTEGER NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX ix_set_ex_ts ON lift_set(exercise_id, ts)")
        DEFAULT_EXERCISES.forEach { db.insert("exercise", null, ContentValues().apply { put("name", it) }) }
    }
    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)
    override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) = Unit
}

/**
 * Single source of truth: SQLite on disk, mirrored in Compose state for instant UI.
 * A gym log is small (years of training = tens of thousands of rows), so keeping it in memory is fine.
 */
class Store(ctx: Context) {
    private val db = Db(ctx.applicationContext).writableDatabase
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val exercises = mutableStateListOf<Exercise>()
    val sets = mutableStateListOf<LiftSet>()            // oldest first
    var unit by mutableStateOf(WeightUnit.valueOf(prefs.getString("unit", "KG")!!))
        private set
    var theme by mutableStateOf(ThemeMode.valueOf(prefs.getString("theme", "AUTO")!!))
        private set
    var current by mutableStateOf(prefs.getLong("last", -1))
        private set

    init { reload() }

    private fun reload() {
        exercises.clear(); sets.clear()
        db.rawQuery("SELECT id, name, custom FROM exercise", null).use { c ->
            while (c.moveToNext()) exercises += Exercise(c.getLong(0), c.getString(1), c.getInt(2) == 1)
        }
        db.rawQuery("SELECT id, exercise_id, weight_kg, reps, ts FROM lift_set ORDER BY ts", null).use { c ->
            while (c.moveToNext()) sets += LiftSet(c.getLong(0), c.getLong(1), c.getDouble(2), c.getInt(3), c.getLong(4))
        }
        if (exercises.none { it.id == current }) current = exercises.firstOrNull()?.id ?: -1
    }

    fun exercise(id: Long) = exercises.firstOrNull { it.id == id }
    fun setsOf(id: Long) = sets.filter { it.exerciseId == id }

    fun select(id: Long) { current = id; prefs.edit().putLong("last", id).apply() }
    fun changeUnit(u: WeightUnit) { unit = u; prefs.edit().putString("unit", u.name).apply() }
    fun changeTheme(t: ThemeMode) { theme = t; prefs.edit().putString("theme", t.name).apply() }

    fun log(exerciseId: Long, weightKg: Double, reps: Int): LiftSet {
        val now = System.currentTimeMillis()
        val id = db.insertOrThrow("lift_set", null, ContentValues().apply {
            put("exercise_id", exerciseId); put("weight_kg", weightKg); put("reps", reps); put("ts", now)
        })
        return LiftSet(id, exerciseId, weightKg, reps, now).also { sets += it }
    }

    fun delete(set: LiftSet) {
        db.delete("lift_set", "id = ?", arrayOf(set.id.toString()))
        sets.removeAll { it.id == set.id }
    }

    /** Puts a deleted set back with its original id and time (undo). */
    fun restore(set: LiftSet) {
        db.insertOrThrow("lift_set", null, ContentValues().apply {
            put("id", set.id); put("exercise_id", set.exerciseId); put("weight_kg", set.weightKg); put("reps", set.reps); put("ts", set.time)
        })
        val i = sets.indexOfFirst { it.time > set.time }.let { if (it < 0) sets.size else it }
        sets.add(i, set)
    }

    /** Returns the existing exercise if the name already exists (case-insensitive). */
    fun addExercise(rawName: String): Exercise {
        val name = rawName.trim().take(60)
        exercises.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        val id = db.insertOrThrow("exercise", null, ContentValues().apply { put("name", name); put("custom", 1) })
        return Exercise(id, name, true).also { exercises += it }
    }

    fun deleteExercise(id: Long) {
        db.delete("exercise", "id = ?", arrayOf(id.toString()))   // sets go with it (ON DELETE CASCADE)
        exercises.removeAll { it.id == id }
        sets.removeAll { it.exerciseId == id }
        if (current == id) select(exercises.firstOrNull()?.id ?: -1)
    }

    fun wipe() {
        db.transaction { delete("lift_set", null, null); delete("exercise", null, null) }
        db.transaction { DEFAULT_EXERCISES.forEach { insert("exercise", null, ContentValues().apply { put("name", it) }) } }
        reload()
        select(exercises.first().id)
    }

    // ---------- backup: same JSON format as the web version (v1) ----------

    fun exportJson(): String {
        val o = JSONObject()
        o.put("v", 1); o.put("unit", unit.label)
        o.put("exercises", JSONArray().apply {
            exercises.forEach { put(JSONObject().put("id", it.id.toString()).put("name", it.name).put("custom", it.custom)) }
        })
        o.put("sets", JSONArray().apply {
            sets.forEach { put(JSONObject().put("id", it.id.toString()).put("e", it.exerciseId.toString()).put("w", it.weightKg).put("r", it.reps).put("t", it.time)) }
        })
        return o.toString(1)
    }

    fun exportCsv(): String = buildString {
        append("date,exercise,weight_${unit.label},reps,e1rm\n")
        val names = exercises.associate { it.id to it.name }
        for (s in sets) {
            val name = (names[s.exerciseId] ?: "?").replace("\"", "\"\"")
            append("${Instant.ofEpochMilli(s.time)},\"$name\",${fmt(s.weightKg, unit)},${s.reps},${fmt(e1rm(s.weightKg, s.reps), unit)}\n")
        }
    }

    /** Replaces all data with a v1 backup. Returns the number of imported sets; throws on a bad file. */
    fun importJson(text: String): Int {
        val o = JSONObject(text)
        require(o.optInt("v") == 1) { "not a v1 backup" }
        val exs = o.getJSONArray("exercises"); val ss = o.getJSONArray("sets")
        var count = 0
        db.transaction {
            delete("lift_set", null, null); delete("exercise", null, null)
            val idMap = HashMap<String, Long>()
            for (i in 0 until exs.length()) {
                val e = exs.getJSONObject(i)
                val name = e.getString("name").trim().take(60)
                if (name.isEmpty()) continue
                val id = insertWithOnConflict("exercise", null, ContentValues().apply {
                    put("name", name); put("custom", if (e.optBoolean("custom")) 1 else 0)
                }, SQLiteDatabase.CONFLICT_IGNORE)
                idMap[e.getString("id")] = if (id > 0) id
                    else rawQuery("SELECT id FROM exercise WHERE name = ?", arrayOf(name)).use { it.moveToFirst(); it.getLong(0) }
            }
            for (i in 0 until ss.length()) {
                val s = ss.getJSONObject(i)
                val ex = idMap[s.optString("e")] ?: continue
                val w = s.optDouble("w", Double.NaN); val r = s.optInt("r", 0); val t = s.optLong("t", 0)
                if (w.isNaN() || w < 0 || r < 1 || t <= 0) continue
                insert("lift_set", null, ContentValues().apply { put("exercise_id", ex); put("weight_kg", w); put("reps", r); put("ts", t) })
                count++
            }
        }
        o.optString("unit").let { if (it == "lb") changeUnit(WeightUnit.LB) else if (it == "kg") changeUnit(WeightUnit.KG) }
        reload()
        return count
    }
}

private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
    beginTransaction()
    try { block(); setTransactionSuccessful() } finally { endTransaction() }
}

/** Weight in the display unit, max one decimal, no trailing ".0". */
fun fmt(kg: Double, unit: WeightUnit): String {
    val v = Math.round(kg / unit.kgPer * 10) / 10.0
    return if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
}
