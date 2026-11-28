package de.tum.cit.aet.artemis.sandbox.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReasonType;

/**
 * Tests how {@link AresTestSources} reads the use of Ares from a single source file: which top-level type registers the sandbox, contains tests or carries a
 * policy, and which uses the Security Framework cannot manage.
 */
class AresTestSourcesTest {

    private static final String JUPITER_IMPORT = "import de.tum.cit.ase.ares.api.jupiter.*;\n";

    private static AresTestSources.Summary analyze(String source) {
        return AresTestSources.analyze("test/de/example/SortingTest.java", source);
    }

    @Test
    void aMethodMarkerRegistersTheSandboxAndMakesTheTypeATestClass() {
        AresTestSources.Summary summary = analyze("package de.example;\n" + JUPITER_IMPORT + """
                class Helper {
                }

                class SortingTest {

                    @PublicTest
                    void sorts() {
                    }
                }
                """);

        assertThat(summary.problems()).isEmpty();
        assertThat(AresTestSources.relevantType(summary)).hasValueSatisfying(type -> {
            assertThat(type.qualifiedName()).isEqualTo("de.example.SortingTest");
            assertThat(type.registersSandbox()).isTrue();
            assertThat(type.containsTests()).isTrue();
        });
    }

    @Test
    void aClassMarkerAloneRegistersTheSandboxWithoutTests() {
        AresTestSources.Summary summary = analyze("package de.example;\n\n@de.tum.cit.ase.ares.api.jupiter.Public\nclass SortingTest {\n}\n");

        assertThat(AresTestSources.relevantType(summary)).hasValueSatisfying(type -> {
            assertThat(type.registersSandbox()).isTrue();
            assertThat(type.containsTests()).isFalse();
        });
    }

    @Test
    void aPolicyAloneMakesATypeRelevant() {
        AresTestSources.Summary summary = analyze("package de.example;\n\n@de.tum.cit.ase.ares.api.Policy(\"SecurityPolicy.yaml\")\nclass SortingTest {\n}\n");

        assertThat(AresTestSources.relevantType(summary)).hasValueSatisfying(type -> {
            assertThat(type.policies()).hasSize(1);
            assertThat(type.registersSandbox()).isFalse();
            assertThat(type.containsTests()).isFalse();
        });
    }

    @Test
    void aFileWithoutPackageNamesItsTypesWithoutOne() {
        AresTestSources.Summary summary = analyze("class SortingTest extends de.example.BaseTest {\n\n    @org.junit.jupiter.api.Test\n    void sorts() {\n    }\n}\n");

        assertThat(summary.topLevelTypes()).singleElement().satisfies(type -> {
            assertThat(type.qualifiedName()).isEqualTo("SortingTest");
            assertThat(type.superclass()).containsExactly("de.example.BaseTest");
        });
    }

    @Test
    void aSimpleSuperclassNameRefersToItsSingleTypeImport() {
        AresTestSources.Summary summary = analyze("package de.example;\n\nimport java.util.List;\nimport de.base.BaseTest;\n\nclass SortingTest extends BaseTest {\n}\n");

        assertThat(summary.topLevelTypes()).singleElement().satisfies(type -> assertThat(type.superclass()).containsExactly("de.base.BaseTest"));
    }

    @Test
    void aBodyThatIsNeverClosedRunsToTheEndOfTheFile() {
        AresTestSources.Summary summary = analyze("package de.example;\n\nclass SortingTest {\n\n    @org.junit.jupiter.api.Test\n    void sorts() {\n    }\n");

        assertThat(AresTestSources.relevantType(summary)).hasValueSatisfying(type -> assertThat(type.containsTests()).isTrue());
    }

    @Test
    void aPolicyOnAnAnnotationTypeIsUnsupported() {
        AresTestSources.Summary summary = analyze("package de.example;\n\n@de.tum.cit.ase.ares.api.Policy(\"SecurityPolicy.yaml\")\n@interface Marker {\n}\n");

        assertThat(summary.problems()).containsExactly(UnsupportedReasonType.POLICY_ON_ANNOTATION_TYPE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "class SortingTest implements @de.tum.cit.ase.ares.api.Policy(\"SecurityPolicy.yaml\") Comparable<SortingTest> {\n}\n",
            "class SortingTest {\n}\n\n@de.tum.cit.ase.ares.api.Policy(\"SecurityPolicy.yaml\")\n" })
    void aPolicyThatAnnotatesNoTopLevelTypeIsUnsupported(String body) {
        assertThat(analyze("package de.example;\n\n" + body).problems()).containsExactly(UnsupportedReasonType.POLICY_NOT_ON_TOP_LEVEL_TYPE);
    }

    @ParameterizedTest
    @ValueSource(strings = { "class SortingTest {\n\n    @Public\n    static class Inner {\n    }\n}\n", "class SortingTest {\n}\n\n@Public\n",
            "class SortingTest {\n\n    static class Inner {\n\n        @HiddenTest\n        void sorts() {\n        }\n    }\n}\n",
            "class SortingTest {\n\n    static class Inner {\n\n        @org.junit.jupiter.api.Test\n        void sorts() {\n        }\n\n        @org.junit.jupiter.api.Test\n        void sortsAgain() {\n        }\n    }\n}\n" })
    void testsAndMarkersInANestedTypeOrOutsideATypeAreUnsupported(String body) {
        assertThat(analyze("package de.example;\n" + JUPITER_IMPORT + "\n" + body).problems()).containsExactly(UnsupportedReasonType.TESTS_IN_NESTED_TYPE);
    }

    @Test
    void aJUnitMarkerOutsideAnyTypeIsNoTest() {
        AresTestSources.Summary summary = analyze("package de.example;\n\nclass SortingTest {\n}\n\n@org.junit.jupiter.api.Test\n");

        assertThat(summary.problems()).isEmpty();
        assertThat(AresTestSources.relevantType(summary)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "@ExtendWith(de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class)",
            "import de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension;\n@ExtendWith(JupiterSecurityExtension.class)",
            JUPITER_IMPORT + "@ExtendWith(JupiterSecurityExtension.class)" })
    void registeringTheSecurityExtensionDirectlyIsUnsupported(String registration) {
        AresTestSources.Summary summary = analyze("package de.example;\n\n" + registration + "\nclass SortingTest {\n}\n");

        assertThat(summary.problems()).containsExactly(UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES);
    }

    @ParameterizedTest
    @ValueSource(strings = { "@ExtendWith(JupiterSecurityExtension.class)", JUPITER_IMPORT + "@ExtendWith(de.example.JupiterSecurityExtension.class)",
            JUPITER_IMPORT + "@ExtendWith(MyJupiterSecurityExtension.class)",
            JUPITER_IMPORT + "@ExtendWith(JupiterSecurityExtension.class)\nclass JupiterSecurityExtension {\n}\n" })
    void anotherExtensionOfTheSameNameIsNoRegistration(String registration) {
        AresTestSources.Summary summary = analyze("package de.example;\n\n" + registration + "\nclass SortingTest {\n}\n");

        assertThat(summary.problems()).isEmpty();
    }

    @Test
    void aMarkerInsideAQualifiedTypeNameDoesNotRegisterTheSandbox() {
        AresTestSources.Summary summary = analyze("package de.example;\n" + JUPITER_IMPORT + "\nclass SortingTest {\n\n    java.lang.@Public String name;\n}\n");

        assertThat(summary.problems()).isEmpty();
        assertThat(summary.topLevelTypes()).singleElement().satisfies(type -> assertThat(type.registersSandbox()).isFalse());
    }
}
