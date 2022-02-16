package com.draco.ladb.views

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.add -> {
                // TODO: Add bookmark
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