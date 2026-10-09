package com.bbs.reader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbs.reader.data.model.Community
import com.bbs.reader.data.repository.CommunityCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.homePrefsDataStore by preferencesDataStore(name = "home_prefs")

/** 홈 커뮤니티 목록 순서 · 활성/비활성 */
class HomePrefsStore(private val context: Context) {
    private val orderKey = stringPreferencesKey("community_order_json")
    private val disabledKey = stringSetPreferencesKey("disabled_community_ids")

    private val catalogIds: List<String>
        get() = CommunityCatalog.communities.map { it.id }

    val communityOrder: Flow<List<String>> = context.homePrefsDataStore.data.map { prefs ->
        mergeOrder(decodeOrder(prefs[orderKey]))
    }

    val disabledCommunityIds: Flow<Set<String>> = context.homePrefsDataStore.data.map { prefs ->
        prefs[disabledKey] ?: emptySet()
    }

    /** 홈에 표시할 커뮤니티 (활성만, 저장 순서 반영) */
    val visibleCommunities: Flow<List<Community>> = combine(
        communityOrder,
        disabledCommunityIds
    ) { order, disabled ->
        val byId = CommunityCatalog.communities.associateBy { it.id }
        order.mapNotNull { byId[it] }.filter { it.id !in disabled }
    }

    /** 설정 화면용: 전체 목록 + 활성 여부 */
    val managedCommunities: Flow<List<ManagedCommunity>> = combine(
        communityOrder,
        disabledCommunityIds
    ) { order, disabled ->
        val byId = CommunityCatalog.communities.associateBy { it.id }
        order.mapNotNull { id ->
            val community = byId[id] ?: return@mapNotNull null
            ManagedCommunity(community = community, enabled = id !in disabled)
        }
    }

    suspend fun setOrder(ids: List<String>) {
        context.homePrefsDataStore.edit { prefs ->
            prefs[orderKey] = encodeOrder(mergeOrder(ids))
        }
    }

    /** 홈에 보이는(활성) 항목만 재정렬했을 때 전체 순서에 반영 */
    suspend fun setOrderFromVisible(visibleIds: List<String>) {
        context.homePrefsDataStore.edit { prefs ->
            val disabled = prefs[disabledKey] ?: emptySet()
            val current = mergeOrder(decodeOrder(prefs[orderKey]))
            val queue = ArrayDeque(visibleIds.filter { it !in disabled })
            val rebuilt = current.map { id ->
                if (id in disabled) id else queue.removeFirstOrNull() ?: id
            }
            prefs[orderKey] = encodeOrder(mergeOrder(rebuilt + queue))
        }
    }

    suspend fun setEnabled(communityId: String, enabled: Boolean) {
        context.homePrefsDataStore.edit { prefs ->
            val current = prefs[disabledKey]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.remove(communityId) else current.add(communityId)
            // 카탈로그에 없는 id 정리
            current.retainAll(catalogIds.toSet())
            prefs[disabledKey] = current
        }
    }

    private fun mergeOrder(stored: List<String>): List<String> {
        val known = catalogIds.toSet()
        val cleaned = stored.filter { it in known }
        val missing = catalogIds.filter { it !in cleaned }
        return cleaned + missing
    }

    private fun encodeOrder(ids: List<String>): String {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeOrder(json: String?): List<String> {
        if (json.isNullOrBlank()) return catalogIds
        return try {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val id = arr.optString(i)
                    if (id.isNotBlank()) add(id)
                }
            }
        } catch (_: Exception) {
            catalogIds
        }
    }
}

data class ManagedCommunity(
    val community: Community,
    val enabled: Boolean
)
