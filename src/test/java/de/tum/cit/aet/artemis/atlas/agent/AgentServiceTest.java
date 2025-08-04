package de.tum.cit.aet.artemis.atlas.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDto;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDto;
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
        AgentChatRequestDto request = new AgentChatRequestDto("Hello, Agent!", 10L, "session1");
        AgentChatResponseDto expectedResponse = new AgentChatResponseDto("Agent response to: Hello, Agent!", "session1");
        when(agentHttpClient.sendMessageToAgent(request)).thenReturn(expectedResponse);

        // Act
        AgentChatResponseDto actualResponse = agentService.forwardMessageToAgent(request);

        // Assert
        assertEquals(expectedResponse, actualResponse);
        verify(agentHttpClient, times(1)).sendMessageToAgent(request);
    }
}
