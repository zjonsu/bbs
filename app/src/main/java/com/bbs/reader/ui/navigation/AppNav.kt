package com.bbs.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bbs.reader.BbsApp
import com.bbs.reader.data.repository.CommunityCatalog
import com.bbs.reader.ui.board.BoardScreen
import com.bbs.reader.ui.board.BoardViewModel
import com.bbs.reader.ui.detail.DetailScreen
import com.bbs.reader.ui.detail.DetailViewModel
import com.bbs.reader.ui.home.HomeScreen
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object Routes {
    const val HOME = "home"
    const val BOARD = "board/{boardId}"
    const val DETAIL = "detail/{mid}/{postId}"

    fun board(boardId: String) = "board/$boardId"
    fun detail(mid: String, postId: String): String {
        val encodedMid = URLEncoder.encode(mid, StandardCharsets.UTF_8.toString())
        val encodedPostId = URLEncoder.encode(postId, StandardCharsets.UTF_8.toString())
        return "detail/$encodedMid/$encodedPostId"
    }
}

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as BbsApp
    val repository = remember { app.boardRepository }
    val readHistory = remember { app.readHistoryStore }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onCommunityClick = { community ->
                    val firstBoard = community.boards.firstOrNull()?.id
                        ?: CommunityCatalog.DDANZI_HOT
                    navController.navigate(Routes.board(firstBoard))
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
            BoardScreen(
                viewModel = vm,
                boardId = boardId,
                onBack = { navController.popBackStack() },
                onBoardChange = { newBoardId ->
                    navController.navigate(Routes.board(newBoardId)) {
                        popUpTo(Routes.board(boardId)) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onPostClick = { post ->
                    navController.navigate(Routes.detail(post.mid, post.id))
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
            DetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
