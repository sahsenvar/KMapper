import com.sahsenvar.kmapper.annotations.MapTo
import kotlinx.collections.immutable.PersistentList

data class TagD(val name: String)
data class ProductD(val tags: PersistentList<TagD>)

@MapTo(TagD::class)
data class TagR(val name: String)

@MapTo(ProductD::class)
data class ProductR(val tags: List<TagR>)
