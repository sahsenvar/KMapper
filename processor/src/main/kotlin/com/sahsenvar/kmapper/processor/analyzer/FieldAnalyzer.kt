package com.sahsenvar.kmapper.processor.analyzer

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.sahsenvar.kmapper.processor.model.ConverterDirective
import com.sahsenvar.kmapper.processor.model.FieldInfo

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
            val validators =
                extractValidators(param).ifEmpty { property?.let { extractValidators(it) } ?: emptyList() }
            val ignoreDefaultValue =
                extractIgnoreDefaultValue(param) || (property?.let { extractIgnoreDefaultValue(it) } == true)

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
     * Extracts all @FieldMap annotations from a field/property.
     * Returns a map of targetClass FQN -> list of target field names.
     * Supports multiple @FieldMap annotations for the same targetClass.
     */
    private fun extractFieldMapTargets(annotated: KSAnnotated): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()

        annotated.annotations
            .filter {
                val shortName = it.shortName.asString()
                val qualifiedName =
                    it.annotationType
                        .resolve()
                        .declaration.qualifiedName
                        ?.asString()
                shortName == "FieldMap" || qualifiedName == "com.sahsenvar.kmapper.annotations.FieldMap"
            }.forEach { annotation ->
                val fieldName =
                    annotation.arguments
                        .firstOrNull { it.name?.asString() == "fieldName" }
                        ?.value as? String ?: return@forEach

                val targetClassArg =
                    annotation.arguments
                        .firstOrNull { it.name?.asString() == "targetClass" }
                        ?.value as? KSType

                val targetClassFqn =
                    targetClassArg
                        ?.declaration
                        ?.qualifiedName
                        ?.asString()
                        ?.takeIf { it != "kotlin.Nothing" }

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

    private fun extractFieldMapTarget(annotated: KSAnnotated): String? = extractFieldMapTargets(annotated).values.firstOrNull()?.firstOrNull()

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
        val annotationFqn = "com.sahsenvar.kmapper.annotations.$shortName"
        val annotation =
            annotated.annotations.firstOrNull {
                it.shortName.asString() == shortName ||
                    it.annotationType
                        .resolve()
                        .declaration.qualifiedName
                        ?.asString() == annotationFqn
            } ?: return null

        val useArgument = annotation.arguments.firstOrNull { it.name?.asString() == "use" }?.value as? KSType
        val useFqn =
            useArgument
                ?.declaration
                ?.qualifiedName
                ?.asString()
                ?.takeIf { it != "com.sahsenvar.kmapper.converter.MapTypeConverter" } // sentinel = unset

        val onFail =
            annotation.arguments
                .firstOrNull { it.name?.asString() == "onFail" }
                ?.value
                ?.toString()
                ?.substringAfterLast('.') ?: "Auto"

        return ConverterDirective(converterFqn = useFqn, onFail = onFail)
    }

    private fun extractIgnore(annotated: KSAnnotated): Boolean = annotated.annotations.any {
        val shortName = it.shortName.asString()
        val qualifiedName =
            it.annotationType
                .resolve()
                .declaration.qualifiedName
                ?.asString()
        shortName == "IgnoreMap" || qualifiedName == "com.sahsenvar.kmapper.annotations.IgnoreMap"
    }

    private fun extractIgnoreDefaultValue(annotated: KSAnnotated): Boolean = annotated.annotations.any {
        val shortName = it.shortName.asString()
        val qualifiedName =
            it.annotationType
                .resolve()
                .declaration.qualifiedName
                ?.asString()
        shortName == "IgnoreDefaultValue" || qualifiedName == "com.sahsenvar.kmapper.annotations.IgnoreDefaultValue"
    }

    private fun extractValidators(annotated: KSAnnotated): List<String> = extractValidatorFqns(annotated, "Validate", "com.sahsenvar.kmapper.annotations.Validate")

    private fun extractValidatorFqns(
        annotated: KSAnnotated,
        shortName: String,
        fqn: String,
    ): List<String> {
        val annotation =
            annotated.annotations.firstOrNull {
                it.shortName.asString() == shortName ||
                    it.annotationType
                        .resolve()
                        .declaration.qualifiedName
                        ?.asString() == fqn
            } ?: return emptyList()

        // vararg validators: KClass<*> vararg — annotation.arguments[0].value is List<KSType>
        @Suppress("UNCHECKED_CAST")
        val validators = annotation.arguments.firstOrNull()?.value as? List<KSType> ?: return emptyList()
        return validators.mapNotNull { it.declaration.qualifiedName?.asString() }
    }
}
