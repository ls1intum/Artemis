package de.tum.cit.aet.artemis.atlas.service.agent;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDto;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDto;

@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class AgentService {

    private final AgentHttpClient agentHttpClient;

    public AgentService(AgentHttpClient agentHttpClient) {
        this.agentHttpClient = agentHttpClient;
    }

    public AgentChatResponseDto forwardMessageToAgent(AgentChatRequestDto request) {
        return agentHttpClient.sendMessageToAgent(request);
    }
}
