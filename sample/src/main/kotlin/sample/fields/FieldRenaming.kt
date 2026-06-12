package sample.fields

import com.sahsenvar.kmapper.annotations.FieldMap
import com.sahsenvar.kmapper.annotations.MapTo

/**
 * FIELDS 1 — renaming with `@FieldMap`.
 *
 * Wire formats love `snake_case`-ish or legacy names; your domain does not have to.
 * `@FieldMap(fieldName = ..., targetClass = ...)` sits on the SOURCE field and points at the
 * target field it should fill. Passing `targetClass` explicitly keeps the rename unambiguous
 * (and is required as soon as the class has more than one `@MapTo`).
 */
data class Article(
    val id: Long,
    val headline: String,
    val authorName: String,
)

@MapTo(Article::class)
data class ArticleResponse(
    val id: Long,
    @FieldMap(fieldName = "headline", targetClass = Article::class)
    val titleText: String,
    @FieldMap(fieldName = "authorName", targetClass = Article::class)
    val byline: String,
)

fun main() = runFieldRenamingDemo()

/** Callable from [sample.GalleryRunner] and the file's own `main`. */
fun runFieldRenamingDemo() {
    val article = ArticleResponse(id = 7, titleText = "Compile-time mapping", byline = "S. Senvar")
        .toArticleResult()
        .getOrThrow()
    println("renamed fields -> $article")
}
