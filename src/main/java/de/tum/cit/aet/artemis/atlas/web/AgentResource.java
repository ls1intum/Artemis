package de.tum.cit.aet.artemis.atlas.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.agent.AgentService;

@Conditional(AtlasEnabled.class)
@Lazy
@RestController
@RequestMapping("api/atlas/agent/")
public class AgentResource {

    private static final Logger log = LoggerFactory.getLogger(AgentResource.class);

    private final AgentService agentService;

    public AgentResource(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("chat-with-agent")
    public ResponseEntity<AgentChatResponseDTO> chatWithAgent(@RequestBody AgentChatRequestDTO request) {
        log.info("REST request to chat with agent: {}", request.message());
        AgentChatResponseDTO response = agentService.forwardMessageToAgent(request);
        return ResponseEntity.ok(response);
    }
}
