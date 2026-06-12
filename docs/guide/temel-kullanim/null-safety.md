# Null Güvenliği ve Fallback Ladder

Bu sayfa, [zihinsel modelin](../baslarken/zihinsel-model.md) 1. ve 2. kuralının çalışan kod
halidir.

## Dört nullable kombinasyonu

Kaynak ile hedef arasında eşlenen bir alan için derleyici dört kombinasyonun hepsini ayrı
ayrı ele alır:

| Kaynak | Hedef | Davranış |
|--------|-------|----------|
| `T` | `T` | doğrudan kopya / dönüşüm |
| `T` | `T?` | doğrudan — non-null değer nullable yuvayı doldurur |
| `T?` | `T?` | doğrudan — null, null olarak akar |
| `T?` | `T` | **ilginç olan** — aşağıda |

## Nullable → non-null: karar ladder'ın

```kotlin
data class User(
    val email: String,                //  kaçış yok       -> eksiklik hatadır
    val nickname: String = "anon",    //  default kaçışı  -> eksiklik default'u alır
    val bio: String?,                 //  nullable kaçışı -> eksiklik null olur
)

@MapTo(User::class)
data class UserResponse(
    val email: String?,
    val nickname: String?,
    val bio: String?,
)
```

Kaynak alan null geldiğinde:

```
1. (dönüştürülecek değer yok)
2. hedefin constructor default'u var mı? -> argüman atlanır, default uygulanır  [sessiz]
3. hedef nullable mı?                    -> null                                 [sessiz]
4. ikisi de değilse                      -> MappingException.RequiredFieldMissing
```

Eksikliğin beyan edilmiş bir kaçışa akması **sessizdir** — null biyografi veridir, olay
değildir.

## Bozuk değerler aynı ladder'ı yürür — ama sesli

Değer mevcut ama dönüşümü **fırlatıyorsa** (`"not-a-date"`, bilinmeyen enum değeri) aynı
kaçışlar geçerlidir; iki farkla:

- emilme, [degradation sink](../gozlemleme/listener.md)'e **raporlanır**
  (`AbsorbedConversionError`; alan yolu ve nedeniyle birlikte), ve
- [`@ConvertWith(onFail = OnFail.Throw)`](../tip-donusumu/convert-with.md) ile alan bazında
  emilmeyi yasaklayabilirsiniz.

```kotlin
// joined: String? -> LocalDate?   değer "garbage" iken
// -> hedefe null gider, sink'e AbsorbedConversionError(path="joined", cause=TypeConversionFailed) düşer
```

## Sert taban: RequiredFieldMissing

Kaçış yoksa mapping, yol taşıyan bir exception ile durur — crash olarak değil, `Result`
hatası olarak teslim edilir:

```kotlin
val result = UserResponse(email = null, …).toUserResult()
result.exceptionOrNull()?.message
// Required field missing: email
```

## Elle yazılan kod: aynı raylar

`kmapper-core`'un public seam'leri elle yazılmış mapper'lara birebir aynı semantiği verir —
üretilen kodun çağırdığı fonksiyonlar tam olarak bunlardır:

```kotlin
val email = response.email.orRequired("email") // null -> RequiredFieldMissing("email")
val joined = response.joined.convertOrNull("joined", "kotlin.String", "kotlinx.datetime.LocalDate") {
    LocalDateStringConverter.convertFrom(it) // bozuk -> null + sink raporu
}
```

Eksiksiz elle yazılmış bir mapper için [galerideki](../baslarken/ornekler.md)
`CoreOnlyMapping` örneğine bakın.

> Sıradaki: **[İç İçe Modeller ve Hata Yolları →](nested.md)**
