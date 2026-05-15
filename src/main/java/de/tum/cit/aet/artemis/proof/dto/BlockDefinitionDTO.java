package de.tum.cit.aet.artemis.proof.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.proof.domain.Associativity;
import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.LayoutCategory;
import de.tum.cit.aet.artemis.proof.domain.MathNode;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

/**
 * Serializable view of a {@link BlockDefinition} returned by {@code GET /api/proof/block-registry}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BlockDefinitionDTO(String type, String category, String label, String paletteLatex, List<String> slots, List<RewriteRuleDTO> rules, int precedence,
        Associativity associativity, LayoutCategory layoutCategory, String displaySymbol, String latexSymbol) {

    /**
     * Serializable view of a {@link RewriteRule}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RewriteRuleDTO(String id, String name, String paletteLatex, MathNode pattern, MathNode template, boolean isReduction) {

        public static RewriteRuleDTO of(RewriteRule rule) {
            return new RewriteRuleDTO(rule.id(), rule.name(), rule.paletteLatex(), rule.pattern(), rule.template(), rule.isReduction());
        }
    }

    public static BlockDefinitionDTO of(BlockDefinition block) {
        List<RewriteRuleDTO> ruleDTOs = block.getRules().stream().map(RewriteRuleDTO::of).toList();
        return new BlockDefinitionDTO(block.getType(), block.getCategory(), block.getLabel(), block.getPaletteLatex(), block.getSlots(), ruleDTOs, block.getPrecedence(),
                block.getAssociativity(), block.getLayoutCategory(), block.getDisplaySymbol(), block.getLatexSymbol());
    }
}
