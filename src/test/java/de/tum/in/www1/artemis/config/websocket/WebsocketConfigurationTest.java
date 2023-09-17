package de.tum.in.www1.artemis.config.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;

class WebsocketConfigurationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Test
    void testGetTopicRelayPrefixes() {
        // No special profiles activated -> Do not forward quiz messages
        assertThat(WebsocketConfiguration.getTopicRelayPrefixes(Set.of(""))).containsExactlyInAnyOrder("/topic");

        // Only decoupling profile -> Forward quiz messages
        assertThat(WebsocketConfiguration.getTopicRelayPrefixes(Set.of("decoupling"))).containsExactlyInAnyOrder("/topic", "/queue/quizExercise");

        // Only quiz profile -> Do not forward quiz messages (because decoupling profile is not enabled)
        assertThat(WebsocketConfiguration.getTopicRelayPrefixes(Set.of("quiz"))).containsExactlyInAnyOrder("/topic");

        // Quiz & decoupling profile -> Do not forward quiz messages
        assertThat(WebsocketConfiguration.getTopicRelayPrefixes(Set.of("decoupling", "quiz"))).containsExactlyInAnyOrder("/topic");
    }

}
