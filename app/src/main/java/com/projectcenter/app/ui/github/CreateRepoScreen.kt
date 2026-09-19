package com.projectcenter.app.ui.github

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.projectcenter.app.domain.models.CreateRepoRequest

@Composable
fun CreateRepoScreen(
    onCreate: (CreateRepoRequest) -> Unit,
    onBack: () -> Unit,
    isCreating: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    var initReadme by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Create repository",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Repository name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description (optional)") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Spacer(modifier = Modifier.height(16.dp))

        Text("Visibility", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = !isPrivate, onClick = { isPrivate = false }, role = Role.RadioButton)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = !isPrivate, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text("Public")
        }
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = isPrivate, onClick = { isPrivate = true }, role = Role.RadioButton)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isPrivate, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text("Private")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(selected = initReadme, onClick = { initReadme = !initReadme }, role = Role.RadioButton)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = initReadme, onClick = null)
            Spacer(Modifier.width(8.dp))
            Text("Initialize with README")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    onCreate(
                        CreateRepoRequest(
                            name = name.trim(),
                            description = description.trim().ifBlank { null },
                            private = isPrivate,
                            autoInit = initReadme
                        )
                    )
                }
            },
            enabled = name.isNotBlank() && !isCreating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isCreating) "Creating…" else "Create repository")
        }
    }
}
