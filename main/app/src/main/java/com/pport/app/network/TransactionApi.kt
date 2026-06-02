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

data class Transaction(
    val id: String,
    val requestId: String,
    val offerId: String,
    val customerId: String,
    val merchantId: String,
    val baseAmount: Double,
    val markup: Double,
    val totalAmount: Double,
    val status: String,
    val confirmedCustomer: Boolean,
    val confirmedMerchant: Boolean,
    val receipt: JSONObject? = null
)

data class AcceptOfferResult(
    val transaction: Transaction,
    val paymentLink: String? = null
)

object TransactionApi {

    /**
     * Accept an offer AND create a transaction row via the `accept-offer` edge function.
     * For withdrawals, the edge function returns a payment link.
     */
    suspend fun acceptOfferAndCreateTransaction(offerId: String): AcceptOfferResult =
        withContext(Dispatchers.IO) {
            val token = Session.accessToken ?: error("Missing token")
            val customerId = Session.userId ?: error("Not logged in")

            val url = URL("${AppConfig.SUPABASE_URL}/functions/v1/accept-offer")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject()
                .put("offer_id", offerId)
                .put("customer_id", customerId)

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText()
                error(err ?: "accept-offer failed (HTTP $code)")
            }

            val json = JSONObject(responseText)
            if (json.optBoolean("success") != true) {
                error(json.optString("error", "Failed to accept offer"))
            }

            val txObj = json.getJSONObject("transaction")
            val transaction = parseTransaction(txObj)
            val paymentLink = json.optString("payment_link", null)

            AcceptOfferResult(transaction, paymentLink)
        }

    /**
     * Fetch all transactions where the current user is customer or merchant, filtered by status list.
     */
    suspend fun fetchMyTransactions(statuses: List<String>): List<Transaction> =
        withContext(Dispatchers.IO) {
            val token = Session.accessToken ?: error("Missing token")
            val userId = Session.userId ?: error("Not logged in")
            val encoded = URLEncoder.encode(userId, "UTF-8")
            val inList = statuses.joinToString(",")

            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/transactions" +
                    "?or=(customer_id.eq.$encoded,merchant_id.eq.$encoded)" +
                    "&status=in.($inList)" +
                    "&order=created_at.desc" +
                    "&limit=50" +
                    "&select=id,request_id,offer_id,customer_id,merchant_id,base_amount,markup,total_amount,status,confirmed_customer,confirmed_merchant,receipt")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            List(arr.length()) { i -> parseTransaction(arr.getJSONObject(i)) }
        }

    /**
     * Confirm a transaction as a customer or merchant.
     * Calls the `confirm-transaction` edge function.
     */
    suspend fun confirmTransaction(transactionId: String, role: String): Transaction =
        withContext(Dispatchers.IO) {
            val token = Session.accessToken ?: error("Missing token")

            val url = URL("${AppConfig.SUPABASE_URL}/functions/v1/confirm-transaction")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
            }

            val body = JSONObject()
                .put("transaction_id", transactionId)
                .put("role", role)

            conn.outputStream.use { it.write(body.toString().toByteArray()) }

            val code = conn.responseCode
            val responseText = if (code in 200..299) {
                conn.inputStream.bufferedReader().readText()
            } else {
                val err = conn.errorStream?.bufferedReader()?.readText()
                error(err ?: "confirm-transaction failed (HTTP $code)")
            }

            val json = JSONObject(responseText)
            parseTransaction(json)
        }

    /**
     * Fetch a single transaction by ID.
     */
    suspend fun fetchTransaction(transactionId: String): Transaction =
        withContext(Dispatchers.IO) {
            val token = Session.accessToken ?: error("Missing token")
            val url = URL("${AppConfig.SUPABASE_URL}/rest/v1/transactions" +
                    "?id=eq.$transactionId" +
                    "&select=id,request_id,offer_id,customer_id,merchant_id,base_amount,markup,total_amount,status,confirmed_customer,confirmed_merchant,receipt")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", AppConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }

            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            if (arr.length() == 0) error("Transaction not found")
            parseTransaction(arr.getJSONObject(0))
        }

    private fun parseTransaction(obj: JSONObject): Transaction = Transaction(
        id = obj.getString("id"),
        requestId = obj.getString("request_id"),
        offerId = obj.getString("offer_id"),
        customerId = obj.getString("customer_id"),
        merchantId = obj.getString("merchant_id"),
        baseAmount = obj.getDouble("base_amount"),
        markup = obj.getDouble("markup"),
        totalAmount = obj.getDouble("total_amount"),
        status = obj.getString("status"),
        confirmedCustomer = obj.optBoolean("confirmed_customer"),
        confirmedMerchant = obj.optBoolean("confirmed_merchant"),
        receipt = obj.optJSONObject("receipt")
    )
}