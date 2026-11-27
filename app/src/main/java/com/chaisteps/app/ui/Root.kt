package com.chaisteps.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.DonutLarge
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import com.chaisteps.app.ui.theme.Accents
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chaisteps.app.ui.screens.ChaiScreen
import com.chaisteps.app.ui.screens.OnboardingScreen
import com.chaisteps.app.ui.screens.SettingsScreen
import com.chaisteps.app.ui.screens.StatsScreen
import com.chaisteps.app.ui.screens.TodayScreen
import com.chaisteps.app.ui.screens.WalkDetailScreen
import com.chaisteps.app.ui.screens.WalkScreen
import com.chaisteps.app.ui.screens.WeightScreen

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.DonutLarge),
    WALK("walk", "Walk", Icons.AutoMirrored.Filled.DirectionsWalk),
    WEIGHT("weight", "Weight", Icons.Filled.MonitorWeight),
    CHAI("chai", "Chai", Icons.Filled.EmojiFoodBeverage),
    STATS("stats", "Stats", Icons.Filled.BarChart),
}

/** Navigation actions shared by the screens. */
class Nav(private val controller: NavHostController) {
    fun tab(tab: Tab) = controller.navigate(tab.route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    fun settings() = controller.navigate("settings") { launchSingleTop = true }
    fun walkDetail(id: String) = controller.navigate("walk/$id")
    fun back() = controller.popBackStack()
}

private fun tabColor(tab: Tab) = when (tab) {
    Tab.TODAY -> Accents.steps
    Tab.WALK -> Accents.walk
    Tab.WEIGHT -> Accents.weight
    Tab.CHAI -> Accents.chai
    Tab.STATS -> Accents.walk
}

@Composable
fun ChaiStepsRoot(vm: MainViewModel, startTab: Tab = Tab.TODAY) {
    val data by vm.data.collectAsStateWithLifecycle()
    if (!data.profile.onboarded) {
        OnboardingScreen(vm)
        return
    }
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val snackbar = remember { SnackbarHostState() }
    val message by vm.message.collectAsStateWithLifecycle()
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }
    val backStack by controller.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val walking by vm.activeWalk.collectAsStateWithLifecycle()
    // While walking, the live map takes the whole screen.
    val showBar = (Tab.entries.any { it.route == route } || route == null) &&
        !(walking != null && (route == Tab.WALK.route || (route == null && startTab == Tab.WALK)))

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route || (route == null && tab == startTab),
                            onClick = { nav.tab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = tabColor(tab),
                                selectedTextColor = tabColor(tab),
                                indicatorColor = tabColor(tab).copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = startTab.route, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Tab.TODAY.route) { TodayScreen(vm, nav) }
            composable(Tab.WALK.route) { WalkScreen(vm, nav) }
            composable(Tab.WEIGHT.route) { WeightScreen(vm, nav) }
            composable(Tab.CHAI.route) { ChaiScreen(vm, nav) }
            composable(Tab.STATS.route) { StatsScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("walk/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                WalkDetailScreen(vm, nav, entry.arguments?.getString("id").orEmpty())
            }
        }
    }
}
