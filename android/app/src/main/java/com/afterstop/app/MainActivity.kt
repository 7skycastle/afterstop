package com.afterstop.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterstop.app.ui.AfterStopTheme
import com.afterstop.app.ui.AppRoot
import com.afterstop.app.ui.AppViewModel
import com.afterstop.app.ui.Screen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = vm.onBackground()
            override fun onStart(owner: LifecycleOwner) = vm.onForeground()
        })
        setContent {
            val s by vm.ui.collectAsStateWithLifecycle()
            AfterStopTheme(night = s.night) {
                BackHandler(enabled = s.screen != Screen.HOME) { vm.go(Screen.HOME) }
                LaunchedEffect(s.toast) {
                    s.toast?.let { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show(); vm.consumeToast() }
                }
                AppRoot(vm, s)
            }
        }
    }
}
