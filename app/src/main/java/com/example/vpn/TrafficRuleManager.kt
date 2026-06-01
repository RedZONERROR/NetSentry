package com.example.vpn

import android.content.Context
import android.util.Log
import com.example.db.AppRule
import com.example.db.NetSentryDao
import com.example.db.NetSentryDatabase
import com.example.db.TrafficLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class TrafficRuleManager private constructor(context: Context) {

    private val db = NetSentryDatabase.getDatabase(context)
    private val dao = db.netSentryDao()
    private val appScope = CoroutineScope(Dispatchers.IO)

    // Fast in-memory lookup for VPN forwarding speed
    private val rulesMap = ConcurrentHashMap<Int, AppRule>()

    // Event flow for exceeding quota to trigger Extend Dialog in UI
    private val _quotaExceededEvent = MutableSharedFlow<QuotaExceededEvent>(extraBufferCapacity = 10)
    val quotaExceededEvent: SharedFlow<QuotaExceededEvent> = _quotaExceededEvent

    data class QuotaExceededEvent(
        val uid: Int,
        val packageName: String,
        val appName: String,
        val bytesUsed: Long,
        val bytesCap: Long
    )

    init {
        // Load existing rules into memory Cache
        appScope.launch {
            try {
                val rules = dao.getAllRules()
                for (rule in rules) {
                    rulesMap[rule.uid] = rule
                }
                Log.d("TrafficRuleManager", "Loaded ${rules.size} firewall rules into memory cache.")
            } catch (e: Exception) {
                Log.e("TrafficRuleManager", "Failed loading rules into memory mapping: ${e.message}")
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TrafficRuleManager? = null

        fun getInstance(context: Context): TrafficRuleManager {
            return INSTANCE ?: synchronized(this) {
                val instance = TrafficRuleManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Check if a packet from/to this app UID should be blocked immediately.
     * Keeps track of bandwidth consumption and updates bytes processed.
     */
    fun shouldBlockAndLog(
        uid: Int,
        packageName: String,
        appName: String,
        bytes: Long,
        domain: String,
        ip: String,
        protocol: String,
        isOutgoing: Boolean
    ): Boolean {
        val rule = rulesMap[uid] ?: run {
            // Unregistered app: create default ALLOWED rule
            val newRule = AppRule(
                uid = uid,
                packageName = packageName,
                appName = appName,
                ruleState = "ALLOWED",
                bytesCap = 0L,
                bytesUsed = 0L,
                isThrottled = false
            )
            rulesMap[uid] = newRule
            appScope.launch {
                dao.insertRule(newRule)
            }
            newRule
        }

        // 1. Check Firewall Rule State
        if (rule.ruleState == "BLOCKED") {
            logTraffic(uid, packageName, appName, domain, ip, protocol, bytes, isOutgoing, allowed = false)
            return true
        }

        // 2. Check Bandwidth Quotas
        if (rule.bytesCap > 0L) {
            val newUsed = rule.bytesUsed + bytes
            if (newUsed >= rule.bytesCap) {
                // Trigger action once when crossed
                if (!rule.isThrottled) {
                    val updatedRule = rule.copy(bytesUsed = newUsed, isThrottled = true)
                    rulesMap[uid] = updatedRule
                    appScope.launch {
                        dao.updateUsage(uid, newUsed, isThrottled = true)
                    }

                    // Broadcast quota exceeded event to trigger Extend Dialog / System Alerts
                    appScope.launch {
                        _quotaExceededEvent.emit(
                            QuotaExceededEvent(
                                uid = uid,
                                packageName = packageName,
                                appName = appName,
                                bytesUsed = newUsed,
                                bytesCap = rule.bytesCap
                            )
                        )
                    }
                } else {
                    // Update byte counters nonetheless
                    val updatedRule = rule.copy(bytesUsed = newUsed)
                    rulesMap[uid] = updatedRule
                    appScope.launch {
                        dao.updateUsage(uid, newUsed, isThrottled = true)
                    }
                }

                // If quota exceeded, block packet if not whitelisted or drop silently
                logTraffic(uid, packageName, appName, domain, ip, protocol, bytes, isOutgoing, allowed = false)
                return true
            } else {
                // Update normal use-counters
                val updatedRule = rule.copy(bytesUsed = newUsed)
                rulesMap[uid] = updatedRule
                appScope.launch {
                    dao.updateUsage(uid, newUsed, isThrottled = false)
                }
            }
        } else {
            // Update counts on unlimited rules as well
            if (bytes > 0) {
                val newUsed = rule.bytesUsed + bytes
                val updatedRule = rule.copy(bytesUsed = newUsed)
                rulesMap[uid] = updatedRule
                appScope.launch {
                    dao.updateRule(updatedRule)
                }
            }
        }

        logTraffic(uid, packageName, appName, domain, ip, protocol, bytes, isOutgoing, allowed = true)
        return false
    }

    private fun logTraffic(
        uid: Int,
        packageName: String,
        appName: String,
        domain: String,
        ip: String,
        protocol: String,
        bytes: Long,
        isOutgoing: Boolean,
        allowed: Boolean
    ) {
        val bytesSent = if (isOutgoing) bytes else 0L
        val bytesReceived = if (!isOutgoing) bytes else 0L
        appScope.launch {
            dao.insertTrafficLog(
                TrafficLog(
                    uid = uid,
                    packageName = packageName,
                    appName = appName,
                    domain = domain,
                    ip = ip,
                    protocol = protocol,
                    bytesSent = bytesSent,
                    bytesReceived = bytesReceived,
                    allowed = allowed
                )
            )
        }
    }

    /**
     * Set a custom firewall state for an app.
     */
    suspend fun setRuleState(uid: Int, state: String) = withContext(Dispatchers.IO) {
        val currentRule = rulesMap[uid]
        if (currentRule != null) {
            val updated = currentRule.copy(ruleState = state)
            rulesMap[uid] = updated
            dao.insertRule(updated)
        }
    }

    /**
     * Register or pre-configure a rule manually.
     */
    suspend fun registerAppRule(uid: Int, packageName: String, appName: String, ruleState: String = "ALLOWED") = withContext(Dispatchers.IO) {
        val existing = rulesMap[uid]
        val rule = existing?.copy(ruleState = ruleState) ?: AppRule(
            uid = uid,
            packageName = packageName,
            appName = appName,
            ruleState = ruleState,
            bytesCap = 0L,
            bytesUsed = 0L,
            isThrottled = false
        )
        rulesMap[uid] = rule
        dao.insertRule(rule)
    }

    /**
     * Extend/Update app limit configuration.
     */
    suspend fun updateAppLimit(uid: Int, limitBytes: Long) = withContext(Dispatchers.IO) {
        val currentRule = rulesMap[uid]
        if (currentRule != null) {
            val updated = currentRule.copy(bytesCap = limitBytes, isThrottled = currentRule.bytesUsed >= limitBytes && limitBytes > 0)
            rulesMap[uid] = updated
            dao.insertRule(updated)
        }
    }

    /**
     * Toggle pinning/favorite status of an app rule.
     */
    suspend fun togglePinAppRule(uid: Int) = withContext(Dispatchers.IO) {
        val currentRule = rulesMap[uid]
        if (currentRule != null) {
            val updated = currentRule.copy(isPinned = !currentRule.isPinned)
            rulesMap[uid] = updated
            dao.insertRule(updated)
        }
    }

    /**
     * Handles decisions from the "Quota Exceeded/Extend Dialog":
     * 1. Extend Limit: Adds allowance.
     * 2. End Connection: Drops all VPN packets for that app (converts state to BLOCKED).
     * 3. No Data Mode: Keeps blocked state while resetting counters, dropping everything silently until manually reset.
     */
    suspend fun resolveQuotaExceeded(uid: Int, choice: QuotaResolution) = withContext(Dispatchers.IO) {
        val currentRule = rulesMap[uid] ?: return@withContext
        val updated = when (choice) {
            is QuotaResolution.Extend -> {
                val newCap = currentRule.bytesCap + choice.allowanceBytes
                currentRule.copy(bytesCap = newCap, isThrottled = false)
            }
            QuotaResolution.EndConnection -> {
                currentRule.copy(ruleState = "BLOCKED")
            }
            QuotaResolution.NoDataMode -> {
                // Drop all packets silently. We transition the state to "BLOCKED" for effective drop.
                currentRule.copy(ruleState = "BLOCKED")
            }
        }
        rulesMap[uid] = updated
        dao.insertRule(updated)
    }

    sealed class QuotaResolution {
        data class Extend(val allowanceBytes: Long) : QuotaResolution()
        object EndConnection : QuotaResolution()
        object NoDataMode : QuotaResolution()
    }
}
