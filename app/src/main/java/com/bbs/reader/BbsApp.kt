package com.bbs.reader

import android.app.Application
import com.bbs.reader.data.local.BoardPrefsStore
import com.bbs.reader.data.local.HomePrefsStore
import com.bbs.reader.data.local.ReadHistoryStore
import com.bbs.reader.data.local.SettingsStore
import com.bbs.reader.data.repository.BoardRepository

class BbsApp : Application() {
    lateinit var boardRepository: BoardRepository
        private set
    lateinit var readHistoryStore: ReadHistoryStore
        private set
    lateinit var settingsStore: SettingsStore
        private set
    lateinit var boardPrefsStore: BoardPrefsStore
        private set
    lateinit var homePrefsStore: HomePrefsStore
        private set

    override fun onCreate() {
        super.onCreate()
        boardRepository = BoardRepository()
        readHistoryStore = ReadHistoryStore(this)
        settingsStore = SettingsStore(this)
        boardPrefsStore = BoardPrefsStore(this)
        homePrefsStore = HomePrefsStore(this)
    }
}
