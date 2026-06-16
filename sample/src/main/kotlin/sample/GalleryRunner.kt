package sample

import sample.basics.runBasicMappingDemo
import sample.basics.runMultipleTargetsDemo
import sample.basics.runReverseMappingDemo
import sample.collections.runElementPoliciesDemo
import sample.collections.runListMappingDemo
import sample.collections.runSetAndMapMappingDemo
import sample.collections.runWrappedCollectionsDemo
import sample.converters.runBuiltInConvertersDemo
import sample.converters.runCustomConverterDemo
import sample.converters.runOnFailPoliciesDemo
import sample.converters.runOneWayConvertersDemo
import sample.converters.runParameterizedConvertersDemo
import sample.converters.runPerFieldOverrideDemo
import sample.converters.runSanctionedNullDemo
import sample.enums.runEnumMappingDemo
import sample.enums.runSerializableEnumMappingDemo
import sample.fields.runExternalParametersDemo
import sample.fields.runFieldRenamingDemo
import sample.fields.runIgnoreFamilyDemo
import sample.handwritten.runCoreOnlyMappingDemo
import sample.nested.runDeepErrorPathsDemo
import sample.nested.runNestedObjectsDemo
import sample.nullability.runFallbackLadderDemo
import sample.nullability.runResultBoundaryDemo
import sample.observability.runListenersAndSinkDemo
import sample.validation.runFieldValidationDemo

/**
 * Runs every example in the gallery in learning-path order:
 *
 *     ./gradlew sample:runSample
 */
fun main() {
    banner("sample.basics.BasicMapping")
    runBasicMappingDemo()
    banner("sample.basics.MultipleTargets")
    runMultipleTargetsDemo()
    banner("sample.basics.ReverseMapping")
    runReverseMappingDemo()
    banner("sample.collections.ElementPolicies")
    runElementPoliciesDemo()
    banner("sample.collections.ListMapping")
    runListMappingDemo()
    banner("sample.collections.SetAndMapMapping")
    runSetAndMapMappingDemo()
    banner("sample.collections.WrappedCollections")
    runWrappedCollectionsDemo()
    banner("sample.converters.BuiltInConverters")
    runBuiltInConvertersDemo()
    banner("sample.converters.CustomConverter")
    runCustomConverterDemo()
    banner("sample.converters.OnFailPolicies")
    runOnFailPoliciesDemo()
    banner("sample.converters.OneWayConverters")
    runOneWayConvertersDemo()
    banner("sample.converters.ParameterizedConverters")
    runParameterizedConvertersDemo()
    banner("sample.converters.PerFieldOverride")
    runPerFieldOverrideDemo()
    banner("sample.converters.SanctionedNull")
    runSanctionedNullDemo()
    banner("sample.enums.EnumMapping")
    runEnumMappingDemo()
    banner("sample.enums.SerializableEnumMapping")
    runSerializableEnumMappingDemo()
    banner("sample.fields.ExternalParameters")
    runExternalParametersDemo()
    banner("sample.fields.FieldRenaming")
    runFieldRenamingDemo()
    banner("sample.fields.IgnoreFamily")
    runIgnoreFamilyDemo()
    banner("sample.handwritten.CoreOnlyMapping")
    runCoreOnlyMappingDemo()
    banner("sample.nested.DeepErrorPaths")
    runDeepErrorPathsDemo()
    banner("sample.nested.NestedObjects")
    runNestedObjectsDemo()
    banner("sample.nullability.FallbackLadder")
    runFallbackLadderDemo()
    banner("sample.nullability.ResultBoundary")
    runResultBoundaryDemo()
    banner("sample.observability.ListenersAndSink")
    runListenersAndSinkDemo()
    banner("sample.validation.FieldValidation")
    runFieldValidationDemo()
}

private fun banner(title: String) {
    println()
    println("=== " + title + " " + "=".repeat((70 - title.length).coerceAtLeast(3)))
}
