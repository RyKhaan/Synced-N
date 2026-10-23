package com.example.syncedn.ui.player

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.example.syncedn.data.model.SongData

object YouTubePlayerHelper {

    fun launchInAppCustomTab(context: Context, videoId: String) {
        val ytUrl = "https://www.youtube.com/watch?v=$videoId"
        try {
            val navyColor = 0xFF0F172A.toInt()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(
                    CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(navyColor)
                        .setNavigationBarColor(navyColor)
                        .build()
                )
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(context, Uri.parse(ytUrl))
        } catch (_: Throwable) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(ytUrl))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    fun buildHybridPlayerView(
        context: Context,
        referenceUrl: String,
        selectedSongTitle: String,
        navyColor: Int = 0xFF0F172A.toInt(),
        dp: (Int) -> Int
    ): View? {
        val videoId = SongData.extractYoutubeVideoId(referenceUrl) ?: return null

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Section header
        val header = TextView(context).apply {
            text = "Rehearsal Video"
            textSize = 13f
            setTextColor(navyColor)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(header)

        // Video container FrameLayout
        val frameContainer = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(210)
            )
        }

        val progressBar = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        frameContainer.addView(progressBar)

        val webView = WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.BLACK)

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                allowContentAccess = true
                allowFileAccess = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                try {
                    // Remove WebView token '; wv' and 'Version/4.0' to prevent YouTube embed blocking
                    val defaultUa = userAgentString
                    userAgentString = defaultUa
                        .replace("; wv", "")
                        .replace("Version/4.0 ", "")
                } catch (_: Throwable) {}
            }

            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                    android.util.Log.d("YouTubePlayer", "WebView console: ${consoleMessage?.message()}")
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    progressBar.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    progressBar.visibility = View.GONE
                }
            }

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                    <style>
                        * { box-sizing: border-box; }
                        body { margin: 0; padding: 0; background-color: #000000; display: flex; align-items: center; justify-content: center; height: 100vh; overflow: hidden; }
                        iframe { width: 100%; height: 100%; border: 0; }
                    </style>
                </head>
                <body>
                    <iframe 
                        id="ytplayer"
                        type="text/html"
                        src="https://www.youtube-nocookie.com/embed/$videoId?playsinline=1&controls=1&enablejsapi=1&rel=0&modestbranding=1" 
                        allowfullscreen 
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture">
                    </iframe>
                </body>
                </html>
            """.trimIndent()

            loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
        }

        frameContainer.addView(webView)
        root.addView(frameContainer)

        // Action row with In-App Player button and YouTube App button
        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(4)
            }
        }

        val inAppBtn = TextView(context).apply {
            text = "\u25B6 Play in In-App Player"
            textSize = 12f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(navyColor)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.2f).apply {
                marginEnd = dp(6)
            }
            setOnClickListener {
                launchInAppCustomTab(context, videoId)
            }
        }
        actionsRow.addView(inAppBtn)

        val ytAppBtn = TextView(context).apply {
            text = "YouTube App"
            textSize = 12f
            setTextColor(navyColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            setPadding(dp(8), dp(10), dp(8), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 0.8f)
            setOnClickListener {
                try {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Throwable) {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        actionsRow.addView(ytAppBtn)
        root.addView(actionsRow)

        val caption = TextView(context).apply {
            text = "Tip: If the video owner restricted embedding, tap 'Play in In-App Player' above to watch without leaving Synced N."
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dp(2), dp(2), dp(2), dp(14))
        }
        root.addView(caption)

        return root
    }
}
