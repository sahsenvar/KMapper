# Arrow — converters-arrow

[Arrow](https://arrow-kt.io) tipleri için wrapper'lar ve processor desteği: *mapping zamanı
garantisi* olarak boş olamayan koleksiyonlar ve açık-eksiklik tipi olarak `Option`.

## Kurulum

```kotlin
commonMain.dependencies {
    implementation("io.github.sahsenvar:kmapper-converters-arrow:2.0.0")
}
```

```kotlin
@KMapperConfig(wrappers = [NonEmptyListWrapper::class, NonEmptySetWrapper::class])
object AppMapperConfig
```

## NonEmptyList / NonEmptySet

```kotlin
data class Role(val permissions: NonEmptySet<Permission>)

@MapTo(Role::class)
data class RoleResponse(val permissions: List<PermissionResponse>)
```

**Boş wire listesi mapping'i düşürür**: `MappingException.EmptyCollection`. Mesele de bu —
domain tipi boş-olamazlığı vadediyor, mapping de bu vaadi imkânsız değeri içeri almak yerine
sınırda uygular. Hata, diğer her mapping hatası gibi `Result` olarak gelir.

## Option

Add-on classpath'teyken processor, nullable kaynakları `Option` hedeflerine doğrudan eşler —
iç içe eşlenmiş çiftler dahil:

```kotlin
data class Profile(
    val nickname: Option<String>,  // String?  -> Option<String>
    val badge: Option<Badge>,      // BadgeR?  -> Option<Badge> (içinde alt mapper)
)

@MapTo(Profile::class)
data class ProfileResponse(
    val nickname: String?,
    val badge: BadgeResponse?,
)
```

`null` → `None`; mevcut değer → `Some(eşlenmiş)` — beyan edilmiş eksiklik,
[nullable kaçışı](../temel-kullanim/null-safety.md) gibi sessiz kalır.

> Birikimli hata sınırı (`toXAccumulated(): IorNel<…>`) tasarlandı ve bir sonraki sürüm için
> park edildi — bkz. [Sınırlamalar ve Yol Haritası](../referans/sinirlamalar.md).

> Sıradaki: **[Tarih ve Saat →](datetime.md)**
