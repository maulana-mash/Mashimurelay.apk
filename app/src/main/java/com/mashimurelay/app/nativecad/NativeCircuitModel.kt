package com.mashimurelay.app.nativecad

import android.graphics.Color
import android.graphics.PointF

enum class Potential(val flag: Int, val color: Int) {
    NONE(0, Color.parseColor("#4b5563")),
    L1(1, Color.parseColor("#ef4444")),      // Merah / Coklat Fasa 1
    L2(2, Color.parseColor("#f59e0b")),      // Kuning / Hitam Fasa 2
    L3(4, Color.parseColor("#10b981")),      // Abu-abu / Hijau Fasa 3
    N(8, Color.parseColor("#3b82f6")),       // Biru Netral
    PE(16, Color.parseColor("#22c55e")),     // Hijau-Kuning Ground
    GND(32, Color.parseColor("#059669")),    // Ground Chasis
    DC_P(64, Color.parseColor("#f43f5e")),   // DC Positif
    DC_N(128, Color.parseColor("#38bdf8")),  // DC Negatif
    DC_OVERHEAT(192, Color.parseColor("#dc2626")); // DC Overheat / Korslet Membara

    companion object {
        fun fromFlag(flag: Int): Potential {
            if ((flag and DC_P.flag) != 0 && (flag and DC_N.flag) != 0) {
                return DC_OVERHEAT
            }
            return entries.find { it.flag == flag } ?: NONE
        }
    }
}

data class TerminalNode(
    val index: Int,
    val relX: Float,
    val relY: Float,
    val tag: String = "",
    val direction: Int = 0 // 0=North/Up, 1=East/Right, 2=South/Down, 3=West/Left
)

data class AssignmentKit(
    val enabled: Boolean = false,
    val name: String = "",
    val allowedComponents: List<String> = emptyList()
)

data class NativeElement(
    val id: Int,
    var svgData: String,
    var tag: String,
    var comment: String = "",
    var x: Float,
    var y: Float,
    var rotation: Int = 0, // Canonical quadrant: 0 (0°), 1 (90°), 2 (180°), 3 (270°)
    var frameIndex: Int = 0,
    var terminalTags: MutableList<String> = mutableListOf(),
    var presetTime: Float = 2.0f,
    var lampColor: String = "yellow",
    var fontSize: Float = 24f,
    var textColor: String = "#93c5fd",
    var locked: Boolean = false,
    var teacherLocked: Boolean = false
) {
    val canonicalRotation: Int
        get() {
            val r = rotation
            return if (kotlin.math.abs(r) >= 4 && r % 90 == 0) {
                ((r / 90) % 4 + 4) % 4
            } else {
                (r % 4 + 4) % 4
            }
        }

    val rotationDegrees: Float
        get() = canonicalRotation * 90f

    fun getWorldNodePos(nodeIndex: Int, nodes: List<TerminalNode>): PointF {
        val node = nodes.getOrNull(nodeIndex) ?: return PointF(x, y)
        val q = canonicalRotation
        val rotX = when (q) {
            0 -> node.relX
            1 -> -node.relY
            2 -> -node.relX
            3 -> node.relY
            else -> node.relX
        }
        val rotY = when (q) {
            0 -> node.relY
            1 -> node.relX
            2 -> -node.relY
            3 -> -node.relX
            else -> node.relY
        }
        return PointF(x + rotX, y + rotY)
    }

    fun getWorldNodeDirection(nodeIndex: Int, nodes: List<TerminalNode>): Int {
        val node = nodes.getOrNull(nodeIndex) ?: return 0
        return ((node.direction + canonicalRotation) % 4 + 4) % 4
    }

    fun getTerminalTag(nodeIndex: Int, defaultTag: String): String {
        val custom = terminalTags.getOrNull(nodeIndex)
        return if (!custom.isNullOrEmpty()) custom else defaultTag
    }
}

enum class RouteOrientation {
    AUTO,
    V_H_V,
    H_V_H,
    BYPASS_U_HORIZONTAL,
    BYPASS_U_VERTICAL
}

data class WireSegment(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val isHorizontal: Boolean,
    val isDraggable: Boolean
) {
    val midX: Float get() = (startX + endX) * 0.5f
    val midY: Float get() = (startY + endY) * 0.5f
    val length: Float get() = kotlin.math.hypot(endX - startX, endY - startY)
}

data class NativeConnector(
    val id: Int,
    val elem1Id: Int,
    val node1Idx: Int,
    val elem2Id: Int,
    val node2Idx: Int,
    var routeOrientation: RouteOrientation = RouteOrientation.AUTO,
    var customMidPos: Float? = null,
    var mStart: Float = 0f,
    var mCenter: Float = 0f,
    var mEnd: Float = 0f,
    var stubLength: Float = 30f
) {
    /**
     * Exact 1:1 bi-directional Manhattan Orthogonal Wire Router matching Web router.js.
     * Computes clean orthogonal wire path taking terminal normal vectors and relative offsets into account.
     */
    fun computeSegments(
        pA: PointF,
        dirA: Int,
        pB: PointF,
        dirB: Int,
        gridSize: Float = 30f
    ): List<WireSegment> {
        val normDirA = ((dirA % 4) + 4) % 4
        val normDirB = ((dirB % 4) + 4) % 4

        fun getDirVec(dir: Int): PointF = when (dir) {
            0 -> PointF(0f, -1f) // North (Up)
            1 -> PointF(1f, 0f)  // East (Right)
            2 -> PointF(0f, 1f)  // South (Down)
            3 -> PointF(-1f, 0f) // West (Left)
            else -> PointF(0f, -1f)
        }

        fun snap(v: Float): Float = kotlin.math.round(v / gridSize) * gridSize

        val p0 = PointF(snap(pA.x), snap(pA.y))
        val p5 = PointF(snap(pB.x), snap(pB.y))

        if (kotlin.math.abs(p0.x - p5.x) < 0.5f && kotlin.math.abs(p0.y - p5.y) < 0.5f) {
            return emptyList()
        }

        val uA = getDirVec(normDirA)
        val uB = getDirVec(normDirB)

        val effectiveCenter = if (customMidPos != null && mCenter == 0f) customMidPos!! else mCenter

        // Straight connection when terminals face each other directly
        if (kotlin.math.abs(p0.x - p5.x) < 0.5f && uA.y != 0f && uB.y != 0f && kotlin.math.abs(effectiveCenter) < 0.5f && (p5.y - p0.y) * uA.y > 0f) {
            return listOf(WireSegment(p0.x, p0.y, p5.x, p5.y, isHorizontal = false, isDraggable = false))
        }
        if (kotlin.math.abs(p0.y - p5.y) < 0.5f && uA.x != 0f && uB.x != 0f && kotlin.math.abs(effectiveCenter) < 0.5f && (p5.x - p0.x) * uA.x > 0f) {
            return listOf(WireSegment(p0.x, p0.y, p5.x, p5.y, isHorizontal = true, isDraggable = false))
        }

        val dStart = kotlin.math.max(gridSize, gridSize + snap(mStart))
        val dEnd = kotlin.math.max(gridSize, gridSize + snap(mEnd))
        val dCenter = snap(effectiveCenter)

        val p1 = PointF(p0.x + uA.x * dStart, p0.y + uA.y * dStart)
        val p4 = PointF(p5.x + uB.x * dEnd, p5.y + uB.y * dEnd)

        val isAVertical = (uA.y != 0f)
        val isBVertical = (uB.y != 0f)

        val p2: PointF
        val p3: PointF

        if (isAVertical && isBVertical) {
            // Both terminals vertical (North/South) -> Bridge via horizontal segment
            val midY = snap((p1.y + p4.y) / 2f) + dCenter
            p2 = PointF(p1.x, midY)
            p3 = PointF(p4.x, midY)
        } else if (!isAVertical && !isBVertical) {
            // Both terminals horizontal (East/West) -> Bridge via vertical segment
            val midX = snap((p1.x + p4.x) / 2f) + dCenter
            p2 = PointF(midX, p1.y)
            p3 = PointF(midX, p4.y)
        } else if (isAVertical && !isBVertical) {
            // Vertical -> Horizontal
            val bendY = p4.y + dCenter
            p2 = PointF(p1.x, bendY)
            p3 = PointF(p4.x, bendY)
        } else {
            // Horizontal -> Vertical
            val bendX = p4.x + dCenter
            p2 = PointF(bendX, p1.y)
            p3 = PointF(bendX, p4.y)
        }

        val rawPoints = listOf(p0, p1, p2, p3, p4, p5)
        val clean = mutableListOf<PointF>()

        for (pt in rawPoints) {
            if (clean.isNotEmpty()) {
                val last = clean.last()
                if (kotlin.math.abs(last.x - pt.x) < 0.5f && kotlin.math.abs(last.y - pt.y) < 0.5f) continue
            }
            clean.add(pt)
            // Collinear pruning
            while (clean.size >= 3) {
                val a = clean[clean.size - 3]
                val b = clean[clean.size - 2]
                val c = clean[clean.size - 1]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                if (kotlin.math.abs(cross) < 0.5f) {
                    clean.removeAt(clean.size - 2)
                } else {
                    break
                }
            }
        }

        if (clean.size < 2) return emptyList()

        val segments = mutableListOf<WireSegment>()
        val midSegIdx = (clean.size - 1) / 2

        for (i in 0 until clean.size - 1) {
            val a = clean[i]
            val b = clean[i + 1]
            val isHoriz = kotlin.math.abs(a.y - b.y) < 0.5f
            val isDraggable = (i == midSegIdx && clean.size > 2)
            segments.add(
                WireSegment(
                    startX = a.x,
                    startY = a.y,
                    endX = b.x,
                    endY = b.y,
                    isHorizontal = isHoriz,
                    isDraggable = isDraggable
                )
            )
        }
        return segments
    }

    /**
     * Overloaded computeSegments fallback for callers without pin directions.
     */
    fun computeSegments(p1: PointF, p2: PointF, gridSize: Float = 30f): List<WireSegment> {
        val dx = kotlin.math.abs(p2.x - p1.x)
        val dy = kotlin.math.abs(p2.y - p1.y)
        val dir1 = if (dy >= dx) 2 else 1
        val dir2 = if (dy >= dx) 0 else 3
        return computeSegments(p1, dir1, p2, dir2, gridSize)
    }
}

data class TimerState(
    var elapsedMs: Long = 0L,
    var elapsedOffMs: Long = 0L,
    var presetMs: Long = 2000L,
    var presetOffMs: Long = 2000L,
    var prevEnergized: Boolean = false,
    var outputState: Boolean = false,
    var isTiming: Boolean = false,
    var energized: Boolean = false
)

data class SimResult(
    val elementFrames: Map<Int, Int>,
    val wirePotentials: Map<Int, Potential>,
    val shortCircuit: Boolean,
    val isDcOverheat: Boolean = false,
    val isFuseBlown: Boolean = false,
    val newlyBlownFuses: Set<Int> = emptySet(),
    val activeCoils: Set<String> = emptySet(),
    val flowingConnectors: Set<Int> = emptySet(),
    val timerStates: Map<Int, TimerState> = emptyMap()
)
