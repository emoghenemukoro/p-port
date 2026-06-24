package com.pport.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.net.HttpURLConnection
import java.net.URL

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

    // Wallet balance
    var walletBalance by remember { mutableStateOf(0.0) }

    // Location dialog state
    var showLocationDialog by remember { mutableStateOf(false) }
    var customerLat by remember { mutableStateOf(0.0) }
    var customerLng by remember { mutableStateOf(0.0) }

    // Transfer dialog state
    var showTransferDialog by remember { mutableStateOf(false) }
    var transferTransactionId by remember { mutableStateOf("") }
    var customerBankName by remember { mutableStateOf("") }
    var customerAccountNumber by remember { mutableStateOf("") }

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

    suspend fun refreshData() {
        try {
            openRequests = RequestApi.fetchOpenRequests()
            counteredOffers = OfferApi.fetchMyOffers(listOf("countered"))
            acceptedOffers = OfferApi.fetchMyOffers(listOf("accepted"))
            activeTransactions = TransactionApi.fetchMyTransactions(
                listOf("pending", "confirmed_by_customer", "confirmed_by_merchant")
            )
            val merchantId = com.pport.app.auth.Session.userId ?: return
            val reviews = ReviewApi.fetchReviewsForMerchant(merchantId)
            totalReviews = reviews.size
            averageRating = if (reviews.isNotEmpty()) reviews.map { it.rating }.average() else null
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    suspend fun refreshBalance() {
        try {
            val token = com.pport.app.auth.Session.accessToken ?: return
            val userId = com.pport.app.auth.Session.userId ?: return
            val url = URL("${com.pport.app.config.AppConfig.SUPABASE_URL}/rest/v1/profiles?id=eq.$userId&select=balance&limit=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("apikey", com.pport.app.config.AppConfig.SUPABASE_ANON_KEY)
                setRequestProperty("Authorization", "Bearer $token")
            }
            val text = conn.inputStream.bufferedReader().readText()
            val arr = JSONArray(text)
            if (arr.length() > 0) {
                walletBalance = arr.getJSONObject(0).optDouble("balance", 0.0)
            }
        } catch (_: Exception) {
            // silently fail
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            refreshBalance()
            delay(5000)
        }
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

    fun viewCustomerLocation(requestId: String) {
        scope.launch {
            try {
                val loc = RequestApi.fetchRequestLocation(requestId)
                if (loc != null) {
                    customerLat = loc.first
                    customerLng = loc.second
                    showLocationDialog = true
                } else {
                    error = "Customer location not available"
                }
            } catch (e: Exception) {
                error = e.message
            }
        }
    }

    // Transfer dialog
    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Transfer to Customer") },
            text = {
                Column {
                    OutlinedTextField(
                        value = customerBankName,
                        onValueChange = { customerBankName = it },
                        label = { Text("Customer Bank Name") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customerAccountNumber,
                        onValueChange = { customerAccountNumber = it },
                        label = { Text("Customer Account Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customerBankName.isBlank() || customerAccountNumber.isBlank()) {
                        error = "Please fill all fields"
                        return@TextButton
                    }
                    scope.launch {
                        try {
                            val details = JSONObject().apply {
                                put("bank_name", customerBankName)
                                put("account_number", customerAccountNumber)
                            }
                            val newBalance = TransactionApi.initiateDepositTransfer(
                                transferTransactionId,
                                details
                            )
                            walletBalance = newBalance
                            toast = "Transfer successful"
                            refreshData()
                        } catch (e: Exception) {
                            error = e.message
                        }
                    }
                    showTransferDialog = false
                }) { Text("Confirm Transfer") }
            },
            dismissButton = { TextButton(onClick = { showTransferDialog = false }) { Text("Cancel") } }
        )
    }

    // Location dialog
    if (showLocationDialog) {
        Dialog(
            onDismissRequest = { showLocationDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.7f),
                shape = MaterialTheme.shapes.medium
            ) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Customer Location", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { showLocationDialog = false }) { Text("Close") }
                    }
                    Box(Modifier.weight(1f)) {
                        SimpleMapView(customerLat, customerLng)
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            val uri = Uri.parse("geo:$customerLat,$customerLng?q=$customerLat,$customerLng")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }) { Text("Open in Maps") }
                    }
                }
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
                    if (tx.receipt != null) {
                        Text("Timestamp: ${tx.receipt.optString("timestamp")}")
                        if (tx.receipt.has("commission")) {
                            Text("Commission: ₦${tx.receipt.optDouble("commission")}")
                        }
                    }
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
                TextButton(onClick = {
                    scope.launch {
                        refreshData()
                        refreshBalance()
                    }
                }) { Text("Refresh") }
            }

            // Wallet balance card
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Wallet Balance", style = MaterialTheme.typography.labelLarge)
                    Text("₦${"%.2f".format(walletBalance)}", style = MaterialTheme.typography.headlineSmall)
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Status: ${if (isOnline) "Online" else "Offline"}")
                Switch(checked = isOnline, onCheckedChange = { toggleOnline(it) })
            }
            averageRating?.let {
                Text("Rating: ${"%.1f".format(it)} / 5 ($totalReviews reviews)")
            } ?: Text("No ratings yet")

            locationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            toast?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            Divider()

            Text("Open Requests")
            if (openRequests.isEmpty()) Text("No open requests right now.")
            else {
                LazyColumn {
                    items(openRequests) { req ->
                        OpenRequestCard(req) { markup ->
                            scope.launch {
                                loading = true
                                try {
                                    OfferApi.submitOffer(req.id, markup)
                                    RequestApi.updateRequestStatus(req.id, "negotiating")
                                    toast = "Offer sent: $markup%"
                                    refreshData()
                                } catch (e: Exception) {
                                    error = e.message
                                } finally { loading = false }
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
                                    refreshData()
                                } catch (e: Exception) {
                                    error = e.message
                                } finally { loading = false }
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
                                TextButton(onClick = { viewCustomerLocation(offer.requestId) }) {
                                    Text("View Location")
                                }
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
                                Text("Type: ${tx.requestType ?: "unknown"}")
                                Text("Status: ${tx.status}")
                                TextButton(onClick = { viewCustomerLocation(tx.requestId) }) {
                                    Text("View Location")
                                }

                                if (!tx.confirmedMerchant) {
                                    if (tx.requestType == "deposit") {
                                        // Deposit: show Transfer to Customer button
                                        Button(onClick = {
                                            transferTransactionId = tx.id
                                            customerBankName = ""
                                            customerAccountNumber = ""
                                            showTransferDialog = true
                                        }) { Text("Transfer to Customer") }
                                    } else {
                                        // Withdrawal: show Payment Received button
                                        Button(onClick = {
                                            scope.launch {
                                                try {
                                                    val updated = TransactionApi.confirmTransaction(tx.id, "merchant")
                                                    if (updated.status == "completed") {
                                                        receiptTransaction = updated
                                                        refreshBalance()
                                                    }
                                                    refreshData()
                                                } catch (e: Exception) { error = e.message }
                                            }
                                        }) { Text("Payment Received") }
                                    }
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

@Composable
fun SimpleMapView(lat: Double, lng: Double) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = "pport-android"
    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(16.0)
                controller.setCenter(GeoPoint(lat, lng))
                val marker = Marker(this)
                marker.position = GeoPoint(lat, lng)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = "Customer"
                overlays.add(marker)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun OpenRequestCard(request: Request, onSubmitOffer: (Int) -> Unit) {
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
private fun CounteredOfferCard(offer: Offer, onUpdate: (Int) -> Unit) {
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