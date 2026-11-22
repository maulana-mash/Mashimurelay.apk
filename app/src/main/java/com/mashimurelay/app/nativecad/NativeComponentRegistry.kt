package com.mashimurelay.app.nativecad

import android.content.Context
import org.json.JSONObject
import java.io.InputStreamReader

data class ContactDef(
    val id: String,
    val nodeId1: Int,
    val nodeId2: Int,
    val enableFrames: String = ""
) {
    fun isEnabledInFrame(frameIdx: Int, isClosed: Boolean): Boolean {
        if (enableFrames.isNotEmpty()) {
            val flags = enableFrames.split(',').map { it.trim() }
            if (frameIdx in flags.indices) {
                return flags[frameIdx] == "1"
            }
        }
        return isClosed
    }
}

data class ComponentDef(
    val type: String,
    val file: String,
    val name: String,
    val category: String,
    val width: Float,
    val height: Float,
    val nodes: List<TerminalNode>,
    val defaultTag: String,
    val defaultComment: String = "",
    val infoType: String = "",
    val contacts: List<ContactDef> = emptyList(),
    val variantFrames: Map<Int, Map<Int, List<String>>> = emptyMap()
)

object NativeComponentRegistry {

    private val registry = mutableMapOf<String, ComponentDef>()
    private var isLoaded = false

    fun loadFromAssets(context: Context) {
        if (isLoaded) return
        try {
            val jsonStream = context.assets.open("components.json")
            val reader = InputStreamReader(jsonStream, "UTF-8")
            val jsonStr = reader.readText()
            reader.close()

            val root = JSONObject(jsonStr)
            val keys = root.keys()

            while (keys.hasNext()) {
                val key = keys.next()
                // Skip non-component launcher icon assets
                if (key.startsWith("icon", ignoreCase = true) || key.contains("%20")) {
                    continue
                }
                val obj = root.getJSONObject(key)

                val file = obj.optString("file", "$key.svg")
                val width = obj.optDouble("width", 60.0).toFloat()
                val height = obj.optDouble("height", 60.0).toFloat()

                val info = obj.optJSONObject("info")
                var name = info?.optString("name", key) ?: key
                var category = info?.optString("category", "others") ?: "others"
                val tagType = info?.optString("tagType", "") ?: ""
                val infoType = info?.optString("type", "") ?: ""

                // Fix mislabeled or un-categorized entries
                if (key.startsWith("motorBreaker", ignoreCase = true) || key == "breaker") {
                    category = "protections"
                    if (name.equals("breaker", ignoreCase = true)) {
                        name = "Motor Circuit Breaker 3P"
                    }
                } else if (key.equals("motor", ignoreCase = true)) {
                    // Skip obsolete placeholder entry
                    continue
                } else if (key.startsWith("motor", ignoreCase = true)) {
                    category = "motors"
                } else if (key == "text") {
                    name = "Text Label"
                } else if (key == "coilSolenoid") {
                    name = "Solenoid Coil"
                } else if (key == "coilDelayOn") {
                    name = "On-Delay Relay Coil"
                } else if (key == "coilDelayOff") {
                    name = "Off-Delay Relay Coil"
                }

                val defaultTag = when {
                    key == "arrowSource" || key == "arrowDestination" -> "-REF1"
                    key.startsWith("overload") || key.startsWith("fuse") -> "-F"
                    tagType == "contactors" -> "-KM"
                    tagType == "coils" -> "-KM"
                    category == "switches" -> "-S"
                    category == "protections" -> "-Q"
                    category == "motors" -> "-M"
                    category == "loads" -> "-HL"
                    category == "sources" -> "-L"
                    else -> "-K"
                }

                val nodesList = mutableListOf<TerminalNode>()
                val seenNodeIds = mutableSetOf<Int>()
                val nodesArray = obj.optJSONArray("nodes")
                if (nodesArray != null) {
                    for (i in 0 until nodesArray.length()) {
                        val nodeObj = nodesArray.getJSONObject(i)
                        val idx = nodeObj.optInt("id", i)
                        if (!seenNodeIds.add(idx)) continue
                        val nodeTag = nodeObj.optString("tag", "")
                        val dirRaw = nodeObj.optString("direction", "0")
                        val dirInt = dirRaw.toIntOrNull() ?: when (dirRaw.lowercase()) {
                            "up", "north" -> 0
                            "right", "east" -> 1
                            "down", "south" -> 2
                            "left", "west" -> 3
                            else -> 0
                        }
                        val dx = nodeObj.optDouble("dx", 0.0).toFloat()
                        val dy = nodeObj.optDouble("dy", 0.0).toFloat()
                        nodesList.add(TerminalNode(idx, dx, dy, nodeTag, dirInt))
                    }
                }

                val contactsList = mutableListOf<ContactDef>()
                val contactsArray = obj.optJSONArray("contacts")
                if (contactsArray != null) {
                    for (i in 0 until contactsArray.length()) {
                        val cObj = contactsArray.getJSONObject(i)
                        val cId = cObj.optString("id", i.toString())
                        val n1 = cObj.optString("nodeId1", "0").toIntOrNull() ?: 0
                        val n2 = cObj.optString("nodeId2", "1").toIntOrNull() ?: 1
                        val enFrames = cObj.optString("enableFrames", "")
                        contactsList.add(ContactDef(cId, n1, n2, enFrames))
                    }
                }

                val variantFramesMap = mutableMapOf<Int, Map<Int, List<String>>>()
                val vfObj = obj.optJSONObject("variantFrames")
                if (vfObj != null) {
                    val vKeys = vfObj.keys()
                    while (vKeys.hasNext()) {
                        val vKeyStr = vKeys.next()
                        val vIdx = vKeyStr.toIntOrNull() ?: continue
                        val fObj = vfObj.optJSONObject(vKeyStr) ?: continue
                        val fMap = mutableMapOf<Int, List<String>>()
                        val fKeys = fObj.keys()
                        while (fKeys.hasNext()) {
                            val fKeyStr = fKeys.next()
                            val fIdx = fKeyStr.toIntOrNull() ?: continue
                            val arr = fObj.optJSONArray(fKeyStr) ?: continue
                            val pids = mutableListOf<String>()
                            for (j in 0 until arr.length()) {
                                val pid = arr.optString(j, "")
                                if (pid.isNotEmpty()) pids.add(pid)
                            }
                            fMap[fIdx] = pids
                        }
                        if (fMap.isNotEmpty()) {
                            variantFramesMap[vIdx] = fMap
                        }
                    }
                }

                registry[key] = ComponentDef(
                    type = key,
                    file = file,
                    name = name,
                    category = category,
                    width = width,
                    height = height,
                    nodes = nodesList,
                    defaultTag = defaultTag,
                    infoType = infoType,
                    contacts = contactsList,
                    variantFrames = variantFramesMap
                )
            }
            isLoaded = true
        } catch (_: Exception) {
            loadFallbacks()
        }
    }

    private fun loadFallbacks() {
        if (registry.isNotEmpty()) return
        register(ComponentDef("sourceL1", "sourceL1.svg", "Phase L1", "sources", 30f, 30f,
            listOf(TerminalNode(0, 0f, 0f, "L1", 2)), "-L1"))
        register(ComponentDef("sourceL2", "sourceL2.svg", "Phase L2", "sources", 30f, 30f,
            listOf(TerminalNode(0, 0f, 0f, "L2", 2)), "-L2"))
        register(ComponentDef("sourceL3", "sourceL3.svg", "Phase L3", "sources", 30f, 30f,
            listOf(TerminalNode(0, 0f, 0f, "L3", 2)), "-L3"))
        register(ComponentDef("sourceN", "sourceN.svg", "Neutral N", "sources", 30f, 30f,
            listOf(TerminalNode(0, 0f, 0f, "N", 2)), "-N"))
        register(ComponentDef("sourcePE", "sourcePE.svg", "Earth PE", "sources", 30f, 30f,
            listOf(TerminalNode(0, 0f, 0f, "PE", 2)), "-PE"))
        register(ComponentDef("sourceL1L2L3N", "sourceL1L2L3N.svg", "3-Phase + Neutral", "sources", 120f, 40f,
            listOf(
                TerminalNode(0, -45f, 0f, "L1", 2),
                TerminalNode(1, -15f, 0f, "L2", 2),
                TerminalNode(2, 15f, 0f, "L3", 2),
                TerminalNode(3, 45f, 0f, "N", 2)
            ), "-L1L2L3N"))
        register(ComponentDef("switch3p0", "switch3p0.svg", "Switch 3-Pos (I - 0 - II)", "switches", 60f, 60f,
            listOf(
                TerminalNode(0, 0f, -60f, "0", 0),
                TerminalNode(1, -60f, 60f, "1", 2),
                TerminalNode(2, 60f, 60f, "2", 2)
            ), "-S"))
        register(ComponentDef("switch3p", "switch3p.svg", "Switch 3-Pos (1 - 2 - 3)", "switches", 60f, 60f,
            listOf(
                TerminalNode(0, 0f, -60f, "0", 0),
                TerminalNode(1, -60f, 60f, "1", 2),
                TerminalNode(2, 60f, 60f, "2", 2)
            ), "-S"))
        register(ComponentDef("contactor3p", "contactor3p.svg", "3-Pole Contactor", "contactors", 90f, 60f,
            listOf(
                TerminalNode(0, -30f, -30f, "1", 0),
                TerminalNode(1, -30f, 30f, "2", 2),
                TerminalNode(2, 0f, -30f, "3", 0),
                TerminalNode(3, 0f, 30f, "4", 2),
                TerminalNode(4, 30f, -30f, "5", 0),
                TerminalNode(5, 30f, 30f, "6", 2)
            ), "-KM"))
        register(ComponentDef("motor3ph", "motor3ph.svg", "Three-Phase Motor", "motors", 70f, 70f,
            listOf(
                TerminalNode(0, -20f, -35f, "U1", 0),
                TerminalNode(1, 0f, -35f, "V1", 0),
                TerminalNode(2, 20f, -35f, "W1", 0),
                TerminalNode(3, 0f, 35f, "PE", 2)
            ), "-M"))
    }

    private fun register(def: ComponentDef) {
        registry[def.type] = def
    }

    fun get(type: String): ComponentDef? = registry[type]

    fun getAll(): List<ComponentDef> = registry.values.toList()

    fun getByCategory(cat: String): List<ComponentDef> = registry.values.filter { it.category == cat }

    fun isPushButton(type: String): Boolean {
        val s = type.lowercase()
        return s.startsWith("button") || s.contains("push") || s.contains("pedal")
    }
}
