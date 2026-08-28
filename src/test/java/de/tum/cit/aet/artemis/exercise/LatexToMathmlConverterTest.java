package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.service.LatexToMathmlConverter;

/**
 * SPIKE (feature/programming/ssr-mathml-spike): unit tests for the server-side LaTeX -> Presentation MathML converter.
 * Pure JUnit, no Spring context.
 */
class LatexToMathmlConverterTest {

    @Test
    void shouldConvertInlineFormulaToMathml() {
        Optional<String> result = LatexToMathmlConverter.toMathml("x^2 + y^2 = z^2", false);

        assertThat(result).isPresent();
        assertThat(result.get()).startsWith("<math xmlns=\"http://www.w3.org/1998/Math/MathML\">").endsWith("</math>").contains("<msup><mi>x</mi><mn>2</mn></msup>");
        assertThat(result.get()).doesNotContain("display=\"block\"");
    }

    @Test
    void shouldMarkDisplayFormulaAsBlock() {
        Optional<String> result = LatexToMathmlConverter.toMathml("\\frac{a}{b}", true);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("display=\"block\"").contains("<mfrac><mi>a</mi><mi>b</mi></mfrac>");
    }

    @Test
    void shouldConvertCommonConstructs() {
        assertThat(LatexToMathmlConverter.toMathml("\\sqrt{2}", false)).get().asString().contains("<msqrt><mn>2</mn></msqrt>");
        assertThat(LatexToMathmlConverter.toMathml("\\sum_{i=1}^n i", false)).isPresent();
        assertThat(LatexToMathmlConverter.toMathml("\\begin{matrix} a & b \\\\ c & d \\end{matrix}", true)).get().asString().contains("<mtable>");
    }

    @Test
    void shouldFallBackWhenCommandIsUnknown() {
        assertThat(LatexToMathmlConverter.toMathml("\\thiscommanddoesnotexist{x}", false)).isEmpty();
    }

    @Test
    void shouldNeverEmitUrlOrResourceAttributes() {
        // Even if a formula could name a resource, the allowlist carries no URL attribute, so none can survive.
        Optional<String> result = LatexToMathmlConverter.toMathml("\\href{https://evil.example}{x}", false);

        // \href either fails to convert (empty) or converts without the href surviving; both are acceptable, never a live href.
        assertThat(result).satisfiesAnyOf(empty -> assertThat(empty).isEmpty(), present -> assertThat(present.get()).doesNotContain("href").doesNotContain("evil.example"));
    }

    @Test
    void shouldRejectMacroDefinitionPrimitives() {
        assertThat(LatexToMathmlConverter.toMathml("\\newcommand{\\x}{x}\\x", false)).isEmpty();
        assertThat(LatexToMathmlConverter.toMathml("\\def\\x{x}\\x", false)).isEmpty();
    }

    @Test
    void shouldRejectOverlyLongInput() {
        assertThat(LatexToMathmlConverter.toMathml("x".repeat(6_000), false)).isEmpty();
    }

    @Test
    void shouldRejectDeeplyNestedInput() {
        assertThat(LatexToMathmlConverter.toMathml("{".repeat(200) + "x" + "}".repeat(200), false)).isEmpty();
    }

    @Test
    void shouldNotEmitSemanticsOrAnnotationWrapper() {
        Optional<String> result = LatexToMathmlConverter.toMathml("x", false);

        assertThat(result).isPresent();
        assertThat(result.get()).doesNotContain("<semantics").doesNotContain("<annotation");
    }
}
