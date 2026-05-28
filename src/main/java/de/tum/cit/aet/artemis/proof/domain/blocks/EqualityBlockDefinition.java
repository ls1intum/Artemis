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
public class EqualityBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "equality";
    }

    @Override
    public String getCategory() {
        return "relational";
    }

    @Override
    public String getLabel() {
        return "Equality";
    }

    @Override
    public String getPaletteLatex() {
        return "a = b";
    }

    @Override
    public List<String> getSlots() {
        return List.of("left", "right");
    }

    @Override
    public int getPrecedence() {
        return 0;
    }

    @Override
    public Associativity getAssociativity() {
        return Associativity.NONE;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.BINARY_INFIX;
    }

    @Override
    public String getDisplaySymbol() {
        return "=";
    }

    @Override
    public String getLatexSymbol() {
        return "=";
    }

    @Override
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        var b = MathNodes.wc("b");
        return List.of(new RewriteRule("eq_symm", "Symmetry", "a = b \\to b = a", MathNodes.eq(a, b), MathNodes.eq(b, a), RuleDirection.BIDIRECTIONAL));
    }
}
