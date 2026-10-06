package com.bbs.reader

import android.app.Application
import com.bbs.reader.data.local.ReadHistoryStore
import com.bbs.reader.data.repository.BoardRepository

class BbsApp : Application() {
    lateinit var boardRepository: BoardRepository
        private set
    lateinit var readHistoryStore: ReadHistoryStore
        private set

    override fun onCreate() {
        super.onCreate()
        boardRepository = BoardRepository()
        readHistoryStore = ReadHistoryStore(this)
    }
}
