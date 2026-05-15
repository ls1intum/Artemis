package de.tum.cit.aet.artemis.proof.domain.blocks;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.proof.config.ProofEnabled;
import de.tum.cit.aet.artemis.proof.domain.Associativity;
import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.LayoutCategory;
import de.tum.cit.aet.artemis.proof.domain.MathNodes;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

@Conditional(ProofEnabled.class)
@Component
public class FractionBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "fraction";
    }

    @Override
    public String getCategory() {
        return "arithmetic";
    }

    @Override
    public String getLabel() {
        return "Fraction";
    }

    @Override
    public String getPaletteLatex() {
        return "\\frac{a}{b}";
    }

    @Override
    public List<String> getSlots() {
        return List.of("numerator", "denominator");
    }

    @Override
    public int getPrecedence() {
        return 80;
    }

    @Override
    public Associativity getAssociativity() {
        return Associativity.NONE;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.FRACTION;
    }

    @Override
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        var b = MathNodes.wc("b");
        var c = MathNodes.wc("c");
        return List.of(new RewriteRule("frac_one_denom", "Identity denominator", "\\frac{a}{1} \\to a", MathNodes.frac(a, MathNodes.num("1")), a, true),
                new RewriteRule("frac_mul_cancel_left", "Cancel left factor", "\\frac{c \\cdot a}{c \\cdot b} \\to \\frac{a}{b}",
                        MathNodes.frac(MathNodes.mul(c, a), MathNodes.mul(c, b)), MathNodes.frac(a, b), true));
    }
}
