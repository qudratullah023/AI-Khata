package com.aikhata.app.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aikhata.app.CustomerRow
import com.aikhata.app.ui.components.AnimatedValue
import com.aikhata.app.ui.components.Eyebrow
import com.aikhata.app.ui.components.FuturisticBackdrop
import com.aikhata.app.ui.components.GlowPill
import com.aikhata.app.ui.components.NeonPanel
import com.aikhata.app.ui.components.ProgressGlow
import com.aikhata.app.ui.components.ScreenEntrance
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    state: HomeUiState,
    onCustomers: () -> Unit,
    onAddCustomer: () -> Unit,
    onReports: () -> Unit,
    onBusiness: () -> Unit,
    onScan: () -> Unit,
    onSettings: () -> Unit,
    onLock: () -> Unit,
    onBackup: () -> Unit,
    onRetryScans: () -> Unit,
    waitingPhotoCount: Int,
    onCustomerClick: (Long) -> Unit,
    onRefresh: () -> Unit
) {
    FuturisticBackdrop {
        LazyColumn(
            modifier = Modifier.fillMaxSize().widthIn(max = 780.dp),
            contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ScreenEntrance {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("AK", color = Color(0xFF06151B), fontWeight = FontWeight.Black, fontSize = 17.sp)
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Eyebrow("AI Khata · Business OS")
                            Text(
                                state.dashboard?.shopName ?: "Your shop",
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text("Your business, in sync.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onLock,
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)
                        ) { Text("LOCK", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }

            if (state.loading && state.dashboard == null) {
                item {
                    NeonPanel(Modifier.fillMaxWidth()) {
                        Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else if (state.error != null && state.dashboard == null) {
                item {
                    NeonPanel(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Eyebrow("Connection status")
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                            Button(onClick = onRefresh) { Text("Try again") }
                        }
                    }
                }
            } else {
                state.dashboard?.let { dashboard ->
                    item {
                        ScreenEntrance(delayMillis = 70) {
                            NeonPanel(Modifier.fillMaxWidth(), radius = 26.dp) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f),
                                                    MaterialTheme.colorScheme.surface,
                                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                                                )
                                            )
                                        )
                                        .padding(20.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Eyebrow("Live financial overview")
                                            GlowPill("LOCAL DATA SECURE")
                                        }
                                        Text(
                                            "Outstanding balance",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 13.sp
                                        )
                                        AnimatedValue(
                                            money(kotlin.math.abs(dashboard.outstanding)),
                                            color = MaterialTheme.colorScheme.onBackground,
                                            size = 32
                                        )
                                        Text(
                                            if (dashboard.outstanding >= 0) "Total amount customers owe your shop"
                                            else "Your customers have an overall advance balance",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                        val repaymentProgress = if (dashboard.totalCredit > 0.0) {
                                            (dashboard.totalPayments / dashboard.totalCredit).toFloat().coerceIn(0f, 1f)
                                        } else 0f
                                        ProgressGlow(repaymentProgress, Modifier.padding(top = 3.dp), 8.dp)
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            MiniTotal("TOTAL CREDIT", money(dashboard.totalCredit))
                                            MiniTotal("RECEIVED", money(dashboard.totalPayments), end = true)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        ScreenEntrance(delayMillis = 130) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                CompactMetric(
                                    title = "THIS MONTH",
                                    value = money(dashboard.monthCredit),
                                    caption = "Sales / credit",
                                    modifier = Modifier.weight(1f),
                                    accent = MaterialTheme.colorScheme.primary
                                )
                                CompactMetric(
                                    title = "ACTIVITY",
                                    value = dashboard.transactionCount.toString(),
                                    caption = "Ledger entries",
                                    modifier = Modifier.weight(1f),
                                    accent = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    item {
                        ScreenEntrance(delayMillis = 180) {
                            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Eyebrow("Control centre")
                                        Text("Quick actions", style = MaterialTheme.typography.titleLarge)
                                    }
                                    GlowPill("READY")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ActionCard("Customers", "01", "Manage accounts", Modifier.weight(1f), onCustomers, true)
                                    ActionCard("New customer", "+", "Create a khata", Modifier.weight(1f), onAddCustomer)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ActionCard("Reports", "↗", "Track performance", Modifier.weight(1f), onReports)
                                    ActionCard("Scan ledger", "◎", "Read a photo", Modifier.weight(1f), onScan)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    ActionCard("Business", "▦", "Stock & expenses", Modifier.weight(1f), onBusiness)
                                    ActionCard("Settings", "⚙", "Shop preferences", Modifier.weight(1f), onSettings)
                                }
                            }
                        }
                    }

                    if (waitingPhotoCount > 0) {
                        item {
                            ScreenEntrance(delayMillis = 210) {
                                NeonPanel(Modifier.fillMaxWidth()) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(15.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Eyebrow("AI scan queue")
                                            Text("$waitingPhotoCount photo scan(s) waiting", fontWeight = FontWeight.SemiBold)
                                        }
                                        Button(onClick = onRetryScans) { Text("Retry") }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        ScreenEntrance(delayMillis = 230) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Eyebrow("Customer signals")
                                    Text("Recently active", style = MaterialTheme.typography.titleLarge)
                                }
                                Text(
                                    "VIEW ALL  →",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable(onClick = onCustomers).padding(6.dp)
                                )
                            }
                        }
                    }

                    if (dashboard.customers.isEmpty()) {
                        item {
                            NeonPanel(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text("Your ledger is ready", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Add your first customer to start recording sales and payments.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                    Button(onClick = onAddCustomer) { Text("Add first customer") }
                                }
                            }
                        }
                    } else {
                        items(dashboard.customers, key = { it.id }) { customer ->
                            ScreenEntrance(delayMillis = 260) {
                                CustomerSummaryCard(customer) { onCustomerClick(customer.id) }
                            }
                        }
                    }

                    item {
                        ScreenEntrance(delayMillis = 300) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) {
                                    Text("Settings")
                                }
                                OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                                    Text("Create backup")
                                }
                            }
                            Text(
                                "AI KHATA  •  SECURE LOCAL LEDGER",
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 9.sp,
                                letterSpacing = 1.4.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniTotal(label: String, value: String, end: Boolean = false) {
    Column(horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, letterSpacing = 1.2.sp)
        Text(value, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun CompactMetric(
    title: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier,
    accent: Color
) {
    NeonPanel(modifier, radius = 19.dp) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Eyebrow(title)
            AnimatedValue(value, color = accent, size = 20)
            Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    glyph: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    featured: Boolean = false
) {
    val background = if (featured) {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface))
    } else {
        Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
    }
    Card(
        modifier = modifier.height(112.dp).clickable(onClick = onClick).animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 8.dp)
    ) {
        Column(
            Modifier.fillMaxSize().background(background).padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                Modifier.size(31.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Text(glyph, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun CustomerSummaryCard(customer: CustomerRow, onClick: () -> Unit) {
    NeonPanel(Modifier.fillMaxWidth(), radius = 18.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    customer.name.trim().take(1).uppercase(Locale.getDefault()),
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(customer.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    customer.phone.ifBlank { "Customer account" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (customer.balance >= 0) "AMOUNT DUE" else "ADVANCE",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp,
                    letterSpacing = 0.7.sp
                )
                Text(
                    money(kotlin.math.abs(customer.balance)),
                    color = if (customer.balance > 0.009) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

private fun money(value: Double): String {
    val format = NumberFormat.getNumberInstance(Locale("en", "PK"))
    format.minimumFractionDigits = 2
    format.maximumFractionDigits = 2
    return "Rs. " + format.format(value)
}
