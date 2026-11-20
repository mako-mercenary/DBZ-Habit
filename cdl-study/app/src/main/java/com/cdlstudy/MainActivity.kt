package com.cdlstudy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.cdlstudy.ui.CardsScreen
import com.cdlstudy.ui.CdlTheme
import com.cdlstudy.ui.ExamScreen
import com.cdlstudy.ui.HomeScreen
import com.cdlstudy.ui.ManualScreen
import com.cdlstudy.ui.PlanScreen
import com.cdlstudy.ui.ProgressScreen
import com.cdlstudy.ui.SectionScreen
import com.cdlstudy.ui.StudyScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CdlTheme { AppNav() }
        }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onSection = { nav.navigate("section/$it") },
                onCards = { nav.navigate("cards") },
                onExams = { nav.navigate("exams") },
                onProgress = { nav.navigate("progress") },
                onManual = { nav.navigate("manual") },
                onPlan = { nav.navigate("plan") },
            )
        }
        composable(
            "section/{number}",
            arguments = listOf(navArgument("number") { type = NavType.IntType }),
        ) { entry ->
            SectionScreen(
                sectionNumber = entry.arguments!!.getInt("number"),
                onSubsection = { nav.navigate("study/$it") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            "study/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            StudyScreen(
                subsectionId = entry.arguments!!.getString("id")!!,
                onDone = { nav.popBackStack() },
                onCards = { nav.navigate("cards") },
                onManual = { nav.navigate("manual") },
            )
        }
        composable("exams") { ExamScreen(onBack = { nav.popBackStack() }) }
        composable("manual") { ManualScreen(onBack = { nav.popBackStack() }) }
        composable("plan") { PlanScreen(onBack = { nav.popBackStack() }) }
        composable("cards") { CardsScreen(onBack = { nav.popBackStack() }) }
        composable("progress") { ProgressScreen(onBack = { nav.popBackStack() }) }
    }
}
