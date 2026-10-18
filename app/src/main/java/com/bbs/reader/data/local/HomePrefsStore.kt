package com.bbs.reader.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bbs.reader.data.model.BoardInfo
import com.bbs.reader.data.model.Community
import com.bbs.reader.data.repository.CommunityCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray

private val Context.homePrefsDataStore by preferencesDataStore(name = "home_prefs")

/** 홈 커뮤니티 목록 순서 · 사이트/게시판 활성/비활성 */
class HomePrefsStore(private val context: Context) {
    private val orderKey = stringPreferencesKey("community_order_json")
    private val disabledKey = stringSetPreferencesKey("disabled_community_ids")
    private val disabledBoardsKey = stringSetPreferencesKey("disabled_board_ids")

    private val catalogIds: List<String>
        get() = CommunityCatalog.communities.map { it.id }

    private val catalogBoardIds: Set<String>
        get() = CommunityCatalog.communities.flatMap { it.boards.map { b -> b.id } }.toSet()

    val communityOrder: Flow<List<String>> = context.homePrefsDataStore.data.map { prefs ->
        mergeOrder(decodeOrder(prefs[orderKey]))
    }

    val disabledCommunityIds: Flow<Set<String>> = context.homePrefsDataStore.data.map { prefs ->
        prefs[disabledKey] ?: emptySet()
    }

    val disabledBoardIds: Flow<Set<String>> = context.homePrefsDataStore.data.map { prefs ->
        prefs[disabledBoardsKey] ?: emptySet()
    }

    /** 홈에 표시할 커뮤니티 (활성 사이트 + 활성 게시판만) */
    val visibleCommunities: Flow<List<Community>> = combine(
        communityOrder,
        disabledCommunityIds,
        disabledBoardIds
    ) { order, disabledCommunities, disabledBoards ->
        val byId = CommunityCatalog.communities.associateBy { it.id }
        order.mapNotNull { id ->
            if (id in disabledCommunities) return@mapNotNull null
            val community = byId[id] ?: return@mapNotNull null
            val enabledBoards = community.boards.filter { it.id !in disabledBoards }
            if (enabledBoards.isEmpty()) return@mapNotNull null
            community.copy(boards = enabledBoards)
        }
    }

    /** 설정 화면용: 전체 사이트 목록 + 활성 여부 */
    val managedCommunities: Flow<List<ManagedCommunity>> = combine(
        communityOrder,
        disabledCommunityIds,
        disabledBoardIds
    ) { order, disabledCommunities, disabledBoards ->
        val byId = CommunityCatalog.communities.associateBy { it.id }
        order.mapNotNull { id ->
            val community = byId[id] ?: return@mapNotNull null
            val enabledBoardCount = community.boards.count { it.id !in disabledBoards }
            ManagedCommunity(
                community = community,
                enabled = id !in disabledCommunities,
                enabledBoardCount = enabledBoardCount,
                totalBoardCount = community.boards.size
            )
        }
    }

    /** 특정 사이트의 게시판 On/Off 목록 */
    fun managedBoards(communityId: String): Flow<List<ManagedBoard>> =
        disabledBoardIds.map { disabledBoards ->
            val community = CommunityCatalog.findCommunity(communityId) ?: return@map emptyList()
            community.boards.map { board ->
                ManagedBoard(board = board, enabled = board.id !in disabledBoards)
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
            current.retainAll(catalogIds.toSet())
            prefs[disabledKey] = current
        }
    }

    suspend fun setBoardEnabled(boardId: String, enabled: Boolean) {
        context.homePrefsDataStore.edit { prefs ->
            val current = prefs[disabledBoardsKey]?.toMutableSet() ?: mutableSetOf()
            if (enabled) current.remove(boardId) else current.add(boardId)
            current.retainAll(catalogBoardIds)
            prefs[disabledBoardsKey] = current
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
    val enabled: Boolean,
    val enabledBoardCount: Int = community.boards.size,
    val totalBoardCount: Int = community.boards.size
)

data class ManagedBoard(
    val board: BoardInfo,
    val enabled: Boolean
)
