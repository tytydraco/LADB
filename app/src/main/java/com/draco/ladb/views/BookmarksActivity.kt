package com.draco.ladb.views

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
    }

    private fun addBookmark() {
        val editText = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Add")
            .setView(editText)
            .setPositiveButton("Done") { _, _ ->
                viewModel.recyclerAdapter.add(editText.text.toString())
            }
            .setNegativeButton("Cancel", null)
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