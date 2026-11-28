package de.tum.cit.aet.artemis.sandbox.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests that {@link AresPolicyAnnotations} rewrites exactly the Ares {@code @Policy} annotations of a source file, and only their activation flag.
 */
class AresPolicyAnnotationsTest {

    /**
     * @return whether the source contains an Ares {@code @Policy} annotation that switches enforcement off
     */
    private static boolean isDeactivated(String source) {
        return AresPolicyAnnotations.find(source).stream().anyMatch(annotation -> !annotation.isActivated());
    }

    private static String source(String imports, String annotation) {
        return "package de.example;\n\n" + imports + "\n" + annotation + "\nclass SortingTest {\n}\n";
    }

    @Test
    void findsTheAnnotationBelowALineCommentInAFileWithCarriageReturnLineEndings() {
        String source = "package de.example;\r\r// The policy of the sorting tests\r@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\rclass SortingTest {\r}\r";

        assertThat(AresPolicyAnnotations.find(source)).singleElement().satisfies(annotation -> assertThat(annotation.policyPath()).contains("SecurityPolicy.yaml"));
        assertThat(AresPolicyAnnotations.mask(source)).hasSameSizeAs(source).contains("\r@de.tum.cit.ase.ares.api.Policy(value = \"                   \")\r");
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
    void maskingKeepsEveryOffset() {
        String original = "class A { String s = \"a(b\"; char c = ')'; /* ( */ // )\n}";

        assertThat(AresPolicyAnnotations.mask(original)).hasSameSizeAs(original).doesNotContain("a(b").contains("class A");
    }

    @ParameterizedTest
    @ValueSource(strings = { "class A { /* ( never closed", "class A { } // ( at the end", "class A { String s = \"( never closed", "class A { String s = \"(\\",
            "class A { /* (\n( */ }" })
    void masksCommentsAndLiteralsThatRunToTheEndOfTheFile(String original) {
        String masked = AresPolicyAnnotations.mask(original);

        assertThat(masked).hasSameSizeAs(original).startsWith("class A {").doesNotContain("(");
    }

    @Test
    void readsTheActivationFlagAndThePolicyPath() {
        AresPolicyAnnotations.Annotation withoutArguments = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy")).getFirst();
        AresPolicyAnnotations.Annotation activated = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy(activated = true)")).getFirst();

        assertThat(withoutArguments.isActivated()).isTrue();
        assertThat(withoutArguments.policyPath()).isEmpty();
        assertThat(activated.isActivated()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "./", "a\u0000b.yaml" })
    void keepsAPolicyPathAsWrittenIfItHasNoNormalizedForm(String path) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy(\"" + path + "\")")).getFirst();

        assertThat(annotation.isEvaluable()).isTrue();
        assertThat(annotation.policyPath()).contains(path);
    }

    @ParameterizedTest
    @ValueSource(strings = { "(value = \"SecurityPolicy.yaml\", withinPath = { \"src\", \"test\" })", "(value = \"SecurityPolicy.yaml\", /* nothing else */)" })
    void evaluatesTheArgumentsBesideArraysAndEmptyArguments(String arguments) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy" + arguments)).getFirst();

        assertThat(annotation.isEvaluable()).isTrue();
        assertThat(annotation.policyPath()).contains("SecurityPolicy.yaml");
    }

    @ParameterizedTest
    @ValueSource(strings = { "(value = 1)", "(value = \"SecurityPolicy\" + YAML)", "(value = PolicyFiles.NAMES[0])", "(activated = (true) == (false))" })
    void doesNotEvaluateNonLiteralExpressions(String arguments) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy" + arguments)).getFirst();

        assertThat(annotation.isEvaluable()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "class SortingTest {\n}\n@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\"",
            "class SortingTest {\n    java.lang.@Policy String name;\n}\n" })
    void findsNoAnnotationThatIsIncompleteOrPartOfATypeName(String body) {
        String original = "package de.example;\n\nimport de.tum.cit.ase.ares.api.*;\n\n" + body;

        assertThat(AresPolicyAnnotations.find(original)).isEmpty();
    }

    @Test
    void findsTheSimpleNameAtTheStartOfTheFile() {
        String original = "@Policy(\"SecurityPolicy.yaml\")\nclass SortingTest {\n}\nimport de.tum.cit.ase.ares.api.Policy;\n";

        assertThat(AresPolicyAnnotations.find(original)).singleElement().satisfies(annotation -> assertThat(annotation.start()).isZero());
    }

    @Test
    void findsAnAnnotationWithoutArgumentsAtTheEndOfTheFile() {
        String original = "package de.example;\n\nclass SortingTest {\n}\n@de.tum.cit.ase.ares.api.Policy  ";

        assertThat(AresPolicyAnnotations.find(original)).singleElement().satisfies(annotation -> assertThat(annotation.arguments()).isEmpty());
    }

    @Test
    void aNestedPolicyTypeDoesNotShadowTheAresAnnotationBeforeOrAfterItsBody() {
        String original = """
                package de.example;

                import de.tum.cit.ase.ares.api.*;

                @Policy("first")
                class SortingTest {

                    static class Helper {

                        void helps() {
                        }

                        @interface Policy {
                            String value();
                        }
                    }
                }

                @Policy("second")
                class OtherTest {
                """;

        assertThat(AresPolicyAnnotations.find(original)).extracting(annotation -> annotation.policyPath().orElseThrow()).containsExactly("first", "second");
    }

    @Test
    void aPolicyTypeInABodyThatIsNeverClosedShadowsTheRestOfTheFile() {
        String original = """
                package de.example;

                import de.tum.cit.ase.ares.api.*;

                class Helper {

                    @interface Policy {
                    }

                    @Policy("helper")
                    void helps() {
                """;

        assertThat(AresPolicyAnnotations.find(original)).isEmpty();
    }
}
