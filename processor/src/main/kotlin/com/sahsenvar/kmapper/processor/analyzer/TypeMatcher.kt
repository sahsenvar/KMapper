package com.sahsenvar.kmapper.processor.analyzer

import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.MappingStrategy
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/**
 * Determines the appropriate mapping strategy for field transformations.
 * Phase 2: built-in converters only. Custom converter discovery via @KMapperConfig is Phase 3.
 */
class TypeMatcher(private val logger: KSPLogger) {

    fun determineMappingStrategy(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        isReverse: Boolean = false
    ): MappingStrategy {
        // 1. Check @UseConverter
        if (sourceField.useConverter != null) {
            return MappingStrategy.Convert(sourceField.useConverter)
        }

        // 2. Check same type
        if (isSameType(sourceField.type, targetField.type)) {
            return MappingStrategy.Direct
        }

        // 3. Check collection types
        if (isCollectionType(sourceField.type) && isCollectionType(targetField.type)) {
            val sourceElementType = extractCollectionElementType(sourceField.type)
            val targetElementType = extractCollectionElementType(targetField.type)

            if (sourceElementType != null && targetElementType != null) {
                val elementStrategy = if (isSameType(sourceElementType, targetElementType)) {
                    MappingStrategy.Direct
                } else if (isDataClass(sourceElementType) && isDataClass(targetElementType)) {
                    val mapperName = "to${targetElementType.declaration.simpleName.asString()}"
                    MappingStrategy.Nested(mapperName)
                } else {
                    MappingStrategy.Direct
                }
                return MappingStrategy.Collection(elementStrategy)
            }
        }

        // 4. Check nested object mapping
        if (isDataClass(sourceField.type) && isDataClass(targetField.type)) {
            val mapperName = "to${targetField.type.declaration.simpleName.asString()}"
            return MappingStrategy.Nested(mapperName)
        }

        // 5. Check built-in converters
        // For reverse mapping, swap source and target to find the correct converter
        val converterFqn = if (isReverse) {
            findBuiltInConverter(targetField.type, sourceField.type)
        } else {
            findBuiltInConverter(sourceField.type, targetField.type)
        }

        if (converterFqn != null) {
            return MappingStrategy.Convert(converterFqn)
        }

        logger.warn("No mapping strategy found for ${sourceField.name}: ${sourceField.type} → ${targetField.type}")
        return MappingStrategy.Direct
    }

    private fun isSameType(source: KSType, target: KSType): Boolean {
        return source.declaration.qualifiedName?.asString() ==
                target.declaration.qualifiedName?.asString()
    }

    fun isCollectionType(type: KSType): Boolean {
        val fqn = type.declaration.qualifiedName?.asString() ?: return false
        return fqn.startsWith("kotlin.collections.List") ||
                fqn.startsWith("kotlin.collections.Set") ||
                fqn.startsWith("kotlinx.collections.immutable")
    }

    fun extractCollectionElementType(type: KSType): KSType? {
        return type.arguments.firstOrNull()?.type?.resolve()
    }

    private fun isDataClass(type: KSType): Boolean {
        val decl = type.declaration as? KSClassDeclaration ?: return false
        return decl.modifiers.contains(Modifier.DATA)
    }

    /**
     * Finds a built-in bilateral converter for the given source → target type pair.
     *
     * All converters in com.sahsenvar.kmapper.converter.builtin are bilateral (MapTypeConverter<S,T>).
     * - Forward direction (S→T): emits convertToNonNull / convertTo
     * - Reverse direction (T→S): the caller passes (target, source) so we still look up the forward key
     *   and let MappingCodeGenerator emit convertFromNonNull / convertFrom.
     */
    private fun findBuiltInConverter(source: KSType, target: KSType): String? {
        val sourceFqn = source.declaration.qualifiedName?.asString()
        val targetFqn = target.declaration.qualifiedName?.asString()

        return when ("$sourceFqn→$targetFqn") {
            // String ↔ Int  (bilateral: StringIntConverter)
            "kotlin.String→kotlin.Int" ->
                "com.sahsenvar.kmapper.converter.builtin.StringIntConverter"

            // String ↔ Long  (bilateral: StringLongConverter)
            "kotlin.String→kotlin.Long" ->
                "com.sahsenvar.kmapper.converter.builtin.StringLongConverter"

            // String ↔ Double  (bilateral: StringDoubleConverter)
            "kotlin.String→kotlin.Double" ->
                "com.sahsenvar.kmapper.converter.builtin.StringDoubleConverter"

            // String ↔ Float  (bilateral: StringFloatConverter)
            "kotlin.String→kotlin.Float" ->
                "com.sahsenvar.kmapper.converter.builtin.StringFloatConverter"

            // String ↔ Boolean  (bilateral: StringBooleanConverter)
            "kotlin.String→kotlin.Boolean" ->
                "com.sahsenvar.kmapper.converter.builtin.StringBooleanConverter"

            // Int ↔ Long  (bilateral: IntLongConverter)
            "kotlin.Int→kotlin.Long" ->
                "com.sahsenvar.kmapper.converter.builtin.IntLongConverter"

            // String ↔ Instant  (bilateral: StringInstantConverter)
            "kotlin.String→kotlinx.datetime.Instant" ->
                "com.sahsenvar.kmapper.converter.builtin.StringInstantConverter"

            // Long ↔ Instant  (bilateral: LongInstantConverter)
            "kotlin.Long→kotlinx.datetime.Instant" ->
                "com.sahsenvar.kmapper.converter.builtin.LongInstantConverter"

            // Reverse directions — same bilateral converter, convertFrom will be used by MappingCodeGenerator
            "kotlin.Int→kotlin.String" ->
                "com.sahsenvar.kmapper.converter.builtin.StringIntConverter"

            "kotlin.Long→kotlin.String" ->
                "com.sahsenvar.kmapper.converter.builtin.StringLongConverter"

            "kotlin.Double→kotlin.String" ->
                "com.sahsenvar.kmapper.converter.builtin.StringDoubleConverter"

            "kotlin.Float→kotlin.String" ->
                "com.sahsenvar.kmapper.converter.builtin.StringFloatConverter"

            "kotlin.Boolean→kotlin.String" ->
                "com.sahsenvar.kmapper.converter.builtin.StringBooleanConverter"

            "kotlin.Long→kotlin.Int" ->
                "com.sahsenvar.kmapper.converter.builtin.IntLongConverter"

            "kotlinx.datetime.Instant→kotlin.String" ->
                "com.sahsenvar.kmapper.converter.builtin.StringInstantConverter"

            "kotlinx.datetime.Instant→kotlin.Long" ->
                "com.sahsenvar.kmapper.converter.builtin.LongInstantConverter"

            else -> null
        }
    }
}
