package com.draco.ladb.views

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.draco.ladb.R
import com.draco.ladb.viewmodels.BookmarksActivityViewModel

class BookmarksActivity: AppCompatActivity() {
    private val viewModel: BookmarksActivityViewModel by viewModels()
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        recycler = findViewById(R.id.recycler)

        viewModel.prepareRecycler(this, recycler)
        viewModel.recyclerAdapter.pickHook = {
            val intent = Intent()
                .putExtra(Intent.EXTRA_TEXT, it)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        viewModel.recyclerAdapter.deleteHook = {
            areYouSure() { viewModel.recyclerAdapter.delete(it) }
        }

        viewModel.recyclerAdapter.editHook = { editBookmark(it) }
    }

    private fun areYouSure(callback: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete)
            .setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ -> callback() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun editBookmark(text: String) {
        val editText = EditText(this)
            .also { it.setText(text) }
        AlertDialog.Builder(this)
            .setTitle(R.string.edit)
            .setView(editText)
            .setPositiveButton(R.string.done) { _, _ ->
                val newText = editText.text.toString()
                if (newText.isNotEmpty() && newText != text)
                    viewModel.recyclerAdapter.edit(text, newText)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun addBookmark() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.add)
            .setView(editText)
            .setPositiveButton(R.string.done) { _, _ ->
                val text = editText.text.toString()
                if (text.isNotEmpty())
                    viewModel.recyclerAdapter.add(text)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add -> {
                addBookmark()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bookmarks, menu)
        return true
    }
}