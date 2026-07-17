package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentToolDescriptionsTest {

    @Test
    void verifyIsDescribedAsAMechanicalPrecheckNotSemanticAcceptance() {
        assertThat(AgentToolDescriptions.VERIFY).contains("mechanical precheck", "failure evidence", "does not prove semantic relevance",
                "post-loop verification determines save eligibility", "quality review may request repairs or flag instructor review")
                .doesNotContain("authoritative self-check", "decides acceptance");
    }
}
