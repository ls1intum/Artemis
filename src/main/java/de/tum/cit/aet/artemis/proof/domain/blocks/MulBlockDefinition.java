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
import de.tum.cit.aet.artemis.proof.domain.RuleDirection;

@Conditional(ProofEnabled.class)
@Component
public class MulBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "mul";
    }

    @Override
    public String getCategory() {
        return "arithmetic";
    }

    @Override
    public String getLabel() {
        return "Multiplication";
    }

    @Override
    public String getPaletteLatex() {
        return "a \\cdot b";
    }

    @Override
    public List<String> getSlots() {
        return List.of("left", "right");
    }

    @Override
    public int getPrecedence() {
        return 20;
    }

    @Override
    public Associativity getAssociativity() {
        return Associativity.LEFT;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.BINARY_INFIX;
    }

    @Override
    public String getDisplaySymbol() {
        return "·";
    }

    @Override
    public String getLatexSymbol() {
        return "\\cdot";
    }

    @Override
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        var b = MathNodes.wc("b");
        var c = MathNodes.wc("c");
        return List.of(new RewriteRule("mul_comm", "Commutativity", "a \\cdot b \\to b \\cdot a", MathNodes.mul(a, b), MathNodes.mul(b, a), RuleDirection.BIDIRECTIONAL),
                new RewriteRule("mul_assoc", "Associativity", "(a \\cdot b) \\cdot c \\to a \\cdot (b \\cdot c)", MathNodes.mul(MathNodes.mul(a, b), c),
                        MathNodes.mul(a, MathNodes.mul(b, c)), RuleDirection.BIDIRECTIONAL),
                new RewriteRule("mul_one_left", "Identity (left)", "1 \\cdot a \\to a", MathNodes.mul(MathNodes.num("1"), a), a, RuleDirection.FORWARD_ONLY),
                new RewriteRule("mul_one_right", "Identity (right)", "a \\cdot 1 \\to a", MathNodes.mul(a, MathNodes.num("1")), a, RuleDirection.FORWARD_ONLY),
                new RewriteRule("mul_zero_left", "Annihilator (left)", "0 \\cdot a \\to 0", MathNodes.mul(MathNodes.num("0"), a), MathNodes.num("0"), RuleDirection.FORWARD_ONLY),
                new RewriteRule("mul_zero_right", "Annihilator (right)", "a \\cdot 0 \\to 0", MathNodes.mul(a, MathNodes.num("0")), MathNodes.num("0"), RuleDirection.FORWARD_ONLY),
                // Distributivity. Forward is "expand", reverse is "factor" — both are useful operations.
                new RewriteRule("mul_distrib", "Distributivity (left)", "a \\cdot (b + c) \\to a \\cdot b + a \\cdot c", MathNodes.mul(a, MathNodes.add(b, c)),
                        MathNodes.add(MathNodes.mul(a, b), MathNodes.mul(a, c)), RuleDirection.BIDIRECTIONAL),
                new RewriteRule("mul_distrib_right", "Distributivity (right)", "(b + c) \\cdot a \\to b \\cdot a + c \\cdot a", MathNodes.mul(MathNodes.add(b, c), a),
                        MathNodes.add(MathNodes.mul(b, a), MathNodes.mul(c, a)), RuleDirection.BIDIRECTIONAL));
    }
}
