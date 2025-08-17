package de.tum.cit.aet.artemis.atlas.service.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDTO;

@Component
@Lazy
@Conditional(AtlasEnabled.class)
public class AgentHttpClient {

    private final RestTemplate restTemplate;

    public void setAgentApiUrl(String agentApiUrl) {
        this.agentApiUrl = agentApiUrl;
    }

    @Value("${agent.api.url}")
    private String agentApiUrl;

    private static final Logger log = LoggerFactory.getLogger(AgentHttpClient.class);

    public AgentHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AgentChatResponseDTO sendMessageToAgent(AgentChatRequestDTO request) {
        try {
            String url = agentApiUrl + "/chat";
            log.debug("Sending request to agent API: {}", url);
            return restTemplate.postForObject(url, request, AgentChatResponseDTO.class);
        }
        catch (RestClientException e) {
            log.error("Failed to communicate with agent API", e);
            throw e; // Re-throw or wrap in custom exception

        }
    }
}
