package app.xwd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.xwd.data.CatalogRefreshWorker
import app.xwd.data.Settings
import app.xwd.ui.LibraryViewModel
import app.xwd.ui.SettingsViewModel
import app.xwd.ui.SolveViewModel
import app.xwd.ui.StatsViewModel
import app.xwd.ui.screens.LibraryScreen
import app.xwd.ui.screens.SettingsScreen
import app.xwd.ui.screens.SolveScreen
import app.xwd.ui.screens.StatsScreen
import app.xwd.ui.theme.Skin
import app.xwd.ui.theme.XwdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep the catalog of downloadable puzzles fresh twice a day.
        CatalogRefreshWorker.schedule(applicationContext)
        setContent {
            var skin by remember {
                mutableStateOf(
                    Skin.entries.firstOrNull { it.name == Settings.getSkinName(this) }
                        ?: Skin.MARGINS,
                )
            }
            val view = LocalView.current
            SideEffect {
                // Terminal is the one dark skin; keep status bar icons legible.
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = skin != Skin.TERMINAL
            }
            XwdTheme(skin) {
                XwdApp(
                    skin = skin,
                    onSkinChange = {
                        skin = it
                        Settings.setSkinName(this, it.name)
                    },
                )
            }
        }
    }
}

@Composable
fun XwdApp(skin: Skin, onSkinChange: (Skin) -> Unit) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "library") {
        composable("library") {
            val vm: LibraryViewModel = viewModel()
            LibraryScreen(
                viewModel = vm,
                onOpenPuzzle = { id -> navController.navigate("solve/$id") },
                onOpenStats = { navController.navigate("stats") },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            val vm: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = vm,
                currentSkin = skin,
                onSkinChange = onSkinChange,
                onBack = { navController.popBackStack() },
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
