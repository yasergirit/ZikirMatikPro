package com.yasergirit.zikirmasterpro

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private fun t(tr: String, en: String, de: String, ar: String, lang: String): String = when (lang) {
    "en" -> en; "de" -> de; "ar" -> ar; else -> tr
}

private fun openInYouTube(context: Context) {
    try {
        val ytIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:${KaabaLiveConfig.YOUTUBE_VIDEO_ID}"))
        ytIntent.setPackage("com.google.android.youtube")
        context.startActivity(ytIntent)
    } catch (_: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(KaabaLiveConfig.YOUTUBE_WATCH_URL)))
        } catch (_: Exception) {}
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun KaabaLiveScreen(
    isDarkTheme: Boolean,
    selectedLanguage: String
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val bg = MaterialTheme.colorScheme.background
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    var isLoading by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Restore orientation when leaving screen
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.javaScriptEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            setBackgroundColor(android.graphics.Color.BLACK)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    isLoading = false
                }
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false
                }
            }

            // Handle YouTube's native fullscreen button
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    customView = view
                    customViewCallback = callback
                    isFullscreen = true
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

                override fun onHideCustomView() {
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                    isFullscreen = false
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }

            loadUrl("https://m.youtube.com/watch?v=${KaabaLiveConfig.YOUTUBE_VIDEO_ID}")
        }
    }

    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }

    // When device rotated to landscape manually, keep fullscreen look
    val showFullscreen = isLandscape || isFullscreen

    if (showFullscreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (customView != null) {
                // YouTube's native fullscreen view (from fullscreen button)
                AndroidView(
                    factory = {
                        customView!!.apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Landscape rotation — show WebView fullscreen
                AndroidView(
                    factory = {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    } else {
        // Portrait mode
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = {
                        (webView.parent as? ViewGroup)?.removeView(webView)
                        webView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = t("Canl\u0131 yay\u0131n YouTube altyap\u0131s\u0131 \u00fczerinden sunulmaktad\u0131r.",
                    "Live stream is provided via YouTube infrastructure.",
                    "Der Livestream wird \u00fcber die YouTube-Infrastruktur bereitgestellt.",
                    "\u064A\u062A\u0645 \u062A\u0642\u062F\u064A\u0645 \u0627\u0644\u0628\u062B \u0639\u0628\u0631 \u0628\u0646\u064A\u0629 \u064A\u0648\u062A\u064A\u0648\u0628.",
                    selectedLanguage),
                fontSize = 13.sp, color = onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { openInYouTube(context) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primary)
            ) {
                Text(
                    text = t("YouTube'da A\u00e7", "Open in YouTube", "In YouTube \u00f6ffnen",
                        "\u0641\u062A\u062D \u0641\u064A \u064A\u0648\u062A\u064A\u0648\u0628", selectedLanguage),
                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
