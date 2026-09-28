# Mimari: Nasıl Çalışır

Kaputun altına bir bakış — build'leri debug etmek, üretilen kodu incelemek ve production'da
ne koştuğuna güvenmek için.

## Boru hattı

```
annotation'lı modelleriniz
   └─ KSP2 (kmapper-compiler)
        ├─ analiz: alanları eşle, converter/wrapper çözümle, direktifleri denetle
        ├─ ret:    MissingConverter / UnsupportedConversion / yapısal hatalar -> build düşer
        └─ üretim: toX() extension fonksiyonu (düz Kotlin, KotlinPoet), artı çözümlenen
              sarmalayıcının her wrap() overload'u için bir extension daha (toXResult(), toXFlow(), …)
              └─ elle yazılmış kod gibi derlenir; çalışma zamanında kmapper-core seam'lerini çağırır
```

Tiplerle ilgili her şey **derleme zamanında** kararlaştırılır: hangi alanı hangi converter
işler, her kaçış ladder'ın hangi basamağını sağlar, hangi validator'lar ateşler. Sıcak yolda
çalışma zamanı registry araması yok, hiçbir yerde reflection yok.

## Üretilen kod neye benziyor?

```kotlin
public fun UserResponse.toUser(): User {
    if (KMapper.hasListeners) KMapper.dispatch { onMapStart(this@toUser, User::class) }
    val result = User(
        id = id,
        joined = joined.convertOrFail("joined", "kotlin.String", "kotlinx.datetime.LocalDate") {
            LocalDateStringConverter.convertFrom(it)
        },
    )
    if (KMapper.hasListeners) KMapper.dispatch { onMapComplete(this@toUser, result) }
    return result
}

// yalnızca bir sarmalayıcı KtResult'a çözümlendiğinde üretilir (bkz. @MapTo(wrapper = …) / modül ayarı):
public fun UserResponse.toUserResult(): Result<User> = KMapperWrapper.KtResult.wrap(this) { it.toUser() }
```

Dikkate değer noktalar:

- **Converter'lar object olarak çağrılır** (`LocalDateStringConverter.convertFrom(...)`),
  asla geçici cast olarak gömülmez — kullanıcı ve built-in converter'ları aynı raylarda koşar.
- **Seam'ler** (`convertOrFail`, `convertOrNull`, `convertEachOrSkip`, …)
  [ladder](../temel-kullanim/null-safety.md)'ı gerçekleyen public `kmapper-core`
  fonksiyonlarıdır — [elle yazılmış mapper'lara](../baslarken/ornekler.md) açık olan
  fonksiyonların aynısı.
- **Yollar string literal'dir** — R8/ProGuard'a dayanıklı hata mesajları.
- Gözlemlenebilirlik kancaları kullanılmadığında tek bir `hasListeners` kontrolünün arkasında
  kaybolur.
- **`toUser()`'ın kendisi hiçbir şeyi yakalamaz** — doğrudan fırlatır. Bir sarmalayıcı
  extension'ı düz fonksiyonu çağırır ve kendi `wrap`'ini uygular — tıpkı elle yazan bir çağıran
  gibi; bkz. [Dönüş Sarmalayıcıları](../temel-kullanim/donus-sarmalayicilari.md).

## Üretilen kodu incelemek

```
build/generated/ksp/<hedef>/kotlin/…
```

Üretilen dosyalar sıradan Kotlin'dir — okunur, debug edilir, breakpoint konur. Mapping
davranışı sizi şaşırttığında önce üretilen fonksiyonu okuyun; soruyu çoğunlukla o yanıtlar.

## Üretecin koruduğu tasarım değişmezleri

- Bir alan ya temiz eşlenir ya da build sorunu adıyla söyler — sessiz atlama yok.
- *Siz* converter'ını yazmadıkça kayıplı dönüşüm yoktur
  ([ret politikası](../tip-donusumu/builtin.md)).
- Emilen her hatanın bir sink olayı, sert her hatanın bir yolu vardır.
- `CancellationException` her zaman yeniden fırlatılır — mapping'ler coroutine iptalini asla
  yutmaz.

> Sıradaki: **[Annotation Referansı →](../referans/anotasyonlar.md)**
