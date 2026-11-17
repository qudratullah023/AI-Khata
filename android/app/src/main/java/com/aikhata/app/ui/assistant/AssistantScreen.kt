package com.aikhata.app.ui.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

private fun styleName(style: String) = when (style) {
    "soft" -> "Soft UI"
    "glass" -> "Glass"
    else -> "Liquid Glass"
}

@Composable
fun AssistantScreen(
    customers: List<CustomerRow>,
    visualStyle: String,
    onVisualStyleChange: (String) -> Unit,
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
            "Hi! I can record shop activity or look up a customer's history. Try: “Search Adel and tell me how much he bought a phone for and how much he paid back.”",
            false
        ))
    }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<JSONArray?>(null) }
    var styleMenu by remember { mutableStateOf(false) }
    val surfaceTint by androidx.compose.animation.animateColorAsState(
        targetValue = when (visualStyle) {
            "soft" -> MaterialTheme.colorScheme.surfaceVariant
            "glass" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
            else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
        },
        animationSpec = tween(280), label = "assistant-surface"
    )
    val backdrop = when (visualStyle) {
        "soft" -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.background))
        "glass" -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)))
        else -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f), MaterialTheme.colorScheme.tertiary.copy(alpha = 0.10f), MaterialTheme.colorScheme.background))
    }

    fun submit() {
        val text = input.trim()
        if (text.isNotEmpty() && !loading) {
            input = ""
            messages.add(ChatLine(text, true))
            loading = true
            draft = null
            onSend(text) { result ->
                loading = false
                result.onSuccess { response ->
                    messages.add(ChatLine(response.reply, false))
                    draft = response.entries.takeIf { it.length() > 0 }
                }.onFailure {
                    messages.add(ChatLine(it.message ?: "I couldn't understand that. Please try again.", false))
                }
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(310.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("AI KHATA", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                            Text("Your shop, clearly organized", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { scope.launch { drawer.close() }; onBack() }) { Text("⌂   Dashboard") }
                    TextButton(onClick = { scope.launch { drawer.close() }; onSettings() }) { Text("⚙   Settings") }
                    Spacer(Modifier.height(12.dp))
                    Text("CUSTOMER HISTORY", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        items(customers, key = { it.id }) { customer ->
                            Surface(
                                onClick = { scope.launch { drawer.close() }; onCustomerSelected(customer.id) },
                                shape = RoundedCornerShape(16.dp),
                                color = if (visualStyle == "glass") MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth().shadow(if (visualStyle == "soft") 5.dp else 1.dp, RoundedCornerShape(16.dp))
                            ) {
                                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(customer.name.take(1).uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
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
        }
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = if (visualStyle == "glass") 0.72f else 1f)).padding(horizontal = 8.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawer.open() } }) { Text("☰", fontSize = 22.sp) }
                    Column(Modifier.weight(1f)) {
                        Text("AI Bookkeeper", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Ask about your ledger · review before saving", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                    Box {
                        TextButton(onClick = { styleMenu = true }) { Text("◈  ${styleName(visualStyle)}") }
                        DropdownMenu(expanded = styleMenu, onDismissRequest = { styleMenu = false }) {
                            DropdownMenuItem(text = { Text("Soft UI · Neumorphism") }, onClick = { onVisualStyleChange("soft"); styleMenu = false })
                            DropdownMenuItem(text = { Text("Glassmorphism") }, onClick = { onVisualStyleChange("glass"); styleMenu = false })
                            DropdownMenuItem(text = { Text("Liquid Glass") }, onClick = { onVisualStyleChange("liquid"); styleMenu = false })
                        }
                    }
                }
            },
            bottomBar = {
                Column(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (visualStyle == "glass") 0.78f else 1f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ask or describe a transaction…") },
                            shape = RoundedCornerShape(if (visualStyle == "soft") 22.dp else 17.dp),
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submit() })
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(enabled = input.isNotBlank() && !loading, onClick = { submit() }) { Text("Send") }
                    }
                    Text("Customer history is read from your local ledger. New entries need your approval.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(backdrop)) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    items(messages) { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.88f)
                                    .shadow(if (visualStyle == "soft") 7.dp else 2.dp, RoundedCornerShape(20.dp))
                                    .animateContentSize(),
                                shape = RoundedCornerShape(if (visualStyle == "soft") 22.dp else 19.dp),
                                color = if (line.fromUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (visualStyle == "glass") 0.82f else 1f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = if (visualStyle == "glass") 0.78f else 0.96f)
                            ) {
                                Text(line.text, modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp), color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                            }
                        }
                    }
                    item {
                        AnimatedVisibility(visible = loading, enter = fadeIn(tween(180)), exit = fadeOut(tween(150))) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text("  Checking your ledger…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        AnimatedVisibility(visible = draft != null, enter = fadeIn(tween(220)), exit = fadeOut(tween(140))) {
                            draft?.let { proposed ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth().shadow(5.dp, RoundedCornerShape(22.dp)).animateContentSize(),
                                    shape = RoundedCornerShape(22.dp),
                                    color = surfaceTint
                                ) {
                                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Text("PROPOSED ENTRIES · ${proposed.length()}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                                        for (index in 0 until proposed.length()) {
                                            val entry = proposed.optJSONObject(index) ?: JSONObject()
                                            Text("${entry.optString("customer_name")} · ${entry.optString("type")} · Rs. ${entry.optDouble("amount")} · ${entry.optString("date")}", fontSize = 12.sp)
                                            Text(entry.optString("description"), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { onConfirmDraft(proposed); draft = null }, modifier = Modifier.weight(1f)) { Text("Review & save") }
                                            TextButton(onClick = { draft = null }) { Text("Discard") }
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
}
