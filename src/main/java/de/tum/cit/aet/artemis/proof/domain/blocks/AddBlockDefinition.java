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
public class AddBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "add";
    }

    @Override
    public String getCategory() {
        return "arithmetic";
    }

    @Override
    public String getLabel() {
        return "Addition";
    }

    @Override
    public String getPaletteLatex() {
        return "a + b";
    }

    @Override
    public List<String> getSlots() {
        return List.of("left", "right");
    }

    @Override
    public int getPrecedence() {
        return 10;
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
        return "+";
    }

    @Override
    public String getLatexSymbol() {
        return "+";
    }

    @Override
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        var b = MathNodes.wc("b");
        var c = MathNodes.wc("c");
        return List.of(new RewriteRule("add_comm", "Commutativity", "a + b \\to b + a", MathNodes.add(a, b), MathNodes.add(b, a), false),
                new RewriteRule("add_assoc_left", "Associativity (left to right)", "(a + b) + c \\to a + (b + c)", MathNodes.add(MathNodes.add(a, b), c),
                        MathNodes.add(a, MathNodes.add(b, c)), false),
                new RewriteRule("add_assoc_right", "Associativity (right to left)", "a + (b + c) \\to (a + b) + c", MathNodes.add(a, MathNodes.add(b, c)),
                        MathNodes.add(MathNodes.add(a, b), c), false),
                new RewriteRule("add_zero_left", "Identity (left)", "0 + a \\to a", MathNodes.add(MathNodes.num("0"), a), a, true),
                new RewriteRule("add_zero_right", "Identity (right)", "a + 0 \\to a", MathNodes.add(a, MathNodes.num("0")), a, true));
    }
}
