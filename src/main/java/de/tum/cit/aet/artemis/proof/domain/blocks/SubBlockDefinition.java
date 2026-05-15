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
public class SubBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "sub";
    }

    @Override
    public String getCategory() {
        return "arithmetic";
    }

    @Override
    public String getLabel() {
        return "Subtraction";
    }

    @Override
    public String getPaletteLatex() {
        return "a - b";
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
        return "−";
    }

    @Override
    public String getLatexSymbol() {
        return "-";
    }

    @Override
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        return List.of(new RewriteRule("sub_zero_right", "Identity (right)", "a - 0 \\to a", MathNodes.sub(a, MathNodes.num("0")), a, true),
                new RewriteRule("sub_self", "Self-subtraction", "a - a \\to 0", MathNodes.sub(a, a), MathNodes.num("0"), true));
    }
}
