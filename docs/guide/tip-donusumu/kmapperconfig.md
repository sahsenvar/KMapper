# @KMapperConfig — Kayıt ve Keşif

`@KMapperConfig`, modülünüzün tek kayıt noktasıdır: converter object'lerini ve collection
wrapper'larını bir kez listeleyin; modüldeki her mapping onları kullanabilsin — **alan bazlı
annotation gerekmeden**.

```kotlin
import com.sahsenvar.kmapper.annotations.KMapperConfig

@KMapperConfig(
    converters = [MoneyStringConverter::class],
    wrappers = [PersistentListWrapper::class, NonEmptyListWrapper::class],
)
object AppMapperConfig
```

Taşıyıcı (`AppMapperConfig`) yalnızca annotation'ın çapasıdır — herhangi bir object olur;
kalıp, modül başına bir tane.

## Keşif tip çiftiyledir

`MoneyStringConverter`'ı *hangi alanın* kullanacağını asla söylemezsiniz. Processor,
`Money → String` (iki yönden biri) alan çiftini görür, `(S, T)` tipleri uyuşan kayıtlı
converter'ı bulur ve bağlar. Tanım sırası önemsizdir.

```kotlin
@MapTo(Invoice::class)
data class InvoiceResponse(
    val total: String, // String -> Money: config üzerinden otomatik çözümlenir
)

data class Invoice(val total: Money)
```

## Çözümleme ve gölgeleme

Her alan çifti için ilk eşleşme kazanır:

1. **alandaki [`@ConvertWith`](convert-with.md)** — açık override
2. **`@KMapperConfig` converter'larınız** — *sizin* `Instant ↔ String` converter'ınız aynı
   çift için built-in'i bütün modülde gölgeler (ör. wire formatını epoch string'e çevirmek
   için)
3. **[core built-in'leri](builtin.md)**
4. **derleme hatası** — çifti söyleyen `MissingConverter`

**Aynı çifti** iddia eden iki kayıtlı converter yazı-tura değil, derleme hatasıdır: registry
belirsizliğe düşemez. Aynı çiftin format *varyantları* (UTF-8 ve Base64 `String ↔ ByteString`)
alan bazlı `@ConvertWith`'in işidir.

## Wrapper'lar

`wrappers = [...]`, koleksiyon eşlemeye yeni kap tipleri öğreten
[`@CollectionWrapper`](ozel-converter.md) object'lerini kaydeder:

```kotlin
data class UserD(
    val tags: PersistentList<Tag>,   // List<TagR> -> PersistentList<Tag>: wrapper + eleman eşleme
    val roles: NonEmptyList<String>, // boş wire listesi -> MappingException.EmptyCollection
)
```

Add-on'lar hazır wrapper'larla gelir — [immutable](immutable.md), [arrow](arrow.md) — ve
kendi kap tipleriniz birebir aynı mekanizmayla kaydolur.

## Kapsam

Bir `@KMapperConfig`, içinde bulunduğu **derleme modülünü** kapsar. Çok modüllü bir build'de
mapping tanımlayan her modülün kendi config'i olur (genelde minik bir object) — bkz.
[Çok Modüllü Projeler](../ileri/cok-modullu.md).

> Sıradaki: **[@ConvertWith ve OnFail →](convert-with.md)**
