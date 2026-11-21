package com.scancode.app

import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

internal fun ComponentActivity.setInsetLayout(layout: Int) {
    enableEdgeToEdge()
    setContentView(layout)
    val root = findViewById<View>(R.id.root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val safe = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
        )
        view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
        insets
    }
}

internal fun View.limitContentWidth(content: View, maxWidthDp: Int) {
    val maxWidth = dp(maxWidthDp)
    addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        val width = (view.width - view.paddingLeft - view.paddingRight).coerceIn(0, maxWidth)
        if (width > 0 && width != content.layoutParams.width) {
            content.updateLayoutParams { this.width = width }
        }
    }
}

internal fun View.dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
