package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic.ContractWitness;

class ContractWitnessProbeTest {

    /** Shaped like a real generated suite: Ares harness annotations on the class, JUnit and static assertion imports. */
    private static final String GENERATED_TEST = """
            package de.tum.cit.aet.nodraft;

            import de.tum.in.test.api.jupiter.Public;
            import de.tum.in.test.api.StrictTimeout;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.*;

            @Public
            @StrictTimeout(1)
            class RosterParserTest {

                @Test
                void testValidInput() {
                    assertEquals("x", new RosterParser().formatRoster("a"), "parses");
                }
            }
            """;

    private static final ContractWitness NEGATIVE_SALARY = new ContractWitness("R1", "testWitnessNegativeSalaryIgnored", """
            @Test
            void testWitnessNegativeSalaryIgnored() {
                assertEquals("Total payroll: $0", new RosterParser().formatRoster("Alice|Dev|-5"), "a negative salary is an invalid record");
            }
            """);

    private static final ContractWitness BLANK_LINE = new ContractWitness("R1", "testWitnessBlankLineIgnored", """
            @Test
            void testWitnessBlankLineIgnored() {
                assertEquals("Total payroll: $0", new RosterParser().formatRoster("\\n\\n"), "blank lines are skipped");
            }
            """);

    @Test
    void buildProbeSource_carriesThePackageAndImportsOfTheSuiteItProbes() {
        String probe = ContractWitnessProbe.buildProbeSource(GENERATED_TEST, List.of(NEGATIVE_SALARY));

        assertThat(probe).startsWith("package de.tum.cit.aet.nodraft;").contains("import org.junit.jupiter.api.Test;").contains("import static org.junit.jupiter.api.Assertions.*;")
                .contains("class " + ContractWitnessProbe.PROBE_CLASS_NAME + " {").contains("void testWitnessNegativeSalaryIgnored()").endsWith("}\n");
    }

    @Test
    void buildProbeSource_doesNotCopyTheGradedSuitesHarnessAnnotations() {
        // The probe is a throwaway written next to the graded suite. Copying @Public/@StrictTimeout would make it look like a graded test to the production harness.
        String probe = ContractWitnessProbe.buildProbeSource(GENERATED_TEST, List.of(NEGATIVE_SALARY));

        assertThat(probe).doesNotContain("@Public").doesNotContain("@StrictTimeout");
    }

    @Test
    void buildProbeSource_carriesEveryWitnessAsItsOwnMethod() {
        String probe = ContractWitnessProbe.buildProbeSource(GENERATED_TEST, List.of(NEGATIVE_SALARY, BLANK_LINE));

        assertThat(probe).contains("void testWitnessNegativeSalaryIgnored()").contains("void testWitnessBlankLineIgnored()");
    }

    @Test
    void buildProbeSource_emptyWhenThereIsNothingToProbe() {
        assertThat(ContractWitnessProbe.buildProbeSource(GENERATED_TEST, List.of())).isEmpty();
    }

    @Test
    void validated_keepsOnlyTheWitnessesThatPassedOnTheReferenceSolution() {
        List<ContractWitness> validated = ContractWitnessProbe.validated(true, List.of("testWitnessBlankLineIgnored"), List.of(NEGATIVE_SALARY, BLANK_LINE));

        assertThat(validated).containsExactly(NEGATIVE_SALARY);
    }

    @Test
    void validated_attributesAFailureReportedInAnyOfTheUsualFormsToItsWitness() {
        // Report forms differ per framework; a witness must not be treated as passing merely because the runner spelled its failure differently.
        assertThat(ContractWitnessProbe.validated(true, List.of("testWitnessNegativeSalaryIgnored()"), List.of(NEGATIVE_SALARY))).isEmpty();
        assertThat(ContractWitnessProbe.validated(true, List.of("HyperionContractWitnessProbeTest.testWitnessNegativeSalaryIgnored"), List.of(NEGATIVE_SALARY))).isEmpty();
    }

    @Test
    void validated_provesNothingWhenTheSuiteNeverRan() {
        // A compile failure, a timeout and a crashed runner all report zero failures. Reading that as "the witness passed" would manufacture evidence from a broken build — the
        // precise mistake this mechanism exists to prevent.
        assertThat(ContractWitnessProbe.validated(false, List.of(), List.of(NEGATIVE_SALARY, BLANK_LINE))).isEmpty();
    }

    @Test
    void probePath_sitsBesideTheSuiteItBorrowedItsPackageFrom() {
        assertThat(ContractWitnessProbe.probePath("test/de/tum/cit/aet/nodraft/RosterParserTest.java"))
                .isEqualTo("test/de/tum/cit/aet/nodraft/" + ContractWitnessProbe.PROBE_CLASS_NAME + ".java");
    }

    @Test
    void probePath_undefinedForAPathWithNoDirectory() {
        assertThat(ContractWitnessProbe.probePath("RosterParserTest.java")).isNull();
    }
}
