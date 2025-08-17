package de.tum.cit.aet.artemis.atlas.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.agent.AgentHttpClient;

class AgentHttpClientTest {

    private AgentHttpClient agentHttpClient;

    @Mock
    private RestTemplate restTemplate;

    @Value("${agent.api.url}")
    private String agentApiUrl = "http://localhost:8080/api/agent";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentHttpClient = new AgentHttpClient(restTemplate);
        agentHttpClient.setAgentApiUrl(agentApiUrl);
    }

    @Test
    void testSendMessageToAgent() {
        // Arrange
        AgentChatRequestDTO request = new AgentChatRequestDTO("Hello, Agent!", 10L, "session1");
        AgentChatResponseDTO expectedResponse = new AgentChatResponseDTO("Agent response to: Hello, Agent!", "session1");
        String url = agentApiUrl + "/chat";
        when(restTemplate.postForObject(url, request, AgentChatResponseDTO.class)).thenReturn(expectedResponse);

        // Act
        AgentChatResponseDTO actualResponse = agentHttpClient.sendMessageToAgent(request);

        // Assert
        assertThat(actualResponse).isEqualTo(expectedResponse);
        verify(restTemplate, times(1)).postForObject(url, request, AgentChatResponseDTO.class);
    }
}
