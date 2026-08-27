package com.marisbyte.invest.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.marisbyte.invest.ui.components.parseDecimalInput

/** Dialog zum Buchen eines Kaufs oder Verkaufs. */
@Composable
fun TransactionDialog(
    symbol: String,
    suggestedPrice: Double,
    onDismiss: () -> Unit,
    onConfirm: (isBuy: Boolean, quantity: Double, price: Double, fee: Double) -> Unit
) {
    var isBuy by remember { mutableStateOf(true) }
    var quantity by remember { mutableStateOf("") }
    var price by remember {
        mutableStateOf(if (suggestedPrice > 0) formatInput(suggestedPrice) else "")
    }
    var fee by remember { mutableStateOf("") }

    val quantityValue = quantity.let(::parseDecimalInput)
    val priceValue = price.let(::parseDecimalInput)
    val valid = quantityValue != null && quantityValue > 0.0 && priceValue != null && priceValue >= 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaktion für $symbol") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isBuy,
                        onClick = { isBuy = true },
                        label = { Text("Kauf") }
                    )
                    FilterChip(
                        selected = !isBuy,
                        onClick = { isBuy = false },
                        label = { Text("Verkauf") }
                    )
                }
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text("Stückzahl") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Kurs je Stück") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = fee,
                    onValueChange = { fee = it },
                    label = { Text("Gebühren (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        isBuy,
                        quantityValue ?: 0.0,
                        priceValue ?: 0.0,
                        fee.let(::parseDecimalInput) ?: 0.0
                    )
                }
            ) { Text("Buchen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}



private fun formatInput(value: Double): String =
    String.format(java.util.Locale.GERMANY, "%.2f", value)
