package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.example.ui.TareasApp
import com.example.widget.TareasWidgetProvider

class MainActivity : ComponentActivity() {
    private var newTaskRequestCounter by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.getBooleanExtra(TareasWidgetProvider.EXTRA_OPEN_NEW_TASK, false) == true) {
            newTaskRequestCounter++
        }
        setContent {
            TareasApp(openNewTaskTrigger = newTaskRequestCounter)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(TareasWidgetProvider.EXTRA_OPEN_NEW_TASK, false)) {
            newTaskRequestCounter++
        }
    }
}
