package de.tum.cit.aet.artemis.atlas.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.agent.AgentHttpClient;
import de.tum.cit.aet.artemis.atlas.service.agent.AgentService;

class AgentServiceTest {

    private AgentService agentService;

    @Mock
    private AgentHttpClient agentHttpClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentService = new AgentService(agentHttpClient);
    }

    @Test
    void testForwardMessageToAgent() {
        // Arrange
        AgentChatRequestDTO request = new AgentChatRequestDTO("Hello, Agent!", 10L, "session1");
        AgentChatResponseDTO expectedResponse = new AgentChatResponseDTO("Agent response to: Hello, Agent!", "session1");
        when(agentHttpClient.sendMessageToAgent(request)).thenReturn(expectedResponse);

        // Act
        AgentChatResponseDTO actualResponse = agentService.forwardMessageToAgent(request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(agentHttpClient, times(1)).sendMessageToAgent(request);
    }
}
