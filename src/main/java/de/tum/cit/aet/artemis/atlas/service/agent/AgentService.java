package de.tum.cit.aet.artemis.atlas.service.agent;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDTO;

@Conditional(AtlasEnabled.class)
@Lazy
@Service
public class AgentService {

    private final AgentHttpClient agentHttpClient;

    public AgentService(AgentHttpClient agentHttpClient) {
        this.agentHttpClient = agentHttpClient;
    }

    public AgentChatResponseDTO forwardMessageToAgent(AgentChatRequestDTO request) {
        return agentHttpClient.sendMessageToAgent(request);
    }
}
