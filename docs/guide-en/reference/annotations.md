# Annotation Reference

All kmap annotations are in the `com.sahsenvar.kmapper.annotations` package. They all have `SOURCE` retention — they are not included in the published binary; they are consumed only during the KSP compilation step. `@CollectionWrapper` is an exception and uses `BINARY` retention (required for type+annotation resolution from dependency artifacts).

## Summary Table

| Annotation | Target | Parameters | Description |
|---|---|---|---|
| `@MapTo` | Class | `target: KClass<*>` | Generates a `toX()` extension from this class to the target class. `@Repeatable` — can be applied multiple times for multiple targets. |
| `@MapFrom` | Class | `source: KClass<*>` | Placed on the target class; generates a reverse-direction mapping. |
| `@FieldMap` | Property | `fieldName: String`, `targetClass: KClass<*> = Nothing::class` | Maps a field to a different name in the target. The `targetClass` parameter specifies which target it applies to when using `@Repeatable`. |
| `@MapDefaultValue` | Property | `expression: String` | The Kotlin expression to use when the nullable source field is `null`. Added to the generated code as a literal — it must be valid Kotlin. |
| `@Ignore` | Property | — | Excludes this field from mapping. The corresponding field in the target constructor must either be absent or have a default value. |
| `@UseMapTypeConverter` | Property | `converter: KClass<out MapTypeConverter<*, *>>` | Overrides the converter from the global `@KMapperConfig` list for this specific field. Does not need to be added to `@KMapperConfig`. |
| `@KMapperConfig` | Object/Class | `converters: Array<KClass<*>> = []`, `wrappers: Array<KClass<*>> = []` | Defines the global converter and wrapper lists for this module. The processor finds `@KMapperConfig` within the module via `getSymbolsWithAnnotation`. |
| `@CollectionWrapper` | Class (object) | `forType: KClass<*>` | `BINARY` retention. Marks an `object` as a collection wrapper; the object must expose `fun <T> wrap(items: List<T>): WrappedCollection<T>`. The consumer module lists this wrapper explicitly in `@KMapperConfig.wrappers`. |

## `@MapTo`

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class MapTo(val target: KClass<*>)
```

Mapping from the same source to multiple targets:

```kotlin
@MapTo(UserDomain::class)
@MapTo(UserUiModel::class)
data class UserRemote(val id: String, val name: String) : RemoteModel
```

See [@MapTo and @MapFrom](../basic-usage/mapto-mapfrom.md).

## `@MapFrom`

Placed on the target class; useful when you cannot add `@MapTo` to the source class:

```kotlin
@MapFrom(UserRemote::class)
data class UserDomain(val id: String, val name: String) : DomainModel
```

See [@MapTo and @MapFrom](../basic-usage/mapto-mapfrom.md).

## `@FieldMap`

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
annotation class FieldMap(val fieldName: String, val targetClass: KClass<*> = Nothing::class)
```

When the field name differs:

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @FieldMap(fieldName = "id")   // userId → id
    val userId: String,
) : RemoteModel
```

When there are multiple targets, specify the target with `targetClass`:

```kotlin
@FieldMap(fieldName = "id",       targetClass = UserDomain::class)
@FieldMap(fieldName = "userId",   targetClass = UserUiModel::class)
val userId: String,
```

See [Field Mapping](../basic-usage/field-mapping.md).

## `@MapDefaultValue`

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class MapDefaultValue(val expression: String)
```

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    @MapDefaultValue("Clock.System.now()")
    val createdAt: Instant?,
) : RemoteModel
```

The `expression` is placed literally in the generated code. See [Null-Safety](../basic-usage/null-safety.md).

## `@Ignore`

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class Ignore
```

```kotlin
@MapTo(UserDomain::class)
data class UserRemote(
    val id: String,
    @Ignore val internalFlag: Boolean,  // UserDomain'e aktarılmaz
) : RemoteModel
```

See [Field Mapping](../basic-usage/field-mapping.md).

## `@UseMapTypeConverter`

```kotlin
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class UseMapTypeConverter(val converter: KClass<out MapTypeConverter<*, *>>)
```

```kotlin
@MapTo(EventDomain::class)
data class EventRemote(
    val startsAt: String,                                       // global: ISO-8601 converter
    @UseMapTypeConverter(EpochStringToInstantConverter::class)  // per-field override
    val legacyTime: String,
) : RemoteModel
```

See [@KMapperConfig and @UseMapTypeConverter](../type-conversion/kmapperconfig.md).

## `@KMapperConfig`

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class KMapperConfig(
    val converters: Array<KClass<*>> = [],
    val wrappers: Array<KClass<*>> = [],
)
```

```kotlin
@KMapperConfig(
    converters = [IsoStringToInstantConverter::class],
    wrappers   = [PersistentListWrapper::class, NonEmptyListWrapper::class],
)
object AppMapperConfig
```

See [@KMapperConfig and @UseMapTypeConverter](../type-conversion/kmapperconfig.md) and [Multi-Module Projects](../advanced/multi-module.md).

## `@CollectionWrapper`

```kotlin
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class CollectionWrapper(val forType: KClass<*>)
```

`@CollectionWrapper` is placed on an `object` and specifies which collection type (`forType`) it wraps. The object must expose `fun <T> wrap(items: List<T>): WrappedCollection<T>`. Used in `converters-immutable` and `converters-arrow`; you can also use it to define your own wrappers. The consumer module must list the wrapper explicitly in `@KMapperConfig.wrappers`. See [Multi-Module Projects](../advanced/multi-module.md).

---

Next: [Limitations and Roadmap](./limitations.md)
