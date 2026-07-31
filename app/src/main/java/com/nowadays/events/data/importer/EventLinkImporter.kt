package com.nowadays.events.data.importer

import java.net.InetAddress
import java.net.URI
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup

data class ImportedEvent(
    val title: String = "",
    val description: String = "",
    val startsAt: String = "",
    val endsAt: String = "",
    val venue: String = "",
    val address: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val organizer: String = "",
    val imageUrl: String = "",
    val priceEuros: String = "",
)

class EventLinkImporter @Inject constructor() {
    suspend fun import(url: String): Result<List<ImportedEvent>> = withContext(Dispatchers.IO) {
        runCatching {
            val uri = validatePublicUrl(url)
            val document = Jsoup.connect(uri.toString())
                .userAgent("Nowadays/1.0 (event preview)")
                .timeout(12_000)
                .maxBodySize(2_000_000)
                .followRedirects(true)
                .get()
            EventMetadataParser.parseAll(document.html())
                ?: error("Aucune information d’événement reconnue sur cette page")
        }
    }

    private fun validatePublicUrl(raw: String): URI {
        val uri = URI(raw.trim())
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()) { "Lien web invalide" }
        val addresses = InetAddress.getAllByName(uri.host)
        require(addresses.none { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress }) {
            "Les adresses locales ne sont pas acceptées"
        }
        return uri
    }
}

object EventMetadataParser {
    fun parseAll(html: String): List<ImportedEvent>? {
        val document = Jsoup.parse(html)
        val structured = mutableListOf<ImportedEvent>()
        document.select("script[type=application/ld+json]").forEach { script ->
            findEvents(runCatching { parseJson(script.data().ifBlank { script.html() }) }.getOrNull()).mapTo(structured, ::fromJsonLd)
        }
        if (structured.isNotEmpty()) return structured.distinctBy { Triple(it.title, it.startsAt, it.venue) }
        val title = meta(document, "og:title").ifBlank { document.title() }
        if (title.isBlank()) return null
        val base = ImportedEvent(
            title = title,
            description = meta(document, "og:description"),
            imageUrl = meta(document, "og:image"),
        )
        return inferEventsFromArticle(document, base).ifEmpty { listOf(base) }
    }

    private fun parseJson(value: String): Any = if (value.trimStart().startsWith("[")) JSONArray(value) else JSONObject(value)

    private fun findEvents(node: Any?): List<JSONObject> = when (node) {
        is JSONArray -> (0 until node.length()).flatMap { findEvents(node.opt(it)) }
        is JSONObject -> {
            val type = node.opt("@type")
            val isEvent = type == "Event" || type is JSONArray && (0 until type.length()).any { type.optString(it) == "Event" }
            if (isEvent) listOf(node) else node.keys().asSequence().flatMap { findEvents(node.opt(it)).asSequence() }.toList()
        }
        else -> emptyList()
    }

    private fun inferEventsFromArticle(document: org.jsoup.nodes.Document, base: ImportedEvent): List<ImportedEvent> {
        val year = Regex("\\b(20\\d{2})\\b").find(document.title())?.groupValues?.get(1)?.toIntOrNull()
            ?: LocalDate.now().year
        val fullText = document.body()?.text().orEmpty()
        val range = Regex("(?i)du\\s+(\\d{1,2})\\s+au\\s+(\\d{1,2})\\s+([a-zéûôîèàù]+)").find(fullText)
        val inferred = mutableListOf<ImportedEvent>()
        range?.let { match ->
            val month = frenchMonth(match.groupValues[3]) ?: return@let
            inferred += base.copy(
                startsAt = LocalDate.of(year, month, match.groupValues[1].toInt()).atStartOfDay().toString().take(16),
                endsAt = LocalDate.of(year, month, match.groupValues[2].toInt()).atTime(23, 59).toString().take(16),
                venue = inferCity(document.title()), address = inferCity(document.title()),
            )
        }
        val datedHeading = Regex("(?i)(?:lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)?\\s*(\\d{1,2})\\s+(janvier|février|mars|avril|mai|juin|juillet|août|septembre|octobre|novembre|décembre)")
        document.select("h2, h3, h4, strong").forEach { heading ->
            val match = datedHeading.find(heading.text()) ?: return@forEach
            val month = frenchMonth(match.groupValues[2]) ?: return@forEach
            val details = generateSequence(heading.nextElementSibling()) { it.nextElementSibling() }.take(3).joinToString(" ") { it.text() }
            val times = Regex("(?i)\\b(\\d{1,2})(?:h|:)(\\d{2})?\\b").findAll(details).take(2).map {
                LocalTime.of(it.groupValues[1].toInt().coerceIn(0, 23), it.groupValues[2].toIntOrNull() ?: 0)
            }.toList()
            val date = LocalDate.of(year, month, match.groupValues[1].toInt())
            val city = inferCity(document.title())
            val preciseVenue = inferVenue(details).ifBlank { city }
            inferred += base.copy(
                title = heading.text().ifBlank { base.title },
                description = details.take(500).ifBlank { base.description },
                startsAt = date.atTime(times.firstOrNull() ?: LocalTime.of(9, 0)).toString().take(16),
                endsAt = date.atTime(times.getOrNull(1) ?: LocalTime.of(23, 59)).toString().take(16),
                venue = preciseVenue, address = city,
            )
        }
        return inferred.distinctBy { it.title to it.startsAt }
    }

    private fun inferCity(title: String): String {
        val normalized = title.replace('\u00A0', ' ').replace(Regex("\\s+"), " ")
        return Regex("(?i)\\bà\\s+([A-ZÀ-Ü][A-Za-zÀ-ÿ' -]+?)(?=\\s+du\\s+\\d|\\s+-|$)")
            .find(normalized)?.groupValues?.get(1)?.trim().orEmpty()
    }

    private fun inferVenue(text: String): String {
        val place = Regex("\\b(?:[Pp]arc|[Pp]lace|[Jj]ardin|[Tt]héâtre|[Aa]rènes|[Mm]usée|[Ss]tade|[Ss]alle|[Éé]glise)\\s+[A-ZÀ-Ü][A-Za-zÀ-ÿ'’-]+(?:\\s+[A-ZÀ-Ü][A-Za-zÀ-ÿ'’-]+){0,3}")
            .find(text)?.value
        return place.orEmpty()
    }

    private fun frenchMonth(raw: String): Month? = mapOf(
        "janvier" to Month.JANUARY, "février" to Month.FEBRUARY, "mars" to Month.MARCH,
        "avril" to Month.APRIL, "mai" to Month.MAY, "juin" to Month.JUNE,
        "juillet" to Month.JULY, "août" to Month.AUGUST, "septembre" to Month.SEPTEMBER,
        "octobre" to Month.OCTOBER, "novembre" to Month.NOVEMBER, "décembre" to Month.DECEMBER,
    )[raw.lowercase()]

    private fun fromJsonLd(event: JSONObject): ImportedEvent {
        val location = event.optJSONObject("location")
        val addressNode = location?.opt("address")
        val address = when (addressNode) {
            is String -> addressNode
            is JSONObject -> listOf("streetAddress", "postalCode", "addressLocality", "addressCountry")
                .map { addressNode.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
            else -> ""
        }
        val geo = location?.optJSONObject("geo")
        val organizer = event.optJSONObject("organizer")?.optString("name").orEmpty()
        val offer = when (val offers = event.opt("offers")) {
            is JSONObject -> offers
            is JSONArray -> offers.optJSONObject(0)
            else -> null
        }
        return ImportedEvent(
            title = event.optString("name"),
            description = Jsoup.parse(event.optString("description")).text(),
            startsAt = formatDate(event.optString("startDate")),
            endsAt = formatDate(event.optString("endDate")),
            venue = location?.optString("name").orEmpty(),
            address = address,
            latitude = geo?.opt("latitude")?.toString().orEmpty(),
            longitude = geo?.opt("longitude")?.toString().orEmpty(),
            organizer = organizer,
            imageUrl = firstText(event.opt("image")),
            priceEuros = offer?.opt("price")?.toString().orEmpty().takeUnless { it == "0" || it == "0.0" }.orEmpty(),
        )
    }

    private fun firstText(value: Any?): String = when (value) {
        is String -> value
        is JSONArray -> value.optString(0)
        is JSONObject -> value.optString("url")
        else -> ""
    }

    private fun formatDate(raw: String): String {
        if (raw.isBlank()) return ""
        val date = runCatching { OffsetDateTime.parse(raw).toLocalDateTime() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(raw).toLocalDateTime() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(raw) }.getOrNull()
            ?: return raw.take(16)
        return date.withSecond(0).withNano(0).toString()
    }

    private fun meta(document: org.jsoup.nodes.Document, property: String): String =
        document.selectFirst("meta[property=$property], meta[name=$property]")?.attr("content").orEmpty()
}
