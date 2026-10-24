package com.menuupdater.weeklymenu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SmokeScreen()
                }
            }
        }
    }
}

/** Pure function so a JVM unit test can assert on it without Android. */
fun greeting(): String = "주간 메뉴 — Android 포트 준비 완료"

@Composable
fun SmokeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = greeting())
    }
}

@Preview(showBackground = true)
@Composable
fun SmokeScreenPreview() {
    MaterialTheme {
        SmokeScreen()
    }
}
