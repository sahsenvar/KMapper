package sample.nested

import com.sahsenvar.kmapper.annotations.MapTo

/**
 * NESTED 1 — object graphs map themselves.
 *
 * A nested data-class field is just another conversion: if `AddressResponse` is `@MapTo`
 * `Address`, then any field of type `AddressResponse -> Address` automatically calls the
 * sub-mapper. No annotations on the field, arbitrary depth, same Result discipline.
 */
data class Address(
    val street: String,
    val zipCode: Int,
)

data class Customer(
    val name: String,
    val address: Address,
)

data class Shipment(
    val trackingId: Long,
    val customer: Customer,
)

@MapTo(Address::class)
data class AddressResponse(
    val street: String,
    val zipCode: String,
)

@MapTo(Customer::class)
data class CustomerResponse(
    val name: String,
    val address: AddressResponse,
)

@MapTo(Shipment::class)
data class ShipmentResponse(
    val trackingId: String,
    val customer: CustomerResponse,
)

fun main() = runNestedObjectsDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runNestedObjectsDemo() {
    val shipment = ShipmentResponse(
        trackingId = "990017",
        customer = CustomerResponse(
            name = "Grace Hopper",
            address = AddressResponse(street = "1 Compiler Way", zipCode = "34000"),
        ),
    ).toShipmentResult().getOrThrow()
    println("three levels deep -> $shipment")
}
