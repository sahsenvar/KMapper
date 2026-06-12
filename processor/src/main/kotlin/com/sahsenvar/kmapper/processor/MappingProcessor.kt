package com.sahsenvar.kmapper.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration // used in generateReverseMappingFunction
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.validate
import com.sahsenvar.kmapper.processor.analyzer.ConverterIntrospector
import com.sahsenvar.kmapper.processor.analyzer.CycleDetector
import com.sahsenvar.kmapper.processor.analyzer.FieldAnalyzer
import com.sahsenvar.kmapper.processor.analyzer.TypeMatcher
import com.sahsenvar.kmapper.processor.analyzer.discoverWrappersFromConfig
import com.sahsenvar.kmapper.processor.generator.FunctionNameGenerator
import com.sahsenvar.kmapper.processor.generator.MappingCodeGenerator
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.validator.BuiltInConverterValidator
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * KSP processor for generating mapping functions from @MapTo and @MapFrom annotations.
 */
class MappingProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {
    companion object {
        private const val MAP_TO_ANNOTATION = "com.sahsenvar.kmapper.annotations.MapTo"
        private const val MAP_FROM_ANNOTATION = "com.sahsenvar.kmapper.annotations.MapFrom"
    }

    private val fieldAnalyzer by lazy { FieldAnalyzer(logger) }
    private val functionNameGenerator by lazy { FunctionNameGenerator(logger) }
    private val codeGen by lazy { MappingCodeGenerator(logger) }

    // TypeMatcher is created fresh each round with the current custom converters and wrappers.
    private var typeMatcher: TypeMatcher = TypeMatcher(logger)

    // Collect all mapping functions grouped by receiver class
    private val mappingFunctions = mutableMapOf<ReceiverKey, MutableList<FunSpec>>()

    data class ReceiverKey(
        val packageName: String,
        val className: String,
    )

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Clear previous round
        mappingFunctions.clear()

        // STEP 0: Discover collection wrappers from @KMapperConfig.wrappers (in-module).
        //
        // discoverWrappersFromConfig reads the consumer's own @KMapperConfig annotation (in-module,
        // works on KMP/iOS) and resolves each listed wrapper object's @CollectionWrapper.forType
        // via standard dependency annotation resolution (also works cross-module in KSP2).
        // Returns Map<forTypeFqn, wrapperObjectFqn>.
        val collectionWrappers = discoverWrappersFromConfig(resolver, logger)

        // STEP 1: Discover custom converters from @KMapperConfig annotations.
        // Returns (typePair → converterFqn) map plus the raw entries list for duplicate-checking.
        // The introspector is shared between discovery and resolution so both walk the same
        // superclass chain (parameterized-converter bases resolve identically everywhere).
        val introspector = ConverterIntrospector(resolver, logger)
        val (customConverters, globalConverterEntries) = discoverCustomConverters(resolver, introspector)
        typeMatcher = TypeMatcher(logger, customConverters, collectionWrappers, introspector)

        // STEP 1b: Validate the global @KMapperConfig list for duplicate (S,T) pairs.
        // Per-field @UseMapTypeConverter converters are exempt — they are never checked here.
        val converterValidator = BuiltInConverterValidator(logger)
        val convertersValid = converterValidator.validate(globalConverterEntries)
        if (!convertersValid) {
            logger.error("Converter validation failed. Fix duplicate converters in @KMapperConfig.")
            return emptyList()
        }

        // Process @MapTo annotations (Source → Target)
        val mapToClasses =
            resolver
                .getSymbolsWithAnnotation(MAP_TO_ANNOTATION)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.validate() }
                .toList()

        // STEP 2: Compile-time cycle detection (unconditional edges only)
        CycleDetector(logger).check(mapToClasses)

        mapToClasses.forEach { sourceClass ->
            processMapToAnnotation(resolver, sourceClass)
        }

        // Process @MapFrom annotations (Target ← Source, reverse mapping)
        val mapFromClasses =
            resolver
                .getSymbolsWithAnnotation(MAP_FROM_ANNOTATION)
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.validate() }
                .toList()

        mapFromClasses.forEach { targetClass ->
            processMapFromAnnotation(resolver, targetClass)
        }

        // Write all collected functions to files
        writeMappingFiles()

        return emptyList()
    }

    /**
     * Discovers custom converters declared via @KMapperConfig(converters = [...]).
     *
     * Returns a pair of:
     *   - Map<Pair<String,String>, String>: (sourceFqn to targetFqn) → converterFqn
     *     (first-wins when the same pair appears twice; the validator will catch the duplicate)
     *   - List<Pair<Pair<String,String>, String>>: ordered raw entries for duplicate detection
     *     (preserves all entries including duplicates so the validator can report them)
     *
     * Priority: per-field @UseMapTypeConverter > this custom registry > built-in table.
     */
    private fun discoverCustomConverters(
        resolver: Resolver,
        introspector: ConverterIntrospector,
    ): Pair<Map<Pair<String, String>, String>, List<Pair<Pair<String, String>, String>>> {
        val entries = mutableListOf<Pair<Pair<String, String>, String>>()

        resolver
            .getSymbolsWithAnnotation("com.sahsenvar.kmapper.annotations.KMapperConfig")
            .filterIsInstance<KSClassDeclaration>()
            .forEach { cfg ->
                val annotation =
                    cfg.annotations.firstOrNull {
                        it.shortName.asString() == "KMapperConfig"
                    } ?: return@forEach

                val convertersArg =
                    annotation.arguments.firstOrNull {
                        it.name?.asString() == "converters"
                    } ?: return@forEach

                @Suppress("UNCHECKED_CAST")
                val converterTypes = convertersArg.value as? List<KSType> ?: return@forEach

                for (converterType in converterTypes) {
                    val pair = converterType.toConverterPair(introspector) ?: continue
                    entries.add(pair)
                }
            }

        // Build map (first-wins; duplicates are reported by BuiltInConverterValidator)
        val result = mutableMapOf<Pair<String, String>, String>()
        for ((typePair, converterFqn) in entries) {
            result.putIfAbsent(typePair, converterFqn)
        }

        return result to entries
    }

    /**
     * Resolves a referenced converter type's MapTypeConverter<S, T> pair through
     * [ConverterIntrospector.typePairOf] (superclass-chain aware — parameterized
     * converter bases register too) and returns ((sourceFqn to targetFqn) to converterFqn),
     * or null if the reference never reaches MapTypeConverter.
     */
    private fun KSType.toConverterPair(introspector: ConverterIntrospector): Pair<Pair<String, String>, String>? {
        val converterDecl = declaration as? KSClassDeclaration ?: return null
        val converterFqn = converterDecl.qualifiedName?.asString() ?: return null
        val typePair = introspector.typePairOf(converterDecl) ?: return null
        return typePair to converterFqn
    }

    private fun writeMappingFiles() {
        logger.info("Writing ${mappingFunctions.size} mapping file(s)")

        mappingFunctions.forEach { (receiverKey, functions) ->
            val fileName = "${receiverKey.className}Mappers"
            logger.info(
                "Creating file: $fileName.kt for receiver ${receiverKey.packageName}.${receiverKey.className} with ${functions.size} function(s)",
            )

            val fileSpec =
                FileSpec
                    .builder(receiverKey.packageName, fileName)
                    .apply { functions.forEach { addFunction(it) } }
                    .indent("  ")
                    .build()

            val file =
                codeGenerator.createNewFile(
                    dependencies = Dependencies(aggregating = false),
                    packageName = receiverKey.packageName,
                    fileName = fileName,
                )

            file.bufferedWriter().use { writer ->
                if (receiverKey.packageName.isNotEmpty()) {
                    writer.write("package ${receiverKey.packageName}\n\n")
                }
                val code = fileSpec.toString()
                val codeWithoutPackage =
                    code
                        .lines()
                        .dropWhile { it.startsWith("package ") || it.isBlank() }
                        .joinToString("\n")
                writer.write(codeWithoutPackage)
            }

            logger.info("Generated $fileName.kt successfully")
        }
    }

    private fun processMapToAnnotation(
        resolver: Resolver,
        sourceClass: KSClassDeclaration,
    ) {
        val sourceFields = fieldAnalyzer.analyzeConstructorFields(sourceClass)

        // Extract all @MapTo targets
        val mapToAnnotations =
            sourceClass.annotations
                .filter {
                    it.shortName.asString() == "MapTo"
                }.toList()

        val hasMultipleTargets = mapToAnnotations.size > 1

        // VALIDATION: Multiple @MapTo requires explicit targetClass in @FieldMap
        if (hasMultipleTargets) {
            sourceFields.forEach { field ->
                if (field.fieldMapTargets.isNotEmpty()) {
                    val hasWildcard = "*" in field.fieldMapTargets
                    if (hasWildcard) {
                        logger.error(
                            "@FieldMap on field '${field.name}' in ${sourceClass.simpleName.asString()} " +
                                "must specify targetClass parameter when multiple @MapTo annotations exist. " +
                                "Example: @FieldMap(fieldName = \"id\", targetClass = UserDomain::class)",
                        )
                    }
                }
            }
        }

        mapToAnnotations.forEach { annotation ->
            val targetType = annotation.arguments.first().value as? KSType ?: return@forEach
            val targetClass = targetType.declaration as? KSClassDeclaration ?: return@forEach

            val targetFields = fieldAnalyzer.analyzeConstructorFields(targetClass)

            // External field detection
            // Exclude fields whose constructor default is usable by mapping
            // (@IgnoreDefaultValue masks a declared default → the field becomes external)
            val externalFields =
                targetFields.filter { targetField ->
                    val hasSourceMapping =
                        sourceFields.any { sourceField ->
                            hasFieldMapping(sourceField, targetField, targetClass)
                        }

                    // External if: no source mapping AND no usable constructor default
                    !hasSourceMapping && !targetField.usesDefaultInMapping
                }

            generateMappingFunction(
                sourceClass = sourceClass,
                targetClass = targetClass,
                sourceFields = sourceFields,
                targetFields = targetFields,
                externalFields = externalFields,
            )
        }
    }

    /**
     * Validates @FieldMap usage in @MapFrom context.
     * Rule: A field cannot have multiple @FieldMap annotations for the same sourceClass.
     * Reason: A domain field cannot map to multiple remote fields (data duplication/ambiguity).
     */
    private fun validateMapFromFieldMappings(
        targetClass: KSClassDeclaration,
        targetFields: List<FieldInfo>,
        sourceClass: KSClassDeclaration,
    ) {
        val sourceClassFqn = sourceClass.qualifiedName?.asString()

        targetFields.forEach { targetField ->
            // Validate the names that resolution would actually consult for THIS source:
            // a class-scoped bucket suppresses the wildcard one (same priority as
            // hasFieldMappingReverse), so specific+wildcard is a legitimate combination —
            // but two names inside the consulted bucket claim two remote fields at once.
            val specificNames = targetField.fieldMapTargets[sourceClassFqn].orEmpty()
            val mappedNames = specificNames.ifEmpty { targetField.fieldMapTargets["*"].orEmpty() }
            if (mappedNames.size > 1) {
                logger.error(
                    "@FieldMap on field '${targetField.name}' in ${targetClass.simpleName.asString()} " +
                        "has multiple mappings (${mappedNames.joinToString(
                            ", ",
                        )}) for the same source class '${sourceClass.simpleName.asString()}' in @MapFrom context. " +
                        "A domain field cannot map to multiple remote fields. " +
                        "Use different targetClass parameters if mapping to multiple @MapFrom sources.",
                )
            }
        }
    }

    /**
     * Checks if sourceField maps to targetField for the given targetClass.
     *
     * Priority:
     * 1. @Ignore check - if sourceField is ignored, return false immediately
     * 2. @FieldMap with specific targetClass (if exists, ignores direct name match)
     * 3. @FieldMap with wildcard (if exists, ignores direct name match)
     * 4. Direct name match (only if no @FieldMap for this targetClass)
     */
    private fun hasFieldMapping(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        targetClass: KSClassDeclaration,
    ): Boolean {
        // Check @Ignore - if source field is ignored, no mapping
        if (sourceField.isIgnored) return false

        val targetClassFqn = targetClass.qualifiedName?.asString()

        // Check @FieldMap with specific targetClass
        val mappedNames = sourceField.fieldMapTargets[targetClassFqn]
        if (mappedNames != null && mappedNames.isNotEmpty()) {
            // @FieldMap exists for this targetClass - check if targetField is in the list
            return targetField.name in mappedNames
        }

        // Check @FieldMap with wildcard (applies to all targets)
        val wildcardMappedNames = sourceField.fieldMapTargets["*"]
        if (wildcardMappedNames != null && wildcardMappedNames.isNotEmpty()) {
            // Wildcard @FieldMap exists - check if targetField is in the list
            return targetField.name in wildcardMappedNames
        }

        // No @FieldMap for this targetClass - use direct name match
        return sourceField.name == targetField.name
    }

    /**
     * Checks if sourceField maps to targetField for reverse mapping (checks targetField's @FieldMap).
     * Used in @MapFrom scenarios where targetField may have @FieldMap pointing to sourceClass fields.
     *
     * Priority:
     * 1. @Ignore check - if targetField is ignored, return false immediately
     * 2. @FieldMap with specific sourceClass (if exists, ignores direct name match)
     * 3. @FieldMap with wildcard (if exists, ignores direct name match)
     * 4. Direct name match (only if no @FieldMap for this sourceClass)
     */
    private fun hasFieldMappingReverse(
        sourceField: FieldInfo,
        targetField: FieldInfo,
        sourceClass: KSClassDeclaration,
    ): Boolean {
        // Check @Ignore - if target field is ignored, no mapping
        if (targetField.isIgnored) return false

        val sourceClassFqn = sourceClass.qualifiedName?.asString()

        // Check @FieldMap on target field with specific sourceClass
        val mappedNames = targetField.fieldMapTargets[sourceClassFqn]
        if (mappedNames != null && mappedNames.isNotEmpty()) {
            // @FieldMap exists for this sourceClass - check if sourceField is in the list
            return sourceField.name in mappedNames
        }

        // Check @FieldMap with wildcard (applies to all sources)
        val wildcardMappedNames = targetField.fieldMapTargets["*"]
        if (wildcardMappedNames != null && wildcardMappedNames.isNotEmpty()) {
            // Wildcard @FieldMap exists - check if sourceField is in the list
            return sourceField.name in wildcardMappedNames
        }

        // No @FieldMap for this sourceClass - use direct name match
        return sourceField.name == targetField.name
    }

    private fun generateMappingFunction(
        sourceClass: KSClassDeclaration,
        targetClass: KSClassDeclaration,
        sourceFields: List<FieldInfo>,
        targetFields: List<FieldInfo>,
        externalFields: List<FieldInfo>,
    ) {
        val packageName = sourceClass.packageName.asString()
        val functionName = functionNameGenerator.generateMapperFunctionName(targetClass)

        val sourceClassName = ClassName(packageName, sourceClass.simpleName.asString())
        val targetClassName =
            ClassName(
                targetClass.packageName.asString(),
                targetClass.simpleName.asString(),
            )

        // Pre-collect fields that will actually be emitted (skip computed; a defaulted target
        // field without a source mapping stays OMITTED — the constructor default applies).
        val fieldsToEmit =
            targetFields
                .filter { !it.isComputed }
                .mapNotNull { targetField ->
                    val sourceField =
                        sourceFields.firstOrNull { sf ->
                            hasFieldMapping(sf, targetField, targetClass)
                        }
                    if (sourceField != null || !targetField.usesDefaultInMapping) {
                        FieldEntry(targetField, sourceField)
                    } else {
                        null
                    }
                }

        val funSpec =
            buildMapperFunSpec(
                functionName = functionName,
                sourceClassName = sourceClassName,
                targetClassName = targetClassName,
                fieldsToEmit = fieldsToEmit,
                externalFields = externalFields,
                isReverse = false,
            )

        // Add to mappingFunctions collection
        val receiverKey = ReceiverKey(packageName, sourceClass.simpleName.asString())
        mappingFunctions.getOrPut(receiverKey) { mutableListOf() }.add(funSpec)
    }

    /** A target constructor parameter paired with its resolved source field (null = external). */
    private data class FieldEntry(
        val targetField: FieldInfo,
        val sourceField: FieldInfo?,
    )

    /**
     * Builds the generated mapper function with the `Result` boundary and omit/copy defaults:
     *
     * ```
     * public fun Source.toTargetResult(): Result<Target> = runCatching {
     *   if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toTargetResult, Target::class) }
     *   val base = Target(<constructor entries — no usable default>)
     *   val result = base.copy(<defaulted entries with a source mapping>)   // or: val result = base
     *   if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toTargetResult, result) }
     *   result
     * }
     * ```
     *
     * The library never throws at the caller: everything (seam errors, validation, listeners'
     * surroundings) runs inside `runCatching`, so hard failures surface as `Result.failure`.
     * Defaulted target fields are OMITTED from the constructor call (the Kotlin default
     * applies to `base`) and then overridden via `.copy()` where the seam falls back to
     * `base.<field>` — this works for any default expression, not just annotation-encodable
     * values. KotlinPoet emits the single `return runCatching { … }` statement as an
     * expression body (`= runCatching { … }`).
     */
    private fun buildMapperFunSpec(
        functionName: String,
        sourceClassName: ClassName,
        targetClassName: ClassName,
        fieldsToEmit: List<FieldEntry>,
        externalFields: List<FieldInfo>,
        isReverse: Boolean,
    ): FunSpec {
        val kMapperClass = ClassName("com.sahsenvar.kmapper", "KMapper")
        val resultTypeName = ClassName("kotlin", "Result").parameterizedBy(targetClassName)

        val constructorEntries = fieldsToEmit.filter { !it.targetField.usesDefaultInMapping }
        val copyEntries = fieldsToEmit.filter { it.targetField.usesDefaultInMapping && it.sourceField != null }

        return FunSpec
            .builder(functionName)
            .receiver(sourceClassName)
            .returns(resultTypeName)
            .apply {
                // External parameters — always required (no annotation-supplied defaults;
                // @MapDefaultValue is removed in the converter redesign).
                externalFields.forEach { field ->
                    addParameter(field.name, field.type.toTypeName())
                }
            }.addCode(
                buildCodeBlock {
                    beginControlFlow("return·runCatching")

                    // Guarded listener dispatch: onMapStart
                    addStatement(
                        "if·(%T.hasListeners)·%T.dispatch·{·onMapStart(this@%N,·%T::class)·}",
                        kMapperClass,
                        kMapperClass,
                        functionName,
                        targetClassName,
                    )

                    // Constructor stage: fields without a usable default MUST be constructed.
                    if (constructorEntries.isEmpty()) {
                        addStatement("val·base·=·%T()", targetClassName)
                    } else {
                        add("val·base·=·%T(\n", targetClassName)
                        indent()
                        constructorEntries.forEachIndexed { index, (targetField, sourceField) ->
                            val separator = if (index == constructorEntries.lastIndex) "\n" else ",\n"
                            if (sourceField != null) {
                                val strategy =
                                    typeMatcher.determineMappingStrategy(sourceField, targetField, isReverse)
                                val mappingCode =
                                    codeGen.generateFieldMapping(
                                        sourceField,
                                        targetField,
                                        strategy,
                                        isReverse,
                                        inCopyStage = false,
                                    )
                                add("%N·=·%L$separator", targetField.name, mappingCode)
                            } else {
                                // External field without constructor default
                                add("%N·=·%N$separator", targetField.name, targetField.name)
                            }
                        }
                        unindent()
                        addStatement(")")
                    }

                    // Copy stage: defaulted fields with a source mapping override the default
                    // through the OrElse seams, falling back to base.<field>.
                    if (copyEntries.isEmpty()) {
                        addStatement("val·result·=·base")
                    } else {
                        add("val·result·=·base.copy(\n")
                        indent()
                        copyEntries.forEachIndexed { index, (targetField, sourceField) ->
                            val separator = if (index == copyEntries.lastIndex) "\n" else ",\n"
                            val strategy =
                                typeMatcher.determineMappingStrategy(sourceField!!, targetField, isReverse)
                            val mappingCode =
                                codeGen.generateFieldMapping(
                                    sourceField,
                                    targetField,
                                    strategy,
                                    isReverse,
                                    inCopyStage = true,
                                )
                            add("%N·=·%L$separator", targetField.name, mappingCode)
                        }
                        unindent()
                        addStatement(")")
                    }

                    // Guarded listener dispatch: onMapComplete
                    addStatement(
                        "if·(%T.hasListeners)·%T.dispatch·{·onMapComplete(this@%N,·result)·}",
                        kMapperClass,
                        kMapperClass,
                        functionName,
                    )
                    // The runCatching block's value — NOT a return (expression value).
                    addStatement("result")
                    endControlFlow()
                },
            ).build()
    }

    private fun processMapFromAnnotation(
        resolver: Resolver,
        targetClass: KSClassDeclaration,
    ) {
        // Analyze all constructors (primary + secondary)
        val targetConstructors = fieldAnalyzer.analyzeAllConstructors(targetClass)

        // Extract all @MapFrom sources
        val mapFromAnnotations =
            targetClass.annotations.filter {
                it.shortName.asString() == "MapFrom"
            }

        // VALIDATION: multiple @MapFrom sources require explicit targetClass in @FieldMap —
        // a wildcard rename cannot disambiguate which source it talks about (mirror of the
        // multi-@MapTo rule above).
        if (mapFromAnnotations.count() > 1) {
            targetConstructors.forEach { (_, targetFields) ->
                targetFields.forEach { field ->
                    if ("*" in field.fieldMapTargets) {
                        logger.error(
                            "@FieldMap on field '${field.name}' in ${targetClass.simpleName.asString()} " +
                                "must specify targetClass parameter when multiple @MapFrom annotations exist. " +
                                "Example: @FieldMap(fieldName = \"id\", targetClass = UserRemote::class)",
                        )
                    }
                }
            }
        }

        mapFromAnnotations.forEach { annotation ->
            val sourceType = annotation.arguments.first().value as? KSType ?: return@forEach
            val sourceClass = sourceType.declaration as? KSClassDeclaration ?: return@forEach

            val sourceFields = fieldAnalyzer.analyzeConstructorFields(sourceClass)

            // Generate mapping function for each constructor
            targetConstructors.forEach { (constructor, targetFields) ->
                // VALIDATION: MapFrom does not allow multiple @FieldMap for the same sourceClass
                validateMapFromFieldMappings(targetClass, targetFields, sourceClass)
                // External field detection (fields in target not in source). Uses the SAME
                // predicate as the fieldsToEmit pairing below — externals and pairing must
                // never disagree about whether a field has a source mapping.
                // Exclude fields whose constructor default is usable by mapping.
                val externalFields =
                    targetFields.filter { targetField ->
                        val hasSourceMapping =
                            sourceFields.any { sourceField ->
                                hasFieldMappingReverse(sourceField, targetField, sourceClass)
                            }

                        // External if: no source mapping AND no usable constructor default
                        !hasSourceMapping && !targetField.usesDefaultInMapping && !targetField.isComputed
                    }

                generateReverseMappingFunction(
                    sourceClass = sourceClass,
                    targetClass = targetClass,
                    constructor = constructor,
                    sourceFields = sourceFields,
                    targetFields = targetFields,
                    externalFields = externalFields,
                )
            }
        }
    }

    private fun generateReverseMappingFunction(
        sourceClass: KSClassDeclaration,
        targetClass: KSClassDeclaration,
        constructor: KSFunctionDeclaration,
        sourceFields: List<FieldInfo>,
        targetFields: List<FieldInfo>,
        externalFields: List<FieldInfo>,
    ) {
        // For @MapFrom, the function is generated on the SOURCE class
        val packageName = sourceClass.packageName.asString()
        val functionName = functionNameGenerator.generateMapperFunctionName(targetClass)

        val sourceClassName = ClassName(packageName, sourceClass.simpleName.asString())
        val targetClassName =
            ClassName(
                targetClass.packageName.asString(),
                targetClass.simpleName.asString(),
            )

        val reverseFieldsToEmit =
            targetFields
                .filter { !it.isComputed }
                .mapNotNull { targetField ->
                    val sourceField =
                        sourceFields.firstOrNull { sf ->
                            hasFieldMappingReverse(sf, targetField, sourceClass)
                        }
                    if (sourceField != null || !targetField.usesDefaultInMapping) {
                        FieldEntry(targetField, sourceField)
                    } else {
                        null
                    }
                    // Else: field has a usable constructor default and no mapping — omitted
                }

        val funSpec =
            buildMapperFunSpec(
                functionName = functionName,
                sourceClassName = sourceClassName,
                targetClassName = targetClassName,
                fieldsToEmit = reverseFieldsToEmit,
                externalFields = externalFields,
                isReverse = true,
            )

        // Add to mappingFunctions collection
        val receiverKey = ReceiverKey(packageName, sourceClass.simpleName.asString())
        mappingFunctions.getOrPut(receiverKey) { mutableListOf() }.add(funSpec)
    }
}
