@file:OptIn(ExperimentalCompilerApi::class)

package com.sahsenvar.kmapper.processor

import com.sahsenvar.kmapper.MappingException
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi

/**
 * Return wrappers (`@MapTo/@MapFrom(wrapper = …)` + the module-wide `kmapper.wrapper` option):
 * the plain `toX()` core is ALWAYS generated; a wrapper adds `to{X}{suffix}` extensions on top,
 * one per `wrap` overload, built-in and user wrappers alike.
 */
class ReturnWrapperTest :
    BehaviorSpec({

        fun modelSource(wrapperArgument: String = "") =
            SourceFile.kotlin(
                "Models.kt",
                """
                import com.sahsenvar.kmapper.KMapperWrapper
                import com.sahsenvar.kmapper.annotations.MapTo

                data class UserDomainModel(val id: Int, val name: String)

                @MapTo(UserDomainModel::class$wrapperArgument)
                data class UserDataModel(val id: String, val name: String)
                """.trimIndent(),
            )

        fun compileOk(
            sources: List<SourceFile>,
            kspOptions: Map<String, String> = emptyMap(),
        ): Pair<JvmCompilationResult, String> {
            val (result, compilation) = compile(sources, kspOptions)
            check(result.exitCode == KotlinCompilation.ExitCode.OK) { result.messages }
            return result to compilation.generatedFile("UserDataModelMappers.kt")
        }

        fun JvmCompilationResult.userData(id: String) = newInstance("UserDataModel", id, "Ada")

        given("a mapping with no wrapper argument and no module-wide option") {
            val (result, generated) = compileOk(listOf(modelSource()))

            then("only the plain core is generated") {
                generated shouldContain "public fun UserDataModel.toUserDomainModel(): UserDomainModel {"
                generated shouldNotContain "Result"
                generated shouldNotContain "runCatching"
            }
            then("the plain core returns the value directly") {
                val mapped = result.invokeMapper("UserDataModelMappersKt", "toUserDomainModel", result.userData("7"))
                mapped!!.prop("id") shouldBe 7
            }
            then("the plain core throws a MappingException on a hard failure") {
                shouldThrow<MappingException> {
                    result.invokeMapper("UserDataModelMappersKt", "toUserDomainModel", result.userData("seven"))
                }
            }
        }

        given("wrapper = KMapperWrapper.KtResult on the annotation") {
            val (result, generated) = compileOk(listOf(modelSource(", wrapper = KMapperWrapper.KtResult::class")))

            then("the core is kept and a Result extension delegates to it") {
                generated shouldContain "public fun UserDataModel.toUserDomainModel(): UserDomainModel {"
                generated shouldContain
                    "public fun UserDataModel.toUserDomainModelResult(): Result<UserDomainModel> = " +
                    "KMapperWrapper.KtResult.wrap<UserDataModel, UserDomainModel>(this) { it.toUserDomainModel() }"
            }
            then("a hard failure becomes Result.failure instead of a throw") {
                val failed = result.invokeResultMapper("UserDataModelMappersKt", "toUserDomainModelResult", result.userData("x"))
                failed.exceptionOrNull().shouldBeInstanceOf<MappingException>()
                val mapped = result.invokeResultMapper("UserDataModelMappersKt", "toUserDomainModelResult", result.userData("3"))
                mapped.getOrThrow()!!.prop("id") shouldBe 3
            }
        }

        given("wrapper = KMapperWrapper.Flow on the annotation") {
            val (result, generated) = compileOk(listOf(modelSource(", wrapper = KMapperWrapper.Flow::class")))

            then("both Flow overloads are generated — single value and Flow of sources") {
                generated shouldContain "public fun UserDataModel.toUserDomainModelFlow(): Flow<UserDomainModel>"
                generated shouldContain "public fun Flow<UserDataModel>.toUserDomainModelFlow(): Flow<UserDomainModel>"
            }
            then("the single-value overload emits the mapped value") {
                val method =
                    result.classLoader
                        .loadClass("UserDataModelMappersKt")
                        .declaredMethods
                        .first { it.name == "toUserDomainModelFlow" && it.parameterTypes[0].name == "UserDataModel" }
                val flow = method.invoke(null, result.userData("5")) as Flow<*>
                runBlocking { flow.toList() }.single()!!.prop("id") shouldBe 5
            }
            then("the Flow overload maps every emitted source") {
                val method =
                    result.classLoader
                        .loadClass("UserDataModelMappersKt")
                        .declaredMethods
                        .first { it.name == "toUserDomainModelFlow" && it.parameterTypes[0] == Flow::class.java }
                val flow = method.invoke(null, flowOf(result.userData("1"), result.userData("2"))) as Flow<*>
                runBlocking { flow.toList() }.map { it!!.prop("id") } shouldBe listOf(1, 2)
            }
        }

        given("wrapper = KMapperWrapper.None while the module-wide option says KtResult") {
            val (_, generated) =
                compileOk(
                    listOf(modelSource(", wrapper = KMapperWrapper.None::class")),
                    kspOptions = mapOf("kmapper.wrapper" to "KtResult"),
                )

            then("the explicit per-mapping None wins") {
                generated shouldNotContain "toUserDomainModelResult"
            }
        }

        given("the module-wide option set to each built-in short name") {
            listOf(
                "KtResult" to "toUserDomainModelResult",
                "Flow" to "toUserDomainModelFlow",
            ).forEach { (option, expectedFunction) ->
                then("Default resolves to $option") {
                    val (_, generated) = compileOk(listOf(modelSource()), kspOptions = mapOf("kmapper.wrapper" to option))
                    generated shouldContain "fun UserDataModel.$expectedFunction("
                }
            }
            then("None and Default both mean plain only") {
                listOf("None", "Default", "").forEach { option ->
                    val (_, generated) = compileOk(listOf(modelSource()), kspOptions = mapOf("kmapper.wrapper" to option))
                    generated shouldNotContain "Result"
                    generated shouldNotContain "Flow"
                }
            }
        }

        given("a user-written wrapper, used exactly like the built-ins") {
            val wrapperSource =
                SourceFile.kotlin(
                    "Outcome.kt",
                    """
                    package example.wrapper

                    import com.sahsenvar.kmapper.KMapperWrapper
                    import com.sahsenvar.kmapper.WrapperSuffix

                    sealed interface Outcome<out T> {
                        data class Ok<T>(val value: T) : Outcome<T>
                        data class Failed(val error: Throwable) : Outcome<Nothing>
                    }

                    @WrapperSuffix("Outcome")
                    object OutcomeWrapper : KMapperWrapper {
                        fun <S, T> wrap(source: S, map: (S) -> T): Outcome<T> =
                            try { Outcome.Ok(map(source)) } catch (error: Exception) { Outcome.Failed(error) }

                        fun <S, T> wrap(source: List<S>, map: (S) -> T): List<Outcome<T>> = source.map { wrap(it, map) }
                    }
                    """.trimIndent(),
                )

            `when`("it is named on the annotation") {
                val source =
                    SourceFile.kotlin(
                        "Models.kt",
                        """
                        import com.sahsenvar.kmapper.annotations.MapTo
                        import example.wrapper.OutcomeWrapper

                        data class UserDomainModel(val id: Int, val name: String)

                        @MapTo(UserDomainModel::class, wrapper = OutcomeWrapper::class)
                        data class UserDataModel(val id: String, val name: String)
                        """.trimIndent(),
                    )
                val (result, generated) = compileOk(listOf(wrapperSource, source))

                then("one extension per wrap overload is generated, with substituted types") {
                    generated shouldContain "public fun UserDataModel.toUserDomainModelOutcome(): Outcome<UserDomainModel>"
                    generated shouldContain
                        "public fun List<UserDataModel>.toUserDomainModelOutcome(): List<Outcome<UserDomainModel>>"
                }
                then("the wrapper runs around the plain core") {
                    val singleValueOverload =
                        result.classLoader
                            .loadClass("UserDataModelMappersKt")
                            .declaredMethods
                            .first { it.name == "toUserDomainModelOutcome" && it.parameterTypes[0].name == "UserDataModel" }
                    val failed = singleValueOverload.invoke(null, result.userData("x"))
                    failed!!::class.java.simpleName shouldBe "Failed"
                    val mapped = singleValueOverload.invoke(null, result.userData("4"))
                    mapped!!.prop("value")!!.prop("id") shouldBe 4
                }
            }

            `when`("it is the module-wide option, by fully qualified name") {
                val (_, generated) =
                    compileOk(
                        listOf(wrapperSource, modelSource()),
                        kspOptions = mapOf("kmapper.wrapper" to "example.wrapper.OutcomeWrapper"),
                    )

                then("Default resolves to the user wrapper") {
                    generated shouldContain "fun UserDataModel.toUserDomainModelOutcome()"
                }
            }
        }

        given("external (non-source) target parameters") {
            val source =
                SourceFile.kotlin(
                    "Models.kt",
                    """
                    import com.sahsenvar.kmapper.KMapperWrapper
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class UserDomainModel(val id: Int, val tenant: String)

                    @MapTo(UserDomainModel::class, wrapper = KMapperWrapper.KtResult::class)
                    data class UserDataModel(val id: Int)
                    """.trimIndent(),
                )
            val (_, compilation) = compile(source)

            then("the wrapper takes and forwards them by name") {
                val generated = compilation.generatedFile("UserDataModelMappers.kt")
                generated shouldContain "fun UserDataModel.toUserDomainModelResult(tenant: String): Result<UserDomainModel>"
                generated shouldContain "{ it.toUserDomainModel(tenant = tenant) }"
            }
        }

        given("a nested mapping whose parent is wrapped") {
            val source =
                SourceFile.kotlin(
                    "Nested.kt",
                    """
                    import com.sahsenvar.kmapper.KMapperWrapper
                    import com.sahsenvar.kmapper.annotations.MapTo

                    data class AddressDomainModel(val city: String)
                    data class UserDomainModel(val address: AddressDomainModel)

                    @MapTo(AddressDomainModel::class, wrapper = KMapperWrapper.Flow::class)
                    data class AddressDataModel(val city: String)

                    @MapTo(UserDomainModel::class, wrapper = KMapperWrapper.KtResult::class)
                    data class UserDataModel(val address: AddressDataModel)
                    """.trimIndent(),
                )
            val (_, compilation) = compile(source)

            then("the parent calls the nested plain core, whatever the nested wrapper is") {
                val generated = compilation.generatedFile("UserDataModelMappers.kt")
                generated shouldContain "it.toAddressDomainModel()"
                generated shouldNotContain "toAddressDomainModelFlow"
            }
        }

        given("invalid wrappers") {
            then("a wrapper without @WrapperSuffix is a compile error") {
                val messages =
                    errMessages(
                        SourceFile.kotlin(
                            "Bad.kt",
                            """
                            import com.sahsenvar.kmapper.KMapperWrapper
                            import com.sahsenvar.kmapper.annotations.MapTo

                            object NoSuffixWrapper : KMapperWrapper {
                                fun <S, T> wrap(source: S, map: (S) -> T): T = map(source)
                            }

                            data class UserDomainModel(val id: Int)

                            @MapTo(UserDomainModel::class, wrapper = NoSuffixWrapper::class)
                            data class UserDataModel(val id: Int)
                            """.trimIndent(),
                        ),
                    )
                messages shouldContain "needs a non-blank @WrapperSuffix"
            }
            then("a wrap function with the wrong shape is a compile error") {
                val messages =
                    errMessages(
                        SourceFile.kotlin(
                            "Bad.kt",
                            """
                            import com.sahsenvar.kmapper.KMapperWrapper
                            import com.sahsenvar.kmapper.WrapperSuffix
                            import com.sahsenvar.kmapper.annotations.MapTo

                            @WrapperSuffix("Bad")
                            object WrongShapeWrapper : KMapperWrapper {
                                fun <T> wrap(value: T): T = value
                            }

                            data class UserDomainModel(val id: Int)

                            @MapTo(UserDomainModel::class, wrapper = WrongShapeWrapper::class)
                            data class UserDataModel(val id: Int)
                            """.trimIndent(),
                        ),
                    )
                messages shouldContain "unsupported shape"
            }
            then("a wrap function whose receiver does not carry the source is a compile error") {
                val messages =
                    errMessages(
                        SourceFile.kotlin(
                            "Bad.kt",
                            """
                            import com.sahsenvar.kmapper.KMapperWrapper
                            import com.sahsenvar.kmapper.WrapperSuffix
                            import com.sahsenvar.kmapper.annotations.MapTo

                            @WrapperSuffix("Bad")
                            object SourcelessWrapper : KMapperWrapper {
                                fun <S, T> wrap(source: String, map: (S) -> T): List<T> = emptyList()
                            }

                            data class UserDomainModel(val id: Int)

                            @MapTo(UserDomainModel::class, wrapper = SourcelessWrapper::class)
                            data class UserDataModel(val id: Int)
                            """.trimIndent(),
                        ),
                    )
                messages shouldContain "unsupported shape"
            }
            then("an unresolvable module-wide option is a compile error") {
                val (result, _) = compile(listOf(modelSource()), kspOptions = mapOf("kmapper.wrapper" to "com.missing.Nope"))
                result.exitCode shouldBe KotlinCompilation.ExitCode.COMPILATION_ERROR
                result.messages shouldContain "cannot be resolved"
            }
        }
    })

