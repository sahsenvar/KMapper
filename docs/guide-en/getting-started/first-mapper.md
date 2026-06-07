# Your First Mapper

In this page you will write your first mapper, converting a REST response (`RemoteModel`) into a domain model.

## 1. Define the Models

The target (domain) model is a plain Kotlin class:

```kotlin
data class UserDomain(
    val id: String,
    val email: String,
)
```

Add `@MapTo` to the source (remote) model to say "I want to be able to map this to `UserDomain`":

```kotlin
import com.sahsenvar.kmapper.annotations.MapTo

@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    val email: String,
)
```

> KMapper does **not** require your models to implement a specific interface (`RemoteModel`, `DomainModel`, etc.). Those interfaces are a convention in your own architecture; use them if you like.

## 2. Build

Build the project. KMapper generates `UserRemoteMappers.kt` in the same package as the source class:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id = id,
    email = email,
)
```

> **Note:** Examples show a simplified body. The actual generated code also includes `KMapper.hasListeners`-guarded observability hooks and uses a `val result = …; return result` form — see [MappingListener](../observability/listener.md).

## 3. Use It

```kotlin
val remote = UserRemote(id = "42", email = "a@b.com")
val domain: UserDomain = remote.toUserDomain()
```

That is all there is to it. Fields with matching names are copied automatically.

## Nested Models Work Automatically

If a field is itself a type annotated with `@MapTo`, KMapper chains the inner mapping call automatically:

```kotlin
data class AddressDomain(val city: String)
data class CustomerDomain(val name: String, val address: AddressDomain)

@MapTo(AddressDomain::class)
data class AddressRemote(val city: String)

@MapTo(CustomerDomain::class)
data class CustomerRemote(val name: String, val address: AddressRemote)
```

The generated code chains the inner mapping:

```kotlin
public fun CustomerRemote.toCustomerDomain(): CustomerDomain = CustomerDomain(
    name = name,
    address = address.toAddressDomain(),
)
```

## Null-Safety Is Active from the Start

Say the remote field is nullable but the domain field is required:

```kotlin
data class UserDomain(val id: String)          // required

@MapTo(UserDomain::class)
data class UserRemote(val id: String?)          // nullable
```

KMapper never silently swallows `null` — it generates a loud exception:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id = id ?: throw MappingException.RequiredFieldMissing("id"),
)
```

If you want to supply a default value instead, use `@MapDefaultValue` (see [Null-Safety](../basic-usage/null-safety.md)).

## What's Next?

- If field names differ: **[Field Mapping (@FieldMap)](../basic-usage/field-mapping.md)**
- If you need type conversion (e.g. `String` → `Int`): **[Type Conversion](../type-conversion/built-in.md)**
- For enum mapping: **[MappableEnum](../enum/mappable-enum.md)**
- For the reverse direction (`DomainModel → RemoteModel`): **[@MapTo and @MapFrom](../basic-usage/mapto-mapfrom.md)**
