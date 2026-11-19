package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.dto.SecurityTestStructureDTO;

/**
 * Tests that {@link AresTestCases} finds the test methods of a test class without compiling it.
 */
class AresTestCasesTest {

    private static final String TEST_CLASS = """
            package de.example;

            import org.junit.jupiter.api.Test;
            import org.junit.jupiter.params.ParameterizedTest;

            @de.tum.cit.ase.ares.api.jupiter.Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class SortingTest {

                // @Test void commentedOut() {}
                private final String text = "@Test void inAString() {}";

                @Test
                void testBubbleSort() {
                }

                @org.junit.jupiter.api.Test
                @org.junit.jupiter.api.DisplayName("Merge sort (big lists)")
                @Timeout(value = 5)
                public void testMergeSort() throws Exception {
                }

                @ParameterizedTest
                @ValueSource(ints = { 1, 2 })
                <T> void testWithParameter(int size) {
                }

                @de.tum.cit.ase.ares.api.jupiter.HiddenTest
                @StrictTimeout(1)
                java.util.List<String> testHidden() {
                    return null;
                }

                private void helper() {
                }
            }
            """;

    @Test
    void findsTheTestMethodsInTheOrderOfTheSource() {
        assertThat(AresTestCases.testCases(TEST_CLASS)).containsExactly("testBubbleSort", "testMergeSort", "testWithParameter", "testHidden");
    }

    @Test
    void listsEveryTestClassWithItsTestCasesAndTheOtherClassesAsHelpers() {
        String helper = "package de.example;\n\nclass SortingTestUtils {\n}\n";

        SecurityTestStructureDTO structure = AresTestCases.structure(Map.of("test/de/example/SortingTest.java", TEST_CLASS, "test/de/example/SortingTestUtils.java", helper));

        assertThat(structure.testClasses())
                .containsExactly(
                        new SecurityTestStructureDTO.TestClassDTO("de.example.SortingTest", List.of("testBubbleSort", "testMergeSort", "testWithParameter", "testHidden"), true,
                                false, List.of(), List.of()),
                        new SecurityTestStructureDTO.TestClassDTO("de.example.SortingTestUtils", List.of(), false, false, List.of(), List.of()));
    }

    @Test
    void findsTheFirstTestAnnotationOfEachTestMethod() {
        assertThat(AresTestCases.testAnnotationOffsets(TEST_CLASS)).containsEntry("testBubbleSort", TEST_CLASS.indexOf("@Test\n    void testBubbleSort"))
                .containsEntry("testMergeSort", TEST_CLASS.indexOf("@org.junit.jupiter.api.Test"));
    }

    @Test
    void readsCompleteMethodNamesAndFindsOverloadedTestCases() {
        String source = """
                class PrüfungTest {

                    @Test
                    void prüfe() {
                    }

                    @Test
                    void sortiereÜ() {
                    }

                    @Test
                    @Tag("slow")
                    void check() {
                    }

                    @ParameterizedTest
                    void check(int value) {
                    }
                }
                """;

        assertThat(AresTestCases.testCases(source)).containsExactly("prüfe", "sortiereÜ", "check");
        assertThat(AresTestCases.overloadedTestCases(source)).containsExactly("check");
    }

    @Test
    void readsTheSignaturesThatDecideWhetherASubclassOverridesATestMethod() {
        String source = """
                class SortingTest {

                    @Test
                    void check() {
                    }

                    @ParameterizedTest
                    @ValueSource(ints = { 1, 2 })
                    protected void check(@AggregateWith(Sizes.class) final java.util.Map<String, List<Integer>> sizes, int values[], String... names) {
                    }

                    public @Test void sorts(List<String> values) {
                    }
                }
                """;

        AresTestCases.TestSignature check = new AresTestCases.TestSignature("check", "check()", false);
        AresTestCases.TestSignature overload = new AresTestCases.TestSignature("check", "check(Map,int[],String[])", true);
        AresTestCases.TestSignature sorts = new AresTestCases.TestSignature("sorts", "sorts(List)", true);
        assertThat(AresTestCases.testSignatures(AresPolicyAnnotations.mask(source)).values()).containsExactlyInAnyOrder(check, overload, sorts);
    }

    @Test
    void ignoresAnnotationsWhoseNameOnlyStartsWithTheNameOfATestAnnotation() {
        String source = """
                class SortingTest {

                    @example.Test$Tag
                    void helper() {
                    }

                    @Testing
                    void otherHelper() {
                    }

                    @org.junit.jupiter.api.Test
                    void sorts() {
                    }
                }
                """;

        assertThat(AresTestCases.testCases(source)).containsExactly("sorts");
    }
}
