package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentToolDescriptionsTest {

    @Test
    void bashDirectsAcceptanceChecksToTheStructuredVerifyTool() {
        assertThat(AgentToolDescriptions.BASH).contains("targeted diagnostics", "Do not treat raw Maven, Gradle, or verify.sh commands as an acceptance verdict",
                "dedicated verify tool");
        assertThat(AgentToolDescriptions.BASH_COMMAND).doesNotContain("verify.sh", "mvn", "gradle");
    }
}
