package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.sahsenvar.kmapper.processor.model.ConverterDirective
import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.sahsenvar.kmapper.processor.model.OnFailPolicy

/**
 * Analyzes fields (constructor parameters) and extracts mapping annotations.
 */
class FieldAnalyzer(
    private val logger: KSPLogger,
) {
    /**
     * Analyzes all constructors (primary + secondary) and returns a map of constructor -> field list.
     */
    fun analyzeAllConstructors(classDecl: KSClassDeclaration): Map<KSFunctionDeclaration, List<FieldInfo>> {
        val result = mutableMapOf<KSFunctionDeclaration, List<FieldInfo>>()

        // 1. Primary constructor
        classDecl.primaryConstructor?.let { constructor ->
            result[constructor] = analyzeConstructor(classDecl, constructor)
        }

        // 2. Secondary constructors
        classDecl.declarations
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.simpleName.asString() == "<init>" }
            .forEach { constructor ->
                result[constructor] = analyzeConstructor(classDecl, constructor)
            }

        return result
    }

    fun analyzeConstructorFields(classDecl: KSClassDeclaration): List<FieldInfo> {
        // Legacy method - returns primary constructor fields + computed properties
        return classDecl.primaryConstructor?.let { analyzeConstructor(classDecl, it) }
            ?: emptyList()
    }

    private fun analyzeConstructor(
        classDecl: KSClassDeclaration,
        constructor: KSFunctionDeclaration,
    ): List<FieldInfo> {
        val constructorParamNames =
            constructor.parameters.mapNotNull { it.name?.asString() }.toSet()

        // Get all properties (both constructor parameters and computed properties)
        val allProperties = classDecl.getAllProperties().toList()
        val propertyAnnotationsMap = allProperties.associateBy { it.simpleName.asString() }

        val result = mutableListOf<FieldInfo>()

        // 1. Add constructor parameters
        constructor.parameters.forEach { param ->
            val fieldName = param.name?.asString() ?: ""
            val property = propertyAnnotationsMap[fieldName]

            // Merge @FieldMap annotations from both param and property
            val fieldMapTargets =
                buildMap {
                    putAll(extractFieldMapTargets(param))
                    property?.let { putAll(extractFieldMapTargets(it)) }
                }

            val convertWith =
                extractConverterDirective(param, "ConvertWith")
                    ?: property?.let { extractConverterDirective(it, "ConvertWith") }
            val convertToDirective =
                extractConverterDirective(param, "ConvertTo")
                    ?: property?.let { extractConverterDirective(it, "ConvertTo") }
            val convertFromDirective =
                extractConverterDirective(param, "ConvertFrom")
                    ?: property?.let { extractConverterDirective(it, "ConvertFrom") }
            val isIgnored = extractIgnore(param) || (property?.let { extractIgnore(it) } == true)
            // UNION of param-site and property-site validators (param-site first), deduplicated preserving order.
            val validators =
                (extractValidators(param) + (property?.let { extractValidators(it) } ?: emptyList())).distinct()
            val ignoreDefaultValue =
                extractIgnoreDefaultValue(param) || (property?.let { extractIgnoreDefaultValue(it) } == true)
            if (ignoreDefaultValue && !param.hasDefault) {
                logger.warn(
                    "$fieldName: @IgnoreDefaultValue has no effect — the field declares no constructor default.",
                )
            }

            result.add(
                FieldInfo(
                    name = fieldName,
                    type = param.type.resolve(),
                    isNullable = param.type.resolve().isMarkedNullable,
                    hasDefault = param.hasDefault,
                    isComputed = false,
                    fieldMapTargets = fieldMapTargets,
                    isIgnored = isIgnored,
                    convertWith = convertWith,
                    convertToDirective = convertToDirective,
                    convertFromDirective = convertFromDirective,
                    validators = validators,
                    ignoreDefaultValue = ignoreDefaultValue,
                ),
            )
        }

        // 2. Add computed properties (only for primary constructor)
        if (constructor == classDecl.primaryConstructor) {
            allProperties.forEach { property ->
                val propertyName = property.simpleName.asString()
                if (propertyName !in constructorParamNames) {
                    // This is a computed property (e.g., val fullName get() = ...)
                    val fieldMapTargets = extractFieldMapTargets(property)
                    val convertWith = extractConverterDirective(property, "ConvertWith")
                    val convertToDirective = extractConverterDirective(property, "ConvertTo")
                    val convertFromDirective = extractConverterDirective(property, "ConvertFrom")
                    val isIgnored = extractIgnore(property)
                    val validators = extractValidators(property)
                    val ignoreDefaultValue = extractIgnoreDefaultValue(property)
                    if (ignoreDefaultValue) {
                        // Computed properties never have a constructor default to ignore.
                        logger.warn(
                            "$propertyName: @IgnoreDefaultValue has no effect — " +
                                "the field declares no constructor default.",
                        )
                    }

                    result.add(
                        FieldInfo(
                            name = propertyName,
                            type = property.type.resolve(),
                            isNullable = property.type.resolve().isMarkedNullable,
                            hasDefault = false, // Computed properties don't have constructor defaults
                            isComputed = true,
                            fieldMapTargets = fieldMapTargets,
                            isIgnored = isIgnored,
                            convertWith = convertWith,
                            convertToDirective = convertToDirective,
                            convertFromDirective = convertFromDirective,
                            validators = validators,
                            ignoreDefaultValue = ignoreDefaultValue,
                        ),
                    )
                }
            }
        }

        return result
    }

    /**
     * Shared KMapper-annotation matcher: shortName fast-path first (no resolve() cost),
     * falling back to the resolved `com.sahsenvar.kmapper.annotations.<shortName>` FQN.
     */
    private fun KSAnnotation.isKMapperAnnotation(annotationShortName: String): Boolean = shortName.asString() == annotationShortName ||
        annotationType
            .resolve()
            .declaration.qualifiedName
            ?.asString() == "com.sahsenvar.kmapper.annotations.$annotationShortName"

    /** First KMapper annotation named [annotationShortName] on this node, or null (shortName-OR-fqn semantics). */
    private fun KSAnnotated.findKMapperAnnotation(annotationShortName: String): KSAnnotation? = annotations.firstOrNull { it.isKMapperAnnotation(annotationShortName) }

    /**
     * Extracts all @FieldMap annotations from a field/property.
     * Returns a map of targetClass FQN -> list of target field names.
     * Supports multiple @FieldMap annotations for the same targetClass.
     */
    private fun extractFieldMapTargets(annotated: KSAnnotated): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        annotated.annotations
            .filter { it.isKMapperAnnotation("FieldMap") }
            .forEach { annotation ->
                val fieldName =
                    annotation.arguments
                        .firstOrNull { it.name?.asString() == "fieldName" }
                        ?.value as? String ?: return@forEach

                val targetClassArg =
                    annotation.arguments
                        .firstOrNull { it.name?.asString() == "targetClass" }
                        ?.value as? KSType

                // `Nothing::class` is the "no targetClass" sentinel. KSP resolves the OMITTED
                // default through the Java mirror as java.lang.Void, so both spellings mean
                // "wildcard" — missing the Void form silently buckets the rename under a key
                // no lookup ever asks for.
                val targetClassFqn =
                    targetClassArg
                        ?.declaration
                        ?.qualifiedName
                        ?.asString()
                        ?.takeIf { it != "kotlin.Nothing" && it != "java.lang.Void" }

                if (targetClassFqn != null) {
                    // Explicit targetClass specified - add to list
                    result.getOrPut(targetClassFqn) { mutableListOf() }.add(fieldName)
                } else {
                    // No targetClass -> applies to all targets (legacy behavior)
                    result.getOrPut("*") { mutableListOf() }.add(fieldName)
                }
            }

        return result
    }

    /**
     * Extracts a [ConverterDirective] from the @ConvertWith / @ConvertTo / @ConvertFrom
     * annotation named [shortName], or null when the annotation is absent.
     *
     * The `use` parameter's sentinel default (MapTypeConverter::class) means "keep
     * auto-discovery" and is read as a null [ConverterDirective.converterFqn].
     */
    private fun extractConverterDirective(
        annotated: KSAnnotated,
        shortName: String,
    ): ConverterDirective? {
        val annotation = annotated.findKMapperAnnotation(shortName) ?: return null

        val useArgument = annotation.arguments.firstOrNull { it.name?.asString() == "use" }?.value as? KSType
        val useFqn =
            useArgument
                ?.declaration
                ?.qualifiedName
                ?.asString()
                ?.takeIf { it != "com.sahsenvar.kmapper.converter.MapTypeConverter" } // sentinel = unset

        val onFail =
            OnFailPolicy.parse(
                annotation.arguments
                    .firstOrNull { it.name?.asString() == "onFail" }
                    ?.value
                    ?.toString()
                    ?.substringAfterLast('.'),
            )

        return ConverterDirective(converterFqn = useFqn, onFail = onFail)
    }

    private fun extractIgnore(annotated: KSAnnotated): Boolean = annotated.findKMapperAnnotation("IgnoreMap") != null

    private fun extractIgnoreDefaultValue(annotated: KSAnnotated): Boolean = annotated.findKMapperAnnotation("IgnoreDefaultValue") != null

    private fun extractValidators(annotated: KSAnnotated): List<String> {
        val annotation = annotated.findKMapperAnnotation("Validate") ?: return emptyList()

        // vararg validators: KClass<*> vararg — annotation.arguments[0].value is List<KSType>
        @Suppress("UNCHECKED_CAST")
        val validators = annotation.arguments.firstOrNull()?.value as? List<KSType> ?: return emptyList()
        return validators.mapNotNull { it.declaration.qualifiedName?.asString() }
    }
}
