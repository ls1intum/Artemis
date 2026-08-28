package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.service.MathFormulaExtractor.Formula;

/**
 * Unit tests for {@link MathFormulaExtractor}, covering the delimiter cases that decide whether a line is display math.
 * <p>
 * These used to be two regular expressions with a repetition over the formula body, which Java's engine turns into one
 * recursive call per repetition. They are scanned now, so the cases below pin both the classification and the absence of
 * that limit.
 */
class MathFormulaExtractorTest {

    private static List<Formula> extract(String markdown, StringBuilder resultOut) {
        List<Formula> formulas = new ArrayList<>();
        resultOut.append(MathFormulaExtractor.extract(markdown, formulas));
        return formulas;
    }

    private static List<Formula> extract(String markdown) {
        return extract(markdown, new StringBuilder());
    }

    // --- Display math ---

    @Test
    void shouldExtractAFormulaThatIsAloneOnItsLine() {
        List<Formula> formulas = extract("before\n$$x^2$$\nafter");

        assertThat(formulas).containsExactly(new Formula("x^2", true));
    }

    @Test
    void shouldKeepSingleDollarSignsInsideTheFormula() {
        // A formula may escape a dollar sign, e.g. for currency, and only a pair of them closes the formula.
        List<Formula> formulas = extract("$$\\text{Price: \\$5}$$");

        assertThat(formulas).containsExactly(new Formula("\\text{Price: \\$5}", true));
    }

    @Test
    void shouldNotExtractWhenTheLineHoldsMoreThanOneFormula() {
        StringBuilder result = new StringBuilder();
        List<Formula> formulas = extract("$$a$$b$$", result);

        // Two formulas on one line are the inline convention's business, not display math's, and applyCompatibility runs
        // first in the renderer. On its own, this line carries no formula that either pattern accepts.
        assertThat(formulas).isEmpty();
        assertThat(result.toString()).isEqualTo("$$a$$b$$");
    }

    @Test
    void shouldLeaveAnEmptyFormulaAsWritten() {
        StringBuilder result = new StringBuilder();
        List<Formula> formulas = extract("$$$$", result);

        assertThat(formulas).isEmpty();
        assertThat(result.toString()).isEqualTo("$$$$");
    }

    @Test
    void shouldExtractFromACarriageReturnLine() {
        // Windows line endings reach the renderer as they were saved, and the formula is the same formula.
        StringBuilder result = new StringBuilder();
        List<Formula> formulas = extract("before\r\n$$x^2$$\r\nafter", result);

        assertThat(formulas).containsExactly(new Formula("x^2", true));
        assertThat(result.toString()).contains("\r\n");
    }

    @Test
    void shouldExtractFromABareCarriageReturnLine() {
        // CommonMark ends a line on a bare CR too, so a formula on such a line is standalone for the renderer downstream
        // and has to be standalone here. The separators are copied back as written.
        StringBuilder result = new StringBuilder();
        List<Formula> formulas = extract("before\r$$x^2$$\rafter", result);

        assertThat(formulas).containsExactly(new Formula("x^2", true));
        assertThat(result.toString()).startsWith("before\r").endsWith("\rafter").doesNotContain("$$");
    }

    @Test
    void shouldExtractAFormulaWithThousandsOfEscapedDollarSigns() {
        // One repetition per escaped dollar sign raised a StackOverflowError while matching, so the request failed instead
        // of rendering. Nothing about this input is pathological other than its length.
        String latex = "a$".repeat(5_000) + "b";

        List<Formula> formulas = extract("$$" + latex + "$$");

        assertThat(formulas).containsExactly(new Formula(latex, true));
    }

    @Test
    void shouldNotHangOnAnUnclosedFormulaWithThousandsOfDollarSigns() {
        StringBuilder result = new StringBuilder();
        String markdown = "$$" + "a$".repeat(5_000);

        List<Formula> formulas = extract(markdown, result);

        // Every pair of dollar signs around a letter is a valid inline formula, so this input is inline math throughout.
        assertThat(formulas).isNotEmpty().noneMatch(Formula::displayMode);
        assertThat(result.toString()).doesNotContain("$a$");
    }

    // --- Inline convention ---

    @Test
    void shouldRewriteADoubleDollarFormulaThatSharesItsLine() {
        String result = MathFormulaExtractor.applyCompatibility("The area $$x^2$$ is known");

        assertThat(result).isEqualTo("The area $x^2$ is known");
    }

    @Test
    void shouldNotRewriteAFormulaThatIsAloneOnItsLine() {
        String result = MathFormulaExtractor.applyCompatibility("$$x^2$$");

        assertThat(result).isEqualTo("$$x^2$$");
    }

    @Test
    void shouldNotRewriteAFormulaWhoseBodyHoldsADollarSign() {
        // Rewritten to single dollars, the inline pattern would stop at the escaped dollar and the formula would come out
        // cut off. Left as written, the reader sees the source instead of a mangled formula.
        String markdown = "The price $$\\text{\\$5}$$ applies";

        assertThat(MathFormulaExtractor.applyCompatibility(markdown)).isEqualTo(markdown);
    }

    @Test
    void shouldNotRewriteACarriageReturnLineThatIsOnlyAFormula() {
        String result = MathFormulaExtractor.applyCompatibility("$$x^2$$\r\ntext");

        assertThat(result).isEqualTo("$$x^2$$\r\ntext");
    }

    @Test
    void shouldNotTreatTextAcrossABareCarriageReturnAsSurroundingText() {
        // The formula is alone on its bare-CR line, so it is display math, and the compatibility pass must leave it be.
        // Text on the other side of a CR is not "surrounding text": the client's regex says so with `.`, which matches no
        // line terminator, and the display extraction downstream ends a line on a bare CR as well.
        String result = MathFormulaExtractor.applyCompatibility("before\r$$x^2$$\rafter");

        assertThat(result).isEqualTo("before\r$$x^2$$\rafter");
    }

    @Test
    void shouldExtractDisplayMathFromABareCarriageReturnLineThroughTheWholePipeline() {
        // Going through applyCompatibility rather than straight to extract: the compatibility pass runs first in the
        // renderer, so a rewrite there decides what the extraction can still see.
        StringBuilder result = new StringBuilder();
        List<Formula> formulas = extract(MathFormulaExtractor.applyCompatibility("before\r$$x^2$$\rafter"), result);

        assertThat(formulas).containsExactly(new Formula("x^2", true));
        assertThat(result.toString()).startsWith("before\r").endsWith("\rafter");
    }

    @Test
    void shouldStillRewriteAFormulaSharingItsBareCarriageReturnLine() {
        // The mirror image: within one CR-delimited segment the convention still applies, and the whole LF line is
        // rewritten, exactly as the client rewrites the whole line it tested.
        String result = MathFormulaExtractor.applyCompatibility("before\rThe area $$x^2$$ is known\rafter");

        assertThat(result).isEqualTo("before\rThe area $x^2$ is known\rafter");
    }

    // --- Restoring and injecting ---

    private static final String TOKEN = "testtoken";

    @Test
    void shouldRestoreEveryPlaceholderAsAMarker() {
        List<Formula> formulas = new ArrayList<>();
        String extracted = MathFormulaExtractor.extract("$$a^2$$\ntext $b^2$ text", formulas);

        String restored = MathFormulaExtractor.restore(extracted, formulas, TOKEN);

        assertThat(restored).contains("data-formula-index=\"" + TOKEN + "-0\"").contains("data-formula-index=\"" + TOKEN + "-1\"");
        // The placeholders are NUL-delimited, so a leftover one would still be in there.
        assertThat(restored).doesNotContain("\0");
    }

    @Test
    void shouldLeaveTextWithoutPlaceholdersUntouched() {
        assertThat(MathFormulaExtractor.restore("plain text", List.of(), TOKEN)).isEqualTo("plain text");
    }

    @Test
    void shouldInjectMathmlForConvertibleFormulaAndSourceForTheRest() {
        List<Formula> formulas = new ArrayList<>();
        String extracted = MathFormulaExtractor.extract("$x^2$ and $\\thiscommanddoesnotexist{y}$", formulas);
        String restored = MathFormulaExtractor.restore(extracted, formulas, TOKEN);

        String injected = MathFormulaExtractor.injectMathml(restored, formulas, TOKEN);

        assertThat(injected).contains("<math xmlns=\"http://www.w3.org/1998/Math/MathML\">").contains("<msup><mi>x</mi><mn>2</mn></msup>");
        assertThat(injected).contains("<span class=\"artemis-formula-source\">");
        assertThat(injected).doesNotContain("data-formula-index");
    }

    @Test
    void shouldNotInjectIntoAForgedMarkerCarryingAnotherToken() {
        List<Formula> formulas = List.of(new Formula("x^2", false));
        // A marker an author could have written: it carries a different token, so it is left as the inert span it is.
        String forged = "<span class=\"artemis-formula-placeholder\" data-formula-index=\"otherToken-0\"></span>";

        String injected = MathFormulaExtractor.injectMathml(forged, formulas, TOKEN);

        assertThat(injected).isEqualTo(forged).doesNotContain("<math");
    }
}
