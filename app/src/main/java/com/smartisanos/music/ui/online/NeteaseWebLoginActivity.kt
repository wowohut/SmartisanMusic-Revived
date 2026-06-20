package com.smartisanos.music.ui.online

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.smartisanos.music.R
import com.smartisanos.music.data.online.NeteaseAuthStore
import com.smartisanos.music.data.online.parseNeteaseCookieHeader
import org.json.JSONObject
import smartisanos.widget.TitleBar

internal class NeteaseWebLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var authStore: NeteaseAuthStore
    private var hasReturned = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        authStore = NeteaseAuthStore(this)
        setResult(Activity.RESULT_CANCELED)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        seedSavedCookies(cookieManager)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            clipChildren = false
            clipToPadding = false
        }
        val statusBarSpacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
            )
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = createToolbarContainer()
        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                allowFileAccess = false
                allowContentAccess = false
                userAgentString = DesktopUserAgent
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            webChromeClient = WebChromeClient()
            webViewClient = LoginWebViewClient()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }
        root.addView(statusBarSpacer)
        root.addView(toolbar)
        root.addView(webView)
        configureSystemBarInsets(root, statusBarSpacer)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackNavigation()
                }
            },
        )

        webView.loadUrl(TargetUrl)
    }

    override fun onDestroy() {
        if (this::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun createToolbarContainer(): FrameLayout {
        val titleBarHeight = resources.getDimensionPixelSize(R.dimen.title_bar_height)
        val shadowHeight = resources.getDimensionPixelSize(R.dimen.title_bar_shadow_height)
        return FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                titleBarHeight,
            )
            clipChildren = false
            clipToPadding = false
            setBackgroundColor(Color.WHITE)
            addView(
                TitleBar(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setCenterText(R.string.netease_login_title)
                    setShadowVisible(false)
                    addLeftImageView(R.drawable.standard_icon_back_selector).apply {
                        contentDescription = getString(R.string.back)
                        setOnClickListener { handleBackNavigation() }
                    }
                    addRightImageView(R.drawable.standard_icon_complete_selector).apply {
                        contentDescription = getString(R.string.done)
                        setOnClickListener { returnCookiesIfLoggedIn(showError = true) }
                    }
                },
            )
            addView(
                View(context).apply {
                    setBackgroundResource(R.drawable.title_bar_shadow)
                    translationY = shadowHeight.toFloat()
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    shadowHeight,
                    android.view.Gravity.BOTTOM,
                ),
            )
        }
    }

    private fun finishWithPageTransition() {
        finishWithCloseTransition()
    }

    private fun finishAfterLogin() {
        finishWithCloseTransition()
    }

    private fun finishWithCloseTransition() {
        val enterAnim = R.anim.legacy_activity_slide_in_from_left
        val exitAnim = R.anim.legacy_activity_slide_out_to_right
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim)
            finish()
        } else {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(enterAnim, exitAnim)
        }
    }

    private fun handleBackNavigation() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            finishWithPageTransition()
        }
    }

    private fun configureSystemBarInsets(root: LinearLayout, statusBarSpacer: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val protectedInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            statusBarSpacer.updateLayoutParams<LinearLayout.LayoutParams> {
                height = protectedInsets.top
            }
            view.setPadding(
                protectedInsets.left,
                0,
                protectedInsets.right,
                protectedInsets.bottom,
            )
            windowInsets
        }
    }

    private fun returnCookiesIfLoggedIn(showError: Boolean): Boolean {
        if (hasReturned) {
            return true
        }
        val cookies = readCookieMap()
        val validation = authStore.validateCookies(cookies)
        if (!validation.isAccepted) {
            if (showError) {
                Toast.makeText(this, R.string.netease_login_cookie_missing, Toast.LENGTH_SHORT).show()
            }
            return false
        }
        hasReturned = true
        val cookieJson = JSONObject().also { root ->
            validation.cookies.forEach { (key, value) -> root.put(key, value) }
        }.toString()
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(ExtraCookieJson, cookieJson),
        )
        finishAfterLogin()
        return true
    }

    private fun readCookieMap(): Map<String, String> {
        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()
        return buildList {
            webView.url?.takeIf(String::isNotBlank)?.let(::add)
            addAll(CookieReadUrls)
        }
            .distinct()
            .mapNotNull { url -> cookieManager.getCookie(url)?.takeIf(String::isNotBlank) }
            .joinToString("; ")
            .let(::parseNeteaseCookieHeader)
    }

    private fun seedSavedCookies(cookieManager: CookieManager) {
        val cookies = authStore.getCookies()
        if (cookies.isEmpty()) {
            return
        }
        for (url in CookieReadUrls) {
            cookies.forEach { (key, value) ->
                cookieManager.setCookie(url, "$key=$value; Path=/")
            }
        }
        cookieManager.flush()
    }

    private fun isAllowedLoginUri(uri: Uri?): Boolean {
        val target = uri ?: return false
        if (target.toString() == "about:blank") {
            return true
        }
        if (!target.scheme.equals("https", ignoreCase = true)) {
            return false
        }
        val host = target.host?.lowercase().orEmpty()
        return AllowedLoginDomains.any { domain ->
            host == domain || host.endsWith(".$domain")
        }
    }

    private inner class LoginWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val uri = request?.url
            return !isAllowedLoginUri(uri)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            CookieManager.getInstance().flush()
        }
    }

    companion object {
        const val ExtraCookieJson = "com.smartisanos.music.extra.NETEASE_COOKIE_JSON"

        fun createIntent(context: Context): Intent {
            return Intent(context, NeteaseWebLoginActivity::class.java)
        }

        private const val TargetUrl = "https://music.163.com/"
        private val CookieReadUrls = listOf(
            "https://music.163.com/",
            "https://music.163.com",
            "https://interface.music.163.com/",
            "https://interface3.music.163.com/",
        )
        private val AllowedLoginDomains = setOf(
            "163.com",
            "126.net",
            "163yun.com",
        )
        private const val DesktopUserAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
    }
}
