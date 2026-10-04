package com.example.shared.domain.ai

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What shape of answer a request expects, independent of any provider.
 *
 * A mini-app declares it once ([com.example.shared.presentation.screens.output.result.BaseResultViewModel.responseFormat]);
 * the common AI layer carries it to every provider; each provider adapter
 * turns it into its own native mechanism (Gemini `generationConfig`, Groq
 * `response_format`) or, where the provider has none, ignores it. The
 * mini-app's own decoder stays the final, typed boundary either way.
 */
sealed interface ResponseFormat {

    /** Free text (or JSON asked for in the prompt only) -- no provider-side constraint. */
    data object Text : ResponseFormat

    /**
     * A JSON value matching [schema], generated from the mini-app's own
     * response model -- the `@Serializable` class it already decodes into,
     * so the contract has exactly one source of truth. [name] identifies it
     * where a provider requires a name (Groq's `json_schema.name`).
     */
    class Json(val name: String, val descriptor: SerialDescriptor) : ResponseFormat {
        val schema: JsonObject by lazy { JsonSchemas.of(descriptor) }

        override fun toString(): String = "Json($name)"

        companion object {
            fun <T> of(name: String, serializer: KSerializer<T>) = Json(name, serializer.descriptor)
        }
    }
}

/**
 * JSON Schema (the subset both Gemini's `responseJsonSchema` and Groq's
 * `json_schema` accept) generated from a kotlinx.serialization descriptor:
 *
 * - a class → `object` with its properties; `required` lists exactly the
 *   properties without a Kotlin default (and not nullable) -- the same rule
 *   the decoder enforces, so the schema can never demand less or more;
 * - `List<T>` → `array` of T;
 * - `Map<String, T>` → `object` with `additionalProperties: T` (free keys,
 *   value shape still constrained);
 * - String/Char → `string`, integers → `integer`, floating point → `number`,
 *   Boolean → `boolean`, enums → `string` with `enum`.
 *
 * A nullable property is described by its non-null type and never required:
 * "may be absent" is how every nullable field in these response models is
 * meant (they all default to null). Polymorphic, contextual and recursive
 * types are rejected -- no AI response model needs them, and guessing a
 * schema for one would silently weaken the contract.
 */
@OptIn(ExperimentalSerializationApi::class)
object JsonSchemas {

    fun of(descriptor: SerialDescriptor): JsonObject = schema(descriptor, emptySet())

    private fun schema(descriptor: SerialDescriptor, path: Set<String>): JsonObject {
        val kind: SerialKind = descriptor.kind
        return when (kind) {
            PrimitiveKind.STRING, PrimitiveKind.CHAR -> type("string")
            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG -> type("integer")
            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> type("number")
            PrimitiveKind.BOOLEAN -> type("boolean")
            SerialKind.ENUM -> buildJsonObject {
                put("type", "string")
                put("enum", JsonArray((0 until descriptor.elementsCount).map { JsonPrimitive(descriptor.getElementName(it)) }))
            }
            StructureKind.LIST -> buildJsonObject {
                put("type", "array")
                put("items", schema(descriptor.getElementDescriptor(0), path))
            }
            StructureKind.MAP -> {
                val key = descriptor.getElementDescriptor(0)
                require(key.kind == PrimitiveKind.STRING) { "${descriptor.serialName}: JSON object keys must be strings, got ${key.serialName}" }
                buildJsonObject {
                    put("type", "object")
                    put("additionalProperties", schema(descriptor.getElementDescriptor(1), path))
                }
            }
            StructureKind.CLASS, StructureKind.OBJECT -> {
                val name = descriptor.serialName.removeSuffix("?")
                require(name !in path) { "$name is recursive -- not supported in an AI response contract" }
                val inner = path + name
                buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            for (i in 0 until descriptor.elementsCount) {
                                put(descriptor.getElementName(i), schema(descriptor.getElementDescriptor(i), inner))
                            }
                        },
                    )
                    val required = (0 until descriptor.elementsCount)
                        .filterNot { descriptor.isElementOptional(it) || descriptor.getElementDescriptor(it).isNullable }
                        .map { JsonPrimitive(descriptor.getElementName(it)) }
                    if (required.isNotEmpty()) put("required", JsonArray(required))
                }
            }
            is PolymorphicKind, SerialKind.CONTEXTUAL ->
                throw IllegalArgumentException("${descriptor.serialName}: $kind is not supported in an AI response contract")
            else -> throw IllegalArgumentException("${descriptor.serialName}: unsupported kind $kind")
        }
    }

    private fun type(name: String) = buildJsonObject { put("type", name) }
}
