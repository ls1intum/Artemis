package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                import java.util.List;

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

                    protected @ValueSource(ints = { 1 }) @ParameterizedTest void sizes(int size) {
                    }
                }
                """;

        AresTestCases.TestSignature check = new AresTestCases.TestSignature("check", "check()", false, Set.of());
        // java starts a package, which a member type named java in scope of a class would change, like an import.
        AresTestCases.TestSignature overload = new AresTestCases.TestSignature("check", "check(java.util.Map,int[],?String[])", true, Set.of("java"));
        // List is resolved only through its import, which a member type inherited by a subclass would shadow.
        AresTestCases.TestSignature sorts = new AresTestCases.TestSignature("sorts", "sorts(java.util.List)", true, Set.of("List"));
        // The braces in the arguments of an annotation end no member, so the modifier before it still counts.
        AresTestCases.TestSignature sizes = new AresTestCases.TestSignature("sizes", "sizes(int)", true, Set.of());
        assertThat(AresTestCases.testSignatures(AresPolicyAnnotations.mask(source)).values()).containsExactlyInAnyOrder(check, overload, sorts, sizes);
    }

    @Test
    void aDottedNameWhoseFirstNameAnImportGivesATypeIsResolved() {
        String source = """
                import models.outer;

                class SortingTest {

                    @Test
                    public void runs(outer.Inner value) {
                    }

                    @Test
                    public void sorts(java.util.List<String> values) {
                    }
                }
                """;

        // outer is the imported type models.outer, although its name is in lower case like a package, so outer.Inner is models.outer.Inner.
        AresTestCases.TestSignature runs = new AresTestCases.TestSignature("runs", "runs(models.outer.Inner)", true, Set.of("outer"));
        AresTestCases.TestSignature sorts = new AresTestCases.TestSignature("sorts", "sorts(java.util.List)", true, Set.of("java"));
        assertThat(AresTestCases.testSignatures(AresPolicyAnnotations.mask(source)).values()).containsExactlyInAnyOrder(runs, sorts);
    }

    @Test
    void aLowerCaseNameStartsAPackageOnlyIfNoTypeOfTheFileInScopeHasIt() {
        String source = """
                class SortingTest {

                    @Test
                    public void runs(a.Inner value) {
                    }

                    static class Nested {

                        static class a {

                            static class Inner {
                            }
                        }

                        @Test
                        public void runs(a.Inner value) {
                        }
                    }
                }
                """;

        // In SortingTest, a.Inner is the type Inner of the package a, which an inherited member type a would change. In Nested, the member type a shadows the
        // package, so a.Inner names a type of the file, not the package a.
        AresTestCases.TestSignature inPackage = new AresTestCases.TestSignature("runs", "runs(a.Inner)", true, Set.of("a"));
        AresTestCases.TestSignature memberType = new AresTestCases.TestSignature("runs", "runs(?a.Inner)", true, Set.of());
        assertThat(AresTestCases.testSignatures(AresPolicyAnnotations.mask(source)).values()).containsExactlyInAnyOrder(inPackage, memberType);
    }

    @Test
    void findsTestMethodsBehindAnnotationsWithNamesOutsideAscii() {
        String source = """
                class SortingTest {

                    @Test
                    @Größe @dé.Ä(1)
                    void sorts() {
                    }
                }
                """;

        assertThat(AresTestCases.testCases(source)).containsExactly("sorts");
    }

    @ParameterizedTest
    @ValueSource(strings = { "int[][] values", "int values[][]", "int values[] []", "int[] values []" })
    void readsArrayDimensionsAfterTheNameAlsoWithWhitespaceBetweenThem(String parameter) {
        String source = "class SortingTest {\n\n    @Test\n    void runs(" + parameter + ") {\n    }\n}\n";

        assertThat(AresTestCases.testSignatures(AresPolicyAnnotations.mask(source)).values()).extracting(AresTestCases.TestSignature::text).containsExactly("runs(int[][])");
    }

    @Test
    void offersOnlyTheTestMethodsOfTheClassItselfAndNotThoseOfANestedGroup() {
        String source = """
                class SortingTest {

                    @net.jqwik.api.Example
                    void runs() {
                    }

                    @net.jqwik.api.Group
                    class Edges {

                        @net.jqwik.api.Example
                        void sorts() {
                        }
                    }
                }
                """;

        // A @Policy on the method of the group would not be accepted, so it is no test case to assign.
        assertThat(AresTestCases.testCases(source)).containsExactly("runs");
        assertThat(AresTestCases.testAnnotationOffsets(source)).containsOnlyKeys("runs");
    }

    @Test
    void anExplicitReceiverParameterIsNoPartOfTheSignature() {
        String source = """
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Target;
                import java.util.Date;

                class SortingTest {

                    // Only a type annotation may annotate the receiver.
                    @Target(ElementType.TYPE_USE)
                    @interface Checked {
                    }

                    @Test
                    public void runs(@Checked SortingTest this, Date date) {
                    }
                }
                """;

        AresTestCases.TestSignature runs = new AresTestCases.TestSignature("runs", "runs(java.util.Date)", true, Set.of("Date"));
        assertThat(AresTestCases.testSignatures(AresPolicyAnnotations.mask(source)).values()).containsExactly(runs);
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

    @ParameterizedTest
    @ValueSource(strings = { "import example.metadata.Test;", "import static example.Markers.Test;" })
    void aTestAnnotationThatAnImportGivesToAnotherTypeMarksNoTest(String testImport) {
        String source = testImport + """


                class SortingTest {

                    @Test
                    void helper() {
                    }

                    @org.junit.jupiter.api.Test
                    void sorts() {
                    }
                }
                """;

        // The single-static import binds Test to the member type example.Markers.Test, like a single-type import.
        assertThat(AresTestCases.testCases(source)).containsExactly("sorts");
    }

    @Test
    void readsTheTypesOfParametersWrittenWithoutOptionalWhitespace() {
        String source = "class SortingTest<T > {\n\n    @Test\n    void runs(T value) {\n    }\n\n    @Test\n    void sorts(java.util.List<String>values) {\n    }\n}\n";

        // The type parameter erases to Object also with whitespace before >, and the name may follow the type arguments directly.
        assertThat(AresTestCases.testSignatures(AresPolicyAnnotations.mask(source)).values()).extracting(AresTestCases.TestSignature::text)
                .containsExactlyInAnyOrder("runs(java.lang.Object)", "sorts(java.util.List)");
    }

}
