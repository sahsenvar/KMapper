package sample.validation

import com.sahsenvar.kmapper.annotations.MapTo
import com.sahsenvar.kmapper.annotations.Validate
import com.sahsenvar.kmapper.validation.builtin.NotBlankValidator
import com.sahsenvar.kmapper.validation.builtin.RegexValidator
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
 * Three places validators come from:
 *   - core built-ins (`com.sahsenvar.kmapper.validation.builtin`): NotBlank, length, ranges, sign…
 *   - the `kmapper-validators` add-on: Email, Url, PhoneE164, Ipv4, Uuid, Slug…
 *   - your own: any `object` extending `Validator<T>` — or, for parameterized rules, an object
 *     subclassing a built-in base like [RegexValidator] (the same recipe the library itself uses).
 */
object CouponCodeValidator : RegexValidator(Regex("[A-Z]{4}-\\d{2}"), "must be a coupon code like SAVE-20")

data class Member(
    @Validate(NotBlankValidator::class)
    val displayName: String, // validated AFTER mapping produces it (target-side anchor)
    val email: String,
    val couponCode: String,
)

@MapTo(Member::class)
data class RegistrationForm(
    val displayName: String,
    @Validate(EmailValidator::class)
    val email: String, // validated BEFORE conversion (source-side anchor)
    @Validate(CouponCodeValidator::class)
    val couponCode: String, // custom parameterized validator (RegexValidator subclass)
)

fun main() = runFieldValidationDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runFieldValidationDemo() {
    println(RegistrationForm("Grace", "grace@navy.mil", "SAVE-20").toMemberResult().getOrThrow())

    val badEmail = RegistrationForm("Grace", "not-an-email", "SAVE-20").toMemberResult()
    println("source-side validator -> ${badEmail.exceptionOrNull()?.message}")
    //  Validation failed for 'email': must be a valid email

    val blankName = RegistrationForm("   ", "grace@navy.mil", "SAVE-20").toMemberResult()
    println("target-side validator -> ${blankName.exceptionOrNull()?.message}")
    //  Validation failed for 'displayName': must not be blank

    val badCoupon = RegistrationForm("Grace", "grace@navy.mil", "save20").toMemberResult()
    println("custom validator -> ${badCoupon.exceptionOrNull()?.message}")
    //  Validation failed for 'couponCode': must be a coupon code like SAVE-20
}
