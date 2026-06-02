package com.pport.app.network

import com.pport.app.auth.Session
import com.pport.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Review(
    val id: String,
    val transactionId: String,
    val customerId: String,
    val merchantId: String,
    val rating: Int,
    val comment: String?,
    val createdAt: String?
)

object ReviewApi {

    /**
     * Submit a review for a completed transaction.
     * @return the created review.
     */
    suspend fun submitReview(
        transactionId: String,
        merchantId: String,
        rating: Int,
        comment: String?
    ): Review = withContext(Dispatchers.IO) {
        val token = Session.accessToken ?: error("Missing token")
        val customerId = Session.userId ?: error("Not logged in")

        val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/reviews")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Prefer", "return=representation")
        }

        val body = JSONObject().apply {
            put("transaction_id", transactionId)
            put("customer_id", customerId)
            put("merchant_id", merchantId)
            put("rating", rating)
            put("comment", comment ?: JSONObject.NULL)
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val responseText = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "Failed to submit review (HTTP $code)")
        }

        val arr = JSONArray(responseText)
        if (arr.length() == 0) error("Review created but no row returned")
        parseReview(arr.getJSONObject(0))
    }

    /**
     * Fetch all reviews for a given merchant.
     */
    suspend fun fetchReviewsForMerchant(merchantId: String): List<Review> =
        withContext(Dispatchers.IO) {
            val token = Session.accessToken ?: error("Missing token")
            val encoded = java.net.URLEncoder.encode(merchantId, "UTF-8")
            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/reviews" +
                    "?merchant_id=eq.$encoded" +
                    "&select=id,transaction_id,customer_id,merchant_id,rating,comment,created_at" +
                    "&order=created_at.desc")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            List(arr.length()) { i -> parseReview(arr.getJSONObject(i)) }
        }

    /**
     * Check if a review already exists for a transaction (to avoid duplicates).
     * @return the existing review or null.
     */
    suspend fun getReviewForTransaction(transactionId: String): Review? =
        withContext(Dispatchers.IO) {
            val token = Session.accessToken ?: error("Missing token")
            val encoded = java.net.URLEncoder.encode(transactionId, "UTF-8")
            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/reviews" +
                    "?transaction_id=eq.$encoded" +
                    "&select=id,transaction_id,customer_id,merchant_id,rating,comment,created_at" +
                    "&limit=1")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            if (arr.length() == 0) null
            else parseReview(arr.getJSONObject(0))
        }

    private fun parseReview(obj: JSONObject): Review = Review(
        id = obj.getString("id"),
        transactionId = obj.getString("transaction_id"),
        customerId = obj.getString("customer_id"),
        merchantId = obj.getString("merchant_id"),
        rating = obj.getInt("rating"),
        comment = obj.optString("comment", null),
        createdAt = obj.optString("created_at", null)
    )
}