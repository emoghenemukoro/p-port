package com.pport.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun PportTopBar(
    onLogout: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // TODO: Replace with your own logo resource (R.drawable.pport_logo)
        // and set tint = Color.Unspecified if the logo has its own colors
        Icon(
            painter = painterResource(id = android.R.drawable.ic_menu_gallery),
            contentDescription = "P‑Port logo",
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        if (onLogout != null) {
            TextButton(onClick = onLogout) {
                Text("Logout")
            }
        }
    }
}