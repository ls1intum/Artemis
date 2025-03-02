package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
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

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.service.AnswerMessageService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/communication/")
public class AnswerMessageResource {

    private static final Logger log = LoggerFactory.getLogger(AnswerMessageResource.class);

    private final AnswerMessageService answerMessageService;

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    public AnswerMessageResource(AnswerMessageService answerMessageService, AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository) {
        this.answerMessageService = answerMessageService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /courses/{courseId}/answer-messages : Create a new answer message
     *
     * @param courseId      id of the course the post belongs to
     * @param answerMessage answer post to create
     * @return ResponseEntity with status 201 (Created) containing the created answer message in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PostMapping("courses/{courseId}/answer-messages")
    @EnforceAtLeastStudent
    public ResponseEntity<AnswerPost> createAnswerMessage(@PathVariable Long courseId, @RequestBody AnswerPost answerMessage) throws URISyntaxException {
        log.debug("POST createAnswerMessage invoked for course {} with message {}", courseId, answerMessage.getContent());
        long start = System.nanoTime();
        AnswerPost createdAnswerMessage = answerMessageService.createAnswerMessage(courseId, answerMessage);
        // creation of answerMessage should not trigger alert
        log.debug("createAnswerMessage took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.created(new URI("/api/communication/courses" + courseId + "/answer-messages/" + createdAnswerMessage.getId())).body(createdAnswerMessage);
    }

    /**
     * PUT /courses/{courseId}/answer-messages/{answerPostId} : Update an existing answer message with given id
     *
     * @param courseId        id of the course the answer post belongs to
     * @param answerMessageId id of the answer post to update
     * @param answerMessage   answer post to update
     * @return ResponseEntity with status 200 (OK) containing the updated answer message in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or associated post validity fail
     */
    @PutMapping("courses/{courseId}/answer-messages/{answerMessageId}")
    @EnforceAtLeastStudent
    public ResponseEntity<AnswerPost> updateAnswerMessage(@PathVariable Long courseId, @PathVariable Long answerMessageId, @RequestBody AnswerPost answerMessage) {
        log.debug("PUT updateAnswerMessage invoked for course {} with message {}", courseId, answerMessage.getContent());
        long start = System.nanoTime();
        AnswerPost updatedAnswerMessage = answerMessageService.updateAnswerMessage(courseId, answerMessageId, answerMessage);
        log.debug("updateAnswerMessage took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(updatedAnswerMessage, null, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/answer-messages/{id} : Delete an answer message by its id
     *
     * @param courseId        id of the course the message belongs to
     * @param answerMessageId id of the answer message to delete
     * @return ResponseEntity with status 200 (OK),
     *         or 400 (Bad Request) if the checks on user or course validity fail
     */
    @DeleteMapping("courses/{courseId}/answer-messages/{answerMessageId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteAnswerMessage(@PathVariable Long courseId, @PathVariable Long answerMessageId) {
        log.debug("PUT deleteAnswerMessage invoked for course {} on message {}", courseId, answerMessageId);
        long start = System.nanoTime();
        answerMessageService.deleteAnswerMessageById(courseId, answerMessageId);
        log.debug("deleteAnswerMessage took {}", TimeLogUtil.formatDurationFrom(start));
        // deletion of answerMessages should not trigger alert
        return ResponseEntity.ok().build();
    }

    /**
     * GET /communication/courses/{courseId}/answer-messages-source-posts : Retrieve source answer posts by their IDs
     *
     * @param courseId      id of the course the answer posts belong to
     * @param answerPostIds list of answer post IDs to retrieve
     * @return ResponseEntity with status 200 (OK) containing the list of found answer posts,
     *         400 (Bad Request) if the provided list is null or empty,
     *         or 404 (Not Found) if no matching answer posts are found
     * @throws BadRequestAlertException if the provided answer post IDs are null, empty, or belong to a different course
     */
    @GetMapping("courses/{courseId}/answer-messages-source-posts")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<AnswerPost>> getSourceAnswerPostsByIds(@PathVariable Long courseId, @RequestParam List<Long> answerPostIds) {
        log.debug("GET getSourceAnswerPostsByIds invoked for course {} with {} posts", courseId, answerPostIds.size());
        long start = System.nanoTime();

        if (answerPostIds == null || answerPostIds.isEmpty()) {
            throw new BadRequestAlertException("AnswerPost IDs cannot be null or empty", answerMessageService.getEntityName(), "invalidAnswerPostIds");
        }

        List<AnswerPost> answerPosts = answerMessageService.findByIdIn(answerPostIds);

        if (answerPosts.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        if (answerPosts.stream().anyMatch(post -> !post.getPost().getConversation().getCourse().getId().equals(courseId))) {
            throw new BadRequestAlertException("Some answer posts do not belong to the specified course", answerMessageService.getEntityName(), "invalidCourse");
        }

        log.debug("getSourceAnswerPostsByIds took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok().body(answerPosts);
    }
}
