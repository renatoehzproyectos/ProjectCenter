package com.projectcenter.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.projectcenter.app.data.storage.ThemePreferences
import com.projectcenter.app.ui.ProjectCenterApp
import com.projectcenter.app.ui.auth.AuthViewModel
import com.projectcenter.app.ui.theme.AppThemeOption
import com.projectcenter.app.ui.theme.ProjectCenterTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        setContent {
            val context = LocalContext.current
            val themePrefs = remember { ThemePreferences(context) }
            val themeOption by themePrefs.themeFlow.collectAsState(initial = AppThemeOption.VERCEL_DARK)

            ProjectCenterTheme(themeOption = themeOption) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProjectCenterApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "projectcenter" && data.host == "oauth") {
            authViewModel.handleOAuthCallback(data)
        }
    }
}
