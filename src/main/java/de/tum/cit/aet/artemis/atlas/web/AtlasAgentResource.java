package de.tum.cit.aet.artemis.atlas.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_ATLAS_AGENT;

import java.time.ZonedDateTime;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasAgentEnabled;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentService;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;

/**
 * REST controller for Atlas Agent functionality.
 */
@Conditional(AtlasEnabled.class)
@AtlasAgentEnabled
@Profile(PROFILE_ATLAS_AGENT)
@Lazy
@RestController
@RequestMapping("api/atlas/agent/")
public class AtlasAgentResource {

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentResource.class);

    private final AtlasAgentService atlasAgentService;

    public AtlasAgentResource(AtlasAgentService atlasAgentService) {
        this.atlasAgentService = atlasAgentService;
    }

    /**
     * POST /courses/{courseId}/chat : Send a chat message to Atlas Agent
     *
     * @param courseId the course ID for context
     * @param request  the chat request containing the message
     * @return the agent response
     */
    @PostMapping("courses/{courseId}/chat")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<AtlasAgentChatResponseDTO> sendChatMessage(@PathVariable Long courseId, @Valid @RequestBody AtlasAgentChatRequestDTO request) {

        log.debug("Received chat message for course {}: {}", courseId, request.message().substring(0, Math.min(request.message().length(), 50)));

        try {
            // Process the message asynchronously
            String response = atlasAgentService.processChatMessage(request.message(), courseId).get();

            AtlasAgentChatResponseDTO responseDTO = new AtlasAgentChatResponseDTO(response, request.sessionId(), ZonedDateTime.now(), true);

            return ResponseEntity.ok(responseDTO);

        }
        catch (Exception e) {
            log.error("Error processing chat message for course {}: {}", courseId, e.getMessage(), e);

            AtlasAgentChatResponseDTO errorResponse = new AtlasAgentChatResponseDTO("I apologize, but I'm having trouble processing your request right now. Please try again.",
                    request.sessionId(), ZonedDateTime.now(), false);

            return ResponseEntity.ok(errorResponse);
        }
    }
}
