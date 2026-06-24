package com.pport.app.data

import com.pport.app.auth.Session
import com.pport.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Request(
    val id: String,
    val customerId: String,
    val amount: Double,
    val type: String,
    val status: String
)

object RequestApi {

    suspend fun createRequestWithLocation(
        amount: Double,
        type: String,
        lat: Double,
        lng: Double
    ): String = withContext(Dispatchers.IO) {

        val accessToken = Session.accessToken ?: error("Missing access token")
        val userId = Session.userId ?: error("Missing user")

        val url = URL("${AppConfig.SUPABASE_URL}/functions/v1/create-request")
        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer $accessToken")

        val body = JSONObject()
            .put("user_id", userId)
            .put("lat", lat)
            .put("lng", lng)
            .put("amount", amount)
            .put("type", type)

        conn.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        val responseText = if (conn.responseCode in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "Failed to create request")
        }

        val json = JSONObject(responseText)
        json.getString("id")
    }

    suspend fun fetchOpenRequests(): List<Request> = withContext(Dispatchers.IO) {
        val accessToken = Session.accessToken ?: error("Missing access token")

        val url = URL(
            "${AppConfig.SUPABASE_URL}/rest/v1/requests" +
                    "?status=in.(open,negotiating)" +
                    "&select=id,customer_id,amount,type,status" +
                    "&order=created_at.desc" +
                    "&limit=50"
        )

        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Authorization", "Bearer $accessToken")

        val text = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(text)

        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Request(
                id = o.getString("id"),
                customerId = o.getString("customer_id"),
                amount = o.getDouble("amount"),
                type = o.getString("type"),
                status = o.getString("status")
            )
        }
    }

    suspend fun fetchMyActiveRequests(): List<Request> = withContext(Dispatchers.IO) {
        val accessToken = Session.accessToken ?: error("Missing access token")
        val customerId = Session.userId ?: error("Not logged in")

        val encodedCustomer = URLEncoder.encode(customerId, "UTF-8")

        val url = URL(
            "${AppConfig.SUPABASE_URL}/rest/v1/requests" +
                    "?customer_id=eq.$encodedCustomer" +
                    "&status=in.(open,negotiating,matched)" +
                    "&select=id,customer_id,amount,type,status" +
                    "&order=created_at.desc" +
                    "&limit=20"
        )

        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Authorization", "Bearer $accessToken")

        val text = conn.inputStream.bufferedReader().readText()
        val arr = JSONArray(text)

        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Request(
                id = o.getString("id"),
                customerId = o.getString("customer_id"),
                amount = o.getDouble("amount"),
                type = o.getString("type"),
                status = o.getString("status")
            )
        }
    }

    suspend fun updateRequestStatus(requestId: String, status: String) = withContext(Dispatchers.IO) {
        val accessToken = Session.accessToken ?: error("Missing access token")

        val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/requests?id=eq.$requestId")
        val conn = url.openConnection() as HttpURLConnection

        conn.requestMethod = "PATCH"
        conn.doOutput = true
        conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
        conn.setRequestProperty("Authorization", "Bearer $accessToken")
        conn.setRequestProperty("Content-Type", "application/json")

        val body = JSONObject().put("status", status)

        conn.outputStream.use {
            it.write(body.toString().toByteArray())
        }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "Failed to update request status")
        }
    }

    /**
     * NEW: Fetch the location (lat, lng) of a request by its ID.
     * Returns Pair(latitude, longitude) or null if not found.
     */
    suspend fun fetchRequestLocation(requestId: String): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val accessToken = Session.accessToken ?: error("Missing access token")
            val encoded = URLEncoder.encode(requestId, "UTF-8")
            val url = URL(
                "${AppConfig.SUPABASE_URL}/rest/v1/requests" +
                        "?id=eq.$encoded" +
                        "&select=location" +
                        "&limit=1"
            )

            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            if (arr.length() == 0) return@withContext null

            val locStr = arr.getJSONObject(0).optString("location", null)
                ?: return@withContext null

            // Parse PostGIS geography string "SRID=4326;POINT(lng lat)"
            val pointPart = locStr.substringAfter("POINT(").substringBefore(")")
            val coords = pointPart.split(" ")
            if (coords.size == 2) {
                val lng = coords[0].toDouble()
                val lat = coords[1].toDouble()
                Pair(lat, lng)   // return as (lat, lng) – GeoPoint expects lat first
            } else null
        }
}