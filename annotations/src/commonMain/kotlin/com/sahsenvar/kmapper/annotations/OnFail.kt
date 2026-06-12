package com.sahsenvar.kmapper.annotations

/**
 * Brokenness policy for a failed conversion. Absence is ALWAYS type-driven
 * (nullable/default) — never a policy.
 *
 * Fallback ladder, in order: converted > default > null/not-a-member > error.
 * A sanctioned null — an `OrNull` converter variant declaring "this input has no
 * legitimate counterpart" (see `MapTypeConverter`) — is legitimate flow, not a
 * failure, so it is exempt even under [Throw]. [Skip] applies to collection
 * elements only.
 */
enum class OnFail {
    /** Follow the fallback ladder (default): converted > default > null/not-a-member > error. */
    Auto,

    /**
     * Harden brokenness: a failed conversion is a hard error even with a declared escape.
     * Sanctioned nulls (`MapTypeConverter` OrNull variants) still pass — they are not failures.
     */
    Throw,

    /** Collection elements only: drop the failed element instead of null-in-place (reported). */
    Skip,
}
