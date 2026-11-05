package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.GradingStudioScreen
import com.example.ui.GradingViewModel
import com.example.ui.theme.ResolveStudioTheme

class MainActivity : ComponentActivity() {
    private val gradingViewModel: GradingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ResolveStudioTheme {
                GradingStudioScreen(viewModel = gradingViewModel)
            }
        }
    }
}
