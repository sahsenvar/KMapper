# İç İçe Modeller ve Hata Yolları

Bir alanın tipi kendisi de eşlenmiş bir çiftse, KMapper onu üretilmiş alt mapper'dan otomatik
geçirir — ve hatalar adreslenebilir kalsın diye hata yollarını uç uca ekler.

## İç içe geçme kendiliğinden çalışır

```kotlin
data class Order(val id: Long, val customer: Customer)
data class Customer(val name: String, val address: Address)
data class Address(val street: String, val zipCode: Int)

@MapTo(Order::class)
data class OrderResponse(val id: Long, val customer: CustomerResponse)

@MapTo(Customer::class)
data class CustomerResponse(val name: String, val address: AddressResponse)

@MapTo(Address::class)
data class AddressResponse(val street: String, val zipCode: String)
```

Her seviyenin kendi `@MapTo`'su olmalı (her çift açık bir tanımdır — yapısal tahmin yok) ve
en üstteki çağrı bütün ağacı eşler:

```kotlin
val order = orderResponse.toOrderResult().getOrThrow()
```

## Hatalar tam yolu taşır

`zipCode` üç seviye derinlikte `"ABC"` geldiğinde:

```
Cannot convert customer.address.zipCode: String -> Int failed for value "ABC" …
```

Yol, üretilen koddaki **derleme zamanı string literal'lerinden** kurulur — R8/ProGuard
karartmasından aynen sağ çıkar. Koleksiyonlar indeks segmenti ekler: `items[3].price`.

## Hasar yarıçapını sınırlamak

Varsayılan olarak herhangi bir yerdeki sert hata `toOrderResult()`'ın tamamını düşürür — tek
`Result`, tek sınır. Payload'un bir *bölümü* isteğe bağlıysa bunu tiple beyan edin; hata
orada durur:

```kotlin
data class Order(
    val id: Long,
    val customer: Customer?, // bozuk customer artık siparişin tamamını öldürmez
)
```

Bozuk alt eşleme bu kez nullable kaçışta emilir (iç içe yolu taşıyan bir
`AbsorbedConversionError` raporuyla) ve siparişin kalanı normal eşlenir. Ladder bileşiktir:
*hataya en yakın kaçış* kazanır.

> Sıradaki: **[Koleksiyonlar →](koleksiyonlar.md)**
