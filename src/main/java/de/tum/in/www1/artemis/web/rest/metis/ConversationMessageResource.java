package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.metis.ConversationMessagingService;
import de.tum.in.www1.artemis.service.notifications.ConversationNotificationService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Message Posts.
 */
@RestController
@RequestMapping("/api")
public class ConversationMessageResource {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ConversationMessagingService conversationMessagingService;

    private final ConversationNotificationService conversationNotificationService;

    public ConversationMessageResource(ConversationNotificationService conversationNotificationService, ConversationMessagingService conversationMessagingService) {
        this.conversationMessagingService = conversationMessagingService;
        this.conversationNotificationService = conversationNotificationService;
    }

    /**
     * POST /courses/{courseId}/messages : Create a new message post
     *
     * @param courseId id of the course the message post belongs to
     * @param post     message post to create
     * @return ResponseEntity with status 201 (Created) containing the created message post in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PostMapping("courses/{courseId}/messages")
    @EnforceAtLeastStudent
    public ResponseEntity<Post> createMessage(@PathVariable Long courseId, @Valid @RequestBody Post post) throws URISyntaxException {
        Post createdMessage = conversationMessagingService.createMessage(courseId, post);
        // creation of message posts should not trigger entity creation alert
        conversationNotificationService.notifyAboutNewMessage(createdMessage);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/messages/" + createdMessage.getId())).body(createdMessage);
    }

    /**
     * GET /courses/{courseId}/posts : Get all message posts for a conversation by its id
     *
     * @param pageable          pagination settings to fetch posts in smaller batches
     * @param postContextFilter request param for filtering posts
     * @param principal         contains the login of the user for the purpose of logging
     * @return ResponseEntity with status 200 (OK) and with body all posts for course, that match the specified context
     *         or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @GetMapping("courses/{courseId}/messages")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Post>> getMessages(@ApiParam Pageable pageable, PostContextFilter postContextFilter, Principal principal) {
        long timeNanoStart = System.nanoTime();
        Page<Post> coursePosts = conversationMessagingService.getMessages(pageable, postContextFilter);
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), coursePosts);
        logDuration(coursePosts.getContent(), principal, timeNanoStart);
        return new ResponseEntity<>(coursePosts.getContent(), headers, HttpStatus.OK);
    }

    private void logDuration(List<Post> posts, Principal principal, long timeNanoStart) {
        if (log.isInfoEnabled()) {
            long answerPosts = posts.stream().mapToLong(post -> post.getAnswers().size()).sum();
            long reactions = posts.stream().mapToLong(post -> post.getReactions().size()).sum();
            long answerReactions = posts.stream().flatMap(post -> post.getAnswers().stream()).mapToLong(answerPost -> answerPost.getReactions().size()).sum();
            log.info("/courses/{courseId}/messages finished in {} for {} posts with {} answer posts, {} reactions, and {} answer post reactions for user {}",
                    TimeLogUtil.formatDurationFrom(timeNanoStart), posts.size(), answerPosts, reactions, answerReactions, principal.getName());
        }
    }

    /**
     * PUT /courses/{courseId}/messages/{messageId} : Update an existing message post with given id
     *
     * @param courseId    id of the course the message post belongs to
     * @param messageId   id of the message post to update
     * @param messagePost message post to update
     * @return ResponseEntity with status 200 (OK) containing the updated message post in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PutMapping("courses/{courseId}/messages/{messageId}")
    @EnforceAtLeastStudent
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
     *         or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @DeleteMapping("courses/{courseId}/messages/{messageId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteMessage(@PathVariable Long courseId, @PathVariable Long messageId) {
        conversationMessagingService.deleteMessageById(courseId, messageId);
        // deletion of message posts should not trigger entity deletion alert
        return ResponseEntity.ok().build();
    }
}
