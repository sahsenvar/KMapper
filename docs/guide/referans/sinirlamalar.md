# Sınırlamalar ve Yol Haritası

Mevcut sürümün dürüst kenarları, gerekçeleriyle — çoğu bilinçli kapsam kararıdır ve projenin
tasarım defterinde kayıtlıdır.

## Mevcut sınırlamalar

- **Modül başına, tip çifti başına tek converter.**
  [Keşif](../tip-donusumu/kmapperconfig.md) belirsizliğe düşemez; format varyantları alan
  bazlı [`@ConvertWith`](../tip-donusumu/convert-with.md) kararıdır.
- **Validator'lar ve converter'lar object'tir.** Üretilen kod onları FQN ile çağırır —
  instance state yok, DI yok. Parametreleme
  [açık taban türetmesiyle](../tip-donusumu/ozel-converter.md) yapılır.
- **`@FieldMap` yalın adla eşler.** Nitelikli yol yeniden adlandırmaları
  (`Data.wireScore → Domain.score` sözdizimi) desteklenmez; tek ad bugüne dek yetti.
- **Map biçimli özel kaplar** (iki tip parametresi, `MultiMap<K, V>` tarzı)
  `@CollectionWrapper` sözleşmesinin dışındadır — wrapper'lar tek eleman tipli kapları
  kapsar.
- **Doğrulama her zaman serttir.** `@Validate` hatası
  [ladder'a](../temel-kullanim/null-safety.md) asla binmez; "emilebilir doğrulama" gerçek bir
  kullanım senaryosu çıkana dek park edildi.
- **`kotlin.uuid.Uuid` core built-in değil** — API deneysel olduğu sürece
  [add-on karşılıyor](../tip-donusumu/uuid.md).

## Park edilenler (tasarlandı, henüz yayınlanmadı)

- **Arrow birikimli sınır** — `toXAccumulated(): IorNel<MappingError, X>`: hızlı düşmek
  yerine *bütün* hataları topla. Uçtan uca tasarlandı; bir sonraki sürümde.
- **Mapping başına özet sink olayı** ("100 elemandan 3'ü düştü") ve listener kısma rehberi.
- **`OnAbsent` eleman politikası** (null kaynak elemanını hata say) — talep gelirse.
- **Map anahtarlarında çakışmada-katı seçeneği** (şimdiki: son giren kazanır +
  `DuplicateKey` raporu).
- **`converters-format` add-on'u** (locale duyarlı sayı formatlama) — bu arada
  [parametreli converter reçetesi](../tip-donusumu/ozel-converter.md) kullanıcı tarafında
  karşılıyor.

Sizi engelleyen bir sınırlama mı buldunuz?
[Issue açın](https://github.com/sahsenvar/KMapper/issues) — park edilenler gerçek kullanım
senaryolarıyla parktan çıkar.

> Sıradaki: **[SSS →](sss.md)**
