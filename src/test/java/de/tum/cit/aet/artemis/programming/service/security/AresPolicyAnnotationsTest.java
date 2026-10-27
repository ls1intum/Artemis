package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests that {@link AresPolicyAnnotations} rewrites exactly the Ares {@code @Policy} annotations of a source file, and only their activation flag.
 */
class AresPolicyAnnotationsTest {

    private static String source(String imports, String annotation) {
        return "package de.example;\n\n" + imports + "\n" + annotation + "\nclass SortingTest {\n}\n";
    }

    @ParameterizedTest
    @ValueSource(strings = { "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")", "@de.tum.cit.ase.ares.api.Policy(value = (\"SecurityPolicy.yaml\"))",
            "@de.tum.cit.ase.ares.api.Policy(value = \"a, b)\", withinPath = \"test\")", "@de.tum.cit.ase.ares.api.Policy(value = \"\"\"\n        x)\n        \"\"\")",
            "@de.tum.cit.ase.ares.api.Policy", "@de.tum.cit.ase.ares.api.Policy(/* policy file */ value = \"SecurityPolicy.yaml\")",
            "@de.tum.cit.ase.ares.api.Policy(value /* policy file */ = /* yaml */ \"SecurityPolicy.yaml\", /* scope */ withinPath = \"test\" /* tests */)",
            "@de.tum.cit.ase.ares.api.Policy(// policy file\n        value = \"SecurityPolicy.yaml\")" })
    void switchingOffAndOnRestoresTheAnnotationExactly(String annotation) {
        String original = source("", annotation);

        String deactivated = AresPolicyAnnotations.setActive(original, false);
        assertThat(AresPolicyAnnotations.containsDeactivated(deactivated)).isTrue();
        assertThat(AresPolicyAnnotations.setActive(deactivated, false)).isEqualTo(deactivated);

        assertThat(AresPolicyAnnotations.setActive(deactivated, true)).isEqualTo(original);
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

        assertThat(AresPolicyAnnotations.containsDeactivated(original)).isTrue();
        assertThat(AresPolicyAnnotations.setActive(original, false)).isEqualTo(original);
        assertThat(AresPolicyAnnotations.setActive(original, true)).contains("@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")").doesNotContain("activated");
    }

    @ParameterizedTest
    @ValueSource(strings = { "\"SecurityPolicy.yaml\"", "\"./SecurityPolicy.yaml\"", "(\"test/../SecurityPolicy.yaml\")", "\"././SecurityPolicy.yaml\"" })
    void normalizesThePolicyPathRelativeToTheRepositoryRoot(String value) {
        AresPolicyAnnotations.Annotation annotation = AresPolicyAnnotations.find(source("", "@de.tum.cit.ase.ares.api.Policy(value = " + value + ")")).getFirst();

        assertThat(annotation.policyPath()).contains("SecurityPolicy.yaml");
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
        assertThat(AresPolicyAnnotations.containsDeactivated(deactivated)).isTrue();
        assertThat(AresPolicyAnnotations.setActive(deactivated, true)).contains("@Policy(value = \"SecurityPolicy.yaml\")").doesNotContain("activated");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "import org.example.Policy;", "import de.tum.cit.ase.ares.api.*;\nimport org.example.Policy;" })
    void keepsOtherPolicyAnnotationsUntouched(String imports) {
        String original = source(imports, "@Policy(activated = false)");

        assertThat(AresPolicyAnnotations.setActive(original, true)).isEqualTo(original);
        assertThat(AresPolicyAnnotations.containsDeactivated(original)).isFalse();
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
        assertThat(AresPolicyAnnotations.containsDeactivated(original)).isFalse();
    }

    @Test
    void maskingKeepsEveryOffset() {
        String original = "class A { String s = \"a(b\"; char c = ')'; /* ( */ // )\n}";

        assertThat(AresPolicyAnnotations.mask(original)).hasSameSizeAs(original).doesNotContain("a(b").contains("class A");
    }
}
