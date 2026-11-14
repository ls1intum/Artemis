package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.agent.AgentSystemPrompt;

class StatementHandoffProjectionTest {

    @Test
    void theDiagramDirectiveTellsTheStatementStageTheServersDecisionAndWhatADiagramShows() {
        assertThat(StatementHandoffProjection.diagramDirective(true)).contains("REQUIRES a class diagram", "rejects a statement without one",
                AgentSystemPrompt.DIAGRAM_REQUIREMENT_RULE);
        assertThat(StatementHandoffProjection.diagramDirective(false)).contains("does not require a class diagram", AgentSystemPrompt.DIAGRAM_REQUIREMENT_RULE)
                .doesNotContain("REQUIRES");
    }
}
