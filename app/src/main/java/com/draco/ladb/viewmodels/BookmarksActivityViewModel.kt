package com.draco.ladb.viewmodels

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
}