# 1.x'ten 2.0'a Geçiş

2.0, converter alt sisteminin yeniden tasarımıdır: hatalar değer oldu, fallback ladder
varsayılan davranış oldu, converter/doğrulama/gözlemlenebilirlik yüzeyleri
[kullanıcı–yazar eşitliği](../baslarken/zihinsel-model.md) için yeniden kuruldu. Sürüm
**bilinçli olarak breaking**; bu sayfa eskiden yeniye eksiksiz haritadır.

## 1. Koordinatlar

| 1.x | 2.0 |
|-----|-----|
| `kmapper-core` | `kmapper-core` (artık tek başına da kullanılabilir) |
| — | **`kmapper-annotations`** (yeni artifact — annotation'lar core'dan ayrıldı) |
| `kmapper-processor` | **`kmapper-compiler`** |
| `ksp("…:kmapper-processor:1.0.0")` | `ksp("…:kmapper-compiler:2.0.0")` |

Annotation **import'ları değişmedi** (`com.sahsenvar.kmapper.annotations.*`) — yalnızca yeni
bağımlılığı eklersiniz:

```kotlin
implementation("io.github.sahsenvar:kmapper-core:2.2.2")
implementation("io.github.sahsenvar:kmapper-annotations:2.2.2")  // yeni
ksp("io.github.sahsenvar:kmapper-compiler:2.2.2")                // yeniden adlandı
```

## 2. Üretilen API: `toX()` → `toXResult()`

| 1.x | 2.0 |
|-----|-----|
| `fun Source.toUser(): User` (fırlatır) | `fun Source.toUserResult(): Result<User>` |

Mekanik geçiş — eski fırlatan davranış bir çağrı uzakta:

```kotlin
// 1.x
val user = response.toUser()

// 2.0, aynı semantik:
val user = response.toUserResult().getOrThrow()
```

…ama [Result sınırı](../hata-yonetimi/mapping-exception.md) özelliğin kendisidir: gerçek
çağrı noktalarında `getOrElse`/`fold` tercih edin.

## 3. Annotation'lar

| 1.x | 2.0 | Not |
|-----|-----|-----|
| `@Ignore` | `@IgnoreMap` | aynı fikir, daha net ad |
| `@MapDefaultValue(expression)` | **kaldırıldı** — constructor default'u kullanın | default artık tek yerde yaşıyor; bkz. [alan eşleme](../temel-kullanim/alan-eslestirme.md) |
| `@UseMapTypeConverter(X::class)` | `@ConvertWith(use = X::class)` | artı `onFail` politikası |
| `@ValidateFrom` / `@ValidateTo` | `@Validate` | artık [alana bağlı](../dogrulama/validate.md): tek tanım, iki yön |
| — | `@IgnoreDefaultValue`, `@ConvertTo`/`@ConvertFrom`, `@CollectionWrapper` | yeni yetenekler |

## 4. Custom converter'lar: 4 metotlu şekil

| 1.x | 2.0 |
|-----|-----|
| `convertToNonNull(value: S): T` | `convertTo(source: S): T` |
| `convertFromNonNull(value: T): S` | `convertFrom(target: T): S` |
| `convertTo(value: S?): T?` (final, null geçiren) | `convertToOrNull(source: S): T?` (open — [sanctioned null](../tip-donusumu/ozel-converter.md)) |
| `convertFrom(value: T?): S?` (final) | `convertFromOrNull(target: T): S?` |

Null işleme converter'lardan tamamen çıktı — artık üretilen
[ladder](../temel-kullanim/null-safety.md)'ın işi. Geçişiniz: iki `NonNull` metodu yeniden
adlandırın, null cambazlıklarını silin. Yeni yetenek: gerçeklemeyi reddettiğiniz yönler için
[`@UnsupportedDirection(reason)`](../tip-donusumu/ozel-converter.md).

## 5. Built-in converter adları: zengin tip önce

| 1.x | 2.0 |
|-----|-----|
| `StringIntConverter` | `IntStringConverter` |
| `StringLongConverter` | `LongStringConverter` |
| `StringDoubleConverter` | `DoubleStringConverter` |
| `StringFloatConverter` | `FloatStringConverter` |
| `StringBooleanConverter` | `BooleanStringConverter` |
| `IntLongConverter` | `LongIntConverter` |
| `StringInstantConverter` | `InstantStringConverter` |
| `LongInstantConverter` | `InstantLongConverter` |

Bunlara adla nadiren başvurdunuz (keşif otomatik); başvurduğunuz yerlerde import'ları
düzeltin. Add-on converter adları bu sürümde değişmedi.

## 6. Taşınan ya da davranışı değişen converter'lar

- **kotlinx-datetime `String` converter'ları core'a taşındı.** `@KMapperConfig`'inizdeki
  `StringLocalDateConverter` tarzı kayıtları silin — `LocalDate`, `LocalDateTime`,
  `LocalTime`, `Instant` ve `Duration` çiftleri artık [built-in](../tip-donusumu/builtin.md).
  `kmapper-converters-datetime`'da yalnızca `java.time` converter'ları ve köprüler kaldı.
- **bignumber'ın kayıplı yönleri artık derlemede reddediyor** (`BigDecimal → Double`,
  `BigInteger → Long`/`Int`, `BigDecimal → BigInteger`). 1.x bunları sessizce kırpıyordu.
  Birine dayanıyorduysanız [açık converter'ı yazın](../tip-donusumu/bignumber.md).

## 7. Gözden geçirilecek davranış değişiklikleri (yalnızca ad değil)

- **Fallback ladder artık varsayılan.** 1.x'te bozuk değer genelde mapping'i düşürürdü;
  2.0'da *nullable ya da default'lu* hedef alan onu emer
  ([degradation raporuyla](../gozlemleme/listener.md)). Sertlik *istediğiniz* alanları
  gözden geçirip `@ConvertWith(onFail = OnFail.Throw)` koyun.
- **Koleksiyonlar varsayılan olarak kurtarır.** Tek bozuk eleman artık listeyi düşürmez —
  atılır/null'lanır ve raporlanır. Alan başına hep-ya-hiç için `OnFail.Throw`.
- **Yeni sink kanalı.** `MappingListener`'a `onDegradation(event)` eklendi; emilen hatalar
  telemetrinize ulaşsın diye bir listener kaydedin.

## 8. Geçiş kontrol listesi

1. Koordinatları güncelleyin (§1), derleyin; `toX()` çağrılarını mekanik düzeltin (§2).
2. Annotation'ları bul-değiştir yapın (§3); `@MapDefaultValue` ifadelerini constructor
   default'larına taşıyın.
3. Custom converter metotlarını yeniden adlandırın (§4); yeni built-in'lerin kapsadığı
   kayıtları silin (§6).
4. Bir degradation listener'ı ekleyin (§7) — basit bir logger bile olur.
5. Sert kalması gereken alanları gözden geçirin; kısmi verinin kabul edilemez olduğu yerlere
   `OnFail.Throw` koyun.
6. Build alın: 2.0, kalan boşlukları **adlı derleme hatalarına** çevirir — onlar yapılacaklar
   listenizdir.
