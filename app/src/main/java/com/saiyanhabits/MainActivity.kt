package com.saiyanhabits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.saiyanhabits.ui.SaiyanHabitsRoot
import com.saiyanhabits.ui.theme.SaiyanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark, so the system bar icons are always light.
        val transparentDark = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = transparentDark, navigationBarStyle = transparentDark)
        super.onCreate(savedInstanceState)
        setContent {
            SaiyanTheme {
                SaiyanHabitsRoot()
            }
        }
    }
}
