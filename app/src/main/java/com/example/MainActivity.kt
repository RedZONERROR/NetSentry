package com.example

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.TrafficViewModel
import com.example.ui.theme.MyApplicationTheme
import com.example.vpn.LocalVpnService
import com.example.vpn.TrafficRuleManager
import com.example.db.AppRule
import com.example.db.TrafficLog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startVpnService()
        } else {
            Toast.makeText(this, "VPN Permission Denied. NetSentry AI cannot inspect packets.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NetSentryMainScreen(
                        onStartVpn = { triggerVpnStart() },
                        onStopVpn = { triggerVpnStop() }
                    )
                }
            }
        }
    }

    private fun triggerVpnStart() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPrepareLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_START
        }
        startService(intent)
    }

    private fun triggerVpnStop() {
        val intent = Intent(this, LocalVpnService::class.java).apply {
            action = LocalVpnService.ACTION_STOP
        }
        startService(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetSentryMainScreen(
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    viewModel: TrafficViewModel = viewModel()
) {
    val context = LocalContext.current
    val appRules by viewModel.appRules.collectAsState()
    val trafficLogs by viewModel.trafficLogs.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()
    val isAiLoading by viewModel.isAiLoading.collectAsState()
    val activeQuotaViolation by viewModel.activeQuotaViolation.collectAsState()
    val apiKeyField by viewModel.apiKeyField.collectAsState()

    var activeTab by remember { mutableStateOf("dashboard") }
    var vpnActiveState by remember { mutableStateOf(LocalVpnService.isServiceActive) }
    var showLimitSettingsDialogForUid by remember { mutableStateOf<Int?>(null) }
    var selectedAppLimitBytes by remember { mutableStateOf("") }
    var showApiKeyField by remember { mutableStateOf(false) }

    // Live update VPN active state
    LaunchedEffect(Unit) {
        while (true) {
            vpnActiveState = LocalVpnService.isServiceActive
            delay(1000)
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_screen_scaffold"),
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "Shield Icon",
                                tint = if (vpnActiveState) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp).padding(end = 4.dp)
                            )
                            Text(
                                text = "NetSentry AI",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Text(
                            text = "SYSTEMS ARCHITECT PRO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showApiKeyField = !showApiKeyField },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(20.dp))
                            .testTag("api_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "AI Key Configuration",
                            modifier = Modifier.size(20.dp),
                            tint = if (apiKeyField.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                NavigationBarItem(
                    selected = activeTab == "dashboard",
                    onClick = { activeTab = "dashboard" },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                    label = { Text("HOME", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_dashboard")
                )
                NavigationBarItem(
                    selected = activeTab == "firewall",
                    onClick = { activeTab = "firewall" },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Firewall") },
                    label = { Text("RULES", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_firewall")
                )
                NavigationBarItem(
                    selected = activeTab == "ai",
                    onClick = { activeTab = "ai" },
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Intelligence AI") },
                    label = { Text("AI CORE", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("nav_tab_ai")
                )
            }
        },
        contentWindowInsets = WindowInsets.navigationBars
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Key BYOK setup drawer inside top
                AnimatedVisibility(
                    visible = showApiKeyField,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Bring Your Own Key Configuration (BYOK)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Provide your personal Google AI Studio Api Key to secure cloud AI analysis if local Gemini Nano hardware model acceleration is unavailable.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            var tempKeyField by remember { mutableStateOf(apiKeyField) }
                            OutlinedTextField(
                                value = tempKeyField,
                                onValueChange = { tempKeyField = it },
                                placeholder = { Text("Gemini AI API Key...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("api_key_text_field"),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    IconButton(onClick = { viewModel.saveApiKey("") }) {
                                        Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.saveApiKey(tempKeyField)
                                        showApiKeyField = false
                                        Toast.makeText(context, "Settings securely updated.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("save_api_key_btn")
                                ) {
                                    Text("Apply Credentials")
                                }
                            }
                        }
                    }
                }

                // Page layouts
                when (activeTab) {
                    "dashboard" -> DashboardView(
                        vpnActiveState = vpnActiveState,
                        onStartVpn = onStartVpn,
                        onStopVpn = onStopVpn,
                        registeredApps = appRules,
                        logs = trafficLogs,
                        viewModel = viewModel,
                        onNavigateToTab = { activeTab = it }
                    )
                    "firewall" -> AppFirewallView(
                        rules = appRules,
                        onUpdateRuleState = { uid, state -> viewModel.updateFirewallRule(uid, state) },
                        onConfigureLimit = { uid ->
                            val currentRule = appRules.find { it.uid == uid }
                            selectedAppLimitBytes = if (currentRule?.bytesCap != null) (currentRule.bytesCap / (1024 * 1024)).toString() else "0"
                            showLimitSettingsDialogForUid = uid
                        },
                        viewModel = viewModel
                    )
                    "ai" -> ArtificialIntelView(
                        aiResponse = aiResponse,
                        isAiLoading = isAiLoading,
                        isLocalSupported = viewModel.isLocalAiSupported,
                        onAuditRequest = { q -> viewModel.runSecurityLogAudit(q) }
                    )
                }
            }

            // High Priority "Quota Exceeded/Extend Dialog" System Alert
            activeQuotaViolation?.let { violation ->
                Dialog(onDismissRequest = { viewModel.dismissQuotaViolation() }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .testTag("quota_alert_dialog"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Limit Alert",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Shield Block: Quota Exceeded",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The application '${violation.appName}' has exceeded its safe limit cap of ${(violation.bytesCap / (1024 * 1024))}MB.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            Text(
                                text = "Immediate Sentry Action Required:",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.Start)
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Options
                            Button(
                                onClick = { viewModel.resolveQuota(violation.uid, TrafficRuleManager.QuotaResolution.Extend(10L * 1024 * 1024)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_extend_10mb"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Extend Limit (+10 MB)")
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { viewModel.resolveQuota(violation.uid, TrafficRuleManager.QuotaResolution.Extend(50L * 1024 * 1024)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_extend_50mb"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Extend Limit (+50 MB)")
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = { viewModel.resolveQuota(violation.uid, TrafficRuleManager.QuotaResolution.EndConnection) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_sever_connection"),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("End Connection (Sever Traffic)")
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            TextButton(
                                onClick = { viewModel.resolveQuota(violation.uid, TrafficRuleManager.QuotaResolution.NoDataMode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_no_data_mode")
                            ) {
                                Text("Activate No Data Drop Packets Silently", color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }

            // Quota Set Dialog
            showLimitSettingsDialogForUid?.let { uid ->
                Dialog(onDismissRequest = { showLimitSettingsDialogForUid = null }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Configure Bandwidth Quota",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Enter daily quota caps in Megabytes (MB). Input '0' to configure unlimited bandwidth usage.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = selectedAppLimitBytes,
                                onValueChange = { selectedAppLimitBytes = it },
                                label = { Text("Byte Limit (in MB)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("limit_input_field")
                            )

                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { showLimitSettingsDialogForUid = null }) {
                                    Text("Dismiss")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val mbVal = selectedAppLimitBytes.toLongOrNull() ?: 0L
                                        val bytesVal = mbVal * 1024 * 1024
                                        viewModel.setAppLimit(uid, bytesVal)
                                        showLimitSettingsDialogForUid = null
                                        Toast.makeText(context, "Limit rules modified.", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.testTag("save_limit_btn")
                                ) {
                                    Text("Apply Caps")
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
fun DashboardView(
    vpnActiveState: Boolean,
    onStartVpn: () -> Unit,
    onStopVpn: () -> Unit,
    registeredApps: List<AppRule>,
    logs: List<TrafficLog>,
    viewModel: TrafficViewModel,
    onNavigateToTab: (String) -> Unit
) {
    val context = LocalContext.current
    var activeInspectedLog by remember { mutableStateOf<TrafficLog?>(null) }
    var logSearchQuery by remember { mutableStateOf("") }
    var logStatusFilter by remember { mutableStateOf("ALL") }
    var logProtocolFilter by remember { mutableStateOf("ALL") }

    val filteredLogs = remember(logs, logSearchQuery, logStatusFilter, logProtocolFilter) {
        logs.filter { log ->
            val matchesSearch = (log.appName ?: "").contains(logSearchQuery, ignoreCase = true) ||
                    (log.packageName ?: "").contains(logSearchQuery, ignoreCase = true) ||
                    (log.protocol ?: "").contains(logSearchQuery, ignoreCase = true) ||
                    (log.ip ?: "").contains(logSearchQuery, ignoreCase = true)
            val matchesStatus = when (logStatusFilter) {
                "ALLOWED" -> log.allowed
                "BLOCKED" -> !log.allowed
                else -> true
            }
            val matchesProtocol = when (logProtocolFilter) {
                "TCP" -> (log.protocol ?: "").contains("TCP", ignoreCase = true) || (log.protocol ?: "").contains("HTTPS", ignoreCase = true)
                "UDP" -> (log.protocol ?: "").contains("UDP", ignoreCase = true)
                "DNS" -> (log.protocol ?: "").contains("DNS", ignoreCase = true)
                else -> true
            }
            matchesSearch && matchesStatus && matchesProtocol
        }
    }

    val totalScanned = logs.size
    val totalBlocked = logs.count { !it.allowed }
    val totalBytes = logs.sumOf { it.bytesSent + it.bytesReceived } / 1024

    // Local state for Daily Quota tracker mockup
    var dailyQuotaMb by remember { mutableStateOf(50.0) }
    var showExtendQuotaDialog by remember { mutableStateOf(false) }
    var typedQuotaText by remember { mutableStateOf("") }
    val totalMbUsed = (logs.sumOf { it.bytesSent + it.bytesReceived }.toDouble() / (1024.0 * 1024.0)).coerceAtLeast(0.4)
    val formattedUsed = String.format("%.1f", totalMbUsed)
    val progressFraction = (totalMbUsed / dailyQuotaMb).coerceIn(0.0, 1.0).toFloat()
    val isNearLimit = progressFraction >= 0.8f

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("dashboard_view_column")
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Sleek Interface VPN Status Card (Protection Engine)
            val cardBgColor = if (vpnActiveState) Color(0xFF4F46E5) else Color(0xFFDC2626)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = cardBgColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ENGINE STATUS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vpnActiveState) Color(0xFFC7D2FE) else Color(0xFFFECACA),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (vpnActiveState) "Shield Active" else "Shield Disarmed",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }

                        // Beautiful Custom Mimicked Switch / Toggle Target
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (vpnActiveState) Color(0xFF6366F1) else Color(0xFFB91C1C))
                                .clickable { if (vpnActiveState) onStopVpn() else onStartVpn() }
                                .padding(4.dp),
                            contentAlignment = if (vpnActiveState) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Adaptive mini state containers 
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0x1AFFFFFF), shape = RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "PROTECTED APPS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (vpnActiveState) Color(0xFFC7D2FE) else Color(0xFFFECACA),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${registeredApps.size}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(Color(0x1AFFFFFF), shape = RoundedCornerShape(16.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = "DPI LATENCY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (vpnActiveState) Color(0xFFC7D2FE) else Color(0xFFFECACA),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (vpnActiveState) "0.4ms" else "--",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Secondary activation trigger for quick accessibility
                    Button(
                        onClick = { if (vpnActiveState) onStopVpn() else onStartVpn() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = cardBgColor
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("master_sentry_toggle_btn"),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = if (vpnActiveState) Icons.Filled.Close else Icons.Filled.PlayArrow,
                            contentDescription = "Shield Toggle Arrow",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (vpnActiveState) "DISARM SENTINEL" else "ACTIVATE SENTINEL",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(14.dp))
            // Beautiful Bandwidth Quota Monitor Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Traffic Quota",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Light Amber or Green Badge
                        val badgeBg = if (isNearLimit) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                        val badgeText = if (isNearLimit) Color(0xFFB45309) else Color(0xFF15803D)
                        val badgeLabel = if (isNearLimit) "NEAR LIMIT" else "OPTIMAL"

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(badgeBg)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = badgeLabel,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = badgeText
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress bar indicator
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        color = if (isNearLimit) Color(0xFFF59E0B) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "DATA USED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = formattedUsed,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = " / ${dailyQuotaMb.toInt()}.0 MB",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 2.dp)
                                )
                            }
                        }

                        // Extend button
                        Button(
                            onClick = {
                                typedQuotaText = dailyQuotaMb.toInt().toString()
                                showExtendQuotaDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(
                                text = "EXTEND LIMIT",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(14.dp))
            // Beautiful AI Security Insight Feed Card
            val isDark = isSystemInDarkTheme()
            val customCardBg = if (isDark) Color(0xFF1E1E3F) else Color(0xFFEEF2FF)
            val customCardBorder = if (isDark) Color(0xFF312E81) else Color(0xFFE0E7FF)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, customCardBorder),
                colors = CardDefaults.cardColors(containerColor = customCardBg)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Pulsing-themed AI dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "Local AI Security Insight",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFFC7D2FE) else Color(0xFF312E81),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Inner White bubble
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    ) {
                        Text(
                            text = if (logs.isNotEmpty()) {
                                "\"Sentry has successfully scanned ${logs.size} packet flows. Suspicious telemetry handshakes have been dynamically routed according to systems safety models.\""
                            } else {
                                "\"Detected standard TLS handshakes. Device telemetry channels appear clean. Launch Chat to inspect raw cryptographic socket structures.\""
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            modifier = Modifier.padding(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Launch button
                    Button(
                        onClick = { onNavigateToTab("ai") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "LAUNCH CHEAT CHAT",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            // Live Stats grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Scanned",
                    value = "$totalScanned",
                    subtitle = "Packets",
                    color = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Blocked",
                    value = "$totalBlocked",
                    subtitle = "Telemetry/Cap",
                    color = MaterialTheme.colorScheme.error
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "Data Flow",
                    value = "$totalBytes",
                    subtitle = "inspected kB",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Firewall Activity Log",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { viewModel.clearLogs() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Delete, "Clear Logs", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Empty Database", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        item {
            if (logs.isNotEmpty()) {
                OutlinedTextField(
                    value = logSearchQuery,
                    onValueChange = { logSearchQuery = it },
                    placeholder = { Text("Search logs by app name, package, IP...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (logSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { logSearchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("log_search_field"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL", "ALLOWED", "BLOCKED").forEach { filterType ->
                        val isSelected = logStatusFilter == filterType
                        val containerColor = if (isSelected) {
                            when (filterType) {
                                "ALLOWED" -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                "BLOCKED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            }
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                        
                        val contentColor = if (isSelected) {
                            when (filterType) {
                                "ALLOWED" -> Color(0xFF2E7D32)
                                "BLOCKED" -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.primary
                            }
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        
                        val borderColor = if (isSelected) contentColor else Color.Transparent

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(containerColor)
                               .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable { logStatusFilter = filterType }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = filterType,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("ALL", "TCP", "UDP", "DNS").forEach { proto ->
                        val isSelected = logProtocolFilter == proto
                        val containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                        
                        val contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        
                        val borderColor = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(containerColor)
                               .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable { logProtocolFilter = proto }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = proto,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        if (filteredLogs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (logs.isEmpty()) {
                                "No traffic packets detected. Enable Sentry Firewall above to intercept packet channels."
                            } else {
                                "No activity logs match the selected filter criteria."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filteredLogs) { log ->
                TrafficLogItem(log) {
                    activeInspectedLog = log
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showExtendQuotaDialog) {
        Dialog(onDismissRequest = { showExtendQuotaDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Adjust Daily Traffic Quota",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Enter the bandwidth limit in MB to regulate real-time local socket connections.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Input Field
                    OutlinedTextField(
                        value = typedQuotaText,
                        onValueChange = { newVal ->
                            if (newVal.all { it.isDigit() } && newVal.length <= 6) {
                                typedQuotaText = newVal
                            }
                        },
                        label = { Text("Daily Quota (MB)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quota_input_field"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Preset buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(25, 50, 100, 500).forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable {
                                        val currentVal = typedQuotaText.toDoubleOrNull() ?: 0.0
                                        typedQuotaText = ((currentVal + preset).toInt()).toString()
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+$preset MB",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = { showExtendQuotaDialog = false }
                        ) {
                            Text("Discard", color = MaterialTheme.colorScheme.error)
                        }

                        Button(
                            onClick = {
                                val newLimit = typedQuotaText.toDoubleOrNull() ?: 0.0
                                if (newLimit > 0) {
                                    dailyQuotaMb = newLimit
                                    showExtendQuotaDialog = false
                                    Toast.makeText(context, "Sentry daily quota configured to ${newLimit} MB.", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Please enter a valid positive quota value.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Set Quota")
                        }
                    }
                }
            }
        }
    }

    activeInspectedLog?.let { log ->
        val existingRule = registeredApps.find { it.uid == log.uid }
        val isPinned = existingRule?.isPinned ?: false
        PacketInspectorDialog(
            log = log,
            isPinned = isPinned,
            onTogglePin = { viewModel.togglePinRule(log.uid, log.packageName, log.appName) },
            onDismiss = { activeInspectedLog = null },
            onConsultAi = { query ->
                activeInspectedLog = null
                viewModel.runSecurityLogAudit(query)
                onNavigateToTab("ai")
            }
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = color)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

data class PayloadData(
    val pathUrl: String,
    val headers: List<Pair<String, String>>,
    val parameters: List<Pair<String, String>>,
    val rawHex: String,
    val rawString: String
)

fun generateSimulationPayload(log: TrafficLog): PayloadData {
    val domain = log.domain
    val isHttps = log.protocol.contains("HTTPS") || log.protocol.contains("TLS") || log.protocol.contains("SSL")
    
    if (log.protocol.contains("DNS")) {
        val queryDomain = domain
        val headers = listOf(
            "Transaction ID" to "0x" + String.format("%04X", (log.id * 13 + 41) % 65535L),
            "Flags" to "0x0100 (Standard DNS query)",
            "Questions" to "1",
            "Answer RRs" to "0",
            "Authority RRs" to "0",
            "Additional RRs" to "0"
        )
        val params = listOf(
            "Query Name" to queryDomain,
            "Query Type" to "A (IPv4 Address)",
            "Query Class" to "IN (Internet)"
        )
        
        val dnsRawHex = buildString {
            append("0000  03 2F 01 00 00 01 00 00  00 00 00 00 ")
            val parts = queryDomain.split(".")
            for (part in parts) {
                if (part.isNotEmpty()) {
                    append(String.format("%02X ", part.length))
                    for (char in part) {
                        append(String.format("%02X ", char.code))
                    }
                }
            }
            append("00 00 01 00 01")
        }
        
        val dnsRawString = "UDP DNS Query:\nHeader: Standard Query\nName: ${queryDomain}\nType: A\nClass: IN"
        
        return PayloadData("dns://${queryDomain}:53", headers, params, dnsRawHex, dnsRawString)
    }
    
    var path = "/v2/telemetry"
    val params = mutableListOf<Pair<String, String>>()
    val headers = mutableListOf<Pair<String, String>>()
    
    when {
        domain.contains("facebook") -> {
            path = "/v15.0/act_telemetry"
            params.add("access_token" to "EAAGX8N39DAZB_91AC")
            params.add("event" to "CUSTOM_APP_EVENTS")
            params.add("advertiser_id" to "fa83-918c-3bc8-9fa1")
            params.add("app_ver" to "421.0.0.27")
            params.add("carrier" to "MobileNet LTE")
            
            headers.add("User-Agent" to "FBAndroidSDK/15.0.0")
            headers.add("Content-Type" to "application/x-www-form-urlencoded")
        }
        domain.contains("snapchat") -> {
            path = "/analytics/v2/report"
            params.add("sdk_version" to "11.14.3")
            params.add("user_id" to "usr_91a03ef93b0")
            params.add("screen_name" to "FeedView")
            params.add("latency_ms" to "239")
            headers.add("Content-Type" to "application/json")
            headers.add("X-Snap-SDK" to "Android-11")
        }
        domain.contains("doubleclick") || domain.contains("google") -> {
            path = "/pagead/ads"
            params.add("client" to "ca-app-pub-394025")
            params.add("slotname" to "384910283")
            params.add("format" to "320x50_as")
            params.add("sdk_ver" to "afma-sdk-a-v23.1")
            params.add("device_model" to "DeviceSandbox-Android")
            headers.add("Host" to domain)
        }
        domain.contains("crashlytics") -> {
            path = "/spi/v1/platforms/android/apps"
            params.add("org_id" to "org_a91b40fe9a")
            params.add("sdk_version" to "18.2.1")
            params.add("report_id" to "crash_39ab1e60f")
            params.add("fatal_error" to "false")
            headers.add("X-CRASHLYTICS-API-KEY" to "8fa5bc012ae")
            headers.add("Content-Type" to "application/octet-stream")
        }
        domain.contains("mixpanel") -> {
            path = "/track"
            params.add("data" to "eyJyZWNvcmQiX3R5cGUiOiAiYmVoYXZpb3IifQ==")
            params.add("verbose" to "1")
            params.add("api_key" to "mp_9c28ea9ce107b")
            headers.add("Content-Type" to "application/json")
        }
        else -> {
            path = "/api/v1/telemetry"
            params.add("app_package" to log.packageName)
            params.add("session_id" to "sess_${(log.timestamp % 100000L)}")
            params.add("bytes_sent" to log.bytesSent.toString())
            params.add("bytes_received" to log.bytesReceived.toString())
            params.add("platform" to "Android Emulator Sandbox")
            params.add("secure_flow" to if (isHttps) "true" else "false")
            headers.add("Host" to domain)
            headers.add("User-Agent" to "SentryShield/1.1 (Android)")
            headers.add("Connection" to "keep-alive")
        }
    }
    
    if (headers.none { it.first == "Host" }) {
        headers.add(0, "Host" to domain)
    }
    if (headers.none { it.first == "User-Agent" }) {
        headers.add("User-Agent" to "Dalvik/2.1.0 (Linux; U; Android 14; Mobile)")
    }
    if (headers.none { it.first == "Accept-Encoding" }) {
        headers.add("Accept-Encoding" to "gzip, deflate")
    }
    headers.add("X-SentryShield-Intercept" to "Active Sentry Engine (UID ${log.uid})")
    
    val rawString = buildString {
        append(if (isHttps) "CONNECT $domain:443 HTTP/1.1\r\n" else "POST $path HTTP/1.1\r\n")
        headers.forEach { (k, v) ->
            append("$k: $v\r\n")
        }
        append("\r\n")
        if (params.isNotEmpty()) {
            append("Query/Body Parameters:\n")
            params.forEach { (k, v) ->
                append("  $k = $v\n")
            }
        }
    }
    
    val rawHex = buildString {
        val bytes = rawString.toByteArray(Charsets.UTF_8)
        var row = 0
        while (row < bytes.size && row < 320) {
            val lineNum = String.format("%04X", row)
            append(lineNum).append("  ")
            
            val chunkLen = minOf(16, bytes.size - row)
            for (i in 0 until 16) {
                if (i < chunkLen) {
                    val b = bytes[row + i].toInt() and 0xFF
                    append(String.format("%02X ", b))
                } else {
                    append("   ")
                }
                if (i == 7) append(" ")
            }
            append(" |")
            
            for (i in 0 until chunkLen) {
                val c = bytes[row + i].toInt().toChar()
                if (c in ' '..'~') {
                    append(c)
                } else {
                    append('.')
                }
            }
            append("|\n")
            row += 16
        }
        if (bytes.size > 320) {
            append("... [${bytes.size - 320} more bytes truncated] ...")
        }
    }
    
    val fullPathUrl = if (isHttps) "https://$domain$path" else "http://$domain$path"
    return PayloadData(fullPathUrl, headers, params, rawHex, rawString)
}

@Composable
fun TrafficLogItem(log: TrafficLog, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (log.allowed) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (log.allowed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (log.allowed) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (log.allowed) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = log.appName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = log.domain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${log.bytesSent + log.bytesReceived} B",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = log.protocol,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (log.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun InfoField(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

data class AppInfoItem(
    val packageName: String,
    val label: String,
    val uid: Int
)

@Composable
fun AppFirewallView(
    rules: List<AppRule>,
    onUpdateRuleState: (Int, String) -> Unit,
    onConfigureLimit: (Int) -> Unit,
    viewModel: TrafficViewModel
) {
    val context = LocalContext.current
    val pm = context.packageManager
    var showSelectAppDialog by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var firewallSearchQuery by remember { mutableStateOf("") }

    // Fetch installed launchable apps on IO thread safely without blocking UI
    LaunchedEffect(showSelectAppDialog) {
        if (showSelectAppDialog) {
            withContext(Dispatchers.IO) {
                try {
                    val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
                    val filtered = apps.filter { app ->
                        pm.getLaunchIntentForPackage(app.packageName) != null && app.packageName != context.packageName
                    }.map { app ->
                        AppInfoItem(
                            packageName = app.packageName,
                            label = app.loadLabel(pm).toString(),
                            uid = app.uid
                        )
                    }.sortedBy { it.label.lowercase() }
                    withContext(Dispatchers.Main) {
                        installedApps = filtered
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "App Firewall Controllers",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Block target applications from executing telemetry requests, whitelists connection routing, and enforce real-time bandwidth consumption limit caps.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Registered firewall rules search field
            if (rules.isNotEmpty()) {
                OutlinedTextField(
                    value = firewallSearchQuery,
                    onValueChange = { firewallSearchQuery = it },
                    placeholder = { Text("Search firewall rules...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (firewallSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { firewallSearchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firewall_rules_search"),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val filteredAndSortedRules = remember(rules, firewallSearchQuery) {
                rules.filter {
                    (it.appName ?: "").contains(firewallSearchQuery, ignoreCase = true) ||
                            (it.packageName ?: "").contains(firewallSearchQuery, ignoreCase = true)
                }.sortedWith(
                    compareByDescending<AppRule> { it.isPinned }
                        .thenBy { it.appName.lowercase() }
                )
            }

            if (filteredAndSortedRules.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (rules.isEmpty()) {
                            "No apps registered. Click the floating 'ADD APP' button below to register a package and monitor its socket telemetry."
                        } else {
                            "No registered rules match '$firewallSearchQuery'."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredAndSortedRules) { rule ->
                        AppRuleItem(
                            rule = rule,
                            onUpdateRuleState = onUpdateRuleState,
                            onConfigureLimit = { onConfigureLimit(rule.uid) },
                            onTogglePin = { viewModel.togglePinRule(rule.uid) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(84.dp)) // Padding to stay clear of the FAB
                    }
                }
            }
        }

        // Floating Action Button to Add / Custom Scan Apps
        FloatingActionButton(
            onClick = { showSelectAppDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .testTag("add_app_rule_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add Installed App")
                Text("ADD APP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Installed App Selector Dialog
    if (showSelectAppDialog) {
        Dialog(onDismissRequest = { showSelectAppDialog = false; searchQuery = "" }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Add Application to Sentry",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Select a launchable package on this device to monitor and regulate firewall rules.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("app_search_field"),
                        placeholder = { Text("Search installed packages...") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear Search")
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredApps = installedApps.filter { app ->
                        app.label.contains(searchQuery, ignoreCase = true) || app.packageName.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredApps.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (installedApps.isEmpty()) "Loading devices index..." else "No matching applications found",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredApps) { app ->
                                val appLabel = app.label
                                val isAlreadyAdded = rules.any { it.packageName == app.packageName }
                                
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isAlreadyAdded) {
                                                viewModel.registerAppRule(app.uid, app.packageName, appLabel)
                                                Toast.makeText(context, "$appLabel registered successfully.", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "$appLabel is already monitored.", Toast.LENGTH_SHORT).show()
                                            }
                                            showSelectAppDialog = false
                                            searchQuery = ""
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isAlreadyAdded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = appLabel,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isAlreadyAdded) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = app.packageName,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.outline,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (isAlreadyAdded) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = "MONITORED",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.AddCircle,
                                                contentDescription = "Add App",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showSelectAppDialog = false; searchQuery = "" }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppRuleItem(
    rule: AppRule,
    onUpdateRuleState: (Int, String) -> Unit,
    onConfigureLimit: () -> Unit,
    onTogglePin: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rule.appName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = rule.appName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (rule.isPinned) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "PINNED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        Text(
                            text = rule.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Lock Toggle for Pinning
                    IconButton(
                        onClick = onTogglePin,
                        modifier = Modifier.testTag("pin_rule_btn_for_${rule.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Pin App Rule at the top",
                            tint = if (rule.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        )
                    }

                    // Limit Configure Gear Icon
                    IconButton(
                        onClick = onConfigureLimit,
                        modifier = Modifier.testTag("cap_settings_for_${rule.uid}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Limits Config",
                            tint = if (rule.bytesCap > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar if limits preset
            if (rule.bytesCap > 0L) {
                val progress = (rule.bytesUsed.toFloat() / rule.bytesCap.toFloat()).coerceIn(0f, 1f)
                val isLimitExceeded = rule.bytesUsed >= rule.bytesCap

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Bandwidth: ${rule.bytesUsed / 1024}kB used",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = if (isLimitExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = "Cap: ${rule.bytesCap / (1024 * 1024)}MB",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = if (isLimitExceeded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    if (isLimitExceeded) {
                        Text(
                            text = "DANGER: App completely throttled from internet access.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Text(
                    text = "Bandwidth: ${rule.bytesUsed / 1024}kB consumed (No Cap Enforced)",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Stateful selector buttons for action rules (Fully dynamic with dynamic light/dark schemas)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val states = listOf("ALLOWED", "WHITELISTED", "BLOCKED")
                states.forEach { s ->
                    val selected = rule.ruleState == s
                    val colorGroup = when (s) {
                        "ALLOWED" -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                        "WHITELISTED" -> Pair(Color(0xFF2E7D32), Color.White)
                        "BLOCKED" -> Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
                        else -> Pair(MaterialTheme.colorScheme.outline, MaterialTheme.colorScheme.onSurface)
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .testTag("rule_btn_${rule.uid}_$s")
                            .clickable { onUpdateRuleState(rule.uid, s) },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) colorGroup.first else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (selected) colorGroup.first else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp,
                                color = if (selected) colorGroup.second else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtificialIntelView(
    aiResponse: String,
    isAiLoading: Boolean,
    isLocalSupported: Boolean,
    onAuditRequest: (String) -> Unit
) {
    var queryText by remember { mutableStateOf("") }
    val samples = listOf(
        "Generate a diagnostic networks report",
        "Is background telemetry active right now?",
        "Why is there a huge volume usage spike?"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Dynamic Security Sandbox (\"Cheat Chat\")",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Analyze network socket headers, trace tracking telemetry, and identify suspicious traffic profiles on-device using local AI models or private cloud fallback.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))

            // AI Status Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isLocalSupported) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                    )
                    .border(
                        1.dp,
                        if (isLocalSupported) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isLocalSupported) Icons.Filled.CheckCircle else Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = if (isLocalSupported) Color(0xFF2E7D32) else Color(0xFFEF6C00),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isLocalSupported) "Local hardware Gemini Nano fully active" else "BYOK Private Cloud Fallback active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isLocalSupported) Color(0xFF2E7D32) else Color(0xFFEF6C00)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // Interactive Chat Window
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (isAiLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Running cryptographic log inspection...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else if (aiResponse.isEmpty()) {
                        Text(
                            text = "Audit Console Terminal Ready.\n\nType your question below or click a template prompt to construct your local environment scan.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    } else {
                        SelectionContainer {
                            Text(
                                text = aiResponse,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().testTag("ai_audit_response")
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        item {
            // Suggestion chips
            Text(
                text = "Templates Prompts:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                samples.take(2).forEach { s ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onAuditRequest(s) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = s,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            // Query Input
            OutlinedTextField(
                value = queryText,
                onValueChange = { queryText = it },
                label = { Text("Query security specialist...") },
                placeholder = { Text("Is graphing telemetry secure?") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (queryText.trim().isNotEmpty()) {
                        onAuditRequest(queryText.trim())
                        queryText = ""
                    }
                }),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (queryText.trim().isNotEmpty()) {
                                onAuditRequest(queryText.trim())
                                queryText = ""
                            }
                        },
                        modifier = Modifier.testTag("ai_send_query_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Query Agent")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_query_input"),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Simple helper fallback wrapper if Compose SelectionContainer is missing
@Composable
fun SelectionContainer(content: @Composable () -> Unit) {
    androidx.compose.foundation.text.selection.SelectionContainer {
        content()
    }
}

@Composable
fun PacketInspectorDialog(
    log: TrafficLog,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onDismiss: () -> Unit,
    onConsultAi: (String) -> Unit
) {
    val context = LocalContext.current
    val payloadData = remember(log) { generateSimulationPayload(log) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Info, 1 = Params, 2 = Hex

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (log.allowed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (log.allowed) "PACKET ALLOWED" else "PACKET BLOCKED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (log.allowed) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = "Packet Inspector",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onTogglePin,
                            modifier = Modifier.testTag("pin_app_header_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = if (isPinned) "Unpin App" else "Pin App",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close Inspector")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Tab Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    val tabNames = listOf("General Info", "HTTP Headers", "Payload Hex")
                    tabNames.forEachIndexed { idx, name ->
                        val active = idx == selectedTab
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = idx },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Details
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> {
                            val scrollState = rememberScrollState()
                            Column(
                                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                InfoField("Target Name", log.appName)
                                InfoField("Bundle Package", log.packageName)
                                InfoField("Caller App UID", "u" + log.uid)
                                InfoField("Socket Port", if (log.protocol == "DNS") "53" else "443 (TLS Secured)")
                                InfoField("IP Endpoint", log.ip)
                                InfoField("Domain Context", log.domain)
                                InfoField("Direct Path", payloadData.pathUrl)
                                InfoField("Protocol Intercepted", log.protocol)
                                InfoField("Data Sent", "${log.bytesSent} bytes")
                                InfoField("Data Received", "${log.bytesReceived} bytes")
                                InfoField("Timestamp", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(log.timestamp)))
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Info,
                                            contentDescription = null,
                                            tint = if (log.allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (log.allowed) 
                                                "Active transmission bypasses local throttling filters safely." 
                                                else "This socket query has been dynamically terminated by SentryShield rules.",
                                            fontSize = 9.sp,
                                            color = if (log.allowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item {
                                    Text("Captured HTTP Custom Headers", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                items(payloadData.headers) { (key, value) ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(key, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text(value, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                        }
                                    }
                                }
                                if (payloadData.parameters.isNotEmpty()) {
                                    item {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text("Captured Body Parameters", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                    items(payloadData.parameters) { (key, value) ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(key, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFC2185B))
                                                Text(value, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        2 -> {
                            Card(
                                modifier = Modifier.fillMaxSize(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                ) {
                                    val scrollState = rememberScrollState()
                                    Text(
                                        text = payloadData.rawHex,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Export / Share log button
                    OutlinedButton(
                        onClick = {
                            val textToExport = buildString {
                                append("===== NetSentry Traffic Log Intercept =====\n")
                                append("App Name: ${log.appName}\n")
                                append("Package Bundle ID: ${log.packageName}\n")
                                append("Process UID: ${log.uid}\n")
                                append("Destination IP/Host: ${log.ip}\n")
                                append("Target URL: ${payloadData.pathUrl}\n")
                                append("Protocol Header: ${log.protocol}\n")
                                append("Bytes: ${log.bytesSent} B sent / ${log.bytesReceived} B received\n\n")
                                append("----- Raw Captured Header Payload -----\n")
                                append(payloadData.rawString)
                            }
                            
                            try {
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("NetSentry Security Log", textToExport)
                                clipboardManager.setPrimaryClip(clip)
                                Toast.makeText(context, "Log details copied to clipboard!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            try {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Sentry Firewall Log: ${log.appName}")
                                    putExtra(Intent.EXTRA_TEXT, textToExport)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Threat Log"))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Export Log", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("EXPORT", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Cheat Chat with AI button
                    Button(
                        onClick = {
                            val aiQueryText = """
                                Explain traffic payload behavior for App: ${log.appName} (${log.packageName}), connecting to domain URL: ${payloadData.pathUrl} over socket protocol ${log.protocol}.
                                Captured bytes sent: ${log.bytesSent} B, received: ${log.bytesReceived} B.
                                Raw intercepted headers:
                                ${payloadData.headers.take(4).joinToString("\n") { "${it.first}: ${it.second}" }}
                                Is this app leaking user data or communicating with dangerous endpoints? Assess privacy threats.
                            """.trimIndent().trim()
                            onConsultAi(aiQueryText)
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Filled.Face, contentDescription = "Consult AI", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CHEAT CHAT", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    // Close button
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f)
                    ) {
                        Text("DISMISS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
