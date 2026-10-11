package com.bbs.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbs.reader.BbsApp
import com.bbs.reader.data.repository.CommunityCatalog
import com.bbs.reader.ui.auth.SiteLoginScreen
import com.bbs.reader.ui.board.BoardScreen
import com.bbs.reader.ui.board.BoardViewModel
import com.bbs.reader.ui.detail.DetailScreen
import com.bbs.reader.ui.detail.DetailViewModel
import com.bbs.reader.ui.home.HomeScreen
import com.bbs.reader.ui.settings.BoardManageScreen
import com.bbs.reader.ui.settings.SettingsScreen
import com.bbs.reader.ui.settings.SettingsViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val BOARD_MANAGE = "board_manage"
    const val BOARD = "board/{boardId}"
    const val DETAIL = "detail/{mid}/{postId}"
    const val SITE_LOGIN = "site_login/{communityId}"

    fun board(boardId: String) = "board/$boardId"
    fun detail(mid: String, postId: String): String {
        val encodedMid = URLEncoder.encode(mid, StandardCharsets.UTF_8.toString())
        val encodedPostId = URLEncoder.encode(postId, StandardCharsets.UTF_8.toString())
        return "detail/$encodedMid/$encodedPostId"
    }

    fun siteLogin(communityId: String) = "site_login/$communityId"
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as BbsApp
    val repository = remember { app.boardRepository }
    val readHistory = remember { app.readHistoryStore }
    val settingsStore = remember { app.settingsStore }
    val boardPrefsStore = remember { app.boardPrefsStore }
    val homePrefsStore = remember { app.homePrefsStore }
    val siteSessionStore = remember { app.siteSessionStore }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                homePrefsStore = homePrefsStore,
                onCommunityClick = { community ->
                    val firstBoard = community.boards.firstOrNull()?.id
                        ?: CommunityCatalog.DDANZI_HOT
                    navController.navigate(Routes.board(firstBoard))
                },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onBoardManageClick = { navController.navigate(Routes.BOARD_MANAGE) }
            )
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(settingsStore, homePrefsStore)
            )
            SettingsScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.BOARD_MANAGE) {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(settingsStore, homePrefsStore)
            )
            BoardManageScreen(
                viewModel = vm,
                siteSessionStore = siteSessionStore,
                onBack = { navController.popBackStack() },
                onSiteLogin = { communityId ->
                    navController.navigate(Routes.siteLogin(communityId))
                }
            )
        }

        composable(
            route = Routes.BOARD,
            arguments = listOf(navArgument("boardId") { type = NavType.StringType })
        ) { entry ->
            val boardId = entry.arguments?.getString("boardId") ?: CommunityCatalog.DDANZI_HOT
            val vm: BoardViewModel = viewModel(
                key = boardId,
                factory = BoardViewModel.factory(boardId, repository, readHistory)
            )
            val loginOk by entry.savedStateHandle
                .getStateFlow("site_login_ok", false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(loginOk) {
                if (loginOk) {
                    entry.savedStateHandle["site_login_ok"] = false
                    vm.refresh()
                }
            }
            BoardScreen(
                viewModel = vm,
                boardId = boardId,
                settingsStore = settingsStore,
                boardPrefsStore = boardPrefsStore,
                onBack = { navController.popBackStack() },
                onBoardChange = { newBoardId ->
                    navController.navigate(Routes.board(newBoardId)) {
                        popUpTo(Routes.board(boardId)) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onPostClick = { post ->
                    navController.navigate(Routes.detail(post.mid, post.id))
                },
                onLoginClick = { communityId ->
                    navController.navigate(Routes.siteLogin(communityId))
                }
            )
        }

        composable(
            route = Routes.DETAIL,
            arguments = listOf(
                navArgument("mid") { type = NavType.StringType },
                navArgument("postId") { type = NavType.StringType }
            )
        ) { entry ->
            val mid = URLDecoder.decode(
                entry.arguments?.getString("mid").orEmpty(),
                StandardCharsets.UTF_8.toString()
            )
            val postId = URLDecoder.decode(
                entry.arguments?.getString("postId").orEmpty(),
                StandardCharsets.UTF_8.toString()
            )
            val vm: DetailViewModel = viewModel(
                key = "$mid-$postId",
                factory = DetailViewModel.factory(mid, postId, repository, readHistory)
            )
            val loginOk by entry.savedStateHandle
                .getStateFlow("site_login_ok", false)
                .collectAsStateWithLifecycle()
            LaunchedEffect(loginOk) {
                if (loginOk) {
                    entry.savedStateHandle["site_login_ok"] = false
                    vm.load()
                }
            }
            DetailScreen(
                viewModel = vm,
                settingsStore = settingsStore,
                onBack = { navController.popBackStack() },
                onLoginClick = { communityId ->
                    navController.navigate(Routes.siteLogin(communityId))
                }
            )
        }

        composable(
            route = Routes.SITE_LOGIN,
            arguments = listOf(navArgument("communityId") { type = NavType.StringType })
        ) { entry ->
            val communityId = entry.arguments?.getString("communityId").orEmpty()
            SiteLoginScreen(
                communityId = communityId,
                siteSessionStore = siteSessionStore,
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("site_login_ok", true)
                    navController.popBackStack()
                }
            )
        }
    }
}
