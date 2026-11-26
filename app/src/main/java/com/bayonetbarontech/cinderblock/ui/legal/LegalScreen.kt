package com.bayonetbarontech.cinderblock.ui.legal

import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.bayonetbarontech.cinderblock.ui.components.FeatureBar
import com.bayonetbarontech.cinderblock.ui.theme.LocalColors

@Composable
fun LegalScreen(nav: NavHostController, asset: String, title: String) {
    val colors = LocalColors.current
    val context = LocalContext.current
    val html = remember(asset, colors.background, colors.foreground, colors.mutedForeground, colors.neonOrange) {
        context.assets.open(asset).bufferedReader().use { it.readText() }
            .replace("--bg: #221A12;", "--bg: ${colors.background.toCss()};")
            .replace("--fg: #E8E0D6;", "--fg: ${colors.foreground.toCss()};")
            .replace("--muted: #B5AA9E;", "--muted: ${colors.mutedForeground.toCss()};")
            .replace("--accent: #E07A2F;", "--accent: ${colors.neonOrange.toCss()};")
    }
    var shown by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        FeatureBar(title, { nav.popBackStack() })
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { host ->
                WebView(host).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                }
            },
            update = { view ->
                view.setBackgroundColor(colors.background.toArgb())
                if (shown != html) {
                    shown = html
                    view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                }
            },
        )
    }
}

private fun Color.toCss(): String = "#%06X".format(0xFFFFFF and toArgb())
