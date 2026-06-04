import arrow.core.NonEmptyList
import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet

data class TagD(val name: String)
data class ProductD(val tags: PersistentList<TagD>)
data class CartD(val items: PersistentSet<TagD>)

@MapTo(TagD::class)
data class TagR(val name: String)

@MapTo(ProductD::class)
data class ProductR(val tags: List<TagR>)

@MapTo(CartD::class)
data class CartR(val items: List<TagR>)

data class TeamD(val members: NonEmptyList<TagD>)

@MapTo(TeamD::class)
data class TeamR(val members: List<TagR>)
