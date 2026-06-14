package com.witt3d.autosteer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun FieldManagementScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Field Management",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
