package com.sahsenvar.kmapper

sealed class MappingException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause) {

    class RequiredFieldMissing(val field: String)
        : MappingException("Required field missing: $field")

    class TypeConversionFailed(val from: String, val to: String, cause: Throwable)
        : MappingException("Cannot convert $from -> $to", cause)

    class UnknownEnumValue(val enum: String, val value: Any)
        : MappingException("Unknown wire value '$value' for enum $enum")

    class EmptyCollection(val detail: String)
        : MappingException("Collection cannot be empty: $detail")

    class ValidationFailed(val field: String, val reason: String)
        : MappingException("Validation failed for '$field': $reason")
}
