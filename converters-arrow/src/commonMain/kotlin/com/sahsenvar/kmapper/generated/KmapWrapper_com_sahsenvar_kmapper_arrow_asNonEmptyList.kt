package com.sahsenvar.kmapper.generated

import com.sahsenvar.kmapper.annotations.CollectionWrapperDescriptor

// Static descriptor — committed as source so consumers can discover this wrapper via
// getDeclarationsFromPackage("com.sahsenvar.kmapper.generated") in any KSP compilation mode,
// including KSP2 kspCommonMainMetadata where generated-output klibs from dependency invocations
// are not visible to the consumer module's processor invocation.
@CollectionWrapperDescriptor(
    forType = "arrow.core.NonEmptyList",
    wrapFunction = "com.sahsenvar.kmapper.arrow.asNonEmptyList"
)
public object KmapWrapper_com_sahsenvar_kmapper_arrow_asNonEmptyList
