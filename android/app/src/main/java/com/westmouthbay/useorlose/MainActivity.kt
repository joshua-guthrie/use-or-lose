package com.westmouthbay.useorlose

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.westmouthbay.useorlose.core.AppDependencies
import com.westmouthbay.useorlose.ui.UseOrLoseApp
import com.westmouthbay.useorlose.ui.theme.UseOrLoseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Python.start runs on the main thread inside create(); I05-02 owns the wiring.
        val dependencies = AppDependencies.create(applicationContext)
        setContent {
            UseOrLoseTheme {
                UseOrLoseApp(dependencies)
            }
        }
    }
}
