package com.aikhata.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private lateinit var fallbackView: LinearLayout

    companion object { private const val FILE_CHOOSER_REQUEST = 4102 }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 248, 245)
        window.navigationBarColor = Color.rgb(247, 248, 245)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        fallbackView = createFallbackView()

        webView = WebView(this).apply {
            setBackgroundColor(Color.rgb(247, 248, 245))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    val allowedHost = Uri.parse(BuildConfig.WEB_APP_URL).host
                    if ((url.scheme == "https" || url.scheme == "http") && url.host == allowedHost) return false
                    return try { startActivity(Intent(Intent.ACTION_VIEW, url)); true } catch (_: Exception) { true }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    fallbackView.visibility = View.GONE
                    this@MainActivity.webView.visibility = View.VISIBLE
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) {
                        this@MainActivity.webView.visibility = View.GONE
                        fallbackView.visibility = View.VISIBLE
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                    this@MainActivity.fileChooserCallback = filePathCallback
                    val pickImage = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                    }
                    val chooser = Intent.createChooser(pickImage, "Choose a khata photo")
                    try {
                        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                        if (cameraIntent.resolveActivity(packageManager) != null) {
                            val photo = File.createTempFile("ai-khata-", ".jpg", cacheDir)
                            pendingCameraUri = FileProvider.getUriForFile(
                                this@MainActivity, "${packageName}.fileprovider", photo
                            )
                            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri)
                            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                        }
                        startActivityForResult(chooser, FILE_CHOOSER_REQUEST)
                    } catch (_: Exception) {
                        this@MainActivity.fileChooserCallback?.onReceiveValue(null)
                        this@MainActivity.fileChooserCallback = null
                        pendingCameraUri = null
                        return false
                    }
                    return true
                }
            }
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
            addView(fallbackView, LinearLayout.LayoutParams(-1, -1))
        }
        setContentView(layout)
        webView.visibility = View.VISIBLE
        fallbackView.visibility = View.GONE
        if (savedInstanceState == null) {
            val target = BuildConfig.WEB_APP_URL
            if (!target.startsWith("https://")) showFallback() else webView.loadUrl(target)
        } else webView.restoreState(savedInstanceState)
    }

    private fun createFallbackView(): LinearLayout {
        val title = TextView(this).apply {
            text = "AI Khata could not connect"; textSize = 21f
            setTextColor(Color.rgb(28, 43, 38)); gravity = Gravity.CENTER
        }
        val message = TextView(this).apply {
            text = "Check your internet connection. The app also needs its web service to be deployed and the APK built with that HTTPS address."
            textSize = 15f; setTextColor(Color.rgb(89, 104, 96)); gravity = Gravity.CENTER
            setPadding(28, 18, 28, 18)
        }
        val retry = TextView(this).apply {
            text = "Tap here to retry"; textSize = 16f
            setTextColor(Color.rgb(22, 123, 98)); gravity = Gravity.CENTER
            setPadding(24, 22, 24, 22)
            setOnClickListener {
                visibility = View.GONE
                webView.visibility = View.VISIBLE
                webView.loadUrl(BuildConfig.WEB_APP_URL)
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(247, 248, 245)); setPadding(24, 24, 24, 24)
            addView(title, LinearLayout.LayoutParams(-1, -2))
            addView(message, LinearLayout.LayoutParams(-1, -2))
            addView(retry, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun showFallback() {
        webView.visibility = View.GONE
        fallbackView.visibility = View.VISIBLE
    }

    @Deprecated("Deprecated in Android 13; kept for compatibility with Android 10 devices")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_CHOOSER_REQUEST) return
        val result = if (resultCode == RESULT_OK) data?.data ?: pendingCameraUri else null
        fileChooserCallback?.onReceiveValue(if (result != null) arrayOf(result) else null)
        fileChooserCallback = null
        pendingCameraUri = null
    }

    @Deprecated("Deprecated in Android 13; compatible fallback for Android 10")
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        if (this::webView.isInitialized) { webView.stopLoading(); webView.destroy() }
        super.onDestroy()
    }
}
