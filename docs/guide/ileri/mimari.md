# Mimari — Modüller ve KSP Pipeline

Bu sayfa kmap'in iç tasarımını kavramsal düzeyde açıklar. Kütüphaneyi kullanmak için buradaki detayları bilmeniz gerekmez; ancak nasıl çalıştığını anlamak sorun gidermeyi ve katkı sağlamayı kolaylaştırır.

## Modül Bölünmesi

kmap dört ayrı artifact'tan oluşur:

```
com.sahsenvar.kmapper
├── core              (KMP)
│   ├── Anotasyonlar: @MapTo, @MapFrom, @FieldMap, @MapDefaultValue,
│   │                 @UseMapTypeConverter, @Ignore, @KMapperConfig, @CollectionWrapper
│   ├── MappableEnum<W>, MappingException (sealed)
│   ├── MapTypeConverter (abstract), TypeConverterRegistry (expect/actual)
│   ├── Built-in primitive converter'lar (str↔int/long/double/float/bool, int↔long, …)
│   └── KMapper, MappingListener, LoggingMappingListener
│
├── processor         (JVM-only, KSP)
│   └── MappingProcessor + FieldAnalyzer → TypeMatcher → MappingCodeGenerator pipeline'ı
│
├── converters-immutable (KMP, opsiyonel)
│   └── List/Set → PersistentList/ImmutableList/ImmutableSet wrapper'ları
│       (kotlinx.collections.immutable bağımlılığı yalnızca burada)
│
└── converters-arrow  (KMP, opsiyonel, bu sürümde boş slot)
    └── Nel converter'ları için ayrılmış (sonraki tur)
```

**Tasarım kararları:**

- Anotasyonlar ve runtime tek `core` artifact'ında bir arada tutulur (MapStruct yaklaşımı). İleride ayrıştırmak mekanik bir işlemdir; şimdilik YAGNI.
- `kotlinx.collections.immutable` `core`'dan çıkarılmıştır — yalnızca `converters-immutable`'dadır. `core`'u kullanan projeler bu bağımlılığı almak zorunda kalmaz.
- `processor` JVM-only'dir: KSP sadece JVM'de çalışır. Üretilen kod KMP'dir.

## KSP Pipeline — Derleme Zamanı

kmap hiçbir zaman çalışma zamanı reflection kullanmaz. Tüm mapping kodu derleme sırasında üretilir:

```
@MapTo ile anotasyonlu kaynak sınıf
        ↓
  FieldAnalyzer
  • Constructor val alanlarını inceler
  • Her alan için strateji belirler:
    direct / type-conversion / nested / collection / wrapped-collection / enum
        ↓
  TypeMatcher
  • @KMapperConfig converter listesini çözer
  • Built-in primitive dönüşümleri kontrol eder
  • Eksik converter → compile error
        ↓
  Validator
  • Döngü tespiti (koşulsuz döngü → compile error)
  • Enum MappableEnum kontrolü
  • W tip uyumu kontrolü
        ↓
  MappingCodeGenerator (KotlinPoet)
  • {Source}Mappers.kt extension dosyasını üretir
  • Null-check'leri yazar (RequiredFieldMissing)
  • Converter çağrılarını TypeConversionFailed ile sarar
  • Listener guard bloklarını ekler
        ↓
  build/generated/ksp/…/{Source}Mappers.kt
```

Processor, analiz ettiği her `@MapTo`/`@MapFrom` anotasyonlu sınıf için bir `.kt` dosyası üretir. Dosyalar normal Kotlin kaynak kodu gibi derlenir; reflection yoktur.

## Neden Reflection Yok?

KSP yaklaşımının temel avantajı tam derleme zamanı güvencesidir:

- Eksik converter → derleme hatası, runtime surprise değil.
- Tip uyumsuzluğu → derleme hatası.
- Döngüsel bağımlılık → derleme hatası.
- iOS/Native dahil tüm KMP hedeflerinde çalışır (reflection kısıtlaması yoktur).

## Platform Uyumluluğu

`core` KMP olduğundan üretilen extension fonksiyonlar Android, iOS (Kotlin/Native) ve JVM hedeflerinde derlenir. `processor` JVM-only'dir ama yalnızca build araçları tarafından çalıştırılır; dağıtılan koda dahil değildir.

`TypeConverterRegistry` `expect/actual` mekanizmasıyla platform başına ayrı implementasyon alır; dışarıya açık API aynıdır.

---

Sonraki adım: [Anotasyon Referansı](../referans/anotasyonlar.md)
