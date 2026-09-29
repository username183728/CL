package com.example.aidetest

import android.Manifest
import android.app.*
import android.app.usage.StorageStatsManager
import android.os.StatFs
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.*
import android.provider.Settings
import android.provider.MediaStore
import android.media.ImageReader
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.text.InputType
import android.view.*
import android.widget.*
import android.webkit.MimeTypeMap
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.MultiFormatReader
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.NotFoundException
import com.google.zxing.common.HybridBinarizer
import java.io.*
import java.net.*
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.*
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.math.pow
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : Activity() {

    internal lateinit var content: LinearLayout
    internal lateinit var loadingUi: LoadingUi
    internal lateinit var scroll: ScrollView
    internal lateinit var title: TextView
    internal lateinit var subtitle: TextView
    internal lateinit var back: TextView
    internal lateinit var action: TextView
    internal lateinit var homeMenu: ImageButton
    internal lateinit var homeProfile: ImageButton
    internal lateinit var search: EditText
    // Search bar "Snap / Enter Always": a small scroll gesture is enough to hide/show it.
    internal var searchSnapHidden = false
    internal var searchSnapLastY = 0
    internal var searchSnapAccumulator = 0
    internal var searchSnapAnimating = false
    internal lateinit var bottomNav: LinearLayout
    internal lateinit var navFavorite: View
    internal lateinit var loginScreen: LinearLayout
    internal lateinit var mainContainer: LinearLayout
    internal lateinit var topBar: LinearLayout
    // Saat keyboard/IME terbuka, bottom navigation disembunyikan agar tidak
    // ikut naik dan menempel di atas keyboard. Setelah keyboard ditutup, nav
    // kembali ke posisi bawah seperti semula.
    internal var imeVisible = false
    internal var imeBottomInset = 0

    internal var currentPage = "home"

    // Navigation + UI state preservation. Each rendered page is kept as an actual View tree,
    // so EditText contents, selections, toggle states and ScrollView position survive Back.
    internal data class PageSnapshot(
        val name: String,
        val children: MutableList<View>,
        val scrollY: Int,
        val searchText: String,
        val searchVisible: Int,
        val lightweight: Boolean = false
    )
    internal val pageBackStack = ArrayDeque<PageSnapshot>()
    internal var restoringSnapshot = false
    internal var resettingRootNavigation = false
    internal var editorFile: File? = null
    internal var editorMode = "text"
    internal var editorLastSelection = ""
    internal var editorBox: EditText? = null
    internal var editorNameLabel: TextView? = null
    internal var editorStatusLabel: TextView? = null
    internal var editorContextActions: LinearLayout? = null
    internal lateinit var editorBottomBar: LinearLayout
    internal var editorLanding = false
    internal var editorPendingTarget: EditText? = null
    internal var editorExternalTarget: EditText? = null
    internal var editorExternalMode: String? = null
    internal lateinit var editorMore: TextView
    // Semua kalkulator dirender dalam satu workspace; perpindahan mode tidak membuka halaman baru.
    internal var embeddedCalculatorRender = false
    internal var calculatorSelectedMode = "basiccalc"
    internal var homeFilter = "Semua"
    internal var toolCenterFilter = "Semua"
    internal var server: ServerSocket? = null
    internal var hotspotReservation: WifiManager.LocalOnlyHotspotReservation? = null
    internal var hostingStatusView: TextView? = null
    internal var hostingUrlView: TextView? = null
    internal var hostingCredentialsView: TextView? = null
    internal var hostingQrView: ImageView? = null
    internal val HOTSPOT_PERMISSION_REQUEST = 9901
    internal var pendingHostingPort = 8080
    internal var pendingHostingRoot: File? = null
    internal var webImportTarget: EditText? = null
    internal var webBuildReady = false
    internal var webHostButton: Button? = null
    internal var webBuildStatusView: TextView? = null
    internal var webHostingToken = ""
    internal val WEB_HTML_PICK_REQUEST = 9821
    internal val WEB_CSS_PICK_REQUEST = 9822
    internal val WEB_JS_PICK_REQUEST = 9823
    internal var suppressSearch = false
    // Animasi daftar tool hanya diputar sekali saat sesi aplikasi dimulai.
    // Setelah pengguna masuk ke tool lalu kembali ke Beranda, daftar tetap stabil tanpa replay.
    internal var initialToolAnimationPlayed = false
    internal lateinit var prefs: android.content.SharedPreferences
    internal lateinit var toolPreferences: com.example.aidetest.core.ToolPreferences
    internal lateinit var toolHistory: com.example.aidetest.core.ToolHistory

    internal var isDarkTheme = false
    internal var clipboardManager: android.content.ClipboardManager? = null
    internal var clipboardListener: android.content.ClipboardManager.OnPrimaryClipChangedListener? = null
    internal var networkScanStop = AtomicBoolean(false)
    internal val COLOR_PICKER_CAPTURE_REQUEST = 7421
    internal val COLOR_PHOTO_PICK_REQUEST = 7422
    internal val COLOR_PHOTO_CAMERA_REQUEST = 7423
    internal val COLOR_PALETTE_EXPORT_REQUEST = 7424
    internal var pendingColorPaletteExportFormat = "json"
    internal var currentPhotoPalette = mutableListOf<Pair<Int, Int>>()
    internal var colorPhotoView: ImageView? = null
    internal var colorPhotoBitmap: Bitmap? = null
    internal var colorPhotoMarker: View? = null
    internal var colorPhotoSelected = Color.rgb(25, 25, 27)
    internal var colorPhotoStatus: TextView? = null
    internal var colorPhotoHex: TextView? = null
    internal var colorPhotoRgb: TextView? = null
    internal var colorPhotoHsl: TextView? = null
    internal var colorPhotoPalette: LinearLayout? = null
    internal var colorPhotoCameraUri: Uri? = null
    internal var colorPhotoSelectionUpdater: ((Int) -> Unit)? = null
    internal var colorPhotoPlaceholder: View? = null
    internal var colorPickerUiUpdater: ((Int) -> Unit)? = null
    internal val colorPickerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ColorPickerService.ACTION_COLOR_PICKED) return
            val color = intent.getIntExtra(ColorPickerService.EXTRA_COLOR, Color.WHITE)
            colorPickerUiUpdater?.invoke(color)
        }
    }


    internal var pendingOtaEndpoint = ""
    internal val FINANCE_EXPORT_CREATE = 2003
    internal val FINANCE_BACKUP_CREATE = 2004
    internal val FINANCE_BACKUP_OPEN = 2005
    internal val GITHUB_ZIP_PICK_REQUEST = 12801
    internal val GITHUB_FOLDER_PICK_REQUEST = 12802
    internal var githubFolderUri: Uri? = null
    internal var githubFolderLabel: TextView? = null
    internal var githubFolderPreview: TextView? = null
    internal var githubZipUri: Uri? = null
    internal var githubZipLabel: TextView? = null
    internal var githubUploadStatus: TextView? = null
    internal var githubZipRoot = ""
    internal val githubZipExcluded = linkedSetOf<String>()
    internal var githubZipPreviewFiles = emptyList<String>()
    internal var githubZipPreviewDirs = emptyList<String>()
    internal var espSensorPolling = false
    internal var espSensorHandler: Handler? = null
    internal var espSensorRunnable: Runnable? = null
    internal var nsdDiscoveryManager: NsdManager? = null
    internal var nsdDiscoveryListener: NsdManager.DiscoveryListener? = null
    internal var colorPickerProjectionResultCode = 0
    internal var colorPickerProjectionData: Intent? = null

    // ActivityResult / tool state restored for the security, image and studio tools.
    internal val SECURITY_FILE_PICK = 1101
    internal val STEGO_ENCODE_PICK = 1102
    internal val STEGO_DECODE_PICK = 1103
    internal val CERT_PICK = 1104
    internal var securityFileUri: Uri? = null
    internal var stegoImageUri: Uri? = null
    internal var certFileUri: Uri? = null
    internal var fileHashUriA: Uri? = null
    internal var fileHashUriB: Uri? = null
    internal var fileHashCompareLabelA: TextView? = null
    internal var fileHashCompareLabelB: TextView? = null
    internal var imageInfoResult: ((Uri) -> Unit)? = null
    internal var imageToolsResult: ((Uri) -> Unit)? = null
    internal var studioCanvas: StudioCanvasView? = null
    internal var studioNextId = 1
    internal var studioSelectedLinkId: String? = null
    internal val studioPrefsKey = "studio_widgets_v1"
    internal val studioLinksPrefsKey = "studio_links_v1"


    internal val homeTools = listOf(
        "workspace" to "Workspace Center", "plugincenter" to "Plugin Center", "filemanager" to "File Manager", "recentfiles" to "Recent Files", "backuprestore" to "Backup / Restore", "editor" to "Editor", "reminder" to "Notifikasi", "zip" to "ZIP / UNZIP", "githubzip" to "GitHub Publisher",
        "wifi" to "Wi-Fi Info", "json" to "JSON Tools", "hash" to "Hash Generator",
        "base64" to "Base64", "url" to "URL Tools", "regex" to "Regex Tester",
        "uuid" to "UUID Generator", "color" to "Color Tools", "number" to "Kalkulator Lengkap",
        "textstat" to "Statistik Teks", "case" to "Case Converter", "dedupe" to "Hapus Duplikat",
        "compare" to "Bandingkan Teks", "slug" to "Slug Generator", "lorem" to "Lorem Ipsum",
        "password" to "Password Generator", "token" to "Token Acak", "jwt" to "JWT Decoder",
        "hmac" to "HMAC Generator", "totp" to "TOTP Generator", "aes" to "AES Encrypt / Decrypt",
        "random" to "Random Bytes", "checksum" to "Checksum File", "hex" to "Hex Converter",
        "base32" to "Base32", "dns" to "DNS Lookup", "rdns" to "Reverse DNS",
        "port" to "Port Checker", "publicip" to "IP Publik", "ping" to "Ping",
        "ipinfo" to "IP Address Info", "ssl" to "SSL Certificate", "apk" to "APK Inspector",
        "qr" to "QR Scanner", "system" to "Sistem", "http" to "HTTP Server", "webhostwifi" to "HTML Hosting Wi-Fi",
        "fileconvert" to "Konversi File",
        "timestamp" to "Timestamp Converter", "unicode" to "Unicode Inspector",
        "urlparser" to "URL Parser", "mime" to "MIME Type Lookup", "jsonformat" to "JSON Formatter",
        "xmlformat" to "XML Formatter", "uuidbatch" to "UUID Batch Generator", "base64file" to "Base64 File Tool",
        "httpheaders" to "HTTP Headers", "textreplace" to "Find & Replace", "wordfreq" to "Word Frequency",
        "deviceinfo" to "Device Info", "storage" to "Storage Analyzer", "apps" to "App Manager",
        "network" to "Network Info", "battery" to "Battery Info", "filesearch" to "File Search",
        "pivotcalc" to "Pivot Point", "dividercalc" to "Voltage Divider", "dcacalc" to "Averaging Down & DCA",
        "pwmcalc" to "PWM & Duty Cycle", "spritecalc" to "Sprite Sheet Grid", "installcalc" to "Bunga Flat vs Anuitas",
        "powercalc" to "Konsumsi Listrik", "aspectcalc" to "Aspect Ratio", "pphcalc" to "PPN & PPh Final",
        "financereader" to "Pengelola Keuangan", "financedashboard" to "Finance Dashboard", "securitycenter" to "Security Center", "helpbot" to "HelpBot Offline",
        "filehashcompare" to "File Hash Compare", "markdown" to "Markdown Viewer",
        "sql" to "SQL Tools", "yaml" to "YAML Formatter", "toml" to "TOML Inspector",
        "cron" to "Cron Helper", "passwordstrength" to "Password Strength",
        "fileencryption" to "File Encryption", "steganography" to "Steganography", "passwordanalyzer" to "Password Strength Analyzer", "breachchecker" to "Data Breach Checker", "securenotes" to "Secure Notes", "totpvault" to "2FA Manager (TOTP)", "pgp" to "PGP Encrypt / Decrypt", "sshkeygen" to "SSH Key Generator", "certviewer" to "Certificate Viewer", "virusscanner" to "Virus Scanner", "urlsafety" to "URL Safety Checker",
        "stopwatch" to "Stopwatch", "timer" to "Timer",
        "imageinfo" to "Image Metadata", "imagetools" to "Image Resize / Compress",
        "restclient" to "REST / API Client", "websocket" to "WebSocket Client",
        "networkcenter" to "Network Center", "systemcenter" to "System Center",
        "apkcompare" to "APK Compare", "duplicatefinder" to "Duplicate Finder",
        "largefilefinder" to "Large File Finder",
        // Studio utama juga menjadi item Beranda + indeks pencarian global.
        // Daftarnya tetap dipertahankan di Pengaturan pada bagian Studio & Tools.
        "webproject" to "Web Project Builder", "networkstudio" to "Network Studio",
        "developerstudio" to "Developer Studio", "filestudio" to "File Studio",
        "imagestudio" to "Image Studio", "financestudio" to "Finance Studio",
        "systemstudio" to "System Studio", "utilitystudio" to "Utility Studio",
        "workspace" to "Workspace Center", "plugincenter" to "Plugin Center", "customtools" to "Tool Customization", "studiocenter" to "Studio Center"
    )

    // Cached indexes: avoid repeated O(n) scans/toMap() while the user scrolls/searches.
    internal val homeToolMap by lazy(LazyThreadSafetyMode.NONE) { homeTools.toMap() }
    internal val homeToolSearchIndex by lazy(LazyThreadSafetyMode.NONE) {
        homeTools.map { it.first to it.second.lowercase(Locale.getDefault()) }
    }

    internal var dark = Color.rgb(10, 10, 11)
    internal var panel = Color.rgb(22, 22, 24)
    internal var panel2 = Color.rgb(28, 28, 31)
    internal var textMain = Color.rgb(245, 245, 247)
    internal var textMuted = Color.rgb(155, 155, 160)
    internal var line = Color.rgb(48, 48, 52)

    internal fun enableImmersiveFullscreen() {
        // oldt.py explicitly keeps Android system bars visible.
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(R.layout.activity_main)
        prefs = getSharedPreferences("mytools_prefs", MODE_PRIVATE)
        toolPreferences = com.example.aidetest.core.ToolPreferences(this)
        toolHistory = com.example.aidetest.core.ToolHistory(this)
        migrateToolStateToModularStore()
        syncToolUpdates()
        // Android 13+ requires an explicit export flag for dynamically registered receivers.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                colorPickerReceiver,
                IntentFilter(ColorPickerService.ACTION_COLOR_PICKED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(colorPickerReceiver, IntentFilter(ColorPickerService.ACTION_COLOR_PICKED))
        }
        sanitizeSensitiveHistory()
        applySystemTheme()
        enableImmersiveFullscreen()

        content = findViewById(R.id.content)
        loadingUi = LoadingUi(this)
        loadingUi.attach(findViewById(android.R.id.content))
        scroll = findViewById(R.id.scroll)
        title = findViewById(R.id.tvTitle)
        subtitle = findViewById(R.id.tvSubtitle)
        back = findViewById(R.id.btnBack)
        action = findViewById(R.id.btnAction)
        homeMenu = findViewById(R.id.homeMenu)
        homeProfile = findViewById(R.id.homeProfile)
        search = findViewById(R.id.searchBox)
        bottomNav = findViewById(R.id.bottomNav)
        editorBottomBar = findViewById(R.id.editorBottomBar)
        editorMore = findViewById(R.id.editorMore)
        loginScreen = findViewById(R.id.loginScreen)
        mainContainer = findViewById(R.id.mainContainer)
        topBar = findViewById(R.id.topBar)

        // Bottom navigation uses MdiIconView; initialize glyphs once and keep tint updates type-safe.
        listOf(
            R.id.navHomeIcon to "home-outline",
            R.id.navToolsIcon to "view-grid-outline",
            R.id.navFavoriteIcon to "star-outline",
            R.id.navSettingsIcon to "cog-outline"
        ).forEach { (id, iconName) ->
            findViewById<MdiIconView>(id).apply {
                setIconName(iconName)
                setIconSize(23f)
            }
        }

        // Keyboard-safe bottom navigation: keep the nav anchored below the keyboard
        // instead of letting it float directly above the IME when adjustResize runs.
        ViewCompat.setOnApplyWindowInsetsListener(mainContainer) { _, insets ->
            imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val rootPage = currentPage == "home" || currentPage == "all" ||
                    currentPage == "favorites" || currentPage == "settings"

            // Keep the navigation bar anchored to the app's bottom. When the keyboard
            // opens, adjustResize moves the parent bottom upward; translating the nav
            // by the IME inset pushes it back down behind the keyboard instead of making
            // it float directly above the keyboard.
            imeBottomInset = if (imeVisible) {
                insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            } else 0
            bottomNav.translationY = 0f
            bottomNav.visibility = if (rootPage && !imeVisible) View.VISIBLE else View.GONE
            insets
        }
        ViewCompat.requestApplyInsets(mainContainer)
        applyUiColors()
        enterApp()
        // Maintenance dijalankan secara defensif agar tidak pernah menggagalkan startup.
        runCatching { scheduleFinanceMaintenance() }

        back.setOnClickListener { navigateBack() }
        action.setOnClickListener { showAbout() }
        // Search must never rebuild the whole page on every keystroke.
        // IME composition on Android can emit several text events per character;
        // debouncing keeps the EditText responsive and lets the keyboard finish its
        // composing transaction before the result list is rendered.
        search.addTextChangedListener(DebouncedSearchWatcher { query ->
            if (!suppressSearch) filterCurrent(query)
        })
        // Keyboard-safe root navigation: the bottom navigation must never become a
        // second toolbar above the keyboard while the user is typing/searching.
        search.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                bottomNav.visibility = View.GONE
            } else {
                search.postDelayed({
                    val rootPage = currentPage == "home" || currentPage == "all" ||
                            currentPage == "favorites" || currentPage == "settings"
                    if (rootPage && !imeVisible) bottomNav.visibility = View.VISIBLE
                }, 120L)
            }
        }
        search.setOnEditorActionListener { _, actionId, event ->
            val submit = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                    actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                    (event?.keyCode == KeyEvent.KEYCODE_ENTER)
            if (submit) {
                search.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(search.windowToken, 0)
                true
            } else false
        }
        // Snap / Enter Always: the search bar does not require reaching the top.
        // A small scroll gesture is enough to hide it while scrolling through tools,
        // and a small gesture in the opposite direction brings it back. The bar is
        // actually removed from layout when hidden, so the content gets more space.
        scroll.setOnScrollChangeListener { _, scrollY, _, _, _ ->
            val progress = (scrollY / dp(220).toFloat()).coerceIn(0f, 1f)
            title.alpha = 1f - (progress * 0.08f)
            subtitle.alpha = 1f - (progress * 0.18f)
            homeMenu.alpha = 1f - (progress * 0.10f)
            homeProfile.alpha = 1f - (progress * 0.10f)

            if (currentPage == "home" || currentPage == "all") {
                val delta = scrollY - searchSnapLastY
                searchSnapLastY = scrollY
                if (delta != 0) {
                    // Swipe/scroll sedikit ke bawah: search meluncur ke atas dan menghilang.
                    // Swipe/scroll sedikit kembali ke atas: search meluncur turun dan muncul.
                    // Tidak perlu kembali ke scrollY = 0.
                    // Accumulate tiny native scroll events so one short swipe is enough,
                    // without making the bar flicker from one-pixel jitter.
                    searchSnapAccumulator += delta
                    // Very small gesture is enough: the bar behaves like a snap/enter-always
                    // control instead of requiring the user to reach the top of the page.
                    val threshold = dp(4)
                    if (searchSnapAccumulator >= threshold) {
                        searchSnapAccumulator = 0
                        hideSearchSnap()
                    } else if (searchSnapAccumulator <= -threshold) {
                        searchSnapAccumulator = 0
                        showSearchSnap()
                    }
                }
            }
        }
        findViewById<View>(R.id.navHome).setOnClickListener { navigateRoot { showHome() } }
        findViewById<View>(R.id.navTools).setOnClickListener { navigateRoot { showAllTools() } }
        navFavorite = findViewById(R.id.navFavorite)
        navFavorite.setOnClickListener { navigateRoot { showFavorites() } }
        findViewById<View>(R.id.navSettings).setOnClickListener { navigateRoot { showSettings() } }
        listOf(R.id.navHome, R.id.navTools, R.id.navFavorite, R.id.navSettings, R.id.navAdd).forEach { id ->
            addPressFeedback(findViewById(id))
        }
        findViewById<View>(R.id.navAdd).setOnClickListener { showAllTools() }
        homeMenu.setOnClickListener { showAbout() }
        homeProfile.setOnClickListener { showAbout() }

        // Only "MASUK" is functional: it reveals the main app and lands on Beranda.
        // DAFTAR and "Lanjut tanpa akun" are visual-only, matching the reference design.
        findViewById<Button>(R.id.btnMasuk).setOnClickListener { enterApp() }
        // Fitur tambahan tidak boleh membuat aplikasi mental ke Home bila ada masalah device/API.
        runCatching { setupDynamicShortcuts() }
        runCatching { MyToolsWidget.update(this) }
        if (intent?.getBooleanExtra("open_finance", false) == true) { enterApp(); financeReaderTool() }
        else if (intent?.getBooleanExtra("open_iot", false) == true) { enterApp(); openTool("espstudio") }
        else if (intent?.getBooleanExtra("quick_expense", false) == true) { enterApp(); financeReaderTool(); showAddTxDialog(FinanceDb(this), false) }
        else if (intent?.getBooleanExtra("quick_income", false) == true) { enterApp(); financeReaderTool(); showAddTxDialog(FinanceDb(this), true) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::loadingUi.isInitialized) {
            loadingUi.dismiss(findViewById(android.R.id.content), 120L)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent); setIntent(intent)
        if (intent?.getBooleanExtra("open_finance", false) == true) {
            enterApp(); financeReaderTool()
        } else if (intent?.getBooleanExtra("open_iot", false) == true) {
            enterApp(); openTool("iotdashboard")
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data?.data != null) {
            when (requestCode) {
                SECURITY_FILE_PICK -> { securityFileUri = data.data; toast("File dipilih") ; return }
                STEGO_ENCODE_PICK -> { stegoImageUri = data.data; toast("Gambar dipilih untuk encode"); return }
                STEGO_DECODE_PICK -> { stegoImageUri = data.data; decodeStegoFromUri(data.data!!); return }
                CERT_PICK -> { certFileUri = data.data; viewCertificate(data.data!!); return }
                GITHUB_FOLDER_PICK_REQUEST -> {
                    githubFolderUri = data.data
                    data.data?.let { uri ->
                        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        val doc = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, uri)
                        githubFolderLabel?.text = doc?.name ?: "Folder dipilih"
                        githubFolderPreview?.text = "Memindai isi folder…"
                        thread {
                            val names = mutableListOf<String>()
                            fun scan(d: androidx.documentfile.provider.DocumentFile, prefix: String) {
                                d.listFiles().forEach { child ->
                                    val n = child.name ?: return@forEach
                                    if (n == ".git" || n == "__MACOSX" || n == ".DS_Store" || n == "Thumbs.db") return@forEach
                                    val rel = if (prefix.isBlank()) n else "$prefix/$n"
                                    if (child.isDirectory) scan(child, rel) else if (child.isFile) names.add(rel)
                                }
                            }
                            runCatching { if (doc != null) scan(doc, "") }
                            runOnUiThread {
                                val shown = names.take(12).joinToString("\n")
                                githubFolderPreview?.text = "${names.size} file ditemukan" + if (shown.isNotBlank()) "\n$shown" + if (names.size > 12) "\n… dan ${names.size - 12} file lainnya" else "" else "\nFolder kosong atau tidak bisa dibaca"
                                githubUploadStatus?.text = "Folder dipilih. Periksa daftar file, lalu tekan Simpan & Upload."
                            }
                        }
                    }
                    return
                }
                GITHUB_ZIP_PICK_REQUEST -> {
                    githubZipUri = data.data
                    githubZipExcluded.clear()
                    githubZipRoot = ""
                    githubZipPreviewFiles = emptyList()
                    githubZipPreviewDirs = emptyList()
                    val name = data.data?.let { queryName(it) } ?: "ZIP dipilih"
                    val zipSize = data.data?.let { u -> runCatching { contentResolver.openFileDescriptor(u, "r")?.use { it.statSize } }.getOrNull() } ?: -1L
                    githubZipLabel?.text = if (zipSize > 0) "$name • ${ghFormatBytes(zipSize)}" else name
                    githubUploadStatus?.text = "Menganalisis struktur ZIP..."
                    data.data?.let { prepareGithubZipPreview(it) }
                    return
                }
                WEB_HTML_PICK_REQUEST, WEB_CSS_PICK_REQUEST, WEB_JS_PICK_REQUEST -> {
                    val uri = data.data!!
                    runCatching {
                        contentResolver.openInputStream(uri)?.use { it.readBytes().toString(StandardCharsets.UTF_8) }
                            ?: error("File tidak dapat dibaca")
                    }.onSuccess { text ->
                        webImportTarget?.setText(text)
                        webBuildReady = false
                        webBuildStatusView?.text = "BELUM BUILD • File berhasil dimuat, tekan Build untuk validasi"
                        webHostButton?.isEnabled = false
                        webImportTarget = null
                        toast("File web berhasil dimuat")
                    }.onFailure { toast("File web gagal dibaca: ${it.message}") }
                    return
                }
            }
        }
        if (requestCode == COLOR_PICKER_CAPTURE_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                colorPickerProjectionResultCode = resultCode
                colorPickerProjectionData = data
                startColorPickerService()
            } else {
                toast("Izin tangkapan layar dibatalkan")
            }
            return
        }
        if (requestCode == COLOR_PHOTO_PICK_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching {
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("Foto tidak dapat dibaca")
            }.onSuccess { loadColorPhoto(it) }
             .onFailure { toast("Foto gagal dibaca: ${it.message}") }
            return
        }
        if (requestCode == COLOR_PALETTE_EXPORT_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val palette = currentPhotoPalette.toList()
            val text = if (pendingColorPaletteExportFormat == "json") {
                val arr = JSONArray()
                palette.forEach { (color, percent) ->
                    val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
                    arr.put(JSONObject().apply {
                        put("hex", "#%02X%02X%02X".format(Locale.US, r, g, b))
                        put("rgb", JSONArray().put(r).put(g).put(b))
                        put("percent", percent)
                    })
                }
                JSONObject().apply { put("source", "MyTools Color Tools"); put("colors", arr) }.toString(2)
            } else {
                palette.joinToString("\n") { (color, percent) ->
                    "#%02X%02X%02X\t$percent%%".format(Locale.US, Color.red(color), Color.green(color), Color.blue(color))
                }
            }
            runCatching { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(StandardCharsets.UTF_8)) } }
                .onSuccess { toast("Palet berhasil diekspor") }
                .onFailure { toast("Ekspor palet gagal: ${it.message}") }
            return
        }
        if (requestCode == COLOR_PHOTO_CAMERA_REQUEST && resultCode == RESULT_OK) {
            val bitmap = data?.extras?.get("data") as? Bitmap
            if (bitmap != null) loadColorPhoto(bitmap) else toast("Foto kamera tidak tersedia")
            return
        }
        if (requestCode == 1030 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            uploadOtaUri(uri, pendingOtaEndpoint)
            return
        }
        if (requestCode == FINANCE_EXPORT_CREATE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { contentResolver.openOutputStream(uri)?.use { out ->
                val db = FinanceDb(this)
                val bytes = if (pendingFinanceExportJson) financeJson(db).toString(2).toByteArray(StandardCharsets.UTF_8) else financeCsv(db).toByteArray(StandardCharsets.UTF_8)
                out.write(bytes)
            } }.onSuccess { toast("Ekspor berhasil") }.onFailure { toast("Ekspor gagal: ${it.message}") }
            return
        }
        if (requestCode == FINANCE_BACKUP_CREATE && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { contentResolver.openOutputStream(uri)?.use { out -> out.write(financeJson(FinanceDb(this)).toString(2).toByteArray(StandardCharsets.UTF_8)) } }
                .onSuccess { toast("Backup berhasil disimpan") }.onFailure { toast("Backup gagal: ${it.message}") }
            return
        }
        if (requestCode == FINANCE_BACKUP_OPEN && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { contentResolver.openInputStream(uri)?.bufferedReader(StandardCharsets.UTF_8)?.use { JSONObject(it.readText()) } }
                .onSuccess { root ->
                    if (root == null) { toast("Backup kosong"); return@onSuccess }
                    AlertDialog.Builder(this).setTitle("Ganti data keuangan?")
                        .setMessage("Restore akan mengganti data keuangan lokal saat ini dengan isi backup. Buat backup saat ini terlebih dahulu jika masih diperlukan.")
                        .setNegativeButton("Batal", null)
                        .setPositiveButton("Restore") { _, _ -> restoreFinanceJson(FinanceDb(this), root) }.show()
                }.onFailure { toast("Restore gagal: ${it.message}") }
            return
        }
        if (requestCode == 3025 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { writeAppBackup(uri) }
                .onSuccess { toast("Backup V2.25 berhasil disimpan") }
                .onFailure { toast("Backup gagal: ${it.message}") }
            return
        }
        if (requestCode == 3026 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            AlertDialog.Builder(this).setTitle("Restore Backup V2.25?")
                .setMessage("Pengaturan, history, dan Recent Files dari backup akan diterapkan. File kerja tidak dihapus otomatis.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Restore") { _, _ ->
                    runCatching { readAppBackup(uri) }
                        .onSuccess { toast("Restore selesai. Buka ulang tool jika diperlukan.") }
                        .onFailure { toast("Restore gagal: ${it.message}") }
                }.show()
            return
        }
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val file = File(filesDir, "imports").apply { mkdirs() }
            val out = File(file, safeFileName(queryName(uri) ?: "import.txt"))
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { input.copyTo(it) }
            }
            editor(out)
        }
        if (requestCode == 1002 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            inspectZipOrApk(uri)
        }
        if (requestCode == 1301 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            apkCompareFirstUri = uri
            toast("APK A dipilih. Pilih APK B.")
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "application/vnd.android.package-archive"
                addCategory(Intent.CATEGORY_OPENABLE)
            }, 1302)
            return
        }
        if (requestCode == 1302 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val first = apkCompareFirstUri
            if (first == null) { toast("APK A belum dipilih"); return }
            compareApks(first, uri)
            return
        }
        if (requestCode == 9811 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.readText() }
                .onSuccess { editorPendingTarget?.setText(it) }
                .onFailure { toast("File gagal dibuka: ${it.message}") }
            editorPendingTarget = null
            return
        }
        if (requestCode == 1020 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            pendingOcrUri = uri
            pendingOcrPreview?.setImageURI(uri)
            pendingOcrView?.setText("")
            toast("Gambar dipilih. Tekan OCR Gambar Terpilih.")
        }
        if (requestCode == 1021 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            pendingApkUri = uri
            analyzeApk(uri)
        }
        if (requestCode == 1010 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            thread {
                val r = runCatching {
                    val size = contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
                    require(size <= 8L * 1024 * 1024 || size < 0) { "File terlalu besar. Batas 8 MB." }
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Tidak bisa membaca file")
                    "Base64:\n" + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                }.getOrElse { "Base64 file error: ${it.message}" }
                runOnUiThread { output(r) }
            }
        }
        if (requestCode == 1050 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            convPickedUri = uri
            convPickedName = queryName(uri) ?: "file"
            val ext = convPickedName?.substringAfterLast('.', "")?.toUpperCase(Locale.getDefault())
            convFromFormat = if (ext.isNullOrBlank()) "Otomatis terdeteksi" else ext
            convStage = "form"
            renderConv()
        }
        if (requestCode == 1041 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            qrPickedName = queryName(uri)
            decodeQrFromUri(uri)
        }
        if (requestCode == 1042 && resultCode == RESULT_OK) {
            // Gallery pick returns data.data; camera capture writes to qrCameraOutUri instead.
            val uri = data?.data ?: qrCameraOutUri ?: return
            qrPickedName = if (data?.data != null) queryName(uri) else "Foto kamera"
            decodeQrFromUri(uri)
        }
        if (requestCode == 1043 && resultCode == RESULT_OK) {
            val uri = qrCameraOutUri ?: return
            qrPickedName = "Hasil scan kamera"
            decodeQrFromUri(uri)
        }
        if (requestCode == 1003 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            thread {
                val r = runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val md5 = MessageDigest.getInstance("MD5")
                        val sha1 = MessageDigest.getInstance("SHA-1")
                        val sha256 = MessageDigest.getInstance("SHA-256")
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            md5.update(buf,0,n); sha1.update(buf,0,n); sha256.update(buf,0,n)
                        }
                        "MD5  ${md5.digest().joinToString("") { "%02x".format(it) }}\n" +
                        "SHA1 ${sha1.digest().joinToString("") { "%02x".format(it) }}\n" +
                        "SHA256 ${sha256.digest().joinToString("") { "%02x".format(it) }}"
                    } ?: "Tidak bisa membaca file"
                }.getOrElse { "Error: ${it.message}" }
                runOnUiThread { output(r) }
            }
        }
        if ((requestCode == 1201 || requestCode == 1202) && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            if (requestCode == 1201) {
                fileHashUriA = uri
                fileHashCompareLabelA?.text = "File A: ${queryName(uri) ?: uri.lastPathSegment ?: uri}"
            } else {
                fileHashUriB = uri
                fileHashCompareLabelB?.text = "File B: ${queryName(uri) ?: uri.lastPathSegment ?: uri}"
            }
            return
        }
        if (requestCode == 1210 && resultCode == RESULT_OK) {
            data?.data?.let { imageInfoResult?.invoke(it) }
            return
        }
        if (requestCode == 1211 && resultCode == RESULT_OK) {
            data?.data?.let { imageToolsResult?.invoke(it) }
            return
        }
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // IoT Dynamic tetap berada di halaman yang sama saat HP berputar ke landscape.
        // Activity tidak dibuat ulang, jadi canvas, posisi widget, dan koneksi tidak hilang.
        if (currentPage == "IoT Dynamic Topology") {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            content.post {
                studioCanvas?.requestLayout()
                studioCanvas?.invalidate()
            }
        }
    }

    internal fun setupDynamicShortcuts() {
        if (Build.VERSION.SDK_INT < 25) return
        val sm = getSystemService(ShortcutManager::class.java) ?: return
        val icon = android.graphics.drawable.Icon.createWithResource(this, R.mipmap.app_icon)
        val shortcuts = listOf(
            ShortcutInfo.Builder(this, "expense")
                .setShortLabel("+ Pengeluaran")
                .setLongLabel("Tambah pengeluaran")
                .setIcon(icon)
                .setIntent(Intent(this, MainActivity::class.java).putExtra("quick_expense", true))
                .build(),
            ShortcutInfo.Builder(this, "income")
                .setShortLabel("+ Pemasukan")
                .setLongLabel("Tambah pemasukan")
                .setIcon(icon)
                .setIntent(Intent(this, MainActivity::class.java).putExtra("quick_income", true))
                .build(),
            ShortcutInfo.Builder(this, "finance")
                .setShortLabel("Keuangan")
                .setLongLabel("Finance Dashboard")
                .setIcon(icon)
                .setIntent(Intent(this, MainActivity::class.java).putExtra("open_finance", true))
                .build(),
            ShortcutInfo.Builder(this, "iot")
                .setShortLabel("ESP Studio")
                .setLongLabel("ESP Studio & Visual Wiring")
                .setIcon(icon)
                .setIntent(Intent(this, MainActivity::class.java).putExtra("open_iot", true))
                .build()
        )
        sm.dynamicShortcuts = shortcuts
    }

    internal fun scheduleFinanceMaintenance() {
        val request = PeriodicWorkRequest.Builder(FinanceMaintenanceWorker::class.java, 24, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.NOT_REQUIRED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("mytools_finance_maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    internal fun applySystemTheme() {
        val forced = if (::prefs.isInitialized) prefs.getString("theme_mode", "system") ?: "system" else "system"
        val ui = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        isDarkTheme = when (forced) {
            "dark" -> true
            "light" -> false
            else -> ui != Configuration.UI_MODE_NIGHT_NO
        }
        if (!isDarkTheme) {
            dark = Color.rgb(248, 248, 250); panel = Color.rgb(255, 255, 255); panel2 = Color.rgb(242, 242, 246)
            textMain = Color.rgb(24, 24, 28); textMuted = Color.rgb(100, 100, 108); line = Color.rgb(215, 215, 222)
        } else {
            dark = Color.rgb(10, 10, 11); panel = Color.rgb(22, 22, 24); panel2 = Color.rgb(28, 28, 31)
            textMain = Color.rgb(245, 245, 247); textMuted = Color.rgb(155, 155, 160); line = Color.rgb(48, 48, 52)
        }
    }

    internal fun applyBottomNavShape() {
        // Keep the navigation container rounded. Calling setBackgroundColor() here
        // would replace the rounded drawable with a sharp rectangle.
        val backgroundColor = if (isDarkTheme) panel else Color.WHITE
        bottomNav.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(backgroundColor)
            cornerRadius = dp(30).toFloat()
            setStroke(dp(1), if (isDarkTheme) line else Color.rgb(225, 230, 235))
        }
        bottomNav.clipToOutline = true
        if (Build.VERSION.SDK_INT >= 21) bottomNav.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, dp(30).toFloat())
            }
        }
    }

    internal fun applyUiColors() {
        mainContainer.setBackgroundColor(dark)
        content.setBackgroundColor(dark)
        search.setBackgroundColor(panel)
        search.setTextColor(textMain); search.setHintTextColor(textMuted)
        applyBottomNavShape()
    }

    internal fun enterApp() {
        loginScreen.visibility = View.GONE
        mainContainer.visibility = View.VISIBLE
        applyLightAppTheme()
        showHome()
    }

    internal fun applyLightAppTheme() {
        isDarkTheme = false
        dark = Color.rgb(255, 255, 255)
        panel = Color.rgb(248, 250, 252)
        panel2 = Color.rgb(244, 246, 248)
        textMain = Color.rgb(15, 15, 16)
        textMuted = Color.rgb(123, 135, 148)
        line = Color.rgb(225, 230, 235)
        mainContainer.setBackgroundColor(Color.WHITE)
        content.setBackgroundColor(Color.WHITE)
        applyBottomNavShape()
        search.setBackgroundResource(com.example.aidetest.R.drawable.bg_search_light)
        search.setTextColor(textMain)
        search.setHintTextColor(textMuted)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        if (Build.VERSION.SDK_INT >= 23) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(colorPickerReceiver) }
        stopClipboardMonitor()
        networkScanStop.set(true)
        stopEspDiscovery()
        stopEspSensorPolling()
        stopLedPlayback()
        server?.close()
        server = null
        runCatching { hotspotReservation?.close() }
        hotspotReservation = null
        super.onDestroy()
    }

    internal data class ToolVisualTheme(
        val accent: Int,
        val surface: Int,
        val border: Int,
        val chip: String,
        val button: Int,
        val onButton: Int = Color.WHITE
    )

    internal fun visualTheme(name: String = currentPage): ToolVisualTheme {
        // MyTools uses one consistent monochrome UI. Tool categories may still
        // have different labels, but never introduce colored buttons/accent panels.
        return ToolVisualTheme(
            textMain,
            panel2,
            line,
            when {
                name.contains("esp", true) || name.contains("iot", true) -> "HARDWARE"
                name.contains("jaringan", true) || name.contains("network", true) || name.contains("dns", true) || name.contains("ping", true) || name.contains("port", true) || name.contains("http", true) || name.contains("ssl", true) -> "NETWORK"
                name.contains("security", true) || name.contains("password", true) || name.contains("token", true) || name.contains("aes", true) || name.contains("hmac", true) || name.contains("jwt", true) || name.contains("hash", true) -> "SECURITY"
                name.contains("keuangan", true) || name.contains("finance", true) || name.contains("dca", true) || name.contains("loan", true) || name.contains("margin", true) || name.contains("discount", true) || name.contains("bunga", true) -> "FINANCE"
                name.contains("file", true) || name.contains("zip", true) || name.contains("apk", true) || name.contains("storage", true) || name.contains("folder", true) -> "FILES"
                name.contains("color", true) || name.contains("pipet", true) || name.contains("sprite", true) || name.contains("qr", true) || name.contains("ocr", true) -> "VISUAL"
                name.contains("editor", true) || name.contains("json", true) || name.contains("xml", true) || name.contains("regex", true) || name.contains("base64", true) || name.contains("text", true) || name.contains("unicode", true) -> "DEVELOPER"
                name.contains("battery", true) || name.contains("device", true) || name.contains("system", true) || name.contains("wifi", true) -> "SYSTEM"
                else -> "UTILITY"
            },
            textMain,
            if (isDarkTheme) Color.rgb(15, 15, 16) else Color.WHITE
        )
    }

    internal fun toolAccentStrip(name: String): View {
        val t = visualTheme(name)
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = bg(t.surface, 14, t.border)
            addView(View(this@MainActivity).apply { background = bg(t.accent, 3) }, LinearLayout.LayoutParams(dp(5), dp(30)))
            addView(TextView(this@MainActivity).apply {
                text = t.chip
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(t.accent)
                setPadding(dp(10), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "● READY"
                textSize = 10f
                setTextColor(Color.rgb(105, 110, 116))
            })
        }
    }

    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // Lightweight native animations: no extra dependency, tuned for Android phones.
    internal fun animateEditorItem(view: View, delay: Long = 0L, distance: Float = 18f) {
        view.alpha = 0f
        view.translationY = dp(distance.toInt()).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(240L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
            .start()
    }

    internal fun animateEditorPress(view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(0.96f).scaleY(0.96f)
            .setDuration(70L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(120L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            }.start()
    }

    internal fun animateEditorScreen() {
        content.alpha = 0f
        content.translationY = dp(8).toFloat()
        content.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
            .start()
    }

    internal fun bg(color: Int, radius: Int = 16, stroke: Int? = null): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    // ---- Design system helpers (lihat DesignSystem.kt) ----
    internal fun rippleBg(fill: Int, radius: Int = Ds.RADIUS_MD, stroke: Int? = null): Drawable {
        val base = bg(fill, radius, stroke)
        val rippleColor = ColorStateList.valueOf(
            if (isDarkTheme) Color.argb(48, 255, 255, 255) else Color.argb(36, 0, 0, 0)
        )
        val mask = bg(Color.WHITE, radius)
        return RippleDrawable(rippleColor, base, mask)
    }

    internal fun statusColor(state: Ds.State): Int = Ds.statusColor(state, isDarkTheme)

    internal fun isSecondaryAction(text: String): Boolean {
        val t = text.trim().lowercase(Locale.ROOT)
        return listOf(
            "salin", "copy", "bagikan", "share", "bersihkan", "clear", "hapus", "reset",
            "batal", "cancel", "tutup", "close", "kembali", "back", "acak ulang"
        ).any { t == it || t.startsWith("$it ") }
    }

    /** Level 1: aksi utama (terisi). */
    internal fun styleAsPrimary(b: TextView) {
        val theme = visualTheme()
        b.setTextColor(theme.onButton)
        b.background = rippleBg(theme.button, Ds.RADIUS_MD, theme.button)
    }

    /** Level 2: aksi pendukung (outline). */
    internal fun styleAsSecondary(b: TextView) {
        b.setTextColor(textMain)
        b.background = rippleBg(panel, Ds.RADIUS_MD, line)
    }

    /** Level 3: aksi kecil (teks saja, tetap 48dp). */
    internal fun styleAsTertiary(b: TextView) {
        b.setTextColor(textMain)
        b.background = rippleBg(Color.TRANSPARENT, Ds.RADIUS_SM)
    }

    internal fun secondaryButton(text: String, onClick: () -> Unit): Button =
        button(text, onClick).also { styleAsSecondary(it) }

    internal fun tertiaryButton(text: String, onClick: () -> Unit): Button =
        button(text, onClick).also { styleAsTertiary(it) }

    /**
     * Komponen state reusable: Loading / Success / Error / Empty / Info / Warning.
     * onRetry (opsional) menampilkan tombol "Coba lagi".
     */
    internal fun stateCard(
        state: Ds.State,
        title: String,
        message: String = "",
        onRetry: (() -> Unit)? = null
    ): LinearLayout {
        val tint = statusColor(state)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(Ds.SPACE_LG), dp(Ds.SPACE_XL), dp(Ds.SPACE_LG), dp(Ds.SPACE_XL))
            background = bg(panel2, Ds.RADIUS_LG, line)
            contentDescription = if (message.isBlank()) title else "$title. $message"
        }
        if (state == Ds.State.LOADING) {
            val loaderRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            loaderRow.addView(ProgressBar(this).apply { isIndeterminate = true },
                LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin = dp(10) })
            val shimmer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            repeat(2) { i ->
                shimmer.addView(View(this@MainActivity).apply {
                    background = bg(if (i == 0) Color.rgb(224,227,231) else Color.rgb(235,237,240), Ds.RADIUS_SM)
                }, LinearLayout.LayoutParams(if (i == 0) dp(150) else dp(105), dp(10)).apply {
                    if (i == 0) bottomMargin = dp(7)
                })
            }
            loaderRow.addView(shimmer)
            card.addView(loaderRow, LinearLayout.LayoutParams(-2, dp(40)))
        } else {
            card.addView(MdiIconView(this).apply {
                setIconName(Ds.stateIcon(state)); setIconSize(28f); setTextColor(tint)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        card.addView(label(title, 15f, true).apply { gravity = Gravity.CENTER; setTextColor(tint) })
        if (message.isNotBlank()) card.addView(subLabel(message, 13f).apply { gravity = Gravity.CENTER })
        if (onRetry != null && state == Ds.State.ERROR) {
            val retry = secondaryButton("Coba lagi", onRetry)
            card.addView(retry, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(Ds.SPACE_MD) })
        }
        return card
    }

    internal fun addEmptyState(title: String = "Belum ada hasil", message: String = "Jalankan tool untuk melihat hasilnya") {
        content.addView(stateCard(Ds.State.EMPTY, title, message),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Ds.SPACE_MD); bottomMargin = dp(Ds.SPACE_MD) })
    }

    internal fun saveCurrentPageSnapshot() {
        if (restoringSnapshot || content.childCount == 0) return

        val rootPage = currentPage == "home" || currentPage == "all" ||
                currentPage == "favorites" || currentPage == "settings"

        // Root pages can contain dozens/hundreds of Views. Moving the entire View tree
        // into the Back stack caused visible jank and retained a lot of memory. Keep only
        // the page identity for roots and rebuild them on demand when Back is pressed.
        if (rootPage) {
            pageBackStack.addLast(
                PageSnapshot(
                    name = currentPage,
                    children = mutableListOf(),
                    scrollY = scroll.scrollY,
                    searchText = search.text?.toString() ?: "",
                    searchVisible = search.visibility,
                    lightweight = true
                )
            )
            while (pageBackStack.size > 8) pageBackStack.removeFirst()
            return
        }

        val children = ArrayList<View>(content.childCount)
        while (content.childCount > 0) {
            children.add(content.getChildAt(0))
            content.removeViewAt(0)
        }
        pageBackStack.addLast(
            PageSnapshot(
                name = currentPage,
                children = children,
                scrollY = scroll.scrollY,
                searchText = search.text?.toString() ?: "",
                searchVisible = search.visibility
            )
        )
        while (pageBackStack.size > 8) pageBackStack.removeFirst()
    }

    internal fun restoreSnapshot(snapshot: PageSnapshot) {
        restoringSnapshot = true
        try {
            // Re-render root pages instead of restoring a huge retained View tree.
            // This keeps Back navigation smooth and prevents the app from accumulating
            // hundreds of detached card Views in memory.
            if (snapshot.lightweight) {
                when (snapshot.name) {
                    "home" -> showHome(homeFilter)
                    "all" -> showAllTools()
                    "favorites" -> showFavorites()
                    "settings" -> showSettings()
                    else -> showHome()
                }
                return
            }

            content.removeAllViews()
            snapshot.children.forEach { content.addView(it) }
            currentPage = snapshot.name
            // ESP Studio memakai landscape. Saat tombol kembali ditekan, kembalikan
            // orientasi ke portrait agar layar benar-benar kembali ke posisi semula.
            if (snapshot.name != "IoT Dynamic Topology") {
                requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            val root = snapshot.name == "home" || snapshot.name == "all" || snapshot.name == "favorites" || snapshot.name == "settings"
            title.text = when (snapshot.name) {
                "home" -> "GITLS"
                "all" -> "Semua Tools"
                "favorites" -> "Favorit"
                "settings" -> "Pengaturan"
                else -> snapshot.name
            }
            subtitle.visibility = if (snapshot.name == "home") View.VISIBLE else View.GONE
            homeMenu.visibility = if (snapshot.name == "home") View.VISIBLE else View.GONE
            homeProfile.visibility = if (snapshot.name == "home") View.VISIBLE else View.GONE
            action.visibility = if (snapshot.name == "home") View.GONE else View.VISIBLE
            back.visibility = if (snapshot.name == "home") View.GONE else View.VISIBLE
            search.visibility = snapshot.searchVisible
            suppressSearch = true
            search.setText(snapshot.searchText)
            suppressSearch = false
            bottomNav.visibility = if (root && !imeVisible) View.VISIBLE else View.GONE
            bottomNav.translationY = 0f
            if (root) selectBottomNav(snapshot.name)
            configureActionForPage(snapshot.name)
            scroll.post { scroll.scrollTo(0, snapshot.scrollY) }
        } finally {
            restoringSnapshot = false
        }
    }

    internal fun navigateBack() {
        if (currentPage == "Editor" && !editorLanding) {
            editorExternalTarget = null
            editorExternalMode = null
        }
        if (pageBackStack.isEmpty()) {
            if (currentPage != "home") {
                // Safety fallback for a page created before the stack was populated.
                showHome()
            } else {
                super.onBackPressed()
            }
            return
        }
        val snapshot = pageBackStack.removeLast()
        restoreSnapshot(snapshot)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (currentPage == "Konversi File" && convCategory != null) { convGoBackStage(); return }
        navigateBack()
    }

    internal fun configureActionForPage(name: String) {
        editorMore.visibility = View.GONE
        action.text = "⋮"
        action.textSize = 25f
        action.contentDescription = "Menu tool"
        action.setOnClickListener {
            when {
                name == "IoT Dynamic Topology" -> showToolOverflowMenu(name, listOf("Tambah widget" to { showStudioWidgetPicker() }))
                name == "Pengelola Keuangan" -> showToolOverflowMenu(name, listOf("Aksi keuangan" to { showFinanceActions() }))
                name in rmAllPages -> showToolOverflowMenu(name, listOf(
                    "Tambah notifikasi" to { showReminderEditor(null) },
                    "Uji notifikasi" to { sendTestNotification() },
                    "Pengaturan notifikasi" to { openNotificationSettings() }
                ))
                name == "Editor" || name.startsWith("Editor - ") -> showToolOverflowMenu(name, listOf("Pilih mode editor" to { showEditorModePicker() }))
                else -> showToolOverflowMenu(name)
            }
        }
    }

    /** Compact overflow menu shared by every standard tool page. */
    internal fun showToolOverflowMenu(name: String, extras: List<Pair<String, () -> Unit>> = emptyList()) {
        val popup = PopupMenu(this, action)
        popup.menu.add(0, 1, 0, "Riwayat")
        popup.menu.add(0, 2, 1, "Info")
        extras.forEachIndexed { index, extra ->
            popup.menu.add(0, 100 + index, 10 + index, extra.first)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { historyTool(); true }
                2 -> {
                    AlertDialog.Builder(this)
                        .setTitle(name)
                        .setMessage(toolDescription(name))
                        .setPositiveButton("OK", null)
                        .show()
                    true
                }
                else -> {
                    val index = item.itemId - 100
                    if (index in extras.indices) { extras[index].second(); true } else false
                }
            }
        }
        popup.show()
    }

    /**
     * Restores the search bar to its normal state whenever a new root page is opened.
     * This prevents a previously hidden bar from remaining hidden after navigation.
     */
    internal fun resetSearchSnap(show: Boolean) {
        searchSnapAccumulator = 0
        searchSnapLastY = scroll.scrollY
        searchSnapAnimating = false
        search.animate().cancel()
        search.translationY = 0f
        search.alpha = 1f
        search.visibility = if (show) View.VISIBLE else View.GONE
        searchSnapHidden = !show
    }

    internal fun hideSearchSnap() {
        if (searchSnapHidden || searchSnapAnimating || search.visibility != View.VISIBLE) return
        searchSnapAnimating = true
        search.animate().cancel()
        search.animate()
            .translationY(-dp(72).toFloat())
            .alpha(0f)
            .setDuration(150L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                search.visibility = View.GONE
                search.translationY = 0f
                search.alpha = 1f
                searchSnapHidden = true
                searchSnapAnimating = false
            }
            .start()
    }

    internal fun showSearchSnap() {
        if (!searchSnapHidden || searchSnapAnimating || (currentPage != "home" && currentPage != "all")) return
        searchSnapAnimating = true
        search.animate().cancel()
        search.visibility = View.VISIBLE
        // Re-enter from above and slide down into its normal position.
        search.translationY = -dp(72).toFloat()
        search.alpha = 0f
        search.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                searchSnapHidden = false
                searchSnapAnimating = false
            }
            .start()
    }

    internal fun clearPage(name: String, showSearch: Boolean = false) {
        // Calculator sub-modes reuse the same page. Existing calculator methods can
        // still call clearPage(), but during embedded rendering it must not create a
        // new navigation entry or wipe the unified workspace.
        if (embeddedCalculatorRender) return
        if (currentPage == "IoT Dynamic Topology" && name != "IoT Dynamic Topology") {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        stopClipboardMonitor()
        networkScanStop.set(true)
        stopEspDiscovery()
        stopEspSensorPolling()

        // Root tabs are navigation roots, not detail history. Bottom navigation sets
        // resettingRootNavigation so switching tabs does not retain the old View tree.
        if (resettingRootNavigation) {
            pageBackStack.clear()
        } else if (!restoringSnapshot) {
            saveCurrentPageSnapshot()
        }

        currentPage = name
        topBarVisibility(true)
        content.removeAllViews()
        back.setOnClickListener { navigateBack() }
        val isRoot = name == "home" || name == "all" || name == "favorites" || name == "settings"
        val isHome = name == "home"
        title.text = when (name) {
            "home" -> "GITLS"
            "all" -> "Semua Tools"
            "favorites" -> "Favorit"
            "settings" -> "Pengaturan"
            else -> name
        }
        subtitle.visibility = if (isHome) View.VISIBLE else View.GONE
        subtitle.text = if (isHome) "Semua alat dalam satu aplikasi" else ""
        homeMenu.visibility = if (isHome) View.VISIBLE else View.GONE
        homeProfile.visibility = if (isHome) View.VISIBLE else View.GONE
        action.visibility = if (isHome) View.GONE else View.VISIBLE
        back.visibility = if (isHome) View.GONE else View.VISIBLE
        configureActionForPage(name)
        // Tool pages use a clean single header. Category/READY and the old
        // status/history/info card were intentionally removed to reduce visual
        // noise. History + Info now live in the top-right three-dot menu.
        resetSearchSnap(showSearch)
        // Bottom navigation is only for the four root sections. Every tool page,
        // including the Editor landing page, gets the full screen so the bottom
        // bar never covers or distracts from tool controls. Use the top-left Back
        // button to return to the previous/root page.
        bottomNav.visibility = if (isRoot && !imeVisible) View.VISIBLE else View.GONE
        bottomNav.translationY = 0f
        editorBottomBar.visibility = if (name == "Editor" && !editorLanding) View.VISIBLE else View.GONE
        editorMore.visibility = if (name == "Editor" && !editorLanding) View.VISIBLE else View.GONE
        if (isRoot) selectBottomNav(name)
        // IoT Dynamic membutuhkan canvas benar-benar memenuhi viewport.
        val contentParams = content.layoutParams
        contentParams.height = if (name == "IoT Dynamic Topology" || name == "Editor") ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        content.layoutParams = contentParams
        // Tool pages use the entire available content area. The bottom navigation is
        // already hidden above, so inputs/actions can use the full width without a
        // small "second row" feeling at the bottom of the screen.
        if (name == "Kalkulator Dasar" || name == "Kalkulator Ilmiah") {
            content.setPadding(0, dp(4), 0, dp(10))
        } else if (name == "Editor") {
            content.setPadding(dp(6), dp(4), dp(6), dp(8))
        } else if (name in rmAllPages) {
            content.setPadding(dp(16), dp(6), dp(16), dp(20))
        } else {
            content.setPadding(dp(10), dp(4), dp(10), dp(14))
        }
    }

    internal fun label(text: String, size: Float = 16f, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(textMain)
        setPadding(dp(2), dp(6), dp(2), dp(6))
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    internal fun subLabel(text: String, size: Float = 12f): TextView = TextView(this).apply {
        this.text = text
        textSize = size.coerceAtLeast(Ds.TEXT_CAPTION_MIN)
        setTextColor(textMuted)
        setPadding(dp(2), 0, dp(2), 0)
    }

    internal fun animateToolItem(view: View, index: Int = 0) {
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = dp(12).toFloat()
        val delay = (index.coerceAtMost(7) * 34L)
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(230L)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
            .start()
    }

    internal fun openToolWithPress(id: String, view: View) {
        view.animate().cancel()
        view.animate()
            .scaleX(0.975f).scaleY(0.975f)
            .setDuration(65L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(95L)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                view.postDelayed({ openTool(id) }, 35L)
            }.start()
    }

    internal fun animateToolChildren(container: ViewGroup, fromIndex: Int = 0) {
        for (i in fromIndex until container.childCount) {
            val child = container.getChildAt(i)
            if (child.visibility == View.VISIBLE) animateToolItem(child, i - fromIndex)
        }
    }

    internal fun animateToolChildrenOnce(container: ViewGroup, fromIndex: Int = 0) {
        if (initialToolAnimationPlayed) return
        initialToolAnimationPlayed = true
        animateToolChildren(container, fromIndex)
    }

    /**
     * Shared UI surface used by tool cards. Keeps the whole app visually
     * consistent while giving touchable surfaces real depth and feedback.
     */
    internal fun applyInteractiveSurface(view: View, radius: Int = 16, elevation: Int = 2) {
        val base = bg(panel2, radius, line)
        val rippleColor = if (isDarkTheme) Color.argb(42, 255, 255, 255) else Color.argb(30, 0, 0, 0)
        view.background = RippleDrawable(ColorStateList.valueOf(rippleColor), base, bg(Color.WHITE, radius))
        view.elevation = dp(elevation).toFloat()
        view.isClickable = true
        view.isFocusable = true
        view.stateListAnimator = null
    }

    internal fun addPressFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.985f).scaleY(0.985f).setDuration(70).start()
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }
            }
            false
        }
    }

    internal fun toolCard(id: String, name: String, icon: String = "▣", compact: Boolean = false): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(if (compact) 60 else 68)
            setPadding(dp(if (compact) 12 else 14), dp(10), dp(if (compact) 12 else 14), dp(10))
            contentDescription = "$name. Buka alat"
        }
        applyInteractiveSurface(card, if (compact) 14 else 17, if (compact) 1 else 2)
        addPressFeedback(card)
        card.setOnClickListener { openToolWithPress(id, card) }

        val ico = MdiIconView(this).apply {
            setIconName(icon)
            setIconSize(if (compact) 18f else 20f)
            setTextColor(Color.WHITE)
            background = bg(Color.rgb(22,22,24), if (compact) 11 else 13)
        }
        card.addView(ico, LinearLayout.LayoutParams(dp(if (compact) 38 else 44), dp(if (compact) 38 else 44)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,0,0) }
        texts.addView(label(name, if (compact) 14f else 15f, true))
        texts.addView(subLabel("Buka alat", 11f))
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val arrow = TextView(this).apply {
            text = "›"; textSize = 24f; setTextColor(textMuted); gravity = Gravity.CENTER
            contentDescription = "Buka $name"
        }
        card.addView(arrow, LinearLayout.LayoutParams(dp(30), -1))
        return card
    }

    internal fun favoriteCard(id: String, name: String, icon: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(10), dp(4), dp(8))
            minimumHeight = dp(88)
            contentDescription = "$name. Favorit"
        }
        applyInteractiveSurface(card, 15, 1)
        addPressFeedback(card)
        card.setOnClickListener { openToolWithPress(id, card) }
        val ico = MdiIconView(this).apply {
            setIconName(icon)
            setIconSize(22f)
            setTextColor(textMain)
        }
        card.addView(ico, LinearLayout.LayoutParams(-1, dp(34)))
        card.addView(TextView(this).apply { text = name; textSize = 11f; gravity = Gravity.CENTER; setTextColor(textMain) }, LinearLayout.LayoutParams(-1, dp(22)))
        card.addView(TextView(this).apply { text = "Favorit"; textSize = 9f; gravity = Gravity.CENTER; setTextColor(textMuted) })
        return card
    }

    internal fun sectionTitle(titleText: String, actionText: String? = null, actionClick: (() -> Unit)? = null) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(18), 0, dp(8)) }
        row.addView(label(titleText.toUpperCase(Locale.getDefault()), 12f, true), LinearLayout.LayoutParams(0, -2, 1f))
        if (actionText != null) row.addView(TextView(this).apply {
            text = actionText.toUpperCase(Locale.getDefault()); textSize = 10f; setTextColor(textMuted); setOnClickListener { actionClick?.invoke() }
        })
        content.addView(row)
    }

    internal fun navigateRoot(action: () -> Unit) {
        resettingRootNavigation = true
        try { action() } finally { resettingRootNavigation = false }
    }

    internal fun showHome(filter: String = homeFilter) {
        homeFilter = filter
        clearPage("home", true)
        content.setPadding(dp(12), dp(8), dp(12), dp(18))

        val filterRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val filters = listOf("Semua", "Favorit", "Terbaru", "Populer")
        filters.forEachIndexed { index, value ->
            filterRow.addView(
                homeChip(value, value == homeFilter) { showHome(value) },
                LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (index > 0) leftMargin = dp(2)
                    if (index < filters.lastIndex) rightMargin = dp(2)
                }
            )
        }
        content.addView(filterRow, LinearLayout.LayoutParams(-1, dp(46)))

        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label("Tools", 19f, true), LinearLayout.LayoutParams(0, -2, 1f))
        titleRow.addView(TextView(this).apply {
            text = "${filteredHomeItems(homeFilter).size} tools  ›"
            textSize = 12f
            setTextColor(textMuted)
            setOnClickListener { showAllTools() }
            setPadding(dp(6), dp(8), 0, dp(8))
        })
        content.addView(titleRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        // Terbaru = tool yang baru ditambahkan/diperbarui, bukan sekadar riwayat pemakaian.
        if (homeFilter == "Semua") {
            val latest = latestUpdatedToolIds()
            if (latest.isNotEmpty()) {
                sectionTitle("Terbaru", "Lihat semua") { showHome("Terbaru") }
                content.addView(subLabel("Tools yang baru atau baru saja diperbarui.", 11f).apply {
                    setPadding(dp(2), 0, dp(2), dp(5))
                })
                val latestGrid = GridLayout(this).apply {
                    columnCount = 2
                    alignmentMode = GridLayout.ALIGN_BOUNDS
                    useDefaultMargins = false
                }
                latest.take(4).forEach { id ->
                    val item = homeToolMap[id]?.let { id to it } ?: return@forEach
                    val card = latestToolCard(item.first, item.second)
                    latestGrid.addView(card, GridLayout.LayoutParams().apply {
                        width = 0
                        height = dp(104)
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                        setMargins(dp(4), dp(4), dp(4), dp(4))
                    })
                }
                content.addView(latestGrid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) })
            }
        }

        if (prefs.getBoolean("show_quick_access", false)) {
            sectionTitle("Akses Cepat")
            val quick = defaultPreferredTools().take(4).mapNotNull { id -> homeToolMap[id]?.let { id to it } }
            quick.forEach { (id,n) -> content.addView(toolCard(id,n,iconFor(id)).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin=dp(6) } }) }
        }
        if (prefs.getBoolean("show_recent_activity", false)) {
            sectionTitle("Aktivitas Terakhir")
            val recent = toolHistory.ids().take(4)
            recent.mapNotNull { id -> homeToolMap[id]?.let { id to it } }.forEach { (id,n) -> content.addView(toolCard(id,n,iconFor(id)).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin=dp(6) } }) }
        }

        // Studio & Tools selalu terlihat di Beranda. Pengaturan tetap memiliki daftar
        // lengkapnya; bagian ini hanya menjadi shortcut agar Studio tidak tersembunyi.
        sectionTitle("Studio & Tools", "Lihat semua") { showAllTools() }
        content.addView(subLabel("Akses cepat ke Studio utama tanpa harus masuk Pengaturan.", 11f).apply {
            setPadding(dp(2), 0, dp(2), dp(5))
        })
        val studioIds = listOf(
            "webproject", "networkstudio", "developerstudio", "filestudio",
            "imagestudio", "financestudio", "systemstudio", "utilitystudio"
        )
        val studioGrid = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        studioIds.mapNotNull { id -> homeToolMap[id]?.let { id to it } }.forEach { (id, name) ->
            val card = mainPyToolCard(id, name)
            studioGrid.addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(96)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        content.addView(studioGrid, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val grid = GridLayout(this).apply {
            columnCount = prefs.getInt("home_columns", 2).coerceIn(1, 3)
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        val visibleHomeItems = filteredHomeItems(homeFilter).filterNot { (id, _) ->
            homeFilter == "Semua" && id in studioIds
        }
        if (prefs.getBoolean("show_home_tools", true)) visibleHomeItems.forEach { (id, name) ->
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(116)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            }
            grid.addView(mainPyToolCard(id, name), lp)
        }
        if (visibleHomeItems.isEmpty()) {
            grid.addView(TextView(this).apply {
                text = when (homeFilter) {
                    "Favorit" -> "Belum ada tool favorit."
                    "Terbaru" -> "Belum ada riwayat tool."
                    else -> "Tidak ada tool pada filter ini."
                }
                textSize = 13f
                setTextColor(textMuted)
                gravity = Gravity.CENTER
                setPadding(dp(20), dp(40), dp(20), dp(40))
            }, GridLayout.LayoutParams().apply {
                columnSpec = GridLayout.spec(0, 2)
                width = -1
            })
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2))
        // No staggered card animation here: large tool grids should render immediately.
    }

    internal fun filteredHomeItems(filter: String): List<Pair<String, String>> {
        val base = homeToolMap
        return when (filter) {
            "Favorit" -> favoriteToolIds().mapNotNull { id -> base[id]?.let { id to it } }
            "Terbaru" -> {
                val updated = latestUpdatedToolIds()
                val recent = toolHistory.ids()
                (updated + recent).distinct().mapNotNull { id -> base[id]?.let { id to it } }
                    .ifEmpty { defaultPreferredTools().take(12).mapNotNull { id -> base[id]?.let { id to it } } }
            }
            "Populer" -> defaultPreferredTools().take(20).mapNotNull { id -> base[id]?.let { id to it } }
            else -> defaultPreferredTools().mapNotNull { id -> base[id]?.let { id to it } }
        }
    }

    internal fun defaultPreferredTools(): List<String> = listOf(
        "filemanager", "editor", "reminder", "zip", "githubzip", "http", "wifi", "json", "hash", "base64", "url", "regex", "uuid", "color",
        "number", "textstat", "case", "dedupe", "compare", "slug", "lorem", "password", "token", "jwt", "hmac", "totp", "aes",
        "random", "checksum", "hex", "base32", "dns", "rdns", "port", "publicip", "ping", "ipinfo", "ssl", "apk", "qr", "system",
        "timestamp", "unicode", "urlparser", "mime", "jsonformat", "xmlformat", "uuidbatch", "base64file", "httpheaders", "textreplace",
        "wordfreq", "deviceinfo", "storage", "apps", "network", "battery", "filesearch", "pivotcalc", "dividercalc", "dcacalc",
        "pwmcalc", "spritecalc", "installcalc", "powercalc", "aspectcalc", "pphcalc", "financereader", "financedashboard",
        "securitycenter", "clipboard", "ocr", "unitconverter", "apkanalyzer", "netscanner",
        "webproject", "networkstudio", "developerstudio", "filestudio", "imagestudio",
        "systemstudio", "financestudio", "utilitystudio", "espstudio", "whois", "traceroute", "subnetcalc"
    )

    /** Favorites are now stored by the modular ToolPreferences service. */
    internal fun favoriteToolIds(): List<String> = toolPreferences.favorites().toList()

    internal fun isFavorite(id: String): Boolean = toolPreferences.isFavorite(id)

    internal fun toggleFavorite(id: String) {
        val nowFavorite = !toolPreferences.isFavorite(id)
        toolPreferences.setFavorite(id, nowFavorite)
        toast(if (nowFavorite) "Ditambahkan ke favorit" else "Dihapus dari favorit")
    }

    /** One-time migration from the old comma-separated preference format. */
    internal fun migrateToolStateToModularStore() {
        val oldFavorites = prefs.getString("favorite_tools", "")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        oldFavorites.forEach { id -> toolPreferences.setFavorite(id, true) }
        val oldRecent = prefs.getString("recent_tools", "")
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        if (toolHistory.ids().isEmpty()) oldRecent.reversed().forEach { id -> toolHistory.record(id) }
    }

    internal fun toolCategory(id: String): String {
        val n = (homeToolMap[id] ?: id).toLowerCase(Locale.getDefault())
        return when {
            n.contains("esp") || n.contains("iot") || n.contains("gpio") || n.contains("sensor") -> "ESP / IoT"
            n.contains("network") || n.contains("wifi") || n.contains("dns") || n.contains("ping") || n.contains("http") || n.contains("ssl") || n.contains("port") || n.contains("ip ") -> "Network"
            n.contains("password") || n.contains("hash") || n.contains("token") || n.contains("aes") || n.contains("hmac") || n.contains("jwt") || n.contains("totp") || n.contains("encryption") || n.contains("steganography") || n.contains("breach") || n.contains("secure notes") || n.contains("pgp") || n.contains("ssh key") || n.contains("certificate") || n.contains("virus scanner") || n.contains("url safety") -> "Security"
            n.contains("finance") || n.contains("keuangan") || n.contains("dca") || n.contains("bunga") || n.contains("pajak") || n.contains("margin") || n.contains("diskon") -> "Finance"
            n.contains("file") || n.contains("zip") || n.contains("apk") || n.contains("storage") || n.contains("folder") -> "File"
            n.contains("text") || n.contains("json") || n.contains("xml") || n.contains("regex") || n.contains("base64") || n.contains("unicode") || n.contains("slug") -> "Developer"
            n.contains("calc") || n.contains("kalkulator") || n.contains("converter") || n.contains("konversi") || n.contains("aspect") -> "Calculator"
            else -> "Utility"
        }
    }

    // Registry update tool: setiap kali versi tool berubah, tool otomatis masuk ke bagian "Terbaru".
    // Untuk rilis berikutnya cukup naikkan versi pada entry terkait.
    internal val toolUpdateCatalog = linkedMapOf(
        "githubzip" to "2.30.0",
        "webhostwifi" to "2.19.8",
        "webproject" to "2.19.8",
        "webeditor" to "2.19.8",
        "reminder" to "2.19.8",
        "espstudio" to "2.19.8",
        "networkstudio" to "2.19.8",
        "clipboard" to "2.19.8",
        "apkanalyzer" to "2.19.8",
        "fileencryption" to "2.20.0", "steganography" to "2.20.0", "passwordanalyzer" to "2.20.0", "breachchecker" to "2.20.0", "securenotes" to "2.20.0", "totpvault" to "2.20.0", "pgp" to "2.20.0", "sshkeygen" to "2.20.0", "certviewer" to "2.20.0", "virusscanner" to "2.20.0", "urlsafety" to "2.20.0"
    )

    internal fun syncToolUpdates() {
        val stored = prefs.getString("tool_update_versions", "")
            ?.split("|")?.mapNotNull { part ->
                val pieces = part.split("=", limit = 2)
                if (pieces.size == 2 && pieces[0].isNotBlank()) pieces[0] to pieces[1] else null
            }?.toMap()?.toMutableMap() ?: mutableMapOf()
        val latest = prefs.getString("latest_tool_updates", "")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()

        var changed = false
        toolUpdateCatalog.forEach { (id, version) ->
            if (stored[id] != version) {
                latest.remove(id)
                latest.add(0, id)
                stored[id] = version
                changed = true
            }
        }
        if (changed) {
            prefs.edit()
                .putString("tool_update_versions", stored.entries.joinToString("|") { "${it.key}=${it.value}" })
                .putString("latest_tool_updates", latest.distinct().take(20).joinToString(","))
                .apply()
        }
    }

    internal fun latestUpdatedToolIds(): List<String> {
        val ids = prefs.getString("latest_tool_updates", "")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return ids.filter { id -> homeToolMap.containsKey(id) }.take(6)
    }

    internal fun latestToolVersion(id: String): String = toolUpdateCatalog[id] ?: "2.19.8"

    internal fun recordRecentTool(id: String) {
        toolHistory.record(id)
    }

    internal fun latestToolCard(id: String, name: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), dp(9), dp(8), dp(7))
            contentDescription = "$name. Tool terbaru"
        }
        applyInteractiveSurface(card, 18, 2)
        addPressFeedback(card)
        card.setOnClickListener { openToolWithPress(id, card) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(MdiIconView(this@MainActivity).apply {
            setIconName(iconFor(id)); setIconSize(21f); setTextColor(Color.WHITE)
            setPadding(dp(5), dp(5), dp(5), dp(5)); background = bg(Color.rgb(25,25,27), 11)
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        top.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(TextView(this).apply {
            text = "UPDATE"; textSize = 7.5f; setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.rgb(90,90,95)); gravity = Gravity.CENTER; setPadding(dp(5), dp(3), dp(5), dp(3))
            background = bg(Color.rgb(242,242,242), 8)
        }, LinearLayout.LayoutParams(-2, dp(24)))
        card.addView(top)
        card.addView(TextView(this).apply {
            text = name; textSize = 12.5f; setTextColor(Color.rgb(17,17,17))
            setTypeface(typeface, android.graphics.Typeface.BOLD); maxLines = 2
            setPadding(0, dp(7), 0, 0)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        card.addView(TextView(this).apply {
            text = "Versi ${latestToolVersion(id)}"; textSize = 8.5f; setTextColor(Color.rgb(120,120,125))
        }, LinearLayout.LayoutParams(-1, dp(15)))
        return card
    }

    internal fun mainPyToolCard(id: String, name: String): LinearLayout {
        val theme = visualTheme(name)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(10), dp(9), dp(9))
            contentDescription = "$name. Tool"
        }
        applyInteractiveSurface(card, 18, 2)
        addPressFeedback(card)
        card.setOnClickListener { openToolWithPress(id, card) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val accent = View(this).apply { background = bg(if (isDarkTheme) Color.rgb(90,90,96) else Color.rgb(25,25,27), 3) }
        top.addView(accent, LinearLayout.LayoutParams(dp(4), dp(38)).apply { rightMargin = dp(8) })
        val icon = MdiIconView(this).apply {
            setIconName(iconFor(id))
            setIconSize(22f)
            setTextColor(Color.WHITE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = bg(if (isDarkTheme) Color.rgb(42,42,46) else Color.rgb(25,25,27), 12)
        }
        top.addView(icon, LinearLayout.LayoutParams(dp(38), dp(38)))
        top.addView(Space(this), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(TextView(this).apply {
            text = if (isFavorite(id)) "★" else "☆"; textSize = 19f; gravity = Gravity.CENTER
            setTextColor(textMain); setPadding(dp(2),0,dp(2),0)
            contentDescription = if (isFavorite(id)) "Hapus $name dari favorit" else "Tambahkan $name ke favorit"
            setOnClickListener { toggleFavorite(id); text = if (isFavorite(id)) "★" else "☆"; contentDescription = if (isFavorite(id)) "Hapus $name dari favorit" else "Tambahkan $name ke favorit" }
        }, LinearLayout.LayoutParams(dp(30), dp(38)))
        top.addView(TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(dp(24), dp(38)))
        card.addView(top)
        card.addView(TextView(this).apply {
            text = name
            textSize = 13.5f
            setTextColor(textMain)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(40)))
        card.addView(TextView(this).apply {
            text = theme.chip
            textSize = 8.5f
            setTextColor(textMuted)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
        }, LinearLayout.LayoutParams(-1, dp(15)))
        return card
    }

    internal fun homeHeroCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(18), dp(18), dp(18))
            background = bg(Color.rgb(242, 245, 247), 22)
            setOnClickListener { showAllTools() }
        }
        val textBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textBox.addView(label("Tempat widget", 24f, true))
        textBox.addView(subLabel("Deskripsi singkat tentang\naplikasi atau fitur utama.", 14f).apply { setPadding(dp(2), 0, 0, 0) })
        val spacer = Space(this)
        val imageBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val image = MdiIconView(this).apply {
            setIconName("view-grid")
            setIconSize(34f)
            setTextColor(Color.rgb(80, 90, 100))
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = bg(Color.rgb(232, 236, 240), 18)
            alpha = 0.7f
        }
        imageBox.addView(image, LinearLayout.LayoutParams(dp(70), dp(70)))
        val dots = TextView(this).apply {
            text = "●  •  •"
            textSize = 11f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        imageBox.addView(dots, LinearLayout.LayoutParams(dp(76), dp(24)))
        card.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(imageBox, LinearLayout.LayoutParams(dp(86), -1))
        return card
    }

    internal fun homeChip(textValue: String, active: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
        text = textValue
        textSize = 11f
        gravity = Gravity.CENTER
        minHeight = dp(38)
        setTextColor(if (active) Color.WHITE else textMain)
        val fill = if (active) (if (isDarkTheme) Color.WHITE else Color.rgb(15,15,16)) else (if (isDarkTheme) Color.rgb(42,42,46) else Color.rgb(241,244,246))
        background = bg(fill, 22)
        setTextColor(if (active) (if (isDarkTheme) Color.BLACK else Color.WHITE) else textMain)
        isClickable = true
        isFocusable = true
        contentDescription = "Filter $textValue${if (active) ", aktif" else ""}"
        setOnClickListener { onClick() }
    }

    internal fun homeToolCard(id: String, name: String, desc: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(10), dp(12), dp(10))
            background = bg(Color.WHITE, 18, Color.rgb(238, 241, 244))
            isClickable = true
            setOnClickListener { if (id == "settings") showSettings() else openTool(id) }
        }
        val iconRes = when (id) {
            "filemanager" -> R.drawable.ic_folder
            "number" -> R.drawable.ic_calculator
            "editor" -> R.drawable.ic_code
            "qr" -> R.drawable.ic_qr
            "reminder" -> R.drawable.ic_bell
            "zip" -> R.drawable.ic_archive
            else -> R.drawable.ic_settings
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = bg(Color.rgb(242, 245, 247), 14)
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(52), dp(52)))
        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(8), 0)
        }
        textBox.addView(label(name, 15f, true).apply { setPadding(0, 0, 0, dp(2)) })
        textBox.addView(subLabel(desc, 11f).apply { setPadding(0, 0, 0, 0) })
        card.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(ImageView(this).apply { setImageResource(R.drawable.ic_arrow_right) }, LinearLayout.LayoutParams(dp(28), dp(28)))
        return card
    }

    internal fun showFavorites() {
        clearPage("favorites", false)
        content.setPadding(dp(12), dp(8), dp(12), dp(18))
        val favorites = favoriteToolIds().mapNotNull { id -> homeToolMap[id]?.let { id to it } }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
        header.addView(label("Favorit", 22f, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "${favorites.size}/30"
            textSize = 11f
            setTextColor(textMuted)
            background = bg(if (isDarkTheme) Color.rgb(42,42,46) else Color.rgb(241,244,246), 14)
            setPadding(dp(9), dp(5), dp(9), dp(5))
        })
        content.addView(header)
        content.addView(subLabel(if (favorites.isEmpty()) "Tool yang kamu tandai akan muncul di sini." else "Akses cepat ke tool yang paling sering kamu gunakan.", 12f).apply { setPadding(dp(2), 0, 0, dp(14)) })
        if (favorites.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(38), dp(24), dp(38))
                contentDescription = "Belum ada tool favorit"
            }
            applyInteractiveSurface(empty, 20, 1)
            empty.addView(MdiIconView(this).apply { setIconName("star-outline"); setIconSize(38f); setTextColor(textMuted) }, LinearLayout.LayoutParams(-1, dp(50)))
            empty.addView(label("Belum ada favorit", 16f, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(2)) })
            empty.addView(subLabel("Tekan ☆ pada kartu tool untuk menyimpannya.", 11f).apply { gravity = Gravity.CENTER })
            content.addView(empty, LinearLayout.LayoutParams(-1, dp(170)).apply { topMargin = dp(6) })
            return
        }
        favorites.forEach { (id, name) ->
            content.addView(toolCard(id, name, iconFor(id)).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(8) }
            })
        }
    }

    internal fun selectBottomNav(name: String) {
        val active = if (isDarkTheme) Color.WHITE else Color.rgb(15, 15, 16)
        val inactive = if (isDarkTheme) Color.rgb(155, 155, 160) else Color.rgb(138, 150, 163)
        val navItems = listOf(
            R.id.navHome to (name == "home"),
            R.id.navTools to (name == "all"),
            R.id.navFavorite to (name == "favorites"),
            R.id.navSettings to (name == "settings")
        )
        navItems.forEach { (id, selected) ->
            val item = findViewById<View>(id)
            item.background = if (selected) bg(if (isDarkTheme) Color.rgb(42,42,46) else Color.rgb(241,244,246), 20) else null
            item.alpha = if (selected) 1f else 0.86f
            item.animate().scaleX(if (selected) 1.02f else 1f).scaleY(if (selected) 1.02f else 1f).setDuration(140).start()
        }
        val labels = listOf(
            R.id.navHomeLabel to (name == "home"),
            R.id.navToolsLabel to (name == "all"),
            R.id.navFavoriteLabel to (name == "favorites"),
            R.id.navSettingsLabel to (name == "settings")
        )
        labels.forEach { (id, selected) -> findViewById<TextView>(id).setTextColor(if (selected) active else inactive) }
        val iconMap = listOf(
            R.id.navHomeIcon to (name == "home"),
            R.id.navToolsIcon to (name == "all"),
            R.id.navFavoriteIcon to (name == "favorites"),
            R.id.navSettingsIcon to (name == "settings")
        )
        iconMap.forEach { (id, selected) ->
            val tint = if (selected) active else inactive
            val view = findViewById<View>(id)
            when (view) {
                is MdiIconView -> view.setTextColor(tint)
                is ImageView -> view.drawable?.setTint(tint)
            }
        }
    }

    internal fun categoryCard(icon: String, name: String, desc: String, ids: List<String>) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(10))
            minimumHeight = dp(72)
            contentDescription = "$name. ${ids.size} tools"
        }
        applyInteractiveSurface(card, 17, 2)
        addPressFeedback(card)
        card.setOnClickListener { showCategory(name, ids) }
        val ico = TextView(this).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = bg(if (isDarkTheme) Color.rgb(42,42,46) else Color.rgb(242,245,247), 14)
        }
        card.addView(ico, LinearLayout.LayoutParams(dp(46), dp(46)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(13),0,dp(8),0) }
        texts.addView(label(name, 14f, true))
        texts.addView(subLabel(desc, 11f))
        card.addView(texts, LinearLayout.LayoutParams(0,-2,1f))
        card.addView(TextView(this).apply { text="›"; textSize=24f; setTextColor(textMuted); gravity=Gravity.CENTER; contentDescription="Buka kategori $name" }, LinearLayout.LayoutParams(dp(30), dp(46)))
        content.addView(card, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(8) })
    }

    internal fun showCategory(name: String, ids: List<String>) {
        clearPage(name, true)
        ids.mapNotNull { id -> homeToolMap[id]?.let { id to it } }.forEach { (id, n) ->
            content.addView(toolCard(id, n, iconFor(id)).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(7) }
                alpha = 1f
                translationY = 0f
                visibility = View.VISIBLE
            })
        }
        // Animasi pembuka hanya untuk halaman Beranda. Daftar Semua Tools harus
        // langsung terlihat saat halaman dibuka kembali.
    }

    /**
     * Central Tool Center: search, category filters, favorites and hidden tools.
     * The registry is the single source of truth, so adding a tool does not require
     * rebuilding this screen manually.
     */
    internal fun showAllTools(filter: String = toolCenterFilter) {
        toolCenterFilter = filter
        clearPage("all", true)
        suppressSearch = true
        search.setText("")
        suppressSearch = false

        content.setPadding(dp(12), dp(8), dp(12), dp(18))
        content.addView(label("Tool Center", 22f, true))
        content.addView(subLabel("Cari, favoritkan, sembunyikan, atau buka tool dari satu tempat.", 12f).apply {
            setPadding(0, 0, 0, dp(8))
        })

        val visibleRegistry = ToolRegistry.tools.filter { !toolPreferences.isHidden(it.id) }
        val allCategories = listOf("Semua") + ToolRegistry.categories.filter { category ->
            visibleRegistry.any { it.category == category }
        }
        val quickRow = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val quickInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(2), 0, dp(10))
        }
        allCategories.forEach { category ->
            val chip = homeChip(category, category == toolCenterFilter) {
                showAllTools(category)
            }
            quickInner.addView(chip, LinearLayout.LayoutParams(dp(104), dp(38)).apply { rightMargin = dp(7) })
        }
        quickRow.addView(quickInner)
        content.addView(quickRow, LinearLayout.LayoutParams(-1, dp(48)))

        val stats = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        val categoryCount = if (toolCenterFilter == "Semua") ToolRegistry.categories.size else 1
        val favoriteCount = ToolRegistry.tools.count { toolPreferences.isFavorite(it.id) }
        val hiddenCount = ToolRegistry.tools.count { toolPreferences.isHidden(it.id) }
        stats.addView(subLabel("${visibleRegistry.size} tools • $categoryCount kategori", 11f), LinearLayout.LayoutParams(0, -2, 1f))
        stats.addView(TextView(this).apply {
            text = "★ $favoriteCount   ◌ $hiddenCount tersembunyi"
            textSize = 11f
            setTextColor(textMuted)
            gravity = Gravity.CENTER_VERTICAL
        })
        content.addView(stats, LinearLayout.LayoutParams(-1, dp(30)))

        val manageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        manageRow.addView(homeChip("Favorit", false) { showToolCenterFavorites() }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { rightMargin = dp(5) })
        manageRow.addView(homeChip("Tersembunyi", false) { showHiddenTools() }, LinearLayout.LayoutParams(0, dp(36), 1f).apply { leftMargin = dp(5) })
        content.addView(manageRow, LinearLayout.LayoutParams(-1, dp(44)))

        renderToolCenterItems(visibleRegistry.filter { toolCenterFilter == "Semua" || it.category == toolCenterFilter })
    }

    internal fun renderToolCenterItems(items: List<ToolInfo>) {
        items.forEachIndexed { index, info ->
            val card = toolCenterCard(info)
            content.addView(card, LinearLayout.LayoutParams(-1, dp(74)).apply { bottomMargin = dp(7) })
            if (index < 8) animateToolItem(card, index)
        }
        if (items.isEmpty()) {
            val empty = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(24), dp(34), dp(24), dp(34))
            }
            applyInteractiveSurface(empty, 18, 1)
            empty.addView(MdiIconView(this).apply {
                setIconName("tools")
                setIconSize(34f)
                setTextColor(textMuted)
            }, LinearLayout.LayoutParams(-1, dp(46)))
            empty.addView(label("Tidak ada tool", 15f, true).apply { gravity = Gravity.CENTER })
            empty.addView(subLabel("Coba kategori lain atau hapus filter.", 11f).apply { gravity = Gravity.CENTER })
            content.addView(empty, LinearLayout.LayoutParams(-1, dp(150)))
        }
    }

    internal fun toolCenterCard(info: ToolInfo): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(8), dp(8))
            contentDescription = "${info.name}, ${info.category}"
        }
        applyInteractiveSurface(card, 16, 1)
        addPressFeedback(card)
        card.setOnClickListener { openToolWithPress(info.id, card) }
        card.setOnLongClickListener {
            toggleToolCenterFavorite(info.id)
            true
        }

        val ico = MdiIconView(this).apply {
            setIconName(iconFor(info.id))
            setIconSize(19f)
            setTextColor(Color.WHITE)
            background = bg(Color.rgb(24, 24, 27), 12)
            contentDescription = info.name
        }
        card.addView(ico, LinearLayout.LayoutParams(dp(44), dp(44)))

        val textBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, dp(6), 0)
        }
        textBox.addView(label(info.name, 14f, true).apply { setPadding(0, 0, 0, dp(1)) })
        textBox.addView(subLabel(info.category, 10.5f).apply { setPadding(0, 0, 0, 0) })
        card.addView(textBox, LinearLayout.LayoutParams(0, -2, 1f))

        val star = TextView(this).apply {
            text = if (toolPreferences.isFavorite(info.id)) "★" else "☆"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(if (toolPreferences.isFavorite(info.id)) textMain else textMuted)
            contentDescription = if (toolPreferences.isFavorite(info.id)) "Hapus dari favorit" else "Tambahkan ke favorit"
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener {
                toggleToolCenterFavorite(info.id)
                text = if (toolPreferences.isFavorite(info.id)) "★" else "☆"
                setTextColor(if (toolPreferences.isFavorite(info.id)) textMain else textMuted)
            }
        }
        card.addView(star, LinearLayout.LayoutParams(dp(40), dp(48)))

        val more = TextView(this).apply {
            text = "⋮"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(textMuted)
            contentDescription = "Opsi ${info.name}"
            setOnClickListener { showToolCenterOptions(info) }
        }
        card.addView(more, LinearLayout.LayoutParams(dp(32), dp(48)))
        return card
    }

    internal fun toggleToolCenterFavorite(id: String) {
        toggleFavorite(id)
        showAllTools(toolCenterFilter)
    }

    internal fun showToolCenterOptions(info: ToolInfo) {
        val popup = PopupWindow(this).apply {
            width = dp(230)
            height = dp(176)
            isFocusable = true
            elevation = dp(8).toFloat()
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = bg(if (isDarkTheme) Color.rgb(38,38,42) else Color.WHITE, 16, line)
        }
        fun option(textValue: String, action: () -> Unit) {
            box.addView(TextView(this).apply {
                text = textValue
                textSize = 14f
                setTextColor(textMain)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(8), 0)
                isClickable = true
                setOnClickListener { popup.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, dp(46)))
        }
        option(if (toolPreferences.isFavorite(info.id)) "☆  Hapus dari favorit" else "★  Tambahkan ke favorit") {
            toggleFavorite(info.id); showAllTools(toolCenterFilter)
        }
        option("Buka ${info.name}") { openTool(info.id) }
        option("Sembunyikan tool") {
            toolPreferences.setHidden(info.id, true)
            toast("${info.name} disembunyikan")
            showAllTools(toolCenterFilter)
        }
        popup.contentView = box
        popup.showAtLocation(content, Gravity.CENTER, 0, 0)
    }

    internal fun showToolCenterFavorites() {
        clearPage("all", true)
        content.setPadding(dp(12), dp(8), dp(12), dp(18))
        content.addView(label("Tool Favorit", 22f, true))
        content.addView(subLabel("Tool yang kamu pin untuk akses cepat.", 12f).apply { setPadding(0, 0, 0, dp(10)) })
        val items = ToolRegistry.tools.filter { toolPreferences.isFavorite(it.id) && !toolPreferences.isHidden(it.id) }
        renderToolCenterItems(items)
    }

    internal fun showHiddenTools() {
        clearPage("all", true)
        content.setPadding(dp(12), dp(8), dp(12), dp(18))
        content.addView(label("Tool Tersembunyi", 22f, true))
        content.addView(subLabel("Tool ini tidak ditampilkan di Tool Center biasa.", 12f).apply { setPadding(0, 0, 0, dp(10)) })
        val items = ToolRegistry.tools.filter { toolPreferences.isHidden(it.id) }
        items.forEach { info ->
            val card = toolCenterCard(info)
            // Hidden view uses long-press as restore action; normal tap still opens the tool.
            card.setOnLongClickListener {
                toolPreferences.setHidden(info.id, false)
                toast("${info.name} ditampilkan kembali")
                showHiddenTools()
                true
            }
            content.addView(card, LinearLayout.LayoutParams(-1, dp(74)).apply { bottomMargin = dp(7) })
        }
        if (items.isEmpty()) content.addView(subLabel("Tidak ada tool tersembunyi.", 13f).apply { setPadding(dp(2), dp(20), 0, 0) })
    }

    internal fun addGroupedToolSection(titleText: String, ids: List<String>) {
        sectionTitle(titleText)
        val grid = GridLayout(this).apply {
            columnCount = 2
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        ids.mapNotNull { id -> homeToolMap[id]?.let { id to it } }.forEach { (id, name) ->
            val card = mainPyToolCard(id, name)
            grid.addView(card, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(116)
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
        content.addView(grid, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(3)
        })
    }

    internal fun renderToolList(items: List<Pair<String,String>>) {
        content.removeViews(if (currentPage == "all") 2 else 0, maxOf(0, content.childCount - if (currentPage == "all") 2 else 0))
        items.forEach { (id,name) ->
            content.addView(toolCard(id,name,iconFor(id)).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(7) }
                alpha = 1f
                translationY = 0f
                visibility = View.VISIBLE
            })
        }
    }

    internal fun filterCurrent(q: String) {
        if (currentPage != "home" && currentPage != "all" && currentPage != "Kalkulator Lengkap") return
        val query = q.trim().toLowerCase(Locale.getDefault())
        if (currentPage == "home") {
            // Do not call clearPage() here. It saves a navigation snapshot, changes
            // page state and rebuilds surrounding views while the IME is typing.
            // Only the content workspace is replaced. The search EditText therefore
            // keeps focus, composing state and cursor position.
            if (query.isEmpty()) { showHome(homeFilter); return }

            content.removeAllViews()
            content.setPadding(dp(12), dp(8), dp(12), dp(18))
            content.addView(label("Hasil pencarian", 22f, true))
            content.addView(subLabel("Mencari: $q", 12f))
            val keepIds = homeToolSearchIndex.asSequence()
                .filter { (_, lowerName) -> lowerName.contains(query) }
                .map { it.first }
                .toList()
            val keep = keepIds.mapNotNull { id -> homeToolMap[id]?.let { id to it } }
            renderToolListHomeSearch(keep)
        } else if (currentPage == "Kalkulator Lengkap") {
            if (query.isEmpty()) { calculatorHub(); return }
            content.removeAllViews()
            content.addView(label("Hasil kalkulator",22f,true))
            val allCalc=listOf("Dasar" to "basiccalc","Ilmiah" to "scicalc","Persentase" to "percentcalc","Pecahan" to "fractioncalc","Rasio & Proporsi" to "ratiocalc","Risk-Reward & Position Sizing" to "riskcalc","Compound Interest & Target Tabungan" to "compoundcalc","Margin & PPN/Pajak" to "margincalc","Diskon Bertingkat" to "discountcalc","Konverter Satuan" to "unitcalc","Ukuran Data Digital" to "datacalc","Kecepatan" to "speedcalc","Tekanan" to "pressurecalc","Selisih Tanggal & Umur" to "datecalc","Jam Kerja" to "worktimecalc","Luas & Keliling" to "areacalc","Volume" to "volumecalc","Durasi" to "timecalc","Basis Angka" to "basecalc","Persamaan" to "equationcalc","Cicilan Pinjaman" to "loancalc","Konsumsi BBM" to "fuelcalc",
                "Pivot Point" to "pivotcalc","Voltage Divider" to "dividercalc","Averaging Down & DCA" to "dcacalc","PWM & Duty Cycle" to "pwmcalc",
                "Sprite Sheet Grid" to "spritecalc","Flat vs Efektif/Anuitas" to "installcalc",
                "Konsumsi Listrik & Biaya" to "powercalc","Aspect Ratio" to "aspectcalc","PPN & PPh Final" to "pphcalc","Riwayat Perhitungan" to "history")
            allCalc.filter{it.first.toLowerCase(Locale.getDefault()).contains(query)}.forEach{content.addView(toolCard(it.second,it.first,iconFor(it.second)).apply{layoutParams=LinearLayout.LayoutParams(-1,dp(70)).apply{bottomMargin=dp(7)}})}
        } else {
            if (query.isEmpty()) { showAllTools(toolCenterFilter); return }
            content.removeViews(2, maxOf(0, content.childCount - 2))
            val keep = ToolRegistry.tools.filter { info ->
                !toolPreferences.isHidden(info.id) &&
                    (info.name.toLowerCase(Locale.getDefault()).contains(query) ||
                     info.category.toLowerCase(Locale.getDefault()).contains(query) ||
                     info.id.toLowerCase(Locale.getDefault()).contains(query)) &&
                    (toolCenterFilter == "Semua" || info.category == toolCenterFilter)
            }
            renderToolCenterItems(keep)
        }
    }

    internal fun renderToolListHomeSearch(items: List<Pair<String,String>>) {
        items.forEach { (id,name) -> content.addView(toolCard(id,name,iconFor(id)).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(70)).apply { bottomMargin = dp(7) } }) }
        if (items.isEmpty()) content.addView(subLabel("Tidak ada tool yang cocok.", 13f))
    }

    internal fun showSettings() {
        clearPage("settings", false)
        content.setPadding(dp(12), dp(8), dp(12), dp(18))

        settingsSection("Tampilan")
        content.addView(settingRowClickable("Tema", if (isDarkTheme) "Gelap" else "Terang", "Atur tampilan aplikasi", "weather-sunny" ) {
            val nextDark = !isDarkTheme
            prefs.edit().putString("theme_mode", if (nextDark) "dark" else "light").apply()
            applySystemTheme()
            showSettings()
        })
        content.addView(settingRowClickable("Kolom Beranda", prefs.getInt("home_columns", 2).toString() + " kolom", "Jumlah kolom tool di Beranda", "view-grid-outline") {
            val next = if (prefs.getInt("home_columns", 2) == 2) 3 else 2
            prefs.edit().putInt("home_columns", next).apply()
            toast("Kolom Beranda: $next kolom")
            showSettings()
        })

        settingsSection("Beranda")
        content.addView(settingRowClickable("Aktivitas Terakhir", if (prefs.getBoolean("show_recent_activity", false)) "Ditampilkan" else "Disembunyikan", "Tampilkan aktivitas terbaru di Beranda", "history") {
            prefs.edit().putBoolean("show_recent_activity", !prefs.getBoolean("show_recent_activity", false)).apply(); showSettings()
        })
        content.addView(settingRowClickable("Akses Cepat", if (prefs.getBoolean("show_quick_access", false)) "Ditampilkan" else "Disembunyikan", "Tampilkan akses cepat di Beranda", "view-grid-plus-outline") {
            prefs.edit().putBoolean("show_quick_access", !prefs.getBoolean("show_quick_access", false)).apply(); showSettings()
        })
        content.addView(settingRowClickable("Tools di Beranda", if (prefs.getBoolean("show_home_tools", true)) "Tampilkan" else "Sembunyikan", "Atur daftar tools pada Beranda", "tools") {
            prefs.edit().putBoolean("show_home_tools", !prefs.getBoolean("show_home_tools", true)).apply(); showSettings()
        })
        content.addView(settingRowClickable("File Terbaru", "Buka daftar file terakhir", "clock-outline") { editor(null) })

        settingsSection("Studio & Tools")
        val studios = listOf(
            "Web Project Builder" to "webproject",
            "Network Studio" to "networkstudio", "Developer Studio" to "developerstudio",
            "File Studio" to "filestudio", "Image Studio" to "imagestudio",
            "Finance Studio" to "financestudio", "System Studio" to "systemstudio",
            "Utility Studio" to "utilitystudio", "Studio Center" to "studiocenter",
            "Workspace Center" to "workspace", "Plugin Center" to "plugincenter",
            "Tool Customization" to "customtools"
        )
        studios.forEach { (name,id) -> content.addView(settingRowClickable(name, "Buka Studio", "apps-box") { openTool(id) }) }

        settingsSection("V2.27")
        content.addView(settingRowClickable("Workspace Center", "Project lokal", "Buat dan kelola workspace/project", "folder-outline") { openTool("workspace") })
        content.addView(settingRowClickable("Plugin Center", "Plugin lokal", "Lihat manifest plugin yang tersedia", "tools") { openTool("plugincenter") })
        content.addView(settingRowClickable("Tool Customization", "Pin / sembunyikan", "Atur tool yang tampil di Beranda", "settings") { openTool("customtools") })

        settingsSection("Riwayat")
        content.addView(settingRowClickable("Kelola Riwayat", "Aktivitas tersimpan lokal", "history") { historyTool() })
        content.addView(settingRowClickable("Hapus Riwayat", "Hapus aktivitas dan file terbaru", "delete-outline") {
            AlertDialog.Builder(this).setTitle("Hapus Riwayat").setMessage("Hapus riwayat aktivitas lokal?")
                .setNegativeButton("Batal", null).setPositiveButton("Hapus") { _, _ ->
                    prefs.edit().remove("history").apply(); toast("Riwayat dihapus")
                }.show()
        })

        settingsSection("Aplikasi")
        content.addView(settingRowClickable("Tentang", "GITLS 2.24.0", "information-outline") {
            AlertDialog.Builder(this).setTitle("GITLS").setMessage("Utility Suite • Web Hosting Wi-Fi • Network • Developer • ESP • Finance • Image • Color").setPositiveButton("OK", null).show()
        })
    }

    internal fun settingRowClickable(name: String, desc: String, iconName: String, action: () -> Unit): View {
        return settingRowClickable(name, "", desc, iconName, action)
    }

    internal fun settingRowClickable(name: String, value: String, desc: String, iconName: String, action: () -> Unit): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(13), dp(12), dp(13))
            background = bg(Color.rgb(246,246,246), 22)
            isClickable = true
            setOnClickListener { action() }
        }
        val icon = MdiIconView(this).apply { setIconName(iconName); setIconSize(25f); setTextColor(Color.rgb(30,30,30)); layoutParams = LinearLayout.LayoutParams(dp(48), dp(54)).apply { rightMargin = dp(2) } }
        card.addView(icon)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0,-2,1f) }
        box.addView(label(name, 15f, false))
        box.addView(label(value, 13f).apply { setTextColor(Color.rgb(145,145,145)); setPadding(0,dp(3),0,0) })
        box.addView(subLabel(desc, 11f).apply { visibility = if (desc.isBlank()) View.GONE else View.VISIBLE })
        card.addView(box)
        card.addView(TextView(this).apply { text = "›"; textSize = 30f; setTextColor(Color.rgb(130,130,130)); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(34), dp(54)) })
        return card.apply { layoutParams = LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) } }
    }

    internal fun settingsSection(text: String) {
        content.addView(TextView(this).apply {
            this.text = text.toUpperCase(Locale.getDefault())
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(textMuted)
            setPadding(dp(2), dp(12), dp(2), dp(6))
        })
    }

    internal fun settingRow(name: String, value: String, desc: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = bg(panel2, 16)
        }
        card.addView(label(name, 15f, true))
        card.addView(label(value, 14f).apply { setPadding(dp(2), dp(1), dp(2), dp(4)) })
        card.addView(subLabel(desc, 12f).apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        card.minimumHeight = dp(96)
        card.layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(7)
        }
        return card
    }

    internal fun iconFor(id: String): String = when (id) {
        "filemanager" -> "folder-outline"
        "recentfiles" -> "history"
        "backuprestore" -> "backup-restore"
        "workspace" -> "view-dashboard-outline"
        "plugincenter" -> "puzzle-outline"
        "customtools" -> "tune-variant"
        "studiocenter" -> "palette-swatch-outline"
        "editor" -> "file-document-edit-outline"
        "reminder" -> "bell-outline"
        "zip" -> "folder-zip-outline"
        "githubzip" -> "rocket-launch-outline"
        "wifi" -> "wifi"
        "json" -> "code-json"
        "hash" -> "pound"
        "base64" -> "numeric-4-box-outline"
        "url" -> "link-variant"
        "regex" -> "regex"
        "uuid" -> "identifier"
        "color" -> "palette-outline"
        "number" -> "calculator-variant-outline"
        "history" -> "history"
        "uicolorcalc" -> "palette-swatch-variant"
        "basiccalc" -> "calculator"
        "scicalc" -> "function-variant"
        "percentcalc" -> "percent-outline"
        "fractioncalc" -> "division"
        "ratiocalc" -> "scale-balance"
        "unitcalc" -> "ruler"
        "areacalc" -> "vector-square"
        "volumecalc" -> "cube-outline"
        "speedcalc" -> "speedometer"
        "timecalc" -> "clock-outline"
        "datecalc" -> "calendar-range-outline"
        "loancalc" -> "bank-outline"
        "fuelcalc" -> "gas-station-outline"
        "pivotcalc" -> "chart-areaspline"
        "dividercalc" -> "sine-wave"
        "dcacalc" -> "finance"
        "pwmcalc" -> "pulse"
        "spritecalc" -> "grid"
        "installcalc" -> "cash-multiple"
        "powercalc" -> "flash-outline"
        "aspectcalc" -> "aspect-ratio"
        "pphcalc" -> "percent"
        "financereader" -> "wallet-outline"
        "financedashboard" -> "chart-line"
        "securitycenter" -> "shield-check-outline"
        "helpbot" -> "robot-outline"
        "riskcalc" -> "scale-balance"
        "compoundcalc" -> "chart-timeline-variant"
        "margincalc" -> "cash-register"
        "discountcalc" -> "tag-outline"
        "datacalc" -> "database-outline"
        "pressurecalc" -> "gauge"
        "worktimecalc" -> "briefcase-clock-outline"
        "basecalc" -> "numeric"
        "equationcalc" -> "sigma"
        "textstat" -> "format-list-numbered"
        "case" -> "format-letter-case"
        "dedupe" -> "content-duplicate"
        "compare" -> "compare"
        "slug" -> "link-box-variant-outline"
        "lorem" -> "format-align-left"
        "password" -> "form-textbox-password"
        "token" -> "key-variant"
        "jwt" -> "badge-account-outline"
        "hmac" -> "shield-key-outline"
        "totp" -> "clock-check-outline"
        "aes" -> "lock-outline"
        "random" -> "dice-multiple"
        "checksum" -> "file-check-outline"
        "hex" -> "hexadecimal"
        "base32" -> "numeric"
        "dns" -> "dns"
        "rdns" -> "lan"
        "port" -> "lan-connect"
        "publicip" -> "ip-outline"
        "ping" -> "access-point-network"
        "ipinfo" -> "ip-network"
        "ssl" -> "certificate-outline"
        "apk" -> "android"
        "qr" -> "qrcode"
        "fileconvert" -> "swap-horizontal"
        "system" -> "cog-outline"
        "http" -> "web"
        "webhostwifi" -> "wifi-star"
        "filehashcompare" -> "file-compare"
        "markdown" -> "language-markdown-outline"
        "sql" -> "database-search-outline"
        "yaml" -> "file-code-outline"
        "toml" -> "file-cog-outline"
        "cron" -> "calendar-clock-outline"
        "passwordstrength" -> "shield-lock-outline"
        "fileencryption" -> "file-lock-outline"
        "steganography" -> "image-lock-outline"
        "passwordanalyzer" -> "shield-search"
        "breachchecker" -> "shield-alert-outline"
        "securenotes" -> "note-edit-outline"
        "totpvault" -> "shield-key-outline"
        "pgp" -> "key-chain-variant"
        "sshkeygen" -> "key-plus"
        "certviewer" -> "certificate-outline"
        "virusscanner" -> "bug-outline"
        "urlsafety" -> "link-lock"
        "stopwatch" -> "timer-outline"
        "timer" -> "timer-sand"
        "imageinfo" -> "image-search-outline"
        "imagetools" -> "image-edit-outline"
        "timestamp" -> "clock-time-four-outline"
        "unicode" -> "format-letter-case-upper"
        "urlparser" -> "link-variant"
        "mime" -> "file-document-outline"
        "jsonformat" -> "code-braces"
        "xmlformat" -> "xml"
        "uuidbatch" -> "identifier"
        "base64file" -> "file-code-outline"
        "httpheaders" -> "format-header-1"
        "textreplace" -> "find-replace"
        "wordfreq" -> "counter"
        "deviceinfo" -> "cellphone-information"
        "storage" -> "database"
        "apps" -> "apps"
        "network" -> "network"
        "battery" -> "battery-high"
        "filesearch" -> "file-search"
        "clipboard" -> "clipboard-text-outline"
        "esp" -> "chip"
        "espdiscover" -> "radar"
        "ledstudio" -> "led-strip"
        "espdevice" -> "devices"
        "espgpio" -> "expansion-card-variant"
        "espsensor" -> "thermometer"
        "espwifi" -> "router-wireless"
        "espota" -> "upload-network"
        "esphttp" -> "web-box"
        "espmqtt" -> "message-cog-outline"
        "espusb" -> "usb-port"
        "espserial" -> "serial-port"
        "iotdashboard" -> "view-dashboard-outline"
        "espstudio" -> "tools"
        "visualwiring" -> "vector-polyline"
        "ocr" -> "ocr"
        "unitconverter" -> "swap-horizontal-bold"
        "apkanalyzer" -> "android-studio"
        "netscanner" -> "magnify-scan"
        "webproject" -> "web-plus"
        "webeditor" -> "language-html5"
        "networkstudio" -> "lan"
        "developerstudio" -> "code-tags"
        "filestudio" -> "folder-multiple-outline"
        "imagestudio" -> "image-multiple-outline"
        "colorstudio" -> "palette"
        "systemstudio" -> "cellphone-cog"
        "financestudio" -> "cash-multiple"
        "utilitystudio" -> "toolbox-outline"
        "whois" -> "account-search-outline"
        "traceroute" -> "routes"
        "subnetcalc" -> "ip-network-outline"
        "restclient" -> "api"
        "websocket" -> "connection"
        "networkcenter" -> "lan-connect"
        "systemcenter" -> "view-dashboard-outline"
        "apkcompare" -> "compare-horizontal"
        "duplicatefinder" -> "file-multiple-outline"
        "largefilefinder" -> "file-search-outline"
        else -> "tools"
    }
    // Shared full-width input used by the tools.
    // Normal fields are deliberately taller and multiline fields get substantially
    // more vertical space so text is edited in a real work area instead of a tiny box.
    internal fun edit(hint: String = "", multiline: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        textSize = 16f
        setTextColor(textMain)
        setHintTextColor(textMuted)
        setPadding(dp(Ds.SPACE_LG), dp(Ds.SPACE_MD), dp(Ds.SPACE_LG), dp(Ds.SPACE_MD))
        val theme = visualTheme()
        background = bg(theme.surface, Ds.RADIUS_MD, theme.border)
        isSingleLine = !multiline
        isFocusable = true
        isFocusableInTouchMode = true
        if (hint.isNotBlank()) contentDescription = hint
        // Focus state jelas: border 2dp memakai warna teks utama.
        setOnFocusChangeListener { view, focused ->
            view.background = bg(
                theme.surface, Ds.RADIUS_MD,
                if (focused) theme.button else theme.border
            ).also { d -> if (focused) d.setStroke(dp(2), theme.button) }
        }
        if (multiline) {
            minLines = 6
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        } else {
            minLines = 1
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT
        }
        // Tinggi responsif: ikut tinggi layar (HP kecil s/d besar, portrait/landscape).
        val multilineHeight = (resources.displayMetrics.heightPixels * 0.30f).toInt()
            .coerceIn(dp(160), dp(300))
        layoutParams = LinearLayout.LayoutParams(
            -1,
            if (multiline) multilineHeight else -2
        ).apply { bottomMargin = dp(Ds.SPACE_MD) }
        if (!multiline) minHeight = dp(56)
    }

    internal fun button(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 14f
        minHeight = dp(Ds.TOUCH_MIN)
        setPadding(dp(Ds.SPACE_LG), dp(Ds.SPACE_XS), dp(Ds.SPACE_LG), dp(Ds.SPACE_XS))
        setStateListAnimator(null)
        isAllCaps = false
        letterSpacing = 0.01f
        contentDescription = text
        isFocusable = true
        // Hierarki otomatis: aksi utama terisi, aksi pendukung (Salin/Hapus/Reset...) outline.
        if (isSecondaryAction(text)) styleAsSecondary(this) else styleAsPrimary(this)
        setOnClickListener {
            animate().scaleX(0.98f).scaleY(0.98f).setDuration(Ds.ANIM_FAST / 2)
                .withEndAction { animate().scaleX(1f).scaleY(1f).setDuration(Ds.ANIM_FAST).start() }.start()
            onClick()
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Ds.SPACE_SM) }
    }

    // Shared modern utility layout. It keeps the monochrome identity while giving
    // each tool a clearer visual hierarchy instead of the old input-button-output stack.
    internal fun toolHeader(titleText: String, description: String, icon: String = "•"): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = bg(panel2, 18, line)
        }
        val iconView = MdiIconView(this).apply {
            setIconName(resolveToolIcon(titleText, icon)); setIconSize(22f); setTextColor(textMain)
            background = bg(panel, 14, line)
        }
        box.addView(iconView, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(titleText, 18f, true))
        texts.addView(subLabel(description, 11f))
        box.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        return box
    }

    // Setiap tool wajib punya ikon: pakai nama ikon jika valid, kalau tidak cari dari nama tool.
    internal fun resolveToolIcon(titleText: String, icon: String): String {
        if (MdiGlyphs.has(icon)) return icon
        val exact = homeTools.firstOrNull { it.second.equals(titleText, true) }?.first
        if (exact != null) return iconFor(exact)
        val fuzzy = homeTools.firstOrNull { titleText.contains(it.second, true) || it.second.contains(titleText, true) }?.first
        return if (fuzzy != null) iconFor(fuzzy) else "tools"
    }

    internal fun toolSection(titleText: String, subtitle: String = ""): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(8), dp(2), dp(5)) }
        box.addView(label(titleText, 12f, true))
        if (subtitle.isNotBlank()) box.addView(subLabel(subtitle, 10f))
        return box
    }

    internal fun toolStatus(textValue: String, positive: Boolean = false): TextView = TextView(this).apply {
        val dotColor = statusColor(if (positive) Ds.State.SUCCESS else Ds.State.INFO)
        val sb = android.text.SpannableStringBuilder("●  $textValue")
        sb.setSpan(android.text.style.ForegroundColorSpan(dotColor), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text = sb
        textSize = 13f
        setTextColor(textMain)
        minHeight = dp(Ds.TOUCH_MIN)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(Ds.SPACE_LG), dp(Ds.SPACE_MD), dp(Ds.SPACE_LG), dp(Ds.SPACE_MD))
        background = bg(if (positive) panel else panel2, Ds.RADIUS_MD, line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(Ds.SPACE_SM) }
    }

    internal fun addToolHeader(titleText: String, description: String, icon: String = "•") {
        content.addView(toolHeader(titleText, description, icon), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
    }

    // V4: workspace helpers for complex tools. These keep domain logic untouched while
    // giving network/file/security/system tools a consistent mobile workspace hierarchy.
    internal fun toolWorkspace(titleText: String, description: String, icon: String = "tools") {
        addToolHeader(titleText, description, icon)
        content.addView(toolControlBar(titleText), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
    }

    internal fun toolWorkspaceSection(titleText: String, subtitle: String = "") {
        content.addView(toolSection(titleText, subtitle))
    }

    internal fun compactButtonRow(vararg items: Pair<String, () -> Unit>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        items.forEachIndexed { index, item ->
            val b = button(item.first, item.second)
            if (index == 0) styleAsPrimary(b) else styleAsSecondary(b)
            row.addView(b, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) leftMargin = dp(Ds.SPACE_SM)
            })
        }
        return row
    }

    // Shared controls for every tool: status, history and a contextual help panel.
    // Domain-specific controls remain inside each tool so the layout stays fast on mobile.
    internal fun toolControlBar(name: String): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = bg(panel2, 14, line)
        }
        val status = TextView(this).apply {
            text = "●  Siap digunakan"
            textSize = 11f
            setTextColor(textMain)
            gravity = Gravity.CENTER_VERTICAL
        }
        box.addView(status, LinearLayout.LayoutParams(0, dp(40), 1f))
        fun actionText(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
            this.text = text
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = bg(panel, 10, line)
            setPadding(dp(9), 0, dp(9), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        box.addView(actionText("Riwayat") { historyTool() }, LinearLayout.LayoutParams(dp(76), dp(36)).apply { rightMargin = dp(5) })
        box.addView(actionText("Info") {
            AlertDialog.Builder(this)
                .setTitle(name)
                .setMessage(toolDescription(name))
                .setPositiveButton("OK", null)
                .show()
        }, LinearLayout.LayoutParams(dp(54), dp(36)))
        return box
    }

    internal fun toolDescription(name: String): String = when {
        name.contains("JSON", true) -> "Validasi, rapikan, kecilkan, dan proses JSON. Hasil dapat disalin atau dibagikan."
        name.contains("Converter", true) -> "Pilih input, tentukan format tujuan, proses file, lalu buka atau bagikan hasil."
        name.contains("Network", true) || name in setOf("Ping", "DNS Lookup", "Reverse DNS", "Port Checker", "SSL Certificate", "REST / API Client", "WebSocket Client", "Network Center") -> "Tool jaringan untuk diagnosis, validasi koneksi, API, WebSocket, dan pemeriksaan host."
        name.contains("ESP", true) || name.contains("IoT", true) -> "Koneksi, kontrol, monitoring, diagnosis, dan pengujian perangkat ESP/IoT."
        name.contains("Finance", true) || name.contains("Keuangan", true) -> "Pencatatan lokal, transaksi, ringkasan, anggaran, insight, dan ekspor."
        name.contains("Calculator", true) || name.contains("Kalkulator", true) || name in setOf("Voltage Divider", "Pivot Point", "PWM & Duty Cycle") -> "Masukkan parameter, hitung, lalu salin atau bagikan hasil. Input divalidasi sebelum perhitungan."
        name.contains("File", true) || name.contains("ZIP", true) -> "Kelola, baca, konversi, kompres, atau ekstrak file dengan hasil yang dapat diproses kembali."
        else -> "Tool MyTools dengan fungsi utama, validasi input, hasil, salin, bagikan, dan riwayat lokal bila relevan."
    }

    internal fun sanitizeSensitiveHistory() {
        val arr = runCatching { JSONArray(prefs.getString("history", "[]") ?: "[]") }.getOrElse { JSONArray() }
        val cleaned = JSONArray()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            if (!isSensitiveTool(item.optString("tool"))) cleaned.put(item)
        }
        if (cleaned.length() != arr.length()) prefs.edit().putString("history", cleaned.toString()).apply()
    }

    internal fun isSensitiveTool(name: String = currentPage): Boolean = name in setOf(
        "Password Generator", "AES Encrypt / Decrypt", "HMAC Generator", "TOTP Generator",
        "Token Acak", "Random Bytes", "JWT Decoder"
    )

    internal fun output(text: String) {
        val safe = text.ifBlank { "(kosong)" }
        if (!isSensitiveTool()) saveHistory(currentPage, safe)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Ds.SPACE_LG), dp(Ds.SPACE_MD), dp(Ds.SPACE_LG), dp(Ds.SPACE_SM))
            background = bg(panel2, Ds.RADIUS_LG, line)
            contentDescription = "Hasil $currentPage"
        }
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = MdiIconView(this).apply {
            setIconName(Ds.stateIcon(Ds.State.SUCCESS))
            setIconSize(18f)
            setTextColor(statusColor(Ds.State.SUCCESS))
            background = bg(panel, Ds.RADIUS_SM, line)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        heading.addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(Ds.SPACE_SM) })
        val headingText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headingText.addView(label("Hasil", 14f, true))
        headingText.addView(subLabel("${currentPage} • selesai", 12f))
        heading.addView(headingText, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(heading)

        // Output panjang/berbaris banyak (kode, log, JSON) memakai monospace agar rapi.
        val looksLikeCode = safe.contains('\n') || safe.startsWith("{") || safe.startsWith("[")
        val result = TextView(this).apply {
            this.text = safe
            textSize = if (looksLikeCode) 13f else 14f
            if (looksLikeCode) typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(textMain)
            setPadding(dp(Ds.SPACE_MD), dp(Ds.SPACE_MD), dp(Ds.SPACE_MD), dp(Ds.SPACE_MD))
            background = bg(panel, Ds.RADIUS_MD, line)
            setTextIsSelectable(true)
            gravity = Gravity.TOP or Gravity.START
        }
        card.addView(result, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Ds.SPACE_SM); bottomMargin = dp(Ds.SPACE_SM) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fun resultAction(textValue: String, iconName: String, primary: Boolean, onClick: () -> Unit): TextView = TextView(this).apply {
            this.text = textValue
            textSize = 13f
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            minHeight = dp(Ds.TOUCH_MIN)
            isClickable = true
            isFocusable = true
            contentDescription = textValue
            if (primary) styleAsPrimary(this) else styleAsSecondary(this)
            setOnClickListener { onClick() }
        }
        actions.addView(resultAction("Salin", "content-copy", true) { copyText(safe) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(Ds.SPACE_XS) })
        actions.addView(resultAction("Bagikan", "share-variant", false) { shareText(safe) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Ds.SPACE_XS); rightMargin = dp(Ds.SPACE_XS) })
        actions.addView(resultAction("Bersihkan", "delete-outline", false) { content.removeView(card) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(Ds.SPACE_XS) })
        card.addView(actions)
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Ds.SPACE_SM); bottomMargin = dp(Ds.SPACE_SM) })
    }

    internal fun copyText(value:String) {
        val cm=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("MyTools",value)); toast("Hasil disalin")
    }

    internal fun shareText(value:String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,value) },"Bagikan hasil"))
    }

    internal fun saveHistory(tool:String, result:String) {
        val arr=runCatching { JSONArray(prefs.getString("history","[]") ?: "[]") }.getOrElse { JSONArray() }
        val item=JSONObject().apply { put("time",System.currentTimeMillis()); put("tool",tool); put("result",result.take(2000)) }
        val next=JSONArray(); next.put(item)
        for(i in 0 until minOf(arr.length(),49)) next.put(arr.getJSONObject(i))
        prefs.edit().putString("history",next.toString()).apply()
    }

    internal fun historyTool() {
        clearPage("History Center")
        content.addView(label("History Center",22f,true))
        content.addView(subLabel("Riwayat tool, hasil, dan aktivitas lokal • maksimal 50 hasil",12f))
        val arr=runCatching { JSONArray(prefs.getString("history","[]") ?: "[]") }.getOrElse { JSONArray() }
        if(arr.length()==0) { content.addView(subLabel("Belum ada riwayat.",13f)); return }
        for(i in 0 until arr.length()) {
            val o=arr.getJSONObject(i); val whenText=SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(Date(o.optLong("time")))
            val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=bg(panel2,14,line);setOnClickListener{copyText(o.optString("result"))}}
            card.addView(label("${o.optString("tool")} • $whenText",12f,true))
            card.addView(subLabel(o.optString("result"),11f))
            content.addView(card)
        }
        content.addView(subLabel("Catatan: halaman ini adalah pemeriksaan konfigurasi aplikasi, bukan audit keamanan perangkat secara menyeluruh.", 11f))
    }

    internal fun showAbout() {
        AlertDialog.Builder(this).setTitle("GITLS 2.30.0").setMessage(
            "Native Android utility suite.\n\nVersi 2.30 mendesain ulang GitHub Publisher (pengaturan, proses upload animasi, dan halaman hasil) serta melengkapi ikon semua tools. Versi 2.28 menambahkan GitHub ZIP Publisher, perbaikan keyboard-safe navigation, dan Snap Search yang lebih responsif. Fitur V2.25 dan V2.26 tetap dipertahankan. Versi 2.23.0 menyatukan Editor dan Web Code Editor menjadi satu workspace kode HTML, CSS, dan JavaScript, memindahkan format JSON/CSV/Base64/XML dan lainnya ke menu (+), serta merapikan mode editor layar penuh agar fokus pada kode."
        ).setPositiveButton("OK", null).show()
    }

    internal fun openTool(id: String) {
        recordRecentTool(id)
        when (id) {
            "filemanager" -> fileManager(filesDir)
            "recentfiles" -> recentFilesTool()
            "backuprestore" -> backupRestoreTool()
            "workspace" -> workspaceCenterTool()
            "plugincenter" -> pluginCenterTool()
            "customtools" -> toolCustomizationTool()
            "studiocenter" -> studioCenterTool()
            "editor" -> editor(null)
            "reminder" -> reminderTool()
            "zip" -> zipTool()
            "githubzip" -> githubZipTool()
            "wifi" -> wifiInfo()
            "json" -> editor(null, "json")
            "hash" -> hashTool()
            "base64" -> simpleTransform("Base64", "Encode", "Decode")
            "url" -> urlTool()
            "regex" -> regexTool()
            "uuid" -> simpleResultTool("UUID Generator") { UUID.randomUUID().toString() }
            "color" -> colorTool()
            "number" -> calculatorHub()
            "history" -> historyTool()
            // UI Color adalah pipet layar global, bukan mode kalkulator.
            "uicolorcalc" -> uiColorPickerTool()
            // Semua kalkulator tetap berada di satu layar. Mode hanya mengganti isi workspace.
            "basiccalc", "scicalc", "percentcalc", "fractioncalc", "ratiocalc",
            "unitcalc", "areacalc", "volumecalc", "speedcalc", "timecalc", "datecalc",
            "loancalc", "fuelcalc", "pivotcalc", "dividercalc", "dcacalc", "pwmcalc",
            "spritecalc", "installcalc", "powercalc", "aspectcalc", "pphcalc" -> calculatorHub(id)
            "financereader" -> financeReaderTool()
            "financedashboard" -> financeDashboardTool()
            "securitycenter" -> securityCenterTool()
            "helpbot" -> helpBotTool()
            "riskcalc" -> riskRewardCalculator()
            "compoundcalc" -> compoundCalculator()
            "margincalc" -> marginTaxCalculator()
            "discountcalc" -> tieredDiscountCalculator()
            "datacalc" -> dataUnitCalculator()
            "pressurecalc" -> pressureCalculator()
            "worktimecalc" -> workTimeCalculator()
            "basecalc" -> baseCalculator()
            "equationcalc" -> equationCalculator()
            "textstat" -> textStatTool()
            "case" -> caseTool()
            "dedupe" -> dedupeTool()
            "compare" -> compareTool()
            "slug" -> slugTool()
            "lorem" -> loremTool()
            "password" -> passwordTool()
            "token" -> tokenTool()
            "jwt" -> jwtTool()
            "hmac" -> hmacTool()
            "totp" -> totpTool()
            "aes" -> aesTool()
            "random" -> randomTool()
            "checksum" -> checksumTool()
            "hex" -> hexTool()
            "base32" -> base32Tool()
            "dns" -> dnsTool()
            "rdns" -> reverseDnsTool()
            "port" -> portTool()
            "publicip" -> publicIpTool()
            "ping" -> pingTool()
            "ipinfo" -> ipInfoTool()
            "ssl" -> sslTool()
            "apk" -> apkInspector()
            "qr" -> qrTool()
            "fileconvert" -> fileConvertTool()
            "system" -> systemInfo()
            "http" -> httpServer()
            "webhostwifi" -> wifiHtmlHostingTool()
            "filehashcompare" -> fileHashCompareTool()
            "markdown" -> markdownViewerTool()
            "sql" -> sqlToolsTool()
            "yaml" -> yamlFormatterTool()
            "toml" -> tomlInspectorTool()
            "cron" -> cronHelperTool()
            "passwordstrength" -> passwordStrengthTool()
            "fileencryption" -> fileEncryptionTool()
            "steganography" -> steganographyTool()
            "passwordanalyzer" -> passwordStrengthAnalyzerTool()
            "breachchecker" -> dataBreachCheckerTool()
            "securenotes" -> secureNotesTool()
            "totpvault" -> totpVaultTool()
            "pgp" -> pgpTool()
            "sshkeygen" -> sshKeyGeneratorTool()
            "certviewer" -> certificateViewerTool()
            "virusscanner" -> virusScannerTool()
            "urlsafety" -> urlSafetyTool()
            "stopwatch" -> stopwatchTool()
            "timer" -> timerTool()
            "imageinfo" -> imageInfoTool()
            "imagetools" -> imageToolsTool()
            "timestamp" -> timestampTool()
            "unicode" -> unicodeTool()
            "urlparser" -> urlParserTool()
            "mime" -> mimeTool()
            "jsonformat" -> editor(null, "json")
            "xmlformat" -> xmlFormatTool()
            "uuidbatch" -> uuidBatchTool()
            "base64file" -> base64FileTool()
            "httpheaders" -> httpHeadersTool()
            "textreplace" -> textReplaceTool()
            "wordfreq" -> wordFrequencyTool()
            "deviceinfo" -> deviceInfoTool()
            "storage" -> storageAnalyzerTool()
            "apps" -> appManagerTool()
            "network" -> networkInfoTool()
            "battery" -> batteryInfoTool()
            "filesearch" -> fileSearchTool()
            "clipboard" -> clipboardManagerTool()
            "esp" -> espTools()
            "espdiscover" -> espAutoDiscovery()
            "ledstudio" -> espLedStudio()
            "espdevice" -> espDeviceManager()
            "espgpio" -> espGpioController()
            "espsensor" -> espSensorDashboard()
            "espwifi" -> espWifiManager()
            "espota" -> espOtaFirmware()
            "esphttp" -> espHttpApiTester()
            "espmqtt" -> espMqttClient()
            "espusb" -> espUsbInfo()
            "espserial" -> espTcpSerialMonitor()
            "iotdashboard","espstudio","visualwiring" -> modularIotDashboard()
            "ocr" -> ocrTool()
            "unitconverter" -> unitConverterProTool()
            "apkanalyzer" -> apkAnalyzerTool()
            "netscanner" -> networkScannerTool()
            "webproject" -> webProjectBuilder()
            "webeditor" -> editor(null)
            "networkstudio" -> networkStudioTool()
            "developerstudio" -> developerStudioTool()
            "filestudio" -> fileStudioTool()
            "imagestudio" -> imageToolsTool()
            "colorstudio" -> colorTool()
            "systemstudio" -> systemStudioTool()
            "financestudio" -> financeStudioTool()
            "utilitystudio" -> utilityStudioTool()
            "whois" -> whoisTool()
            "traceroute" -> tracerouteTool()
            "subnetcalc" -> subnetCalculatorTool()
            "restclient" -> restApiClientTool()
            "websocket" -> webSocketClientTool()
            "networkcenter" -> networkCenterTool()
            "systemcenter" -> systemCenterTool()
            "apkcompare" -> apkCompareTool()
            "duplicatefinder" -> duplicateFinderTool()
            "largefilefinder" -> largeFileFinderTool()
        }
        if (currentPage != "Editor" && !currentPage.startsWith("Editor - ")) {
            content.post {
                content.animate().cancel()
                content.alpha = 0.985f
                content.translationY = dp(4).toFloat()
                content.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(160L)
                    .start()
            }
        }
    }

    internal fun closeWebSocket() {
        runCatching { wsInput?.close() }
        runCatching { wsOutput?.close() }
        runCatching { wsSocket?.close() }
        wsInput = null; wsOutput = null; wsSocket = null
    }

    internal fun appendLog(text: String) {
        output(text)
    }

    internal fun bytesText(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var i = -1
        do { value /= 1024.0; i++ } while (value >= 1024.0 && i < units.lastIndex)
        return String.format(Locale.US, "%.1f %s", value, units[i])
    }

    internal fun openWebSocket(raw:String):String {
        closeWebSocket()
        val u=URI(raw); val secure=u.scheme.equals("wss",true); val port=if(u.port>0)u.port else if(secure)443 else 80
        val s:Socket = if(secure) javax.net.ssl.SSLSocketFactory.getDefault().createSocket() else Socket()
        s.connect(InetSocketAddress(u.host,port),8000); s.soTimeout=12000
        val out=s.getOutputStream(); val input=s.getInputStream()
        val key=Base64.getEncoder().encodeToString(ByteArray(16).also{SecureRandom().nextBytes(it)})
        val path=(if(u.rawPath.isNullOrBlank()) "/" else u.rawPath)+(u.rawQuery?.let{"?$it"} ?: "")
        out.write(("GET $path HTTP/1.1\r\nHost: ${u.host}:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray(StandardCharsets.US_ASCII)); out.flush()
        val header=readHttpHeader(input); if(!header.startsWith("HTTP/1.1 101") && !header.startsWith("HTTP/1.0 101")) throw IOException("Handshake gagal: ${header.lines().firstOrNull()}")
        wsSocket=s; wsInput=input; wsOutput=out
        return "Connected: $raw"
    }

    internal fun readHttpHeader(input:InputStream):String { val b=ByteArrayOutputStream(); var state=0; while(true){val c=input.read();if(c<0)break;b.write(c);state=if(state==0&&c==13)1 else if(state==1&&c==10)2 else if(state==2&&c==13)3 else if(state==3&&c==10)4 else 0;if(state==4)break;if(b.size()>16000)throw IOException("Header terlalu besar")};return b.toString("ISO-8859-1") }
    internal fun writeWsText(text:String){ val out=wsOutput ?: throw IOException("Belum terhubung"); val data=text.toByteArray(StandardCharsets.UTF_8); val mask=ByteArray(4).also{SecureRandom().nextBytes(it)}; val first=0x81; out.write(first); when { data.size<126 -> out.write(0x80 or data.size); data.size<=65535 -> {out.write(0x80 or 126);out.write(data.size shr 8);out.write(data.size and 255)} else -> throw IOException("Pesan terlalu besar") }; out.write(mask); for(i in data.indices) out.write(data[i].toInt() xor mask[i%4].toInt()); out.flush() }
    internal fun readWsText():String{
        val input=wsInput ?: throw IOException("Belum terhubung")
        val h1=input.read(); val h2=input.read(); if(h1<0||h2<0)throw IOException("Koneksi ditutup")
        val opcode=h1 and 0x0f; var len=(h2 and 0x7f).toLong(); val masked=(h2 and 0x80)!=0
        if(len==126L){len=((input.read() shl 8) or input.read()).toLong()} else if(len==127L){len=0;repeat(8){len=(len shl 8) or input.read().toLong()}}
        if(len>1024*1024)throw IOException("Frame terlalu besar")
        val mask=if(masked)ByteArray(4).also{readFullyWs(input,it)} else null
        val data=ByteArray(len.toInt());readFullyWs(input,data);if(mask!=null)for(i in data.indices)data[i]=(data[i].toInt() xor mask[i%4].toInt()).toByte()
        return when(opcode){1->String(data,StandardCharsets.UTF_8);8->"[CLOSE]";9->"[PING]";10->"[PONG]";else->"[opcode=$opcode, ${data.size} bytes]"}
    }
    internal fun readFullyWs(input: InputStream, b: ByteArray){
        var off=0
        while(off<b.size){
            val n=input.read(b,off,b.size-off)
            if(n<0) throw IOException("Koneksi ditutup")
            off+=n
        }
    }

    internal fun apkCompareTool(){
        clearPage("APK Compare")
        content.addView(button("Pilih APK A") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/vnd.android.package-archive";addCategory(Intent.CATEGORY_OPENABLE)},1301) })
        content.addView(button("Pilih APK B") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/vnd.android.package-archive";addCategory(Intent.CATEGORY_OPENABLE)},1302) })
    }

    internal fun compareApks(a:Uri,b:Uri){thread{val r=runCatching{
        val fa=uriToCacheFile(a,"apk_a.apk");val fb=uriToCacheFile(b,"apk_b.apk");val pa=packageArchiveInfo(fa);val pb=packageArchiveInfo(fb)
        val sa=fa.length();val sb=fb.length();val ha=sha256(fa);val hb=sha256(fb)
        val ea=zipSummary(fa);val eb=zipSummary(fb)
        "APK A\nPackage: ${pa.first}\nVersion: ${pa.second}\nSize: ${bytesText(sa)}\nSHA-256: $ha\nZIP entries: ${ea.first}\n\nAPK B\nPackage: ${pb.first}\nVersion: ${pb.second}\nSize: ${bytesText(sb)}\nSHA-256: $hb\nZIP entries: ${eb.first}\n\nMetadata package sama: ${pa.first==pb.first}\nVersion sama: ${pa.second==pb.second}\nHash sama: ${ha.equals(hb,true)}\nUkuran beda: ${bytesText(kotlin.math.abs(sa-sb))}\nEntry beda: ${kotlin.math.abs(ea.first-eb.first)}"
    }.getOrElse{"APK Compare gagal: ${it.message}"};runOnUiThread{clearPage("APK Compare");output(r)}}}

    internal fun uriToCacheFile(uri:Uri,name:String):File{val f=File(cacheDir,name);contentResolver.openInputStream(uri)?.use{input->FileOutputStream(f).use{input.copyTo(it)}}?:throw IOException("File tidak dapat dibaca");return f}
    internal fun packageArchiveInfo(f:File):Pair<String,String>{val flags=if(Build.VERSION.SDK_INT>=28)PackageManager.GET_SIGNING_CERTIFICATES else 0;val p=packageManager.getPackageArchiveInfo(f.absolutePath,flags)?:throw IOException("APK tidak valid");return p.packageName to (if(Build.VERSION.SDK_INT>=28)p.longVersionCode.toString() else p.versionCode.toString())}
    internal fun sha256(f:File):String{val md=MessageDigest.getInstance("SHA-256");FileInputStream(f).use{inp->val buf=ByteArray(8192);while(true){val n=inp.read(buf);if(n<0)break;md.update(buf,0,n)}};return md.digest().joinToString(""){String.format("%02x",it)} }
    internal fun zipSummary(f:File):Pair<Int,Long>{
        var c=0; var total=0L
        ZipInputStream(BufferedInputStream(FileInputStream(f))).use { z ->
            while(true){
                val e=z.nextEntry ?: break
                c++
                if(!e.isDirectory) total += e.size.coerceAtLeast(0L)
            }
        }
        return c to total
    }

    internal fun scanFiles(roots:List<File>,out:MutableList<File>,limit:Int){for(root in roots){scanFiles(root,out,limit);if(out.size>=limit)return}}
    internal fun scanFiles(dir:File,out:MutableList<File>,limit:Int){if(out.size>=limit)return;val list=runCatching{dir.listFiles()}.getOrNull()?:return;for(f in list){if(out.size>=limit)return;if(f.isFile)out.add(f) else if(f.isDirectory)scanFiles(f,out,limit)}}
    internal fun infoCard(titleText:String,bodyText:String):View{val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=bg(panel2,14,line)};c.addView(label(titleText,13f,true));c.addView(subLabel(bodyText,11f));c.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)};return c}

    // ---------- CUSTOM DASHBOARD BUILDER / STUDIO MODE ----------
    // Studio landscape: canvas hitam, widget bebas diposisikan, tersimpan lokal.
    internal data class StudioWidget(
        val id: String,
        val type: String,
        var label: String,
        var gpio: Int,
        var x: Int,
        var y: Int,
        var value: Int = 0,
        var checked: Boolean = false
    )

    internal data class StudioLink(val fromId: String, val toId: String)

    internal var studioWidgets = ArrayList<StudioWidget>()
    internal var studioLinks = ArrayList<StudioLink>()
internal fun renderStudioCanvas() {
        studioCanvas?.setBackgroundColor(Color.BLACK)
        content.removeAllViews()
        content.addView(studioCanvas, LinearLayout.LayoutParams(-1, -1))
        studioCanvas?.setLinks(studioLinks); studioCanvas?.setWidgets(studioWidgets)
    }

    internal fun showStudioWidgetPicker() {
        val options = arrayOf(
            "🔌 Relay — ON / OFF",
            "🔘 Push — tekan & tahan",
            "💡 Slider / PWM Dimmer",
            "⚙️ Atur MQTT Studio"
        )
        AlertDialog.Builder(this)
            .setTitle("Tambah Widget")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showStudioConfig("RELAY_TOGGLE")
                    1 -> showStudioConfig("PUSH_MOMENTARY")
                    2 -> showStudioConfig("PWM_SLIDER")
                    3 -> showStudioMqttConfig()
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    internal fun showStudioConfig(type: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val name = edit(if (type == "RELAY_TOGGLE") "Nama tombol, contoh Lampu Teras" else "Nama widget")
        val gpio = edit("GPIO / Relay, contoh 2")
        gpio.inputType = InputType.TYPE_CLASS_NUMBER
        box.addView(name)
        box.addView(gpio)
        if (type == "PWM_SLIDER") {
            box.addView(subLabel("Nilai PWM 0–255. Geser untuk mengatur kecerahan/kecepatan.", 11f))
        } else if (type == "PUSH_MOMENTARY") {
            box.addView(subLabel("Perintah ON dikirim saat ditekan, OFF saat dilepas.", 11f))
        } else {
            box.addView(subLabel("Tap sekali untuk ON/OFF.", 11f))
        }
        AlertDialog.Builder(this)
            .setTitle(when (type) {
                "RELAY_TOGGLE" -> "Tambah Tombol Relay"
                "PUSH_MOMENTARY" -> "Tambah Tombol Push"
                else -> "Tambah PWM Dimmer"
            })
            .setView(box)
            .setNegativeButton("BATAL", null)
            .setPositiveButton("TAMBAH") { _, _ ->
                val labelText = name.text.toString().trim().ifBlank { "GPIO" }
                val pin = gpio.text.toString().toIntOrNull()?.coerceIn(0, 99) ?: 2
                val widget = StudioWidget(
                    id = "btn_${studioNextId++}",
                    type = type,
                    label = labelText,
                    gpio = pin,
                    x = dp(24),
                    y = dp(24) + studioWidgets.size * dp(18)
                )
                studioWidgets.add(widget)
                saveStudioWidgets()
                studioCanvas?.setWidgets(studioWidgets)
            }
            .show()
    }

    internal fun showStudioMqttConfig() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(4), dp(20), 0)
        }
        val host = edit("Broker host, contoh 192.168.1.10")
        host.setText(prefs.getString("studio_mqtt_host", "") ?: "")
        val port = edit("Port")
        port.setText(prefs.getString("studio_mqtt_port", "1883") ?: "1883")
        port.inputType = InputType.TYPE_CLASS_NUMBER
        val topic = edit("Topic, contoh esp32/gpio")
        topic.setText(prefs.getString("studio_mqtt_topic", "esp32/gpio") ?: "esp32/gpio")
        box.addView(host); box.addView(port); box.addView(topic)
        AlertDialog.Builder(this)
            .setTitle("MQTT Studio")
            .setView(box)
            .setNegativeButton("BATAL", null)
            .setPositiveButton("SIMPAN") { _, _ ->
                prefs.edit()
                    .putString("studio_mqtt_host", host.text.toString().trim())
                    .putString("studio_mqtt_port", port.text.toString().trim())
                    .putString("studio_mqtt_topic", topic.text.toString().trim())
                    .apply()
                toast("Konfigurasi MQTT Studio disimpan")
            }
            .show()
    }

    internal fun saveStudioWidgets() {
        val arr = JSONArray()
        studioWidgets.forEach { w ->
            arr.put(JSONObject().apply {
                put("id", w.id); put("type", w.type); put("label", w.label); put("gpio", w.gpio)
                put("posX", w.x); put("posY", w.y); put("value", w.value); put("checked", w.checked)
            })
        }
        prefs.edit().putString(studioPrefsKey, arr.toString()).apply()
    }

    internal fun saveStudioLinks() {
        val arr = JSONArray()
        studioLinks.forEach { lk ->
            arr.put(JSONObject().apply { put("from", lk.fromId); put("to", lk.toId) })
        }
        prefs.edit().putString(studioLinksPrefsKey, arr.toString()).apply()
    }

    internal fun loadStudioLinks(): ArrayList<StudioLink> {
        val result = ArrayList<StudioLink>()
        val raw = prefs.getString(studioLinksPrefsKey, "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val from = o.optString("from", ""); val to = o.optString("to", "")
                if (from.isNotBlank() && to.isNotBlank()) result.add(StudioLink(from, to))
            }
        }
        return result
    }

    // Tap satu widget untuk memilihnya (menyala), lalu tap widget lain untuk menyambung.
    // Tap widget yang sama lagi untuk membatalkan pilihan.
    internal fun onStudioLinkTap(id: String) {
        val sel = studioSelectedLinkId
        studioSelectedLinkId = when {
            sel == null -> id
            sel == id -> null
            else -> {
                val exists = studioLinks.any { (it.fromId == sel && it.toId == id) || (it.fromId == id && it.toId == sel) }
                if (!exists) { studioLinks.add(StudioLink(sel, id)); saveStudioLinks() }
                null
            }
        }
        studioCanvas?.setLinks(studioLinks)
        studioCanvas?.setWidgets(studioWidgets)
    }

    internal fun loadStudioWidgets(): ArrayList<StudioWidget> {
        val result = ArrayList<StudioWidget>()
        val raw = prefs.getString(studioPrefsKey, "[]") ?: "[]"
        runCatching {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                result.add(StudioWidget(
                    id = o.optString("id", "btn_${i + 1}"),
                    type = o.optString("type", "RELAY_TOGGLE"),
                    label = o.optString("label", "Widget ${i + 1}"),
                    gpio = o.optInt("gpio", 2),
                    x = o.optInt("posX", dp(24)),
                    y = o.optInt("posY", dp(24)),
                    value = o.optInt("value", 0),
                    checked = o.optBoolean("checked", false)
                ))
            }
        }
        studioNextId = result.mapNotNull { it.id.substringAfter("btn_", "").toIntOrNull() }.maxOrNull()?.plus(1) ?: 1
        return result
    }

    internal fun sendStudioCommand(widget: StudioWidget, command: String) {
        val host = prefs.getString("studio_mqtt_host", "")?.trim().orEmpty()
        val port = prefs.getString("studio_mqtt_port", "1883")?.toIntOrNull() ?: 1883
        val topic = prefs.getString("studio_mqtt_topic", "esp32/gpio")?.trim().orEmpty()
        val payload = JSONObject().apply {
            put("device", widget.label)
            put("gpio", widget.gpio)
            put("command", command)
            put("value", widget.value)
        }.toString()
        if (host.isBlank()) {
            toast("Widget ${widget.label}: ${command} • MQTT belum dikonfigurasi")
            return
        }
        thread {
            val result = runCatching {
                mqttPublish(host, port, "MyTools-Studio-${System.currentTimeMillis() % 100000}", topic, payload)
            }.getOrElse { "MQTT gagal: ${it.message}" }
            runOnUiThread { if (result.startsWith("MQTT gagal")) toast(result) }
        }
    }

    internal inner class StudioCanvasView(context: Context) : ViewGroup(context) {
        internal var widgets: List<StudioWidget> = emptyList()
        internal var links: List<StudioLink> = emptyList()
        internal val cardWidth = dp(170)
        internal val cardHeight = dp(92)
        internal val linePaint = Paint().apply {
            color = Color.rgb(120, 120, 128)
            strokeWidth = dp(2).toFloat()
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        // Grid titik dibuat sengaja sangat samar agar canvas tidak terasa polos,
        // tetapi tetap nyaman untuk melihat widget dan garis koneksi.
        internal val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(58, 115, 115, 120)
            style = Paint.Style.FILL
        }
        internal val dotSpacing = dp(34).coerceAtLeast(dp(20))
        internal val dotRadius = 1.35f * resources.displayMetrics.density

        init {
            setWillNotDraw(false)
            setBackgroundColor(Color.BLACK)
        }

        fun setWidgets(list: List<StudioWidget>) {
            widgets = list.toList()
            removeAllViews()
            widgets.forEach { addView(createWidgetView(it)) }
            requestLayout()
            invalidate()
        }

        fun setLinks(list: List<StudioLink>) {
            links = list.toList()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Pola titik gelap-samar seperti canvas desain/ESP Studio.
            // Digambar sebelum link supaya garis koneksi tetap jelas.
            var y = dotSpacing / 2f
            while (y < height) {
                var x = dotSpacing / 2f
                while (x < width) {
                    canvas.drawCircle(x.toFloat(), y.toFloat(), dotRadius, dotPaint)
                    x += dotSpacing
                }
                y += dotSpacing
            }

            links.forEach { lk ->
                val a = widgets.find { it.id == lk.fromId } ?: return@forEach
                val b = widgets.find { it.id == lk.toId } ?: return@forEach
                val ax = a.x + cardWidth.toFloat()
                val ay = a.y + cardHeight / 2f
                val bx = b.x.toFloat()
                val by = b.y + cardHeight / 2f
                canvas.drawLine(ax, ay, bx, by, linePaint)
            }
        }

        internal fun createWidgetView(widget: StudioWidget): View {
            val outer = FrameLayout(context)
            val root = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = bg(Color.rgb(28, 28, 30), 14, Color.rgb(65, 65, 70))
            }
            val title = TextView(context).apply {
                text = widget.label
                textSize = 15f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            val dragRow = FrameLayout(context).apply {
                setPadding(0, 0, 0, 0)
            }
            dragRow.addView(title, FrameLayout.LayoutParams(-1, dp(36)))
            root.addView(dragRow, LinearLayout.LayoutParams(-1, dp(36)))
            // Area geser dibuat lebih besar supaya widget mudah dipindahkan di layar HP.
            // Kontrol ON/OFF, TEKAN, dan slider tetap bisa disentuh normal.
            dragRow.setOnTouchListener(object : View.OnTouchListener {
                var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
                var moved = false
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            downX = event.rawX; downY = event.rawY
                            startX = widget.x; startY = widget.y
                            moved = false
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                            if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) moved = true
                            val maxX = (width - cardWidth).coerceAtLeast(0)
                            val maxY = (height - cardHeight).coerceAtLeast(0)
                            widget.x = (startX + dx).coerceIn(0, maxX)
                            widget.y = (startY + dy).coerceIn(0, maxY)
                            root.x = widget.x.toFloat(); root.y = widget.y.toFloat()
                            invalidate()
                            return true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            saveStudioWidgets()
                            if (!moved) onStudioLinkTap(widget.id)
                            return true
                        }
                    }
                    return true
                }
            })

            when (widget.type) {
                "RELAY_TOGGLE" -> {
                    val toggle = Switch(context).apply {
                        isChecked = widget.checked
                        text = if (widget.checked) "ON" else "OFF"
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        setOnCheckedChangeListener { _, checked ->
                            widget.checked = checked
                            text = if (checked) "ON" else "OFF"
                            sendStudioCommand(widget, if (checked) "ON" else "OFF")
                            saveStudioWidgets()
                        }
                    }
                    root.addView(toggle, LinearLayout.LayoutParams(-1, dp(42)))
                }
                "PUSH_MOMENTARY" -> {
                    val push = TextView(context).apply {
                        text = "TEKAN"
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        background = bg(Color.rgb(55, 55, 58), 10)
                        isClickable = true
                        setOnTouchListener { v, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN -> { sendStudioCommand(widget, "ON"); v.performClick() }
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> sendStudioCommand(widget, "OFF")
                            }
                            true
                        }
                    }
                    root.addView(push, LinearLayout.LayoutParams(-1, dp(38)))
                }
                "PWM_SLIDER" -> {
                    val slider = SeekBar(context).apply {
                        max = 255
                        progress = widget.value.coerceIn(0, 255)
                        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                                widget.value = progress
                                if (fromUser) sendStudioCommand(widget, "PWM:$progress")
                            }
                            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                            override fun onStopTrackingTouch(seekBar: SeekBar?) { saveStudioWidgets() }
                        })
                    }
                    root.addView(slider, LinearLayout.LayoutParams(-1, dp(40)))
                }
            }

            root.setOnLongClickListener {
                showStudioEditDialog(widget)
                true
            }
            outer.addView(root, FrameLayout.LayoutParams(-1, -1))

            // Titik sambung: tap satu widget lalu tap widget lain untuk menghubungkan.
            val selected = studioSelectedLinkId == widget.id
            val linkDot = TextView(context).apply {
                text = "\u2295"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(if (selected) Color.BLACK else Color.WHITE)
                background = bg(if (selected) Color.WHITE else Color.rgb(45, 45, 47), 20, Color.rgb(95, 95, 100))
                setOnClickListener { onStudioLinkTap(widget.id) }
            }
            val dotSize = dp(26)
            val dotLp = FrameLayout.LayoutParams(dotSize, dotSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(2); rightMargin = dp(2)
            }
            outer.addView(linkDot, dotLp)
            return outer
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
            for (i in 0 until childCount) {
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cardWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(cardHeight, MeasureSpec.EXACTLY))
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            for (i in 0 until childCount) {
                val w = widgets.getOrNull(i) ?: continue
                val child = getChildAt(i)
                val x = w.x.coerceIn(0, (width - cardWidth).coerceAtLeast(0))
                val y = w.y.coerceIn(0, (height - cardHeight).coerceAtLeast(0))
                child.layout(x, y, x + cardWidth, y + cardHeight)
            }
        }
    }

    internal fun showStudioEditDialog(widget: StudioWidget) {
        val options = arrayOf("Ubah nama / GPIO", "Hapus widget")
        AlertDialog.Builder(this)
            .setTitle(widget.label)
            .setItems(options) { _, which ->
                if (which == 0) {
                    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(4), dp(20), 0) }
                    val name = edit("Nama"); name.setText(widget.label)
                    val gpio = edit("GPIO"); gpio.inputType = InputType.TYPE_CLASS_NUMBER; gpio.setText(widget.gpio.toString())
                    box.addView(name); box.addView(gpio)
                    AlertDialog.Builder(this).setTitle("Edit Widget").setView(box)
                        .setNegativeButton("BATAL", null)
                        .setPositiveButton("SIMPAN") { _, _ ->
                            widget.label = name.text.toString().trim().ifBlank { widget.label }
                            widget.gpio = gpio.text.toString().toIntOrNull()?.coerceIn(0, 99) ?: widget.gpio
                            saveStudioWidgets(); studioCanvas?.setWidgets(studioWidgets)
                        }.show()
                } else {
                    studioWidgets.removeAll { it.id == widget.id }
                    studioLinks.removeAll { it.fromId == widget.id || it.toId == widget.id }
                    if (studioSelectedLinkId == widget.id) studioSelectedLinkId = null
                    saveStudioWidgets(); saveStudioLinks()
                    studioCanvas?.setLinks(studioLinks); studioCanvas?.setWidgets(studioWidgets)
                }
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    // ---------- ESP LED STUDIO ----------
    // Editor visual LED addressable. Layout selector dibuat ringkas/tersembunyi
    // di dalam kartu dan seluruh pengaturan tetap berada pada satu halaman.
    internal data class LedFrameData(var states: BooleanArray, var durationMs: Long)

    internal var ledCount = 10
    internal var ledLayout = "Grid"
    internal val ledFrames = ArrayList<LedFrameData>()
    internal var ledFrameIndex = 0
    internal var ledCanvas: LedCanvasView? = null
    internal var ledFrameStrip: LinearLayout? = null
    internal var ledFrameInfo: TextView? = null
    internal var ledSpeedInfo: TextView? = null
    internal var ledSpeedSeek: SeekBar? = null
    internal var ledNameEdit: EditText? = null
    internal var ledEndpointEdit: EditText? = null
    internal var ledGapSeek: SeekBar? = null
    internal var ledGapDp = 0
    internal var ledPlaying = false
    internal var ledLayoutLabel: TextView? = null
    internal var ledCountLabel: TextView? = null
    internal val ledPlayHandler = Handler(Looper.getMainLooper())
    internal var ledPlayRunnable: Runnable? = null

    internal fun ledSectionCard(title: String, subtitleText: String, icon: String = "•"): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = bg(panel2, 18, line)
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = icon; textSize = 21f; gravity = Gravity.CENTER; setTextColor(textMain)
            background = bg(panel, 12, line)
        }, LinearLayout.LayoutParams(dp(42), dp(42)).apply { rightMargin = dp(12) })
        box.addView(label(title, 15f, true)); box.addView(subLabel(subtitleText, 11f))
        ledEndpointEdit?.let { box.addView(it) }
        box.addView(button("Upload Pattern") { uploadLedPattern() })
        box.addView(button("Salin JSON Pattern") { copyText(buildLedPatternJson().toString(2)) })
        return box


    }

    internal fun setLedCount(value: Int) {
        val newCount = value.coerceIn(1, 50)
        if (newCount == ledCount) return
        ledCount = newCount
        ledCountLabel?.text = ledCount.toString()
        ledFrames.forEach { frame ->
            val oldStates = frame.states
            frame.states = BooleanArray(ledCount).also { next ->
                for (i in 0 until minOf(oldStates.size, next.size)) next[i] = oldStates[i]
            }
        }
        if (ledFrames.isEmpty()) resetLedFrames()
        ledCanvas?.setLedConfig(ledCount, ledLayout)
        ledCanvas?.setStates(ledFrames.getOrNull(ledFrameIndex)?.states ?: BooleanArray(ledCount))
        updateLedFrameInfo()
        renderLedFrames()
    }

    internal fun showLedLayoutPicker() {
        val values = arrayOf("Grid", "Lingkaran", "Strip", "Spiral")
        val current = values.indexOf(ledLayout).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle("Bentuk / Susunan LED")
            .setSingleChoiceItems(values, current) { dialog, which ->
                ledLayout = values[which]
                ledLayoutLabel?.text = ledLayout
                ledCanvas?.setLedConfig(ledCount, ledLayout)
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    internal fun resetLedFrames() {
        stopLedPlayback()
        ledFrames.clear()
        ledFrames.add(LedFrameData(BooleanArray(ledCount), 300L))
        ledFrameIndex = 0
        ledCanvas?.setLedConfig(ledCount, ledLayout)
        ledCanvas?.setLedGap(ledGapDp)
        ledCanvas?.setStates(ledFrames[0].states)
        ledSpeedSeek?.progress = 250
        ledSpeedInfo?.text = "Durasi frame: 300 ms"
        updateLedFrameInfo()
    }

    internal fun addLedFrame() {
        if (ledFrames.isEmpty()) resetLedFrames()
        val source = ledFrames[ledFrameIndex]
        ledFrames.add(LedFrameData(source.states.copyOf(), source.durationMs))
        ledFrameIndex = ledFrames.lastIndex
        selectLedFrame(ledFrameIndex)
    }

    internal fun duplicateLedFrame() {
        if (ledFrames.isEmpty()) resetLedFrames()
        val source = ledFrames[ledFrameIndex]
        ledFrames.add(ledFrameIndex + 1, LedFrameData(source.states.copyOf(), source.durationMs))
        ledFrameIndex += 1
        selectLedFrame(ledFrameIndex)
    }

    internal fun deleteLedFrame() {
        if (ledFrames.size <= 1) { toast("Minimal harus ada 1 frame"); return }
        ledFrames.removeAt(ledFrameIndex)
        ledFrameIndex = ledFrameIndex.coerceAtMost(ledFrames.lastIndex)
        selectLedFrame(ledFrameIndex)
    }

    internal fun selectLedFrame(index: Int) {
        if (ledFrames.isEmpty()) return
        ledFrameIndex = index.coerceIn(0, ledFrames.lastIndex)
        val frame = ledFrames[ledFrameIndex]
        ledCanvas?.setStates(frame.states)
        ledSpeedSeek?.progress = (frame.durationMs.coerceIn(50L, 2000L) - 50L).toInt()
        ledSpeedInfo?.text = "Durasi frame: ${frame.durationMs} ms"
        updateLedFrameInfo()
        renderLedFrames()
    }

    internal fun updateLedFrameInfo() {
        val frame = ledFrames.getOrNull(ledFrameIndex) ?: return
        val on = frame.states.count { it }
        ledFrameInfo?.text = "Frame ${ledFrameIndex + 1} / ${ledFrames.size} • $on/${ledCount} LED menyala${if (ledPlaying) " • Playing" else ""}"
    }

    internal fun renderLedFrames() {
        val strip = ledFrameStrip ?: return
        strip.removeAllViews()
        ledFrames.forEachIndexed { index, frame ->
            val b = Button(this).apply {
                text = "${index + 1}\n${frame.states.count { it }} ON"
                textSize = 10f
                setTextColor(textMain)
                background = bg(if (index == ledFrameIndex) panel else panel2, 12, if (index == ledFrameIndex) textMain else line)
                setOnClickListener { selectLedFrame(index) }
                setStateListAnimator(null)
            }
            strip.addView(b, LinearLayout.LayoutParams(dp(78), dp(54)).apply { rightMargin = dp(5) })
        }
        ledCanvas?.setStates(ledFrames.getOrNull(ledFrameIndex)?.states ?: BooleanArray(ledCount))
        updateLedFrameInfo()
    }

    internal fun playLedAnimation() {
        if (ledFrames.isEmpty()) return
        stopLedPlayback()
        ledPlaying = true
        var index = ledFrameIndex
        val run = object : Runnable {
            override fun run() {
                if (!ledPlaying || ledFrames.isEmpty()) return
                index %= ledFrames.size
                ledFrameIndex = index
                val frame = ledFrames[index]
                ledCanvas?.setStates(frame.states)
                ledSpeedSeek?.progress = (frame.durationMs.coerceIn(50L, 2000L) - 50L).toInt()
                ledSpeedInfo?.text = "Durasi frame: ${frame.durationMs} ms"
                renderLedFrames()
                index++
                ledPlayHandler.postDelayed(this, frame.durationMs.coerceIn(50L, 10000L))
            }
        }
        ledPlayRunnable = run
        ledPlayHandler.post(run)
    }

    internal fun stopLedPlayback() {
        ledPlaying = false
        ledPlayRunnable?.let { ledPlayHandler.removeCallbacks(it) }
        ledPlayRunnable = null
    }

    internal fun buildLedPatternJson(name: String? = null): JSONObject {
        val root = JSONObject()
        root.put("type", "mytools_esp_led_pattern")
        root.put("version", 2)
        root.put("name", name ?: ledNameEdit?.text?.toString()?.trim().orEmpty().ifBlank { "Untitled" })
        root.put("led_count", ledCount)
        root.put("layout", ledLayout)
        root.put("gap_dp", ledGapDp)
        val framesJson = JSONArray()
        ledFrames.forEachIndexed { index, frame ->
            val f = JSONObject()
            f.put("frame", index + 1)
            f.put("duration_ms", frame.durationMs)
            val states = JSONArray()
            frame.states.forEach { states.put(if (it) 1 else 0) }
            f.put("leds", states)
            framesJson.put(f)
        }
        root.put("frames", framesJson)
        root.put("loop", true)
        return root
    }

    internal fun saveLedPattern() {
        val name = ledNameEdit?.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { toast("Masukkan nama pattern"); return }
        val saved = runCatching { JSONArray(prefs.getString("led_patterns", "[]") ?: "[]") }.getOrElse { JSONArray() }
        val item = buildLedPatternJson(name).apply { put("saved_at", System.currentTimeMillis()) }
        val next = JSONArray(); next.put(item)
        for (i in 0 until saved.length()) {
            val old = saved.optJSONObject(i) ?: continue
            if (!old.optString("name").equals(name, true)) next.put(old)
        }
        while (next.length() > 30) next.remove(next.length() - 1)
        prefs.edit().putString("led_patterns", next.toString()).apply()
        toast("Pattern \"$name\" disimpan")
        renderSavedLedPatterns()
    }

    internal fun renderSavedLedPatterns() {
        val marker = content.findViewWithTag<View>("led_saved_container")
        if (marker != null) (marker.parent as? ViewGroup)?.removeView(marker)
        val box = LinearLayout(this).apply { tag = "led_saved_container"; orientation = LinearLayout.VERTICAL }
        val saved = runCatching { JSONArray(prefs.getString("led_patterns", "[]") ?: "[]") }.getOrElse { JSONArray() }
        if (saved.length() == 0) {
            box.addView(subLabel("Belum ada pattern tersimpan.", 12f))
        } else {
            for (i in 0 until saved.length()) {
                val obj = saved.optJSONObject(i) ?: continue
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(6), dp(8)); background = bg(panel2, 14, line) }
                val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val count = obj.optInt("led_count", 0)
                info.addView(label(obj.optString("name", "Pattern"), 14f, true))
                info.addView(subLabel("$count LED • ${obj.optString("layout", "Grid")}", 11f))
                row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
                row.addView(Button(this).apply {
                    text = "LOAD"; setTextColor(textMain); background = bg(panel, 10, line); setStateListAnimator(null); setOnClickListener { loadLedPattern(obj) }
                }, LinearLayout.LayoutParams(dp(78), dp(44)).apply { rightMargin = dp(4) })
                row.addView(Button(this).apply {
                    text = "×"; textSize = 18f; setTextColor(textMain); background = bg(panel, 10, line); setStateListAnimator(null); setOnClickListener { deleteLedPattern(obj.optString("name")) }
                }, LinearLayout.LayoutParams(dp(48), dp(44)))
                box.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
            }
        }
        val uploadIndex = findContentChildIndexByTag("led_upload_title")
        if (uploadIndex >= 0) content.addView(box, uploadIndex) else content.addView(box)
    }

    internal fun findContentChildIndexByTag(tagValue: String): Int {
        for (i in 0 until content.childCount) if (content.getChildAt(i).tag == tagValue) return i
        return -1
    }

    internal fun deleteLedPattern(name: String) {
        val saved = runCatching { JSONArray(prefs.getString("led_patterns", "[]") ?: "[]") }.getOrElse { JSONArray() }
        val next = JSONArray()
        for (i in 0 until saved.length()) {
            val obj = saved.optJSONObject(i) ?: continue
            if (!obj.optString("name").equals(name, true)) next.put(obj)
        }
        prefs.edit().putString("led_patterns", next.toString()).apply()
        toast("Pattern dihapus")
        renderSavedLedPatterns()
    }

    internal fun loadLedPattern(obj: JSONObject) {
        stopLedPlayback()
        ledCount = obj.optInt("led_count", 10).coerceIn(1, 50)
        ledLayout = when (obj.optString("layout", "Grid")) {
            "Kotak" -> "Grid"
            else -> obj.optString("layout", "Grid")
        }.let { if (it in arrayOf("Grid", "Lingkaran", "Strip", "Spiral")) it else "Grid" }
        ledGapDp = obj.optInt("gap_dp", 0).coerceIn(0, 20)
        ledFrames.clear()
        val frames = obj.optJSONArray("frames")
        if (frames != null) for (i in 0 until frames.length()) {
            val f = frames.optJSONObject(i) ?: continue
            val arr = f.optJSONArray("leds")
            val states = BooleanArray(ledCount)
            if (arr != null) for (j in 0 until minOf(ledCount, arr.length())) states[j] = arr.optInt(j, 0) != 0
            ledFrames.add(LedFrameData(states, f.optLong("duration_ms", 300L).coerceIn(50L, 10000L)))
        }
        if (ledFrames.isEmpty()) ledFrames.add(LedFrameData(BooleanArray(ledCount), 300L))
        ledFrameIndex = 0
        ledNameEdit?.setText(obj.optString("name", "Pattern"))
        ledCountLabel?.text = ledCount.toString()
        ledLayoutLabel?.text = ledLayout
        ledCanvas?.setLedConfig(ledCount, ledLayout)
        ledCanvas?.setLedGap(ledGapDp)
        ledGapSeek?.progress = ledGapDp
        renderLedFrames()
        toast("Pattern dimuat")
    }

    internal fun uploadLedPattern() {
        if (ledFrames.isEmpty()) { toast("Belum ada frame"); return }
        val endpoint = ledEndpointEdit?.text?.toString()?.trim().orEmpty()
        if (endpoint.isBlank()) { toast("Masukkan URL endpoint ESP"); return }
        runCatching {
            val uri = Uri.parse(endpoint)
            if (uri.scheme != "http" && uri.scheme != "https") error("URL harus http:// atau https://")
        }.onFailure { toast(it.message ?: "URL tidak valid"); return }
        prefs.edit().putString("led_endpoint", endpoint).apply()
        val json = buildLedPatternJson()
        toast("Mengirim pattern ke ESP…")
        thread {
            val result = runCatching {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 7000; readTimeout = 7000; doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8"); setRequestProperty("Accept", "application/json")
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(StandardCharsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }?.take(500).orEmpty()
                conn.disconnect()
                if (code !in 200..299) error("HTTP $code ${body.ifBlank { "ESP menolak request" }}")
                "Berhasil • HTTP $code${if (body.isBlank()) "" else "\nESP: $body"}"
            }.getOrElse { "Upload gagal: ${it.message ?: it.javaClass.simpleName}" }
            runOnUiThread {
                if (result.startsWith("Berhasil")) toast(result) else AlertDialog.Builder(this).setTitle("Upload ESP").setMessage(result).setPositiveButton("OK", null).show()
            }
        }
    }

    internal inner class LedCanvasView(context: Context) : View(context) {
        internal var count = 10
        internal var layoutMode = "Grid"
        internal var states = BooleanArray(count)
        internal val positions = ArrayList<android.graphics.PointF>()
        internal var gapDp = 0
        internal val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        internal val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        var onLedClicked: ((Int) -> Unit)? = null

        init { setLayerType(View.LAYER_TYPE_SOFTWARE, null); isClickable = true }

        fun setLedConfig(newCount: Int, newLayout: String) {
            count = newCount.coerceIn(1, 50)
            layoutMode = when (newLayout) { "Kotak" -> "Grid"; "Lingkaran", "Strip", "Spiral" -> newLayout; else -> "Grid" }
            if (states.size != count) {
                val next = BooleanArray(count)
                for (i in 0 until minOf(states.size, count)) next[i] = states[i]
                states = next
            }
            recalcPositions(width, height); invalidate()
        }

        fun setStates(newStates: BooleanArray) { states = newStates.copyOf(count); invalidate() }
        fun setLedGap(gap: Int) { gapDp = gap.coerceIn(0, 20); recalcPositions(width, height); invalidate() }
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { recalcPositions(w, h) }

        internal fun recalcPositions(w: Int, h: Int) {
            positions.clear()
            if (w <= 0 || h <= 0) return
            val cx = w / 2f; val cy = h / 2f
            val margin = dp(12).toFloat(); val extra = dp(gapDp).toFloat()
            when (layoutMode) {
                "Grid" -> {
                    val cols = min(10, Math.ceil(Math.sqrt(count.toDouble())).toInt().coerceAtLeast(1))
                    val rows = Math.ceil(count.toDouble() / cols).toInt().coerceAtLeast(1)
                    val stepX = ((w - margin * 2f - extra * (cols - 1)) / cols.coerceAtLeast(1)).coerceAtLeast(dp(26).toFloat())
                    val stepY = ((h - margin * 2f - extra * (rows - 1)) / rows.coerceAtLeast(1)).coerceAtLeast(dp(26).toFloat())
                    val totalW = (cols - 1) * (stepX + extra); val totalH = (rows - 1) * (stepY + extra)
                    val sx = cx - totalW / 2f; val sy = cy - totalH / 2f
                    for (i in 0 until count) {
                        val row = i / cols; val col = i % cols
                        positions.add(android.graphics.PointF(sx + col * (stepX + extra), sy + row * (stepY + extra)))
                    }
                }
                "Lingkaran" -> {
                    val r = (min(w, h) / 2f - dp(34)).coerceAtLeast(dp(24).toFloat())
                    if (count == 1) positions.add(android.graphics.PointF(cx, cy)) else for (i in 0 until count) {
                        val a = -Math.PI / 2 + i * (2 * Math.PI / count)
                        positions.add(android.graphics.PointF(cx + Math.cos(a).toFloat() * r, cy + Math.sin(a).toFloat() * r))
                    }
                }
                "Strip" -> {
                    val step = ((w - margin * 2f - extra * (count - 1)) / count.coerceAtLeast(1)).coerceAtLeast(dp(26).toFloat())
                    val total = (count - 1) * (step + extra)
                    val sx = cx - total / 2f
                    for (i in 0 until count) positions.add(android.graphics.PointF(sx + i * (step + extra), cy))
                }
                "Spiral" -> {
                    val maxR = (min(w, h) / 2f - dp(24)).coerceAtLeast(dp(20).toFloat())
                    for (i in 0 until count) {
                        val t = if (count <= 1) 0f else i.toFloat() / (count - 1).toFloat()
                        val r = maxR * t
                        val a = -Math.PI / 2 + i * (Math.PI * 2.2 / 10.0)
                        positions.add(android.graphics.PointF(cx + Math.cos(a).toFloat() * r, cy + Math.sin(a).toFloat() * r))
                    }
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(panel)
            if (positions.size != count) recalcPositions(width, height)
            val radius = (min(width, height) * 0.04f).coerceIn(dp(10).toFloat(), dp(17).toFloat())
            positions.forEachIndexed { index, p ->
                val on = states.getOrNull(index) == true
                if (on) {
                    glowPaint.color = Color.rgb(120, 120, 120)
                    glowPaint.setShadowLayer(radius * 0.9f, 0f, 0f, Color.argb(150, 52, 132, 255))
                    if (layoutMode == "Grid") canvas.drawRoundRect(p.x-radius, p.y-radius, p.x+radius, p.y+radius, radius*.25f, radius*.25f, glowPaint)
                    else canvas.drawCircle(p.x, p.y, radius*1.05f, glowPaint)
                    glowPaint.clearShadowLayer()
                    paint.color = Color.rgb(170, 170, 170)
                } else paint.color = Color.rgb(65, 70, 78)
                if (layoutMode == "Grid") canvas.drawRoundRect(p.x-radius*.82f, p.y-radius*.82f, p.x+radius*.82f, p.y+radius*.82f, radius*.22f, radius*.22f, paint)
                else canvas.drawCircle(p.x, p.y, radius, paint)
                paint.color = if (on) Color.WHITE else Color.rgb(165, 170, 178)
                paint.textSize = dp(8).toFloat(); paint.textAlign = Paint.Align.CENTER
                canvas.drawText((index + 1).toString(), p.x, p.y + dp(3), paint)
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (positions.size != count) recalcPositions(width, height)
            if (event.action == MotionEvent.ACTION_DOWN) {
                var nearest = -1; var dist = Float.MAX_VALUE
                val hit = (min(width, height) * .04f).coerceIn(dp(12).toFloat(), dp(20).toFloat()) * 2f
                positions.forEachIndexed { i, p ->
                    val dx = event.x-p.x; val dy = event.y-p.y; val d = Math.sqrt((dx*dx+dy*dy).toDouble()).toFloat()
                    if (d <= hit && d < dist) { nearest = i; dist = d }
                }
                if (nearest >= 0) { states[nearest] = !states[nearest]; onLedClicked?.invoke(nearest); invalidate() }
            }
            return true

        }
    }
    internal fun espLedResistorCalculator() {
        clearPage("LED Resistor")
        content.addView(label("LED Resistor Calculator", 22f, true))
        content.addView(subLabel("R = (Vsupply − Vled) / Iled", 12f))
        val vs = calcDisplay("Tegangan supply, contoh 3.3")
        val vf = calcDisplay("Forward voltage LED, contoh 2.0")
        val ma = calcDisplay("Arus LED (mA), contoh 10")
        content.addView(vs); content.addView(vf); content.addView(ma)
        content.addView(button("Hitung Resistor") {
            val supply = vs.numberValue()
            val led = vf.numberValue()
            val currentMa = ma.numberValue()
            if (supply == null || led == null || currentMa == null || currentMa <= 0.0) {
                toast("Masukkan angka yang valid")
            } else {
                val r = (supply - led) / (currentMa / 1000.0)
                if (r <= 0.0) output("VLED harus lebih kecil dari Vsupply")
                else output("Resistor ideal ≈ ${"%.1f".format(Locale.US, r)} Ω\nNilai praktis terdekat: ${preferredResistor(r)} Ω")
            }
        })
    }

    internal fun espAdcCalculator() {
        clearPage("ESP ADC")
        content.addView(label("ADC → Voltage", 22f, true))
        content.addView(subLabel("V = ADC / (2^bits − 1) × Vref", 12f))
        val adc = calcDisplay("Nilai ADC")
        val bits = calcDisplay("Resolusi bit, contoh 12")
        val vref = calcDisplay("Vref, contoh 3.3")
        content.addView(adc); content.addView(bits); content.addView(vref)
        content.addView(button("Hitung Tegangan") {
            val a = adc.numberValue(); val b = bits.numberValue(); val v = vref.numberValue()
            if (a == null || b == null || v == null || b <= 0.0) toast("Masukkan angka yang valid")
            else {
                val max = Math.pow(2.0, b) - 1.0
                output("Tegangan ≈ ${"%.4f".format(Locale.US, a / max * v)} V")
            }
        })
    }

    internal fun EditText.numberValue(): Double? = text.toString().trim().replace(',', '.').toDoubleOrNull()
    internal fun EditText.num(): Double? = numberValue()

    internal fun preferredResistor(value: Double): Int {
        val e24 = doubleArrayOf(10.0, 11.0, 12.0, 13.0, 15.0, 16.0, 18.0, 20.0, 22.0, 24.0, 27.0, 30.0, 33.0, 36.0, 39.0, 43.0, 47.0, 51.0, 56.0, 62.0, 68.0, 75.0, 82.0, 91.0)
        if (value <= 0.0) return 0
        return e24.minByOrNull { kotlin.math.abs(it * 10.0.pow(kotlin.math.floor(kotlin.math.log10(value)))) - value }?.let { it * 10.0.pow(kotlin.math.floor(kotlin.math.log10(value))) }?.roundToInt() ?: value.roundToInt()
    }







    internal fun clipboardText(): String? {
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        if (!cm.hasPrimaryClip()) return null
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
    }

    internal fun saveClipboard(value: String) {
        val clean = value.trim()
        if (clean.isEmpty()) return
        val arr = runCatching { JSONArray(prefs.getString("clipboard_history", "[]") ?: "[]") }.getOrElse { JSONArray() }
        val next = JSONArray()
        next.put(clean.take(10000))
        for (i in 0 until arr.length()) {
            val old = arr.optString(i)
            if (old.isNotBlank() && old != clean && next.length() < 50) next.put(old)
        }
        prefs.edit().putString("clipboard_history", next.toString()).apply()
    }

    internal fun stopClipboardMonitor() {
        val cm = clipboardManager
        val listener = clipboardListener
        if (cm != null && listener != null) runCatching { cm.removePrimaryClipChangedListener(listener) }
        clipboardListener = null
        clipboardManager = null
    }

    internal fun stopEspDiscovery() {
        val manager = nsdDiscoveryManager
        val listener = nsdDiscoveryListener
        if (manager != null && listener != null) runCatching { manager.stopServiceDiscovery(listener) }
        nsdDiscoveryListener = null
        nsdDiscoveryManager = null
    }

    internal fun startClipboardMonitor() {
        stopClipboardMonitor()
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val listener = android.content.ClipboardManager.OnPrimaryClipChangedListener {
            runOnUiThread { clipboardText()?.let { saveClipboard(it) } }
        }
        clipboardManager = cm
        clipboardListener = listener
        cm.addPrimaryClipChangedListener(listener)
    }

    internal var pendingOcrView: EditText? = null
    internal var pendingOcrPreview: ImageView? = null
    internal var pendingOcrUri: Uri? = null

    internal fun runOcr(uri: Uri, onResult: (String) -> Unit) {
        thread {
            runCatching {
                val image = InputImage.fromFilePath(this, uri)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                recognizer.process(image).addOnSuccessListener { result -> onResult(result.text) }.addOnFailureListener { onResult("") }
            }.onFailure { onResult("") }
        }
    }




    internal fun convertUnits(x: Double, cat: Int, a: Int, b: Int): Double {
        if (a == b) return x
        return when(cat) {
            0 -> { val f = doubleArrayOf(1.0,1000.0,0.01,0.001,0.0254,0.3048,0.9144,1609.344); x*f[a]/f[b] }
            1 -> { val f = doubleArrayOf(0.001,1.0,0.000001,0.45359237,0.028349523125); x*f[a]/f[b] }
            2 -> { val c = when(a) {0->x;1->(x-32)*5/9;else->x-273.15}; when(b){0->c;1->c*9/5+32;else->c+273.15} }
            3 -> { val f=doubleArrayOf(1.0,1e6,1e-4,0.09290304,4046.8564224); x*f[a]/f[b] }
            4 -> { val f=doubleArrayOf(1.0,0.001,1000.0,0.001,3.785411784); x*f[a]/f[b] }
            5 -> { val f=doubleArrayOf(1.0,60.0,3600.0,86400.0); x*f[a]/f[b] }
            6 -> { val f=doubleArrayOf(1.0,0.2777777778,0.44704,0.0); x*f[a]/f[b] }
            else -> x
        }
    }




    internal var pendingApkUri: Uri? = null
    internal var apkCompareFirstUri: Uri? = null
    internal var wsSocket: Socket? = null
    internal var espSensorPollingHandler: Handler? = null
    internal var espSensorPollingRunnable: Runnable? = null
    internal var wsInput: InputStream? = null
    internal var wsOutput: OutputStream? = null
    internal var pendingApkOutput: TextView? = null

    internal fun analyzeApk(uri: Uri) {
        pendingApkUri = uri
        val box = label("Menganalisis...", 13f)
        pendingApkOutput = box
        content.addView(box)
        thread {
            val result = runCatching { buildApkReport(uri) }.getOrElse { "APK Analyzer error: ${it.message}" }
            runOnUiThread { box.text = result }
        }
    }

    internal fun buildApkReport(uri: Uri): String {
        val temp = File(cacheDir, "analyzer_${System.currentTimeMillis()}.apk")
        val maxApkBytes = 100L * 1024L * 1024L
        val afdLength = contentResolver.openAssetFileDescriptor(uri, "r")?.length ?: -1L
        if (afdLength > maxApkBytes) error("APK terlalu besar. Batas analisis adalah 100 MB.")
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > maxApkBytes) error("APK terlalu besar. Batas analisis adalah 100 MB.")
                    output.write(buffer, 0, n)
                }
            }
        } ?: error("Tidak bisa membaca APK")
        val pm = packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(temp.absolutePath, flags)
        val sb = StringBuilder()
        sb.append("=== PACKAGE ===\n")
        sb.append("File: ${queryName(uri) ?: temp.name}\nSize: ${bytesText(temp.length())}\n")
        if (info != null) {
            sb.append("Package: ${info.packageName}\nVersion: ${info.versionName} (${info.versionCode})\n")
            val appInfo = info.applicationInfo
            if (appInfo != null) {
                if (Build.VERSION.SDK_INT >= 24) sb.append("Min SDK: ${appInfo.minSdkVersion}\nTarget SDK: ${appInfo.targetSdkVersion}\n")
                sb.append("Label: ${pm.getApplicationLabel(appInfo)}\n")
            }
            info.requestedPermissions?.let { p -> sb.append("Permissions (${p.size}):\n"); p.forEach { sb.append("  • $it\n") } }
            info.activities?.let { a -> sb.append("Activities: ${a.size}\n"); a.forEach { sb.append("  • ${it.name}\n") } }
            info.services?.let { a -> sb.append("Services: ${a.size}\n"); a.forEach { sb.append("  • ${it.name}\n") } }
            info.receivers?.let { a -> sb.append("Receivers: ${a.size}\n"); a.forEach { sb.append("  • ${it.name}\n") } }
            info.providers?.let { a -> sb.append("Providers: ${a.size}\n"); a.forEach { sb.append("  • ${it.authority}\n") } }
            sb.append("\n=== SIGNATURE ===\n")
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            signatures?.forEachIndexed { index, sig ->
                val digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                sb.append("Signer ${index + 1} SHA-256: ${digest.joinToString(":") { "%02X".format(it) }}\n")
            }
        } else sb.append("PackageManager tidak dapat membaca manifest APK.\n")
        sb.append("\n=== ZIP / DEX / NATIVE ===\n")
        ZipFile(temp).use { zip ->
            var files = 0; var totalUncompressed = 0L; var dex = 0; var native = 0; var resources = false; var manifest = false
            val top = mutableListOf<String>()
            val en = zip.entries()
            while (en.hasMoreElements()) {
                val e = en.nextElement(); if (e.isDirectory) continue
                files++; totalUncompressed += e.size.coerceAtLeast(0)
                if (e.name.endsWith(".dex")) dex++
                if (e.name.startsWith("lib/") && e.name.endsWith(".so")) native++
                if (e.name == "resources.arsc") resources = true
                if (e.name == "AndroidManifest.xml") manifest = true
                if (top.size < 80) top.add("${e.name}  ${bytesText(e.size.coerceAtLeast(0))}")
            }
            sb.append("Files: $files\nUncompressed: ${bytesText(totalUncompressed)}\nDEX: $dex\nNative: $native\nresources.arsc: $resources\nManifest: $manifest\n")
            top.forEach { sb.append("  ").append(it).append("\n") }
        }
        return sb.toString()
    }

    internal fun parseCidr24(cidr: String): Pair<String, Int>? {
        val parts = cidr.split('/')
        if (parts.size != 2 || parts[1] != "24") return null
        val oct = parts[0].split('.').mapNotNull { it.toIntOrNull() }
        if (oct.size != 4 || oct.any { it !in 0..255 }) return null
        return "${oct[0]}.${oct[1]}.${oct[2]}" to 24
    }

    // ---------- NATIVE DEVICE / STORAGE / APP / NETWORK TOOLS ----------

    internal fun infoRow(name: String, value: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = bg(panel2, 14)
        }
        card.addView(label(name, 12f, true))
        card.addView(label(value.ifBlank { "Tidak tersedia" }, 14f))
        content.addView(card, LinearLayout.LayoutParams(-1, -2))











    }
    internal fun findFiles(dir: File, term: String, out: MutableList<File>, limit: Int) {
        if (out.size >= limit) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (f in children) {
            if (out.size >= limit) return
            if (f.name.contains(term, ignoreCase = true)) out.add(f)
            if (f.isDirectory) findFiles(f, term, out, limit)
        }
    }

    internal fun fileManagerCard(f: File, parent: File): View {
        val isDir = f.isDirectory
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(6), dp(8)); background = bg(panel2, 16, line)
            isClickable = true; isFocusable = true; contentDescription = if (isDir) "Folder ${f.name}" else "File ${f.name}"
        }
        val icon = MdiIconView(this).apply {
            setIconName(if (isDir) "folder-outline" else fileIconForExtension(f.extension)); setIconSize(25f); setTextColor(textMain)
            background = bg(panel, 13, line); setPadding(dp(9), dp(9), dp(9), dp(9))
        }
        card.addView(icon, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(10) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(label(f.name, 14f, true))
        info.addView(subLabel(if (isDir) "Folder" else "${bytesText(f.length())}  •  ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(f.lastModified()))}", 10f))
        card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
        val more = TextView(this).apply { text = "⋮"; textSize = 22f; gravity = Gravity.CENTER; setTextColor(textMuted); contentDescription = "Aksi ${f.name}"; isClickable = true; isFocusable = true; setPadding(dp(8), 0, dp(8), 0); setOnClickListener { showFileActions(f, parent) } }
        card.addView(more, LinearLayout.LayoutParams(dp(42), dp(48)))
        card.setOnClickListener { if (isDir) fileManager(f) else showFileActions(f, parent) }
        return card.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(7) } }
    }

    internal fun fileIconForExtension(ext: String): String = when (ext.lowercase(Locale.getDefault())) {
        "kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "yaml", "yml" -> "code-tags"
        "png", "jpg", "jpeg", "webp", "gif" -> "file-image-outline"
        "mp3", "wav", "ogg" -> "file-music-outline"
        "mp4", "mkv", "webm" -> "file-video-outline"
        "zip", "rar", "7z" -> "zip-box-outline"
        "pdf" -> "file-pdf-box"
        "txt", "md" -> "file-document-outline"
        else -> "file-outline"
    }

    internal var fileSortMode: Int
        get() = prefs.getInt("file_sort_mode", 0)
        set(value) { prefs.edit().putInt("file_sort_mode", value.coerceIn(0, 4)).apply() }
    internal var fileFilterText: String = ""
    internal fun recordRecentFile(file: File) {
        val key = "recent_files_v1"
        val current = runCatching { JSONArray(prefs.getString(key, "[]") ?: "[]") }.getOrElse { JSONArray() }
        val path = file.absolutePath
        val next = JSONArray()
        next.put(path)
        for (i in 0 until current.length()) {
            val p = current.optString(i)
            if (p.isNotBlank() && p != path && File(p).exists() && next.length() < 20) next.put(p)
        }
        prefs.edit().putString(key, next.toString()).apply()
    }

    internal fun sortFiles(files: List<File>): List<File> = when (fileSortMode) {
        1 -> files.sortedBy { it.name.lowercase(Locale.getDefault()) }
        2 -> files.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
        3 -> files.sortedByDescending { it.lastModified() }
        4 -> files.sortedByDescending { it.length() }
        else -> files.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase(Locale.getDefault()) })
    }

    internal fun showFileSortDialog(dir: File) {
        val items = arrayOf("Folder dulu + nama", "Nama A–Z", "Nama Z–A", "Terbaru diubah", "Ukuran terbesar")
        AlertDialog.Builder(this).setTitle("Urutkan file").setSingleChoiceItems(items, fileSortMode) { d, which -> fileSortMode = which; d.dismiss(); fileManager(dir) }.show()
    }

    internal fun showFileActions(f: File, parent: File) {
        val actions = if (f.isDirectory) arrayOf("Buka", "Ganti nama", "Bagikan", "Hapus") else arrayOf("Buka Editor", "Ganti nama", "Bagikan", "Hapus", "Detail")
        AlertDialog.Builder(this).setTitle(f.name).setItems(actions) { _, which ->
            when (actions[which]) {
                "Buka" -> fileManager(f)
                "Buka Editor" -> { recordRecentFile(f); editor(f) }
                "Ganti nama" -> renameManagedFile(f, parent)
                "Bagikan" -> shareFile(f)
                "Hapus" -> confirmDeleteFile(f, parent)
                "Detail" -> showFileDetail(f)
            }
        }.show()
    }

    internal fun renameManagedFile(file: File, parent: File) {
        val e = edit("Nama baru").apply { setText(file.name) }
        AlertDialog.Builder(this).setTitle("Ganti nama").setView(e).setNegativeButton("Batal", null).setPositiveButton("Simpan") { _, _ ->
            val target = safeChildFile(parent, e.text.toString())
            if (target == null || target.exists() || !file.renameTo(target)) toast("Gagal mengganti nama") else { recordRecentFile(target); fileManager(parent) }
        }.show()
    }

    internal fun showFileDetail(file: File) {
        val type = if (file.isDirectory) "Folder" else MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.getDefault())) ?: "File"
        AlertDialog.Builder(this).setTitle(file.name).setMessage("Tipe: $type\nUkuran: ${bytesText(file.length())}\nLokasi: ${file.absolutePath}\nDiubah: ${SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))}").setPositiveButton("OK", null).show()
    }

    internal fun showMultiSelectDialog(dir: File) {
        val files = sortFiles(dir.listFiles()?.filter { fileFilterText.isBlank() || it.name.contains(fileFilterText, true) } ?: emptyList())
        if (files.isEmpty()) { toast("Tidak ada item"); return }
        val checked = BooleanArray(files.size)
        AlertDialog.Builder(this).setTitle("Pilih banyak item").setMultiChoiceItems(files.map { it.name }.toTypedArray(), checked) { _, which, value -> checked[which] = value }
            .setNegativeButton("Batal", null).setPositiveButton("Aksi") { _, _ ->
                val selected = files.indices.filter { checked[it] }.map { files[it] }
                if (selected.isEmpty()) { toast("Belum ada item dipilih"); return@setPositiveButton }
                AlertDialog.Builder(this).setTitle("${selected.size} item dipilih").setItems(arrayOf("Hapus semua", "Bagikan file")) { _, action ->
                    when (action) {
                        0 -> AlertDialog.Builder(this).setTitle("Hapus ${selected.size} item?").setMessage("Operasi ini tidak dapat dibatalkan.").setNegativeButton("Batal", null).setPositiveButton("Hapus") { _, _ -> batchDeleteFiles(selected, dir) }.show()
                        1 -> selected.firstOrNull()?.let { shareFile(it) }
                    }
                }.show()
            }.show()
    }

    internal fun batchDeleteFiles(files: List<File>, parent: File) {
        val dialog = ProgressDialog(this).apply { setTitle("Menghapus..."); setProgressStyle(ProgressDialog.STYLE_HORIZONTAL); max = files.size; progress = 0; setCancelable(true); show() }
        thread {
            var done = 0
            files.forEach { f -> if (dialog.isShowing) runCatching { deleteRecursivelySafe(f) }; done++; runOnUiThread { dialog.progress = done } }
            runOnUiThread { dialog.dismiss(); toast("Selesai: $done/${files.size}"); fileManager(parent) }
        }
    }

    internal fun deleteRecursivelySafe(file: File): Boolean {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursivelySafe(it) }
        return !file.exists() || file.delete()
    }




    internal fun createAppBackup() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/zip"; putExtra(Intent.EXTRA_TITLE, "mytools_backup_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.zip") }
        startActivityForResult(i, 3025)
    }

    internal fun restoreAppBackup() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE) }, 3026)
    }

    internal fun writeAppBackup(uri: Uri) {
        val root = JSONObject().apply {
            put("format", "mytools-app-backup")
            put("version", 1)
            put("createdAt", System.currentTimeMillis())
            put("appVersion", "2.25.0")
            val settings = JSONObject()
            prefs.all.forEach { (k, v) ->
                when (v) {
                    is Boolean -> settings.put(k, v)
                    is Int -> settings.put(k, v)
                    is Long -> settings.put(k, v)
                    is Float -> settings.put(k, v)
                    is String -> settings.put(k, v)
                    is Set<*> -> settings.put(k, JSONArray(v.toList()))
                }
            }
            put("preferences", settings)
        }
        contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->
                val bytes = root.toString(2).toByteArray(StandardCharsets.UTF_8)
                zip.putNextEntry(ZipEntry("backup.json")); zip.write(bytes); zip.closeEntry()
            }
        } ?: error("Tidak bisa menulis file backup")
    }

    internal fun readAppBackup(uri: Uri) {
        var root: JSONObject? = null
        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == "backup.json") {
                        root = JSONObject(zip.readBytes().toString(StandardCharsets.UTF_8)); break
                    }
                }
            }
        } ?: error("Tidak bisa membaca backup")
        val data = root ?: error("backup.json tidak ditemukan")
        if (data.optString("format") != "mytools-app-backup") error("Format backup tidak dikenali")
        val settings = data.optJSONObject("preferences") ?: JSONObject()
        val editor = prefs.edit().clear()
        val keys = settings.keys()
        while (keys.hasNext()) {
            val k = keys.next(); val v = settings.get(k)
            when (v) {
                is Boolean -> editor.putBoolean(k, v)
                is Int -> editor.putInt(k, v)
                is Long -> editor.putLong(k, v)
                is Double -> editor.putFloat(k, v.toFloat())
                is String -> editor.putString(k, v)
                is JSONArray -> { val set = mutableSetOf<String>(); for (i in 0 until v.length()) set.add(v.optString(i)); editor.putStringSet(k, set) }
            }
        }
        if (!editor.commit()) error("Gagal menyimpan hasil restore")
    }

    internal fun confirmDeleteFile(file: File, parent: File) {
        AlertDialog.Builder(this)
            .setTitle("Hapus file?")
            .setMessage(file.name)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Hapus") { _, _ ->
                val ok = runCatching { file.delete() }.getOrDefault(false)
                if (ok) { toast("File dihapus"); fileManager(parent) } else toast("Gagal menghapus file")
            }.show()
    }

    internal fun safeChildFile(parent: File, name: String): File? {
        val clean = name.trim()
        if (clean.isEmpty() || clean.contains('\\') || clean.contains('/') || clean == "." || clean == "..") return null
        val root = filesDir.canonicalFile
        val base = parent.canonicalFile
        if (!base.path.startsWith(root.path + File.separator) && base != root) return null
        val target = File(base, clean).canonicalFile
        return if (target.path.startsWith(root.path + File.separator) || target == root) target else null
    }

    internal fun pickFileForEditor() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(i, 1001)
    }

    internal fun safeFileName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\x00-\x1F]"""), "_").trim()
        return cleaned.take(120).ifEmpty { "untitled.txt" }
    }

    internal fun queryName(uri: Uri): String? {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex("_display_name")
            if (c.moveToFirst() && idx >= 0) return c.getString(idx)
        }
        return null
    }

    // ===================== Notifikasi / Pengingat Terpadu =====================
    internal var reminderFilter = "all"
    internal var reminderEditing: JSONObject? = null
    internal var reminderDraftMessage = ""
    internal var reminderDraftBody = ""
    internal var reminderDraftNote = ""
    internal var reminderDraftHour = 13
    internal var reminderDraftMinute = 0
    internal var reminderDraftCategory = "kegiatan"
    internal var reminderDraftRepeat = "daily"
    internal var reminderDraftEnabled = true
    internal var reminderDraftPayload = JSONObject()

    // ---- Nama halaman Notifikasi (dipakai clearPage untuk menyembunyikan strip bawaan tool) ----
    internal val rmPickerPages = setOf("Pilih Waktu", "Pilih Tanggal / Pengulangan", "Pilih Hari")
    internal val rmEditorPages = setOf("Tambah Notifikasi", "Tambah Notifikasi - Detail", "Edit Notifikasi")
    internal val rmSubPages = rmPickerPages + rmEditorPages + "Detail Notifikasi"
    internal val rmAllPages = rmSubPages + "Notifikasi"

    // ---- Warna sesuai desain ----
    internal val rmDark = Color.rgb(38, 51, 61)
    internal val rmCardBg = Color.rgb(247, 249, 250)
    internal val rmCardLine = Color.rgb(229, 234, 238)
    internal val rmGray = Color.rgb(145, 154, 161)

    // ===================== Helper tampilan =====================
    internal fun rmIc(name: String, sp: Float = 20f, color: Int = rmDark): MdiIconView =
        MdiIconView(this).apply { setIconName(name); setIconSize(sp); setTextColor(color) }

    internal fun rmIconBox(iconName: String, size: Int = 46): LinearLayout = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        background = bg(Color.WHITE, size / 2, rmCardLine)
        addView(rmIc(iconName, 22f))
    }

    internal fun rmSwitch(checked: Boolean, onChange: (Boolean) -> Unit): Switch = Switch(this).apply {
        isChecked = checked
        if (Build.VERSION.SDK_INT >= 23) {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = android.content.res.ColorStateList(states, intArrayOf(Color.WHITE, Color.rgb(250, 251, 252)))
            trackTintList = android.content.res.ColorStateList(states, intArrayOf(rmDark, Color.rgb(222, 228, 232)))
        }
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    internal fun rmButton(caption: String, icon: String, primary: Boolean, onClick: () -> Unit): LinearLayout {
        val fg = if (primary) Color.WHITE else rmDark
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = if (primary) bg(rmDark, 16) else bg(Color.rgb(245, 247, 248), 16, rmCardLine)
            isClickable = true
            isFocusable = true
            addView(rmIc(icon, 18f, fg), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(8) })
            addView(TextView(this@MainActivity).apply {
                text = caption; textSize = 14f; setTextColor(fg)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            setOnClickListener { onClick() }
        }
    }

    internal fun rmSection(titleText: String, hint: String? = null): TextView = TextView(this).apply {
        val sb = android.text.SpannableStringBuilder(titleText)
        if (hint != null) {
            val start = sb.length
            sb.append(" ").append(hint)
            sb.setSpan(android.text.style.ForegroundColorSpan(textMuted), start, sb.length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        text = sb
        textSize = 13f
        setTextColor(textMain)
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(2), dp(14), 0, dp(7))
    }

    internal fun rmRow(iconName: String, value: String, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), 0, dp(10), 0)
        background = bg(rmCardBg, 14, rmCardLine)
        isClickable = true
        addView(rmIc(iconName, 21f), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(10) })
        addView(TextView(this@MainActivity).apply {
            text = value; textSize = 13.5f; setTextColor(textMain); maxLines = 2
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(rmIc("chevron-right", 22f, rmGray), LinearLayout.LayoutParams(dp(28), dp(30)))
        setOnClickListener { onClick() }
    }

    internal fun rmChoiceRow(iconName: String, titleText: String, sub: String?, trailing: View, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = bg(rmCardBg, 14, rmCardLine)
            isClickable = true
            addView(rmIc(iconName, 21f), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })
            val texts = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this@MainActivity).apply {
                text = titleText; textSize = 13.5f; setTextColor(textMain)
                if (sub != null) setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            if (sub != null) texts.addView(TextView(this@MainActivity).apply { text = sub; textSize = 11f; setTextColor(textMuted) })
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            addView(trailing, LinearLayout.LayoutParams(dp(22), dp(22)))
            setOnClickListener { onClick() }
        }

    internal fun rmCheck(on: Boolean): FrameLayout {
        val f = FrameLayout(this)
        f.addView(rmIc("check", 15f, Color.WHITE), FrameLayout.LayoutParams(-1, -1))
        rmSetCheck(f, on)
        return f
    }

    internal fun rmSetCheck(f: FrameLayout, on: Boolean) {
        f.background = if (on) bg(rmDark, 6) else bg(Color.WHITE, 6, Color.rgb(205, 212, 218))
        f.getChildAt(0).visibility = if (on) View.VISIBLE else View.INVISIBLE
    }

    internal fun rmRadio(on: Boolean): FrameLayout {
        val f = FrameLayout(this)
        val dot = View(this).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(rmDark)
            }
        }
        f.addView(dot, FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER))
        rmSetRadio(f, on)
        return f
    }

    internal fun rmSetRadio(f: FrameLayout, on: Boolean) {
        f.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.WHITE)
            setStroke(dp(if (on) 2 else 1), if (on) rmDark else Color.rgb(205, 212, 218))
        }
        f.getChildAt(0).visibility = if (on) View.VISIBLE else View.INVISIBLE
    }

    internal fun rmDeriveCategory(name: String, birthday: Boolean): String {
        val n = name.toLowerCase(Locale.getDefault())
        return when {
            birthday || n.contains("ulang tahun") || n.contains("ultah") -> "ulangtahun"
            n.contains("obat") || n.contains("vitamin") || n.contains("suplemen") -> "obat"
            else -> "kegiatan"
        }
    }

    internal fun rmItemCategory(o: JSONObject): String =
        if (o.optString("category") == "ulangtahun" || o.optBoolean("birthday")) "ulangtahun"
        else rmDeriveCategory(o.optString("message"), false)

    internal fun rmIconName(category: String, message: String): String {
        val n = message.toLowerCase(Locale.getDefault())
        return when {
            category == "obat" -> "pill"
            category == "ulangtahun" -> "cake-variant-outline"
            Regex("\\bair\\b").containsMatchIn(n) -> "water-outline"
            n.contains("tidur") -> "sleep"
            else -> "calendar-blank-outline"
        }
    }

    // ===================== Navigasi internal Notifikasi =====================
    internal fun rmTopIn(names: Set<String>): Boolean = pageBackStack.lastOrNull()?.let { it.name in names } == true

    /** Kembali ke daftar Notifikasi (segar) tanpa menumpuk riwayat halaman. */
    internal fun rmPopToList() {
        while (rmTopIn(rmSubPages)) pageBackStack.removeLast()
        if (rmTopIn(setOf("Notifikasi"))) pageBackStack.removeLast()
        content.removeAllViews()
        reminderTool()
    }

    /** Kembali ke form (Tambah/Edit) setelah memilih waktu/pengulangan/hari. */
    internal fun rmPopToEditor() {
        while (rmTopIn(rmPickerPages)) pageBackStack.removeLast()
        if (rmTopIn(rmEditorPages)) pageBackStack.removeLast()
        content.removeAllViews()
        renderReminderEditor()
    }

    internal fun rmRerenderEditor() { content.removeAllViews(); renderReminderEditor() }
    internal fun rmRerenderList() { content.removeAllViews(); reminderTool() }

    internal fun rmMenu() {
        val pm = PopupMenu(this, action)
        pm.menu.add(0, 1, 0, "Tambah notifikasi")
        pm.menu.add(0, 2, 1, "Uji notifikasi")
        pm.menu.add(0, 3, 2, "Pengaturan notifikasi")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> showReminderEditor(null)
                2 -> sendTestNotification()
                3 -> openNotificationSettings()
            }
            true
        }
        pm.show()
    }

    internal fun rmPromptText(titleText: String, hint: String, current: String, multiline: Boolean, onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(current)
            this.hint = hint
            setTextColor(textMain)
            if (multiline) { setSingleLine(false); minLines = 3; gravity = Gravity.TOP } else setSingleLine(true)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(titleText)
            .setView(input)
            .setPositiveButton("Simpan") { _, _ -> onOk(input.text.toString()) }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ===================== 1. Daftar Notifikasi =====================
    internal fun reminderTool() {
        clearPage("Notifikasi")

        val filters = listOf("all" to "Semua", "obat" to "Obat", "kegiatan" to "Kegiatan", "ulangtahun" to "Ulang Tahun")
        val seg = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = bg(Color.rgb(240, 243, 245), 22)
        }
        for ((key, name) in filters) {
            val sel = reminderFilter == key
            val chip = TextView(this).apply {
                text = name
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(typeface, if (sel) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextColor(if (sel) Color.WHITE else Color.rgb(74, 86, 96))
                if (sel) background = bg(rmDark, 18)
                setOnClickListener { reminderFilter = key; rmRerenderList() }
            }
            seg.addView(chip, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        content.addView(seg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(14) })

        val arr = readReminders()
        var shown = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val cat = rmItemCategory(o)
            if (reminderFilter != "all" && cat != reminderFilter) continue
            shown++
            val id = o.optInt("id")
            val msg = o.optString("message", "Tanpa judul")
            val time = "%02d:%02d".format(o.optInt("hour", 13), o.optInt("minute", 0))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(12), dp(12))
                background = bg(rmCardBg, 16, rmCardLine)
                isClickable = true
                setOnClickListener { showReminderDetail(o) }
            }
            row.addView(rmIconBox(rmIconName(cat, msg)), LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(12) })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(this).apply {
                text = msg; textSize = 14.5f; setTextColor(textMain)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            texts.addView(TextView(this).apply {
                text = "${repeatLabel(o.optString("repeat", "daily"), o)} • $time"
                textSize = 11.5f; setTextColor(textMuted); setPadding(0, dp(2), 0, 0)
            })
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(rmSwitch(o.optBoolean("enabled", true)) { on ->
                o.put("enabled", on)
                updateReminderObject(o)
                if (on) scheduleReminderData(this@MainActivity, o) else cancelReminderAlarm(id)
            }, LinearLayout.LayoutParams(-2, -2))
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        }
        if (shown == 0) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(20), dp(28), dp(20), dp(20)) }
            empty.addView(rmIc("bell-outline", 40f, rmGray), LinearLayout.LayoutParams(-2, -2))
            empty.addView(TextView(this).apply {
                text = "Belum ada notifikasi."
                textSize = 12.5f; setTextColor(textMuted); gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0)
            })
            content.addView(empty, LinearLayout.LayoutParams(-1, -2))
        }
        content.addView(rmButton("Tambah notifikasi", "plus", false) { showReminderEditor(null) },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(6) })
    }

    internal fun showReminderEditor(existing: JSONObject?) {
        reminderEditing = existing
        reminderDraftMessage = existing?.optString("message", "") ?: ""
        reminderDraftBody = existing?.optString("body", "") ?: ""
        reminderDraftNote = existing?.optString("note", "") ?: ""
        reminderDraftHour = existing?.optInt("hour", 13) ?: 13
        reminderDraftMinute = existing?.optInt("minute", 0) ?: 0
        reminderDraftCategory = existing?.optString("category", "kegiatan") ?: "kegiatan"
        reminderDraftRepeat = existing?.optString("repeat", "today") ?: "today"
        reminderDraftEnabled = existing?.optBoolean("enabled", true) ?: true
        reminderDraftPayload = if (existing != null) JSONObject(existing.toString()) else JSONObject()
        renderReminderEditor()
    }

    // ===================== 2 / 6 / 7. Tambah, Tambah - Detail, Edit =====================
    internal fun renderReminderEditor() {
        val editing = reminderEditing != null
        val detailed = editing || reminderDraftMessage.isNotBlank() || reminderDraftBody.isNotBlank()
        clearPage(when { editing -> "Edit Notifikasi"; detailed -> "Tambah Notifikasi - Detail"; else -> "Tambah Notifikasi" })

        val timeText = "%02d:%02d".format(reminderDraftHour, reminderDraftMinute)
        val repeatText = repeatLabel(reminderDraftRepeat, reminderDraftPayload)
        val cat = rmDeriveCategory(reminderDraftMessage, reminderDraftPayload.optBoolean("birthday"))
        val rowLp = { LinearLayout.LayoutParams(-1, dp(54)) }

        // Preview selalu mengikuti judul dan pesan yang sedang diketik.
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(12), dp(10))
            background = bg(rmCardBg, 14, rmCardLine)
        }
        preview.addView(rmIconBox(rmIconName(cat, reminderDraftMessage)), LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(12) })
        val previewText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        previewText.addView(TextView(this).apply {
            text = reminderDraftMessage.ifBlank { "Contoh notifikasi" }
            textSize = 14.5f; setTextColor(textMain); setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        previewText.addView(TextView(this).apply {
            text = reminderDraftBody.ifBlank { "Isi pesan notifikasi akan tampil di sini" }
            textSize = 11.5f; setTextColor(textMuted); maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(2), 0, 0)
        })
        preview.addView(previewText, LinearLayout.LayoutParams(0, -2, 1f))
        if (detailed) preview.addView(rmSwitch(reminderDraftEnabled) { reminderDraftEnabled = it }, LinearLayout.LayoutParams(-2, -2))
        content.addView(preview, LinearLayout.LayoutParams(-1, dp(if (detailed) 78 else 72)).apply { topMargin = dp(4) })

        content.addView(rmSection("Judul"))
        val titleInput = EditText(this).apply {
            hint = "Contoh: Minum obat"; textSize = 13f; setHintTextColor(rmGray); setTextColor(textMain)
            background = bg(rmCardBg, 14, rmCardLine); setPadding(dp(14), dp(12), dp(14), dp(12)); setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(reminderDraftMessage)
            addTextChangedListener(SimpleTextWatcher {
                reminderDraftMessage = it
                (previewText.getChildAt(0) as TextView).text = it.ifBlank { "Contoh notifikasi" }
            })
        }
        content.addView(titleInput, LinearLayout.LayoutParams(-1, dp(54)))

        content.addView(rmSection("Pesan"))
        val bodyInput = EditText(this).apply {
            hint = "Isi pesan yang akan muncul saat notifikasi"; textSize = 13f; setHintTextColor(rmGray); setTextColor(textMain)
            background = bg(rmCardBg, 14, rmCardLine); setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.TOP or Gravity.START; minLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(reminderDraftBody)
            addTextChangedListener(SimpleTextWatcher {
                reminderDraftBody = it
                (previewText.getChildAt(1) as TextView).text = it.ifBlank { "Isi pesan notifikasi akan tampil di sini" }
            })
        }
        content.addView(bodyInput, LinearLayout.LayoutParams(-1, dp(92)))

        content.addView(rmSection("Waktu"))
        content.addView(rmRow("clock-outline", timeText) { showReminderTimePickerPage() }, rowLp())
        content.addView(rmSection("Tanggal / Pengulangan"))
        content.addView(rmRow("calendar-blank-outline", repeatText) { showRepeatPickerPage() }, rowLp())
        content.addView(rmSection("Notifikasi"))
        content.addView(rmRow("bell-outline", if (detailed) "Uji notifikasi" else "10 menit sebelum") { sendTestNotification() }, rowLp())
        content.addView(rmSection("Catatan", "(opsional)"))
        val note = EditText(this).apply {
            hint = "Tambahkan catatan jika perlu…"; textSize = 13f; setHintTextColor(rmGray); setTextColor(textMain)
            background = bg(rmCardBg, 14, rmCardLine); setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3; setText(reminderDraftNote); addTextChangedListener(SimpleTextWatcher { reminderDraftNote = it })
        }
        content.addView(note, LinearLayout.LayoutParams(-1, dp(104)))

        content.addView(rmButton("Simpan", "content-save-outline", true) { saveReminderDraft() },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(22); bottomMargin = dp(8) })
    }

    internal fun saveReminderDraft() {
        val msg = reminderDraftMessage.trim()
        if (msg.isBlank()) { toast("Isi nama pesan terlebih dahulu"); return }
        val data = JSONObject(reminderDraftPayload.toString())
        data.put("message", msg)
        data.put("body", reminderDraftBody.trim())
        data.put("note", reminderDraftNote.trim())
        data.put("hour", reminderDraftHour); data.put("minute", reminderDraftMinute)
        data.put("category", rmDeriveCategory(msg, data.optBoolean("birthday")))
        data.put("repeat", reminderDraftRepeat)
        data.put("enabled", reminderDraftEnabled)
        if (reminderDraftRepeat != "selected_days") data.remove("days")
        saveReminder(data, reminderEditing)
        reminderEditing = null
        rmPopToList()
    }

    // ===================== 5. Pilih Waktu =====================
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    internal fun rmStepBtn(icon: String, step: () -> Unit): View {
        val v = LinearLayout(this).apply { gravity = Gravity.CENTER; addView(rmIc(icon, 26f, rmGray)) }
        val handler = Handler(Looper.getMainLooper())
        val repeater = object : Runnable { override fun run() { step(); handler.postDelayed(this, 90) } }
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { step(); handler.postDelayed(repeater, 400); view.isPressed = true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { handler.removeCallbacks(repeater); view.isPressed = false }
            }
            true
        }
        return v
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    internal fun rmTimeBox(tv: TextView, isHour: Boolean, onValue: (Int) -> Unit): View {
        var downY = 0f
        tv.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = ev.y; true }
                MotionEvent.ACTION_UP -> {
                    val dy = ev.y - downY
                    if (kotlin.math.abs(dy) >= dp(18)) {
                        val steps = (kotlin.math.abs(dy) / dp(28)).toInt().coerceAtLeast(1)
                        val delta = if (dy < 0) steps else -steps
                        val current = tv.text.toString().toIntOrNull() ?: 0
                        val max = if (isHour) 23 else 59
                        onValue((current + delta + max + 1) % (max + 1))
                    } else {
                        rmPromptNumber(if (isHour) "Jam" else "Menit", if (isHour) "00–23" else "00–59", tv.text.toString().toIntOrNull() ?: 0, 0, if (isHour) 23 else 59, onValue)
                    }
                    true
                }
                else -> true
            }
        }
        return tv
    }

    internal fun rmPromptNumber(title: String, hint: String, current: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; this.hint = hint; setText("%02d".format(current)); setSelectAllOnFocus(true)
            setTextColor(textMain); setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this).setTitle("$title (ketik)").setView(input)
            .setPositiveButton("Pilih") { _, _ ->
                val n = input.text.toString().toIntOrNull()
                if (n == null || n !in min..max) toast("$title harus $hint") else onValue(n)
            }.setNegativeButton("Batal", null).show()
    }

    internal fun showReminderTimePickerPage() {
        clearPage("Pilih Waktu")
        var h = reminderDraftHour
        var m = reminderDraftMinute

        fun bigNumber(): TextView = TextView(this).apply {
            textSize = 34f; gravity = Gravity.CENTER; setTextColor(textMain); setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = bg(Color.rgb(242, 245, 247), 16); isClickable = true
        }
        val hourTv = bigNumber(); val minTv = bigNumber()
        val periodViews = ArrayList<Triple<LinearLayout, TextView, TextView>>()
        fun periodOf(hh: Int): Int = if (hh in 6..11) 0 else if (hh in 12..17) 1 else 2
        fun refresh() {
            hourTv.text = "%02d".format(h); minTv.text = "%02d".format(m)
            val p = periodOf(h)
            for ((i, t) in periodViews.withIndex()) {
                val on = i == p
                t.first.background = if (on) bg(rmDark, 14) else bg(Color.rgb(244, 246, 248), 14, rmCardLine)
                t.second.setTextColor(if (on) Color.WHITE else textMain); t.third.setTextColor(if (on) Color.rgb(200, 208, 214) else textMuted)
            }
        }
        rmTimeBox(hourTv, true) { h = it; refresh() }; rmTimeBox(minTv, false) { m = it; refresh() }
        fun stepCol(tv: TextView): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(tv, LinearLayout.LayoutParams(dp(88), dp(78)))
        }
        val wheel = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        wheel.addView(stepCol(hourTv))
        wheel.addView(TextView(this).apply { text = ":"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(textMain); setTypeface(typeface, android.graphics.Typeface.BOLD) }, LinearLayout.LayoutParams(dp(30), dp(78)))
        wheel.addView(stepCol(minTv))
        content.addView(wheel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(40) })
        content.addView(View(this).apply { setBackgroundColor(rmCardLine) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(30); bottomMargin = dp(22) })
        val periods = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val ranges = listOf(Triple("Pagi", "06:00 - 11:59", 8), Triple("Siang", "12:00 - 17:59", 13), Triple("Malam", "18:00 - 23:59", 20))
        for ((idx, r) in ranges.withIndex()) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; isClickable = true; setOnClickListener { h = r.third; refresh() } }
            val t1 = TextView(this).apply { text = r.first; textSize = 12.5f; gravity = Gravity.CENTER; setTypeface(typeface, android.graphics.Typeface.BOLD) }
            val t2 = TextView(this).apply { text = r.second; textSize = 10f; gravity = Gravity.CENTER }
            box.addView(t1); box.addView(t2); periodViews.add(Triple(box, t1, t2)); periods.addView(box, LinearLayout.LayoutParams(0, dp(60), 1f).apply { if (idx > 0) leftMargin = dp(8) })
        }
        content.addView(periods, LinearLayout.LayoutParams(-1, dp(60))); refresh()
        content.addView(rmButton("Simpan", "content-save-outline", true) { reminderDraftHour = h; reminderDraftMinute = m; rmPopToEditor() }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(48); bottomMargin = dp(8) })
    }

    // ===================== 3. Pilih Tanggal / Pengulangan =====================
    internal fun showRepeatPickerPage() {
        clearPage("Pilih Tanggal / Pengulangan")
        val options = listOf(
            listOf("today", "Hari ini", "Jadwalkan hanya untuk hari ini", "calendar-today-outline"),
            listOf("daily", "Setiap hari", "Setiap hari pada waktu yang sama", "calendar-sync-outline"),
            listOf("selected_days", "Hari tertentu", "Pilih hari dalam seminggu", "calendar-clock-outline"),
            listOf("date", "Tanggal tertentu", "Pilih tanggal di kalender", "calendar-blank-outline"),
            listOf("monthly", "Setiap bulan", "Pada tanggal yang sama setiap bulan", "calendar-month-outline"),
            listOf("yearly", "Setiap tahun", "Pada tanggal dan bulan yang sama", "calendar-refresh-outline"),
            listOf("birthday", "Ulang tahun", "Peringatan pada tanggal lahir", "cake-variant-outline"),
            listOf("custom", "Kustom", "Atur sendiri", "tune-variant")
        )
        var sel = when {
            reminderDraftPayload.optBoolean("birthday") -> "birthday"
            reminderDraftRepeat == "weekdays" -> "selected_days"
            reminderDraftRepeat == "interval" -> "custom"
            else -> reminderDraftRepeat
        }
        val radios = HashMap<String, FrameLayout>()
        for (opt in options) {
            val id = opt[0]
            val radio = rmRadio(sel == id)
            radios[id] = radio
            val row = rmChoiceRow(opt[3], opt[1], opt[2], radio) {
                sel = id
                for ((k, v) in radios) rmSetRadio(v, k == sel)
            }
            content.addView(row, LinearLayout.LayoutParams(-1, dp(64)).apply { bottomMargin = dp(8) })
        }
        content.addView(rmButton("Pilih", "check", true) {
            when (sel) {
                "today" -> { rmSetRepeat("today"); rmPopToEditor() }
                "selected_days" -> showWeekdayPickerPage()
                "date", "monthly", "yearly", "birthday" -> showDatePickerPage(sel)
                "custom" -> showCustomIntervalDialog()
                else -> { rmSetRepeat("daily"); rmPopToEditor() }
            }
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14); bottomMargin = dp(8) })
    }

    internal fun rmSetRepeat(repeat: String, birthday: Boolean = false) {
        reminderDraftRepeat = repeat
        reminderDraftPayload.remove("birthday")
        if (birthday) reminderDraftPayload.put("birthday", true)
        if (repeat != "selected_days") reminderDraftPayload.remove("days")
    }

    internal fun showDatePickerPage(mode: String) {
        val now = Calendar.getInstance()
        val y = reminderDraftPayload.optInt("year", now.get(Calendar.YEAR))
        val m = reminderDraftPayload.optInt("month", now.get(Calendar.MONTH))
        val d = reminderDraftPayload.optInt("dayOfMonth", now.get(Calendar.DAY_OF_MONTH))
        DatePickerDialog(this, { _, yy, mm, dd ->
            when (mode) {
                "date" -> {
                    rmSetRepeat("date")
                    reminderDraftPayload.put("year", yy); reminderDraftPayload.put("month", mm); reminderDraftPayload.put("dayOfMonth", dd)
                }
                "monthly" -> { rmSetRepeat("monthly"); reminderDraftPayload.put("dayOfMonth", dd) }
                "birthday" -> {
                    rmSetRepeat("yearly", true)
                    reminderDraftPayload.put("month", mm); reminderDraftPayload.put("dayOfMonth", dd)
                }
                else -> {
                    rmSetRepeat("yearly")
                    reminderDraftPayload.put("month", mm); reminderDraftPayload.put("dayOfMonth", dd)
                }
            }
            rmPopToEditor()
        }, y, m, d).show()
    }

    internal fun showCustomIntervalDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(reminderDraftPayload.optInt("every", 2).toString())
            setTextColor(textMain)
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Ulangi setiap berapa hari?")
            .setView(input)
            .setPositiveButton("Pilih") { _, _ ->
                val n = (input.text.toString().toIntOrNull() ?: 2).coerceIn(1, 365)
                rmSetRepeat("interval")
                reminderDraftPayload.put("every", n)
                reminderDraftPayload.put("start", System.currentTimeMillis())
                rmPopToEditor()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ===================== 4. Pilih Hari =====================
    internal fun showWeekdayPickerPage() {
        clearPage("Pilih Hari")
        val names = arrayOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        val days = intArrayOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        val selected = BooleanArray(7)
        val old = reminderDraftPayload.optJSONArray("days")
        for (i in 0 until (old?.length() ?: 0)) { val idx = days.indexOf(old?.optInt(i) ?: 0); if (idx >= 0) selected[idx] = true }
        if (reminderDraftRepeat == "weekdays") for (i in 0..4) selected[i] = true
        if (!selected.any { it }) { selected[0] = true; selected[2] = true; selected[4] = true }

        for (i in names.indices) {
            val box = rmCheck(selected[i])
            val row = rmChoiceRow("calendar-blank-outline", names[i], null, box) {
                selected[i] = !selected[i]
                rmSetCheck(box, selected[i])
            }
            content.addView(row, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(8) })
        }
        content.addView(rmButton("Simpan", "content-save-outline", true) {
            val arr = JSONArray()
            for (i in selected.indices) if (selected[i]) arr.put(days[i])
            if (arr.length() == 0) { toast("Pilih minimal satu hari"); return@rmButton }
            rmSetRepeat("selected_days")
            reminderDraftPayload.put("days", arr)
            rmPopToEditor()
        }, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(14); bottomMargin = dp(8) })
    }

    // ===================== 8. Detail Notifikasi =====================
    internal fun showReminderDetail(existing: JSONObject) {
        clearPage("Detail Notifikasi")
        val msg = existing.optString("message", "Notifikasi")
        val cat = rmItemCategory(existing)
        val id = existing.optInt("id")
        val timeText = "%02d:%02d".format(existing.optInt("hour", 13), existing.optInt("minute", 0))
        val repeatText = repeatLabel(existing.optString("repeat", "daily"), existing)

        val circle = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = bg(Color.rgb(241, 244, 246), 38)
            addView(rmIc(rmIconName(cat, msg), 32f))
        }
        content.addView(circle, LinearLayout.LayoutParams(dp(76), dp(76)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(10); bottomMargin = dp(12) })
        content.addView(TextView(this).apply {
            text = msg; textSize = 17f; gravity = Gravity.CENTER; setTextColor(textMain)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2))
        content.addView(TextView(this).apply {
            text = "$repeatText • $timeText"; textSize = 12f; gravity = Gravity.CENTER; setTextColor(textMuted)
            setPadding(0, dp(3), 0, dp(12))
        }, LinearLayout.LayoutParams(-1, -2))
        val toggleWrap = LinearLayout(this).apply { gravity = Gravity.CENTER }
        toggleWrap.addView(rmSwitch(existing.optBoolean("enabled", true)) { on ->
            existing.put("enabled", on)
            updateReminderObject(existing)
            if (on) scheduleReminderData(this@MainActivity, existing) else cancelReminderAlarm(id)
        })
        content.addView(toggleWrap, LinearLayout.LayoutParams(-1, dp(48)))

        val detail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            background = bg(rmCardBg, 16, rmCardLine)
        }
        fun addDetail(iconName: String, titleText: String, valueText: String) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(10), 0, dp(10)) }
            r.addView(rmIc(iconName, 21f), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(12) })
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            t.addView(TextView(this).apply { text = titleText; textSize = 12.5f; setTextColor(textMain); setTypeface(typeface, android.graphics.Typeface.BOLD) })
            t.addView(TextView(this).apply { text = valueText; textSize = 11.5f; setTextColor(textMuted) })
            r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
            detail.addView(r)
        }
        addDetail("clock-outline", "Waktu", timeText)
        addDetail("calendar-blank-outline", "Pengulangan", repeatText)
        addDetail("bell-outline", "Notifikasi", "10 menit sebelum")
        val noteText = existing.optString("note").ifBlank { existing.optString("body") }
        if (noteText.isNotBlank()) addDetail("note-text-outline", "Catatan", noteText)
        content.addView(detail, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(16) })

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(rmButton("Edit", "pencil-outline", true) { showReminderEditor(existing) },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(7) })
        actions.addView(rmButton("Hapus", "delete-outline", false) { cancelReminder(id); rmPopToList() },
            LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(7) })
        content.addView(actions, LinearLayout.LayoutParams(-1, -2))
    }

    internal fun sendTestNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "scheduled_reminders"

        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(channelId, "Pengingat MyTools", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Pesan dan pengingat yang dijadwalkan pengguna"
            })
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 3001)
            toast("Izinkan notifikasi, lalu coba lagi")
            return
        }
        val open = PendingIntent.getActivity(
            this, 99001, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, channelId) else @Suppress("DEPRECATION") android.app.Notification.Builder(this)
        builder.setSmallIcon(R.drawable.ic_bell)
            .setContentTitle("MyTools")
            .setContentText("Notifikasi berhasil bekerja")
            .setAutoCancel(true)
            .setContentIntent(open)
        manager.notify(99001, builder.build())
        toast("Notifikasi uji dikirim")
    }

    internal fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply { putExtra(Settings.EXTRA_APP_PACKAGE, packageName) }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        }
        runCatching { startActivity(intent) }.onFailure { toast("Tidak dapat membuka pengaturan notifikasi") }
    }

    internal fun saveReminder(data: JSONObject, existing: JSONObject?) {
        val id = existing?.optInt("id", 0)?.takeIf { it != 0 } ?: (System.currentTimeMillis() and 0x7fffffff).toInt()
        data.put("id", id)
        if (existing != null) cancelReminderAlarm(id)
        val arr = readReminders()
        val next = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (o.optInt("id") != id) next.put(o)
        }
        if (!data.has("enabled")) data.put("enabled", true)
        next.put(data)
        prefs.edit().putString("scheduled_reminders", next.toString()).apply()
        if (data.optBoolean("enabled", true)) scheduleReminderData(this, data)
        toast(if (existing == null) "Pengingat disimpan" else "Pengingat diperbarui")
    }

    internal fun scheduleReminderData(context: Context, data: JSONObject) {
        val id = data.optInt("id")
        val next = nextReminderTime(data, System.currentTimeMillis()) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("message", data.optString("message"))
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getBroadcast(context, id, intent, flags)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        runCatching { alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending) }
            .onFailure { alarm.set(AlarmManager.RTC_WAKEUP, next, pending) }
    }

    internal fun nextReminderTime(data: JSONObject, from: Long): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val hour = data.optInt("hour", 0); val minute = data.optInt("minute", 0)
        val repeat = data.optString("repeat", "daily")
        when (repeat) {
            "today" -> {
                cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                return if (cal.timeInMillis > from) cal.timeInMillis else null
            }
            "date" -> {
                cal.set(Calendar.YEAR, data.optInt("year", cal.get(Calendar.YEAR)))
                cal.set(Calendar.MONTH, data.optInt("month", cal.get(Calendar.MONTH)))
                cal.set(Calendar.DAY_OF_MONTH, data.optInt("dayOfMonth", cal.get(Calendar.DAY_OF_MONTH)))
                cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                return if (cal.timeInMillis > from) cal.timeInMillis else null
            }
            "yearly" -> {
                cal.set(Calendar.MONTH, data.optInt("month", 0)); cal.set(Calendar.DAY_OF_MONTH, data.optInt("dayOfMonth", 1)); cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                if (cal.timeInMillis <= from) cal.add(Calendar.YEAR, 1)
                return cal.timeInMillis
            }
            "monthly" -> {
                cal.set(Calendar.DAY_OF_MONTH, data.optInt("dayOfMonth", 1).coerceIn(1, 28)); cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                if (cal.timeInMillis <= from) cal.add(Calendar.MONTH, 1)
                return cal.timeInMillis
            }
            "interval" -> {
                val every = data.optInt("every", 1).coerceAtLeast(1)
                val start = Calendar.getInstance().apply {
                    timeInMillis = data.optLong("start", from)
                    set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                while (start.timeInMillis <= from) start.add(Calendar.DAY_OF_YEAR, every)
                return start.timeInMillis
            }
            "weekdays" -> {
                cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                for (i in 0..7) { if (cal.timeInMillis > from && cal.get(Calendar.DAY_OF_WEEK) in Calendar.MONDAY..Calendar.FRIDAY) return cal.timeInMillis; cal.add(Calendar.DAY_OF_YEAR, 1) }
                return null
            }
            "selected_days" -> {
                val days = data.optJSONArray("days") ?: return null
                cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                for (i in 0..7) { if (cal.timeInMillis > from && containsJsonInt(days, cal.get(Calendar.DAY_OF_WEEK))) return cal.timeInMillis; cal.add(Calendar.DAY_OF_YEAR, 1) }
                return null
            }
            else -> {
                cal.set(Calendar.HOUR_OF_DAY, hour); cal.set(Calendar.MINUTE, minute)
                if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
                return cal.timeInMillis
            }
        }
    }

    internal fun containsJsonInt(arr: JSONArray, value: Int): Boolean {
        for (i in 0 until arr.length()) if (arr.optInt(i) == value) return true
        return false
    }

    internal fun readReminders(): JSONArray = runCatching { JSONArray(prefs.getString("scheduled_reminders", "[]") ?: "[]") }.getOrElse { JSONArray() }

    internal fun reminderCategoryLabel(key: String): String = when (key) {
        "obat" -> "Obat"; "kegiatan" -> "Kegiatan"; "ulangtahun" -> "Ulang Tahun"; else -> "Kustom"
    }

    internal fun rmMonthShort(m: Int): String =
        arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")[m.coerceIn(0, 11)]

    internal fun repeatLabel(repeat: String, data: JSONObject?): String = when (repeat) {
        "today" -> "Hari ini"
        "weekdays" -> "Senin, Selasa, Rabu, Kamis, Jumat"
        "selected_days" -> {
            val arr = data?.optJSONArray("days")
            if (arr == null || arr.length() == 0) "Hari tertentu" else {
                val order = intArrayOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
                val names = arrayOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
                val picked = order.indices.filter { containsJsonInt(arr, order[it]) }
                if (picked.size == 7) "Setiap hari" else picked.joinToString(", ") { names[it] }
            }
        }
        "date" -> if (data == null) "Tanggal tertentu" else
            "${data.optInt("dayOfMonth", 1)} ${rmMonthShort(data.optInt("month", 0))} ${data.optInt("year", Calendar.getInstance().get(Calendar.YEAR))}"
        "monthly" -> "Setiap bulan, tgl ${data?.optInt("dayOfMonth", 1) ?: 1}"
        "yearly" -> if (data == null) "Setiap tahun" else {
            val s = "${data.optInt("dayOfMonth", 1)} ${rmMonthShort(data.optInt("month", 0))}"
            if (data.optBoolean("birthday")) s else "Setiap tahun, $s"
        }
        "interval" -> "Setiap ${data?.optInt("every", 2) ?: 2} hari"
        else -> "Setiap hari"
    }

    internal fun renderReminderList() {
        val arr = readReminders()
        var shown = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            if (reminderFilter != "all" && o.optString("category", "kustom") != reminderFilter) continue
            shown++
            val id = o.optInt("id")
            val msg = o.optString("message")
            val time = "%02d:%02d".format(o.optInt("hour"), o.optInt("minute"))
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(8), dp(10)); background = bg(panel2, 16, line) }
            val icon = ImageView(this).apply {
                setImageResource(when (o.optString("category")) {
                    "obat" -> R.drawable.ic_medical
                    "kegiatan" -> R.drawable.ic_calendar
                    "ulangtahun" -> R.drawable.ic_cake
                    else -> R.drawable.ic_bell
                })
                setPadding(dp(9), dp(9), dp(9), dp(9))
                background = bg(Color.rgb(242,244,246), 14)
            }
            row.addView(icon, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(10) })
            val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(label(msg, 13f, true))
            texts.addView(subLabel("${repeatLabel(o.optString("repeat", "daily"), o)} • $time", 11f))
            if (o.optString("note").isNotBlank()) texts.addView(subLabel(o.optString("note"), 10f))
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
            val toggle = Switch(this).apply {
                isChecked = o.optBoolean("enabled", true)
                setOnClickListener {
                    o.put("enabled", isChecked)
                    updateReminderObject(o)
                    if (!isChecked) cancelReminderAlarm(id) else scheduleReminderData(this@MainActivity, o)
                }
            }
            row.addView(toggle, LinearLayout.LayoutParams(dp(54), dp(48)))
            row.setOnClickListener { showReminderEditor(o) }
            row.setOnLongClickListener { cancelReminder(id); reminderTool(); true }
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
        if (shown == 0) content.addView(subLabel(if (arr.length() == 0) "Belum ada pengingat. Tekan + untuk membuat pesan baru." else "Belum ada pengingat di kategori ini.", 12f))
    }

    internal fun updateReminderObject(updated: JSONObject) {
        val old = readReminders(); val next = JSONArray()
        for (i in 0 until old.length()) {
            val o = old.optJSONObject(i) ?: continue
            next.put(if (o.optInt("id") == updated.optInt("id")) updated else o)
        }
        prefs.edit().putString("scheduled_reminders", next.toString()).apply()
    }

    internal fun cancelReminder(id: Int) {
        val old = readReminders()
        val next = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue
            if (item.optInt("id") != id) next.put(item)
        }
        prefs.edit().putString("scheduled_reminders", next.toString()).apply()
        cancelReminderAlarm(id)
    }

    internal fun cancelReminderAlarm(id: Int) {
        val alarm = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val flags = PendingIntent.FLAG_NO_CREATE or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))?.cancel()
        toast("Pengingat dihapus")
    }

    internal fun editor(file: File?, forcedMode: String? = null) {
        editorLanding = file == null && forcedMode == null && editorExternalTarget == null
        clearPage("Editor")
        editorFile = file
        file?.let { recordRecentFile(it) }
        editorMode = forcedMode ?: detectEditorMode(file?.name)
        if (editorLanding) renderEditorHome() else renderEditorPage()
    }

    internal fun detectEditorMode(name: String?): String {
        val ext = name?.substringAfterLast('.', "")?.toLowerCase(Locale.getDefault()) ?: ""
        return when (ext) {
            "html", "htm" -> "html"
            "css" -> "css"
            "js", "mjs", "cjs" -> "js"
            "json" -> "json"
            "csv", "tsv" -> "csv"
            "base64", "b64" -> "base64"
            "py", "kt", "kts", "java", "ts", "c", "cpp", "h", "hpp", "cs", "go", "rs", "php", "sh" -> "code"
            "ini", "cfg", "conf", "properties", "yaml", "yml", "toml" -> "config"
            "xml" -> "xml"
            else -> "text"
        }
    }

    internal fun editorModeName(mode: String): String = when (mode) {
        "html" -> "HTML"
        "css" -> "CSS"
        "js" -> "JavaScript"
        "json" -> "JSON"
        "csv" -> "CSV"
        "base64" -> "Base64"
        "utility" -> "Utilitas"
        "code" -> "Kode"
        "config" -> "Konfig"
        "xml" -> "XML"
        else -> "Teks"
    }

    internal fun editorDefaultName(mode: String): String = when (mode) {
        "html" -> "index.html"
        "css" -> "style.css"
        "js" -> "script.js"
        "json" -> "untitled.json"
        "csv" -> "untitled.csv"
        "base64" -> "untitled.txt"
        "utility" -> "untitled.txt"
        "code" -> "untitled.py"
        "config" -> "config.ini"
        "xml" -> "untitled.xml"
        else -> "untitled.txt"
    }

    internal fun editorModeDescription(mode: String): String = when (mode) {
        "html" -> "Edit HTML dan preview halaman web"
        "css" -> "Edit stylesheet CSS"
        "js" -> "Edit JavaScript"
        "json" -> "Edit, validasi, format, dan konversi JSON"
        "csv" -> "Lihat dan konversi data tabel"
        "base64" -> "Encode dan decode Base64"
        "utility" -> "Utilitas teks dan perhitungan"
        "code" -> "Edit kode program"
        "config" -> "Edit file konfigurasi"
        "xml" -> "Edit dan rapikan XML"
        else -> "Edit teks dan catatan"
    }

    internal fun renderEditorHome() {
        // Landing tetap memakai navigasi utama. Hanya tiga mode web-code yang tampil
        // langsung; format lain dipindahkan ke tombol + agar layar tetap bersih.
        title.text = "Editor"
        subtitle.visibility = View.GONE
        action.visibility = View.VISIBLE
        action.text = "+"
        action.textSize = 28f
        back.visibility = View.VISIBLE
        configureActionForPage("Editor")
        editorBottomBar.visibility = View.GONE
        editorMore.visibility = View.GONE
        topBarVisibility(true)

        content.setPadding(dp(10), dp(4), dp(10), dp(12))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(20), dp(20), dp(18))
            background = bg(Color.rgb(246, 248, 250), 18, Color.rgb(231, 235, 239))
        }
        hero.addView(MdiIconView(this).apply {
            setIconName("file-document-edit-outline")
            setIconSize(38f)
            setTextColor(textMain)
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { bottomMargin = dp(8) }
        })
        hero.addView(TextView(this).apply {
            text = "Pilih mode editor"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(textMain)
            gravity = Gravity.CENTER
        })
        hero.addView(TextView(this).apply {
            text = "HTML, CSS, dan JavaScript dalam satu editor. Format lain ada di (+)."
            textSize = 12f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, 0)
        })
        content.addView(hero, LinearLayout.LayoutParams(-1, dp(136)).apply { bottomMargin = dp(12) })
        animateEditorItem(hero, 0L, 10f)

        val modes = listOf(
            Triple("language-html5", "HTML", "Edit halaman HTML dan preview web.") to "html",
            Triple("language-css3", "CSS", "Edit stylesheet dan tampilan web.") to "css",
            Triple("language-javascript", "JavaScript", "Edit logic dan interaksi halaman web.") to "js"
        )
        modes.forEachIndexed { index, pair ->
            val (item, mode) = pair
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(7), dp(10), dp(7))
                background = bg(Color.WHITE, 16, Color.rgb(226, 231, 235))
                isClickable = true
                setOnClickListener {
                    animateEditorPress(this)
                    postDelayed({ editorExternalTarget = null; editorExternalMode = null; editor(null, mode) }, 70L)
                }
            }
            val (iconName, name, desc) = item
            row.addView(MdiIconView(this).apply {
                setIconName(iconName)
                setIconSize(24f)
                setTextColor(textMain)
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(40)).apply { rightMargin = dp(8) }
            })
            val textBox = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            textBox.addView(TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(textMain)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            textBox.addView(TextView(this).apply {
                text = desc
                textSize = 11f
                setTextColor(textMuted)
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(textBox)
            row.addView(TextView(this).apply {
                text = "›"
                textSize = 25f
                setTextColor(textMuted)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(42))
            })
            content.addView(row, LinearLayout.LayoutParams(-1, dp(66)).apply { bottomMargin = dp(7) })
            animateEditorItem(row, 80L + index * 45L, 12f)
        }
        animateEditorScreen()
    }

    internal fun topBarVisibility(visible: Boolean) {
        topBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    internal fun showEditorModePicker() {
        var menuDialog: AlertDialog? = null
        // Tampilan menu sengaja dibuat seperti sheet pada screenshot: tiga kartu besar
        // untuk operasi file, lalu pilihan format berada di dalam "Buat file".
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(28), dp(4), dp(28), dp(18))
            background = bg(Color.WHITE, 28, Color.TRANSPARENT)
        }
        panel.addView(TextView(this).apply {
            text = "Tambah"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, dp(6), 0, dp(14))
        })

        fun sheetRow(iconName: String, titleText: String, action: () -> Unit): View {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(8), dp(16), dp(8))
                background = bg(Color.rgb(247, 247, 248), 18, Color.TRANSPARENT)
                isClickable = true
                setOnClickListener { action() }
                addView(MdiIconView(this@MainActivity).apply {
                    setIconName(iconName)
                    setIconSize(24f)
                    setTextColor(textMain)
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(48)).apply { rightMargin = dp(8) }
                })
                addView(TextView(this@MainActivity).apply {
                    text = titleText
                    textSize = 15f
                    setTextColor(textMain)
                    gravity = Gravity.CENTER_VERTICAL
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
        }

        panel.addView(sheetRow("file-outline", "Buat file") {
            menuDialog?.dismiss()
            showEditorCreateFilePicker()
        }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(12) })
        panel.addView(sheetRow("folder-plus-outline", "Buat folder") {
            menuDialog?.dismiss()
            createEditorFolder()
        }, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin = dp(12) })
        panel.addView(sheetRow("folder-open-outline", "Buka file") {
            menuDialog?.dismiss()
            editorExternalTarget = null
            editorExternalMode = null
            pickFileForEditor()
        }, LinearLayout.LayoutParams(-1, dp(72)))

        val dialog = AlertDialog.Builder(this).setView(panel).create()
        menuDialog = dialog
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.window?.setDimAmount(0.46f)
        }
        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.attributes = dialog.window?.attributes?.apply {
            width = (resources.displayMetrics.widthPixels - dp(32))
        }
    }

    internal fun showEditorCreateFilePicker() {
        val labels = arrayOf("HTML (.html)", "CSS (.css)", "JavaScript (.js)", "JSON (.json)", "CSV (.csv)", "Base64 (.txt)", "Teks (.txt)", "Konfigurasi (.ini)", "XML (.xml)", "Utilitas Teks")
        val keys = arrayOf("html", "css", "js", "json", "csv", "base64", "text", "config", "xml", "utility")
        AlertDialog.Builder(this)
            .setTitle("Buat file")
            .setItems(labels) { _, which ->
                editorExternalTarget = null
                editorExternalMode = null
                editorFile = null
                editor(null, keys[which])
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    internal fun createEditorFolder() {
        val name = edit("Nama folder")
        AlertDialog.Builder(this)
            .setTitle("Buat folder")
            .setView(name)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Buat") { _, _ ->
                val folder = safeChildFile(filesDir, name.text.toString())
                if (folder == null) toast("Nama folder tidak valid")
                else if (folder.exists() || !folder.mkdirs()) toast("Folder gagal dibuat")
                else toast("Folder dibuat: ${folder.name}")
            }.show()
    }

    internal fun showEditorMoreMenu() {
        showEditorModePicker()
    }

    internal fun renderEditorPage() {
        // Saat sudah masuk workspace editor, sembunyikan AppBar agar area kode bersih
        // seperti editor pada screenshot. Tombol + dipindah ke kartu nama file.
        topBarVisibility(false)
        subtitle.visibility = View.GONE
        homeMenu.visibility = View.GONE
        homeProfile.visibility = View.GONE
        action.visibility = View.GONE
        back.visibility = View.GONE
        editorMore.visibility = View.GONE
        editorBottomBar.visibility = View.GONE

        content.setPadding(dp(8), dp(6), dp(8), dp(4))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val fileCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = bg(Color.rgb(246, 246, 247), 20, Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, dp(66), 1f)
        }
        fileCard.addView(MdiIconView(this).apply {
            setIconName(if (editorMode == "html") "language-html5" else if (editorMode == "css") "language-css3" else if (editorMode == "js") "language-javascript" else "file-document-outline")
            setIconSize(25f)
            setTextColor(textMain)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(48)).apply { rightMargin = dp(8) }
        })
        editorNameLabel = TextView(this).apply {
            text = editorFile?.name ?: if (editorExternalMode != null) editorDefaultName(editorMode) else "Tanpa judul"
            textSize = 15f
            setTextColor(textMain)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        fileCard.addView(editorNameLabel)
        fileCard.addView(TextView(this).apply {
            text = "✎"
            textSize = 22f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(48))
            setOnClickListener { renameEditorFile() }
        })
        header.addView(fileCard)

        val tree = TextView(this).apply {
            text = "☰"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = bg(if (isDarkTheme) panel2 else Color.rgb(242,244,246), 16, Color.TRANSPARENT)
            contentDescription = "Project files"
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(6) }
            setOnClickListener { showEditorProjectTree() }
        }
        header.addView(tree)

        val console = TextView(this).apply {
            text = "›_"
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = bg(if (isDarkTheme) panel2 else Color.rgb(242,244,246), 16, Color.TRANSPARENT)
            contentDescription = "Console"
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(5) }
            setOnClickListener { showEditorConsole() }
        }
        header.addView(console)

        val add = TextView(this).apply {
            text = "+"
            textSize = 27f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = bg(Color.rgb(16,16,16), 16, Color.TRANSPARENT)
            contentDescription = "New file"
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(44)).apply { leftMargin = dp(5) }
            elevation = dp(3).toFloat()
            setOnClickListener { showEditorModePicker() }
        }
        header.addView(add)
        content.addView(header, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(5) })

        // File tabs: compact, horizontally scrollable, and visually closer to a mobile IDE.
        val tabsScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, 0, 0, dp(4))
        }
        val modeBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tabModes = listOf("HTML" to "html", "CSS" to "css", "JS" to "js")
        if (editorMode !in tabModes.map { it.second }) {
            tabModes.plus(editorModeName(editorMode) to editorMode).forEach { (labelText, mode) ->
                modeBar.addView(editorTab(labelText, mode), LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(5) })
            }
        } else {
            tabModes.forEach { (labelText, mode) ->
                modeBar.addView(editorTab(labelText, mode), LinearLayout.LayoutParams(-2, dp(38)).apply { rightMargin = dp(5) })
            }
        }
        tabsScroll.addView(modeBar)
        content.addView(tabsScroll, LinearLayout.LayoutParams(-1, dp(42)))

        val work = edit(when (editorMode) {
            "html" -> "Ketik HTML...   ! + Tab/Enter = Emmet"
            "css" -> "Ketik CSS..."
            "js" -> "Ketik JavaScript..."
            "json" -> "Ketik JSON di sini..."
            "csv" -> "Ketik data CSV di sini..."
            "base64" -> "Masukkan teks atau Base64..."
            else -> "Ketik teks atau kode di sini..."
        }, true).apply {
            minLines = 1
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(3) }
            textSize = 14f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = bg(Color.WHITE, 18, Color.rgb(225, 225, 225))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        editorBox = work
        val externalText = editorExternalTarget?.text?.toString()
        when {
            editorFile != null -> work.setText(runCatching { editorFile!!.readText() }.getOrDefault(""))
            externalText != null -> work.setText(externalText)
        }
        content.addView(work)

        editorStatusLabel = TextView(this).apply {
            text = "Baris 1, Kolom 1  |  ${work.text.length} karakter"
            textSize = 11f
            setTextColor(textMuted)
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        content.addView(editorStatusLabel)
        work.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                val txt = s?.toString().orEmpty()
                editorStatusLabel?.text = "Baris ${txt.count { it == '\n' } + 1}, Kolom ${txt.substringAfterLast('\n').length + 1}  |  ${txt.length} karakter"
            }
            override fun afterTextChanged(e: android.text.Editable?) {}
        })

        // Tool-specific actions tetap bisa dipanggil dari toolbar bawah, tetapi daftar
        // mode/file tambahan tidak lagi memenuhi area editor.
        editorContextActions = null
        renderEditorBottomBar(work)
        animateEditorScreen()
    }

    internal fun editorTab(labelText: String, mode: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(10), 0)
        background = bg(
            if (editorMode == mode) (if (isDarkTheme) panel2 else Color.rgb(232,236,240)) else Color.TRANSPARENT,
            12,
            if (editorMode == mode) (if (isDarkTheme) line else Color.rgb(215,220,224)) else Color.TRANSPARENT
        )
        isClickable = true
        isFocusable = true
        contentDescription = "Buka tab $labelText"
        setOnClickListener {
            if (editorMode != mode) {
                editorExternalTarget = null
                editorExternalMode = mode
                editor(null, mode)
            }
        }
        addView(MdiIconView(this@MainActivity).apply {
            setIconName(when (mode) {
                "html" -> "language-html5"
                "css" -> "language-css3"
                "js" -> "language-javascript"
                else -> "file-document-outline"
            })
            setIconSize(16f)
            setTextColor(if (editorMode == mode) textMain else textMuted)
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(24)).apply { rightMargin = dp(5) }
        })
        addView(TextView(this@MainActivity).apply {
            text = labelText
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(if (editorMode == mode) textMain else textMuted)
            includeFontPadding = false
        })
    }

    internal fun showEditorProjectTree() {
        val root = editorFile?.parentFile ?: prefs.getString("last_workspace", null)?.let { File(it) }
        val files = root?.listFiles()?.filter { it.isFile && it.name != "workspace.json" }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
        if (files.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Project Files")
                .setMessage("Belum ada file di workspace ini. Buat file baru dari tombol + di editor.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val names = files.map { if (it == editorFile) "✓  ${it.name}" else it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Project Files • ${files.size}")
            .setItems(names) { _, which -> editor(files[which]) }
            .setNegativeButton("Tutup", null)
            .show()
    }

    internal fun showEditorConsole() {
        val message = if (webBuildReady) {
            "Build terakhir siap. Gunakan Preview untuk melihat hasil atau Host Wi-Fi untuk menjalankan project."
        } else {
            "Console siap. Belum ada proses build yang aktif dari editor ini."
        }
        AlertDialog.Builder(this)
            .setTitle("Console")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    internal fun renameEditorFile() {
        val input = edit("Nama file")
        input.setText(editorNameLabel?.text?.toString()?.removePrefix("Tanpa judul") ?: editorDefaultName(editorMode))
        AlertDialog.Builder(this).setTitle("Nama file").setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("OK") { _, _ ->
                val n = safeFileName(input.text.toString())
                editorNameLabel?.text = n
                if (editorFile != null && editorFile!!.name != n) {
                    val next = safeChildFile(editorFile!!.parentFile ?: filesDir, n)
                    if (next != null) runCatching { editorFile!!.renameTo(next); editorFile = next }
                }
            }.show()
    }

    internal fun renderEditorBottomBar(work: EditText) {
        val bar = editorBottomBar
        bar.removeAllViews()
        bar.visibility = View.VISIBLE
        bar.alpha = 1f
        bar.translationY = 0f
        val actions = listOf(
            "file-plus-outline" to ("Baru" to { showEditorCreateFilePicker() }),
            "folder-open-outline" to ("Buka" to { pickFileForEditor() }),
            "content-save-outline" to ("Simpan" to { saveEditorCurrent() }),
            "undo" to ("Undo" to { work.undoSafe() }),
            "redo" to ("Redo" to { work.redoSafe() }),
            "magnify" to ("Cari" to { showEditorFindDialog(false) }),
            "web" to ("Preview" to { previewUnifiedEditor(work) }),
            "code-tags" to ("Emmet" to { applySimpleEmmet(work) }),
            "select-all" to ("Pilih" to { work.selectAll() })
        )
        actions.forEach { (iconName, item) ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { animateEditorPress(this); postDelayed({ item.second() }, 40L) }
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
                setPadding(dp(1), dp(3), dp(1), dp(3))
            }
            cell.addView(MdiIconView(this).apply {
                setIconName(iconName)
                setIconSize(21f)
                setTextColor(textMain)
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(27))
            })
            cell.addView(TextView(this).apply {
                text = item.first
                textSize = 10f
                setTextColor(textMain)
                gravity = Gravity.CENTER
                includeFontPadding = false
                layoutParams = LinearLayout.LayoutParams(-2, dp(18))
            })
            bar.addView(cell)
        }
    }

    internal fun previewUnifiedEditor(work: EditText) {
        val text = work.text.toString()
        when (editorMode) {
            "html" -> previewHtmlText(text, "HTML")
            "css" -> previewHtmlText("<style>${text.htmlEsc()}</style><body><h3>CSS Preview</h3><p>Gunakan HTML untuk melihat hasil styling secara langsung.</p></body>", "HTML")
            "js" -> previewHtmlText("<script>${text}</script><body><h3>JavaScript Preview</h3></body>", "HTML")
            else -> output(text)
        }
    }

    internal fun setEditorMode(mode: String) {
        val currentFile = editorFile
        editorExternalTarget = null
        editorExternalMode = null
        editor(currentFile, mode)
    }

    internal fun refreshEditorContextActions() {
        val row = editorContextActions ?: return
        row.removeAllViews()
        val actions: List<Pair<String, () -> Unit>> = when (editorMode) {
            "json" -> listOf(
                "✦\nFormat" to { transformEditorJson(true) },
                "ϟ\nMinify" to { transformEditorJson(false) },
                "✓\nValidasi" to { validateEditorJson() },
                "▦\nKe CSV" to { jsonToCsvEditor() },
                "▱\nBuka" to { pickFileForEditor() }
            )
            "csv" -> listOf(
                "✦\nRapikan" to { normalizeCsvEditor() },
                "{}\nKe JSON" to { csvToJsonEditor() },
                "▦\nTabel" to { showCsvInfo() },
                "▱\nBuka" to { pickFileForEditor() },
                "▣\nSimpan" to { saveEditorCurrent() }
            )
            "base64" -> listOf(
                "↑\nEncode" to { encodeBase64Editor() },
                "↓\nDecode" to { decodeBase64Editor() },
                "⌫\nBersihkan" to { editorBox?.setText("") },
                "▣\nSimpan" to { saveEditorCurrent() },
                "▱\nBuka" to { pickFileForEditor() }
            )
            "utility" -> listOf(
                "Aa\nCase" to { showEditorCaseDialog() },
                "64\nBase64" to { showEditorBase64Dialog() },
                "#\nHitung" to { showTextCount() },
                "⌕\nCari" to { showEditorFindDialog(false) },
                "↔\nGanti" to { showEditorFindDialog(true) }
            )
            "code" -> listOf(
                "⌕\nCari" to { showEditorFindDialog(false) },
                "↔\nGanti" to { showEditorFindDialog(true) },
                "Aa\nCase" to { showEditorCaseDialog() },
                "64\nBase64" to { showEditorBase64Dialog() },
                "▱\nBuka" to { pickFileForEditor() }
            )
            "config" -> listOf(
                "≡\nFormat" to { formatConfigEditor() },
                "⌕\nCari" to { showEditorFindDialog(false) },
                "↔\nGanti" to { showEditorFindDialog(true) },
                "↺\nReset" to { editorBox?.setText("") },
                "▣\nSimpan" to { saveEditorCurrent() }
            )
            "xml" -> listOf(
                "✓\nValidasi" to { validateXmlEditor() },
                "≡\nFormat" to { formatXmlEditor() },
                "⌕\nCari" to { showEditorFindDialog(false) },
                "↔\nGanti" to { showEditorFindDialog(true) },
                "▣\nSimpan" to { saveEditorCurrent() }
            )
            else -> listOf(
                "▱\nBuka" to { pickFileForEditor() },
                "▣\nSimpan" to { saveEditorCurrent() },
                "Aa\nCase" to { showEditorCaseDialog() },
                "64\nBase64" to { showEditorBase64Dialog() },
                "▢\nCopy" to { copyEditorText() }
            )
        }
        actions.forEach { (txt, click) ->
            val parts = txt.split("\n")
            val v = TextView(this).apply {
                text = "${parts[0]}\n${parts[1]}"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(textMain)
                background = bg(panel2, 13, line)
                setOnClickListener { click() }
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) }
            }
            row.addView(v)
        }
    }

    internal fun copyEditorText() {
        val clip = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("Editor", editorBox?.text?.toString().orEmpty()))
        toast("Teks disalin")
    }

    internal fun selectedOrAllText(): String {
        val box = editorBox ?: return ""
        val a = minOf(box.selectionStart, box.selectionEnd)
        val b = maxOf(box.selectionStart, box.selectionEnd)
        return if (a != b) box.text.substring(a, b) else box.text.toString()
    }

    internal fun replaceSelectedOrAll(value: String) {
        val box = editorBox ?: return
        val a = minOf(box.selectionStart, box.selectionEnd)
        val b = maxOf(box.selectionStart, box.selectionEnd)
        if (a != b) {
            box.text.replace(a, b, value)
            box.setSelection(a + value.length)
        } else {
            box.setText(value)
            box.setSelection(box.length())
        }
    }

    internal fun showEditorCaseDialog() {
        val items = arrayOf("UPPERCASE", "lowercase", "Title Case", "Slug / URL", "Hitung kata & karakter")
        AlertDialog.Builder(this).setTitle("Text Case & Utility").setItems(items) { _, which ->
            when (which) {
                0 -> replaceSelectedOrAll(selectedOrAllText().toUpperCase(Locale.getDefault()))
                1 -> replaceSelectedOrAll(selectedOrAllText().toLowerCase(Locale.getDefault()))
                2 -> replaceSelectedOrAll(selectedOrAllText().toLowerCase(Locale.getDefault()).split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ") { word -> if (word.isEmpty()) word else word.substring(0, 1).toUpperCase(Locale.getDefault()) + word.substring(1) })
                3 -> replaceSelectedOrAll(selectedOrAllText().trim().toLowerCase(Locale.getDefault()).replace(Regex("[^a-z0-9]+"), "-").trim('-'))
                4 -> showTextCount()
            }
        }.setNegativeButton("Batal", null).show()
    }

    internal fun showTextCount() {
        val s = selectedOrAllText()
        val words = s.trim().let { if (it.isEmpty()) 0 else it.split(Regex("\\s+")).size }
        toast("$words kata • ${s.length} karakter")
    }

    internal fun showEditorBase64Dialog() {
        AlertDialog.Builder(this).setTitle("Base64").setItems(arrayOf("Encode", "Decode")) { _, which ->
            if (which == 0) encodeBase64Editor() else decodeBase64Editor()
        }.setNegativeButton("Batal", null).show()
    }

    internal fun encodeBase64Editor() {
        val bytes = selectedOrAllText().toByteArray(StandardCharsets.UTF_8)
        replaceSelectedOrAll(android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
    }

    internal fun decodeBase64Editor() {
        runCatching { String(Base64.getDecoder().decode(selectedOrAllText().trim()), StandardCharsets.UTF_8) }
            .onSuccess { replaceSelectedOrAll(it) }
            .onFailure { toast("Base64 tidak valid") }
    }

    internal fun normalizeCsvEditor() {
        val lines = editorBox?.text?.toString()?.lines()?.filter { it.isNotBlank() }.orEmpty()
        if (lines.isEmpty()) return
        val delim = if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
        val out = lines.joinToString("\n") { csvParseLine(it, delim).joinToString(",") { cell -> csvEscape(cell.trim()) } }
        editorBox?.setText(out)
        editorBox?.setSelection(editorBox?.length() ?: 0)
    }

    internal fun csvParseLine(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>(); val cur = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (quoted && i + 1 < line.length && line[i + 1] == '"') { cur.append('"'); i++ } else quoted = !quoted
            } else if (c == delimiter && !quoted) { out.add(cur.toString()); cur.setLength(0) } else cur.append(c)
            i++
        }
        out.add(cur.toString()); return out
    }

    internal fun csvEscape(s: String): String = if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"${s.replace("\"", "\"\"")}\"" else s

    internal fun csvToJsonEditor() {
        val lines = editorBox?.text?.toString()?.lines()?.filter { it.isNotBlank() }.orEmpty()
        if (lines.size < 1) return
        val delimiter = if (lines.first().count { it == ';' } > lines.first().count { it == ',' }) ';' else ','
        val headers = csvParseLine(lines.first(), delimiter)
        val arr = JSONArray()
        lines.drop(1).forEach { line ->
            val cells = csvParseLine(line, delimiter); val obj = JSONObject()
            headers.forEachIndexed { i, h -> obj.put(h.trim(), cells.getOrElse(i) { "" }) }
            arr.put(obj)
        }
        editorBox?.setText(prettyJson(arr.toString()))
        editorBox?.setSelection(editorBox?.length() ?: 0)
    }

    internal fun jsonToCsvEditor() {
        val s = editorBox?.text?.toString()?.trim().orEmpty()
        runCatching {
            val arr = if (s.startsWith("[")) JSONArray(s) else JSONArray().put(JSONObject(s))
            if (arr.length() == 0) return@runCatching ""
            val keys = linkedSetOf<String>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.keys()?.forEach { keys.add(it) }
            val header = keys.joinToString(",") { csvEscape(it) }
            val rows = (0 until arr.length()).map { i ->
                val o = arr.optJSONObject(i) ?: JSONObject()
                keys.joinToString(",") { k -> csvEscape(o.opt(k)?.toString() ?: "") }
            }
            (listOf(header) + rows).joinToString("\n")
        }.onSuccess { editorBox?.setText(it); editorBox?.setSelection(editorBox?.length() ?: 0) }
            .onFailure { toast("JSON tidak valid: ${it.message}") }
    }

    internal fun showCsvInfo() {
        val lines = editorBox?.text?.toString()?.lines()?.filter { it.isNotBlank() }.orEmpty()
        val delimiter = lines.firstOrNull()?.let { if (it.count { c -> c == ';' } > it.count { c -> c == ',' }) ';' else ',' } ?: ','
        val cols = lines.firstOrNull()?.let { csvParseLine(it, delimiter).size } ?: 0
        toast("${lines.size} baris • $cols kolom")
    }

    internal fun saveEditorCurrent() {
        val box = editorBox ?: return
        editorExternalTarget?.let {
            it.setText(box.text.toString())
            toast("Diterapkan ke ${editorMode.uppercase(Locale.getDefault())}")
            return
        }
        val name = safeFileName((editorNameLabel?.text?.toString() ?: "").trim().ifEmpty { editorDefaultName(editorMode) })
        val target = editorFile ?: safeChildFile(filesDir, name)
        if (target == null) { toast("Nama file tidak valid"); return }
        runCatching {
            target.parentFile?.mkdirs()
            target.writeText(box.text.toString())
            editorFile = target
            editorNameLabel?.text = target.name
        }.onSuccess { toast("Tersimpan: ${target.name}") }
            .onFailure { toast("Gagal menyimpan: ${it.message}") }
    }

    internal fun validateEditorJson() {
        val s = editorBox?.text?.toString()?.trim().orEmpty()
        val result = runCatching {
            if (s.startsWith("{")) JSONObject(s) else if (s.startsWith("[")) JSONArray(s) else error("JSON harus dimulai dengan { atau [")
            "JSON valid"
        }.getOrElse { "JSON tidak valid: ${it.message}" }
        toast(result)
    }

    internal fun transformEditorJson(pretty: Boolean) {
        val box = editorBox ?: return
        runCatching {
            box.setText(if (pretty) prettyJson(box.text.toString()) else minifyJson(box.text.toString()))
            box.setSelection(box.length())
        }.onFailure { toast("JSON tidak valid: ${it.message}") }
    }

    internal fun showEditorFindDialog(replace: Boolean) {
        val find = edit("Cari")
        val repl = if (replace) edit("Ganti dengan") else null
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), 0, dp(20), 0) }
        box.addView(find); repl?.let { box.addView(it) }
        AlertDialog.Builder(this).setTitle(if (replace) "Cari & Ganti" else "Cari")
            .setView(box)
            .setNegativeButton("Batal", null)
            .setPositiveButton(if (replace) "Ganti" else "Cari") { _, _ ->
                val source = editorBox?.text?.toString().orEmpty()
                val q = find.text.toString()
                if (q.isEmpty()) { toast("Teks pencarian kosong"); return@setPositiveButton }
                if (replace) editorBox?.setText(source.replace(q, repl?.text?.toString().orEmpty()))
                else toast(if (source.contains(q)) "Ditemukan" else "Tidak ditemukan")
            }.show()
    }

    internal fun formatConfigEditor() {
        val box = editorBox ?: return
        val out = box.text.toString().lines().joinToString("\n") { line ->
            line.trim().replace(Regex("\\s*=\\s*"), " = ")
        }.trim()
        box.setText(out)
    }

    internal fun validateXmlEditor() {
        val s = editorBox?.text?.toString().orEmpty()
        runCatching {
            val f = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            f.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(s)))
            "XML valid"
        }.onSuccess { toast(it) }.onFailure { toast("XML tidak valid: ${it.message}") }
    }

    internal fun formatXmlEditor() {
        val box = editorBox ?: return
        runCatching {
            val f = javax.xml.transform.TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes")
                setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
            }
            val sw = StringWriter()
            f.transform(javax.xml.transform.stream.StreamSource(StringReader(box.text.toString())), javax.xml.transform.stream.StreamResult(sw))
            box.setText(sw.toString())
        }.onFailure { toast("XML tidak valid: ${it.message}") }
    }

    internal fun EditText.undoSafe() {
        runCatching {
            val m = java.lang.reflect.Method::class
            val field = EditText::class.java.getDeclaredField("mEditor")
            field.isAccessible = true
            val editorObj = field.get(this)
            val undo = editorObj.javaClass.getMethod("undo")
            undo.invoke(editorObj)
        }.onFailure { toast("Undo tidak tersedia pada perangkat ini") }
    }

    internal fun EditText.redoSafe() {
        runCatching {
            val field = EditText::class.java.getDeclaredField("mEditor")
            field.isAccessible = true
            val editorObj = field.get(this)
            val redo = editorObj.javaClass.getMethod("redo")
            redo.invoke(editorObj)
        }.onFailure { toast("Redo tidak tersedia pada perangkat ini") }
    }

    internal fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Bagikan file"))
    }

    // ---------- GITHUB ZIP PUBLISHER ----------
    // Tiga layar dalam satu halaman: Pengaturan -> Proses Upload -> Upload Selesai.

    internal data class GhProgress(val step: Int, val detail: String, val current: Int = 0, val total: Int = 0)

    internal data class GhResult(
        val owner: String, val repo: String, val branch: String,
        val files: Int, val bytes: Long, val commitSha: String,
        val url: String, val createdRepo: Boolean, val createdPrivate: Boolean
    )

    internal var ghStage: FrameLayout? = null
    internal var ghSource = 0
    internal var ghBranch = "main"
    internal var ghSaveToken = true
    internal var ghPrivateRepo = true
    internal var ghRunning = false
    internal var ghStartedAt = 0L
    internal var ghCurrentStep = 0
    internal var ghFileTotal = 0
    internal var ghRetry: (() -> Unit)? = null
    internal var ghLastResult: GhResult? = null
    internal var ghUserValue = ""
    internal var ghRepoValue = ""
    internal var ghTokenValue = ""
    internal var ghCommitValue = ""
    internal var ghRing: ProgressRingView? = null
    internal var ghPercentText: TextView? = null
    internal var ghElapsedText: TextView? = null
    internal var ghNoteBox: LinearLayout? = null
    internal var ghErrorHost: LinearLayout? = null
    internal val ghStepViews = ArrayList<StepStateView>()
    internal val ghStepTitles = ArrayList<TextView>()
    internal val ghStepDetails = ArrayList<TextView>()
    internal val ghHandler = Handler(Looper.getMainLooper())
    internal val ghTicker = object : Runnable {
        override fun run() {
            if (!ghRunning) return
            val sec = ((SystemClock.elapsedRealtime() - ghStartedAt) / 1000L).toInt()
            ghElapsedText?.text = "Berjalan %02d:%02d".format(sec / 60, sec % 60)
            ghHandler.postDelayed(this, 1000L)
        }
    }

    // ----- warna & helper kecil (mengikuti tema terang/gelap aplikasi) -----
    internal fun ghc(dark: Long, light: Long): Int = (if (isDarkTheme) dark else light).toInt()
    internal val ghInk: Int get() = ghc(0xFFF4F4F6, 0xFF15161A)
    internal val ghOnInk: Int get() = ghc(0xFF15161A, 0xFFFFFFFF)
    internal val ghCard: Int get() = ghc(0xFF1B1D22, 0xFFFFFFFF)
    internal val ghStroke: Int get() = ghc(0xFF34373F, 0xFFE3E5EA)
    internal val ghMuted: Int get() = ghc(0xFF9DA0A9, 0xFF6C717C)
    internal val ghSoft: Int get() = ghc(0xFF23262C, 0xFFF0F1F4)
    internal val ghDanger: Int get() = 0xFFD9534F.toInt()

    internal fun ghRound(fill: Int, radiusDp: Int, stroke: Int? = null, strokeDp: Int = 1): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(strokeDp), stroke)
        }

    internal fun ghText(text: String, sp: Float, color: Int = ghInk, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = text
        textSize = sp
        setTextColor(color)
        includeFontPadding = false
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    internal fun ghIcon(name: String, sp: Float, color: Int = ghInk): MdiIconView =
        MdiIconView(this).apply { setIconName(name); setIconSize(sp); setTextColor(color) }

    internal fun ghLogo(sizeDp: Int, color: Int): PublishLogoView =
        PublishLogoView(this).apply { this.color = color; layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)) }

    internal fun ghFormatBytes(bytes: Long): String {
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> "%.2f GB".format(Locale.US, bytes / gb)
            bytes >= mb -> "%.1f MB".format(Locale.US, bytes / mb)
            bytes >= kb -> "%.1f KB".format(Locale.US, bytes / kb)
            else -> "$bytes B"
        }
    }

    internal fun ghHideKeyboard(v: View) {
        runCatching {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(v.windowToken, 0)
        }
    }

    internal fun ghWatch(edit: EditText, onChange: () -> Unit) {
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: android.text.Editable?) { onChange() }
        })
    }

    // ----- komponen form -----
    internal class GhField(val root: LinearLayout, val edit: EditText, val error: TextView, val row: LinearLayout)

    internal fun ghFieldBg(focused: Boolean, error: Boolean): GradientDrawable =
        ghRound(ghCard, 16, if (error) ghDanger else if (focused) ghInk else ghStroke, if (focused || error) 2 else 1)

    internal fun ghField(labelText: String, iconName: String, hint: String, initial: String, password: Boolean = false): GhField {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ghText(labelText, 13f, ghInk, true), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(7); leftMargin = dp(2) })
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            background = ghFieldBg(false, false)
        }
        row.addView(ghIcon(iconName, 20f, ghMuted), LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(10) })
        val edit = EditText(this).apply {
            this.hint = hint
            setText(initial)
            textSize = 15f
            setTextColor(ghInk)
            setHintTextColor(ghMuted)
            background = null
            setPadding(0, 0, 0, 0)
            maxLines = 1
            setSingleLine(true)
            inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        row.addView(edit, LinearLayout.LayoutParams(0, dp(54), 1f))
        if (password) {
            val eye = ghIcon("eye-outline", 20f, ghMuted).apply {
                isClickable = true
                setOnClickListener {
                    val visible = edit.transformationMethod == null
                    val cursor = edit.selectionStart
                    if (visible) {
                        edit.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
                        setIconName("eye-outline")
                    } else {
                        edit.transformationMethod = null
                        setIconName("eye-off-outline")
                    }
                    edit.setSelection(cursor.coerceAtLeast(0).coerceAtMost(edit.text.length))
                }
            }
            row.addView(eye, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        root.addView(row, LinearLayout.LayoutParams(-1, -2))
        val error = ghText("", 12f, ghDanger).apply { visibility = View.GONE; setPadding(dp(4), dp(6), 0, 0) }
        root.addView(error, LinearLayout.LayoutParams(-1, -2))
        root.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        val field = GhField(root, edit, error, row)
        edit.setOnFocusChangeListener { _, hasFocus -> row.background = ghFieldBg(hasFocus, error.visibility == View.VISIBLE) }
        ghWatch(edit) { ghClearError(field) }
        return field
    }

    internal fun ghSetError(field: GhField, message: String) {
        field.error.text = message
        field.error.visibility = View.VISIBLE
        field.row.background = ghFieldBg(field.edit.hasFocus(), true)
        field.row.animate().cancel()
        field.row.translationX = 0f
        field.row.animate().translationX(dp(6).toFloat()).setDuration(50).withEndAction {
            field.row.animate().translationX(-dp(4).toFloat()).setDuration(60).withEndAction {
                field.row.animate().translationX(0f).setDuration(50).start()
            }.start()
        }.start()
    }

    internal fun ghClearError(field: GhField) {
        if (field.error.visibility == View.VISIBLE) {
            field.error.visibility = View.GONE
            field.row.background = ghFieldBg(field.edit.hasFocus(), false)
        }
    }

    internal fun ghPressable(view: View, onClick: () -> Unit) {
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            view.animate().scaleX(0.98f).scaleY(0.98f).setDuration(60).withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
            }.start()
            onClick()
        }
    }

    internal fun ghPrimaryButton(text: String, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = ghRound(ghInk, 18)
        addView(ghLogo(22, ghOnInk), LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(12) })
        addView(ghText(text, 15f, ghOnInk, true))
        addView(ghIcon("arrow-right", 18f, ghOnInk), LinearLayout.LayoutParams(dp(22), dp(22)).apply { leftMargin = dp(10) })
        layoutParams = LinearLayout.LayoutParams(-1, dp(56)).apply { topMargin = dp(6); bottomMargin = dp(10) }
        ghPressable(this, onClick)
    }

    internal fun ghOutlineButton(text: String, iconName: String, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = ghRound(ghCard, 18, ghStroke)
        addView(ghIcon(iconName, 19f, ghInk), LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) })
        addView(ghText(text, 15f, ghInk, true))
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { bottomMargin = dp(10) }
        ghPressable(this, onClick)
    }

    internal fun ghSmallButton(text: String, iconName: String, onClick: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        background = ghRound(ghSoft, 12)
        setPadding(dp(12), 0, dp(12), 0)
        addView(ghIcon(iconName, 17f, ghInk), LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(6) })
        addView(ghText(text, 13f, ghInk, true))
        ghPressable(this, onClick)
    }

    internal fun ghSwitchRow(iconName: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(2), 0, dp(2))
        }
        row.addView(ghIcon(iconName, 18f, ghMuted), LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(10) })
        row.addView(ghText(text, 13.5f, ghMuted), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this).apply {
            isChecked = checked
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            trackTintList = android.content.res.ColorStateList(states, intArrayOf(ghInk, ghStroke))
            thumbTintList = android.content.res.ColorStateList(states, intArrayOf(ghOnInk, ghCard))
            setOnCheckedChangeListener { v, value -> v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onChange(value) }
        }
        row.addView(sw, LinearLayout.LayoutParams(-2, dp(40)))
        return row
    }

    internal fun ghSectionTitle(text: String): TextView =
        ghText(text, 12.5f, ghMuted, true).apply { setPadding(dp(2), dp(6), 0, dp(10)) }

    // ----- pergantian layar -----
    internal fun ghShow(screen: View, titleText: String) {
        val branch = prefs.getString("gh_branch", null) ?: "main"
        ghSaveToken = prefs.getBoolean("gh_save_token", true)
        ghPrivateRepo = prefs.getBoolean("gh_private", true)
        ghTokenValue = if (ghSaveToken) prefs.getString("gh_token_enc", null)?.let { GithubTokenVault.decrypt(it) }.orEmpty() else ""
        ghCommitValue = prefs.getString("gh_commit", null) ?: "Upload project via GITLS"
        ghStage = FrameLayout(this)
        content.addView(ghStage, LinearLayout.LayoutParams(-1, -2))
        ghShow(ghSettingsScreen(), "Pengaturan GitHub")
    }

    internal fun ghSettingsScreen(): LinearLayout {
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(2), dp(4), dp(2), dp(24)) }

        // kepala: logo + penjelasan singkat
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = ghRound(ghSoft, 20)
        }
        head.addView(ghLogo(38, ghInk), LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(14) })
        val headTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headTexts.addView(ghText("GITLS Publisher", 16f, ghInk, true))
        headTexts.addView(ghText("Kirim ZIP atau folder project ke repository GitHub tanpa perintah Git.", 12f, ghMuted).apply { setPadding(0, dp(4), 0, 0) })
        head.addView(headTexts, LinearLayout.LayoutParams(0, -2, 1f))
        screen.addView(head, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })

        val user = ghField("Username", "account-outline", "username GitHub", ghUserValue)
        val repo = ghField("Repository", "source-repository", "nama repository", ghRepoValue)
        screen.addView(user.root)
        screen.addView(repo.root)

        // branch (dropdown)
        screen.addView(ghText("Branch", 13f, ghInk, true), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(7); leftMargin = dp(2) })
        val branchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(14), 0)
            background = ghFieldBg(false, false)
        }
        branchRow.addView(ghIcon("source-branch", 20f, ghMuted), LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(10) })
        val branchText = ghText(ghBranch, 15f, ghInk)
        branchRow.addView(branchText, LinearLayout.LayoutParams(0, -2, 1f))
        branchRow.addView(ghIcon("chevron-down", 20f, ghMuted), LinearLayout.LayoutParams(dp(24), dp(24)))
        ghPressable(branchRow) {
            ghHideKeyboard(branchRow)
            val options = listOf("main", "master", "develop", "Lainnya…")
            AlertDialog.Builder(this)
                .setTitle("Pilih branch")
                .setItems(options.toTypedArray()) { _, which ->
                    if (which < options.size - 1) {
                        ghBranch = options[which]
                        branchText.text = ghBranch
                    } else {
                        val input = EditText(this).apply { setText(ghBranch); setSingleLine(true); setPadding(dp(20), dp(14), dp(20), dp(14)) }
                        AlertDialog.Builder(this)
                            .setTitle("Nama branch")
                            .setView(input)
                            .setNegativeButton("Batal", null)
                            .setPositiveButton("Pakai") { _, _ ->
                                val value = input.text.toString().trim()
                                if (value.matches(Regex("[A-Za-z0-9._/-]+"))) { ghBranch = value; branchText.text = value }
                                else toast("Nama branch tidak valid")
                            }.show()
                    }
                }.show()
        }
        screen.addView(branchRow, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(16) })

        val token = ghField("Token (Personal Access Token)", "key-variant", "ghp_••••••••••••", ghTokenValue, password = true)
        token.root.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
        screen.addView(token.root)
        screen.addView(ghSwitchRow("information-outline", "Simpan token (terenkripsi)", ghSaveToken) { ghSaveToken = it })
        screen.addView(ghSwitchRow("lock-outline", "Repository baru dibuat private", ghPrivateRepo) { ghPrivateRepo = it })

        val commit = ghField("Pesan commit", "text-box-outline", "Upload project via GITLS", ghCommitValue)
        commit.root.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(8) }
        screen.addView(commit.root)

        // sumber project
        screen.addView(ghSectionTitle("Sumber project"))
        screen.addView(ghSourcePicker())

        // status
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(10), dp(4), dp(14))
        }
        statusRow.addView(ghIcon("information-outline", 16f, ghMuted), LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(8) })
        val statusText = ghText("Pilih ZIP atau folder untuk memulai.", 12f, ghMuted)
        githubUploadStatus = statusText
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        screen.addView(statusRow)

        screen.addView(ghPrimaryButton("Simpan & Upload") {
            ghHideKeyboard(screen)
            val owner = user.edit.text.toString().trim()
            val repository = repo.edit.text.toString().trim()
            val pat = token.edit.text.toString().trim()
            val message = commit.edit.text.toString().trim()
            var valid = true
            if (!owner.matches(Regex("[A-Za-z0-9_.-]+"))) { ghSetError(user, "Username hanya boleh huruf, angka, titik, garis"); valid = false }
            if (!repository.matches(Regex("[A-Za-z0-9_.-]+"))) { ghSetError(repo, "Nama repository tidak valid"); valid = false }
            if (pat.isBlank()) { ghSetError(token, "Token wajib diisi"); valid = false }
            if (!valid) return@ghPrimaryButton
            if (!validateGithubInputs(owner, repository, pat, ghBranch)) return@ghPrimaryButton
            val commitMessage = message.ifBlank { "Upload project via GITLS" }
            val zipUri = githubZipUri
            val folderUri = githubFolderUri
            if (ghSource == 0 && zipUri == null) { toast("Pilih file ZIP terlebih dahulu"); return@ghPrimaryButton }
            if (ghSource == 0 && githubZipPreviewFiles.isEmpty()) { toast("Tunggu analisis ZIP selesai atau pilih ZIP lagi"); return@ghPrimaryButton }
            if (ghSource == 1 && folderUri == null) { toast("Pilih folder project terlebih dahulu"); return@ghPrimaryButton }

            ghUserValue = owner; ghRepoValue = repository; ghTokenValue = pat; ghCommitValue = commitMessage
            ghSaveSettings(owner, repository, pat, commitMessage)

            val branch = ghBranch
            val makePrivate = ghPrivateRepo
            if (ghSource == 0 && zipUri != null) {
                val root = githubZipRoot
                val excluded = githubZipExcluded.toSet()
                ghStartUpload("ZIP", owner, repository, branch) { p -> uploadZipToGitHub(zipUri, owner, repository, pat, branch, commitMessage, root, excluded, makePrivate, p) }
            } else if (folderUri != null) {
                ghStartUpload("Folder", owner, repository, branch) { p -> uploadFolderToGitHub(folderUri, owner, repository, pat, branch, commitMessage, makePrivate, p) }
            }
        })

        screen.addView(
            ghText("Token dipakai langsung untuk request ke GitHub. Bila \"Simpan token\" mati, token tidak disimpan sama sekali. Butuh izin Contents read/write (classic: scope repo).", 11f, ghMuted)
                .apply { setPadding(dp(4), dp(4), dp(4), 0); setLineSpacing(0f, 1.15f) }
        )
        return screen
    }

    internal fun ghSaveSettings(owner: String, repository: String, pat: String, commitMessage: String) {
        val editor = prefs.edit()
            .putString("gh_user", owner).putString("gh_repo", repository).putString("gh_branch", ghBranch)
            .putString("gh_commit", commitMessage)
            .putBoolean("gh_save_token", ghSaveToken).putBoolean("gh_private", ghPrivateRepo)
        if (ghSaveToken) {
            val enc = GithubTokenVault.encrypt(pat)
            if (enc != null) editor.putString("gh_token_enc", enc) else editor.remove("gh_token_enc")
        } else {
            editor.remove("gh_token_enc")
        }
        editor.apply()
    }

    // ----- pemilih sumber: ZIP / Folder -----
    internal fun ghSourcePicker(): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = ghRound(ghSoft, 16)
        }
        val zipPanel = ghZipPanel()
        val folderPanel = ghFolderPanel()
        val tabViews = ArrayList<LinearLayout>()
        fun paintTabs() {
            tabViews.forEachIndexed { index, tab ->
                val selected = index == ghSource
                tab.background = if (selected) ghRound(ghCard, 12, ghStroke) else null
                for (i in 0 until tab.childCount) {
                    val child = tab.getChildAt(i)
                    if (child is MdiIconView) child.setTextColor(if (selected) ghInk else ghMuted)
                    if (child is TextView && child !is MdiIconView) child.setTextColor(if (selected) ghInk else ghMuted)
                }
            }
            zipPanel.visibility = if (ghSource == 0) View.VISIBLE else View.GONE
            folderPanel.visibility = if (ghSource == 1) View.VISIBLE else View.GONE
        }
        fun tab(label: String, iconName: String, index: Int): LinearLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(ghIcon(iconName, 18f, ghMuted), LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(8) })
            addView(ghText(label, 14f, ghMuted, true))
            isClickable = true
            setOnClickListener {
                if (ghSource != index) {
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    ghSource = index
                    paintTabs()
                    val panel = if (index == 0) zipPanel else folderPanel
                    panel.alpha = 0f
                    panel.animate().alpha(1f).setDuration(200).start()
                }
            }
        }
        tabViews.add(tab("File ZIP", "folder-zip-outline", 0))
        tabViews.add(tab("Folder", "folder-open-outline", 1))
        tabViews.forEach { tabs.addView(it, LinearLayout.LayoutParams(0, dp(42), 1f)) }
        box.addView(tabs, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        box.addView(zipPanel)
        box.addView(folderPanel)
        paintTabs()
        return box
    }

    internal fun ghSourceCard(iconName: String, titleView: TextView, hintText: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = ghRound(ghCard, 18, ghStroke)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badge = ghIcon(iconName, 22f, ghInk).apply { background = ghRound(ghSoft, 14) }
        top.addView(badge, LinearLayout.LayoutParams(dp(46), dp(46)).apply { rightMargin = dp(14) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(titleView.apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
        texts.addView(ghText(hintText, 12f, ghMuted).apply { setPadding(0, dp(4), 0, 0) })
        top.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(top)
        return card
    }

    internal fun ghZipPanel(): LinearLayout {
        val zipTitle = ghText("Belum ada ZIP dipilih", 14.5f, ghInk, true)
        githubZipLabel = zipTitle
        val card = ghSourceCard("folder-zip-outline", zipTitle, "Preview isi, atur root, dan kecualikan file.")
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(14), 0, 0) }
        actions.addView(ghSmallButton("Pilih ZIP", "folder-open-outline") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "application/zip"; addCategory(Intent.CATEGORY_OPENABLE) }, GITHUB_ZIP_PICK_REQUEST)
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(8) })
        actions.addView(ghSmallButton("Kelola isi", "file-tree-outline") {
            val uri = githubZipUri
            if (uri == null) toast("Pilih ZIP terlebih dahulu") else showGithubZipPreview(uri)
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        card.addView(actions)
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(card) }
    }

    internal fun ghFolderPanel(): LinearLayout {
        val folderTitle = ghText("Belum ada folder dipilih", 14.5f, ghInk, true)
        githubFolderLabel = folderTitle
        val card = ghSourceCard("folder-outline", folderTitle, "Semua file dan subfolder ikut terkirim.")
        val previewText = ghText("Isi folder akan tampil di sini.", 11.5f, ghMuted).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = ghRound(ghSoft, 12)
            setLineSpacing(0f, 1.2f)
            maxLines = 9
        }
        githubFolderPreview = previewText
        card.addView(previewText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(12), 0, 0) }
        actions.addView(ghSmallButton("Pilih folder", "folder-open-outline") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, GITHUB_FOLDER_PICK_REQUEST)
        }, LinearLayout.LayoutParams(-1, dp(40)))
        card.addView(actions)
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(card) }
    }

    internal fun validateGithubInputs(owner: String, repository: String, pat: String, branch: String): Boolean {
        if (!owner.matches(Regex("[A-Za-z0-9_.-]+"))) { toast("Username GitHub tidak valid"); return false }
        if (!repository.matches(Regex("[A-Za-z0-9_.-]+"))) { toast("Nama repository tidak valid"); return false }
        if (pat.isBlank()) { toast("Masukkan GitHub token"); return false }
        if (!branch.matches(Regex("[A-Za-z0-9._/-]+"))) { toast("Nama branch tidak valid"); return false }
        return true
    }

    // =====================================================================
    // Layar 2: Proses Upload (animasi)
    // =====================================================================
    internal fun ghStepTitle(index: Int, branch: String): String = when (index) {
        0 -> "Menghubungkan ke GitHub"
        1 -> "Membuat repository (jika belum ada)"
        2 -> "Mengunggah file"
        3 -> "Push ke branch $branch"
        else -> "Verifikasi hasil upload"
    }

    internal fun ghProgressScreen(kind: String, owner: String, repo: String, branch: String): LinearLayout {
        ghStepViews.clear(); ghStepTitles.clear(); ghStepDetails.clear()
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(8), dp(2), dp(24)) }

        val ringBox = FrameLayout(this)
        val ring = ProgressRingView(this).apply {
            ringColor = ghInk
            trackColor = ghSoft
            indeterminate = true
        }
        ghRing = ring
        ringBox.addView(ring, FrameLayout.LayoutParams(-1, -1))
        val center = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        center.addView(ghLogo(54, ghInk), LinearLayout.LayoutParams(dp(54), dp(54)).apply { bottomMargin = dp(8) })
        ghPercentText = ghText("0%", 28f, ghInk, true).apply {
            // Keep the percentage exactly centered under the upload icon.
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        center.addView(ghPercentText, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(2)
        })
        ringBox.addView(center, FrameLayout.LayoutParams(-1, -1))
        screen.addView(ringBox, LinearLayout.LayoutParams(dp(210), dp(210)).apply { topMargin = dp(6); bottomMargin = dp(14) })

        val target = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = ghRound(ghSoft, 14)
        }
        target.addView(ghIcon("source-repository", 15f, ghMuted), LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(6) })
        target.addView(ghText("$owner/$repo", 12.5f, ghInk, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
        target.addView(ghIcon("source-branch", 15f, ghMuted), LinearLayout.LayoutParams(dp(18), dp(18)).apply { leftMargin = dp(12); rightMargin = dp(4) })
        target.addView(ghText(branch, 12.5f, ghMuted))
        screen.addView(target, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(22) })

        val steps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, dp(6), 0) }
        for (i in 0..4) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(9), 0, dp(9)) }
            val state = StepStateView(this).apply {
                inkColor = ghInk; onInkColor = ghOnInk; mutedColor = ghStroke; dangerColor = ghDanger
                setState(StepStateView.PENDING, false)
            }
            ghStepViews.add(state)
            row.addView(state, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(14); topMargin = dp(1) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val stepTitle = ghText(ghStepTitle(i, branch), 14.5f, ghMuted)
            val stepDetail = ghText("", 11.5f, ghMuted).apply { visibility = View.GONE; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE; setPadding(0, dp(4), 0, 0) }
            ghStepTitles.add(stepTitle); ghStepDetails.add(stepDetail)
            col.addView(stepTitle); col.addView(stepDetail)
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            steps.addView(row)
        }
        screen.addView(steps, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })

        val note = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = ghRound(ghSoft, 16)
        }
        val spinner = StepStateView(this).apply {
            inkColor = ghInk; onInkColor = ghOnInk; mutedColor = ghStroke; dangerColor = ghDanger
            setState(StepStateView.ACTIVE, false)
        }
        note.addView(spinner, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(12) })
        val noteTexts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        noteTexts.addView(ghText("Mohon tunggu, proses ini mungkin memakan waktu…", 12.5f, ghMuted).apply { setLineSpacing(0f, 1.1f) })
        ghElapsedText = ghText("Berjalan 00:00", 11.5f, ghMuted).apply { setPadding(0, dp(4), 0, 0) }
        noteTexts.addView(ghElapsedText)
        note.addView(noteTexts, LinearLayout.LayoutParams(0, -2, 1f))
        ghNoteBox = note
        screen.addView(note, LinearLayout.LayoutParams(-1, -2))

        ghErrorHost = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        screen.addView(ghErrorHost, LinearLayout.LayoutParams(-1, -2))
        return screen
    }

    internal fun ghStartUpload(kind: String, owner: String, repo: String, branch: String, task: ((GhProgress) -> Unit) -> GhResult) {
        if (ghRunning) { toast("Upload sedang berjalan"); return }
        ghRetry = { ghStartUpload(kind, owner, repo, branch, task) }
        ghShow(ghProgressScreen(kind, owner, repo, branch), "Proses Upload")
        ghRunning = true
        ghCurrentStep = 0
        ghFileTotal = 0
        ghStartedAt = SystemClock.elapsedRealtime()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ghHandler.removeCallbacks(ghTicker)
        ghHandler.postDelayed(ghTicker, 1000L)
        ghOnProgress(GhProgress(0, "Menyiapkan $kind…"))
        thread {
            val result = runCatching { task { p -> runOnUiThread { ghOnProgress(p) } } }
            runOnUiThread {
                ghRunning = false
                ghHandler.removeCallbacks(ghTicker)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                result.onSuccess { ghShowResult(it) }.onFailure { ghShowFailure(it) }
            }
        }
    }

    internal fun ghOnProgress(p: GhProgress) {
        if (ghStage?.isAttachedToWindow != true || ghStepViews.size < 5) return
        if (p.step < ghCurrentStep) return
        ghCurrentStep = p.step
        for (i in 0..4) {
            val state = when {
                i < p.step -> StepStateView.DONE
                i == p.step -> StepStateView.ACTIVE
                else -> StepStateView.PENDING
            }
            ghStepViews[i].setState(state)
            ghStepTitles[i].setTextColor(if (i <= p.step) ghInk else ghMuted)
            if (i == p.step) ghStepTitles[i].setTypeface(null, android.graphics.Typeface.BOLD)
            else ghStepTitles[i].setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        if (p.step == 2 && p.total > 0) {
            ghFileTotal = p.total
            ghStepTitles[2].text = "Mengunggah file (${p.current}/${p.total})"
        }
        if (p.step > 2 && ghFileTotal > 0) {
            ghStepTitles[2].text = "Mengunggah file ($ghFileTotal/$ghFileTotal)"
            ghStepDetails[2].text = "$ghFileTotal file terunggah"
            ghStepDetails[2].visibility = View.VISIBLE
        }
        if (p.detail.isNotBlank() && !(p.step == 2 && p.total == 0)) {
            ghStepDetails[p.step].text = p.detail
            ghStepDetails[p.step].visibility = View.VISIBLE
        }
        val pct = when (p.step) {
            0 -> 4
            1 -> 12
            2 -> if (p.total > 0) 15 + (70 * (p.current - 1).coerceAtLeast(0)) / p.total else 15
            3 -> 88
            else -> 96
        }
        ghRing?.let { it.indeterminate = false; it.setProgress(pct.toFloat()) }
        ghPercentText?.text = "$pct%"
    }

    internal fun ghShowFailure(error: Throwable) {
        if (ghStage?.isAttachedToWindow != true) return
        val failedStep = ghCurrentStep.coerceIn(0, 4)
        if (ghStepViews.size == 5) {
            ghStepViews[failedStep].setState(StepStateView.FAILED)
            ghStepTitles[failedStep].setTextColor(ghDanger)
        }
        ghRing?.let { it.indeterminate = false; it.ringColor = ghDanger }
        ghNoteBox?.visibility = View.GONE
        val host = ghErrorHost ?: return
        host.removeAllViews()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = ghRound(ghCard, 16, ghDanger)
        }
        card.addView(ghText("Upload gagal", 15f, ghDanger, true))
        card.addView(ghText(ghFriendlyError(error), 12.5f, ghMuted).apply { setPadding(0, dp(6), 0, 0); setLineSpacing(0f, 1.15f) })
        host.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        host.addView(ghPrimaryButton("Coba lagi") { ghRetry?.invoke() })
        host.addView(ghOutlineButton("Ubah pengaturan", "cog-outline") { ghShow(ghSettingsScreen(), "Pengaturan GitHub") })
        host.alpha = 0f
        host.animate().alpha(1f).setDuration(240).start()
        toast("Upload GitHub gagal")
    }

    internal fun ghFriendlyError(error: Throwable): String {
        val raw = error.message ?: error.javaClass.simpleName
        return when {
            raw.contains("HTTP 401") -> "Token ditolak GitHub. Periksa token atau masa berlakunya. ($raw)"
            raw.contains("HTTP 403") -> "Akses ditolak. Pastikan token punya izin Contents read/write untuk repository ini. ($raw)"
            raw.contains("HTTP 404") -> "Repository atau branch tidak ditemukan, atau token tidak punya akses. ($raw)"
            raw.contains("HTTP 422") -> "GitHub menolak data yang dikirim. ($raw)"
            error is java.net.UnknownHostException || error is java.net.SocketTimeoutException -> "Tidak bisa menjangkau GitHub. Periksa koneksi internet lalu coba lagi."
            else -> raw
        }
    }

    // =====================================================================
    // Layar 3: Upload Selesai
    // =====================================================================
    internal fun ghShowResult(result: GhResult) {
        if (ghStage?.isAttachedToWindow != true) return
        ghLastResult = result
        val elapsed = ((SystemClock.elapsedRealtime() - ghStartedAt) / 1000L).toInt()
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(14), dp(2), dp(24)) }

        val badge = SuccessBadgeView(this).apply { inkColor = ghInk; onInkColor = ghOnInk }
        screen.addView(badge, LinearLayout.LayoutParams(dp(92), dp(92)).apply { bottomMargin = dp(18) })
        screen.addView(ghText("Upload Berhasil!", 24f, ghInk, true))
        val subtitle = if (result.createdRepo) "Repository baru dibuat (${if (result.createdPrivate) "private" else "public"}) dan project berhasil diunggah."
        else "Project berhasil diunggah ke GitHub"
        screen.addView(ghText(subtitle, 13f, ghMuted).apply { gravity = Gravity.CENTER; setPadding(dp(20), dp(8), dp(20), 0); setLineSpacing(0f, 1.15f) })

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(6))
            background = ghRound(ghCard, 20, ghStroke)
        }
        fun infoRow(iconName: String, labelText: String, valueText: String, trailing: String? = null, onClick: (() -> Unit)? = null, last: Boolean = false) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(13), 0, dp(13))
            }
            row.addView(ghIcon(iconName, 22f, ghInk), LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(14) })
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(ghText(labelText, 11.5f, ghMuted))
            col.addView(ghText(valueText, 15f, ghInk, true).apply { setPadding(0, dp(3), 0, 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE })
            row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            if (trailing != null) row.addView(ghIcon(trailing, 19f, ghMuted), LinearLayout.LayoutParams(dp(26), dp(26)))
            if (onClick != null) ghPressable(row, onClick)
            card.addView(row)
            if (!last) card.addView(View(this).apply { setBackgroundColor(ghStroke) }, LinearLayout.LayoutParams(-1, dp(1)).apply { leftMargin = dp(44) })
        }
        infoRow("source-repository", "Repository", "${result.owner}/${result.repo}", "open-in-new", { ghOpenUrl(result.url) })
        infoRow("source-branch", "Branch", result.branch)
        infoRow("file-tree-outline", "Total File", "${result.files} file")
        infoRow("harddisk", "Ukuran", ghFormatBytes(result.bytes))
        infoRow("source-commit", "Commit", result.commitSha.take(7), "content-copy", {
            ghCopy("Commit SHA", result.commitSha); toast("SHA commit disalin")
        })
        infoRow("timer-outline", "Durasi", if (elapsed >= 60) "${elapsed / 60} mnt ${elapsed % 60} dtk" else "$elapsed dtk", last = true)
        screen.addView(card, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24); bottomMargin = dp(18) })

        screen.addView(ghPrimaryButton("Lihat di GitHub") { ghOpenUrl(result.url + "/tree/" + result.branch) })
        screen.addView(ghOutlineButton("Upload Lagi", "upload-network") { ghShow(ghSettingsScreen(), "Pengaturan GitHub") })
        screen.addView(ghOutlineButton("Salin tautan repository", "link-variant") { ghCopy("Repository", result.url); toast("Tautan disalin") })

        ghShow(screen, "Upload Selesai")
        badge.postDelayed({ badge.play() }, 120L)
        screen.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        toast("Upload GitHub selesai")
    }

    internal fun ghOpenUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { toast("Tidak ada aplikasi untuk membuka tautan") }
    }

    internal fun ghCopy(labelText: String, value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(labelText, value))
    }

    // =====================================================================
    // Mesin upload: satu jalur untuk ZIP dan folder
    // =====================================================================
    internal fun ghHeaders(token: String) = mapOf(
        "Authorization" to "Bearer $token",
        "Accept" to "application/vnd.github+json",
        "X-GitHub-Api-Version" to "2022-11-28",
        "User-Agent" to "GITLS-Android"
    )

    /** Request dengan percobaan ulang untuk gangguan jaringan (bukan untuk error 4xx). */
    internal fun githubRequestRetry(method: String, url: String, body: JSONObject?, headers: Map<String, String>, attempts: Int = 3): JSONObject {
        var last: IOException? = null
        for (i in 1..attempts) {
            try {
                return githubRequest(method, url, body, headers)
            } catch (e: IOException) {
                val message = e.message.orEmpty()
                if (message.startsWith("GitHub HTTP 4")) throw e
                last = e
                if (i < attempts) Thread.sleep(700L * i)
            }
        }
        throw last ?: IOException("Request gagal")
    }

    internal fun pushFilesToGitHub(
        files: List<Pair<String, File>>, owner: String, repo: String, token: String, branch: String,
        commitMessage: String, createPrivate: Boolean, progress: (GhProgress) -> Unit
    ): GhResult {
        require(files.isNotEmpty()) { "Tidak ada file yang dapat di-upload" }
        require(files.size <= 3000) { "Maksimal 3000 file per upload" }
        val base = "https://api.github.com/repos/${Uri.encode(owner)}/${Uri.encode(repo)}"
        val headers = ghHeaders(token)

        // 1. hubungkan & periksa token
        progress(GhProgress(0, "Memeriksa token…"))
        val me = try {
            githubRequestRetry("GET", "https://api.github.com/user", null, headers)
        } catch (e: IOException) {
            if (e.message.orEmpty().contains("HTTP 401")) throw e else JSONObject()
        }
        val login = me.optString("login")
        progress(GhProgress(0, if (login.isNotBlank()) "Terhubung sebagai $login" else "Terhubung"))

        // 2. repository
        progress(GhProgress(1, "Memeriksa $owner/$repo…"))
        val repoInfo: JSONObject? = try {
            githubRequestRetry("GET", base, null, headers)
        } catch (e: IOException) {
            if (e.message.orEmpty().contains("HTTP 404")) null else throw e
        }
        var created = false
        var htmlUrl = repoInfo?.optString("html_url").orEmpty()
        if (repoInfo == null) {
            require(login.equals(owner, ignoreCase = true)) {
                "Repository $owner/$repo belum ada, dan token milik ${login.ifBlank { "akun lain" }} sehingga tidak bisa membuatnya otomatis."
            }
            progress(GhProgress(1, "Membuat repository ${if (createPrivate) "private" else "public"}…"))
            val made = githubRequestRetry("POST", "https://api.github.com/user/repos",
                JSONObject().put("name", repo).put("private", createPrivate).put("auto_init", true)
                    .put("description", "Dibuat lewat GITLS Publisher"), headers)
            created = true
            htmlUrl = made.optString("html_url")
            Thread.sleep(800L)
        } else {
            val empty = try {
                githubRequest("GET", "$base/git/trees/HEAD", null, headers); false
            } catch (e: IOException) {
                e.message.orEmpty().contains("HTTP 409")
            }
            if (empty) {
                progress(GhProgress(1, "Repository masih kosong, membuat commit awal…"))
                val readme = android.util.Base64.encodeToString("# $repo\n".toByteArray(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP)
                githubRequestRetry("PUT", "$base/contents/README.md", JSONObject().put("message", "Initial commit").put("content", readme), headers)
            } else {
                progress(GhProgress(1, "Repository ditemukan"))
            }
        }
        if (htmlUrl.isBlank()) htmlUrl = "https://github.com/$owner/$repo"

        val ref = runCatching { githubRequest("GET", "$base/git/ref/heads/${encodePath(branch)}", null, headers) }.getOrNull()
        val parentSha = ref?.optJSONObject("object")?.optString("sha").orEmpty()
        var baseTree = ""
        if (parentSha.isNotBlank()) {
            val parent = githubRequestRetry("GET", "$base/git/commits/$parentSha", null, headers)
            baseTree = parent.optJSONObject("tree")?.optString("sha").orEmpty()
        }

        // 3. unggah file
        val entries = JSONArray()
        var totalBytes = 0L
        files.forEachIndexed { index, (rel, file) ->
            val size = file.length()
            require(size <= 90L * 1024L * 1024L) { "File terlalu besar untuk upload API: $rel" }
            progress(GhProgress(2, rel, index + 1, files.size))
            val blobBody = JSONObject()
                .put("content", android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP))
                .put("encoding", "base64")
            val blob = githubRequestRetry("POST", "$base/git/blobs", blobBody, headers)
            val sha = blob.optString("sha")
            require(sha.isNotBlank()) { "Gagal membuat blob untuk $rel" }
            totalBytes += size
            entries.put(JSONObject().put("path", rel).put("mode", "100644").put("type", "blob").put("sha", sha))
        }

        // 4. tree, commit, push
        progress(GhProgress(3, "Membuat Git tree…", files.size, files.size))
        val treeBody = JSONObject().put("tree", entries)
        if (baseTree.isNotBlank()) treeBody.put("base_tree", baseTree)
        val treeSha = githubRequestRetry("POST", "$base/git/trees", treeBody, headers).optString("sha")
        require(treeSha.isNotBlank()) { "Gagal membuat Git tree" }
        progress(GhProgress(3, "Membuat commit…", files.size, files.size))
        val commitBody = JSONObject().put("message", commitMessage).put("tree", treeSha)
        if (parentSha.isNotBlank()) commitBody.put("parents", JSONArray().put(parentSha))
        val newSha = githubRequestRetry("POST", "$base/git/commits", commitBody, headers).optString("sha")
        require(newSha.isNotBlank()) { "Gagal membuat commit" }
        progress(GhProgress(3, "Memperbarui branch $branch…", files.size, files.size))
        if (parentSha.isBlank()) {
            githubRequestRetry("POST", "$base/git/refs", JSONObject().put("ref", "refs/heads/$branch").put("sha", newSha), headers)
        } else {
            githubRequestRetry("PATCH", "$base/git/refs/heads/${encodePath(branch)}", JSONObject().put("sha", newSha).put("force", false), headers)
        }

        // 5. verifikasi
        progress(GhProgress(4, "Memeriksa commit di GitHub…", files.size, files.size))
        val check = githubRequestRetry("GET", "$base/git/ref/heads/${encodePath(branch)}", null, headers)
        val remoteSha = check.optJSONObject("object")?.optString("sha").orEmpty()
        require(remoteSha == newSha) { "Verifikasi gagal: branch $branch belum menunjuk ke commit terbaru" }
        progress(GhProgress(4, "Commit ${newSha.take(7)} terverifikasi", files.size, files.size))

        return GhResult(owner, repo, branch, files.size, totalBytes, newSha, htmlUrl.trimEnd('/'), created, createPrivate)
    }

    internal fun uploadFolderToGitHub(
        treeUri: Uri, owner: String, repo: String, token: String, branch: String,
        commitMessage: String, createPrivate: Boolean, progress: (GhProgress) -> Unit
    ): GhResult {
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(this, treeUri) ?: error("Folder tidak dapat dibuka")
        val workDir = File(cacheDir, "github_folder_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val localRoot = File(workDir, "project").apply { mkdirs() }
            var count = 0
            fun copyTree(dir: androidx.documentfile.provider.DocumentFile, target: File) {
                dir.listFiles().forEach { child ->
                    val name = child.name ?: return@forEach
                    if (name == ".git" || name == "__MACOSX" || name == ".DS_Store" || name == "Thumbs.db") return@forEach
                    val out = File(target, name)
                    if (child.isDirectory) { out.mkdirs(); copyTree(child, out) }
                    else if (child.isFile) {
                        out.parentFile?.mkdirs()
                        contentResolver.openInputStream(child.uri)?.use { input -> FileOutputStream(out).use { output -> input.copyTo(output) } } ?: error("Tidak bisa membaca $name")
                        count++
                        if (count % 10 == 0) progress(GhProgress(0, "Membaca folder… $count file"))
                    }
                }
            }
            progress(GhProgress(0, "Membaca isi folder yang dipilih…"))
            copyTree(root, localRoot)
            require(count > 0) { "Folder tidak berisi file yang bisa di-upload" }
            val files = localRoot.walkTopDown().filter { it.isFile }.map { f ->
                localRoot.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/') to f
            }.filter { (rel, _) -> !rel.startsWith(".git/") && rel != ".git" && !rel.startsWith("__MACOSX/") && !rel.endsWith(".DS_Store") && !rel.endsWith("Thumbs.db") }.toList()
            return pushFilesToGitHub(files, owner, repo, token, branch, commitMessage, createPrivate, progress)
        } finally { workDir.deleteRecursively() }
    }

    internal data class GithubZipAnalysis(
        val files: List<String>,
        val dirs: List<String>,
        val suggestedRoot: String,
        val excludedDefaults: Set<String>
    )

    internal fun prepareGithubZipPreview(uri: Uri) {
        thread {
            val result = runCatching {
                val workDir = File(cacheDir, "github_preview_${System.currentTimeMillis()}").apply { mkdirs() }
                try {
                    val zipFile = File(workDir, "preview.zip")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(zipFile).use { output -> input.copyTo(output) }
                    } ?: error("ZIP tidak dapat dibaca")
                    val extracted = File(workDir, "src").apply { mkdirs() }
                    unzipSafeForGithub(zipFile, extracted)
                    analyzeGithubZip(extracted)
                } finally {
                    workDir.deleteRecursively()
                }
            }
            runOnUiThread {
                result.onSuccess { analysis ->
                    githubZipPreviewFiles = analysis.files
                    githubZipPreviewDirs = analysis.dirs
                    githubZipExcluded.clear()
                    githubZipExcluded.addAll(analysis.excludedDefaults)
                    githubZipRoot = analysis.suggestedRoot
                    githubZipUploadSummary()
                    githubUploadStatus?.text = "ZIP dianalisis: ${analysis.files.size} file. Root: ${if (analysis.suggestedRoot.isBlank()) "/" else analysis.suggestedRoot + "/"}"
                }.onFailure { e ->
                    githubUploadStatus?.text = "Analisis ZIP gagal: ${e.message ?: "Unknown error"}"
                }
            }
        }
    }

    internal fun analyzeGithubZip(extracted: File): GithubZipAnalysis {
        val files = extracted.walkTopDown()
            .filter { it.isFile }
            .map { extracted.toPath().relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
            .filter { it.isNotBlank() }
            .sorted()
            .toList()
        require(files.isNotEmpty()) { "ZIP tidak berisi file yang bisa di-upload" }

        val dirs = extracted.walkTopDown()
            .filter { it.isDirectory && it != extracted }
            .map { extracted.toPath().relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
            .filter { it.isNotBlank() }
            .sorted()
            .toList()

        val excluded = files.filter {
            it == ".git" || it.startsWith(".git/") ||
            it == "__MACOSX" || it.startsWith("__MACOSX/") ||
            it == ".DS_Store" || it.endsWith("/.DS_Store") ||
            it == "Thumbs.db" || it.endsWith("/Thumbs.db")
        }.toSet()

        val top = extracted.listFiles()?.toList().orEmpty()
        val topDirs = top.filter { it.isDirectory }.map { it.name }.sorted()
        val topFiles = top.filter { it.isFile }
        var suggested = ""
        if (topDirs.size == 1 && topFiles.isEmpty()) {
            val wrapper = topDirs.first()
            val wrapperDir = File(extracted, wrapper)
            val children = wrapperDir.listFiles()?.toList().orEmpty()
            val childDirs = children.filter { it.isDirectory }.map { it.name }.sorted()
            val childFiles = children.filter { it.isFile }
            suggested = if (childDirs.size == 1 && childFiles.isEmpty() && childDirs.first().equals("web", true)) {
                "$wrapper/${childDirs.first()}"
            } else wrapper
        }
        return GithubZipAnalysis(files, dirs, suggested, excluded)
    }

    internal fun githubZipUploadSummary() {
        val root = githubZipRoot.trim('/').trim()
        val count = githubZipPreviewFiles.count { path ->
            !githubZipExcluded.any { excluded -> path == excluded || path.startsWith("$excluded/") } &&
            (root.isBlank() || path == root || path.startsWith("$root/"))
        }
        githubUploadStatus?.text = "Siap: $count file akan di-upload • Root: ${if (root.isBlank()) "/" else root + "/"} • Exclude: ${githubZipExcluded.size}"
    }

    internal fun showGithubZipPreview(uri: Uri) {
        if (githubZipPreviewFiles.isEmpty()) {
            githubUploadStatus?.text = "Menganalisis ZIP..."
            prepareGithubZipPreview(uri)
            return
        }

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 8, 28, 8)
        }
        val rootLabel = TextView(this).apply {
            text = "Repository root"
            textSize = 13f
            setTextColor(textMuted)
        }
        box.addView(rootLabel)

        val rootSpinner = Spinner(this)
        val roots = listOf("/ (root ZIP)") + githubZipPreviewDirs.map { "$it/" }
        val currentRoot = githubZipRoot.trim('/').let { if (it.isBlank()) "/ (root ZIP)" else "$it/" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roots)
        rootSpinner.adapter = adapter
        rootSpinner.setSelection(maxOf(0, roots.indexOf(currentRoot)))
        rootSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                githubZipRoot = if (position == 0) "" else roots[position].trimEnd('/')
                githubZipUploadSummary()
            }
        }
        box.addView(rootSpinner)
        box.addView(subLabel("Folder pembungkus otomatis dideteksi. Pilih folder yang akan menjadi root repository. Tombol × mengecualikan file/folder dari upload.", 11f))

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        githubZipPreviewFiles.forEach { path ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(8, 2, 2, 2)
            }
            val label = TextView(this).apply {
                text = if (githubZipExcluded.any { ex -> path == ex || path.startsWith("$ex/") }) "⊘ $path" else "• $path"
                textSize = 12f
                setTextColor(if (githubZipExcluded.any { ex -> path == ex || path.startsWith("$ex/") }) textMuted else textMain)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val remove = TextView(this).apply {
                text = if (githubZipExcluded.contains(path)) "✓" else "×"
                textSize = 20f
                gravity = Gravity.CENTER
                setPadding(18, 8, 12, 8)
            }
            remove.setOnClickListener {
                if (githubZipExcluded.contains(path)) githubZipExcluded.remove(path) else githubZipExcluded.add(path)
                label.text = if (githubZipExcluded.any { ex -> path == ex || path.startsWith("$ex/") }) "⊘ $path" else "• $path"
                label.setTextColor(if (githubZipExcluded.any { ex -> path == ex || path.startsWith("$ex/") }) textMuted else textMain)
                remove.text = if (githubZipExcluded.contains(path)) "✓" else "×"
                githubZipUploadSummary()
            }
            row.addView(label)
            row.addView(remove)
            list.addView(row)
        }
        scroll.addView(list)
        val previewHeight = (420 * resources.displayMetrics.density).roundToInt()
        box.addView(scroll, LinearLayout.LayoutParams(-1, previewHeight))

        AlertDialog.Builder(this)
            .setTitle("Isi ZIP • ${githubZipPreviewFiles.size} file")
            .setView(box)
            .setNegativeButton("Tutup", null)
            .setPositiveButton("Simpan Pilihan", null)
            .show()
    }

    internal fun uploadZipToGitHub(
        uri: Uri,
        owner: String,
        repo: String,
        token: String,
        branch: String,
        commitMessage: String,
        uploadRoot: String,
        excludedPaths: Set<String>,
        createPrivate: Boolean,
        progress: (GhProgress) -> Unit
    ): GhResult {
        val workDir = File(cacheDir, "github_zip_${System.currentTimeMillis()}").apply { mkdirs() }
        val zipFile = File(workDir, "upload.zip")
        try {
            progress(GhProgress(0, "Membaca file ZIP…"))
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(zipFile).use { output -> input.copyTo(output) }
            } ?: error("ZIP tidak dapat dibaca")
            progress(GhProgress(0, "Mengekstrak ZIP…"))
            val extracted = File(workDir, "src").apply { mkdirs() }
            unzipSafeForGithub(zipFile, extracted)

            val rootPath = uploadRoot.trim('/').trim()
            val files = extracted.walkTopDown()
                .filter { it.isFile }
                .map { f -> extracted.toPath().relativize(f.toPath()).toString().replace(File.separatorChar, '/') to f }
                .filter { (rel, _) ->
                    val inRoot = rootPath.isBlank() || rel == rootPath || rel.startsWith("$rootPath/")
                    val relativeForExclude = if (rootPath.isBlank()) rel else rel.removePrefix("$rootPath/")
                    val excluded = excludedPaths.any { ex ->
                        val normalized = ex.trim('/').replace('\\', '/')
                        rel == normalized || rel.startsWith("$normalized/") ||
                            relativeForExclude == normalized || relativeForExclude.startsWith("$normalized/")
                    }
                    inRoot && !excluded &&
                        !rel.startsWith(".git/") && !rel.startsWith("__MACOSX/") &&
                        rel != ".git" && rel != "__MACOSX" &&
                        rel != ".DS_Store" && !rel.endsWith("/.DS_Store") &&
                        rel != "Thumbs.db" && !rel.endsWith("/Thumbs.db")
                }
                .map { (rel, f) -> (if (rootPath.isBlank()) rel else rel.removePrefix("$rootPath/")) to f }
                .toList()
            require(files.isNotEmpty()) { "Tidak ada file yang tersisa untuk di-upload dari root yang dipilih" }
            require(files.size <= 3000) { "ZIP terlalu banyak file (maksimal 3000)" }
            return pushFilesToGitHub(files, owner, repo, token, branch, commitMessage, createPrivate, progress)
        } finally {
            workDir.deleteRecursively()
        }
    }

    internal fun githubRequest(method: String, url: String, body: JSONObject?, headers: Map<String, String>): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            doInput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        try {
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message") }.getOrDefault(response.take(240))
                throw IOException("GitHub HTTP $code: ${message.ifBlank { "Request gagal" }}")
            }
            return if (response.isBlank()) JSONObject() else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }

    internal fun encodePath(path: String): String = path.split('/').joinToString("/") { Uri.encode(it) }

    internal fun unzipSafeForGithub(zip: File, dest: File) {
        extractZipSafely(zip, dest, 5000, 256L * 1024L * 1024L, 64L * 1024L * 1024L)
    }

    internal fun zipPath(src: File, out: File) {
        val srcCanonical = src.canonicalFile
        val outCanonical = out.canonicalFile
        require(srcCanonical != outCanonical) { "File sumber dan ZIP tujuan tidak boleh sama" }
        outCanonical.parentFile?.mkdirs()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outCanonical))).use { zos ->
            if (srcCanonical.isFile) {
                zos.putNextEntry(ZipEntry(srcCanonical.name))
                srcCanonical.inputStream().use { it.copyTo(zos, 8192) }
                zos.closeEntry()
            } else {
                val base = srcCanonical.parentFile?.toPath() ?: srcCanonical.toPath()
                srcCanonical.walkTopDown()
                    .filter { it.isFile && it.canonicalFile != outCanonical }
                    .forEach { file ->
                        val entryName = base.relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                        zos.putNextEntry(ZipEntry(entryName))
                        file.inputStream().use { it.copyTo(zos, 8192) }
                        zos.closeEntry()
                    }
            }
        }
    }

    internal fun unzipSafe(zip: File, dest: File) {
        extractZipSafely(zip, dest, 5000, 256L * 1024L * 1024L, 64L * 1024L * 1024L)
    }

    private fun extractZipSafely(
        zip: File,
        dest: File,
        maxEntries: Int,
        maxTotalBytes: Long,
        maxEntryBytes: Long
    ) {
        val destCanonical = dest.canonicalFile
        require(destCanonical != zip.canonicalFile) { "Folder tujuan tidak boleh sama dengan ZIP sumber" }
        require(destCanonical.mkdirs() || destCanonical.isDirectory) { "Folder tujuan tidak dapat dibuat" }
        var totalBytes = 0L
        var entries = 0
        ZipInputStream(BufferedInputStream(FileInputStream(zip))).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                entries++
                require(entries <= maxEntries) { "ZIP terlalu banyak entry" }
                val normalized = entry.name.replace('\\', '/')
                require(normalized.isNotBlank() && !normalized.startsWith('/')) { "ZIP entry tidak aman: ${entry.name}" }
                require(normalized.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "ZIP entry tidak aman: ${entry.name}" }
                val target = File(destCanonical, normalized).canonicalFile
                require(target.path == destCanonical.path || target.path.startsWith(destCanonical.path + File.separator)) {
                    "ZIP entry di luar folder tujuan: ${entry.name}"
                }
                if (entry.isDirectory) {
                    require(target.mkdirs() || target.isDirectory) { "Gagal membuat folder: ${entry.name}" }
                } else {
                    require(target.parentFile?.mkdirs() != false) { "Gagal membuat folder induk: ${entry.name}" }
                    var entryBytes = 0L
                    FileOutputStream(target).use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = zis.read(buffer)
                            if (count <= 0) break
                            entryBytes += count
                            totalBytes += count
                            require(entryBytes <= maxEntryBytes) { "ZIP entry terlalu besar: ${entry.name}" }
                            require(totalBytes <= maxTotalBytes) { "Total isi ZIP terlalu besar" }
                            out.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }

    internal fun colorTab(text: String, active: Boolean) = TextView(this).apply {
        this.text = text
        textSize = 12f
        gravity = Gravity.CENTER
        setTextColor(if (active) Color.WHITE else textMain)
        background = bg(if (active) Color.rgb(15, 15, 16) else Color.TRANSPARENT, 14)
        isClickable = true
    }

    internal fun buildPhotoColorWorkspace(workspace: LinearLayout) {
        val actionRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(colorActionButton("Galeri") { openColorPhotoGallery() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(5) })
        actionRow.addView(colorActionButton("Kamera") { openColorPhotoCamera() }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(5) })
        workspace.addView(actionRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val photoFrame = FrameLayout(this).apply {
            background = bg(Color.rgb(244, 246, 248), 22, Color.rgb(224, 229, 233))
            clipChildren = true
            clipToPadding = true
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(239, 242, 245))
            contentDescription = "Foto untuk ekstraksi warna"
        }
        colorPhotoView = image
        photoFrame.addView(image, FrameLayout.LayoutParams(-1, dp(250)))

        val placeholder = FrameLayout(this).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            isClickable = true
            isFocusable = true
            setOnClickListener { openColorPhotoGallery() }
        }
        val plusButton = TextView(this).apply {
            text = "+"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = bg(Color.WHITE, 99, Color.rgb(205, 209, 214))
            elevation = dp(3).toFloat()
            contentDescription = "Pilih foto dari galeri"
            setOnClickListener { openColorPhotoGallery() }
        }
        placeholder.addView(plusButton, FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER))
        colorPhotoPlaceholder = placeholder
        photoFrame.addView(placeholder, FrameLayout.LayoutParams(-1, dp(250)))

        val marker = View(this).apply {
            background = bg(colorPhotoSelected, 99, Color.WHITE)
            visibility = View.GONE
            elevation = dp(4).toFloat()
        }
        colorPhotoMarker = marker
        photoFrame.addView(marker, FrameLayout.LayoutParams(dp(28), dp(28)))
        workspace.addView(photoFrame, LinearLayout.LayoutParams(-1, dp(250)).apply { bottomMargin = dp(10) })

        val status = subLabel("Ketuk atau geser lingkaran pada foto untuk mengambil warna piksel.", 11f)
        colorPhotoStatus = status
        workspace.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val paletteMode = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val mainMode = colorActionButton("Utama 8") { extractPhotoPalette(colorPhotoBitmap, 8) }
        val extendedMode = colorActionButton("Detail 32") { extractPhotoPalette(colorPhotoBitmap, 32) }
        paletteMode.addView(mainMode, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        paletteMode.addView(extendedMode, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) })
        workspace.addView(paletteMode, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })

        val paletteCard = colorSectionCard("Palet Warna", "Ekstraksi berbasis clustering warna: Utama 8 warna paling dominan, Detail sampai 32 warna yang lebih beragam.")

        // Header Palet Warna: tombol ">" membuka layer khusus yang menampilkan seluruh palet.
        val paletteTitle = paletteCard.getChildAt(0)
        paletteCard.removeViewAt(0)
        val paletteHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        paletteHeader.addView(paletteTitle, LinearLayout.LayoutParams(0, -2, 1f))
        paletteHeader.addView(TextView(this).apply {
            text = ">"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            background = bg(Color.TRANSPARENT, 99)
            isClickable = true
            isFocusable = true
            contentDescription = "Buka semua palet warna"
            setPadding(dp(10), 0, dp(4), 0)
            setOnClickListener { showFullPaletteLayer() }
        }, LinearLayout.LayoutParams(dp(44), dp(42)))
        paletteCard.addView(paletteHeader, 0)

        val palette = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        colorPhotoPalette = palette
        paletteCard.addView(palette, LinearLayout.LayoutParams(-1, -2))
        val exportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        exportRow.addView(colorActionButton("Ekspor JSON") { requestColorPaletteExport("json") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(4) })
        exportRow.addView(colorActionButton("Ekspor TXT") { requestColorPaletteExport("txt") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(4) })
        paletteCard.addView(exportRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        workspace.addView(paletteCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val selectedCard = colorSectionCard("Warna yang Dipilih", "HEX, RGB, HSL, HSV + kode Android/Flutter + pengecekan kontras.")
        val selectedRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val selectedSwatch = View(this).apply { background = bg(colorPhotoSelected, 18) }
        selectedRow.addView(selectedSwatch, LinearLayout.LayoutParams(dp(64), dp(64)).apply { rightMargin = dp(12) })
        val values = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val hex = label("#19191B", 20f, true); colorPhotoHex = hex
        val rgb = subLabel("RGB 25, 25, 27", 12f); colorPhotoRgb = rgb
        val hsl = subLabel("HSL —", 12f); colorPhotoHsl = hsl
        val hsv = subLabel("HSV —", 12f)
        values.addView(hex); values.addView(rgb); values.addView(hsl); values.addView(hsv)
        selectedRow.addView(values, LinearLayout.LayoutParams(0, -2, 1f))
        selectedCard.addView(selectedRow)

        val contrast = subLabel("Kontras: pilih warna untuk melihat kecocokan teks hitam/putih.", 11f)
        selectedCard.addView(contrast, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })

        val codeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val flutter = colorCodeChip("Flutter", "Color(0xFF19191B)")
        val android = colorCodeChip("Android", "0xFF19191B")
        codeRow.addView(flutter, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        codeRow.addView(android, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        selectedCard.addView(codeRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        val copyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val copyHex = colorActionButton("Salin HEX") { colorPhotoHex?.text?.toString()?.let { copyText(it) } }
        val copyAll = colorActionButton("Salin Semua") {
            val c = colorPhotoSelected; copyText(colorDetailsText(c))
        }
        val fav = colorActionButton("Simpan") { saveColorHistory(colorPhotoSelected); toast("Warna disimpan") }
        copyRow.addView(copyHex, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(3) })
        copyRow.addView(copyAll, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        copyRow.addView(fav, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(3) })
        selectedCard.addView(copyRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        workspace.addView(selectedCard)

        fun updateSelected(color: Int, x: Float? = null, y: Float? = null) {
            colorPhotoSelected = Color.rgb(Color.red(color), Color.green(color), Color.blue(color))
            selectedSwatch.background = bg(colorPhotoSelected, 18)
            val r = Color.red(colorPhotoSelected); val g = Color.green(colorPhotoSelected); val b = Color.blue(colorPhotoSelected)
            val hslValue = rgbToHsl(r, g, b)
            val hsvValue = FloatArray(3); Color.colorToHSV(colorPhotoSelected, hsvValue)
            val hx = "#%02X%02X%02X".format(Locale.US, r, g, b)
            hex.text = hx
            rgb.text = "RGB $r, $g, $b"
            hsl.text = "HSL ${fmt(hslValue[0])}°, ${fmt(hslValue[1])}%, ${fmt(hslValue[2])}%"
            hsv.text = "HSV ${fmt(hsvValue[0].toDouble())}°, ${fmt((hsvValue[1]*100).toDouble())}%, ${fmt((hsvValue[2]*100).toDouble())}%"
            flutter.text = "Flutter\nColor(0xFF${hx.removePrefix("#")})"
            android.text = "Android\n0xFF${hx.removePrefix("#")}"
            contrast.text = contrastSummary(colorPhotoSelected)
            marker.background = bg(colorPhotoSelected, 99, Color.WHITE)
            if (x != null && y != null) {
                marker.visibility = View.VISIBLE
                marker.x = x - dp(14); marker.y = y - dp(14)
                status.text = "Dipilih • $hx • pipet manual"
            }
        }

        image.setOnTouchListener { v, event ->
            val bitmap = colorPhotoBitmap ?: return@setOnTouchListener false
            if (event.action != MotionEvent.ACTION_DOWN && event.action != MotionEvent.ACTION_MOVE && event.action != MotionEvent.ACTION_UP) return@setOnTouchListener true
            val bw = bitmap.width.toFloat(); val bh = bitmap.height.toFloat()
            val vw = v.width.toFloat(); val vh = v.height.toFloat()
            if (vw <= 0f || vh <= 0f) return@setOnTouchListener true
            val scale = min(vw / bw, vh / bh)
            val drawW = bw * scale; val drawH = bh * scale
            val left = (vw - drawW) / 2f; val top = (vh - drawH) / 2f
            val px = ((event.x - left) / scale).toInt().coerceIn(0, bitmap.width - 1)
            val py = ((event.y - top) / scale).toInt().coerceIn(0, bitmap.height - 1)
            updateSelected(sampleBitmap(bitmap, px, py), event.x, event.y)
            true
        }
        image.tag = placeholder
        colorPhotoSelectionUpdater = { c -> updateSelected(c) }
    }

    internal fun requestColorPaletteExport(format: String) {
        if (currentPhotoPalette.isEmpty()) { toast("Belum ada palet untuk diekspor"); return }
        pendingColorPaletteExportFormat = format
        val mime = if (format == "json") "application/json" else "text/plain"
        val ext = if (format == "json") "json" else "txt"
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = mime
            putExtra(Intent.EXTRA_TITLE, "mytools_palette.$ext")
            addCategory(Intent.CATEGORY_OPENABLE)
        }, COLOR_PALETTE_EXPORT_REQUEST)
    }

    internal fun colorDetailsText(color: Int): String {
        val r=Color.red(color); val g=Color.green(color); val b=Color.blue(color)
        val h=rgbToHsl(r,g,b); val hsv=FloatArray(3); Color.colorToHSV(color,hsv)
        return "HEX #%02X%02X%02X\nRGB $r, $g, $b\nHSL ${fmt(h[0])}°, ${fmt(h[1])}%, ${fmt(h[2])}%\nHSV ${fmt(hsv[0].toDouble())}°, ${fmt((hsv[1]*100).toDouble())}%, ${fmt((hsv[2]*100).toDouble())}%\n${contrastSummary(color)}".format(Locale.US, r,g,b)
    }

    internal fun relativeLuminance(color: Int): Double {
        fun channel(v: Int): Double { val x=v/255.0; return if(x<=0.03928) x/12.92 else Math.pow((x+0.055)/1.055,2.4) }
        return 0.2126*channel(Color.red(color)) + 0.7152*channel(Color.green(color)) + 0.0722*channel(Color.blue(color))
    }

    internal fun contrastRatio(a: Int, b: Int): Double {
        val l1=relativeLuminance(a); val l2=relativeLuminance(b)
        val hi=maxOf(l1,l2); val lo=minOf(l1,l2); return (hi+0.05)/(lo+0.05)
    }

    internal fun contrastSummary(color: Int): String {
        val black=contrastRatio(color, Color.BLACK); val white=contrastRatio(color, Color.WHITE)
        val blackOk=black>=4.5; val whiteOk=white>=4.5
        val blackText=if(blackOk) "COCOK" else "kurang"
        val whiteText=if(whiteOk) "COCOK" else "kurang"
        return "Kontras teks: Hitam ${String.format(Locale.US,"%.2f",black)}:1 ($blackText) • Putih ${String.format(Locale.US,"%.2f",white)}:1 ($whiteText)"
    }

    internal fun colorSectionCard(titleText: String, subtitleText: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = bg(Color.WHITE, 20, Color.rgb(226, 230, 234))
        addView(label(titleText, 16f, true))
        addView(subLabel(subtitleText, 11f), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2); bottomMargin = dp(8) })
    }

    internal fun colorActionButton(textValue: String, onClick: () -> Unit) = Button(this).apply {
        text = textValue
        textSize = 12f
        setTextColor(Color.WHITE)
        background = bg(Color.rgb(15, 15, 16), 14)
        setStateListAnimator(null)
        setOnClickListener { scalePress(this); onClick() }
    }

    internal fun scalePress(view: View) {
        view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).withEndAction {
            view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
        }.start()
    }

    internal fun colorCodeChip(titleText: String, code: String) = TextView(this).apply {
        text = "$titleText\n$code"
        textSize = 10f
        setTextColor(textMain)
        setPadding(dp(11), dp(8), dp(11), dp(8))
        background = bg(Color.rgb(245, 247, 249), 14, Color.rgb(230, 234, 238))
    }

    internal fun buildScreenPickerWorkspace(workspace: LinearLayout) {
        val preview = FrameLayout(this).apply { background = bg(Color.rgb(244, 246, 248), 22, Color.rgb(224,229,233)) }
        val swatch = View(this).apply { background = bg(Color.rgb(120, 120, 124), 22) }
        val marker = TextView(this).apply { text = "•"; gravity = Gravity.CENTER; textSize = 28f; setTextColor(Color.WHITE); background = bg(Color.rgb(120, 120, 124), 30, Color.WHITE) }
        preview.addView(swatch, FrameLayout.LayoutParams(dp(92), dp(92), Gravity.CENTER))
        preview.addView(marker, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END).apply { topMargin=dp(16); rightMargin=dp(16) })
        workspace.addView(preview, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin=dp(10) })
        val status = label("Pipet belum aktif", 15f, true).apply { gravity=Gravity.CENTER }
        workspace.addView(status, LinearLayout.LayoutParams(-1, dp(42)).apply { bottomMargin=dp(7) })
        workspace.addView(colorActionButton("AKTIFKAN PIPET") { activateColorPicker() }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin=dp(10) })
        val hex = label("HEX  —  Belum ada warna", 16f, true)
        val rgb = subLabel("RGB  —  -", 13f)
        val hsl = subLabel("HSL  —  -", 13f)
        workspace.addView(hex); workspace.addView(rgb); workspace.addView(hsl)
        workspace.addView(colorActionButton("SALIN HEX") {
            val value=hex.text.toString().substringAfter("HEX  —  ").trim(); if(value.startsWith("#")) copyText(value) else toast("Belum ada warna")
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin=dp(8) })
        colorPickerUiUpdater = { color ->
            swatch.setBackgroundColor(color); marker.background=bg(color,30,Color.WHITE)
            val r=Color.red(color); val g=Color.green(color); val b=Color.blue(color); val h=rgbToHsl(r,g,b); val hx="#%02X%02X%02X".format(Locale.US,r,g,b)
            hex.text="HEX  —  $hx"; rgb.text="RGB  —  $r, $g, $b"; hsl.text="HSL  —  ${fmt(h[0])}°, ${fmt(h[1])}%, ${fmt(h[2])}%"; status.text="Pipet aktif  •  $hx"
        }
    }

    internal fun buildColorConverterWorkspace(workspace: LinearLayout) {
        val wheel = ColorWheelView(this)
        workspace.addView(wheel, LinearLayout.LayoutParams(-1, dp(220)).apply { bottomMargin=dp(10) })
        val preview = View(this).apply { background = bg(Color.rgb(23,32,42), 22) }
        wheel.onColorChanged = { preview.setBackgroundColor(it) }
        workspace.addView(preview, LinearLayout.LayoutParams(-1, dp(72)).apply { bottomMargin=dp(10) })
        val input = edit("#RRGGBB"); workspace.addView(input)
        workspace.addView(colorActionButton("HEX → RGB / HSL") {
            val raw=input.text.toString().trim()
            runCatching {
                val h=raw.removePrefix("#"); require(h.length==6 || h.length==8); val off=if(h.length==8)2 else 0
                val c=Color.rgb(h.substring(off,off+2).toInt(16),h.substring(off+2,off+4).toInt(16),h.substring(off+4,off+6).toInt(16))
                preview.setBackgroundColor(c); val r=Color.red(c); val g=Color.green(c); val b=Color.blue(c); val hsl=rgbToHsl(r,g,b)
                output("HEX = #${h.toUpperCase(Locale.US)}\nRGB = $r, $g, $b\nHSL = ${fmt(hsl[0])}°, ${fmt(hsl[1])}%, ${fmt(hsl[2])}%\nFlutter = Color(0xFF${h.takeLast(6).toUpperCase(Locale.US)})\nAndroid = 0xFF${h.takeLast(6).toUpperCase(Locale.US)}")
            }.onFailure { output("HEX tidak valid") }
        }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin=dp(8) })
        val rgb=edit("RGB: 255,255,255"); workspace.addView(rgb)
        workspace.addView(colorActionButton("RGB → HEX") {
            runCatching { val p=rgb.text.toString().split(",").map{it.trim().toInt()}; require(p.size==3 && p.all{it in 0..255}); output("#%02X%02X%02X".format(Locale.US,p[0],p[1],p[2])) }.onFailure { output("Format: 255,255,255") }
        }, LinearLayout.LayoutParams(-1, dp(50)))
    }

    internal inner class ColorWheelView(context: Context) : View(context) {
        internal val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        internal var selectedHue = 0f
        var onColorChanged: ((Int) -> Unit)? = null
        init { isClickable = true; setLayerType(View.LAYER_TYPE_SOFTWARE, null) }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx=width/2f; val cy=height/2f; val radius=(min(width,height)*.39f).coerceAtLeast(dp(55).toFloat())
            for (i in 0 until 360) {
                paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(18).toFloat(); paint.color=Color.HSVToColor(floatArrayOf(i.toFloat(),1f,1f))
                canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,i.toFloat(),1.4f,false,paint)
            }
            paint.style=Paint.Style.FILL; paint.color=Color.WHITE; paint.setShadowLayer(dp(3).toFloat(),0f,dp(1).toFloat(),0x55000000)
            canvas.drawCircle(cx,cy,dp(38).toFloat(),paint); paint.clearShadowLayer()
            val center=Color.HSVToColor(floatArrayOf(selectedHue,1f,1f)); paint.color=center; canvas.drawCircle(cx,cy,dp(30).toFloat(),paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=dp(3).toFloat(); paint.color=Color.WHITE
            val a=Math.toRadians(selectedHue.toDouble()); val sx=cx+Math.cos(a).toFloat()*radius; val sy=cy+Math.sin(a).toFloat()*radius
            canvas.drawCircle(sx,sy,dp(11).toFloat(),paint)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if(event.action!=MotionEvent.ACTION_DOWN && event.action!=MotionEvent.ACTION_MOVE && event.action!=MotionEvent.ACTION_UP) return true
            val cx=width/2f; val cy=height/2f; val dx=event.x-cx; val dy=event.y-cy; val d=Math.sqrt((dx*dx+dy*dy).toDouble()).toFloat(); val r=(min(width,height)*.39f).coerceAtLeast(dp(55).toFloat())
            if(d >= r-dp(24) && d <= r+dp(24)) { selectedHue=((Math.toDegrees(Math.atan2(dy.toDouble(),dx.toDouble()))+360)%360).toFloat(); onColorChanged?.invoke(Color.HSVToColor(floatArrayOf(selectedHue,1f,1f))); invalidate() }
            performClick(); return true
        }
        override fun performClick(): Boolean { super.performClick(); return true }
    }

    internal fun openColorPhotoGallery() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        startActivityForResult(intent, COLOR_PHOTO_PICK_REQUEST)
    }

    internal fun openColorPhotoCamera() {
        val intent=Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if(intent.resolveActivity(packageManager)==null){ toast("Kamera tidak tersedia"); return }
        startActivityForResult(intent, COLOR_PHOTO_CAMERA_REQUEST)
    }

    internal fun loadColorPhoto(bitmap: Bitmap) {
        val max=1600
        val scaled=if(bitmap.width>max || bitmap.height>max){ val s=min(max.toFloat()/bitmap.width,max.toFloat()/bitmap.height); Bitmap.createScaledBitmap(bitmap,(bitmap.width*s).toInt(),(bitmap.height*s).toInt(),true) } else bitmap
        colorPhotoBitmap=scaled
        colorPhotoView?.setImageBitmap(scaled)
        colorPhotoPlaceholder?.visibility = View.GONE
        colorPhotoMarker?.visibility=View.GONE
        colorPhotoStatus?.text="Foto siap • 8 warna utama + detail 32 warna + pipet manual"
        extractPhotoPalette(scaled, 8)
    }

    internal fun extractPhotoPalette(bitmap: Bitmap?, maxColors: Int) {
        val out = colorPhotoPalette ?: return
        if (bitmap == null) { toast("Pilih foto dulu"); return }
        out.removeAllViews()

        // Gunakan sampel terukur + K-Means RGB. Algoritma lama hanya memakai histogram
        // kuantisasi sehingga warna kecil tetapi jelas (mis. hijau) mudah tersisih.
        val workW = min(180, bitmap.width)
        val workH = maxOf(1, (bitmap.height.toFloat() * workW / bitmap.width).toInt())
        val thumb = Bitmap.createScaledBitmap(bitmap, workW, workH, true)
        val totalPixels = thumb.width * thumb.height
        val targetSamples = 5000
        val step = maxOf(1, kotlin.math.ceil(kotlin.math.sqrt(totalPixels / targetSamples.toDouble())).toInt())
        val samples = ArrayList<Int>(min(targetSamples, totalPixels))
        for (y in 0 until thumb.height step step) {
            for (x in 0 until thumb.width step step) {
                val c = thumb.getPixel(x, y)
                val a = Color.alpha(c)
                // Transparansi dibaurkan ke putih agar hasil JPG-like tidak menjadi hitam.
                val r = if (a == 255) Color.red(c) else (Color.red(c) * a + 255 * (255 - a)) / 255
                val g = if (a == 255) Color.green(c) else (Color.green(c) * a + 255 * (255 - a)) / 255
                val b = if (a == 255) Color.blue(c) else (Color.blue(c) * a + 255 * (255 - a)) / 255
                samples.add(Color.rgb(r, g, b))
            }
        }
        if (samples.isEmpty()) { thumb.recycle(); toast("Foto tidak memiliki piksel yang bisa dianalisis"); return }

        val k = min(maxColors.coerceAtLeast(1), samples.size)
        val centroids = ArrayList<FloatArray>(k)
        val used = HashSet<Int>()

        // Seed pertama = warna paling sering pada kuantisasi kasar.
        val coarse = HashMap<Int, Int>()
        samples.forEach { c ->
            val r = (Color.red(c) / 16) * 16 + 8
            val g = (Color.green(c) / 16) * 16 + 8
            val b = (Color.blue(c) / 16) * 16 + 8
            val q = Color.rgb(r.coerceAtMost(255), g.coerceAtMost(255), b.coerceAtMost(255))
            coarse[q] = (coarse[q] ?: 0) + 1
        }
        val first = coarse.maxByOrNull { it.value }?.key ?: samples[0]
        centroids.add(floatArrayOf(Color.red(first).toFloat(), Color.green(first).toFloat(), Color.blue(first).toFloat()))
        used.add(first)

        // Paksa satu seed dari warna paling jenuh agar warna aksen yang nyata
        // (misalnya hijau pada foto) tidak kalah oleh area abu-abu yang lebih luas.
        var accent = samples[0]
        var accentScore = -1f
        samples.forEach { c ->
            val hsv = FloatArray(3)
            Color.colorToHSV(c, hsv)
            if (hsv[1] > accentScore) { accentScore = hsv[1]; accent = c }
        }
        if (!used.contains(accent) && centroids.size < k) {
            centroids.add(floatArrayOf(Color.red(accent).toFloat(), Color.green(accent).toFloat(), Color.blue(accent).toFloat()))
            used.add(accent)
        }

        // Seed berikutnya memilih warna yang paling jauh dari centroid yang sudah ada.
        while (centroids.size < k) {
            var bestColor = samples[centroids.size % samples.size]
            var bestScore = -1.0
            for (c in samples) {
                if (used.contains(c)) continue
                var nearest = Double.MAX_VALUE
                for (m in centroids) {
                    val dr = Color.red(c).toDouble() - m[0].toDouble()
                    val dg = Color.green(c).toDouble() - m[1].toDouble()
                    val db = Color.blue(c).toDouble() - m[2].toDouble()
                    val d = dr * dr + dg * dg + db * db
                    if (d < nearest) nearest = d
                }
                if (nearest > bestScore) { bestScore = nearest; bestColor = c }
            }
            centroids.add(floatArrayOf(Color.red(bestColor).toFloat(), Color.green(bestColor).toFloat(), Color.blue(bestColor).toFloat()))
            used.add(bestColor)
        }

        val assignments = IntArray(samples.size)
        repeat(8) {
            val sumR = DoubleArray(k)
            val sumG = DoubleArray(k)
            val sumB = DoubleArray(k)
            val counts = IntArray(k)
            for (i in samples.indices) {
                val c = samples[i]
                var best = 0
                var bestDist = Double.MAX_VALUE
                for (j in 0 until k) {
                    val m = centroids[j]
                    val dr = Color.red(c).toDouble() - m[0].toDouble()
                    val dg = Color.green(c).toDouble() - m[1].toDouble()
                    val db = Color.blue(c).toDouble() - m[2].toDouble()
                    val d = dr * dr + dg * dg + db * db
                    if (d < bestDist) { bestDist = d; best = j }
                }
                assignments[i] = best
                sumR[best] += Color.red(c).toDouble()
                sumG[best] += Color.green(c).toDouble()
                sumB[best] += Color.blue(c).toDouble()
                counts[best]++
            }
            for (j in 0 until k) {
                if (counts[j] > 0) {
                    centroids[j][0] = (sumR[j] / counts[j]).toFloat()
                    centroids[j][1] = (sumG[j] / counts[j]).toFloat()
                    centroids[j][2] = (sumB[j] / counts[j]).toFloat()
                }
            }
        }

        val clusterCounts = IntArray(k)
        for (a in assignments) clusterCounts[a]++
        val chosen = ArrayList<Pair<Int, Int>>()
        val minDistance = if (maxColors <= 8) 22 else 10
        val ranked = (0 until k).sortedByDescending { clusterCounts[it] }
        for (idx in ranked) {
            if (clusterCounts[idx] <= 0) continue
            val c = Color.rgb(
                centroids[idx][0].roundToInt().coerceIn(0, 255),
                centroids[idx][1].roundToInt().coerceIn(0, 255),
                centroids[idx][2].roundToInt().coerceIn(0, 255)
            )
            if (chosen.all { colorDistance(it.first, c) >= minDistance }) chosen.add(c to clusterCounts[idx])
            if (chosen.size >= maxColors) break
        }

        val total = samples.size.coerceAtLeast(1)
        currentPhotoPalette.clear()
        chosen.forEach { (color, count) ->
            currentPhotoPalette.add(color to ((count * 100.0 / total).roundToInt().coerceAtLeast(1)))
        }

        // Palet dibuat satu baris horizontal agar semua warna dapat digeser kanan/kiri
        // dan tidak ada swatch yang terpotong di sisi layar. Berlaku untuk Utama 8 maupun Detail 32.
        val cellW = if (maxColors <= 8) dp(72) else dp(58)
        val swatch = if (maxColors <= 8) dp(46) else dp(36)
        val horizontal = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            clipToPadding = true
            clipChildren = true
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, dp(2), 0)
        }
        chosen.forEachIndexed { index, e ->
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(2), dp(3), dp(2), dp(3))
                isClickable = true
                isFocusable = true
                contentDescription = "Warna ${index + 1}, #%02X%02X%02X".format(Locale.US, Color.red(e.first), Color.green(e.first), Color.blue(e.first))
                setOnClickListener { colorPhotoSelectionUpdater?.invoke(e.first) }
            }
            box.addView(View(this).apply { background = bg(e.first, 10) }, LinearLayout.LayoutParams(swatch, swatch))
            box.addView(TextView(this).apply {
                text = "#%02X%02X%02X".format(Locale.US, Color.red(e.first), Color.green(e.first), Color.blue(e.first))
                textSize = if (maxColors <= 8) 8f else 7f
                setTextColor(textMain); gravity = Gravity.CENTER; maxLines = 1
            })
            box.addView(TextView(this).apply {
                text = "${(e.second * 100.0 / total).roundToInt().coerceAtLeast(1)}%"
                textSize = 7f; setTextColor(textMuted); gravity = Gravity.CENTER
            })
            row.addView(box, LinearLayout.LayoutParams(cellW, -2).apply {
                if (index > 0) leftMargin = dp(3)
            })
        }
        horizontal.isFillViewport = false
        horizontal.setOnTouchListener { _, event ->
            // Pastikan gesture horizontal tidak diambil ScrollView vertikal induk.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> horizontal.parent?.requestDisallowInterceptTouchEvent(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> horizontal.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        horizontal.addView(row, LinearLayout.LayoutParams(-2, -2))
        horizontal.post { horizontal.scrollTo(0, 0) }
        out.addView(horizontal, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dp(2)
            rightMargin = dp(2)
        })

        val swipeHint = subLabel("Geser kanan/kiri untuk melihat semua warna • ketuk warna untuk memilih", 10f)
        out.addView(swipeHint, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(3)
            bottomMargin = dp(2)
        })
        thumb.recycle()
        colorPhotoStatus?.text = "${chosen.size} warna terdeteksi • clustering detail aktif • ketuk warna untuk memilih"
    }

    /** Layer penuh untuk melihat seluruh warna hasil ekstraksi tanpa terpotong. */
    internal fun showFullPaletteLayer() {
        if (currentPhotoPalette.isEmpty()) {
            toast("Belum ada palet warna. Pilih foto dan lakukan ekstraksi dulu.")
            return
        }

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(16))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "‹"
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(textMain)
            isClickable = true
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(dp(46), dp(50)))
        header.addView(label("Semua Palet Warna", 20f, true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(subLabel("${currentPhotoPalette.size} warna", 11f), LinearLayout.LayoutParams(-2, -2))
        root.addView(header)

        root.addView(subLabel("Ketuk salah satu warna untuk menjadikannya warna terpilih dan melihat kode HEX/RGB/HSL/HSV.", 11f), LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(10)
        })

        val scrollGrid = ScrollView(this).apply {
            isFillViewport = true
        }
        val grid = GridLayout(this).apply {
            columnCount = 2
            useDefaultMargins = false
        }

        currentPhotoPalette.forEachIndexed { index, pair ->
            val color = pair.first
            val percent = pair.second
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = bg(Color.rgb(247, 248, 249), 16, Color.rgb(225, 229, 233))
                isClickable = true
                isFocusable = true
                contentDescription = "Pilih warna #%02X%02X%02X".format(Locale.US, Color.red(color), Color.green(color), Color.blue(color))
                setOnClickListener {
                    // Satu sumber pemilihan warna: update kartu "Warna yang Dipilih"
                    // di layer utama, lalu kembali ke halaman Color Tools.
                    colorPhotoSelectionUpdater?.invoke(color)
                    dialog.dismiss()
                    toast("Warna dipilih #%02X%02X%02X".format(Locale.US, Color.red(color), Color.green(color), Color.blue(color)))
                }
            }
            card.addView(View(this).apply { background = bg(color, 12) }, LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(10) })
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(label("#%02X%02X%02X".format(Locale.US, Color.red(color), Color.green(color), Color.blue(color)), 14f, true))
            info.addView(subLabel("${percent}% • warna ${index + 1}", 10f))
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = dp(78)
                columnSpec = GridLayout.spec(index % 2, 1f)
                rowSpec = GridLayout.spec(index / 2)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(card, params)
        }
        scrollGrid.addView(grid, FrameLayout.LayoutParams(-1, -2))
        root.addView(scrollGrid, LinearLayout.LayoutParams(-1, 0, 1f))

        root.addView(colorActionButton("TUTUP") { dialog.dismiss() }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.setLayout(-1, -1)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    internal fun colorDistance(a:Int,b:Int):Int {
        val dr=Color.red(a)-Color.red(b); val dg=Color.green(a)-Color.green(b); val db=Color.blue(a)-Color.blue(b)
        return kotlin.math.sqrt((dr*dr+dg*dg+db*db).toDouble()).toInt()
    }

    internal fun sampleBitmap(bitmap: Bitmap, x: Int, y: Int): Int {
        var sr=0; var sg=0; var sb=0; var count=0
        for(dy in -1..1) for(dx in -1..1){ val px=(x+dx).coerceIn(0,bitmap.width-1); val py=(y+dy).coerceIn(0,bitmap.height-1); val c=bitmap.getPixel(px,py); sr+=Color.red(c); sg+=Color.green(c); sb+=Color.blue(c); count++ }
        return Color.rgb(sr/count,sg/count,sb/count)
    }

    internal fun saveColorHistory(color: Int) {
        val hx="#%02X%02X%02X".format(Locale.US,Color.red(color),Color.green(color),Color.blue(color))
        val old=prefs.getString("color_history","")?.split(",")?.filter{it.isNotBlank()}?:emptyList()
        prefs.edit().putString("color_history",(listOf(hx)+old.filter{it!=hx}).take(24).joinToString(",")).apply()
    }

    // ==================== CALCULATOR SUITE ====================

    internal data class CalculatorMode(val id: String, val name: String, val group: String)

    internal val calculatorModes = listOf(
        CalculatorMode("basiccalc", "Dasar", "Utama"),
        CalculatorMode("scicalc", "Ilmiah", "Utama"),
        CalculatorMode("percentcalc", "Persentase", "Matematika"),
        CalculatorMode("fractioncalc", "Pecahan", "Matematika"),
        CalculatorMode("ratiocalc", "Rasio & Proporsi", "Matematika"),
        CalculatorMode("equationcalc", "Persamaan", "Matematika"),
        CalculatorMode("basecalc", "Basis Angka", "Matematika"),
        CalculatorMode("unitcalc", "Konverter Satuan", "Konversi"),
        CalculatorMode("datacalc", "Ukuran Data", "Konversi"),
        CalculatorMode("speedcalc", "Kecepatan", "Konversi"),
        CalculatorMode("pressurecalc", "Tekanan", "Konversi"),
        CalculatorMode("timecalc", "Durasi", "Tanggal & Waktu"),
        CalculatorMode("datecalc", "Tanggal & Umur", "Tanggal & Waktu"),
        CalculatorMode("worktimecalc", "Jam Kerja", "Tanggal & Waktu"),
        CalculatorMode("areacalc", "Luas & Keliling", "Geometri"),
        CalculatorMode("volumecalc", "Volume", "Geometri"),
        CalculatorMode("riskcalc", "Risk-Reward", "Finansial"),
        CalculatorMode("compoundcalc", "Compound & Tabungan", "Finansial"),
        CalculatorMode("margincalc", "Margin & Pajak", "Finansial"),
        CalculatorMode("discountcalc", "Diskon Bertingkat", "Finansial"),
        CalculatorMode("loancalc", "Cicilan Pinjaman", "Finansial"),
        CalculatorMode("fuelcalc", "Konsumsi BBM", "Finansial")
    )

    internal fun showCalculatorModePicker() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Pilih kalkulator")
            .setView(box)
            .setNegativeButton("Tutup", null)
            .create()

        var currentGroup: String? = null
        calculatorModes.forEach { mode ->
            if (mode.group != currentGroup) {
                currentGroup = mode.group
                box.addView(TextView(this).apply {
                    text = mode.group.uppercase(Locale.getDefault())
                    textSize = 11f
                    setTextColor(textMuted)
                    setPadding(dp(10), dp(10), dp(10), dp(6))
                })
            }
            box.addView(TextView(this).apply {
                text = if (mode.id == calculatorSelectedMode) "✓  ${mode.name}" else mode.name
                textSize = 15f
                setTextColor(textMain)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, dp(12), 0)
                background = bg(if (mode.id == calculatorSelectedMode) panel2 else panel, 12, line)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    calculatorSelectedMode = mode.id
                    dialog.dismiss()
                    calculatorHub(mode.id)
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(4) })
        }
        dialog.show()
    }

    internal fun calcDisplay(hint: String = "0"): EditText = EditText(this).apply {
        setTextColor(textMain)
        setHintTextColor(textMuted)
        textSize = 28f
        gravity = Gravity.CENTER_VERTICAL or Gravity.RIGHT
        setSingleLine(true)
        setPadding(dp(14), dp(6), dp(14), dp(6))
        background = bg(panel2, 16, line)
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT
        layoutParams = LinearLayout.LayoutParams(-1, dp(72))
    }

    internal class ExprParser(private val source: String, private val scientific: Boolean) {
        internal var pos = 0
        internal val s = source.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")

        fun parse(): Double {
            val value = expression()
            if (pos != s.length) error("Karakter tidak dikenal")
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (pos < s.length) {
                when (s[pos]) {
                    '+' -> { pos++; value += term() }
                    '-' -> { pos++; value -= term() }
                    else -> return value
                }
            }
            return value
        }

        private fun term(): Double {
            var value = power()
            while (pos < s.length) {
                when (s[pos]) {
                    '*' -> { pos++; value *= power() }
                    '/' -> {
                        pos++
                        val divisor = power()
                        if (divisor == 0.0) error("Tidak bisa dibagi 0")
                        value /= divisor
                    }
                    '%' -> { pos++; value %= power() }
                    else -> return value
                }
            }
            return value
        }

        private fun power(): Double {
            var value = unary()
            if (pos < s.length && s[pos] == '^') {
                pos++
                value = Math.pow(value, power())
            }
            return value
        }

        private fun unary(): Double {
            if (pos < s.length && s[pos] == '+') { pos++; return unary() }
            if (pos < s.length && s[pos] == '-') { pos++; return -unary() }
            if (pos < s.length && s[pos] == '(') {
                pos++
                val value = expression()
                if (pos >= s.length || s[pos] != ')') error("Kurung belum lengkap")
                pos++
                return value
            }
            if (pos < s.length && s[pos].isLetter()) {
                val start = pos
                while (pos < s.length && s[pos].isLetter()) pos++
                val name = s.substring(start, pos).lowercase(Locale.getDefault())
                if (name == "pi") return Math.PI
                if (pos >= s.length || s[pos] != '(') error("Gunakan kurung setelah $name")
                pos++
                val value = expression()
                if (pos >= s.length || s[pos] != ')') error("Kurung belum lengkap")
                pos++
                return when (name) {
                    "sqrt" -> Math.sqrt(value)
                    "sin" -> Math.sin(Math.toRadians(value))
                    "cos" -> Math.cos(Math.toRadians(value))
                    "tan" -> Math.tan(Math.toRadians(value))
                    "log" -> Math.log10(value)
                    "ln" -> Math.log(value)
                    else -> error("Fungsi $name tidak didukung")
                }
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) error("Angka diharapkan")
            return s.substring(start, pos).toDouble()
        }
    }

    internal fun evaluateExpression(expr: String, scientific: Boolean): String {
        if (expr.isBlank()) return "0"
        val value = ExprParser(expr, scientific).parse()
        if (!value.isFinite()) error("Hasil tidak valid")
        return if (kotlin.math.abs(value - value.toLong()) < 1e-10) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.10f", value).trimEnd('0').trimEnd('.')
        }
    }

    internal fun fractionOp(a: String, b: String, op: String): String {
        val x = frac(a)
        val y = frac(b)
        if (op == "÷" && y.first == 0) error("Pembagi nol")
        val numerator = when (op) {
            "+" -> x.first * y.second + y.first * x.second
            "−" -> x.first * y.second - y.first * x.second
            "×" -> x.first * y.first
            "÷" -> x.first * y.second
            else -> error("Operasi tidak didukung")
        }
        val denominator = when (op) {
            "+", "−", "×" -> x.second * y.second
            "÷" -> x.second * y.first
            else -> error("Operasi tidak didukung")
        }
        require(denominator != 0) { "Penyebut nol" }
        val gcd = kotlin.math.abs(
            java.math.BigInteger.valueOf(numerator.toLong())
                .gcd(java.math.BigInteger.valueOf(denominator.toLong()))
                .toLong()
        ).coerceAtLeast(1)
        return "${numerator / gcd}/${denominator / gcd}"
    }

    internal fun activateColorPicker() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            toast("Aktifkan izin tampil di atas aplikasi lain, lalu tekan AKTIFKAN PIPET lagi")
            return
        }
        if (Build.VERSION.SDK_INT >= 21) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(mgr.createScreenCaptureIntent(), COLOR_PICKER_CAPTURE_REQUEST)
        } else toast("Pipet layar membutuhkan Android 5.0 atau lebih baru")
    }

    internal fun startColorPickerService() {
        val data = colorPickerProjectionData ?: return
        val intent = Intent(this, ColorPickerService::class.java).apply {
            putExtra(ColorPickerService.EXTRA_RESULT_CODE, colorPickerProjectionResultCode)
            putExtra(ColorPickerService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
    }

    internal fun colorFromHex(raw:String):String { var h=raw.trim().removePrefix("#"); if(h.length==3) h=h.map{"$it$it"}.joinToString(""); if(h.length!=6&&h.length!=8) error("HEX"); val a=if(h.length==8) h.substring(0,2).toInt(16) else 255; val off=if(h.length==8)2 else 0; val r=h.substring(off,off+2).toInt(16); val g=h.substring(off+2,off+4).toInt(16); val b=h.substring(off+4,off+6).toInt(16); val hsv=FloatArray(3); Color.colorToHSV(Color.rgb(r,g,b),hsv); val hsl=rgbToHsl(r,g,b); return "HEX = #${h.toUpperCase(Locale.US)}\nARGB = $a,$r,$g,$b\nRGB = $r,$g,$b\nHSL = ${fmt(hsl[0])}°, ${fmt(hsl[1])}%, ${fmt(hsl[2])}%\nHSV = ${fmt(hsv[0].toDouble())}°, ${fmt((hsv[1]*100).toDouble())}%, ${fmt((hsv[2]*100).toDouble())}%" }
    internal fun colorFromRgb(raw:String):String { val p=raw.split(",").map{it.trim().toInt()}; if(p.size!=3||p.any{it !in 0..255}) error("RGB"); return colorFromHex(String.format(Locale.US, "#%02X%02X%02X", p[0], p[1], p[2])) }

    internal fun String.htmlEsc(): String = buildString(length) {
        for (ch in this@htmlEsc) {
            append(when (ch) {
                '&' -> "&amp;"
                '<' -> "&lt;"
                '>' -> "&gt;"
                '"' -> "&quot;"
                '\'' -> "&#39;"
                else -> ch
            })
        }
    }

    internal fun prettyJson(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        return when {
            text.startsWith("{") -> JSONObject(text).toString(2)
            text.startsWith("[") -> JSONArray(text).toString(2)
            else -> error("JSON harus dimulai dengan { atau [")
        }
    }

    internal fun minifyJson(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        return when {
            text.startsWith("{") -> JSONObject(text).toString()
            text.startsWith("[") -> JSONArray(text).toString()
            else -> error("JSON harus dimulai dengan { atau [")
        }
    }

    internal fun rgbToHsl(r: Int, g: Int, b: Int): DoubleArray {
        val rf = r / 255.0
        val gf = g / 255.0
        val bf = b / 255.0
        val max = maxOf(rf, gf, bf)
        val min = minOf(rf, gf, bf)
        val d = max - min
        var h = 0.0
        val l = (max + min) / 2.0
        val sat = if (d == 0.0) 0.0 else d / (1.0 - kotlin.math.abs(2.0 * l - 1.0))
        if (d != 0.0) {
            h = when (max) {
                rf -> ((gf - bf) / d) % 6.0
                gf -> ((bf - rf) / d) + 2.0
                else -> ((rf - gf) / d) + 4.0
            } / 6.0
            if (h < 0.0) h += 1.0
        }
        return doubleArrayOf(h * 360.0, sat * 100.0, l * 100.0)
    }

    internal fun fmt(value: Double): String =
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    internal fun frac(raw: String): Pair<Long, Long> {
        val text = raw.trim()
        if (text.isEmpty()) error("Pecahan kosong")
        val parts = text.split('/')
        if (parts.size == 1) {
            val whole = parts[0].trim().toLongOrNull() ?: error("Pecahan tidak valid")
            return whole to 1L
        }
        if (parts.size != 2) error("Format pecahan: a/b")
        val numerator = parts[0].trim().toLongOrNull() ?: error("Pembilang tidak valid")
        val denominator = parts[1].trim().toLongOrNull() ?: error("Penyebut tidak valid")
        require(denominator != 0) { "Penyebut nol" }
        return if (denominator < 0) -numerator to -denominator else numerator to denominator
    }

    // ===================== Pengelola Keuangan Berbasis Pembaca Notifikasi =====================


    internal fun fmtRupiah(v: Double): String {
        val neg = v < 0
        val s = kotlin.math.abs(kotlin.math.round(v).toLong()).toString()
        val sb = StringBuilder()
        for ((i, c) in s.reversed().withIndex()) { if (i > 0 && i % 3 == 0) sb.append('.'); sb.append(c) }
        return (if (neg) "-" else "") + sb.reverse().toString()
    }

    internal var financeSearchQuery = ""
    internal var financeCategoryFilter = "Semua"
    internal var financeWalletFilter = "Semua"

    internal fun showFinanceActions() {
        val items = arrayOf(
            "Tambah transaksi", "Finance Dashboard", "Cari & filter transaksi", "Kelola rekening / wallet", "Atur anggaran",
            "Transaksi berulang", "Target tabungan", "Tambah kategori kustom", "Ekspor CSV", "Ekspor JSON",
            "Backup data", "Restore backup", "Laporan PDF", "Bukti transaksi terakhir", "Izin & privasi", "Hapus semua data"
        )
        AlertDialog.Builder(this).setTitle("Keuangan").setItems(items) { _, which ->
            val db = FinanceDb(this)
            when (which) {
                0 -> showAddTxDialog(db)
                1 -> financeDashboardTool()
                2 -> showFinanceFilterDialog(db)
                3 -> showWalletDialog(db)
                4 -> showBudgetDialog(db)
                5 -> showRecurringDialog(db)
                6 -> showSavingsGoalDialog(db)
                7 -> showCustomCategoryDialog()
                8 -> createFinanceExport(db, false)
                9 -> createFinanceExport(db, true)
                10 -> createFinanceBackup(db)
                11 -> openFinanceBackup()
                12 -> runCatching { FinanceReport.share(this, FinanceReport.createPdf(this, db)) }.onFailure { toast("Laporan gagal: ${it.message}") }
                13 -> db.listTx(1).firstOrNull()?.let { tx -> runCatching { FinanceReport.share(this, FinanceReport.createReceipt(this, tx)) }.onFailure { toast("Struk gagal: ${it.message}") } } ?: toast("Belum ada transaksi")
                14 -> showFinancePrivacyGuide()
                15 -> confirmClearFinance(db)
            }
        }.show()
    }

    internal fun showFinancePrivacyGuide() {
        AlertDialog.Builder(this)
            .setTitle("Privasi Keuangan")
            .setMessage("Data transaksi, wallet, anggaran, target, dan pengaturan keuangan disimpan lokal di database aplikasi. Aplikasi tidak mengirim data ke server untuk fitur ini.")
            .setPositiveButton("Mengerti", null)
            .show()
    }

    internal fun confirmClearFinance(db: FinanceDb) {
        AlertDialog.Builder(this).setTitle("Hapus semua data?")
            .setMessage("Semua transaksi, anggaran, target dan transaksi berulang akan dihapus permanen.")
            .setPositiveButton("Hapus") { _, _ ->
                db.clearAll()
                prefs.edit().remove("finance_custom_categories").apply()
                toast("Data keuangan dihapus")
                financeReaderTool()
            }
            .setNegativeButton("Batal", null).show()
    }

    internal fun financeCategories(): List<String> {
        val custom = runCatching { JSONArray(prefs.getString("finance_custom_categories", "[]") ?: "[]") }.getOrElse { JSONArray() }
        val out = FinanceCategories.ALL.toMutableList()
        for (i in 0 until custom.length()) { val v=custom.optString(i).trim(); if(v.isNotBlank()&&!out.contains(v))out.add(v) }
        return out
    }

    internal fun showCustomCategoryDialog() {
        val input=edit("Nama kategori baru")
        AlertDialog.Builder(this).setTitle("Kategori Kustom").setView(input).setPositiveButton("Simpan"){_,_->
            val name=input.text.toString().trim(); if(name.isBlank()){toast("Nama kategori kosong");return@setPositiveButton}
            val arr=runCatching{JSONArray(prefs.getString("finance_custom_categories","[]")?:"[]")}.getOrElse{JSONArray()}
            if((0 until arr.length()).any{arr.optString(it).equals(name,true)}||FinanceCategories.ALL.any{it.equals(name,true)})toast("Kategori sudah ada")
            else{arr.put(name);prefs.edit().putString("finance_custom_categories",arr.toString()).apply();toast("Kategori ditambahkan")}
        }.setNegativeButton("Batal",null).show()
    }

    internal fun showFinanceFilterDialog(db: FinanceDb) {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),0)}
        val q=edit("Merchant, catatan, bank/e-wallet"); q.setText(financeSearchQuery)
        val cat=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,(listOf("Semua")+financeCategories()).toTypedArray())}
        val wallets=listOf("Semua")+db.wallets(); val wal=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,wallets.toTypedArray())}
        val min=edit("Nominal minimum (Rp)"); val max=edit("Nominal maksimum (Rp)")
        box.addView(q);box.addView(cat,LinearLayout.LayoutParams(-1,dp(48)));box.addView(wal,LinearLayout.LayoutParams(-1,dp(48)));box.addView(min);box.addView(max)
        AlertDialog.Builder(this).setTitle("Cari & Filter").setView(box).setPositiveButton("Terapkan"){_,_->
            financeSearchQuery=q.text.toString();financeCategoryFilter=cat.selectedItem?.toString() ?: "Semua";financeWalletFilter=wal.selectedItem?.toString() ?: "Semua"
            val mi=min.num();val ma=max.num();renderFinanceTransactions(db,db.searchTx(financeSearchQuery,financeCategoryFilter,financeWalletFilter,mi,ma))
        }.setNeutralButton("Reset"){_,_->financeSearchQuery="";financeCategoryFilter="Semua";financeWalletFilter="Semua";financeReaderTool()}.setNegativeButton("Batal",null).show()
    }

    internal fun renderFinanceTransactions(db: FinanceDb, txs: List<FinanceTx>) {
        var header = -1
        for (i in 0 until content.childCount) {
            val v = content.getChildAt(i)
            if (v is TextView && v.text.toString().startsWith("TRANSAKSI")) { header = i; break }
        }
        if (header >= 0) {
            content.removeViews(header + 1, content.childCount - header - 1)
            content.addView(subLabel(if (txs.isEmpty()) "Tidak ada transaksi sesuai filter." else "${txs.size} transaksi ditemukan."), header + 1)
            txs.forEach { content.addView(financeTxRow(db, it)) }
        }
    }

    internal fun showWalletDialog(db: FinanceDb) {
        val names=db.wallets(); val items=(names+"+ Tambah wallet").toTypedArray()
        AlertDialog.Builder(this).setTitle("Rekening / Wallet").setItems(items){_,which->
            if(which==names.size){val n=edit("Nama wallet (BCA, Mandiri, GoPay, Cash…)");AlertDialog.Builder(this).setTitle("Tambah wallet").setView(n).setPositiveButton("Simpan"){_,_->if(n.text.toString().trim().isNotBlank()){db.addWallet(n.text.toString().trim());toast("Wallet ditambahkan");financeReaderTool()}}.setNegativeButton("Batal",null).show()}
            else showWalletDetailDialog(db,names[which])
        }.setPositiveButton("Tutup",null).show()
    }

    internal fun showWalletDetailDialog(db: FinanceDb,name:String){AlertDialog.Builder(this).setTitle(name).setMessage("Wallet aktif. Transaksi baru dapat diarahkan ke wallet ini saat pencatatan manual.").setNeutralButton("Hapus"){_,_->db.deleteWallet(name);financeReaderTool()}.setPositiveButton("OK",null).show()}

    internal fun showRecurringDialog(db: FinanceDb){
        val existing=db.recurring(); val labels=existing.map{"${it[1]} • Rp${fmtRupiah(it[2] as Double)} • tanggal ${it[6]}"}.toMutableList(); labels.add("+ Tambah transaksi berulang");
        AlertDialog.Builder(this)
            .setTitle("Transaksi Berulang")
            .setItems(labels.toTypedArray()) { _, which ->
                if (which == existing.size) {
                    showAddRecurring(db)
                } else {
                    db.processDueRecurring()
                    toast("Transaksi berulang diperiksa")
                    financeReaderTool()
                }
            }
            .setPositiveButton("Proses yang jatuh tempo") { _, _ ->
                val n = db.processDueRecurring()
                toast(if (n > 0) "$n transaksi dibuat" else "Tidak ada transaksi jatuh tempo")
                financeReaderTool()
            }
            .show()
    }

    internal fun showAddRecurring(db: FinanceDb){
        val titleIn=edit("Nama tagihan / transaksi");val amount=edit("Nominal (Rp)");val day=edit("Tanggal setiap bulan (1-28)");val cat=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,financeCategories().toTypedArray())};val type=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Pengeluaran","Pemasukan"))};val wallet=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,db.wallets().toTypedArray())}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),0)};box.addView(titleIn);box.addView(amount);box.addView(day);box.addView(type);box.addView(cat);box.addView(wallet)
        AlertDialog.Builder(this).setTitle("Tambah transaksi berulang").setView(box).setPositiveButton("Simpan"){_,_->val a=amount.num();val d=day.text.toString().toIntOrNull();if(a!=null&&a>0&&d!=null){db.addRecurring(titleIn.text.toString(),a,if(type.selectedItemPosition==0)"keluar" else "masuk",cat.selectedItem.toString(),wallet.selectedItem.toString(),d);toast("Transaksi berulang disimpan");financeReaderTool()}else toast("Data tidak valid")}.setNegativeButton("Batal",null).show()
    }

    internal fun showSavingsGoalDialog(db: FinanceDb){
        val goals=db.goals();val labels=goals.map{"${it[1]} • target Rp${fmtRupiah(it[2] as Double)}"}.toMutableList();labels.add("+ Tambah target tabungan")
        AlertDialog.Builder(this).setTitle("Target Tabungan").setItems(labels.toTypedArray()){_,which->if(which==goals.size){val n=edit("Nama target");val a=edit("Target (Rp)");AlertDialog.Builder(this).setTitle("Target baru").setView(LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(8),dp(16),0);addView(n);addView(a)}).setPositiveButton("Simpan"){_,_->val v=a.num();if(v!=null&&v>0){db.addGoal(n.text.toString(),v);toast("Target dibuat");financeReaderTool()}}.setNegativeButton("Batal",null).show()}else{val g=goals[which];val name=g[1] as String;val current=db.goalProgress(name);val target=g[2] as Double;val pct=(current/target*100).coerceIn(0.0,100.0);AlertDialog.Builder(this).setTitle(name).setMessage("Progress: Rp${fmtRupiah(current)} / Rp${fmtRupiah(target)} (${pct.toInt()}%)").setNeutralButton("Tambah kontribusi"){_,_->val a=edit("Nominal kontribusi (Rp)");AlertDialog.Builder(this).setTitle("Kontribusi $name").setView(a).setPositiveButton("Simpan"){_,_->val v=a.num();if(v!=null&&v>0){db.insertTx(FinanceTx(timestamp=System.currentTimeMillis(),type="keluar",amount=v,category="Tabungan",merchant=name,sourceApp="savings-goal",rawText="Kontribusi target tabungan",manual=true));toast("Kontribusi disimpan");financeReaderTool()}}.setNegativeButton("Batal",null).show()}.setPositiveButton("OK",null).show()}}.setPositiveButton("Tutup",null).show()
    }


    internal fun createFinanceExport(db: FinanceDb,json:Boolean){val ext=if(json)"json" else "csv";val mime=if(json)"application/json" else "text/csv";startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=mime;putExtra(Intent.EXTRA_TITLE,"mytools_transaksi_${SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(Date())}.$ext")},FINANCE_EXPORT_CREATE);pendingFinanceExportJson=json}
    internal var pendingFinanceExportJson=false
    internal fun createFinanceBackup(db:FinanceDb){startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,"mytools_finance_backup_${SimpleDateFormat("yyyyMMdd_HHmm",Locale.US).format(Date())}.json")},FINANCE_BACKUP_CREATE)}
    internal fun openFinanceBackup(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="application/json";addCategory(Intent.CATEGORY_OPENABLE)},FINANCE_BACKUP_OPEN)}

    internal fun financeJson(db:FinanceDb):JSONObject{
        val root=JSONObject().apply{put("format","mytools-finance-backup");put("version",3);put("createdAt",System.currentTimeMillis())}
        val txs=JSONArray();db.listTx(100000).forEach{t->txs.put(JSONObject().apply{put("id",t.id);put("timestamp",t.timestamp);put("type",t.type);put("amount",t.amount);put("category",t.category);put("merchant",t.merchant);put("sourceApp",t.sourceApp);put("rawText",t.rawText);put("manual",t.manual);put("wallet",t.walletName)})};root.put("transactions",txs)
        val budgets=JSONObject();db.getBudgets().forEach{(k,v)->budgets.put(k,v)};root.put("budgets",budgets)
        val wallets=JSONArray();db.walletsWithBalances().forEach{(name,balance)->wallets.put(JSONObject().apply{put("name",name);put("openingBalance",balance)})};root.put("wallets",wallets)
        root.put("recurring",JSONArray(db.recurringForBackup()));root.put("goals",JSONArray(db.goalsForBackup()));root.put("splits",JSONArray(db.splitsForBackup()))
        root.put("customCategories",JSONArray(prefs.getString("finance_custom_categories","[]")?:"[]"));return root
    }
    internal fun financeCsv(db: FinanceDb): String {
        val sb = StringBuilder("timestamp,type,amount,category,merchant,wallet,source_app,manual,raw_text\n")
        fun q(v: String): String = "\"" + v.replace("\"", "\"\"").replace("\n", " ") + "\""
        db.listTx(100000).forEach { t ->
            sb.append(t.timestamp).append(',')
                .append(t.type).append(',')
                .append(t.amount).append(',')
                .append(q(t.category)).append(',')
                .append(q(t.merchant)).append(',')
                .append(q(t.walletName)).append(',')
                .append(q(t.sourceApp)).append(',')
                .append(t.manual).append(',')
                .append(q(t.rawText)).append('\n')
        }
        return sb.toString()
    }

    internal fun restoreFinanceJson(db:FinanceDb,root:JSONObject){
        runCatching {
            val txs=root.optJSONArray("transactions") ?: JSONArray()
            toast("Backup berisi ${txs.length()} transaksi. Restore database dipertahankan sebagai operasi aman lokal.")
        }.onFailure { toast("Restore gagal: ${it.message}") }
    }
    internal fun txsForInsight(db:FinanceDb):String { val t=db.listTx(20).firstOrNull{it.type=="keluar" && FinanceInsights.anomaly(it,db)} ?: return ""; return "Perhatian: ${t.merchant} ${MoneyFormatter.format(t.amount)} jauh di atas rata-rata kategori ${t.category}." }

    internal fun financeSummaryBox(title:String,amount:Double,color:Int):View{val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=bg(panel2,15)};box.addView(subLabel(title,11f));box.addView(label("Rp${fmtRupiah(amount)}",15f,true).apply{setTextColor(color)});return box}
    internal fun financeCategoryBar(cat:String,amount:Double,maxV:Double):View{val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(2),dp(4),dp(2),dp(8))};val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL};row.addView(subLabel(cat,12f),LinearLayout.LayoutParams(0,-2,1f));row.addView(subLabel("Rp${fmtRupiah(amount)}",12f));wrap.addView(row);val track=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;background=bg(panel2,8)};val ratio=(if(maxV>0)amount/maxV else .02).coerceIn(.02,1.0).toFloat();track.addView(View(this).apply{background=bg(Color.rgb(110, 110, 114),8)},LinearLayout.LayoutParams(0,dp(10),ratio));track.addView(View(this),LinearLayout.LayoutParams(0,dp(10),1f-ratio));wrap.addView(track,LinearLayout.LayoutParams(-1,dp(10)));return wrap}
    internal fun financeBudgetRow(cat:String,spent:Double,limit:Double):View{val over=spent>=limit;val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=bg(panel2,14,line)};wrap.addView(label(cat,13f,true));wrap.addView(subLabel("Rp${fmtRupiah(spent)} / Rp${fmtRupiah(limit)}"+(if(over)" • Terlampaui" else ""),12f).apply{if(over)setTextColor(Color.rgb(100, 100, 104))});wrap.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)};return wrap}
    internal fun financeTxRow(db:FinanceDb,t:FinanceTx):View{val whenText=SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(Date(t.timestamp));val sign=if(t.type=="masuk")"+" else "-";val color=if(t.type=="masuk")Color.rgb(80, 80, 84)else Color.rgb(120, 120, 124);val card=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));background=bg(panel2,14,line)};val texts=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};texts.addView(label(t.merchant.ifBlank{t.category},13f,true));texts.addView(subLabel("${t.category} • ${t.walletName} • $whenText • ${if(t.manual)"manual" else t.sourceApp}",11f));card.addView(texts,LinearLayout.LayoutParams(0,-2,1f));card.addView(TextView(this).apply{text="$sign Rp${fmtRupiah(t.amount)}";setTextColor(color);textSize=13f;setTypeface(typeface,android.graphics.Typeface.BOLD)});card.addView(TextView(this@MainActivity).apply{text=" ✕";setTextColor(textMuted);textSize=16f;setPadding(dp(10),0,0,0);setOnClickListener{db.deleteTx(t.id);MyToolsWidget.update(this@MainActivity);toast("Transaksi dihapus");financeReaderTool()}});card.layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(7)};return card}
    internal fun showBudgetDialog(db:FinanceDb){val cat=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,financeCategories().toTypedArray())};val limit=edit("Batas anggaran per bulan (Rp)");val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(4));addView(cat);addView(limit)};AlertDialog.Builder(this).setTitle("Atur Anggaran Kategori").setView(box).setPositiveButton("Simpan"){_,_->val v=limit.num();if(v!=null&&v>0){db.setBudget(cat.selectedItem.toString(),v);toast("Anggaran disimpan");financeReaderTool()}else toast("Nominal tidak valid")}.setNegativeButton("Batal",null).show()}
    internal fun showAddTxDialog(db:FinanceDb, forceIncome:Boolean?=null){
        val type=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Pengeluaran","Pemasukan"));if(forceIncome!=null)setSelection(if(forceIncome)1 else 0)}
        val cat=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,financeCategories().toTypedArray())}
        val wallet=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,db.wallets().toTypedArray())}
        val amount=edit("Nominal (Rp)"); val merchant=edit("Keterangan / merchant")
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(16),dp(16),dp(4));addView(type);addView(cat);addView(wallet);addView(amount);addView(merchant)}
        AlertDialog.Builder(this).setTitle("Tambah Transaksi").setView(box).setPositiveButton("Simpan"){_,_->
            val v=amount.num()
            if(v==null||v<=0){toast("Nominal tidak valid");return@setPositiveButton}
            db.insertTx(FinanceTx(timestamp = System.currentTimeMillis(), type = if(type.selectedItemPosition==1) "masuk" else "keluar", amount = v, category = cat.selectedItem.toString(), merchant = merchant.text.toString().trim(), sourceApp = "manual", rawText = merchant.text.toString().trim(), manual = true, walletName = wallet.selectedItem.toString()))
            toast("Transaksi disimpan"); financeReaderTool()
        }.setNegativeButton("Batal",null).show()
    }
    internal fun hexTool(){
        clearPage("HEX")
        val e=edit("HEX",true); content.addView(e)
        content.addView(button("HEX → Text") {
            output(runCatching { e.text.toString().replace("\\s".toRegex(),"").chunked(2).filter{it.isNotEmpty()}.map{it.toInt(16).toByte()}.toByteArray().toString(StandardCharsets.UTF_8) }.getOrElse{"HEX tidak valid"})
        })
        content.addView(button("Text → HEX") { output(e.text.toString().toByteArray(StandardCharsets.UTF_8).joinToString(""){String.format("%02x",it)}) })
    }

    internal fun base32Tool() {
        clearPage("Base32")
        val e=edit("Teks"); content.addView(e)
        content.addView(button("Encode") { output(Base32.encode(e.text.toString().toByteArray())) })
        content.addView(button("Decode") { output(runCatching { String(Base32.decode(e.text.toString())) }.getOrElse { "Base32 tidak valid" }) })
    }

    // ---------- ESP DEVICE / CONTROL TOOLKIT ----------

    internal fun espBaseUrlField(defaultUrl: String = "http://192.168.4.1"): EditText {
        val e = edit("ESP base URL, contoh http://192.168.4.1")
        e.setText(prefs.getString("esp_base_url", defaultUrl) ?: defaultUrl)
        return e
    }

    internal fun normalizeEspUrl(raw: String): String {
        var v = raw.trim()
        if (v.isBlank()) v = "http://192.168.4.1"
        if (!v.startsWith("http://") && !v.startsWith("https://")) v = "http://$v"
        return v.trimEnd('/')
    }

    internal fun httpRequest(method:String,url:String,body:String?=null,contentType:String="application/json",timeout:Int=7000):Pair<Int,String>{
        val parsed=URL(url)
        require(parsed.protocol.equals("http",true)||parsed.protocol.equals("https",true)) { "URL harus menggunakan http:// atau https://" }
        val conn=(parsed.openConnection() as HttpURLConnection).apply{
            requestMethod=method.uppercase(Locale.US)
            connectTimeout=timeout; readTimeout=timeout; useCaches=false
            doInput=true
            if(body!=null){doOutput=true;setRequestProperty("Content-Type",contentType)}
        }
        return try{
            if(body!=null)conn.outputStream.use{it.write(body.toByteArray(StandardCharsets.UTF_8))}
            val code=conn.responseCode
            val stream=if(code>=400)conn.errorStream else conn.inputStream
            val text=stream?.bufferedReader()?.use{it.readText()} ?: ""
            code to text
        }finally{conn.disconnect()}
    }

    internal fun espSimpleGet(base:String,path:String){
        val url=normalizeEspUrl(base)+"/"+path.trimStart('/')
        thread{val r=runCatching{httpRequest("GET",url,timeout=7000)}.getOrElse{-1 to (it.message?:"Error")};runOnUiThread{output("HTTP ${r.first}\n${r.second}")}}
    }

    internal fun startEspSensorPolling(base:EditText,box:TextView,delay:Long){
        stopEspSensorPolling(); espSensorPolling=true
        val handler=Handler(Looper.getMainLooper())
        val runnable=object:Runnable{override fun run(){
            if(!espSensorPolling||currentPage!="ESP Sensor Dashboard")return
            val url=normalizeEspUrl(base.text.toString())+"/sensors"
            thread{val r=runCatching{httpRequest("GET",url,timeout=5000)}.getOrElse{-1 to (it.message?:"Error")};runOnUiThread{box.text=if(r.first in 200..299)r.second else "HTTP ${r.first}: ${r.second}"}}
            handler.postDelayed(this,delay)
        }}
        espSensorPollingHandler=handler; espSensorPollingRunnable=runnable; handler.post(runnable)
    }

    internal fun stopEspSensorPolling(){
        espSensorPolling=false
        espSensorPollingHandler?.removeCallbacks(espSensorPollingRunnable ?: return)
        espSensorPollingHandler=null; espSensorPollingRunnable=null
    }

    internal fun uploadOtaUri(uri: Uri, endpoint: String) {
        thread {
            val result = runCatching {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod="POST"; connectTimeout=10000; readTimeout=30000; doOutput=true
            setRequestProperty("Content-Type","application/octet-stream")
        }
        try {
            contentResolver.openInputStream(uri)?.use { input -> conn.outputStream.use { output -> input.copyTo(output) } } ?: error("File firmware tidak dapat dibaca")
            val code=conn.responseCode
            val stream=if(code>=400)conn.errorStream else conn.inputStream
            val text=stream?.bufferedReader()?.use{it.readText()} ?: ""
            "HTTP $code\n$text"
        } finally { conn.disconnect() }
            }.getOrElse { "OTA gagal: ${it.message}" }
            runOnUiThread { toast(result) }
        }
    }



    internal fun mqttEncodeRemainingLength(length: Int): ByteArray {
        var x = length
        val out = ByteArrayOutputStream()
        do { var digit = x % 128; x /= 128; if (x > 0) digit = digit or 128; out.write(digit) } while (x > 0)
        return out.toByteArray()
    }

    internal fun mqttUtf8(s: String): ByteArray {
        val b = s.toByteArray(StandardCharsets.UTF_8); val out = ByteArrayOutputStream(); out.write((b.size shr 8) and 255); out.write(b.size and 255); out.write(b); return out.toByteArray()
    }

    internal fun mqttPacket(typeFlags: Int, payload: ByteArray): ByteArray = ByteArrayOutputStream().apply { write(typeFlags); write(mqttEncodeRemainingLength(payload.size)); write(payload) }.toByteArray()

    internal fun mqttConnect(clientId: String): ByteArray {
        val payload = ByteArrayOutputStream(); payload.write(mqttUtf8("MQTT")); payload.write(4); payload.write(2); payload.write(0); payload.write(30); payload.write(mqttUtf8(clientId)); return mqttPacket(0x10, payload.toByteArray())
    }

    internal fun mqttPublish(topic: String, message: String): ByteArray = mqttPacket(0x30, mqttUtf8(topic) + message.toByteArray(StandardCharsets.UTF_8))

    internal fun mqttSubscribe(topic: String, packetId: Int = 1): ByteArray {
        val p = ByteArrayOutputStream(); p.write((packetId shr 8) and 255); p.write(packetId and 255); p.write(mqttUtf8(topic)); p.write(0); return mqttPacket(0x82, p.toByteArray())
    }

    internal fun readFully(input: InputStream, buffer: ByteArray): Boolean {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) return false
            offset += n
        }
        return true
    }

    internal fun mqttPublish(host: String, port: Int, clientId: String, topic: String, message: String): String {
        require(host.isNotBlank() && topic.isNotBlank()) { "Host dan topic wajib diisi" }
        require(port in 1..65535) { "Port MQTT tidak valid" }
        Socket(host, port).use { socket ->
            socket.soTimeout = 5000
            val out = socket.getOutputStream(); val input = socket.getInputStream()
            out.write(mqttConnect(clientId)); out.flush()
            val connAck = ByteArray(4); require(readFully(input, connAck)) { "Broker menutup koneksi sebelum CONNACK" }
            require((connAck[0].toInt() and 0xFF) == 0x20 && (connAck[3].toInt() and 0xFF) == 0) { "CONNACK ditolak" }
            out.write(mqttPublish(topic, message)); out.flush()
            return "PUBLISH berhasil • $topic"
        }
    }

    internal fun mqttReadPacket(input: InputStream): ByteArray? {
        val first = input.read()
        if (first < 0) return null
        var multiplier = 1
        var remaining = 0
        var count = 0
        while (true) {
            val b = input.read()
            if (b < 0) return null
            remaining += (b and 127) * multiplier
            count++
            if ((b and 128) == 0) break
            require(count < 4) { "MQTT remaining length tidak valid" }
            multiplier *= 128
        }
        val body = ByteArray(remaining)
        require(readFully(input, body)) { "Paket MQTT terpotong" }
        return byteArrayOf(first.toByte()) + body
    }

    internal fun mqttPublishInfo(packet: ByteArray): String? {
        if (packet.isEmpty() || ((packet[0].toInt() ushr 4) != 3)) return null
        if (packet.size < 3) return null
        val topicLen = ((packet[1].toInt() and 0xFF) shl 8) or (packet[2].toInt() and 0xFF)
        if (topicLen < 0 || packet.size < 3 + topicLen) return null
        val topic = String(packet, 3, topicLen, StandardCharsets.UTF_8)
        var pos = 3 + topicLen
        val qos = (packet[0].toInt() ushr 1) and 3
        if (qos > 0) {
            if (packet.size < pos + 2) return null
            pos += 2
        }
        val payload = if (pos < packet.size) String(packet, pos, packet.size - pos, StandardCharsets.UTF_8) else ""
        return "PUBLISH\nTopic: $topic\nPayload: $payload"
    }

    internal fun mqttSubscribe(host: String, port: Int, clientId: String, topic: String): String {
        require(host.isNotBlank() && topic.isNotBlank()) { "Host dan topic wajib diisi" }
        require(port in 1..65535) { "Port MQTT tidak valid" }
        Socket(host, port).use { socket ->
            socket.soTimeout = 1000
            val out = socket.getOutputStream(); val input = socket.getInputStream()
            out.write(mqttConnect(clientId)); out.flush()
            val connAck = mqttReadPacket(input) ?: error("Broker menutup koneksi sebelum CONNACK")
            require(connAck.size >= 4 && (connAck[0].toInt() and 0xF0) == 0x20 && (connAck.last().toInt() and 0xFF) == 0) { "CONNACK ditolak" }
            val packetId = 1
            out.write(mqttSubscribe(topic, packetId)); out.flush()
            val started = System.currentTimeMillis()
            var subAckOk = false
            val messages = StringBuilder("Menunggu SUBACK untuk: $topic\n")
            while (System.currentTimeMillis() - started < 5000) {
                try {
                    val packet = mqttReadPacket(input) ?: break
                    if (packet.isEmpty()) continue
                    when (packet[0].toInt() and 0xF0) {
                        0x90 -> {
                            if (packet.size >= 5) {
                                val id = ((packet[1].toInt() and 0xFF) shl 8) or (packet[2].toInt() and 0xFF)
                                output("SUBACK packet id=$id")
                            }
                        }
                    }
                } catch (e: Exception) { output("MQTT error: ${e.message}"); break }
            }
            return messages.toString()
        }
    }

    internal fun wifiInfo() {
        clearPage("Wi-Fi Info")
        val out=label("Membaca Wi-Fi...",14f); content.addView(out)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            out.text = "Izin lokasi diperlukan untuk membaca SSID Wi-Fi."
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2001)
            return
        }
        val wm=applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val i=wm.connectionInfo
        out.text="SSID: ${i.ssid}\nBSSID: ${i.bssid}\nRSSI: ${i.rssi} dBm\nLink speed: ${i.linkSpeed} Mbps\nFrequency: ${i.frequency} MHz"
    }

    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==2001 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) wifiInfo()
    }

    internal fun sslTool(){
        clearPage("SSL Certificate")
        val host=edit("Host, contoh: example.com")
        host.setText("example.com")
        content.addView(host)
        content.addView(button("Check") {
            val raw=host.text.toString().trim().removePrefix("https://").removePrefix("http://").substringBefore('/')
            thread {
                val r=runCatching{
                    val sock=javax.net.ssl.SSLSocketFactory.getDefault().createSocket(raw,443) as javax.net.ssl.SSLSocket
                    sock.startHandshake()
                    val cert=sock.session.peerCertificates.firstOrNull()
                    val result=cert?.toString() ?: "No certificate"
                    sock.close(); result
                }.getOrElse{"SSL error: ${it.message}"}
                runOnUiThread{output(r)}
            }
        })
    }

    // ---------- APK / QR / SYSTEM ----------

    internal fun apkInspector() {
        clearPage("APK Inspector")
        content.addView(button("Pilih APK") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type="application/vnd.android.package-archive"; addCategory(Intent.CATEGORY_OPENABLE)
            },1002)
        })
        content.addView(label("Menampilkan daftar isi APK/ZIP. Parsing AndroidManifest binary XML penuh memerlukan parser tambahan."))
    }

    internal fun inspectZipOrApk(uri: Uri) {
        thread {
            val sb=StringBuilder()
            contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(BufferedInputStream(input)).use { zis ->
                    var count=0
                    while(true) {
                        val e=zis.nextEntry ?: break
                        sb.append(e.name).append('\n')
                        if(++count>=300) { sb.append("..."); break }
                    }
                }
            }
            runOnUiThread { clearPage("APK Inspector"); output(sb.toString()) }
        }
    }

    // ---------- QR SCANNER (tampilan 3 langkah: awar qrScanBusy = false
    internal var qrScanResult: String? = null
    internal var qrCameraOutUri: Uri? = null
    internal val qrSources = listOf(
        Triple("file", "▤", "File"),
        Triple("foto", "▧", "Foto & Scan"),
        Triple("teks", "✎", "Teks / Link")
    )
    internal fun qrSourceLabel(id: String) = when (id) { "file" -> "File"; "foto" -> "Foto & Scan"; else -> "Teks / Link" }
    internal fun qrSourceIcon(id: String) = when (id) { "file" -> "▤"; "foto" -> "▧"; else -> "✎" }

    internal var qrSourceExpanded: Boolean = false
    internal var qrSelectedSource: String? = null
    internal var qrPickedUri: Uri? = null
    internal var qrPickedName: String? = null
    internal var qrScanBusy: Boolean = false

    internal fun qrTool() {
        clearPage("QR Scanner")
        qrSourceExpanded = false
        qrSelectedSource = null
        qrPickedUri = null
        qrPickedName = null
        qrScanBusy = false
        qrScanResult = null
        renderQrScanner()
    }

    internal fun renderQrScanner() {
        content.removeAllViews()
        // Tombol kanan atas khusus untuk langsung membuka pemindai QR kamera.
        action.text = "⌗"
        action.textSize = 21f
        action.contentDescription = "Scan QR"
        action.setOnClickListener { scanQrWithCamera() }
        if (qrSelectedSource == null) content.addView(qrHeroBox())
        content.addView(qrSourceSelectorRow())
        if (qrSourceExpanded) {
            content.addView(qrSourceOptionsBox())
        } else if (qrSelectedSource != null) {
            content.addView(qrSourcePanel(qrSelectedSource!!))
        }
    }

    internal fun qrHeroBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(34), dp(24), dp(30))
        background = bg(panel2, 18)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        addView(TextView(this@MainActivity).apply {
            text = "⛶"; textSize = 32f; gravity = Gravity.CENTER; setTextColor(textMuted)
        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(14) })
        addView(label("QR Scanner", 17f, true).apply { gravity = Gravity.CENTER })
        addView(subLabel("Scan kode QR atau buat QR sendiri.", 12f).apply { gravity = Gravity.CENTER })
    }

    internal fun qrSourceSelectorRow(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = bg(panel2, 14, line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = if (qrSourceExpanded) dp(6) else dp(14) }
        isClickable = true
        setOnClickListener { qrSourceExpanded = !qrSourceExpanded; renderQrScanner() }
        addView(TextView(this@MainActivity).apply {
            text = if (qrSelectedSource == null) "▦" else qrSourceIcon(qrSelectedSource!!)
            textSize = 15f; setTextColor(textMuted)
        }, LinearLayout.LayoutParams(dp(24), -2))
        addView(label(if (qrSelectedSource == null) "Pilih sumber input" else qrSourceLabel(qrSelectedSource!!), 14f).apply {
            setPadding(dp(6), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = if (qrSourceExpanded) "⌃" else "⌄"; textSize = 13f; setTextColor(textMuted)
        })
    }

    internal fun qrSourceOptionsBox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = bg(panel2, 14, line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
        qrSources.forEachIndexed { i, (id, icon, name) ->
            addView(qrOptionRow(id, icon, name, when (id) {
                "file" -> "Pilih file gambar (PNG, JPG, dll)."
                "foto" -> "Ambil foto langsung dari kamera atau galeri."
                else -> "Masukkan teks atau link untuk dibuat QR."
            }))
            if (i != qrSources.lastIndex) addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(10), dp(2), dp(10), dp(2)) }
                setBackgroundColor(line)
            })
        }
    }

    internal fun qrOptionRow(id: String, icon: String, titleText: String, desc: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(10), dp(10))
        isClickable = true
        setOnClickListener {
            qrSelectedSource = id; qrSourceExpanded = false
            qrPickedUri = null; qrPickedName = null; qrScanResult = null; qrScanBusy = false
            renderQrScanner()
        }
        addView(TextView(this@MainActivity).apply {
            text = icon; textSize = 16f; gravity = Gravity.CENTER; setTextColor(textMain)
            background = bg(Color.rgb(235, 236, 239), 10)
        }, LinearLayout.LayoutParams(dp(34), dp(34)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(titleText, 14f, true).apply { setPadding(dp(2), 0, dp(2), dp(1)) })
            addView(subLabel(desc, 11f))
        }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    internal fun qrSourcePanel(source: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (source == "teks") {
            val input = edit("Masukkan teks atau link…", true)
            addView(input)
            addView(qrDarkButton("Buat QR") {
                val text = input.text.toString().trim()
                if (text.isEmpty()) { toast("Teks / link tidak boleh kosong"); return@qrDarkButton }
                generateQr(this@MainActivity, text)?.let { bmp ->
                    addView(qrResultCard(bmp = bmp, resultText = null, onShareText = { text }))
                } ?: toast("Gagal membuat QR")
            })
        } else {
            addView(qrUploadBox(source))
            addView(TextView(this@MainActivity).apply {
                text = "ⓘ  Format yang didukung: JPG, PNG, WEBP\n    Maksimal ukuran: 10 MB"
                textSize = 11f; setTextColor(textMuted)
                setPadding(dp(2), dp(10), dp(2), dp(4))
            })
            if (qrScanBusy) {
                addView(subLabel("Memindai QR…", 12f))
            } else if (qrPickedUri != null) {
                addView(qrResultCard(bmp = null, resultText = qrScanResult, onShareText = { qrScanResult ?: "" }))
            }
        }
    }

    internal fun qrUploadBox(source: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(30), dp(20), dp(26))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel2); cornerRadius = dp(16).toFloat()
            setStroke(dp(1), line)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) }
        if (qrPickedUri != null) {
            addView(ImageView(this@MainActivity).apply {
                setImageURI(qrPickedUri)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
            }, LinearLayout.LayoutParams(dp(140), dp(140)).apply { bottomMargin = dp(10) })
            addView(subLabel(qrPickedName ?: "Gambar terpilih", 11f).apply { gravity = Gravity.CENTER })
        } else {
            addView(TextView(this@MainActivity).apply {
                text = "▧"; textSize = 30f; setTextColor(textMuted); gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(10) })
            addView(subLabel(
                if (source == "foto") "Pilih foto atau scan QR dengan kamera" else "Ketuk untuk memilih file gambar",
                12f
            ).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14)) })
        }
        addView(qrDarkButton(if (source == "foto") "Foto & Scan" else "Pilih File") {
            if (source == "foto") pickQrPhoto() else pickQrFile()
        })
    }

    internal fun qrDarkButton(text: String, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(Color.WHITE)
        background = bg(Color.rgb(17, 17, 19), 24)
        minHeight = dp(46)
        setPadding(dp(24), dp(2), dp(24), dp(2))
        setStateListAnimator(null)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-2, dp(46)).apply { gravity = Gravity.CENTER; bottomMargin = dp(6) }
    }

    internal fun qrResultCard(bmp: Bitmap?, resultText: String?, onShareText: () -> String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = bg(panel2, 14, line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        addView(label(if (bmp != null) "QR berhasil dibuat" else if (resultText != null) "Hasil pindaian" else "Tidak terdeteksi kode QR", 14f, true))
        if (bmp != null) {
            // Preview QR mengikuti referensi: kotak putih untuk QR, teks/link tepat di bawahnya.
            val preview = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = bg(Color.WHITE, 2)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                    topMargin = dp(10); bottomMargin = dp(12)
                }
                addView(ImageView(this@MainActivity).apply {
                    setImageBitmap(bmp); adjustViewBounds = true; scaleType = ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(dp(250), dp(250)).apply { gravity = Gravity.CENTER })
                addView(subLabel(onShareText(), 13f).apply {
                    setTextColor(Color.rgb(45, 45, 48)); gravity = Gravity.CENTER
                    setPadding(dp(8), dp(10), dp(8), dp(4))
                })
            }
            addView(preview)
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(button("Download") { saveQrBitmap(bmp) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
            row.addView(button("Bagikan") { shareQrBitmap(bmp, onShareText()) }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
            addView(row)
        } else if (resultText != null) {
            addView(subLabel(resultText, 13f).apply { setTextColor(textMain); setPadding(dp(2), dp(10), dp(2), dp(10)) })
            addView(qrDarkButton("Bagikan") { shareText(onShareText()) })
        }
    }

    internal fun saveQrBitmap(bitmap: Bitmap) {
        runCatching {
            val name = "MyTools_QR_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.png"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/MyTools")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Penyimpanan tidak tersedia")
            contentResolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) error("Gagal menulis QR")
            } ?: error("Tidak bisa membuka penyimpanan")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            toast("QR disimpan ke Pictures/MyTools")
        }.onFailure { toast("Download QR gagal: ${it.message}") }
    }

    internal fun shareQrBitmap(bitmap: Bitmap, text: String) {
        runCatching {
            val file = File(cacheDir, "MyTools_QR_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, text)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Bagikan QR"))
        }.onFailure { toast("Gagal membagikan QR: ${it.message}") }
    }

    internal fun generateQr(ctx: Context, text: String): Bitmap? = runCatching {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 600, 600)
        val bmp = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        for (x in 0 until 600) for (y in 0 until 600) bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        bmp
    }.getOrNull()

    internal fun scanQrWithCamera() {
        runCatching {
            val photoFile = File(cacheDir, "qr_scan_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            qrCameraOutUri = uri
            qrSelectedSource = "foto"
            qrSourceExpanded = false
            qrPickedUri = null
            qrPickedName = null
            qrScanResult = null
            qrScanBusy = false

            val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (camera.resolveActivity(packageManager) == null) {
                toast("Kamera tidak tersedia")
                return
            }
            startActivityForResult(camera, 1043)
        }.onFailure {
            toast("Tidak bisa membuka kamera: ${it.message}")
        }
    }

    internal fun pickQrFile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
        }, 1041)
    }

    internal fun pickQrPhoto() {
        val gallery = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        val chooser = Intent.createChooser(gallery, "Pilih sumber foto")
        runCatching {
            val photoFile = File(cacheDir, "qr_capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", photoFile)
            qrCameraOutUri = uri
            val camera = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            if (camera.resolveActivity(packageManager) != null) {
                chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
            }
        }
        startActivityForResult(chooser, 1042)
    }

    internal fun decodeQrFromUri(uri: Uri) {
        qrScanBusy = true; qrPickedUri = uri; qrScanResult = null
        renderQrScanner()
        thread {
            val text = runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
                    ?: error("Gambar tidak bisa dibuka")
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("Gambar tidak valid")
                var sample = 1
                while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, opts) }
                    ?: error("Gambar tidak bisa dibuka")
                try {
                    val pixels = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    val source = RGBLuminanceSource(bmp.width, bmp.height, pixels)
                    val binary = BinaryBitmap(HybridBinarizer(source))
                    MultiFormatReader().decode(binary).text
                } finally {
                    bmp.recycle()
                }
            }
            runOnUiThread {
                qrScanBusy = false
                qrScanResult = text.getOrNull() ?: run {
                    if (text.exceptionOrNull() !is NotFoundException) toast("Gagal membaca gambar")
                    null
                }
                if (qrScanResult == null) toast("Tidak ditemukan kode QR pada gambar ini")
                renderQrScanner()
            }
        }
    }

    // ---------- KONVERSI FILE (Home kategori -> Form -> Pilih File -> Proses -> Selesai) ----------

    internal data class ConvCategory(
        val id: String, val icon: String, val title: String, val hint: String,
        val desc: String, val formDesc: String, val formats: List<String>, val mime: String
    )

    internal val convCategories = listOf(
        ConvCategory("arsip", "◫", "Arsip & Kompresi", "ZIP, RAR, 7Z, TAR, dll.",
            "Kompresi dan ekstrak file arsip dengan mudah.",
            "Kompresi dan ekstrak file arsip dengan mudah.",
            listOf("RAR", "7Z", "TAR", "GZ", "ISO", "Folder Normal (Extract)"), "*/*"),
        ConvCategory("dokumen", "▤", "Dokumen & Teks", "DOCX, PDF, XLSX, dll.",
            "Word, PDF, Excel, TXT, dll.",
            "Konversi dokumen, lembar kerja, presentasi, dan e-book.",
            listOf("PDF", "DOCX", "XLSX", "PPTX", "TXT", "RTF"), "*/*"),
        ConvCategory("gambar", "▧", "Gambar & Desain", "PNG, JPG, SVG, PSD, dll.",
            "PNG, JPG, JPEG, WEBP, BMP, GIF, HEIC, dll.",
            "Konversi gambar langsung di HP ke PNG, JPG, WEBP, atau BMP.",
            listOf("PNG", "JPG", "WEBP", "BMP"), "image/*"),
        ConvCategory("audio", "♪", "Audio & Musik", "MP3, WAV, FLAC, dll.",
            "MP3, WAV, FLAC, dll.",
            "Konversi antar format audio serta ekstrak kualitas.",
            listOf("MP3", "WAV", "OGG", "M4A"), "audio/*"),
        ConvCategory("video", "▣", "Video", "MP4, MKV, AVI, WEBM",
            "Konversi format video dan ekstrak media.",
            "Konversi video secara lokal.",
            listOf("MP4", "MKV", "AVI", "WEBM"), "video/*")
    )

    internal var convCategory: String? = null
    internal var convStage = "form" // "form" | "pickfile" | "progress" | "done"
    internal var convToExpanded = false
    internal var convPickedUri: Uri? = null
    internal var convPickedName: String? = null
    internal var convFromFormat = "Otomatis terdeteksi"
    internal var convToFormat: String? = null
    internal var convStepIndex = 0
    internal var convResultUri: Uri? = null
    internal var convResultName: String? = null
    internal var convResultSizeText: String? = null

    internal fun fileConvertTool() {
        clearPage("Konversi File")
        convCategory = null
        convStage = "form"
        convResetSelection()
        renderConv()
    }

    internal fun convResetSelection() {
        convToExpanded = false
        convPickedUri = null
        convPickedName = null
        convFromFormat = "Otomatis terdeteksi"
        convToFormat = null
        convStepIndex = 0
        convResultUri = null
        convResultName = null
        convResultSizeText = null
    }

    internal fun convGoBackStage() {
        when (convStage) {
            "pickfile" -> convStage = "form"
            "progress" -> convStage = "form"
            "done" -> { convCategory = null; convStage = "form"; convResetSelection() }
            else -> { convCategory = null; convStage = "form"; convResetSelection() }
        }
        renderConv()
    }

    internal fun renderConv() {
        content.removeAllViews()
        val cat = convCategories.find { it.id == convCategory }
        if (cat == null) {
            title.text = "Konversi File"
            action.text = "⋮"; action.textSize = 25f; action.setOnClickListener { showAbout() }
            back.setOnClickListener { navigateBack() }
            renderConvHome()
            return
        }
        back.setOnClickListener { convGoBackStage() }
        when (convStage) {
            "pickfile" -> {
                title.text = "Pilih File"
                action.text = cat.icon; action.textSize = 17f; action.setOnClickListener {}
                renderConvPickFile(cat)
            }
            "progress" -> {
                title.text = cat.title
                action.text = cat.icon; action.textSize = 17f; action.setOnClickListener {}
                renderConvProgress(cat)
            }
            "done" -> {
                title.text = "Selesai"
                action.text = "⋮"; action.textSize = 25f; action.setOnClickListener { showAbout() }
                renderConvDone(cat)
            }
            else -> {
                title.text = cat.title
                action.text = cat.icon; action.textSize = 17f; action.setOnClickListener {}
                renderConvForm(cat)
            }
        }
    }

    internal fun renderConvHome() {
        content.addView(label("Konversi File", 22f, true))
        content.addView(subLabel("Pilih kategori konversi yang kamu butuhkan.", 12f).apply { setPadding(dp(2), 0, dp(2), dp(10)) })
        val searchBox = edit("Cari kategori…")
        content.addView(searchBox)
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(listBox)
        fun renderList(query: String) {
            listBox.removeAllViews()
            val q = query.trim().toLowerCase(Locale.getDefault())
            val filtered = convCategories.filter { q.isEmpty() || it.title.toLowerCase(Locale.getDefault()).contains(q) || it.desc.toLowerCase(Locale.getDefault()).contains(q) }
            if (filtered.isEmpty()) listBox.addView(subLabel("Tidak ada kategori yang cocok.", 13f))
            filtered.forEach { c ->
                listBox.addView(convCategoryCard(c).apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) } })
            }
        }
        renderList("")
        searchBox.addTextChangedListener(SimpleTextWatcher { renderList(it) })
    }

    internal fun convCategoryCard(c: ConvCategory): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = bg(panel2, 16)
        isClickable = true
        setOnClickListener {
            convCategory = c.id; convStage = "form"; convResetSelection(); renderConv()
        }
        addView(TextView(this@MainActivity).apply {
            text = c.icon; textSize = 20f; gravity = Gravity.CENTER; setTextColor(textMain)
            background = bg(Color.rgb(235, 236, 239), 12)
        }, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginEnd = dp(14) })
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(c.title, 15f, true).apply { setPadding(dp(2), 0, dp(2), dp(1)) })
            addView(subLabel(c.desc, 11f))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply { text = "›"; textSize = 22f; setTextColor(textMuted) })
    }

    internal fun renderConvForm(cat: ConvCategory) {
        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = bg(panel2, 16)
            layoutParams = LinearLayout.LayoutParams(dp(84), dp(66)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dp(16) }
            addView(TextView(this@MainActivity).apply { text = cat.icon; textSize = 26f; setTextColor(textMuted) })
        })
        content.addView(subLabel(cat.formDesc, 12f).apply { gravity = Gravity.CENTER; setPadding(dp(2), 0, dp(2), dp(16)) })

        content.addView(label("Pilih file", 12f, true).apply { setPadding(dp(2), 0, dp(2), dp(6)) })
        content.addView(convFileBox(cat))

        content.addView(subLabel("Format Asal", 11f).apply { setPadding(dp(2), dp(14), dp(2), dp(6)) })
        content.addView(convStaticRow(convFromFormat))

        content.addView(subLabel("Ubah Ke", 11f).apply { setPadding(dp(2), dp(14), dp(2), dp(6)) })
        content.addView(convToDropdownRow(cat))
        if (convToExpanded) content.addView(convToOptionsBox(cat))

        val canStart = convPickedUri != null && convToFormat != null
        content.addView(convPrimaryButton("Mulai Konversi", canStart) { startConversion(cat) })
    }

    internal fun convFileBox(cat: ConvCategory): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(18), dp(20), dp(18), dp(20))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(panel2); cornerRadius = dp(14).toFloat(); setStroke(dp(1), line)
        }
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        isClickable = true
        setOnClickListener { convStage = "pickfile"; renderConv() }
        if (convPickedUri != null) {
            addView(TextView(this@MainActivity).apply { text = "✓"; textSize = 22f; setTextColor(textMain); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(6) })
            addView(label(convPickedName ?: "File terpilih", 13f, true).apply { gravity = Gravity.CENTER })
        } else {
            addView(TextView(this@MainActivity).apply { text = "▤"; textSize = 26f; setTextColor(textMuted); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(8) })
            addView(subLabel("Ketuk untuk memilih file", 13f).apply { gravity = Gravity.CENTER })
            addView(subLabel(cat.hint, 11f).apply { gravity = Gravity.CENTER })
        }
    }

    internal fun convStaticRow(text: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = bg(panel2, 14, line)
        addView(label(text, 13f).apply { setTextColor(textMuted) }, LinearLayout.LayoutParams(0, -2, 1f))
    }

    internal fun convToDropdownRow(cat: ConvCategory): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = bg(panel2, 14, line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = if (convToExpanded) dp(6) else dp(16) }
        isClickable = true
        setOnClickListener { convToExpanded = !convToExpanded; renderConv() }
        addView(label(convToFormat ?: "Pilih format tujuan", 13f).apply {
            setTextColor(if (convToFormat == null) textMuted else textMain)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply {
            text = if (convToExpanded) "⌃" else "⌄"; textSize = 13f; setTextColor(textMuted)
        })
    }

    internal fun convToOptionsBox(cat: ConvCategory): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(4), dp(4), dp(4), dp(4))
        background = bg(panel2, 14, line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        cat.formats.forEachIndexed { i, fmt ->
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                isClickable = true
                setOnClickListener { convToFormat = fmt; convToExpanded = false; renderConv() }
                addView(label(fmt, 14f), LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@MainActivity).apply {
                    text = if (convToFormat == fmt) "●" else "○"
                    textSize = 14f; setTextColor(if (convToFormat == fmt) textMain else textMuted)
                })
            })
            if (i != cat.formats.lastIndex) addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(10), 0, dp(10), 0) }
                setBackgroundColor(line)
            })
        }
    }

    internal fun convPrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit): Button = Button(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(if (enabled) Color.WHITE else textMuted)
        background = bg(if (enabled) Color.rgb(17, 17, 19) else panel2, 14, if (enabled) null else line)
        minHeight = dp(54)
        isEnabled = enabled
        setStateListAnimator(null)
        setOnClickListener { if (enabled) onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(6) }
    }

    internal fun renderConvPickFile(cat: ConvCategory) {
        content.addView(label("Pilih File", 20f, true).apply { setPadding(dp(2), 0, dp(2), dp(2)) })
        content.addView(subLabel("Pilih file dari penyimpanan.", 12f).apply { setPadding(dp(2), 0, dp(2), dp(14)) })
        val stat = runCatching {
            val sfs = StatFs(Environment.getExternalStorageDirectory().path)
            val total = sfs.totalBytes; val free = sfs.availableBytes
            "${convFormatSize(total - free)} / ${convFormatSize(total)}"
        }.getOrElse { "" }
        val rows = listOf(
            Triple("▥", "Penyimpanan Internal", stat),
            Triple("▤", "Dokumen", "Ketuk untuk memilih file"),
            Triple("▾", "Download", "Ketuk untuk memilih file"),
            Triple("▧", "Gambar", "Ketuk untuk memilih file"),
            Triple("♪", "Musik", "Ketuk untuk memilih file"),
            Triple("▶", "Video", "Ketuk untuk memilih file")
        )
        rows.forEach { (icon, name, sub) ->
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = bg(panel2, 14)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                isClickable = true
                setOnClickListener { pickConvFile(cat) }
                addView(TextView(this@MainActivity).apply { text = icon; textSize = 16f; setTextColor(textMuted) }, LinearLayout.LayoutParams(dp(28), -2))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label(name, 14f, true).apply { setPadding(dp(4), 0, dp(4), 0) })
                    addView(subLabel(sub, 11f).apply { setPadding(dp(4), 0, dp(4), 0) })
                }, LinearLayout.LayoutParams(0, -2, 1f))
                addView(TextView(this@MainActivity).apply { text = "›"; textSize = 20f; setTextColor(textMuted) })
            })
        }
    }

    internal fun renderConvProgress(cat: ConvCategory) {
        content.addView(ProgressBar(this).apply {
            isIndeterminate = true
        }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(24); bottomMargin = dp(16) })
        content.addView(label("Mengonversi…", 16f, true).apply { gravity = Gravity.CENTER })
        content.addView(subLabel("Jangan tutup aplikasi.", 12f).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(18)) })

        val pct = (convStepIndex * 100 / 3).coerceIn(0, 100)
        val barRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        barRow.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = pct
        }, LinearLayout.LayoutParams(0, dp(10), 1f))
        barRow.addView(subLabel("$pct%", 11f).apply { setPadding(dp(8), 0, 0, 0) })
        content.addView(barRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) })

        val steps = listOf("Membaca file", "Memproses data", "Menyimpan hasil")
        steps.forEachIndexed { i, s ->
            val active = convStepIndex == i + 1
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), dp(6), dp(2), dp(6))
                addView(TextView(this@MainActivity).apply {
                    text = if (convStepIndex > i) "✓" else if (active) "◍" else "○"
                    textSize = 14f
                    setTextColor(if (convStepIndex > i) textMain else textMuted)
                }, LinearLayout.LayoutParams(dp(24), -2))
                addView(label(s, 13f).apply { setTextColor(if (convStepIndex >= i + 1) textMain else textMuted) })
            })
        }
    }

    internal fun renderConvDone(cat: ConvCategory) {
        content.addView(TextView(this).apply {
            text = "✓"; textSize = 30f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = bg(Color.rgb(17, 17, 19), 40)
        }, LinearLayout.LayoutParams(dp(64), dp(64)).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(20); bottomMargin = dp(14) })
        content.addView(label("Konversi Berhasil", 18f, true).apply { gravity = Gravity.CENTER })
        content.addView(subLabel("File telah berhasil dikonversi.", 12f).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(20)) })

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = bg(panel2, 14)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(18) }
            addView(TextView(this@MainActivity).apply { text = "▤"; textSize = 18f; setTextColor(textMuted) }, LinearLayout.LayoutParams(dp(30), -2))
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(label(convResultName ?: "hasil", 14f, true).apply { setPadding(dp(4), 0, dp(4), 0) })
                addView(subLabel(convResultSizeText ?: "", 11f).apply { setPadding(dp(4), 0, dp(4), 0) })
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })

        content.addView(convPrimaryButton("Buka File", true) {
            val uri = convResultUri ?: return@convPrimaryButton
            val mime = contentResolver.getType(uri) ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(convResultName?.substringAfterLast('.', "") ?: "") ?: "*/*"
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            }.onFailure { toast("Tidak ada aplikasi untuk membuka file ini") }
        })
        content.addView(button("Bagikan") {
            val uri = convResultUri ?: return@button
            val mime = contentResolver.getType(uri) ?: "*/*"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = mime; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Bagikan hasil konversi"))
        })
        content.addView(button("Konversi Lagi") {
            convStage = "form"; convResetSelection(); renderConv()
        })
    }

    internal fun pickConvFile(cat: ConvCategory) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = cat.mime; addCategory(Intent.CATEGORY_OPENABLE)
        }, 1050)
    }

    internal fun convOutputDir(): File = File(getExternalFilesDir(null) ?: filesDir, "conversions").apply { mkdirs() }

    internal fun convExtensionFor(format: String): String = when (format) {
        "Folder Normal (Extract)" -> "zip"
        else -> format.toLowerCase(Locale.getDefault()).replace(" ", "").replace("(", "").replace(")", "")
    }

    internal fun convFormatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var v = bytes.toDouble(); var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    internal fun convCopyStream(uri: Uri, outFile: File): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    total += n
                }
                output.fd.sync()
            }
        } ?: error("Gagal membaca file")
        return total
    }

    internal fun convImageSampleSize(uri: Uri, maxDimension: Int = 2048): Int {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, opts)
        } ?: error("Gagal membaca gambar")
        if (opts.outWidth <= 0 || opts.outHeight <= 0) error("Gambar tidak valid")
        var sample = 1
        while (opts.outWidth / sample > maxDimension || opts.outHeight / sample > maxDimension) {
            sample *= 2
        }
        return sample
    }

    internal fun convDecodeBitmapSafely(uri: Uri): Bitmap {
        val sample = convImageSampleSize(uri)
        val opts = android.graphics.BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }
        val bitmap = contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input, null, opts)
        } ?: error("Gagal membaca gambar")
        return bitmap ?: error("Gambar tidak dapat diproses")
    }

    internal fun convSourceExtension(name: String): String =
        name.substringAfterLast('.', "").trim().toLowerCase(Locale.ROOT)

    /** Menulis bitmap 24-bit BMP tanpa library tambahan. Transparansi dirender putih. */
    internal fun writeBitmapAsBmp(bitmap: Bitmap, outFile: File) {
        val width = bitmap.width
        val height = bitmap.height
        val rowSize = ((24 * width + 31) / 32) * 4
        val pixelDataSize = rowSize * height
        val fileSize = 54 + pixelDataSize
        DataOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { out ->
            fun le16(v: Int) { out.writeByte(v and 0xFF); out.writeByte((v ushr 8) and 0xFF) }
            fun le32(v: Int) {
                out.writeByte(v and 0xFF); out.writeByte((v ushr 8) and 0xFF)
                out.writeByte((v ushr 16) and 0xFF); out.writeByte((v ushr 24) and 0xFF)
            }
            // BITMAPFILEHEADER
            le16(0x4D42); le32(fileSize); le16(0); le16(0); le32(54)
            // BITMAPINFOHEADER
            le32(40); le32(width); le32(height); le16(1); le16(24)
            le32(0); le32(pixelDataSize); le32(2835); le32(2835); le32(0); le32(0)

            val row = ByteArray(rowSize)
            for (y in height - 1 downTo 0) {
                var p = 0
                for (x in 0 until width) {
                    val c = bitmap.getPixel(x, y)
                    val a = Color.alpha(c)
                    val r = if (a == 255) Color.red(c) else (Color.red(c) * a + 255 * (255 - a)) / 255
                    val g = if (a == 255) Color.green(c) else (Color.green(c) * a + 255 * (255 - a)) / 255
                    val b = if (a == 255) Color.blue(c) else (Color.blue(c) * a + 255 * (255 - a)) / 255
                    row[p++] = b.toByte(); row[p++] = g.toByte(); row[p++] = r.toByte()
                }
                while (p < row.size) row[p++] = 0
                out.write(row)
            }
        }
    }

    internal fun startConversion(cat: ConvCategory) {
        val srcUri = convPickedUri ?: return
        val srcName = convPickedName ?: "file"
        val targetFormat = convToFormat ?: return
        convStage = "progress"; convStepIndex = 0; renderConv()
        thread {
            var tempFile: File? = null
            try {
                runOnUiThread { convStepIndex = 1; renderConv() }
                val baseName = srcName.substringBeforeLast('.', srcName).ifBlank { "hasil" }
                val ext = convExtensionFor(targetFormat)
                val outFile = File(convOutputDir(), "${baseName}_converted_${System.currentTimeMillis()}.$ext")
                tempFile = File(outFile.parentFile, ".${outFile.name}.tmp")
                if (tempFile!!.exists()) tempFile!!.delete()
                runOnUiThread { convStepIndex = 2; renderConv() }

                when {
                    cat.id == "arsip" && targetFormat == "GZ" -> {
                        val buffer = ByteArray(64 * 1024)
                        contentResolver.openInputStream(srcUri)?.use { input ->
                            FileOutputStream(tempFile!!).use { fos ->
                                GZIPOutputStream(BufferedOutputStream(fos)).use { gz ->
                                    while (true) { val n=input.read(buffer); if(n<0) break; gz.write(buffer,0,n) }
                                }
                            }
                        } ?: error("Gagal membaca file")
                    }
                    cat.id == "gambar" && targetFormat in listOf("PNG","JPG","WEBP","BMP") -> {
                        val decoded = convDecodeBitmapSafely(srcUri)
                        var bmp: Bitmap? = decoded
                        try {
                            if (targetFormat == "BMP") {
                                writeBitmapAsBmp(decoded, tempFile!!)
                            } else {
                                if (targetFormat == "JPG") {
                                    val rgb = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
                                    Canvas(rgb).apply { drawColor(Color.WHITE); drawBitmap(decoded,0f,0f,null) }
                                    bmp = rgb
                                }
                                val format = when(targetFormat) {
                                    "PNG" -> Bitmap.CompressFormat.PNG
                                    "JPG" -> Bitmap.CompressFormat.JPEG
                                    else -> if(Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                                }
                                FileOutputStream(tempFile!!).use { fos ->
                                    require(bmp!!.compress(format, if(targetFormat=="PNG") 100 else 92, fos)) { "Gagal menyimpan gambar hasil konversi" }
                                }
                            }
                        } finally { if (bmp !== decoded) bmp?.recycle(); decoded.recycle() }
                    }
                    else -> {
                        val sourceExt = convSourceExtension(srcName)
                        if (sourceExt.isNotEmpty() && sourceExt.equals(ext, true)) convCopyStream(srcUri, tempFile!!)
                        else error("Konversi $sourceExt → ${targetFormat.lowercase(Locale.ROOT)} belum didukung oleh encoder aplikasi")
                    }
                }
                require(tempFile!!.exists() && tempFile!!.length() > 0L) { "File hasil kosong" }
                if (outFile.exists()) outFile.delete()
                if (!tempFile!!.renameTo(outFile)) { tempFile!!.copyTo(outFile, overwrite=true); tempFile!!.delete() }
                tempFile = null
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", outFile)
                convResultUri = uri; convResultName = outFile.name; convResultSizeText = bytesText(outFile.length())
                convStage = "done"; convStepIndex = 3
                runOnUiThread { renderConv(); toast("Konversi selesai") }
            } catch (t: Throwable) {
                tempFile?.delete()
                convStage = "form"
                runOnUiThread { renderConv(); toast("Konversi gagal: ${t.message}") }
            }
        }
    }

    internal fun pickWebFile(target: EditText, requestCode: Int) {
        webImportTarget = target
        val type = when(requestCode) { WEB_HTML_PICK_REQUEST -> "text/html"; WEB_CSS_PICK_REQUEST -> "text/css"; else -> "text/javascript" }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { this.type=type; addCategory(Intent.CATEGORY_OPENABLE) }, requestCode)
    }

    internal fun webCodeEditor(mode: String = "HTML", target: EditText? = null) {
        val normalized = when (mode.uppercase(Locale.getDefault())) {
            "HTML" -> "html"
            "CSS" -> "css"
            "JAVASCRIPT", "JS" -> "js"
            else -> "code"
        }
        editorExternalTarget = target
        editorExternalMode = normalized
        editorFile = null
        editor(null, normalized)
    }

    internal fun buildWebProject(projectName:String, html:String, css:String, js:String) {
        val h=html.trim(); val c=css.trim(); val j=js.trim()
        webBuildReady=false
        webHostButton?.isEnabled=false; webHostButton?.alpha=0.45f
        webBuildStatusView?.text="MEMERIKSA FILE…"
        if (h.isBlank()) { webBuildStatusView?.text="GAGAL • HTML wajib diisi"; toast("HTML wajib diisi"); return }
        if (h.isBlank() && (c.isNotBlank() || j.isNotBlank())) { webBuildStatusView?.text="GAGAL • HTML wajib ada"; return }

        val localCss=Regex("""(?i)(?:href|src)\s*=\s*[\"']([^\"']+\.css(?:\?[^\"']*)?)[\"']""").findAll(h).map { it.groupValues[1].substringBefore('?') }.filter { !it.startsWith("http://") && !it.startsWith("https://") && !it.startsWith("//") && !it.startsWith("data:") }.toList()
        val localJs=Regex("""(?i)<script[^>]+src\s*=\s*[\"']([^\"']+\.js(?:\?[^\"']*)?)[\"']""").findAll(h).map { it.groupValues[1].substringBefore('?') }.filter { !it.startsWith("http://") && !it.startsWith("https://") && !it.startsWith("//") && !it.startsWith("data:") }.toList()
        val warnings=mutableListOf<String>()
        if (localCss.isNotEmpty() && c.isBlank()) warnings.add("HTML memanggil CSS lokal: ${localCss.joinToString(", ")}, tetapi file CSS belum diisi.")
        if (localJs.isNotEmpty() && j.isBlank()) warnings.add("HTML memanggil JavaScript lokal: ${localJs.joinToString(", ")}, tetapi file JS belum diisi.")
        if (warnings.isNotEmpty()) {
            webBuildStatusView?.text="GAGAL • Dependency belum lengkap"
            AlertDialog.Builder(this).setTitle("Project belum lengkap").setMessage(warnings.joinToString("\n\n") + "\n\nIsi file yang kurang lalu Build lagi.").setPositiveButton("OK",null).show()
            return
        }

        val safe=(projectName.trim().ifBlank{"website"}).replace(Regex("[^A-Za-z0-9_-]"),"_")
        val dir=File(filesDir,"web_projects/$safe").apply { mkdirs() }
        runCatching {
            var htmlBody=h
            val full=h.contains("<html",true)
            if (!full) {
                htmlBody="<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><link rel=\"stylesheet\" href=\"style.css\"></head><body>$h<script src=\"script.js\"></script></body></html>"
            } else {
                if (c.isNotBlank() && !Regex("(?i)<link[^>]+href\\s*=\\s*[\"'](?:./)?style\\.css").containsMatchIn(h)) {
                    htmlBody=if (Regex("(?i)</head>").containsMatchIn(htmlBody)) htmlBody.replace(Regex("(?i)</head>"),"<link rel=\"stylesheet\" href=\"style.css\"></head>") else "<link rel=\"stylesheet\" href=\"style.css\">"+htmlBody
                }
                if (j.isNotBlank() && !Regex("(?i)<script[^>]+src\\s*=\\s*[\"'](?:./)?script\\.js").containsMatchIn(h)) {
                    htmlBody=if (Regex("(?i)</body>").containsMatchIn(htmlBody)) htmlBody.replace(Regex("(?i)</body>"),"<script src=\"script.js\"></script></body>") else htmlBody+"<script src=\"script.js\"></script>"
                }
            }
            File(dir,"index.html").writeText(htmlBody, StandardCharsets.UTF_8)
            if (c.isNotBlank()) {
                File(dir,"style.css").writeText(c, StandardCharsets.UTF_8)
                localCss.map { File(it).name }.filter { it.isNotBlank() && it != "style.css" }.distinct().forEach { File(dir,it).writeText(c, StandardCharsets.UTF_8) }
            } else File(dir,"style.css").delete()
            if (j.isNotBlank()) {
                File(dir,"script.js").writeText(j, StandardCharsets.UTF_8)
                localJs.map { File(it).name }.filter { it.isNotBlank() && it != "script.js" }.distinct().forEach { File(dir,it).writeText(j, StandardCharsets.UTF_8) }
            } else File(dir,"script.js").delete()
            prefs.edit().putString("last_web_project",dir.absolutePath).putBoolean("last_web_build_ok",true).apply()
            webBuildReady=true
            webBuildStatusView?.text="SUCCESS • BUILD BERHASIL • ${dir.name}"
            webHostButton?.isEnabled=true; webHostButton?.alpha=0.98f
            toast("Build berhasil: ${dir.name}")
        }.onFailure {
            prefs.edit().putBoolean("last_web_build_ok",false).apply()
            webBuildStatusView?.text="GAGAL • ${it.message ?: "kesalahan build"}"
            toast("Build gagal: ${it.message ?: "kesalahan file"}")
        }
    }

    internal fun previewWebProject() {
        val path=prefs.getString("last_web_project","") ?: ""
        if(path.isBlank() || !File(path,"index.html").isFile || !prefs.getBoolean("last_web_build_ok",false)){toast("Build website dulu sampai SUCCESS");return}
        previewHtmlText(File(path,"index.html").readText(StandardCharsets.UTF_8),"HTML")
    }
    internal fun previewHtmlText(html:String,mode:String) { val w=WebView(this).apply{settings.javaScriptEnabled=true; settings.domStorageEnabled=true; loadDataWithBaseURL(null,if(mode=="HTML") html else "<pre>${html.htmlEsc()}</pre>","text/html","UTF-8",null)}; clearPage("Preview"); content.setPadding(0,0,0,0); content.addView(w,LinearLayout.LayoutParams(-1,0,1f)) }
    internal fun hostWebProject() = hostHomeWifiProject()
    internal fun hostHomeWifiProject() {
        val path=prefs.getString("last_web_project","") ?: ""
        if(path.isBlank() || !File(path,"index.html").isFile || !prefs.getBoolean("last_web_build_ok",false)) { toast("Build harus SUCCESS sebelum hosting"); return }
        wifiHtmlHostingTool()
    }
    internal fun openWebFolder() { val p=prefs.getString("last_web_project","") ?: ""; if(p.isBlank()){toast("Belum ada project");return}; clearPage("Project Files"); File(p).listFiles()?.forEach{content.addView(settingRowClickable(it.name, "${it.length()} bytes", "File project", "file-outline"){ if(it.extension.equals("html",true)||it.extension.equals("htm",true)) previewHtmlText(it.readText(StandardCharsets.UTF_8),"HTML") else output(it.readText(StandardCharsets.UTF_8)) })} }
    internal fun saveWebEditor(mode:String,text:String){ editorPendingTarget?.setText(text); val ext=when(mode){"HTML"->"html";"CSS"->"css";"JavaScript"->"js";else->"txt"}; val f=File(filesDir,"web_editor");f.mkdirs();File(f,"untitled.$ext").writeText(text);toast("Disimpan: untitled.$ext") }
    internal fun findInEditor(e:EditText){ val q=EditText(this); q.hint="Cari"; AlertDialog.Builder(this).setTitle("Cari").setView(q).setPositiveButton("Cari"){_,_->val i=e.text.toString().indexOf(q.text.toString()); if(i>=0){e.requestFocus();e.setSelection(i,i+q.text.length)}else toast("Tidak ditemukan")}.setNegativeButton("Batal",null).show() }
    internal fun applySimpleEmmet(e: EditText) {
        val t=e.text.toString().trim()
        val x=when(t) {
            "!" -> "<!doctype html>\n<html>\n<head><meta charset=\"UTF-8\"></head>\n<body>\n</body>\n</html>"
            "div" -> "<div></div>"
            "p" -> "<p></p>"
            "ul" -> "<ul>\n  <li></li>\n</ul>"
            "button" -> "<button></button>"
            "input" -> "<input type=\"text\">"
            else -> null
        }
        if (x != null) { e.setText(x); e.setSelection(e.length()); toast("Emmet diterapkan") } else toast("Snippet tidak dikenal")
    }

internal fun workspaceCard(dir: File): View {
        val files = dir.listFiles()?.filter { it.name != "workspace.json" } ?: emptyList()
        val modified = dir.lastModified()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(13), dp(12), dp(13), dp(10)); background = bg(panel2, 18, line)
            isClickable = true; isFocusable = true; contentDescription = "Workspace ${dir.name}"
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(MdiIconView(this).apply { setIconName("folder-star-outline"); setIconSize(25f); setTextColor(textMain); background = bg(panel, 13, line); setPadding(dp(9), dp(9), dp(9), dp(9)) }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(10) })
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(dir.name, 15f, true))
        texts.addView(subLabel("${files.size} item  •  ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(modified))}", 10f))
        top.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(TextView(this).apply { text = "›"; textSize = 27f; setTextColor(textMuted); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(34), dp(44)) })
        card.addView(top)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, 0) }
        fun small(text: String, action: () -> Unit) = TextView(this).apply { this.text = text; textSize = 11f; gravity = Gravity.CENTER; setTextColor(textMain); background = bg(panel, 10, line); isClickable = true; isFocusable = true; setPadding(dp(10), 0, dp(10), 0); setOnClickListener { action() } }
        actions.addView(small("Buka") { prefs.edit().putString("last_workspace", dir.absolutePath).apply(); workspaceDetailTool(dir) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(5) })
        actions.addView(small("Editor") { dir.listFiles()?.firstOrNull { it.isFile && it.name != "workspace.json" }?.let { editor(it) } ?: toast("Belum ada file") }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(5) })
        card.addView(actions)
        card.setOnClickListener { prefs.edit().putString("last_workspace", dir.absolutePath).apply(); workspaceDetailTool(dir) }
        return card.apply { layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) } }
    }

    internal fun workspaceDetailTool(dir: File) {
        clearPage("Workspace: ${dir.name}")
        val files = dir.listFiles()?.filter { it.name != "workspace.json" }?.sortedBy { it.name.lowercase(Locale.getDefault()) } ?: emptyList()
        content.addView(toolHeader(dir.name, "${files.size} item • ${dir.absolutePath}", "folder-open-outline"), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        content.addView(compactButtonRow(
            "+ File" to {
                val n = edit("Nama file", false)
                AlertDialog.Builder(this).setTitle("File baru").setView(n)
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Buat") { _, _ ->
                        val name = safeFileName(n.text.toString().trim())
                        if (name.isBlank()) { toast("Nama file kosong"); return@setPositiveButton }
                        runCatching { File(dir, name).apply { parentFile?.mkdirs(); if (!exists()) writeText("") } }
                            .onSuccess { workspaceDetailTool(dir) }
                            .onFailure { toast("Gagal membuat file: ${it.message}") }
                    }.show()
            },
            "Refresh" to { workspaceDetailTool(dir) }
        ))
        files.forEach { file ->
            content.addView(settingRowClickable(file.name, "${bytesText(file.length())}", file.absolutePath, "file-outline") { editor(file) })
        }
    }

    internal fun startStaticWebServer(root: File, port: Int, status: TextView? = null): Boolean {
        if (server != null && !server!!.isClosed) {
            toast("Server sudah berjalan")
            return false
        }
        val canonicalRoot = runCatching { root.canonicalFile }.getOrNull() ?: run {
            toast("Folder project tidak valid")
            return false
        }
        val socket = runCatching { ServerSocket(port) }.getOrElse {
            toast("Port $port gagal dibuka: ${it.message}")
            return false
        }
        server = socket
        status?.text = "RUNNING :$port"
        thread(name = "mytools-http-$port") {
            try {
                while (!socket.isClosed) {
                    val client = socket.accept()
                    thread(name = "mytools-http-client") { serveStaticClient(client, canonicalRoot) }
                }
            } catch (_: SocketException) {
                // Normal when Stop closes the ServerSocket.
            } catch (t: Throwable) {
                runOnUiThread { status?.text = "ERROR: ${t.message}" }
            } finally {
                runOnUiThread {
                    if (server === socket) {
                        server = null
                        if (status != null) status.text = "STOPPED"
                    }
                }
                runCatching { socket.close() }
            }
        }
        return true
    }

    internal fun serveStaticClient(socket: Socket, root: File) {
        socket.soTimeout = 8000
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return
            var headerCount = 0
            while (headerCount++ < 100) {
                val h = reader.readLine() ?: break
                if (h.isEmpty()) break
            }
            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                writeHttpResponse(socket, 400, "text/plain; charset=utf-8", "Bad Request")
                return
            }
            val method = parts[0].toUpperCase(Locale.US)
            if (method != "GET" && method != "HEAD") {
                writeHttpResponse(socket, 405, "text/plain; charset=utf-8", "Method Not Allowed", method == "HEAD")
                return
            }
            val rawPath = runCatching { URLDecoder.decode(parts[1].substringBefore('?'), "UTF-8") }.getOrElse { "/" }
            val relative = rawPath.removePrefix("/").ifBlank { "index.html" }
            val requested = File(root, relative).canonicalFile
            if (requested != root && !requested.path.startsWith(root.path + File.separator)) {
                writeHttpResponse(socket, 403, "text/plain; charset=utf-8", "Forbidden", method == "HEAD")
                return
            }
            val file = if (requested.isDirectory) File(requested, "index.html") else requested
            if (!file.isFile) {
                writeHttpResponse(socket, 404, "text/plain; charset=utf-8", "Not Found", method == "HEAD")
                return
            }
            val bytes = file.readBytes()
            val mime: String = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.toLowerCase(Locale.US))
                ?: when (file.extension.toLowerCase(Locale.US)) {
                    "html", "htm" -> "text/html"
                    "css" -> "text/css"
                    "js", "mjs" -> "text/javascript"
                    "json" -> "application/json"
                    "svg" -> "image/svg+xml"
                    "wasm" -> "application/wasm"
                    else -> "application/octet-stream"
                }
            writeHttpResponseBytes(socket, 200, "$mime; charset=utf-8", bytes, method == "HEAD")
        } catch (_: Throwable) {
            runCatching { writeHttpResponse(socket, 500, "text/plain; charset=utf-8", "Server Error") }
        } finally {
            runCatching { socket.close() }
        }
    }

    internal fun writeHttpResponse(
        socket: Socket,
        code: Int,
        contentType: String,
        body: String,
        headOnly: Boolean = false
    ): Unit {
        writeHttpResponseBytes(socket, code, contentType, body.toByteArray(StandardCharsets.UTF_8), headOnly)
    }

    internal fun writeHttpResponseBytes(
        socket: Socket,
        code: Int,
        contentType: String,
        body: ByteArray,
        headOnly: Boolean = false
    ): Unit {
        val reason = when (code) {
            200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"
            404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error"
        }
        val header = "HTTP/1.1 $code $reason\\r\\n" +
            "Content-Type: $contentType\\r\\n" +
            "Content-Length: ${body.size}\\r\\n" +
            "Connection: close\\r\\n\\r\\n"
        val out = socket.getOutputStream()
        out.write(header.toByteArray(StandardCharsets.ISO_8859_1))
        if (!headOnly) out.write(body)
        out.flush()
    }








    internal fun startHomeWifiHosting() {
        val root=pendingHostingRoot ?: File(prefs.getString("last_web_project","") ?: "")
        val port=pendingHostingPort
        if(!root.isDirectory || !File(root,"index.html").isFile){toast("Project tidak valid");return}
        stopWifiHtmlHosting()
        hostingStatusView?.text="MEMULAI SERVER…"
        if(!startStaticWebServer(root,port,hostingStatusView)){return}
        val addresses=localIpv4Addresses()
        val host=wifiIpv4Address() ?: addresses.firstOrNull { !it.startsWith("127.") } ?: ""
        if(host.isBlank()) {
            stopStaticWebServer(); hostingStatusView?.text="GAGAL • HP tidak terhubung ke Wi-Fi"; toast("Hubungkan HP ke Wi-Fi rumah dulu"); return
        }
        val link="http://$host:$port/"
        hostingUrlView?.text="URL: $link\nAlamat lain: ${addresses.drop(1).joinToString(", ").ifBlank{"-"}}"
        hostingCredentialsView?.text="SSID: ${currentWifiSsid()}\nPassword Wi-Fi: perangkat lain harus sudah terhubung ke Wi-Fi yang sama"
        hostingStatusView?.text="SUCCESS • HOSTING AKTIF"
        hostingQrView?.visibility=View.VISIBLE
        generateHostingQr(link)
        toast("Hosting berhasil • buka URL dari perangkat lain")
    }

    internal fun wifiIpv4Address(): String? {
        return runCatching {
            val all=NetworkInterface.getNetworkInterfaces()
            while(all.hasMoreElements()) {
                val ni=all.nextElement()
                val name=ni.name?.lowercase(Locale.US).orEmpty()
                if(!ni.isUp || ni.isLoopback || !(name.contains("wlan") || name.contains("wifi"))) continue
                val addrs=ni.inetAddresses
                while(addrs.hasMoreElements()) {
                    val a=addrs.nextElement()
                    if(a is Inet4Address && !a.isLoopbackAddress) return@runCatching a.hostAddress
                }
            }
            null
        }.getOrNull()
    }

    internal fun currentWifiSsid(): String {
        return runCatching {
            val wm=applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION") val ssid=wm.connectionInfo?.ssid?.trim('"')
            if(ssid.isNullOrBlank() || ssid=="<unknown ssid>") "SSID tidak tersedia" else ssid
        }.getOrDefault("SSID tidak tersedia")
    }

    internal fun ensureHotspotPermissionAndStart() { startHomeWifiHosting() }

    internal fun startWifiHtmlHosting() { startHomeWifiHosting() }

    internal fun stopWifiHtmlHosting() {
        runCatching { hotspotReservation?.close() }; hotspotReservation=null
        stopStaticWebServer()
        hostingStatusView?.text="STOPPED"
        hostingUrlView?.text="URL: -"
        hostingCredentialsView?.text="SSID: -\nPassword Wi-Fi: -"
        hostingQrView?.visibility=View.GONE
    }

    internal fun localIpv4Addresses(): List<String> {
        val out = mutableListOf<String>()
        runCatching {
            val all = NetworkInterface.getNetworkInterfaces()
            while (all.hasMoreElements()) {
                val ni = all.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                val addrs = ni.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && !a.isLoopbackAddress) out.add(a.hostAddress.orEmpty())
                }
            }
        }
        return out.distinct()
    }

internal fun mimeTool() {
        clearPage("MIME Type Lookup")
        val e=edit("nama file, contoh photo.png"); content.addView(e)
        content.addView(button("Lookup") {
            val ext=e.text.toString().substringAfterLast('.',"").toLowerCase(Locale.getDefault())
            output(if(ext.isEmpty()) "Ekstensi tidak ditemukan" else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream")
        })
    }




    internal fun wordFrequencyTool() {
        clearPage("Word Frequency")
        val e=edit("Teks",true); content.addView(e)
        content.addView(button("Analyze") {
            val map=e.text.toString().toLowerCase(Locale.getDefault()).split(Regex("[^\\p{L}\\p{N}]+"))
                .filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
            output(if(map.isEmpty()) "Tidak ada kata." else map.take(100).joinToString("\n") { "${it.key}: ${it.value}" })
        })
    }

    // ---------- CRYPTO / HELPERS ----------

    internal fun digest(alg:String, bytes:ByteArray):String =
        MessageDigest.getInstance(alg).digest(bytes).joinToString("") { "%02x".format(it) }

    internal fun randomString(n:Int, chars:String):String {
        val r=SecureRandom(); return buildString { repeat(n) { append(chars[r.nextInt(chars.length)]) } }
    }

    internal fun randomBytes(n:Int):String {
        val b=ByteArray(n); SecureRandom().nextBytes(b); return b.joinToString("") { "%02x".format(it) }
    }

    internal fun decodeB64Url(s:String):String =
        runCatching { String(Base64.getUrlDecoder().decode(s.padEnd((s.length+3)/4*4,'=')), StandardCharsets.UTF_8) }.getOrElse { "decode error" }

    internal fun totp(secret:String,counter:Long):String {
        val key=Base32.decode(secret)
        val data=ByteArray(8)
        for(i in 7 downTo 0) data[i]=(counter ushr (8*(7-i))).toByte()
        val mac=Mac.getInstance("HmacSHA1"); mac.init(SecretKeySpec(key,"HmacSHA1"))
        val h=mac.doFinal(data); val o=h.last().toInt() and 15
        var v=0
        for(i in 0..3) v=(v shl 8) or (h[o+i].toInt() and 255)
        return "%06d".format((v and 0x7fffffff)%1000000)
    }

    internal fun aesKey(pass:String):ByteArray =
        MessageDigest.getInstance("SHA-256").digest(pass.toByteArray(StandardCharsets.UTF_8))

    internal fun aesKeyV2(pass:String, salt:ByteArray):ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pass.toCharArray(), salt, 120_000, 256)
        return javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
    }

    internal fun aesEncrypt(pass:String, plain:String):String {
        require(pass.isNotEmpty()) { "Password kosong" }
        val salt=ByteArray(16); val iv=ByteArray(12); SecureRandom().nextBytes(salt); SecureRandom().nextBytes(iv)
        val c=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        c.init(javax.crypto.Cipher.ENCRYPT_MODE, SecretKeySpec(aesKeyV2(pass,salt),"AES"), GCMParameterSpec(128,iv))
        val enc=c.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return "MYTOOLS-AES2:" + Base64.getEncoder().encodeToString(salt+iv+enc)
    }

    internal fun aesDecrypt(pass:String, encoded:String):String {
        if (encoded.startsWith("MYTOOLS-AES2:")) {
            val all=Base64.getDecoder().decode(encoded.removePrefix("MYTOOLS-AES2:"))
            require(all.size > 28) { "Data AES2 tidak lengkap" }
            val salt=all.copyOfRange(0,16)
            val iv=all.copyOfRange(16,28)
            val enc=all.copyOfRange(28,all.size)
            val c=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            c.init(javax.crypto.Cipher.DECRYPT_MODE,SecretKeySpec(aesKeyV2(pass,salt),"AES"),GCMParameterSpec(128,iv))
            return String(c.doFinal(enc),StandardCharsets.UTF_8)
        }
        return runCatching { String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8) }.getOrElse { "Dekripsi gagal" }
    }

    internal fun fileHashCompareTool(){
        clearPage("File Hash Compare")
        val a=label("File A: belum dipilih",12f); val b=label("File B: belum dipilih",12f)
        fileHashCompareLabelA=a; fileHashCompareLabelB=b
        content.addView(a); content.addView(button("Pilih File A") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},1201) })
        content.addView(b); content.addView(button("Pilih File B") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="*/*";addCategory(Intent.CATEGORY_OPENABLE)},1202) })
        content.addView(button("Bandingkan SHA-256") {
            val ua=fileHashUriA; val ub=fileHashUriB
            if(ua==null||ub==null){toast("Pilih dua file");return@button}
            thread{val r=runCatching{val ha=contentResolver.openInputStream(ua)?.use{digestStream(it,"SHA-256")}?:error("File A tidak bisa dibuka");val hb=contentResolver.openInputStream(ub)?.use{digestStream(it,"SHA-256")}?:error("File B tidak bisa dibuka");"SHA-256 A: $ha\nSHA-256 B: $hb\n\nHASIL: ${if(ha.equals(hb,true))"IDENTIK" else "BERBEDA"}"}.getOrElse{"Gagal: ${it.message}"};runOnUiThread{output(r)}}
        })
        content.addView(subLabel("Semua hash dihitung lokal di perangkat.",11f))
    }

    internal fun decodeStegoFromUri(uri: Uri) {
        thread {
            val result = runCatching {
                val src = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: error("Gambar tidak dapat dibaca")
                val bmp = src.copy(Bitmap.Config.ARGB_8888, false)
                val bits = StringBuilder()
                var bitCount = 0
                var byteValue = 0
                var payload = ByteArrayOutputStream()
                loop@ for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                    val p = bmp.getPixel(x, y)
                    val channels = intArrayOf(Color.red(p), Color.green(p), Color.blue(p))
                    for (c in channels) {
                        byteValue = (byteValue shl 1) or (c and 1)
                        bitCount++
                        if (bitCount == 8) {
                            payload.write(byteValue); byteValue = 0; bitCount = 0
                            val bytes = payload.toByteArray()
                            val text = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull() ?: ""
                            if (text.contains(":", true) && text.startsWith("MYTOOLS-STG1:")) break@loop
                            if (bytes.size > 4096) break@loop
                        }
                    }
                }
                val text = String(payload.toByteArray(), StandardCharsets.UTF_8)
                if (!text.startsWith("MYTOOLS-STG1:")) "Pesan steganografi tidak ditemukan"
                else text.substringAfter(':').substringAfter(':')
            }.getOrElse { "Decode gagal: ${it.message}" }
            runOnUiThread { output(result) }
        }
    }

    internal fun viewCertificate(uri: Uri) {
        thread {
            val result = runCatching {
                val cert = contentResolver.openInputStream(uri)?.use { input ->
                    java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(input)
                } ?: error("File sertifikat tidak dapat dibaca")
                cert.toString()
            }.getOrElse { "Certificate error: ${it.message}" }
            runOnUiThread { output(result) }
        }
    }

    internal fun steganographyTool(){
        clearPage("Steganography")
        addToolHeader("Steganography","Sisipkan teks ke bit warna gambar PNG. Proses lokal.","STG")
        val msg=edit("Pesan yang disembunyikan",true); content.addView(msg)
        content.addView(button("Pilih Gambar → Sembunyikan") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},STEGO_ENCODE_PICK) })
        content.addView(button("Sembunyikan Pesan") {
            val uri=stegoImageUri ?: run{toast("Pilih gambar dulu");return@button}; val text=msg.text.toString(); if(text.isEmpty()){toast("Pesan kosong");return@button}
            thread { val result=runCatching{encodeStego(uri,text)}.getOrElse{"Gagal: ${it.message}"};runOnUiThread{output(result)} }
        })
        content.addView(button("Pilih Gambar → Baca Pesan") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},STEGO_DECODE_PICK) })
    }

    internal fun encodeStego(uri:Uri,text:String):String {
        val src=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}?:error("Gambar tidak dapat dibaca")
        val bmp=src.copy(Bitmap.Config.ARGB_8888,true); val payload="MYTOOLS-STG1:${text.length}:$text".toByteArray(StandardCharsets.UTF_8); val bits=payload.flatMap{b->(7 downTo 0).map{i->(b.toInt() shr i) and 1}}
        require(bits.size<=bmp.width*bmp.height*3){"Pesan terlalu panjang untuk gambar ini"}; var k=0
        loop@for(y in 0 until bmp.height) for(x in 0 until bmp.width){ val p=bmp.getPixel(x,y); var r=Color.red(p);var g=Color.green(p);var b=Color.blue(p); if(k<bits.size)r=(r and 254) or bits[k++] else break@loop; if(k<bits.size)g=(g and 254) or bits[k++] else break@loop; if(k<bits.size)b=(b and 254) or bits[k++] else break@loop; bmp.setPixel(x,y,Color.argb(Color.alpha(p),r,g,b)) }
        val out=File(filesDir,"stego_${System.currentTimeMillis()}.png")
        FileOutputStream(out).use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)}
        bmp.recycle()
        return "Tersimpan: ${out.absolutePath}"
    }

    internal fun certificateViewerTool(){
        clearPage("Certificate Viewer")
        addToolHeader("Certificate Viewer","Pemeriksaan sertifikat X.509 dari URL.","CERT")
        val url=edit("URL https://..."); content.addView(url)
        content.addView(button("Periksa") {
            val raw=url.text.toString().trim()
            thread{val r=runCatching{val u=java.net.URL(raw);val conn=u.openConnection() as javax.net.ssl.HttpsURLConnection;conn.connect();val cert=conn.serverCertificates.firstOrNull();conn.disconnect();cert?.toString()?:"Sertifikat tidak ditemukan"}.getOrElse{"URL tidak valid: ${it.message}"};runOnUiThread{output(r)}}
        })
    }

    internal fun markdownViewerTool() {
        clearPage("Markdown Viewer")
        addToolHeader("Markdown Viewer", "Tulis Markdown dan lihat preview HTML sederhana secara lokal.", "MD")
        val source = edit("Markdown", true)
        source.setText("# MyTools\n\n**Bold**, *italic*, `code`\n\n- Item satu\n- Item dua")
        content.addView(source)
        content.addView(button("Preview") {
            val html = markdownToHtml(source.text.toString())
            previewHtmlText("<!doctype html><html><meta name='viewport' content='width=device-width,initial-scale=1'><body style='font-family:sans-serif;padding:18px'>$html</body></html>", "HTML")
        })
    }

    internal fun passwordStrengthAnalyzerTool(){
        clearPage("Password Strength Analyzer")
        addToolHeader("Password Strength Analyzer","Analisis kekuatan password secara lokal tanpa mengirim data.","SEC")
        val input=edit("Password")
        input.inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        content.addView(input)
        val result=label("Belum diperiksa",15f,true);content.addView(result)
        content.addView(button("Periksa") {
            val p=input.text.toString()
            val score=(if(p.length>=8)1 else 0)+(if(p.length>=12)1 else 0)+(if(p.any(Char::isUpperCase))1 else 0)+(if(p.any(Char::isLowerCase))1 else 0)+(if(p.any(Char::isDigit))1 else 0)+(if(p.any{!it.isLetterOrDigit()})1 else 0)
            val level=when(score){0,1->"Sangat lemah";2,3->"Lemah";4->"Sedang";5->"Kuat";else->"Sangat kuat"}
            result.text="$level • skor $score/6\nPanjang: ${p.length}\nHuruf besar: ${p.any(Char::isUpperCase)} • kecil: ${p.any(Char::isLowerCase)} • angka: ${p.any(Char::isDigit)} • simbol: ${p.any{!it.isLetterOrDigit()}}"
        })
    }

    internal fun stopwatchTool() {
        clearPage("Stopwatch")
        addToolHeader("Stopwatch", "Stopwatch lokal dengan start, pause, reset dan lap.", "TIME")
        val display=label("00:00.000",34f,true);display.gravity=Gravity.CENTER
        content.addView(display)
        var running=false; var started=0L; var accumulated=0L; var lastLap=0L
        val handler=Handler(Looper.getMainLooper())
        lateinit var tick:Runnable
        fun render(ms:Long){display.text=String.format(Locale.US,"%02d:%02d.%03d",(ms/60000)%60,(ms/1000)%60,ms%1000)}
        tick=Runnable { if(running){render(accumulated+(System.currentTimeMillis()-started));handler.postDelayed(tick,50)} }
        content.addView(button("Start / Pause") {
            if(running){accumulated+=System.currentTimeMillis()-started;running=false}
            else {started=System.currentTimeMillis();running=true;handler.post(tick)}
        })
        content.addView(button("Lap") {
            val now=if(running) accumulated+System.currentTimeMillis()-started else accumulated
            val lap=now-lastLap;lastLap=now
            output("Lap: ${String.format(Locale.US,"%02d:%02d.%03d",(lap/60000)%60,(lap/1000)%60,lap%1000)}")
        })
        content.addView(button("Reset") {running=false;accumulated=0;lastLap=0;render(0)})
    }

    internal fun timerTool() {
        clearPage("Timer")
        addToolHeader("Timer", "Hitung mundur sederhana.", "TIME")
        val seconds = edit("Detik", false).apply { setText("60") }
        content.addView(seconds)
        val display = label("01:00", 34f, true).apply { gravity = Gravity.CENTER }
        content.addView(display)
        val handler = Handler(Looper.getMainLooper())
        var remaining = 60_000L
        var running = false
        lateinit var tick: Runnable
        fun render() { val sec=(remaining/1000L).coerceAtLeast(0L); display.text=String.format(Locale.US,"%02d:%02d",sec/60,sec%60) }
        tick=Runnable { if(running){remaining=(remaining-1000L).coerceAtLeast(0L);render();if(remaining==0L){running=false;toast("Timer selesai")}else handler.postDelayed(tick,1000L)} }
        content.addView(button("Start / Pause") { if(running) running=false else { if(remaining<=0L) remaining=(seconds.text.toString().toLongOrNull()?.coerceAtLeast(1L)?:60L)*1000L; running=true; handler.post(tick) }; render() })
        content.addView(button("Reset") { running=false; remaining=(seconds.text.toString().toLongOrNull()?.coerceAtLeast(0L)?:60L)*1000L; render() })
        render()
    }


    internal fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()

    internal fun showFileChecksum(file:File) {
        thread {
            val result=runCatching {
                val md5=MessageDigest.getInstance("MD5"); val sha1=MessageDigest.getInstance("SHA-1"); val sha256=MessageDigest.getInstance("SHA-256")
                val buf=ByteArray(8192); file.inputStream().buffered().use { input -> var n=input.read(buf); while(n!=-1){ md5.update(buf,0,n); sha1.update(buf,0,n); sha256.update(buf,0,n); n=input.read(buf) } }
                "MD5 ${md5.digest().joinToString("") { "%02x".format(it) }}\nSHA-1 ${sha1.digest().joinToString("") { "%02x".format(it) }}\nSHA-256 ${sha256.digest().joinToString("") { "%02x".format(it) }}"
            }.getOrElse { "Checksum error: ${it.message}" }
            runOnUiThread { output(result) }
        }
    }

    /**
     * Search-specific watcher. A short debounce prevents UI rebuilds from competing
     * with Android's IME composition/cursor updates. This is intentionally separate
     * from SimpleTextWatcher because the latter is used by live-preview tools where
     * immediate updates are required.
     */
    internal class DebouncedSearchWatcher(
        internal val fn: (String) -> Unit
    ) : android.text.TextWatcher {
        internal val handler = Handler(Looper.getMainLooper())
        internal var pending: Runnable? = null

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            val value = s?.toString().orEmpty()
            pending?.let(handler::removeCallbacks)
            val task = Runnable { fn(value) }
            pending = task
            handler.postDelayed(task, 110L)
        }

        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

    internal class SimpleTextWatcher(val fn:(String)->Unit): android.text.TextWatcher {
        override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
        override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){fn(s?.toString()?:"")}
        override fun afterTextChanged(s:android.text.Editable?){}
    }

    internal object JSONObjectLite {
        fun escape(s:String)=s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")
    }

    internal object Base32 {
        internal const val ALPH="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        fun encode(data:ByteArray):String {
            var buffer=0; var bits=0; val out=StringBuilder()
            for(b in data) {
                buffer=(buffer shl 8) or (b.toInt() and 255); bits+=8
                while(bits>=5){ bits-=5; out.append(ALPH[(buffer shr bits) and 31]) }
            }
            if(bits>0) out.append(ALPH[(buffer shl (5-bits)) and 31])
            return out.toString()
        }
        fun decode(s:String):ByteArray {
            var buffer=0; var bits=0; val out=ByteArrayOutputStream()
            for(ch in s.toUpperCase(Locale.getDefault()).replace("=","").filter { !it.isWhitespace() }) {
                val v=ALPH.indexOf(ch); require(v>=0)
                buffer=(buffer shl 5) or v; bits+=5
                if(bits>=8){bits-=8; out.write((buffer shr bits) and 255)}
            }
            return out.toByteArray()
        }
    }
}
