package app.boomdoom

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import app.boomdoom.core.designsystem.BoomColors
import app.boomdoom.core.designsystem.BoomTypography
import app.boomdoom.feature.home.HomeScreen
import app.boomdoom.feature.insights.InsightsScreen
import app.boomdoom.feature.learn.LearnScreen
import app.boomdoom.feature.learn.LearnedScreen
import app.boomdoom.feature.learn.PlayerScreen
import app.boomdoom.feature.learn.TopicsScreen
import app.boomdoom.feature.onboarding.OnboardingScreen
import app.boomdoom.feature.settings.AboutScreen
import app.boomdoom.feature.settings.FlagsScreen
import app.boomdoom.feature.settings.GoalsScreen
import app.boomdoom.feature.settings.PermissionsScreen
import app.boomdoom.feature.settings.PrivacyScreen
import app.boomdoom.feature.settings.SettingsPage
import app.boomdoom.feature.settings.YouScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home), Tab("learn", "Learn", Icons.Filled.MenuBook),
    Tab("insights", "Insights", Icons.Filled.BarChart), Tab("you", "You", Icons.Filled.Person),
)

/** Tech Spec §3.3: four tabs; onboarding, player and settings pages are full screen (no bottom bar). */
@Composable
fun BoomDoomNavHost(startOnboarding: Boolean, onNav: (NavHostController) -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(nav) { onNav(nav) }
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val night = route == "home" || route == "onboarding"
    val view = LocalView.current
    // Status/navigation bar icons: light on night screens, dark on paper screens.
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }
    }

    fun openPlayer(id: String) = nav.navigate("player/$id")
    fun toTab(r: String) = nav.navigate(r) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun exitPlayer() { if (!nav.popBackStack()) nav.navigate("home") }

    Scaffold(
        containerColor = if (night) BoomColors.Background else BoomColors.Paper,
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar(containerColor = if (night) BoomColors.Card else BoomColors.PaperCard) {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route, onClick = { toTab(t.route) },
                            icon = { Icon(t.icon, null) }, label = { Text(t.label, style = BoomTypography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = if (night) BoomColors.Boom else BoomColors.LearnDeep,
                                selectedTextColor = if (night) BoomColors.Boom else BoomColors.LearnDeep,
                                indicatorColor = if (night) Color(0xFF3A221A) else BoomColors.LearnTint,
                                unselectedIconColor = if (night) BoomColors.TextPrimary else BoomColors.Ink,
                                unselectedTextColor = if (night) BoomColors.TextPrimary else BoomColors.Ink,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(nav, startDestination = if (startOnboarding) "onboarding" else "home") {
                composable("onboarding") {
                    OnboardingScreen(onPlayLesson = ::openPlayer, onFinished = {
                        nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                    })
                }
                composable("home") { HomeScreen(onOpenLesson = ::openPlayer, onOpenLearn = { toTab("learn") }) }
                composable("learn", deepLinks = listOf(navDeepLink { uriPattern = "boomdoom://learn" })) {
                    LearnScreen(onPlay = ::openPlayer, onOpenTopics = { nav.navigate("topics") }, onOpenLearned = { nav.navigate("learned") })
                }
                composable("insights") { InsightsScreen(onOpenLearn = { toTab("learn") }) }
                composable("you") {
                    YouScreen(onOpen = { nav.navigate("settings/${it.name}") }, onOpenTopics = { nav.navigate("topics") })
                }
                composable(
                    "player/{lessonId}?from={from}",
                    arguments = listOf(navArgument("lessonId") { type = NavType.StringType },
                        navArgument("from") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    deepLinks = listOf(navDeepLink { uriPattern = "boomdoom://player/{lessonId}?from={from}" }),
                ) {
                    PlayerScreen(
                        onExit = ::exitPlayer,
                        onAnotherLesson = { next -> nav.navigate("player/$next") { popUpTo("player/{lessonId}?from={from}") { inclusive = true } } },
                    )
                }
                composable("learned") { LearnedScreen(onBack = { nav.popBackStack() }, onRewatch = ::openPlayer) }
                composable("topics") { TopicsScreen(onBack = { nav.popBackStack() }) }
                composable("settings/{page}") { e ->
                    val back = { nav.popBackStack(); Unit }
                    when (SettingsPage.valueOf(e.arguments?.getString("page") ?: "GOALS")) {
                        SettingsPage.GOALS -> GoalsScreen(back)
                        SettingsPage.FLAGS -> FlagsScreen(back)
                        SettingsPage.PRIVACY -> PrivacyScreen(back, onAccountDeleted = {
                            nav.navigate("onboarding") { popUpTo(0) { inclusive = true } }
                        })
                        SettingsPage.PERMISSIONS -> PermissionsScreen(back)
                        SettingsPage.ABOUT -> AboutScreen(back, BuildConfig.VERSION_NAME)
                    }
                }
            }
        }
    }
}
