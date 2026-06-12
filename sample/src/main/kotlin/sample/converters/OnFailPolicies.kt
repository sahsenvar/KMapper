package sample.converters

import com.sahsenvar.kmapper.annotations.ConvertWith
import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.OnFail

/**
 * CONVERTERS 4 — `onFail`: hardening brokenness without touching absence.
 *
 * The ladder's default (`OnFail.Auto`) absorbs a BROKEN value wherever the type declares an
 * escape. Sometimes that is too lenient for one specific field: "this field is OPTIONAL, but
 * if it is present it must be VALID". That is `OnFail.Throw`:
 *
 * - absence stays type-driven (null target -> null) — Throw does NOT make absence an error;
 * - brokenness becomes a hard failure even though the target is nullable.
 */
data class Applicant(
    val name: String,
    val age: Int?, //          optional AND validated: missing is fine, "abc" is a bug
    val score: Int?, //        optional and lenient: missing OR broken -> null (reported)
)

@MapTo(Applicant::class)
data class ApplicationForm(
    val name: String,
    @ConvertWith(onFail = OnFail.Throw)
    val age: String?,
    val score: String?,
)

fun main() = runOnFailPoliciesDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runOnFailPoliciesDemo() {
    // Absent is absent under BOTH policies — nothing to be strict about.
    println(ApplicationForm("Grace", age = null, score = null).toApplicantResult().getOrThrow())
    //  Applicant(name=Grace, age=null, score=null)

    // Broken + Auto -> absorbed to null (and reported to the sink).
    println(ApplicationForm("Ada", age = "36", score = "n/a").toApplicantResult().getOrThrow())
    //  Applicant(name=Ada, age=36, score=null)

    // Broken + Throw -> hard failure. An invalid PRESENT value is a contract violation.
    val outcome = ApplicationForm("Bob", age = "thirty", score = "10").toApplicantResult()
    println("strict field failed -> ${outcome.exceptionOrNull()?.message}")
    //  Cannot convert age: kotlin.String -> kotlin.Int
}
