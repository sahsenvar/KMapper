# SSS

## Üretilen fonksiyon neden fırlatmak yerine `Result` dönüyor?

Çünkü wire verisi eninde sonunda bozuk *gelecek* ve fırlatan bir mapper, başkasının kötü
deploy'unu sizin crash'inize çevirir. `Result` ile hata imzanın parçasıdır: çağrı noktası
karar verir (`getOrThrow` / `getOrElse` / `fold`) ve karar code review'da görünür.
[Ayrıntılar](../hata-yonetimi/mapping-exception.md).

## Bozuk değerim neden hataya dönüşmedi?

Muhtemelen hedef alan nullable ya da default'lu —
[fallback ladder](../temel-kullanim/null-safety.md)'da beyan edilmiş bir kaçış. Emilme,
[degradation sink](../gozlemleme/listener.md)'e raporlandı; bir listener kaydedin, görürsünüz.
O alan için sertlik mi istiyorsunuz? `@ConvertWith(onFail = OnFail.Throw)`.

## Neden `@MapDefaultValue` yok?

Kotlin'in zaten default değerleri var — constructor'da. KMapper onları *argümanı atlayarak*
kullanır: default tam olarak tek yerde yaşar ve mapping ile elle kurma için birebir aynı
davranır. Bir default wire fallback'i gibi davranmamalıysa, o iş
[`@IgnoreDefaultValue`](../temel-kullanim/alan-eslestirme.md)'nun.

## `Long → Int` neden build'imi düşürüyor? Diğer mapper'lar dönüştürüyor.

Dönüştürüyorlar — *değer sığmayana kadar*; sonra sessizce yanlış bir sayı veriyorlar. KMapper
kayıplı yönleri gerekçeli mesajla
[derleme zamanında reddeder](../tip-donusumu/builtin.md); domain'iniz aralığı garanti
ediyorsa üç satırlık custom converter bu güvenceyi açık ve sahipli yapar.

## KMapper enum'ları neden otomatik olarak adla eşlemiyor?

`name`/`ordinal` eşlemesi yeniden adlandırma/sıralamada sessizce kırılır — KMapper'ın yok
etmek için var olduğu hata sınıfının ta kendisi.
[`MappableEnum`](../enum/mappable-enum.md) sabit başına bir `wireValue`'ya mal olur ve
yeniden adlandırmaya dayanıklıdır.

## `@ConvertWith` annotation'ım yok sayılıyor gibi. Neden?

Alan direktifleri **üretilen yönün kaynak tarafından** okunur — `@MapTo` wire modelindeyse
wire alanını işaretleyin, domain alanını değil.
[Yerleşim kuralı](../tip-donusumu/convert-with.md).

## KMapper'ı kod üretimi olmadan kullanabilir miyim?

Evet — `kmapper-core` bağımsız bir artifact. Elle yazılmış mapper'lar, üretilen kodla aynı
public seam'leri, ladder semantiğini, converter'ları ve hata tiplerini kullanır
([parity ilkesi](../baslarken/zihinsel-model.md)). `CoreOnlyMapping`
[örneğine](../baslarken/ornekler.md) bakın.

## Ne üretildiğini nasıl görürüm?

`build/generated/ksp/<hedef>/kotlin/…` — düz Kotlin, breakpoint konabilir.
[Mimari](../ileri/mimari.md).

## R8/ProGuard ile çalışıyor mu?

Evet. Reflection yok ve hata yolları derleme zamanı string literal'i — release build stack
trace'leriniz hâlâ `customer.address.zipCode` der.

## Eksiksiz çalıştırılabilir örnekler nerede?

[Örnek galerisi](../baslarken/ornekler.md): 25 dosya, her özellik, basitten gelişmişe, her
biri belgelenmiş çıktısıyla.
