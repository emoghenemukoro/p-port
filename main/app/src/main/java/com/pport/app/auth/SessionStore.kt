package com.pport.app.auth

import android.content.Context
import com.pport.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

object SessionStore {

    suspend fun loadProfileFromSupabase(context: Context) {
        val userId = Session.userId ?: return
        val token = Session.accessToken ?: return

        withContext(Dispatchers.IO) {
            val url = URL(
                "${AppConfig.SUPABASE_URL}/rest/v1/profiles" +
                        "?id=eq.$userId" +
                        "&select=role,country_iso2,flutterwave_subaccount_id"
            )

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }

            val code = conn.responseCode
            val responseText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()
                ?.readText()
                ?: ""

            if (code !in 200..299) {
                throw Exception("Profile fetch failed: HTTP $code $responseText")
            }

            val arr = JSONArray(responseText)
            if (arr.length() == 0) {
                throw Exception("Profile not found in database")
            }

            val json = arr.getJSONObject(0)

            Session.role = json.optString("role", null)
            Session.countryIso2 = json.optString("country_iso2", null)
            Session.flutterwaveSubaccountId = json.optString("flutterwave_subaccount_id", null)
        }
    }
}
