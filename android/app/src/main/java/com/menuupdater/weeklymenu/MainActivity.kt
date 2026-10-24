package com.menuupdater.weeklymenu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.menuupdater.weeklymenu.ui.MenuApp
import com.menuupdater.weeklymenu.ui.MenuTheme
import com.menuupdater.weeklymenu.ui.MenuViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MenuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MenuTheme {
                MenuApp(viewModel)
            }
        }
    }

    /** Foreground: re-evaluate "today" and refresh if the cached menu is stale. */
    override fun onResume() {
        super.onResume()
        viewModel.didBecomeActive()
    }
}

/** Kept for the JVM smoke test; no longer shown in the UI. */
fun greeting(): String = "주간 메뉴 — Android 포트 준비 완료"
