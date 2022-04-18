package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.Conversation;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.metis.ConversationService;

/**
 * REST controller for managing Conversation.
 */
@RestController
@RequestMapping("/api/courses")
public class ConversationResource {

    private final ConversationService conversationService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public ConversationResource(ConversationService conversationService, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.conversationService = conversationService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET /courses/{courseId}/conversations : get all conversations for user within course by courseId
     *
     * @param courseId the courseId which the searched conversations belong to
     * @return the ResponseEntity with status 200 (OK) and with body
     */
    @GetMapping("/{courseId}/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Conversation>> getConversationsOfUser(@PathVariable Long courseId) {
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), null);

        List<Conversation> conversations = conversationService.getConversationsOfUser(courseId);
        return new ResponseEntity<>(conversations, null, HttpStatus.OK);
    }

    /**
     * POST /courses/{courseId}/conversations : create a new conversation
     *
     * @param courseId        course to associate the new conversation
     * @param conversation    conversation to create
     * @return ResponseEntity with status 201 (Created) containing the created conversation in the response body,
     * or with status 400 (Bad Request) if the checks on user or course validity fail
     */
    @PostMapping("/{courseId}/conversations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Conversation> createConversation(@PathVariable Long courseId, @Valid @RequestBody Conversation conversation) throws URISyntaxException {
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, courseRepository.findByIdElseThrow(courseId), null);

        Conversation createdConversation = conversationService.createConversation(courseId, conversation);
        return ResponseEntity.created(new URI("/api/conversations/" + createdConversation.getId())).body(createdConversation);
    }
}
