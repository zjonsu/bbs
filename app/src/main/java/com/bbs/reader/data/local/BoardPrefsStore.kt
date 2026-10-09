package com.bbs.reader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.boardPrefsDataStore by preferencesDataStore(name = "board_prefs")

/** 게시판별 UI 선호값 (읽은 글 숨기기 등) */
class BoardPrefsStore(private val context: Context) {
    private val hideReadKey = stringSetPreferencesKey("hide_read_board_ids")

    val hideReadBoardIds: Flow<Set<String>> = context.boardPrefsDataStore.data.map { prefs ->
        prefs[hideReadKey] ?: emptySet()
    }

    fun hideReadFor(boardId: String): Flow<Boolean> =
        hideReadBoardIds.map { boardId in it }

    suspend fun setHideRead(boardId: String, hide: Boolean) {
        context.boardPrefsDataStore.edit { prefs ->
            val current = prefs[hideReadKey]?.toMutableSet() ?: mutableSetOf()
            if (hide) current.add(boardId) else current.remove(boardId)
            prefs[hideReadKey] = current
        }
    }
}
