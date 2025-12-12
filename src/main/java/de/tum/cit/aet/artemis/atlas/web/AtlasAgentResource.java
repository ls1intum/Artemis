package de.tum.cit.aet.artemis.atlas.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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

    private final AtlasAgentService atlasAgentService;

    private final UserRepository userRepository;

    public AtlasAgentResource(AtlasAgentService atlasAgentService, UserRepository userRepository) {
        this.atlasAgentService = atlasAgentService;
        this.userRepository = userRepository;
    }

    /**
     * POST /courses/{courseId}/chat : Send a chat message to Atlas Agent
     * The sessionId is generated server-side based on the authenticated user and course for security.
     *
     * @param courseId the course ID for context
     * @param request  the chat request containing the message
     * @return the agent response
     */
    @PostMapping("courses/{courseId}/chat")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<AtlasAgentChatResponseDTO> sendChatMessage(@PathVariable Long courseId, @Valid @RequestBody AtlasAgentChatRequestDTO request) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        String sessionId = atlasAgentService.generateSessionId(courseId, user.getId());

        AtlasAgentChatResponseDTO result = atlasAgentService.processChatMessage(request.message(), courseId, sessionId);
        return ResponseEntity.ok(result);
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
        String sessionId = atlasAgentService.generateSessionId(courseId, user.getId());

        List<AtlasAgentHistoryMessageDTO> history = atlasAgentService.getConversationHistoryAsDTO(sessionId);
        return ResponseEntity.ok(history);
    }
}
