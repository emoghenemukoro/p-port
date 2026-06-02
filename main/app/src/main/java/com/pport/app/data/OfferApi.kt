package com.pport.app.network

import com.pport.app.auth.Session
import com.pport.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Offer(
    val id: String,
    val requestId: String,
    val markupPercent: Int,
    val status: String
)

object OfferApi {

    suspend fun submitOffer(requestId: String, markupPercent: Int): String =
        withContext(Dispatchers.IO) {

            val merchantId = Session.userId ?: error("Not logged in")
            val accessToken = Session.accessToken ?: error("Missing access token")

            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/offers")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=representation")

            val body = JSONObject()
                .put("request_id", requestId)
                .put("merchant_id", merchantId)
                .put("markup_percent", markupPercent)
                .put("status", "pending")

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText()
                error(err ?: "Failed to submit offer (HTTP $code)")
            }

            val arr = JSONArray(responseText)
            if (arr.length() == 0) error("Offer submitted but no row returned")
            arr.getJSONObject(0).getString("id")
        }

    suspend fun fetchLatestOfferForRequest(requestId: String): Offer? =
        withContext(Dispatchers.IO) {

            val accessToken = Session.accessToken ?: error("Missing access token")

            val encoded = URLEncoder.encode(requestId, "UTF-8")
            val url = URL(
                "${AppConfig.SUPABASE_URL}/rest/v1/offers" +
                        "?request_id=eq.$encoded" +
                        "&status=in.(pending,countered)" +
                        "&select=id,request_id,markup_percent,status" +
                        "&order=created_at.desc" +
                        "&limit=1"
            )

            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            if (arr.length() == 0) return@withContext null

            val obj = arr.getJSONObject(0)
            Offer(
                id = obj.getString("id"),
                requestId = obj.getString("request_id"),
                markupPercent = obj.getInt("markup_percent"),
                status = obj.getString("status")
            )
        }

    /**
     * Merchant: fetch offers created by this merchant, filtered by status list.
     */
    suspend fun fetchMyOffers(statuses: List<String>): List<Offer> =
        withContext(Dispatchers.IO) {

            val merchantId = Session.userId ?: error("Not logged in")
            val accessToken = Session.accessToken ?: error("Missing access token")

            val inList = statuses.joinToString(",") { it }
            val encodedMerchant = URLEncoder.encode(merchantId, "UTF-8")

            val url = URL(
                "${AppConfig.SUPABASE_URL}/rest/v1/offers" +
                        "?merchant_id=eq.$encodedMerchant" +
                        "&status=in.($inList)" +
                        "&select=id,request_id,markup_percent,status" +
                        "&order=created_at.desc" +
                        "&limit=50"
            )

            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)

            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                Offer(
                    id = obj.getString("id"),
                    requestId = obj.getString("request_id"),
                    markupPercent = obj.getInt("markup_percent"),
                    status = obj.getString("status")
                )
            }
        }

    /**
     * Merchant updates an offer after customer counters.
     */
    suspend fun updateOfferMarkup(offerId: String, newMarkupPercent: Int) =
        withContext(Dispatchers.IO) {

            val accessToken = Session.accessToken ?: error("Missing access token")

            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/offers?id=eq.$offerId")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject()
                .put("markup_percent", newMarkupPercent)
                .put("status", "pending")

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                error(err ?: "Failed to update offer")
            }
        }

    suspend fun counterOffer(offerId: String, newMarkupPercent: Int) =
        withContext(Dispatchers.IO) {
            val accessToken = Session.accessToken ?: error("Missing access token")

            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/offers?id=eq.$offerId")
            val conn = url.openConnection() as HttpURLConnection

            conn.requestMethod = "PATCH"
            conn.doOutput = true
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")

            val body = JSONObject()
                .put("markup_percent", newMarkupPercent)
                .put("status", "countered")

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                error(err ?: "Failed to counter offer")
            }
        }

    /**
     * NEW – Customer: fetch all pending/countered offers for a specific request.
     */
    suspend fun fetchOffersForRequest(requestId: String): List<Offer> =
        withContext(Dispatchers.IO) {
            val accessToken = Session.accessToken ?: error("Missing access token")
            val encoded = URLEncoder.encode(requestId, "UTF-8")

            val url = URL(
                "${AppConfig.SUPABASE_URL}/rest/v1/offers" +
                        "?request_id=eq.$encoded" +
                        "&status=in.(pending,countered)" +
                        "&select=id,request_id,markup_percent,status" +
                        "&order=created_at.desc"
            )

            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)

            List(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                Offer(
                    id = obj.getString("id"),
                    requestId = obj.getString("request_id"),
                    markupPercent = obj.getInt("markup_percent"),
                    status = obj.getString("status")
                )
            }
        }
}