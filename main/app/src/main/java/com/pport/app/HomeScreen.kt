    package com.pport.app.ui

    import androidx.compose.foundation.layout.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.unit.dp
    import com.pport.app.auth.Session

    @Composable
    fun HomeScreen(
        onLogout: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Role: ${Session.role ?: "unknown"}")
            Text("Country: ${Session.countryIso2 ?: "unknown"}")

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onLogout) {
                Text("Logout")
            }
        }
    }
