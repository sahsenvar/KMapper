# Zihinsel Model

KMapper'ın yaptığı her şey üç kuraldan türer. Bunları içselleştirin; bu kılavuzdaki her sayfa
birer dipnota dönüşür.

## Kural 1 — Eksiklik tipi takip eder

Kaynak değer **eksikken** (nullable kaynak alanı `null`), ne olacağına *hedef alanın tipi*
karar verir. Annotation gerekmez:

| Hedef alan | Eksikliğin sonucu |
|------------|--------------------|
| `val x: T?` | `null` |
| `val x: T = default` | constructor default'u (argüman basitçe atlanır) |
| `val x: T` (ikisi de değil) | `MappingException.RequiredFieldMissing` |

Beyan edilmiş eksiklik **sessizdir** — nullable bir alanın null gelmesi normal veridir,
bir olay değil.

## Kural 2 — Bozukluk gürültülüdür ama kontrol altındadır

Kaynak değer **var ama bozukken** (parse edilemeyen tarih, uygulamanın tanımadığı enum
değeri) KMapper **fallback ladder**'ı yürür:

```
dönüştürülmüş değer  >  constructor default  >  null  >  hata
```

Bozuk bir değer, beyan edilmiş bir kaçışa (default ya da nullable) emilebilir — ama
eksiklikten farklı olarak **her emilme raporlanır**
([degradation sink](../gozlemleme/listener.md)). Mapping'in tamamı zaten `Result<T>` döner;
`.getOrThrow()` demediğiniz sürece sert bir hata bile uygulamanızı çökertmez.

Ayrım önemli: *eksiklik veridir, bozukluk sinyaldir.* 99 sağlam alanla kullanıcıya hizmet
etmeye devam edersiniz; telemetriniz 1 bozuk alanı size söyler.

## Kural 3 — Esneklik açık, yerel ve derleyici kontrolündedir

Yukarıdakilerin hiçbiri global olarak *daha* gevşetilemez — "hataları yoksay" düğmesi yok.
Ayarlamalar, modelde görünen alan bazlı annotation'lardır:

```kotlin
@ConvertWith(onFail = OnFail.Throw)  // bu alan emilemeyecek kadar önemli
@ConvertWith(onFail = OnFail.Skip)   // bu koleksiyonun bozuk elemanlarını at
```

Güvenle yapılamayan her şey — kırpacak bir converter, kimsenin yazmadığı bir yön —
**derleme zamanında**, çözümü söyleyen bir mesajla durur.

## Parity ilkesi

Kural olmayan ama söz olan bir şey daha: **kütüphane ne yapabiliyorsa, siz de
yapabilirsiniz.** Built-in converter'lar, validator'lar ve collection wrapper'ları,
sizinkilerle aynı raylarda koşan sıradan public sınıflardır. `LocalDate ↔ String` tip çiftiyle
çözümlenen bir built-in converter nesnesi alıyorsa, sizin `Money ↔ String` converter'ınız da
tam olarak aynı şekilde kaydedilir, çözümlenir, override edilir ve derlemede denetlenir.
Yazara özel ayrıcalıklı bir mekanizma yoktur.

## Hangi kavram nerede?

| Kavram | Sayfa |
|--------|-------|
| skaler alanlarda ladder | [Null Güvenliği](../temel-kullanim/null-safety.md) |
| koleksiyon elemanlarında ladder | [Koleksiyonlar](../temel-kullanim/koleksiyonlar.md) |
| converter'lar ve keşif | [Built-in'ler](../tip-donusumu/builtin.md), [@KMapperConfig](../tip-donusumu/kmapperconfig.md) |
| alan bazlı politika | [@ConvertWith ve OnFail](../tip-donusumu/convert-with.md) |
| değişmezler (invariant) | [@Validate](../dogrulama/validate.md) |
| rapor kanalı | [Gözlemlenebilirlik](../gozlemleme/listener.md) |

> Sıradaki: **[Örnek Galerisi →](ornekler.md)**
