package de.tum.cit.aet.artemis.atlas.service.agent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDto;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDto;

@Component
public class AgentHttpClient {

    private final RestTemplate restTemplate;

    public void setAgentApiUrl(String agentApiUrl) {
        this.agentApiUrl = agentApiUrl;
    }

    @Value("${agent.api.url}")
    private String agentApiUrl;

    public AgentHttpClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AgentChatResponseDto sendMessageToAgent(AgentChatRequestDto request) {
        String url = agentApiUrl + "/chat";
        return restTemplate.postForObject(url, request, AgentChatResponseDto.class);
    }
}
