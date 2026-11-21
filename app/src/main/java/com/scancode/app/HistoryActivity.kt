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
    private lateinit var search: EditText
    private lateinit var empty: View
    private lateinit var emptyTitle: TextView
    private lateinit var emptyDetail: TextView
    private var items = emptyList<ScanResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setInsetLayout(R.layout.activity_history)
        findViewById<View>(R.id.root).limitContentWidth(findViewById(R.id.content), 660)
        ViewCompat.setAccessibilityHeading(findViewById(R.id.historyTitle), true)
        history = History(this)
        adapter = HistoryAdapter(this)
        clearButton = findViewById(R.id.clearButton)
        search = findViewById(R.id.searchInput)
        empty = findViewById(R.id.historyEmpty)
        emptyTitle = findViewById(R.id.emptyTitle)
        emptyDetail = findViewById(R.id.emptyDetail)

        findViewById<View>(R.id.backButton).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        clearButton.setOnClickListener { confirmClear() }
        search.doAfterTextChanged { filter() }

        findViewById<ListView>(R.id.historyList).apply {
            adapter = this@HistoryActivity.adapter
            emptyView = empty
            setOnItemClickListener { _, _, index, _ ->
                this@HistoryActivity.adapter.getItem(index)?.let { result ->
                    showResult(result, history, fromHistory = true, onHistoryChanged = ::refresh)
                }
            }
        }
        refresh()
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

    private fun refresh() {
        items = history.all()
        clearButton.isVisible = items.isNotEmpty()
        search.isEnabled = items.isNotEmpty()
        filter()
    }

    private fun filter() {
        val query = search.text.toString().trim()
        emptyTitle.setText(if (items.isEmpty()) R.string.history_empty_title else R.string.history_no_matches)
        emptyDetail.setText(if (items.isEmpty()) R.string.history_empty_detail else R.string.history_try_search)
        adapter.replace(items.filter {
            query.isEmpty() || it.searchableValue(this).contains(query, ignoreCase = true) ||
                it.displayFormat(this).contains(query, ignoreCase = true)
        })
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
