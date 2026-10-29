package com.example.syncedn.ui.player

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
import android.widget.Toast
import com.example.syncedn.data.model.SongData

object YouTubePlayerHelper {

    private val SAFE_VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

    fun isValidVideoId(videoId: String?): Boolean {
        return !videoId.isNullOrBlank() && SAFE_VIDEO_ID_REGEX.matches(videoId.trim())
    }

    fun openInAppPlayerModal(context: Context, videoId: String, songTitle: String = "") {
        val cleanVideoId = videoId.trim()
        if (!isValidVideoId(cleanVideoId)) return
        val dialog = android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
        }

        // Top Bar
        val topBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#0F172A"))
            val p = (14 * context.resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }

        val closeBtn = TextView(context).apply {
            text = "✕ Close"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            val pHorizontal = (12 * context.resources.displayMetrics.density).toInt()
            val pVertical = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(pHorizontal, pVertical, pHorizontal, pVertical)
            isClickable = true
            isFocusable = true
            contentDescription = "Close in-app player"
            setOnClickListener { dialog.dismiss() }
        }

        val titleView = TextView(context).apply {
            text = if (songTitle.isNotBlank()) "$songTitle — In-App Player" else "Synced N Player"
            setTextColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val pLeft = (12 * context.resources.displayMetrics.density).toInt()
            setPadding(pLeft, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        topBar.addView(closeBtn)
        topBar.addView(titleView)
        root.addView(topBar)

        val playerFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.BLACK)
        }

        val modalProgressBar = ProgressBar(context).apply {
            isIndeterminate = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        playerFrame.addView(modalProgressBar)

        val modalWebView = WebView(context).apply {
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
                allowContentAccess = false
                allowFileAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                try {
                    val defaultUa = userAgentString
                    userAgentString = defaultUa
                        .replace("; wv", "")
                        .replace("Version/4.0 ", "")
                } catch (_: Throwable) {}
            }

            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    modalProgressBar.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    modalProgressBar.visibility = View.GONE
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
                        src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&enablejsapi=1&rel=0&modestbranding=1" 
                        allowfullscreen 
                        allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture">
                    </iframe>
                </body>
                </html>
            """.trimIndent()
            loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
        }

        playerFrame.addView(modalWebView)
        root.addView(playerFrame)

        dialog.setContentView(root)
        dialog.setOnDismissListener {
            try {
                modalWebView.stopLoading()
                modalWebView.loadUrl("about:blank")
                modalWebView.destroy()
            } catch (_: Throwable) {}
        }
        dialog.show()
    }

    fun launchInAppCustomTab(context: Context, videoId: String) {
        openInAppPlayerModal(context, videoId)
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
                allowContentAccess = false
                allowFileAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                try {
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

        // Action row with in-app play controls (100% inside Synced N)
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
            text = "▶ Play in App Player"
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
                progressBar.visibility = View.VISIBLE
                val playHtml = """
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
                            src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&enablejsapi=1&rel=0&modestbranding=1" 
                            allowfullscreen 
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture">
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()
                webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", playHtml, "text/html", "UTF-8", null)
                webView.evaluateJavascript("var p = document.getElementById('ytplayer'); if (p) { p.contentWindow.postMessage('{\"event\":\"command\",\"func\":\"playVideo\",\"args\":\"\"}', '*'); }", null)
                Toast.makeText(context, "Playing rehearsal video in Synced N...", Toast.LENGTH_SHORT).show()
            }
        }
        actionsRow.addView(inAppBtn)

        val fullscreenBtn = TextView(context).apply {
            text = "⛶ Fullscreen In-App"
            textSize = 12f
            setTextColor(navyColor)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            setPadding(dp(8), dp(10), dp(8), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 0.8f)
            setOnClickListener {
                openInAppPlayerModal(context, videoId, selectedSongTitle)
            }
        }
        actionsRow.addView(fullscreenBtn)
        root.addView(actionsRow)

        val caption = TextView(context).apply {
            text = "Tip: Video plays directly inside Synced N. Tap 'Play in App Player' to watch while viewing chords."
            textSize = 11f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(dp(2), dp(2), dp(2), dp(14))
        }
        root.addView(caption)

        return root
    }
}
