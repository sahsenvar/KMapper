package sample.fields

import com.sahsenvar.kmapper.annotations.IgnoreDefaultValue
import com.sahsenvar.kmapper.annotations.IgnoreMap
import com.sahsenvar.kmapper.annotations.MapTo

/**
 * FIELDS 2 — the Ignore family. Each annotation removes ONE thing from the mapper's view:
 *
 * - `@IgnoreMap` removes the FIELD from auto-matching. Its value never flows through the
 *   mapping; the target slot is filled by its constructor default — or, if there is none,
 *   becomes a required parameter on the generated function (see `rawPassword` below: you
 *   probably want to hash it yourself, not copy it).
 *
 * - `@IgnoreDefaultValue` removes only the field's DEFAULT from the mapping. A constructor
 *   default is often just construction convenience — it does NOT have to mean "silently fall
 *   back when the wire omits this". With the annotation, absence becomes a hard
 *   `RequiredFieldMissing` again instead of quietly using the default.
 */
data class Account(
    val email: String,
    val passwordHash: String, // no default -> @IgnoreMap on the source makes this a caller-supplied parameter
    @IgnoreDefaultValue
    val plan: String = "FREE", // "FREE" is for hand-written construction; the WIRE must always send a plan
)

@MapTo(Account::class)
data class SignUpRequest(
    val email: String,
    @IgnoreMap
    val passwordHash: String, // same name as the target, but we break the match on purpose
    val plan: String?,
)

fun main() = runIgnoreFamilyDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runIgnoreFamilyDemo() {
    val request = SignUpRequest(email = "dev@example.com", passwordHash = "plaintext-from-wire", plan = "PRO")

    // The generated signature is: toAccountResult(passwordHash: String) — the caller decides.
    val account = request.toAccountResult(passwordHash = hash(request.passwordHash)).getOrThrow()
    println("account                -> $account")

    // @IgnoreDefaultValue at work: plan missing on the wire is now an ERROR, not silently "FREE".
    val missingPlan = SignUpRequest(email = "dev@example.com", passwordHash = "x", plan = null)
        .toAccountResult(passwordHash = "irrelevant")
    println("missing plan outcome   -> ${missingPlan.exceptionOrNull()?.message}")
    // prints: Required field missing: plan
}

private fun hash(raw: String): String = "sha256:" + raw.hashCode().toUInt().toString(16)
