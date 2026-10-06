package com.bbs.reader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "read_history")

class ReadHistoryStore(private val context: Context) {
    private val key = stringSetPreferencesKey("read_post_ids")

    val readIds: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[key] ?: emptySet()
    }

    suspend fun markRead(postId: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            current.add(postId)
            // Keep a reasonable upper bound
            if (current.size > 2000) {
                prefs[key] = current.toList().takeLast(1500).toSet()
            } else {
                prefs[key] = current
            }
        }
    }
}
