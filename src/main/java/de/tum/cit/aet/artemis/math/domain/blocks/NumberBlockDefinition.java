package de.tum.cit.aet.artemis.math.domain.blocks;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.LayoutCategory;
import de.tum.cit.aet.artemis.math.domain.RewriteRule;

@Lazy
@Conditional(MathEnabled.class)
@Component
public class NumberBlockDefinition implements BlockDefinition {

    @Override
    public String getType() {
        return "number";
    }

    @Override
    public String getCategory() {
        return "terminal";
    }

    @Override
    public String getLabel() {
        return "Number";
    }

    @Override
    public String getPaletteLatex() {
        return "n";
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
    public LayoutCategory getLayoutCategory() {
        return LayoutCategory.TERMINAL_NUMBER;
    }

    @Override
    public List<RewriteRule> getRules() {
        return List.of();
    }
}
