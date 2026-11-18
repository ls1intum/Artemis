package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContractWitnessAuthorTest {

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
