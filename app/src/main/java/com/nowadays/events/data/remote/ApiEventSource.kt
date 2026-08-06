package com.nowadays.events.data.remote

import com.nowadays.events.BuildConfig
import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.model.EventStatus
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ApiEventSource @Inject constructor() : EventSource {
    override val name: String = "nowadays-api"

    override suspend fun fetchEvents(updatedSince: Instant?): List<Event> = withContext(Dispatchers.IO) {
        val connection = URL("${BuildConfig.NOWADAYS_API_BASE_URL.trimEnd('/')}/events")
            .openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 5_000
            connection.readTimeout = 10_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            check(connection.responseCode in 200..299) { "Nowadays API HTTP ${connection.responseCode}" }
            val root = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val array = root.optJSONArray("events") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val updatedAt = item.instant("last_seen_at") ?: Instant.now()
                    if (updatedSince != null && updatedAt <= updatedSince) continue
                    val start = item.instant("start_at") ?: continue
                    val end = item.instant("end_at") ?: start
                    val title = item.optString("title")
                    if (title.isBlank()) continue
                    add(
                        Event(
                            id = "api-${item.getString("external_id")}",
                            title = title,
                            shortDescription = item.optString("description").ifBlank {
                                "Événement provenant d’une source publique vérifiée."
                            }.take(240),
                            fullDescription = item.optString("description").ifBlank { null },
                            category = item.optString("category").toCategory(),
                            startsAt = start,
                            endsAt = end,
                            venueName = item.optString("venue").ifBlank { item.optString("address") },
                            address = item.optString("address").ifBlank { item.optString("venue") },
                            latitude = item.getDouble("latitude"),
                            longitude = item.getDouble("longitude"),
                            sourceUrl = item.optJSONArray("source_urls")?.optString(0).orEmpty()
                                .ifBlank { BuildConfig.NOWADAYS_API_BASE_URL },
                            imageUrl = null,
                            organizer = item.optString("source_name").ifBlank { null },
                            price = when (item.optString("price_type", "unknown").lowercase()) {
                                "free" -> EventPrice.Free
                                "paid" -> EventPrice.Paid(
                                    item.takeIf { it.has("price_cents") && !it.isNull("price_cents") }?.getInt("price_cents"),
                                    item.optString("currency", "EUR"),
                                )
                                else -> EventPrice.Unknown
                            },
                            updatedAt = updatedAt,
                            origin = DataOrigin.AUTOMATIC,
                            status = when (item.optString("status", "active").lowercase()) {
                                "cancelled" -> EventStatus.CANCELLED
                                "postponed" -> EventStatus.POSTPONED
                                "unverified" -> EventStatus.UNVERIFIED
                                else -> EventStatus.ACTIVE
                            },
                            occurrenceCount = item.optInt("occurrence_count", 1).coerceAtLeast(1),
                            nextOccurrenceAt = item.instant("next_occurrence_at"),
                        ),
                    )
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun JSONObject.instant(key: String): Instant? =
    optString(key).takeIf(String::isNotBlank)?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun String.toCategory(): EventCategory =
    runCatching { EventCategory.valueOf(uppercase()) }.getOrDefault(EventCategory.COMMUNITY)
