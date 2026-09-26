package com.nowadays.events.presentation.map

internal object ReferencePlaceCodec {
    fun encode(places: List<ReferencePlace>): String = places.take(3).joinToString("|") {
        "${it.label.replace("|", " ").replace(";", " ")};${it.latitude};${it.longitude};${it.kind}"
    }

    fun decode(value: String): List<ReferencePlace> = value.split('|').mapNotNull { raw ->
        val fields = raw.split(';')
        if (fields.size != 4) return@mapNotNull null
        ReferencePlace(fields[0], fields[1].toDoubleOrNull() ?: return@mapNotNull null, fields[2].toDoubleOrNull() ?: return@mapNotNull null, fields[3])
    }
}
