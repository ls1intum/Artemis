package de.tum.cit.aet.artemis.proof.service;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.proof.config.ProofEnabled;
import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.RewriteRule;

/**
 * Collects all {@link BlockDefinition} Spring beans at startup.
 * New block types are contributed via code — no database changes required.
 */
@Conditional(ProofEnabled.class)
@Lazy
@Service
public class BlockRegistry {

    private final List<BlockDefinition> blocks;

    public BlockRegistry(List<BlockDefinition> blocks) {
        this.blocks = blocks;
    }

    public List<BlockDefinition> getAllBlocks() {
        return blocks;
    }

    public Optional<RewriteRule> findRuleById(String ruleId) {
        return blocks.stream().flatMap(b -> b.getRules().stream()).filter(r -> r.id().equals(ruleId)).findFirst();
    }
}
