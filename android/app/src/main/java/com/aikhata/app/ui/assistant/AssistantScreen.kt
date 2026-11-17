package com.aikhata.app.ui.assistant

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikhata.app.AssistantDraft
import com.aikhata.app.CustomerRow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class ChatLine(val text: String, val fromUser: Boolean)

@Composable
fun AssistantScreen(
    customers: List<CustomerRow>,
    onBack: () -> Unit,
    onCustomerSelected: (Long) -> Unit,
    onSettings: () -> Unit,
    onSend: (String, (Result<AssistantDraft>) -> Unit) -> Unit,
    onConfirmDraft: (JSONArray) -> Unit
) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(ChatLine(
            "Tell me what happened in your shop. For example: “Ahmed bought 3 bags of rice for Rs. 4,500 yesterday.” I will prepare a draft for your approval.",
            false
        ))
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<JSONArray?>(null) }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text("AI KHATA", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("Your customer history", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                    TextButton(onClick = { scope.launch { drawer.close() }; onBack() }) { Text("⌂  Dashboard") }
                    TextButton(onClick = onSettings) { Text("⚙  Settings") }
                    Spacer(Modifier.height(12.dp))
                    Text("CUSTOMERS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(customers, key = { it.id }) { customer ->
                            Surface(
                                onClick = { scope.launch { drawer.close() }; onCustomerSelected(customer.id) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(customer.name, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(if (customer.balance >= 0) "Due · Rs. ${"%.2f".format(customer.balance)}" else "Advance · Rs. ${"%.2f".format(kotlin.math.abs(customer.balance))}",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawer.open() } }) { Text("☰", fontSize = 22.sp) }
                    Column(Modifier.weight(1f)) {
                        Text("AI Bookkeeper", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Review-first · Your data stays in control", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    TextButton(onClick = onSettings) { Text("⋮") }
                }
            },
            bottomBar = {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Describe a sale or payment…") },
                            shape = RoundedCornerShape(20.dp),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                val text = input.trim()
                                if (text.isNotEmpty() && !loading) {
                                    input = ""; messages.add(ChatLine(text, true)); loading = true; draft = null
                                    onSend(text) { result ->
                                        loading = false
                                        result.onSuccess { response ->
                                            messages.add(ChatLine(response.reply, false))
                                            draft = response.entries.takeIf { it.length() > 0 }
                                        }.onFailure { messages.add(ChatLine(it.message ?: "I couldn't understand that. Please try again.", false)) }
                                    }
                                }
                            })
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = input.isNotBlank() && !loading,
                            onClick = {
                                val text = input.trim()
                                if (text.isNotEmpty()) {
                                    input = ""; messages.add(ChatLine(text, true)); loading = true; draft = null
                                    onSend(text) { result ->
                                        loading = false
                                        result.onSuccess { response ->
                                            messages.add(ChatLine(response.reply, false))
                                            draft = response.entries.takeIf { it.length() > 0 }
                                        }.onFailure { messages.add(ChatLine(it.message ?: "I couldn't understand that. Please try again.", false)) }
                                    }
                                }
                            }
                        ) { Text("Send") }
                    }
                    Text(
                        "For history questions, only matched customer records are sent to your configured Gemini provider. New entries are never saved until you approve them.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 6.dp, top = 5.dp)
                    )
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                    reverseLayout = false,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { line ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 })
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(0.88f).animateContentSize(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (line.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                    tonalElevation = 2.dp,
                                    shadowElevation = 3.dp
                                ) {
                                    Text(line.text, modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                    if (loading) item { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("  Preparing a draft…", fontSize = 12.sp) } }
                }
                draft?.let { proposed ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PROPOSED ENTRIES · ${proposed.length()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            for (index in 0 until proposed.length()) {
                                val entry = proposed.optJSONObject(index) ?: JSONObject()
                                Text("${entry.optString("customer_name")} · ${entry.optString("type")} · Rs. ${entry.optDouble("amount")} · ${entry.optString("date")}", fontSize = 12.sp)
                                Text(entry.optString("description"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onConfirmDraft(proposed); draft = null }, modifier = Modifier.weight(1f)) { Text("Review entries") }
                                TextButton(onClick = { draft = null }) { Text("Discard") }
                            }
                        }
                    }
                }
            }
        }
    }
}
