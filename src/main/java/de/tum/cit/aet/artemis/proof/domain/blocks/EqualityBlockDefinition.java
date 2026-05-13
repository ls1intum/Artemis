package de.tum.cit.aet.artemis.proof.domain.blocks;

import java.util.List;

import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.MathNodes;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

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
    public List<RewriteRule> getRules() {
        var a = MathNodes.wc("a");
        var b = MathNodes.wc("b");
        return List.of(new RewriteRule("eq_symm", "Symmetry", "a = b \\to b = a", MathNodes.eq(a, b), MathNodes.eq(b, a), false));
    }
}
