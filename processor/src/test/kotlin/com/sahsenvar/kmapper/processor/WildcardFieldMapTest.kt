@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * A wildcard `@FieldMap(fieldName = "x")` — no `targetClass` — applies to EVERY generated
 * direction of the declaring class. With a single @MapTo this is the natural spelling; it
 * must rename the match, not silently drop it (degenerating the target slot into an
 * external parameter).
 */
class WildcardFieldMapTest :
    BehaviorSpec({

        given("a single @MapTo with a wildcard @FieldMap (named argument, no targetClass)") {
            val source =
                SourceFile.kotlin(
                    "WildcardRename.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class ArticleDomainModel(val headline: String, val views: Int)

                    @MapTo(ArticleDomainModel::class)
                    data class ArticleDataModel(
                        @FieldMap(fieldName = "headline") val title: String,
                        val views: Int,
                    )
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the renamed field maps through instead of becoming an external parameter") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    val generated = compilation.generatedFile("ArticleDataModelMappers.kt")
                    generated shouldContain "headline = title"
                    // the degenerate form renders `toArticleDomainModelResult(headline: String)` —
                    // pin the parameterless signature instead
                    generated shouldContain "toArticleDomainModelResult(): Result<ArticleDomainModel>"
                }
            }

            `when`("the generated mapper runs") {
                then("the value lands in the renamed target field") {
                    val mapped =
                        result
                            .invokeResultMapper(
                                "ArticleDataModelMappersKt",
                                "toArticleDomainModelResult",
                                result.newInstance("ArticleDataModel", "Breaking", 42),
                            ).getOrThrow()
                            .shouldNotBeNull()
                    mapped.prop("headline") shouldBe "Breaking"
                    mapped.prop("views") shouldBe 42
                }
            }
        }

        given("the same wildcard @FieldMap written with a positional argument") {
            val source =
                SourceFile.kotlin(
                    "WildcardPositional.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class TagDomainModel(val label: String)

                    @MapTo(TagDomainModel::class)
                    data class TagDataModel(
                        @FieldMap("label") val name: String,
                    )
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("positional and named spellings behave identically") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    val generated = compilation.generatedFile("TagDataModelMappers.kt")
                    generated shouldContain "label = name"
                }
            }
        }

        given("multiple @MapTo targets but a wildcard @FieldMap (ambiguous)") {
            val source =
                SourceFile.kotlin(
                    "WildcardAmbiguous.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class CardDomainModel(val headline: String)
                    data class RowDomainModel(val headline: String)

                    @MapTo(CardDomainModel::class)
                    @MapTo(RowDomainModel::class)
                    data class StoryDataModel(
                        @FieldMap(fieldName = "headline") val title: String,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the build fails asking for an explicit targetClass") {
                    val messages = errMessages(source)
                    messages shouldContain "must specify targetClass"
                }
            }
        }

        given("multiple @MapFrom sources but a wildcard @FieldMap (ambiguous, mirror of the @MapTo rule)") {
            val source =
                SourceFile.kotlin(
                    "WildcardMapFromAmbiguous.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.annotations.MapFrom

                    data class StoryWireModel(val title: String)
                    data class StoryFeedModel(val caption: String)

                    @MapFrom(StoryWireModel::class)
                    @MapFrom(StoryFeedModel::class)
                    data class StoryModel(
                        @FieldMap(fieldName = "title") val headline: String,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the build fails asking for an explicit targetClass") {
                    val messages = errMessages(source)
                    messages shouldContain "must specify targetClass"
                }
            }
        }

        given("two wildcard @FieldMaps on one @MapFrom field (claims two remote fields at once)") {
            val source =
                SourceFile.kotlin(
                    "WildcardMapFromDuplicate.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.annotations.MapFrom

                    data class NoteWireModel(val title: String, val subtitle: String)

                    @MapFrom(NoteWireModel::class)
                    data class NoteModel(
                        @FieldMap(fieldName = "title")
                        @FieldMap(fieldName = "subtitle")
                        val headline: String,
                    )
                    """.trimIndent(),
                )

            `when`("the processor runs") {
                then("the build fails on the multiple-mappings rule instead of silently picking one") {
                    val messages = errMessages(source)
                    messages shouldContain "has multiple mappings"
                }
            }
        }

        given("a @MapFrom target declaring a wildcard @FieldMap toward its source") {
            val source =
                SourceFile.kotlin(
                    "WildcardMapFrom.kt",
                    """
                    import com.sahsenvar.kmapper.annotations.FieldMap
                    import com.sahsenvar.kmapper.annotations.MapFrom

                    data class ArticleWireModel(val title: String)

                    @MapFrom(ArticleWireModel::class)
                    data class ArticleModel(
                        @FieldMap(fieldName = "title") val headline: String,
                    )
                    """.trimIndent(),
                )
            val (result, compilation) = compile(source)

            `when`("the processor runs") {
                then("the reverse direction honors the wildcard rename") {
                    result.exitCode shouldBe KotlinCompilation.ExitCode.OK
                    // @MapFrom generates onto the SOURCE (receiver) class — the file is named after it
                    val generated = compilation.generatedFile("ArticleWireModelMappers.kt")
                    generated shouldContain "headline = title"
                }
            }

            `when`("the generated mapper runs") {
                then("the wire value lands in the renamed domain field") {
                    val mapped =
                        result
                            .invokeResultMapper(
                                "ArticleWireModelMappersKt",
                                "toArticleModelResult",
                                result.newInstance("ArticleWireModel", "Sabah haberi"),
                            ).getOrThrow()
                            .shouldNotBeNull()
                    mapped.prop("headline") shouldBe "Sabah haberi"
                }
            }
        }
    })
