package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the lexical machinery {@link ExerciseIntegrityGate} reads Java sources with. Only the properties a caller silently depends on are pinned here —
 * the ones a plausible "simplify this into a regex" rewrite would break without failing any gate test: literal-aware comment stripping, line-structure preservation, annotation
 * blocks that span lines, and per-element XML extraction. The gate's own rejection messages stay covered by {@link ExerciseIntegrityGateTest}.
 */
class JavaSourceInspectorTest {

    @ParameterizedTest
    @ValueSource(strings = { "\n", "\r", "\r\n" })
    void stripJavaComments_preservesJavaLineTerminators(String newline) {
        String source = "// hidden" + newline + "class Visible {} /* hidden" + newline + "hidden */";

        assertThat(JavaSourceInspector.stripJavaComments(source)).isEqualTo("         " + newline + "class Visible {}          " + newline + "         ");
        assertThat(JavaSourceInspector.sourceDeclaresType(source, "Visible")).isTrue();
    }

    @Test
    void stripJavaComments_preservesTextBlocksAndRemovesFollowingComments() {
        String literal = String.join("\n", "\"\"\"", "a single \" followed by // literal text", "/* also literal */", "an escaped \\\"\"\" is not the end", "\"\"\"");
        String source = "String value = " + literal + "; // removed\n/* removed too */ class Visible {}";

        assertThat(JavaSourceInspector.stripJavaComments(source)).isEqualTo("String value = " + literal + ";           \n                  class Visible {}");
    }

    @Test
    void stripJavaComments_keepsSlashesInsideStringAndCharacterLiterals() {
        // A regex-based stripper eats from the // in the URL to the end of the line, silently deleting the code that follows on it.
        String source = """
                String endpoint = "https://example.org/api"; String kept = "after";
                char quote = '"'; String stillCode = "not a comment";
                String escaped = "he said \\" // not a comment"; int x = 1;
                """;

        String stripped = JavaSourceInspector.stripJavaComments(source);

        assertThat(stripped).contains("https://example.org/api").contains("String kept = \"after\"").contains("String stillCode = \"not a comment\"").contains("int x = 1");
    }

    @Test
    void stripJavaComments_removesCommentBodiesWithoutMovingAnyLine() {
        // javaTestAnnotationSummary attributes a method to its enclosing class by line index, so a stripper that collapsed a block comment would misattribute later methods.
        String source = """
                class A {
                    /* first
                       second
                       third */
                    void run() {} // trailing
                }
                """;

        String stripped = JavaSourceInspector.stripJavaComments(source);

        assertThat(stripped.split("\n", -1)).hasSameSizeAs(source.split("\n", -1));
        assertThat(stripped).doesNotContain("second").doesNotContain("trailing").contains("void run() {}");
        assertThat(stripped.split("\n", -1)[4]).startsWith("    void run() {}");
    }

    @Test
    void javaTestAnnotationSummary_readsAnAnnotationWhoseArgumentListSpansLines() {
        // Line-at-a-time reading would see "@StrictTimeout(" with no argument and reject a valid bound.
        String source = """
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;
                import org.junit.jupiter.api.Test;

                @Public
                @WhitelistPath("build")
                @BlacklistPath("build/classes/java/test")
                class StackTest {

                    @StrictTimeout(
                            2)
                    @Test
                    void pushes() {
                    }
                }
                """;

        JavaSourceInspector.JavaTestAnnotationSummary summary = JavaSourceInspector.javaTestAnnotationSummary(source);

        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isFalse();
        assertThat(summary.classWithMissingAresAnnotations()).isFalse();
    }

    @Test
    void javaTestAnnotationSummary_attributesEachMethodToTheClassItIsDeclaredIn() {
        // Two top-level classes in one file: the trusted annotations on the first must not vouch for a test method in the second.
        String source = """
                import de.tum.in.test.api.BlacklistPath;
                import de.tum.in.test.api.StrictTimeout;
                import de.tum.in.test.api.WhitelistPath;
                import de.tum.in.test.api.jupiter.Public;
                import org.junit.jupiter.api.Test;

                @Public
                @WhitelistPath("build")
                @BlacklistPath("build/classes/java/test")
                class GuardedTest {

                    @StrictTimeout(1)
                    @Test
                    void guarded() {
                    }
                }

                @SuppressWarnings("unused")
                class UnguardedTest {

                    @StrictTimeout(1)
                    @Test
                    void unguarded() {
                    }
                }
                """;

        JavaSourceInspector.JavaTestAnnotationSummary summary = JavaSourceInspector.javaTestAnnotationSummary(source);

        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isFalse();
    }

    @Test
    void unannotatedClassCannotBorrowRestrictionsOrTimeoutFromEarlierClass() {
        String source = """
                @de.tum.in.test.api.jupiter.Public
                @de.tum.in.test.api.WhitelistPath("build")
                @de.tum.in.test.api.BlacklistPath("build/classes/java/test")
                @de.tum.in.test.api.StrictTimeout(1)
                class Decoy {}
                class Actual {
                    @org.junit.jupiter.api.Test
                    void runs() {}
                }
                """;
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isTrue();
    }

    @Test
    void completedNestedClassCannotVouchForOuterMethods() {
        String source = """
                class Outer {
                    @de.tum.in.test.api.jupiter.Public
                    @de.tum.in.test.api.WhitelistPath("build")
                    @de.tum.in.test.api.BlacklistPath("build/classes/java/test")
                    @de.tum.in.test.api.StrictTimeout(1)
                    class Decoy {
                        String misleading = "{{{{";
                    }
                    @org.junit.jupiter.api.Test
                    void runs() {}
                }
                """;
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isTrue();
    }

    @Test
    void sameLineTestMethodStillRequiresRestrictionsAndTimeout() {
        var summary = JavaSourceInspector.javaTestAnnotationSummary("class TestCase { @org.junit.jupiter.api.Test void checks() {} }");
        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "org.junit.jupiter.api.Test", "org.junit.jupiter.params.ParameterizedTest", "org.junit.jupiter.api.RepeatedTest(2)",
            "org.junit.jupiter.api.TestFactory", "org.junit.jupiter.api.TestTemplate" })
    void composedJUnitAnnotationsCannotHideExecutableMethods(String annotation) {
        String source = "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @" + annotation
                + " @interface MyTest {} class Unprotected { @MyTest void executesWithoutTimeout() {} }";
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isTrue();
    }

    @Test
    void unicodeTranslationPrecedesCommentsAndRespectsBackslashEligibility() {
        String test = "class TestCase { @org.junit.jupiter.api.Test void runs() {} }";
        for (String prefix : List.of("\\" + "u000a", "\\\\\\" + "uu000a", "\\" + "u005c" + "\\" + "u000a")) {
            var summary = JavaSourceInspector.javaTestAnnotationSummary("// " + prefix + test);
            assertThat(summary.unsupportedAnnotationSyntax()).as(prefix).isFalse();
            assertThat(summary.hasTestMethods()).as(prefix).isTrue();
            assertThat(summary.classWithMissingAresAnnotations()).as(prefix).isTrue();
            assertThat(summary.testMethodWithoutStrictTimeout()).as(prefix).isTrue();
        }
        for (String prefix : List.of("\\\\" + "u000a", "\\" + "u005cu000a", "\\\\" + "u00zz")) {
            var summary = JavaSourceInspector.javaTestAnnotationSummary("// " + prefix + test);
            assertThat(summary.unsupportedAnnotationSyntax()).as(prefix).isFalse();
            assertThat(summary.hasTestMethods()).as(prefix).isFalse();
        }
    }

    @Test
    void encodedLiteralCannotInventAnnotations() {
        String source = "class Helper { String text = \"" + "\\" + "u0040org.junit.jupiter.api.Test\"; }";
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.unsupportedAnnotationSyntax()).isFalse();
        assertThat(summary.hasTestMethods()).isFalse();
    }

    @Test
    void encodedCommentsCannotSupplySafetyAnnotations() {
        String source = "\\" + "u002f" + "\\" + "u002a @de.tum.in.test.api.jupiter.Public @de.tum.in.test.api.StrictTimeout(1) */ "
                + "class TestCase { @org.junit.jupiter.api.Test void runs() {} }";
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.unsupportedAnnotationSyntax()).isFalse();
        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "u00", "uu00zz", "u００４０" })
    void malformedEligibleUnicodeEscapesFailClosed(String escape) {
        assertThat(JavaSourceInspector.javaTestAnnotationSummary("// " + "\\" + escape).unsupportedAnnotationSyntax()).isTrue();
    }

    @Test
    void unrelatedAnnotationDeclarationsRemainSupported() {
        var summary = JavaSourceInspector.javaTestAnnotationSummary("@interface Marker {} class Helper { @Marker void helper() {} }");
        assertThat(summary.hasTestMethods()).isFalse();
    }

    @Test
    void inlineTrustedAnnotationsAreAcceptedWithoutDependingOnLineBreaks() {
        String source = """
                @de.tum.in.test.api.jupiter.Public @de.tum.in.test.api.WhitelistPath("build")
                @de.tum.in.test.api.BlacklistPath("build/classes/java/test") class TestCase {
                  @org.junit.jupiter.api.Test @de.tum.in.test.api.StrictTimeout(1) void checks() {}
                }
                """;
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.classWithMissingAresAnnotations()).isFalse();
        assertThat(summary.testMethodWithoutStrictTimeout()).isFalse();
    }

    @Test
    void annotationTextBlockCannotSupplyClassRestrictionsOrTimeout() {
        String source = "@SuppressWarnings(" + "\"\"\"\n" + """
                @de.tum.in.test.api.jupiter.Public
                @de.tum.in.test.api.WhitelistPath("build")
                @de.tum.in.test.api.BlacklistPath("build/classes/java/test")
                @de.tum.in.test.api.StrictTimeout(1)
                """ + "\"\"\")\n" + """
                class TestCase {
                    @org.junit.jupiter.api.Test
                    void checks() {}
                }
                """;
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isTrue();
    }

    @Test
    void annotationStringCannotInventATestMethod() {
        var summary = JavaSourceInspector.javaTestAnnotationSummary("""
                class TestCase {
                    @SuppressWarnings("@org.junit.jupiter.api.Test")
                    void helper() {}
                }
                """);
        assertThat(summary.hasTestMethods()).isFalse();
    }

    @Test
    void encodedCommentTerminatorCannotHideATestFromInspection() {
        String source = "class TestCase { // comment" + "\\" + "u000a @org.junit.jupiter.api.Test void checks() {} }";
        var summary = JavaSourceInspector.javaTestAnnotationSummary(source);
        assertThat(summary.hasTestMethods()).isTrue();
        assertThat(summary.classWithMissingAresAnnotations()).isTrue();
        assertThat(summary.testMethodWithoutStrictTimeout()).isTrue();
    }

    @Test
    void declaresPackageMatchingPath_ignoresACommentedOutPackageAndTheOnesThatFollow() {
        String source = """
                // package de.tum.wrong;
                package de.tum.right;

                class Calculator {
                }
                """;

        assertThat(JavaSourceInspector.declaresPackageMatchingPath("src/de/tum/right/Calculator.java", source, List.of("src/"))).isTrue();
        assertThat(JavaSourceInspector.declaresPackageMatchingPath("src/de/tum/wrong/Calculator.java", source, List.of("src/"))).isFalse();
    }

    @Test
    void sourceDeclaresType_ignoresCommentedOutDeclarations() {
        String source = """
                // class MissingClass {}
                /* record MissingRecord(int value) {} */
                class PresentClass {}
                """;

        assertThat(JavaSourceInspector.sourceDeclaresType(source, "MissingClass")).isFalse();
        assertThat(JavaSourceInspector.sourceDeclaresType(source, "MissingRecord")).isFalse();
        assertThat(JavaSourceInspector.sourceDeclaresType(source, "PresentClass")).isTrue();
    }
}
