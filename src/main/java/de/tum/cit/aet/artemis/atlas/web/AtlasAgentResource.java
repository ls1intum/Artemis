package de.tum.cit.aet.artemis.atlas.web;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
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
import de.tum.cit.aet.artemis.atlas.dto.ChatHistoryMessageDTO;
import de.tum.cit.aet.artemis.atlas.service.AtlasAgentService;
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

    private static final Logger log = LoggerFactory.getLogger(AtlasAgentResource.class);

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
     * @param request  the chat request containing the message
     * @return the agent response
     */
    @PostMapping("courses/{courseId}/chat")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<AtlasAgentChatResponseDTO> sendChatMessage(@PathVariable Long courseId, @Valid @RequestBody AtlasAgentChatRequestDTO request) {

        log.debug("Received chat message for course {}: {}", courseId, request.message().substring(0, Math.min(request.message().length(), 50)));

        try {
            final var future = atlasAgentService.processChatMessage(request.message(), courseId, request.sessionId());
            final var result = future.get(CHAT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            return ResponseEntity.ok(new AtlasAgentChatResponseDTO(result.message(), request.sessionId(), ZonedDateTime.now(), true, result.competenciesModified()));
        }
        catch (TimeoutException te) {
            log.warn("Chat timed out for course {}: {}", courseId, te.getMessage());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .body(new AtlasAgentChatResponseDTO("The agent timed out. Please try again.", request.sessionId(), ZonedDateTime.now(), false, false));
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Chat interrupted for course {}: {}", courseId, ie.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new AtlasAgentChatResponseDTO("The request was interrupted. Please try again.", request.sessionId(), ZonedDateTime.now(), false, false));
        }
        catch (ExecutionException ee) {
            log.error("Upstream error processing chat for course {}: {}", courseId, ee.getMessage(), ee);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new AtlasAgentChatResponseDTO("Upstream error while processing your request.", request.sessionId(), ZonedDateTime.now(), false, false));
        }
    }

    /**
     * GET /courses/{courseId}/history : Retrieve conversation history for the current user in a course
     *
     * @param courseId the course ID to retrieve history for
     * @return list of historical messages for the current user
     */
    @GetMapping("courses/{courseId}/history")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<ChatHistoryMessageDTO>> getConversationHistory(@PathVariable Long courseId) {
        var currentUser = userRepository.getUser();
        log.debug("Retrieving conversation history for course {} and user {}", courseId, currentUser.getId());

        String sessionId = "course_" + courseId + "_user_" + currentUser.getId();
        List<Message> messages = atlasAgentService.getConversationHistory(sessionId);

        // Convert Spring AI Message objects to DTOs, filtering out system messages
        List<ChatHistoryMessageDTO> history = messages.stream().filter(msg -> !(msg instanceof SystemMessage)).map(this::convertToDTO)
                .collect(Collectors.toCollection(ArrayList::new));
        log.debug("Returning {} historical messages for course {} and user {}", history.size(), courseId, currentUser.getId());
        return ResponseEntity.ok(history);
    }

    /**
     * Convert Spring AI Message to DTO
     *
     * @param message the Spring AI message
     * @return DTO with role and content
     */
    private ChatHistoryMessageDTO convertToDTO(Message message) {
        String role = switch (message) {
            case UserMessage um -> "user";
            case AssistantMessage am -> "assistant";
            case SystemMessage sm -> "system";
            default -> "unknown";
        };

        return new ChatHistoryMessageDTO(role, message.getText());
    }
}
