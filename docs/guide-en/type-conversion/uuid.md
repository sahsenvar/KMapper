# UUID Converters — converters-uuid

The `converters-uuid` module provides **scalar converters** for `kotlin.uuid.Uuid` (KMP common) and
`java.util.UUID` (JVM/Android). Like other scalar add-ons, they must be listed in
`@KMapperConfig(converters = [...])` — they are not auto-discovered.

---

## Setup

```kotlin
// build.gradle.kts (consuming module)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.sahsenvar:kmapper-core:1.0.0")
            implementation("io.github.sahsenvar:kmapper-converters-uuid:1.0.0")
        }
    }
}
```

---

## Platform Support

| Source set | Converters available |
|------------|---------------------|
| `commonMain` | `StringUuidConverter` (`String` ↔ `kotlin.uuid.Uuid`) |
| `jvmAndroidMain` | `JavaStringUuidConverter` (`String` ↔ `java.util.UUID`), `KotlinJavaUuidConverter` (`kotlin.uuid.Uuid` ↔ `java.util.UUID`) |

---

## commonMain Converters

| Object | S | T | Forward | Reverse |
|--------|---|---|---------|---------|
| `StringUuidConverter` | `String` | `kotlin.uuid.Uuid` | `Uuid.parse(value)` | `value.toString()` |

`kotlin.uuid.Uuid` is stable since Kotlin 2.1. This project targets Kotlin 2.3.10 — no `@OptIn` annotation is required.

---

## jvmAndroidMain Converters

| Object | S | T | Forward | Reverse |
|--------|---|---|---------|---------|
| `JavaStringUuidConverter` | `String` | `java.util.UUID` | `UUID.fromString(value)` | `value.toString()` |
| `KotlinJavaUuidConverter` | `kotlin.uuid.Uuid` | `java.util.UUID` | `value.toJavaUuid()` | `value.toKotlinUuid()` |

---

## Usage

List the converters in `@KMapperConfig(converters = [...])`:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.uuid.StringUuidConverter

@KMapperConfig(converters = [StringUuidConverter::class])
object AppMappers
```

Then use `kotlin.uuid.Uuid` in your models:

```kotlin
import kotlin.uuid.Uuid

@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,           // "550e8400-e29b-41d4-a716-446655440000"
    val name: String,
)

data class UserDomain(
    val id: Uuid,
    val name: String,
)
```

Generated mapping:

```kotlin
public fun UserRemote.toUserDomain(): UserDomain = UserDomain(
    id   = StringUuidConverter.convertToNonNull(id),
    name = name,
)
```

On JVM/Android, to bridge between `kotlin.uuid.Uuid` and `java.util.UUID`:

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig
import com.sahsenvar.kmapper.uuid.KotlinJavaUuidConverter

@KMapperConfig(converters = [KotlinJavaUuidConverter::class])
object AppMappers
```

---

## Which Converter Should You Use?

| Your target type | Converter |
|-----------------|-----------|
| `kotlin.uuid.Uuid` (KMP common) | `StringUuidConverter` |
| `java.util.UUID` (JVM/Android only) | `JavaStringUuidConverter` |
| Bridge: `kotlin.uuid.Uuid` ↔ `java.util.UUID` | `KotlinJavaUuidConverter` |

---

Next: [Okio Converters →](okio.md) | Other references: [@KMapperConfig](kmapperconfig.md)
