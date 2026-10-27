package com.sahsenvar.kmapper.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.FileLocation
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Origin
import com.sahsenvar.kmapper.processor.analyzer.CollectionWrapperDescriptor
import com.sahsenvar.kmapper.processor.analyzer.ConverterIntrospector
import com.sahsenvar.kmapper.processor.analyzer.CycleDetector
import com.sahsenvar.kmapper.processor.analyzer.FieldAnalyzer
import com.sahsenvar.kmapper.processor.analyzer.ReturnWrapper
import com.sahsenvar.kmapper.processor.analyzer.ReturnWrapperResolver
import com.sahsenvar.kmapper.processor.analyzer.TypeMatcher
import com.sahsenvar.kmapper.processor.analyzer.discoverWrappersFromConfig
import com.sahsenvar.kmapper.processor.analyzer.substituteTypeParameters
import com.sahsenvar.kmapper.processor.generator.FunctionNameGenerator
import com.sahsenvar.kmapper.processor.generator.MappingCodeGenerator
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.validator.BuiltInConverterValidator
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName

/**
 * KSP processor for generating mapping functions from @MapTo and @MapFrom annotations.
 */
class MappingProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String> = emptyMap(),
) : SymbolProcessor {
    companion object {
        private const val MAP_TO_ANNOTATION = "com.sahsenvar.kmapper.annotations.MapTo"
        private const val MAP_FROM_ANNOTATION = "com.sahsenvar.kmapper.annotations.MapFrom"
        private const val KMAPPER_CONFIG_ANNOTATION = "com.sahsenvar.kmapper.annotations.KMapperConfig"
    }

    private val fieldAnalyzer by lazy { FieldAnalyzer(logger) }
    private val functionNameGenerator by lazy { FunctionNameGenerator(logger) }
    private val codeGen by lazy { MappingCodeGenerator(logger) }

    // TypeMatcher is created fresh each round with the current custom converters and wrappers.
    private var typeMatcher: TypeMatcher = TypeMatcher(logger)

    // Created fresh each round (it holds the round's Resolver).
    private lateinit var returnWrapperResolver: ReturnWrapperResolver

    // Collect all mapping functions grouped by receiver class
    private val mappingFunctions = mutableMapOf<ReceiverKey, MutableList<FunSpec>>()

    data class ReceiverKey(
        val packageName: String,
        val className: String,
    )

    // @KMapperConfig is discovered in the round its file appears in, and `getSymbolsWithAnnotation`
    // never returns it again. Its converters and wrappers are kept here (as FQN strings, never as
    // round-scoped KS symbols) so mapping classes processed in a later round still see them.
    private val knownConverterEntries = mutableListOf<Pair<Pair<String, String>, String>>()
    private val knownCollectionWrappers = mutableMapOf<String, CollectionWrapperDescriptor>()

    // Keeps the edges of classes mapped in earlier rounds, so a cycle through a class that waited
    // for a generated type is still found.
    private val cycleDetector by lazy { CycleDetector(logger) }

    // Mappers files already written for a receiver (a receiver can get mappers in several rounds).
    private val writtenMapperFiles = mutableMapOf<ReceiverKey, Int>()

    // Classes still waiting for an unresolved type after the latest round; reported by [finish].
    private var waitingClasses: List<String> = emptyList()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Clear previous round
        mappingFunctions.clear()

        val mapToClasses =
            resolver
                .getSymbolsWithAnnotation(MAP_TO_ANNOTATION)
                .filterIsInstance<KSClassDeclaration>()
                .toList()
        val mapFromClasses =
            resolver
                .getSymbolsWithAnnotation(MAP_FROM_ANNOTATION)
                .filterIsInstance<KSClassDeclaration>()
                .toList()
        val configClasses =
            resolver
                .getSymbolsWithAnnotation(KMAPPER_CONFIG_ANNOTATION)
                .filterIsInstance<KSClassDeclaration>()
                .toList()

        // STEP -1: Defer what cannot be resolved yet, typically a type another processor generates
        // in this round. Dropping such a class (the old `.filter { it.validate() }`) let KSP finish
        // "successfully" without its mappers, and that incomplete output then went into the build
        // cache. Only what KMapper actually reads is checked (constructor parameters, annotation
        // arguments, converter/wrapper supertypes), not every member, so an unrelated member can
        // never hold a class back. A converter or wrapper that is not resolvable yet holds back the
        // whole batch, since any class could need it.
        val waitingConfig = configClasses.associateWith { unresolvedConfigParts(it) }.filterValues { it.isNotEmpty() }
        if (waitingConfig.isNotEmpty()) {
            val heldBack = (mapToClasses + mapFromClasses).distinct().size
            waitingClasses = describeWaiting(waitingConfig) + listOfNotNull("and the $heldBack mapping class(es) that wait for it".takeIf { heldBack > 0 })
            logger.info("Deferring mapping generation; @KMapperConfig not resolvable yet: ${waitingClasses.joinToString()}")
            return mapToClasses + mapFromClasses + configClasses
        }
        val waitingMappings =
            (mapToClasses + mapFromClasses)
                .distinct()
                .let { mappingClasses ->
                    val partnerPartsCache = mutableMapOf<KSClassDeclaration, List<String>>()
                    mappingClasses.associateWith { unresolvedMappingParts(it, partnerPartsCache) }
                }
                .filterValues { it.isNotEmpty() }
        waitingClasses = describeWaiting(waitingMappings)
        if (waitingMappings.isNotEmpty()) {
            logger.info("Deferring to the next round, not resolvable yet: ${waitingClasses.joinToString()}")
        }
        val readyMapToClasses = mapToClasses.filterNot { it in waitingMappings }
        val readyMapFromClasses = mapFromClasses.filterNot { it in waitingMappings }

        // STEP 0: Discover collection wrappers from @KMapperConfig.wrappers (in-module).
        //
        // discoverWrappersFromConfig reads the consumer's own @KMapperConfig annotation (in-module,
        // works on KMP/iOS) and resolves each listed wrapper object's @CollectionWrapper.forType
        // via standard dependency annotation resolution (also works cross-module in KSP2).
        // Returns Map<forTypeFqn, wrapperObjectFqn>.
        discoverWrappersFromConfig(configClasses, logger).forEach { (forTypeFqn, descriptor) ->
            val known = knownCollectionWrappers.putIfAbsent(forTypeFqn, descriptor)
            if (known != null && known.wrapperObjectFqn != descriptor.wrapperObjectFqn) {
                logger.error(
                    "Duplicate @CollectionWrapper for $forTypeFqn: ${known.wrapperObjectFqn} and " +
                        "${descriptor.wrapperObjectFqn} are both listed in @KMapperConfig.wrappers.",
                )
            }
        }

        // STEP 1: Discover custom converters from @KMapperConfig annotations.
        // Returns (typePair → converterFqn) map plus the raw entries list for duplicate-checking.
        // The introspector is shared between discovery and resolution so both walk the same
        // superclass chain (parameterized-converter bases resolve identically everywhere).
        val introspector = ConverterIntrospector(resolver, logger)
        knownConverterEntries.addAll(discoverCustomConverters(configClasses, introspector))
        // First registration wins; duplicates are reported by BuiltInConverterValidator below.
        val knownConverters = buildMap { knownConverterEntries.forEach { (typePair, converterFqn) -> putIfAbsent(typePair, converterFqn) } }
        typeMatcher = TypeMatcher(logger, knownConverters, knownCollectionWrappers, introspector)
        returnWrapperResolver = ReturnWrapperResolver(resolver, logger, options[ReturnWrapperResolver.WRAPPER_OPTION])

        // STEP 1b: Validate the global @KMapperConfig list for duplicate (S,T) pairs.
        // Per-field @UseMapTypeConverter converters are exempt — they are never checked here.
        val converterValidator = BuiltInConverterValidator(logger)
        val convertersValid = converterValidator.validate(knownConverterEntries)
        if (!convertersValid) {
            logger.error("Converter validation failed. Fix duplicate converters in @KMapperConfig.")
            waitingClasses = emptyList()
            return emptyList()
        }

        // STEP 2: Compile-time cycle detection (unconditional edges only)
        cycleDetector.check(readyMapToClasses)

        // Process @MapTo annotations (Source → Target)
        readyMapToClasses.forEach { sourceClass ->
            processMapToAnnotation(resolver, sourceClass)
        }

        // Process @MapFrom annotations (Target ← Source, reverse mapping)
        readyMapFromClasses.forEach { targetClass ->
            processMapFromAnnotation(resolver, targetClass)
        }

        // Write all collected functions to files
        writeMappingFiles()

        return waitingMappings.keys.toList()
    }

    override fun finish() {
        if (waitingClasses.isEmpty()) return
        // KSP does not report symbols that are still deferred when the rounds end. An error, not a
        // warning: a successful KSP task would put the incomplete output into the build cache.
        logger.error(
            "KMapper generated no mappers for these classes because a type they depend on never " +
                "became resolvable: ${waitingClasses.joinToString("; ")}",
        )
    }

    private fun describeWaiting(waiting: Map<KSClassDeclaration, List<String>>): List<String> = waiting.map { (declaration, parts) ->
        val name = declaration.qualifiedName?.asString() ?: declaration.simpleName.asString()
        val location = (declaration.location as? FileLocation)?.let { " at ${it.filePath}:${it.lineNumber}" }.orEmpty()
        "$name$location (${parts.joinToString()})"
    }

    /**
     * What KMapper reads from a mapping class that does not resolve yet: its constructor
     * parameters, properties and their annotations, class annotations and supertypes, and the
     * primary-constructor fields of each mapped partner. Deliberately narrow: a full `validate()`
     * also walks unrelated members, and a member whose inferred type depends on a mapper KMapper
     * is about to generate (e.g. `fun toUi() = toUserUiModel()`) would then hold the class back
     * forever. Only declarations of this compilation are walked, since a library type that does
     * not resolve now never will.
     */
    private fun unresolvedMappingParts(
        mappingClass: KSClassDeclaration,
        partnerPartsCache: MutableMap<KSClassDeclaration, List<String>>,
    ): List<String> {
        val partners =
            mappingClass.annotations
                .filter { it.shortName.asString() == "MapTo" || it.shortName.asString() == "MapFrom" }
                .mapNotNull { (it.arguments.firstOrNull()?.value as? KSType)?.takeUnless { type -> type.isError } }
                .mapNotNull { it.declaration as? KSClassDeclaration }
                .toList()
        val partnerFieldNames =
            partners
                .flatMap { partner -> partner.primaryConstructor?.parameters.orEmpty() }
                .mapNotNull { it.name?.asString() }
                .toSet()
        val ownParts =
            unresolvedFieldParts(mappingClass, allConstructors = true, matchedFieldNames = partnerFieldNames) +
                unresolvedAnnotationParts(mappingClass) +
                unresolvedSupertypeParts(mappingClass).map { "supertype $it" }
        val partnerParts =
            partners.flatMap { partner ->
                partnerPartsCache.getOrPut(partner) {
                    unresolvedFieldParts(partner, allConstructors = false, matchedFieldNames = emptySet())
                        .map { "${partner.simpleName.asString()}.$it" }
                }
            }
        return ownParts + partnerParts
    }

    /**
     * Unresolved arguments of a @KMapperConfig class and, for every converter or wrapper it lists,
     * unresolved types along its supertype chain and in its annotations, plus the wrap/unwrap
     * signatures of wrappers. Other functions are not read: a converter's
     * `override fun convertTo(value: X) = value.toY()` must not wait for KMapper's own output.
     */
    private fun unresolvedConfigParts(configClass: KSClassDeclaration): List<String> {
        val listedClasses =
            configClass.annotations
                .filter { it.shortName.asString() == "KMapperConfig" }
                .flatMap { annotation -> annotation.arguments.asSequence() }
                .flatMap { argument -> (argument.value as? List<*>).orEmpty().filterIsInstance<KSType>() }
                .mapNotNull { it.declaration as? KSClassDeclaration }
                .toList()
        val listedParts =
            listedClasses.flatMap { listed ->
                val name = listed.simpleName.asString()
                val signatureParts =
                    listed.declarations
                        .filterIsInstance<KSFunctionDeclaration>()
                        .filter { it.simpleName.asString() == "wrap" || it.simpleName.asString() == "unwrap" }
                        .flatMap { function ->
                            (function.parameters.map { it.type } + listOfNotNull(function.returnType))
                                .filter { it.resolve().containsError() }
                                .map { "$name.${function.simpleName.asString()}: $it" }
                        }.toList()
                unresolvedSupertypeParts(listed).map { "$name : $it" } +
                    unresolvedAnnotationParts(listed).map { "$name $it" } +
                    signatureParts
            }
        return unresolvedAnnotationParts(configClass) + listedParts
    }

    private fun unresolvedSupertypeParts(
        declaration: KSClassDeclaration,
        visited: MutableSet<KSClassDeclaration> = mutableSetOf(),
    ): List<String> {
        if (!declaration.isFromSource() || !visited.add(declaration)) return emptyList()
        return declaration.superTypes.toList().flatMap { reference ->
            val supertype = reference.resolve()
            if (supertype.containsError()) {
                listOf(reference.toString())
            } else {
                (supertype.declaration as? KSClassDeclaration)?.let { unresolvedSupertypeParts(it, visited) }.orEmpty()
            }
        }
    }

    /**
     * Parameter types and annotations of the primary constructor (of every constructor when
     * [allConstructors], as @MapFrom analyzes them all) and, for a declaration of this compilation,
     * its properties' annotations and types. An inferred property type is checked only when the
     * property is matched to a field of the other side ([matchedFieldNames]): otherwise it may
     * depend on KMapper's own output (see [unresolvedMappingParts]).
     */
    private fun unresolvedFieldParts(
        declaration: KSClassDeclaration,
        allConstructors: Boolean,
        matchedFieldNames: Set<String>,
    ): List<String> {
        val secondaryConstructors =
            if (allConstructors) {
                declaration.declarations
                    .filterIsInstance<KSFunctionDeclaration>()
                    .filter { it.simpleName.asString() == "<init>" && it != declaration.primaryConstructor }
                    .toList()
            } else {
                emptyList()
            }
        val parameterParts =
            (listOfNotNull(declaration.primaryConstructor) + secondaryConstructors).flatMap { constructor ->
                constructor.parameters.flatMap { parameter ->
                    val name = parameter.name?.asString()
                    val typePart = if (parameter.type.resolve().containsError()) listOf("$name: ${parameter.type}") else emptyList()
                    typePart + unresolvedAnnotationParts(parameter).map { "$name $it" }
                }
            }
        val propertyParts =
            declaration
                .getAllProperties()
                .filter { it.isFromSource() }
                .toList()
                .flatMap { property ->
                    val name = property.simpleName.asString()
                    val inferred = property.type.origin == Origin.SYNTHETIC || property.type.element == null
                    val checkedType = property.type.takeUnless { inferred && name !in matchedFieldNames }
                    val typePart = if (checkedType?.resolve()?.containsError() == true) listOf("$name: ${property.type}") else emptyList()
                    typePart + unresolvedAnnotationParts(property).map { "$name $it" }
                }
        return (parameterParts + propertyParts).distinct()
    }

    /** True for a declaration in this compilation's sources; library declarations cannot change. */
    private fun KSDeclaration.isFromSource(): Boolean = origin == Origin.KOTLIN || origin == Origin.JAVA

    /**
     * Annotation arguments that do not resolve, including a referenced class (e.g. a per-field
     * `@ConvertWith(use = …)` converter) whose own supertype chain does not resolve yet.
     */
    private fun unresolvedAnnotationParts(annotated: KSAnnotated): List<String> = annotated.annotations
        .flatMap { annotation ->
            annotation.arguments
                .filter { argument ->
                    argument.value.referencedTypes().any { type ->
                        type.containsError() ||
                            (type.declaration as? KSClassDeclaration)?.let { unresolvedSupertypeParts(it).isNotEmpty() } == true
                    }
                }.map { argument -> "@${annotation.shortName.asString()}(${argument.name?.asString()})" }
                .asSequence()
        }.toList()

    private fun Any?.referencedTypes(): List<KSType> = when (this) {
        is KSType -> listOf(this)
        is List<*> -> flatMap { it.referencedTypes() }
        else -> emptyList()
    }

    private fun KSType.containsError(): Boolean = isError || arguments.any { it.type?.resolve()?.containsError() == true }

    /**
     * Discovers custom converters declared via @KMapperConfig(converters = [...]).
     *
     * Returns the ordered raw ((sourceFqn to targetFqn) to converterFqn) entries, duplicates
     * included so the validator can report them; the caller builds the first-wins registry.
     *
     * Priority: per-field @UseMapTypeConverter > this custom registry > built-in table.
     */
    private fun discoverCustomConverters(
        configClasses: List<KSClassDeclaration>,
        introspector: ConverterIntrospector,
    ): List<Pair<Pair<String, String>, String>> {
        val entries = mutableListOf<Pair<Pair<String, String>, String>>()

        configClasses
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

        return entries
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
            // A receiver that already got a file in an earlier round gets a numbered one; Kotlin
            // resolves extension functions across files of the same package.
            val earlierFiles = writtenMapperFiles.getOrDefault(receiverKey, 0)
            writtenMapperFiles[receiverKey] = earlierFiles + 1
            val fileName = "${receiverKey.className}Mappers" + if (earlierFiles == 0) "" else "${earlierFiles + 1}"
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
                returnWrapper = returnWrapperResolver.resolve(annotation, sourceClass),
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
        returnWrapper: ReturnWrapper?,
    ) {
        val packageName = sourceClass.packageName.asString()
        val functionName = functionNameGenerator.generateMapperFunctionName(targetClass)

        // toClassName() carries ALL enclosing simple names, so a nested target/source
        // (e.g. OptionAssetModel.DailyBar) resolves as Outer.Inner instead of a bare,
        // unresolvable innermost name with a phantom top-level import (issue #18).
        val sourceClassName = sourceClass.toClassName()
        val targetClassName = targetClass.toClassName()

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
        mappingFunctions.getOrPut(receiverKey) { mutableListOf() }.apply {
            add(funSpec)
            addAll(buildWrapperFunSpecs(returnWrapper, targetClass, sourceClassName, targetClassName, externalFields))
        }
    }

    /** A target constructor parameter paired with its resolved source field (null = external). */
    private data class FieldEntry(
        val targetField: FieldInfo,
        val sourceField: FieldInfo?,
    )

    /**
     * Builds the plain core mapper function with omit/copy defaults:
     *
     * ```
     * public fun Source.toTarget(): Target {
     *   if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toTarget, Target::class) }
     *   val base = Target(<constructor entries — no usable default>)
     *   val result = base.copy(<defaulted entries with a source mapping>)   // or: val result = base
     *   if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toTarget, result) }
     *   return result
     * }
     * ```
     *
     * Hard failures (seam errors, validation) propagate as a thrown [com.sahsenvar.kmapper.MappingException];
     * a return wrapper (e.g. `KMapperWrapper.KtResult`) turns them into a value on top of this core.
     * Defaulted target fields are OMITTED from the constructor call (the Kotlin default
     * applies to `base`) and then overridden via `.copy()` where the seam falls back to
     * `base.<field>` — this works for any default expression, not just annotation-encodable
     * values.
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

        val constructorEntries = fieldsToEmit.filter { !it.targetField.usesDefaultInMapping }
        val copyEntries = fieldsToEmit.filter { it.targetField.usesDefaultInMapping && it.sourceField != null }

        return FunSpec
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
                    addStatement("return·result")
                },
            ).build()
    }

    /**
     * Builds one extension per `wrap` overload of [returnWrapper], each delegating to the plain
     * core (`[functionName]`) through the wrapper object — the same call shape for built-in and
     * user wrappers:
     *
     * ```
     * public fun Source.toTargetResult(<externals>): Result<Target> =
     *   KMapperWrapper.KtResult.wrap<Source, Target>(this) { it.toTarget(<externals>) }
     * ```
     *
     * Explicit type arguments pin the overload (e.g. `wrap(S)` vs `wrap(Flow<S>)`).
     */
    private fun buildWrapperFunSpecs(
        returnWrapper: ReturnWrapper?,
        targetClass: KSClassDeclaration,
        sourceClassName: ClassName,
        targetClassName: ClassName,
        externalFields: List<FieldInfo>,
    ): List<FunSpec> {
        if (returnWrapper == null) return emptyList()
        val coreFunctionName = functionNameGenerator.generateMapperFunctionName(targetClass)
        val wrapperFunctionName = functionNameGenerator.generateWrapperFunctionName(targetClass, returnWrapper.suffix)
        val forwardedArguments = externalFields.map { CodeBlock.of("%N·=·%N", it.name, it.name) }.joinToCode(",·")

        return returnWrapper.wrapFunctions.map { wrapFunction ->
            val bindings =
                mapOf(
                    wrapFunction.sourceTypeParameter to sourceClassName,
                    wrapFunction.targetTypeParameter to targetClassName,
                )
            val typeArguments = wrapFunction.typeParameterOrder.map { bindings.getValue(it) }
            FunSpec
                .builder(wrapperFunctionName)
                .receiver(wrapFunction.receiverShape.substituteTypeParameters(bindings))
                .returns(wrapFunction.returnShape.substituteTypeParameters(bindings))
                .apply { externalFields.forEach { addParameter(it.name, it.type.toTypeName()) } }
                .addStatement(
                    "return·%T.wrap<%L>(this)·{·it.%N(%L)·}",
                    returnWrapper.wrapperClassName,
                    typeArguments.map { CodeBlock.of("%T", it) }.joinToCode(",·"),
                    coreFunctionName,
                    forwardedArguments,
                ).build()
        }
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
                    returnWrapper = returnWrapperResolver.resolve(annotation, targetClass),
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
        returnWrapper: ReturnWrapper?,
    ) {
        // For @MapFrom, the function is generated on the SOURCE class
        val packageName = sourceClass.packageName.asString()
        val functionName = functionNameGenerator.generateMapperFunctionName(targetClass)

        // toClassName() carries ALL enclosing simple names, so a nested target/source
        // (e.g. OptionAssetModel.DailyBar) resolves as Outer.Inner instead of a bare,
        // unresolvable innermost name with a phantom top-level import (issue #18).
        val sourceClassName = sourceClass.toClassName()
        val targetClassName = targetClass.toClassName()

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
        mappingFunctions.getOrPut(receiverKey) { mutableListOf() }.apply {
            add(funSpec)
            addAll(buildWrapperFunSpecs(returnWrapper, targetClass, sourceClassName, targetClassName, externalFields))
        }
    }
}
