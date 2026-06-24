package com.pport.app.network

import com.pport.app.auth.Session
import com.pport.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class NearbyMerchant(
    val id: String,
    val fullName: String?,
    val distanceMeters: Double,
    val maxTransaction: Double,
    val latitude: Double,
    val longitude: Double
)

object MerchantApi {

    suspend fun setOnlineStatus(online: Boolean) = withContext(Dispatchers.IO) {
        val token = Session.accessToken ?: error("Missing token")
        val userId = Session.userId ?: error("Missing user")

        val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
        }

        val body = JSONObject().put("is_online", online)
        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "Failed to update online status")
        }
    }

    suspend fun updateLocation(lat: Double, lng: Double) = withContext(Dispatchers.IO) {
        val token = Session.accessToken ?: error("Missing token")

        val url = URL("${AppConfig.SUPABASE_URL}/functions/v1/update-location")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }

        val body = JSONObject()
            .put("user_id", Session.userId)
            .put("lat", lat)
            .put("lng", lng)

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "Location update failed")
        }
    }

    suspend fun fetchNearbyMerchants(lat: Double, lng: Double): List<NearbyMerchant> =
        withContext(Dispatchers.IO) {
            val token = Session.accessToken ?: error("Missing token")
            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/rpc/get_nearby_merchants")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject().apply {
                put("user_lat", lat)
                put("user_lng", lng)
            }

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val text = if (conn.responseCode in 200..299)
                conn.inputStream.bufferedReader().readText()
            else
                throw Exception("Failed to fetch merchants: ${conn.errorStream?.bufferedReader()?.readText()}")

            val arr = JSONArray(text)
            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                NearbyMerchant(
                    id = obj.getString("id"),
                    fullName = obj.optString("full_name", null),
                    distanceMeters = obj.getDouble("distance_meters"),
                    maxTransaction = obj.getDouble("max_transaction"),
                    latitude = obj.getDouble("lat"),
                    longitude = obj.getDouble("lng")
                )
            }
        }

    // New function for merchant profile registration
    suspend fun updateMerchantProfile(
        posMachineId: String,
        bankDetails: JSONObject,
        subaccountId: String?
    ) = withContext(Dispatchers.IO) {
        val token = Session.accessToken ?: error("Missing token")
        val userId = Session.userId ?: error("Missing user")

        val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
        }

        val body = JSONObject().apply {
            put("pos_machine_id", posMachineId)
            put("bank_details", bankDetails)
            if (subaccountId != null) put("flutterwave_subaccount_id", subaccountId)
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "Profile update failed")
        }
    }
    // Add these methods inside the MerchantApi object

    suspend fun submitKYC(
        fullName: String,
        bvn: String,
        nin: String,
        bankName: String,
        accountNumber: String
    ) = withContext(Dispatchers.IO) {
        val token = Session.accessToken ?: error("Missing token")
        val userId = Session.userId ?: error("Missing user")

        val url = URL("${AppConfig.SUPABASE_URL}/functions/v1/merchant-kyc")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }

        val body = JSONObject().apply {
            put("user_id", userId)
            put("full_name", fullName)
            put("bvn", bvn)
            put("nin", nin)
            put("bank_name", bankName)
            put("account_number", accountNumber)
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "KYC submission failed")
        }
    }

    suspend fun linkPOS(
        posType: String,
        posSerial: String?
    ) = withContext(Dispatchers.IO) {
        val token = Session.accessToken ?: error("Missing token")
        val userId = Session.userId ?: error("Missing user")

        val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.$userId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PATCH"
            doOutput = true
            setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=minimal")
        }

        val body = JSONObject().apply {
            put("pos_type", posType)
            if (posSerial != null) put("assigned_pos_serial", posSerial)
            put("kyc_status", "in_progress")
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "POS linking failed")
        }
    }
}