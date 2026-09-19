package com.projectcenter.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun OfflineBanner(visible: Boolean) {
    if (!visible) return
    Text(
        text = "You're offline. GitHub operations require an internet connection.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onError,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.error)
            .padding(12.dp)
    )
}
