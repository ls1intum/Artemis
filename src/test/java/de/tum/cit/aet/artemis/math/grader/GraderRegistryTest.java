package de.tum.cit.aet.artemis.math.grader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.math.domain.BlockDefinition;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.domain.blocks.AddBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.EqualityBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.FractionBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.MulBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.NumberBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.ParenthesesBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.SubBlockDefinition;
import de.tum.cit.aet.artemis.math.domain.blocks.VariableBlockDefinition;
import de.tum.cit.aet.artemis.math.service.BlockRegistry;

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
        assertThatThrownBy(() -> registry.getGrader(GraderType.LEAN)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No MathGrader");
    }

    @Test
    void index_duplicateType_throws() {
        MathGrader duplicate = new MathGrader() {

            @Override
            public GraderType getType() {
                return GraderType.REWRITE_CHAIN;
            }

            @Override
            public GradingResult grade(MathExercise exercise, MathSubmission submission) {
                return GradingResult.of(0.0);
            }
        };
        GraderRegistry duped = new GraderRegistry(List.of(rewriteChainGrader, duplicate));
        assertThatThrownBy(duped::index).isInstanceOf(IllegalStateException.class).hasMessageContaining("Multiple MathGrader");
    }
}
