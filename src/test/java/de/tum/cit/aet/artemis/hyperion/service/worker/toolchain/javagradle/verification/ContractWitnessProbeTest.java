package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic.ContractWitness;

class ContractWitnessProbeTest {

    @Test
    void adoptedWitnessRenamesOnlyItsDeclarationAndAvoidsExistingProbeNames() {
        String code = """
                // void retained() must remain untouched here.
                @Test void retained() { assertEquals("void retained()", "void retained()"); }
                """;
        ContractWitness witness = new ContractWitness("R1", "retained", code, "counterexample");
        var execution = ContractWitnessProbe.forExecution(witness, Map.of("Test.java", "class Test { void retained() {} void retainedHyperionProbe() {} }"));

        assertThat(execution.testName()).isEqualTo("retainedHyperionProbe2");
        assertThat(execution.code()).isEqualTo("""
                // void retained() must remain untouched here.
                @Test void retainedHyperionProbe2() { assertEquals("void retained()", "void retained()"); }
                """);
        assertThat(execution.ruleId()).isEqualTo(witness.ruleId());
        assertThat(execution.wrongBehavior()).isEqualTo(witness.wrongBehavior());
        assertThat(witness.code()).isEqualTo(code);
    }

    @Test
    void noncollidingWitnessKeepsItsNameAndIgnoresCommentAndLiteralMatches() {
        ContractWitness witness = new ContractWitness("R1", "retained", "@Test void retained() { assertTrue(true); }", "counterexample");

        assertThat(ContractWitnessProbe.forExecution(witness, Map.of("Test.java", "// void retained() {}\nclass Test { String text = \"void retained()\"; }"))).isSameAs(witness);
    }

    @Test
    void collidingWitnessWithoutOneUnambiguousDeclarationIsRejected() {
        for (String code : List.of("@Test void another() {}", "@Test void retained() {} void retained() {}")) {
            ContractWitness witness = new ContractWitness("R1", "retained", code, "counterexample");
            assertThatThrownBy(() -> ContractWitnessProbe.forExecution(witness, Map.of("Test.java", "class Test { void retained() {} }")))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    /** Shaped like a real generated suite: Ares harness annotations on the class, JUnit and static assertion imports. */
    private static final String GENERATED_TEST = """
            package de.tum.cit.aet.nodraft;

            import de.tum.cit.ase.ares.api.jupiter.Public;
            import de.tum.cit.ase.ares.api.StrictTimeout;
            import de.tum.cit.ase.ares.api.Policy;
            import org.junit.jupiter.api.Test;

            import static org.junit.jupiter.api.Assertions.*;

            @Public
            @Policy(value = "SecurityPolicy.yaml")
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
    void buildProbeSourceUsesExplicitAresRestrictionsWithoutDependingOnHostImports() {
        String probe = ContractWitnessProbe.buildProbeSource(GENERATED_TEST, List.of(NEGATIVE_SALARY));

        assertThat(probe).contains("@de.tum.cit.ase.ares.api.jupiter.Public", "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")",
                "@de.tum.cit.ase.ares.api.StrictTimeout(15)", "@org.junit.jupiter.api.DisplayNameGeneration(org.junit.jupiter.api.DisplayNameGenerator.Simple.class)");
        assertThat(JavaSourceInspector.javaTestAnnotationSummary(probe)).isEqualTo(new JavaSourceInspector.JavaTestAnnotationSummary(true, false, false, false));
    }

    @Test
    void workspaceOverlayRegistersOnlyTheTemporaryInstructorClassAndPreservesTheCandidate() {
        String policy = """
                regardingTheSupervisedCode:
                  theSupervisedCodeUsesTheFollowingPackage: "de.tum.cit.aet.nodraft"
                  theFollowingClassesAreTestClasses:
                    - "de.tum.cit.aet.nodraft.RosterParserTest"
                  theFollowingResourceAccessesArePermitted:
                    regardingNetworkConnections: [ ]
                """;
        String hostPath = "test/de/tum/cit/aet/nodraft/RosterParserTest.java";
        Map<String, String> candidate = Map.of(hostPath, GENERATED_TEST, "SecurityPolicy.yaml", policy);
        String path = ContractWitnessProbe.probePath(hostPath, candidate.keySet());
        String source = ContractWitnessProbe.buildProbeSource(GENERATED_TEST, List.of(NEGATIVE_SALARY));

        Map<String, String> overlay = ContractWitnessProbe.workspaceOverlay(candidate, path, source);

        assertThat(overlay).containsOnlyKeys("tests/" + path, "tests/SecurityPolicy.yaml");
        assertThat(overlay.get("tests/" + path)).isEqualTo(source);
        assertThat(overlay.get("tests/SecurityPolicy.yaml")).contains("    - \"de.tum.cit.aet.nodraft.HyperionContractWitnessProbeTest\"\n",
                "    - \"de.tum.cit.aet.nodraft.RosterParserTest\"\n");
        assertThat(AresSecurityPolicy.onlyTestClassesChanged(policy, overlay.get("tests/SecurityPolicy.yaml"))).isTrue();
        assertThat(candidate).containsExactlyInAnyOrderEntriesOf(Map.of(hostPath, GENERATED_TEST, "SecurityPolicy.yaml", policy));
    }

    @Test
    void workspaceOverlayRejectsAMissingPolicyOrAnExistingProbe() {
        String path = "test/example/HyperionContractWitnessProbeTest.java";
        assertThatThrownBy(() -> ContractWitnessProbe.workspaceOverlay(Map.of(), path, "probe")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires the candidate's Ares 2 security policy");
        assertThatThrownBy(() -> ContractWitnessProbe.workspaceOverlay(Map.of(path, "existing"), path, "probe")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overwrite");
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
        assertThat(ContractWitnessProbe.validated(List.of("testWitnessNegativeSalaryIgnored()"), List.of("testWitnessNegativeSalaryIgnored()"), List.of(NEGATIVE_SALARY)))
                .isEmpty();
        assertThat(ContractWitnessProbe.validated(List.of("HyperionContractWitnessProbeTest.testWitnessNegativeSalaryIgnored"), List.of(), List.of(NEGATIVE_SALARY)))
                .containsExactly(NEGATIVE_SALARY);
    }

    @Test
    void validated_rejectsAWitnessTheBuildNeverRan() {
        // Absence from the failure list is also satisfied by a witness that never executed (undiscovered, disabled, or in a probe class that failed to compile while the graded
        // tests still ran), so silence is not evidence of passing.
        assertThat(ContractWitnessProbe.validated(List.of("testValidInput"), List.of(), List.of(NEGATIVE_SALARY, BLANK_LINE))).isEmpty();
    }

    @Test
    void witnessNameCollisionWithAnExistingGradedTestIsDetectedBeforeAttribution() {
        ContractWitness collision = new ContractWitness("R1", "testValidInput", "@Test void testValidInput() { fail(); }", "collides");

        assertThat(ContractWitnessProbe.collidesWithExistingTest(collision, Map.of("test/RosterParserTest.java", GENERATED_TEST))).isTrue();
        assertThat(ContractWitnessProbe.collidesWithExistingTest(NEGATIVE_SALARY, Map.of("test/RosterParserTest.java", GENERATED_TEST))).isFalse();
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
        // The name is distinctive, not reserved: removing the probe afterwards would delete the graded test it overwrote.
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
