package com.sahsenvar.kmapper.processor.analyzer

import com.sahsenvar.kmapper.processor.model.FieldInfo
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Analyzes fields (constructor parameters) and extracts mapping annotations.
 */
class FieldAnalyzer(private val logger: KSPLogger) {

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
        constructor: KSFunctionDeclaration
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
            val fieldMapTargets = buildMap {
                putAll(extractFieldMapTargets(param))
                property?.let { putAll(extractFieldMapTargets(it)) }
            }

            val mapDefaultValue =
                extractMapDefaultValue(param) ?: property?.let { extractMapDefaultValue(it) }
            val useConverter =
                extractUseConverter(param) ?: property?.let { extractUseConverter(it) }
            val isIgnored = extractIgnore(param) || (property?.let { extractIgnore(it) } == true)
            val validateFrom =
                extractValidateFrom(param).ifEmpty { property?.let { extractValidateFrom(it) } ?: emptyList() }
            val validateTo =
                extractValidateTo(param).ifEmpty { property?.let { extractValidateTo(it) } ?: emptyList() }

            result.add(
                FieldInfo(
                    name = fieldName,
                    type = param.type.resolve(),
                    isNullable = param.type.resolve().isMarkedNullable,
                    hasDefault = param.hasDefault,
                    defaultValue = mapDefaultValue,
                    isComputed = false,
                    fieldMapTargets = fieldMapTargets,
                    useConverter = useConverter,
                    isIgnored = isIgnored,
                    validateFrom = validateFrom,
                    validateTo = validateTo,
                )
            )
        }

        // 2. Add computed properties (only for primary constructor)
        if (constructor == classDecl.primaryConstructor) {
            allProperties.forEach { property ->
                val propertyName = property.simpleName.asString()
                if (propertyName !in constructorParamNames) {
                    // This is a computed property (e.g., val fullName get() = ...)
                    val fieldMapTargets = extractFieldMapTargets(property)
                    val mapDefaultValue = extractMapDefaultValue(property)
                    val useConverter = extractUseConverter(property)
                    val isIgnored = extractIgnore(property)
                    val validateFrom = extractValidateFrom(property)
                    val validateTo = extractValidateTo(property)

                    result.add(
                        FieldInfo(
                            name = propertyName,
                            type = property.type.resolve(),
                            isNullable = property.type.resolve().isMarkedNullable,
                            hasDefault = false, // Computed properties don't have constructor defaults
                            defaultValue = mapDefaultValue,
                            isComputed = true,
                            fieldMapTargets = fieldMapTargets,
                            useConverter = useConverter,
                            isIgnored = isIgnored,
                            validateFrom = validateFrom,
                            validateTo = validateTo,
                        )
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
                    it.annotationType.resolve().declaration.qualifiedName?.asString()
                shortName == "FieldMap" || qualifiedName == "com.sahsenvar.kmapper.annotations.FieldMap"
            }
            .forEach { annotation ->
                val fieldName = annotation.arguments
                    .firstOrNull { it.name?.asString() == "fieldName" }
                    ?.value as? String ?: return@forEach

                val targetClassArg = annotation.arguments
                    .firstOrNull { it.name?.asString() == "targetClass" }
                    ?.value as? KSType

                val targetClassFqn = targetClassArg?.declaration?.qualifiedName?.asString()
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

    private fun extractFieldMapTarget(annotated: KSAnnotated): String? {
        return extractFieldMapTargets(annotated).values.firstOrNull()?.firstOrNull()
    }

    private fun extractMapDefaultValue(annotated: KSAnnotated): String? {
        val annotation = annotated.annotations.firstOrNull {
            it.shortName.asString() == "MapDefaultValue"
        } ?: return null

        return annotation.arguments.first().value as? String
    }

    private fun extractUseConverter(annotated: KSAnnotated): String? {
        val annotation = annotated.annotations.firstOrNull {
            val shortName = it.shortName.asString()
            val qualifiedName = it.annotationType.resolve().declaration.qualifiedName?.asString()
            shortName == "UseMapTypeConverter" || qualifiedName == "com.sahsenvar.kmapper.annotations.UseMapTypeConverter"
        } ?: return null

        val converterType = annotation.arguments.first().value as? KSType
        return converterType?.declaration?.qualifiedName?.asString()
    }

    private fun extractIgnore(annotated: KSAnnotated): Boolean {
        return annotated.annotations.any {
            val shortName = it.shortName.asString()
            val qualifiedName = it.annotationType.resolve().declaration.qualifiedName?.asString()
            shortName == "Ignore" || qualifiedName == "com.sahsenvar.kmapper.annotations.Ignore"
        }
    }

    private fun extractValidateFrom(annotated: KSAnnotated): List<String> =
        extractValidatorFqns(annotated, "ValidateFrom", "com.sahsenvar.kmapper.annotations.ValidateFrom")

    private fun extractValidateTo(annotated: KSAnnotated): List<String> =
        extractValidatorFqns(annotated, "ValidateTo", "com.sahsenvar.kmapper.annotations.ValidateTo")

    private fun extractValidatorFqns(annotated: KSAnnotated, shortName: String, fqn: String): List<String> {
        val annotation = annotated.annotations.firstOrNull {
            it.shortName.asString() == shortName ||
                it.annotationType.resolve().declaration.qualifiedName?.asString() == fqn
        } ?: return emptyList()
        // vararg validators: KClass<*> vararg — annotation.arguments[0].value is List<KSType>
        @Suppress("UNCHECKED_CAST")
        val validators = annotation.arguments.firstOrNull()?.value as? List<KSType> ?: return emptyList()
        return validators.mapNotNull { it.declaration.qualifiedName?.asString() }
    }
}
