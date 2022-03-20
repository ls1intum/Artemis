package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.ChatSession;
import de.tum.in.www1.artemis.service.metis.ChatService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing ChatSession.
 */
@RestController
@RequestMapping("/api/courses")
public class ChatSessionResource {

    private final ChatService chatService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public ChatSessionResource(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * GET chatSessions : get all chatSessions for user within course by courseId
     *
     * @param courseId the courseId for chatSession search
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/{courseId}/chatSessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<ChatSession>> getChatSessionsOfUser(@PathVariable Long courseId) {
        List<ChatSession> chatSessions = chatService.getChatSessions(courseId);
        return new ResponseEntity<>(chatSessions, null, HttpStatus.OK);
    }

    /**
     * POST /courses/{courseId}/chatSessions : Create a new chat session
     *
     * @param chatSession     chat session to create
     * @return ResponseEntity with status 201 (Created) containing the created chatSession in the response body,
     * or with status 400 (Bad Request) if the checks on user or course validity fail
     */
    @PostMapping("/{courseId}/chatSessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ChatSession> createChatSession(@PathVariable Long courseId, @Valid @RequestBody ChatSession chatSession) throws URISyntaxException {
        ChatSession createdChatSession = chatService.createChatSession(courseId, chatSession);
        return ResponseEntity.created(new URI("/api/chatSessions/" + createdChatSession.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, chatService.getEntityName(), createdChatSession.getId().toString())).body(createdChatSession);
    }
}
