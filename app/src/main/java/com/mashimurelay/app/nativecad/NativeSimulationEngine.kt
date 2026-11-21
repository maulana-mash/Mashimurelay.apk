package com.mashimurelay.app.nativecad

class NativeSimulationEngine {

    companion object {
        val PASSIVE_2TERM_LOADS = setOf(
            "lamp", "lampBlink", "buzzer", "siren", "ring",
            "coil", "coilDelayOn", "coilDelayOff", "coilDelayOnOff", "coilFlasher", "coilLatch", "coilSolenoid", "flasher",
            "motorDC", "motor1ph"
        )

        fun isValidPair(potA: Int, potB: Int): Boolean {
            if (potA == 0 || potB == 0) return false
            val hasL1_A = (potA and Potential.L1.flag) != 0
            val hasL2_A = (potA and Potential.L2.flag) != 0
            val hasL3_A = (potA and Potential.L3.flag) != 0
            val hasN_A  = (potA and Potential.N.flag) != 0
            val hasDCP_A = (potA and Potential.DC_P.flag) != 0
            val hasDCN_A = (potA and Potential.DC_N.flag) != 0

            val hasL1_B = (potB and Potential.L1.flag) != 0
            val hasL2_B = (potB and Potential.L2.flag) != 0
            val hasL3_B = (potB and Potential.L3.flag) != 0
            val hasN_B  = (potB and Potential.N.flag) != 0
            val hasDCP_B = (potB and Potential.DC_P.flag) != 0
            val hasDCN_B = (potB and Potential.DC_N.flag) != 0

            val isAcSourceA = hasL1_A || hasL2_A || hasL3_A
            val isAcSourceB = hasL1_B || hasL2_B || hasL3_B

            // AC Phase to Neutral
            if ((isAcSourceA && hasN_B) || (isAcSourceB && hasN_A)) return true

            // AC Phase to different AC Phase
            if ((hasL1_A && (hasL2_B || hasL3_B)) || (hasL2_A && (hasL1_B || hasL3_B)) || (hasL3_A && (hasL1_B || hasL2_B))) return true

            // DC Positive to DC Negative
            if ((hasDCP_A && hasDCN_B) || (hasDCP_B && hasDCN_A)) return true

            return false
        }
    }

    val manualStates = mutableMapOf<Int, Int>() // elemId -> position (0, 1, 2)
    val buttonStates = mutableMapOf<Int, Boolean>()
    val timerStates = mutableMapOf<Int, TimerState>() // elemId -> TimerState
    val tagStateMap = mutableMapOf<String, Boolean>() // tag -> outputState (persistent across simulation frames)
    val fuseHeatTimers = mutableMapOf<Int, Long>() // elemId -> accumulated short circuit heat ms
    val fuseBlownElements = mutableSetOf<Int>() // elemId of fuses blown by short circuit
    val breakerTripTimers = mutableMapOf<Int, Long>() // elemId -> accumulated fault trip delay ms
    val trippedBreakerElements = mutableSetOf<Int>() // elemId of MCB/MPCB/TM/RCBO tripped by short circuit
    val trippedEarthLeakageElements = mutableSetOf<Int>() // elemId of RCCB/RCBO tripped by earth leakage

    fun reset(preserveFuses: Boolean = true) {
        if (preserveFuses) {
            val preservedFuseStates = manualStates.filter { (id, state) ->
                state == 0 && fuseBlownElements.contains(id)
            }
            manualStates.clear()
            manualStates.putAll(preservedFuseStates)
            fuseBlownElements.retainAll(preservedFuseStates.keys)
        } else {
            manualStates.clear()
            fuseBlownElements.clear()
        }
        fuseHeatTimers.clear()
        breakerTripTimers.clear()
        trippedBreakerElements.clear()
        trippedEarthLeakageElements.clear()
        buttonStates.clear()
        timerStates.clear()
        tagStateMap.clear()
    }

    fun setSwitchPosition(elemId: Int, pos: Int): Int {
        manualStates[elemId] = pos
        if (pos != 0) {
            trippedBreakerElements.remove(elemId)
            trippedEarthLeakageElements.remove(elemId)
            breakerTripTimers.remove(elemId)
        }
        return pos
    }

    fun toggleSwitch(elemId: Int, svgData: String): Int {
        val isCartridgeFuse = svgData.startsWith("fuse") && !svgData.startsWith("fuseSwitch")
        val cur = manualStates[elemId] ?: if (svgData == "switch3p0" || isCartridgeFuse) 1 else 0
        val next = when (svgData) {
            "switch3p0" -> when (cur) {
                1 -> 0
                0 -> 2
                else -> 1
            }
            "switch3p" -> (cur + 1) % 3
            else -> if (cur == 0) 1 else 0
        }
        manualStates[elemId] = next
        if (next != 0) {
            trippedBreakerElements.remove(elemId)
            trippedEarthLeakageElements.remove(elemId)
            breakerTripTimers.remove(elemId)
        }
        return next
    }

    fun setButtonPressed(elemId: Int, pressed: Boolean) {
        buttonStates[elemId] = pressed
    }

    fun step(elements: List<NativeElement>, connectors: List<NativeConnector>, dtMs: Long = 0L): SimResult {
        val elementFrames = mutableMapOf<Int, Int>()
        val wirePotentials = mutableMapOf<Int, Potential>()
        val seriesEnergizedLoads = mutableSetOf<Int>()
        val seriesActiveNets = mutableMapOf<Int, Int>()
        val nodeToNetId = mutableMapOf<String, Int>()
        var shortCircuit = false
        var isDcOverheat = false
        var isEarthLeakage = false
        val activeCoilTags = mutableSetOf<String>()
        val newlyBlownFuses = mutableSetOf<Int>()
        val newlyTrippedBreakers = mutableSetOf<Int>()

        // Prune fuseBlownElements & tripped breakers to only currently existing elements
        val currentElemIds = elements.map { it.id }.toSet()
        trippedBreakerElements.retainAll(currentElemIds)
        trippedEarthLeakageElements.retainAll(currentElemIds)
        val currentFuseIds = elements.filter {
            it.svgData.startsWith("fuse") && !it.svgData.startsWith("fuseSwitch")
        }.map { it.id }.toSet()
        fuseBlownElements.retainAll(currentFuseIds)
        val turnedOnFuses = fuseBlownElements.filter { (manualStates[it] ?: 1) != 0 }
        fuseBlownElements.removeAll(turnedOnFuses.toSet())

        // Seed timer states and tagStateMap for all coils in circuit
        for (el in elements) {
            val def = NativeComponentRegistry.get(el.svgData) ?: continue
            if (def.category == "coils") {
                val presetOnMs = (el.presetTime * 1000f).toLong().coerceAtLeast(0L)
                val presetOffMs = if (el.presetTimeOff > 0f) (el.presetTimeOff * 1000f).toLong() else presetOnMs
                val state = timerStates.getOrPut(el.id) {
                    TimerState(
                        presetMs = if (presetOnMs > 0) presetOnMs else 2000L,
                        presetOffMs = if (presetOffMs > 0) presetOffMs else 2000L
                    )
                }
                if (presetOnMs > 0) {
                    state.presetMs = presetOnMs
                }
                if (presetOffMs > 0) {
                    state.presetOffMs = presetOffMs
                }
                if (el.tag.isNotEmpty()) {
                    tagStateMap[el.tag.trim()] = state.outputState
                }
            }
        }

        // 1. Initial Switch & Button States
        for (el in elements) {
            val def = NativeComponentRegistry.get(el.svgData) ?: continue
            if (def.category == "switches" || def.category == "protections") {
                if (NativeComponentRegistry.isPushButton(el.svgData)) {
                    val isPressed = buttonStates[el.id] == true
                    elementFrames[el.id] = if (isPressed) 1 else 0
                } else {
                    val isCartridgeFuse = el.svgData.startsWith("fuse") && !el.svgData.startsWith("fuseSwitch")
                    val pos = manualStates[el.id] ?: if (el.svgData == "switch3p0" || isCartridgeFuse) 1 else 0
                    elementFrames[el.id] = if (isCartridgeFuse) (if (pos == 1) 0 else 1) else pos
                }
            }
        }

        val elemMap = elements.associateBy { it.id }

        // 2. Fixed-Point Iteration Solver
        val MAX_ITER = 12
        var iter = 0
        var converged = false

        val nodePotentialMap = mutableMapOf<String, Int>() // "elemId:nodeIdx" -> potential bitmask
        val adj = mutableMapOf<String, MutableSet<String>>()
        val conductingDiodes = mutableSetOf<Int>()
        val activePowerSupplies = mutableSetOf<Int>()

        while (!converged && iter < MAX_ITER) {
            iter++
            converged = true
            val stepDt = if (iter == 1) dtMs else 0L
            shortCircuit = false
            isDcOverheat = false
            isEarthLeakage = false
            nodePotentialMap.clear()

            // Set fixed potentials at power sources
            for (el in elements) {
                when (el.svgData) {
                    "sourceL1" -> nodePotentialMap["${el.id}:0"] = Potential.L1.flag
                    "sourceL2" -> nodePotentialMap["${el.id}:0"] = Potential.L2.flag
                    "sourceL3" -> nodePotentialMap["${el.id}:0"] = Potential.L3.flag
                    "sourceN" -> nodePotentialMap["${el.id}:0"] = Potential.N.flag
                    "sourcePE" -> nodePotentialMap["${el.id}:0"] = Potential.PE.flag
                    "sourceGnd" -> nodePotentialMap["${el.id}:0"] = Potential.GND.flag
                    "sourcePlus" -> nodePotentialMap["${el.id}:0"] = Potential.DC_P.flag
                    "sourceNegative" -> nodePotentialMap["${el.id}:0"] = Potential.DC_N.flag
                    "battery" -> {
                        nodePotentialMap["${el.id}:0"] = Potential.DC_P.flag
                        nodePotentialMap["${el.id}:1"] = Potential.DC_N.flag
                    }
                    "powerSupply2" -> {
                        val hasInWire = connectors.any {
                            (it.elem1Id == el.id && (it.node1Idx == 0 || it.node1Idx == 2)) ||
                            (it.elem2Id == el.id && (it.node2Idx == 0 || it.node2Idx == 2))
                        }
                        if (!hasInWire || activePowerSupplies.contains(el.id)) {
                            nodePotentialMap["${el.id}:1"] = Potential.DC_P.flag
                            nodePotentialMap["${el.id}:3"] = Potential.DC_N.flag
                        }
                    }
                    "powerSupply" -> {
                        val hasInWire = connectors.any {
                            (it.elem1Id == el.id && (it.node1Idx == 0 || it.node1Idx == 2 || it.node1Idx == 4)) ||
                            (it.elem2Id == el.id && (it.node2Idx == 0 || it.node2Idx == 2 || it.node2Idx == 4))
                        }
                        if (!hasInWire || activePowerSupplies.contains(el.id)) {
                            nodePotentialMap["${el.id}:1"] = Potential.DC_P.flag
                            nodePotentialMap["${el.id}:3"] = Potential.DC_P.flag
                            nodePotentialMap["${el.id}:5"] = Potential.DC_N.flag
                            nodePotentialMap["${el.id}:7"] = Potential.DC_N.flag
                        }
                        nodePotentialMap["${el.id}:6"] = Potential.PE.flag
                    }
                    "generator3ph" -> {
                        nodePotentialMap["${el.id}:4"] = Potential.L1.flag
                        val hasCtrlWire = connectors.any {
                            (it.elem1Id == el.id && (it.node1Idx == 4 || it.node1Idx == 5)) ||
                            (it.elem2Id == el.id && (it.node2Idx == 4 || it.node2Idx == 5))
                        }
                        if (!hasCtrlWire || activePowerSupplies.contains(el.id)) {
                            nodePotentialMap["${el.id}:0"] = Potential.L1.flag
                            nodePotentialMap["${el.id}:1"] = Potential.L2.flag
                            nodePotentialMap["${el.id}:2"] = Potential.L3.flag
                            nodePotentialMap["${el.id}:3"] = Potential.N.flag
                        }
                    }
                    "sourceL1L2L3" -> {
                        nodePotentialMap["${el.id}:0"] = Potential.L1.flag
                        nodePotentialMap["${el.id}:1"] = Potential.L2.flag
                        nodePotentialMap["${el.id}:2"] = Potential.L3.flag
                    }
                    "sourceL1L2L3N" -> {
                        nodePotentialMap["${el.id}:0"] = Potential.L1.flag
                        nodePotentialMap["${el.id}:1"] = Potential.L2.flag
                        nodePotentialMap["${el.id}:2"] = Potential.L3.flag
                        nodePotentialMap["${el.id}:3"] = Potential.N.flag
                    }
                }
            }
            val rawSourcePotentials = nodePotentialMap.toMap()

            // Build Adjacency Graph of Conductive Nodes
            adj.clear()
            fun addEdge(a: String, b: String) {
                adj.getOrPut(a) { mutableSetOf() }.add(b)
                adj.getOrPut(b) { mutableSetOf() }.add(a)
            }

            // External wires (Connectors)
            for (c in connectors) {
                val n1 = "${c.elem1Id}:${c.node1Idx}"
                val n2 = "${c.elem2Id}:${c.node2Idx}"
                addEdge(n1, n2)
            }

            // Cross-reference Arrow connections (e.g. -REF1, case-insensitive & trimmed)
            val arrowElems = elements.filter { (it.svgData == "arrowSource" || it.svgData == "arrowDestination") && it.tag.trim().isNotEmpty() }
            for (i in arrowElems.indices) {
                val a1 = arrowElems[i]
                val tag1 = a1.tag.trim().uppercase()
                for (j in i + 1 until arrowElems.size) {
                    val a2 = arrowElems[j]
                    if (a2.tag.trim().uppercase() == tag1) {
                        addEdge("${a1.id}:0", "${a2.id}:0")
                    }
                }
            }

            // Internal Element Conductive Paths
            for (el in elements) {
                val def = NativeComponentRegistry.get(el.svgData) ?: continue
                val cat = def.category
                val contacts = def.contacts

                // Diodes (Conduct from Anode:0 to Cathode:1 when forward biased)
                if (el.svgData == "diode") {
                    if (conductingDiodes.contains(el.id)) {
                        addEdge("${el.id}:0", "${el.id}:1")
                    }
                    elementFrames[el.id] = 0
                    continue
                }

                // Junctions / Wire nodes (Always Conductive internally between all their terminals)
                if (def.infoType == "junction" || el.svgData.startsWith("junction")) {
                    if (def.nodes.size > 1) {
                        for (i in 0 until def.nodes.size - 1) {
                            addEdge("${el.id}:$i", "${el.id}:${i + 1}")
                        }
                    }
                    elementFrames[el.id] = 0
                    continue
                }

                // Fuses (fuse1p, fuse2p, etc.): Frame 0 = intact (ON), Frame 1 = blown (OFF)
                if (el.svgData.startsWith("fuse") && !el.svgData.startsWith("fuseSwitch")) {
                    val isIntact = (manualStates[el.id] ?: 1) == 1
                    val frame = if (isIntact) 0 else 1
                    elementFrames[el.id] = frame
                    if (isIntact) {
                        if (contacts.isNotEmpty()) {
                            for (c in contacts) {
                                addEdge("${el.id}:${c.nodeId1}", "${el.id}:${c.nodeId2}")
                            }
                        } else if (def.nodes.size >= 2) {
                            for (i in 0 until def.nodes.size step 2) {
                                if (i + 1 < def.nodes.size) addEdge("${el.id}:$i", "${el.id}:${i + 1}")
                            }
                        }
                    }
                    continue
                }

                // Thermal Overload Relays (overload, overload1p, overload2p, overload3p, overload4p):
                if (el.svgData.startsWith("overload")) {
                    val isTripped = (manualStates[el.id] ?: 0) == 1
                    val frame = if (isTripped) 1 else 0
                    elementFrames[el.id] = frame
                    if (contacts.isNotEmpty()) {
                        for (c in contacts) {
                            if (c.isEnabledInFrame(frame, !isTripped)) {
                                addEdge("${el.id}:${c.nodeId1}", "${el.id}:${c.nodeId2}")
                            }
                        }
                    } else if (!isTripped) {
                        for (i in 0 until def.nodes.size step 2) {
                            if (i + 1 < def.nodes.size) addEdge("${el.id}:$i", "${el.id}:${i + 1}")
                        }
                    }
                    continue
                }

                // Switches, Buttons, Protections, Contactors, Auxiliaries
                var isClosed = false
                var frameIdx = 0

                if (cat == "protections" || cat == "switches") {
                    val manualVal = manualStates[el.id]
                    val isNC = el.svgData.contains("NC")

                    if (el.svgData == "switch3p0") {
                        frameIdx = manualVal ?: 1
                        isClosed = (frameIdx != 1)
                    } else if (el.svgData == "switch3p") {
                        frameIdx = manualVal ?: 0
                        isClosed = true
                    } else if (def.infoType == "button" || NativeComponentRegistry.isPushButton(el.svgData)) {
                        val isPressed = buttonStates[el.id] == true
                        isClosed = if (isNC) !isPressed else isPressed
                        frameIdx = if (isPressed) 1 else 0
                    } else if (def.infoType == "emergency" || el.svgData.contains("emergency")) {
                        val isLatched = (manualStates[el.id] ?: 0) == 1
                        isClosed = if (isNC) !isLatched else isLatched
                        frameIdx = if (isLatched) 1 else 0
                    } else if (isNC) {
                        val manualOn = (manualVal ?: 0) == 1
                        isClosed = !manualOn
                        frameIdx = if (manualOn) 1 else 0
                    } else {
                        // Normally Open Isolator, Breaker, Switch: Default is 0 (OFF / OPEN)!
                        val manualOn = (manualVal ?: 0) == 1
                        isClosed = manualOn
                        frameIdx = if (isClosed) 1 else 0
                    }
                } else if (cat == "auxiliaries" || cat == "contactors") {
                    val tag = el.tag.trim()
                    var rawMasterActive = false
                    var masterIsTimerCoil = false
                    if (tag.isNotEmpty()) {
                        val masterProt = elements.find {
                            it.id != el.id && it.tag.trim() == tag &&
                                (it.svgData.startsWith("overload") || it.svgData.contains("breaker", ignoreCase = true))
                        }
                        val masterCoil = elements.find {
                            it.id != el.id && it.tag.trim() == tag &&
                                (it.svgData.startsWith("coil") || it.svgData == "phaseFailure")
                        }
                        if (masterProt != null) {
                            rawMasterActive = (manualStates[masterProt.id] ?: 0) == 1
                        } else {
                            rawMasterActive = tagStateMap[tag] ?: false
                            if (masterCoil != null && (masterCoil.svgData == "coilDelayOn" ||
                                    masterCoil.svgData == "coilDelayOff" ||
                                    masterCoil.svgData == "coilDelayOnOff")) {
                                masterIsTimerCoil = true
                            }
                        }
                    }

                    // Kontak tunda independen (pneumatic timer block) jika dipasangkan ke koil instan / breaker / overload
                    var masterActive = rawMasterActive
                    if (!masterIsTimerCoil && (el.svgData.endsWith("DelayOn") || el.svgData.endsWith("DelayOff"))) {
                        val presetMs = (el.presetTime * 1000f).toLong().coerceAtLeast(100L)
                        val cState = timerStates.getOrPut(el.id) {
                            TimerState(presetMs = presetMs, presetOffMs = presetMs)
                        }
                        cState.presetMs = presetMs
                        cState.energized = rawMasterActive
                        if (el.svgData.endsWith("DelayOn")) {
                            if (rawMasterActive) {
                                if (cState.elapsedMs < cState.presetMs) {
                                    cState.elapsedMs += stepDt
                                    if (cState.elapsedMs >= cState.presetMs) {
                                        cState.elapsedMs = cState.presetMs
                                        cState.outputState = true
                                        cState.isTiming = false
                                    } else {
                                        cState.outputState = false
                                        cState.isTiming = true
                                    }
                                } else {
                                    cState.outputState = true
                                    cState.isTiming = false
                                }
                            } else {
                                cState.elapsedMs = 0L
                                cState.isTiming = false
                                cState.outputState = false
                            }
                        } else {
                            if (rawMasterActive) {
                                cState.elapsedMs = 0L
                                cState.isTiming = false
                                cState.outputState = true
                            } else if (cState.outputState) {
                                cState.elapsedMs += stepDt
                                if (cState.elapsedMs < cState.presetMs) {
                                    cState.isTiming = true
                                    cState.outputState = true
                                } else {
                                    cState.elapsedMs = cState.presetMs
                                    cState.isTiming = false
                                    cState.outputState = false
                                }
                            } else {
                                cState.elapsedMs = 0L
                                cState.isTiming = false
                                cState.outputState = false
                            }
                        }
                        masterActive = cState.outputState
                    }

                    if (el.svgData.contains("NC")) {
                        isClosed = !masterActive
                        frameIdx = if (masterActive) 1 else 0
                    } else {
                        isClosed = masterActive
                        frameIdx = if (masterActive) 1 else 0
                    }
                }

                elementFrames[el.id] = frameIdx

                if (contacts.isNotEmpty()) {
                    for (c in contacts) {
                        if (c.isEnabledInFrame(frameIdx, isClosed)) {
                            addEdge("${el.id}:${c.nodeId1}", "${el.id}:${c.nodeId2}")
                        }
                    }
                } else {
                    // Fallbacks for any components without contacts defined in json
                    when (el.svgData) {
                        "switchNO", "breaker1p", "isolator1p" -> {
                            if (isClosed) addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "switchNC" -> {
                            if (isClosed) addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "switchCON", "rollerCON", "contactCON" -> {
                            if (frameIdx == 0) addEdge("${el.id}:0", "${el.id}:1")
                            else addEdge("${el.id}:0", "${el.id}:2")
                        }
                        "switch3p0" -> {
                            if (frameIdx == 0) addEdge("${el.id}:0", "${el.id}:1")
                            else if (frameIdx == 2) addEdge("${el.id}:0", "${el.id}:2")
                        }
                        "switch3p" -> {
                            if (frameIdx == 0) addEdge("${el.id}:0", "${el.id}:1")
                            else if (frameIdx == 1) addEdge("${el.id}:0", "${el.id}:2")
                            else if (frameIdx == 2) addEdge("${el.id}:0", "${el.id}:3")
                        }
                        "buttonNO", "emergencyNO" -> {
                            if (isClosed) addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "buttonNC", "emergencyNC" -> {
                            if (isClosed) addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "buttonCON", "emergencyCON" -> {
                            if (isClosed) addEdge("${el.id}:0", "${el.id}:2")
                            else addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "buttonNONC", "emergencyNONC" -> {
                            if (isClosed) addEdge("${el.id}:2", "${el.id}:3")
                            else addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "buttonCrossover", "switchCrossover" -> {
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:1")
                                addEdge("${el.id}:2", "${el.id}:3")
                            } else {
                                addEdge("${el.id}:0", "${el.id}:3")
                                addEdge("${el.id}:2", "${el.id}:1")
                            }
                        }
                        "contactNO", "contactor1p" -> {
                            if (isClosed) addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "contactNC", "contactorNC1p" -> {
                            if (isClosed) addEdge("${el.id}:0", "${el.id}:1")
                        }
                        "contactor2p", "contactorNC2p" -> {
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:1")
                                addEdge("${el.id}:2", "${el.id}:3")
                            }
                        }
                        "contactor3p", "contactorNC3p" -> {
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:1")
                                addEdge("${el.id}:2", "${el.id}:3")
                                addEdge("${el.id}:4", "${el.id}:5")
                            }
                        }
                        "contactor4p", "contactorNC4p" -> {
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:1")
                                addEdge("${el.id}:2", "${el.id}:3")
                                addEdge("${el.id}:4", "${el.id}:5")
                                addEdge("${el.id}:6", "${el.id}:7")
                            }
                        }
                        "breaker" -> {
                            // Motor Circuit Breaker 3P: pin atas 0,1,2 -> pin bawah 3,4,5
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:3")
                                addEdge("${el.id}:1", "${el.id}:4")
                                addEdge("${el.id}:2", "${el.id}:5")
                            }
                        }
                        "breaker2p", "motorBreaker2p", "tmBreaker2p", "rcboBreaker2p", "rccbBreaker2p", "fuseSwitch2p", "fuseSwitch1pn", "isolator2p" -> {
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:1")
                                addEdge("${el.id}:2", "${el.id}:3")
                            }
                        }
                        "breaker3p", "motorBreaker3p", "tmBreaker3p", "fuseSwitch3p", "isolator3p" -> {
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:1")
                                addEdge("${el.id}:2", "${el.id}:3")
                                addEdge("${el.id}:4", "${el.id}:5")
                            }
                        }
                        "breaker4p", "motorBreaker4p", "tmBreaker4p", "rcboBreaker4p", "rccbBreaker4p", "fuseSwitch4p", "fuseSwitch3pn", "isolator4p" -> {
                            if (isClosed) {
                                addEdge("${el.id}:0", "${el.id}:1")
                                addEdge("${el.id}:2", "${el.id}:3")
                                addEdge("${el.id}:4", "${el.id}:5")
                                addEdge("${el.id}:6", "${el.id}:7")
                            }
                        }
                    }
                }
            }

            // Propagate Potentials across Adjacency Graph using independent per-source BFS
            // (Per-seed visited set guarantees L1/L2/L3/N/DC+/DC- propagate fully regardless of element order!)
            for ((startNode, sourcePot) in nodePotentialMap.toMap()) {
                if (sourcePot == 0) continue
                val visited = mutableSetOf(startNode)
                val queue = ArrayDeque<String>()
                queue.add(startNode)

                while (queue.isNotEmpty()) {
                    val curr = queue.removeFirst()
                    val neighbors = adj[curr] ?: emptySet()
                    for (next in neighbors) {
                        val currentPot = nodePotentialMap[next] ?: 0
                        val combined = currentPot or sourcePot
                        if (currentPot != combined) {
                            nodePotentialMap[next] = combined
                            converged = false
                        }
                        if (!visited.contains(next)) {
                            visited.add(next)
                            queue.add(next)
                        }
                    }
                }
            }

            // Check Short Circuit & Earth Leakage Condition
            for ((_, pot) in nodePotentialMap) {
                val hasL1 = (pot and Potential.L1.flag) != 0
                val hasL2 = (pot and Potential.L2.flag) != 0
                val hasL3 = (pot and Potential.L3.flag) != 0
                val hasN  = (pot and Potential.N.flag) != 0
                val hasEarth = (pot and (Potential.PE.flag or Potential.GND.flag)) != 0
                val hasDCP = (pot and Potential.DC_P.flag) != 0
                val hasDCN = (pot and Potential.DC_N.flag) != 0

                val phaseCount = (if (hasL1) 1 else 0) + (if (hasL2) 1 else 0) + (if (hasL3) 1 else 0)
                if (phaseCount >= 2 || (phaseCount >= 1 && hasN)) {
                    shortCircuit = true
                }
                if (phaseCount >= 1 && hasEarth) {
                    isEarthLeakage = true
                }
                if (hasDCP && hasDCN) {
                    shortCircuit = true
                    isDcOverheat = true
                }
            }

            // Trace Fault Paths from Raw Source Pins & Evaluate Natural-Delay Protection Trips:
            // - Cartridge Fuse (fuse1p..4p): ~1.3s thermal melting on short circuit
            // - Magnetic/Short-Circuit Breakers (breaker, breaker1p..4p, tmBreaker*, motorBreaker*, rcboBreaker*): ~0.9s visible delay
            // - Earth-Leakage Breakers (rccbBreaker*, rcboBreaker*): ~0.8s visible delay on L -> PE/GND leakage
            if ((shortCircuit || isEarthLeakage) && stepDt > 0L) {
                fun tracePinsBetween(starts: Set<String>, targets: Set<String>, outPins: MutableSet<String>) {
                    if (starts.isEmpty() || targets.isEmpty()) return
                    for (startPin in starts) {
                        val queue = ArrayDeque<Pair<String, List<String>>>()
                        queue.add(Pair(startPin, listOf(startPin)))
                        val seen = mutableSetOf(startPin)
                        while (queue.isNotEmpty()) {
                            val (curr, path) = queue.removeFirst()
                            if (curr != startPin && targets.contains(curr)) {
                                outPins.addAll(path)
                                continue
                            }
                            for (nxt in adj[curr] ?: emptySet()) {
                                if (seen.add(nxt)) {
                                    queue.add(Pair(nxt, path + nxt))
                                }
                            }
                        }
                    }
                }

                val rawL1 = rawSourcePotentials.filterValues { (it and Potential.L1.flag) != 0 }.keys
                val rawL2 = rawSourcePotentials.filterValues { (it and Potential.L2.flag) != 0 }.keys
                val rawL3 = rawSourcePotentials.filterValues { (it and Potential.L3.flag) != 0 }.keys
                val rawN = rawSourcePotentials.filterValues { (it and Potential.N.flag) != 0 }.keys
                val rawPhases = rawL1 + rawL2 + rawL3
                val rawDCP = rawSourcePotentials.filterValues { (it and Potential.DC_P.flag) != 0 }.keys
                val rawDCN = rawSourcePotentials.filterValues { (it and Potential.DC_N.flag) != 0 }.keys
                val rawEarth = rawSourcePotentials.filterValues { (it and (Potential.PE.flag or Potential.GND.flag)) != 0 }.keys

                val shortFaultPins = mutableSetOf<String>()
                if (shortCircuit) {
                    tracePinsBetween(rawPhases, rawN, shortFaultPins)
                    tracePinsBetween(rawL1, rawL2 + rawL3, shortFaultPins)
                    tracePinsBetween(rawL2, rawL3, shortFaultPins)
                    tracePinsBetween(rawDCP, rawDCN, shortFaultPins)
                }

                val earthFaultPins = mutableSetOf<String>()
                if (isEarthLeakage) {
                    tracePinsBetween(rawPhases, rawEarth, earthFaultPins)
                }

                fun getPolePairs(el: NativeElement, def: ComponentDef?): List<Pair<Int, Int>> {
                    if (def != null && def.contacts.isNotEmpty()) {
                        return def.contacts.map { Pair(it.nodeId1, it.nodeId2) }
                    }
                    if (el.svgData == "breaker") {
                        return listOf(Pair(0, 3), Pair(1, 4), Pair(2, 5))
                    }
                    val n = def?.nodes?.size ?: 2
                    val pairs = mutableListOf<Pair<Int, Int>>()
                    for (idx in 0 until n step 2) {
                        if (idx + 1 < n) pairs.add(Pair(idx, idx + 1))
                    }
                    return pairs
                }

                // 1) Cartridge Fuses (~1.3s thermal melting)
                for (el in elements) {
                    if (!el.svgData.startsWith("fuse") || el.svgData.startsWith("fuseSwitch")) continue
                    val isIntact = (manualStates[el.id] ?: 1) == 1
                    if (!isIntact) continue
                    val def = NativeComponentRegistry.get(el.svgData)
                    val isFuseInFault = getPolePairs(el, def).any { (a, b) ->
                        shortFaultPins.contains("${el.id}:$a") && shortFaultPins.contains("${el.id}:$b")
                    }
                    if (isFuseInFault) {
                        val currentHeat = (fuseHeatTimers[el.id] ?: 0L) + stepDt
                        fuseHeatTimers[el.id] = currentHeat
                        if (currentHeat >= 1300L) {
                            manualStates[el.id] = 0 // Frame 1: blown / open circuit
                            fuseBlownElements.add(el.id)
                            newlyBlownFuses.add(el.id)
                            elementFrames[el.id] = 1
                            converged = false
                        }
                    } else {
                        val curHeat = fuseHeatTimers[el.id] ?: 0L
                        if (curHeat > 0L) {
                            fuseHeatTimers[el.id] = (curHeat - (stepDt * 0.5f).toLong()).coerceAtLeast(0L)
                        }
                    }
                }

                // 2) Automatic Protection Breakers (MCB/MPCB/TM/RCBO short-circuit trip ~0.9s; RCCB/RCBO earth-leakage trip ~0.8s)
                for (el in elements) {
                    val isShortBreaker = el.svgData == "breaker" ||
                            el.svgData.startsWith("breaker") ||
                            el.svgData.startsWith("tmBreaker") ||
                            el.svgData.startsWith("motorBreaker") ||
                            el.svgData.startsWith("rcboBreaker")
                    val isEarthBreaker = el.svgData.startsWith("rccbBreaker") ||
                            el.svgData.startsWith("rcboBreaker")
                    if (!isShortBreaker && !isEarthBreaker) continue
                    val isClosedNow = (manualStates[el.id] ?: 0) == 1
                    if (!isClosedNow) continue

                    val def = NativeComponentRegistry.get(el.svgData)
                    val pairs = getPolePairs(el, def)
                    val inShort = isShortBreaker && pairs.any { (a, b) ->
                        shortFaultPins.contains("${el.id}:$a") && shortFaultPins.contains("${el.id}:$b")
                    }
                    val inEarth = isEarthBreaker && pairs.any { (a, b) ->
                        earthFaultPins.contains("${el.id}:$a") && earthFaultPins.contains("${el.id}:$b")
                    }

                    if (inShort || inEarth) {
                        val tripDelay = if (inEarth && !inShort) 800L else 900L
                        val acc = (breakerTripTimers[el.id] ?: 0L) + stepDt
                        breakerTripTimers[el.id] = acc
                        if (acc >= tripDelay) {
                            manualStates[el.id] = 0 // Trip OPEN
                            breakerTripTimers.remove(el.id)
                            if (inEarth && !inShort) {
                                trippedEarthLeakageElements.add(el.id)
                            } else {
                                trippedBreakerElements.add(el.id)
                            }
                            newlyTrippedBreakers.add(el.id)
                            elementFrames[el.id] = 0
                            converged = false
                        }
                    } else {
                        val curAcc = breakerTripTimers[el.id] ?: 0L
                        if (curAcc > 0L) {
                            breakerTripTimers[el.id] = (curAcc - (stepDt * 0.5f).toLong()).coerceAtLeast(0L)
                        }
                    }
                }
            } else if (stepDt > 0L) {
                for (k in fuseHeatTimers.keys.toList()) {
                    val v = (fuseHeatTimers[k] ?: 0L) - (stepDt * 0.5f).toLong()
                    if (v <= 0L) fuseHeatTimers.remove(k) else fuseHeatTimers[k] = v
                }
                for (k in breakerTripTimers.keys.toList()) {
                    val v = (breakerTripTimers[k] ?: 0L) - (stepDt * 0.5f).toLong()
                    if (v <= 0L) breakerTripTimers.remove(k) else breakerTripTimers[k] = v
                }
            }

            // Series Circuit Resolution Pass (Passive 2-Terminal Loads Traversal)
            seriesEnergizedLoads.clear()
            seriesActiveNets.clear()
            nodeToNetId.clear()

            if (!shortCircuit) {
                val netPotentials = mutableMapOf<Int, Int>()
                var netCounter = 0

                val allNodes = mutableSetOf<String>()
                allNodes.addAll(adj.keys)
                allNodes.addAll(nodePotentialMap.keys)
                for (c in connectors) {
                    allNodes.add("${c.elem1Id}:${c.node1Idx}")
                    allNodes.add("${c.elem2Id}:${c.node2Idx}")
                }
                for (el in elements) {
                    val def = NativeComponentRegistry.get(el.svgData) ?: continue
                    for (node in def.nodes) {
                        allNodes.add("${el.id}:${node.index}")
                    }
                }

                for (node in allNodes) {
                    if (nodeToNetId.containsKey(node)) continue
                    val curNet = ++netCounter
                    val q = ArrayDeque<String>()
                    q.add(node)
                    nodeToNetId[node] = curNet
                    var netPot = 0

                    while (q.isNotEmpty()) {
                        val curr = q.removeFirst()
                        val p = nodePotentialMap[curr] ?: 0
                        netPot = netPot or p
                        for (nxt in adj[curr] ?: emptySet()) {
                            if (!nodeToNetId.containsKey(nxt)) {
                                nodeToNetId[nxt] = curNet
                                q.add(nxt)
                            }
                        }
                    }
                    netPotentials[curNet] = netPot
                }

                data class NetEdge(val toNet: Int, val elemId: Int)
                val netAdj = mutableMapOf<Int, MutableList<NetEdge>>()

                for (el in elements) {
                    if (el.svgData == "diode") {
                        val n0 = nodeToNetId["${el.id}:0"] ?: continue
                        val n1 = nodeToNetId["${el.id}:1"] ?: continue
                        if (n0 != n1) {
                            netAdj.getOrPut(n0) { mutableListOf() }.add(NetEdge(n1, el.id))
                        }
                        continue
                    }
                    if (el.svgData !in PASSIVE_2TERM_LOADS) continue
                    val n0 = nodeToNetId["${el.id}:0"] ?: continue
                    val n1 = nodeToNetId["${el.id}:1"] ?: continue
                    if (n0 != n1) {
                        netAdj.getOrPut(n0) { mutableListOf() }.add(NetEdge(n1, el.id))
                        netAdj.getOrPut(n1) { mutableListOf() }.add(NetEdge(n0, el.id))
                    }
                }

                // Search paths from High Rails (L1, L2, L3, DC_P) to Return Rails (N, DC_N, or valid return phases)
                val sourceNets = netPotentials.filter { (_, pot) ->
                    (pot and (Potential.L1.flag or Potential.L2.flag or Potential.L3.flag or Potential.DC_P.flag)) != 0
                }

                for ((startNet, startPot) in sourceNets) {
                    val visitedNetsOnPath = mutableSetOf<Int>()
                    visitedNetsOnPath.add(startNet)
                    val currentPathLoads = mutableListOf<Int>()
                    val currentPathNets = mutableListOf<Int>()

                    fun dfs(currNetId: Int, depth: Int) {
                        if (depth > 20) return
                        val edges = netAdj[currNetId] ?: return
                        for (edge in edges) {
                            val nextNetId = edge.toNet
                            if (visitedNetsOnPath.contains(nextNetId)) continue

                            val nextPot = netPotentials[nextNetId] ?: 0
                            currentPathLoads.add(edge.elemId)

                            if (nextPot != 0) {
                                if (isValidPair(startPot, nextPot)) {
                                    val hasOffFlasher = currentPathLoads.any { lid ->
                                        val el = elemMap[lid]
                                        el != null && el.svgData == "flasher" && (timerStates[lid]?.outputState == false)
                                    }

                                    for (lid in currentPathLoads) {
                                        val el = elemMap[lid]
                                        if (el != null && el.svgData == "flasher") {
                                            seriesEnergizedLoads.add(lid)
                                        } else if (!hasOffFlasher) {
                                            seriesEnergizedLoads.add(lid)
                                        }
                                    }

                                    if (!hasOffFlasher) {
                                        for (midNetId in currentPathNets) {
                                            if (!seriesActiveNets.containsKey(midNetId)) {
                                                seriesActiveNets[midNetId] = startPot
                                            }
                                        }
                                    }
                                }
                            } else {
                                visitedNetsOnPath.add(nextNetId)
                                currentPathNets.add(nextNetId)
                                dfs(nextNetId, depth + 1)
                                currentPathNets.removeAt(currentPathNets.size - 1)
                                visitedNetsOnPath.remove(nextNetId)
                            }
                            currentPathLoads.removeAt(currentPathLoads.size - 1)
                        }
                    }

                    dfs(startNet, 0)
                }
            }

            // Evaluate Forward-Biased Diodes and Conditional Power Supplies / 3-Phase Generators
            val AC_PHASE_MASK = Potential.L1.flag or Potential.L2.flag or Potential.L3.flag
            val AC_MASK = AC_PHASE_MASK or Potential.N.flag
            val POS_MASK = AC_PHASE_MASK or Potential.DC_P.flag
            val NEG_MASK = Potential.N.flag or Potential.DC_N.flag
            for (el in elements) {
                if (el.svgData == "diode") {
                    val p0 = nodePotentialMap["${el.id}:0"] ?: 0
                    val p1 = nodePotentialMap["${el.id}:1"] ?: 0
                    val hasDc = ((p0 or p1) and (Potential.DC_P.flag or Potential.DC_N.flag)) != 0
                    val isDcForward = ((p0 and Potential.DC_P.flag) != 0 && (p0 and Potential.DC_N.flag) == 0 && (p1 and Potential.DC_P.flag) == 0) ||
                            ((p1 and Potential.DC_N.flag) != 0 && (p1 and Potential.DC_P.flag) == 0 && (p0 and Potential.DC_N.flag) == 0)
                    // Pada AC (bolak-balik), dioda menyearahkan setengah gelombang pada kedua arah polaritas
                    val isAcHalfWave = !hasDc && (((p0 and AC_MASK) != 0) || ((p1 and AC_MASK) != 0))
                    val shouldConduct = if (hasDc) isDcForward else isAcHalfWave
                    if (shouldConduct && !conductingDiodes.contains(el.id)) {
                        conductingDiodes.add(el.id)
                        converged = false
                    }
                } else if (el.svgData == "powerSupply2") {
                    val p0 = nodePotentialMap["${el.id}:0"] ?: 0
                    val p2 = nodePotentialMap["${el.id}:2"] ?: 0
                    if (isValidPair(p0, p2) && !activePowerSupplies.contains(el.id)) {
                        activePowerSupplies.add(el.id)
                        converged = false
                    }
                } else if (el.svgData == "powerSupply") {
                    val p0 = nodePotentialMap["${el.id}:0"] ?: 0
                    val p2 = nodePotentialMap["${el.id}:2"] ?: 0
                    val p4 = nodePotentialMap["${el.id}:4"] ?: 0
                    if ((isValidPair(p0, p2) || isValidPair(p4, p2) || isValidPair(p0, p4)) && !activePowerSupplies.contains(el.id)) {
                        activePowerSupplies.add(el.id)
                        converged = false
                    }
                } else if (el.svgData == "generator3ph") {
                    val p4 = nodePotentialMap["${el.id}:4"] ?: 0
                    val p5 = nodePotentialMap["${el.id}:5"] ?: 0
                    if (((p5 and POS_MASK) != 0 || isValidPair(p4, p5)) && !activePowerSupplies.contains(el.id)) {
                        activePowerSupplies.add(el.id)
                        converged = false
                    }
                }
            }

            // Evaluate Coils Activation & State Machines
            for (el in elements) {
                val isSeriesActive = seriesEnergizedLoads.contains(el.id)
                val def = NativeComponentRegistry.get(el.svgData)
                val cat = def?.category ?: ""
                if (el.svgData.startsWith("coil") || el.svgData == "flasher") {
                    val potA1 = nodePotentialMap["${el.id}:0"] ?: 0
                    val potA2 = nodePotentialMap["${el.id}:1"] ?: 0
                    val isEnergized = isValidPair(potA1, potA2) || isSeriesActive

                    val presetOnMs = (el.presetTime * 1000f).toLong().coerceAtLeast(0L)
                    val presetOffMs = if (el.presetTimeOff > 0f) (el.presetTimeOff * 1000f).toLong() else presetOnMs
                    val state = timerStates.getOrPut(el.id) {
                        TimerState(
                            presetMs = if (presetOnMs > 0) presetOnMs else 2000L,
                            presetOffMs = if (presetOffMs > 0) presetOffMs else 2000L
                        )
                    }
                    state.energized = isEnergized
                    if (presetOnMs > 0) {
                        state.presetMs = presetOnMs
                    }
                    if (presetOffMs > 0) {
                        state.presetOffMs = presetOffMs
                    }

                    var nextOutput = state.outputState

                    when (el.svgData) {
                        "coilDelayOn" -> {
                            if (isEnergized) {
                                if (state.elapsedMs < state.presetMs) {
                                    state.elapsedMs += stepDt
                                    if (state.elapsedMs >= state.presetMs) {
                                        state.elapsedMs = state.presetMs
                                        nextOutput = true
                                        state.isTiming = false
                                    } else {
                                        nextOutput = false
                                        state.isTiming = true
                                    }
                                } else {
                                    nextOutput = true
                                    state.isTiming = false
                                }
                            } else {
                                state.elapsedMs = 0L
                                state.isTiming = false
                                nextOutput = false
                            }
                        }
                        "coilDelayOff" -> {
                            if (isEnergized) {
                                state.elapsedMs = 0L
                                state.isTiming = false
                                nextOutput = true
                            } else if (state.outputState) {
                                state.elapsedMs += stepDt
                                if (state.elapsedMs < state.presetMs) {
                                    state.isTiming = true
                                    nextOutput = true
                                } else {
                                    state.elapsedMs = state.presetMs
                                    state.isTiming = false
                                    nextOutput = false
                                }
                            } else {
                                state.elapsedMs = 0L
                                state.isTiming = false
                                nextOutput = false
                            }
                        }
                        "coilDelayOnOff" -> {
                            val pOff = if (state.presetOffMs > 0) state.presetOffMs else state.presetMs
                            if (isEnergized) {
                                state.elapsedOffMs = 0L
                                if (state.elapsedMs < state.presetMs) {
                                    state.elapsedMs += stepDt
                                    if (state.elapsedMs >= state.presetMs) {
                                        state.elapsedMs = state.presetMs
                                        nextOutput = true
                                        state.isTiming = false
                                    } else {
                                        nextOutput = false
                                        state.isTiming = true
                                    }
                                } else {
                                    nextOutput = true
                                    state.isTiming = false
                                }
                            } else {
                                state.elapsedMs = 0L
                                if (state.outputState) {
                                    state.elapsedOffMs += stepDt
                                    if (state.elapsedOffMs < pOff) {
                                        state.isTiming = true
                                        nextOutput = true
                                    } else {
                                        state.elapsedOffMs = pOff
                                        state.isTiming = false
                                        nextOutput = false
                                    }
                                } else {
                                    state.isTiming = false
                                    nextOutput = false
                                }
                            }
                        }
                        "coilFlasher", "flasher" -> {
                            if (isEnergized) {
                                val tOn = state.presetMs.coerceAtLeast(100L)
                                val tOff = if (state.presetOffMs > 0L) state.presetOffMs else tOn
                                val tTotal = tOn + tOff
                                state.presetMs = tOn
                                state.presetOffMs = tOff
                                state.elapsedMs = (state.elapsedMs + stepDt) % tTotal
                                state.isTiming = true
                                nextOutput = state.elapsedMs < tOn
                            } else {
                                state.elapsedMs = 0L
                                state.isTiming = false
                                nextOutput = false
                            }
                        }
                        "coilLatch" -> {
                            state.isTiming = false
                            if (stepDt > 0L && isEnergized && !state.prevEnergized) {
                                nextOutput = !state.outputState
                            }
                        }
                        else -> {
                            // Standard instant coil and coilSolenoid
                            state.isTiming = false
                            nextOutput = isEnergized
                        }
                    }

                    if (stepDt > 0L) {
                        state.prevEnergized = isEnergized
                    }

                    elementFrames[el.id] = if (el.svgData == "coilFlasher" || el.svgData == "flasher") {
                        if (nextOutput) 1 else 0
                    } else {
                        if (isEnergized || nextOutput) 1 else 0
                    }

                    if (state.outputState != nextOutput) {
                        state.outputState = nextOutput
                        if (el.tag.isNotEmpty()) {
                            val tag = el.tag.trim()
                            tagStateMap[tag] = nextOutput
                        }
                        converged = false
                    } else if (el.tag.isNotEmpty()) {
                        val tag = el.tag.trim()
                        if (nextOutput) {
                            tagStateMap[tag] = true
                        }
                    }

                    if (nextOutput && el.tag.isNotEmpty()) {
                        activeCoilTags.add(el.tag.trim())
                    }
                } else if (el.svgData == "phaseFailure") {
                    val p0 = nodePotentialMap["${el.id}:0"] ?: 0
                    val p1 = nodePotentialMap["${el.id}:1"] ?: 0
                    val p2 = nodePotentialMap["${el.id}:2"] ?: 0
                    val all3PhFlags = (Potential.L1.flag or Potential.L2.flag or Potential.L3.flag)
                    val active = (p0 != 0 && p1 != 0 && p2 != 0 && ((p0 or p1 or p2) and all3PhFlags) == all3PhFlags)
                    elementFrames[el.id] = if (active) 1 else 0
                    if (el.tag.isNotEmpty()) {
                        val tag = el.tag.trim()
                        if (tagStateMap[tag] != active) {
                            tagStateMap[tag] = active
                            converged = false
                        }
                        if (active) activeCoilTags.add(tag)
                    }
                } else if (el.svgData == "lampBlink") {
                    val p1 = nodePotentialMap["${el.id}:0"] ?: 0
                    val p2 = nodePotentialMap["${el.id}:1"] ?: 0
                    val isEnergized = isValidPair(p1, p2) || isSeriesActive
                    if (isEnergized) {
                        val blinkPresetMs = if (el.presetTime > 0f && el.presetTime != 2.0f) (el.presetTime * 1000f).toLong() else 500L
                        val state = timerStates.getOrPut(el.id) {
                            TimerState(presetMs = blinkPresetMs, presetOffMs = blinkPresetMs)
                        }
                        state.energized = true
                        state.presetMs = blinkPresetMs
                        if (stepDt > 0L) {
                            state.elapsedMs = (state.elapsedMs + stepDt) % (2 * state.presetMs)
                        }
                        val isBlinkOn = state.elapsedMs < state.presetMs
                        elementFrames[el.id] = if (isBlinkOn) 1 else 0
                    } else {
                        timerStates.remove(el.id)
                        elementFrames[el.id] = 0
                    }
                } else if (cat == "loads" || el.svgData == "lamp" || el.svgData == "buzzer" || el.svgData == "siren" || el.svgData == "ring") {
                    val p1 = nodePotentialMap["${el.id}:0"] ?: 0
                    val p2 = nodePotentialMap["${el.id}:1"] ?: 0
                    val isLit = isValidPair(p1, p2) || isSeriesActive
                    elementFrames[el.id] = if (isLit) 1 else 0
                } else if (el.svgData == "motorDC") {
                    val p1 = nodePotentialMap["${el.id}:0"] ?: 0 // Terminal X1
                    val p2 = nodePotentialMap["${el.id}:1"] ?: 0 // Terminal X2
                    // Motor DC secara teori hanya berputar pada sumber arus searah (DC+ dan DC-), bukan AC!
                    val hasP1Pos = (p1 and Potential.DC_P.flag) != 0 && (p1 and Potential.DC_N.flag) == 0
                    val hasP1Neg = (p1 and Potential.DC_N.flag) != 0 && (p1 and Potential.DC_P.flag) == 0
                    val hasP2Pos = (p2 and Potential.DC_P.flag) != 0 && (p2 and Potential.DC_N.flag) == 0
                    val hasP2Neg = (p2 and Potential.DC_N.flag) != 0 && (p2 and Potential.DC_P.flag) == 0
                    val isSeriesDc = isSeriesActive && ((p1 or p2) and (Potential.DC_P.flag or Potential.DC_N.flag)) != 0

                    val frame = when {
                        hasP1Pos && hasP2Neg -> 1 // Forward rotation (CW)
                        hasP1Neg && hasP2Pos -> 2 // Reverse rotation (CCW)
                        isSeriesDc -> if ((p1 and Potential.DC_N.flag) != 0 || (p2 and Potential.DC_P.flag) != 0) 2 else 1
                        else -> 0
                    }
                    elementFrames[el.id] = frame
                } else if (el.svgData == "motor1ph") {
                    val p1 = nodePotentialMap["${el.id}:0"] ?: 0
                    val p2 = nodePotentialMap["${el.id}:1"] ?: 0
                    val isRunning = isValidPair(p1, p2) || isSeriesActive
                    elementFrames[el.id] = if (isRunning) 1 else 0
                } else if (el.svgData == "motor3ph") {
                    val u = nodePotentialMap["${el.id}:0"] ?: 0
                    val v = nodePotentialMap["${el.id}:1"] ?: 0
                    val w = nodePotentialMap["${el.id}:2"] ?: 0

                    val hasL1_u = (u and Potential.L1.flag) != 0
                    val hasL2_u = (u and Potential.L2.flag) != 0
                    val hasL3_u = (u and Potential.L3.flag) != 0

                    val hasL1_v = (v and Potential.L1.flag) != 0
                    val hasL2_v = (v and Potential.L2.flag) != 0
                    val hasL3_v = (v and Potential.L3.flag) != 0

                    val hasL1_w = (w and Potential.L1.flag) != 0
                    val hasL2_w = (w and Potential.L2.flag) != 0
                    val hasL3_w = (w and Potential.L3.flag) != 0
                    val all3PhFlags = (Potential.L1.flag or Potential.L2.flag or Potential.L3.flag)
                    val is3Ph = (u != 0 && v != 0 && w != 0 && ((u or v or w) and all3PhFlags) == all3PhFlags)
                    val isReverse = (hasL3_u && hasL2_v && hasL1_w) || (hasL1_u && hasL3_v && hasL2_w) || (hasL2_u && hasL1_v && hasL3_w)
                    val frame = when {
                        !is3Ph -> 0
                        isReverse -> 2 // Reverse rotation (CCW)
                        else -> 1      // Forward rotation (CW)
                    }
                    elementFrames[el.id] = frame
                } else if (el.svgData == "motorStarDelta" || el.svgData == "motorDhalander" || el.svgData == "motorTwoSpeeds") {
                    val u1 = nodePotentialMap["${el.id}:0"] ?: 0
                    val v1 = nodePotentialMap["${el.id}:1"] ?: 0
                    val w1 = nodePotentialMap["${el.id}:2"] ?: 0

                    val b3 = nodePotentialMap["${el.id}:3"] ?: 0 // motorStarDelta: W2 | Dahlander/2Speed: U2
                    val b4 = nodePotentialMap["${el.id}:4"] ?: 0 // motorStarDelta: U2 | Dahlander/2Speed: V2
                    val b5 = nodePotentialMap["${el.id}:5"] ?: 0 // motorStarDelta: V2 | Dahlander/2Speed: W2

                    val all3PhFlags = (Potential.L1.flag or Potential.L2.flag or Potential.L3.flag)
                    val is3Ph_set1 = (u1 != 0 && v1 != 0 && w1 != 0 && ((u1 or v1 or w1) and all3PhFlags) == all3PhFlags)
                    val is3Ph_set2 = (b3 != 0 && b4 != 0 && b5 != 0 && ((b3 or b4 or b5) and all3PhFlags) == all3PhFlags)

                    fun isReverseSeq(ua: Int, va: Int, wa: Int): Boolean {
                        val l1u = (ua and Potential.L1.flag) != 0
                        val l2u = (ua and Potential.L2.flag) != 0
                        val l3u = (ua and Potential.L3.flag) != 0
                        val l1v = (va and Potential.L1.flag) != 0
                        val l2v = (va and Potential.L2.flag) != 0
                        val l3v = (va and Potential.L3.flag) != 0
                        val l1w = (wa and Potential.L1.flag) != 0
                        val l2w = (wa and Potential.L2.flag) != 0
                        val l3w = (wa and Potential.L3.flag) != 0
                        return (l3u && l2v && l1w) || (l1u && l3v && l2w) || (l2u && l1v && l3w)
                    }

                    var frame = 0
                    if (el.svgData == "motorStarDelta") {
                        val net3 = nodeToNetId["${el.id}:3"]
                        val net4 = nodeToNetId["${el.id}:4"]
                        val net5 = nodeToNetId["${el.id}:5"]
                        val isStarBridge = (net3 != null && net3 == net4 && net4 == net5)
                        // Validasi Delta: tiap lilitan (U1-U2, V1-V2, W1-W2) wajib memiliki beda fasa!
                        val u1Ph = u1 and all3PhFlags
                        val v1Ph = v1 and all3PhFlags
                        val w1Ph = w1 and all3PhFlags
                        val w2Ph = b3 and all3PhFlags
                        val u2Ph = b4 and all3PhFlags
                        val v2Ph = b5 and all3PhFlags
                        val isValidDelta = is3Ph_set2 && (u1Ph != u2Ph) && (v1Ph != v2Ph) && (w1Ph != w2Ph)
                        if (is3Ph_set1 && !shortCircuit) {
                            val rev = isReverseSeq(u1, v1, w1)
                            when {
                                isValidDelta -> frame = if (rev) 4 else 3 // Delta (Δ) CW/CCW
                                isStarBridge -> frame = if (rev) 2 else 1 // Star (Y) CW/CCW
                            }
                        }
                    } else {
                        // motorDhalander & motorTwoSpeeds:
                        // Frame 1/2 = Speed 1 (CW/CCW, tampil angka "1")
                        // Frame 3/4 = Speed 2 (CW/CCW, tampil angka "2", putaran lebih cepat)
                        // Frame 5 = Konflik 2 belitan aktif bersamaan (merah peringatan)
                        if (!shortCircuit) {
                            when {
                                is3Ph_set1 && is3Ph_set2 -> frame = 5
                                is3Ph_set1 -> frame = if (isReverseSeq(u1, v1, w1)) 2 else 1
                                is3Ph_set2 -> frame = if (isReverseSeq(b3, b4, b5)) 4 else 3
                                else -> frame = 0
                            }
                        }
                    }
                    elementFrames[el.id] = frame
                } else if (el.svgData == "generator3ph") {
                    val p0 = nodePotentialMap["${el.id}:0"] ?: 0
                    elementFrames[el.id] = if (p0 != 0) 1 else 0
                }
            }
        }

        // Helper to check if a terminal pin belongs to an OPEN switch contact
        fun isOpenSwitchContact(elemId: Int, nodeIdx: Int): Boolean {
            val el = elements.find { it.id == elemId } ?: return false
            val def = NativeComponentRegistry.get(el.svgData) ?: return false
            val cat = def.category
            val isSwitchingElem = cat == "switches" || cat == "contacts" || cat == "breakers" ||
                    el.svgData.startsWith("switch") || el.svgData.startsWith("pushbutton")
            if (!isSwitchingElem) return false

            val pinKey = "$elemId:$nodeIdx"
            val internalNeighbors = adj[pinKey]?.filter { it.startsWith("$elemId:") } ?: emptyList()
            return internalNeighbors.isEmpty()
        }

        // Map Wire Potentials for Rendering (Termasuk simpul aktif rangkaian seri dengan proteksi cabang terbuka)
        for (c in connectors) {
            val n1Key = "${c.elem1Id}:${c.node1Idx}"
            val n2Key = "${c.elem2Id}:${c.node2Idx}"
            var pot1 = nodePotentialMap[n1Key] ?: 0
            var pot2 = nodePotentialMap[n2Key] ?: 0

            val isPin1OpenSwitch = isOpenSwitchContact(c.elem1Id, c.node1Idx)
            val isPin2OpenSwitch = isOpenSwitchContact(c.elem2Id, c.node2Idx)

            // Cabang buntu yang menempel pada kontak saklar terbuka tidak boleh dialiri tegangan
            var isOpenDeadBranch = false
            if (isPin1OpenSwitch) {
                val otherPot = nodePotentialMap[n2Key] ?: 0
                val otherEl = elements.find { it.id == c.elem2Id }
                val otherIsActiveLoad = otherEl != null && (seriesEnergizedLoads.contains(otherEl.id))
                if (otherPot == 0 && !otherIsActiveLoad) {
                    isOpenDeadBranch = true
                }
            }
            if (isPin2OpenSwitch) {
                val otherPot = nodePotentialMap[n1Key] ?: 0
                val otherEl = elements.find { it.id == c.elem1Id }
                val otherIsActiveLoad = otherEl != null && (seriesEnergizedLoads.contains(otherEl.id))
                if (otherPot == 0 && !otherIsActiveLoad) {
                    isOpenDeadBranch = true
                }
            }

            if (isOpenDeadBranch) {
                wirePotentials[c.id] = Potential.NONE
                continue
            }

            if (pot1 == 0) {
                val net1 = nodeToNetId[n1Key]
                if (net1 != null && seriesActiveNets.containsKey(net1)) {
                    pot1 = seriesActiveNets[net1] ?: 0
                }
            }
            if (pot2 == 0) {
                val net2 = nodeToNetId[n2Key]
                if (net2 != null && seriesActiveNets.containsKey(net2)) {
                    pot2 = seriesActiveNets[net2] ?: 0
                }
            }

            val combined = pot1 or pot2
            wirePotentials[c.id] = Potential.fromFlag(combined)
        }

        // Identify Connectors Carrying Real Current Flow (Closed Circuit)
        val flowingConnectors = mutableSetOf<Int>()
        val activeLoadIds = mutableSetOf<Int>()
        for (el in elements) {
            val frame = elementFrames[el.id] ?: 0
            val isEnergized = timerStates[el.id]?.energized == true || frame == 1
            if ((el.svgData.startsWith("coil") || el.svgData == "flasher") && isEnergized) activeLoadIds.add(el.id)
            else if ((el.svgData == "lamp" || el.svgData == "lampBlink" || el.svgData == "buzzer" || el.svgData == "siren" || el.svgData == "ring") && isEnergized) activeLoadIds.add(el.id)
            else if (el.svgData.startsWith("motor") && frame in 1..4) activeLoadIds.add(el.id)
        }

        if (activeLoadIds.isNotEmpty() && !shortCircuit) {
            val sourceNodes = mutableSetOf<String>()
            val sinkNodes = mutableSetOf<String>()
            val acPhaseNodes = mutableMapOf<String, MutableSet<String>>()

            for (el in elements) {
                when (el.svgData) {
                    "sourceL1" -> {
                        sourceNodes.add("${el.id}:0")
                        acPhaseNodes.getOrPut("L1") { mutableSetOf() }.add("${el.id}:0")
                    }
                    "sourceL2" -> {
                        sourceNodes.add("${el.id}:0")
                        acPhaseNodes.getOrPut("L2") { mutableSetOf() }.add("${el.id}:0")
                    }
                    "sourceL3" -> {
                        sourceNodes.add("${el.id}:0")
                        acPhaseNodes.getOrPut("L3") { mutableSetOf() }.add("${el.id}:0")
                    }
                    "sourceL1L2L3" -> {
                        sourceNodes.add("${el.id}:0")
                        sourceNodes.add("${el.id}:1")
                        sourceNodes.add("${el.id}:2")
                        acPhaseNodes.getOrPut("L1") { mutableSetOf() }.add("${el.id}:0")
                        acPhaseNodes.getOrPut("L2") { mutableSetOf() }.add("${el.id}:1")
                        acPhaseNodes.getOrPut("L3") { mutableSetOf() }.add("${el.id}:2")
                    }
                    "sourceL1L2L3N" -> {
                        sinkNodes.add("${el.id}:0")
                        sourceNodes.add("${el.id}:1")
                        sourceNodes.add("${el.id}:2")
                        sourceNodes.add("${el.id}:3")
                        acPhaseNodes.getOrPut("L1") { mutableSetOf() }.add("${el.id}:1")
                        acPhaseNodes.getOrPut("L2") { mutableSetOf() }.add("${el.id}:2")
                        acPhaseNodes.getOrPut("L3") { mutableSetOf() }.add("${el.id}:3")
                    }
                    "generator3ph" -> {
                        sourceNodes.add("${el.id}:0")
                        sourceNodes.add("${el.id}:1")
                        sourceNodes.add("${el.id}:2")
                        sinkNodes.add("${el.id}:3")
                        acPhaseNodes.getOrPut("L1") { mutableSetOf() }.add("${el.id}:0")
                        acPhaseNodes.getOrPut("L2") { mutableSetOf() }.add("${el.id}:1")
                        acPhaseNodes.getOrPut("L3") { mutableSetOf() }.add("${el.id}:2")
                    }
                    "sourceN", "sourcePE", "sourceGnd", "junctionPE" -> sinkNodes.add("${el.id}:0")
                    "sourcePlus" -> sourceNodes.add("${el.id}:0")
                    "sourceNegative" -> sinkNodes.add("${el.id}:0")
                    "battery" -> {
                        sourceNodes.add("${el.id}:0")
                        sinkNodes.add("${el.id}:1")
                    }
                    "powerSupply2" -> {
                        sourceNodes.add("${el.id}:1")
                        sinkNodes.add("${el.id}:3")
                    }
                    "powerSupply" -> {
                        sourceNodes.add("${el.id}:1")
                        sourceNodes.add("${el.id}:3")
                        sinkNodes.add("${el.id}:5")
                        sinkNodes.add("${el.id}:7")
                        sinkNodes.add("${el.id}:6")
                    }
                    else -> {
                        if (el.svgData.startsWith("source")) {
                            val def = NativeComponentRegistry.get(el.svgData)
                            val nodeCount = def?.nodes?.size ?: 1
                            for (i in 0 until nodeCount) {
                                sourceNodes.add("${el.id}:$i")
                            }
                        }
                    }
                }
            }

            // Map pair of nodes -> Connector ID
            val connMap = mutableMapOf<Pair<String, String>, MutableList<Int>>()
            for (c in connectors) {
                val n1 = "${c.elem1Id}:${c.node1Idx}"
                val n2 = "${c.elem2Id}:${c.node2Idx}"
                connMap.getOrPut(Pair(n1, n2)) { mutableListOf() }.add(c.id)
                connMap.getOrPut(Pair(n2, n1)) { mutableListOf() }.add(c.id)
            }

            // Kawat perantara rangkaian seri (antara flasher, saklar, dan lampu)
            for (c in connectors) {
                val n1 = "${c.elem1Id}:${c.node1Idx}"
                val n2 = "${c.elem2Id}:${c.node2Idx}"
                val net1 = nodeToNetId[n1]
                val net2 = nodeToNetId[n2]

                val isPin1OpenSwitch = isOpenSwitchContact(c.elem1Id, c.node1Idx)
                val isPin2OpenSwitch = isOpenSwitchContact(c.elem2Id, c.node2Idx)
                // Kawat yang menempel pada kontak saklar terbuka tidak mengalirkan arus
                if (isPin1OpenSwitch || isPin2OpenSwitch) {
                    continue
                }

                if (net1 != null && seriesActiveNets.containsKey(net1) && net1 == net2) {
                    flowingConnectors.add(c.id)
                }
                if (activeLoadIds.contains(c.elem1Id) && activeLoadIds.contains(c.elem2Id)) {
                    flowingConnectors.add(c.id)
                }
            }

            // Supply & Return Pins untuk beban aktif (termasuk flasher)
            val activeLoadSupplyPins = mutableSetOf<String>()
            val activeLoadReturnPins = mutableSetOf<String>()

            for (lid in activeLoadIds) {
                val el = elements.find { it.id == lid } ?: continue
                val def = NativeComponentRegistry.get(el.svgData) ?: continue
                if (el.svgData.startsWith("motor") && def.nodes.size >= 3) {
                    for (i in 0 until def.nodes.size) {
                        activeLoadSupplyPins.add("$lid:$i")
                        activeLoadReturnPins.add("$lid:$i")
                    }
                } else if (def.nodes.size >= 2) {
                    val p0 = nodePotentialMap["$lid:0"] ?: 0
                    val p1 = nodePotentialMap["$lid:1"] ?: 0
                    val hasPos0 = (p0 and (Potential.L1.flag or Potential.L2.flag or Potential.L3.flag or Potential.DC_P.flag)) != 0
                    val hasPos1 = (p1 and (Potential.L1.flag or Potential.L2.flag or Potential.L3.flag or Potential.DC_P.flag)) != 0
                    val hasNeg0 = (p0 and (Potential.N.flag or Potential.DC_N.flag)) != 0
                    val hasNeg1 = (p1 and (Potential.N.flag or Potential.DC_N.flag)) != 0

                    if (hasPos0 || hasNeg1) {
                        activeLoadSupplyPins.add("$lid:0")
                        activeLoadReturnPins.add("$lid:1")
                    } else if (hasPos1 || hasNeg0) {
                        activeLoadSupplyPins.add("$lid:1")
                        activeLoadReturnPins.add("$lid:0")
                    } else {
                        activeLoadSupplyPins.add("$lid:0")
                        activeLoadReturnPins.add("$lid:1")
                    }
                }
            }

            // Topological Leaf-Peeling Subgraph Extractor for source-to-load and load-to-sink flow paths
            fun extractFlowing(termA: Set<String>, termB: Set<String>) {
                val visitedGlobal = mutableSetOf<String>()
                for (start in termA) {
                    if (visitedGlobal.contains(start)) continue
                    val compNodes = mutableSetOf<String>()
                    val q = ArrayDeque<String>()
                    q.add(start)
                    compNodes.add(start)
                    visitedGlobal.add(start)

                    while (q.isNotEmpty()) {
                        val curr = q.removeFirst()
                        for (nxt in (adj[curr] ?: emptySet())) {
                            val nxtElemId = nxt.substringBefore(':').toIntOrNull() ?: -1
                            val nxtEl = elements.find { it.id == nxtElemId }
                            val isPassiveLoad = nxtEl != null && nxtEl.svgData in PASSIVE_2TERM_LOADS
                            if (isPassiveLoad && !activeLoadIds.contains(nxtElemId)) {
                                continue
                            }
                            if (compNodes.add(nxt)) {
                                visitedGlobal.add(nxt)
                                q.add(nxt)
                            }
                        }
                    }

                    val startsInComp = compNodes.intersect(termA)
                    val endsInComp = compNodes.intersect(termB)
                    if (startsInComp.isEmpty() || endsInComp.isEmpty()) continue

                    val protectedTerminals = startsInComp union endsInComp
                    val survivingNodes = compNodes.toMutableSet()
                    val degMap = mutableMapOf<String, Int>()

                    for (u in survivingNodes) {
                        val nbrs = (adj[u] ?: emptySet()).filter { it in survivingNodes }
                        degMap[u] = nbrs.size
                    }

                    val leafQ = ArrayDeque<String>()
                    for ((u, deg) in degMap) {
                        if (deg <= 1 && u !in protectedTerminals) {
                            leafQ.add(u)
                        }
                    }

                    while (leafQ.isNotEmpty()) {
                        val leaf = leafQ.removeFirst()
                        if (!survivingNodes.remove(leaf)) continue
                        val nbrs = (adj[leaf] ?: emptySet()).filter { it in survivingNodes }
                        for (nbr in nbrs) {
                            val newDeg = (degMap[nbr] ?: 1) - 1
                            degMap[nbr] = newDeg
                            if (newDeg <= 1 && nbr !in protectedTerminals) {
                                leafQ.add(nbr)
                            }
                        }
                    }

                    for (u in survivingNodes) {
                        for (v in (adj[u] ?: emptySet())) {
                            if (v in survivingNodes) {
                                connMap[Pair(u, v)]?.let { flowingConnectors.addAll(it) }
                            }
                        }
                    }
                }
            }

            if (activeLoadSupplyPins.isNotEmpty()) {
                extractFlowing(sourceNodes, activeLoadSupplyPins)
            }
            if (activeLoadReturnPins.isNotEmpty()) {
                extractFlowing(activeLoadReturnPins, sinkNodes)
            }
            if (activeLoadReturnPins.isNotEmpty() && sourceNodes.isNotEmpty()) {
                extractFlowing(activeLoadReturnPins, sourceNodes)
            }

            // Extract AC Phase-to-Phase closed loops
            val phases = acPhaseNodes.keys.toList()
            for (i in phases.indices) {
                val pA = acPhaseNodes[phases[i]] ?: continue
                val otherNodes = mutableSetOf<String>()
                for (j in phases.indices) {
                    if (i != j) {
                        acPhaseNodes[phases[j]]?.let { otherNodes.addAll(it) }
                    }
                }
                otherNodes.addAll(sinkNodes)
                if (otherNodes.isNotEmpty()) {
                    extractFlowing(pA, otherNodes)
                }
            }
        }

        val isFuseBlown = fuseBlownElements.isNotEmpty() && !shortCircuit
        val trippedBreakerTags = trippedBreakerElements.mapNotNull { id ->
            elemMap[id]?.let { it.tag.ifBlank { it.svgData } }
        }
        val trippedEarthLeakageTags = trippedEarthLeakageElements.mapNotNull { id ->
            elemMap[id]?.let { it.tag.ifBlank { it.svgData } }
        }
        return SimResult(
            elementFrames = elementFrames,
            wirePotentials = wirePotentials,
            shortCircuit = shortCircuit,
            isDcOverheat = isDcOverheat,
            isEarthLeakage = isEarthLeakage,
            isFuseBlown = isFuseBlown,
            newlyBlownFuses = newlyBlownFuses,
            trippedBreakerTags = trippedBreakerTags,
            trippedEarthLeakageTags = trippedEarthLeakageTags,
            newlyTrippedBreakers = newlyTrippedBreakers,
            activeCoils = activeCoilTags,
            flowingConnectors = flowingConnectors,
            timerStates = timerStates.toMap()
        )
    }
}
