package com.scancode.app

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

internal fun AppCompatActivity.showResult(
    result: ScanResult,
    history: History,
    fromHistory: Boolean = false,
    onHistoryChanged: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    val content = ScanContent.parse(result)
    val dialog = BottomSheetDialog(this)
    dialog.setContentView(R.layout.dialog_result)
    val view = requireNotNull(dialog.findViewById<View>(R.id.resultContent))
    dialog.behavior.skipCollapsed = true
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
    dialog.setOnDismissListener { onDismiss() }

    view.findViewById<TextView>(R.id.formatText).text = getString(
        R.string.code_format, result.displayFormat(this), getString(content.kind.label),
    )
    view.findViewById<TextView>(R.id.valueText).text = result.value
    view.findViewById<View>(R.id.closeButton).setOnClickListener { dialog.dismiss() }

    val action = actionFor(result, content)
    view.findViewById<MaterialButton>(R.id.openButton).apply {
        isVisible = action != null
        if (action != null) {
            setText(action.first)
            setOnClickListener { openExternal(action.second) }
        }
    }
    view.findViewById<MaterialButton>(R.id.copyPasswordButton).apply {
        isVisible = content.wifiPassword != null
        content.wifiPassword?.let { password ->
            setOnClickListener { copyCode(password) }
        }
    }

    view.findViewById<MaterialButton>(R.id.saveButton).apply {
        if (fromHistory) {
            setText(R.string.delete)
            setOnClickListener {
                MaterialAlertDialogBuilder(this@showResult)
                    .setTitle(R.string.delete_title)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ ->
                        history.remove(result)
                        onHistoryChanged()
                        dialog.dismiss()
                    }
                    .show()
            }
        } else {
            val markSaved = {
                setText(R.string.saved)
                isEnabled = false
            }
            if (history.all().any { it.value == result.value }) markSaved()
            setOnClickListener {
                history.add(result)
                markSaved()
                message(R.string.saved)
            }
        }
    }

    view.findViewById<View>(R.id.copyButton).setOnClickListener { copyCode(result.value) }
    view.findViewById<View>(R.id.shareButton).setOnClickListener {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, result.value)
        openExternal(Intent.createChooser(send, getString(R.string.share)))
    }
    dialog.show()
}

internal fun ScanResult.displayFormat(context: Context): String =
    if (format == "Code" || format == "Código") context.getString(R.string.unknown_format) else format

internal fun ScanResult.searchableValue(context: Context): String =
    if (ScanContent.parse(this).kind == CodeKind.WIFI) summary(context) else value

internal fun ScanResult.summary(context: Context): String {
    val content = ScanContent.parse(this)
    if (content.kind == CodeKind.WIFI) {
        return content.wifiNetwork?.let { context.getString(R.string.wifi_network, it) }
            ?: context.getString(R.string.kind_wifi)
    }
    return value.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(240).orEmpty()
}

private val CodeKind.label: Int
    get() = when (this) {
        CodeKind.TEXT -> R.string.kind_text
        CodeKind.LINK -> R.string.kind_link
        CodeKind.PHONE -> R.string.kind_phone
        CodeKind.EMAIL -> R.string.kind_email
        CodeKind.MESSAGE -> R.string.kind_message
        CodeKind.LOCATION -> R.string.kind_location
        CodeKind.STORE -> R.string.kind_store
        CodeKind.WIFI -> R.string.kind_wifi
        CodeKind.CONTACT -> R.string.kind_contact
        CodeKind.EVENT -> R.string.kind_event
        CodeKind.BARCODE -> R.string.kind_barcode
    }

private fun actionFor(result: ScanResult, content: ScanContent): Pair<Int, Intent>? {
    if (content.kind == CodeKind.WIFI) return R.string.wifi to Intent(Settings.ACTION_WIFI_SETTINGS)
    val uri = content.target?.toUri()
    return when (content.kind) {
        CodeKind.LINK -> R.string.open_link to Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE)
        CodeKind.PHONE -> R.string.dial to Intent(Intent.ACTION_DIAL, uri)
        CodeKind.EMAIL -> R.string.compose_email to Intent(Intent.ACTION_SENDTO, uri)
        CodeKind.MESSAGE -> R.string.compose_message to Intent(Intent.ACTION_SENDTO, uri)
        CodeKind.LOCATION -> R.string.open_map to Intent(Intent.ACTION_VIEW, uri)
        CodeKind.STORE -> R.string.open_store to Intent(Intent.ACTION_VIEW, uri)
        CodeKind.TEXT, CodeKind.BARCODE -> {
            val value = result.value.trim()
            if (value.length > 1000) return null
            val searchUrl = "https://www.google.com/search".toUri().buildUpon()
                .appendQueryParameter("q", value)
                .build()
            R.string.search to Intent(Intent.ACTION_VIEW, searchUrl).addCategory(Intent.CATEGORY_BROWSABLE)
        }
        CodeKind.WIFI, CodeKind.CONTACT, CodeKind.EVENT -> null
    }
}

internal fun Context.openExternal(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        message(R.string.no_app)
    } catch (_: SecurityException) {
        message(R.string.unsupported_action)
    }
}

internal fun Context.message(@StringRes text: Int) =
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

private fun Context.copyCode(value: String) {
    val clip = ClipData.newPlainText(getString(R.string.app_name), value)
    if (Build.VERSION.SDK_INT >= 33) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) message(R.string.copied)
}
