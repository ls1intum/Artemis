package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

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
    void listsEveryTestClassWithItsTestCases() {
        SecurityTestStructureDTO structure = AresTestCases.structure(Map.of("test/de/example/SortingTest.java", TEST_CLASS),
                Set.of("de.example.SortingTest", "de.example.InheritedTest"));

        assertThat(structure.testClasses()).containsExactly(new SecurityTestStructureDTO.TestClassDTO("de.example.InheritedTest", List.of()),
                new SecurityTestStructureDTO.TestClassDTO("de.example.SortingTest", List.of("testBubbleSort", "testMergeSort", "testWithParameter", "testHidden")));
    }
}
