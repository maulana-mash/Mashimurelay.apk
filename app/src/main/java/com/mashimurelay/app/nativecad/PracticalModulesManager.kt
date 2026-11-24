package com.mashimurelay.app.nativecad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class PracticalModulePin(
    val code: String,
    val desc: String
)

data class PracticalModule(
    val id: String,
    val code: String,
    val title: String,
    val track: String, // "industri" | "otomotif" | "umum"
    val subject: String,
    val level: String,
    val duration: String,
    val author: String,
    val isBuiltin: Boolean,
    val videoId: String? = null,
    val secondaryVideoId: String? = null,
    val summary: String = "",
    val objectives: List<String> = emptyList(),
    val safetyK3: List<String> = emptyList(),
    val theory: String = "",
    val pinTable: List<PracticalModulePin> = emptyList(),
    val steps: List<String> = emptyList(),
    val checks: List<String> = emptyList(),
    val allowedComponents: List<String> = emptyList(),
    val exampleCircuitJson: String? = null,
    val updatedAt: Long = 0L
)

data class ModuleCompletionInfo(
    val done: Int,
    val total: Int,
    val percent: Int,
    val completed: Boolean
)

object PracticalModulesManager {

    private const val CUSTOM_MODULES_FILE = "teacher_modules.json"
    private const val PROGRESS_PREFS = "mashimurelay_module_progress"

    @Volatile
    private var cachedBuiltin: List<PracticalModule>? = null

    fun getBuiltinModules(context: Context): List<PracticalModule> {
        cachedBuiltin?.let { return it }
        val loaded = try {
            val raw = context.assets.open("practical_modules.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parseModuleArray(JSONArray(raw), isBuiltinDefault = true)
        } catch (_: Exception) {
            emptyList()
        }
        cachedBuiltin = loaded
        return loaded
    }

    fun getCustomModules(context: Context): List<PracticalModule> {
        val file = File(context.filesDir, CUSTOM_MODULES_FILE)
        if (!file.exists()) return emptyList()
        return try {
            val raw = file.readText(Charsets.UTF_8)
            if (raw.isBlank()) emptyList()
            else parseModuleArray(JSONArray(raw), isBuiltinDefault = false)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getAllModules(context: Context): List<PracticalModule> {
        return getBuiltinModules(context) + getCustomModules(context)
    }

    fun getModuleById(context: Context, id: String): PracticalModule? {
        return getAllModules(context).firstOrNull { it.id == id }
    }

    fun upsertCustomModulesFromJsonArray(context: Context, jsonArrayString: String): Int {
        val trimmed = jsonArrayString.trim()
        if (trimmed.isEmpty()) return 0
        val incoming = try {
            if (trimmed.startsWith("[")) {
                parseModuleArray(JSONArray(trimmed), isBuiltinDefault = false)
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val arr = obj.optJSONArray("customModules") ?: obj.optJSONArray("modules")
                if (arr != null) parseModuleArray(arr, isBuiltinDefault = false)
                else listOfNotNull(parseSingleModule(obj, isBuiltinDefault = false))
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (incoming.isEmpty()) return 0
        return upsertCustomModules(context, incoming)
    }

    fun extractAndSaveFromCircuitJson(context: Context, circuitJsonString: String): Int {
        val trimmed = circuitJsonString.trim()
        if (!trimmed.startsWith("{")) return 0
        return try {
            val root = JSONObject(trimmed)
            val incoming = mutableListOf<PracticalModule>()
            val arr = root.optJSONArray("customModules")
            if (arr != null && arr.length() > 0) {
                incoming.addAll(parseModuleArray(arr, isBuiltinDefault = false))
            }
            val single = root.optJSONObject("customModule")
            if (single != null) {
                parseSingleModule(single, isBuiltinDefault = false)?.let { incoming.add(it) }
            }
            if (incoming.isEmpty()) 0 else upsertCustomModules(context, incoming)
        } catch (_: Exception) {
            0
        }
    }

    @Synchronized
    fun upsertCustomModules(context: Context, incoming: List<PracticalModule>): Int {
        if (incoming.isEmpty()) return 0
        val current = getCustomModules(context).toMutableList()
        var count = 0
        for (rawMod in incoming) {
            val mod = rawMod.copy(isBuiltin = false, updatedAt = System.currentTimeMillis())
            val idx = current.indexOfFirst { it.id == mod.id }
            if (idx >= 0) {
                current[idx] = mod
            } else {
                current.add(0, mod)
            }
            count++
        }
        writeCustomModules(context, current)
        return count
    }

    @Synchronized
    fun deleteCustomModule(context: Context, moduleId: String): Boolean {
        val current = getCustomModules(context).toMutableList()
        val removed = current.removeAll { it.id == moduleId }
        if (!removed) return false
        writeCustomModules(context, current)
        val prefs = context.getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove("chk_$moduleId").apply()
        return true
    }

    fun getCheckedIndices(context: Context, moduleId: String): Set<Int> {
        val prefs = context.getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString("chk_$moduleId", "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    fun toggleCheck(context: Context, moduleId: String, index: Int): Set<Int> {
        val next = getCheckedIndices(context, moduleId).toMutableSet()
        if (next.contains(index)) next.remove(index) else next.add(index)
        val sorted = next.sorted()
        val prefs = context.getSharedPreferences(PROGRESS_PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString("chk_$moduleId", sorted.joinToString(",")).apply()
        return next
    }

    fun getCompletion(context: Context, mod: PracticalModule): ModuleCompletionInfo {
        val total = mod.checks.size
        if (total == 0) return ModuleCompletionInfo(0, 0, 0, false)
        val checked = getCheckedIndices(context, mod.id).count { it in 0 until total }
        val pct = (checked * 100) / total
        return ModuleCompletionInfo(checked, total, pct, checked >= total)
    }

    fun getBuiltinCompletedCount(context: Context): Pair<Int, Int> {
        val builtins = getBuiltinModules(context)
        val done = builtins.count { getCompletion(context, it).completed }
        return Pair(done, builtins.size)
    }

    private fun writeCustomModules(context: Context, list: List<PracticalModule>) {
        val arr = JSONArray()
        for (m in list) {
            arr.put(serializeModule(m))
        }
        val file = File(context.filesDir, CUSTOM_MODULES_FILE)
        file.writeText(arr.toString(), Charsets.UTF_8)
    }

    private fun serializeModule(m: PracticalModule): JSONObject {
        return JSONObject().apply {
            put("id", m.id)
            put("code", m.code)
            put("title", m.title)
            put("track", m.track)
            put("subject", m.subject)
            put("level", m.level)
            put("duration", m.duration)
            put("author", m.author)
            put("isBuiltin", false)
            if (!m.videoId.isNullOrBlank()) put("videoId", m.videoId)
            if (!m.secondaryVideoId.isNullOrBlank()) put("secondaryVideoId", m.secondaryVideoId)
            put("summary", m.summary)
            put("objectives", JSONArray(m.objectives))
            put("safetyK3", JSONArray(m.safetyK3))
            put("theory", m.theory)
            val pinArr = JSONArray()
            m.pinTable.forEach { p ->
                pinArr.put(JSONObject().apply {
                    put("code", p.code)
                    put("desc", p.desc)
                })
            }
            put("pinTable", pinArr)
            put("steps", JSONArray(m.steps))
            put("checks", JSONArray(m.checks))
            put("allowedComponents", JSONArray(m.allowedComponents))
            if (!m.exampleCircuitJson.isNullOrBlank()) {
                try {
                    put("exampleCircuit", JSONObject(m.exampleCircuitJson))
                } catch (_: Exception) {}
            }
            put("updatedAt", m.updatedAt)
        }
    }

    private fun parseModuleArray(arr: JSONArray, isBuiltinDefault: Boolean): List<PracticalModule> {
        val out = mutableListOf<PracticalModule>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseSingleModule(obj, isBuiltinDefault)?.let { out.add(it) }
        }
        return out
    }

    private fun parseSingleModule(obj: JSONObject, isBuiltinDefault: Boolean): PracticalModule? {
        val title = obj.optString("title", "").trim()
        if (title.isEmpty()) return null
        val id = obj.optString("id", "").trim().ifEmpty {
            "mod_guru_${System.currentTimeMillis()}_${title.hashCode()}"
        }
        val trackRaw = obj.optString("track", if (isBuiltinDefault) "industri" else "umum").lowercase()
        val track = when (trackRaw) {
            "industri", "otomotif", "umum" -> trackRaw
            else -> "umum"
        }

        fun readStringList(key: String): List<String> {
            val a = obj.optJSONArray(key) ?: return emptyList()
            val res = mutableListOf<String>()
            for (i in 0 until a.length()) {
                val s = a.optString(i, "").trim()
                if (s.isNotEmpty()) res.add(s)
            }
            return res
        }

        val pinTable = mutableListOf<PracticalModulePin>()
        val pinArr = obj.optJSONArray("pinTable")
        if (pinArr != null) {
            for (i in 0 until pinArr.length()) {
                val p = pinArr.optJSONObject(i) ?: continue
                val code = p.optString("code", "").trim()
                val desc = p.optString("desc", "").trim()
                if (code.isNotEmpty() || desc.isNotEmpty()) {
                    pinTable.add(PracticalModulePin(code, desc))
                }
            }
        }

        val exampleObj = obj.optJSONObject("exampleCircuit")
        val exampleJson = if (exampleObj != null && exampleObj.optJSONArray("data")?.length() ?: 0 > 0) {
            exampleObj.toString()
        } else {
            null
        }

        val vid = obj.optString("videoId", "").trim().takeIf { it.isNotEmpty() && it != "null" }
        val secVid = obj.optString("secondaryVideoId", "").trim().takeIf { it.isNotEmpty() && it != "null" }

        return PracticalModule(
            id = id,
            code = obj.optString("code", if (isBuiltinDefault) "MOD" else "GURU").trim().ifEmpty { "GURU" },
            title = title,
            track = track,
            subject = obj.optString("subject", if (isBuiltinDefault) "Praktikum SMK" else "Modul Mandiri Guru").trim(),
            level = obj.optString("level", if (isBuiltinDefault) "Dasar" else "Mandiri").trim(),
            duration = obj.optString("duration", "45 Menit").trim(),
            author = obj.optString("author", if (isBuiltinDefault) "Mashimurelay" else "Guru Pengampu").trim(),
            isBuiltin = isBuiltinDefault,
            videoId = vid,
            secondaryVideoId = secVid,
            summary = obj.optString("summary", "").trim(),
            objectives = readStringList("objectives"),
            safetyK3 = readStringList("safetyK3"),
            theory = obj.optString("theory", "").trim(),
            pinTable = pinTable,
            steps = readStringList("steps"),
            checks = readStringList("checks"),
            allowedComponents = readStringList("allowedComponents"),
            exampleCircuitJson = exampleJson,
            updatedAt = obj.optLong("updatedAt", 0L)
        )
    }
}
