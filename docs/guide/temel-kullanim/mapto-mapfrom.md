# @MapTo ve @MapFrom

İki annotation da aynı şeyi söyler — "şu iki sınıf arasında mapping üret" — tek farkları
**tanımı hangi sınıfın taşıdığıdır**.

## @MapTo — kaynak üzerinde

```kotlin
@MapTo(User::class)
data class UserResponse(val id: Long, val name: String)

// üretir: fun UserResponse.toUserResult(): Result<User>
```

## @MapFrom — hedef üzerinde

Bazen kaynak sınıf sizin değildir (başka bir modül, üretilmiş kod). Tanımı hedefe koyun;
üretilen fonksiyon birebir aynıdır:

```kotlin
@MapFrom(UserResponse::class)
data class User(val id: Long, val name: String)

// aynısını üretir: fun UserResponse.toUserResult(): Result<User>
```

## Hangi tarafı işaretlemeli?

Tercihiniz **wire modelinde `@MapTo`** olsun. Wire modelleri değişken taraftır — API
değiştiğinde model ve mapping kuralları aynı dosyada birlikte değişir. Kaynak sınıf sizin
kontrolünüzde değilse `@MapFrom`'a uzanın.

## İkisi de tekrarlanabilir

Bir kaynak birden çok hedefe, bir hedef birden çok kaynaktan eşlenebilir:

```kotlin
@MapTo(User::class)
@MapTo(UserListItem::class)
data class UserResponse(val id: Long, val name: String, val avatarUrl: String?)

// fun UserResponse.toUserResult(): Result<User>
// fun UserResponse.toUserListItemResult(): Result<UserListItem>
```

Gerektiğinde alan direktifleri tek bir hedefe daraltılabilir — bkz.
[`@FieldMap(targetClass = …)`](alan-eslestirme.md).

## Neler eşlenir?

Alanlar, kaynağın property'leri ile hedefin primary constructor parametreleri arasında
**isimle** eşlenir. Eşleşen her çift için sırasıyla:

1. aynı tip → kopyalanır
2. farklı tip → derleme zamanında bir [converter](../tip-donusumu/builtin.md) çözümlenir
3. iç içe `@MapTo`/`@MapFrom` çifti → üretilen alt mapper'dan geçer
4. hiçbiri uymazsa → alanı ve eksik parçayı söyleyen **derleme hatası**

Karşılığı ve default'u olmayan *hedef* alanları, üretilen fonksiyonun zorunlu parametresi
olur (bkz. [çağıranın sağladığı parametreler](alan-eslestirme.md)).

> Sıradaki: **[Alan Eşleme ve Ignore Ailesi →](alan-eslestirme.md)**
