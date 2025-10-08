package de.tum.cit.aet.artemis.atlas.web;

import java.time.ZonedDateTime;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentService;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;

/**
 * REST controller for Atlas Agent functionality.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@RestController
@RequestMapping("api/atlas/agent/")
public class AtlasAgentResource {

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentResource.class);

    private static final int CHAT_TIMEOUT_SECONDS = 30;

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
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<AtlasAgentChatResponseDTO> sendChatMessage(@PathVariable Long courseId, @Valid @RequestBody AtlasAgentChatRequestDTO request) {

        log.debug("Received chat message for course {}: {}", courseId, request.message().substring(0, Math.min(request.message().length(), 50)));

        try {
            final var future = atlasAgentService.processChatMessage(request.message(), courseId);
            final String response = future.get(CHAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return ResponseEntity.ok(new AtlasAgentChatResponseDTO(response, request.sessionId(), ZonedDateTime.now(), true));
        }
        catch (TimeoutException te) {
            log.warn("Chat timed out for course {}: {}", courseId, te.getMessage());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new AtlasAgentChatResponseDTO("The agent timed out. Please try again.", request.sessionId(), ZonedDateTime.now(), false));
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Chat interrupted for course {}: {}", courseId, ie.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AtlasAgentChatResponseDTO("The request was interrupted. Please try again.", request.sessionId(), ZonedDateTime.now(), false));
        }
        catch (ExecutionException ee) {
            log.error("Upstream error processing chat for course {}: {}", courseId, ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new AtlasAgentChatResponseDTO("Upstream error while processing your request.", request.sessionId(), ZonedDateTime.now(), false));
        }
    }
}
