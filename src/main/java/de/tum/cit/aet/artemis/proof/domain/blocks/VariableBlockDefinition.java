package de.tum.cit.aet.artemis.proof.domain.blocks;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.proof.domain.Associativity;
import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.LayoutCategory;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

@Component
public class VariableBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "variable";
    }

    @Override
    public String getCategory() {
        return "terminal";
    }

    @Override
    public String getLabel() {
        return "Variable";
    }

    @Override
    public String getPaletteLatex() {
        return "x";
    }

    @Override
    public List<String> getSlots() {
        return List.of();
    }

    @Override
    public int getPrecedence() {
        return 100;
    }

    @Override
    public Associativity getAssociativity() {
        return Associativity.NONE;
    }

    @Override
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.TERMINAL_VARIABLE;
    }

    @Override
    public List<RewriteRule> getRules() {
        return List.of();
    }
}
