package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
            """, "accepts negative salary records");

    private static final ContractWitness BLANK_LINE = new ContractWitness("R1", "testWitnessBlankLineIgnored", """
            @Test
            void testWitnessBlankLineIgnored() {
                assertEquals("Total payroll: $0", new RosterParser().formatRoster("\\n\\n"), "blank lines are skipped");
            }
            """, "does not skip blank lines");

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
    void validated_keepsOnlyTheWitnessesThatRanAndPassedOnTheReferenceSolution() {
        List<ContractWitness> validated = ContractWitnessProbe.validated(List.of("testWitnessNegativeSalaryIgnored", "testWitnessBlankLineIgnored"),
                List.of("testWitnessBlankLineIgnored"), List.of(NEGATIVE_SALARY, BLANK_LINE));

        assertThat(validated).containsExactly(NEGATIVE_SALARY);
    }

    @Test
    void validated_attributesAResultReportedInAnyOfTheUsualFormsToItsWitness() {
        // Report forms differ per framework; attribution must survive all of them, in both directions.
        assertThat(ContractWitnessProbe.validated(List.of("testWitnessNegativeSalaryIgnored()"), List.of("testWitnessNegativeSalaryIgnored()"), List.of(NEGATIVE_SALARY)))
                .isEmpty();
        assertThat(ContractWitnessProbe.validated(List.of("HyperionContractWitnessProbeTest.testWitnessNegativeSalaryIgnored"), List.of(), List.of(NEGATIVE_SALARY)))
                .containsExactly(NEGATIVE_SALARY);
    }

    @Test
    void validated_rejectsAWitnessTheBuildNeverRan() {
        // The decisive case: absence from the failure list is ALSO satisfied by a witness that never executed — undiscovered by the runner, missing its annotation, disabled, or
        // in a probe class that failed to compile while the ordinary graded tests still ran and made the build look healthy. Silence is not evidence of passing.
        assertThat(ContractWitnessProbe.validated(List.of("testValidInput"), List.of(), List.of(NEGATIVE_SALARY, BLANK_LINE))).isEmpty();
    }

    @Test
    void validated_provesNothingWhenTheBuildReportedNoTestsAtAll() {
        assertThat(ContractWitnessProbe.validated(List.of(), List.of(), List.of(NEGATIVE_SALARY))).isEmpty();
    }

    @Test
    void discriminating_keepsOnlySolutionPassingWitnessesThatExecuteAndFailOnTheStarter() {
        List<ContractWitness> discriminating = ContractWitnessProbe.discriminating(List.of(NEGATIVE_SALARY, BLANK_LINE),
                List.of("HyperionContractWitnessProbeTest.testWitnessNegativeSalaryIgnored", "testWitnessBlankLineIgnored()"), List.of("testWitnessNegativeSalaryIgnored()"));

        assertThat(discriminating).containsExactly(NEGATIVE_SALARY);
    }

    @Test
    void discriminating_rejectsAStarterPassAndAWitnessTheStarterNeverRan() {
        assertThat(ContractWitnessProbe.discriminating(List.of(NEGATIVE_SALARY, BLANK_LINE), List.of("testWitnessNegativeSalaryIgnored"), List.of())).isEmpty();
    }

    @Test
    void probePath_sitsBesideTheSuiteItBorrowedItsPackageFrom() {
        assertThat(ContractWitnessProbe.probePath("test/de/tum/cit/aet/nodraft/RosterParserTest.java", Set.of("test/de/tum/cit/aet/nodraft/RosterParserTest.java")))
                .isEqualTo("test/de/tum/cit/aet/nodraft/" + ContractWitnessProbe.PROBE_CLASS_NAME + ".java");
    }

    @Test
    void probePath_refusesToOverwriteAGeneratedTestOfTheSameName() {
        // The name is distinctive, not reserved. Overwriting graded work would destroy it, and removing the probe afterwards would delete it.
        String taken = "test/de/tum/cit/aet/nodraft/" + ContractWitnessProbe.PROBE_CLASS_NAME + ".java";

        assertThat(ContractWitnessProbe.probePath("test/de/tum/cit/aet/nodraft/RosterParserTest.java", Set.of(taken))).isNull();
    }

    @Test
    void probePath_undefinedForAPathWithNoDirectory() {
        assertThat(ContractWitnessProbe.probePath("RosterParserTest.java", Set.of())).isNull();
    }

    @Test
    void host_skipsStructuralFactoriesThatCannotCompileAnOrdinaryWitness() {
        String structuralFactory = """
                package de.tum.cit.aet.nodraft;
                import org.junit.jupiter.api.TestFactory;
                class ClassTest {
                    @TestFactory Object generateTestsForAllClasses() { return null; }
                }
                """;

        assertThat(ContractWitnessProbe.host(Map.of("test/ClassTest.java", structuralFactory, "test/RosterParserTest.java", GENERATED_TEST))).get().extracting(Map.Entry::getKey)
                .isEqualTo("test/RosterParserTest.java");
    }
}
