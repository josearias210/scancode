package com.scancode.app

import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

internal fun AppCompatActivity.editCollectionName(
    history: History,
    collection: ScanCollection? = null,
    onCancel: () -> Unit = {},
    onSaved: (ScanCollection) -> Unit,
) {
    val dialog = MaterialAlertDialogBuilder(this)
        .setTitle(if (collection == null) R.string.collection_new else R.string.collection_rename)
        .setView(R.layout.dialog_collection_name)
        .setNegativeButton(R.string.cancel) { _, _ -> onCancel() }
        .setPositiveButton(if (collection == null) R.string.collection_create else R.string.save, null)
        .create()
    dialog.setOnCancelListener { onCancel() }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.setOnShowListener {
        val name = requireNotNull(dialog.findViewById<TextInputEditText>(R.id.collectionName))
        val field = requireNotNull(dialog.findViewById<TextInputLayout>(R.id.collectionNameField))
        name.setText(collection?.name)
        name.selectAll()
        name.requestFocus()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val value = name.text.toString().trim()
            if (value.isEmpty()) {
                field.error = getString(R.string.collection_name_required)
                return@setOnClickListener
            }
            val saved = history.saveCollectionName(value, collection?.id)
            if (saved == null) {
                field.error = getString(R.string.collection_name_unavailable)
                return@setOnClickListener
            }
            dialog.dismiss()
            onSaved(saved)
        }
    }
    dialog.show()
}

internal fun AppCompatActivity.chooseResultCollections(
    history: History,
    result: ScanResult,
    saveToHistory: Boolean,
    onSaved: () -> Unit,
) {
    val selected = history.snapshot().collectionIdsFor(result.value).toMutableSet()

    fun applySelection() {
        history.setCollections(result, selected, saveToHistory)
        onSaved()
    }

    fun showPicker() {
        val choices = history.snapshot().collections
        if (choices.isEmpty()) {
            editCollectionName(history) {
                selected.add(it.id)
                applySelection()
            }
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.collection_choose)
            .setMultiChoiceItems(
                choices.map { it.name }.toTypedArray(),
                BooleanArray(choices.size) { choices[it].id in selected },
            ) { _, index, checked ->
                val id = choices[index].id
                if (checked) selected.add(id) else selected.remove(id)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.collection_new) { _, _ ->
                editCollectionName(history, onCancel = ::showPicker) {
                    selected.add(it.id)
                    showPicker()
                }
            }
            .setPositiveButton(R.string.save) { _, _ -> applySelection() }
            .show()
    }

    showPicker()
}
