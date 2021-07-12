package de.tum.in.www1.artemis.web.rest.metis;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Post.
 */
@RestController
@RequestMapping("/api")
public class PostResource {

    private final Logger log = LoggerFactory.getLogger(PostResource.class);

    private static final String ENTITY_NAME = "metis.post";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final PostRepository postRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureRepository lectureRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final GroupNotificationService groupNotificationService;

    public PostResource(PostRepository postRepository, GroupNotificationService groupNotificationService, LectureRepository lectureRepository,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, ExerciseRepository exerciseRepository, CourseRepository courseRepository) {
        this.postRepository = postRepository;
        this.groupNotificationService = groupNotificationService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * POST /courses/{courseId}/posts : Create a new post.
     *
     * @param courseId course the post belongs to
     * @param post the post to create
     * @return the ResponseEntity with status 201 (Created) and with body the new post, or with status 400 (Bad Request) if the post
     * already has an ID or the courseId in the body doesn't match the PathVariable
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> createPost(@PathVariable Long courseId, @Valid @RequestBody Post post) throws URISyntaxException {
        if (!post.getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesn't match the courseId of the sent Post in Body");
        }
        log.debug("REST request to save Post : {}", post);
        User user = this.userRepository.getUserWithGroupsAndAuthorities();
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new post cannot already have an ID", ENTITY_NAME, "idexists");
        }
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // set author to current user
        post.setAuthor(user);
        Post savedPost = postRepository.save(post);
        if (savedPost.getExercise() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForExercise(savedPost);

            // Protect Sample Solution, Grading Instructions, etc.
            savedPost.getExercise().filterSensitiveInformation();
        }
        if (savedPost.getLecture() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForLecture(savedPost);
        }
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/posts/" + savedPost.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedPost.getId().toString())).body(savedPost);
    }

    /**
     * PUT /courses/{courseId}/posts : Updates an existing post.
     *
     * @param courseId course the post belongs to
     * @param post the post to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated post, or with status 400 (Bad Request) if the post is not valid, or with
     *         status 500 (Internal Server Error) if the post couldn't be updated
     */
    @PutMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> updatePost(@PathVariable Long courseId, @RequestBody Post post) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        log.debug("REST request to update Post : {}", post);
        if (post.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        courseRepository.findByIdElseThrow(courseId);
        Post existingPost = postRepository.findByIdElseThrow(post.getId());
        if (!existingPost.getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesnt match courseId of the Post that should be changed");
        }
        mayUpdateOrDeletePostElseThrow(existingPost, user);
        existingPost.setTitle(post.getTitle());
        existingPost.setContent(post.getContent());
        existingPost.setVisibleForStudents(post.isVisibleForStudents());
        existingPost.setTags(post.getTags());
        Post result = postRepository.save(existingPost);

        if (result.getExercise() != null) {
            // Protect Sample Solution, Grading Instructions, etc.
            result.getExercise().filterSensitiveInformation();
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, post.getId().toString())).body(result);
    }

    /**
     * PUT /courses/{courseId}/posts/{postId}/votes : Updates votes for a post.
     *
     * @param courseId course the post belongs to
     * @param postId the ID of the post to update
     * @param voteChange value by which votes are increased / decreased
     * @return the ResponseEntity with status 200 (OK) and with body the updated post, or with status 400 (Bad Request) if the post or the voteChanges are invalid, or with
     *         status 500 (Internal Server Error) if the post couldn't be updated
     */
    @PutMapping("courses/{courseId}/posts/{postId}/votes")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> updatePostVotes(@PathVariable Long courseId, @PathVariable Long postId, @RequestBody Integer voteChange) {
        if (voteChange < -2 || voteChange > 2) {
            return badRequest("voteChange", "400", "voteChange must be >= -2 and <= 2");
        }
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        Post post = postRepository.findByIdElseThrow(postId);
        courseRepository.findByIdElseThrow(courseId);
        if (!post.getCourse().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesnt match courseId of the Post that should be changed");
        }
        mayUpdatePostVotesElseThrow(post, user);
        Integer newVotes = post.getVotes() + voteChange;
        post.setVotes(newVotes);
        Post result = postRepository.save(post);
        if (result.getExercise() != null) {
            // Protect Sample Solution, Grading Instructions, etc.
            result.getExercise().filterSensitiveInformation();
        }
        return ResponseEntity.ok().body(result);
    }

    /**
     * GET /courses/{courseId}/exercises/{exerciseId}/posts : get all posts for exercise.
     *
     * @param courseId course the post belongs to
     * @param exerciseId the exercise that the posts belong to
     * @return the ResponseEntity with status 200 (OK) and with body all posts for exercise or 400 (Bad Request) if exercises courseId doesnt match
     * the PathVariable courseId
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Post>> getAllPostsForExercise(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (!exercise.getCourseViaExerciseGroupOrCourseMember().getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesnt match courseId of the exercise that should be returned");
        }
        List<Post> posts = postRepository.findPostsByExercise_Id(exerciseId);
        // Protect Sample Solution, Grading Instructions, etc.
        posts.forEach(post -> post.getExercise().filterSensitiveInformation());
        return new ResponseEntity<>(posts, null, HttpStatus.OK);
    }

    /**
     * GET /courses/{courseId}/lectures/{lectureId}/posts : get all posts for lecture.
     *
     * @param courseId course the post belongs to
     * @param lectureId the lecture that the posts belong to
     * @return the ResponseEntity with status 200 (OK) and with body all posts for lecture or 400 (Bad Request) if the lectures courseId doesnt match
     * the PathVariable courseId
     */
    @GetMapping("courses/{courseId}/lectures/{lectureId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Post>> getAllPostsForLecture(@PathVariable Long courseId, @PathVariable Long lectureId) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        courseRepository.findByIdElseThrow(courseId);
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        if (lecture.getCourse().getId().equals(courseId)) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, lecture.getCourse(), user);
            List<Post> posts = postRepository.findPostsByLecture_Id(lectureId);
            // Protect Sample Solution, Grading Instructions, etc.
            posts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);
            return new ResponseEntity<>(posts, null, HttpStatus.OK);
        }
        else {
            return badRequest("courseId", "400", "PathVariable courseId and the courseId of the Lecture dont match");
        }
    }

    /**
     * GET /courses/{courseId}/posts/tags : get all tags for posts in a certain course.
     *
     * @param courseId course the postTags belongs to
     * @return the ResponseEntity with status 200 (OK) and with body all tags for posts in that course
     */
    @GetMapping("courses/{courseId}/posts/tags")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<String>> getAllPostTagsForCourse(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        List<String> tags = postRepository.findPostTagsForCourse(courseId);
        return new ResponseEntity<>(tags, null, HttpStatus.OK);
    }

    /**
     *
     * GET /courses/{courseId}/posts : get all posts for course
     * @param courseId the course that the posts belong to
     * @return the ResponseEntity with status 200 (OK) and with body all posts for course
     */
    @GetMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Post>> getAllPostsForCourse(@PathVariable Long courseId) {
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<Post> posts = postRepository.findPostsForCourse(courseId);
        // Protect Sample Solution, Grading Instructions, etc.
        posts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);
        return new ResponseEntity<>(posts, null, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/posts/:id : delete the post with {id}.
     *
     * @param courseId course the post belongs to
     * @param postId the id of the post to delete
     * @return the ResponseEntity with status 200 (OK) or 400 (Bad Request) if the data is inconsistent
     */
    @DeleteMapping("courses/{courseId}/posts/{postId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deletePost(@PathVariable Long courseId, @PathVariable Long postId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        courseRepository.findByIdElseThrow(courseId);
        Post post = postRepository.findByIdElseThrow(postId);
        Course course = post.getCourse();
        String entity = "";
        if (post.getLecture() != null) {
            entity = "lecture with id: " + post.getLecture().getId();
        }
        else if (post.getExercise() != null) {
            entity = "exercise with id: " + post.getExercise().getId();
        }
        if (post.getCourse() == null) {
            return ResponseEntity.badRequest().build();
        }
        if (!course.getId().equals(courseId)) {
            return badRequest("courseId", "400", "PathVariable courseId doesnt match courseId of the AnswerPost that should be deleted");
        }
        mayUpdateOrDeletePostElseThrow(post, user);
        log.info("Post deleted by " + user.getLogin() + ". Post: " + post.getContent() + " for " + entity);
        postRepository.deleteById(postId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, postId.toString())).build();

    }

    /**
     * Check if user can update or delete Post, if not throws an AccessForbiddenException
     *
     * @param post post for which to check
     * @param user user for which to check
     */
    private void mayUpdateOrDeletePostElseThrow(Post post, User user) {
        if (!user.getId().equals(post.getAuthor().getId())) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, post.getCourse(), user);
        }
    }

    /**
     * Check if user can update the post votes, if not throws an AccessForbiddenException
     *
     * @param post postAnswer for which to check
     * @param user user for which to check
     */
    private void mayUpdatePostVotesElseThrow(Post post, User user) {
        Course course = post.getCourse();
        if (course != null) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        }
    }
}
