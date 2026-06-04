package com.sahsenvar.kmapper.processor.analyzer

import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.MappingStrategy
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/**
 * Determines the appropriate mapping strategy for field transformations.
 *
 * Priority: per-field @UseMapTypeConverter > @KMapperConfig custom registry > built-in table.
 * When no strategy matches for differing types, emits a compile error and returns Unmappable.
 *
 * @param customConverters Map of (sourceFqn to targetFqn) → converterFqn, populated from @KMapperConfig.
 * @param collectionWrappers Map of target-collection-FQN → wrapper-function-FQN, from @CollectionWrapper descriptors.
 */
class TypeMatcher(
    private val logger: KSPLogger,
    private val customConverters: Map<Pair<String, String>, String> = emptyMap(),
    private val collectionWrappers: Map<String, String> = emptyMap()
) {

    fun determineMappingStrategy(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        isReverse: Boolean = false
    ): MappingStrategy {
        // 1. Check per-field @UseMapTypeConverter (highest priority)
        if (sourceField.useConverter != null) {
            return MappingStrategy.Convert(sourceField.useConverter)
        }

        // 2. Check collection types — must come before same-type check because isSameType only
        //    compares the outer type FQN (ignoring generic arguments). Two List<X>/List<Y> types
        //    with different element types would be incorrectly treated as Direct if same-type ran first.
        //
        //    Also handles @CollectionWrapper: when the target FQN (e.g. PersistentList) is in
        //    collectionWrappers, the source must be a standard collection (List/Set) and we emit
        //    WrappedCollection so the generator appends the wrapper call after .map { }.
        val targetCollFqn = targetField.type.declaration.qualifiedName?.asString()
        val wrapperFqn = if (targetCollFqn != null) collectionWrappers[targetCollFqn] else null

        if (wrapperFqn != null && isCollectionType(sourceField.type)) {
            // Target is a wrapped collection (e.g. PersistentList); source is a plain List/Set.
            val sourceElementType = extractCollectionElementType(sourceField.type)
            val targetElementType = extractCollectionElementType(targetField.type)
            val elementStrategy = if (sourceElementType != null && targetElementType != null) {
                if (isSameType(sourceElementType, targetElementType)) {
                    MappingStrategy.Direct
                } else if (isDataClass(sourceElementType) && isDataClass(targetElementType)) {
                    val mapperName = "to${targetElementType.declaration.simpleName.asString()}"
                    MappingStrategy.Nested(mapperName)
                } else {
                    MappingStrategy.Direct
                }
            } else {
                MappingStrategy.Direct
            }
            return MappingStrategy.WrappedCollection(elementStrategy, wrapperFqn)
        }

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
                val isSet = isSetCollectionType(targetField.type)
                return MappingStrategy.Collection(elementStrategy, isSet)
            }
        }

        // 3b. Check same type (non-collection)
        if (isSameType(sourceField.type, targetField.type)) {
            return MappingStrategy.Direct
        }

        // 4. Check nested object mapping
        if (isDataClass(sourceField.type) && isDataClass(targetField.type)) {
            val mapperName = "to${targetField.type.declaration.simpleName.asString()}"
            return MappingStrategy.Nested(mapperName)
        }

        // 5. Check enum mapping via MappableEnum
        val sourceDecl = sourceField.type.declaration as? KSClassDeclaration
        val targetDecl = targetField.type.declaration as? KSClassDeclaration
        if (sourceDecl?.classKind == ClassKind.ENUM_CLASS || targetDecl?.classKind == ClassKind.ENUM_CLASS) {
            return determineEnumStrategy(sourceField, targetField, sourceDecl, targetDecl)
        }

        // 6. Check custom converters from @KMapperConfig (second priority after per-field)
        val customConverterFqn = if (isReverse) {
            customConverters[targetField.type.fqn() to sourceField.type.fqn()]
                ?: customConverters[sourceField.type.fqn() to targetField.type.fqn()]
        } else {
            customConverters[sourceField.type.fqn() to targetField.type.fqn()]
                ?: customConverters[targetField.type.fqn() to sourceField.type.fqn()]
        }
        if (customConverterFqn != null) {
            return MappingStrategy.Convert(customConverterFqn)
        }

        // 7. Check built-in converters
        val converterFqn = if (isReverse) {
            findBuiltInConverter(targetField.type, sourceField.type)
        } else {
            findBuiltInConverter(sourceField.type, targetField.type)
        }

        if (converterFqn != null) {
            return MappingStrategy.Convert(converterFqn)
        }

        // 8. No strategy found — emit a compile error
        logger.error(
            "no converter for ${sourceField.type.fqn()} -> ${targetField.type.fqn()}; " +
                "add it to @KMapperConfig(converters=[...]) or annotate the field with @UseMapTypeConverter"
        )
        return MappingStrategy.Unmappable
    }

    private fun determineEnumStrategy(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        sourceDecl: KSClassDeclaration?,
        targetDecl: KSClassDeclaration?
    ): MappingStrategy {
        val mappableEnumFqn = "com.sahsenvar.kmapper.MappableEnum"

        // Target is the enum (wire → enum)
        if (targetDecl?.classKind == ClassKind.ENUM_CLASS) {
            val wireFqn = resolveEnumWireType(targetDecl, mappableEnumFqn)
            if (wireFqn == null) {
                logger.error(
                    "enum '${targetDecl.simpleName.asString()}' must implement MappableEnum<...> " +
                        "or use @UseMapTypeConverter"
                )
                return MappingStrategy.Unmappable
            }
            val sourceFqn = sourceField.type.fqn()
            if (sourceFqn != wireFqn) {
                logger.error(
                    "enum wire type mismatch: expected $wireFqn but source type is $sourceFqn"
                )
                return MappingStrategy.Unmappable
            }
            return MappingStrategy.EnumFromWire(targetDecl.qualifiedName!!.asString())
        }

        // Source is the enum (enum → wire)
        if (sourceDecl?.classKind == ClassKind.ENUM_CLASS) {
            val wireFqn = resolveEnumWireType(sourceDecl, mappableEnumFqn)
            if (wireFqn == null) {
                logger.error(
                    "enum '${sourceDecl.simpleName.asString()}' must implement MappableEnum<...> " +
                        "or use @UseMapTypeConverter"
                )
                return MappingStrategy.Unmappable
            }
            val targetFqn = targetField.type.fqn()
            if (targetFqn != wireFqn) {
                logger.error(
                    "enum wire type mismatch: expected $wireFqn but target type is $targetFqn"
                )
                return MappingStrategy.Unmappable
            }
            return MappingStrategy.EnumToWire
        }

        // Should not reach here, but guard anyway
        logger.error("Unexpected enum resolution state for ${sourceField.name}")
        return MappingStrategy.Unmappable
    }

    /**
     * Resolves the wire type FQN from an enum's MappableEnum<W> supertype.
     * Returns null if the enum does not implement MappableEnum.
     */
    private fun resolveEnumWireType(enumDecl: KSClassDeclaration, mappableEnumFqn: String): String? {
        for (supertype in enumDecl.superTypes) {
            val resolved = supertype.resolve()
            val declFqn = resolved.declaration.qualifiedName?.asString() ?: continue
            if (declFqn == mappableEnumFqn) {
                // MappableEnum<W> — extract the W type argument
                val wireTypeArg = resolved.arguments.firstOrNull()?.type?.resolve()
                return wireTypeArg?.declaration?.qualifiedName?.asString()
            }
        }
        return null
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

    /**
     * Returns true when the given type is a stdlib Set (kotlin.collections.Set or MutableSet).
     * Used to determine whether the generator must append `.toSet()` after `.map { }`.
     */
    fun isSetCollectionType(type: KSType): Boolean {
        val fqn = type.declaration.qualifiedName?.asString() ?: return false
        return fqn.startsWith("kotlin.collections.Set") ||
                fqn.startsWith("kotlin.collections.MutableSet")
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

/** Returns the fully-qualified name of this KSType for use in error messages and converter lookup. */
internal fun KSType.fqn(): String =
    declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
