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
import com.sahsenvar.kmapper.processor.analyzer.fqn
import com.sahsenvar.kmapper.processor.generator.FunctionNameGenerator
import com.sahsenvar.kmapper.processor.generator.MappingCodeGenerator
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.validator.BuiltInConverterValidator
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName
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
        val (customConverters, globalConverterEntries) = discoverCustomConverters(resolver)
        typeMatcher = TypeMatcher(logger, customConverters, collectionWrappers, ConverterIntrospector(resolver))

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
                    val pair = converterType.toConverterPair() ?: continue
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
     * Walks a converter type's supertypes to find MapTypeConverter<S,T> and returns
     * ((sourceFqn to targetFqn) to converterFqn), or null if not a MapTypeConverter.
     */
    private fun KSType.toConverterPair(): Pair<Pair<String, String>, String>? {
        val converterDecl = declaration as? KSClassDeclaration ?: return null
        val converterFqn = converterDecl.qualifiedName?.asString() ?: return null
        val mapTypeConverterFqn = "com.sahsenvar.kmapper.converter.MapTypeConverter"

        for (supertype in converterDecl.superTypes) {
            val resolved = supertype.resolve()
            val supertypeFqn = resolved.declaration.qualifiedName?.asString() ?: continue
            if (supertypeFqn == mapTypeConverterFqn) {
                val sType =
                    resolved.arguments
                        .getOrNull(0)
                        ?.type
                        ?.resolve() ?: continue
                val tType =
                    resolved.arguments
                        .getOrNull(1)
                        ?.type
                        ?.resolve() ?: continue
                val sFqn = sType.fqn()
                val tFqn = tType.fqn()
                return (sFqn to tFqn) to converterFqn
            }
        }
        return null
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
            // Exclude fields that have constructor default values
            val externalFields =
                targetFields.filter { targetField ->
                    val hasSourceMapping =
                        sourceFields.any { sourceField ->
                            hasFieldMapping(sourceField, targetField, targetClass)
                        }

                    // External if: no source mapping AND no constructor default
                    !hasSourceMapping && !targetField.hasDefault
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
            val mappedNames = targetField.fieldMapTargets[sourceClassFqn]
            if (mappedNames != null && mappedNames.size > 1) {
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

        val kMapperClass = ClassName("com.sahsenvar.kmapper", "KMapper")
        val dispatchMember = MemberName("com.sahsenvar.kmapper", "dispatch")

        val funSpec =
            FunSpec
                .builder(functionName)
                .receiver(sourceClassName)
                .returns(targetClassName)
                .apply {
                    // External parameters — always required (no annotation-supplied defaults;
                    // @MapDefaultValue is removed in the converter redesign).
                    externalFields.forEach { field ->
                        addParameter(field.name, field.type.toTypeName())
                    }
                }.addCode(
                    buildCodeBlock {
                        // Guarded listener dispatch: onMapStart
                        addStatement(
                            "if·(%T.hasListeners)·%T.dispatch·{·onMapStart(this@%N,·%T::class)·}",
                            kMapperClass,
                            kMapperClass,
                            functionName,
                            targetClassName,
                        )

                        // Build the Target(...) constructor call into a local variable
                        add("val·result·=·%T(\n", targetClassName)
                        indent()

                        // Pre-collect fields that will actually be emitted (skip computed + defaulted)
                        data class FieldEntry(
                            val targetField: FieldInfo,
                            val sourceField: FieldInfo?,
                        )

                        val fieldsToEmit =
                            targetFields
                                .filter { !it.isComputed }
                                .mapNotNull { targetField ->
                                    val sourceField =
                                        sourceFields.firstOrNull { sf ->
                                            hasFieldMapping(sf, targetField, targetClass)
                                        }
                                    if (sourceField != null || !targetField.hasDefault) {
                                        FieldEntry(targetField, sourceField)
                                    } else {
                                        null
                                    }
                                }

                        fieldsToEmit.forEachIndexed { index, (targetField, sourceField) ->
                            val separator = if (index == fieldsToEmit.lastIndex) "\n" else ",\n"
                            if (sourceField != null) {
                                val strategy =
                                    typeMatcher.determineMappingStrategy(sourceField, targetField)
                                val mappingCode =
                                    codeGen.generateFieldMapping(sourceField, targetField, strategy)
                                add("%N·=·%L$separator", targetField.name, mappingCode)
                            } else {
                                // External field without constructor default
                                add("%N·=·%N$separator", targetField.name, targetField.name)
                            }
                        }

                        unindent()
                        addStatement(")")

                        // Guarded listener dispatch: onMapComplete
                        addStatement(
                            "if·(%T.hasListeners)·%T.dispatch·{·onMapComplete(this@%N,·result)·}",
                            kMapperClass,
                            kMapperClass,
                            functionName,
                        )
                        addStatement("return·result")
                    },
                ).build()

        // Add to mappingFunctions collection
        val receiverKey = ReceiverKey(packageName, sourceClass.simpleName.asString())
        mappingFunctions.getOrPut(receiverKey) { mutableListOf() }.add(funSpec)
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

        mapFromAnnotations.forEach { annotation ->
            val sourceType = annotation.arguments.first().value as? KSType ?: return@forEach
            val sourceClass = sourceType.declaration as? KSClassDeclaration ?: return@forEach

            val sourceFields = fieldAnalyzer.analyzeConstructorFields(sourceClass)

            // Generate mapping function for each constructor
            targetConstructors.forEach { (constructor, targetFields) ->
                // VALIDATION: MapFrom does not allow multiple @FieldMap for the same sourceClass
                validateMapFromFieldMappings(targetClass, targetFields, sourceClass)
                // External field detection (fields in target not in source)
                // Exclude fields that have constructor default values
                val externalFields =
                    targetFields.filter { targetField ->
                        val hasSourceMapping =
                            sourceFields.any { sourceField ->
                                sourceField.name == targetField.name ||
                                    targetField.fieldMapTarget == sourceField.name
                            }

                        // External if: no source mapping AND no constructor default
                        !hasSourceMapping && !targetField.hasDefault && !targetField.isComputed
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

        val kMapperClass = ClassName("com.sahsenvar.kmapper", "KMapper")

        val funSpec =
            FunSpec
                .builder(functionName)
                .receiver(sourceClassName)
                .returns(targetClassName)
                .apply {
                    // External parameters — always required (no annotation-supplied defaults;
                    // @MapDefaultValue is removed in the converter redesign).
                    externalFields.forEach { field ->
                        addParameter(field.name, field.type.toTypeName())
                    }
                }.addCode(
                    buildCodeBlock {
                        // Guarded listener dispatch: onMapStart
                        addStatement(
                            "if·(%T.hasListeners)·%T.dispatch·{·onMapStart(this@%N,·%T::class)·}",
                            kMapperClass,
                            kMapperClass,
                            functionName,
                            targetClassName,
                        )

                        add("val·result·=·%T(\n", targetClassName)
                        indent()

                        data class ReverseFieldEntry(
                            val targetField: FieldInfo,
                            val sourceField: FieldInfo?,
                        )

                        val reverseFieldsToEmit =
                            targetFields
                                .filter { !it.isComputed }
                                .mapNotNull { targetField ->
                                    val sourceField =
                                        sourceFields.firstOrNull { sf ->
                                            hasFieldMappingReverse(sf, targetField, sourceClass)
                                        }
                                    if (sourceField != null || !targetField.hasDefault) {
                                        ReverseFieldEntry(targetField, sourceField)
                                    } else {
                                        null
                                    }
                                    // Else: field has constructor default, skip it
                                }

                        reverseFieldsToEmit.forEachIndexed { index, (targetField, sourceField) ->
                            val separator = if (index == reverseFieldsToEmit.lastIndex) "\n" else ",\n"

                            if (sourceField != null) {
                                // Has source mapping
                                val strategy =
                                    typeMatcher.determineMappingStrategy(
                                        sourceField,
                                        targetField,
                                        isReverse = true,
                                    )
                                val mappingCode =
                                    codeGen.generateFieldMapping(
                                        sourceField,
                                        targetField,
                                        strategy,
                                        isReverse = true,
                                    )
                                add("%N·=·%L$separator", targetField.name, mappingCode)
                            } else {
                                // External field without constructor default
                                add("%N·=·%N$separator", targetField.name, targetField.name)
                            }
                        }

                        unindent()
                        addStatement(")")

                        // Guarded listener dispatch: onMapComplete
                        addStatement(
                            "if·(%T.hasListeners)·%T.dispatch·{·onMapComplete(this@%N,·result)·}",
                            kMapperClass,
                            kMapperClass,
                            functionName,
                        )
                        addStatement("return·result")
                    },
                ).build()

        // Add to mappingFunctions collection
        val receiverKey = ReceiverKey(packageName, sourceClass.simpleName.asString())
        mappingFunctions.getOrPut(receiverKey) { mutableListOf() }.add(funSpec)
    }
}
