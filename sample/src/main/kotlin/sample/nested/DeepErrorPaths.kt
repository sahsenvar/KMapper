package sample.nested

import com.sahsenvar.kmapper.MappingException
import com.sahsenvar.kmapper.annotations.MapTo

/**
 * NESTED 2 — deep failures tell you WHERE, and types bound the blast radius.
 *
 * 1. Every mapping error carries a field PATH from the root: a broken zip code two levels
 *    down reports `address.zipCode`, not just "zipCode". (Paths are compile-time string
 *    literals — they survive R8/proguard untouched.)
 * 2. The "blast radius" — how far one deep error spreads — is declared by your TYPES,
 *    GraphQL-style: an error climbs until a nullable (or defaulted) field absorbs it;
 *    with no escape on the way up, the whole mapping fails.
 */

// ---- Demo A: a declared escape absorbs the deep failure --------------------------------

data class GeoPoint(
    val lat: Double,
    val lon: Double,
)

data class Venue(
    val name: String,
    val location: GeoPoint?, // <- declared escape: a broken GeoPoint costs ONLY this field
)

@MapTo(GeoPoint::class)
data class GeoPointResponse(
    val lat: String,
    val lon: String,
)

@MapTo(Venue::class)
data class VenueResponse(
    val name: String,
    val location: GeoPointResponse?,
)

// ---- Demo B: a required chain end to end — the failure names the full path -------------

data class ParcelAddress(
    val street: String,
    val zipCode: Int,
)

data class Parcel(
    val trackingId: Long,
    val address: ParcelAddress, // no escape anywhere on this chain
)

@MapTo(ParcelAddress::class)
data class ParcelAddressResponse(
    val street: String,
    val zipCode: String,
)

@MapTo(Parcel::class)
data class ParcelResponse(
    val trackingId: Long,
    val address: ParcelAddressResponse,
)

fun main() = runDeepErrorPathsDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runDeepErrorPathsDemo() {
    // A: broken lon, absorbed at the nullable `location` — the Venue survives, minus one field.
    // (The absorption is REPORTED with the deep cause path; see sample.observability.)
    val venue = VenueResponse(
        name = "Convention Center",
        location = GeoPointResponse(lat = "41.0", lon = "not-a-longitude"),
    ).toVenueResult().getOrThrow()
    println("absorbed at the declared escape -> $venue")
    //  Venue(name=Convention Center, location=null)

    // B: same kind of break under a REQUIRED chain — the whole mapping fails, and the
    // exception's path walks you straight to the culprit.
    val outcome = ParcelResponse(
        trackingId = 990017,
        address = ParcelAddressResponse(street = "1 Compiler Way", zipCode = "not-a-zip"),
    ).toParcelResult()
    val failure = outcome.exceptionOrNull() as MappingException
    println("hard deep failure -> path='${failure.path}'")
    println("                  -> ${failure.message}")
    //  path='address.zipCode'
    //  Cannot convert address.zipCode: kotlin.String -> kotlin.Int
}
