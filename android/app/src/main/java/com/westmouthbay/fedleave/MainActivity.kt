package com.westmouthbay.fedleave

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.westmouthbay.fedleave.core.AppDependencies
import com.westmouthbay.fedleave.ui.FedLeaveApp
import com.westmouthbay.fedleave.ui.theme.FedLeaveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Python.start runs on the main thread inside create(); I05-02 owns the wiring.
        val dependencies = AppDependencies.create(applicationContext)
        setContent {
            FedLeaveTheme {
                FedLeaveApp(dependencies)
            }
        }
    }
}
