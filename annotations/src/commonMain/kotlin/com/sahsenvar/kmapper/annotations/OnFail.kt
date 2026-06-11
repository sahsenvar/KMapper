package com.sahsenvar.kmapper.annotations

/** Brokenness policy. Absence is ALWAYS type-driven (nullable/default) — never a policy. */
enum class OnFail {
    /** Follow the fallback ladder (default). */
    Auto,

    /** Harden brokenness: a failed conversion is a hard error even with a declared escape. */
    Throw,

    /** Collection elements only: drop instead of null-in-place (reported). */
    Skip,
}
