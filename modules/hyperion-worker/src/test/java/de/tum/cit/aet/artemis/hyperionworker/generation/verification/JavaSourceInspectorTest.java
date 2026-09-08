package de.tum.cit.aet.artemis.hyperionworker.generation.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for the lexical machinery {@link ExerciseIntegrityGate} reads Java sources with. Only the properties a caller silently depends on are pinned here —
 * the ones a plausible "simplify this into a regex" rewrite would break without failing any gate test: literal-aware comment stripping, line-structure preservation, annotation
 * blocks that span lines, and per-element XML extraction. The gate's own rejection messages stay covered by {@link ExerciseIntegrityGateTest}.
 */
class JavaSourceInspectorTest {

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
