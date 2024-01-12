package de.tum.in.www1.artemis.web.rest.metis;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.metis.AnswerPostService;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

/**
 * REST controller for managing AnswerPost.
 */
@RestController
@RequestMapping("/api")
public class AnswerPostResource {

    private static final Logger log = LoggerFactory.getLogger(AnswerPostResource.class);

    private final AnswerPostService answerPostService;

    public AnswerPostResource(AnswerPostService answerPostService) {
        this.answerPostService = answerPostService;
    }

    /**
     * POST /courses/{courseId}/answer-posts : Create a new answer post
     *
     * @param courseId   id of the course the post belongs to
     * @param answerPost answer post to create
     * @return ResponseEntity with status 201 (Created) containing the created answer post in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PostMapping("courses/{courseId}/answer-posts")
    @EnforceAtLeastStudent
    public ResponseEntity<AnswerPost> createAnswerPost(@PathVariable Long courseId, @RequestBody AnswerPost answerPost) throws URISyntaxException {
        log.debug("POST createAnswerPost invoked for course {} with post {}", courseId, answerPost.getContent());
        long start = System.nanoTime();
        AnswerPost createdAnswerPost = answerPostService.createAnswerPost(courseId, answerPost);
        log.info("createAnswerPost took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.created(new URI("/api/courses" + courseId + "/answer-posts/" + createdAnswerPost.getId())).body(createdAnswerPost);
    }

    /**
     * PUT /courses/{courseId}/answer-posts/{answerPostId} : Update an existing answer post with given id
     *
     * @param courseId     id of the course the answer post belongs to
     * @param answerPostId id of the answer post to update
     * @param answerPost   answer post to update
     * @return ResponseEntity with status 200 (OK) containing the updated answer post in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or associated post validity fail
     */
    @PutMapping("courses/{courseId}/answer-posts/{answerPostId}")
    @EnforceAtLeastStudent
    public ResponseEntity<AnswerPost> updateAnswerPost(@PathVariable Long courseId, @PathVariable Long answerPostId, @RequestBody AnswerPost answerPost) {
        log.debug("PUT updateAnswerPost invoked for course {} with post {}", courseId, answerPost.getContent());
        long start = System.nanoTime();
        AnswerPost updatedAnswerPost = answerPostService.updateAnswerPost(courseId, answerPostId, answerPost);
        log.info("updatedAnswerPost took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(updatedAnswerPost, null, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/posts/{id} : Delete an answer post by its id
     *
     * @param courseId     id of the course the post belongs to
     * @param answerPostId id of the answer post to delete
     * @return ResponseEntity with status 200 (OK),
     *         or 400 (Bad Request) if the checks on user or course validity fail
     */
    @DeleteMapping("courses/{courseId}/answer-posts/{answerPostId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> deleteAnswerPost(@PathVariable Long courseId, @PathVariable Long answerPostId) {
        log.debug("PUT deleteAnswerPost invoked for course {} on post {}", courseId, answerPostId);
        long start = System.nanoTime();
        answerPostService.deleteAnswerPostById(courseId, answerPostId);
        log.info("deleteAnswerPost took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok().build();
    }
}
