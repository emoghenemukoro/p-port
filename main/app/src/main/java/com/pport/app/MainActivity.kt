package com.pport.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pport.app.auth.EmailAuthScreen
import com.pport.app.auth.Session
import com.pport.app.config.AppConfig
import com.pport.app.ui.CustomerHomeScreen
import com.pport.app.ui.MerchantHomeScreen
import com.pport.app.ui.MerchantRegistrationScreen
import com.pport.app.ui.theme.PPortTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PPortTheme {
                AppRoot()
            }
        }
    }
}

private enum class Route {
    AUTH,
    LOADING,
    CUSTOMER,
    MERCHANT
}

@Composable
private fun AppRoot() {

    var route by remember { mutableStateOf(Route.AUTH) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var needsRegistration by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun logout() {
        Session.clear()
        route = Route.AUTH
        needsRegistration = false
    }

    fun loadProfile() {
        scope.launch {
            loading = true
            error = null
            route = Route.LOADING
            needsRegistration = false

            try {
                val profile = fetchProfile()

                Session.role = profile.role
                Session.countryIso2 = profile.countryIso2

                if (profile.role == "merchant") {
                    // Check if POS machine is registered
                    val posId = fetchPosMachineId()
                    needsRegistration = posId.isNullOrBlank()
                    route = Route.MERCHANT
                } else {
                    route = Route.CUSTOMER
                }

            } catch (e: Exception) {
                error = e.message
                route = Route.AUTH
            } finally {
                loading = false
            }
        }
    }

    Scaffold { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {

            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            when (route) {

                Route.AUTH -> {
                    EmailAuthScreen(
                        onAuthSuccess = {
                            loadProfile()
                        }
                    )
                }

                Route.LOADING -> {
                    Text("Loading profile...")
                }

                Route.CUSTOMER -> {
                    CustomerHomeScreen(
                        onLogout = { logout() }
                    )
                }

                Route.MERCHANT -> {
                    if (needsRegistration) {
                        MerchantRegistrationScreen(
                            onComplete = {
                                needsRegistration = false
                                // Trigger re-fetch to update UI (just force a small state change)
                                route = Route.MERCHANT
                            }
                        )
                    } else {
                        MerchantHomeScreen(
                            onLogout = { logout() }
                        )
                    }
                }
            }
        }
    }
}

private data class Profile(
    val role: String,
    val countryIso2: String
)

private suspend fun fetchProfile(): Profile = withContext(Dispatchers.IO) {

    val token = Session.accessToken ?: error("No token")
    val userId = Session.userId ?: error("No user")

    val encodedId = URLEncoder.encode(userId, "UTF-8")

    val url = URL(
        "${AppConfig.SUPABASE_URL}/rest/v1/profiles" +
                "?id=eq.$encodedId&select=role,country_iso2&limit=1"
    )

    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
        setRequestProperty("Authorization", "Bearer $token")
    }

    val code = conn.responseCode

    val text = if (code in 200..299) {
        conn.inputStream.bufferedReader().readText()
    } else {
        val err = conn.errorStream?.bufferedReader()?.readText()
        error(err ?: "Profile fetch failed")
    }

    val arr = JSONArray(text)
    if (arr.length() == 0) error("Profile not found")

    val obj = arr.getJSONObject(0)

    Profile(
        role = obj.getString("role"),
        countryIso2 = obj.getString("country_iso2")
    )
}

private suspend fun fetchPosMachineId(): String? = withContext(Dispatchers.IO) {
    val token = Session.accessToken ?: return@withContext null
    val userId = Session.userId ?: return@withContext null
    val encoded = URLEncoder.encode(userId, "UTF-8")
    val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.$encoded&select=pos_machine_id&limit=1")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
        setRequestProperty("Authorization", "Bearer $token")
    }
    val text = conn.inputStream.bufferedReader().readText()
    val arr = JSONArray(text)
    if (arr.length() == 0) null else arr.getJSONObject(0).optString("pos_machine_id", null)
}