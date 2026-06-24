package com.pport.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pport.app.network.MerchantApi
import com.pport.app.ui.components.PportTopBar
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun MerchantRegistrationScreen(
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // KYC fields
    var bvn by remember { mutableStateOf("") }
    var nin by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }

    // POS type selection
    var posType by remember { mutableStateOf("physical") } // 'physical' or 'digital'
    var posSerial by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var kycStatus by remember { mutableStateOf("pending") }

    // Steps: 1 = KYC form, 2 = POS linking
    var currentStep by remember { mutableStateOf(1) }

    Column(Modifier.fillMaxSize()) {
        PportTopBar(onLogout = null)

        Column(
            Modifier
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Progress indicator
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                StepIndicator(step = 1, label = "KYC", isActive = currentStep == 1, isComplete = currentStep > 1)
                Spacer(Modifier.width(16.dp))
                StepIndicator(step = 2, label = "POS", isActive = currentStep == 2, isComplete = currentStep > 2)
            }

            Spacer(Modifier.height(24.dp))

            if (currentStep == 1) {
                // KYC Form
                Text("Identity Verification", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Your information is securely verified via Flutterwave", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Full Legal Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = bvn,
                    onValueChange = { bvn = it.filter { c -> c.isDigit() }.take(11) },
                    label = { Text("BVN (11 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = nin,
                    onValueChange = { nin = it.filter { c -> c.isDigit() }.take(11) },
                    label = { Text("NIN (11 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Payout Bank Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.filter { c -> c.isDigit() }.take(10) },
                    label = { Text("Payout Account Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (fullName.isBlank() || bvn.length != 11 || nin.length != 11 ||
                            bankName.isBlank() || accountNumber.length != 10) {
                            error = "Please fill all fields correctly"
                            return@Button
                        }
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                // Submit KYC verification via edge function
                                MerchantApi.submitKYC(
                                    fullName = fullName,
                                    bvn = bvn,
                                    nin = nin,
                                    bankName = bankName,
                                    accountNumber = accountNumber
                                )
                                kycStatus = "in_progress"
                                currentStep = 2
                            } catch (e: Exception) {
                                error = e.message
                            } finally { loading = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Verifying..." else "Verify Identity & Continue")
                }
            }

            if (currentStep == 2) {
                // POS Linking
                Text("Link Your POS", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Choose how you'll accept payments", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))

                Row(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = posType == "physical",
                        onClick = { posType = "physical" },
                        label = { Text("Physical POS") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = posType == "digital",
                        onClick = { posType = "digital" },
                        label = { Text("Digital POS") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))

                if (posType == "physical") {
                    Text("Enter the serial number on your Flutterwave POS terminal", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = posSerial,
                        onValueChange = { posSerial = it },
                        label = { Text("POS Serial Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text("You'll accept payments via NFC tap-to-pay or bank transfer directly through the app.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("No physical POS needed — your phone is your terminal.", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            try {
                                MerchantApi.linkPOS(
                                    posType = posType,
                                    posSerial = if (posType == "physical") posSerial else null
                                )
                                success = true
                                onComplete()
                            } catch (e: Exception) {
                                error = e.message
                            } finally { loading = false }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (loading) "Activating..." else "Complete Setup")
                }
            }

            if (loading) CircularProgressIndicator(Modifier.padding(16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            if (success) Text("Setup complete! You can now accept transactions.", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StepIndicator(step: Int, label: String, isActive: Boolean, isComplete: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = when {
                isComplete -> MaterialTheme.colorScheme.primary
                isActive -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isComplete) {
                    Text("✓", color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("$step", color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}