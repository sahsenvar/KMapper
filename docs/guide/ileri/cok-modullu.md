# Çok Modüllü Projeler

Pratik kural: **compiler, mapping'in tanımlandığı yere; runtime, üretilen kodun çağrıldığı
yere gider.**

## Kime ne lazım?

| Modül | İhtiyacı |
|-------|----------|
| `@MapTo`/`@MapFrom` modelleri tanımlıyor | `kmapper-annotations` + `ksp(kmapper-compiler)` |
| yalnızca `toXResult()` *çağırıyor* | `kmapper-core` (çoğunlukla transitif gelir) |
| yalnızca seam'leri/validator'ları elle kullanıyor | `kmapper-core` |

Tipik katmanlı bir uygulama:

```
:data       -> wire modelleri + @MapTo + kendi @KMapperConfig'i; KSP çalışır
:domain     -> düz modeller; yalnızca kmapper-core (transitif)
:app        -> üretilen mapper'ları çağırır; KSP yok
```

## Modüller arası model çiftleri çalışır

`:data`'daki `@MapTo(DomainUser::class)`, `:domain`'deki bir sınıfı gösterebilir — KSP
classpath sembollerini, constructor default'ları dahil çözümler
([ladder](../temel-kullanim/null-safety.md) modül sınırlarının ötesinde çalışır).

## Tanımlayan modül başına bir @KMapperConfig

[Kayıt](../tip-donusumu/kmapperconfig.md) derleme modülü başınadır. Modüller birbirinin
config'ini miras almaz; mapping tanımlayan her modüle kendi config'ini verin (converter
*object'lerini* aralarında paylaşmak serbest — onlar sıradan sınıflar):

```kotlin
// :data/src/…/DataMapperConfig.kt
@KMapperConfig(converters = [MoneyStringConverter::class])
object DataMapperConfig
```

## KMP bağlama hatırlatması

Multiplatform bir modülde işlemci **hedef başına** kaydedilir (`kspCommonMainMetadata`,
`kspJvm`, `kspIosArm64`, …) — tam snippet için [Kurulum](../baslarken/kurulum.md).

> Sıradaki: **[Mimari →](mimari.md)**
