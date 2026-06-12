package sample.validation

import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.Validate
import com.sahsenvar.kmapper.validation.Validator
import com.sahsenvar.kmapper.validators.EmailValidator

/**
 * VALIDATION — field-anchored `@Validate`.
 *
 * A validator is anchored to the FIELD, not to a mapping direction: whenever the annotated
 * field enters a mapping —
 *   - as a SOURCE, its value is validated BEFORE conversion,
 *   - as a TARGET, the produced value is validated AFTER conversion —
 * in every generated direction. Put validators on the model that OWNS the rule (usually the
 * domain model: one declaration covers data->domain and domain->presentation alike).
 *
 * A failed validator is always a hard `ValidationFailed` carrying the field path — validation
 * is a declared invariant, not something the ladder absorbs.
 *
 * Custom validators are objects extending [Validator]; `EmailValidator` ships in the
 * `kmapper-validators` add-on.
 */
object NotBlankValidator : Validator<String>(String::class) {
    override fun validate(value: String): String? = if (value.isNotBlank()) null else "must not be blank"
}

data class Member(
    @Validate(NotBlankValidator::class)
    val displayName: String, // validated AFTER mapping produces it (target-side anchor)
    val email: String,
)

@MapTo(Member::class)
data class RegistrationForm(
    val displayName: String,
    @Validate(EmailValidator::class)
    val email: String, // validated BEFORE conversion (source-side anchor)
)

fun main() = runFieldValidationDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runFieldValidationDemo() {
    println(RegistrationForm("Grace", "grace@navy.mil").toMemberResult().getOrThrow())

    val badEmail = RegistrationForm("Grace", "not-an-email").toMemberResult()
    println("source-side validator -> ${badEmail.exceptionOrNull()?.message}")
    //  Validation failed for 'email': must be a valid email

    val blankName = RegistrationForm("   ", "grace@navy.mil").toMemberResult()
    println("target-side validator -> ${blankName.exceptionOrNull()?.message}")
    //  Validation failed for 'displayName': must not be blank
}
