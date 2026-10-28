package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
