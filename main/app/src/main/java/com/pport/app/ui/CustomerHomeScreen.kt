package com.pport.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pport.app.LocationManager
import com.pport.app.data.Request
import com.pport.app.data.RequestApi
import com.pport.app.network.MerchantApi
import com.pport.app.network.NearbyMerchant
import com.pport.app.network.Offer
import com.pport.app.network.OfferApi
import com.pport.app.network.Transaction
import com.pport.app.network.TransactionApi
import com.pport.app.network.ReviewApi
import com.pport.app.ui.components.PportTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun CustomerHomeScreen(onLogout: () -> Unit) {

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val locationManager = remember { LocationManager(context) }

    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("withdraw") }

    var requests by remember { mutableStateOf<List<Request>>(emptyList()) }
    var status by remember { mutableStateOf("Ready") }
    var loading by remember { mutableStateOf(false) }

    var currentLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var merchants by remember { mutableStateOf<List<NearbyMerchant>>(emptyList()) }

    var expandedRequestId by remember { mutableStateOf<String?>(null) }
    var offersForRequest by remember { mutableStateOf<Map<String, List<Offer>>>(emptyMap()) }
    var counterDialogOpen by remember { mutableStateOf(false) }
    var counterOfferId by remember { mutableStateOf("") }
    var counterNewMarkup by remember { mutableStateOf("") }

    var activeTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var receiptTransaction by remember { mutableStateOf<Transaction?>(null) }

    var ratingDialogOpen by remember { mutableStateOf(false) }
    var ratingTransactionId by remember { mutableStateOf("") }
    var ratingMerchantId by remember { mutableStateOf("") }
    var selectedRating by remember { mutableStateOf(5) }
    var ratingComment by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) status = "Permission granted, getting location..."
        else status = "Location permission denied"
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openPaymentLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    }

    suspend fun refreshRequests() {
        requests = RequestApi.fetchMyActiveRequests()
    }

    suspend fun refreshTransactions() {
        activeTransactions = TransactionApi.fetchMyTransactions(listOf("pending", "confirmed_by_customer", "confirmed_by_merchant"))
    }

    // Periodically fetch offers for all requests
    LaunchedEffect(Unit) {
        while (true) {
            refreshRequests()
            refreshTransactions()

            val updatedMap = mutableMapOf<String, List<Offer>>()
            for (req in requests) {
                try {
                    val offers = OfferApi.fetchOffersForRequest(req.id)
                    updatedMap[req.id] = offers
                } catch (_: Exception) {
                    updatedMap[req.id] = offersForRequest[req.id] ?: emptyList()
                }
            }
            offersForRequest = updatedMap

            delay(5000)
        }
    }

    // Manually expanded request → refresh immediately
    LaunchedEffect(expandedRequestId) {
        val reqId = expandedRequestId ?: return@LaunchedEffect
        try {
            val offers = OfferApi.fetchOffersForRequest(reqId)
            offersForRequest = offersForRequest + (reqId to offers)
        } catch (e: Exception) {
            Log.e("Customer", "Failed to fetch offers", e)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (hasLocationPermission()) {
                locationManager.getLocation { loc ->
                    if (loc != null) currentLocation = GeoPoint(loc.latitude, loc.longitude)
                    else status = "Location unavailable"
                }
            } else {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            delay(10_000L)
        }
    }

    LaunchedEffect(currentLocation) {
        val loc = currentLocation ?: return@LaunchedEffect
        try {
            merchants = MerchantApi.fetchNearbyMerchants(loc.latitude, loc.longitude)
        } catch (e: Exception) {
            merchants = emptyList()
        }
    }

    // Counter dialog
    if (counterDialogOpen) {
        AlertDialog(
            onDismissRequest = { counterDialogOpen = false },
            title = { Text("Counter Offer") },
            text = {
                OutlinedTextField(
                    value = counterNewMarkup,
                    onValueChange = { counterNewMarkup = it },
                    label = { Text("New markup % (0‑15)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newMarkup = counterNewMarkup.toIntOrNull()
                    if (newMarkup != null && newMarkup in 0..15) {
                        scope.launch {
                            try {
                                OfferApi.counterOffer(counterOfferId, newMarkup)
                                status = "Counter offer sent"
                                expandedRequestId?.let { id ->
                                    offersForRequest = offersForRequest + (id to OfferApi.fetchOffersForRequest(id))
                                }
                            } catch (e: Exception) { status = "Error: ${e.message}" }
                        }
                        counterDialogOpen = false
                    }
                }) { Text("Counter") }
            },
            dismissButton = { TextButton(onClick = { counterDialogOpen = false }) { Text("Cancel") } }
        )
    }

    // Receipt dialog
    receiptTransaction?.let { tx ->
        AlertDialog(
            onDismissRequest = { receiptTransaction = null },
            title = { Text("Transaction Receipt") },
            text = {
                Column {
                    Text("Transaction ID: ${tx.id}")
                    Text("Amount: ₦${tx.baseAmount}")
                    Text("Markup: ₦${tx.markup}")
                    Text("Total Paid: ₦${tx.totalAmount}")
                    Text("Status: ${tx.status}")
                    if (tx.receipt != null) {
                        Text("Timestamp: ${tx.receipt.optString("timestamp")}")
                    }
                    if (tx.status == "completed") {
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = {
                            receiptTransaction = null
                            ratingTransactionId = tx.id
                            ratingMerchantId = tx.merchantId
                            selectedRating = 5
                            ratingComment = ""
                            ratingDialogOpen = true
                        }) { Text("Rate Merchant") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { receiptTransaction = null }) { Text("Close") } }
        )
    }

    // Rating dialog
    if (ratingDialogOpen) {
        AlertDialog(
            onDismissRequest = { ratingDialogOpen = false },
            title = { Text("Rate Merchant") },
            text = {
                Column {
                    Text("Rating: $selectedRating / 5")
                    Slider(
                        value = selectedRating.toFloat(),
                        onValueChange = { selectedRating = it.toInt() },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    OutlinedTextField(
                        value = ratingComment,
                        onValueChange = { ratingComment = it },
                        label = { Text("Comment (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            ReviewApi.submitReview(
                                transactionId = ratingTransactionId,
                                merchantId = ratingMerchantId,
                                rating = selectedRating,
                                comment = ratingComment.ifBlank { null }
                            )
                            status = "Review submitted"
                        } catch (e: Exception) {
                            status = "Review failed: ${e.message}"
                        }
                    }
                    ratingDialogOpen = false
                }) { Text("Submit") }
            },
            dismissButton = { TextButton(onClick = { ratingDialogOpen = false }) { Text("Cancel") } }
        )
    }

    Column(Modifier.fillMaxSize()) {
        PportTopBar(onLogout = onLogout)

        Column(Modifier.padding(16.dp)) {
            Text("Request Cash", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))

            Box(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                if (currentLocation != null) {
                    MapViewComposable(
                        currentLocation = currentLocation!!,
                        merchants = merchants,
                        onMerchantClick = { merchant -> status = "Selected ${merchant.fullName ?: "merchant"}" }
                    )
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = type == "withdraw",
                    onClick = { type = "withdraw" },
                    label = { Text("Withdraw") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = type == "deposit",
                    onClick = { type = "deposit" },
                    label = { Text("Deposit") },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val amt = amount.toDoubleOrNull()
                    if (amt == null) {
                        status = "Invalid amount"
                        return@Button
                    }
                    if (!hasLocationPermission()) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        status = "Location permission required"
                        return@Button
                    }
                    loading = true
                    status = "Getting location..."
                    locationManager.getLocation { loc ->
                        if (loc == null) {
                            status = "Location unavailable"
                            loading = false
                            return@getLocation
                        }
                        scope.launch {
                            try {
                                RequestApi.createRequestWithLocation(amt, type, loc.latitude, loc.longitude)
                                status = "Request created"
                                amount = ""
                            } catch (e: Exception) {
                                status = "Error: ${e.message}"
                            } finally { loading = false }
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "Processing..." else "Request Cash")
            }

            Spacer(Modifier.height(12.dp))
            Text(status)
            Divider(Modifier.padding(vertical = 12.dp))

            Text("My Requests")
            LazyColumn {
                items(requests) { req ->
                    val isExpanded = expandedRequestId == req.id
                    val offers = offersForRequest[req.id] ?: emptyList()
                    val hasNewOffer = offers.isNotEmpty() && !isExpanded

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(8.dp)
                            .clickable { expandedRequestId = if (isExpanded) null else req.id }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(12.dp)) {
                                Text("Amount: ${req.amount}")
                                Text("Type: ${req.type}")
                                Text("Status: ${req.status}")
                                if (isExpanded) {
                                    Divider(Modifier.padding(vertical = 4.dp))
                                    if (offers.isEmpty()) {
                                        Text("No offers yet.")
                                    } else {
                                        Text("Offers:")
                                        offers.forEach { offer ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Column {
                                                    Text("Markup: ${offer.markupPercent}%")
                                                    Text("Status: ${offer.status}")
                                                }
                                                Row {
                                                    TextButton(onClick = {
                                                        scope.launch {
                                                            try {
                                                                val result = TransactionApi.acceptOfferAndCreateTransaction(offer.id)
                                                                status = "Offer accepted"
                                                                refreshRequests()
                                                                refreshTransactions()
                                                                expandedRequestId = null
                                                                if (result.paymentLink != null) {
                                                                    openPaymentLink(result.paymentLink)
                                                                }
                                                            } catch (e: Exception) {
                                                                status = "Error: ${e.message}"
                                                            }
                                                        }
                                                    }) { Text("Accept") }
                                                    TextButton(onClick = {
                                                        counterOfferId = offer.id
                                                        counterNewMarkup = ""
                                                        counterDialogOpen = true
                                                    }) { Text("Counter") }
                                                }
                                            }
                                            Divider()
                                        }
                                    }
                                }
                            }
                            if (hasNewOffer) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Divider(Modifier.padding(vertical = 8.dp))
            Text("Active Transactions", style = MaterialTheme.typography.titleMedium)

            if (activeTransactions.isEmpty()) {
                Text("No active transactions.")
            } else {
                LazyColumn {
                    items(activeTransactions) { tx ->
                        Card(Modifier.fillMaxWidth().padding(8.dp)) {
                            Column(Modifier.padding(12.dp)) {
                                Text("Amount: ₦${tx.baseAmount} + ₦${tx.markup} = ₦${tx.totalAmount}")
                                Text("Status: ${tx.status}")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!tx.confirmedCustomer) {
                                        Button(onClick = {
                                            scope.launch {
                                                try {
                                                    val updated = TransactionApi.confirmTransaction(tx.id, "customer")
                                                    if (updated.status == "completed") {
                                                        receiptTransaction = updated
                                                    }
                                                    refreshTransactions()
                                                } catch (e: Exception) {
                                                    status = "Error: ${e.message}"
                                                }
                                            }
                                        }) { Text("I've Paid") }
                                    }
                                    if (tx.status == "completed") {
                                        Button(onClick = {
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

// Only MapViewComposable here (no SimpleMapView, no MerchantHomeScreen)
@Composable
fun MapViewComposable(
    currentLocation: GeoPoint,
    merchants: List<NearbyMerchant>,
    onMerchantClick: (NearbyMerchant) -> Unit
) {
    val context = LocalContext.current
    Configuration.getInstance().userAgentValue = "pport-android"

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(15.0)
                controller.setCenter(currentLocation)

                val customerMarker = Marker(this)
                customerMarker.position = currentLocation
                customerMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                customerMarker.title = "You are here"
                overlays.add(customerMarker)
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            view.overlays.removeAll { it is Marker && it.title != "You are here" }
            for (merchant in merchants) {
                val marker = Marker(view)
                marker.position = GeoPoint(merchant.latitude, merchant.longitude)
                marker.title = merchant.fullName ?: "Merchant"
                marker.snippet = "${"%.0f".format(merchant.distanceMeters)}m • ₦${merchant.maxTransaction} max"
                marker.setOnMarkerClickListener { _, _ ->
                    onMerchantClick(merchant)
                    true
                }
                view.overlays.add(marker)
            }
            view.invalidate()
        }
    )
}