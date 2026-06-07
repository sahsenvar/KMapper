# KMapper — Mapping Library Extraction & Hardening (Round 1)

- **Date:** 2026-06-04
- **Status:** Design — awaiting user review
- **Author:** Şahan Şenvar (with Claude)
- **Round scope:** Standalone extraction + correctness fixes **+ enum mapping** (`MappableEnum<W>`, §5.4 — kullanıcının önceliklendirdiği konu). Sealed class, `Map<K,V>`, arrow converters fill-in, and build-time instrumentation are **deferred to later rounds**.

---

## 1. Background & Goal

DomatApp bugün KSP tabanlı bir compile-time object-mapping sistemi içeriyor:

- `core:mapping` (KMP runtime): anotasyonlar + `MapTypeConverter` + `TypeConverterRegistry` + built-in converter'lar + `startKMapper {}` DSL. Tek dış bağı `core:resulting` (`MappingError`).
- `core:processor` (JVM-only KSP): `@MapTo`/`@MapFrom`'dan `{Source}Mappers.kt` extension'larını üreten `MappingProcessor` + `FieldAnalyzer` → `TypeMatcher` → `MappingCodeGenerator` → `BuiltInConverterValidator` hattı.

**Amaç:** Bu sistemi DomatApp'tan tamamen bağımsız, **yayınlanabilir bir kütüphaneye** çıkarmak ve bu sırada keşfedilen doğruluk eksiklerini düzeltmek.

- **Repo adı:** `KMapper` (KMP + mapper), GitHub'da kullanıcının hesabında.
- **groupId:** `com.sahsenvar.kmapper`
- **Dağıtım:** Maven Central (başka projelerde de tüketilebilsin diye).

**Bu round'un net çıktısı:** Ayrı bir Gradle projesi olarak `KMapper` (bağımsız build + publish yapılandırması) + DomatApp'ın bu artifact'ı tüketecek şekilde güncellenmesi ve eski `core:mapping` + mapping processor'ının kaldırılması.

---

## 2. Identity & Distribution

| Özellik | Değer |
|---|---|
| Repo | `KMapper` (ayrı GitHub repo) |
| groupId | `com.sahsenvar.kmapper` |
| Hedef platformlar (runtime) | KMP: `androidTarget`, `iosArm64`, `iosSimulatorArm64`, (mümkünse `jvm`) |
| Processor platformu | JVM-only (KSP) |
| Publish | Maven Central (`maven-publish` + signing) |

> **Spec review notu:** `com.sahsenvar.kmapper` paket adını onayla / değiştir.

---

## 3. Module / Artifact Architecture

```
KMapper/ (com.sahsenvar.kmapper)
├── core                  (KMP)  com.sahsenvar.kmapper:core
│     • anotasyonlar: @MapTo, @MapFrom, @FieldMap, @MapDefaultValue,
│       @UseMapTypeConverter, @Ignore, @KMapperConfig
│     • MapTypeConverter, TypeConverterRegistry (expect/actual)
│     • MappingException (sealed)
│     • built-in primitive converters (str↔int/long/double/float/bool, int↔long, …)
│     • KMapper (listener registry) + MappingListener + LoggingMappingListener
├── processor             (JVM)  com.sahsenvar.kmapper:processor
│     • MappingProcessor + analyzer/generator/validator
│     • compile-testing test seti burada
├── converters-compose    (KMP)  com.sahsenvar.kmapper:converters-compose
│     • List/Set → PersistentList/ImmutableList/ImmutableSet wrapper'ları
│     • kotlinx.collections.immutable bağımlılığı YALNIZCA burada
└── converters-arrow      (KMP)  com.sahsenvar.kmapper:converters-arrow
      • bu round'da BOŞ slot (yapı kurulur; Nel converter'ları sonraki tur)
```

**Kararlar:**
- Anotasyonlar + runtime tek `core` artifact'ında (MapStruct deseni). İleride `annotations`'ı ayırmak mekanik; şimdilik YAGNI.
- Primitive converter'lar `core`'da built-in; processor bunları zaten tip-çifti tablosundan bilir (keşif gerekmez, sadece FQN'ler `com.sahsenvar.kmapper.core...`'a güncellenir).
- `kotlinx.collections.immutable` core'dan **çıkar** → `converters-compose`'a taşınır. (Bkz. §5 collection-wrapper seam — "ImmutableList hardcoding" eksisinin çözümü.)

**Tüketici tarafı (örnek):**
```kotlin
// build.gradle.kts
commonMainImplementation("com.sahsenvar.kmapper:core:<v>")
add("kspCommonMainMetadata", "com.sahsenvar.kmapper:processor:<v>")
commonMainImplementation("com.sahsenvar.kmapper:converters-compose:<v>") // opsiyonel
```

---

## 4. Error Model

DomatApp'a coupling'i kesmek için generated kodun gömdüğü `core:resulting`'deki `MappingError` yerine kütüphanenin **kendi** hiyerarşisi:

```kotlin
// com.sahsenvar.kmapper.core
sealed class MappingException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause) {

    class RequiredFieldMissing(val field: String)
        : MappingException("Required field missing: $field")

    class TypeConversionFailed(val from: String, val to: String, cause: Throwable)
        : MappingException("Cannot convert $from -> $to", cause)

    class UnknownEnumValue(val enum: String, val value: Any)
        : MappingException("Unknown wire value '$value' for enum $enum")
}
```

**Kararlar:**
- `RequiredFieldMissing` — generated null-check'lerin fırlattığı tip (mevcut davranış korunur).
- `TypeConversionFailed` — **YENİ doğruluk düzeltmesi:** converter çağrısı bir exception fırlatırsa (örn. `"abc".toInt()` → `NumberFormatException`) generated kod onu `TypeConversionFailed`'e sarar. Bugün ham platform exception sızıyor; bu kapatılıyor.
- **`CircularReference` runtime tipi DÜŞÜRÜLÜR** — döngü artık derleme zamanında yakalanır (§6), runtime exception değil **compile error** olur.
- **`EmptyCollection` DÜŞÜRÜLÜR** — onu tetikleyecek bir anotasyon yok; spekülatif ölü tip. (İleride `converters-arrow`'da `List→NonEmptyList` için geri gelebilir.)

**DomatApp entegrasyonu (mevcut pattern'e oturur):**
```kotlin
// feature/auth/data/.../mapper/AuthMapper.kt — zaten var olan exception→domain çevirisi
fun Throwable.toAuthError(): AuthError = when (this) {
    is MappingException.RequiredFieldMissing -> AuthError.Unknown(message, this)
    is MappingException.TypeConversionFailed -> AuthError.Unknown(message, this)
    /* ... mevcut RemoteError dalları ... */
}
```

---

## 5. Converter System

### 5.1 Kayıt & keşif: `@KMapperConfig` + per-field override

**Sorun:** Processor derleme zamanında çalışır; runtime `KMapper.addConverter()` çağrısını göremez → saf runtime kayıt, compile-time generation/safety'yi imkânsız kılar. Processor'ın görebileceği tek "tek-yer liste", bir **annotation argümanı**dır (KSP, `KClass` referanslarını — bağımlılık artifact'larındakiler dahil — derleme zamanında çözer).

```kotlin
// Tüketici modülünde tek yer (Application'ın yanında):
@KMapperConfig(converters = [
    IsoStringToInstantConverter::class,
    PersistentListConverter::class,        // converters-compose'dan
])
object AppMapperConfig
```

- Processor `@KMapperConfig`'i `getSymbolsWithAnnotation` ile bulur (in-module), her converter'ın `MapTypeConverter<S,T>` supertype'ından `(S,T)` çiftini çözer.
- Bir alan için gereken converter ne global listede ne de override'da bulunursa → **compile error**: `no converter for String -> Instant; add it to @KMapperConfig or annotate the field`.
- **Runtime kaydı da aynı listeden ÜRETİLİR** → kullanıcı `addConverter`'ı elle çağırmaz. `startKMapper {}` ve regex kaynak-tarama (`MappingProcessor.kt:526`) **tamamen kaldırılır**.
- `KMapper.addConverter(...)` yine de **runtime escape-hatch** olarak public kalır (compile-time safety vermez; dokümante edilir).

**Per-field override (`@UseMapTypeConverter`)** — aynı `(S,T)` çifti farklı dönüşüm gerektirdiğinde global'i ezer:
```kotlin
@MapTo(EventDomain::class)
data class EventRemote(
    val startsAt: String,                                        // global'den: ISO
    @UseMapTypeConverter(EpochStringToInstantConverter::class)   // istisna: epoch
    val legacyTime: String,
) : RemoteModel

// üretilen:
fun EventRemote.toEventDomain() = EventDomain(
    startsAt   = IsoStringToInstantConverter.convertToNonNull(startsAt),
    legacyTime = EpochStringToInstantConverter.convertToNonNull(legacyTime),
)
```
Öncelik: **per-field override > global liste > built-in primitive**.

### 5.2 Runtime registration aggregation (modüller arası)

- **Compile-time (her modül):** O modüldeki `@KMapperConfig` o modülün mapper'ları için yeterli (DomatApp'ta mapping'ler `feature:auth:data`'da; `@KMapperConfig` de oraya gelir — bugünkü `MapperConfiguration.kt`'nin yerine).
- **Runtime (uygulama bir kez):** Her modül KSP run'ı, kayıtları bilinen bir pakete (`com.sahsenvar.kmapper.generated`) bir descriptor olarak üretir. Tüm feature'lara bağımlı olan app/composeApp modülünün processor run'ı `resolver.getDeclarationsFromPackage("com.sahsenvar.kmapper.generated")` ile hepsini toplayıp tek bir `KMapper.initGeneratedConverters()` üretir; kullanıcı bunu `Application`'da bir kez çağırır.

### 5.3 Collection-wrapper seam (immutable handling'i core'dan çıkarma)

Bugün `MappingCodeGenerator` hedef tip `kotlinx.collections.immutable.ImmutableList` ise `.toImmutableList()` gömüyor (hardcoded + core'da kotlinx.collections.immutable bağımlılığı). Bu **çıkarılır**.

- `core` yalnızca stdlib `List`/`Set` element-mapping'ini bilir: `source.map { it.toX() }`.
- Collection-tipi sarma `converters-compose`'a taşınır. **Karar: `@CollectionWrapper` anotasyonu** (otomatik keşif; kullanıcı listeye eklemez).

```kotlin
// converters-compose:
@CollectionWrapper(forType = PersistentList::class)
fun <T> List<T>.asPersistentList(): PersistentList<T> = toPersistentList()

@CollectionWrapper(forType = ImmutableList::class)
fun <T> List<T>.asImmutableList(): ImmutableList<T> = toImmutableList()
// (ImmutableSet vb. aynı şekilde)
```
Processor hedef alan `PersistentList<TagDomain>` görünce kayıtlı wrapper'ı bulur ve `source.map { it.toTagDomain() }.asPersistentList()` üretir.

**Cross-module keşif (kritik):** KSP `getSymbolsWithAnnotation` bağımlılık artifact'ındaki (`converters-compose`) anotasyonları GÖREMEZ. Bu yüzden B, §5.2'deki descriptor mekanizmasına dayanır: `converters-compose` build edilirken kendi KSP run'ı `@CollectionWrapper` fonksiyonlarını görür (in-module) ve `com.sahsenvar.kmapper.generated` paketine birer descriptor üretir; tüketici modülün processor'ı bunları `getDeclarationsFromPackage` ile bulur. Converter runtime-registration ile **aynı altyapı**.

> **Çakışma guard'ı:** Aynı `forType` için classpath'te birden fazla `@CollectionWrapper` bulunursa processor **compile error** verir (hangi wrapper'ın aktif olduğu sessiz kalmasın — B'nin "sihirli classpath" riskini kapatır).

### 5.4 Enum mapping (`MappableEnum<W>`)

Enum'lar `ordinal`/`name` ile **sessizce yanlış** eşlenebilir (reorder/rename → runtime bug, ne compile ne runtime hatası). Çözüm: enum'lar kütüphanenin generic interface'ini implement eder; processor enum gördüğünde bunu kullanır. **`ordinal` asla kullanılmaz.**

```kotlin
// core:
interface MappableEnum<W : Any> { val wireValue: W }

enum class OrderStatus(override val wireValue: String) : MappableEnum<String> {
    PENDING("PENDING"), SHIPPED("in_transit"), DELIVERED("DELIVERED"),
}
enum class Priority(override val wireValue: Int) : MappableEnum<Int> {
    LOW(10), MEDIUM(20), HIGH(30),
}
```
```kotlin
// üretilen (processor MappableEnum<W> görür; W, wire-tarafı alan tipiyle eşleşmeli):
fun String.toOrderStatus(): OrderStatus =
    OrderStatus.entries.firstOrNull { it.wireValue == this }
        ?: throw MappingException.UnknownEnumValue("OrderStatus", this)
fun OrderStatus.toWire(): String = wireValue   // reverse her zaman total
```

**Kurallar:**
- **Katılık (no silent default):** Enum `MappableEnum` implement etmiyor VE `@UseMapTypeConverter` yoksa → **compile error** (`enum 'Foo' must implement MappableEnum<...> or use @UseMapTypeConverter`).
- **Totality bedava:** constructor `wireValue`'yu zorunlu kıldığı için eşlemesiz sabit imkânsız (exhaustive `when`'den güçlü; processor değer okumak zorunda değil — KSP zaten constructor arg'ının runtime değerini güvenilir okuyamaz).
- **W tip kontrolü:** wire alan tipi (`String`/`Int`) enum'ın `MappableEnum<W>`'siyle uyuşmazsa → compile error.
- **Reorder-safe** (değer sabite bağlı); nullable enum alanı → null geçişli üretim.

**Edge-case'ler:**
- **Aynı `wireValue` iki sabitte** → forward `firstOrNull` ilkini seçer (sessiz bug). KSP değerleri okuyamadığı için derlemede yakalanamaz → opsiyonel `KMapper.verifyEnums()` (debug init) benzersizliği kontrol edip gürültülü hata verir.
- **3rd-party / değiştirilemeyen enum** → interface eklenemez → `@UseMapTypeConverter` ile elle enum converter (escape hatch).

---

## 6. Cycle Detection (compile-time, smart)

Processor `@MapTo`/`@MapFrom` tip grafini kurar ve **yalnızca koşulsuz döngüde** compile error verir.

- **Koşulsuz döngü** = halkadaki tüm kenarlar non-null + non-collection alanlardan geçer → veri inşa edilemez, garantili sonsuz → **compile error**.
- **Koşullu recursion** = halka en az bir nullable veya collection alandan geçer (ağaç, opsiyonel geri-referans) → geçerli, **izin verilir**.

```kotlin
// ERROR — koşulsuz:
@MapTo(BDomain::class) data class A(val b: B)   // non-null
@MapTo(ADomain::class) data class B(val a: A)   // non-null
//  e: Mapping cycle A -> B -> A (guaranteed infinite).
//     Break it with @Ignore or @UseMapTypeConverter.

// OK — koşullu:
@MapTo(NodeDomain::class) data class Node(val children: List<Node>)   // collection
@MapTo(CatDomain::class)  data class Category(val parent: Category?)   // nullable
```

> Not: DomatApp'ın mevcut modelleri düz; bu, publishable lib için yabancı kullanıcıların hatalı modellerine karşı bir güvenlik ağı. Runtime maliyeti yok, generated imza değişmez.

---

## 7. Observability — `MappingListener`

Runtime gözlemleme; generated kod, dinleyici yoksa ~sıfır maliyetli guard arkasında kaba olaylar yayar.

```kotlin
// com.sahsenvar.kmapper.core
interface MappingListener {
    fun onMapStart(source: Any, target: KClass<*>) {}
    fun onMapComplete(source: Any, result: Any) {}
    fun onError(source: Any, error: MappingException) {}
    // ileri-uyumlu (default no-op): onFieldDefaulted, onConversion — sonraki turlarda yayılır
}

object KMapper {
    val hasListeners: Boolean
    fun addListener(l: MappingListener)
    fun removeListener(l: MappingListener)
    internal inline fun dispatch(block: MappingListener.() -> Unit)
}

class LoggingMappingListener(private val log: (String) -> Unit) : MappingListener { /* ... */ }
```

```kotlin
// generated (guard'lı):
fun UserRemote.toUserDomain(): UserDomain {
    if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toUserDomain, UserDomain::class) }
    val result = UserDomain(id = id ?: throw MappingException.RequiredFieldMissing("id"))
    if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toUserDomain, result) }
    return result
}
```

- Olaylar **kaba** (per-mapper) — generated kod yalın kalır. Per-field olaylar interface'te tanımlı ama bu round'da yayılmaz (ileri-uyumlu).
- **Bonus:** `kmapper.verbose` KSP option'ı — processor'ın hangi mapper'ları/eşleşmeleri ürettiğini build-time `logger.info`'a yazar (ucuz, isteğe bağlı).

---

## 8. Reverse Mapping (`@MapFrom`)

`@MapFrom` processor'da **zaten implement** (`processMapFromAnnotation`, `isReverse` tüm hatta dağılmış). Çıkarımda korunur. Bugüne dek hiç kullanılmadığı için **test edilmemiş** → §9 compile-testing bunu kapsar.

---

## 9. Testing Strategy — processor compile-testing (JVM)

Tüm ilginç mantık + yeni düzeltmeler JVM-only processor'da; `kotlin-compile-testing` ile kaynak verip üretilen kodu ve diagnostic'leri doğrularız. Runtime converter'lar trivial → bu round'da runtime unit testleri kapsam dışı.

Kapsanacak vakalar (en az):
- `RequiredFieldMissing`: nullable→non-null, default'lu/default'suz.
- **Converter failure → `TypeConversionFailed`** sarma.
- **Koşulsuz döngü → COMPILATION_ERROR** + mesaj; koşullu recursion → OK.
- **Eksik converter → COMPILATION_ERROR** (global listede de field'da da yok).
- Per-field `@UseMapTypeConverter` global'i ezer.
- `@KMapperConfig` listesinden tipli converter çağrısı üretimi.
- Nested mapping (`user.toX()`), collection element mapping.
- Reverse mapping (`@MapFrom`).
- `@FieldMap` rename, `@Ignore`, `@MapDefaultValue`.

> Proje geneli test politikası "kapalı"; bu yalnızca `KMapper` reposuna özeldir (DomatApp'a test eklenmez).

---

## 10. Eksiler → bu round'daki karşılığı

| # | Eksi | Bu round |
|---|---|---|
| 1 | Ölü hata tipleri (CircularReference/EmptyCollection/TypeConversion) | TypeConversion → **wired** (TypeConversionFailed); Circular → compile error; Empty → **silindi** |
| 2 | Döngü tespiti yok | **Compile-time smart detection** (§6) |
| 3 | Converter hataları ham sızıyor | **`TypeConversionFailed`'e sarılır** (§4) |
| 4 | `@KMapperConfiguration` regex parse | **Kaldırıldı** → `@KMapperConfig` KClass listesi (§5.1) |
| 5 | Enum yok (ordinal/name sessiz bug) | **Bu round** — `MappableEnum<W>` interface, no silent default (§5.4) |
| 5b | Sealed class yok | **Ertelendi** (sonraki round) |
| 6 | `Map<K,V>` yok | **Ertelendi** (sonraki round) |
| 7 | Constructor-only analiz | Bu round'da değişmiyor (kabul edilen sınır) |
| 8 | Kullanım ~sıfır → test yok | **Compile-testing** ile gelişmiş yüzey kapsanır (§9) |
| — | ImmutableList hardcoding | **`converters-compose`'a taşındı** (§5.3) |
| — | Hardcoded `com.domatapp` FQN'leri | **Decoupling checklist** (§11) |

---

## 11. Decoupling checklist (extraction blockers)

- [ ] Generated kodun `MappingError` FQN'i → `com.sahsenvar.kmapper.core.MappingException` (§4).
- [ ] Anotasyon FQN string'leri (`MappingProcessor`, `FieldAnalyzer`) → `com.sahsenvar.kmapper.core.annotations.*`.
- [ ] Built-in converter FQN'leri (`TypeMatcher`) → `com.sahsenvar.kmapper.core.converter.builtin.*`.
- [ ] `MapTypeConverter` base FQN (`BuiltInConverterValidator`) → yeni paket.
- [ ] `kotlinx.collections.immutable` core'dan çıkar → `converters-compose` (§5.3).
- **Marker interface'ler (`RemoteModel`/`DomainModel`/…) gerektirmez** — processor onları kontrol etmiyor; kütüphane bunlardan tamamen bağımsız (DomatApp kendi marker'larını kullanmaya devam edebilir, lib umursamaz).

---

## 12. Out of scope (sonraki round'lar)

- Sealed class mapping; `Map<K,V>` collection mapping. (Enum bu round'a alındı — §5.4.)
- `converters-arrow` gerçek dolumu (`List→NonEmptyList`, burada `EmptyCollection` geri gelebilir).
- Runtime depth-guard (döngüsel **veri**); build-time instrumentation toggle; per-field listener olayları.
- Ayrı `annotations` artifact'ı.
- Maven Central publish pipeline / CI otomasyonu detayları (yapı kurulur, tam otomasyon ayrı iş).
- Constructor-dışı property mapping.

---

## 13. Migration (DomatApp tarafı, implementation sonrası)

1. `KMapper` artifact'ları publish (veya local Maven / `includeBuild` ile geçici tüketim).
2. DomatApp: `core:mapping` + `core:processor`'ın mapping kısmı **silinir** (navigation/remote/config processor'ları kalır).
3. `feature:auth:data`: `MapperConfiguration.kt` (`startKMapper`) → `@KMapperConfig` object'ine dönüştürülür; `AuthMapper.toAuthError` `MappingException` dallarını ekler.
4. Bağımlılıklar `com.sahsenvar.kmapper:*`'a çevrilir; build doğrulanır (`AuthSession`/`AuthUser` mapper'ları aynen üretilmeli).
