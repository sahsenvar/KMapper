package com.sahsenvar.kmapper.itest

import com.sahsenvar.kmapper.MappingException
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * End-to-end coverage of `@MapTo(wrapper = …)`: the plain core `toX()` is ALWAYS generated, and
 * a wrapper adds `toX{suffix}()` extension(s) on top of it — built-in ([KMapperWrapper.KtResult],
 * [KMapperWrapper.Flow]) and a user-written one ([OutcomeWrapper], defined in
 * `ReturnWrapperModels.kt`) exercised the same way, per the project's user–author parity principle.
 */
class ReturnWrapperIntegrationTest {
    // ─── Default (no wrapper argument): plain core only, throws MappingException ───────────

    @Test
    fun `plain core maps successfully`() {
        val domain = PlainWrapperDataModel(id = "7", name = "Ada").toWrapperTargetDomainModel()
        domain.id shouldBe 7
        domain.name shouldBe "Ada"
    }

    @Test
    fun `plain core throws MappingException on a hard failure`() {
        val exception =
            assertFailsWith<MappingException.TypeConversionFailed> {
                PlainWrapperDataModel(id = "not-a-number", name = "Ada").toWrapperTargetDomainModel()
            }
        exception.path shouldBe "id"
    }

    // ─── KMapperWrapper.KtResult: plain core + toXResult(): Result<X> ───────────────────────

    @Test
    fun `KtResult wrapper turns a success into Result_success`() {
        val outcome = ResultWrapperDataModel(id = "3", name = "Grace").toWrapperTargetDomainModelResult()
        outcome.isSuccess shouldBe true
        outcome.getOrThrow().id shouldBe 3
    }

    @Test
    fun `KtResult wrapper turns a hard failure into Result_failure instead of throwing`() {
        val outcome = ResultWrapperDataModel(id = "nope", name = "Grace").toWrapperTargetDomainModelResult()
        outcome.isFailure shouldBe true
        outcome.exceptionOrNull().shouldBeInstanceOf<MappingException.TypeConversionFailed>()
    }

    // ─── KMapperWrapper.Flow: plain core + toXFlow() on Source AND on Flow<Source> ──────────

    @Test
    fun `Flow wrapper — single-value overload emits the mapped value`() = runTest {
        val mapped = FlowWrapperDataModel(id = "9", name = "Barbara").toWrapperTargetDomainModelFlow().toList()
        mapped.single().id shouldBe 9
    }

    @Test
    fun `Flow wrapper — single-value overload throws on collection for a hard failure`() = runTest {
        assertFailsWith<MappingException.TypeConversionFailed> {
            FlowWrapperDataModel(id = "bad", name = "Barbara").toWrapperTargetDomainModelFlow().toList()
        }
    }

    @Test
    fun `Flow wrapper — Flow-of-sources overload maps every emitted source`() = runTest {
        val sources =
            flowOf(
                FlowWrapperDataModel(id = "1", name = "Ada"),
                FlowWrapperDataModel(id = "2", name = "Barbara"),
            )
        val mapped = sources.toWrapperTargetDomainModelFlow().toList()
        mapped.map { it.id } shouldBe listOf(1, 2)
    }

    // ─── User-written wrapper (OutcomeWrapper): two wrap overloads, same rails as the built-ins ──

    @Test
    fun `user wrapper — single-value overload wraps a success as Outcome_Ok`() {
        val outcome = OutcomeWrapperDataModel(id = "5", name = "Katherine").toWrapperTargetDomainModelOutcome()
        val ok = outcome.shouldBeInstanceOf<Outcome.Ok<WrapperTargetDomainModel>>()
        ok.value.id shouldBe 5
    }

    @Test
    fun `user wrapper — single-value overload wraps a hard failure as Outcome_Failed`() {
        val outcome = OutcomeWrapperDataModel(id = "nope", name = "Katherine").toWrapperTargetDomainModelOutcome()
        val failed = outcome.shouldBeInstanceOf<Outcome.Failed>()
        failed.error.shouldBeInstanceOf<MappingException.TypeConversionFailed>()
    }

    @Test
    fun `user wrapper — List-of-sources overload wraps each element independently`() {
        val sources =
            listOf(
                OutcomeWrapperDataModel(id = "1", name = "Ada"),
                OutcomeWrapperDataModel(id = "nope", name = "Barbara"),
            )
        val outcomes = sources.toWrapperTargetDomainModelOutcome()
        outcomes[0].shouldBeInstanceOf<Outcome.Ok<WrapperTargetDomainModel>>().value.id shouldBe 1
        outcomes[1].shouldBeInstanceOf<Outcome.Failed>()
    }
}
