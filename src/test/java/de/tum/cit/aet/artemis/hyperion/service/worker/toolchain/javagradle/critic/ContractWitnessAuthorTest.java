package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification.ContractWitnessOutcome;

class ContractWitnessAuthorTest {

    @ParameterizedTest
    @ValueSource(strings = { "empty", "unknownRule", "suiteHelper" })
    void anEmptyOrRejectedBatchGetsOneCorrectionBeforeItCanCountAsReviewed(String mode) {
        var reviewer = mock(ReviewerClient.class);
        when(reviewer.configured()).thenReturn(true);
        String rejected = switch (mode) {
            case "unknownRule" -> """
                    {"witnesses":[{"rule":"R9","testName":"probe","wrongBehavior":"returns two","code":"@Test void probe() { assertEquals(1, 2); }"}]}
                    """;
            case "suiteHelper" -> """
                    {"witnesses":[{"rule":"R1","testName":"probe","wrongBehavior":"returns two","code":"@Test void probe() { assertEquals(1, scheduler(2).choose()); }"}]}
                    """;
            default -> "{\"witnesses\":[]}";
        };
        String code = "@org.junit.jupiter.api.Test void probe() { var subject = new Subject(); org.junit.jupiter.api.Assertions.assertEquals(1, subject.value()); }";
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn(rejected, """
                {"witnesses":[{"rule":"R1","testName":"probe","wrongBehavior":"returns two","code":"%s"}]}
                """.formatted(code));
        var author = new ContractWitnessAuthor(reviewer, JsonMapper.builder().build());

        assertThat(author.authorContractWitnesses("R1. Return one.", SUITE, "class Subject { int value() { return 1; } }", Map.of(), null, () -> false))
                .containsExactly(new ContractWitness("R1", "probe", code, "returns two"));
        verify(reviewer, times(2)).call(anyString(), anyString(), any(), any(), anyInt());
    }

    @Test
    void anEmptyCorrectionIsUnavailableRatherThanAnEmptySuccessfulReview() {
        var reviewer = mock(ReviewerClient.class);
        when(reviewer.configured()).thenReturn(true);
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn("{\"witnesses\":[]}");
        var author = new ContractWitnessAuthor(reviewer, JsonMapper.builder().build());

        assertThatThrownBy(() -> author.authorContractWitnesses("R1. Return one.", SUITE, "class Subject {}", Map.of(), null, () -> false))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no admissible executable witness after one bounded correction");
        verify(reviewer, times(2)).call(anyString(), anyString(), any(), any(), anyInt());
    }

    private static final String SUITE = """
            package example;

            class SchedulerTest {

                private static int if$helper() {
                    return 1;
                }

                private static Scheduler scheduler(
                        @SuppressWarnings("unused") int size) {
                    return new Scheduler();
                }

                @Test
                void choosesFirst() {
                    if (scheduler(1).choose() != 1) {
                        fail("expected first");
                    }
                    else if (if$helper() != 1) {
                        fail("expected one");
                    }
                    assertThat(scheduler(1).choose()).isEqualTo(1);
                }
            }
            """;

    @Test
    void compilerFeedbackKeepsTheOriginalProbeIdentityAndMisconception() {
        var reviewer = mock(ReviewerClient.class);
        when(reviewer.configured()).thenReturn(true);
        String correctedCode = "@org.junit.jupiter.api.Test void probe() { var subject = new Subject(); org.junit.jupiter.api.Assertions.assertEquals(1, subject.value()); }";
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn("""
                {"witnesses":[{"rule":"R1","testName":"probe","wrongBehavior":"model changed the description","code":"%s"}]}
                """.formatted(correctedCode));
        var original = new ContractWitness("R1", "probe", "@Test void probe() { assertEquals(1, subject.value()); }", "returns two");
        var failure = new ContractWitnessOutcome(original, ContractWitnessOutcome.Disposition.INCONCLUSIVE,
                "HyperionContractWitnessProbeTest.java:9: error: cannot find symbol subject");
        var author = new ContractWitnessAuthor(reviewer, JsonMapper.builder().build());

        var repaired = author.repairCompilation("R1. Return one.", "class Subject { int value() { return 1; } }", List.of(failure), null, () -> false);

        assertThat(repaired).containsExactly(new ContractWitness(original.ruleId(), original.testName(), correctedCode, original.wrongBehavior()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "differentRule", "differentName", "unchanged" })
    void compilationRepairCannotChangeTheRuleOrIdentityOrRepeatTheSameCode(String mode) {
        var reviewer = mock(ReviewerClient.class);
        when(reviewer.configured()).thenReturn(true);
        var original = new ContractWitness("R1", "probe", "@Test void probe() { assertEquals(1, subject.value()); }", "returns two");
        String name = mode.equals("differentName") ? "otherProbe" : "probe";
        String code = mode.equals("unchanged") ? original.code() : "@Test void " + name + "() { assertEquals(1, new Subject().value()); }";
        String rule = mode.equals("differentRule") ? "R2" : "R1";
        when(reviewer.call(anyString(), anyString(), any(), any(), anyInt())).thenReturn("""
                {"witnesses":[{"rule":"%s","testName":"%s","wrongBehavior":"returns two","code":"%s"}]}
                """.formatted(rule, name, code));
        var failure = new ContractWitnessOutcome(original, ContractWitnessOutcome.Disposition.INCONCLUSIVE,
                "HyperionContractWitnessProbeTest.java:9: error: cannot find symbol subject");
        var author = new ContractWitnessAuthor(reviewer, JsonMapper.builder().build());

        assertThat(author.repairCompilation("R1. Return one. R2. Keep requests.", "class Subject {}", List.of(failure), null, () -> false)).isEmpty();
    }

    @Test
    void aCallToAMethodDeclaredByTheSuiteIsASuiteLocalHelper() {
        assertThat(ContractWitnessAuthor.usesSuiteLocalHelper("assertEquals(1, scheduler(2).choose());", "witness", SUITE)).isTrue();
    }

    @Test
    void controlFlowAndLibraryCallsInTheSuiteAreNotSuiteLocalHelpers() {
        assertThat(ContractWitnessAuthor.usesSuiteLocalHelper("if (new Scheduler().choose() == 1) { assertThat(true).isTrue(); }", "witness", SUITE)).isFalse();
    }

    @Test
    void anElseIfLineIsNotADeclarationButADollarIdentifierStartingWithIfIs() {
        assertThat(ContractWitnessAuthor.usesSuiteLocalHelper("if (true) { assertEquals(1, 1); }", "witness", SUITE)).isFalse();
        assertThat(ContractWitnessAuthor.usesSuiteLocalHelper("assertEquals(1, if$helper());", "witness", SUITE)).isTrue();
    }

    @Test
    void aLongDeclarationPrefixDoesNotOverflowTheStack() {
        String suite = "class ExampleTest {\n" + "static ".repeat(20_000) + "int helper() { return 1; }\n}";

        assertThat(ContractWitnessAuthor.usesSuiteLocalHelper("assertEquals(1, helper());", "witness", suite)).isTrue();
    }

    @Test
    void helperDeclarationsCanHaveNestedAnnotationsAndAThrowsClause() {
        String suite = "class ExampleTest {\nprivate static int helper(@Limit(value = (1)) int size) throws Exception { return size; }\n}";

        assertThat(ContractWitnessAuthor.usesSuiteLocalHelper("assertEquals(1, helper(1));", "witness", suite)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { " ", "\t", "    " })
    void helperNamesCanBeSeparatedFromTheirParametersByWhitespace(String whitespace) {
        String suite = "class ExampleTest {\nprivate static int helper" + whitespace + "() { return 1; }\n}";

        assertThat(ContractWitnessAuthor.usesSuiteLocalHelper("assertEquals(1, helper());", "witness", suite)).isTrue();
    }
}
