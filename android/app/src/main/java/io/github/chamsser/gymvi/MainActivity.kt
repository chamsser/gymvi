package io.github.chamsser.gymvi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.chamsser.gymvi.ui.GymviRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = 0xFFF5F5F5.toInt(),
                darkScrim = 0xFF181818.toInt(),
            ),
        )
        setContent {
            GymviRoute()
        }
    }
}
