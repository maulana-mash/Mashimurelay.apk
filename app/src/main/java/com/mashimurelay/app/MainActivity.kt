package com.mashimurelay.app

import android.app.Activity
import android.app.Dialog
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import com.mashimurelay.app.nativecad.AppThemeHelper
import com.mashimurelay.app.nativecad.AssignmentKit
import com.mashimurelay.app.nativecad.PracticalModule
import com.mashimurelay.app.nativecad.PracticalModulesManager
import com.mashimurelay.app.nativecad.SettingsManager
import com.mashimurelay.app.nativecad.NativePdfReportGenerator
import com.mashimurelay.app.nativecad.StudentPdfInfo
import com.mashimurelay.app.nativecad.SvgComponentRenderer
import androidx.core.content.FileProvider
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.google.zxing.ResultPoint
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.net.NetworkInterface
import java.util.Collections
import android.text.InputFilter
import android.view.Gravity
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mashimurelay.app.nativecad.ComponentDef
import com.mashimurelay.app.nativecad.NativeCanvasView
import com.mashimurelay.app.nativecad.NativeComponentRegistry
import com.mashimurelay.app.nativecad.NativeConnector
import com.mashimurelay.app.nativecad.NativeElement
import com.mashimurelay.app.nativecad.ProjectItem
import com.mashimurelay.app.nativecad.VisualComponentAdapter
import com.mashimurelay.app.nativecad.WorkspaceManager
import com.mashimurelay.app.nativecad.WorkspaceProjectsAdapter
import com.mashimurelay.app.nativecad.CompactMenuHelper
import com.mashimurelay.app.nativecad.CompactMenuItem

class MainActivity : Activity() {

    // Top Drawer & Navigation (Gambar 1)
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var btnOpenMenu: View
    private lateinit var tvDrawerSubtitle: TextView
    private lateinit var menuDrawerWorkspace: View
    private var menuDrawerModules: View? = null
    private var tvDrawerModulesBadge: TextView? = null
    private lateinit var menuDrawerSave: View
    private lateinit var menuDrawerSaveAs: View
    private lateinit var menuDrawerInstructions: View
    private lateinit var menuDrawerAbout: View
    private lateinit var menuDrawerSettings: View

    // Undo, Redo & Theme Toggle
    private lateinit var btnUndo: View
    private lateinit var btnRedo: View
    private var btnToggleTheme: View? = null
    private var ivToolbarThemeIcon: ImageView? = null
    private var btnWorkspaceThemeToggle: ImageView? = null

    // Workspace Fullscreen Overlay (Gambar 2)
    private lateinit var layoutWorkspaceOverlay: LinearLayout
    private lateinit var tvWorkspaceHeaderTitle: TextView
    private lateinit var btnWorkspaceBack: ImageView
    private lateinit var btnNewProject: ImageView
    private lateinit var btnWorkspaceImport: ImageView
    private var btnWorkspaceScanQr: ImageView? = null
    private lateinit var btnWorkspaceMore: ImageView
    private val REQUEST_CODE_IMPORT = 1001
    private val REQUEST_CODE_CAMERA_PERMISSION = 2002
    private val REQUEST_CODE_PICK_QR_IMAGE = 2003
    private lateinit var rvWorkspaceProjects: RecyclerView
    private var layoutInlineModulesPage: LinearLayout? = null
    private lateinit var tabWorkspace: LinearLayout
    private var tabModules: LinearLayout? = null
    private var pillTabModules: LinearLayout? = null
    private var ivTabModulesIcon: ImageView? = null
    private var tvTabModulesLabel: TextView? = null
    private lateinit var tabExamples: LinearLayout
    private lateinit var pillTabWorkspace: LinearLayout
    private lateinit var pillTabExamples: LinearLayout
    private lateinit var ivTabWorkspaceIcon: ImageView
    private lateinit var ivTabExamplesIcon: ImageView
    private lateinit var tvTabWorkspaceLabel: TextView
    private lateinit var tvTabExamplesLabel: TextView
    private lateinit var workspaceAdapter: WorkspaceProjectsAdapter
    private var isViewingExamples = false
    private var activeWorkspaceTab: String = "workspace" // "modules" | "workspace" | "examples"
    private var activeModuleFilter: String = "all"
    private var activeModuleSearchQuery: String = ""
    private var activeDetailModule: PracticalModule? = null
    private var isInlineModulesWired: Boolean = false

    // Startup Loading Motion Overlay
    private lateinit var layoutLoadingOverlay: FrameLayout
    private lateinit var webViewLoading: WebView

    // Classroom Hotspot Sync State
    private var lastClassroomServerUrl: String? = null
    private var studentName: String? = null
    private var studentPresensi: String? = null
    private var isClassroomExamMode: Boolean = false

    // Classroom Jobsheet State
    private var activeJobsheetFile: java.io.File? = null
    private var activeJobsheetFilename: String? = null
    private var pendingJobsheetProjectName: String? = null
    private var isJobsheetAvailableOnServer: Boolean = false
    private var isJobsheetDownloading: Boolean = false
    private var isOpeningJobsheet: Boolean = false
    private var jobsheetOpenedAtMs: Long = 0L

    private lateinit var canvasView: NativeCanvasView
    private lateinit var btnToggleLibrary: View
    private lateinit var ivToolbarLibraryIcon: ImageView
    private lateinit var tvToolbarLibraryLabel: TextView
    private lateinit var btnRun: View
    private lateinit var btnStop: View
    private lateinit var btnToolbarMore: View
    private lateinit var btnExitFullscreen: View
    private lateinit var appBarLayout: View

    // Visual Component Library Side Panel
    private lateinit var panelLibrary: MaterialCardView
    private lateinit var spinnerCategory: Spinner
    private lateinit var btnCloseLibrary: Button
    private lateinit var rvComponentLibrary: RecyclerView
    private lateinit var visualAdapter: VisualComponentAdapter

    // Floating Action Pill
    private lateinit var floatingPill: MaterialCardView
    private lateinit var pillLabel: TextView
    private lateinit var pillSwitchOptions: LinearLayout
    private lateinit var pillEditControls: LinearLayout
    private lateinit var btnPos1: Button
    private lateinit var btnPos0: Button
    private lateinit var btnPos2: Button
    private lateinit var btnRotate: Button
    private lateinit var btnTag: Button
    private lateinit var btnDelete: Button

    // Zoom Controls
    private lateinit var cardZoomWidget: MaterialCardView
    private lateinit var btnZoomIn: Button
    private lateinit var btnZoomOut: Button

    // Alerts & FAB
    private lateinit var cardShortCircuit: MaterialCardView
    private lateinit var tvAlertShortCircuit: TextView
    private lateinit var btnCloseAlert: Button
    private lateinit var fabAdd: FloatingActionButton

    private var activeSwitchElement: NativeElement? = null
    private var lastSelectedElementId: Int? = null
    private var lastSelectedSwitchId: Int? = null
    private var currentProjectName = "DOL Motor Starter"
    private var isLibraryOpen = false
    private var lastWorkspaceBackTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // 1. Enable layout across cutout (shortEdges) & edge-to-edge BEFORE super.onCreate & setContentView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes = window?.attributes?.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window?.statusBarColor = Color.TRANSPARENT
        window?.navigationBarColor = Color.TRANSPARENT

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_native_cad)

        WorkspaceManager.initDefaultProjects(this)

        initViews()
        setupWindowCutoutAndInsets()
        setupListeners()
        setupVisualLibrary()
        setupWorkspaceOverlay()
        applyAppTheme(SettingsManager.isLightTheme(this))
        restoreLastProjectOrTemplate()
        setupLoadingScreen()
        openWorkspaceOverlay()
        hideSystemUI()
    }

    private fun setupWindowCutoutAndInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window?.attributes = window?.attributes?.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, insetsCompat ->
            val cutout = insetsCompat.displayCutout
            val cutoutInsets = insetsCompat.getInsets(WindowInsetsCompat.Type.displayCutout())
            val safeTop = maxOf(cutout?.safeInsetTop ?: 0, cutoutInsets.top)
            val safeBottom = maxOf(cutout?.safeInsetBottom ?: 0, cutoutInsets.bottom)
            val safeLeft = maxOf(cutout?.safeInsetLeft ?: 0, cutoutInsets.left)
            val safeRight = maxOf(cutout?.safeInsetRight ?: 0, cutoutInsets.right)

            // 1. Bilah Toolbar Atas Kanvas CAD (appBarLayout)
            if (::appBarLayout.isInitialized) {
                appBarLayout.setPadding(safeLeft, safeTop, safeRight, 0)
            }

            // 2. Layar Penuh Workspace Overlay (layoutWorkspaceOverlay)
            if (::layoutWorkspaceOverlay.isInitialized) {
                layoutWorkspaceOverlay.setPadding(safeLeft, safeTop, safeRight, safeBottom)
            }

            // 3. Tombol Mengambang Keluar Fullscreen (btnExitFullscreen)
            if (::btnExitFullscreen.isInitialized) {
                val density = resources.displayMetrics.density
                val baseMargin = (16 * density).toInt()
                (btnExitFullscreen.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
                    topMargin = baseMargin + safeTop
                    marginStart = baseMargin + safeLeft
                    btnExitFullscreen.layoutParams = this
                }
            }

            // 4. Panel Library Samping
            if (::panelLibrary.isInitialized) {
                val density = resources.displayMetrics.density
                val baseTopMargin = (52 * density).toInt()
                (panelLibrary.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
                    topMargin = baseTopMargin + safeTop
                    marginStart = safeLeft
                    bottomMargin = safeBottom
                    panelLibrary.layoutParams = this
                }
            }

            // 5. Slide-in Left Drawer
            val leftDrawer = findViewById<View>(R.id.leftDrawer)
            leftDrawer?.setPadding(safeLeft, safeTop, 0, safeBottom)

            insetsCompat
        }
    }

    private fun initViews() {
        // Drawer & Navigation (Gambar 1)
        drawerLayout = findViewById(R.id.drawerLayout)
        btnOpenMenu = findViewById(R.id.btnOpenMenu)
        tvDrawerSubtitle = findViewById(R.id.tvDrawerSubtitle)
        menuDrawerWorkspace = findViewById(R.id.menuDrawerWorkspace)
        menuDrawerModules = findViewById(R.id.menuDrawerModules)
        tvDrawerModulesBadge = findViewById(R.id.tvDrawerModulesBadge)
        menuDrawerSave = findViewById(R.id.menuDrawerSave)
        menuDrawerSaveAs = findViewById(R.id.menuDrawerSaveAs)
        menuDrawerInstructions = findViewById(R.id.menuDrawerInstructions)
        menuDrawerAbout = findViewById(R.id.menuDrawerAbout)
        menuDrawerSettings = findViewById(R.id.menuDrawerSettings)

        val classroomPrefs = getSharedPreferences("simurelay_classroom", android.content.Context.MODE_PRIVATE)
        lastClassroomServerUrl = classroomPrefs.getString("server_url", null)
        studentName = classroomPrefs.getString("student_name", null)
        studentPresensi = classroomPrefs.getString("student_presensi", null)
        isClassroomExamMode = classroomPrefs.getBoolean("is_exam_mode", false)

        // Undo, Redo & Theme Toggle
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnToggleTheme = findViewById(R.id.btnToggleTheme)
        ivToolbarThemeIcon = findViewById(R.id.ivToolbarThemeIcon)
        btnWorkspaceThemeToggle = findViewById(R.id.btnWorkspaceThemeToggle)
        updateUndoRedoButtons(false, false)

        canvasView = findViewById(R.id.canvasView)
        btnToggleLibrary = findViewById(R.id.btnToggleLibrary)
        ivToolbarLibraryIcon = findViewById(R.id.ivToolbarLibraryIcon)
        tvToolbarLibraryLabel = findViewById(R.id.tvToolbarLibraryLabel)
        btnRun = findViewById(R.id.btnRun)
        btnStop = findViewById(R.id.btnStop)
        btnToolbarMore = findViewById(R.id.btnToolbarMore)
        appBarLayout = findViewById(R.id.appBarLayout)

        // Visual Library Panel
        panelLibrary = findViewById(R.id.panelLibrary)
        spinnerCategory = findViewById(R.id.spinnerCategory)
        btnCloseLibrary = findViewById(R.id.btnCloseLibrary)
        rvComponentLibrary = findViewById(R.id.rvComponentLibrary)

        // Floating Pill
        floatingPill = findViewById(R.id.floatingPill)
        pillLabel = findViewById(R.id.pillLabel)
        pillSwitchOptions = findViewById(R.id.pillSwitchOptions)
        pillEditControls = findViewById(R.id.pillEditControls)
        btnPos1 = findViewById(R.id.btnPos1)
        btnPos0 = findViewById(R.id.btnPos0)
        btnPos2 = findViewById(R.id.btnPos2)
        btnRotate = findViewById(R.id.btnRotate)
        btnTag = findViewById(R.id.btnTag)
        btnDelete = findViewById(R.id.btnDelete)

        // Zoom Controls
        cardZoomWidget = findViewById(R.id.cardZoomWidget)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)

        cardShortCircuit = findViewById(R.id.cardShortCircuit)
        tvAlertShortCircuit = findViewById(R.id.tvAlertShortCircuit)
        btnCloseAlert = findViewById(R.id.btnCloseAlert)
        fabAdd = findViewById(R.id.fabAdd)

        // Workspace Overlay (Gambar 2)
        layoutWorkspaceOverlay = findViewById(R.id.layoutWorkspaceOverlay)
        tvWorkspaceHeaderTitle = findViewById(R.id.tvWorkspaceHeaderTitle)
        btnWorkspaceBack = findViewById(R.id.btnWorkspaceBack)
        btnNewProject = findViewById(R.id.btnNewProject)
        btnWorkspaceImport = findViewById(R.id.btnWorkspaceImport)
        btnWorkspaceScanQr = findViewById(R.id.btnWorkspaceScanQr)
        btnWorkspaceMore = findViewById(R.id.btnWorkspaceMore)
        btnExitFullscreen = findViewById(R.id.btnExitFullscreen)
        rvWorkspaceProjects = findViewById(R.id.rvWorkspaceProjects)
        layoutInlineModulesPage = findViewById(R.id.layoutInlineModulesPage)
        tabWorkspace = findViewById(R.id.tabWorkspace)
        tabModules = findViewById(R.id.tabModules)
        pillTabModules = findViewById(R.id.pillTabModules)
        ivTabModulesIcon = findViewById(R.id.ivTabModulesIcon)
        tvTabModulesLabel = findViewById(R.id.tvTabModulesLabel)
        tabExamples = findViewById(R.id.tabExamples)
        pillTabWorkspace = findViewById(R.id.pillTabWorkspace)
        pillTabExamples = findViewById(R.id.pillTabExamples)
        ivTabWorkspaceIcon = findViewById(R.id.ivTabWorkspaceIcon)
        ivTabExamplesIcon = findViewById(R.id.ivTabExamplesIcon)
        tvTabWorkspaceLabel = findViewById(R.id.tvTabWorkspaceLabel)
        tvTabExamplesLabel = findViewById(R.id.tvTabExamplesLabel)

        // Startup Loading Motion Overlay
        layoutLoadingOverlay = findViewById(R.id.layoutLoadingOverlay)
        webViewLoading = findViewById(R.id.webViewLoading)
    }

    private fun setupLoadingScreen() {
        webViewLoading.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            setBackgroundColor(Color.parseColor("#01143a"))
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onLoadingFinished() {
                    runOnUiThread {
                        finishLoadingScreen()
                    }
                }
            }, "AndroidApp")
            loadUrl("file:///android_asset/loading/index.html")
        }
    }

    private fun finishLoadingScreen() {
        if (!::layoutLoadingOverlay.isInitialized || layoutLoadingOverlay.visibility != View.VISIBLE) return
        layoutLoadingOverlay.animate()
            .alpha(0f)
            .setDuration(450)
            .withEndAction {
                layoutLoadingOverlay.visibility = View.GONE
                (layoutLoadingOverlay.parent as? ViewGroup)?.removeView(layoutLoadingOverlay)
                openWorkspaceOverlay()
            }
            .start()
    }

    private fun setupListeners() {
        // Buka Slide-in Left Drawer (Gambar 1)
        btnOpenMenu.setOnClickListener {
            findViewById<android.widget.ScrollView>(R.id.leftDrawerScrollView)?.scrollTo(0, 0)
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Drawer Menu Click Listeners
        menuDrawerWorkspace.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            handleExitCanvasToWorkspace()
        }

        menuDrawerModules?.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showPracticalModulesDialog()
        }
        updateDrawerModulesBadge()

        menuDrawerSave.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            saveCurrentProject()
        }

        menuDrawerSaveAs.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showSaveAsDialog()
        }

        menuDrawerInstructions.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showInstructionsDialog()
        }

        menuDrawerAbout.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showAboutDialog()
        }

        menuDrawerSettings.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            showSettingsDialog()
        }

        // Undo & Redo Click Handlers (Gambar 1)
        btnUndo.setOnClickListener {
            canvasView.undo()
        }

        btnRedo.setOnClickListener {
            canvasView.redo()
        }

        // 1-Tap Theme Toggle (Dark CAD <-> Light Paper)
        btnToggleTheme?.setOnClickListener {
            toggleAppTheme()
        }

        btnWorkspaceThemeToggle?.setOnClickListener {
            toggleAppTheme()
        }

        // Toggle Visual Component Library
        btnToggleLibrary.setOnClickListener {
            toggleLibraryPanel()
        }


        btnCloseLibrary.setOnClickListener {
            closeLibraryPanel()
        }

        // Run Simulation
        btnRun.setOnClickListener {
            btnRun.visibility = View.GONE
            btnStop.visibility = View.VISIBLE
            cardShortCircuit.visibility = View.GONE
            closeLibraryPanel()

            val prevSelectedId = canvasView.selectedElementId
            canvasView.startSimulation()

            // Otomatis Pop Up Panel Pekerjaan saat Running
            val targetSwitch = resolveTargetSwitchForRunning(prevSelectedId)
            if (targetSwitch != null) {
                canvasView.selectedElementId = targetSwitch.id
                canvasView.invalidate()
                updatePill(targetSwitch)
            } else {
                floatingPill.visibility = View.GONE
                updateFabPosition(false)
            }
        }

        // Stop Simulation
        btnStop.setOnClickListener {
            stopSimulationIfRunning(restoreEditPill = true)
        }

        // More Options Menu (Gambar 5 Toolbar)
        btnToolbarMore.setOnClickListener { anchor ->
            showToolbarMoreMenu(anchor)
        }

        // Tombol Keluar Layar Penuh (Pojok Kiri Atas - Gambar 4)
        btnExitFullscreen.setOnClickListener {
            exitFullscreenMode()
        }

        // Import Project Button di Workspace Overlay
        btnWorkspaceImport.setOnClickListener {
            launchImportPicker()
        }

        btnWorkspaceScanQr?.setOnClickListener {
            launchQrScanner()
        }

        // Zoom In & Out
        btnZoomIn.setOnClickListener { canvasView.zoomIn() }
        btnZoomOut.setOnClickListener { canvasView.zoomOut() }

        // Canvas Interaction Callback
        canvasView.interactionListener = object : NativeCanvasView.OnCanvasInteractionListener {
            override fun onElementSelected(element: NativeElement?) {
                if (element != null) {
                    lastSelectedElementId = element.id
                    if (isControllableSwitch(element.svgData)) {
                        lastSelectedSwitchId = element.id
                    }
                }
                updatePill(element)
            }

            override fun onConnectorSelected(connector: NativeConnector?) {
                updatePillForConnector(connector)
            }

            override fun onSwitchClicked(element: NativeElement) {
                lastSelectedElementId = element.id
                lastSelectedSwitchId = element.id
                updatePill(element)
            }

            private fun refreshSimulationAlertBanner() {
                val res = canvasView.simResult
                when {
                    res?.shortCircuit == true -> {
                        cardShortCircuit.setCardBackgroundColor(Color.parseColor("#f0dc2626"))
                        tvAlertShortCircuit.text = if (res.isDcOverheat) {
                            "DC OVERHEAT! Kutub (+) dan (-) terhubung tanpa beban"
                        } else {
                            "SHORT CIRCUIT! Periksa fasa & netral pada jalur oranye"
                        }
                        cardShortCircuit.visibility = View.VISIBLE
                    }
                    res?.isEarthLeakage == true -> {
                        cardShortCircuit.setCardBackgroundColor(Color.parseColor("#f0ea580c"))
                        tvAlertShortCircuit.text = "ARUS BOCOR KE TANAH (PE/GND)! Periksa isolasi fasa"
                        cardShortCircuit.visibility = View.VISIBLE
                    }
                    res?.trippedEarthLeakageTags?.isNotEmpty() == true -> {
                        val tags = res.trippedEarthLeakageTags.joinToString(", ")
                        cardShortCircuit.setCardBackgroundColor(Color.parseColor("#d97706"))
                        tvAlertShortCircuit.text = "ELCB/RCCB TRIP ($tags)! Arus bocor ke tanah terdeteksi"
                        cardShortCircuit.visibility = View.VISIBLE
                    }
                    res?.trippedBreakerTags?.isNotEmpty() == true -> {
                        val tags = res.trippedBreakerTags.joinToString(", ")
                        cardShortCircuit.setCardBackgroundColor(Color.parseColor("#d97706"))
                        tvAlertShortCircuit.text = "BREAKER TRIP ($tags)! Hubung singkat terdeteksi — periksa rangkaian"
                        cardShortCircuit.visibility = View.VISIBLE
                    }
                    res?.isFuseBlown == true -> {
                        cardShortCircuit.setCardBackgroundColor(Color.parseColor("#d97706"))
                        tvAlertShortCircuit.text = "FUSE TERPUTUS! Hubung singkat terdeteksi — perbaiki di properti"
                        cardShortCircuit.visibility = View.VISIBLE
                    }
                    else -> {
                        cardShortCircuit.visibility = View.GONE
                        cardShortCircuit.setCardBackgroundColor(Color.parseColor("#f0dc2626"))
                    }
                }
            }

            override fun onShortCircuit(isShort: Boolean, isDcOverheat: Boolean) {
                refreshSimulationAlertBanner()
            }

            override fun onFuseBlown(isBlown: Boolean) {
                refreshSimulationAlertBanner()
            }

            override fun onBreakerTripped(shortTags: List<String>, earthTags: List<String>, isEarthLeakageActive: Boolean) {
                refreshSimulationAlertBanner()
            }

            override fun onUndoRedoStateChanged(canUndo: Boolean, canRedo: Boolean) {
                updateUndoRedoButtons(canUndo, canRedo)
            }

            override fun onCircuitModified() {
                triggerAutoSaveIfEnabled(showFeedback = false)
            }

            override fun onTeacherLockedMoveAttempt() {
                // Hening tanpa popup putih mengganggu, visual gembok sudah jelas di kanvas
            }

            override fun onLockedMoveAttempt() {
                // Hening tanpa popup putih mengganggu, visual gembok sudah jelas di kanvas
            }
        }

        // Pill Action Buttons
        btnRotate.setOnClickListener {
            val elId = canvasView.selectedElementId ?: return@setOnClickListener
            val el = canvasView.elements.find { it.id == elId } ?: return@setOnClickListener
            if (el.teacherLocked || el.locked) {
                return@setOnClickListener
            }
            canvasView.pushUndoState()
            el.rotation = (el.canonicalRotation + 1) % 4
            canvasView.invalidate()
            triggerAutoSaveIfEnabled()
        }

        btnDelete.setOnClickListener {
            if (canvasView.selectedConnectorId != null) {
                val connId = canvasView.selectedConnectorId!!
                canvasView.pushUndoState()
                canvasView.connectors.removeAll { it.id == connId }
                canvasView.selectedConnectorId = null
                floatingPill.visibility = View.GONE
                updateFabPosition(false)
                canvasView.invalidate()
                triggerAutoSaveIfEnabled()
            } else if (canvasView.selectedElementId != null) {
                val elId = canvasView.selectedElementId!!
                val el = canvasView.elements.find { it.id == elId }
                if (el != null && (el.teacherLocked || el.locked)) {
                    return@setOnClickListener
                }
                canvasView.pushUndoState()
                canvasView.elements.removeAll { it.id == elId }
                canvasView.connectors.removeAll { it.elem1Id == elId || it.elem2Id == elId }
                canvasView.selectedElementId = null
                floatingPill.visibility = View.GONE
                updateFabPosition(false)
                canvasView.invalidate()
                triggerAutoSaveIfEnabled()
            }
        }

        btnTag.setOnClickListener {
            val elId = canvasView.selectedElementId ?: return@setOnClickListener
            val el = canvasView.elements.find { it.id == elId } ?: return@setOnClickListener
            showComponentPropertiesDialog(el)
        }

        // Switch Position Actions via mapped index
        btnPos0.setOnClickListener { onSwitchButtonTapped(0) }
        btnPos1.setOnClickListener { onSwitchButtonTapped(1) }
        btnPos2.setOnClickListener { onSwitchButtonTapped(2) }

        btnCloseAlert.setOnClickListener {
            cardShortCircuit.visibility = View.GONE
        }

        // FAB Toggles Visual Library
        fabAdd.setOnClickListener {
            toggleLibraryPanel()
        }
    }

    private fun setupVisualLibrary() {
        val categories = listOf(
            "Semua Komponen (All)",
            "Sumber Daya (Sources)",
            "Proteksi / MCB (Protections)",
            "Saklar & Tombol (Switches)",
            "Kontaktor (Contactors)",
            "Koil & Timer (Coils)",
            "Kontak Bantu (Auxiliaries)",
            "Motor Listrik (Motors)",
            "Beban & Lampu (Loads)",
            "Terminal & Jalur (Others)"
        )

        val spinnerAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, categories) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                if (v is TextView) {
                    val isLight = SettingsManager.isLightTheme(this@MainActivity)
                    v.setTextColor(if (isLight) Color.parseColor("#0f172a") else Color.WHITE)
                    v.textSize = 13f
                }
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                if (v is TextView) {
                    val isLight = SettingsManager.isLightTheme(this@MainActivity)
                    v.setTextColor(if (isLight) Color.parseColor("#0f172a") else Color.parseColor("#f1f5f9"))
                    v.setBackgroundColor(if (isLight) Color.WHITE else Color.parseColor("#1c1c28"))
                    v.textSize = 13f
                }
                return v
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCategory.adapter = spinnerAdapter

        val allComponents = NativeComponentRegistry.getAll()
        visualAdapter = VisualComponentAdapter(allComponents) { selectedDef ->
            onComponentChosenFromLibrary(selectedDef)
        }

        val gridManager = GridLayoutManager(this, 3).apply {
            spanSizeLookup = visualAdapter.getSpanSizeLookup(3)
        }
        rvComponentLibrary.layoutManager = gridManager
        rvComponentLibrary.adapter = visualAdapter

        spinnerCategory.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val filterKey = when (position) {
                    1 -> "sources"
                    2 -> "protections"
                    3 -> "switches"
                    4 -> "contactors"
                    5 -> "coils"
                    6 -> "auxiliaries"
                    7 -> "motors"
                    8 -> "loads"
                    9 -> "others"
                    else -> "all"
                }
                visualAdapter.setCategoryFilter(filterKey)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun toggleLibraryPanel() {
        if (btnExitFullscreen.visibility == View.VISIBLE) {
            return
        }
        if (isLibraryOpen) {
            closeLibraryPanel()
        } else {
            openLibraryPanel()
        }
    }

    private fun openLibraryPanel() {
        if (btnExitFullscreen.visibility == View.VISIBLE) {
            return
        }
        isLibraryOpen = true
        panelLibrary.visibility = View.VISIBLE
        rvComponentLibrary.scrollToPosition(0)
        tvToolbarLibraryLabel.text = "CLOSE"
        ivToolbarLibraryIcon.setImageResource(R.drawable.ic_close)
        val isLight = SettingsManager.isLightTheme(this)
        btnToggleLibrary.setBackgroundColor(Color.parseColor(if (isLight) "#2563eb" else "#1e3a8a"))
        tvToolbarLibraryLabel.setTextColor(Color.WHITE)
        ivToolbarLibraryIcon.setColorFilter(Color.WHITE)
        fabAdd.setImageResource(R.drawable.ic_fab_minus)
        fabAdd.contentDescription = "Tutup Library"
    }

    private fun closeLibraryPanel() {
        isLibraryOpen = false
        panelLibrary.visibility = View.GONE
        tvToolbarLibraryLabel.text = "LIBRARY"
        ivToolbarLibraryIcon.setImageResource(R.drawable.ic_toolbar_library)
        btnToggleLibrary.background = null
        val isLight = SettingsManager.isLightTheme(this)
        val fgColor = if (isLight) Color.parseColor("#1e293b") else Color.WHITE
        tvToolbarLibraryLabel.setTextColor(fgColor)
        ivToolbarLibraryIcon.setColorFilter(fgColor)
        fabAdd.setImageResource(R.drawable.ic_fab_plus)
        fabAdd.contentDescription = "Buka Library"
    }

    private fun updateFabPosition(pillVisible: Boolean) {
        val isFullscreen = btnExitFullscreen.visibility == View.VISIBLE
        val shiftY = if (pillVisible) -76f * resources.displayMetrics.density else 0f
        if (isFullscreen) {
            fabAdd.visibility = View.GONE
            val zoomShift = shiftY + 60f * resources.displayMetrics.density
            cardZoomWidget.animate()
                .translationY(zoomShift)
                .setDuration(220)
                .start()
        } else {
            fabAdd.visibility = View.VISIBLE
            fabAdd.animate()
                .translationY(shiftY)
                .setDuration(220)
                .start()
            cardZoomWidget.animate()
                .translationY(shiftY)
                .setDuration(220)
                .start()
        }
    }

    private fun onComponentChosenFromLibrary(def: ComponentDef) {
        if (canvasView.isSimulating) {
            return
        }
        val center = canvasView.screenToWorld(canvasView.width / 2f, canvasView.height / 2f)
        val snappedX = canvasView.snap(center.x)
        val snappedY = canvasView.snap(center.y)

        canvasView.pushUndoState()
        val nextId = (canvasView.elements.maxOfOrNull { it.id } ?: 0) + 1
        val newElement = NativeElement(
            id = nextId,
            svgData = def.type,
            tag = def.defaultTag,
            comment = "",
            x = snappedX,
            y = snappedY
        )

        canvasView.elements.add(newElement)
        canvasView.selectedElementId = newElement.id
        canvasView.invalidate()
        updatePill(newElement)
        triggerAutoSaveIfEnabled()
    }

    private fun onSwitchButtonTapped(buttonIndex: Int) {
        val el = activeSwitchElement ?: return
        val isSwitch3p0 = (el.svgData == "switch3p0")
        val isSwitch3p = (el.svgData == "switch3p")
        val isCON = el.svgData.contains("CON", ignoreCase = true)

        val targetPos = when {
            isSwitch3p0 -> when (buttonIndex) {
                1 -> 0 // Pos 1 (mainPath1)
                0 -> 1 // Pos 0 (mainPath2)
                else -> 2 // Pos 2 (mainPath3)
            }
            isSwitch3p -> when (buttonIndex) {
                1 -> 0 // Pos 1 (mainPath1)
                0 -> 1 // Pos 2 (mainPath2)
                else -> 2 // Pos 3 (mainPath3)
            }
            isCON -> when (buttonIndex) {
                1 -> 0 // Pos 1 (mainPath1)
                else -> 1 // Pos 2 (mainPath2)
            }
            else -> when (buttonIndex) {
                1 -> 1 // ON (mainPath2)
                else -> 0 // OFF (mainPath1)
            }
        }
        changeSwitchPos(targetPos)
    }

    private fun changeSwitchPos(pos: Int) {
        val el = activeSwitchElement ?: return
        val isCartridgeFuse = el.svgData.startsWith("fuse") && !el.svgData.startsWith("fuseSwitch")
        val mappedFrame = if (isCartridgeFuse) (if (pos == 1) 0 else 1) else pos
        canvasView.soundManager.playSwitchClick(floatingPill)
        canvasView.engine.setSwitchPosition(el.id, pos)
        if (isCartridgeFuse && pos == 1) {
            canvasView.engine.fuseBlownElements.remove(el.id)
        }
        el.frameIndex = mappedFrame // Update visual frame canvas seketika
        updateSwitchButtonsState(el.svgData, pos)
        if (canvasView.isSimulating) {
            canvasView.stepSimulation()
        } else {
            canvasView.invalidate()
        }
    }

    private fun updateSwitchButtonsState(svgData: String, pos: Int) {
        val onActiveBg = Color.parseColor("#10b981") // Hijau emerald tegas untuk ON
        val offActiveBg = Color.parseColor("#ef4444") // Merah tegas untuk OFF
        val inactiveBg = Color.parseColor("#1e293b") // Slate gelap netral
        val selectorActiveBg = Color.parseColor("#0284c7") // Biru cyan untuk Pos I / Pos II

        val isSwitch3p0 = (svgData == "switch3p0")
        val isSwitch3p = (svgData == "switch3p")
        val isCON = svgData.contains("CON", ignoreCase = true)

        when {
            isSwitch3p0 -> {
                btnPos1.backgroundTintList = ColorStateList.valueOf(if (pos == 0) selectorActiveBg else inactiveBg)
                btnPos0.backgroundTintList = ColorStateList.valueOf(if (pos == 1) offActiveBg else inactiveBg)
                btnPos2.backgroundTintList = ColorStateList.valueOf(if (pos == 2) selectorActiveBg else inactiveBg)
                btnPos1.setTextColor(if (pos == 0) Color.WHITE else Color.parseColor("#94a3b8"))
                btnPos0.setTextColor(if (pos == 1) Color.WHITE else Color.parseColor("#94a3b8"))
                btnPos2.setTextColor(if (pos == 2) Color.WHITE else Color.parseColor("#94a3b8"))
            }
            isSwitch3p -> {
                btnPos1.backgroundTintList = ColorStateList.valueOf(if (pos == 0) selectorActiveBg else inactiveBg)
                btnPos0.backgroundTintList = ColorStateList.valueOf(if (pos == 1) selectorActiveBg else inactiveBg)
                btnPos2.backgroundTintList = ColorStateList.valueOf(if (pos == 2) selectorActiveBg else inactiveBg)
                btnPos1.setTextColor(if (pos == 0) Color.WHITE else Color.parseColor("#94a3b8"))
                btnPos0.setTextColor(if (pos == 1) Color.WHITE else Color.parseColor("#94a3b8"))
                btnPos2.setTextColor(if (pos == 2) Color.WHITE else Color.parseColor("#94a3b8"))
            }
            isCON -> {
                val isPos1 = (pos == 0)
                btnPos1.backgroundTintList = ColorStateList.valueOf(if (isPos1) selectorActiveBg else inactiveBg)
                btnPos0.backgroundTintList = ColorStateList.valueOf(if (!isPos1) selectorActiveBg else inactiveBg)
                btnPos1.setTextColor(if (isPos1) Color.WHITE else Color.parseColor("#94a3b8"))
                btnPos0.setTextColor(if (!isPos1) Color.WHITE else Color.parseColor("#94a3b8"))
            }
            else -> {
                // Standar Breaker & Switch (Pos 0 = OFF, Pos 1 = ON)
                val isOff = (pos == 0)
                btnPos0.backgroundTintList = ColorStateList.valueOf(if (isOff) offActiveBg else inactiveBg)
                btnPos1.backgroundTintList = ColorStateList.valueOf(if (!isOff) onActiveBg else inactiveBg)
                btnPos0.setTextColor(if (isOff) Color.WHITE else Color.parseColor("#94a3b8"))
                btnPos1.setTextColor(if (!isOff) Color.WHITE else Color.parseColor("#94a3b8"))
            }
        }
    }

    private fun isControllableSwitch(svgData: String): Boolean {
        val def = NativeComponentRegistry.get(svgData)
        val isPushButton = NativeComponentRegistry.isPushButton(svgData)
        return !isPushButton && (def?.category == "switches" || def?.category == "protections")
    }

    private fun resolveTargetSwitchForRunning(preferredId: Int? = null): NativeElement? {
        val elements = canvasView.elements
        if (preferredId != null) {
            val pref = elements.find { it.id == preferredId && isControllableSwitch(it.svgData) }
            if (pref != null) {
                lastSelectedSwitchId = pref.id
                lastSelectedElementId = pref.id
                return pref
            }
        }
        val cur = elements.find { it.id == canvasView.selectedElementId && isControllableSwitch(it.svgData) }
        if (cur != null) {
            lastSelectedSwitchId = cur.id
            lastSelectedElementId = cur.id
            return cur
        }
        if (lastSelectedSwitchId != null) {
            val lastSwitch = elements.find { it.id == lastSelectedSwitchId && isControllableSwitch(it.svgData) }
            if (lastSwitch != null) {
                return lastSwitch
            }
        }
        val firstSwitch = elements.firstOrNull { isControllableSwitch(it.svgData) }
        if (firstSwitch != null) {
            lastSelectedSwitchId = firstSwitch.id
            lastSelectedElementId = firstSwitch.id
            return firstSwitch
        }
        return null
    }

    private fun resolveTargetElementForEdit(preferredId: Int? = null): NativeElement? {
        val elements = canvasView.elements
        if (preferredId != null) {
            val pref = elements.find { it.id == preferredId }
            if (pref != null) {
                lastSelectedElementId = pref.id
                if (isControllableSwitch(pref.svgData)) lastSelectedSwitchId = pref.id
                return pref
            }
        }
        val cur = elements.find { it.id == canvasView.selectedElementId }
        if (cur != null) {
            lastSelectedElementId = cur.id
            if (isControllableSwitch(cur.svgData)) lastSelectedSwitchId = cur.id
            return cur
        }
        if (lastSelectedElementId != null) {
            val lastEl = elements.find { it.id == lastSelectedElementId }
            if (lastEl != null) return lastEl
        }
        val firstEl = elements.firstOrNull()
        if (firstEl != null) {
            lastSelectedElementId = firstEl.id
            if (isControllableSwitch(firstEl.svgData)) lastSelectedSwitchId = firstEl.id
            return firstEl
        }
        return null
    }

    private fun updatePill(element: NativeElement?) {
        if (element == null) {
            floatingPill.visibility = View.GONE
            activeSwitchElement = null
            updateFabPosition(false)
            return
        }

        lastSelectedElementId = element.id
        if (isControllableSwitch(element.svgData)) {
            lastSelectedSwitchId = element.id
        }

        if (canvasView.isSimulating && NativeComponentRegistry.isPushButton(element.svgData)) {
            floatingPill.visibility = View.GONE
            activeSwitchElement = null
            updateFabPosition(false)
            return
        }

        activeSwitchElement = element
        val isText = (element.svgData == "text")
        val label = when {
            isText && element.comment.isNotEmpty() && element.comment != "null" -> element.comment
            isText && element.tag.isNotEmpty() && element.tag != "null" -> element.tag
            isText -> "Teks CAD"
            element.tag.isNotEmpty() && element.tag != "null" -> element.tag
            else -> element.svgData
        }
        pillLabel.text = label

        val def = NativeComponentRegistry.get(element.svgData)
        val isPushButton = NativeComponentRegistry.isPushButton(element.svgData)
        val isOverload = element.svgData.startsWith("overload")
        val isSwitch = !isPushButton && (def?.category == "switches" || def?.category == "protections")

        val isSimulating = canvasView.isSimulating

        if (isSimulating) {
            // Ketika simulasi BERJALAN:
            // Tampilkan opsi pekerjaan komponen sakelar/pemutus
            if (isSwitch) {
                pillSwitchOptions.visibility = View.VISIBLE
                val isCartridgeFuse = element.svgData.startsWith("fuse") && !element.svgData.startsWith("fuseSwitch")
                val curPos = canvasView.engine.manualStates[element.id] ?: if (element.svgData == "switch3p0" || isCartridgeFuse) 1 else 0
                val isSwitch3p0 = (element.svgData == "switch3p0")
                val isSwitch3p = (element.svgData == "switch3p")
                val isCON = element.svgData.contains("CON", ignoreCase = true)

                when {
                    isSwitch3p0 -> {
                        btnPos1.text = "Pos 1"
                        btnPos0.text = "Pos 0"
                        btnPos2.text = "Pos 2"
                        btnPos2.visibility = View.VISIBLE
                    }
                    isSwitch3p -> {
                        btnPos1.text = "Pos 1"
                        btnPos0.text = "Pos 2"
                        btnPos2.text = "Pos 3"
                        btnPos2.visibility = View.VISIBLE
                    }
                    isCON -> {
                        btnPos1.text = "Pos 1"
                        btnPos0.text = "Pos 2"
                        btnPos2.visibility = View.GONE
                    }
                    isOverload -> {
                        btnPos1.text = "TRIP"
                        btnPos0.text = "NORMAL"
                        btnPos2.visibility = View.GONE
                    }
                    else -> {
                        btnPos1.text = "ON"
                        btnPos0.text = "OFF"
                        btnPos2.visibility = View.GONE
                    }
                }
                updateSwitchButtonsState(element.svgData, curPos)
                pillEditControls.visibility = View.GONE
                floatingPill.visibility = View.VISIBLE
                updateFabPosition(true)
            } else {
                pillSwitchOptions.visibility = View.GONE
                pillEditControls.visibility = View.GONE
                floatingPill.visibility = View.GONE
                updateFabPosition(false)
            }
        } else {
            // Ketika simulasi TIDAK BERJALAN (Mode CAD / Desain):
            // Opsi pekerjaan sakelar TIDAK dimasukkan ke floating pill di bawah komponen agar tidak terpotong.
            // Opsi pekerjaan sakelar dimasukkan ke dalam panel "TAG" (properti komponen).
            pillSwitchOptions.visibility = View.GONE
            pillEditControls.visibility = View.VISIBLE
            val isLocked = element.teacherLocked || element.locked
            btnRotate.visibility = if (isLocked) View.GONE else View.VISIBLE
            btnTag.visibility = View.VISIBLE
            btnDelete.visibility = if (isLocked) View.GONE else View.VISIBLE
            floatingPill.visibility = View.VISIBLE
            updateFabPosition(true)
        }
    }

    private fun showComponentPropertiesDialog(el: NativeElement) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_modern_properties, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val dm = resources.displayMetrics
        val isLandscape = dm.widthPixels > dm.heightPixels
        val dialogWidth = if (isLandscape) {
            (dm.widthPixels * 0.70f).toInt().coerceIn((420 * dm.density).toInt(), (580 * dm.density).toInt())
        } else {
            (dm.widthPixels * 0.92f).toInt().coerceAtMost((480 * dm.density).toInt())
        }
        val maxAvailableHeight = (dm.heightPixels * (if (isLandscape) 0.94f else 0.82f)).toInt()

        val scrollView = dialogView.findViewById<ScrollView>(R.id.scrollPropBody)
        val cardPropRoot = dialogView.findViewById<View>(R.id.cardPropRoot)
        val layoutPropContainer = dialogView.findViewById<LinearLayout>(R.id.layoutPropContainer)

        val isTimerCoil = el.svgData.contains("Delay", ignoreCase = true) || el.svgData == "coilFlasher" || el.svgData == "flasher"
        val isTextComp = (el.svgData == "text")
        val isLampComp = (el.svgData == "lamp" || el.svgData == "lampBlink")
        val def = NativeComponentRegistry.get(el.svgData)
        val isPushButton = NativeComponentRegistry.isPushButton(el.svgData)
        val isOverload = el.svgData.startsWith("overload")
        val isSwitch = !isPushButton && (def?.category == "switches" || def?.category == "protections")

        val ivIcon = dialog.findViewById<ImageView>(R.id.ivPropHeaderIcon)
        val tvTitle = dialog.findViewById<TextView>(R.id.tvPropHeaderTitle)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseProp)
        val tvTagLabel = dialog.findViewById<TextView>(R.id.tvPropTagLabel)
        val etTag = dialog.findViewById<EditText>(R.id.etPropTag)
        val layoutFontSize = dialog.findViewById<View>(R.id.layoutPropFontSize)
        val layoutLampColor = dialog.findViewById<View>(R.id.layoutPropLampColor)
        val layoutLampRow = dialog.findViewById<LinearLayout>(R.id.layoutPropLampRow)
        val layoutTimer = dialog.findViewById<View>(R.id.layoutPropTimer)
        val etTimer = dialog.findViewById<EditText>(R.id.etPropTimer)
        val layoutComment = dialog.findViewById<View>(R.id.layoutPropComment)
        val etComment = dialog.findViewById<EditText>(R.id.etPropComment)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelProp)
        val btnSave = dialog.findViewById<Button>(R.id.btnSaveProp)
        val layoutPropTerminalTags = dialog.findViewById<View>(R.id.layoutPropTerminalTags)
        val layoutPropTerminalRows = dialog.findViewById<LinearLayout>(R.id.layoutPropTerminalRows)
        val terminalInputs = mutableListOf<Pair<Int, EditText>>()

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnCancel?.setOnClickListener { dialog.dismiss() }

        when {
            isTextComp -> {
                ivIcon?.setImageResource(R.drawable.ic_edit)
                tvTitle?.text = "Properti Label Teks"
                tvTagLabel?.text = "Teks Label CAD:"
                val textVal = if (el.comment.isNotEmpty() && el.comment != "null") el.comment else if (el.tag.isNotEmpty() && el.tag != "null") el.tag else ""
                etTag?.setText(textVal)
                etTag?.hint = "Tulis teks label CAD..."
                layoutComment?.visibility = View.GONE
            }
            isTimerCoil -> {
                ivIcon?.setImageResource(R.drawable.ic_edit)
                tvTitle?.text = "Pengaturan Koil & Timer"
                tvTagLabel?.text = "Tag / Identitas Koil:"
                etTag?.setText(if (el.tag != "null") el.tag else "")
                etTag?.hint = "Contoh: -KT1"
                layoutComment?.visibility = View.VISIBLE
                etComment?.setText(if (el.comment != "null") el.comment else "")
            }
            isLampComp -> {
                ivIcon?.setImageResource(R.drawable.ic_edit)
                tvTitle?.text = "Properti Lampu Indikator"
                tvTagLabel?.text = "Tag Lampu Indikator:"
                etTag?.setText(if (el.tag != "null") el.tag else "")
                etTag?.hint = "Contoh: -HL1"
                layoutComment?.visibility = View.VISIBLE
                etComment?.setText(if (el.comment != "null") el.comment else "")
            }
            isSwitch -> {
                ivIcon?.setImageResource(R.drawable.ic_edit)
                tvTitle?.text = "Properti Sakelar & Kontak"
                tvTagLabel?.text = "Tag Sakelar:"
                etTag?.setText(if (el.tag != "null") el.tag else "")
                etTag?.hint = "Contoh: -S1, -QM1, -Q1"
                layoutComment?.visibility = View.VISIBLE
                etComment?.setText(if (el.comment != "null") el.comment else "")
            }
            else -> {
                ivIcon?.setImageResource(R.drawable.ic_edit)
                tvTitle?.text = "Properti Komponen"
                tvTagLabel?.text = "Tag / Identitas Komponen:"
                etTag?.setText(if (el.tag != "null") el.tag else "")
                etTag?.hint = "Contoh: -KM1, -SB1, -Q1"
                layoutComment?.visibility = View.VISIBLE
                etComment?.setText(if (el.comment != "null") el.comment else "")
            }
        }

        // Font Size handling
        val isLightProp = SettingsManager.isLightTheme(this)
        val propInactiveBg = Color.parseColor(if (isLightProp) "#f1f5f9" else "#262a40")
        val propChipInactiveBg = Color.parseColor(if (isLightProp) "#f1f5f9" else "#1f2338")
        val propInactiveText = Color.parseColor(if (isLightProp) "#475569" else "#94a3b8")

        var selectedFontSize = if (el.fontSize > 0) el.fontSize else 24f
        if (isTextComp) {
            layoutFontSize?.visibility = View.VISIBLE
            val fsButtons = listOf(
                Pair(14f, dialog.findViewById<Button>(R.id.btnPropFs14)),
                Pair(20f, dialog.findViewById<Button>(R.id.btnPropFs20)),
                Pair(28f, dialog.findViewById<Button>(R.id.btnPropFs28)),
                Pair(36f, dialog.findViewById<Button>(R.id.btnPropFs36))
            )
            fun updateFsSelection() {
                for ((size, btn) in fsButtons) {
                    val active = (selectedFontSize == size)
                    btn?.backgroundTintList = ColorStateList.valueOf(if (active) Color.parseColor("#7c3aed") else propInactiveBg)
                    btn?.setTextColor(if (active) Color.WHITE else propInactiveText)
                }
            }
            updateFsSelection()
            for ((size, btn) in fsButtons) {
                btn?.setOnClickListener {
                    selectedFontSize = size
                    updateFsSelection()
                }
            }
        } else {
            layoutFontSize?.visibility = View.GONE
        }

        // Text Color & Lamp Color handling
        var selectedLampColor = el.lampColor.ifEmpty { "yellow" }
        var selectedTextColor = el.textColor.ifEmpty { "#93c5fd" }
        val tvLampColorLabel = dialog.findViewById<TextView>(R.id.tvPropLampColorLabel)

        if (isTextComp) {
            layoutLampColor?.visibility = View.VISIBLE
            tvLampColorLabel?.text = "Pilihan Warna Teks CAD:"
            tvLampColorLabel?.setTextColor(Color.parseColor("#38bdf8"))
            layoutLampRow?.removeAllViews()

            val textColors = listOf(
                Pair("Cyan", "#93c5fd"),
                Pair("Putih", "#f8fafc"),
                Pair("Kuning", "#fbbf24"),
                Pair("Merah", "#ef4444"),
                Pair("Hijau", "#22c55e"),
                Pair("Abu-abu", "#94a3b8")
            )
            val colorButtons = mutableListOf<Button>()
            for ((label, hex) in textColors) {
                val btnCol = Button(this).apply {
                    text = label
                    textSize = 8.5f
                    isAllCaps = false
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minimumWidth = 0
                    layoutParams = LinearLayout.LayoutParams(0, (34 * resources.displayMetrics.density).toInt(), 1f).apply {
                        setMargins(2, 0, 2, 0)
                    }
                }
                val isCur = selectedTextColor.equals(hex, ignoreCase = true)
                btnCol.backgroundTintList = ColorStateList.valueOf(if (isCur) Color.parseColor(hex) else Color.parseColor("#1f2338"))
                btnCol.setTextColor(if (isCur) (if (hex == "#f8fafc" || hex == "#fbbf24" || hex == "#93c5fd") Color.BLACK else Color.WHITE) else Color.parseColor(hex))
                btnCol.tag = hex
                btnCol.setOnClickListener {
                    selectedTextColor = hex
                    for (b in colorButtons) {
                        val bHex = b.tag as String
                        val active = bHex.equals(hex, ignoreCase = true)
                        b.backgroundTintList = ColorStateList.valueOf(if (active) Color.parseColor(bHex) else Color.parseColor("#1f2338"))
                        b.setTextColor(if (active) (if (bHex == "#f8fafc" || bHex == "#fbbf24" || bHex == "#93c5fd") Color.BLACK else Color.WHITE) else Color.parseColor(bHex))
                    }
                }
                colorButtons.add(btnCol)
                layoutLampRow?.addView(btnCol)
            }
        } else if (isLampComp) {
            layoutLampColor?.visibility = View.VISIBLE
            tvLampColorLabel?.text = "Pilihan Warna Lampu Indikator:"
            tvLampColorLabel?.setTextColor(Color.parseColor("#fbbf24"))
            layoutLampRow?.removeAllViews()
            val colors = listOf(
                Pair("Putih", "white"),
                Pair("Kuning", "yellow"),
                Pair("Merah", "red"),
                Pair("Hijau", "green"),
                Pair("Biru", "blue")
            )
            val colorHexes = mapOf(
                "white" to "#f8fafc",
                "yellow" to "#fbbf24",
                "red" to "#ef4444",
                "green" to "#22c55e",
                "blue" to "#3b82f6"
            )
            val lampButtons = mutableListOf<Button>()
            for ((label, colKey) in colors) {
                val hex = colorHexes[colKey] ?: "#fbbf24"
                val btnCol = Button(this).apply {
                    text = label
                    textSize = 9.0f // Lebih kecil sesuai permintaan user agar tidak terpotong
                    isAllCaps = false // Huruf normal (Putih, Kuning, Merah, Hijau, Biru) hemat tempat
                    setPadding(0, 0, 0, 0)
                    minWidth = 0
                    minimumWidth = 0
                    layoutParams = LinearLayout.LayoutParams(0, (34 * resources.displayMetrics.density).toInt(), 1f).apply {
                        setMargins(2, 0, 2, 0)
                    }
                }
                val isCur = selectedLampColor.equals(colKey, ignoreCase = true)
                btnCol.backgroundTintList = ColorStateList.valueOf(if (isCur) Color.parseColor(hex) else Color.parseColor("#1f2338"))
                btnCol.setTextColor(if (isCur) (if (colKey == "white" || colKey == "yellow") Color.BLACK else Color.WHITE) else Color.parseColor(hex))
                btnCol.tag = colKey
                btnCol.setOnClickListener {
                    selectedLampColor = colKey
                    for (b in lampButtons) {
                        val cKey = b.tag as String
                        val active = cKey.equals(colKey, ignoreCase = true)
                        val bHex = colorHexes[cKey] ?: "#fbbf24"
                        b.backgroundTintList = ColorStateList.valueOf(if (active) Color.parseColor(bHex) else Color.parseColor("#1f2338"))
                        b.setTextColor(if (active) (if (cKey == "white" || cKey == "yellow") Color.BLACK else Color.WHITE) else Color.parseColor(bHex))
                    }
                }
                lampButtons.add(btnCol)
                layoutLampRow?.addView(btnCol)
            }
        } else {
            layoutLampColor?.visibility = View.GONE
        }

        // Timer Preset handling
        if (isTimerCoil) {
            layoutTimer?.visibility = View.VISIBLE
            etTimer?.setText(String.format(java.util.Locale.US, "%.1f", if (el.presetTime > 0) el.presetTime else 2.0f))
            dialog.findViewById<Button>(R.id.btnPropTimer1)?.setOnClickListener { etTimer?.setText("1.0") }
            dialog.findViewById<Button>(R.id.btnPropTimer2)?.setOnClickListener { etTimer?.setText("2.0") }
            dialog.findViewById<Button>(R.id.btnPropTimer5)?.setOnClickListener { etTimer?.setText("5.0") }
            dialog.findViewById<Button>(R.id.btnPropTimer10)?.setOnClickListener { etTimer?.setText("10.0") }
            dialog.findViewById<Button>(R.id.btnPropTimer30)?.setOnClickListener { etTimer?.setText("30.0") }
        } else {
            layoutTimer?.visibility = View.GONE
        }

        // Switch State / Position handling (Opsi Pekerjaan Sakelar di panel TAG saat tidak running)
        val layoutPropSwitchState = dialog.findViewById<View>(R.id.layoutPropSwitchState)
        val btnPropPos1 = dialog.findViewById<Button>(R.id.btnPropPos1)
        val btnPropPos0 = dialog.findViewById<Button>(R.id.btnPropPos0)
        val btnPropPos2 = dialog.findViewById<Button>(R.id.btnPropPos2)

        val isSwitch3p0 = (el.svgData == "switch3p0")
        val isSwitch3p = (el.svgData == "switch3p")
        val isCON = el.svgData.contains("CON", ignoreCase = true)
        val isCartridgeFuse = el.svgData.startsWith("fuse") && !el.svgData.startsWith("fuseSwitch")
        var selectedSwitchPos = canvasView.engine.manualStates[el.id] ?: if (isSwitch3p0 || isCartridgeFuse) 1 else 0

        val onActiveBg = Color.parseColor("#10b981")
        val offActiveBg = Color.parseColor("#ef4444")
        val inactiveBg = propInactiveBg
        val selectorActiveBg = Color.parseColor("#0284c7")

        fun updatePropSwitchVisuals() {
            when {
                isSwitch3p0 -> {
                    btnPropPos1?.text = "Pos 1"
                    btnPropPos0?.text = "Pos 0"
                    btnPropPos2?.text = "Pos 2"
                    btnPropPos2?.visibility = View.VISIBLE

                    val p1 = (selectedSwitchPos == 0)
                    val p0 = (selectedSwitchPos == 1)
                    val p2 = (selectedSwitchPos == 2)

                    btnPropPos1?.backgroundTintList = ColorStateList.valueOf(if (p1) selectorActiveBg else inactiveBg)
                    btnPropPos0?.backgroundTintList = ColorStateList.valueOf(if (p0) offActiveBg else inactiveBg)
                    btnPropPos2?.backgroundTintList = ColorStateList.valueOf(if (p2) selectorActiveBg else inactiveBg)

                    btnPropPos1?.setTextColor(if (p1) Color.WHITE else propInactiveText)
                    btnPropPos0?.setTextColor(if (p0) Color.WHITE else propInactiveText)
                    btnPropPos2?.setTextColor(if (p2) Color.WHITE else propInactiveText)
                }
                isSwitch3p -> {
                    btnPropPos1?.text = "Pos 1"
                    btnPropPos0?.text = "Pos 2"
                    btnPropPos2?.text = "Pos 3"
                    btnPropPos2?.visibility = View.VISIBLE

                    val p1 = (selectedSwitchPos == 0)
                    val p2 = (selectedSwitchPos == 1)
                    val p3 = (selectedSwitchPos == 2)

                    btnPropPos1?.backgroundTintList = ColorStateList.valueOf(if (p1) selectorActiveBg else inactiveBg)
                    btnPropPos0?.backgroundTintList = ColorStateList.valueOf(if (p2) selectorActiveBg else inactiveBg)
                    btnPropPos2?.backgroundTintList = ColorStateList.valueOf(if (p3) selectorActiveBg else inactiveBg)

                    btnPropPos1?.setTextColor(if (p1) Color.WHITE else propInactiveText)
                    btnPropPos0?.setTextColor(if (p2) Color.WHITE else propInactiveText)
                    btnPropPos2?.setTextColor(if (p3) Color.WHITE else propInactiveText)
                }
                isCON -> {
                    btnPropPos1?.text = "Pos 1"
                    btnPropPos0?.text = "Pos 2"
                    btnPropPos2?.visibility = View.GONE

                    val p1 = (selectedSwitchPos == 0)
                    btnPropPos1?.backgroundTintList = ColorStateList.valueOf(if (p1) selectorActiveBg else inactiveBg)
                    btnPropPos0?.backgroundTintList = ColorStateList.valueOf(if (!p1) selectorActiveBg else inactiveBg)

                    btnPropPos1?.setTextColor(if (p1) Color.WHITE else propInactiveText)
                    btnPropPos0?.setTextColor(if (!p1) Color.WHITE else propInactiveText)
                }
                else -> {
                    btnPropPos0?.text = if (isOverload) "NORMAL" else "OFF / Buka"
                    btnPropPos1?.text = if (isOverload) "TRIP" else "ON / Tutup"
                    btnPropPos2?.visibility = View.GONE

                    val isOff = (selectedSwitchPos == 0)
                    btnPropPos0?.backgroundTintList = ColorStateList.valueOf(if (isOff) offActiveBg else inactiveBg)
                    btnPropPos1?.backgroundTintList = ColorStateList.valueOf(if (!isOff) onActiveBg else inactiveBg)

                    btnPropPos0?.setTextColor(if (isOff) Color.WHITE else propInactiveText)
                    btnPropPos1?.setTextColor(if (!isOff) Color.WHITE else propInactiveText)
                }
            }
        }

        if (isSwitch) {
            layoutPropSwitchState?.visibility = View.VISIBLE
            updatePropSwitchVisuals()

            btnPropPos1?.setOnClickListener {
                selectedSwitchPos = when {
                    isSwitch3p0 || isSwitch3p || isCON -> 0
                    else -> 1 // ON
                }
                updatePropSwitchVisuals()
            }
            btnPropPos0?.setOnClickListener {
                selectedSwitchPos = when {
                    isSwitch3p0 || isSwitch3p -> 1 // Pos 0 atau Pos 2
                    isCON -> 1 // Pos 2
                    else -> 0 // OFF
                }
                updatePropSwitchVisuals()
            }
            btnPropPos2?.setOnClickListener {
                selectedSwitchPos = 2 // Pos 2 atau Pos 3
                updatePropSwitchVisuals()
            }
        } else {
            layoutPropSwitchState?.visibility = View.GONE
        }

        // Terminal Pin Tags handling (Input / Output Customization)
        if (def != null && def.nodes.isNotEmpty() && !isTextComp) {
            layoutPropTerminalTags?.visibility = View.VISIBLE
            layoutPropTerminalRows?.removeAllViews()
            terminalInputs.clear()

            val density = resources.displayMetrics.density
            for (node in def.nodes) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, (3 * density).toInt(), 0, (3 * density).toInt())
                    }
                }

                val dirStr = when (node.direction) {
                    0 -> "Atas"
                    1 -> "Kanan"
                    2 -> "Bawah"
                    3 -> "Kiri"
                    else -> "Node"
                }
                val pinLabelText = "Pin ${node.index} ($dirStr)" + if (node.tag.isNotEmpty()) " [Def: ${node.tag}]" else ""

                val tvNode = TextView(this).apply {
                    text = pinLabelText
                    setTextColor(Color.parseColor("#94a3b8"))
                    textSize = 12f
                    typeface = Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val etNode = EditText(this).apply {
                    setBackgroundResource(R.drawable.bg_edit_text_modern)
                    setTextColor(Color.parseColor("#38bdf8"))
                    setHintTextColor(Color.parseColor("#475569"))
                    textSize = 13f
                    typeface = Typeface.MONOSPACE
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    filters = arrayOf(InputFilter.LengthFilter(8))
                    hint = node.tag.ifEmpty { "-" }
                    val currentVal = el.getTerminalTag(node.index, node.tag)
                    setText(currentVal)
                    layoutParams = LinearLayout.LayoutParams((84 * density).toInt(), (36 * density).toInt()).apply {
                        setMargins((6 * density).toInt(), 0, 0, 0)
                    }
                }

                row.addView(tvNode)
                row.addView(etNode)
                layoutPropTerminalRows?.addView(row)
                terminalInputs.add(Pair(node.index, etNode))
            }
        } else {
            layoutPropTerminalTags?.visibility = View.GONE
        }

        btnSave?.setOnClickListener {
            canvasView.pushUndoState()
            if (isTextComp) {
                val inputVal = etTag?.text?.toString()?.trim() ?: ""
                el.tag = inputVal
                el.comment = inputVal
                el.fontSize = selectedFontSize
                el.textColor = selectedTextColor
            } else {
                el.tag = etTag?.text?.toString()?.trim() ?: ""
                if (etComment != null) {
                    el.comment = etComment.text.toString().trim()
                }
            }
            if (terminalInputs.isNotEmpty() && def != null) {
                val newTags = mutableListOf<String>()
                for (node in def.nodes) {
                    val customVal = terminalInputs.find { it.first == node.index }?.second?.text?.toString()?.trim()
                    newTags.add(if (!customVal.isNullOrEmpty()) customVal else node.tag)
                }
                el.terminalTags = newTags
            }
            if (isLampComp) {
                el.lampColor = selectedLampColor
            }
            if (isTimerCoil && etTimer != null) {
                val secVal = etTimer.text.toString().toFloatOrNull()
                if (secVal != null && secVal > 0f) {
                    el.presetTime = secVal
                }
            }
            if (isSwitch) {
                val mappedFrame = if (isCartridgeFuse) (if (selectedSwitchPos == 1) 0 else 1) else selectedSwitchPos
                canvasView.engine.setSwitchPosition(el.id, selectedSwitchPos)
                if (isCartridgeFuse && selectedSwitchPos == 1) {
                    canvasView.engine.fuseBlownElements.remove(el.id)
                }
                el.frameIndex = mappedFrame
            }
            val isText = (el.svgData == "text")
            val displayTag = when {
                isText && el.comment.isNotEmpty() && el.comment != "null" -> el.comment
                isText && el.tag.isNotEmpty() && el.tag != "null" -> el.tag
                isText -> "Teks CAD"
                el.tag.isNotEmpty() && el.tag != "null" -> el.tag
                else -> el.svgData
            }
            pillLabel.text = displayTag
            canvasView.invalidate()
            triggerAutoSaveIfEnabled()
            dialog.dismiss()
        }

        // Ukuran adaptif untuk mencegah tombol terpotong pada landscape:
        dialogView.measure(
            View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val desiredHeight = dialogView.measuredHeight
        val forceFullHeight = isLandscape || (desiredHeight > maxAvailableHeight)

        val lp = scrollView?.layoutParams as? LinearLayout.LayoutParams
        if (lp != null) {
            lp.height = if (forceFullHeight) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
            lp.weight = if (forceFullHeight) 1f else 0f
            scrollView.layoutParams = lp
        }
        val targetHeightMode = if (forceFullHeight) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        cardPropRoot?.layoutParams?.height = targetHeightMode
        layoutPropContainer?.layoutParams?.height = targetHeightMode

        showThemedDialog(dialog)
        val finalHeight = if (forceFullHeight) maxAvailableHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        dialog.window?.setLayout(dialogWidth, finalHeight)
    }

    private fun updatePillForConnector(connector: NativeConnector?) {
        if (connector == null) {
            if (canvasView.selectedElementId == null) {
                floatingPill.visibility = View.GONE
                updateFabPosition(false)
            }
            return
        }

        activeSwitchElement = null
        pillLabel.text = "Kawat #${connector.id}"
        pillSwitchOptions.visibility = View.GONE

        // Kawat bisa dihapus, tetapi tidak di-rotate atau di-tag
        btnRotate.visibility = View.GONE
        btnTag.visibility = View.GONE
        btnDelete.visibility = View.VISIBLE
        pillEditControls.visibility = if (canvasView.isSimulating) View.GONE else View.VISIBLE
        floatingPill.visibility = View.VISIBLE
        updateFabPosition(true)
    }

    private fun showProjectDashboardDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_project_dashboard)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.85).toInt()
        )

        dialog.findViewById<View>(R.id.btnCloseDashboard).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnDashNew).setOnClickListener {
            loadEmptyCanvasTemplate()
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnDashImport)?.setOnClickListener {
            dialog.dismiss()
            launchImportPicker()
        }

        dialog.findViewById<View>(R.id.btnDashScanQr)?.setOnClickListener {
            dialog.dismiss()
            launchQrScanner()
        }

        dialog.findViewById<View>(R.id.cardTemplateDol)?.setOnClickListener {
            loadDolStarterTemplate()
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnUseTemplateDol)?.setOnClickListener {
            loadDolStarterTemplate()
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.cardTemplateHazard)?.setOnClickListener {
            loadHazardLampTemplate()
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnUseTemplateHazard)?.setOnClickListener {
            loadHazardLampTemplate()
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.cardTemplateEmpty)?.setOnClickListener {
            loadEmptyCanvasTemplate()
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.btnUseTemplateEmpty)?.setOnClickListener {
            loadEmptyCanvasTemplate()
            dialog.dismiss()
        }

        showThemedDialog(dialog)
    }

    private fun loadEmptyCanvasTemplate() {
        canvasView.elements.clear()
        canvasView.connectors.clear()
        setCurrentProject("Canvas Baru")
        floatingPill.visibility = View.GONE
        updateFabPosition(false)
        canvasView.clearUndoHistory()
        canvasView.invalidate()
        markCurrentStateAsSaved()
    }

    private fun loadStarterDemoTemplate() {
        try {
            val assetStream = assets.open("assets/examples/rangkaian_sederhana_demo.smrl")
            val text = assetStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val circuit = WorkspaceManager.deserializeCircuit(text)
            canvasView.elements.clear()
            canvasView.elements.addAll(circuit.first)
            canvasView.connectors.clear()
            canvasView.connectors.addAll(circuit.second)
            setCurrentProject("Rangkaian Sederhana Demo")
            WorkspaceManager.saveProject(this, "Rangkaian Sederhana Demo", canvasView.elements, canvasView.connectors)
            canvasView.clearUndoHistory()
            canvasView.invalidate()
            markCurrentStateAsSaved()
            canvasView.post { canvasView.fitToScreen() }
        } catch (e: Exception) {
            e.printStackTrace()
            loadDolStarterTemplate()
        }
    }

    private fun loadDolStarterTemplate() {
        canvasView.elements.clear()
        canvasView.connectors.clear()
        setCurrentProject("DOL Motor Starter")

        // Power Circuit Column (X = 150f, vertical tier spacing 150-180px - Non-overlapping!)
        val pwr = NativeElement(1, "sourceL1L2L3N", "-L1L2L3N", "", 150f, 60f)
        val qm = NativeElement(2, "motorBreaker3p", "-QM1", "", 150f, 210f)
        val km = NativeElement(3, "contactor3p", "-KM1", "", 150f, 360f)
        val fr = NativeElement(4, "overload3p", "-FR1", "", 150f, 510f)
        val m = NativeElement(5, "motor3ph", "-M1", "", 150f, 690f)

        // Control Circuit Column (X = 330f & 450f, Auxiliary self-holding NO at X = 240f)
        val sbStop = NativeElement(6, "buttonNC", "-SB1", "Stop", 330f, 210f)
        val sbStart = NativeElement(7, "buttonNO", "-SB2", "Start", 330f, 360f)
        val kmAuxNo = NativeElement(10, "contactNO", "-KM1", "13-14", 240f, 360f)
        val coilKm = NativeElement(8, "coil", "-KM1", "", 330f, 510f)
        val hlLamp = NativeElement(9, "lamp", "-HL1", "Run Lamp", 450f, 510f)

        canvasView.elements.addAll(listOf(pwr, qm, km, fr, m, sbStop, sbStart, kmAuxNo, coilKm, hlLamp))

        // Power Wires (Tersambung lurus vertikal dengan jarak lega)
        canvasView.connectors.add(NativeConnector(1, 1, 0, 2, 0)) // L1 -> QM (1)
        canvasView.connectors.add(NativeConnector(2, 1, 1, 2, 2)) // L2 -> QM (3)
        canvasView.connectors.add(NativeConnector(3, 1, 2, 2, 4)) // L3 -> QM (5)

        canvasView.connectors.add(NativeConnector(4, 2, 1, 3, 0)) // QM (2) -> KM (1)
        canvasView.connectors.add(NativeConnector(5, 2, 3, 3, 2)) // QM (4) -> KM (3)
        canvasView.connectors.add(NativeConnector(6, 2, 5, 3, 4)) // QM (6) -> KM (5)

        canvasView.connectors.add(NativeConnector(7, 3, 1, 4, 0)) // KM (2) -> FR (1)
        canvasView.connectors.add(NativeConnector(8, 3, 3, 4, 2)) // KM (4) -> FR (3)
        canvasView.connectors.add(NativeConnector(9, 3, 5, 4, 4)) // KM (6) -> FR (5)

        canvasView.connectors.add(NativeConnector(10, 4, 1, 5, 0)) // FR (2) -> Motor U1
        canvasView.connectors.add(NativeConnector(11, 4, 3, 5, 1)) // FR (4) -> Motor V1
        canvasView.connectors.add(NativeConnector(12, 4, 5, 5, 2)) // FR (6) -> Motor W1

        // Control Circuit Wires (Dengan Rangkaian Pengunci Otomatis / Self-Holding Latch)
        canvasView.connectors.add(NativeConnector(13, 1, 0, 6, 0)) // L1 -> SB1 Stop (1)
        canvasView.connectors.add(NativeConnector(14, 6, 1, 7, 0)) // SB1 (2) -> SB2 Start (3)
        canvasView.connectors.add(NativeConnector(15, 6, 1, 10, 0)) // SB1 (2) -> KM1 Aux NO (13) (Paralel Start)
        canvasView.connectors.add(NativeConnector(16, 7, 1, 8, 0)) // SB2 (4) -> Coil KM1 (A1)
        canvasView.connectors.add(NativeConnector(17, 10, 1, 8, 0)) // KM1 Aux NO (14) -> Coil KM1 (A1) (Self-holding latch)
        canvasView.connectors.add(NativeConnector(18, 8, 0, 9, 0)) // Coil A1 -> Lamp HL1 (X1)
        canvasView.connectors.add(NativeConnector(19, 8, 1, 1, 3)) // Coil A2 -> Neutral N
        canvasView.connectors.add(NativeConnector(20, 9, 1, 1, 3)) // Lamp X2 -> Neutral N

        canvasView.engine.manualStates[qm.id] = 1 // Breaker tertutup (ready)
        canvasView.clearUndoHistory()
        canvasView.invalidate()
        markCurrentStateAsSaved()
        canvasView.post { canvasView.fitToScreen() }
    }

    private fun loadHazardLampTemplate() {
        canvasView.elements.clear()
        canvasView.connectors.clear()
        setCurrentProject("Lampu Sign & Hazard")

        val pwr = NativeElement(1, "sourcePlus", "-+", "", 180f, 60f)
        val gnd = NativeElement(2, "sourceNegative", "--", "", 180f, 540f)
        val swHazard = NativeElement(3, "switch3p0", "-S1", "Hazard Switch", 180f, 210f)
        val lampL = NativeElement(4, "lamp", "-HL1", "Sein Kiri", 120f, 380f)
        val lampR = NativeElement(5, "lamp", "-HL2", "Sein Kanan", 240f, 380f)

        canvasView.elements.addAll(listOf(pwr, gnd, swHazard, lampL, lampR))
        canvasView.connectors.add(NativeConnector(1, 1, 0, 3, 0))
        canvasView.connectors.add(NativeConnector(2, 3, 1, 4, 0))
        canvasView.connectors.add(NativeConnector(3, 3, 2, 5, 0))
        canvasView.connectors.add(NativeConnector(4, 4, 1, 2, 0))
        canvasView.connectors.add(NativeConnector(5, 5, 1, 2, 0))

        canvasView.clearUndoHistory()
        canvasView.invalidate()
        markCurrentStateAsSaved()
        canvasView.post { canvasView.fitToScreen() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density + 0.5f).toInt()

        // Sesuaikan lebar panel library di mode landscape
        val panelW = if (isLandscape) dp(260) else dp(225)
        panelLibrary.layoutParams = panelLibrary.layoutParams.apply {
            width = panelW
        }

        // Sesuaikan margin bawah floating pill
        val pillMarginBottom = if (isLandscape) dp(12) else dp(24)
        (floatingPill.layoutParams as? CoordinatorLayout.LayoutParams)?.apply {
            bottomMargin = pillMarginBottom
            floatingPill.layoutParams = this
        }

        hideSystemUI()
        ViewCompat.requestApplyInsets(window.decorView)

        // Segarkan canvas
        canvasView.post {
            canvasView.invalidate()
        }
    }

    private fun updateUndoRedoButtons(canUndo: Boolean, canRedo: Boolean) {
        btnUndo.isEnabled = canUndo
        btnUndo.alpha = if (canUndo) 1.0f else 0.35f
        btnRedo.isEnabled = canRedo
        btnRedo.alpha = if (canRedo) 1.0f else 0.35f
    }

    private fun setupWorkspaceOverlay() {
        workspaceAdapter = WorkspaceProjectsAdapter(
            items = WorkspaceManager.listProjects(this),
            onItemClick = { item ->
                loadProjectItem(item)
            },
            onOptionsClick = { anchorView, item ->
                showProjectOptionsMenu(anchorView, item)
            }
        )
        rvWorkspaceProjects.layoutManager = LinearLayoutManager(this)
        rvWorkspaceProjects.adapter = workspaceAdapter

        btnWorkspaceBack.setOnClickListener {
            if (activeWorkspaceTab == "modules" && activeDetailModule != null) {
                closeInlineModuleDetail()
            } else {
                closeWorkspaceOverlay()
            }
        }

        btnNewProject.setOnClickListener {
            showNewProjectDialog()
        }

        btnWorkspaceMore.setOnClickListener { anchor ->
            showWorkspaceMoreMenu(anchor)
        }

        tabModules?.setOnClickListener {
            switchWorkspaceTab("modules")
        }

        tabWorkspace.setOnClickListener {
            switchWorkspaceTab("workspace")
        }

        tabExamples.setOnClickListener {
            switchWorkspaceTab("examples")
        }
    }

    private fun stopSimulationIfRunning(restoreEditPill: Boolean = false) {
        val wasSimulating = canvasView.isSimulating || btnStop.visibility == View.VISIBLE
        if (!wasSimulating) return

        btnStop.visibility = View.GONE
        btnRun.visibility = View.VISIBLE
        cardShortCircuit.visibility = View.GONE

        val prevSelectedId = canvasView.selectedElementId
        canvasView.stopSimulation()

        if (restoreEditPill) {
            val targetElement = resolveTargetElementForEdit(prevSelectedId)
            if (targetElement != null) {
                canvasView.selectedElementId = targetElement.id
                canvasView.invalidate()
                updatePill(targetElement)
            } else {
                floatingPill.visibility = View.GONE
                updateFabPosition(false)
            }
        } else {
            canvasView.selectedElementId = null
            canvasView.selectedConnectorId = null
            floatingPill.visibility = View.GONE
            updateFabPosition(false)
        }
    }

    private fun openWorkspaceOverlay(initialTab: String = "workspace") {
        stopSimulationIfRunning(restoreEditPill = false)
        activeDetailModule = null
        switchWorkspaceTab(initialTab)
        layoutWorkspaceOverlay.visibility = View.VISIBLE
        if (initialTab != "modules") {
            rvWorkspaceProjects.scrollToPosition(0)
        }
    }

    private fun closeWorkspaceOverlay() {
        activeDetailModule = null
        layoutWorkspaceOverlay.visibility = View.GONE
    }

    private fun switchWorkspaceTab(showExamples: Boolean) {
        switchWorkspaceTab(if (showExamples) "examples" else "workspace")
    }

    private fun switchWorkspaceTab(tab: String) {
        activeWorkspaceTab = tab
        isViewingExamples = (tab == "examples")
        if (tab != "modules") {
            activeDetailModule = null
        }

        val isLight = SettingsManager.isLightTheme(this)
        val activeLabelColor = if (isLight) Color.parseColor("#1d4ed8") else Color.WHITE
        val inactiveColor = Color.parseColor(if (isLight) "#475569" else "#64748b")

        fun styleBottomTab(pill: LinearLayout?, icon: ImageView?, label: TextView?, isActive: Boolean) {
            if (isActive) {
                pill?.setBackgroundResource(R.drawable.bg_nav_pill_active)
                icon?.imageTintList = ColorStateList.valueOf(Color.WHITE)
                label?.setTextColor(activeLabelColor)
                label?.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                pill?.setBackgroundResource(R.drawable.bg_nav_pill_inactive)
                icon?.imageTintList = ColorStateList.valueOf(inactiveColor)
                label?.setTextColor(inactiveColor)
                label?.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }

        styleBottomTab(pillTabModules, ivTabModulesIcon, tvTabModulesLabel, tab == "modules")
        styleBottomTab(pillTabWorkspace, ivTabWorkspaceIcon, tvTabWorkspaceLabel, tab == "workspace")
        styleBottomTab(pillTabExamples, ivTabExamplesIcon, tvTabExamplesLabel, tab == "examples")

        val btnDeleteDetail = findViewById<TextView>(R.id.btnPmDeleteDetail)
        val bottomBar = findViewById<View>(R.id.workspaceBottomBar)
        val bottomDivider = findViewById<View>(R.id.workspaceBottomDivider)

        when (tab) {
            "modules" -> {
                rvWorkspaceProjects.visibility = View.GONE
                layoutInlineModulesPage?.visibility = View.VISIBLE
                ensureInlineModulesWired()
                val detailMod = activeDetailModule
                if (detailMod != null) {
                    btnWorkspaceBack.visibility = View.VISIBLE
                    tvWorkspaceHeaderTitle.text = "${detailMod.code} · Detail Modul"
                    btnNewProject.visibility = View.GONE
                    btnWorkspaceImport.visibility = View.GONE
                    btnWorkspaceScanQr?.visibility = View.GONE
                    btnWorkspaceMore.visibility = View.GONE
                    btnWorkspaceThemeToggle?.visibility = View.VISIBLE
                    btnDeleteDetail?.visibility = if (detailMod.isBuiltin) View.GONE else View.VISIBLE
                    bottomBar?.visibility = View.GONE
                    bottomDivider?.visibility = View.GONE
                    renderInlineModuleDetail(detailMod)
                } else {
                    btnWorkspaceBack.visibility = View.GONE
                    tvWorkspaceHeaderTitle.text = "Modul"
                    btnNewProject.visibility = View.GONE
                    btnWorkspaceImport.visibility = View.GONE
                    btnWorkspaceScanQr?.visibility = View.VISIBLE
                    btnWorkspaceThemeToggle?.visibility = View.VISIBLE
                    btnWorkspaceMore.visibility = View.VISIBLE
                    btnDeleteDetail?.visibility = View.GONE
                    bottomBar?.visibility = View.VISIBLE
                    bottomDivider?.visibility = View.VISIBLE
                    renderInlineModulesList()
                }
            }
            "examples" -> {
                layoutInlineModulesPage?.visibility = View.GONE
                rvWorkspaceProjects.visibility = View.VISIBLE
                btnWorkspaceBack.visibility = View.GONE
                tvWorkspaceHeaderTitle.text = "Examples"
                btnNewProject.visibility = View.GONE
                btnWorkspaceImport.visibility = View.GONE
                btnWorkspaceScanQr?.visibility = View.GONE
                btnWorkspaceThemeToggle?.visibility = View.VISIBLE
                btnWorkspaceMore.visibility = View.VISIBLE
                btnDeleteDetail?.visibility = View.GONE
                bottomBar?.visibility = View.VISIBLE
                bottomDivider?.visibility = View.VISIBLE
                workspaceAdapter.updateItems(WorkspaceManager.listExamples())
                rvWorkspaceProjects.scrollToPosition(0)
            }
            else -> {
                layoutInlineModulesPage?.visibility = View.GONE
                rvWorkspaceProjects.visibility = View.VISIBLE
                btnWorkspaceBack.visibility = View.GONE
                tvWorkspaceHeaderTitle.text = "Workspace"
                btnNewProject.visibility = View.VISIBLE
                btnWorkspaceImport.visibility = View.VISIBLE
                btnWorkspaceScanQr?.visibility = View.VISIBLE
                btnWorkspaceThemeToggle?.visibility = View.VISIBLE
                btnWorkspaceMore.visibility = View.VISIBLE
                btnDeleteDetail?.visibility = View.GONE
                bottomBar?.visibility = View.VISIBLE
                bottomDivider?.visibility = View.VISIBLE
                workspaceAdapter.updateItems(WorkspaceManager.listProjects(this))
                rvWorkspaceProjects.scrollToPosition(0)
            }
        }
    }

    private fun refreshWorkspaceList() {
        when (activeWorkspaceTab) {
            "modules" -> if (activeDetailModule != null) renderInlineModuleDetail(activeDetailModule!!) else renderInlineModulesList()
            "examples" -> workspaceAdapter.updateItems(WorkspaceManager.listExamples())
            else -> workspaceAdapter.updateItems(WorkspaceManager.listProjects(this))
        }
    }

    private fun loadProjectItem(item: ProjectItem) {
        stopSimulationIfRunning(restoreEditPill = false)
        if (item.isExample) {
            when (item.name) {
                "Rangkaian Sederhana Demo" -> loadStarterDemoTemplate()
                "DOL Motor Starter" -> loadDolStarterTemplate()
                "Hazard Switch & Lamp" -> loadHazardLampTemplate()
                "Forward-Reverse Motor" -> loadForwardReverseTemplate()
                else -> loadStarterDemoTemplate()
            }
        } else {
            val circuit = WorkspaceManager.loadProject(item.file)
            if (circuit != null) {
                canvasView.elements.clear()
                canvasView.elements.addAll(circuit.first)
                canvasView.connectors.clear()
                canvasView.connectors.addAll(circuit.second)
                canvasView.clearUndoHistory()
                setCurrentProject(item.name)
                val kit = WorkspaceManager.currentAssignmentKit
                if (::visualAdapter.isInitialized) {
                    if (kit != null && kit.enabled) {
                        visualAdapter.setAllowedComponentFilter(kit.allowedComponents.toSet())
                    } else {
                        visualAdapter.setAllowedComponentFilter(null)
                    }
                }
                canvasView.invalidate()
                markCurrentStateAsSaved()
                canvasView.post { canvasView.fitToScreen() }
            } else {
                Toast.makeText(this, "Gagal membaca berkas proyek", Toast.LENGTH_SHORT).show()
            }
        }
        closeWorkspaceOverlay()
    }

    private fun showProjectOptionsMenu(anchorView: View, item: ProjectItem) {
        val items = listOf(
            CompactMenuItem(1, R.drawable.ic_edit, "Ganti Nama") {
                showRenameProjectDialog(item)
            },
            CompactMenuItem(2, R.drawable.ic_copy, "Salin Proyek") {
                val copyName = WorkspaceManager.duplicateProject(this, item.name)
                if (copyName != null) {
                    refreshWorkspaceList()
                    Toast.makeText(this, "Berhasil disalin sebagai '$copyName'", Toast.LENGTH_SHORT).show()
                }
            },
            CompactMenuItem(4, R.drawable.ic_export, "Ekspor .smrl") {
                exportProject(item.name, asLegacyTxt = false)
            },
            CompactMenuItem(5, R.drawable.ic_workspace_file, "Ekspor .txt") {
                exportProject(item.name, asLegacyTxt = true)
            },
            CompactMenuItem(3, R.drawable.ic_delete, "Hapus Proyek", isDanger = true) {
                val dialog = Dialog(this)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(R.layout.dialog_modern_confirm)
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((440 * resources.displayMetrics.density).toInt())
                dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

                dialog.findViewById<ImageView>(R.id.ivConfirmHeaderIcon)?.setImageResource(R.drawable.ic_delete)
                dialog.findViewById<TextView>(R.id.tvConfirmHeaderTitle)?.text = "Hapus Proyek"
                dialog.findViewById<TextView>(R.id.tvConfirmMessage)?.text = "Apakah Anda yakin ingin menghapus proyek '${item.name}'? Berkas yang dihapus tidak dapat dipulihkan."

                dialog.findViewById<ImageView>(R.id.btnCloseConfirm)?.setOnClickListener { dialog.dismiss() }
                dialog.findViewById<Button>(R.id.btnCancelConfirm)?.setOnClickListener { dialog.dismiss() }
                dialog.findViewById<Button>(R.id.btnActionConfirm)?.apply {
                    text = "Hapus"
                    backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ef4444"))
                    setOnClickListener {
                        WorkspaceManager.deleteProject(this@MainActivity, item.name)
                        val jsFile = java.io.File(java.io.File(filesDir, "jobsheets"), "${item.name}.pdf")
                        if (jsFile.exists()) {
                            jsFile.delete()
                        }
                        if (currentProjectName == item.name) {
                            activeJobsheetFile = null
                        }
                        refreshWorkspaceList()
                        Toast.makeText(this@MainActivity, "Proyek '${item.name}' dihapus", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                showThemedDialog(dialog)
            }
        )

        CompactMenuHelper.show(this, anchorView, items, widthDp = 185)
    }

    private fun showRenameProjectDialog(item: ProjectItem) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_modern_single_input)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((460 * resources.displayMetrics.density).toInt())
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<ImageView>(R.id.ivInputHeaderIcon)?.setImageResource(R.drawable.ic_edit)
        dialog.findViewById<TextView>(R.id.tvInputHeaderTitle)?.text = "Ganti Nama Proyek"
        dialog.findViewById<TextView>(R.id.tvInputLabel)?.text = "Nama Proyek Baru:"

        val input = dialog.findViewById<EditText>(R.id.etInputField)
        input?.setText(item.name)
        input?.selectAll()

        dialog.findViewById<ImageView>(R.id.btnCloseInput)?.setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btnCancelInput)?.setOnClickListener { dialog.dismiss() }

        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmInput)
        btnConfirm?.text = "Simpan"
        btnConfirm?.setOnClickListener {
            val newName = input?.text?.toString()?.trim() ?: ""
            if (newName.isNotEmpty() && newName != item.name) {
                if (WorkspaceManager.renameProject(this, item.name, newName)) {
                    val jsDir = java.io.File(filesDir, "jobsheets")
                    val oldJs = java.io.File(jsDir, "${item.name}.pdf")
                    if (oldJs.exists()) {
                        val newJs = java.io.File(jsDir, "$newName.pdf")
                        oldJs.renameTo(newJs)
                    }
                    if (currentProjectName == item.name) {
                        setCurrentProject(newName)
                    }
                    refreshWorkspaceList()
                    Toast.makeText(this, "Nama berhasil diubah", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, "Gagal mengubah nama proyek", Toast.LENGTH_SHORT).show()
                }
            } else if (newName == item.name) {
                dialog.dismiss()
            }
        }
        showThemedDialog(dialog)
    }

    private fun showWorkspaceMoreMenu(anchorView: View) {
        val items = listOf(
            CompactMenuItem(1, R.drawable.ic_qr_scan, "Scan QR Tugas Guru") {
                launchQrScanner()
            },
            CompactMenuItem(2, R.drawable.ic_refresh, "Segarkan") {
                refreshWorkspaceList()
            },
            CompactMenuItem(3, R.drawable.ic_workspace_add, "Proyek Baru") {
                showNewProjectDialog()
            }
        )

        CompactMenuHelper.show(this, anchorView, items, widthDp = 185)
    }

    private fun showNewProjectDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_modern_single_input)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((460 * resources.displayMetrics.density).toInt())
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<ImageView>(R.id.ivInputHeaderIcon)?.setImageResource(R.drawable.ic_workspace_add)
        dialog.findViewById<TextView>(R.id.tvInputHeaderTitle)?.text = "Buat Proyek Baru"
        dialog.findViewById<TextView>(R.id.tvInputLabel)?.text = "Nama Proyek:"

        val input = dialog.findViewById<EditText>(R.id.etInputField)
        input?.hint = "Contoh: Rangkaian Kontrol 1"

        dialog.findViewById<ImageView>(R.id.btnCloseInput)?.setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btnCancelInput)?.setOnClickListener { dialog.dismiss() }

        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmInput)
        btnConfirm?.text = "Buat Proyek"
        btnConfirm?.setOnClickListener {
            val name = input?.text?.toString()?.trim()?.ifEmpty { "Project ${(1..99).random()}" } ?: "Project ${(1..99).random()}"
            stopSimulationIfRunning(restoreEditPill = false)
            canvasView.pushUndoState()
            canvasView.elements.clear()
            canvasView.connectors.clear()
            canvasView.clearUndoHistory()
            floatingPill.visibility = View.GONE
            updateFabPosition(false)
            WorkspaceManager.saveProject(this, name, emptyList(), emptyList())
            setCurrentProject(name)
            markCurrentStateAsSaved()
            refreshWorkspaceList()
            closeWorkspaceOverlay()
            canvasView.invalidate()
            Toast.makeText(this, "Proyek '$name' dibuat", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        showThemedDialog(dialog)
    }

    private fun showSaveAsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_modern_save_as, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val dm = resources.displayMetrics
        val isLandscape = dm.widthPixels > dm.heightPixels
        val dialogWidth = if (isLandscape) {
            (dm.widthPixels * 0.65f).toInt().coerceIn((420 * dm.density).toInt(), (540 * dm.density).toInt())
        } else {
            (dm.widthPixels * 0.92f).toInt().coerceAtMost((480 * dm.density).toInt())
        }
        val maxAvailableHeight = (dm.heightPixels * (if (isLandscape) 0.88f else 0.82f)).toInt()
        val scrollView = dialogView.findViewById<ScrollView>(R.id.scrollSaveAsBody)

        val etName = dialog.findViewById<EditText>(R.id.etSaveAsProjectName)
        val cardSmrl = dialog.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardOptionSmrl)
        val cardTxt = dialog.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardOptionTxt)
        val cardPdf = dialog.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardOptionPdf)
        val tvRadioSmrl = dialog.findViewById<TextView>(R.id.tvRadioSmrl)
        val tvRadioTxt = dialog.findViewById<TextView>(R.id.tvRadioTxt)
        val tvRadioPdf = dialog.findViewById<TextView>(R.id.tvRadioPdf)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseSaveAs)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelSaveAs)
        val btnConfirm = dialog.findViewById<Button>(R.id.btnConfirmSaveAs)

        etName?.setText("$currentProjectName (Salinan)")

        // 0: SMRL, 1: TXT, 2: PDF
        var selectedFormat = 0

        val isLight = SettingsManager.isLightTheme(this)
        val selBgEmerald = Color.parseColor(if (isLight) "#ecfdf5" else "#1a2238")
        val selBgBlue = Color.parseColor(if (isLight) "#eff6ff" else "#162238")
        val idleBg = Color.parseColor(if (isLight) "#f8fafc" else "#181a28")
        val idleStroke = Color.parseColor(if (isLight) "#e2e8f0" else "#2d3148")

        fun updateFormatSelection() {
            // SMRL
            if (selectedFormat == 0) {
                cardSmrl?.strokeColor = Color.parseColor("#059669")
                cardSmrl?.setCardBackgroundColor(selBgEmerald)
                tvRadioSmrl?.setBackgroundResource(R.drawable.bg_status_badge_on)
                tvRadioSmrl?.text = "✓"
            } else {
                cardSmrl?.strokeColor = idleStroke
                cardSmrl?.setCardBackgroundColor(idleBg)
                tvRadioSmrl?.setBackgroundResource(R.drawable.bg_status_badge_off)
                tvRadioSmrl?.text = ""
            }

            // TXT
            if (selectedFormat == 1) {
                cardTxt?.strokeColor = Color.parseColor("#059669")
                cardTxt?.setCardBackgroundColor(selBgEmerald)
                tvRadioTxt?.setBackgroundResource(R.drawable.bg_status_badge_on)
                tvRadioTxt?.text = "✓"
            } else {
                cardTxt?.strokeColor = idleStroke
                cardTxt?.setCardBackgroundColor(idleBg)
                tvRadioTxt?.setBackgroundResource(R.drawable.bg_status_badge_off)
                tvRadioTxt?.text = ""
            }

            // PDF
            if (selectedFormat == 2) {
                cardPdf?.strokeColor = Color.parseColor("#0284c7")
                cardPdf?.setCardBackgroundColor(selBgBlue)
                tvRadioPdf?.setBackgroundResource(R.drawable.bg_status_badge_on)
                tvRadioPdf?.text = "✓"
            } else {
                cardPdf?.strokeColor = idleStroke
                cardPdf?.setCardBackgroundColor(idleBg)
                tvRadioPdf?.setBackgroundResource(R.drawable.bg_status_badge_off)
                tvRadioPdf?.text = ""
            }
        }

        cardSmrl?.setOnClickListener {
            selectedFormat = 0
            updateFormatSelection()
        }

        cardTxt?.setOnClickListener {
            selectedFormat = 1
            updateFormatSelection()
        }

        cardPdf?.setOnClickListener {
            selectedFormat = 2
            updateFormatSelection()
        }

        val cardOptionSendToTeacher = dialog.findViewById<View>(R.id.cardOptionSendToTeacher)
        cardOptionSendToTeacher?.setOnClickListener {
            dialog.dismiss()
            sendTaskToTeacher()
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnConfirm?.setOnClickListener {
            val name = etName?.text?.toString()?.trim()?.ifEmpty { "$currentProjectName (Salinan)" } ?: "$currentProjectName (Salinan)"
            if (selectedFormat == 2) {
                dialog.dismiss()
                showPdfStudentInfoDialog(name)
                return@setOnClickListener
            }

            WorkspaceManager.saveProject(this, name, canvasView.elements, canvasView.connectors)
            setCurrentProject(name)
            markCurrentStateAsSaved()
            refreshWorkspaceList()

            if (selectedFormat == 0) {
                Toast.makeText(this, "Proyek disimpan sebagai '$name.smrl'", Toast.LENGTH_SHORT).show()
            } else {
                val exportedFile = WorkspaceManager.exportProjectFile(this, name, asLegacyTxt = true)
                if (exportedFile != null) {
                    Toast.makeText(this, "Proyek disimpan & diekspor sebagai '$name.txt'", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Proyek disimpan sebagai '$name'", Toast.LENGTH_SHORT).show()
                }
            }
            dialog.dismiss()
        }

        dialogView.measure(
            View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val desiredHeight = dialogView.measuredHeight
        val useMaxHeight = (desiredHeight > maxAvailableHeight)
        if (useMaxHeight) {
            val lp = scrollView?.layoutParams as? LinearLayout.LayoutParams
            if (lp != null) {
                lp.height = 0
                lp.weight = 1f
                scrollView?.layoutParams = lp
            }
        }
        showThemedDialog(dialog)
        updateFormatSelection()
        val finalHeight = if (useMaxHeight) maxAvailableHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        dialog.window?.setLayout(dialogWidth, finalHeight)
    }

    private fun showPdfStudentInfoDialog(defaultTitle: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_modern_pdf_student_info, null)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val dm = resources.displayMetrics
        val isLandscape = dm.widthPixels > dm.heightPixels
        val dialogWidth = if (isLandscape) {
            (dm.widthPixels * 0.65f).toInt().coerceIn((420 * dm.density).toInt(), (560 * dm.density).toInt())
        } else {
            (dm.widthPixels * 0.92f).toInt().coerceAtMost((480 * dm.density).toInt())
        }
        dialog.window?.setLayout(dialogWidth, WindowManager.LayoutParams.WRAP_CONTENT)

        val prefs = getSharedPreferences("simurelay_pdf_etiket", Context.MODE_PRIVATE)
        val etTitle = dialogView.findViewById<EditText>(R.id.etPdfProjectTitle)
        val etName = dialogView.findViewById<EditText>(R.id.etPdfStudentName)
        val etPres = dialogView.findViewById<EditText>(R.id.etPdfStudentPresensi)
        val etClass = dialogView.findViewById<EditText>(R.id.etPdfStudentClass)
        val etSchool = dialogView.findViewById<EditText>(R.id.etPdfStudentSchool)

        val btnClose = dialogView.findViewById<ImageView>(R.id.btnClosePdfStudentInfo)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelPdfStudentInfo)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btnConfirmPdfStudentInfo)

        etTitle?.setText(defaultTitle)
        etName?.setText(studentName ?: prefs.getString("pdf_student_name", "") ?: "")
        etPres?.setText(studentPresensi ?: prefs.getString("pdf_student_presensi", "") ?: "")
        etClass?.setText(prefs.getString("pdf_student_class", "XII Teknik Instalasi Tenaga Listrik") ?: "")
        etSchool?.setText(prefs.getString("pdf_student_school", "SMK Negeri 2") ?: "")

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnConfirm?.setOnClickListener {
            val title = etTitle?.text?.toString()?.trim()?.ifEmpty { defaultTitle } ?: defaultTitle
            val name = etName?.text?.toString()?.trim()?.ifEmpty { studentName ?: "Ahmad Maulana" } ?: "Ahmad Maulana"
            val pres = etPres?.text?.toString()?.trim()?.ifEmpty { studentPresensi ?: "—" } ?: "—"
            val sClass = etClass?.text?.toString()?.trim()?.ifEmpty { "XII TITL" } ?: "XII TITL"
            val sSchool = etSchool?.text?.toString()?.trim()?.ifEmpty { "SMK Vokasi" } ?: "SMK Vokasi"

            prefs.edit()
                .putString("pdf_student_name", name)
                .putString("pdf_student_presensi", pres)
                .putString("pdf_student_class", sClass)
                .putString("pdf_student_school", sSchool)
                .apply()

            dialog.dismiss()
            Toast.makeText(this, "Sedang menyusun dokumen PDF 2 halaman...", Toast.LENGTH_SHORT).show()

            val info = StudentPdfInfo(
                studentName = name,
                studentPresensi = pres,
                studentClass = sClass,
                studentSchool = sSchool,
                projectTitle = title
            )

            val elementsCopy = canvasView.elements.map { it.copy() }
            val connectorsCopy = canvasView.connectors.map { it.copy() }
            val cleanFilename = "Laporan_Praktikum_${name.replace(Regex("""[^\w\-]"""), "_")}_${title.replace(Regex("""[^\w\-]"""), "_")}.pdf"

            kotlin.concurrent.thread {
                try {
                    val pdfBytes = NativePdfReportGenerator.generatePdfBytes(
                        context = this@MainActivity,
                        elements = elementsCopy,
                        connectors = connectorsCopy,
                        info = info
                    )
                    val savedFile = NativePdfReportGenerator.savePdfToDownloads(this@MainActivity, cleanFilename, pdfBytes)

                    runOnUiThread {
                        if (savedFile != null) {
                            Toast.makeText(this@MainActivity, "Laporan PDF berhasil disimpan ke folder Download:\n$cleanFilename", Toast.LENGTH_LONG).show()
                            openPdfFile(savedFile)
                        } else {
                            Toast.makeText(this@MainActivity, "Gagal menyimpan berkas PDF", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error ekspor PDF: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        showThemedDialog(dialog)
    }

    private fun openPdfFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(intent, "Buka Laporan Praktikum PDF"))
        } catch (_: Exception) {
        }
    }

    private fun saveCurrentProject() {
        val success = WorkspaceManager.saveProject(this, currentProjectName, canvasView.elements, canvasView.connectors)
        if (success) {
            markCurrentStateAsSaved()
            Toast.makeText(this, "Proyek '$currentProjectName' berhasil disimpan", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Gagal menyimpan proyek", Toast.LENGTH_SHORT).show()
        }
    }

    private val autoSaveExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun triggerAutoSaveIfEnabled(showFeedback: Boolean = false) {
        if (!SettingsManager.isAutoSaveEnabled(this)) return
        if (canvasView.isSimulating) return
        val projectName = if (currentProjectName.isNotEmpty()) currentProjectName else "Project 1"
        val elementsSnapshot = ArrayList(canvasView.elements)
        val connectorsSnapshot = ArrayList(canvasView.connectors)

        autoSaveExecutor.execute {
            try {
                val success = WorkspaceManager.saveProject(this, projectName, elementsSnapshot, connectorsSnapshot)
                if (success) {
                    SettingsManager.setLastProjectName(this, projectName)
                    runOnUiThread {
                        markCurrentStateAsSaved()
                    }
                    if (showFeedback) {
                        runOnUiThread {
                            Toast.makeText(this, "Simpan Otomatis '$projectName' Aktif", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setCurrentProject(name: String) {
        currentProjectName = name
        tvDrawerSubtitle.text = name
        SettingsManager.setLastProjectName(this, name)
        updateActiveJobsheetForProject(name)
    }

    private fun updateActiveJobsheetForProject(projectName: String) {
        val jobsheetDir = java.io.File(filesDir, "jobsheets")
        val projectJobsheet = java.io.File(jobsheetDir, "$projectName.pdf")
        if (projectJobsheet.exists() && projectJobsheet.length() > 0L) {
            activeJobsheetFile = projectJobsheet
        } else {
            activeJobsheetFile = null
        }
        isJobsheetAvailableOnServer = false
    }

    private fun restoreLastProjectOrTemplate() {
        val lastProject = SettingsManager.getLastProjectName(this)
        var restored = false
        if (!lastProject.isNullOrEmpty() &&
            !lastProject.contains("hazard", ignoreCase = true) &&
            !lastProject.contains("sign", ignoreCase = true)
        ) {
            val file = java.io.File(WorkspaceManager.getWorkspaceDir(this), "$lastProject.smrl")
            if (file.exists()) {
                val data = WorkspaceManager.loadProject(file)
                if (data != null) {
                    canvasView.elements.clear()
                    canvasView.elements.addAll(data.first)
                    canvasView.connectors.clear()
                    canvasView.connectors.addAll(data.second)
                    setCurrentProject(lastProject)
                    canvasView.clearUndoHistory()
                    canvasView.invalidate()
                    markCurrentStateAsSaved()
                    canvasView.post { canvasView.fitToScreen() }
                    restored = true
                }
            }
        }
        if (!restored) {
            loadStarterDemoTemplate()
        }
    }



    private fun showThemedDialog(dialog: Dialog) {
        dialog.show()
        val root = dialog.findViewById<View>(android.R.id.content) ?: dialog.window?.decorView
        AppThemeHelper.applyThemeToDialog(root, SettingsManager.isLightTheme(this))
    }

    private fun toggleAppTheme() {
        val nextLight = !SettingsManager.isLightTheme(this)
        SettingsManager.setThemeMode(this, if (nextLight) SettingsManager.THEME_LIGHT else SettingsManager.THEME_DARK)
        applyAppTheme(nextLight)
        val label = if (nextLight) "Tema: Light Paper" else "Tema: Dark CAD"
        Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
    }

    private fun applyAppTheme(isLight: Boolean) {
        // 1. System bars icon contrast
        try {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = isLight
            controller.isAppearanceLightNavigationBars = isLight
        } catch (_: Exception) {}

        // 2. Root containers & Canvas
        val canvasBg = Color.parseColor(if (isLight) "#f8fafc" else "#121216")
        drawerLayout.setBackgroundColor(canvasBg)
        findViewById<View>(R.id.mainCoordinator)?.setBackgroundColor(canvasBg)
        canvasView.applyTheme(isLight)

        // 3. Top Toolbar (AppBar)
        val toolbarBg = Color.parseColor(if (isLight) "#ffffff" else "#14141a")
        val toolbarFg = Color.parseColor(if (isLight) "#1e293b" else "#ffffff")
        val dividerCol = Color.parseColor(if (isLight) "#cbd5e1" else "#272734")
        appBarLayout.setBackgroundColor(toolbarBg)
        findViewById<View>(R.id.toolbarBottomDivider)?.setBackgroundColor(Color.parseColor(if (isLight) "#e2e8f0" else "#222230"))
        findViewById<View>(R.id.toolbarDivider1)?.setBackgroundColor(dividerCol)
        findViewById<View>(R.id.toolbarDivider2)?.setBackgroundColor(dividerCol)

        fun tintToolbarButton(container: View?, tintIcon: Boolean = true) {
            if (container !is ViewGroup) return
            for (i in 0 until container.childCount) {
                when (val child = container.getChildAt(i)) {
                    is ImageView -> if (tintIcon) child.setColorFilter(toolbarFg) else child.clearColorFilter()
                    is TextView -> child.setTextColor(toolbarFg)
                }
            }
        }
        tintToolbarButton(btnOpenMenu, tintIcon = true)
        if (isLibraryOpen) {
            btnToggleLibrary.setBackgroundColor(Color.parseColor(if (isLight) "#2563eb" else "#1e3a8a"))
            tvToolbarLibraryLabel.setTextColor(Color.WHITE)
            ivToolbarLibraryIcon.setColorFilter(Color.WHITE)
        } else {
            btnToggleLibrary.background = null
            tintToolbarButton(btnToggleLibrary, tintIcon = true)
        }
        tintToolbarButton(btnRun, tintIcon = false)
        tintToolbarButton(btnStop, tintIcon = false)
        tintToolbarButton(btnUndo, tintIcon = true)
        tintToolbarButton(btnRedo, tintIcon = true)
        ivToolbarThemeIcon?.setImageResource(if (isLight) R.drawable.ic_theme_moon else R.drawable.ic_theme_sun)
        tintToolbarButton(btnToggleTheme, tintIcon = true)
        tintToolbarButton(btnToolbarMore, tintIcon = true)

        // 4. Slide-in Left Drawer + Wide Seamless Gradient Header
        val leftDrawer = findViewById<View>(R.id.leftDrawer)
        leftDrawer?.setBackgroundColor(Color.parseColor(if (isLight) "#ffffff" else "#141418"))
        findViewById<View>(R.id.leftDrawerHeader)?.background = AppThemeHelper.createDrawerHeaderDrawable(isLight)
        findViewById<View>(R.id.drawerMenuDivider)?.setBackgroundColor(Color.parseColor(if (isLight) "#e2e8f0" else "#282834"))

        val drawerIconColor = Color.parseColor(if (isLight) "#334155" else "#ffffff")
        val drawerTextColor = Color.parseColor(if (isLight) "#0f172a" else "#ffffff")
        val drawerRows = listOfNotNull(
            menuDrawerWorkspace, menuDrawerModules, menuDrawerSave, menuDrawerSaveAs,
            menuDrawerInstructions, menuDrawerAbout, menuDrawerSettings
        )
        for (row in drawerRows) {
            if (row is ViewGroup) {
                for (i in 0 until row.childCount) {
                    when (val child = row.getChildAt(i)) {
                        is ImageView -> child.setColorFilter(drawerIconColor)
                        is TextView -> if (child.id != R.id.tvDrawerModulesBadge) {
                            child.setTextColor(drawerTextColor)
                        }
                    }
                }
            }
        }
        // 5. Visual Component Library Panel
        if (isLight) {
            panelLibrary.setCardBackgroundColor(Color.WHITE)
            panelLibrary.strokeColor = Color.parseColor("#cbd5e1")
            findViewById<View>(R.id.panelLibraryHeader)?.setBackgroundColor(Color.parseColor("#f1f5f9"))
            btnCloseLibrary.setTextColor(Color.parseColor("#475569"))
            spinnerCategory.setPopupBackgroundDrawable(ColorDrawable(Color.WHITE))
            spinnerCategory.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563eb"))
        } else {
            panelLibrary.setCardBackgroundColor(Color.parseColor("#13131c"))
            panelLibrary.strokeColor = Color.parseColor("#262638")
            findViewById<View>(R.id.panelLibraryHeader)?.setBackgroundColor(Color.parseColor("#1c1c28"))
            btnCloseLibrary.setTextColor(Color.parseColor("#94a3b8"))
            spinnerCategory.setPopupBackgroundDrawable(ColorDrawable(Color.parseColor("#1c1c28")))
            spinnerCategory.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#38bdf8"))
        }
        (spinnerCategory.selectedView as? TextView)?.setTextColor(if (isLight) Color.parseColor("#0f172a") else Color.WHITE)
        if (::visualAdapter.isInitialized) {
            visualAdapter.setLightTheme(isLight)
        }

        // 6. Floating Action Pill, Zoom Controls & Exit Fullscreen
        if (isLight) {
            floatingPill.setCardBackgroundColor(Color.parseColor("#f7ffffff"))
            floatingPill.strokeColor = Color.parseColor("#cbd5e1")
            pillLabel.setTextColor(Color.parseColor("#1d4ed8"))
            val pillBtnBg = ColorStateList.valueOf(Color.parseColor("#f1f5f9"))
            val pillBtnFg = Color.parseColor("#0f172a")
            listOf(btnRotate, btnTag).forEach { b ->
                b.backgroundTintList = pillBtnBg
                b.setTextColor(pillBtnFg)
                if (b is com.google.android.material.button.MaterialButton) {
                    b.iconTint = ColorStateList.valueOf(pillBtnFg)
                }
            }
            btnDelete.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#fee2e2"))
            btnDelete.setTextColor(Color.parseColor("#dc2626"))
            (btnDelete as? com.google.android.material.button.MaterialButton)?.iconTint = ColorStateList.valueOf(Color.parseColor("#dc2626"))
            cardZoomWidget.setCardBackgroundColor(Color.parseColor("#f5ffffff"))
            cardZoomWidget.strokeColor = Color.parseColor("#cbd5e1")
            btnZoomIn.setTextColor(Color.parseColor("#0f172a"))
            btnZoomOut.setTextColor(Color.parseColor("#0f172a"))
            findViewById<View>(R.id.zoomDivider)?.setBackgroundColor(Color.parseColor("#e2e8f0"))
            (btnExitFullscreen as? MaterialCardView)?.apply {
                setCardBackgroundColor(Color.parseColor("#f2ffffff"))
                strokeColor = Color.parseColor("#cbd5e1")
            }
            findViewById<ImageView>(R.id.ivExitFullscreenIcon)?.setColorFilter(Color.parseColor("#0f172a"))
        } else {
            floatingPill.setCardBackgroundColor(Color.parseColor("#f213131e"))
            floatingPill.strokeColor = Color.parseColor("#334155")
            pillLabel.setTextColor(Color.parseColor("#38bdf8"))
            val pillBtnBg = ColorStateList.valueOf(Color.parseColor("#262638"))
            listOf(btnRotate, btnTag).forEach { b ->
                b.backgroundTintList = pillBtnBg
                b.setTextColor(Color.WHITE)
                if (b is com.google.android.material.button.MaterialButton) {
                    b.iconTint = ColorStateList.valueOf(Color.WHITE)
                }
            }
            btnDelete.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#7f1d1d"))
            btnDelete.setTextColor(Color.parseColor("#fca5a5"))
            (btnDelete as? com.google.android.material.button.MaterialButton)?.iconTint = ColorStateList.valueOf(Color.parseColor("#fca5a5"))
            cardZoomWidget.setCardBackgroundColor(Color.parseColor("#d9161622"))
            cardZoomWidget.strokeColor = Color.parseColor("#2d2d42")
            btnZoomIn.setTextColor(Color.WHITE)
            btnZoomOut.setTextColor(Color.WHITE)
            findViewById<View>(R.id.zoomDivider)?.setBackgroundColor(Color.parseColor("#2d2d42"))
            (btnExitFullscreen as? MaterialCardView)?.apply {
                setCardBackgroundColor(Color.parseColor("#d916161c"))
                strokeColor = Color.parseColor("#40ffffff")
            }
            findViewById<ImageView>(R.id.ivExitFullscreenIcon)?.clearColorFilter()
        }

        // 7. Workspace Overlay
        layoutWorkspaceOverlay.setBackgroundColor(Color.parseColor(if (isLight) "#f8fafc" else "#111114"))
        findViewById<View>(R.id.workspaceTopBar)?.setBackgroundColor(Color.parseColor(if (isLight) "#ffffff" else "#16161c"))
        findViewById<View>(R.id.workspaceBottomBar)?.setBackgroundColor(Color.parseColor(if (isLight) "#ffffff" else "#16161c"))
        val wsDivider = Color.parseColor(if (isLight) "#e2e8f0" else "#1e1e28")
        findViewById<View>(R.id.workspaceTopDivider)?.setBackgroundColor(wsDivider)
        findViewById<View>(R.id.workspaceBottomDivider)?.setBackgroundColor(wsDivider)
        tvWorkspaceHeaderTitle.setTextColor(Color.parseColor(if (isLight) "#0f172a" else "#ffffff"))
        val wsIconColor = Color.parseColor(if (isLight) "#334155" else "#ffffff")
        btnWorkspaceBack.setColorFilter(wsIconColor)
        btnNewProject.setColorFilter(wsIconColor)
        btnWorkspaceImport.setColorFilter(wsIconColor)
        btnWorkspaceScanQr?.setColorFilter(Color.parseColor(if (isLight) "#2563eb" else "#38bdf8"))
        btnWorkspaceThemeToggle?.setImageResource(if (isLight) R.drawable.ic_theme_moon else R.drawable.ic_theme_sun)
        btnWorkspaceThemeToggle?.setColorFilter(wsIconColor)
        btnWorkspaceMore.setColorFilter(wsIconColor)
        if (::workspaceAdapter.isInitialized) {
            workspaceAdapter.setLightTheme(isLight)
            switchWorkspaceTab(activeWorkspaceTab)
        }
    }

    private fun showInstructionsDialog(initialVideoId: String? = null) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_modern_instructions)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dm = resources.displayMetrics
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val dialogWidth = (dm.widthPixels * 0.95f).toInt().coerceAtMost((650 * dm.density).toInt())
        val dialogHeight = if (isLandscape) {
            (dm.heightPixels * 0.92f).toInt()
        } else {
            (dm.heightPixels * 0.72f).toInt().coerceAtMost((540 * dm.density).toInt())
        }
        dialog.window?.setLayout(dialogWidth, dialogHeight)

        val isLight = SettingsManager.isLightTheme(this)
        val webView = dialog.findViewById<WebView>(R.id.webViewInstructions)
        webView?.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            @Suppress("DEPRECATION")
            settings.allowFileAccessFromFileURLs = true
            @Suppress("DEPRECATION")
            settings.allowUniversalAccessFromFileURLs = true
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.displayZoomControls = false
            setBackgroundColor(Color.TRANSPARENT)
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun closeDialog() {
                    runOnUiThread { dialog.dismiss() }
                }
            }, "AndroidBridge")
            val themeParam = if (isLight) "light" else "dark"
            val videoParam = if (!initialVideoId.isNullOrBlank()) {
                "&video=${java.net.URLEncoder.encode(initialVideoId, "UTF-8")}"
            } else {
                ""
            }
            loadUrl("file:///android_asset/instructions/index.html?theme=$themeParam$videoParam")
            scrollTo(0, 0)
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    val themeAttr = if (isLight) "light" else "dark"
                    view?.evaluateJavascript("document.documentElement.setAttribute('data-theme', '$themeAttr');", null)
                    view?.scrollTo(0, 0)
                }
            }
        }

        dialog.setOnDismissListener {
            webView?.evaluateJavascript("if (typeof stopEasterEggBgm === 'function') stopEasterEggBgm(false);", null)
            webView?.loadUrl("about:blank")
        }

        showThemedDialog(dialog)
    }

    private fun showAboutDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_modern_about)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((480 * resources.displayMetrics.density).toInt())
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<TextView>(R.id.tvAboutVersion)?.text = "Versi 5.0.5"
        dialog.findViewById<ImageView>(R.id.btnCloseAbout)?.setOnClickListener {
            dialog.dismiss()
        }

        showThemedDialog(dialog)
    }

    private fun updateSwitchVisuals(
        switch: androidx.appcompat.widget.SwitchCompat,
        statusText: TextView,
        isChecked: Boolean
    ) {
        val isLight = SettingsManager.isLightTheme(this)
        if (isChecked) {
            statusText.text = "ON"
            statusText.setTextColor(Color.parseColor(if (isLight) "#059669" else "#10b981"))
            statusText.setBackgroundResource(R.drawable.bg_status_badge_on)
            switch.trackTintList = ColorStateList.valueOf(Color.parseColor("#2563eb"))
            switch.thumbTintList = ColorStateList.valueOf(Color.parseColor("#ffffff"))
        } else {
            statusText.text = "OFF"
            statusText.setTextColor(Color.parseColor(if (isLight) "#64748b" else "#94a3b8"))
            statusText.setBackgroundResource(R.drawable.bg_status_badge_off)
            switch.trackTintList = ColorStateList.valueOf(Color.parseColor(if (isLight) "#cbd5e1" else "#334155"))
            switch.thumbTintList = ColorStateList.valueOf(Color.parseColor(if (isLight) "#ffffff" else "#64748b"))
        }
    }

    private fun applySettingsDialogTheme(dialog: Dialog, isLight: Boolean) {
        val density = resources.displayMetrics.density
        val rootCard = dialog.findViewById<MaterialCardView>(R.id.cardSettingsRoot) ?: return
        val headerTitle = dialog.findViewById<TextView>(R.id.tvSettingsHeaderTitle)
        val headerIcon = dialog.findViewById<ImageView>(R.id.ivSettingsHeaderIcon)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseSettings)
        val headerDivider = dialog.findViewById<View>(R.id.settingsHeaderDivider)
        val listContainer = dialog.findViewById<ViewGroup>(R.id.layoutSettingsList)
        val spinnerGrid = dialog.findViewById<Spinner>(R.id.spinnerGridSnap)
        val btnClear = dialog.findViewById<Button>(R.id.btnClearCanvas)

        rootCard.setCardBackgroundColor(Color.parseColor(if (isLight) "#ffffff" else "#181824"))
        rootCard.strokeColor = Color.parseColor(if (isLight) "#cbd5e1" else "#2d2d42")
        headerTitle?.setTextColor(Color.parseColor(if (isLight) "#0f172a" else "#ffffff"))
        headerIcon?.setColorFilter(Color.parseColor(if (isLight) "#475569" else "#94a3b8"))
        btnClose?.setColorFilter(Color.parseColor(if (isLight) "#475569" else "#94a3b8"))
        headerDivider?.setBackgroundColor(Color.parseColor(if (isLight) "#e2e8f0" else "#242436"))

        val cardBg = Color.parseColor(if (isLight) "#f8fafc" else "#1e1e2d")
        val cardStroke = Color.parseColor(if (isLight) "#e2e8f0" else "#2c2c40")
        val titleCol = Color.parseColor(if (isLight) "#0f172a" else "#ffffff")
        val subCol = Color.parseColor(if (isLight) "#64748b" else "#94a3b8")

        if (listContainer != null) {
            for (i in 0 until listContainer.childCount) {
                val child = listContainer.getChildAt(i)
                if (child is MaterialCardView) {
                    child.setCardBackgroundColor(cardBg)
                    child.strokeColor = cardStroke
                    fun recolorText(v: View) {
                        if (v.id == R.id.groupThemeMode || v.id == R.id.groupGridStyle ||
                            v.id == R.id.tvStatusAutoSave || v.id == R.id.tvStatusGlowWires ||
                            v.id == R.id.tvStatusPinLabels || v.id == R.id.tvStatusSoundEffects ||
                            v.id == R.id.tvStatusComponentNames
                        ) return
                        if (v is TextView && v !is Button) {
                            val isBold = v.typeface?.isBold == true
                            v.setTextColor(if (isBold) titleCol else subCol)
                        } else if (v is ViewGroup) {
                            for (k in 0 until v.childCount) recolorText(v.getChildAt(k))
                        }
                    }
                    recolorText(child)
                }
            }
        }

        spinnerGrid?.background = AppThemeHelper.createSpinnerDrawable(isLight, density)
        spinnerGrid?.setPopupBackgroundDrawable(ColorDrawable(Color.parseColor(if (isLight) "#ffffff" else "#161622")))
        (spinnerGrid?.selectedView as? TextView)?.apply {
            setTextColor(Color.parseColor(if (isLight) "#0f172a" else "#f1f5f9"))
            setBackgroundColor(Color.TRANSPARENT)
        }
        if (btnClear != null) {
            AppThemeHelper.createClearCanvasDangerStyle(btnClear, isLight)
        }

        // Refresh Segmented Groups
        val groupTheme = dialog.findViewById<View>(R.id.groupThemeMode)
        val btnThemeDark = dialog.findViewById<TextView>(R.id.btnThemeDark)
        val btnThemeLight = dialog.findViewById<TextView>(R.id.btnThemeLight)
        groupTheme?.background = AppThemeHelper.createSegmentedTrackDrawable(isLight, density)
        if (btnThemeDark != null) AppThemeHelper.styleSegmentedOption(btnThemeDark, !isLight, isLight, density)
        if (btnThemeLight != null) AppThemeHelper.styleSegmentedOption(btnThemeLight, isLight, isLight, density)

        val currentGridStyle = canvasView.gridStyle
        val groupGrid = dialog.findViewById<View>(R.id.groupGridStyle)
        val btnGridBoth = dialog.findViewById<TextView>(R.id.btnGridBoth)
        val btnGridDots = dialog.findViewById<TextView>(R.id.btnGridDots)
        val btnGridLines = dialog.findViewById<TextView>(R.id.btnGridLines)
        val btnGridNone = dialog.findViewById<TextView>(R.id.btnGridNone)
        groupGrid?.background = AppThemeHelper.createSegmentedTrackDrawable(isLight, density)
        if (btnGridBoth != null) AppThemeHelper.styleSegmentedOption(btnGridBoth, currentGridStyle == SettingsManager.GRID_BOTH, isLight, density)
        if (btnGridDots != null) AppThemeHelper.styleSegmentedOption(btnGridDots, currentGridStyle == SettingsManager.GRID_DOTS, isLight, density)
        if (btnGridLines != null) AppThemeHelper.styleSegmentedOption(btnGridLines, currentGridStyle == SettingsManager.GRID_LINES, isLight, density)
        if (btnGridNone != null) AppThemeHelper.styleSegmentedOption(btnGridNone, currentGridStyle == SettingsManager.GRID_NONE, isLight, density)
    }

    private fun showSettingsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_modern_settings)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((480 * resources.displayMetrics.density).toInt())
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseSettings)
        val btnThemeDark = dialog.findViewById<TextView>(R.id.btnThemeDark)
        val btnThemeLight = dialog.findViewById<TextView>(R.id.btnThemeLight)
        val btnGridBoth = dialog.findViewById<TextView>(R.id.btnGridBoth)
        val btnGridDots = dialog.findViewById<TextView>(R.id.btnGridDots)
        val btnGridLines = dialog.findViewById<TextView>(R.id.btnGridLines)
        val btnGridNone = dialog.findViewById<TextView>(R.id.btnGridNone)
        val switchAutoSave = dialog.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchAutoSave)
        val tvStatusAutoSave = dialog.findViewById<TextView>(R.id.tvStatusAutoSave)
        val spinnerGrid = dialog.findViewById<Spinner>(R.id.spinnerGridSnap)
        val switchGlow = dialog.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchGlowWires)
        val tvStatusGlowWires = dialog.findViewById<TextView>(R.id.tvStatusGlowWires)
        val switchPin = dialog.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchPinLabels)
        val tvStatusPinLabels = dialog.findViewById<TextView>(R.id.tvStatusPinLabels)
        val switchSound = dialog.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchSoundEffects)
        val tvStatusSound = dialog.findViewById<TextView>(R.id.tvStatusSoundEffects)
        val switchComponentNames = dialog.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switchComponentNames)
        val tvStatusComponentNames = dialog.findViewById<TextView>(R.id.tvStatusComponentNames)
        val btnClear = dialog.findViewById<Button>(R.id.btnClearCanvas)

        btnClose?.setOnClickListener { dialog.dismiss() }

        fun refreshAllSettingsSwitches() {
            if (switchAutoSave != null && tvStatusAutoSave != null) updateSwitchVisuals(switchAutoSave, tvStatusAutoSave, switchAutoSave.isChecked)
            if (switchGlow != null && tvStatusGlowWires != null) updateSwitchVisuals(switchGlow, tvStatusGlowWires, switchGlow.isChecked)
            if (switchPin != null && tvStatusPinLabels != null) updateSwitchVisuals(switchPin, tvStatusPinLabels, switchPin.isChecked)
            if (switchSound != null && tvStatusSound != null) updateSwitchVisuals(switchSound, tvStatusSound, switchSound.isChecked)
            if (switchComponentNames != null && tvStatusComponentNames != null) updateSwitchVisuals(switchComponentNames, tvStatusComponentNames, switchComponentNames.isChecked)
        }

        // 0. Tema Tampilan (Dark CAD / Light Paper)
        fun selectThemeFromSettings(isLight: Boolean) {
            val mode = if (isLight) SettingsManager.THEME_LIGHT else SettingsManager.THEME_DARK
            if (SettingsManager.getThemeMode(this) == mode) return
            SettingsManager.setThemeMode(this, mode)
            applyAppTheme(isLight)
            applySettingsDialogTheme(dialog, isLight)
            refreshAllSettingsSwitches()
            val label = if (isLight) "Tema: Light Paper" else "Tema: Dark CAD"
            Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
        }
        btnThemeDark?.setOnClickListener { selectThemeFromSettings(false) }
        btnThemeLight?.setOnClickListener { selectThemeFromSettings(true) }

        // 2b. Gaya Grid Kanvas (Kombinasi / Titik / Garis / Polos)
        fun selectGridStyleFromSettings(style: String, label: String) {
            if (canvasView.gridStyle == style) return
            canvasView.gridStyle = style
            SettingsManager.setGridStyle(this, style)
            canvasView.invalidate()
            applySettingsDialogTheme(dialog, SettingsManager.isLightTheme(this))
            Toast.makeText(this, "Grid Kanvas: $label", Toast.LENGTH_SHORT).show()
        }
        btnGridBoth?.setOnClickListener { selectGridStyleFromSettings(SettingsManager.GRID_BOTH, "Kombinasi") }
        btnGridDots?.setOnClickListener { selectGridStyleFromSettings(SettingsManager.GRID_DOTS, "Titik") }
        btnGridLines?.setOnClickListener { selectGridStyleFromSettings(SettingsManager.GRID_LINES, "Garis") }
        btnGridNone?.setOnClickListener { selectGridStyleFromSettings(SettingsManager.GRID_NONE, "Polos") }

        // 1. Auto-Save
        val currentAutoSave = SettingsManager.isAutoSaveEnabled(this)
        switchAutoSave?.isChecked = currentAutoSave
        if (switchAutoSave != null && tvStatusAutoSave != null) {
            updateSwitchVisuals(switchAutoSave, tvStatusAutoSave, currentAutoSave)
        }
        switchAutoSave?.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setAutoSaveEnabled(this, isChecked)
            if (tvStatusAutoSave != null) {
                updateSwitchVisuals(switchAutoSave, tvStatusAutoSave, isChecked)
            }
            if (isChecked) {
                triggerAutoSaveIfEnabled(showFeedback = true)
            } else {
                Toast.makeText(this, "Simpan Otomatis Dinonaktifkan", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Grid Snapping
        val gridOptions = listOf("20px (Kecil)", "30px (Standar)", "40px (Besar)", "Nonaktif")
        val gridAdapter = object : ArrayAdapter<String>(this, R.layout.item_spinner_grid, gridOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                if (v is TextView) {
                    val isLight = SettingsManager.isLightTheme(this@MainActivity)
                    v.setTextColor(Color.parseColor(if (isLight) "#0f172a" else "#f1f5f9"))
                    v.setBackgroundColor(Color.TRANSPARENT)
                }
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                if (v is TextView) {
                    val isLight = SettingsManager.isLightTheme(this@MainActivity)
                    v.setTextColor(Color.parseColor(if (isLight) "#0f172a" else "#f1f5f9"))
                    v.setBackgroundColor(Color.parseColor(if (isLight) "#ffffff" else "#161622"))
                }
                return v
            }
        }
        gridAdapter.setDropDownViewResource(R.layout.item_spinner_grid)
        spinnerGrid?.adapter = gridAdapter

        val currentSnap = canvasView.isGridSnapEnabled
        val currentSize = canvasView.GRID_SIZE
        val selectedIdx = when {
            !currentSnap -> 3
            currentSize == 20f -> 0
            currentSize == 40f -> 2
            else -> 1 // 30f standar
        }
        spinnerGrid?.setSelection(selectedIdx)

        var isSpinnerFirstInit = true
        spinnerGrid?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                when (position) {
                    0 -> {
                        canvasView.isGridSnapEnabled = true
                        canvasView.GRID_SIZE = 20f
                        SettingsManager.setGridSnapEnabled(this@MainActivity, true)
                        SettingsManager.setGridSize(this@MainActivity, 20f)
                    }
                    1 -> {
                        canvasView.isGridSnapEnabled = true
                        canvasView.GRID_SIZE = 30f
                        SettingsManager.setGridSnapEnabled(this@MainActivity, true)
                        SettingsManager.setGridSize(this@MainActivity, 30f)
                    }
                    2 -> {
                        canvasView.isGridSnapEnabled = true
                        canvasView.GRID_SIZE = 40f
                        SettingsManager.setGridSnapEnabled(this@MainActivity, true)
                        SettingsManager.setGridSize(this@MainActivity, 40f)
                    }
                    3 -> {
                        canvasView.isGridSnapEnabled = false
                        SettingsManager.setGridSnapEnabled(this@MainActivity, false)
                    }
                }
                isSpinnerFirstInit = false
                canvasView.invalidate()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 3. Glow Wires
        val currentGlow = canvasView.showCurrentFlowAnimation
        switchGlow?.isChecked = currentGlow
        if (switchGlow != null && tvStatusGlowWires != null) {
            updateSwitchVisuals(switchGlow, tvStatusGlowWires, currentGlow)
        }
        switchGlow?.setOnCheckedChangeListener { _, isChecked ->
            canvasView.showCurrentFlowAnimation = isChecked
            SettingsManager.setCurrentFlowAnimEnabled(this, isChecked)
            if (tvStatusGlowWires != null) {
                updateSwitchVisuals(switchGlow, tvStatusGlowWires, isChecked)
            }
            canvasView.invalidate()
        }

        // 4. Pin Labels
        val currentPin = canvasView.showPinLabels
        switchPin?.isChecked = currentPin
        if (switchPin != null && tvStatusPinLabels != null) {
            updateSwitchVisuals(switchPin, tvStatusPinLabels, currentPin)
        }
        switchPin?.setOnCheckedChangeListener { _, isChecked ->
            canvasView.showPinLabels = isChecked
            SettingsManager.setPinLabelsEnabled(this, isChecked)
            if (tvStatusPinLabels != null) {
                updateSwitchVisuals(switchPin, tvStatusPinLabels, isChecked)
            }
            canvasView.invalidate()
        }

        // 5. Sound Effects (Efek Suara Industri)
        val currentSound = SettingsManager.isSoundEnabled(this)
        switchSound?.isChecked = currentSound
        if (switchSound != null && tvStatusSound != null) {
            updateSwitchVisuals(switchSound, tvStatusSound, currentSound)
        }
        switchSound?.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.setSoundEnabled(this, isChecked)
            canvasView.soundManager.isEnabled = isChecked
            if (!isChecked) {
                canvasView.soundManager.stopAllLoops()
            }
            if (tvStatusSound != null) {
                updateSwitchVisuals(switchSound, tvStatusSound, isChecked)
            }
        }

        // 6. Tampilkan Nama Komponen (Component Names Visibility)
        val currentNames = canvasView.showComponentNames
        switchComponentNames?.isChecked = currentNames
        if (switchComponentNames != null && tvStatusComponentNames != null) {
            updateSwitchVisuals(switchComponentNames, tvStatusComponentNames, currentNames)
        }
        switchComponentNames?.setOnCheckedChangeListener { _, isChecked ->
            canvasView.showComponentNames = isChecked
            SettingsManager.setComponentNamesEnabled(this, isChecked)
            if (tvStatusComponentNames != null) {
                updateSwitchVisuals(switchComponentNames, tvStatusComponentNames, isChecked)
            }
            canvasView.invalidate()
        }

        // 7. Clear Canvas
        btnClear?.setOnClickListener {
            dialog.dismiss()
            confirmClearCanvas()
        }

        applySettingsDialogTheme(dialog, SettingsManager.isLightTheme(this))
        dialog.show()
    }

    private fun confirmClearCanvas() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_modern_confirm)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((440 * resources.displayMetrics.density).toInt())
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.findViewById<ImageView>(R.id.ivConfirmHeaderIcon)?.setImageResource(R.drawable.ic_delete)
        dialog.findViewById<TextView>(R.id.tvConfirmHeaderTitle)?.text = "Kosongkan Kanvas"
        dialog.findViewById<TextView>(R.id.tvConfirmMessage)?.text = "Apakah Anda yakin ingin menghapus semua komponen dan kawat pada kanvas?"

        dialog.findViewById<ImageView>(R.id.btnCloseConfirm)?.setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btnCancelConfirm)?.setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.btnActionConfirm)?.apply {
            text = "Kosongkan"
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#ef4444"))
            setOnClickListener {
                canvasView.pushUndoState()
                canvasView.elements.clear()
                canvasView.connectors.clear()
                floatingPill.visibility = View.GONE
                updateFabPosition(false)
                canvasView.invalidate()
                canvasView.notifyUndoRedoState()
                triggerAutoSaveIfEnabled()
                Toast.makeText(this@MainActivity, "Kanvas berhasil dikosongkan", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        showThemedDialog(dialog)
    }

    private fun enterFullscreenMode() {
        closeLibraryPanel()
        appBarLayout.visibility = View.GONE
        btnExitFullscreen.visibility = View.VISIBLE
        fabAdd.visibility = View.GONE
        hideSystemUI()
        updateFabPosition(floatingPill.visibility == View.VISIBLE)
    }

    private fun exitFullscreenMode() {
        appBarLayout.visibility = View.VISIBLE
        btnExitFullscreen.visibility = View.GONE
        fabAdd.visibility = View.VISIBLE
        hideSystemUI()
        updateFabPosition(floatingPill.visibility == View.VISIBLE)
    }

    private fun launchImportPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(intent, REQUEST_CODE_IMPORT)
        } catch (_: Exception) {
            try {
                val fallback = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
                startActivityForResult(fallback, REQUEST_CODE_IMPORT)
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak dapat membuka pengelola berkas: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_QR_IMAGE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            decodeQrFromUri(uri)
            return
        }
        val qrResult = com.google.zxing.integration.android.IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (qrResult != null) {
            if (qrResult.contents != null) {
                handleScannedQrPayload(qrResult.contents)
            }
            return
        }
        if (requestCode == REQUEST_CODE_IMPORT && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            try {
                val content = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: return
                val fileName = getFileNameFromUri(uri) ?: "Sirkuit_Impor"
                val importedName = WorkspaceManager.importCircuitContent(this, fileName, content)
                if (importedName != null) {
                    refreshWorkspaceList()
                    Toast.makeText(this, "Berhasil mengimpor '$importedName'", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Gagal mengimpor: Berkas sirkuit tidak valid", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Error membaca file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        var name: String? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
        if (name == null) {
            name = uri.lastPathSegment
        }
        return name
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION && grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showQrScannerDialog()
        } else if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            Toast.makeText(this, "Izin kamera diperlukan untuk memindai QR", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchQrScanner() {
        if (checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), REQUEST_CODE_CAMERA_PERMISSION)
            return
        }
        showQrScannerDialog()
    }

    private fun showQrScannerDialog() {
        try {
            val dialog = Dialog(this)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            dialog.setContentView(R.layout.dialog_modern_qr_scanner)
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((440 * resources.displayMetrics.density).toInt())
            dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

            val barcodeScannerView = dialog.findViewById<DecoratedBarcodeView>(R.id.zxing_barcode_scanner)
            try {
                barcodeScannerView?.statusView?.visibility = View.GONE
                barcodeScannerView?.viewFinder?.visibility = View.GONE
            } catch (_: Exception) {}

            val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseQrScanner)
            val btnToggleTorch = dialog.findViewById<View>(R.id.btnToggleTorch)
            val ivTorchIcon = dialog.findViewById<ImageView>(R.id.ivTorchIcon)
            val tvTorchText = dialog.findViewById<TextView>(R.id.tvTorchText)
            val btnPickFromGallery = dialog.findViewById<View>(R.id.btnPickFromGallery)
            val tvStatusDeviceInfo = dialog.findViewById<TextView>(R.id.tvStatusDeviceInfo)

            // Dynamic Device & Local IP Telemetry
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val model = Build.MODEL
            val ip = getDeviceLocalIp() ?: "Wi-Fi Terhubung"
            tvStatusDeviceInfo?.text = "$manufacturer $model • $ip"

            // Torch (Flashlight) Control
            var isTorchOn = false
            val isLightQr = SettingsManager.isLightTheme(this)
            btnToggleTorch?.setOnClickListener {
                isTorchOn = !isTorchOn
                if (isTorchOn) {
                    barcodeScannerView?.setTorchOn()
                    val onCol = Color.parseColor(if (isLightQr) "#2563eb" else "#38bdf8")
                    ivTorchIcon?.setColorFilter(onCol)
                    tvTorchText?.setTextColor(onCol)
                } else {
                    barcodeScannerView?.setTorchOff()
                    val offCol = Color.parseColor(if (isLightQr) "#1e293b" else "#f4f4f5")
                    ivTorchIcon?.setColorFilter(offCol)
                    tvTorchText?.setTextColor(offCol)
                }
            }

            // Pick QR image from gallery
            btnPickFromGallery?.setOnClickListener {
                dialog.dismiss()
                launchGalleryPickerForQr()
            }

            // Close dialog
            btnClose?.setOnClickListener {
                dialog.dismiss()
            }

            // Start Scanning (Single Decode)
            barcodeScannerView?.decodeSingle(object : BarcodeCallback {
                override fun barcodeResult(result: BarcodeResult?) {
                    if (result != null && !result.text.isNullOrEmpty()) {
                        dialog.dismiss()
                        handleScannedQrPayload(result.text)
                    }
                }
                override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
            })

            dialog.setOnDismissListener {
                barcodeScannerView?.pause()
            }

            showThemedDialog(dialog)
            barcodeScannerView?.resume()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membuka pemindai: ${e.message}", Toast.LENGTH_SHORT).show()
            promptPasteQrCode()
        }
    }

    private fun launchGalleryPickerForQr() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(Intent.createChooser(intent, "Pilih Gambar QR"), REQUEST_CODE_PICK_QR_IMAGE)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka galeri: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeQrFromUri(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val bitmap = BitmapFactory.decodeStream(stream)
                if (bitmap != null) {
                    val qrResult = decodeQrFromBitmap(bitmap)
                    if (qrResult != null) {
                        handleScannedQrPayload(qrResult)
                        return
                    }
                }
            }
            Toast.makeText(this, "Tidak ada kode QR yang valid pada gambar", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal memproses gambar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
        return try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
            val reader = MultiFormatReader()
            val result = reader.decode(binaryBitmap)
            result.text
        } catch (_: Exception) {
            null
        }
    }

    private fun getDeviceLocalIp(): String? {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                if (intf.isLoopback || !intf.isUp) continue
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress
                    }
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun promptPasteQrCode() {
        val input = EditText(this).apply {
            hint = "Tempel kode sirkuit (smrl://...)"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#64748b"))
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (clipText != null && (clipText.startsWith("smrl://") || clipText.startsWith("http://") || clipText.startsWith("https://"))) {
                setText(clipText)
            }
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("Impor Kode Sirkuit Offline")
            .setView(input)
            .setPositiveButton("Buka") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) handleScannedQrPayload(txt)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun decompressQrPayload(payload: String): String? {
        return try {
            val trimmed = payload.trim()
            if (trimmed.startsWith("smrl://v1/")) {
                val base64 = trimmed.substring("smrl://v1/".length)
                val compressedBytes = android.util.Base64.decode(base64, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.DEFAULT)
                val inflater = java.util.zip.Inflater()
                inflater.setInput(compressedBytes)
                val outputStream = java.io.ByteArrayOutputStream(compressedBytes.size * 4)
                val buffer = ByteArray(1024)
                while (!inflater.finished()) {
                    val count = inflater.inflate(buffer)
                    if (count == 0 && inflater.needsInput()) break
                    outputStream.write(buffer, 0, count)
                }
                inflater.end()
                outputStream.toString("UTF-8")
            } else if (trimmed.startsWith("smrl://v0/")) {
                val base64 = trimmed.substring("smrl://v0/".length)
                val bytes = android.util.Base64.decode(base64, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.DEFAULT)
                String(bytes, Charsets.UTF_8)
            } else if (trimmed.startsWith("{")) {
                trimmed
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadJobsheetInBackground(baseUrl: String, filename: String, onFinished: ((java.io.File?) -> Unit)? = null) {
        if (isJobsheetDownloading) return
        isJobsheetDownloading = true
        val jobsheetDir = java.io.File(filesDir, "jobsheets").apply {
            if (!exists()) mkdirs()
        }
        val safeName = filename.replace(Regex("[^a-zA-Z0-9._-]"), "_").let {
            if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf"
        }
        val stagingFile = java.io.File(jobsheetDir, "staging_task_jobsheet.pdf")
        val tempFile = java.io.File(jobsheetDir, "temp_task_jobsheet.pdf")

        val downloadUrl = if (baseUrl.endsWith("/jobsheet.pdf") || baseUrl.endsWith("/jobsheet")) {
            baseUrl
        } else {
            "${baseUrl.trimEnd('/')}/jobsheet.pdf"
        }

        kotlin.concurrent.thread {
            var successFile: java.io.File? = null
            try {
                val url = java.net.URL(downloadUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 20000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                if (code in 200..299) {
                    conn.inputStream.use { input ->
                        java.io.FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.length() > 0L) {
                        if (stagingFile.exists()) stagingFile.delete()
                        if (tempFile.renameTo(stagingFile)) {
                            successFile = stagingFile
                        } else {
                            tempFile.copyTo(stagingFile, overwrite = true)
                            tempFile.delete()
                            successFile = stagingFile
                        }
                    }
                }
            } catch (_: Exception) {
                runCatching { tempFile.delete() }
            } finally {
                isJobsheetDownloading = false
                if (successFile != null && successFile.exists() && successFile.length() > 0L) {
                    activeJobsheetFilename = safeName
                    val pendingProject = pendingJobsheetProjectName
                    if (pendingProject != null) {
                        val projectFile = java.io.File(jobsheetDir, "$pendingProject.pdf")
                        try {
                            if (successFile.absolutePath != projectFile.absolutePath) {
                                successFile.copyTo(projectFile, overwrite = true)
                            }
                            if (currentProjectName == pendingProject) {
                                activeJobsheetFile = projectFile
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    } else {
                        activeJobsheetFile = successFile
                    }
                }
                runOnUiThread {
                    onFinished?.invoke(activeJobsheetFile)
                }
            }
        }
    }

    private fun openJobsheetPdfFile(file: java.io.File) {
        try {
            val uri: android.net.Uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            isOpeningJobsheet = true
            jobsheetOpenedAtMs = android.os.SystemClock.elapsedRealtime()
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            isOpeningJobsheet = false
            jobsheetOpenedAtMs = 0L
            android.widget.Toast.makeText(this, "Tidak ada aplikasi pembaca PDF di perangkat ini", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            isOpeningJobsheet = false
            jobsheetOpenedAtMs = 0L
            android.widget.Toast.makeText(this, "Gagal membuka jobsheet: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleScannedQrPayload(rawPayload: String) {
        val trimmed = rawPayload.trim()

        // 1. KLASIFIKASI: QR Tugas Guru (Online via HTTP/HTTPS/IP Laptop Guru)
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            val serverUrl = if (trimmed.contains("/task.smrl")) {
                trimmed.substringBeforeLast('/')
            } else {
                trimmed
            }
            lastClassroomServerUrl = serverUrl
            getSharedPreferences("simurelay_classroom", android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("server_url", serverUrl)
                .apply()

            Toast.makeText(this, "Menghubungi Laptop Guru ($serverUrl)...", Toast.LENGTH_SHORT).show()
            kotlin.concurrent.thread {
                try {
                    val url = java.net.URL(trimmed)
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    if (code == 200) {
                        val examHeader = conn.getHeaderField("X-Exam-Mode")
                        val isExam = examHeader.equals("true", ignoreCase = true)
                        isClassroomExamMode = isExam
                        getSharedPreferences("simurelay_classroom", android.content.Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("is_exam_mode", isExam)
                            .apply()
                        val jobsheetHeader = conn.getHeaderField("X-Has-Jobsheet")
                        val jobsheetNameHeader = conn.getHeaderField("X-Jobsheet-Name")
                        val hasModulesHeader = conn.getHeaderField("X-Has-Modules")

                        val downloadedText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                        var hasJs = jobsheetHeader?.equals("true", ignoreCase = true) == true
                        var jsName = jobsheetNameHeader?.takeIf { it.isNotBlank() }
                        var hasMods = hasModulesHeader?.equals("true", ignoreCase = true) == true

                        try {
                            val rootObj = org.json.JSONObject(downloadedText)
                            val jsObj = rootObj.optJSONObject("jobsheet")
                            if (jsObj != null) {
                                if (!hasJs) hasJs = jsObj.optBoolean("available", false)
                                if (jsName.isNullOrEmpty()) jsName = jsObj.optString("filename", "").takeIf { it.isNotBlank() }
                            }
                            val modsMeta = rootObj.optJSONObject("modulesMeta")
                            if (modsMeta != null && !hasMods) {
                                hasMods = modsMeta.optBoolean("available", false)
                            }
                        } catch (_: Exception) {}

                        // Auto-save Modul Guru dari isi /task.smrl atau unduh dari /modules.json
                        val embeddedSaved = PracticalModulesManager.extractAndSaveFromCircuitJson(this@MainActivity, downloadedText)
                        if (embeddedSaved > 0) {
                            runOnUiThread {
                                updateDrawerModulesBadge()
                                Toast.makeText(this@MainActivity, "$embeddedSaved Modul dari Guru tersimpan di menu Modul Praktikum", Toast.LENGTH_LONG).show()
                            }
                        } else if (hasMods) {
                            downloadTeacherModulesInBackground(serverUrl)
                        }

                        pendingJobsheetProjectName = null
                        isJobsheetAvailableOnServer = hasJs
                        activeJobsheetFilename = jsName

                        if (hasJs) {
                            downloadJobsheetInBackground(serverUrl, jsName ?: "jobsheet.pdf")
                        } else {
                            activeJobsheetFile = null
                        }

                        runOnUiThread {
                            showStudentIdentityDialog(downloadedText, serverUrl)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Gagal mengunduh tugas dari guru (HTTP $code)", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Koneksi ke laptop guru terputus: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        // 2. KLASIFIKASI: QR Sirkuit Langsung (Offline SMRL / TXT / JSON)
        val jsonString = decompressQrPayload(trimmed)
        if (jsonString == null) {
            Toast.makeText(this, "Format QR Code bukan data sirkuit Simurelay yang valid", Toast.LENGTH_LONG).show()
            return
        }

        val qrModsSaved = PracticalModulesManager.extractAndSaveFromCircuitJson(this, jsonString)
        if (qrModsSaved > 0) {
            updateDrawerModulesBadge()
            Toast.makeText(this, "$qrModsSaved Modul dari Guru tersimpan di menu Modul Praktikum", Toast.LENGTH_LONG).show()
        }

        showDirectCircuitImportDialog(jsonString)
    }

    private fun showDirectCircuitImportDialog(jsonString: String) {
        try {
            val root = org.json.JSONObject(jsonString)
            val circuitName = root.optString("name", "Sirkuit QR").ifEmpty { "Sirkuit QR" }
            val elements = root.optJSONArray("elements")
            val elementCount = elements?.length() ?: 0

            val teacherClass = root.optString("class").ifEmpty {
                root.optJSONObject("header")?.optString("class") ?: ""
            }
            val teacherSchool = root.optString("school").ifEmpty {
                root.optJSONObject("header")?.optString("school") ?: ""
            }
            if (teacherClass.isNotEmpty() || teacherSchool.isNotEmpty()) {
                val etiketPrefs = getSharedPreferences("simurelay_pdf_etiket", Context.MODE_PRIVATE).edit()
                if (teacherClass.isNotEmpty()) etiketPrefs.putString("pdf_student_class", teacherClass)
                if (teacherSchool.isNotEmpty()) etiketPrefs.putString("pdf_student_school", teacherSchool)
                etiketPrefs.apply()
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_modern_qr_import, null)
            val dialog = android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val tvCircuitName = dialogView.findViewById<TextView>(R.id.tvQrCircuitName)
            val tvLockStatus = dialogView.findViewById<TextView>(R.id.tvQrLockStatus)
            val tvKitStatus = dialogView.findViewById<TextView>(R.id.tvQrKitStatus)
            val btnOpenDirectly = dialogView.findViewById<Button>(R.id.btnQrOpenDirectly)
            val btnSaveToWorkspace = dialogView.findViewById<Button>(R.id.btnQrSaveToWorkspace)
            val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseQrImport)

            tvCircuitName?.text = circuitName
            if (teacherClass.isNotEmpty() || teacherSchool.isNotEmpty()) {
                val infoText = listOf(teacherClass, teacherSchool).filter { it.isNotEmpty() }.joinToString(" • ")
                tvLockStatus?.text = infoText
                tvLockStatus?.setTextColor(Color.parseColor("#38bdf8"))
            } else {
                tvLockStatus?.text = "File Sirkuit Langsung (Offline)"
                tvLockStatus?.setTextColor(Color.parseColor("#10b981"))
            }
            tvKitStatus?.text = "$elementCount Komponen Terdeteksi"

            btnClose?.setOnClickListener { dialog.dismiss() }

            btnOpenDirectly?.setOnClickListener {
                dialog.dismiss()
                val importedName = WorkspaceManager.importCircuitContent(this, circuitName, jsonString)
                if (importedName != null) {
                    closeWorkspaceOverlay()
                    val dir = WorkspaceManager.getWorkspaceDir(this)
                    val targetFile = java.io.File(dir, "$importedName.smrl")
                    loadProjectItem(ProjectItem(name = importedName, file = targetFile, lastModified = targetFile.lastModified(), elementCount = elementCount, isExample = false))
                    setCurrentProject(importedName)
                    refreshWorkspaceList()
                    Toast.makeText(this, "Sirkuit '$importedName' berhasil dibuka", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gagal membuka sirkuit", Toast.LENGTH_SHORT).show()
                }
            }

            btnSaveToWorkspace?.setOnClickListener {
                dialog.dismiss()
                val importedName = WorkspaceManager.importCircuitContent(this, circuitName, jsonString)
                if (importedName != null) {
                    refreshWorkspaceList()
                    Toast.makeText(this, "Sirkuit '$importedName' disimpan ke Workspace", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Gagal menyimpan sirkuit", Toast.LENGTH_SHORT).show()
                }
            }

            showThemedDialog(dialog)
        } catch (e: Exception) {
            Toast.makeText(this, "Error memproses data sirkuit: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showStudentIdentityDialog(jsonString: String, teacherUrl: String? = null) {
        try {
            val root = org.json.JSONObject(jsonString)
            val circuitName = root.optString("name", "Tugas Praktikum").ifEmpty { "Tugas Praktikum" }

            val teacherClass = root.optString("class").ifEmpty {
                root.optJSONObject("header")?.optString("class") ?: ""
            }
            val teacherSchool = root.optString("school").ifEmpty {
                root.optJSONObject("header")?.optString("school") ?: ""
            }
            if (teacherClass.isNotEmpty() || teacherSchool.isNotEmpty()) {
                val etiketPrefs = getSharedPreferences("simurelay_pdf_etiket", Context.MODE_PRIVATE).edit()
                if (teacherClass.isNotEmpty()) etiketPrefs.putString("pdf_student_class", teacherClass)
                if (teacherSchool.isNotEmpty()) etiketPrefs.putString("pdf_student_school", teacherSchool)
                etiketPrefs.apply()
            }

            val dialogView = layoutInflater.inflate(R.layout.dialog_modern_student_identity, null)
            val dialog = android.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .create()
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

            val tvIdentityTaskName = dialogView.findViewById<TextView>(R.id.tvIdentityTaskName)
            val etStudentNameInput = dialogView.findViewById<EditText>(R.id.etStudentNameInput)
            val etStudentPresensiInput = dialogView.findViewById<EditText>(R.id.etStudentPresensiInput)
            val tvStudentAutoFilename = dialogView.findViewById<TextView>(R.id.tvStudentAutoFilename)
            val btnViewTaskJobsheet = dialogView.findViewById<Button>(R.id.btnViewTaskJobsheet)
            val btnCancelStudentIdentity = dialogView.findViewById<Button>(R.id.btnCancelStudentIdentity)
            val btnConfirmStudentIdentity = dialogView.findViewById<Button>(R.id.btnConfirmStudentIdentity)
            val btnCloseStudentIdentity = dialogView.findViewById<ImageView>(R.id.btnCloseStudentIdentity)

            val displayTeacher = if (!teacherUrl.isNullOrEmpty()) " ($teacherUrl)" else ""
            var taskText = "Nama Tugas: $circuitName$displayTeacher"
            if (teacherClass.isNotEmpty() || teacherSchool.isNotEmpty()) {
                val meta = listOf(teacherClass, teacherSchool).filter { it.isNotEmpty() }.joinToString(" • ")
                taskText += "\nTarget: $meta"
            }
            tvIdentityTaskName?.text = taskText
            if (!studentName.isNullOrEmpty()) etStudentNameInput?.setText(studentName)
            if (!studentPresensi.isNullOrEmpty()) etStudentPresensiInput?.setText(studentPresensi)

            fun updateFilenamePreview() {
                val rawName = etStudentNameInput?.text?.toString()?.trim() ?: ""
                val rawPresensi = etStudentPresensiInput?.text?.toString()?.trim() ?: ""
                val cleanName = rawName.replace(Regex("[^a-zA-Z0-9_ -]"), "").replace(Regex("\\s+"), "_").ifEmpty { "Nama" }
                val cleanPresensi = rawPresensi.replace(Regex("[^a-zA-Z0-9]"), "").ifEmpty { "Presensi" }
                val cleanTask = circuitName.replace(Regex("[^a-zA-Z0-9_ -]"), "").replace(Regex("\\s+"), "_").ifEmpty { "Tugas" }
                tvStudentAutoFilename?.text = "${cleanName}_${cleanPresensi}_${cleanTask}.smrl"
            }

            val watcher = object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateFilenamePreview()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }
            etStudentNameInput?.addTextChangedListener(watcher)
            etStudentPresensiInput?.addTextChangedListener(watcher)
            updateFilenamePreview()

            btnViewTaskJobsheet?.setOnClickListener {
                if (!isJobsheetAvailableOnServer && (activeJobsheetFile == null || !activeJobsheetFile!!.exists())) {
                    Toast.makeText(this, "Jobsheet tidak tersedia", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (isJobsheetDownloading) {
                    Toast.makeText(this, "Sedang mengunduh jobsheet, mohon tunggu sebentar...", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val readyFile = activeJobsheetFile
                if (readyFile != null && readyFile.exists() && readyFile.length() > 0L) {
                    openJobsheetPdfFile(readyFile)
                } else if (!teacherUrl.isNullOrEmpty()) {
                    Toast.makeText(this, "Mengunduh jobsheet dari hotspot guru...", Toast.LENGTH_SHORT).show()
                    downloadJobsheetInBackground(teacherUrl, activeJobsheetFilename ?: "jobsheet.pdf") { file ->
                        if (file != null && file.exists()) {
                            openJobsheetPdfFile(file)
                        } else {
                            Toast.makeText(this, "Jobsheet tidak tersedia", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(this, "Jobsheet tidak tersedia", Toast.LENGTH_SHORT).show()
                }
            }

            btnCloseStudentIdentity?.setOnClickListener {
                dialog.dismiss()
                updateActiveJobsheetForProject(currentProjectName)
            }
            btnCancelStudentIdentity?.setOnClickListener {
                dialog.dismiss()
                updateActiveJobsheetForProject(currentProjectName)
            }
            dialog.setOnCancelListener {
                updateActiveJobsheetForProject(currentProjectName)
            }

            btnConfirmStudentIdentity?.setOnClickListener {
                val inputName = etStudentNameInput?.text?.toString()?.trim() ?: ""
                val inputPresensi = etStudentPresensiInput?.text?.toString()?.trim() ?: ""

                if (inputName.isEmpty()) {
                    Toast.makeText(this, "Harap masukkan Nama Lengkap Siswa", Toast.LENGTH_SHORT).show()
                    etStudentNameInput?.requestFocus()
                    return@setOnClickListener
                }
                if (inputPresensi.isEmpty()) {
                    Toast.makeText(this, "Harap masukkan No. Presensi Siswa", Toast.LENGTH_SHORT).show()
                    etStudentPresensiInput?.requestFocus()
                    return@setOnClickListener
                }

                val cleanName = inputName.replace(Regex("[^a-zA-Z0-9_ -]"), "").replace(Regex("\\s+"), "_")
                val cleanPresensi = inputPresensi.replace(Regex("[^a-zA-Z0-9]"), "")
                val cleanTask = circuitName.replace(Regex("[^a-zA-Z0-9_ -]"), "").replace(Regex("\\s+"), "_")
                val finalProjectName = "${cleanName}_${cleanPresensi}_${cleanTask}"

                studentName = inputName
                studentPresensi = inputPresensi
                getSharedPreferences("simurelay_classroom", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("student_name", inputName)
                    .putString("student_presensi", inputPresensi)
                    .apply()

                dialog.dismiss()

                val jobsheetDir = java.io.File(filesDir, "jobsheets").apply { if (!exists()) mkdirs() }
                pendingJobsheetProjectName = finalProjectName

                val stagingJs = activeJobsheetFile
                if (stagingJs != null && stagingJs.exists() && stagingJs.length() > 0L) {
                    val projectJobsheetFile = java.io.File(jobsheetDir, "$finalProjectName.pdf")
                    try {
                        if (stagingJs.absolutePath != projectJobsheetFile.absolutePath) {
                            stagingJs.copyTo(projectJobsheetFile, overwrite = true)
                        }
                        activeJobsheetFile = projectJobsheetFile
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val importedName = WorkspaceManager.importCircuitContent(this, finalProjectName, jsonString)
                if (importedName != null) {
                    if (importedName != finalProjectName) {
                        val oldJs = java.io.File(jobsheetDir, "$finalProjectName.pdf")
                        val newJs = java.io.File(jobsheetDir, "$importedName.pdf")
                        if (oldJs.exists()) {
                            oldJs.renameTo(newJs)
                            activeJobsheetFile = newJs
                        }
                        pendingJobsheetProjectName = importedName
                    }
                    closeWorkspaceOverlay()
                    val dir = WorkspaceManager.getWorkspaceDir(this)
                    val targetFile = java.io.File(dir, "$importedName.smrl")
                    loadProjectItem(ProjectItem(name = importedName, file = targetFile, lastModified = targetFile.lastModified(), elementCount = 0, isExample = false))
                    setCurrentProject(importedName)
                    refreshWorkspaceList()
                    Toast.makeText(this, "Tugas '$importedName' siap dikerjakan", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Gagal membuka tugas praktikum", Toast.LENGTH_SHORT).show()
                }
            }

            showThemedDialog(dialog)

        } catch (e: Exception) {
            Toast.makeText(this, "Error memproses tugas QR: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showCustomExamLockedDialog(message: String) {
        runOnUiThread {
            try {
                val dialogView = layoutInflater.inflate(R.layout.dialog_modern_exam_alert, null)
                val dialog = android.app.AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create()
                dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                val tvMessage = dialogView.findViewById<TextView>(R.id.tvAlertMessage)
                val btnOk = dialogView.findViewById<Button>(R.id.btnAlertOk)
                val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseAlertTop)

                tvMessage?.text = message
                btnOk?.setOnClickListener { dialog.dismiss() }
                btnClose?.setOnClickListener { dialog.dismiss() }

                showThemedDialog(dialog)
            } catch (_: Exception) {
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendTaskToTeacher() {
        val serverUrl = lastClassroomServerUrl
        if (serverUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Belum terhubung ke Hotspot Guru. Silakan Scan QR Tugas Guru terlebih dahulu.", Toast.LENGTH_LONG).show()
            return
        }

        val name = studentName ?: "Siswa"
        val presensi = studentPresensi ?: "-"
        val currentName = currentProjectName
        val cleanFilename = if (currentName.endsWith(".smrl")) currentName else "$currentName.smrl"

        val classroomPrefs = getSharedPreferences("simurelay_classroom", Context.MODE_PRIVATE)
        val isAlreadySubmittedInExam = classroomPrefs.getBoolean("exam_submitted_${cleanFilename}", false)

        if (isClassroomExamMode && isAlreadySubmittedInExam) {
            showCustomExamLockedDialog("Mode Ujian Aktif: Berkas tugas ini telah Anda kirimkan sebelumnya dan telah dikunci. Pengiriman ulang tidak diizinkan oleh sistem guru.")
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_modern_confirm_send_teacher, null)
        val dialog = android.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvFilename = dialogView.findViewById<TextView>(R.id.tvConfirmSendFilename)
        val tvStats = dialogView.findViewById<TextView>(R.id.tvConfirmSendStats)
        val tvPresensi = dialogView.findViewById<TextView>(R.id.tvConfirmSendPresensi)
        val tvStudentName = dialogView.findViewById<TextView>(R.id.tvConfirmSendStudentName)
        val tvServerUrl = dialogView.findViewById<TextView>(R.id.tvConfirmSendServerUrl)
        val cardExamWarning = dialogView.findViewById<View>(R.id.cardExamWarningNotice)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btnCloseConfirmSendTeacher)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancelConfirmSend)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmitConfirmSend)

        tvFilename?.text = cleanFilename
        val count = canvasView.elements.size
        tvStats?.text = "Sirkuit CAD (.smrl) + Laporan PDF 2 Hal • $count Komponen"
        tvPresensi?.text = presensi
        tvStudentName?.text = name
        tvServerUrl?.text = serverUrl

        // Periksa status visual mode ujian
        cardExamWarning?.visibility = if (isClassroomExamMode) View.VISIBLE else View.GONE
        if (isClassroomExamMode) {
            btnSubmit?.text = "KIRIM & KUNCI UJIAN"
        } else {
            btnSubmit?.text = "KIRIM SEKARANG"
        }

        // Live probe ke server guru untuk memastikan status mode ujian sinkron secara real-time
        kotlin.concurrent.thread {
            try {
                val infoUrl = if (serverUrl.endsWith("/")) "${serverUrl}info" else "${serverUrl}/info"
                val conn = java.net.URL(infoUrl).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 2000
                conn.readTimeout = 2000
                if (conn.responseCode == 200) {
                    val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val jsonObj = org.json.JSONObject(resp)
                    if (jsonObj.has("exam_mode")) {
                        val liveExam = jsonObj.getBoolean("exam_mode")
                        isClassroomExamMode = liveExam
                        classroomPrefs.edit().putBoolean("is_exam_mode", liveExam).apply()
                        runOnUiThread {
                            cardExamWarning?.visibility = if (liveExam) View.VISIBLE else View.GONE
                            if (liveExam) {
                                btnSubmit?.text = "KIRIM & KUNCI UJIAN"
                            } else {
                                btnSubmit?.text = "KIRIM SEKARANG"
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnSubmit?.setOnClickListener {
            dialog.dismiss()
            performSendTaskToTeacher(serverUrl, currentName, name, presensi)
        }

        showThemedDialog(dialog)
    }

    private fun performSendTaskToTeacher(serverUrl: String, currentName: String, name: String, presensi: String) {
        Toast.makeText(this, "Menyusun laporan PDF & mengirim ke laptop guru...", Toast.LENGTH_SHORT).show()
        val circuitJson = WorkspaceManager.serializeCircuit(canvasView.elements, canvasView.connectors)
        val submitUrl = if (serverUrl.endsWith("/")) "${serverUrl}submit" else "${serverUrl}/submit"

        val elementsCopy = canvasView.elements.map { it.copy() }
        val connectorsCopy = canvasView.connectors.map { it.copy() }

        kotlin.concurrent.thread {
            try {
                val prefs = getSharedPreferences("simurelay_pdf_etiket", Context.MODE_PRIVATE)
                val sClass = prefs.getString("pdf_student_class", "XII Teknik Instalasi Tenaga Listrik") ?: "XII TITL"
                val sSchool = prefs.getString("pdf_student_school", "SMK Negeri 2") ?: "SMK"
                val studentInfo = StudentPdfInfo(
                    studentName = name,
                    studentPresensi = presensi,
                    studentClass = sClass,
                    studentSchool = sSchool,
                    projectTitle = currentName
                )
                val pdfBytes = NativePdfReportGenerator.generatePdfBytes(
                    context = this@MainActivity,
                    elements = elementsCopy,
                    connectors = connectorsCopy,
                    info = studentInfo
                )
                val pdfBase64 = android.util.Base64.encodeToString(pdfBytes, android.util.Base64.NO_WRAP)
                val baseFilename = if (currentName.endsWith(".smrl")) currentName.removeSuffix(".smrl") else currentName
                val pdfFilename = "$baseFilename.pdf"
                val cleanFilename = if (currentName.endsWith(".smrl")) currentName else "$currentName.smrl"

                // Simpan juga salinan PDF ke folder Download HP siswa
                NativePdfReportGenerator.savePdfToDownloads(this@MainActivity, pdfFilename, pdfBytes)

                val postData = org.json.JSONObject().apply {
                    put("student_name", name)
                    put("presensi_no", presensi)
                    put("filename", cleanFilename)
                    put("content", circuitJson)
                    put("pdf_filename", pdfFilename)
                    put("pdf_base64", pdfBase64)
                }

                val url = java.net.URL(submitUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")

                val bytes = postData.toString().toByteArray(Charsets.UTF_8)
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { os ->
                    os.write(bytes)
                    os.flush()
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val responseText = try {
                        conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } catch (_: Exception) { "" }

                    var successMsg = "Tugas berhasil dikirim ke Laptop Guru"
                    try {
                        val jsonResp = org.json.JSONObject(responseText)
                        if (jsonResp.has("revision")) {
                            val rev = jsonResp.getInt("revision")
                            if (rev > 1) {
                                successMsg = "Revisi ke-$rev berhasil diperbarui di Laptop Guru"
                            }
                        }
                    } catch (_: Exception) {}

                    if (isClassroomExamMode) {
                        getSharedPreferences("simurelay_classroom", Context.MODE_PRIVATE)
                            .edit()
                            .putBoolean("exam_submitted_${cleanFilename}", true)
                            .apply()
                        successMsg = "Tugas Ujian berhasil dikumpulkan & telah dikunci."
                    }

                    val finalSuccessMsg = successMsg
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, finalSuccessMsg, Toast.LENGTH_LONG).show()
                    }
                } else if (code == 403) {
                    // Ditolak karena Mode Ujian (Terkunci)
                    val errorText = try {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    } catch (_: Exception) { "" }

                    var errorMsg = "Mode Ujian Aktif: Tugas Anda telah dikumpulkan sebelumnya dan telah dikunci. Pengumpulan ulang tidak diizinkan."
                    try {
                        val jsonErr = org.json.JSONObject(errorText)
                        if (jsonErr.has("message")) {
                            errorMsg = jsonErr.getString("message")
                        }
                    } catch (_: Exception) {}

                    showCustomExamLockedDialog(errorMsg)
                } else {
                    val errorText = try {
                        conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                    } catch (_: Exception) { "" }
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Server Guru menolak berkas (HTTP $code): $errorText", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Gagal mengirim ke guru: ${e.message}. Pastikan Wi-Fi/Hotspot Guru tetap terhubung.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun exportProject(projectName: String, asLegacyTxt: Boolean) {
        try {
            val file = WorkspaceManager.exportProjectFile(this, projectName, asLegacyTxt)
            if (file != null && file.exists()) {
                val typeName = if (asLegacyTxt) ".txt (Legacy)" else ".smrl (JSON)"
                Toast.makeText(this, "Berhasil mengekspor $typeName ke:\n${file.absolutePath}", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Gagal mengekspor berkas proyek", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error ekspor: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showToolbarMoreMenu(anchor: View) {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val rotLabel = if (isLandscape) "Ubah ke Portrait" else "Ubah ke Landscape"

        val items = mutableListOf(
            CompactMenuItem(1, R.drawable.ic_fullscreen, "Layar Penuh") {
                enterFullscreenMode()
            },
            CompactMenuItem(2, R.drawable.ic_camera, "Tangkapan Layar") {
                captureCanvasScreenshot()
            },
            CompactMenuItem(3, R.drawable.ic_screen_rotation, rotLabel) {
                requestedOrientation = if (isLandscape) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            },
            CompactMenuItem(4, R.drawable.ic_fit_screen, "Pusatkan Sirkuit") {
                canvasView.fitToScreen()
            }
        )

        val readyFile = activeJobsheetFile
        if (readyFile != null && readyFile.exists() && readyFile.length() > 0L) {
            items.add(
                CompactMenuItem(5, R.drawable.ic_description, "Lihat Jobsheet") {
                    openJobsheetPdfFile(readyFile)
                }
            )
        }

        CompactMenuHelper.show(this, anchor, items, widthDp = 185)
    }

    private fun captureCanvasScreenshot() {
        try {
            val bitmap = canvasView.captureBitmap()
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val fileName = "Mashimurelay_Capture_$timeStamp.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(
                        android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        "${android.os.Environment.DIRECTORY_PICTURES}/Mashimurelay"
                    )
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                        outStream.flush()
                    }
                    values.clear()
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    Toast.makeText(this, "Gambar tersimpan ke Galeri:\nPictures/Mashimurelay/$fileName", Toast.LENGTH_LONG).show()
                    return
                }
            }

            // Fallback untuk Android 7–9 (API 24–28) atau jika MediaStore tidak mengembalikan URI
            val publicPictures = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val targetDir = if (publicPictures != null && (publicPictures.exists() || publicPictures.mkdirs()) && publicPictures.canWrite()) {
                java.io.File(publicPictures, "Mashimurelay").apply { if (!exists()) mkdirs() }
            } else {
                getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES) ?: cacheDir
            }
            val file = java.io.File(targetDir, fileName)
            java.io.FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                fos.flush()
            }
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(file.absolutePath),
                arrayOf("image/png"),
                null
            )
            Toast.makeText(this, "Gambar tersimpan ke Galeri:\n${file.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengambil gambar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadForwardReverseTemplate() {
        canvasView.elements.clear()
        canvasView.connectors.clear()
        setCurrentProject("Forward-Reverse Motor")

        // 1. Rangkaian Daya (Power Circuit Column, X = 180f)
        val pwr = NativeElement(1, "sourceL1L2L3N", "-L1L2L3N", "", 180f, 60f)
        val qm = NativeElement(2, "motorBreaker3p", "-QM1", "", 180f, 210f)
        val kmFwd = NativeElement(3, "contactor3p", "-KM1", "Maju", 120f, 380f)
        val kmRev = NativeElement(4, "contactor3p", "-KM2", "Mundur", 240f, 380f)
        val fr = NativeElement(5, "overload3p", "-FR1", "", 180f, 530f)
        val m = NativeElement(6, "motor3ph", "-M1", "", 180f, 710f)

        // 2. Rangkaian Kontrol (Control Circuit Columns)
        val sbStop = NativeElement(7, "buttonNC", "-SB0", "Stop", 400f, 210f)

        // Cabang Kontrol Maju (Forward Branch)
        val sbFwd = NativeElement(8, "buttonNO", "-SB1", "Maju", 360f, 340f)
        val kmFwdNo = NativeElement(9, "contactNO", "-KM1", "13-14", 440f, 340f)
        val kmRevNc = NativeElement(10, "contactNC", "-KM2", "21-22", 360f, 460f)
        val coilFwd = NativeElement(11, "coil", "-KM1", "Maju", 360f, 580f)

        // Cabang Kontrol Mundur (Reverse Branch)
        val sbRev = NativeElement(12, "buttonNO", "-SB2", "Mundur", 540f, 340f)
        val kmRevNo = NativeElement(13, "contactNO", "-KM2", "13-14", 620f, 340f)
        val kmFwdNc = NativeElement(14, "contactNC", "-KM1", "21-22", 540f, 460f)
        val coilRev = NativeElement(15, "coil", "-KM2", "Mundur", 540f, 580f)

        canvasView.elements.addAll(listOf(
            pwr, qm, kmFwd, kmRev, fr, m,
            sbStop, sbFwd, kmFwdNo, kmRevNc, coilFwd,
            sbRev, kmRevNo, kmFwdNc, coilRev
        ))

        // Power Wires (Sumber ke Breaker)
        canvasView.connectors.add(NativeConnector(1, 1, 0, 2, 0)) // L1 -> QM (1)
        canvasView.connectors.add(NativeConnector(2, 1, 1, 2, 2)) // L2 -> QM (3)
        canvasView.connectors.add(NativeConnector(3, 1, 2, 2, 4)) // L3 -> QM (5)

        // Breaker ke Kontaktor Maju (KM1)
        canvasView.connectors.add(NativeConnector(4, 2, 1, 3, 0)) // QM (2) -> KM1 (1)
        canvasView.connectors.add(NativeConnector(5, 2, 3, 3, 2)) // QM (4) -> KM1 (3)
        canvasView.connectors.add(NativeConnector(6, 2, 5, 3, 4)) // QM (6) -> KM1 (5)

        // Breaker ke Kontaktor Mundur (KM2)
        canvasView.connectors.add(NativeConnector(7, 2, 1, 4, 0)) // QM (2) -> KM2 (1)
        canvasView.connectors.add(NativeConnector(8, 2, 3, 4, 2)) // QM (4) -> KM2 (3)
        canvasView.connectors.add(NativeConnector(9, 2, 5, 4, 4)) // QM (6) -> KM2 (5)

        // Kontaktor Maju ke Overload (Urutan Fasa Normal L1-L2-L3 -> CW)
        canvasView.connectors.add(NativeConnector(10, 3, 1, 5, 0)) // KM1 (2) -> FR (1) [U]
        canvasView.connectors.add(NativeConnector(11, 3, 3, 5, 2)) // KM1 (4) -> FR (3) [V]
        canvasView.connectors.add(NativeConnector(12, 3, 5, 5, 4)) // KM1 (6) -> FR (5) [W]

        // Kontaktor Mundur ke Overload (Pembalikan Fasa L1 & L3 -> CCW)
        canvasView.connectors.add(NativeConnector(13, 4, 1, 5, 4)) // KM2 (2) -> FR (5) [W - Ditukar]
        canvasView.connectors.add(NativeConnector(14, 4, 3, 5, 2)) // KM2 (4) -> FR (3) [V]
        canvasView.connectors.add(NativeConnector(15, 4, 5, 5, 0)) // KM2 (6) -> FR (1) [U - Ditukar]

        // Overload ke Terminal Motor 3 Phasa
        canvasView.connectors.add(NativeConnector(16, 5, 1, 6, 0)) // FR (2) -> Motor U
        canvasView.connectors.add(NativeConnector(17, 5, 3, 6, 1)) // FR (4) -> Motor V
        canvasView.connectors.add(NativeConnector(18, 5, 5, 6, 2)) // FR (6) -> Motor W

        // Control Wires: L1 ke Tombol Stop
        canvasView.connectors.add(NativeConnector(19, 1, 0, 7, 0)) // L1 -> SB0 Stop (1)
        canvasView.connectors.add(NativeConnector(20, 7, 1, 8, 0)) // SB0 (2) -> SB1 Maju (3)
        canvasView.connectors.add(NativeConnector(21, 7, 1, 9, 0)) // SB0 (2) -> KM1 NO (13) (Paralel)
        canvasView.connectors.add(NativeConnector(22, 7, 1, 12, 0)) // SB0 (2) -> SB2 Mundur (3)
        canvasView.connectors.add(NativeConnector(23, 7, 1, 13, 0)) // SB0 (2) -> KM2 NO (13) (Paralel)

        // Cabang Kontrol Maju (Self-holding & Interlock NC KM2)
        canvasView.connectors.add(NativeConnector(24, 8, 1, 9, 1)) // SB1 (4) -> KM1 NO (14)
        canvasView.connectors.add(NativeConnector(25, 8, 1, 10, 0)) // SB1 (4) -> KM2 NC Interlock (21)
        canvasView.connectors.add(NativeConnector(26, 10, 1, 11, 0)) // KM2 NC (22) -> Coil KM1 Maju (A1)

        // Cabang Kontrol Mundur (Self-holding & Interlock NC KM1)
        canvasView.connectors.add(NativeConnector(27, 12, 1, 13, 1)) // SB2 (4) -> KM2 NO (14)
        canvasView.connectors.add(NativeConnector(28, 12, 1, 14, 0)) // SB2 (4) -> KM1 NC Interlock (21)
        canvasView.connectors.add(NativeConnector(29, 14, 1, 15, 0)) // KM1 NC (22) -> Coil KM2 Mundur (A1)

        // Jalur Netral ke Kedua Koil Kontaktor
        canvasView.connectors.add(NativeConnector(30, 11, 1, 1, 3)) // Coil KM1 (A2) -> Netral N
        canvasView.connectors.add(NativeConnector(31, 15, 1, 1, 3)) // Coil KM2 (A2) -> Netral N

        canvasView.engine.manualStates[qm.id] = 1 // Breaker tertutup (ready)
        canvasView.clearUndoHistory()
        canvasView.invalidate()
        markCurrentStateAsSaved()
        canvasView.post { canvasView.fitToScreen() }
    }

    override fun onPause() {
        super.onPause()
        triggerAutoSaveIfEnabled(showFeedback = false)
    }

    override fun onResume() {
        super.onResume()
        if (isOpeningJobsheet) {
            isOpeningJobsheet = false
            jobsheetOpenedAtMs = 0L
        }
    }

    override fun onBackPressed() {
        if (::layoutLoadingOverlay.isInitialized && layoutLoadingOverlay.visibility == View.VISIBLE) {
            return
        }
        if (btnExitFullscreen.visibility == View.VISIBLE) {
            exitFullscreenMode()
            return
        }
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
            return
        }
        if (isLibraryOpen) {
            closeLibraryPanel()
            return
        }

        // 1. Jika pengguna berada di halaman Workspace Overlay (Modul / Workspace / Examples)
        if (layoutWorkspaceOverlay.visibility == View.VISIBLE) {
            if (activeWorkspaceTab == "modules" && activeDetailModule != null) {
                closeInlineModuleDetail()
                return
            }
            if (activeWorkspaceTab != "workspace") {
                switchWorkspaceTab("workspace")
                return
            }
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastWorkspaceBackTime < 2000) {
                finish()
            } else {
                lastWorkspaceBackTime = currentTime
                Toast.makeText(this, "Tekan kembali sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // 2. Jika pengguna berada di Canvas proyek -> Kembali ke Workspace
        handleExitCanvasToWorkspace()
    }

    // Circuit Baseline Snapshot for Unsaved Changes Tracking
    private var lastSavedSnapshot: NativeCanvasView.CircuitSnapshot? = null

    private fun markCurrentStateAsSaved() {
        lastSavedSnapshot = canvasView.createSnapshot()
    }

    private fun hasUnsavedChanges(): Boolean {
        val baseline = lastSavedSnapshot ?: return false
        val current = canvasView.createSnapshot()
        if (baseline.elements.size != current.elements.size || baseline.connectors.size != current.connectors.size) {
            return true
        }
        val sortedBaseElements = baseline.elements.sortedBy { it.id }
        val sortedCurrElements = current.elements.sortedBy { it.id }
        for (i in sortedBaseElements.indices) {
            if (sortedBaseElements[i] != sortedCurrElements[i]) return true
        }

        val sortedBaseConnectors = baseline.connectors.sortedBy { it.id }
        val sortedCurrConnectors = current.connectors.sortedBy { it.id }
        for (i in sortedBaseConnectors.indices) {
            if (sortedBaseConnectors[i] != sortedCurrConnectors[i]) return true
        }

        return false
    }

    private fun handleExitCanvasToWorkspace() {
        stopSimulationIfRunning(restoreEditPill = false)
        if (SettingsManager.isAutoSaveEnabled(this)) {
            triggerAutoSaveIfEnabled(showFeedback = false)
            openWorkspaceOverlay()
        } else if (hasUnsavedChanges()) {
            showUnsavedChangesDialog()
        } else {
            openWorkspaceOverlay()
        }
    }

    private fun showUnsavedChangesDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_modern_unsaved_changes)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val dialogWidth = (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtMost((460 * resources.displayMetrics.density).toInt())
        dialog.window?.setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

        val tvMessage = dialog.findViewById<TextView>(R.id.tvUnsavedMessage)
        val btnClose = dialog.findViewById<ImageView>(R.id.btnCloseUnsaved)
        val btnSave = dialog.findViewById<Button>(R.id.btnSaveUnsaved)
        val btnDontSave = dialog.findViewById<Button>(R.id.btnDontSaveUnsaved)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancelUnsaved)

        tvMessage?.text = "Proyek '$currentProjectName' memiliki perubahan yang belum disimpan. Apakah Anda ingin menyimpannya sebelum kembali ke Workspace?"

        btnClose?.setOnClickListener { dialog.dismiss() }
        btnCancel?.setOnClickListener { dialog.dismiss() }

        btnSave?.setOnClickListener {
            dialog.dismiss()
            saveCurrentProject()
            openWorkspaceOverlay()
        }

        btnDontSave?.setOnClickListener {
            dialog.dismiss()
            reloadCurrentProjectFromDisk()
            openWorkspaceOverlay()
        }

        showThemedDialog(dialog)
    }

    private fun reloadCurrentProjectFromDisk() {
        try {
            val examples = WorkspaceManager.listExamples()
            val exampleItem = examples.find { it.name == currentProjectName }
            if (exampleItem != null) {
                loadProjectItem(exampleItem)
                return
            }
            val projects = WorkspaceManager.listProjects(this)
            val projectItem = projects.find { it.name == currentProjectName }
            if (projectItem != null) {
                loadProjectItem(projectItem)
                return
            }
        } catch (_: Exception) {}
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window?.attributes?.let { lp ->
                    if (lp.layoutInDisplayCutoutMode != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
                        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        window?.attributes = lp
                    }
                }
            }

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } catch (_: Exception) {}
    }

    private fun updateDrawerModulesBadge() {
        val total = PracticalModulesManager.getAllModules(this).size
        tvDrawerModulesBadge?.text = total.toString()
    }

    private fun downloadTeacherModulesInBackground(serverUrl: String) {
        kotlin.concurrent.thread {
            try {
                val base = serverUrl.trimEnd('/')
                val modUrl = if (base.endsWith("/modules.json") || base.endsWith("/modules")) base else "$base/modules.json"
                val conn = (java.net.URL(modUrl).openConnection() as java.net.HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    requestMethod = "GET"
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val saved = PracticalModulesManager.upsertCustomModulesFromJsonArray(this@MainActivity, text)
                    if (saved > 0) {
                        runOnUiThread {
                            updateDrawerModulesBadge()
                            Toast.makeText(
                                this@MainActivity,
                                "$saved Modul dari Guru otomatis tersimpan di menu Modul Praktikum",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun showPracticalModulesDialog(initialTrack: String = "all") {
        activeModuleFilter = initialTrack
        activeDetailModule = null
        openWorkspaceOverlay("modules")
    }

    private fun pmDp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun makePmRoundedDrawable(
        fill: Int,
        stroke: Int,
        radiusDp: Int,
        strokeWidthDp: Int = 1
    ): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = pmDp(radiusDp).toFloat()
            setColor(fill)
            if (strokeWidthDp > 0) setStroke(pmDp(strokeWidthDp), stroke)
        }
    }

    private fun ensureInlineModulesWired() {
        if (isInlineModulesWired) return
        val root = layoutInlineModulesPage ?: return
        isInlineModulesWired = true

        val chipAll = root.findViewById<TextView>(R.id.chipFilterAll)
        val chipInd = root.findViewById<TextView>(R.id.chipFilterIndustri)
        val chipOto = root.findViewById<TextView>(R.id.chipFilterOtomotif)
        val chipGuru = root.findViewById<TextView>(R.id.chipFilterGuru)
        val etSearch = root.findViewById<EditText>(R.id.etPmSearch)
        val svCards = root.findViewById<ScrollView>(R.id.svPmCards)

        chipAll?.setOnClickListener { activeModuleFilter = "all"; renderInlineModulesList(); svCards?.scrollTo(0, 0) }
        chipInd?.setOnClickListener { activeModuleFilter = "industri"; renderInlineModulesList(); svCards?.scrollTo(0, 0) }
        chipOto?.setOnClickListener { activeModuleFilter = "otomotif"; renderInlineModulesList(); svCards?.scrollTo(0, 0) }
        chipGuru?.setOnClickListener { activeModuleFilter = "guru"; renderInlineModulesList(); svCards?.scrollTo(0, 0) }

        etSearch?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                activeModuleSearchQuery = s?.toString() ?: ""
                renderInlineModulesList()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun confirmDeleteInlineTeacherModule(mod: PracticalModule) {
        if (mod.isBuiltin) return
        val alert = android.app.AlertDialog.Builder(this)
            .setTitle("Hapus Modul Guru?")
            .setMessage("Hapus modul \"${mod.title}\" dari penyimpanan lokal HP ini? Modul bawaan tidak terpengaruh.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                PracticalModulesManager.deleteCustomModule(this, mod.id)
                Toast.makeText(this, "Modul \"${mod.title}\" dihapus", Toast.LENGTH_SHORT).show()
                if (activeDetailModule?.id == mod.id) {
                    closeInlineModuleDetail()
                } else {
                    renderInlineModulesList()
                }
            }
            .create()
        showThemedDialog(alert)
    }

    private fun openInlineModuleDetail(mod: PracticalModule) {
        activeDetailModule = mod
        switchWorkspaceTab("modules")
    }

    private fun closeInlineModuleDetail() {
        activeDetailModule = null
        switchWorkspaceTab("modules")
    }

    private fun renderInlineModulesList() {
        val root = layoutInlineModulesPage ?: return
        val isLight = SettingsManager.isLightTheme(this)

        val bgCard = Color.parseColor(if (isLight) "#ffffff" else "#1a1a25")
        val bgSubCard = Color.parseColor(if (isLight) "#ffffff" else "#16161f")
        val strokeColor = Color.parseColor(if (isLight) "#cbd5e1" else "#2a2a3c")
        val textPrimary = Color.parseColor(if (isLight) "#0f172a" else "#f8fafc")
        val textSecondary = Color.parseColor(if (isLight) "#475569" else "#94a3b8")
        val accentBlue = Color.parseColor(if (isLight) "#2563eb" else "#38bdf8")
        val accentAmber = Color.parseColor(if (isLight) "#b45309" else "#fbbf24")
        val accentSlate = Color.parseColor(if (isLight) "#334155" else "#cbd5e1")
        val accentDanger = Color.parseColor(if (isLight) "#dc2626" else "#f87171")

        val layoutListView = root.findViewById<LinearLayout>(R.id.layoutPmListView)
        val layoutDetailView = root.findViewById<LinearLayout>(R.id.layoutPmDetailView)
        layoutListView?.visibility = View.VISIBLE
        layoutDetailView?.visibility = View.GONE

        val chipAll = root.findViewById<TextView>(R.id.chipFilterAll)
        val chipInd = root.findViewById<TextView>(R.id.chipFilterIndustri)
        val chipOto = root.findViewById<TextView>(R.id.chipFilterOtomotif)
        val chipGuru = root.findViewById<TextView>(R.id.chipFilterGuru)
        val etSearch = root.findViewById<EditText>(R.id.etPmSearch)
        val cardsContainer = root.findViewById<LinearLayout>(R.id.pmCardsContainer)

        val customCount = PracticalModulesManager.getCustomModules(this).size
        chipGuru?.text = "Modul Guru ($customCount)"
        updateDrawerModulesBadge()

        val chips = listOf(
            Pair(chipAll, "all"),
            Pair(chipInd, "industri"),
            Pair(chipOto, "otomotif"),
            Pair(chipGuru, "guru")
        )
        for ((chip, key) in chips) {
            if (chip == null) continue
            val selected = activeModuleFilter == key
            if (selected) {
                chip.background = makePmRoundedDrawable(Color.parseColor("#2563eb"), Color.parseColor("#2563eb"), 8)
                chip.setTextColor(Color.WHITE)
            } else {
                chip.background = makePmRoundedDrawable(bgCard, strokeColor, 8)
                chip.setTextColor(textSecondary)
            }
        }

        etSearch?.background = makePmRoundedDrawable(bgSubCard, strokeColor, 8)
        etSearch?.setTextColor(textPrimary)
        etSearch?.setHintTextColor(textSecondary)

        cardsContainer?.removeAllViews()
        val allMods = PracticalModulesManager.getAllModules(this)
        val q = activeModuleSearchQuery.trim().lowercase()
        val filtered = allMods.filter { mod ->
            val trackOk = when (activeModuleFilter) {
                "guru" -> !mod.isBuiltin
                "industri" -> mod.track == "industri"
                "otomotif" -> mod.track == "otomotif"
                else -> true
            }
            val queryOk = if (q.isEmpty()) true else {
                val hay = listOf(mod.code, mod.title, mod.subject, mod.author, mod.summary, mod.theory)
                    .joinToString(" ").lowercase()
                hay.contains(q)
            }
            trackOk && queryOk
        }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = if (activeModuleFilter == "guru" && q.isEmpty()) {
                    "Belum ada Modul Guru tersimpan di perangkat ini.\n\nSaat guru membagikan tugas berisi Modul Mandiri melalui QR Code atau Hotspot LAN, modul akan otomatis tersimpan di sini agar dapat dipelajari secara offline."
                } else {
                    "Tidak ditemukan modul yang cocok dengan pencarian."
                }
                setTextColor(textSecondary)
                textSize = 12.5f
                gravity = android.view.Gravity.CENTER
                setPadding(pmDp(18), pmDp(28), pmDp(18), pmDp(28))
                background = makePmRoundedDrawable(bgCard, strokeColor, 10)
            }
            cardsContainer?.addView(emptyTv)
            return
        }

        for (mod in filtered) {
            val cardLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = makePmRoundedDrawable(bgCard, strokeColor, 11)
                setPadding(pmDp(14), pmDp(12), pmDp(14), pmDp(12))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = pmDp(10) }
                isClickable = true
                isFocusable = true
                setOnClickListener { openInlineModuleDetail(mod) }
            }

            // Top row: Track Badge + Code
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val badgeBg: Int
            val badgeStroke: Int
            val badgeTextCol: Int
            val badgeLabel: String
            when {
                !mod.isBuiltin -> {
                    badgeBg = Color.parseColor(if (isLight) "#f1f5f9" else "#1e293b")
                    badgeStroke = Color.parseColor(if (isLight) "#cbd5e1" else "#475569")
                    badgeTextCol = accentSlate
                    badgeLabel = "MODUL GURU"
                }
                mod.track == "otomotif" -> {
                    badgeBg = Color.parseColor(if (isLight) "#fef3c7" else "#33240d")
                    badgeStroke = Color.parseColor(if (isLight) "#fcd34d" else "#92400e")
                    badgeTextCol = accentAmber
                    badgeLabel = "OTOMOTIF · TKR/TSM"
                }
                else -> {
                    badgeBg = Color.parseColor(if (isLight) "#dbeafe" else "#122942")
                    badgeStroke = Color.parseColor(if (isLight) "#93c5fd" else "#1e40af")
                    badgeTextCol = accentBlue
                    badgeLabel = "INDUSTRI · TITL"
                }
            }
            val tvBadge = TextView(this).apply {
                text = badgeLabel
                setTextColor(badgeTextCol)
                textSize = 10f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(pmDp(8), pmDp(2), pmDp(8), pmDp(2))
                background = makePmRoundedDrawable(badgeBg, badgeStroke, 5)
            }
            val tvCode = TextView(this).apply {
                text = mod.code
                setTextColor(textSecondary)
                textSize = 11f
                setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                gravity = android.view.Gravity.END
            }
            topRow.addView(tvBadge)
            topRow.addView(tvCode)
            cardLayout.addView(topRow)

            // Title
            cardLayout.addView(TextView(this).apply {
                text = mod.title
                setTextColor(textPrimary)
                textSize = 14.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = pmDp(7) }
            })

            // Summary
            if (mod.summary.isNotEmpty() || mod.theory.isNotEmpty()) {
                cardLayout.addView(TextView(this).apply {
                    text = mod.summary.ifEmpty { mod.theory }
                    setTextColor(textSecondary)
                    textSize = 12f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = pmDp(4) }
                })
            }

            // Bottom row: Meta (tanpa target penyelesaian wajib) + Hapus / Buka
            val footerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = pmDp(9) }
            }
            val kitPart = if (mod.allowedComponents.isNotEmpty()) " · Kit ${mod.allowedComponents.size}" else ""
            footerRow.addView(TextView(this).apply {
                text = "${mod.level} · ${mod.duration}$kitPart"
                setTextColor(textSecondary)
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            if (!mod.isBuiltin) {
                footerRow.addView(TextView(this).apply {
                    text = "Hapus"
                    setTextColor(accentDanger)
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setPadding(pmDp(10), pmDp(4), pmDp(10), pmDp(4))
                    background = makePmRoundedDrawable(
                        Color.parseColor(if (isLight) "#fef2f2" else "#2a1419"),
                        Color.parseColor(if (isLight) "#fca5a5" else "#7f1d1d"),
                        6
                    )
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { confirmDeleteInlineTeacherModule(mod) }
                })
            } else {
                footerRow.addView(TextView(this).apply {
                    text = "Buka ->"
                    setTextColor(accentBlue)
                    textSize = 11.5f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
            }
            cardLayout.addView(footerRow)
            cardsContainer?.addView(cardLayout)
        }
    }

    private fun renderInlineModuleDetail(mod: PracticalModule) {
        val root = layoutInlineModulesPage ?: return
        val isLight = SettingsManager.isLightTheme(this)

        val bgHeader = Color.parseColor(if (isLight) "#ffffff" else "#16161c")
        val bgCard = Color.parseColor(if (isLight) "#ffffff" else "#1a1a25")
        val bgSubCard = Color.parseColor(if (isLight) "#f8fafc" else "#13131d")
        val strokeColor = Color.parseColor(if (isLight) "#cbd5e1" else "#2a2a3c")
        val dividerColor = Color.parseColor(if (isLight) "#e2e8f0" else "#1e1e28")
        val textPrimary = Color.parseColor(if (isLight) "#0f172a" else "#f8fafc")
        val textSecondary = Color.parseColor(if (isLight) "#475569" else "#94a3b8")
        val accentBlue = Color.parseColor(if (isLight) "#2563eb" else "#38bdf8")
        val accentAmber = Color.parseColor(if (isLight) "#b45309" else "#fbbf24")
        val accentDanger = Color.parseColor(if (isLight) "#dc2626" else "#f87171")

        // Palet tombol sekunder senada (tanpa ungu & tanpa hijau kontras)
        val secBtnText = Color.parseColor(if (isLight) "#1e293b" else "#e2e8f0")
        val secBtnStroke = Color.parseColor(if (isLight) "#cbd5e1" else "#3b475c")
        val secBtnFill = Color.parseColor(if (isLight) "#f8fafc" else "#1c2233")

        val layoutListView = root.findViewById<LinearLayout>(R.id.layoutPmListView)
        val layoutDetailView = root.findViewById<LinearLayout>(R.id.layoutPmDetailView)
        layoutListView?.visibility = View.GONE
        layoutDetailView?.visibility = View.VISIBLE

        root.findViewById<View>(R.id.pmFooterDivider)?.setBackgroundColor(dividerColor)
        root.findViewById<View>(R.id.pmDetailFooter)?.setBackgroundColor(bgHeader)

        val btnDeleteDetail = findViewById<TextView>(R.id.btnPmDeleteDetail)
        btnDeleteDetail?.background = makePmRoundedDrawable(
            Color.parseColor(if (isLight) "#fef2f2" else "#2a1419"),
            Color.parseColor(if (isLight) "#fca5a5" else "#7f1d1d"),
            7
        )
        btnDeleteDetail?.setTextColor(accentDanger)
        btnDeleteDetail?.setOnClickListener { confirmDeleteInlineTeacherModule(mod) }

        val svDetail = root.findViewById<ScrollView>(R.id.svPmDetail)
        val detailBody = root.findViewById<LinearLayout>(R.id.pmDetailBodyContainer)
        val btnActionVideo = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPmActionVideo)
        val btnActionExample = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPmActionExample)
        val btnActionPractice = root.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPmActionPractice)

        listOf(btnActionVideo, btnActionExample).forEach { b ->
            b?.setTextColor(secBtnText)
            b?.strokeColor = ColorStateList.valueOf(secBtnStroke)
            b?.backgroundTintList = ColorStateList.valueOf(secBtnFill)
        }
        btnActionPractice?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#2563eb"))
        btnActionPractice?.setTextColor(Color.WHITE)

        detailBody?.removeAllViews()

        fun addSectionBox(title: String, isK3: Boolean = false, buildContent: (LinearLayout) -> Unit) {
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val fillCol = if (isK3) Color.parseColor(if (isLight) "#fffbeb" else "#241d0f") else bgCard
                val strCol = if (isK3) Color.parseColor(if (isLight) "#fcd34d" else "#854d0e") else strokeColor
                background = makePmRoundedDrawable(fillCol, strCol, 10)
                setPadding(pmDp(13), pmDp(11), pmDp(13), pmDp(11))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = pmDp(10) }
            }
            box.addView(TextView(this).apply {
                text = title.uppercase()
                setTextColor(if (isK3) accentAmber else textPrimary)
                textSize = 11.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = pmDp(6) }
            })
            buildContent(box)
            detailBody?.addView(box)
        }

        // Hero Header Card
        val heroBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makePmRoundedDrawable(bgCard, strokeColor, 10)
            setPadding(pmDp(13), pmDp(12), pmDp(13), pmDp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = pmDp(10) }
        }
        heroBox.addView(TextView(this).apply {
            text = "${mod.code} · ${mod.subject} · ${mod.author} · ${mod.duration}"
            setTextColor(accentBlue)
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        heroBox.addView(TextView(this).apply {
            text = mod.title
            setTextColor(textPrimary)
            textSize = 16.5f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pmDp(4) }
        })
        if (mod.summary.isNotEmpty()) {
            heroBox.addView(TextView(this).apply {
                text = mod.summary
                setTextColor(textSecondary)
                textSize = 12.5f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = pmDp(5) }
            })
        }
        detailBody?.addView(heroBox)

        if (mod.objectives.isNotEmpty()) {
            addSectionBox("Tujuan Pembelajaran") { box ->
                mod.objectives.forEachIndexed { idx, obj ->
                    box.addView(TextView(this).apply {
                        text = "${idx + 1}. $obj"
                        setTextColor(textSecondary)
                        textSize = 12.5f
                        setPadding(0, pmDp(2), 0, pmDp(2))
                    })
                }
            }
        }

        if (mod.safetyK3.isNotEmpty()) {
            addSectionBox("Keselamatan Kerja (K3)", isK3 = true) { box ->
                mod.safetyK3.forEach { item ->
                    box.addView(TextView(this).apply {
                        text = "- $item"
                        setTextColor(textPrimary)
                        textSize = 12.2f
                        setPadding(0, pmDp(2), 0, pmDp(2))
                    })
                }
            }
        }

        if (mod.theory.isNotEmpty() || mod.pinTable.isNotEmpty()) {
            addSectionBox("Ringkasan Teori & Kode Terminal") { box ->
                if (mod.theory.isNotEmpty()) {
                    box.addView(TextView(this).apply {
                        text = mod.theory
                        setTextColor(textSecondary)
                        textSize = 12.5f
                    })
                }
                if (mod.pinTable.isNotEmpty()) {
                    for (pin in mod.pinTable) {
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(pmDp(8), pmDp(6), pmDp(8), pmDp(6))
                            background = makePmRoundedDrawable(bgSubCard, strokeColor, 6)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply { topMargin = pmDp(6) }
                        }
                        row.addView(TextView(this).apply {
                            text = pin.code
                            setTextColor(accentBlue)
                            textSize = 11.5f
                            setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(pmDp(116), LinearLayout.LayoutParams.WRAP_CONTENT)
                        })
                        row.addView(TextView(this).apply {
                            text = pin.desc
                            setTextColor(textPrimary)
                            textSize = 11.8f
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        box.addView(row)
                    }
                }
            }
        }

        if (mod.allowedComponents.isNotEmpty()) {
            addSectionBox("Daftar Komponen Praktikum (${mod.allowedComponents.size} Komponen)") { box ->
                val names = mod.allowedComponents.map { key ->
                    NativeComponentRegistry.get(key)?.name ?: key
                }
                box.addView(TextView(this).apply {
                    text = names.joinToString("  •  ")
                    setTextColor(textPrimary)
                    textSize = 12f
                })
            }
        }

        if (mod.steps.isNotEmpty()) {
            addSectionBox("Langkah Wiring Bertahap") { box ->
                mod.steps.forEachIndexed { idx, step ->
                    box.addView(TextView(this).apply {
                        text = "${idx + 1}. $step"
                        setTextColor(textPrimary)
                        textSize = 12.5f
                        setPadding(0, pmDp(3), 0, pmDp(3))
                    })
                }
            }
        }

        if (mod.checks.isNotEmpty()) {
            addSectionBox("Panduan Pengujian Mandiri") { box ->
                mod.checks.forEach { checkText ->
                    box.addView(TextView(this).apply {
                        text = "•  $checkText"
                        setTextColor(textPrimary)
                        textSize = 12.3f
                        setPadding(0, pmDp(3), 0, pmDp(3))
                    })
                }
            }
        }

        // Extra secondary video button inside detail if available (senada slate-biru)
        if (!mod.secondaryVideoId.isNullOrBlank()) {
            val secBtn = com.google.android.material.button.MaterialButton(this).apply {
                text = "Tonton Video Tambahan (${mod.secondaryVideoId.replace('_', ' ')})"
                isAllCaps = false
                setTextColor(secBtnText)
                backgroundTintList = ColorStateList.valueOf(secBtnFill)
                this.strokeColor = ColorStateList.valueOf(secBtnStroke)
                strokeWidth = pmDp(1)
                cornerRadius = pmDp(8)
                elevation = 0f
                stateListAnimator = null
                setOnClickListener {
                    showInstructionsDialog(mod.secondaryVideoId)
                }
            }
            detailBody?.addView(secBtn)
        }

        svDetail?.scrollTo(0, 0)

        // Action Footer Buttons
        btnActionVideo?.visibility = if (!mod.videoId.isNullOrBlank()) View.VISIBLE else View.GONE
        btnActionVideo?.setOnClickListener {
            showInstructionsDialog(mod.videoId)
        }

        btnActionExample?.visibility = if (!mod.exampleCircuitJson.isNullOrBlank()) View.VISIBLE else View.GONE
        btnActionExample?.setOnClickListener {
            val exJson = mod.exampleCircuitJson ?: return@setOnClickListener
            try {
                stopSimulationIfRunning(restoreEditPill = false)
                val circuit = WorkspaceManager.deserializeCircuit(exJson)
                canvasView.elements.clear()
                canvasView.elements.addAll(circuit.first)
                canvasView.connectors.clear()
                canvasView.connectors.addAll(circuit.second)
                canvasView.clearUndoHistory()
                WorkspaceManager.currentAssignmentKit = null
                if (::visualAdapter.isInitialized) {
                    visualAdapter.setAllowedComponentFilter(null)
                }
                setCurrentProject("Contoh: ${mod.title}")
                canvasView.showPinLabels = true
                SettingsManager.setPinLabelsEnabled(this, true)
                canvasView.invalidate()
                closeWorkspaceOverlay()
                canvasView.post { canvasView.fitToScreen() }
                Toast.makeText(this, "Contoh \"${mod.title}\" dimuat. Tekan RUN untuk simulasi!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal memuat contoh rangkaian: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        val kitCount = mod.allowedComponents.size
        btnActionPractice?.text = if (kitCount > 0) "Latihan (+ Kit $kitCount)" else "Mulai Latihan"
        btnActionPractice?.setOnClickListener {
            stopSimulationIfRunning(restoreEditPill = false)
            canvasView.elements.clear()
            canvasView.connectors.clear()
            canvasView.clearUndoHistory()
            if (kitCount > 0) {
                val kit = AssignmentKit(
                    enabled = true,
                    name = mod.title,
                    allowedComponents = mod.allowedComponents
                )
                WorkspaceManager.currentAssignmentKit = kit
                if (::visualAdapter.isInitialized) {
                    visualAdapter.setAllowedComponentFilter(kit.allowedComponents.toSet())
                }
            } else {
                WorkspaceManager.currentAssignmentKit = null
                if (::visualAdapter.isInitialized) {
                    visualAdapter.setAllowedComponentFilter(null)
                }
            }
            setCurrentProject("Latihan: ${mod.title}")
            canvasView.invalidate()
            closeWorkspaceOverlay()
            Toast.makeText(
                this,
                if (kitCount > 0) "Mode Latihan dimulai dengan Kit $kitCount komponen!" else "Kanvas latihan \"${mod.title}\" siap!",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            SvgComponentRenderer.clearCache()
            NativePdfReportGenerator.clearCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        SvgComponentRenderer.clearCache()
        NativePdfReportGenerator.clearCache()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::webViewLoading.isInitialized) {
                webViewLoading.destroy()
            }
        } catch (_: Exception) {}
        autoSaveExecutor.shutdown()
        SvgComponentRenderer.clearCache()
        NativePdfReportGenerator.clearCache()
    }
}
