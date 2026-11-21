package app.boomdoom.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import app.boomdoom.feature.home.HomeScreen
import app.boomdoom.feature.insights.InsightsScreen
import app.boomdoom.feature.learn.LearnScreen
import app.boomdoom.feature.onboarding.OnboardingScreen
import app.boomdoom.feature.settings.SettingsScreen

/** Tech Spec §3.3: top-level home · learn · insights · you; full-screen onboarding/player/settings. */
private val tabs = listOf("home" to "Home", "learn" to "Learn", "insights" to "Insights", "you" to "You")

@Composable
fun BoomDoomNavHost() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    Scaffold(
        bottomBar = {
            if (tabs.any { it.first == route }) {
                NavigationBar {
                    tabs.forEach { (r, label) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                nav.navigate(r) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {},
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen() }
            composable("learn") { LearnScreen() }
            composable("insights") { InsightsScreen() }
            composable("you") { SettingsScreen() }
            composable("onboarding") { OnboardingScreen() }
            composable("player/{lessonId}", deepLinks = listOf(navDeepLink { uriPattern = "boomdoom://player/{lessonId}" })) {
                Text("Player: ${it.arguments?.getString("lessonId")}") // TODO(B): lesson player (§7.2)
            }
        }
    }
}
