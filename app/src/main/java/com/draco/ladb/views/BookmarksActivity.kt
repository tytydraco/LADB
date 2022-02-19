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
    private lateinit var initialText: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)
        recycler = findViewById(R.id.recycler)

        initialText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

        viewModel.prepareRecycler(this, recycler)
        viewModel.recyclerAdapter.pickHook = {
            val intent = Intent()
                .putExtra(Intent.EXTRA_TEXT, it)
            setResult(Activity.RESULT_OK, intent)
            finish()
        }

        viewModel.recyclerAdapter.deleteHook = {
            viewModel.areYouSure(this) { viewModel.recyclerAdapter.delete(it) }
        }

        viewModel.recyclerAdapter.editHook = { viewModel.edit(this, it) }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add -> {
                viewModel.add(this, initialText)
                initialText = ""
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