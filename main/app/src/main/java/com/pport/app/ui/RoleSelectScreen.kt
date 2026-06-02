package com.pport.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectScreen(
    onRoleSelected: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Choose your role", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { onRoleSelected("customer") },
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Customer")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onRoleSelected("merchant") },
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Text("Merchant")
        }
    }
}
