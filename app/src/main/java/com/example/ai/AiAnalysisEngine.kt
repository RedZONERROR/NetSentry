package com.example.ai

import android.content.Context
import android.util.Log
import com.example.db.TrafficLog
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AiAnalysisEngine(private val context: Context) {

    private val sharedPrefs = context.getSharedPreferences("NetSentrySettings", Context.MODE_PRIVATE)

    companion object {
        private const val MODEL_NAME = "gemini-3.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Store custom Bring Your Own Key (BYOK)
     */
    fun saveCustomApiKey(key: String) {
        sharedPrefs.edit().putString("custom_gemini_api_key", key.trim()).apply()
    }

    /**
     * Get target Gemini API Key. Fallback to BuildConfig if custom key is not configured.
     */
    fun getActiveApiKey(): String {
        val customKey = sharedPrefs.getString("custom_gemini_api_key", "")
        if (!customKey.isNullOrEmpty()) {
            return customKey
        }
        // Fallback to BuildConfig provided by AI Studio Secrets Panel
        try {
            return BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            return ""
        }
    }

    /**
     * Detects if device has on-device Gemini Nano capability.
     * Since Android AICore hardware check requires specialized system-level bindings,
     * we perform an architectural capability check and default to standard Cloud API fallback.
     */
    fun isLocalAiCoreSupported(): Boolean {
        // AICore is supported on premium/hardware-accelerated Android devices (e.g., Pixel 8+, Samsung S24+)
        // We simulate capabilities detection base on manufacturer profile or hardware specs.
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        return (manufacturer.contains("google") && (model.contains("pixel 8") || model.contains("pixel 9") || model.contains("pixel 10") || model.contains("pro"))) ||
                (manufacturer.contains("samsung") && (model.contains("s24") || model.contains("s25") || model.contains("s26")))
    }

    /**
     * Analyze traffic logs with Gemini AI.
     * Constructs a strong, safety-conscious security sandbox analysis report.
     */
    suspend fun analyzeTrafficLogs(logs: List<TrafficLog>, userQuestion: String): String = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        if (apiKey.isEmpty()) {
            return@withContext "API Configuration Check Failed:\n\nPlease enter your custom Gemini API key or configure the GEMINI_API_KEY secret in the AI Studio environment."
        }

        val logsSummary = logs.joinToString("\n") { log ->
            "Timestamp: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(log.timestamp)} " +
            "| App: ${log.appName} (${log.packageName}) " +
            "| Hostname: ${log.domain} | IP: ${log.ip} | Prot: ${log.protocol} " +
            "| Sent: ${log.bytesSent}B | Recv: ${log.bytesReceived}B | Allowed: ${log.allowed}"
        }

        val designPrompt = """
            You are NetSentry AI, a local Android Security Auditing Specialist. 
            Analyze the following captured background network traffic logs and address the user's specific query.
            
            Log Entries Audited:
            $logsSummary
            
            User's Query:
            $userQuestion
            
            Response Format:
            Provide a short, extremely scannable, design-focused security audit report. Highlight:
            1. Identified background trackers, telemetry servers, or insecure connections (HTTP plain text).
            2. Anomalies: unusual traffic volume spikes per application UID.
            3. Actionable Security Recommendations (e.g. Whitelisting rules, blocking, or daily data limits to configure).
            Keep description friendly, brief, and highly technical yet understandable.
        """.trimIndent()

        // Construct JSON Request Payload according to gemini REST specifications
        try {
            val requestBodyJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    val contentObj = JSONObject().apply {
                        val partsArray = JSONArray().apply {
                            val partObj = JSONObject().apply {
                                put("text", designPrompt)
                            }
                            put(partObj)
                        }
                        put("parts", partsArray)
                    }
                    put(contentObj)
                }
                put("contents", contentsArray)
            }

            val requestBody = requestBodyJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val urlWithKey = "$BASE_URL?key=$apiKey"
            val request = Request.Builder()
                .url(urlWithKey)
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext "Network Audit Exception: Gemini Engine returned Error Code ${response.code}.\nDetails: ${response.body?.string()}"
            }

            val responseBody = response.body?.string() ?: return@withContext "Null response body from AI services."
            val jsonResponse = JSONObject(responseBody)
            val candidates = jsonResponse.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidateObj = candidates.getJSONObject(0)
                val contentObj = candidateObj.optJSONObject("content")
                if (contentObj != null) {
                    val partsArray = contentObj.optJSONArray("parts")
                    if (partsArray != null && partsArray.length() > 0) {
                        return@withContext partsArray.getJSONObject(0).optString("text", "No readable analysis returned.")
                    }
                }
            }
            return@withContext "Error: Failed to extract response blocks from Gemini response payload."
        } catch (e: Exception) {
            Log.e("AiAnalysisEngine", "AI Query Network Failure: ${e.message}")
            return@withContext "Local API Connection Timeout: Failed to reach standard Google Generative AI Services.\nCheck your internet connectivity or security certificate.\nDetails: ${e.localizedMessage}"
        }
    }
}
