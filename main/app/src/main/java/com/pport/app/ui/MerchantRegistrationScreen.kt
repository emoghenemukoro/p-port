package com.pport.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

    var posMachineId by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var subaccountId by remember { mutableStateOf("") }

    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PportTopBar(onLogout = null)

        Column(
            Modifier.padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Merchant Registration", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = posMachineId,
                onValueChange = { posMachineId = it },
                label = { Text("POS Machine ID") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = bankName,
                onValueChange = { bankName = it },
                label = { Text("Bank Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = accountNumber,
                onValueChange = { accountNumber = it },
                label = { Text("Account Number") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = subaccountId,
                onValueChange = { subaccountId = it },
                label = { Text("Flutterwave Subaccount ID (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            if (loading) CircularProgressIndicator()

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (success) Text("Registration saved!", color = MaterialTheme.colorScheme.primary)

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (posMachineId.isBlank() || bankName.isBlank() || accountNumber.isBlank()) {
                        error = "Please fill all required fields"
                        return@Button
                    }
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val bankDetails = JSONObject()
                                .put("bank_name", bankName)
                                .put("account_number", accountNumber)

                            MerchantApi.updateMerchantProfile(
                                posMachineId = posMachineId,
                                bankDetails = bankDetails,
                                subaccountId = subaccountId.ifBlank { null }
                            )
                            success = true
                            onComplete()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save & Continue")
            }
        }
    }
}