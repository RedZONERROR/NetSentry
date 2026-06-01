package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiAnalysisEngine
import com.example.db.AppRule
import com.example.db.NetSentryDatabase
import com.example.db.TrafficLog
import com.example.vpn.TrafficRuleManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class TrafficViewModel(application: Application) : AndroidViewModel(application) {

    private val db = NetSentryDatabase.getDatabase(application)
    private val dao = db.netSentryDao()
    private val ruleManager = TrafficRuleManager.getInstance(application)
    private val aiEngine = AiAnalysisEngine(application)

    // Flows from database
    val appRules: StateFlow<List<AppRule>> = dao.getAllRulesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trafficLogs: StateFlow<List<TrafficLog>> = dao.getRecentTrafficLogsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // AI Query States
    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    // Quota Exceeded Alerts (Extend Dialog state)
    private val _activeQuotaViolation = MutableStateFlow<TrafficRuleManager.QuotaExceededEvent?>(null)
    val activeQuotaViolation: StateFlow<TrafficRuleManager.QuotaExceededEvent?> = _activeQuotaViolation.asStateFlow()

    // BYOK Key configurations
    private val _apiKeyField = MutableStateFlow(aiEngine.getActiveApiKey())
    val apiKeyField: StateFlow<String> = _apiKeyField.asStateFlow()

    // AICore / Dynamic support status
    val isLocalAiSupported = aiEngine.isLocalAiCoreSupported()

    init {
        // Collect quota exceeded events from rule manager to display the Extend Dialog instantly
        viewModelScope.launch {
            ruleManager.quotaExceededEvent.collect { event ->
                _activeQuotaViolation.value = event
            }
        }
    }

    fun updateFirewallRule(uid: Int, state: String) {
        viewModelScope.launch {
            ruleManager.setRuleState(uid, state)
        }
    }

    fun togglePinRule(uid: Int, packageName: String? = null, appName: String? = null) {
        viewModelScope.launch {
            if (packageName != null && appName != null) {
                val exists = appRules.value.any { it.uid == uid }
                if (!exists) {
                    ruleManager.registerAppRule(uid, packageName, appName, "ALLOWED")
                }
            }
            ruleManager.togglePinAppRule(uid)
        }
    }

    fun registerAppRule(uid: Int, packageName: String, appName: String, ruleState: String = "ALLOWED") {
        viewModelScope.launch {
            ruleManager.registerAppRule(uid, packageName, appName, ruleState)
        }
    }

    fun setAppLimit(uid: Int, limitBytes: Long) {
        viewModelScope.launch {
            ruleManager.updateAppLimit(uid, limitBytes)
        }
    }

    fun saveApiKey(key: String) {
        aiEngine.saveCustomApiKey(key)
        _apiKeyField.value = key
    }

    fun dismissQuotaViolation() {
        _activeQuotaViolation.value = null
    }

    fun resolveQuota(uid: Int, choice: TrafficRuleManager.QuotaResolution) {
        viewModelScope.launch {
            ruleManager.resolveQuotaExceeded(uid, choice)
            _activeQuotaViolation.value = null
        }
    }

    fun runSecurityLogAudit(question: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResponse.value = "Analyzing traffic profiles & tracking patterns locally..."
            val logs = dao.getRecentTrafficLogs(30)
            val response = aiEngine.analyzeTrafficLogs(logs, question)
            _aiResponse.value = response
            _isAiLoading.value = false
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            dao.clearLogs()
        }
    }
}
