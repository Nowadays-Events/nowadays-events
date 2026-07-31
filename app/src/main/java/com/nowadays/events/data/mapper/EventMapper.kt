package com.nowadays.events.data.mapper

import com.nowadays.events.data.local.EventEntity
import com.nowadays.events.domain.model.DataOrigin
import com.nowadays.events.domain.model.Event
import com.nowadays.events.domain.model.EventCategory
import com.nowadays.events.domain.model.EventPrice
import java.time.Instant

fun EventEntity.toDomain(): Event = Event(
    id = id, title = title, shortDescription = shortDescription, fullDescription = fullDescription,
    category = EventCategory.valueOf(category), startsAt = Instant.ofEpochMilli(startsAtEpochMillis),
    endsAt = Instant.ofEpochMilli(endsAtEpochMillis), venueName = venueName, address = address,
    latitude = latitude, longitude = longitude, sourceUrl = sourceUrl, imageUrl = imageUrl,
    organizer = organizer, price = if (isFree) EventPrice.Free else EventPrice.Paid(priceCents, currency),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis), origin = DataOrigin.valueOf(origin),
    goingCount = goingCount, maybeCount = maybeCount, isFictional = isFictional,
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id, title = title, shortDescription = shortDescription, fullDescription = fullDescription,
    category = category.name, startsAtEpochMillis = startsAt.toEpochMilli(), endsAtEpochMillis = endsAt.toEpochMilli(),
    venueName = venueName, address = address, latitude = latitude, longitude = longitude,
    sourceUrl = sourceUrl, imageUrl = imageUrl, organizer = organizer,
    isFree = price is EventPrice.Free, priceCents = (price as? EventPrice.Paid)?.amountCents,
    currency = (price as? EventPrice.Paid)?.currency ?: "EUR", updatedAtEpochMillis = updatedAt.toEpochMilli(),
    origin = origin.name, goingCount = goingCount, maybeCount = maybeCount, isFictional = isFictional,
)

