package com.example.metronome

import android.net.Uri
import android.renderscript.Type
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer

class UriTypeAdapter : JsonSerializer<Uri>, JsonDeserializer<Uri> {

    override fun serialize(
        src: Uri?,
        typeOfSrc: java.lang.reflect.Type?,
        context: JsonSerializationContext?
    ): JsonElement? {
        // Convert Uri to its String representation for JSON
        return JsonPrimitive(src?.toString())
    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: java.lang.reflect.Type?,
        context: JsonDeserializationContext?
    ): Uri? {
        // Convert String from JSON back to Uri
        return json?.asString?.let { Uri.parse(it) }
    }
}