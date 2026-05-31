package de.tum.cit.aet.artemis.proof.domain.blocks;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.proof.config.ProofEnabled;
import de.tum.cit.aet.artemis.proof.domain.Associativity;
import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.LayoutCategory;
import de.tum.cit.aet.artemis.proof.domain.MathNodes;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;
import de.tum.cit.aet.artemis.proof.domain.RuleDirection;

/**
 * Unary negation. Used to represent {@code -a} cleanly, replacing the {@code 0 - a} workaround.
 * Bridges with the {@code sub} block via {@code sub_as_add_neg} so subtraction-side and addition-side
 * rules can cooperate.
 */
@Lazy
@Conditional(ProofEnabled.class)
@Component
public class NegationBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "negation";
    }

    @Override
    public String getCategory() {
        return "arithmetic";
    }

    @Override
    public String getLabel() {
        return "Negation";
    }

    @Override
    public String getPaletteLatex() {
        return "-a";
    }

    @Override
    public List<String> getSlots() {
        return List.of("inner");
    }

    @Override
    public int getPrecedence() {
        // Higher than +/− but lower than ·, matching standard convention -a · b → (-a) · b.
        return 30;
    }

    @Override
    public Associativity getAssociativity() {
        return Associativity.NONE;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.UNARY_PREFIX;
    }

    @Override
    public String getDisplaySymbol() {
        return "−";
    }

    @Override
    public String getLatexSymbol() {
        return "-";
    }

    @Override
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        return List.of(new RewriteRule("neg_neg", "Double negation", "-(-a) \\to a", MathNodes.neg(MathNodes.neg(a)), a, RuleDirection.BIDIRECTIONAL),
                new RewriteRule("neg_zero", "Negation of zero", "-0 \\to 0", MathNodes.neg(MathNodes.num("0")), MathNodes.num("0"), RuleDirection.FORWARD_ONLY),
                new RewriteRule("add_inverse", "Additive inverse", "a + (-a) \\to 0", MathNodes.add(a, MathNodes.neg(a)), MathNodes.num("0"), RuleDirection.FORWARD_ONLY));
    }
}
