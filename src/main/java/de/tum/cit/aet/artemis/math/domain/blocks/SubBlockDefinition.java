package de.tum.cit.aet.artemis.math.domain.blocks;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.Associativity;
import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.LayoutCategory;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;
import de.tum.cit.aet.artemis.math.domain.RuleDirection;

@Lazy
@Conditional(MathEnabled.class)
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
        var b = MathNodes.wc("b");
        return List.of(new RewriteRule("sub_zero_right", "Identity (right)", "a - 0 \\to a", MathNodes.sub(a, MathNodes.num("0")), a, RuleDirection.FORWARD_ONLY),
                new RewriteRule("sub_self", "Self-subtraction", "a - a \\to 0", MathNodes.sub(a, a), MathNodes.num("0"), RuleDirection.FORWARD_ONLY),
                // Bridge rule: lets +/- rules cooperate via the negation block.
                new RewriteRule("sub_as_add_neg", "Subtraction as add-negation", "a - b \\to a + (-b)", MathNodes.sub(a, b), MathNodes.add(a, MathNodes.neg(b)),
                        RuleDirection.BIDIRECTIONAL));
    }
}
