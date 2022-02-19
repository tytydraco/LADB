package com.draco.ladb.viewmodels

import android.app.Application
import android.content.Context
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.draco.ladb.R
import com.draco.ladb.recyclers.BookmarksRecyclerAdapter

class BookmarksActivityViewModel(application: Application): AndroidViewModel(application) {
    val recyclerAdapter = BookmarksRecyclerAdapter(application.applicationContext)

    /**
     * Prepare the recycler view
     */
    fun prepareRecycler(context: Context, recycler: RecyclerView) {
        recycler.apply {
            adapter = recyclerAdapter
            layoutManager = LinearLayoutManager(context)
        }
        recyclerAdapter.updateList()
    }

    fun add(context: Context, initialText: String) {
        val editText = EditText(context)
            .also {
                it.setText(initialText)
            }
        AlertDialog.Builder(context)
            .setTitle(R.string.add)
            .setView(editText)
            .setPositiveButton(R.string.done) { _, _ ->
                val text = editText.text.toString()
                if (text.isNotEmpty())
                    recyclerAdapter.add(text)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun edit(context: Context, text: String) {
        val editText = EditText(context)
            .also { it.setText(text) }
        AlertDialog.Builder(context)
            .setTitle(R.string.edit)
            .setView(editText)
            .setPositiveButton(R.string.done) { _, _ ->
                val newText = editText.text.toString()
                if (newText.isNotEmpty() && newText != text)
                    recyclerAdapter.edit(text, newText)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    fun areYouSure(context: Context, callback: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> callback() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}