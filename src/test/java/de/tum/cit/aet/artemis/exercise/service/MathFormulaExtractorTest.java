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

    // --- Restoring ---

    @Test
    void shouldRestoreEveryPlaceholder() {
        List<Formula> formulas = new ArrayList<>();
        String extracted = MathFormulaExtractor.extract("$$a^2$$\ntext $b^2$ text", formulas);

        String restored = MathFormulaExtractor.restore(extracted, formulas);

        assertThat(restored).contains("data-formula=\"a^2\" data-display-mode=\"true\"").contains("data-formula=\"b^2\" data-display-mode=\"false\"");
        // The placeholders are NUL-delimited, so a leftover one would still be in there.
        assertThat(restored).doesNotContain("\0");
    }

    @Test
    void shouldLeaveTextWithoutPlaceholdersUntouched() {
        assertThat(MathFormulaExtractor.restore("plain text", List.of())).isEqualTo("plain text");
    }
}
