package de.tum.cit.aet.artemis.plagiarism.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismPostService;
import de.tum.cit.aet.artemis.web.rest.dto.PostContextFilterDTO;
import tech.jhipster.web.util.PaginationUtil;

/**
 * REST controller for managing Post.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class PlagiarismPostResource {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismPostResource.class);

    private final PlagiarismPostService plagiarismPostService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public PlagiarismPostResource(PlagiarismPostService plagiarismPostService) {
        this.plagiarismPostService = plagiarismPostService;
    }

    /**
     * POST /courses/{courseId}/posts : Create a new post
     *
     * @param courseId id of the course the post belongs to
     * @param post     post to create
     * @return ResponseEntity with status 201 (Created) containing the created post in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PostMapping("courses/{courseId}/posts")
    @EnforceAtLeastInstructor
    public ResponseEntity<Post> createPost(@PathVariable Long courseId, @Valid @RequestBody Post post) throws URISyntaxException {
        log.debug("POST createPost invoked for course {} with post {}", courseId, post.getContent());
        long start = System.nanoTime();
        Post createdPost = plagiarismPostService.createPost(courseId, post);
        log.info("createPost took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/posts/" + createdPost.getId())).body(createdPost);
    }

    /**
     * PUT /courses/{courseId}/posts/{postId} : Update an existing post with given id
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the post to update
     * @param post     post to update
     * @return ResponseEntity with status 200 (OK) containing the updated post in the response body,
     *         or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PutMapping("courses/{courseId}/posts/{postId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Post> updatePost(@PathVariable Long courseId, @PathVariable Long postId, @RequestBody Post post) {
        log.debug("PUT updatePost invoked for course {} with post {}", courseId, post.getContent());
        long start = System.nanoTime();
        Post updatedPost = plagiarismPostService.updatePost(courseId, postId, post);
        log.info("updatePost took {}", TimeLogUtil.formatDurationFrom(start));
        return new ResponseEntity<>(updatedPost, null, HttpStatus.OK);
    }

    /**
     * GET /courses/{courseId}/posts : Get all posts for a course by its id
     *
     * @param postContextFilter request param for filtering posts
     * @return ResponseEntity with status 200 (OK) and with body all posts for course, that match the specified context
     *         or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @GetMapping("courses/{courseId}/posts")
    @EnforceAtLeastStudent
    public ResponseEntity<List<Post>> getPostsInCourse(PostContextFilterDTO postContextFilter) {
        Page<Post> coursePosts;
        if (postContextFilter.plagiarismCaseId() != null) {
            coursePosts = new PageImpl<>(plagiarismPostService.getAllPlagiarismCasePosts(postContextFilter));
        }
        else {
            throw new BadRequestAlertException("A post cannot be associated with more than one context if plagiarismCaseId is set", plagiarismPostService.getEntityName(),
                    "noPlagiarismCase");
        }
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), coursePosts);

        return new ResponseEntity<>(coursePosts.getContent(), headers, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/posts/{id} : Delete a post by its id
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the post to delete
     * @return ResponseEntity with status 200 (OK),
     *         or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @DeleteMapping("courses/{courseId}/posts/{postId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deletePost(@PathVariable Long courseId, @PathVariable Long postId) {
        log.debug("DELETE deletePost invoked for course {} on post {}", courseId, postId);
        long start = System.nanoTime();
        plagiarismPostService.deletePostById(courseId, postId);
        log.info("deletePost took {}", TimeLogUtil.formatDurationFrom(start));
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, plagiarismPostService.getEntityName(), postId.toString())).build();
    }
}
