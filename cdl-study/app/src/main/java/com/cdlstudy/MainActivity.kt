package com.cdlstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cdlstudy.ui.AiScreen
import com.cdlstudy.ui.BackupScreen
import com.cdlstudy.ui.CardsScreen
import com.cdlstudy.ui.CdlTheme
import com.cdlstudy.ui.DrillsScreen
import com.cdlstudy.ui.InspectionWalkScreen
import com.cdlstudy.ui.ProcedureListScreen
import com.cdlstudy.ui.ProcedureScreen
import com.cdlstudy.ui.ExamScreen
import com.cdlstudy.ui.Gold
import com.cdlstudy.ui.ManualScreen
import com.cdlstudy.ui.NumberDrillScreen
import com.cdlstudy.ui.PlanScreen
import com.cdlstudy.ui.ProgressScreen
import com.cdlstudy.ui.ReadingScreen
import com.cdlstudy.ui.ReminderScreen
import com.cdlstudy.ui.SectionScreen
import com.cdlstudy.ui.SettingsScreen
import com.cdlstudy.ui.StudyScreen
import com.cdlstudy.ui.TestDayScreen
import com.cdlstudy.ui.TodayScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CdlTheme { AppNav() }
        }
    }
}

/** The four places the bottom bar goes. */
private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Home),
    PROGRESS("progress", "Progress", Icons.Filled.CheckCircle),
    CARDS("cards", "Cards", Icons.Filled.List),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = Tab.entries.any { it.route == route }

    Scaffold(
        bottomBar = { if (onTab) BottomBar(nav, route) },
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(pad),
        ) {
            composable(Tab.TODAY.route) {
                TodayScreen(
                    onSection = { nav.navigate("section/$it") },
                    onStudy = { nav.navigate("study/$it") },
                    onExams = { nav.navigate("exams") },
                    onCards = { nav.switchTab(Tab.CARDS) },
                    onProgress = { nav.switchTab(Tab.PROGRESS) },
                    onManual = { nav.navigate("manual") },
                    onDrills = { nav.navigate("drills") },
                )
            }
            composable(Tab.PROGRESS.route) { ProgressScreen(onBack = null) }
            composable(Tab.CARDS.route) { CardsScreen(onBack = null) }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    onReading = { nav.navigate("reading") },
                    onManual = { nav.navigate("manual") },
                    onPlan = { nav.navigate("plan") },
                    onBackup = { nav.navigate("backup") },
                    onReminder = { nav.navigate("reminder") },
                    onAi = { nav.navigate("ai") },
                    onTestDay = { nav.navigate("testday") },
                )
            }

            composable(
                "section/{number}",
                arguments = listOf(navArgument("number") { type = NavType.IntType }),
            ) { backStack ->
                SectionScreen(
                    sectionNumber = backStack.arguments!!.getInt("number"),
                    onSubsection = { nav.navigate("study/$it") },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(
                "study/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStack ->
                StudyScreen(
                    subsectionId = backStack.arguments!!.getString("id")!!,
                    onDone = { nav.popBackStack() },
                    onCards = { nav.switchTab(Tab.CARDS) },
                    onManual = { nav.navigate("manual") },
                )
            }
            composable("exams") { ExamScreen(onBack = { nav.popBackStack() }) }
            composable("manual") { ManualScreen(onBack = { nav.popBackStack() }) }
            composable("plan") { PlanScreen(onBack = { nav.popBackStack() }) }
            composable("reading") { ReadingScreen(onBack = { nav.popBackStack() }) }
        composable("backup") { BackupScreen(onBack = { nav.popBackStack() }) }
        composable("reminder") { ReminderScreen(onBack = { nav.popBackStack() }) }
        composable("ai") { AiScreen(onBack = { nav.popBackStack() }) }
        composable("testday") { TestDayScreen(onBack = { nav.popBackStack() }) }
        composable("drills") {
            DrillsScreen(
                onBack = { nav.popBackStack() },
                onNumbers = { nav.navigate("drill-numbers") },
                onProcedures = { nav.navigate("drill-order") },
                onWalk = { nav.navigate("drill-walk") },
            )
        }
        composable("drill-numbers") {
            NumberDrillScreen(
                onBack = { nav.popBackStack() },
                onCards = { nav.switchTab(Tab.CARDS) },
            )
        }
        composable("drill-order") {
            ProcedureListScreen(
                onBack = { nav.popBackStack() },
                onProcedure = { nav.navigate("procedure/$it") },
            )
        }
        composable(
            "procedure/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStack ->
            ProcedureScreen(
                procedureId = backStack.arguments!!.getString("id")!!,
                onBack = { nav.popBackStack() },
                onStudy = { nav.navigate("study/$it") },
            )
        }
        composable("drill-walk") {
            InspectionWalkScreen(
                onBack = { nav.popBackStack() },
                onArea = { nav.navigate("study/$it") },
            )
        }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = { nav.switchTab(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Gold,
                    selectedTextColor = Gold,
                ),
            )
        }
    }
}

/** Moves between the four tabs without piling them on the back stack. */
private fun NavHostController.switchTab(tab: Tab) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
