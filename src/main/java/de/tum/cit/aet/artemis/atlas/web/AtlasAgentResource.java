package de.tum.cit.aet.artemis.atlas.web;

import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.validation.Valid;

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
import de.tum.cit.aet.artemis.atlas.service.AgentChatResult;
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
        try {
            final CompletableFuture<AgentChatResult> future = atlasAgentService.processChatMessage(request.message(), courseId, request.sessionId());
            final AgentChatResult result = future.get(CHAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return ResponseEntity.ok(new AtlasAgentChatResponseDTO(result.message(), request.sessionId(), ZonedDateTime.now(), true, result.competenciesModified()));
        }
        catch (TimeoutException te) {
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new AtlasAgentChatResponseDTO("The agent timed out. Please try again.", request.sessionId(), ZonedDateTime.now(), false, false));
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AtlasAgentChatResponseDTO("The request was interrupted. Please try again.", request.sessionId(), ZonedDateTime.now(), false, false));
        }
        catch (ExecutionException ee) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new AtlasAgentChatResponseDTO("Upstream error while processing your request.", request.sessionId(), ZonedDateTime.now(), false, false));
        }
    }
}
