package com.mashimurelay.app.nativecad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ProjectItem(
    val name: String,
    val file: File,
    val lastModified: Long,
    val elementCount: Int,
    val isExample: Boolean = false
)

object WorkspaceManager {

    var currentAssignmentKit: AssignmentKit? = null

    fun getWorkspaceDir(context: Context): File {
        val dir = File(context.filesDir, "workspace")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun initDefaultProjects(context: Context) {
        val dir = getWorkspaceDir(context)

        // Hapus proyek kelistrikan hazard dari workspace, sisakan rangkaian demo
        dir.listFiles()?.forEach { file ->
            if (file.name.contains("hazard", ignoreCase = true) || file.name.contains("sign", ignoreCase = true)) {
                try { file.delete() } catch (_: Exception) {}
            }
        }

        val demoFile = File(dir, "Rangkaian Sederhana Demo.smrl")
        var needRefresh = !demoFile.exists()
        if (demoFile.exists()) {
            val content = demoFile.readText()
            if (!content.contains("\"position\"") || content.length < 5000) {
                needRefresh = true
            }
        }
        if (needRefresh) {
            try {
                val assetStream = context.assets.open("assets/examples/rangkaian_sederhana_demo.smrl")
                val text = assetStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                demoFile.writeText(text)
            } catch (_: Exception) {
                val dummyElements = createSampleCircuit()
                val dummyConnectors = createSampleConnectors()
                demoFile.writeText(serializeCircuit(dummyElements, dummyConnectors))
            }
        }
    }

    fun listProjects(context: Context): List<ProjectItem> {
        val dir = getWorkspaceDir(context)
        dir.listFiles()?.forEach { file ->
            if (file.name.contains("hazard", ignoreCase = true) || file.name.contains("sign", ignoreCase = true)) {
                try { file.delete() } catch (_: Exception) {}
            }
        }
        val files = dir.listFiles { f -> f.extension.equals("smrl", ignoreCase = true) } ?: return emptyList()
        return files.map { file ->
            val name = file.nameWithoutExtension
            val count = parseElementCount(file)
            ProjectItem(name, file, file.lastModified(), count, isExample = false)
        }.sortedByDescending { it.lastModified }
    }

    fun listExamples(): List<ProjectItem> {
        return listOf(
            ProjectItem("Rangkaian Sederhana Demo", File("rangkaian_sederhana_demo.smrl"), System.currentTimeMillis(), 12, isExample = true)
        )
    }

    fun saveProject(context: Context, projectName: String, elements: List<NativeElement>, connectors: List<NativeConnector>): Boolean {
        return try {
            val dir = getWorkspaceDir(context)
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "$projectName.smrl")
            val json = serializeCircuit(elements, connectors)
            file.writeText(json)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadProject(file: File): Pair<List<NativeElement>, List<NativeConnector>>? {
        return try {
            val json = file.readText()
            deserializeCircuit(json)
        } catch (_: Exception) {
            null
        }
    }

    fun renameProject(context: Context, oldName: String, newName: String): Boolean {
        val dir = getWorkspaceDir(context)
        val src = File(dir, "$oldName.smrl")
        val dst = File(dir, "$newName.smrl")
        return if (src.exists() && !dst.exists()) src.renameTo(dst) else false
    }

    fun duplicateProject(context: Context, name: String): String? {
        val dir = getWorkspaceDir(context)
        val src = File(dir, "$name.smrl")
        if (!src.exists()) return null
        var copyName = "$name (Copy)"
        var counter = 2
        var dst = File(dir, "$copyName.smrl")
        while (dst.exists()) {
            copyName = "$name (Copy $counter)"
            dst = File(dir, "$copyName.smrl")
            counter++
        }
        src.copyTo(dst, overwrite = true)
        return copyName
    }

    fun deleteProject(context: Context, name: String): Boolean {
        val dir = getWorkspaceDir(context)
        val file = File(dir, "$name.smrl")
        return file.delete()
    }

    fun serializeCircuit(elements: List<NativeElement>, connectors: List<NativeConnector>): String {
        val root = JSONObject()
        val createdIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        val header = JSONObject().apply {
            put("generator", "Mashimurelay")
            put("3.0.0.30", "1.0")
            put("created_at", createdIso)
            put("version", "5.0.5")
            put("timestamp", System.currentTimeMillis())
        }
        root.put("header", header)

        val usedIds = mutableSetOf<Int>()
        val dataArr = JSONArray()
        for (el in elements) {
            usedIds.add(el.id)
            val colorType = when (el.lampColor.lowercase()) {
                "red" -> 0
                "green" -> 1
                "yellow" -> 2
                "blue" -> 3
                "white" -> 4
                else -> 2
            }
            val obj = JSONObject().apply {
                put("type", "ElectricalElement")
                put("id", el.id)
                put("svgData", el.svgData)
                put("tag", el.tag.ifEmpty { JSONObject.NULL })
                put("comment", el.comment.ifEmpty { JSONObject.NULL })
                put("text", if (el.svgData == "text") el.comment else JSONObject.NULL)
                put("value", JSONObject.NULL)
                put("tagVisibility", true)
                put("margins", JSONObject.NULL)
                put("idNode1", JSONObject.NULL)
                put("idNode2", JSONObject.NULL)
                put("idElementNode1", JSONObject.NULL)
                put("idElementNode2", JSONObject.NULL)
                put("x", el.x.toDouble())
                put("y", el.y.toDouble())
                val posObj = JSONObject().apply {
                    put("x", el.x.toDouble())
                    put("y", el.y.toDouble())
                }
                put("position", posObj)
                put("rotation", el.canonicalRotation)
                put("frameIndex", el.frameIndex)
                put("presetTime", el.presetTime.toDouble())
                put("valueTimeOn", el.presetTime.toDouble())
                put("presetTimeOff", el.presetTimeOff.toDouble())
                put("valueTimeOff", el.presetTimeOff.toDouble())
                put("lampColor", el.lampColor)
                put("colorType", colorType)
                put("fontSize", el.fontSize.toDouble())
                put("indexFontSize", if (el.svgData == "text") el.fontSize.toInt() else 5)
                put("textColor", el.textColor)
                put("locked", el.locked)
                put("teacherLocked", el.teacherLocked)
                val tagsArr = JSONArray()
                el.terminalTags.forEach { tagsArr.put(it) }
                put("terminalTags", tagsArr)
            }
            dataArr.put(obj)
        }

        val usedConnIds = mutableSetOf<Int>()
        for (c in connectors) {
            var safeConnId = c.id
            if (safeConnId <= 0 || !usedConnIds.add(safeConnId) || !usedIds.add(safeConnId)) {
                var candidate = (usedIds.maxOrNull() ?: 0) + 1
                while (!usedIds.add(candidate)) candidate++
                usedConnIds.add(candidate)
                safeConnId = candidate
            }
            val obj = JSONObject().apply {
                put("type", "Connector")
                put("svgData", "text")
                put("id", safeConnId)
                put("position", JSONObject.NULL)
                put("rotation", JSONObject.NULL)
                put("text", JSONObject.NULL)
                put("tag", JSONObject.NULL)
                put("comment", JSONObject.NULL)
                put("value", JSONObject.NULL)
                put("tagVisibility", true)
                put("elem1Id", c.elem1Id)
                put("node1Idx", c.node1Idx)
                put("elem2Id", c.elem2Id)
                put("node2Idx", c.node2Idx)
                put("idElementNode1", c.elem1Id)
                put("idNode1", c.node1Idx)
                put("idElementNode2", c.elem2Id)
                put("idNode2", c.node2Idx)
                put("valueTimeOn", 0.0)
                put("valueTimeOff", 0.0)
                put("colorType", 0)
                put("indexFontSize", 5)
                put("terminalTags", JSONArray())
                put("frameIndex", 0)
                put("routeOrientation", c.routeOrientation.name)
                val marginsObj = JSONObject().apply {
                    put("start", c.mStart.toDouble())
                    put("center", c.mCenter.toDouble())
                    put("end", c.mEnd.toDouble())
                }
                put("margins", marginsObj)
                if (c.mCenter != 0f) {
                    put("customMidPos", c.mCenter.toDouble())
                }
            }
            dataArr.put(obj)
        }
        root.put("data", dataArr)

        val kit = currentAssignmentKit
        if (kit != null && kit.enabled) {
            val kitObj = JSONObject().apply {
                put("enabled", true)
                put("name", kit.name)
                val kitArr = JSONArray()
                kit.allowedComponents.forEach { kitArr.put(it) }
                put("allowedComponents", kitArr)
            }
            root.put("assignmentKit", kitObj)
        }

        return root.toString(2)
    }

    fun deserializeCircuit(jsonStr: String): Pair<List<NativeElement>, List<NativeConnector>> {
        val elements = mutableListOf<NativeElement>()
        val connectors = mutableListOf<NativeConnector>()
        val root = JSONObject(jsonStr)

        val kitObj = root.optJSONObject("assignmentKit")
        currentAssignmentKit = if (kitObj != null && kitObj.optBoolean("enabled", false)) {
            val name = kitObj.optString("name", "Kit Praktikum Guru")
            val arr = kitObj.optJSONArray("allowedComponents")
            val list = mutableListOf<String>()
            if (arr != null) {
                for (k in 0 until arr.length()) list.add(arr.getString(k))
            }
            AssignmentKit(enabled = true, name = name, allowedComponents = list)
        } else {
            null
        }

        val dataArr = root.optJSONArray("data") ?: return Pair(elements, connectors)
        val usedConnIds = mutableSetOf<Int>()

        for (i in 0 until dataArr.length()) {
            val obj = dataArr.getJSONObject(i)
            val type = obj.optString("type")
            val isConn = type == "Connector" || type == "wire" ||
                (!obj.isNull("elem1Id") && obj.optInt("elem1Id", 0) > 0) ||
                (!obj.isNull("idElementNode1") && obj.optInt("idElementNode1", 0) > 0)
            val isElem = !isConn && (type == "ElectricalElement" || (obj.has("svgData") && !obj.isNull("svgData")))
            if (isElem) {
                val id = obj.optInt("id", i + 1)
                val rawSvg = obj.optString("svgData", "coil")
                val svgData = FORWARD_LEGACY_MAP[rawSvg.lowercase()] ?: rawSvg
                val rawTag = if (obj.isNull("tag")) "" else obj.optString("tag", "")
                val tag = if (rawTag.equals("null", ignoreCase = true)) "" else rawTag

                val rawComment = if (obj.isNull("comment")) "" else obj.optString("comment", "")
                var comment = if (rawComment.equals("null", ignoreCase = true)) "" else rawComment

                val rawText = if (obj.isNull("text")) "" else obj.optString("text", "")
                if (comment.isEmpty() && rawText.isNotEmpty() && !rawText.equals("null", ignoreCase = true)) {
                    comment = rawText
                }

                val posObj = obj.optJSONObject("position")
                val x = if (posObj != null && posObj.has("x")) {
                    posObj.optDouble("x", 0.0).toFloat()
                } else {
                    obj.optDouble("x", 0.0).toFloat()
                }
                val y = if (posObj != null && posObj.has("y")) {
                    posObj.optDouble("y", 0.0).toFloat()
                } else {
                    obj.optDouble("y", 0.0).toFloat()
                }

                val rawRot = obj.optInt("rotation", 0)
                val rotation = if (kotlin.math.abs(rawRot) >= 4 && rawRot % 90 == 0) ((rawRot / 90) % 4 + 4) % 4 else ((rawRot % 4) + 4) % 4
                val frameIndex = obj.optInt("frameIndex", 0)
                val presetTime = when {
                    obj.has("valueTimeOn") -> {
                        val raw = obj.opt("valueTimeOn")
                        when (raw) {
                            is Number -> raw.toFloat()
                            is String -> raw.trim().replace(',', '.').toFloatOrNull() ?: 2.0f
                            else -> 2.0f
                        }
                    }
                    obj.has("presetTime") -> obj.optDouble("presetTime", 2.0).toFloat()
                    else -> 2.0f
                }
                val presetTimeOff = when {
                    obj.has("valueTimeOff") -> {
                        val raw = obj.opt("valueTimeOff")
                        when (raw) {
                            is Number -> raw.toFloat()
                            is String -> raw.trim().replace(',', '.').toFloatOrNull() ?: 0.0f
                            else -> 0.0f
                        }
                    }
                    obj.has("presetTimeOff") -> obj.optDouble("presetTimeOff", 0.0).toFloat()
                    else -> 0.0f
                }
                val lampColor = obj.optString("lampColor", "yellow")
                val fontSize = obj.optDouble("fontSize", 24.0).toFloat()
                val textColor = if (obj.has("textColor")) obj.optString("textColor", "#93c5fd") else if (obj.has("color")) obj.optString("color", "#93c5fd") else "#93c5fd"
                val locked = obj.optBoolean("locked", false)
                val teacherLocked = obj.optBoolean("teacherLocked", false)
                val terminalTags = mutableListOf<String>()
                val tagsArr = obj.optJSONArray("terminalTags")
                if (tagsArr != null) {
                    for (t in 0 until tagsArr.length()) {
                        terminalTags.add(tagsArr.getString(t))
                    }
                }
                elements.add(
                    NativeElement(
                        id = id,
                        svgData = svgData,
                        tag = tag,
                        comment = comment,
                        x = x,
                        y = y,
                        rotation = rotation,
                        frameIndex = frameIndex,
                        terminalTags = terminalTags,
                        presetTime = presetTime,
                        presetTimeOff = presetTimeOff,
                        lampColor = lampColor,
                        fontSize = fontSize,
                        textColor = textColor,
                        locked = (locked || teacherLocked),
                        teacherLocked = teacherLocked
                    )
                )
            } else if (isConn) {
                var id = obj.optInt("id", i + 1)
                if (id <= 0 || !usedConnIds.add(id)) {
                    var candidate = (usedConnIds.maxOrNull() ?: 0) + 1
                    while (!usedConnIds.add(candidate)) candidate++
                    id = candidate
                }
                val elem1Id = obj.optInt("elem1Id", obj.optInt("idElementNode1", 0))
                val node1Idx = obj.optInt("node1Idx", obj.optInt("idNode1", 0))
                val elem2Id = obj.optInt("elem2Id", obj.optInt("idElementNode2", 0))
                val node2Idx = obj.optInt("node2Idx", obj.optInt("idNode2", 0))
                val routeName = obj.optString("routeOrientation", "AUTO")
                val route = try { RouteOrientation.valueOf(routeName) } catch (_: Exception) { RouteOrientation.AUTO }
                val marginsObj = obj.optJSONObject("margins")
                val mStart = marginsObj?.optDouble("start", 0.0)?.toFloat() ?: 0f
                val mCenter = marginsObj?.optDouble("center", 0.0)?.toFloat()
                    ?: obj.optDouble("customMidPos", 0.0).toFloat()
                val mEnd = marginsObj?.optDouble("end", 0.0)?.toFloat() ?: 0f

                if (elem1Id > 0 && elem2Id > 0) {
                    connectors.add(
                        NativeConnector(
                            id = id,
                            elem1Id = elem1Id,
                            node1Idx = node1Idx,
                            elem2Id = elem2Id,
                            node2Idx = node2Idx,
                            routeOrientation = route,
                            mStart = mStart,
                            mCenter = mCenter,
                            mEnd = mEnd,
                            customMidPos = if (mCenter != 0f) mCenter else null
                        )
                    )
                }
            }
        }
        return Pair(elements, connectors)
    }

    private fun parseElementCount(file: File): Int {
        return try {
            val text = file.readText()
            val root = JSONObject(text)
            val arr = root.optJSONArray("data") ?: return 0
            var count = 0
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val type = obj.optString("type")
                val isConn = type == "Connector" || type == "wire" ||
                    (!obj.isNull("elem1Id") && obj.optInt("elem1Id", 0) > 0) ||
                    (!obj.isNull("idElementNode1") && obj.optInt("idElementNode1", 0) > 0)
                val isElem = !isConn && (type == "ElectricalElement" || (obj.has("svgData") && !obj.isNull("svgData")))
                if (isElem) count++
            }
            count
        } catch (_: Exception) { 0 }
    }

    private fun createSampleCircuit(): List<NativeElement> {
        val pwr = NativeElement(1, "sourceL1", "-L1", "Fasa L1", 180f, 60f)
        val n = NativeElement(2, "sourceN", "-N", "Netral", 360f, 60f)
        val s1 = NativeElement(3, "buttonNO", "-S1", "Tombol Start", 180f, 180f)
        val k1 = NativeElement(4, "coil", "-KM1", "Koil Utama", 180f, 330f)
        val hl1 = NativeElement(5, "lamp", "-HL1", "Indikator ON", 360f, 330f)
        return listOf(pwr, n, s1, k1, hl1)
    }

    private fun createSampleConnectors(): List<NativeConnector> {
        return listOf(
            NativeConnector(1, 1, 0, 3, 0),
            NativeConnector(2, 3, 1, 4, 0),
            NativeConnector(3, 4, 1, 2, 0),
            NativeConnector(4, 3, 1, 5, 0),
            NativeConnector(5, 5, 1, 2, 0)
        )
    }

    val REVERSE_LEGACY_MAP = mapOf(
        "lamp" to "Lamp", "lampBlink" to "LampBlink", "switch3p0" to "SwitchC_3P_O", "switch3p" to "Switch3p",
        "contactNO" to "ContactNO", "contactNC" to "ContactNC", "contactCON" to "ContactCON", "contactNONC" to "ContactNONC",
        "contactNODelayOn" to "ContactNODelayOn", "contactNCDelayOn" to "ContactNCDelayOn",
        "contactNODelayOff" to "ContactNODelayOff", "contactNCDelayOff" to "ContactNCDelayOff",
        "coilFlasher" to "CoilFlasher", "flasher" to "Flasher", "coil" to "CoilKM", "coilDelayOn" to "CoilDelayOn",
        "coilDelayOff" to "CoilDelayOff", "coilDelayOnOff" to "CoilDelayOnOff", "coilLatch" to "CoilLatch",
        "coilSolenoid" to "CoilSolenoid", "contactor1p" to "Contactor1P", "contactor2p" to "Contactor2P",
        "contactor3p" to "Contactor3P", "contactor4p" to "Contactor4P", "overload1p" to "OverloadRelay1",
        "overload2p" to "OverloadRelay2", "overload3p" to "OverloadRelay3", "isolator1p" to "Isolator1",
        "isolator2p" to "Isolator2", "isolator3p" to "Isolator3", "isolator4p" to "Isolator4",
        "fuse1p" to "Fuse1", "fuse2p" to "Fuse2", "fuse3p" to "Fuse3", "fuse4p" to "Fuse4",
        "buttonNO" to "ButtonNO", "buttonNC" to "ButtonNC", "buttonNONC" to "ButtonNONC",
        "emergencyNO" to "EmergencyNO", "emergencyNC" to "EmergencyNC", "emergencyCON" to "EmergencyCON",
        "motor3ph" to "Motor3Ph", "motor1ph" to "Motor1Ph", "motorDC" to "MotorDC",
        "motorStarDelta" to "MotorStarDelta", "motorDhalander" to "MotorDahlander", "motorTwoSpeeds" to "MotorTwoSpeeds",
        "ring" to "Ring", "buzzer" to "Buzzer", "siren" to "Siren",
        "sourceN" to "SourceN", "sourceL1" to "SourceL1", "sourceL2" to "SourceL2", "sourceL3" to "SourceL3",
        "sourcePE" to "SourcePE", "sourceGnd" to "SourceGnd", "sourcePlus" to "SourcePlus", "sourceNegative" to "SourceNegative",
        "sourceL1L2L3" to "SourceL1L2L3", "sourceL1L2L3N" to "SourceL1L2L3N",
        "junction2p90" to "Junction90", "junction2p" to "Junction2P", "junction3p" to "Junction3P",
        "junction4p" to "Junction4P", "junctionPE" to "JunctionPE", "text" to "BaseText",
        "breaker" to "MotorBreaker3P", "motorBreaker3p" to "MotorBreaker3P", "breaker3p" to "CircuitBreaker3"
    )

    val FORWARD_LEGACY_MAP = REVERSE_LEGACY_MAP.entries.associate { (k, v) -> v.lowercase() to k }.toMutableMap().apply {
        put("motorbreaker3p", "motorBreaker3p")
        put("circuitbreaker3", "breaker3p")
        put("coilkm", "coil")
    }

    fun exportToLegacyTxt(elements: List<NativeElement>, connectors: List<NativeConnector>, asBase64: Boolean = true): String {
        val totalItems = elements.size + connectors.size
        val maxId = (elements.map { it.id } + connectors.map { it.id }).maxOrNull() ?: 0

        val lines = mutableListOf<String>()
        lines.add("-1@$totalItems@${maxId + 1}@0.5@0.0@200.0@0.0@0.5@200.0@0.0@0.0@1.0")

        fun fmtTime(v: Float): String {
            return if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
        }

        for (el in elements) {
            val legacyType = REVERSE_LEGACY_MAP[el.svgData] ?: el.svgData.replaceFirstChar { it.uppercase() }
            val tagPrefix = el.tag.filter { it.isLetter() || it == '-' || it == '_' }
            val tagNum = el.tag.filter { it.isDigit() }
            val x = Math.round(el.x)
            val y = Math.round(el.y)
            val comment = el.comment
            val rot = el.canonicalRotation
            val vTimeOn = fmtTime(el.presetTime)
            val vTimeOff = fmtTime(el.presetTimeOff)
            val fontIdx = if (el.svgData == "text") 24 else 0

            lines.add("$legacyType@${el.id}@$tagPrefix@$tagNum@$x@$y@$comment@0@2@$vTimeOn@$vTimeOff@$rot@$fontIdx@1@0")
        }

        for (c in connectors) {
            val n1 = c.node1Idx + 1
            val n2 = c.node2Idx + 1
            val mStart = c.mStart.toInt()
            val mCenter = c.mCenter.toInt()
            val mEnd = c.mEnd.toInt()
            lines.add("Connector@$n1@$n2@${c.elem1Id}@${c.elem2Id}@$mStart@$mCenter@$mEnd@$mCenter@0@0@0@0@0@0@0")
        }

        val plainText = lines.joinToString("\n")
        return if (asBase64) {
            android.util.Base64.encodeToString(plainText.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
        } else {
            plainText
        }
    }

    fun parseLegacyTxt(rawText: String): Pair<List<NativeElement>, List<NativeConnector>>? {
        val text = try {
            val trimmed = rawText.trim()
            if (!trimmed.contains("@") && trimmed.length > 20) {
                val decoded = android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                String(decoded, Charsets.UTF_8)
            } else {
                trimmed
            }
        } catch (_: Exception) {
            rawText.trim()
        }

        if (!text.contains("@")) return null

        val elements = mutableListOf<NativeElement>()
        val connectors = mutableListOf<NativeConnector>()

        val lines = text.lines()
        for (line in lines) {
            val parts = line.split("@")
            if (parts.size < 5) continue

            val typeStr = parts[0].trim()
            if (typeStr == "-1") continue

            if (typeStr.equals("Connector", ignoreCase = true)) {
                val n1 = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                val n2 = (parts.getOrNull(2)?.toIntOrNull() ?: 1) - 1
                val e1 = parts.getOrNull(3)?.toIntOrNull() ?: 0
                val e2 = parts.getOrNull(4)?.toIntOrNull() ?: 0
                val mStart = parts.getOrNull(5)?.toFloatOrNull() ?: 0f
                val mCenter = parts.getOrNull(6)?.toFloatOrNull() ?: 0f
                val mEnd = parts.getOrNull(7)?.toFloatOrNull() ?: 0f
                val id = connectors.size + 1
                connectors.add(
                    NativeConnector(
                        id = id,
                        elem1Id = e1,
                        node1Idx = n1.coerceAtLeast(0),
                        elem2Id = e2,
                        node2Idx = n2.coerceAtLeast(0),
                        mStart = mStart,
                        mCenter = mCenter,
                        mEnd = mEnd,
                        customMidPos = if (mCenter != 0f) mCenter else null
                    )
                )
            } else {
                val svgData = FORWARD_LEGACY_MAP[typeStr.lowercase()] ?: typeStr.replaceFirstChar { it.lowercase() }
                val id = parts.getOrNull(1)?.toIntOrNull() ?: (elements.size + 1)
                val prefix = parts.getOrNull(2) ?: ""
                val num = parts.getOrNull(3) ?: ""
                val tag = if (prefix.isNotEmpty() || num.isNotEmpty()) "$prefix$num" else ""
                val x = parts.getOrNull(4)?.toFloatOrNull() ?: 100f
                val y = parts.getOrNull(5)?.toFloatOrNull() ?: 100f
                val comment = parts.getOrNull(6) ?: ""
                val timeOn = parts.getOrNull(9)?.toFloatOrNull() ?: 2.0f
                val rawRot = parts.getOrNull(11)?.toIntOrNull() ?: 0
                val rot = if (kotlin.math.abs(rawRot) >= 4 && rawRot % 90 == 0) ((rawRot / 90) % 4 + 4) % 4 else ((rawRot % 4) + 4) % 4

                elements.add(
                    NativeElement(
                        id = id,
                        svgData = svgData,
                        tag = tag,
                        comment = comment,
                        x = x,
                        y = y,
                        rotation = rot,
                        presetTime = timeOn
                    )
                )
            }
        }
        return if (elements.isNotEmpty()) Pair(elements, connectors) else null
    }

    fun importCircuitContent(context: Context, originalFileName: String, rawText: String): String? {
        val trimmed = rawText.trim()
        val circuit = if (trimmed.startsWith("{")) {
            deserializeCircuit(trimmed)
        } else {
            parseLegacyTxt(trimmed) ?: deserializeCircuit(trimmed)
        }

        // Only reject if completely empty without kit and not valid JSON
        if (circuit.first.isEmpty() && circuit.second.isEmpty() && currentAssignmentKit == null && !trimmed.startsWith("{")) {
            return null
        }

        val dir = getWorkspaceDir(context)
        var baseName = originalFileName.replace(Regex("""\.[^/.]+$"""), "").trim().ifEmpty { "Sirkuit Impor" }
        var targetFile = File(dir, "$baseName.smrl")
        var counter = 2
        while (targetFile.exists()) {
            baseName = "${originalFileName.replace(Regex("""\.[^/.]+$"""), "").trim()} ($counter)"
            targetFile = File(dir, "$baseName.smrl")
            counter++
        }

        targetFile.writeText(serializeCircuit(circuit.first, circuit.second))
        return baseName
    }

    fun exportProjectFile(context: Context, projectName: String, asLegacyTxt: Boolean): File? {
        val dir = getWorkspaceDir(context)
        val srcFile = File(dir, "$projectName.smrl")
        if (!srcFile.exists()) return null

        val circuit = loadProject(srcFile) ?: return null

        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val targetDir = if (downloadsDir != null && downloadsDir.exists() && downloadsDir.canWrite()) {
            downloadsDir
        } else {
            context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir
        }

        val ext = if (asLegacyTxt) "txt" else "smrl"
        val cleanName = projectName.replace(Regex("""[^\w\-\s]"""), "_")
        val outFile = File(targetDir, "$cleanName.$ext")

        if (asLegacyTxt) {
            val txtContent = exportToLegacyTxt(circuit.first, circuit.second, asBase64 = true)
            outFile.writeText(txtContent)
        } else {
            val jsonContent = serializeCircuit(circuit.first, circuit.second)
            outFile.writeText(jsonContent)
        }
        return outFile
    }
}
