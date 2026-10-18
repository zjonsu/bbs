package com.bbs.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bbs.reader.data.local.ManagedCommunity
import com.bbs.reader.data.local.SiteSessionStore
import com.bbs.reader.data.model.SiteAuthInfo
import com.bbs.reader.data.repository.CommunityCatalog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardManageScreen(
    viewModel: SettingsViewModel,
    siteSessionStore: SiteSessionStore,
    onBack: () -> Unit,
    onCommunityClick: (communityId: String) -> Unit = {},
    onSiteLogin: (communityId: String) -> Unit = {}
) {
    val managedCommunities by viewModel.managedCommunities.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "게시판 설정",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = "사이트 이름을 누르면 게시판을 On/Off 할 수 있습니다.\n스위치로 사이트 전체를 숨기거나, 홈에서 길게 눌러 순서를 바꿀 수 있습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (managedCommunities.isEmpty()) {
                Text(
                    text = "게시판 목록을 불러오는 중…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                managedCommunities.forEach { item ->
                    CommunityManageRow(
                        item = item,
                        onClick = { onCommunityClick(item.community.id) },
                        onEnabledChange = { enabled ->
                            viewModel.setCommunityEnabled(item.community.id, enabled)
                        }
                    )
                }
            }

            if (CommunityCatalog.siteAuthList.isNotEmpty()) {
                Text(
                    text = "사이트 로그인",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 4.dp)
                )
                Text(
                    text = "성인게시판 등 로그인이 필요한 게시판을 보려면 사이트에 로그인하세요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CommunityCatalog.siteAuthList.forEach { auth ->
                    SiteAuthRow(
                        auth = auth,
                        siteSessionStore = siteSessionStore,
                        onLogin = { onSiteLogin(auth.communityId) },
                        onLogout = {
                            scope.launch { siteSessionStore.clear(auth.communityId) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CommunityManageRow(
    item: ManagedCommunity,
    onClick: () -> Unit,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.community.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "게시판 ${item.enabledBoardCount}/${item.totalBoardCount} 활성",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "게시판 관리",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp)
            )
        }
        Switch(
            checked = item.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}

@Composable
private fun SiteAuthRow(
    auth: SiteAuthInfo,
    siteSessionStore: SiteSessionStore,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val loggedIn by siteSessionStore.isLoggedIn(auth.communityId)
        .collectAsStateWithLifecycle(initialValue = false)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = auth.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (loggedIn) "로그인됨" else "로그인 필요",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (loggedIn) {
            TextButton(onClick = onLogout) {
                Text("로그아웃")
            }
        }
        TextButton(onClick = onLogin) {
            Text(if (loggedIn) "다시 로그인" else "로그인")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
}
