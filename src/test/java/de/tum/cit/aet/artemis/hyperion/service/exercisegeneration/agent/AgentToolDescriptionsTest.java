package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentToolDescriptionsTest {

    @Test
    void verifyIsDescribedAsAMechanicalPrecheckNotSemanticAcceptance() {
        assertThat(AgentToolDescriptions.VERIFY)
                .contains("mechanical precheck", "failure evidence", "does not prove semantic relevance", "final post-loop integrity and semantic review decides acceptance")
                .doesNotContain("authoritative self-check");
    }
}
