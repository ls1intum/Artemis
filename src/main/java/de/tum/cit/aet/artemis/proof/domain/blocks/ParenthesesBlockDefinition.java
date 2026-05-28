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
public class ParenthesesBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "parentheses";
    }

    @Override
    public String getCategory() {
        return "structural";
    }

    @Override
    public String getLabel() {
        return "Parentheses";
    }

    @Override
    public String getPaletteLatex() {
        return "\\left(a\\right)";
    }

    @Override
    public List<String> getSlots() {
        return List.of("content");
    }

    @Override
    public int getPrecedence() {
        return 90;
    }

    @Override
    public Associativity getAssociativity() {
        return Associativity.NONE;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.PARENTHESES;
    }

    @Override
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        return List.of(new RewriteRule("paren_unwrap", "Remove parentheses", "\\left(a\\right) \\to a", MathNodes.paren(a), a, RuleDirection.FORWARD_ONLY));
    }
}
