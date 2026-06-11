package com.allaway.xwd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.allaway.xwd.data.CatalogRefreshWorker
import com.allaway.xwd.ui.LibraryViewModel
import com.allaway.xwd.ui.SolveViewModel
import com.allaway.xwd.ui.StatsViewModel
import com.allaway.xwd.ui.screens.LibraryScreen
import com.allaway.xwd.ui.screens.SolveScreen
import com.allaway.xwd.ui.screens.StatsScreen
import com.allaway.xwd.ui.theme.XwdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the catalog of downloadable puzzles fresh twice a day.
        CatalogRefreshWorker.schedule(applicationContext)
        setContent {
            XwdTheme {
                XwdApp()
            }
        }
    }
}

@Composable
fun XwdApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            val vm: LibraryViewModel = viewModel()
            LibraryScreen(
                viewModel = vm,
                onOpenPuzzle = { id -> navController.navigate("solve/$id") },
                onOpenStats = { navController.navigate("stats") },
            )
        }
        composable(
            route = "solve/{puzzleId}",
            arguments = listOf(navArgument("puzzleId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val puzzleId = backStackEntry.arguments?.getString("puzzleId") ?: return@composable
            val context = LocalContext.current.applicationContext as android.app.Application
            val vm: SolveViewModel = viewModel(
                key = puzzleId,
                factory = SolveViewModel.Factory(context, puzzleId),
            )
            SolveScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
        composable("stats") {
            val vm: StatsViewModel = viewModel()
            StatsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}
