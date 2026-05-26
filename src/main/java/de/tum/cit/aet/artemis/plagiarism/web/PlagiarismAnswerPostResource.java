package de.tum.cit.aet.artemis.plagiarism.web;

import java.net.URI;
import java.net.URISyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.dto.AnswerPostResponseDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostCreateRequestDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostUpdateRequestDTO;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismAnswerPostService;

/**
 * REST controller for managing AnswerPost.
 */
@Conditional(PlagiarismEnabled.class)
@Lazy
@RestController
@RequestMapping("api/plagiarism/")
public class PlagiarismAnswerPostResource {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismAnswerPostResource.class);

    private final PlagiarismAnswerPostService plagiarismAnswerPostService;

    public PlagiarismAnswerPostResource(PlagiarismAnswerPostService plagiarismAnswerPostService) {
        this.plagiarismAnswerPostService = plagiarismAnswerPostService;
    }

    /**
     * POST /courses/{courseId}/answer-posts : Create a new answer post
     *
     * @param courseId id of the course the post belongs to
     * @param request  create payload carrying the parent post id and the new answer's content
     * @return ResponseEntity with status 201 (Created) containing the created answer post as a
     *         cycle-free DTO, or with status 400 (Bad Request) if the checks on user, course or
     *         associated post validity fail
     */
    @PostMapping("courses/{courseId}/answer-posts")
    @EnforceAtLeastStudent
    public ResponseEntity<AnswerPostResponseDTO> createAnswerPost(@PathVariable Long courseId, @RequestBody PlagiarismAnswerPostCreateRequestDTO request)
            throws URISyntaxException {
        log.debug("POST createAnswerPost invoked for course {} with parent post {}", courseId, request.postId());
        long start = System.nanoTime();
        AnswerPost createdAnswerPost = plagiarismAnswerPostService.createAnswerPost(courseId, request);
        log.info("createAnswerPost took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.created(new URI("/api/plagiarism/courses/" + courseId + "/answer-posts/" + createdAnswerPost.getId()))
                .body(AnswerPostResponseDTO.from(createdAnswerPost));
    }

    /**
     * PUT /courses/{courseId}/answer-posts/{answerPostId} : Update an existing answer post with given id
     *
     * @param courseId     id of the course the answer post belongs to
     * @param answerPostId id of the answer post to update
     * @param request      update payload carrying the fields the caller may mutate
     * @return ResponseEntity with status 200 (OK) containing the updated answer post as a cycle-free
     *         DTO, or with status 400 (Bad Request) if the checks on user, course or associated post
     *         validity fail
     */
    @PutMapping("courses/{courseId}/answer-posts/{answerPostId}")
    @EnforceAtLeastStudent
    public ResponseEntity<AnswerPostResponseDTO> updateAnswerPost(@PathVariable Long courseId, @PathVariable Long answerPostId,
            @RequestBody PlagiarismAnswerPostUpdateRequestDTO request) {
        log.debug("PUT updateAnswerPost invoked for course {} on answer post {}", courseId, answerPostId);
        long start = System.nanoTime();
        AnswerPost updatedAnswerPost = plagiarismAnswerPostService.updateAnswerPost(courseId, answerPostId, request);
        log.info("updatedAnswerPost took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok(AnswerPostResponseDTO.from(updatedAnswerPost));
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
        plagiarismAnswerPostService.deleteAnswerPostById(courseId, answerPostId);
        log.info("deleteAnswerPost took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok().build();
    }
}
