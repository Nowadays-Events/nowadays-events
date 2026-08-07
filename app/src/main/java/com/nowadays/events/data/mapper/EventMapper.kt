package com.nowadays.events.data.mapper

import com.nowadays.events.data.local.EventEntity
import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import com.nowadays.events.domain.model.EventStatus
import java.time.Instant

fun EventEntity.toDomain(): Event = Event(
    id = id, title = title, shortDescription = shortDescription, fullDescription = fullDescription,
    category = EventCategory.valueOf(category), startsAt = Instant.ofEpochMilli(startsAtEpochMillis),
    endsAt = Instant.ofEpochMilli(endsAtEpochMillis), venueName = venueName, address = address,
    latitude = latitude, longitude = longitude, sourceUrl = sourceUrl,
    sourceUrls = sourceUrls.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        .ifEmpty { listOf(sourceUrl) },
    imageUrl = imageUrl,
    organizer = organizer, price = when (priceType) {
        "FREE" -> EventPrice.Free
        "PAID" -> EventPrice.Paid(priceCents, currency)
        else -> EventPrice.Unknown
    },
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis), origin = DataOrigin.valueOf(origin),
    goingCount = goingCount, maybeCount = maybeCount, isFictional = isFictional,
    status = runCatching { EventStatus.valueOf(status) }.getOrDefault(EventStatus.ACTIVE),
    occurrenceCount = occurrenceCount,
    nextOccurrenceAt = nextOccurrenceAtEpochMillis?.let(Instant::ofEpochMilli),
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id, title = title, shortDescription = shortDescription, fullDescription = fullDescription,
    category = category.name, startsAtEpochMillis = startsAt.toEpochMilli(), endsAtEpochMillis = endsAt.toEpochMilli(),
    venueName = venueName, address = address, latitude = latitude, longitude = longitude,
    sourceUrl = sourceUrl, sourceUrls = sourceUrls.distinct().joinToString("\n"),
    imageUrl = imageUrl, organizer = organizer,
    isFree = price is EventPrice.Free, priceCents = (price as? EventPrice.Paid)?.amountCents,
    priceType = when (price) {
        EventPrice.Unknown -> "UNKNOWN"
        EventPrice.Free -> "FREE"
        is EventPrice.Paid -> "PAID"
    },
    currency = (price as? EventPrice.Paid)?.currency ?: "EUR", updatedAtEpochMillis = updatedAt.toEpochMilli(),
    origin = origin.name, goingCount = goingCount, maybeCount = maybeCount, isFictional = isFictional,
    status = status.name,
    occurrenceCount = occurrenceCount,
    nextOccurrenceAtEpochMillis = nextOccurrenceAt?.toEpochMilli(),
)
