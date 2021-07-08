package de.tum.in.www1.artemis.web.rest.metis;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
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

    private static final String METIS_POST_ENTITY_NAME = "post";

    private final Logger log = LoggerFactory.getLogger(PostResource.class);

    private final PostRepository postRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureRepository lectureRepository;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final GroupNotificationService groupNotificationService;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

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
     * @param courseId id of the course the post belongs to
     * @param post     post to create
     * @return ResponseEntity with status 201 (Created) containing the created post in response body, or with status 400 (Bad Request) if the post
     * already has an ID or the courseId in the body doesn't match the PathVariable
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> createPost(@PathVariable Long courseId, @Valid @RequestBody Post post) throws URISyntaxException {
        log.debug("REST request to save Post : {}", post);
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        // check
        preCheckPostValidity(user, post, courseId);
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new Post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }

        // set author to current user
        post.setAuthor(user);
        Post savedPost = postRepository.save(post);

        // notify via exercise
        if (savedPost.getExercise() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForExercise(savedPost);

            // protect Sample Solution, Grading Instructions, etc.
            savedPost.getExercise().filterSensitiveInformation();
        }
        // notify via lecture
        if (savedPost.getLecture() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForLecture(savedPost);
        }

        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/posts/" + savedPost.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, METIS_POST_ENTITY_NAME, savedPost.getId().toString())).body(savedPost);
    }

    /**
     * PUT /courses/{courseId}/posts : Updates an existing post.
     *
     * @param courseId course the post belongs to
     * @param post     the post to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated post, or with status 400 (Bad Request) if the post is not valid, or with
     * status 500 (Internal Server Error) if the post couldn't be updated
     */
    @PutMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> updatePost(@PathVariable Long courseId, @RequestBody Post post) {
        log.debug("REST request to update Post : {}", post);
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // check
        if (post.getId() == null) {
            throw new BadRequestAlertException("Invalid id", METIS_POST_ENTITY_NAME, "idnull");
        }
        Post existingPost = postRepository.findByIdElseThrow(post.getId());
        preCheckPostValidity(user, existingPost, courseId);
        mayUpdateOrDeletePostElseThrow(existingPost, user);

        // update
        existingPost.setTitle(post.getTitle());
        existingPost.setContent(post.getContent());
        existingPost.setVisibleForStudents(post.isVisibleForStudents());
        existingPost.setTags(post.getTags());
        Post result = postRepository.save(existingPost);

        if (result.getExercise() != null) {
            // protect Sample Solution, Grading Instructions, etc.
            result.getExercise().filterSensitiveInformation();
        }

        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, METIS_POST_ENTITY_NAME, post.getId().toString())).body(result);
    }

    /**
     * PUT /courses/{courseId}/posts/{postId}/votes : Updates votes for a post.
     *
     * @param courseId   course the post belongs to
     * @param postId     the ID of the post to update
     * @param voteChange value by which votes are increased / decreased
     * @return the ResponseEntity with status 200 (OK) and with body the updated post, or with status 400 (Bad Request) if the post or the voteChanges are invalid, or with
     * status 500 (Internal Server Error) if the post couldn't be updated
     */
    @PutMapping("courses/{courseId}/posts/{postId}/votes")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Post> updatePostVotes(@PathVariable Long courseId, @PathVariable Long postId, @RequestBody Integer voteChange) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(user, post, courseId);
        if (voteChange < -2 || voteChange > 2) {
            return badRequest("voteChange", "400", "voteChange must be >= -2 and <= 2");
        }

        // update
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
     * @param courseId   course the post belongs to
     * @param exerciseId the exercise for which the posts should be retrieved
     * @return the ResponseEntity with status 200 (OK) and with body all posts for the given exerciseId or 400 (Bad Request) if exercises courseId doesnt match
     * the PathVariable courseId
     */
    @GetMapping("courses/{courseId}/exercises/{exerciseId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Post>> getAllPostsForExercise(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        preCheckUserAndCourse(user, courseId);
        preCheckExercise(user, courseId, exercise);

        // retrieve posts
        List<Post> posts = postRepository.findPostsByExercise_Id(exerciseId);
        // protect Sample Solution, Grading Instructions, etc.
        posts.forEach(post -> post.getExercise().filterSensitiveInformation());

        return new ResponseEntity<>(posts, null, HttpStatus.OK);
    }

    /**
     * GET /courses/{courseId}/lectures/{lectureId}/posts : get all posts for lecture.
     *
     * @param courseId  course the post belongs to
     * @param lectureId the lecture that the posts belong to
     * @return the ResponseEntity with status 200 (OK) and with body all posts for lecture or 400 (Bad Request) if the lectures courseId doesnt match
     * the PathVariable courseId
     */
    @GetMapping("courses/{courseId}/lectures/{lectureId}/posts")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<Post>> getAllPostsForLecture(@PathVariable Long courseId, @PathVariable Long lectureId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        preCheckUserAndCourse(user, courseId);
        preCheckLecture(user, courseId, lecture);

        // retrieve posts
        List<Post> posts = postRepository.findPostsByLecture_Id(lectureId);
        // protect Sample Solution, Grading Instructions, etc.
        posts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return new ResponseEntity<>(posts, null, HttpStatus.OK);
    }

    /**
     * GET /courses/{courseId}/posts : get all posts for course
     *
     * @param courseId the course that the posts belong to
     * @return the ResponseEntity with status 200 (OK) and with body all posts for course
     */
    @GetMapping("courses/{courseId}/posts")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Post>> getAllPostsForCourse(@PathVariable Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        // retrieve posts
        List<Post> posts = postRepository.findPostsForCourse(courseId);
        // Protect Sample Solution, Grading Instructions, etc.
        posts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return new ResponseEntity<>(posts, null, HttpStatus.OK);
    }

    /**
     * DELETE /courses/{courseId}/posts/:id : delete the post with {id}.
     *
     * @param courseId course the post belongs to
     * @param postId   the id of the post to delete
     * @return the ResponseEntity with status 200 (OK) or 400 (Bad Request) if the data is inconsistent
     */
    @DeleteMapping("courses/{courseId}/posts/{postId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deletePost(@PathVariable Long courseId, @PathVariable Long postId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(user, post, courseId);
        mayUpdateOrDeletePostElseThrow(post, user);

        String entity = "";
        if (post.getLecture() != null) {
            entity = "lecture with id: " + post.getLecture().getId();
        }
        else if (post.getExercise() != null) {
            entity = "exercise with id: " + post.getExercise().getId();
        }

        // delete
        log.info("Post deleted by " + user.getLogin() + ". Post: " + post.getContent() + " for " + entity);
        postRepository.deleteById(postId);

        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, METIS_POST_ENTITY_NAME, postId.toString())).build();

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

    private void preCheckUserAndCourse(User user, Long courseId) {
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        // check if course has posts enabled
        if (!course.getPostsEnabled()) {
            throw new BadRequestAlertException("Course with this Id does not have Posts enabled", METIS_POST_ENTITY_NAME, "400");
        }
    }

    private void preCheckPostValidity(User user, Post post, Long courseId) {
        preCheckUserAndCourse(user, courseId);

        if (!post.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Post sent in body", METIS_POST_ENTITY_NAME, "400");
        }

        // do not allow postings for exam exercises
        if (post.getExercise() != null && post.getExercise().isExamExercise()) {
            throw new BadRequestAlertException("Postings are not allowed on exam exercises", METIS_POST_ENTITY_NAME, "400");
        }
    }

    private void preCheckExercise(User user, Long courseId, Exercise exercise) {
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (!exercise.getCourseViaExerciseGroupOrCourseMember().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Exercise", METIS_POST_ENTITY_NAME, "400");
        }
    }

    private void preCheckLecture(User user, Long courseId, Lecture lecture) {
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, lecture.getCourse(), user);
        if (!lecture.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Lecture", METIS_POST_ENTITY_NAME, "400");
        }
    }

}
