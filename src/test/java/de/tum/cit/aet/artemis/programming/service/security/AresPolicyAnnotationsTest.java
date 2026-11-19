package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * Tests that {@link AresPolicyAnnotations} rewrites exactly the Ares {@code @Policy} annotations of a source file, and only their activation flag.
 */
class AresPolicyAnnotationsTest {

    private static final String TEST_CLASS = """
            package de.example;

            import de.tum.cit.ase.ares.api.Policy;
            import de.tum.cit.ase.ares.api.jupiter.Public;
            import org.junit.jupiter.api.Test;

            @Public
            @Policy(value = "SecurityPolicy.yaml", withinPath = "test")
            class SortingTest {

                @Test
                void testBubbleSort() {
                }

                @Policy(value = "Old.yaml", withinPath = "test")
                @Test
                void testMergeSort() {
                }

                @Test
                void testQuickSort() {
                }
            }
            """;

    /**
     * @return whether the source contains an Ares {@code @Policy} annotation that switches enforcement off
     */
    private static boolean isDeactivated(String source) {
        return AresPolicyAnnotations.find(source).stream().anyMatch(annotation -> !annotation.isActivated());
    }

    private static String source(String imports, String annotation) {
        return "package de.example;\n\n" + imports + "\n" + annotation + "\nclass SortingTest {\n}\n";
    }

    @ParameterizedTest
    @ValueSource(strings = { "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")", "@de.tum.cit.ase.ares.api.Policy(value = (\"SecurityPolicy.yaml\"))",
            "@de.tum.cit.ase.ares.api.Policy(value = \"a, b)\", withinPath = \"test\")", "@de.tum.cit.ase.ares.api.Policy(value = \"\"\"\n        x)\n        \"\"\")",
            "@de.tum.cit.ase.ares.api.Policy", "@de.tum.cit.ase.ares.api.Policy(/* policy file */ value = \"SecurityPolicy.yaml\")",
            "@de.tum.cit.ase.ares.api.Policy(value /* policy file */ = /* yaml */ \"SecurityPolicy.yaml\", /* scope */ withinPath = \"test\" /* tests */)",
            "@de.tum.cit.ase.ares.api.Policy(// policy file\n        value = \"SecurityPolicy.yaml\")", "@ de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")",
            "@/* Ares */ de . tum.cit.ase.ares.api.Policy", "@de.tum.cit.ase.ares.api.Policy /* course policy */ (value = \"SecurityPolicy.yaml\")" })
    void switchingOffAndOnRestoresTheAnnotationExactly(String annotation) {
        String original = source("", annotation);

        String deactivated = AresPolicyAnnotations.setActive(original, false);
        assertThat(isDeactivated(deactivated)).isTrue();
        assertThat(AresPolicyAnnotations.setActive(deactivated, false)).isEqualTo(deactivated);

        assertThat(AresPolicyAnnotations.setActive(deactivated, true)).isEqualTo(original);
    }

    @Test
    void keepsACommentBeforeTheArgumentListWhenTheLastArgumentIsRemoved() {
        String deactivated = source("", "@de.tum.cit.ase.ares.api.Policy // course policy\n(activated = false) @SuppressWarnings(\"unused\")");

        // Without the parentheses, the line comment would swallow the annotation after them.
        assertThat(AresPolicyAnnotations.setActive(deactivated, true)).contains("@de.tum.cit.ase.ares.api.Policy // course policy\n() @SuppressWarnings(\"unused\")");
    }

    @Test
    void findsTheAnnotationBelowALineCommentInAFileWithCarriageReturnLineEndings() {
        String source = "package de.example;\r\r// The policy of the sorting tests\r@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\rclass SortingTest {\r}\r";

        assertThat(AresPolicyAnnotations.find(source)).singleElement().satisfies(annotation -> assertThat(annotation.policyPath()).contains("SecurityPolicy.yaml"));
        assertThat(AresPolicyAnnotations.mask(source)).hasSameSizeAs(source).contains("\r@de.tum.cit.ase.ares.api.Policy(value = \"                   \")\r");
    }

    @Test
    void addsTheFlagAfterAParenthesizedValue() {
        String deactivated = AresPolicyAnnotations.setActive(source("", "@de.tum.cit.ase.ares.api.Policy(value = (\"SecurityPolicy.yaml\"))"), false);

        assertThat(deactivated).contains("@de.tum.cit.ase.ares.api.Policy(value = (\"SecurityPolicy.yaml\"), activated = false)");
    }

    @Test
    void readsArgumentsBehindComments() {
        String deactivated = AresPolicyAnnotations.setActive(source("", "@de.tum.cit.ase.ares.api.Policy(/* policy file */ value = \"SecurityPolicy.yaml\")"), false);

        assertThat(deactivated).contains("@de.tum.cit.ase.ares.api.Policy(/* policy file */ value = \"SecurityPolicy.yaml\", activated = false)");
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(deactivated).getFirst();
        assertThat(annotation.isActivated()).isFalse();
        assertThat(annotation.policyPath()).contains("SecurityPolicy.yaml");

        AresPolicyAnnotations.Annotation commentedFlag = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy(activated = /* off */ false)")).getFirst();
        assertThat(commentedFlag.isActivated()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "activated = (false)", "activated = ( /* off */ (false) )" })
    void recognizesAParenthesizedFlag(String flag) {
        String original = source("", "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\", " + flag + ")");

        assertThat(isDeactivated(original)).isTrue();
        assertThat(AresPolicyAnnotations.setActive(original, false)).isEqualTo(original);
        assertThat(AresPolicyAnnotations.setActive(original, true)).contains("@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")").doesNotContain("activated");
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"SecurityPolicy.yaml\"", "\"./SecurityPolicy.yaml\"", "(\"test/../SecurityPolicy.yaml\")", "\"././SecurityPolicy.yaml\"" })
    void normalizesThePolicyPathRelativeToTheRepositoryRoot(String value) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy(value = " + value + ")")).getFirst();

        assertThat(annotation.policyPath()).contains("SecurityPolicy.yaml");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "(\"SecurityPolicy.yaml\")", "(value = (\"SecurityPolicy.yaml\"))", "(value = /* file */ \"SecurityPolicy.yaml\", activated = (false))",
            "(activated = true)", "(value = \"SecurityPolicy.yaml\", withinPath = \"src\" + \"/main\")" })
    void evaluatesLiteralArguments(String arguments) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy" + arguments)).getFirst();

        assertThat(annotation.isEvaluable()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "(value = \"Security\" + \"Policy.yaml\")", "(activated = !true)", "(value = PolicyFiles.DEFAULT)", "(activated = Flags.OFF)",
            "(value = \"\"\"\n        SecurityPolicy.yaml\"\"\")", "(value = \"Security\\u0050olicy.yaml\")" })
    void doesNotEvaluateOtherExpressions(String arguments) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy" + arguments)).getFirst();

        assertThat(annotation.isEvaluable()).isFalse();
    }

    @Test
    void neverAppendsTheFlagBehindALineComment() {
        String deactivated = AresPolicyAnnotations.setActive(source("", "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\" // policy file\n)"), false);

        assertThat(deactivated).contains("@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\", activated = false)");
    }

    @ParameterizedTest
    @ValueSource(strings = { "import de.tum.cit.ase.ares.api.Policy;", "import de.tum.cit.ase.ares.api.*;" })
    void rewritesTheSimpleNameIfItRefersToAres(String imports) {
        String deactivated = AresPolicyAnnotations.setActive(source(imports, "@Policy(\"SecurityPolicy.yaml\")"), false);

        assertThat(deactivated).contains("@Policy(value = \"SecurityPolicy.yaml\", activated = false)");
        assertThat(isDeactivated(deactivated)).isTrue();
        assertThat(AresPolicyAnnotations.setActive(deactivated, true)).contains("@Policy(value = \"SecurityPolicy.yaml\")").doesNotContain("activated");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "import org.example.Policy;", "import de.tum.cit.ase.ares.api.*;\nimport org.example.Policy;",
            "import de.tum.cit.ase.ares.api.*;\nimport com.bücher.Policy;" })
    void keepsOtherPolicyAnnotationsUntouched(String imports) {
        String original = source(imports, "@Policy(activated = false)");

        assertThat(AresPolicyAnnotations.setActive(original, true)).isEqualTo(original);
        assertThat(isDeactivated(original)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "@Policy$Tag(\"metadata\")", "@PolicyÜ(\"metadata\")", "@de.tum.cit.ase.ares.api.Policy$Tag(\"metadata\")" })
    void leavesAnAnnotationAloneWhoseNameOnlyStartsWithPolicy(String annotation) {
        String original = source("import de.tum.cit.ase.ares.api.*;", annotation);

        assertThat(AresPolicyAnnotations.setActive(original, false)).isEqualTo(original);
        assertThat(AresPolicyAnnotations.setActive(original, true)).isEqualTo(original);
    }

    @Test
    void leavesAPolicyAnnotationAloneThatTheFileDeclaresItself() {
        // The declared annotation shadows the Ares one that the wildcard import provides, so adding the flag would break the file.
        String original = """
                package de.example;

                import de.tum.cit.ase.ares.api.*;

                @interface Policy {
                    String value();
                }

                @Policy("SecurityPolicy.yaml")
                class SortingTest {
                }
                """;

        assertThat(AresPolicyAnnotations.setActive(original, false)).isEqualTo(original);
        assertThat(isDeactivated(original)).isFalse();
    }

    @Test
    void aNestedPolicyTypeShadowsTheAresAnnotationOnlyWithinItsBody() {
        String original = """
                package de.example;

                import de.tum.cit.ase.ares.api.*;

                @Policy("SecurityPolicy.yaml")
                class SortingTest {

                    static class Helper {

                        @interface Policy {
                            String value();
                        }

                        @Policy("helper")
                        static class Inner {
                        }
                    }
                }
                """;

        // Only the annotation on SortingTest refers to Ares; the one inside Helper refers to Helper.Policy.
        assertThat(AresPolicyAnnotations.find(original)).singleElement().satisfies(annotation -> assertThat(annotation.policyPath()).contains("SecurityPolicy.yaml"));
    }

    @Test
    void ignoresAnnotationsInCommentsAndStrings() {
        String original = """
                package de.example;

                // @de.tum.cit.ase.ares.api.Policy(value = "commented")
                /* @de.tum.cit.ase.ares.api.Policy(activated = false) */
                class SortingTest {
                    String text = "@de.tum.cit.ase.ares.api.Policy(value = \\"string\\")";
                }
                """;

        assertThat(AresPolicyAnnotations.setActive(original, false)).isEqualTo(original);
        assertThat(AresPolicyAnnotations.setActive(original, true)).isEqualTo(original);
        assertThat(isDeactivated(original)).isFalse();
    }

    @Test
    void maskingKeepsEveryOffset() {
        String original = "class A { String s = \"a(b\"; char c = ')'; /* ( */ // )\n}";

        assertThat(AresPolicyAnnotations.mask(original)).hasSameSizeAs(original).doesNotContain("a(b").contains("class A");
    }

    @Test
    void pointsAnAnnotationToAnotherPolicyFileOrToNoneAndKeepsItsOtherArguments() {
        String original = """
                package de.example;

                @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml", /* kept */ activated = true)
                class SortingTest {
                }
                """;
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(original).getFirst();

        assertThat(AresPolicyAnnotations.withPolicyPath(annotation, "StressTests.yaml"))
                .isEqualTo("@de.tum.cit.ase.ares.api.Policy(value = \"StressTests.yaml\", /* kept */ activated = true)");
        assertThat(AresPolicyAnnotations.withPolicyPath(annotation, null)).isEqualTo("@de.tum.cit.ase.ares.api.Policy(/* kept */ activated = true)");
    }

    @Test
    void keepsAnUnnamedValueUnnamedAndAddsAMissingValue() {
        AresPolicyAnnotations.Annotation unnamed = AresPolicyAnnotations.find(source("import de.tum.cit.ase.ares.api.Policy;", "@Policy(\"SecurityPolicy.yaml\")")).getFirst();
        AresPolicyAnnotations.Annotation withoutValue = AresPolicyAnnotations.find(source("import de.tum.cit.ase.ares.api.Policy;", "@Policy(activated = false)")).getFirst();

        assertThat(AresPolicyAnnotations.withPolicyPath(unnamed, "policies/Stress.yml")).isEqualTo("@Policy(\"policies/Stress.yml\")");
        assertThat(AresPolicyAnnotations.withPolicyPath(unnamed, null)).isEqualTo("@Policy");
        assertThat(AresPolicyAnnotations.withPolicyPath(withoutValue, "Stress.yaml")).isEqualTo("@Policy(value = \"Stress.yaml\", activated = false)");
    }

    @ParameterizedTest
    @ValueSource(strings = { "Stress\"Tests.yaml", "Stress\\Tests.yaml" })
    void refusesAPathThatIsNoPlainStringLiteral(String policyPath) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("import de.tum.cit.ase.ares.api.Policy;", "@Policy(\"SecurityPolicy.yaml\")")).getFirst();

        assertThatIllegalArgumentException().isThrownBy(() -> AresPolicyAnnotations.withPolicyPath(annotation, policyPath));
    }

    @Test
    void assignsSingleTestCasesWithMethodAnnotations() {
        String assigned = AresPolicyAnnotations.assign("test/de/example/SortingTest.java", TEST_CLASS, null,
                Map.of("testBubbleSort", "Stress.yaml", "testMergeSort", "SecurityPolicy.yaml"), Set.of());

        assertThat(assigned).contains("@Public\n@Policy(withinPath = \"test\")\nclass SortingTest {")
                .contains("    @Policy(value = \"Stress.yaml\", withinPath = \"test\")\n    @Test\n    void testBubbleSort()")
                .contains("    @Policy(value = \"SecurityPolicy.yaml\", withinPath = \"test\")\n    @Test\n    void testMergeSort()")
                .contains("    }\n\n    @Test\n    void testQuickSort()");
        AresTestSources.TypeDeclaration type = AresTestSources.analyze("test/de/example/SortingTest.java", assigned).topLevelTypes().getFirst();
        assertThat(type.methodPolicies()).containsOnlyKeys("testBubbleSort", "testMergeSort");
        assertThat(AresTestSources.analyze("test/de/example/SortingTest.java", assigned).problems()).isEmpty();
    }

    @Test
    void keepsTheLinesOfAFileWithCarriageReturnLineEndings() {
        String source = TEST_CLASS.replace("\n", "\r");
        Map<String, String> methods = Map.of("testBubbleSort", "Stress.yaml", "testMergeSort", "SecurityPolicy.yaml");

        String assigned = AresPolicyAnnotations.assign("test/de/example/SortingTest.java", source, null, methods, Set.of());
        String wholeClass = AresPolicyAnnotations.assign("test/de/example/SortingTest.java", source, "Stress.yaml", Map.of(), Set.of());

        assertThat(assigned).contains("    @Policy(value = \"Stress.yaml\", withinPath = \"test\")\r    @Test\r    void testBubbleSort()").doesNotContain("\n");
        // Removing the annotation of testMergeSort removes its whole line, as in a file with line feeds.
        String expected = AresPolicyAnnotations.assign("test/de/example/SortingTest.java", TEST_CLASS, "Stress.yaml", Map.of(), Set.of()).replace("\n", "\r");
        assertThat(wholeClass).isEqualTo(expected);
    }

    @Test
    void anAnnotationOnAMethodThatIsNoTestIsNotSupported() {
        String helper = "    @Policy(\"Missing.yaml\")\n    void helper() {\n    }\n\n";
        String source = TEST_CLASS.replace("    @Test\n    void testQuickSort()", helper + "    @Test\n    void testQuickSort()");

        AresTestSources.Summary summary = AresTestSources.analyze("test/de/example/SortingTest.java", source);

        assertThat(summary.problems()).containsExactly(SecurityFrameworkState.UnsupportedReasonType.POLICY_NOT_ON_TOP_LEVEL_TYPE);
        assertThat(summary.topLevelTypes().getFirst().methodPolicies()).containsOnlyKeys("testMergeSort");
    }

    @Test
    void assigningTheWholeClassRemovesTheMethodAnnotations() {
        String assigned = AresPolicyAnnotations.assign("test/de/example/SortingTest.java", TEST_CLASS, "Stress.yaml", Map.of(), Set.of());

        assertThat(assigned).contains("@Policy(value = \"Stress.yaml\", withinPath = \"test\")\nclass SortingTest {").doesNotContain("Old.yaml")
                .contains("    }\n\n    @Test\n    void testMergeSort()");
        assertThat(AresPolicyAnnotations.assign("test/de/example/SortingTest.java", assigned, "Stress.yaml", Map.of(), Set.of())).isEqualTo(assigned);
    }

    @Test
    void keepsTheOverridesOfTestCasesWithoutAPolicyFile() {
        String source = TEST_CLASS.replace("@Policy(value = \"Old.yaml\", withinPath = \"test\")", "@Policy(activated = false)");

        String assigned = AresPolicyAnnotations.assign("test/de/example/SortingTest.java", source, "Stress.yaml", Map.of(), Set.of("testMergeSort"));

        assertThat(assigned).contains("@Policy(value = \"Stress.yaml\", withinPath = \"test\")\nclass SortingTest {")
                .contains("    @Policy(activated = false)\n    @Test\n    void testMergeSort()");
    }
}
