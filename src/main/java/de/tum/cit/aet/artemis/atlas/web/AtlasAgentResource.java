package de.tum.cit.aet.artemis.atlas.web;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatRequestDTO;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentChatResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.AtlasAgentHistoryMessageDTO;
import de.tum.cit.aet.artemis.atlas.service.AgentChatResult;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
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

    private final UserRepository userRepository;

    public AtlasAgentResource(AtlasAgentService atlasAgentService, UserRepository userRepository) {
        this.atlasAgentService = atlasAgentService;
        this.userRepository = userRepository;
    }

    /**
     * POST /courses/{courseId}/chat : Send a chat message to Atlas Agent
     *
     * @param courseId the course ID for context
     * @param request  the chat request containing the message and sessionId
     * @return the agent response
     */
    @PostMapping("courses/{courseId}/chat")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<AtlasAgentChatResponseDTO> sendChatMessage(@PathVariable Long courseId, @Valid @RequestBody AtlasAgentChatRequestDTO request) {

        // Validate sessionId is provided
        if (request.sessionId() == null || request.sessionId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AtlasAgentChatResponseDTO("Session ID is required for maintaining conversation context.", null, ZonedDateTime.now(), false, false));
        }

        try {
            final var future = atlasAgentService.processChatMessage(request.message(), courseId, request.sessionId());
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

    /**
     * GET /courses/{courseId}/chat/history : Get conversation history for the current user
     *
     * @param courseId the course ID
     * @return list of conversation messages
     */
    @GetMapping("courses/{courseId}/chat/history")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<AtlasAgentHistoryMessageDTO>> getConversationHistory(@PathVariable Long courseId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        String sessionId = String.format("course_%d_user_%d", courseId, user.getId());

        List<AtlasAgentHistoryMessageDTO> history = atlasAgentService.getConversationHistoryAsDTO(sessionId);
        return ResponseEntity.ok(history);
    }
}
