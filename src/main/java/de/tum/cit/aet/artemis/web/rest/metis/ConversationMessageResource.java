package de.tum.cit.aet.artemis.web.rest.metis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.Collections;
import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.communication.domain.CreatedConversationMessage;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.domain.enumeration.DisplayPriority;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.metis.ConversationMessagingService;
import de.tum.cit.aet.artemis.service.util.TimeLogUtil;
import de.tum.cit.aet.artemis.web.rest.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import io.swagger.annotations.ApiParam;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Message Posts.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ConversationMessageResource {

    private static final Logger log = LoggerFactory.getLogger(ConversationMessageResource.class);

    private final ConversationMessagingService conversationMessagingService;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public ConversationMessageResource(ConversationMessagingService conversationMessagingService, UserRepository userRepository,
            AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.conversationMessagingService = conversationMessagingService;
        this.userRepository = userRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
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
        log.debug("POST createMessage invoked for course {} with post {}", courseId, post.getContent());
        long start = System.nanoTime();
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new message post cannot already have an ID", conversationMessagingService.getEntityName(), "idexists");
        }
        if (post.getConversation() == null || post.getConversation().getId() == null) {
            throw new BadRequestAlertException("A new message post must have a conversation", conversationMessagingService.getEntityName(), "conversationnotset");
        }
        CreatedConversationMessage createdMessageData = conversationMessagingService.createMessage(courseId, post);
        conversationMessagingService.notifyAboutMessageCreation(createdMessageData);

        Post sendToUserPost = createdMessageData.messageWithHiddenDetails();
        sendToUserPost.setConversation(sendToUserPost.getConversation().copy());
        sendToUserPost.getConversation().setConversationParticipants(Collections.emptySet());

        log.info("createMessage took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/messages/" + sendToUserPost.getId())).body(sendToUserPost);
    }

    /**
     * GET /courses/{courseId}/posts : Get all messages for a conversation by its id or in a list of course-wide channels
     *
     * @param pageable          pagination settings to fetch messages in smaller batches
     * @param postContextFilter request param for filtering messages
     * @param principal         contains the login of the user for the purpose of logging
     * @return ResponseEntity with status 200 (OK) and with body all posts for course, that match the specified context
     *         or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @GetMapping("courses/{courseId}/messages")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Post>> getMessages(@ApiParam Pageable pageable, PostContextFilterDTO postContextFilter, Principal principal) {
        long timeNanoStart = System.nanoTime();
        Page<Post> coursePosts;

        final var requestingUser = userRepository.getUserWithGroupsAndAuthorities();
        final var course = courseRepository.findByIdElseThrow(postContextFilter.courseId());
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, requestingUser);

        if (postContextFilter.conversationId() != null) {
            coursePosts = conversationMessagingService.getMessages(pageable, postContextFilter, requestingUser, course.getId());
        }
        else if (postContextFilter.courseWideChannelIds() != null) {
            coursePosts = conversationMessagingService.getCourseWideMessages(pageable, postContextFilter, requestingUser, course.getId());
        }
        else {
            throw new BadRequestAlertException("Messages must be associated with a conversion", conversationMessagingService.getEntityName(), "conversationMissing");
        }
        // keep the data as small as possible and avoid unnecessary information sent to the client
        // TODO: in the future we should use a DTO and send only the necessary information
        coursePosts.getContent().forEach(post -> {
            if (post.getConversation() != null) {
                post.getConversation().hideDetails();
            }
        });
        final var headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), coursePosts);
        logDuration(coursePosts.getContent(), principal, timeNanoStart);
        return new ResponseEntity<>(coursePosts.getContent(), headers, HttpStatus.OK);
    }

    private void logDuration(List<Post> posts, Principal principal, long timeNanoStart) {
        if (log.isInfoEnabled()) {
            long answerPosts = posts.stream().mapToLong(post -> post.getAnswers().size()).sum();
            long reactions = posts.stream().mapToLong(post -> post.getReactions().size()).sum();
            long answerReactions = posts.stream().flatMap(post -> post.getAnswers().stream()).mapToLong(answerPost -> answerPost.getReactions().size()).sum();
            log.debug("/courses/{courseId}/messages finished in {} for {} posts with {} answer posts, {} reactions, and {} answer post reactions for user {}",
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
        log.debug("PUT updateMessage invoked for course {} with post {}", courseId, messagePost.getContent());
        long start = System.nanoTime();
        Post updatedMessagePost = conversationMessagingService.updateMessage(courseId, messageId, messagePost);
        log.info("updateMessage took {}", TimeLogUtil.formatDurationFrom(start));
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
        log.debug("DELETE deleteMessage invoked for course {} on message {}", courseId, messageId);
        long start = System.nanoTime();
        conversationMessagingService.deleteMessageById(courseId, messageId);
        // deletion of message posts should not trigger entity deletion alert
        log.info("deleteMessage took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok().build();
    }

    /**
     * PUT /courses/{courseId}/posts/{postId}/display-priority : Update the display priority of an existing post
     *
     * @param courseId        id of the course the post belongs to
     * @param postId          id of the post change the displayPriority for
     * @param displayPriority new enum value for displayPriority, i.e. either PINNED, ARCHIVED, NONE
     * @return ResponseEntity with status 200 (OK) containing the updated post in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PutMapping("courses/{courseId}/messages/{postId}/display-priority")
    @EnforceAtLeastStudent
    public ResponseEntity<Post> updateDisplayPriority(@PathVariable Long courseId, @PathVariable Long postId, @RequestParam DisplayPriority displayPriority) {
        Post postWithUpdatedDisplayPriority = conversationMessagingService.changeDisplayPriority(courseId, postId, displayPriority);
        return ResponseEntity.ok().body(postWithUpdatedDisplayPriority);
    }

    /**
     * POST /courses/{courseId}/messages/similarity-check : trigger a similarity check for post to be created
     *
     * @param courseId id of the course the post should be published in
     * @param post     post to create
     * @return ResponseEntity with status 200 (OK)
     */
    @PostMapping("courses/{courseId}/messages/similarity-check")
    @EnforceAtLeastStudent
    // TODO: unused, remove
    public ResponseEntity<List<Post>> computeSimilarityScoresWitCoursePosts(@PathVariable Long courseId, @RequestBody Post post) {
        List<Post> similarPosts = conversationMessagingService.getSimilarPosts(courseId, post);
        return ResponseEntity.ok().body(similarPosts);
    }

    /**
     * GET /courses/{courseId}/posts/tags : Get all tags for posts in a certain course
     *
     * @param courseId id of the course the post belongs to
     * @return the ResponseEntity with status 200 (OK) and with body all tags for posts in that course,
     *         or 400 (Bad Request) if the checks on user or course validity fail
     */
    @GetMapping("courses/{courseId}/messages/tags")
    // TODO: unused, delete
    @EnforceAtLeastStudent
    public ResponseEntity<List<String>> getAllPostTagsForCourse(@PathVariable Long courseId) {
        List<String> tags = conversationMessagingService.getAllCourseTags(courseId);
        return new ResponseEntity<>(tags, null, HttpStatus.OK);
    }
}
