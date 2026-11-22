package com.mashimurelay.app.nativecad

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.hypot

class NativeCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), android.view.Choreographer.FrameCallback {

    interface OnCanvasInteractionListener {
        fun onElementSelected(element: NativeElement?)
        fun onConnectorSelected(connector: NativeConnector?)
        fun onSwitchClicked(element: NativeElement)
        fun onShortCircuit(isShort: Boolean, isDcOverheat: Boolean = false)
        fun onFuseBlown(isBlown: Boolean) {}
        fun onBreakerTripped(shortTags: List<String>, earthTags: List<String>, isEarthLeakageActive: Boolean) {}
        fun onUndoRedoStateChanged(canUndo: Boolean, canRedo: Boolean) {}
        fun onCircuitModified() {}
        fun onTeacherLockedMoveAttempt() {}
        fun onLockedMoveAttempt() {}
    }

    var interactionListener: OnCanvasInteractionListener? = null

    // Circuit Data
    val elements = mutableListOf<NativeElement>()
    val connectors = mutableListOf<NativeConnector>()
    val engine = NativeSimulationEngine()
    val soundManager = SoundManager(context)
    var simResult: SimResult? = null
    var isSimulating = false
    private var isLoopRunning = false
    private var lastFrameTimeNanos: Long = 0L

    // Undo / Redo History Stack
    data class CircuitSnapshot(
        val elements: List<NativeElement>,
        val connectors: List<NativeConnector>
    )

    private val undoStack = ArrayDeque<CircuitSnapshot>()
    private val redoStack = ArrayDeque<CircuitSnapshot>()
    private val maxHistory = 35
    private var pendingDragSnapshot: CircuitSnapshot? = null

    fun createSnapshot(): CircuitSnapshot {
        return CircuitSnapshot(
            elements = elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) },
            connectors = connectors.map { it.copy() }
        )
    }

    fun pushUndoState() {
        if (isSimulating) return
        val snapshot = createSnapshot()
        undoStack.addLast(snapshot)
        if (undoStack.size > maxHistory) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        notifyUndoRedoState()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo() {
        if (isSimulating || undoStack.isEmpty()) return
        val current = CircuitSnapshot(
            elements = elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) },
            connectors = connectors.map { it.copy() }
        )
        redoStack.addLast(current)

        val previous = undoStack.removeLast()
        elements.clear()
        elements.addAll(previous.elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) })
        connectors.clear()
        connectors.addAll(previous.connectors.map { it.copy() })

        selectedElementId = null
        selectedConnectorId = null
        interactionListener?.onElementSelected(null)
        interactionListener?.onConnectorSelected(null)
        invalidate()
        notifyUndoRedoState()
        interactionListener?.onCircuitModified()
    }

    fun redo() {
        if (isSimulating || redoStack.isEmpty()) return
        val current = CircuitSnapshot(
            elements = elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) },
            connectors = connectors.map { it.copy() }
        )
        undoStack.addLast(current)

        val next = redoStack.removeLast()
        elements.clear()
        elements.addAll(next.elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) })
        connectors.clear()
        connectors.addAll(next.connectors.map { it.copy() })

        selectedElementId = null
        selectedConnectorId = null
        interactionListener?.onElementSelected(null)
        interactionListener?.onConnectorSelected(null)
        invalidate()
        notifyUndoRedoState()
        interactionListener?.onCircuitModified()
    }

    fun clearUndoHistory() {
        undoStack.clear()
        redoStack.clear()
        notifyUndoRedoState()
    }

    fun notifyUndoRedoState() {
        interactionListener?.onUndoRedoStateChanged(canUndo(), canRedo())
    }

    // Transform State
    var zoom = 1.0f
    var panX = 60f
    var panY = 80f
    var GRID_SIZE = 30f // Standard CAD Grid (Adjustable: 20px, 30px, 40px)
    private var zoomAnimator: ValueAnimator? = null
    private var pendingTargetZoom: Float = 1.0f

    // Professional CAD Settings
    var isGridSnapEnabled = true
    var showCurrentFlowAnimation = true
    var showPinLabels = true
    var showComponentNames = true

    private var flowAnimPhase = 0f
    private val wireFlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND
    }

    fun captureBitmap(): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        draw(c)
        return bitmap
    }

    // Interaction State
    var selectedElementId: Int? = null
    var selectedConnectorId: Int? = null
    private var isDraggingWire = false
    private var activeDragConnector: NativeConnector? = null
    private var wireDragStartPos = PointF()
    private var activeWireStart: Pair<Int, Int>? = null // elemId to nodeIdx
    private var draggedElement: NativeElement? = null
    private var dragStartPos = PointF()
    private var currentTouchPos = PointF()
    private var activePressedButtonElement: NativeElement? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    // Paints
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2a324b")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.4f
    }

    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94a3b8")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val wireSelectedGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4000e5ff")
        strokeWidth = 7f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val wireDcOverheatGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#70dc2626") // Pijar panas merah membara DC Overheat
        strokeWidth = 9.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val wireDcOverheatOuterAuraPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#35ef4444") // Radiasi aura luar korslet
        strokeWidth = 18f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val wireHandleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00e5ff")
        style = Paint.Style.FILL
    }

    private val wireHandleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0f172a")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val selBoxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1838bdf8")
        style = Paint.Style.FILL
    }

    private val selBoxBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9938bdf8")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 6f), 0f)
    }

    private val selCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00e5ff")
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val selTagBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00e5ff")
        style = Paint.Style.FILL
    }

    private val selTagTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0f172a")
        textSize = 10f
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val elementPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38bdf8")
        textSize = 10.5f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e613131c")
        style = Paint.Style.FILL
    }

    private val tagBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0284c7")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val terminalLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#cbd5e1")
        textSize = 10f
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val pinBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#e60f111a")
        style = Paint.Style.FILL
    }
    private val pinBadgeRect = RectF()

    private val terminalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val snapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00e5ff")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    // Motor Real-Time 60fps Spinning Animation
    private var motorSpinAngle = 0f
    private val motorArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ef4444")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val motorArrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#ef4444")
        style = Paint.Style.FILL
    }

    // Lamp Custom Colors & Glowing Halo
    private val lampGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    private val lampDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Text Label CAD Component
    private val textCompPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#93c5fd")
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    // Performance Buffers & Reusable Objects (Zero-Allocation onDraw)
    private var gridPoints = FloatArray(16384)
    private val wireScratchPath = Path()
    private val wireHandleRect = RectF()
    private val flowDashIntervals = floatArrayOf(5f, 11f)
    private var hasActiveFlowAnimation = false
    private var hasActiveRunningMotor = false
    private var hasActiveBlinkingLamp = false
    private var hasActiveOverheatAnimation = false
    private val selBoxRect = RectF()
    private val selCornerPath = Path()
    private val selBadgeRect = RectF()
    private val motorOvalRect = RectF()
    private val motorHeadPath = Path()
    private val elementCommentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94a3b8")
        textSize = 9f
        textAlign = Paint.Align.CENTER
    }
    private val timerBadgeRect = RectF()
    private val timerBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#fbbf24")
        textSize = 9.5f
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
        textAlign = Paint.Align.CENTER
    }

    private val lockBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val lockBadgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val lockBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 9.5f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val lockBadgeRect = RectF()

    // Gesture Detectors
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            zoomAnimator?.cancel()
            pendingTargetZoom = zoom
            return draggedElement == null && !isDraggingWire && activeWireStart == null
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (draggedElement != null || isDraggingWire || activeWireStart != null) return false
            val factor = detector.scaleFactor
            val targetZoom = (zoom * factor).coerceIn(0.2f, 4.0f)
            val focusX = detector.focusX
            val focusY = detector.focusY

            panX = focusX - (focusX - panX) * (targetZoom / zoom)
            panY = focusY - (focusY - panY) * (targetZoom / zoom)
            zoom = targetZoom
            pendingTargetZoom = zoom
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (draggedElement == null && !isDraggingWire && activeWireStart == null && !scaleDetector.isInProgress) {
                zoomAnimator?.cancel()
                pendingTargetZoom = zoom
                panX -= distanceX
                panY -= distanceY
                invalidate()
                return true
            }
            return false
        }
    })

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        scaleDetector.isQuickScaleEnabled = false
        NativeComponentRegistry.loadFromAssets(context)
        isGridSnapEnabled = SettingsManager.isGridSnapEnabled(context)
        GRID_SIZE = SettingsManager.getGridSize(context)
        showCurrentFlowAnimation = SettingsManager.isCurrentFlowAnimEnabled(context)
        showPinLabels = SettingsManager.isPinLabelsEnabled(context)
        showComponentNames = SettingsManager.isComponentNamesEnabled(context)
    }

    fun startSimulation() {
        if (isSimulating) return
        isSimulating = true
        hasActiveBlinkingLamp = false
        val cur = elements.find { it.id == selectedElementId }
        val def = if (cur != null) NativeComponentRegistry.get(cur.svgData) else null
        val isPushButton = cur != null && NativeComponentRegistry.isPushButton(cur.svgData)
        val isSwitch = cur != null && !isPushButton && (def?.category == "switches" || def?.category == "protections")
        if (!isSwitch) {
            selectedElementId = null
        }
        activeWireStart = null
        draggedElement = null
        lastFrameTimeNanos = 0L
        stepSimulation(0L)
        startContinuousLoop()
    }

    fun stopSimulation() {
        isSimulating = false
        stopContinuousLoop()
        activePressedButtonElement = null
        hasActiveBlinkingLamp = false
        engine.reset(preserveFuses = true)
        simResult = null
        soundManager.stopAllLoops()
        interactionListener?.onShortCircuit(false, false)
        interactionListener?.onFuseBlown(false)
        interactionListener?.onBreakerTripped(emptyList(), emptyList(), false)
        invalidate()
    }

    private fun startContinuousLoop() {
        if (!isLoopRunning && isAttachedToWindow && isSimulating) {
            isLoopRunning = true
            lastFrameTimeNanos = 0L
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun stopContinuousLoop() {
        if (isLoopRunning) {
            isLoopRunning = false
            android.view.Choreographer.getInstance().removeFrameCallback(this)
        }
        lastFrameTimeNanos = 0L
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isSimulating || !isAttachedToWindow || visibility != VISIBLE) {
            stopContinuousLoop()
            return
        }

        val dtMs = if (lastFrameTimeNanos == 0L) {
            16L
        } else {
            val rawDeltaMs = (frameTimeNanos - lastFrameTimeNanos) / 1_000_000L
            rawDeltaMs.coerceIn(1L, 100L)
        }
        lastFrameTimeNanos = frameTimeNanos

        stepSimulation(dtMs)

        if (isLoopRunning) {
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun stepSimulation(dtMs: Long = 0L) {
        if (!isSimulating) return
        simResult = engine.step(elements, connectors, dtMs)
        soundManager.updateSimulationSounds(elements, simResult?.elementFrames, isSimulating)
        if (simResult?.newlyBlownFuses?.isNotEmpty() == true) {
            soundManager.playFuseBlownSound()
        }
        if (simResult?.newlyTrippedBreakers?.isNotEmpty() == true) {
            soundManager.playSwitchClick(this)
        }
        interactionListener?.onShortCircuit(simResult?.shortCircuit == true, simResult?.isDcOverheat == true)
        interactionListener?.onFuseBlown(simResult?.isFuseBlown == true)
        interactionListener?.onBreakerTripped(
            simResult?.trippedBreakerTags ?: emptyList(),
            simResult?.trippedEarthLeakageTags ?: emptyList(),
            simResult?.isEarthLeakage == true
        )
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isSimulating) {
            startContinuousLoop()
        }
    }

    override fun onDetachedFromWindow() {
        stopContinuousLoop()
        super.onDetachedFromWindow()
        soundManager.release()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isSimulating) {
            startContinuousLoop()
        } else {
            stopContinuousLoop()
        }
    }

    fun screenToWorld(sx: Float, sy: Float): PointF {
        return PointF((sx - panX) / zoom, (sy - panY) / zoom)
    }

    fun snap(value: Float): Float {
        return if (isGridSnapEnabled) Math.round(value / GRID_SIZE) * GRID_SIZE else value
    }

    fun fitToScreen() {
        zoomAnimator?.cancel()
        if (elements.isEmpty()) {
            panX = 60f
            panY = 80f
            zoom = 1f
            pendingTargetZoom = 1f
            invalidate()
            return
        }

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (el in elements) {
            val def = NativeComponentRegistry.get(el.svgData) ?: continue
            minX = minOf(minX, el.x - def.width / 2f)
            maxX = maxOf(maxX, el.x + def.width / 2f)
            minY = minOf(minY, el.y - def.height / 2f)
            maxY = maxOf(maxY, el.y + def.height / 2f)
        }

        val circuitW = (maxX - minX).coerceAtLeast(100f)
        val circuitH = (maxY - minY).coerceAtLeast(100f)

        val targetZoom = minOf((width - 80f) / circuitW, (height - 120f) / circuitH).coerceIn(0.4f, 2.0f)
        zoom = targetZoom
        pendingTargetZoom = targetZoom
        panX = (width - circuitW * zoom) / 2f - minX * zoom
        panY = (height - circuitH * zoom) / 2f - minY * zoom
        invalidate()
    }

    fun zoomIn() {
        val centerX = if (width > 0) width / 2f else resources.displayMetrics.widthPixels / 2f
        val centerY = if (height > 0) height / 2f else resources.displayMetrics.heightPixels / 2f
        val baseZoom = if (zoomAnimator?.isRunning == true) pendingTargetZoom else zoom
        val newTarget = (baseZoom * 1.25f).coerceIn(0.2f, 4.0f)
        pendingTargetZoom = newTarget
        animateZoomTo(newTarget, centerX, centerY)
    }

    fun zoomOut() {
        val centerX = if (width > 0) width / 2f else resources.displayMetrics.widthPixels / 2f
        val centerY = if (height > 0) height / 2f else resources.displayMetrics.heightPixels / 2f
        val baseZoom = if (zoomAnimator?.isRunning == true) pendingTargetZoom else zoom
        val newTarget = (baseZoom / 1.25f).coerceIn(0.2f, 4.0f)
        pendingTargetZoom = newTarget
        animateZoomTo(newTarget, centerX, centerY)
    }

    private fun animateZoomTo(targetZoom: Float, focusX: Float, focusY: Float) {
        if (targetZoom == zoom) return

        zoomAnimator?.cancel()

        val startZoom = zoom
        val startPanX = panX
        val startPanY = panY

        val endPanX = focusX - (focusX - startPanX) * (targetZoom / startZoom)
        val endPanY = focusY - (focusY - startPanY) * (targetZoom / startZoom)

        zoomAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                zoom = startZoom + (targetZoom - startZoom) * fraction
                panX = startPanX + (endPanX - startPanX) * fraction
                panY = startPanY + (endPanY - startPanY) * fraction
                invalidate()
            }
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Multi-touch pinch zoom & canvas pan HANYA aktif jika tidak sedang menyeret komponen atau kawat
        val isInteractingWithObject = (draggedElement != null || isDraggingWire || activeWireStart != null)
        if (!isInteractingWithObject || scaleDetector.isInProgress) {
            scaleDetector.onTouchEvent(event)
        }
        if (!isInteractingWithObject && !scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }

        val dynamicTolerance = maxOf(30f, (36f * resources.displayMetrics.density) / zoom)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                zoomAnimator?.cancel()
                pendingTargetZoom = zoom
                activePointerId = event.getPointerId(0)
                val worldPos = screenToWorld(event.x, event.y)
                currentTouchPos = worldPos

                // 1. Check Terminal Pins Tap (prioritas utama untuk penarikan kawat)
                val hitTerminal = findTerminalAt(worldPos.x, worldPos.y)
                if (hitTerminal != null && !isSimulating) {
                    val (elemId, nodeIdx) = hitTerminal
                    if (activeWireStart == null) {
                        activeWireStart = Pair(elemId, nodeIdx)
                    } else {
                        val (startElem, startNode) = activeWireStart!!
                        if (startElem == elemId) {
                            activeWireStart = null
                            invalidate()
                            return true
                        }
                        if (startElem != elemId || startNode != nodeIdx) {
                            val exists = connectors.any {
                                (it.elem1Id == startElem && it.node1Idx == startNode && it.elem2Id == elemId && it.node2Idx == nodeIdx) ||
                                (it.elem2Id == startElem && it.node2Idx == startNode && it.elem1Id == elemId && it.node1Idx == nodeIdx)
                            }
                            if (!exists) {
                                pushUndoState()
                                val nextConnId = (connectors.maxOfOrNull { it.id } ?: 0) + 1
                                connectors.add(NativeConnector(nextConnId, startElem, startNode, elemId, nodeIdx))
                                interactionListener?.onCircuitModified()
                            }
                        }
                        activeWireStart = null
                    }
                    invalidate()
                    return true
                }

                // 2. Prioritaskan Kawat yang Sedang Terpilih (jika ada) agar pegangan handle / segmen kawat langsung responsif
                if (!isSimulating && selectedConnectorId != null) {
                    val selWire = connectors.find { it.id == selectedConnectorId }
                    if (selWire != null && isPointNearConnector(selWire, worldPos.x, worldPos.y, dynamicTolerance)) {
                        isDraggingWire = true
                        activeDragConnector = selWire
                        pendingDragSnapshot = CircuitSnapshot(
                            elements = elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) },
                            connectors = connectors.map { it.copy() }
                        )
                        wireDragStartPos = PointF(worldPos.x, worldPos.y)
                        invalidate()
                        return true
                    }
                }

                // 3. Check Component Tap (Aktif baik di Mode EDIT maupun Mode RUN)
                val hitElement = findElementAt(worldPos.x, worldPos.y)
                if (hitElement != null) {
                    selectedElementId = hitElement.id
                    selectedConnectorId = null
                    interactionListener?.onConnectorSelected(null)

                    if (isSimulating) {
                        val def = NativeComponentRegistry.get(hitElement.svgData)
                        if (NativeComponentRegistry.isPushButton(hitElement.svgData)) {
                            activePressedButtonElement = hitElement
                            engine.setButtonPressed(hitElement.id, true)
                            interactionListener?.onElementSelected(hitElement)
                            stepSimulation()
                            invalidate()
                            return true
                        } else if (def?.category == "switches" || def?.category == "protections") {
                            engine.toggleSwitch(hitElement.id, hitElement.svgData)
                            soundManager.playSwitchClick(this)
                            stepSimulation()
                            interactionListener?.onSwitchClicked(hitElement)
                        } else {
                            // Komponen lainnya (lampu, motor, koil, kontak, dsb.) saat simulasi berjalan
                            interactionListener?.onElementSelected(hitElement)
                        }
                    } else {
                        if (hitElement.teacherLocked || hitElement.locked) {
                            draggedElement = null
                            interactionListener?.onElementSelected(hitElement)
                            if (hitElement.teacherLocked) {
                                interactionListener?.onTeacherLockedMoveAttempt()
                            } else {
                                interactionListener?.onLockedMoveAttempt()
                            }
                        } else {
                            pendingDragSnapshot = CircuitSnapshot(
                                elements = elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) },
                                connectors = connectors.map { it.copy() }
                            )
                            draggedElement = hitElement
                            dragStartPos = PointF(worldPos.x - hitElement.x, worldPos.y - hitElement.y)
                            interactionListener?.onElementSelected(hitElement)
                        }
                    }
                    invalidate()
                    return true
                }

                // 4. Check Wire Tap (Draggable Wire Segments)
                if (!isSimulating) {
                    val hitWire = hitTestConnector(worldPos.x, worldPos.y, dynamicTolerance)
                    if (hitWire != null) {
                        selectedConnectorId = hitWire.id
                        selectedElementId = null
                        isDraggingWire = true
                        activeDragConnector = hitWire
                        pendingDragSnapshot = CircuitSnapshot(
                            elements = elements.map { it.copy(terminalTags = it.terminalTags.toMutableList()) },
                            connectors = connectors.map { it.copy() }
                        )
                        wireDragStartPos = PointF(worldPos.x, worldPos.y)
                        interactionListener?.onElementSelected(null)
                        interactionListener?.onConnectorSelected(hitWire)
                        invalidate()
                        return true
                    }
                }

                // 5. Tap Canvas Background
                if (activeWireStart != null) {
                    activeWireStart = null
                    invalidate()
                    return true
                }

                selectedElementId = null
                selectedConnectorId = null
                interactionListener?.onElementSelected(null)
                interactionListener?.onConnectorSelected(null)
                invalidate()
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    if (newPointerIndex < event.pointerCount) {
                        activePointerId = event.getPointerId(newPointerIndex)
                        val newWorldPos = screenToWorld(event.getX(newPointerIndex), event.getY(newPointerIndex))
                        if (draggedElement != null) {
                            dragStartPos = PointF(newWorldPos.x - draggedElement!!.x, newWorldPos.y - draggedElement!!.y)
                        }
                        wireDragStartPos = PointF(newWorldPos.x, newWorldPos.y)
                    }
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = if (activePointerId != MotionEvent.INVALID_POINTER_ID) event.findPointerIndex(activePointerId) else 0
                val curX = if (pointerIndex != -1 && pointerIndex < event.pointerCount) event.getX(pointerIndex) else event.x
                val curY = if (pointerIndex != -1 && pointerIndex < event.pointerCount) event.getY(pointerIndex) else event.y
                val worldPos = screenToWorld(curX, curY)
                currentTouchPos = worldPos

                if (activePressedButtonElement != null) {
                    return true
                }

                if (isDraggingWire && activeDragConnector != null && !isSimulating) {
                    val conn = activeDragConnector!!
                    val el1 = elements.find { it.id == conn.elem1Id }
                    val el2 = elements.find { it.id == conn.elem2Id }
                    val def1 = el1?.let { NativeComponentRegistry.get(it.svgData) }
                    val def2 = el2?.let { NativeComponentRegistry.get(it.svgData) }
                    if (el1 != null && el2 != null && def1 != null && def2 != null) {
                        val p1 = el1.getWorldNodePos(conn.node1Idx, def1.nodes)
                        val dir1 = el1.getWorldNodeDirection(conn.node1Idx, def1.nodes)
                        val p2 = el2.getWorldNodePos(conn.node2Idx, def2.nodes)
                        val dir2 = el2.getWorldNodeDirection(conn.node2Idx, def2.nodes)

                        val uA = when (((dir1 % 4) + 4) % 4) { 0 -> PointF(0f, -1f); 1 -> PointF(1f, 0f); 2 -> PointF(0f, 1f); else -> PointF(-1f, 0f) }
                        val uB = when (((dir2 % 4) + 4) % 4) { 0 -> PointF(0f, -1f); 1 -> PointF(1f, 0f); 2 -> PointF(0f, 1f); else -> PointF(-1f, 0f) }

                        val isVertA = (uA.y != 0f)
                        val isVertB = (uB.y != 0f)

                        val dStart = maxOf(GRID_SIZE, GRID_SIZE + snap(conn.mStart))
                        val dEnd = maxOf(GRID_SIZE, GRID_SIZE + snap(conn.mEnd))
                        val stub1 = PointF(p1.x + uA.x * dStart, p1.y + uA.y * dStart)
                        val stub2 = PointF(p2.x + uB.x * dEnd, p2.y + uB.y * dEnd)

                        if (isVertA && isVertB) {
                            val defaultMidY = snap((stub1.y + stub2.y) / 2f)
                            val delta = snap(worldPos.y - defaultMidY)
                            conn.mCenter = delta
                            conn.customMidPos = if (delta != 0f) delta else null
                        } else if (!isVertA && !isVertB) {
                            val defaultMidX = snap((stub1.x + stub2.x) / 2f)
                            val delta = snap(worldPos.x - defaultMidX)
                            conn.mCenter = delta
                            conn.customMidPos = if (delta != 0f) delta else null
                        } else if (isVertA && !isVertB) {
                            val delta = snap(worldPos.y - stub2.y)
                            conn.mCenter = delta
                            conn.customMidPos = if (delta != 0f) delta else null
                        } else {
                            val delta = snap(worldPos.x - stub2.x)
                            conn.mCenter = delta
                            conn.customMidPos = if (delta != 0f) delta else null
                        }
                        invalidate()
                    }
                } else if (draggedElement != null && !isSimulating) {
                    val newX = snap(worldPos.x - dragStartPos.x)
                    val newY = snap(worldPos.y - dragStartPos.y)
                    if (draggedElement!!.x != newX || draggedElement!!.y != newY) {
                        draggedElement!!.x = newX
                        draggedElement!!.y = newY
                        invalidate()
                    }
                } else if (activeWireStart != null) {
                    invalidate()
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID

                if (activePressedButtonElement != null) {
                    val btnEl = activePressedButtonElement!!
                    engine.setButtonPressed(btnEl.id, false)
                    activePressedButtonElement = null
                    stepSimulation()
                    invalidate()
                }

                if (pendingDragSnapshot != null) {
                    val before = pendingDragSnapshot!!
                    val hasChanged = elements.any { el ->
                        val b = before.elements.find { it.id == el.id }
                        b != null && (b.x != el.x || b.y != el.y)
                    } || connectors.any { c ->
                        val b = before.connectors.find { it.id == c.id }
                        b != null && (b.customMidPos != c.customMidPos || b.routeOrientation != c.routeOrientation)
                    }
                    if (hasChanged) {
                        undoStack.addLast(before)
                        if (undoStack.size > maxHistory) {
                            undoStack.removeFirst()
                        }
                        redoStack.clear()
                        notifyUndoRedoState()
                        interactionListener?.onCircuitModified()
                    }
                    pendingDragSnapshot = null
                }
                isDraggingWire = false
                activeDragConnector = null
                draggedElement = null
            }
        }
        return true
    }

    private fun distancePointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot(px - x1, py - y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot(px - projX, py - projY)
    }

    private fun isPointNearConnector(c: NativeConnector, wx: Float, wy: Float, toleranceWorld: Float): Boolean {
        val el1 = elements.find { it.id == c.elem1Id } ?: return false
        val el2 = elements.find { it.id == c.elem2Id } ?: return false
        val def1 = NativeComponentRegistry.get(el1.svgData) ?: return false
        val def2 = NativeComponentRegistry.get(el2.svgData) ?: return false
        val p1 = el1.getWorldNodePos(c.node1Idx, def1.nodes)
        val p2 = el2.getWorldNodePos(c.node2Idx, def2.nodes)
        val dir1 = el1.getWorldNodeDirection(c.node1Idx, def1.nodes)
        val dir2 = el2.getWorldNodeDirection(c.node2Idx, def2.nodes)
        val segments = c.computeSegments(p1, dir1, p2, dir2, GRID_SIZE)
        for (seg in segments) {
            if (distancePointToSegment(wx, wy, seg.startX, seg.startY, seg.endX, seg.endY) <= toleranceWorld) {
                return true
            }
        }
        return false
    }

    private fun hitTestConnector(wx: Float, wy: Float, toleranceWorld: Float = 30f): NativeConnector? {
        // If a connector is currently selected, check it first for effortless re-grabbing
        if (selectedConnectorId != null) {
            val sel = connectors.find { it.id == selectedConnectorId }
            if (sel != null && isPointNearConnector(sel, wx, wy, toleranceWorld)) {
                return sel
            }
        }
        for (c in connectors.asReversed()) {
            if (isPointNearConnector(c, wx, wy, toleranceWorld)) {
                return c
            }
        }
        return null
    }

    private fun findElementAt(wx: Float, wy: Float): NativeElement? {
        for (el in elements.reversed()) {
            val def = NativeComponentRegistry.get(el.svgData) ?: continue
            val (halfW, halfH) = if (el.svgData == "text") {
                val textStr = if (el.comment.isNotEmpty()) el.comment else if (el.tag.isNotEmpty()) el.tag else "Teks"
                val fs = if (el.fontSize > 0) el.fontSize else 24f
                Pair(maxOf(90f, textStr.length * fs * 0.65f + 30f) / 2f + 12f, maxOf(36f, fs * 1.5f + 16f) / 2f + 12f)
            } else {
                Pair((def.width / 2f) + 12f, (def.height / 2f) + 12f)
            }
            if (wx in (el.x - halfW)..(el.x + halfW) && wy in (el.y - halfH)..(el.y + halfH)) {
                return el
            }
        }
        return null
    }

    private fun findTerminalAt(wx: Float, wy: Float): Pair<Int, Int>? {
        val HIT_RADIUS = 28f
        for (el in elements) {
            val def = NativeComponentRegistry.get(el.svgData) ?: continue
            for (node in def.nodes) {
                val nodePos = el.getWorldNodePos(node.index, def.nodes)
                if (hypot(wx - nodePos.x, wy - nodePos.y) <= HIT_RADIUS) {
                    return Pair(el.id, node.index)
                }
            }
        }
        return null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#121216"))

        canvas.save()
        canvas.translate(panX, panY)
        canvas.scale(zoom, zoom)

        // 1. Draw Grid
        drawGrid(canvas)

        // 2. Draw Wires (Connectors)
        drawWires(canvas)

        // 3. Draw Elements (Using SVG Component Renderer for 100% Identical Visuals)
        drawElements(canvas)

        // 4. Draw Terminal Pins
        drawTerminals(canvas)

        canvas.restore()

        if (isSimulating && (hasActiveFlowAnimation || hasActiveRunningMotor || hasActiveBlinkingLamp || hasActiveOverheatAnimation)) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawGrid(canvas: Canvas) {
        if (!isGridSnapEnabled) return
        val left = (-panX / zoom) - GRID_SIZE
        val top = (-panY / zoom) - GRID_SIZE
        val right = ((width - panX) / zoom) + GRID_SIZE
        val bottom = ((height - panY) / zoom) + GRID_SIZE

        val startX = (left / GRID_SIZE).toInt() * GRID_SIZE
        val startY = (top / GRID_SIZE).toInt() * GRID_SIZE

        val reqX = (((right - startX) / GRID_SIZE).toInt() + 2).coerceAtLeast(0)
        val reqY = (((bottom - startY) / GRID_SIZE).toInt() + 2).coerceAtLeast(0)
        val totalFloats = reqX * reqY * 2
        if (gridPoints.size < totalFloats) {
            gridPoints = FloatArray(totalFloats + 2048)
        }

        var idx = 0
        var x = startX
        while (x <= right) {
            var y = startY
            while (y <= bottom) {
                gridPoints[idx++] = x
                gridPoints[idx++] = y
                y += GRID_SIZE
            }
            x += GRID_SIZE
        }

        if (idx > 0) {
            canvas.drawPoints(gridPoints, 0, idx, dotPaint)
        }
    }

    private fun drawWires(canvas: Canvas) {
        val curSimResult = simResult
        hasActiveFlowAnimation = false
        hasActiveOverheatAnimation = false
        if (isSimulating && showCurrentFlowAnimation) {
            flowAnimPhase = (flowAnimPhase + 1.2f) % 24f
            wireFlowPaint.color = Color.WHITE
            wireFlowPaint.pathEffect = android.graphics.DashPathEffect(flowDashIntervals, -flowAnimPhase)
        }

        // Thermal Pulsing Animation Parameter (0.35s / 350ms infinite ease-in-out cycle)
        val cycleMs = 350L
        val timeInCycle = (android.os.SystemClock.uptimeMillis() % cycleMs).toFloat() / cycleMs.toFloat()
        // Smooth sine wave oscillating from 0.0f to 1.0f
        val pulseFactor = ((Math.sin(timeInCycle * 2.0 * Math.PI - Math.PI / 2.0) + 1.0) / 2.0).toFloat()

        // Interpolated pulsating core color: #dc2626 (crimson) -> #fbbf24 (radiant amber)
        val coreR = (220 + (251 - 220) * pulseFactor).toInt()
        val coreG = (38 + (191 - 38) * pulseFactor).toInt()
        val coreB = (38 + (36 - 38) * pulseFactor).toInt()
        val pulseCoreColor = Color.rgb(coreR, coreG, coreB)

        // Interpolated pulsating glow halo: #dc2626 -> #f97316
        val glowR = (220 + (249 - 220) * pulseFactor).toInt()
        val glowG = (38 + (115 - 38) * pulseFactor).toInt()
        val glowAlpha = (70 + (130 * pulseFactor)).toInt() // 70 to 200
        val pulseGlowColor = Color.argb(glowAlpha, glowR, glowG, 38)

        // Outer aura alpha & stroke
        val auraAlpha = (25 + (65 * pulseFactor)).toInt() // 25 to 90
        val pulseAuraColor = Color.argb(auraAlpha, 239, 68, 68)

        wireDcOverheatGlowPaint.color = pulseGlowColor
        wireDcOverheatGlowPaint.strokeWidth = 7.5f + 8.5f * pulseFactor
        wireDcOverheatOuterAuraPaint.color = pulseAuraColor
        wireDcOverheatOuterAuraPaint.strokeWidth = 14f + 12f * pulseFactor

        for (c in connectors) {
            val el1 = elements.find { it.id == c.elem1Id } ?: continue
            val el2 = elements.find { it.id == c.elem2Id } ?: continue
            val def1 = NativeComponentRegistry.get(el1.svgData) ?: continue
            val def2 = NativeComponentRegistry.get(el2.svgData) ?: continue

            val p1 = el1.getWorldNodePos(c.node1Idx, def1.nodes)
            val p2 = el2.getWorldNodePos(c.node2Idx, def2.nodes)
            val dir1 = el1.getWorldNodeDirection(c.node1Idx, def1.nodes)
            val dir2 = el2.getWorldNodeDirection(c.node2Idx, def2.nodes)

            val pot = curSimResult?.wirePotentials?.get(c.id) ?: Potential.NONE
            val isWireEnergized = isSimulating && pot != Potential.NONE
            // Pewarnaan korsleting khusus per-jalur (per-net): hanya kawat yang mengalami korsleting/arus bocor yang menyala oranye/merah membara!
            val isDcOverheatWire = isSimulating && pot == Potential.DC_OVERHEAT
            val isAcShortWire = isSimulating && pot == Potential.AC_SHORT
            val isThermalPulsingWire = isDcOverheatWire || isAcShortWire

            if (isThermalPulsingWire) {
                hasActiveOverheatAnimation = true
            }

            wirePaint.color = when {
                isThermalPulsingWire -> pulseCoreColor
                isWireEnergized -> pot.color
                isSimulating -> Color.parseColor("#475569")
                else -> Color.parseColor("#94a3b8")
            }
            wirePaint.strokeWidth = when {
                isThermalPulsingWire -> 3.8f + 2.2f * pulseFactor
                isWireEnergized -> 3.5f
                else -> 2.5f
            }

            val segments = c.computeSegments(p1, dir1, p2, dir2, GRID_SIZE)
            if (segments.isEmpty()) continue

            wireScratchPath.reset()
            wireScratchPath.moveTo(segments.first().startX, segments.first().startY)
            for (seg in segments) {
                wireScratchPath.lineTo(seg.endX, seg.endY)
            }

            // Glow jika korslet DC Overheat / AC Short Circuit berdenyut membara
            if (isThermalPulsingWire) {
                canvas.drawPath(wireScratchPath, wireDcOverheatOuterAuraPaint)
                canvas.drawPath(wireScratchPath, wireDcOverheatGlowPaint)
            }

            // Glow jika kawat sedang dipilih
            val isSelected = (c.id == selectedConnectorId)
            if (isSelected) {
                canvas.drawPath(wireScratchPath, wireSelectedGlowPaint)
            }

            canvas.drawPath(wireScratchPath, wirePaint)

            val isFlowing = isSimulating && showCurrentFlowAnimation && (curSimResult?.flowingConnectors?.contains(c.id) == true)
            if (isFlowing) {
                canvas.drawPath(wireScratchPath, wireFlowPaint)
                hasActiveFlowAnimation = true
            }

            // Handle geser pada segmen tengah kawat yang dipilih
            if (isSelected) {
                val draggableSeg = segments.firstOrNull { it.isDraggable && it.length > 2f }
                    ?: segments.filter { it.length > 2f }.getOrNull(segments.size / 2)
                    ?: segments.firstOrNull()
                if (draggableSeg != null) {
                    val hx = draggableSeg.midX
                    val hy = draggableSeg.midY
                    val pillW = if (draggableSeg.isHorizontal) 28f else 12f
                    val pillH = if (draggableSeg.isHorizontal) 12f else 28f
                    wireHandleRect.set(hx - pillW * 0.5f, hy - pillH * 0.5f, hx + pillW * 0.5f, hy + pillH * 0.5f)
                    canvas.drawRoundRect(wireHandleRect, 6f, 6f, wireHandleFill)
                    canvas.drawRoundRect(wireHandleRect, 6f, 6f, wireHandleStroke)
                }
            }
        }

        // Active Drawing Rubberband Wire
        if (activeWireStart != null) {
            val (elId, nodeIdx) = activeWireStart!!
            val el = elements.find { it.id == elId }
            val def = el?.let { NativeComponentRegistry.get(it.svgData) }
            if (el != null && def != null) {
                val p1 = el.getWorldNodePos(nodeIdx, def.nodes)
                wirePaint.color = Color.parseColor("#38bdf8")
                wirePaint.strokeWidth = 2.5f
                wireScratchPath.reset()
                wireScratchPath.moveTo(p1.x, p1.y)
                wireScratchPath.lineTo(currentTouchPos.x, currentTouchPos.y)
                canvas.drawPath(wireScratchPath, wirePaint)
            }
        }
    }

    private fun drawElements(canvas: Canvas) {
        var hasActiveRunningMotor = false
        var hasActiveBlinkingLamp = false
        for (el in elements) {
            val def = NativeComponentRegistry.get(el.svgData) ?: continue
            canvas.save()
            canvas.translate(el.x, el.y)
            canvas.rotate(el.rotationDegrees)

            val isTextComp = (el.svgData == "text")
            val pad = 8f
            val halfW = if (isTextComp) {
                val textStr = if (el.comment.isNotEmpty()) el.comment else if (el.tag.isNotEmpty()) el.tag else "Teks"
                val fs = if (el.fontSize > 0) el.fontSize else 24f
                maxOf(90f, textStr.length * fs * 0.65f + 30f) / 2f + pad
            } else {
                def.width / 2f + pad
            }
            val halfH = if (isTextComp) {
                val fs = if (el.fontSize > 0) el.fontSize else 24f
                maxOf(36f, fs * 1.5f + 16f) / 2f + pad
            } else {
                def.height / 2f + pad
            }

            // CAD Selection Bounding Box dengan Corner Brackets & Tag Badge
            // (Aktif di Mode EDIT maupun Mode RUN saat mengoperasikan komponen!)
            if (el.id == selectedElementId) {
                selBoxRect.set(-halfW, -halfH, halfW, halfH)

                val accentColor = if (isSimulating) Color.parseColor("#10b981") else Color.parseColor("#00e5ff")
                selCornerPaint.color = accentColor
                selTagBadgePaint.color = accentColor

                // Background highlight dan border halus
                canvas.drawRoundRect(selBoxRect, 6f, 6f, selBoxFillPaint)
                canvas.drawRoundRect(selBoxRect, 6f, 6f, selBoxBorderPaint)

                // 4 Siku Sudut Tegas (CAD Corner Brackets)
                val cornerLen = minOf(14f, minOf(selBoxRect.width(), selBoxRect.height()) * 0.28f)
                selCornerPath.reset()
                // Kiri Atas
                selCornerPath.moveTo(selBoxRect.left, selBoxRect.top + cornerLen)
                selCornerPath.lineTo(selBoxRect.left, selBoxRect.top)
                selCornerPath.lineTo(selBoxRect.left + cornerLen, selBoxRect.top)
                // Kanan Atas
                selCornerPath.moveTo(selBoxRect.right - cornerLen, selBoxRect.top)
                selCornerPath.lineTo(selBoxRect.right, selBoxRect.top)
                selCornerPath.lineTo(selBoxRect.right, selBoxRect.top + cornerLen)
                // Kiri Bawah
                selCornerPath.moveTo(selBoxRect.left, selBoxRect.bottom - cornerLen)
                selCornerPath.lineTo(selBoxRect.left, selBoxRect.bottom)
                selCornerPath.lineTo(selBoxRect.left + cornerLen, selBoxRect.bottom)
                // Kanan Bawah
                selCornerPath.moveTo(selBoxRect.right - cornerLen, selBoxRect.bottom)
                selCornerPath.lineTo(selBoxRect.right, selBoxRect.bottom)
                selCornerPath.lineTo(selBoxRect.right, selBoxRect.bottom - cornerLen)
                canvas.drawPath(selCornerPath, selCornerPaint)

                // Badge tag di atas kotak seleksi (kecuali text component) - selalu tegak lurus
                if (!isTextComp) {
                    val badgeText = if (el.tag.isNotEmpty()) el.tag else el.svgData
                    val badgeW = maxOf(28f, selTagTextPaint.measureText(badgeText) + 12f)
                    val badgeH = 15f
                    val badgeCenterY = -halfH - badgeH / 2f - 3f
                    canvas.save()
                    canvas.translate(0f, badgeCenterY)
                    canvas.rotate(-el.rotationDegrees)
                    selBadgeRect.set(-badgeW / 2f, -badgeH / 2f, badgeW / 2f, badgeH / 2f)
                    canvas.drawRoundRect(selBadgeRect, 4f, 4f, selTagBadgePaint)
                    val textY = -(selTagTextPaint.descent() + selTagTextPaint.ascent()) * 0.5f
                    canvas.drawText(badgeText, 0f, textY, selTagTextPaint)
                    canvas.restore()
                }
            }

            // Determine current visual frame (position)
            val isCartridgeFuse = el.svgData.startsWith("fuse") && !el.svgData.startsWith("fuseSwitch")
            val currentFrame = if (isSimulating) {
                simResult?.elementFrames?.get(el.id) ?: run {
                    val rawState = engine.manualStates[el.id] ?: if (isCartridgeFuse) 1 else el.frameIndex
                    if (isCartridgeFuse) (if (rawState == 1) 0 else 1) else rawState
                }
            } else {
                val rawState = engine.manualStates[el.id] ?: if (isCartridgeFuse) 1 else el.frameIndex
                if (isCartridgeFuse) (if (rawState == 1) 0 else 1) else rawState
            }

            val isTimingOrBlinking = (el.svgData == "lampBlink" || el.svgData == "flasher" || el.svgData == "coilFlasher" || el.svgData.contains("Delay", ignoreCase = true))
            if (isTimingOrBlinking && isSimulating) {
                val tState = simResult?.timerStates?.get(el.id) ?: engine.timerStates[el.id]
                if (tState?.energized == true || tState?.isTiming == true) {
                    hasActiveBlinkingLamp = true
                }
            }

            val variant = getElementVariantIndex(el)

            if (isTextComp) {
                drawTextComponent(canvas, el)
            } else {
                // Render Official SVG Vector using SvgComponentRenderer with frame, variant & lampColor support
                val rendered = SvgComponentRenderer.render(
                    context,
                    canvas,
                    def.file,
                    -def.width / 2f,
                    -def.height / 2f,
                    def.width,
                    def.height,
                    currentFrame,
                    variant,
                    el.lampColor
                )

                if (!rendered) {
                    // High-fidelity fallback if SVG file not yet indexed
                    drawFallbackIecSymbol(canvas, el, def)
                }
            }

            // Lamp Custom Color Effects (Glowing Halo saat On, Indicator Dot di Edit Mode)
            val isLamp = (el.svgData == "lamp" || el.svgData == "lampBlink")
            if (isLamp) {
                val lampColorInt = getLampColorInt(el.lampColor)
                if (isSimulating && currentFrame == 1) {
                    lampGlowPaint.color = lampColorInt
                    lampGlowPaint.alpha = 85
                    canvas.drawCircle(0f, 0f, 26f, lampGlowPaint)
                    lampGlowPaint.alpha = 190
                    canvas.drawCircle(0f, 0f, 20f, lampGlowPaint)
                } else if (!isSimulating) {
                    lampDotPaint.color = lampColorInt
                    canvas.drawCircle(0f, 0f, 5.5f, lampDotPaint)
                }
            }

            // Animasi Putaran Motor Listrik (Real-Time 60fps Hardware-Accelerated; 1/2 = Speed 1, 3/4 = Speed 2)
            if (isSimulating && def.category == "motors" && currentFrame in 1..4) {
                hasActiveRunningMotor = true
                val radius = if (el.svgData == "motorDC") 48.5f else 73.5f
                val isCCW = (currentFrame == 2 || currentFrame == 4)
                val speedMult = if (currentFrame == 3 || currentFrame == 4) 1.85f else 1.0f
                drawMotorSpinningArrows(canvas, radius, isCCW, speedMult)
            }

            canvas.restore()

            // 2. Gambar Anotasi & Label Teks di Koordinat World (Selalu Tegak Upright 0°)
            canvas.save()
            canvas.translate(el.x, el.y)

            val isRotated90or270 = (el.rotationDegrees == 90f || el.rotationDegrees == 270f)
            val visualW = if (isRotated90or270) def.height else def.width
            val visualH = if (isRotated90or270) def.width else def.height

            // Draw Tag Label (jika tidak terpilih, untuk menghindari teks ganda dengan badge seleksi)
            if (el.tag.isNotEmpty() && el.id != selectedElementId && !isTextComp) {
                val tag = el.tag
                val tagW = textPaint.measureText(tag)
                if (visualW >= 90f) {
                    val tagX = 0f
                    val tagY = -visualH / 2f - 10f
                    val bgRect = RectF(tagX - tagW / 2f - 4f, tagY - 9f, tagX + tagW / 2f + 4f, tagY + 4f)
                    canvas.drawRoundRect(bgRect, 3f, 3f, tagBgPaint)
                    canvas.drawRoundRect(bgRect, 3f, 3f, tagBorderPaint)
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(tag, tagX, tagY, textPaint)
                } else {
                    val tagX = visualW / 2f + 14f
                    val tagY = 0f
                    val bgRect = RectF(tagX - 4f, tagY - 9f, tagX + tagW + 4f, tagY + 4f)
                    canvas.drawRoundRect(bgRect, 3f, 3f, tagBgPaint)
                    canvas.drawRoundRect(bgRect, 3f, 3f, tagBorderPaint)
                    textPaint.textAlign = Paint.Align.LEFT
                    canvas.drawText(tag, tagX, tagY, textPaint)
                }
            }

            // Draw Timer Preset / Countdown Badge for Timers / Delay Coils
            val isTimerCoil = el.svgData.contains("Delay", ignoreCase = true) || el.svgData == "coilFlasher" || el.svgData == "flasher"
            if (isTimerCoil) {
                val tState = simResult?.timerStates?.get(el.id) ?: engine.timerStates[el.id]
                val timerText = if (isSimulating && tState != null) {
                    when {
                        tState.isTiming -> {
                            if (el.svgData == "coilFlasher" || el.svgData == "flasher") {
                                val half = tState.presetMs.coerceAtLeast(50L)
                                val cur = tState.elapsedMs
                                val rem = if (cur < half) half - cur else (2 * half) - cur
                                val remSec = rem / 1000f
                                if (tState.outputState) "ON ${String.format(java.util.Locale.US, "%.1f", remSec)}s"
                                else "OFF ${String.format(java.util.Locale.US, "%.1f", remSec)}s"
                            } else {
                                val rem = (tState.presetMs - tState.elapsedMs).coerceAtLeast(0L)
                                val remSec = rem / 1000f
                                "T: ${String.format(java.util.Locale.US, "%.1f", remSec)}s"
                            }
                        }
                        tState.outputState -> "ON"
                        else -> "${String.format(java.util.Locale.US, "%.1f", el.presetTime)}s"
                    }
                } else {
                    "${String.format(java.util.Locale.US, "%.1f", el.presetTime)}s"
                }
                val tW = timerBadgePaint.measureText(timerText)
                val tY = -visualH / 2f - (if (el.tag.isNotEmpty()) 24f else 10f)
                timerBadgeRect.set(-tW / 2f - 6f, tY - 10f, tW / 2f + 6f, tY + 4f)
                canvas.drawRoundRect(timerBadgeRect, 3f, 3f, tagBgPaint)
                canvas.drawRoundRect(timerBadgeRect, 3f, 3f, tagBorderPaint)
                canvas.drawText(timerText, 0f, tY, timerBadgePaint)
            }

            // Draw Comment Label (hanya jika diaktifkan di Settings)
            if (showComponentNames && el.comment.isNotEmpty()) {
                canvas.drawText(el.comment, 0f, visualH / 2f + 16f, elementCommentPaint)
            }

            // Draw Lock Indicator Badge if locked or teacherLocked (in edit mode)
            if ((el.teacherLocked || el.locked) && !isSimulating) {
                val isTeacher = el.teacherLocked
                lockBadgeBgPaint.color = if (isTeacher) Color.parseColor("#065f46") else Color.parseColor("#78350f")
                lockBadgeBorderPaint.color = if (isTeacher) Color.parseColor("#10b981") else Color.parseColor("#f59e0b")
                val lockText = if (isTeacher) "KIT" else "LOCK"
                val textW = lockBadgeTextPaint.measureText(lockText)
                val bx = visualW / 2f - 4f
                val by = -visualH / 2f + 4f
                lockBadgeRect.set(bx - textW / 2f - 4f, by - 8f, bx + textW / 2f + 4f, by + 6f)
                canvas.drawRoundRect(lockBadgeRect, 3f, 3f, lockBadgeBgPaint)
                canvas.drawRoundRect(lockBadgeRect, 3f, 3f, lockBadgeBorderPaint)
                canvas.drawText(lockText, bx, by + 1.5f, lockBadgeTextPaint)
            }

            canvas.restore()
        }

        this.hasActiveRunningMotor = hasActiveRunningMotor
        this.hasActiveBlinkingLamp = hasActiveBlinkingLamp
        if (isSimulating && hasActiveRunningMotor) {
            motorSpinAngle = (motorSpinAngle + 6f) % 360f
        }
    }

    private fun drawMotorSpinningArrows(canvas: Canvas, radius: Float, isCCW: Boolean, speedMultiplier: Float = 1.0f) {
        canvas.save()
        val dir = if (isCCW) -1f else 1f
        canvas.rotate(motorSpinAngle * speedMultiplier * dir)

        val strokeW = if (radius < 50f) 6f else 7.5f
        motorArcPaint.strokeWidth = strokeW
        motorOvalRect.set(-radius, -radius, radius, radius)

        val headLen = if (radius < 50f) 14f else 18.5f
        val headW = if (radius < 50f) 10f else 13.5f

        if (!isCCW) {
            // Forward (Clockwise): Panah melengkung dan ekornya berputar searah jarum jam (CW)
            canvas.drawArc(motorOvalRect, -165f, 130f, false, motorArcPaint)
            drawArrowHeadAtTip(canvas, radius, -35f, headLen, headW, isCCW = false)

            canvas.drawArc(motorOvalRect, 15f, 130f, false, motorArcPaint)
            drawArrowHeadAtTip(canvas, radius, 145f, headLen, headW, isCCW = false)
        } else {
            // Reverse (Counter-Clockwise): Panah melengkung dan ekornya berputar berlawanan jarum jam (CCW)
            canvas.drawArc(motorOvalRect, -15f, -130f, false, motorArcPaint)
            drawArrowHeadAtTip(canvas, radius, -145f, headLen, headW, isCCW = true)

            canvas.drawArc(motorOvalRect, 165f, -130f, false, motorArcPaint)
            drawArrowHeadAtTip(canvas, radius, 35f, headLen, headW, isCCW = true)
        }

        canvas.restore()
    }

    private fun drawArrowHeadAtTip(canvas: Canvas, radius: Float, tipAngleDeg: Float, headLen: Float, headW: Float, isCCW: Boolean) {
        val rad = Math.toRadians(tipAngleDeg.toDouble())
        val cx = (radius * Math.cos(rad)).toFloat()
        val cy = (radius * Math.sin(rad)).toFloat()

        motorHeadPath.reset()
        motorHeadPath.moveTo(headLen * 0.55f, 0f)
        motorHeadPath.lineTo(-headLen * 0.45f, -headW * 0.5f)
        motorHeadPath.lineTo(-headLen * 0.45f, headW * 0.5f)
        motorHeadPath.close()

        canvas.save()
        canvas.translate(cx, cy)
        val tangentDeg = if (isCCW) tipAngleDeg - 90f else tipAngleDeg + 90f
        canvas.rotate(tangentDeg)
        canvas.drawPath(motorHeadPath, motorArrowHeadPaint)
        canvas.restore()
    }

    private fun getElementVariantIndex(el: NativeElement): Int {
        val tag = el.tag.trim()
        if (tag.isNotEmpty()) {
            val master = elements.find { it.id != el.id && it.tag.trim() == tag }
            if (master != null) {
                when (master.svgData) {
                    "coilDelayOn" -> return 1
                    "coilDelayOff" -> return 2
                    "coilDelayOnOff", "coilFlasher", "flasher" -> return 3
                    else -> if (master.svgData.startsWith("overload") || tag.startsWith("-F")) return 4
                }
            }
            if (tag.startsWith("-KT")) return 1
            if (tag.startsWith("-FR") || tag.startsWith("-F")) return 4
        }
        return 0
    }

    private fun drawFallbackIecSymbol(canvas: Canvas, el: NativeElement, def: ComponentDef) {
        val frame = simResult?.elementFrames?.get(el.id) ?: el.frameIndex
        elementPaint.color = Color.WHITE

        when (def.category) {
            "sources" -> {
                elementPaint.style = Paint.Style.STROKE
                canvas.drawCircle(0f, 0f, 14f, elementPaint)
                val lbl = when (el.svgData) {
                    "sourceL1" -> "L1"
                    "sourceL2" -> "L2"
                    "sourceL3" -> "L3"
                    "sourceN" -> "N"
                    "sourcePE" -> "PE"
                    else -> "~"
                }
                canvas.drawText(lbl, 0f, 4f, textPaint)
            }
            "switches", "auxiliaries" -> {
                val isNC = el.svgData.contains("NC") && !el.svgData.contains("NONC")
                val isClosed = if (isNC) (frame == 0) else (frame == 1)
                canvas.drawLine(0f, -30f, 0f, -14f, elementPaint)
                canvas.drawLine(0f, 14f, 0f, 30f, elementPaint)
                if (isClosed) {
                    canvas.drawLine(0f, -14f, 0f, 14f, elementPaint)
                } else {
                    canvas.drawLine(0f, 14f, 12f, -14f, elementPaint)
                }
            }
            "coils" -> {
                canvas.drawRect(-14f, -22f, 14f, 22f, elementPaint)
                canvas.drawLine(0f, -30f, 0f, -22f, elementPaint)
                canvas.drawLine(0f, 22f, 0f, 30f, elementPaint)
                if (frame == 1) {
                    fillPaint.color = Color.parseColor("#dc2626")
                    canvas.drawRect(-12f, -20f, 12f, 20f, fillPaint)
                }
            }
            "motors" -> {
                canvas.drawCircle(0f, 0f, 26f, elementPaint)
                canvas.drawText("M", 0f, -2f, textPaint)
                canvas.drawText("3~", 0f, 14f, textPaint)
            }
            "loads" -> {
                canvas.drawCircle(0f, 0f, 18f, elementPaint)
                canvas.drawLine(-12f, -12f, 12f, 12f, elementPaint)
                canvas.drawLine(12f, -12f, -12f, 12f, elementPaint)
                canvas.drawLine(0f, -30f, 0f, -18f, elementPaint)
                canvas.drawLine(0f, 18f, 0f, 30f, elementPaint)
                if (frame == 1) {
                    fillPaint.color = Color.parseColor("#f59e0b")
                    canvas.drawCircle(0f, 0f, 16f, fillPaint)
                }
            }
            else -> {
                canvas.drawRect(-def.width / 2f, -def.height / 2f, def.width / 2f, def.height / 2f, elementPaint)
            }
        }
    }

    fun getLampColorInt(colorStr: String): Int {
        return when (colorStr.lowercase()) {
            "white" -> Color.parseColor("#f8fafc")
            "red" -> Color.parseColor("#ef4444")
            "green" -> Color.parseColor("#22c55e")
            "blue" -> Color.parseColor("#3b82f6")
            else -> Color.parseColor("#fbbf24")
        }
    }

    private fun drawTextComponent(canvas: Canvas, el: NativeElement) {
        val displayText = if (el.comment.isNotEmpty()) el.comment else if (el.tag.isNotEmpty()) el.tag else "Teks"
        val fs = if (el.fontSize > 0) el.fontSize else 24f
        textCompPaint.textSize = fs

        val rawColor = el.textColor.ifEmpty { "#93c5fd" }
        val colorInt = try {
            Color.parseColor(rawColor)
        } catch (_: Exception) {
            when (rawColor.lowercase()) {
                "white" -> Color.parseColor("#f8fafc")
                "yellow" -> Color.parseColor("#fbbf24")
                "red" -> Color.parseColor("#ef4444")
                "green" -> Color.parseColor("#22c55e")
                "gray" -> Color.parseColor("#94a3b8")
                else -> Color.parseColor("#93c5fd")
            }
        }
        textCompPaint.color = colorInt

        val textY = -(textCompPaint.descent() + textCompPaint.ascent()) * 0.5f
        canvas.save()
        canvas.rotate(-el.rotationDegrees)
        canvas.drawText(displayText, 0f, textY, textCompPaint)
        canvas.restore()
    }

    private fun drawTerminals(canvas: Canvas) {
        for (el in elements) {
            val def = NativeComponentRegistry.get(el.svgData) ?: continue
            for ((listIdx, node) in def.nodes.withIndex()) {
                val p = el.getWorldNodePos(listIdx, def.nodes)

                if (activeWireStart != null) {
                    canvas.drawCircle(p.x, p.y, 6f, snapPaint)
                } else {
                    canvas.drawCircle(p.x, p.y, 3.5f, terminalPaint)
                }

                if (showPinLabels) {
                    val pinTag = el.getTerminalTag(listIdx, node.tag)
                    if (pinTag.isNotEmpty()) {
                        val worldDir = el.getWorldNodeDirection(listIdx, def.nodes)
                        val textW = terminalLabelPaint.measureText(pinTag)
                        val pinRadius = if (activeWireStart != null) 6f else 3.5f
                        val gap = 5f // 5px jarak bersih dari lingkaran terminal pin sesuai permintaan user

                        val (tx, ty, align) = when (worldDir) {
                            1 -> {
                                // Pin menghadap ke Kanan (kawat ke kanan) -> letakkan teks di atas pin agar kawat tidak tertutup
                                Triple(p.x, p.y - pinRadius - gap, Paint.Align.CENTER)
                            }
                            3 -> {
                                // Pin menghadap ke Kiri (kawat ke kiri) -> letakkan teks di atas pin
                                Triple(p.x, p.y - pinRadius - gap, Paint.Align.CENTER)
                            }
                            else -> {
                                // Pin vertikal (Atas/Bawah) -> letakkan teks di sisi kanan pin dengan jarak 5px
                                Triple(p.x + pinRadius + gap, p.y + 3.5f, Paint.Align.LEFT)
                            }
                        }

                        // Background pill lembut untuk keterbacaan tinggi terhadap kawat dan grid
                        val rectLeft = when (align) {
                            Paint.Align.CENTER -> tx - textW / 2f - 2.5f
                            Paint.Align.RIGHT -> tx - textW - 2.5f
                            else -> tx - 2.5f
                        }
                        val rectTop = ty - 8f
                        val rectRight = rectLeft + textW + 5f
                        val rectBottom = ty + 3f
                        pinBadgeRect.set(rectLeft, rectTop, rectRight, rectBottom)
                        canvas.drawRoundRect(pinBadgeRect, 2.5f, 2.5f, pinBadgeBgPaint)

                        terminalLabelPaint.textAlign = align
                        canvas.drawText(pinTag, tx, ty, terminalLabelPaint)
                    }
                }
            }
        }
    }
}
