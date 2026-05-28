package de.tum.cit.aet.artemis.proof.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.proof.domain.BlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.domain.blocks.AddBlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.blocks.EqualityBlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.blocks.FractionBlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.blocks.MulBlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.blocks.ParenthesesBlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.blocks.SubBlockDefinition;
import de.tum.cit.aet.artemis.proof.domain.blocks.VariableBlockDefinition;
import de.tum.cit.aet.artemis.proof.service.BlockRegistry;

class GraderRegistryTest {

    private GraderRegistry registry;

    private RewriteChainGrader rewriteChainGrader;

    @BeforeEach
    void setUp() {
        List<BlockDefinition> blocks = List.of(new NumberBlockDefinition(), new VariableBlockDefinition(), new AddBlockDefinition(), new SubBlockDefinition(),
                new MulBlockDefinition(), new FractionBlockDefinition(), new EqualityBlockDefinition(), new ParenthesesBlockDefinition());
        BlockRegistry blockRegistry = new BlockRegistry(blocks);
        blockRegistry.index();
        rewriteChainGrader = new RewriteChainGrader(blockRegistry);
        registry = new GraderRegistry(List.of(rewriteChainGrader));
        registry.index();
    }

    @Test
    void getGrader_rewriteChain_returnsTheBean() {
        assertThat(registry.getGrader(GraderType.REWRITE_CHAIN)).isSameAs(rewriteChainGrader);
    }

    @Test
    void getGrader_unregisteredType_throws() {
        assertThatThrownBy(() -> registry.getGrader(GraderType.LEAN)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No ProofGrader");
    }

    @Test
    void index_duplicateType_throws() {
        ProofGrader duplicate = new ProofGrader() {

            @Override
            public GraderType getType() {
                return GraderType.REWRITE_CHAIN;
            }

            @Override
            public GradingResult grade(ProofExercise exercise, ProofSubmission submission) {
                return GradingResult.of(0.0);
            }
        };
        GraderRegistry duped = new GraderRegistry(List.of(rewriteChainGrader, duplicate));
        assertThatThrownBy(duped::index).isInstanceOf(IllegalStateException.class).hasMessageContaining("Multiple ProofGrader");
    }
}
