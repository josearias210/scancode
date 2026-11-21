package com.scancode.app

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Date

class HistoryActivity : AppCompatActivity() {
    private lateinit var history: History
    private lateinit var adapter: HistoryAdapter
    private lateinit var clearButton: MaterialButton
    private lateinit var manageButton: MaterialButton
    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var search: EditText
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDetail: TextView
    private var items = emptyList<ScanResult>()
    private var selectedCollectionId: String? = null
    private var selectedCollection: ScanCollection? = null

    private val backToHistory = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = selectCollection(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setInsetLayout(R.layout.activity_history)
        findViewById<View>(R.id.root).limitContentWidth(findViewById(R.id.content), 660)
        history = History(this)
        adapter = HistoryAdapter(this)
        selectedCollectionId = savedInstanceState?.getString("collectionId")
        clearButton = findViewById(R.id.clearButton)
        manageButton = findViewById(R.id.manageCollectionButton)
        title = findViewById(R.id.historyTitle)
        subtitle = findViewById(R.id.historySubtitle)
        search = findViewById(R.id.searchInput)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyDetail = findViewById(R.id.emptyDetail)
        ViewCompat.setAccessibilityHeading(title, true)

        onBackPressedDispatcher.addCallback(this, backToHistory)
        findViewById<View>(R.id.backButton).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.collectionsButton).setOnClickListener { openCollections() }
        clearButton.setOnClickListener { confirmClear() }
        manageButton.setOnClickListener { selectedCollection?.let(::manageCollection) }
        search.doAfterTextChanged { filter() }

        findViewById<ListView>(R.id.historyList).apply {
            adapter = this@HistoryActivity.adapter
            emptyView = this@HistoryActivity.findViewById(R.id.historyEmpty)
            setOnItemClickListener { _, _, index, _ ->
                this@HistoryActivity.adapter.getItem(index)?.let { result ->
                    val origin = if (selectedCollection == null) ResultOrigin.HISTORY else ResultOrigin.COLLECTION
                    showResult(result, history, origin = origin, onHistoryChanged = ::refresh)
                }
            }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("collectionId", selectedCollectionId)
    }

    private fun openCollections() {
        val choices = history.snapshot().collections
        val names = listOf(getString(R.string.collection_all_history)) + choices.map { it.name }
        val selected = choices.indexOfFirst { it.id == selectedCollectionId } + 1
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.collections)
            .setSingleChoiceItems(names.toTypedArray(), selected) { dialog, position ->
                dialog.dismiss()
                selectCollection(choices.getOrNull(position - 1)?.id)
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.collection_new) { _, _ ->
                editCollectionName(history) { selectCollection(it.id) }
            }
            .show()
    }

    private fun manageCollection(collection: ScanCollection) {
        val actions = listOf(R.string.collection_rename, R.string.collection_delete)
            .map(::getString).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(collection.name)
            .setItems(actions) { _, position ->
                if (position == 0) editCollectionName(history, collection) { refresh() }
                else confirmDeleteCollection(collection)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteCollection(collection: ScanCollection) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.collection_delete_title)
            .setMessage(R.string.collection_delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                history.deleteCollection(collection.id)
                selectCollection(null)
            }
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.clear_all) { _, _ ->
                history.clear()
                search.text.clear()
                refresh()
            }
            .show()
    }

    private fun selectCollection(id: String?) {
        selectedCollectionId = id
        search.text.clear()
        search.clearFocus()
        refresh()
    }

    private fun refresh() {
        val library = history.snapshot()
        val collection = library.collections.firstOrNull { it.id == selectedCollectionId }
        selectedCollection = collection
        selectedCollectionId = collection?.id
        items = if (collection == null) library.recent else library.scansIn(collection)
        backToHistory.isEnabled = collection != null
        title.text = collection?.name ?: getString(R.string.history_title)
        subtitle.setText(if (collection == null) R.string.history_subtitle else R.string.collection_subtitle)
        manageButton.isVisible = collection != null
        clearButton.isVisible = collection == null && items.isNotEmpty()
        search.isEnabled = items.isNotEmpty()
        filter()
    }

    private fun filter() {
        val query = search.text.toString().trim()
        val visible = items.filter {
            query.isEmpty() || it.searchableValue(this).contains(query, ignoreCase = true) ||
                it.displayFormat(this).contains(query, ignoreCase = true)
        }
        when {
            items.isNotEmpty() -> {
                emptyTitle.setText(R.string.history_no_matches)
                emptyDetail.setText(R.string.history_try_search)
            }
            selectedCollection != null -> {
                emptyTitle.setText(R.string.collection_empty_title)
                emptyDetail.setText(R.string.collection_empty_detail)
            }
            else -> {
                emptyTitle.setText(R.string.history_empty_title)
                emptyDetail.setText(R.string.history_empty_detail)
            }
        }
        adapter.replace(visible)
    }
}

private class HistoryAdapter(context: Context) : ArrayAdapter<ScanResult>(context, R.layout.row_history) {
    private val inflater = LayoutInflater.from(context)
    private val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    fun replace(items: List<ScanResult>) {
        setNotifyOnChange(false)
        clear()
        addAll(items)
        notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: inflater.inflate(R.layout.row_history, parent, false)
        val result = requireNotNull(getItem(position))
        row.findViewById<TextView>(R.id.historyValue).text = result.summary(context)
        row.findViewById<TextView>(R.id.historyMeta).text = context.getString(
            R.string.code_format, result.displayFormat(context), date.format(Date(result.time)),
        )
        return row
    }
}
