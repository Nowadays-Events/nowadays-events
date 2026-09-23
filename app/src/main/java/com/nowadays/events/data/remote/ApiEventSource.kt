package com.nowadays.events.data.remote

import com.nowadays.events.BuildConfig
import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.model.EventStatus
import com.nowadays.events.domain.model.EventTimePrecision
import com.nowadays.events.domain.model.EventScheduleType
import java.time.ZoneId
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class ApiEventSource @Inject constructor() : EventSource {
    override val name: String = "nowadays-api"

    override suspend fun fetchSnapshot(): RemoteEventSnapshot = withContext(Dispatchers.IO) {
        val base = BuildConfig.NOWADAYS_API_BASE_URL.trimEnd('/')
        val root = JSONObject(fetchText("$base/events"))
        val health = JSONObject(fetchText("$base/health.json"))
        val array = root.getJSONArray("events")
        val generatedAt = root.instant("generated_at")
        val events = buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val updatedAt = item.instant("last_seen_at") ?: generatedAt ?: Instant.EPOCH
                    val start = requireNotNull(item.instant("start_at")) { "event[$index].start_at missing or invalid" }
                    val end = item.instant("end_at") ?: start
                    val title = item.optString("title")
                    require(title.isNotBlank()) { "event[$index].title missing" }
                    val occurrenceStarts = item.optJSONArray("occurrence_starts")?.let { values ->
                        buildList {
                            for (valueIndex in 0 until values.length()) {
                                values.getString(valueIndex).takeIf(String::isNotBlank)
                                    ?.let(Instant::parse)?.let(::add)
                            }
                        }
                    }.orEmpty().distinct().sorted()
                    val occurrenceCount = item.optInt("occurrence_count", 1).coerceAtLeast(1)
                    val nextOccurrence = item.instant("next_occurrence_at")
                    val sourceUrls = item.optJSONArray("source_urls")?.let { urls ->
                        buildList {
                            for (urlIndex in 0 until urls.length()) {
                                urls.optString(urlIndex).takeIf(String::isNotBlank)?.let(::add)
                            }
                        }
                    }.orEmpty().distinct()
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
                            sourceUrl = sourceUrls.firstOrNull() ?: BuildConfig.NOWADAYS_API_BASE_URL,
                            sourceUrls = sourceUrls.ifEmpty { listOf(BuildConfig.NOWADAYS_API_BASE_URL) },
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
                            occurrenceCount = occurrenceCount,
                            nextOccurrenceAt = nextOccurrence,
                            scheduleType = item.scheduleType(start, end, occurrenceCount, nextOccurrence),
                            occurrenceStarts = occurrenceStarts.ifEmpty { listOfNotNull(nextOccurrence) },
                            timePrecision = item.timePrecision(start),
                            originalTimeText = item.optString("original_time_text").ifBlank { null },
                        ),
                    )
                }
        }
        RemoteEventSnapshot(
            events = events,
            generatedAt = generatedAt,
            healthStatus = health.optString("status", "missing"),
            declaredEventCount = health.optInt("exported", -1),
        )
    }
}

private fun fetchText(url: String): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 5_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        check(connection.responseCode in 200..299) { "Xymis Events API HTTP ${connection.responseCode}" }
        return connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

private fun JSONObject.instant(key: String): Instant? =
    optString(key).takeIf(String::isNotBlank)?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun JSONObject.timePrecision(start: Instant): EventTimePrecision {
    val explicit = optString("time_precision").uppercase()
    if (explicit.isNotBlank()) {
        return runCatching { EventTimePrecision.valueOf(explicit) }.getOrDefault(EventTimePrecision.UNKNOWN)
    }
    // Legacy feeds encoded a date without a known time as local midnight.
    return if (start.atZone(ZoneId.of("Europe/Paris")).toLocalTime() == java.time.LocalTime.MIDNIGHT)
        EventTimePrecision.DATE_ONLY else EventTimePrecision.EXACT
}

private fun JSONObject.scheduleType(
    start: Instant,
    end: Instant,
    occurrenceCount: Int,
    nextOccurrence: Instant?,
): EventScheduleType {
    optString("schedule_type").uppercase().takeIf(String::isNotBlank)?.let { explicit ->
        runCatching { EventScheduleType.valueOf(explicit) }.getOrNull()?.let { return it }
    }
    // Compatibilité avec les flux antérieurs au contrat temporel explicite.
    if (occurrenceCount > 1 || nextOccurrence != null) return EventScheduleType.RECURRING
    return if (end.epochSecond - start.epochSecond >= 86_400L) EventScheduleType.CONTINUOUS
    else EventScheduleType.SINGLE
}

private fun String.toCategory(): EventCategory =
    runCatching { EventCategory.valueOf(uppercase()) }.getOrDefault(EventCategory.COMMUNITY)
