package com.aikhata.app

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.*
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import androidx.biometric.BiometricManager
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView.Guidelines
import com.canhub.cropper.CropImageView.RequestSizeOptions
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import android.content.res.Configuration
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.aikhata.app.data.KhataRepository
import com.aikhata.app.ui.home.HomeDashboardScreen
import com.aikhata.app.ui.home.HomeViewModel
import com.aikhata.app.ui.home.HomeViewModelFactory
import com.aikhata.app.ui.customers.CustomersScreen
import com.aikhata.app.ui.customers.CustomersViewModel
import com.aikhata.app.ui.customers.CustomersViewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavHostController
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.collect
import com.aikhata.app.ui.customer.CustomerDetailScreen
import com.aikhata.app.ui.customer.CustomerDetailViewModel
import com.aikhata.app.ui.customer.CustomerDetailViewModelFactory
import com.aikhata.app.ui.reports.ReportsScreen
import com.aikhata.app.ui.reports.ReportsViewModel
import com.aikhata.app.ui.reports.ReportsViewModelFactory
import com.aikhata.app.ui.settings.SettingsScreen
import com.aikhata.app.ui.theme.AiKhataTheme
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.ConnectException
import java.util.UUID
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

class MainActivity : FragmentActivity() {
    private lateinit var db: KhataDatabase
    private lateinit var root: LinearLayout
    private var unlocked = false
    private var pendingCameraUri: Uri? = null
    private lateinit var imagePicker: ActivityResultLauncher<String>
    private lateinit var cameraCapture: ActivityResultLauncher<Uri>
    private lateinit var cropImage: ActivityResultLauncher<CropImageContractOptions>
    private lateinit var backupCreator: ActivityResultLauncher<String>
    private lateinit var backupPicker: ActivityResultLauncher<Array<String>>
    private lateinit var notificationPermission: ActivityResultLauncher<String>
    private var backAction: (() -> Unit)? = null
    private var currentScreen = "start"
    private var lastHomeBack = 0L
    private var sortCustomersByBalance = false
    private var showOnlyDueCustomers = false
    private var backgroundedAt = 0L
    private var pendingBackupContent: ByteArray? = null
    private var biometricPromptShowing = false
    private var initialComposeRoute = "home"
    private var composeNavController: NavHostController? = null
    private val customerDetailViewModel by lazy {
        ViewModelProvider(this, CustomerDetailViewModelFactory(KhataRepository(db)))
            .get(CustomerDetailViewModel::class.java)
    }
    private val reportsViewModel by lazy {
        ViewModelProvider(this, ReportsViewModelFactory(KhataRepository(db)))
            .get(ReportsViewModel::class.java)
    }

    private val green = Color.rgb(18, 107, 86)
    private val deepGreen = Color.rgb(19, 53, 44)
    private val ink = Color.rgb(32, 49, 42)
    private val muted = Color.rgb(111, 126, 117)
    private val paper = Color.rgb(245, 248, 245)
    private val line = Color.rgb(226, 234, 228)
    private val red = Color.rgb(190, 67, 67)
    private val prefs by lazy { getSharedPreferences("ai_khata_secure", MODE_PRIVATE) }
    private val customersViewModel by lazy {
        ViewModelProvider(this, CustomersViewModelFactory(KhataRepository(db))).get(CustomersViewModel::class.java)
    }
    private val homeViewModel by lazy {
        ViewModelProvider(this, HomeViewModelFactory(KhataRepository(db)))
            .get(HomeViewModel::class.java)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cropImage = registerForActivityResult(CropImageContract()) { result ->
            val croppedUri = result.uriContent
            when {
                croppedUri != null && result.isSuccessful -> runAiExtraction(croppedUri)
                result.error != null -> toast("The photo could not be cropped: " + result.error?.message.orEmpty())
                else -> toast("Photo cropping was cancelled.")
            }
        }
        imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { launchLedgerCrop(it) }
        }
        cameraCapture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) launchLedgerCrop(uri)
        }
        backupCreator = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri != null) writeBackupFile(uri) else pendingBackupContent = null
        }
        backupPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { readBackupFile(it) }
        }
        notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        scheduleBackupReminder()
        pendingCameraUri = savedInstanceState?.getString("pending_camera_uri")?.let { Uri.parse(it) }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val controller = composeNavController
                if (unlocked && controller != null && currentScreen != "home") {
                    if (!controller.popBackStack()) {
                        controller.navigate("home") {
                            popUpTo(controller.graph.startDestinationId) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    return
                }
                if (controller?.popBackStack() == true) return
                val action = backAction
                if (action != null) {
                    backAction = null
                    action.invoke()
                    return
                }
                if (unlocked && currentScreen == "home") {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastHomeBack < 1800L) finish()
                    else {
                        lastHomeBack = now
                        toast("Press back again to exit.")
                    }
                } else finish()
            }
        })
        db = KhataDatabase(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val darkSystemUi = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val systemBarColor = if (darkSystemUi) Color.rgb(8, 15, 24) else Color.rgb(241, 246, 250)
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        window.decorView.systemUiVisibility = if (darkSystemUi) 0 else
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        if (db.isSetUp()) {
            if (lastCustomNonConfigurationInstance == true) {
                // Keep the current session through a screen rotation, but not through process death.
                unlocked = true
                when (savedInstanceState?.getString("current_screen")) {
                    "customers" -> showCustomers()
                    "reports" -> showReports()
                    "settings" -> showSettings()
                    else -> showHome()
                }
            } else {
                showUnlock()
            }
        } else {
            showSetup()
        }
    }

    override fun onRetainCustomNonConfigurationInstance(): Any? = unlocked

    override fun onStart() {
        super.onStart()
        if (unlocked && backgroundedAt > 0L &&
            SystemClock.elapsedRealtime() - backgroundedAt >= 120_000L
        ) {
            unlocked = false
            backgroundedAt = 0L
            showUnlock()
        }
    }

    override fun onStop() {
        if (unlocked) backgroundedAt = SystemClock.elapsedRealtime()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingCameraUri?.let { outState.putString("pending_camera_uri", it.toString()) }
        outState.putString("current_screen", currentScreen)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun money(value: Double) = "Rs. " + String.format(Locale.US, "%,.2f", value)
    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    private fun parseAmount(raw: String): Double? = LedgerMath.parseAmount(raw)

    private fun datePickerButton(initialDate: String, onSelected: (String) -> Unit): TextView {
        var currentDate = initialDate
        val label = text("Date: $initialDate", 15, green, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 13, line)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val parts = currentDate.split("-")
                val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
                val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
                DatePickerDialog(this@MainActivity, { _, y, m, d ->
                    val chosen = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)
                    currentDate = chosen
                    text = "Date: $chosen"
                    onSelected(chosen)
                }, year, month, day).show()
            }
        }
        return label
    }

    private fun monthPickerButton(initialMonth: String, onSelected: (String) -> Unit): TextView {
        var currentMonth = initialMonth
        return text("Month: $initialMonth", 15, green, true).apply {
            gravity = Gravity.CENTER
            background = rounded(Color.WHITE, 13, line)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                val parts = currentMonth.split("-")
                val year = parts.getOrNull(0)?.toIntOrNull() ?: 2026
                val month = (parts.getOrNull(1)?.toIntOrNull() ?: 1) - 1
                DatePickerDialog(this@MainActivity, { _, y, m, _ ->
                    val chosen = String.format(Locale.US, "%04d-%02d", y, m + 1)
                    currentMonth = chosen
                    text = "Month: $chosen"
                    onSelected(chosen)
                }, year, month, 1).show()
            }
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    private fun text(
        value: String,
        size: Int = 16,
        color: Int = ink,
        bold: Boolean = false
    ) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
        includeFontPadding = false
    }

    private fun input(hintText: String, password: Boolean = false) = EditText(this).apply {
        hint = hintText
        textSize = 15f
        setSingleLine(true)
        inputType = if (password) {
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT
        }
        setTextColor(ink)
        setHintTextColor(muted)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(13).toFloat()
            setStroke(dp(1), line)
        }
    }

    private fun actionButton(
        label: String,
        action: () -> Unit,
        primary: Boolean = false,
        danger: Boolean = false,
        height: Int = 50
    ) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setTextColor(if (primary) Color.WHITE else if (danger) red else green)
        isClickable = true
        isFocusable = true
        val fill = if (primary) green else if (danger) Color.rgb(255, 246, 245) else Color.WHITE
        val shape = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(14).toFloat()
            if (!primary) setStroke(dp(1), if (danger) Color.rgb(245, 218, 216) else line)
        }
        background = RippleDrawable(
            ColorStateList.valueOf(Color.argb(34, 18, 107, 86)),
            shape,
            null
        )
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            animate().scaleX(0.97f).scaleY(0.97f).setDuration(65).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(105).withEndAction {
                    action()
                }.start()
            }.start()
        }
        minimumHeight = dp(height)
    }

    private fun button(
        label: String,
        action: () -> Unit,
        primary: Boolean = false,
        danger: Boolean = false
    ) {
        root.addView(
            actionButton(label, action, primary, danger),
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(9) }
        )
    }

    private fun gap(height: Int = 14) {
        root.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun page(title: String, subtitle: String? = null, back: (() -> Unit)? = null) {
        backAction = back
        currentScreen = when (title) {
            "Customers" -> "customers"
            "Reports" -> "reports"
            "Settings" -> "settings"
            else -> if (title == db.shopName()) "home" else title.lowercase(Locale.US)
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(paper)
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, 0, 0, dp(10))
            addView(root)
        }
        setContentView(scroll)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (back != null) {
            val backView = TextView(this).apply {
                text = "‹"
                textSize = 34f
                gravity = Gravity.CENTER
                setTextColor(green)
                background = rounded(Color.WHITE, 13, line)
                setOnClickListener { back.invoke() }
            }
            header.addView(backView, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(11)
            })
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_khata_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
            rightMargin = dp(10)
        })
        val titleBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBlock.addView(text(title, 22, deepGreen, true))
        if (!subtitle.isNullOrBlank()) titleBlock.addView(text(subtitle, 13, muted))
        header.addView(titleBlock, LinearLayout.LayoutParams(0, -2, 1f))
        val more = TextView(this).apply {
            text = "⋮"; textSize = 27f; gravity = Gravity.CENTER
            setTextColor(green); background = rounded(Color.WHITE, 13, line)
            isClickable = true; isFocusable = true
            setOnClickListener { view ->
                val menu = PopupMenu(this@MainActivity, view)
                menu.menu.add(0, 1, 0, "Home")
                menu.menu.add(0, 2, 1, "Customers")
                menu.menu.add(0, 3, 2, "Monthly reports")
                menu.menu.add(0, 4, 3, "Shop settings")
                menu.menu.add(0, 5, 4, "Export backup")
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> showHome(); 2 -> showCustomers(); 3 -> showReports()
                        4 -> showSettings(); 5 -> exportBackup()
                    }; true
                }
                menu.show()
            }
        }
        header.addView(more, LinearLayout.LayoutParams(dp(42), dp(42)).apply { leftMargin = dp(8) })
        root.addView(header)
        gap(17)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun card(title: String, value: String, detail: String = "") {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(13), dp(16), dp(13))
            background = rounded(Color.WHITE, 17, line)
        }
        box.addView(text(title, 13, muted, true))
        box.addView(text(value, 23, deepGreen, true))
        if (detail.isNotBlank()) box.addView(text(detail, 12, muted))
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun section(title: String, detail: String? = null) {
        root.addView(text(title, 17, deepGreen, true))
        if (!detail.isNullOrBlank()) root.addView(text(detail, 13, muted))
    }

    private fun scheduleBackupReminder() {
        val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(30, TimeUnit.DAYS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ai_khata_monthly_backup_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun showSetup() {
        composeNavController = null
        unlocked = false
        page("Welcome to AI Khata", "A simple, private khata for your shop.")
        val shop = input("Shop name")
        val pin = input("Create a 6-digit PIN", true)
        val confirm = input("Confirm your PIN", true)
        root.addView(shop)
        gap(9)
        root.addView(pin)
        gap(9)
        root.addView(confirm)
        button("Create my shop ledger", {
            val enteredPin = pin.text.toString()
            if (shop.text.isNullOrBlank() || enteredPin.length < 6 ||
                enteredPin != confirm.text.toString()
            ) {
                toast("Enter a shop name and matching PIN with at least 6 digits.")
            } else {
                db.createOwner(shop.text.toString().trim(), enteredPin)
                unlocked = true
                showHome()
            }
        }, true)
    }

    private fun showUnlock() {
        composeNavController = null
        unlocked = false
        page(db.shopName(), "Enter your PIN to continue.")
        val pin = input("Shop PIN", true)
        root.addView(pin)
        button("Unlock", {
            val now = System.currentTimeMillis()
            val lockedUntil = prefs.getLong("pin_locked_until", 0L)
            if (now < lockedUntil) {
                val seconds = ((lockedUntil - now + 999) / 1000).coerceAtLeast(1)
                toast("Too many attempts. Please wait $seconds seconds.")
                return@button
            }
            if (db.verifyPin(pin.text.toString())) {
                prefs.edit().remove("pin_failures").remove("pin_locked_until").apply()
                unlocked = true
                showHome()
            } else {
                val failures = prefs.getInt("pin_failures", 0) + 1
                if (failures >= 5) {
                    val stage = (prefs.getInt("pin_lock_stage", 0) + 1).coerceAtMost(6)
                    val waitSeconds = (30L * (1L shl (stage - 1))).coerceAtMost(900L)
                    prefs.edit().putInt("pin_lock_stage", stage).putInt("pin_failures", 0)
                        .putLong("pin_locked_until", now + waitSeconds * 1000L).apply()
                    toast("Too many wrong PINs. Try again in $waitSeconds seconds.")
                } else {
                    prefs.edit().putInt("pin_failures", failures).apply()
                    toast("That PIN is not correct. $failures of 5 attempts used.")
                }
                pin.text.clear()
            }
        }, true)
        if (prefs.getBoolean("biometric_enabled", false)) {
            root.post { startBiometricUnlock() }
        }
    }

    private fun canUseBiometric(): Boolean {
        val manager = BiometricManager.from(this)
        val result = if (android.os.Build.VERSION.SDK_INT >= 29) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        } else {
            manager.canAuthenticate()
        }
        return result == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun startBiometricUnlock() {
        if (biometricPromptShowing || !prefs.getBoolean("biometric_enabled", false)) return
        if (!canUseBiometric()) {
            toast("Fingerprint unlock is unavailable. Use your shop PIN.")
            return
        }
        biometricPromptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    biometricPromptShowing = false
                    prefs.edit().remove("pin_failures").remove("pin_locked_until").apply()
                    unlocked = true
                    showHome()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    biometricPromptShowing = false
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        toast("Fingerprint unlock failed. You can enter your shop PIN.")
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    toast("Fingerprint not recognized. Try again or use your PIN.")
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock AI Khata")
            .setSubtitle("Use your registered fingerprint")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(promptInfo)
    }

    private fun guard(): Boolean {
        if (!unlocked) {
            showUnlock()
            return false
        }
        return true
    }

    private fun showHome() {
        if (!guard()) return
        backAction = null
        homeViewModel.refresh()
        customersViewModel.refresh()
        val waitingPhotos = queuedScanFiles().size
        val startRoute = initialComposeRoute
        initialComposeRoute = "home"
        setContent {
            val navController = rememberNavController()
            LaunchedEffect(navController) {
                composeNavController = navController
                navController.currentBackStackEntryFlow.collect { entry ->
                    val route = entry.destination.route.orEmpty()
                    currentScreen = when {
                        route == "home" -> "home"
                        route == "customers" -> "customers"
                        route.startsWith("customer/") -> "customer"
                        route.startsWith("reports/") -> "reports"
                        route == "settings" -> "settings"
                        else -> "home"
                    }
                }
            }
            AiKhataTheme {
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    enterTransition = {
                        fadeIn(animationSpec = tween(260)) +
                            slideInHorizontally(
                                initialOffsetX = { it / 16 },
                                animationSpec = tween(340)
                            )
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(180)) +
                            slideOutHorizontally(
                                targetOffsetX = { -it / 22 },
                                animationSpec = tween(260)
                            )
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(240)) +
                            slideInHorizontally(
                                initialOffsetX = { -it / 20 },
                                animationSpec = tween(300)
                            )
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(170)) +
                            slideOutHorizontally(
                                targetOffsetX = { it / 18 },
                                animationSpec = tween(250)
                            )
                    }
                ) {
                    composable("home") {
                        val state by homeViewModel.state.collectAsStateWithLifecycle()
                        HomeDashboardScreen(
                            state = state,
                            onCustomers = { navController.navigate("customers") },
                            onAddCustomer = { addCustomerDialog() },
                            onReports = { navController.navigate("reports/" + today().take(7)) },
                            onScan = { chooseImage() },
                            onSettings = { navController.navigate("settings") },
                            onLock = {
                                composeNavController = null
                                unlocked = false
                                showUnlock()
                            },
                            onBackup = { exportBackup() },
                            onRetryScans = { processNextQueuedPhoto() },
                            waitingPhotoCount = waitingPhotos,
                            onCustomerClick = { navController.navigate("customer/" + it) },
                            onRefresh = { homeViewModel.refresh() }
                        )
                    }
                    composable("customers") {
                        CustomersScreen(
                            viewModel = customersViewModel,
                            onBack = { if (!navController.popBackStack()) showHome() },
                            onAddCustomer = { addCustomerDialog() },
                            onCustomerClick = { navController.navigate("customer/" + it) }
                        )
                    }
                    composable("customer/{customerId}") { entry ->
                        val customerId = entry.arguments?.getString("customerId")?.toLongOrNull() ?: -1L
                        CustomerDetailScreen(
                            customerId = customerId,
                            viewModel = customerDetailViewModel,
                            onBack = { if (!navController.popBackStack()) showCustomers() },
                            onAddCredit = { addTransactionDialog(customerId, "credit") },
                            onRecordPayment = { addTransactionDialog(customerId, "payment") },
                            onEditCustomer = {
                                customerDetailViewModel.state.value.detail?.customer?.let { editCustomerDialog(it) }
                            },
                            onMergeCustomer = {
                                customerDetailViewModel.state.value.detail?.customer?.let { showMergeCustomerDialog(it) }
                            },
                            onShareStatement = { shareStatement(customerId) },
                            onDeleteCustomer = { confirmDeleteCustomer(customerId) },
                            onEditTransaction = { editTransactionDialog(customerId, it) },
                            onDeleteTransaction = { confirmDeleteTransaction(customerId, it) }
                        )
                    }
                    composable("reports/{month}") { entry ->
                        val month = entry.arguments?.getString("month") ?: today().take(7)
                        ReportsScreen(
                            initialMonth = month,
                            viewModel = reportsViewModel,
                            onBack = { if (!navController.popBackStack()) showHome() },
                            onShareReport = { shareMonthlyReport(it) }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            shopName = db.shopName(),
                            biometricEnabled = prefs.getBoolean("biometric_enabled", false),
                            apiKeyCount = apiKeyCount(),
                            appVersion = BuildConfig.VERSION_NAME,
                            onBack = { if (!navController.popBackStack()) showHome() },
                            onSaveShopName = { value ->
                                if (value.isNotBlank()) {
                                    db.updateShopName(value.take(80))
                                    toast("Shop name updated.")
                                    showSettings()
                                } else toast("Enter a shop name.")
                            },
                            onChangePin = { pin, confirm ->
                                if (pin.length >= 6 && pin.all { it.isDigit() } && pin == confirm) {
                                    db.changePin(pin)
                                    toast("Your PIN has been changed.")
                                    showSettings()
                                } else toast("Your PINs must match and contain at least 6 digits.")
                            },
                            onToggleBiometric = { enabled ->
                                if (!enabled) {
                                    prefs.edit().putBoolean("biometric_enabled", false).apply()
                                    toast("Fingerprint unlock is turned off.")
                                    showSettings()
                                } else if (canUseBiometric()) {
                                    prefs.edit().putBoolean("biometric_enabled", true).apply()
                                    toast("Fingerprint unlock is ready.")
                                    showSettings()
                                } else {
                                    toast("Set up a fingerprint in your phone settings first. Your shop PIN will continue to work.")
                                }
                            },
                            onSaveApiKeys = { value ->
                                val count = saveApiKeys(value)
                                if (count > 0) {
                                    toast(count.toString() + " AI key(s) saved securely. Automatic fallback is on.")
                                    showSettings()
                                } else toast("No valid key was found. Paste one key per line.")
                            },
                            onDeleteApiKeys = {
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Remove AI keys?")
                                    .setMessage("Photo reading will be turned off until you add keys again.")
                                    .setNegativeButton("Cancel", null)
                                    .setPositiveButton("Remove") { _, _ ->
                                        deleteApiKey()
                                        toast("AI keys removed.")
                                        showSettings()
                                    }
                                    .show()
                            },
                            onExportBackup = { exportBackup() },
                            onImportBackup = { importBackup() }
                        )
                    }
                }
            }
        }
    }

    private fun customerTile(customer: CustomerRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.WHITE, 15, line)
            isClickable = true
            isFocusable = true
            setOnClickListener { showCustomer(customer.id) }
        }
        val copy = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(customer.name, 16, ink, true))
        copy.addView(text(if (customer.phone.isBlank()) "Customer account" else customer.phone, 12, muted))
        box.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        val amountText = if (customer.balance >= 0) {
            "Due\n" + money(customer.balance)
        } else {
            "Advance\n" + money(kotlin.math.abs(customer.balance))
        }
        box.addView(text(amountText, 13, if (customer.balance > 0) red else green, true))
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun showCustomers(query: String = "") {
        if (!guard()) return
        if (query.isNotBlank()) customersViewModel.setQuery(query)
        initialComposeRoute = "customers"
        showHome()
    }

    private fun addCustomerDialog() {
        if (!guard()) return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = input("Customer name")
        val phone = input("Phone number (optional)").apply {
            inputType = InputType.TYPE_CLASS_PHONE
        }
        box.addView(name)
        box.addView(phone, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("New customer")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                if (name.text.isNullOrBlank()) {
                    toast("Enter the customer's name.")
                } else {
                    val id = db.addCustomer(name.text.toString(), phone.text.toString())
                    dialog.dismiss()
                    showCustomer(id)
                }
            }
        }
        dialog.show()
    }

    private fun showCustomer(id: Long) {
        if (!guard()) return
        initialComposeRoute = "customer/" + id
        showHome()
    }

    private fun showMergeCustomerDialog(target: CustomerRow) {
        val possibleDuplicates = db.customers().filter { it.id != target.id }
        if (possibleDuplicates.isEmpty()) {
            toast("There are no other customer accounts to merge.")
            return
        }
        val labels = possibleDuplicates.map { customer ->
            customer.name + if (customer.phone.isBlank()) "" else " · " + customer.phone
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choose the duplicate account")
            .setItems(labels) { _, index ->
                val source = possibleDuplicates[index]
                AlertDialog.Builder(this)
                    .setTitle("Merge " + source.name + " into " + target.name + "?")
                    .setMessage(
                        "All sales and payments from " + source.name +
                            " will move into " + target.name +
                            ". The duplicate account will then be removed. No transaction amounts will be changed."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Merge accounts") { _, _ ->
                        try {
                            db.mergeCustomers(source.id, target.id)
                            toast("Customer accounts merged. All entries were kept.")
                            showCustomer(target.id)
                        } catch (error: Exception) {
                            toast("The accounts could not be merged: " + error.message)
                        }
                    }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun transactionTile(customerId: Long, entry: TransactionRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(Color.WHITE, 15, line)
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val kind = if (entry.type == "credit") "CREDIT / SALE" else "PAYMENT RECEIVED"
        top.addView(text(kind, 12, if (entry.type == "credit") red else green, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(text(money(entry.amount), 15, ink, true))
        box.addView(top)
        box.addView(text(entry.date, 12, muted))
        if (entry.description.isNotBlank()) box.addView(text(entry.description, 14, ink))
        var swipeStartX = 0f
        box.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    swipeStartX = event.x
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> true
                android.view.MotionEvent.ACTION_UP -> {
                    val swipeDistance = swipeStartX - event.x
                    if (swipeDistance > dp(72)) {
                        deleteTransactionWithUndo(customerId, entry)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val edit = actionButton("Edit entry", { editTransactionDialog(customerId, entry) }, height = 42)
        val delete = actionButton("Delete", { confirmDeleteTransaction(customerId, entry) }, danger = true, height = 42)
        actions.addView(edit, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(5) })
        actions.addView(delete, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(5) })
        box.addView(actions, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
        root.addView(box, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    }

    private fun editCustomerDialog(customer: CustomerRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val name = input("Customer name").apply { setText(customer.name) }
        val phone = input("Phone number").apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(customer.phone)
        }
        box.addView(name)
        box.addView(phone, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit customer")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                if (name.text.isNullOrBlank()) toast("Enter the customer's name.")
                else {
                    db.updateCustomer(customer.id, name.text.toString(), phone.text.toString())
                    dialog.dismiss()
                    showCustomer(customer.id)
                }
            }
        }
        dialog.show()
    }

    private fun addTransactionDialog(customerId: Long, type: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val amount = input("Amount in rupees").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val description = input("Description / item")
        var selectedDate = today()
        val date = datePickerButton(selectedDate) { selectedDate = it }
        box.addView(amount)
        box.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (type == "credit") "Add credit / sale" else "Record payment")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val value = parseAmount(amount.text.toString())
                val day = selectedDate
                if (value == null || value <= 0 || !value.isFinite() || !validDate(day)) {
                    toast("Enter a valid amount and date.")
                } else {
                    try {
                        db.addTransaction(customerId, type, value, description.text.toString(), day)
                        dialog.dismiss()
                        showCustomer(customerId)
                    } catch (error: Exception) {
                        toast("The entry could not be saved.")
                    }
                }
            }
        }
        dialog.show()
    }

    private fun editTransactionDialog(customerId: Long, entry: TransactionRow) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val amount = input("Amount in rupees").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(entry.amount.toString())
        }
        val description = input("Description / item").apply { setText(entry.description) }
        var selectedDate = entry.date
        val date = datePickerButton(selectedDate) { selectedDate = it }
        box.addView(amount)
        box.addView(description, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        box.addView(date, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Edit transaction")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save changes", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val value = parseAmount(amount.text.toString())
                val day = selectedDate
                if (value == null || value <= 0 || !value.isFinite() || !validDate(day)) {
                    toast("Enter a valid amount and date.")
                } else {
                    db.updateTransaction(entry.id, value, description.text.toString(), day)
                    dialog.dismiss()
                    showCustomer(customerId)
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteTransaction(customerId: Long, entry: TransactionRow) {
        AlertDialog.Builder(this)
            .setTitle("Delete this entry?")
            .setMessage(
                "This removes the " + money(entry.amount) +
                    (if (entry.type == "credit") " sale" else " payment") +
                    " entry. You can undo it for a short time."
            )
            .setNegativeButton("Keep entry", null)
            .setPositiveButton("Delete") { _, _ -> deleteTransactionWithUndo(customerId, entry) }
            .show()
    }

    private fun deleteTransactionWithUndo(customerId: Long, entry: TransactionRow) {
        if (db.deleteTransaction(entry.id) == 0) return
        showCustomer(customerId)
        Snackbar.make(findViewById(android.R.id.content), "Entry deleted", Snackbar.LENGTH_LONG)
            .setAction("UNDO") {
                try {
                    db.addTransaction(customerId, entry.type, entry.amount, entry.description, entry.date)
                    showCustomer(customerId)
                    toast("Entry restored.")
                } catch (error: Exception) {
                    toast("The entry could not be restored.")
                }
            }
            .show()
    }

    private fun confirmDeleteCustomer(customerId: Long) {
        val customer = db.customer(customerId) ?: return showCustomers()
        val savedEntries = db.transactions(customerId)
        AlertDialog.Builder(this)
            .setTitle("Delete " + customer.name + "'s khata?")
            .setMessage(
                "This removes this customer and all " + savedEntries.size +
                    " entries. You can undo it for a short time."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete khata") { _, _ ->
                db.deleteCustomer(customerId)
                showCustomers()
                Snackbar.make(findViewById(android.R.id.content), "Customer khata deleted", Snackbar.LENGTH_LONG)
                    .setAction("UNDO") {
                        try {
                            val restoredId = db.addCustomer(customer.name, customer.phone)
                            savedEntries.forEach { entry ->
                                db.addTransaction(restoredId, entry.type, entry.amount, entry.description, entry.date)
                            }
                            showCustomer(restoredId)
                            toast("Customer khata restored.")
                        } catch (error: Exception) {
                            toast("The customer account could not be restored.")
                        }
                    }
                    .show()
            }
            .show()
    }

    private fun shareStatement(customerId: Long) {
        val customer = db.customer(customerId) ?: return
        val entries = db.transactions(customerId)
        val credits = entries.filter { it.type == "credit" }.sumOf { it.amount }
        val payments = entries.filter { it.type == "payment" }.sumOf { it.amount }
        val statement = buildString {
            append(db.shopName()).append("\n")
            append("Account statement: ").append(customer.name).append("\n")
            if (customer.phone.isNotBlank()) append("Phone: ").append(customer.phone).append("\n")
            append("------------------------------\n")
            entries.forEach {
                append(it.date).append(" | ")
                    .append(if (it.type == "credit") "Credit" else "Payment")
                    .append(" | ").append(money(it.amount))
                if (it.description.isNotBlank()) append(" | ").append(it.description)
                append("\n")
            }
            append("------------------------------\n")
            append("Total credit: ").append(money(credits)).append("\n")
            append("Total paid: ").append(money(payments)).append("\n")
            append("Balance due: ").append(money(credits - payments))
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Khata statement - " + customer.name)
            putExtra(Intent.EXTRA_TEXT, statement)
        }
        startActivity(Intent.createChooser(send, "Share account statement"))
    }

    private fun shareMonthlyReport(month: String) {
        val totals = db.totals()
        val monthTotals = db.monthTotals(month)
        val dueCount = db.customers().count { it.balance > 0.009 }
        val reportText = buildString {
            append(db.shopName()).append("\n")
            append("AI Khata report · ").append(month).append("\n")
            append("------------------------------\n")
            append("All-time sales: ").append(money(totals.first)).append("\n")
            append("All-time payments: ").append(money(totals.second)).append("\n")
            append("Outstanding balance: ").append(money(totals.first - totals.second)).append("\n")
            append("All-time entries: ").append(totals.third).append("\n")
            append("------------------------------\n")
            append("Monthly sales: ").append(money(monthTotals.first)).append("\n")
            append("Monthly payments: ").append(money(monthTotals.second)).append("\n")
            append("Monthly entries: ").append(db.monthTransactionCount(month)).append("\n")
            append("Customers with amount due: ").append(dueCount)
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "AI Khata report - " + month)
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        startActivity(Intent.createChooser(send, "Share shop report"))
    }

    private fun showReports(month: String = today().take(7)) {
        if (!guard()) return
        val validMonth = if (Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(month)) month else today().take(7)
        initialComposeRoute = "reports/" + validMonth
        showHome()
    }

    private fun showSettings() {
        if (!guard()) return
        initialComposeRoute = "settings"
        showHome()
    }

    private fun validDate(value: String): Boolean = LedgerMath.isValidDate(value)

    private fun launchLedgerCrop(uri: Uri) {
        try {
            cropImage.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions(
                        guidelines = Guidelines.ON,
                        fixAspectRatio = false,
                        activityTitle = "Crop ledger page",
                        toolbarColor = green,
                        toolbarTitleColor = Color.WHITE,
                        toolbarBackButtonColor = Color.WHITE,
                        outputCompressFormat = Bitmap.CompressFormat.JPEG,
                        outputCompressQuality = 88,
                        outputRequestWidth = 1600,
                        outputRequestHeight = 1600,
                        outputRequestSizeOptions = RequestSizeOptions.RESIZE_INSIDE
                    )
                )
            )
        } catch (error: Exception) {
            toast("Could not open the crop editor. Please choose the photo again.")
        }
    }

    private fun chooseImage() {
        if (!guard()) return
        AlertDialog.Builder(this)
            .setTitle("Choose a khata photo")
            .setItems(arrayOf("Take a new photo", "Choose from gallery")) { _, which ->
                if (which == 0) {
                    try {
                        val photo = File.createTempFile("khata-scan-", ".jpg", cacheDir)
                        pendingCameraUri = FileProvider.getUriForFile(
                            this, packageName + ".fileprovider", photo
                        )
                        cameraCapture.launch(pendingCameraUri!!)
                    } catch (error: Exception) {
                        pendingCameraUri = null
                        toast("Could not open the camera.")
                    }
                } else {
                    imagePicker.launch("image/*")
                }
            }
            .show()
    }

    private fun exportBackup() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val password = input("Create backup password").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        val confirm = input("Confirm backup password").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(password)
        box.addView(confirm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })
        val dialog = AlertDialog.Builder(this)
            .setTitle("Protect your backup")
            .setMessage("Your customer names, phone numbers and account history will be encrypted. Keep this password safe; it cannot be recovered.")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create backup", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(-1).setOnClickListener {
                val first = password.text.toString()
                if (first.length < 8 || first != confirm.text.toString()) {
                    toast("Use matching passwords with at least 8 characters.")
                } else {
                    try {
                        pendingBackupContent = BackupCrypto.encrypt(db.exportBackup(), first)
                            .toByteArray(Charsets.UTF_8)
                        dialog.dismiss()
                        backupCreator.launch("ai-khata-backup.aikhata")
                    } catch (error: Exception) {
                        toast("The backup could not be prepared: " + error.message)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun importBackup() {
        backupPicker.launch(arrayOf("application/json", "application/octet-stream"))
    }

    private fun writeBackupFile(uri: Uri) {
        try {
            val contents = pendingBackupContent ?: throw IllegalStateException("The backup was not prepared.")
            contentResolver.openOutputStream(uri)?.use { it.write(contents) }
                ?: throw IllegalStateException("The backup file could not be created.")
            pendingBackupContent = null
            prefs.edit().putLong("last_backup_at", System.currentTimeMillis()).apply()
            toast("Your password-protected backup is ready.")
        } catch (error: Exception) {
            pendingBackupContent = null
            toast("Backup export failed: " + (error.message ?: "Please try again."))
        }
    }

    private fun readBackupFile(uri: Uri) {
        try {
            val fileText = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalArgumentException("The backup file could not be opened.")
            val parsed = JSONObject(fileText)
            if (parsed.optString("format") == "ai-khata-encrypted-backup") {
                val password = input("Backup password").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
                AlertDialog.Builder(this)
                    .setTitle("Unlock backup")
                    .setMessage("Enter the password used when this backup was created.")
                    .setView(password)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue") { _, _ ->
                        try {
                            val json = BackupCrypto.decrypt(fileText, password.text.toString())
                            confirmRestoreBackup(json)
                        } catch (error: Exception) {
                            toast(error.message ?: "Could not unlock this backup.")
                        }
                    }
                    .show()
            } else if (parsed.optString("format") == "ai-khata-backup") {
                AlertDialog.Builder(this)
                    .setTitle("Older unencrypted backup")
                    .setMessage("This older backup is not password-protected. Restore it only if you trust this file.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Continue") { _, _ -> confirmRestoreBackup(fileText) }
                    .show()
            } else {
                throw IllegalArgumentException("This is not an AI Khata backup.")
            }
        } catch (error: Exception) {
            toast("Could not read this backup file: " + (error.message ?: "Please check the file."))
        }
    }

    private fun confirmRestoreBackup(json: String) {
        AlertDialog.Builder(this)
            .setTitle("Restore this backup?")
            .setMessage("Restoring will replace the customer accounts and entries currently in this app. Export a backup of your current records first if you need them.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                try {
                    db.restoreBackup(json)
                    toast("Your backup has been restored.")
                    showHome()
                } catch (error: Exception) {
                    toast("Backup could not be restored: " + error.message)
                }
            }
            .show()
    }

    private fun hasNetworkConnection(): Boolean {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun installIdentifier(): String {
        val saved = prefs.getString("installation_id", null)
        if (!saved.isNullOrBlank()) return saved
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("installation_id", created).apply()
        return created
    }

    private fun queuedScanDirectory() = File(filesDir, "queued_scans").apply { mkdirs() }

    private fun queuedScanFiles(): List<File> =
        queuedScanDirectory().listFiles()?.filter { it.isFile && it.extension == "jpg" }
            ?.sortedBy { it.name } ?: emptyList()

    private fun saveQueuedPhoto(bytes: ByteArray): File {
        val target = File(queuedScanDirectory(), System.currentTimeMillis().toString() + "-" + UUID.randomUUID() + ".jpg")
        target.writeBytes(bytes)
        return target
    }

    private fun processNextQueuedPhoto() {
        val file = queuedScanFiles().firstOrNull()
        if (file == null) {
            toast("There are no saved photos waiting to be read.")
            return
        }
        if (!hasNetworkConnection()) {
            toast("Internet is not available yet. Your photo is safe; try again when connected.")
            return
        }
        runAiExtractionBytes(file.readBytes(), file)
    }

    private fun runAiExtraction(uri: Uri) {
        val progress = ProgressDialog(this)
        progress.show()
        Thread {
            var prepared: ByteArray? = null
            try {
                val photoBytes = LedgerPhotoProcessor.prepare(contentResolver, uri)
                prepared = photoBytes
                if (!hasNetworkConnection()) {
                    saveQueuedPhoto(photoBytes)
                    runOnUiThread {
                        progress.dismiss()
                        toast("No internet connection. Your photo was saved and can be read later.")
                        showHome()
                    }
                    return@Thread
                }
                val entries = AiLedgerClient(BuildConfig.AI_BACKEND_URL)
                    .extract(photoBytes, installIdentifier(), apiKeys())
                runOnUiThread {
                    progress.dismiss()
                    reviewAiEntries(entries)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    showAiFailure(error, prepared, null)
                }
            }
        }.start()
    }

    private fun runAiExtractionBytes(bytes: ByteArray, queuedFile: File?) {
        val progress = ProgressDialog(this)
        progress.show()
        Thread {
            try {
                require(hasNetworkConnection()) { "No internet connection. Please reconnect and retry." }
                val entries = AiLedgerClient(BuildConfig.AI_BACKEND_URL)
                    .extract(bytes, installIdentifier(), apiKeys())
                runOnUiThread {
                    progress.dismiss()
                    reviewAiEntries(entries, queuedFile)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    progress.dismiss()
                    showAiFailure(error, bytes, queuedFile)
                }
            }
        }.start()
    }

    private fun showAiFailure(error: Exception, photoBytes: ByteArray?, queuedFile: File?) {
        val detail = error.message.orEmpty()
        val lower = detail.lowercase(Locale.ROOT)
        val networkFailure = error is SocketTimeoutException || error is UnknownHostException ||
            error is ConnectException || !hasNetworkConnection()
        val message = when {
            networkFailure -> "AI Khata could not reach the internet service. Check your connection."
            lower.contains("timeout") || lower.contains("took too long") ->
                "The AI service took too long to respond. Please try again."
            lower.contains("quota") || lower.contains("usage limit") || lower.contains("429") ->
                "The AI service has reached its usage limit. Please try again later."
            lower.contains("invalid") && lower.contains("key") ->
                "The saved AI key is invalid. Check it in Advanced AI settings."
            lower.contains("model") && lower.contains("unavailable") ->
                "The selected AI models are unavailable. The service needs a model update."
            lower.contains("no entries") || lower.contains("empty response") ->
                "The AI could not find clear entries. Try a brighter, closer photo."
            else -> detail.ifBlank { "The photo could not be read. Try a clearer picture." }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Photo reading failed")
            .setMessage(
                message + if (queuedFile != null) {
                    "\n\nYour saved photo is safe. You can retry it when the service is ready."
                } else ""
            )
            .setNegativeButton("Close", null)

        if (photoBytes != null) {
            dialog.setPositiveButton("Retry now") { _, _ ->
                runAiExtractionBytes(photoBytes, queuedFile)
            }
            if (queuedFile == null) {
                dialog.setNeutralButton("Save for later") { _, _ ->
                    saveQueuedPhoto(photoBytes)
                    toast("Photo saved. You can retry it from the home screen.")
                    showHome()
                }
            }
        }
        dialog.show()
    }

    private fun reviewAiEntries(entries: JSONArray, queuedFile: File? = null) {
        val rows = ArrayList<JSONObject>()
        val lines = ArrayList<String>()
        val suggestions = LinkedHashMap<String, CustomerRow>()
        for (index in 0 until entries.length()) {
            val item = entries.optJSONObject(index) ?: continue
            val name = item.optString("customer_name").trim()
            val amount = item.optDouble("amount", Double.NaN)
            val date = item.optString("date", today())
            if (name.isBlank() || !amount.isFinite() || amount <= 0 || !validDate(date)) continue
            val type = if (item.optString("type") == "payment") "payment" else "credit"
            rows.add(
                JSONObject()
                    .put("name", name)
                    .put("amount", amount)
                    .put("type", type)
                    .put("date", date)
                    .put("description", item.optString("description", ""))
            )
            lines.add(
                name + ": " + (if (type == "credit") "Credit " else "Payment ") +
                    money(amount) + " · " + date
            )
            val possibleMatch = findSimilarCustomer(name)
            if (possibleMatch != null && !possibleMatch.name.equals(name, true)) {
                suggestions[name] = possibleMatch
                lines.add("Possible existing customer: " + possibleMatch.name)
            }
        }
        if (rows.isEmpty()) {
            toast("No clear ledger entries were found.")
            return
        }

        val message = lines.joinToString("\n\n") +
            "\n\nPlease check every name, date and amount. Nothing is saved until you confirm."
        AlertDialog.Builder(this)
            .setTitle("Check " + rows.size + " suggested entries")
            .setMessage(message)
            .setNegativeButton("Discard", null)
            .setPositiveButton("Continue") { _, _ ->
                if (suggestions.isEmpty()) {
                    saveReviewedEntries(rows, queuedFile, suggestions, true)
                } else {
                    val choices = suggestions.entries.joinToString("\n") {
                        it.key + "  →  " + it.value.name
                    }
                    AlertDialog.Builder(this)
                        .setTitle("Possible duplicate customers")
                        .setMessage(
                            "AI Khata found accounts with similar names:\n\n" + choices +
                                "\n\nUse the existing accounts, or create separate new accounts?"
                        )
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Create separate") { _, _ ->
                            saveReviewedEntries(rows, queuedFile, suggestions, false)
                        }
                        .setPositiveButton("Use existing") { _, _ ->
                            saveReviewedEntries(rows, queuedFile, suggestions, true)
                        }
                        .show()
                }
            }
            .show()
    }

    private fun saveReviewedEntries(
        rows: List<JSONObject>,
        queuedFile: File?,
        suggestions: Map<String, CustomerRow>,
        useSuggestions: Boolean
    ) {
        try {
            db.inTransaction {
                rows.forEach { item ->
                    val name = item.getString("name")
                    val exact = db.customers().firstOrNull { it.name.equals(name, true) }
                    val suggested = if (useSuggestions) suggestions[name] else null
                    val customerId = exact?.id ?: suggested?.id ?: db.addCustomer(name, "")
                    db.addTransaction(
                        customerId,
                        item.getString("type"),
                        item.getDouble("amount"),
                        item.getString("description"),
                        item.getString("date")
                    )
                }
            }
            queuedFile?.delete()
            toast("Entries added to your khata.")
            showHome()
        } catch (error: Exception) {
            toast("Some entries could not be saved: " + error.message)
        }
    }

    private fun findSimilarCustomer(name: String): CustomerRow? {
        val normalized = normalizeCustomerName(name)
        if (normalized.length < 3) return null
        return db.customers()
            .asSequence()
            .map { customer -> customer to customerNameSimilarity(normalized, normalizeCustomerName(customer.name)) }
            .filter { it.second >= 0.72 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun normalizeCustomerName(value: String): String =
        value.lowercase(Locale.ROOT)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun customerNameSimilarity(first: String, second: String): Double {
        if (first == second) return 1.0
        if (first.isBlank() || second.isBlank()) return 0.0
        if (first.startsWith("$second ") || second.startsWith("$first ")) {
            return 0.90
        }
        val longer = maxOf(first.length, second.length)
        val previous = IntArray(second.length + 1) { it }
        val current = IntArray(second.length + 1)
        for (i in first.indices) {
            current[0] = i + 1
            for (j in second.indices) {
                val cost = if (first[i] == second[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            for (j in previous.indices) previous[j] = current[j]
        }
        return 1.0 - previous[second.length].toDouble() / longer
    }

    private fun hasApiKey() = prefs.contains("gemini_key")
    private fun apiKeyCount() = apiKeys().size

    private fun saveApiKeys(value: String): Int {
        val keys = value.split(Regex("[\\r\\n,;]+")).map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("Saved securely") }.distinct()
        if (keys.isEmpty()) return 0
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(JSONArray(keys).toString().toByteArray(Charsets.UTF_8))
        prefs.edit().putString("gemini_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("gemini_key", Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
        return keys.size
    }

    private fun apiKeys(): List<String> {
        if (!prefs.contains("gemini_key")) return emptyList()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, encryptionKey(),
            GCMParameterSpec(128, Base64.decode(prefs.getString("gemini_iv", ""), Base64.NO_WRAP)))
        val raw = String(cipher.doFinal(Base64.decode(prefs.getString("gemini_key", ""), Base64.NO_WRAP)), Charsets.UTF_8)
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).map { array.getString(it) }.filter { it.isNotBlank() }
        } catch (_: Exception) { listOf(raw).filter { it.isNotBlank() } }
    }

    private fun deleteApiKey() { prefs.edit().remove("gemini_iv").remove("gemini_key").apply() }

    private fun encryptionKey(): javax.crypto.SecretKey {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "AIKhataGeminiKey"
        if (keyStore.containsAlias(alias)) {
            return (keyStore.getEntry(alias, null) as java.security.KeyStore.SecretKeyEntry).secretKey
        }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }
}
