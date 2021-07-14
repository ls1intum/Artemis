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

import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.service.metis.PostService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Post.
 */
@RestController
@RequestMapping("/api")
public class PostResource {

    private final PostService postService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public PostResource(PostService postService) {
        this.postService = postService;
    }

    /**
     * POST /courses/{courseId}/posts : Create a new post
     *
     * @param courseId id of the course the post belongs to
     * @param post     post to create
     * @return ResponseEntity with status 201 (Created) containing the created post in the response body,
     * or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PostMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> createPost(@PathVariable Long courseId, @Valid @RequestBody Post post) throws URISyntaxException {
        Post createdPost = postService.createPost(courseId, post);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/posts/" + createdPost.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, postService.getEntityName(), createdPost.getId().toString())).body(createdPost);
    }

    /**
     * PUT /courses/{courseId}/posts : Update an existing post
     *
     * @param courseId id of the course the post belongs to
     * @param post     post to update
     * @return ResponseEntity with status 200 (OK) containing the updated post in the response body,
     * or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PutMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> updatePost(@PathVariable Long courseId, @RequestBody Post post) {
        Post updatedPost = postService.updatePost(courseId, post);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, postService.getEntityName(), updatedPost.getId().toString()))
                .body(updatedPost);
    }

    /**
     * PUT /courses/{courseId}/posts/{postId}/votes : Vote on an existing post
     *
     * @param courseId   id of the course the post belongs to
     * @param postId     id of the post to vote on
     * @param voteChange value by which votes are increased / decreased
     * @return ResponseEntity with status 200 (OK) containing the updated post in the response body,
     * or with status 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @PutMapping("courses/{courseId}/posts/{postId}/votes")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> updatePostVotes(@PathVariable Long courseId, @PathVariable Long postId, @RequestBody Integer voteChange) {
        Post postWithUpdatedVotes = postService.updatePostVotes(courseId, postId, voteChange);
        return ResponseEntity.ok().body(postWithUpdatedVotes);
    }

    /**
     * GET /courses/{courseId}/exercises/{exerciseId}/posts : Get all posts for an exercise by its id
     *
     * @param courseId   id of the course the post belongs to
     * @param exerciseId id of the exercise for which the posts should be retrieved
     * @return ResponseEntity with status 200 (OK) containing the a list of posts in the response body,
     * or 400 (Bad Request) if the checks on user, course, exercise or post validity fail
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Post>> getAllPostsForExercise(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        List<Post> exercisePosts = postService.getAllExercisePosts(courseId, exerciseId);
        return new ResponseEntity<>(exercisePosts, null, HttpStatus.OK);
    }

    /**
     * GET /courses/{courseId}/lectures/{lectureId}/posts : Get all posts a lecture by its id
     *
     * @param courseId  id of the course the post belongs to
     * @param lectureId id of the lecture for which the posts should be retrieved
     * @return ResponseEntity with status 200 (OK) containing the a list of posts in the response body,
     * or 400 (Bad Request) if the checks on user, course, lecture or post validity fail
     */
    @GetMapping("courses/{courseId}/lectures/{lectureId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Post>> getAllPostsForLecture(@PathVariable Long courseId, @PathVariable Long lectureId) {
        List<Post> lecturePosts = postService.getAllLecturePosts(courseId, lectureId);
        return new ResponseEntity<>(lecturePosts, null, HttpStatus.OK);
    }

    /**
     * GET /courses/{courseId}/posts : Get all posts for a course by its id
     *
     * @param courseId id of the course the post belongs to
     * @return ResponseEntity with status 200 (OK) and with body all posts for course,
     * or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @GetMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Post>> getAllPostsForCourse(@PathVariable Long courseId) {
        List<Post> coursePosts = postService.getAllCoursePosts(courseId);
        return new ResponseEntity<>(coursePosts, null, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/posts/{id} : Delete a post by its id
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the post to delete
     * @return ResponseEntity with status 200 (OK),
     * or 400 (Bad Request) if the checks on user, course or post validity fail
     */
    @DeleteMapping("courses/{courseId}/posts/{postId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deletePost(@PathVariable Long courseId, @PathVariable Long postId) {
        postService.deletePostById(courseId, postId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, postService.getEntityName(), postId.toString())).build();
    }
}
