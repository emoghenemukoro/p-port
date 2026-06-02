package com.pport.app.network

import com.pport.app.auth.Session
import com.pport.app.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class InitiatePaymentResult(
    val paymentLink: String,
    val txRef: String,
    val customerPays: Double
)

object PaymentsApi {

    /**
     * Calls Supabase Edge Function flutterwave-initiate-payment
     * Returns Flutterwave hosted checkout link.
     */
    suspend fun initiateFlutterwavePayment(
        requestId: String,
        offerId: String,
        redirectUrl: String = "https://example.com/redirect"
    ): InitiatePaymentResult = withContext(Dispatchers.IO) {

        val accessToken = Session.accessToken ?: error("Missing access token")

        val fnUrl = "${AppConfig.SUPABASE_URL}/functions/v1/flutterwave-initiate-payment"
        val conn = (URL(fnUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
        }

        val body = JSONObject()
            .put("request_id", requestId)
            .put("offer_id", offerId)
            .put("redirect_url", redirectUrl)

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        val code = conn.responseCode
        val text = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText()
            error(err ?: "initiate-payment failed (HTTP $code)")
        }

        val json = JSONObject(text)
        if (!json.optBoolean("ok", false)) {
            error(json.optString("error", "Unknown payment init error"))
        }

        InitiatePaymentResult(
            paymentLink = json.getString("payment_link"),
            txRef = json.getString("tx_ref"),
            customerPays = json.getDouble("customer_pays")
        )
    }
}
