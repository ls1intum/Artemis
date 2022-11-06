package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.metis.ConversationMessagingService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Message Posts.
 */
@RestController
@RequestMapping("/api")
public class ConversationMessageResource {

    private final ConversationMessagingService conversationMessagingService;

    public ConversationMessageResource(ConversationMessagingService conversationMessagingService) {
        this.conversationMessagingService = conversationMessagingService;
    }

    /**
     * POST /courses/{courseId}/messages : Create a new message post
     *
     * @param courseId id of the course the message post belongs to
     * @param post     message post to create
     * @return ResponseEntity with status 201 (Created) containing the created message post in the response body,
     * or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PostMapping("courses/{courseId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> createMessage(@PathVariable Long courseId, @Valid @RequestBody Post post) throws URISyntaxException {
        Post createdMessage = conversationMessagingService.createMessage(courseId, post);
        // creation of message posts should not trigger entity creation alert
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/messages/" + createdMessage.getId())).body(createdMessage);
    }

    /**
     * GET /courses/{courseId}/posts : Get all message posts for a conversation by its id
     *
     * @param pageable                  pagination settings to fetch posts in smaller batches
     * @param postContextFilter         request param for filtering posts
     * @return ResponseEntity with status 200 (OK) and with body all posts for course, that match the specified context
     * or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @GetMapping("courses/{courseId}/messages")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Post>> getMessages(@ApiParam Pageable pageable, PostContextFilter postContextFilter) {

        Page<Post> coursePosts = conversationMessagingService.getMessages(pageable, postContextFilter);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), coursePosts);

        return new ResponseEntity<>(coursePosts.getContent(), headers, HttpStatus.OK);
    }

    /**
     * PUT /courses/{courseId}/messages/{messageId} : Update an existing message post with given id
     *
     * @param courseId  id of the course the message post belongs to
     * @param messageId id of the message post to update
     * @param messagePost      message post to update
     * @return ResponseEntity with status 200 (OK) containing the updated message post in the response body,
     * or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PutMapping("courses/{courseId}/messages/{messageId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> updateMessage(@PathVariable Long courseId, @PathVariable Long messageId, @RequestBody Post messagePost) {
        Post updatedMessagePost = conversationMessagingService.updateMessage(courseId, messageId, messagePost);
        return new ResponseEntity<>(updatedMessagePost, null, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/messages/{id} : Delete a message post by its id
     *
     * @param courseId  id of the course the message post belongs to
     * @param messageId id of the message post to delete
     * @return ResponseEntity with status 200 (OK),
     * or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @DeleteMapping("courses/{courseId}/messages/{messageId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteMessage(@PathVariable Long courseId, @PathVariable Long messageId) {
        conversationMessagingService.deleteMessageById(courseId, messageId);
        // deletion of message posts should not trigger entity deletion alert
        return ResponseEntity.ok().build();
    }
}
