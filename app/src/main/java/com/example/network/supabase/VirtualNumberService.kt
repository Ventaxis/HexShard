package com.example.network.supabase

import android.content.Context
import com.example.data.SecurePrefsManager
import com.example.util.VirtualNumberGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

sealed class VirtualNumberReservationResult {
    data class Success(val raw8Digits: String, val formatted: String, val expiresAt: Long) : VirtualNumberReservationResult()
    data class Error(val message: String) : VirtualNumberReservationResult()
}

sealed class VirtualNumberConfirmationResult {
    data class Success(val raw8Digits: String, val formatted: String) : VirtualNumberConfirmationResult()
    data class Error(val message: String) : VirtualNumberConfirmationResult()
}

sealed class ActiveVirtualNumberState {
    data class Active(val raw8Digits: String, val formatted: String) : ActiveVirtualNumberState()
    object NoActiveNumber : ActiveVirtualNumberState()
    data class TemporaryError(val message: String) : ActiveVirtualNumberState()
}

object VirtualNumberService {

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Atomically reserves a +999 identity number via Supabase Edge Function or RPC.
     * STRICT NO-FALLBACK: A network or server error returns Error immediately.
     * No local numbers are ever generated or assumed available on the client.
     */
    suspend fun reserveCandidateNumber(
        userId: String,
        accessToken: String,
        preferred: String? = null,
        context: Context? = null
    ): VirtualNumberReservationResult = withContext(Dispatchers.IO) {
        if (userId.isBlank() || accessToken.isBlank()) {
            return@withContext VirtualNumberReservationResult.Error("Authentication is required to reserve a number")
        }

        val baseUrl = SupabaseConfig.getBaseUrl()
        val anonKey = SupabaseConfig.getAnonKey(context)

        // 1. Try Edge Function /functions/v1/reserve-virtual-number
        try {
            val edgePayload = JSONObject().apply {
                if (!preferred.isNullOrBlank()) put("preferred", preferred)
            }
            val edgeReq = Request.Builder()
                .url("$baseUrl/functions/v1/reserve-virtual-number")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(edgePayload.toString().toRequestBody(JSON_MEDIA))
                .build()

            val edgeResp = httpClient.newCall(edgeReq).execute()
            val edgeBody = edgeResp.body?.string() ?: ""

            if (edgeResp.isSuccessful && edgeBody.isNotEmpty()) {
                val json = JSONObject(edgeBody)
                val rawDigits = json.optString("raw_number", json.optString("number"))
                val formatted = json.optString("formatted", VirtualNumberGenerator.format8Digits(rawDigits))
                val expiresAt = json.optLong("expires_at", System.currentTimeMillis() + 600_000L)

                if (rawDigits.length == 8 && rawDigits.all { it.isDigit() }) {
                    return@withContext VirtualNumberReservationResult.Success(
                        raw8Digits = rawDigits,
                        formatted = formatted,
                        expiresAt = expiresAt
                    )
                }
            } else if (edgeResp.code == 409 || edgeResp.code == 429) {
                val err = parseErrorMessage(edgeBody, "No virtual numbers currently available")
                return@withContext VirtualNumberReservationResult.Error(err)
            }
        } catch (e: Exception) {
            Timber.d("reserve-virtual-number Edge Function call failed: ${e.message}")
        }

        // 2. Fallback to atomic RPC reserve_hex_number
        try {
            // PostgREST maps JSON fields to named parameters.
            // If preferred is empty, send empty JSON {} so PostgreSQL DEFAULT NULL is triggered.
            val rpcPayload = JSONObject().apply {
                if (!preferred.isNullOrBlank()) {
                    put("p_preferred", preferred.trim())
                }
            }
            val rpcReq = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/reserve_hex_number")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(rpcPayload.toString().toRequestBody(JSON_MEDIA))
                .build()

            var resp = httpClient.newCall(rpcReq).execute()
            var body = resp.body?.string() ?: ""

            // Fallback attempt: if parameterized call failed with 404 / schema cache error, try parameterless call {}
            if ((resp.code == 404 || body.contains("schema cache") || body.contains("Could not find the function")) && rpcPayload.length() > 0) {
                Timber.w("Parameterized RPC failed with schema mismatch ($body), attempting parameterless reserve_hex_number()")
                val fallbackReq = Request.Builder()
                    .url("$baseUrl/rest/v1/rpc/reserve_hex_number")
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $accessToken")
                    .header("Content-Type", "application/json")
                    .post("{}".toRequestBody(JSON_MEDIA))
                    .build()
                resp = httpClient.newCall(fallbackReq).execute()
                body = resp.body?.string() ?: ""
            }

            if (resp.isSuccessful && body.isNotEmpty()) {
                val json = JSONObject(body)
                val rawField = json.optString("raw_number", json.optString("number"))
                var cleanDigits = rawField.filter { it.isDigit() }
                if (cleanDigits.length == 11 && cleanDigits.startsWith("999")) {
                    cleanDigits = cleanDigits.substring(3)
                }
                val formatted = json.optString("formatted", VirtualNumberGenerator.format8Digits(cleanDigits))
                val expiresAt = json.optLong("expires_at", System.currentTimeMillis() + 600_000L)

                if (cleanDigits.length == 8) {
                    return@withContext VirtualNumberReservationResult.Success(
                        raw8Digits = cleanDigits,
                        formatted = formatted,
                        expiresAt = expiresAt
                    )
                }
            }

            if (resp.code == 409 || resp.code == 429) {
                val err = parseErrorMessage(body, "No virtual numbers currently available")
                return@withContext VirtualNumberReservationResult.Error(err)
            }

            Timber.w("RPC reserve_hex_number returned code ${resp.code}: $body")
            return@withContext VirtualNumberReservationResult.Error(parseErrorMessage(body, "Server reservation failed"))
        } catch (e: Exception) {
            Timber.e(e, "RPC reserve_hex_number network failure")
            return@withContext VirtualNumberReservationResult.Error("Network error during number reservation: ${e.message}")
        }
    }

    /**
     * Confirms the reserved +999 number and activates it on the server.
     * STRICT: Only returns success if server database marks it active.
     */
    suspend fun confirmVirtualNumber(
        userId: String,
        accessToken: String,
        raw8Digits: String,
        context: Context
    ): Boolean = withContext(Dispatchers.IO) {
        when (val res = confirmVirtualNumberDetailed(userId, accessToken, raw8Digits, context)) {
            is VirtualNumberConfirmationResult.Success -> true
            is VirtualNumberConfirmationResult.Error -> false
        }
    }

    suspend fun confirmVirtualNumberDetailed(
        userId: String,
        accessToken: String,
        raw8Digits: String,
        context: Context
    ): VirtualNumberConfirmationResult = withContext(Dispatchers.IO) {
        val cleanDigits = raw8Digits.filter { it.isDigit() }
        if (cleanDigits.length != 8) {
            return@withContext VirtualNumberConfirmationResult.Error("Number must contain exactly 8 digits")
        }

        val baseUrl = SupabaseConfig.getBaseUrl()
        val anonKey = SupabaseConfig.getAnonKey(context)
        val formatted = VirtualNumberGenerator.format8Digits(cleanDigits)

        // 1. Try Edge Function /functions/v1/confirm-virtual-number
        try {
            val edgePayload = JSONObject().apply {
                put("raw_number", cleanDigits)
            }
            val edgeReq = Request.Builder()
                .url("$baseUrl/functions/v1/confirm-virtual-number")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(edgePayload.toString().toRequestBody(JSON_MEDIA))
                .build()

            val edgeResp = httpClient.newCall(edgeReq).execute()
            val edgeBody = edgeResp.body?.string() ?: ""

            if (edgeResp.isSuccessful && edgeBody.isNotEmpty()) {
                val json = JSONObject(edgeBody)
                val confirmed = json.optBoolean("confirmed", true)
                if (confirmed) {
                    val serverFormatted = json.optString("formatted", formatted)
                    SessionManager.updateVirtualNumber(context, cleanDigits)
                    return@withContext VirtualNumberConfirmationResult.Success(cleanDigits, serverFormatted)
                }
            } else if (edgeResp.code == 400 || edgeResp.code == 403 || edgeResp.code == 409) {
                return@withContext VirtualNumberConfirmationResult.Error(
                    parseErrorMessage(edgeBody, "Server rejected number confirmation")
                )
            }
        } catch (e: Exception) {
            Timber.d("confirm-virtual-number Edge Function call failed: ${e.message}")
        }

        // 2. Authoritative atomic RPC confirm_hex_number
        try {
            val rpcPayload = JSONObject().apply {
                put("p_raw_number", cleanDigits)
            }
            val rpcReq = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/confirm_hex_number")
                .header("apikey", anonKey)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .post(rpcPayload.toString().toRequestBody(JSON_MEDIA))
                .build()

            val rpcResp = httpClient.newCall(rpcReq).execute()
            val rpcBody = rpcResp.body?.string() ?: ""

            if (rpcResp.isSuccessful && rpcBody.isNotEmpty()) {
                val json = JSONObject(rpcBody)
                val confirmed = json.optBoolean("confirmed", true)
                if (confirmed) {
                    val serverFormatted = json.optString("formatted", formatted)
                    SessionManager.updateVirtualNumber(context, cleanDigits)
                    return@withContext VirtualNumberConfirmationResult.Success(cleanDigits, serverFormatted)
                }
            } else {
                val err = parseErrorMessage(rpcBody, "Number confirmation failed on server")
                return@withContext VirtualNumberConfirmationResult.Error(err)
            }
        } catch (e: Exception) {
            Timber.e(e, "RPC confirm_hex_number failure")
            return@withContext VirtualNumberConfirmationResult.Error("Network error during number confirmation: ${e.message}")
        }

        VirtualNumberConfirmationResult.Error("Failed to confirm number on server")
    }

    /**
     * Unified, canonical method to query server-authoritative active HexShard ID.
     * Accurately distinguishes [ActiveVirtualNumberState.Active],
     * [ActiveVirtualNumberState.NoActiveNumber], and
     * [ActiveVirtualNumberState.TemporaryError].
     */
    suspend fun loadActiveVirtualNumber(
        accountId: String,
        accessToken: String,
        context: Context
    ): ActiveVirtualNumberState = withContext(Dispatchers.IO) {
        if (accountId.isBlank() || accessToken.isBlank()) {
            return@withContext ActiveVirtualNumberState.TemporaryError("Missing account credentials")
        }
        val baseUrl = SupabaseConfig.getBaseUrl()
        val anonKey = SupabaseConfig.getAnonKey(context)

        // Try querying by account_id first (production schema), then owner_id (v11 schema)
        val candidateUrls = listOf(
            "$baseUrl/rest/v1/hex_numbers?account_id=eq.$accountId&status=eq.active&select=*&limit=1",
            "$baseUrl/rest/v1/hex_numbers?owner_id=eq.$accountId&status=eq.active&select=*&limit=1"
        )

        var lastError: String? = null
        for (url in candidateUrls) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("apikey", anonKey)
                    .header("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                val resp = httpClient.newCall(req).execute()
                val body = resp.body?.string() ?: ""

                if (resp.isSuccessful) {
                    val arr = org.json.JSONArray(body)
                    if (arr.length() > 0) {
                        val obj = arr.getJSONObject(0)
                        val raw = obj.optString("raw_number", obj.optString("number", ""))
                        var clean = raw.filter { it.isDigit() }
                        if (clean.length == 11 && clean.startsWith("999")) {
                            clean = clean.substring(3)
                        }
                        if (clean.length == 8) {
                            val formatted = VirtualNumberGenerator.format8Digits(clean)
                            SessionManager.updateVirtualNumber(context, clean)
                            SecurePrefsManager.setPrivateVirtualNumber(context, clean, accountId)
                            return@withContext ActiveVirtualNumberState.Active(clean, formatted)
                        }
                    }
                    // Server authoritatively confirmed 0 active numbers exist for this account
                    return@withContext ActiveVirtualNumberState.NoActiveNumber
                } else if (resp.code == 400 && body.contains("does not exist")) {
                    // Column name mismatch between schemas, try fallback url
                    lastError = "Column mismatch: $body"
                    continue
                } else {
                    lastError = "Server HTTP ${resp.code}: $body"
                }
            } catch (e: Exception) {
                Timber.w(e, "Error loading active virtual number from $url")
                lastError = e.message ?: "Network error"
            }
        }

        ActiveVirtualNumberState.TemporaryError(lastError ?: "Failed to query active virtual number")
    }

    /**
     * Queries Supabase for the active virtual number assigned to [accountId] and synchronizes session state.
     * If server returns active row, updates local cache and session with canonical 8 digits.
     * If server returns no record or an error, returns null.
     */
    suspend fun fetchActiveVirtualNumber(
        accountId: String,
        accessToken: String,
        context: Context
    ): String? = withContext(Dispatchers.IO) {
        when (val res = loadActiveVirtualNumber(accountId, accessToken, context)) {
            is ActiveVirtualNumberState.Active -> res.formatted
            is ActiveVirtualNumberState.NoActiveNumber -> null
            is ActiveVirtualNumberState.TemporaryError -> null
        }
    }

    private fun parseErrorMessage(responseBody: String, fallback: String): String {
        return try {
            val json = JSONObject(responseBody)
            json.optString("error", json.optString("message", fallback))
        } catch (_: Exception) {
            fallback
        }
    }
}
