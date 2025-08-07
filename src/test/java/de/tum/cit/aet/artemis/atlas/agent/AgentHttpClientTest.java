package de.tum.cit.aet.artemis.atlas.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDto;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDto;
import de.tum.cit.aet.artemis.atlas.service.agent.AgentHttpClient;

class AgentHttpClientTest {

    private AgentHttpClient agentHttpClient;

    @Mock
    private RestTemplate restTemplate;

    private final String agentApiUrl = "http://localhost:8080/api/agent";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentHttpClient = new AgentHttpClient(restTemplate);
        agentHttpClient.setAgentApiUrl(agentApiUrl);
    }

    @Test
    void testSendMessageToAgent() {
        // Arrange
        AgentChatRequestDto request = new AgentChatRequestDto("Hello, Agent!", 10L, "session1");
        AgentChatResponseDto expectedResponse = new AgentChatResponseDto("Agent response to: Hello, Agent!", "session1");
        String url = agentApiUrl + "/chat";
        when(restTemplate.postForObject(url, request, AgentChatResponseDto.class)).thenReturn(expectedResponse);

        // Act
        AgentChatResponseDto actualResponse = agentHttpClient.sendMessageToAgent(request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(restTemplate, times(1)).postForObject(url, request, AgentChatResponseDto.class);
    }
}
