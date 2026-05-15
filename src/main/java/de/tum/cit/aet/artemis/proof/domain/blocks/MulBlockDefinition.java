package de.tum.cit.aet.artemis.proof.domain.blocks;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.proof.domain.Associativity;
import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.LayoutCategory;
import de.tum.cit.aet.artemis.proof.domain.MathNodes;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

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
        return List.of(new RewriteRule("mul_comm", "Commutativity", "a \\cdot b \\to b \\cdot a", MathNodes.mul(a, b), MathNodes.mul(b, a), false),
                new RewriteRule("mul_assoc_left", "Associativity (left to right)", "(a \\cdot b) \\cdot c \\to a \\cdot (b \\cdot c)", MathNodes.mul(MathNodes.mul(a, b), c),
                        MathNodes.mul(a, MathNodes.mul(b, c)), false),
                new RewriteRule("mul_assoc_right", "Associativity (right to left)", "a \\cdot (b \\cdot c) \\to (a \\cdot b) \\cdot c", MathNodes.mul(a, MathNodes.mul(b, c)),
                        MathNodes.mul(MathNodes.mul(a, b), c), false),
                new RewriteRule("mul_one_left", "Identity (left)", "1 \\cdot a \\to a", MathNodes.mul(MathNodes.num("1"), a), a, true),
                new RewriteRule("mul_one_right", "Identity (right)", "a \\cdot 1 \\to a", MathNodes.mul(a, MathNodes.num("1")), a, true),
                new RewriteRule("mul_zero_left", "Annihilator (left)", "0 \\cdot a \\to 0", MathNodes.mul(MathNodes.num("0"), a), MathNodes.num("0"), true),
                new RewriteRule("mul_zero_right", "Annihilator (right)", "a \\cdot 0 \\to 0", MathNodes.mul(a, MathNodes.num("0")), MathNodes.num("0"), true));
    }
}
