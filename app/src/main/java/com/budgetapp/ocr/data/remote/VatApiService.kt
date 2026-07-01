package com.budgetapp.ocr.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Client for the Polish VAT White List API (Ministerstwo Finansów).
 * Endpoint: GET https://wl-api.mf.gov.pl/api/search/nip/{nip}?date={YYYY-MM-DD}
 * Free, no registration, no API key required.
 */
class VatApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Looks up a company name by NIP from the VAT registry.
     * Returns the company name or null on any error (graceful degradation).
     */
    suspend fun lookupByNip(nip: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanNip = nip.replace("-", "").replace(" ", "")
            if (cleanNip.length != 10 || !cleanNip.all { it.isDigit() }) return@withContext null

            val date = LocalDate.now().toString()
            val url = "https://wl-api.mf.gov.pl/api/search/nip/$cleanNip?date=$date"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val subject = json.getJSONObject("result").optJSONObject("subject")
                ?: return@withContext null

            subject.optString("name", null)
        } catch (e: Exception) {
            android.util.Log.w("VatApiService", "NIP lookup failed: ${e.message}")
            null
        }
    }
}
