package com.chaisteps.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.chaisteps.app.ui.ChaiStepsRoot
import com.chaisteps.app.ui.MainViewModel
import com.chaisteps.app.ui.theme.ChaiStepsTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        viewModel.recoverInterruptedWalk()
        setContent {
            ChaiStepsTheme {
                ChaiStepsRoot(viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as ChaiStepsApp).stepMonitor.acquire()
        viewModel.refreshDay()
    }

    override fun onStop() {
        (application as ChaiStepsApp).stepMonitor.release()
        super.onStop()
    }
}
