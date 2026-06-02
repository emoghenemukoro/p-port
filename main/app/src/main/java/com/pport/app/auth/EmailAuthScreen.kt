package com.pport.app.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pport.app.config.AppConfig
import com.pport.app.ui.components.PportTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailAuthScreen(onAuthSuccess: () -> Unit) {

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf("customer") }
    var country by remember { mutableStateOf("NG") }   // default Nigeria

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // Top bar without logout
        PportTopBar(onLogout = null)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(Color(0xFF121212), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                Text(
                    text = if (isSignUp) "Create Account" else "Welcome Back",
                    style = MaterialTheme.typography.headlineSmall
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1F1F1F),
                        unfocusedContainerColor = Color(0xFF1F1F1F)
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1F1F1F),
                        unfocusedContainerColor = Color(0xFF1F1F1F)
                    )
                )

                if (isSignUp) {
                    // Role selection
                    Row {
                        RadioButton(
                            selected = role == "customer",
                            onClick = { role = "customer" }
                        )
                        Text("Customer")
                        Spacer(Modifier.width(12.dp))
                        RadioButton(
                            selected = role == "merchant",
                            onClick = { role = "merchant" }
                        )
                        Text("Merchant")
                    }

                    // Country dropdown
                    var expanded by remember { mutableStateOf(false) }
                    val countries = listOf("NG" to "Nigeria", "GH" to "Ghana", "KE" to "Kenya", "CI" to "Ivory Coast", "SN" to "Senegal", "CM" to "Cameroon")
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = countries.first { it.first == country }.second,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Country") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1F1F1F),
                                unfocusedContainerColor = Color(0xFF1F1F1F)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            countries.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        country = code
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                authenticate(email, password, role, country, isSignUp)
                                onAuthSuccess()
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                loading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Loading..." else if (isSignUp) "Sign Up" else "Sign In")
                }

                TextButton(onClick = { isSignUp = !isSignUp }) {
                    Text(if (isSignUp) "Already have an account?" else "Create account")
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private suspend fun authenticate(
    email: String,
    password: String,
    role: String,
    country: String,
    isSignUp: Boolean
) = withContext(Dispatchers.IO) {

    val endpoint = if (isSignUp) "signup" else "token?grant_type=password"
    val url = URL("${AppConfig.SUPABASE_URL}/auth/v1/$endpoint")

    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
        setRequestProperty("Content-Type", "application/json")
        doOutput = true
    }

    val body = if (isSignUp) {
        JSONObject()
            .put("email", email)
            .put("password", password)
            .put("data", JSONObject()
                .put("role", role)
                .put("country_iso2", country)
            )
    } else {
        JSONObject()
            .put("email", email)
            .put("password", password)
    }

    conn.outputStream.use { it.write(body.toString().toByteArray()) }

    val text = conn.inputStream.bufferedReader().readText()
    val json = JSONObject(text)

    Session.accessToken = json.getString("access_token")
    Session.userId = json.getJSONObject("user").getString("id")
    Session.isLoggedIn = true
}