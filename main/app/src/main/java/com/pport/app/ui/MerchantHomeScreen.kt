package com.pport.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pport.app.LocationManager
import com.pport.app.data.Request
import com.pport.app.data.RequestApi
import com.pport.app.network.MerchantApi
import com.pport.app.network.Offer
import com.pport.app.network.OfferApi
import com.pport.app.network.Transaction
import com.pport.app.network.TransactionApi
import com.pport.app.network.ReviewApi
import com.pport.app.ui.components.PportTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MerchantHomeScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locationManager = remember { LocationManager(context) }

    var isOnline by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    var openRequests by remember { mutableStateOf<List<Request>>(emptyList()) }
    var counteredOffers by remember { mutableStateOf<List<Offer>>(emptyList()) }
    var acceptedOffers by remember { mutableStateOf<List<Offer>>(emptyList()) }
    var activeTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    var receiptTransaction by remember { mutableStateOf<Transaction?>(null) }
    var averageRating by remember { mutableStateOf<Double?>(null) }
    var totalReviews by remember { mutableStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) locationError = "Location permission denied"
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun refreshAll() {
        error = null
        try {
            openRequests = RequestApi.fetchOpenRequests()
            counteredOffers = OfferApi.fetchMyOffers(listOf("countered"))
            acceptedOffers = OfferApi.fetchMyOffers(listOf("accepted"))
            activeTransactions = TransactionApi.fetchMyTransactions(listOf("pending", "confirmed_by_customer", "confirmed_by_merchant"))

            // Fetch reviews
            val merchantId = com.pport.app.auth.Session.userId ?: return
            val reviews = ReviewApi.fetchReviewsForMerchant(merchantId)
            totalReviews = reviews.size
            averageRating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else null
        } catch (e: Exception) {
            error = e.message
        }
    }

    LaunchedEffect(Unit) {
        while (true) { refreshAll(); delay(5000) }
    }

    LaunchedEffect(isOnline) {
        if (!isOnline) return@LaunchedEffect
        while (isOnline) {
            if (hasLocationPermission()) {
                locationManager.getLocation { loc ->
                    if (loc != null) {
                        scope.launch {
                            try {
                                MerchantApi.updateLocation(loc.latitude, loc.longitude)
                                locationError = null
                            } catch (e: Exception) {
                                locationError = "Location update failed: ${e.message}"
                            }
                        }
                    } else locationError = "Location unavailable"
                }
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                locationError = "Location permission required"
            }
            delay(30_000L)
        }
    }

    fun toggleOnline(online: Boolean) {
        scope.launch {
            try {
                MerchantApi.setOnlineStatus(online)
                isOnline = online
                if (!online) locationError = null
            } catch (e: Exception) {
                error = "Failed to change status: ${e.message}"
            }
        }
    }

    receiptTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { receiptTransaction = null },
            title = { Text("Transaction Receipt") },
            text = {
                Column {
                    Text("Transaction ID: ${tx.id}")
                    Text("Amount: ₦${tx.baseAmount}")
                    Text("Markup: ₦${tx.markup}")
                    Text("Total: ₦${tx.totalAmount}")
                    Text("Status: ${tx.status}")
                    if (tx.receipt != null) Text("Timestamp: ${tx.receipt.optString("timestamp")}")
                }
            },
            confirmButton = { TextButton(onClick = { receiptTransaction = null }) { Text("Close") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        PportTopBar(onLogout = onLogout)

        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Merchant Dashboard", style = MaterialTheme.typography.headlineMedium)
                TextButton(onClick = { scope.launch { refreshAll() } }) { Text("Refresh") }
            }

            // Rating display
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status: ${if (isOnline) "Online" else "Offline"}")
                Switch(checked = isOnline, onCheckedChange = { toggleOnline(it) })
            }
            averageRating?.let {
                Text("Rating: ${"%.1f".format(it)} / 5 ($totalReviews reviews)")
            } ?: Text("No ratings yet")

            locationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            toast?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Divider()

            Text("Open Requests")
            if (openRequests.isEmpty()) Text("No open requests")
            else {
                LazyColumn {
                    items(openRequests) { req ->
                        OpenRequestCard(req) { markup ->
                            scope.launch {
                                loading = true
                                try {
                                    OfferApi.submitOffer(req.id, markup)
                                    toast = "Offer sent: $markup%"
                                    refreshAll()
                                } catch (e: Exception) { error = e.message } finally { loading = false }
                            }
                        }
                    }
                }
            }

            Divider()
            Text("Customer Countered")
            if (counteredOffers.isEmpty()) Text("No counter offers")
            else {
                LazyColumn {
                    items(counteredOffers) { offer ->
                        CounteredOfferCard(offer) { newMarkup ->
                            scope.launch {
                                loading = true
                                try {
                                    OfferApi.updateOfferMarkup(offer.id, newMarkup)
                                    toast = "Updated to $newMarkup%"
                                    refreshAll()
                                } catch (e: Exception) { error = e.message } finally { loading = false }
                            }
                        }
                    }
                }
            }

            Divider()
            Text("Accepted Deals")
            if (acceptedOffers.isEmpty()) Text("No accepted deals")
            else {
                LazyColumn {
                    items(acceptedOffers) { offer ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Request: ${offer.requestId}")
                                Text("Markup: ${offer.markupPercent}%")
                            }
                        }
                    }
                }
            }

            Divider()
            Text("Active Transactions")
            if (activeTransactions.isEmpty()) Text("No active transactions")
            else {
                LazyColumn {
                    items(activeTransactions) { tx ->
                        Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Amount: ₦${tx.baseAmount} + ₦${tx.markup} = ₦${tx.totalAmount}")
                                Text("Status: ${tx.status}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!tx.confirmedMerchant) {
                                        Button(onClick = {
                                            scope.launch {
                                                try {
                                                    val updated = TransactionApi.confirmTransaction(tx.id, "merchant")
                                                    if (updated.status == "completed") receiptTransaction = updated
                                                    refreshAll()
                                                } catch (e: Exception) { error = e.message }
                                            }
                                        }) { Text("Payment Received") }
                                    }
                                    if (tx.status == "completed") {
                                        TextButton(onClick = {
                                            scope.launch {
                                                val fullTx = TransactionApi.fetchTransaction(tx.id)
                                                receiptTransaction = fullTx
                                            }
                                        }) { Text("View Receipt") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper cards (unchanged)
@Composable
private fun OpenRequestCard(
    request: Request,
    onSubmitOffer: (Int) -> Unit
) {
    var markupText by remember { mutableStateOf("5") }
    var localError by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Amount: ${request.amount}")
            Text("Type: ${request.type}")
            Text("Status: ${request.status}")
            OutlinedTextField(
                value = markupText,
                onValueChange = { markupText = it },
                label = { Text("Markup % (5–15)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val m = markupText.toIntOrNull()
                    if (m == null || m !in 5..15) localError = "Markup must be 5–15%"
                    else { localError = null; onSubmitOffer(m) }
                }
            ) { Text("Send Offer") }
        }
    }
}

@Composable
private fun CounteredOfferCard(
    offer: Offer,
    onUpdate: (Int) -> Unit
) {
    var newMarkupText by remember { mutableStateOf(offer.markupPercent.toString()) }
    var localError by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Request: ${offer.requestId}")
            Text("Customer countered to: ${offer.markupPercent}%")
            OutlinedTextField(
                value = newMarkupText,
                onValueChange = { newMarkupText = it },
                label = { Text("Your new markup (5–15)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            localError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val m = newMarkupText.toIntOrNull()
                    if (m == null || m !in 5..15) localError = "Markup must be 5–15%"
                    else { localError = null; onUpdate(m) }
                }
            ) { Text("Update Offer") }
        }
    }
}