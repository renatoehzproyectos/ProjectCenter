package com.projectcenter.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectcenter.app.data.storage.ThemePreferences
import com.projectcenter.app.ui.auth.AuthViewModel
import com.projectcenter.app.ui.theme.AppThemeOption
import com.projectcenter.app.ui.theme.ForestPrimary
import com.projectcenter.app.ui.theme.GeistBg
import com.projectcenter.app.ui.theme.GeistLightBg
import com.projectcenter.app.ui.theme.GeistLightPrimary
import com.projectcenter.app.ui.theme.GeistPrimary
import com.projectcenter.app.ui.theme.MidnightPrimary
import com.projectcenter.app.ui.theme.OceanPrimary
import com.projectcenter.app.ui.theme.RosePrimary
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel = viewModel()
) {
    val auth by authViewModel.state.collectAsState()
    val context = LocalContext.current
    val themePrefs = remember { ThemePreferences(context) }
    val currentTheme by themePrefs.themeFlow.collectAsState(initial = AppThemeOption.VERCEL_DARK)
    val scope = rememberCoroutineScope()
    var pat by remember { mutableStateOf("") }
    var showPat by remember { mutableStateOf(false) }

    val shape = RoundedCornerShape(8.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.outline,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(28.dp))
        SectionLabel("ACCOUNT")
        Spacer(modifier = Modifier.height(12.dp))

        if (auth.isLoggedIn && auth.user != null) {
            Text(
                text = "@${auth.user!!.login}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Connected to GitHub",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { authViewModel.logout() },
                modifier = Modifier.fillMaxWidth(),
                shape = shape
            ) {
                Text("Sign out")
            }
        } else {
            Text(
                text = "Sign in to push projects and manage Actions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            // OAuth — opens browser where passkeys / saved accounts work
            Button(
                onClick = { authViewModel.startLogin() },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                enabled = !auth.isLoading,
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
            ) {
                Text(
                    if (auth.isLoading) "Opening GitHub…" else "Continue with GitHub",
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Opens GitHub in a secure browser tab. Use a saved account or passkey there — Android cannot list GitHub accounts via Google Credential Manager.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Or use a Personal Access Token",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (!showPat) {
                TextButton(onClick = { showPat = true }) {
                    Text("Enter token instead")
                }
            } else {
                OutlinedTextField(
                    value = pat,
                    onValueChange = { pat = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("ghp_… or github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = shape,
                    colors = fieldColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { authViewModel.signInWithToken(pat) },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = pat.isNotBlank() && !auth.isLoading,
                    shape = shape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp)
                ) {
                    Text("Sign in with token", fontWeight = FontWeight.Medium)
                }
                TextButton(onClick = {
                    authViewModel.openGitHubLogin()
                }) {
                    Text("Create token on GitHub")
                }
            }

            auth.info?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            auth.error?.let {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("WEB SESSIONS")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Open provider login pages to use passkeys or switch accounts. Sessions stay in the browser.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = { authViewModel.openGitHubLogin() },
            modifier = Modifier.fillMaxWidth(),
            shape = shape
        ) { Text("GitHub login (passkeys)") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(
            onClick = { authViewModel.openVercelLogin() },
            modifier = Modifier.fillMaxWidth(),
            shape = shape
        ) { Text("Vercel login (passkeys)") }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(24.dp))

        SectionLabel("APPEARANCE")
        Spacer(modifier = Modifier.height(12.dp))
        AppThemeOption.entries.forEach { option ->
            ThemeOptionRow(
                option = option,
                selected = currentTheme == option,
                onClick = { scope.launch { themePrefs.setTheme(option) } }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(24.dp))
        SectionLabel("ABOUT")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Project Center", style = MaterialTheme.typography.titleMedium)
        Text(
            "v1.0.0 · AI agent ZIP → GitHub → APK",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ThemeOptionRow(
    option: AppThemeOption,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = shape
            )
            .background(
                if (selected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemeSwatch(option)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(option.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(option.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ThemeSwatch(option: AppThemeOption) {
    val (bg, fg) = when (option) {
        AppThemeOption.VERCEL_DARK -> GeistBg to GeistPrimary
        AppThemeOption.VERCEL_LIGHT -> GeistLightBg to GeistLightPrimary
        AppThemeOption.MIDNIGHT -> Color(0xFF0B0F19) to MidnightPrimary
        AppThemeOption.OCEAN -> Color(0xFF0C1222) to OceanPrimary
        AppThemeOption.FOREST -> Color(0xFF0A120E) to ForestPrimary
        AppThemeOption.ROSE -> Color(0xFF140A0E) to RosePrimary
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(fg))
    }
}
