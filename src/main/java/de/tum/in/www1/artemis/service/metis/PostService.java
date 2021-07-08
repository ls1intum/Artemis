package de.tum.in.www1.artemis.service.metis;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

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

@Service
public class PostService extends PostingService {

    private static final String METIS_POST_ENTITY_NAME = "post";

    private final UserRepository userRepository;

    private final PostRepository postRepository;

    private final ExerciseRepository exerciseRepository;

    private final LectureRepository lectureRepository;

    protected PostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, GroupNotificationService groupNotificationService,
            UserRepository userRepository, PostRepository postRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository) {
        super(courseRepository, authorizationCheckService, groupNotificationService);
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.exerciseRepository = exerciseRepository;
        this.lectureRepository = lectureRepository;
    }

    /**
     * Checks course, user and post validity,
     * determines the post's author, persists the post,
     * and sends a notification to affected user groups
     *
     * @param courseId id of the course the post belongs to
     * @param post     post to create
     * @return created post that was persisted
     */
    public Post createPost(Long courseId, Post post) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        // check
        preCheckPostValidity(user, post, courseId);
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new Post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }

        // set author to current user
        post.setAuthor(user);
        Post savedPost = postRepository.save(post);

        sendNotification(savedPost);

        return savedPost;
    }

    /**
     * Checks course, user and post validity,
     * updates non-restricted field of the post, persists the posts,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId id of the course the post belongs to
     * @param post     post to update
     * @return updated post that was persisted
     */
    public Post updatePost(Long courseId, Post post) {
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
        Post savedPost = postRepository.save(existingPost);

        if (savedPost.getExercise() != null) {
            // protect Sample Solution, Grading Instructions, etc.
            savedPost.getExercise().filterSensitiveInformation();
        }

        return savedPost;
    }

    /**
     * Checks course, user and post validity,
     * updates the votes, persists the posts,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId   id of the course the post belongs to
     * @param postId     id of the post to vote on
     * @param voteChange value by which votes are increased / decreased
     * @return updated post that was persisted
     */
    public Post updatePostVotes(Long courseId, Long postId, Integer voteChange) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(user, post, courseId);
        if (voteChange < -2 || voteChange > 2) {
            throw new BadRequestAlertException("VoteChange can only be changed by one", METIS_POST_ENTITY_NAME, "voteChange", true);
        }

        // update
        Integer newVotes = post.getVotes() + voteChange;
        post.setVotes(newVotes);
        Post savedPost = postRepository.save(post);

        if (savedPost.getExercise() != null) {
            // protect Sample Solution, Grading Instructions, etc.
            savedPost.getExercise().filterSensitiveInformation();
        }

        return savedPost;
    }

    /**
     * Checks course, user, exercise and post validity,
     * retrieves all posts for an exercise by its id
     * and ensures that sensitive information is filtered out
     *
     * @param courseId   id of the course the post belongs to
     * @param exerciseId id of the exercise for which the posts should be retrieved
     * @return list of posts that belong to the exercise
     */
    public List<Post> getAllExercisePosts(Long courseId, Long exerciseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        preCheckExercise(user, courseId, exerciseId);

        // retrieve posts
        List<Post> exercisePosts = postRepository.findPostsByExercise_Id(exerciseId);
        // protect sample solution, grading instructions, etc.
        exercisePosts.forEach(post -> post.getExercise().filterSensitiveInformation());

        return exercisePosts;
    }

    /**
     * Checks course, user, lecture and post validity,
     * retrieves all posts for a lecture by its id
     * and ensures that sensitive information is filtered out
     *
     * @param courseId  id of the course the post belongs to
     * @param lectureId id of the lecture for which the posts should be retrieved
     * @return list of posts that belong to the lecture
     */
    public List<Post> getAllLecturePosts(Long courseId, Long lectureId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        preCheckLecture(user, courseId, lectureId);

        // retrieve posts
        List<Post> lecturePosts = postRepository.findPostsByLecture_Id(lectureId);
        // protect Sample Solution, Grading Instructions, etc.
        lecturePosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return lecturePosts;
    }

    /**
     * Checks course, user and post validity,
     * retrieves all posts for a course by its id
     * and ensures that sensitive information is filtered out
     *
     * @param courseId id of the course the post belongs to
     * @return list of posts that belong to the course
     */
    public List<Post> getAllCoursePosts(Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        var course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        // retrieve posts
        List<Post> coursePosts = postRepository.findPostsForCourse(courseId);
        // Protect Sample Solution, Grading Instructions, etc.
        coursePosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return coursePosts;
    }

    /**
     * Checks course, user and post validity,
     * determines authority to delete post and deletes the post
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the post to delete
     */
    public void deletePostById(Long courseId, Long postId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(user, post, courseId);
        mayUpdateOrDeletePostElseThrow(post, user);

        // delete
        postRepository.deleteById(postId);
    }

    /**
     * Helper method to (i) compare id of the course belonging to the post with the path variable courseId,
     * and (ii) if the possibly associated exercise is not an exam exercise
     *
     * @param user     requesting user
     * @param post     post that is checked
     * @param courseId id of the course that is used as path variable
     */
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

    /**
     * Helper method to (i) check if the exercise exists, (ii) check if requesting user is authorized in the exercise context,
     * and (iii) compare the id of the course belonging to the exercise with the path variable courseId,
     *
     * @param user       requesting user
     * @param courseId   id of the course that is used as path variable
     * @param exerciseId id of the exercise that is used as path variable
     */
    private void preCheckExercise(User user, Long courseId, Long exerciseId) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (!exercise.getCourseViaExerciseGroupOrCourseMember().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Exercise", METIS_POST_ENTITY_NAME, "400");
        }
    }

    /**
     * Helper method to (i) check if the lecture exists, (ii) check if requesting user is authorized in the lecture context,
     * and (iii) compare the id of the course belonging to the lecture with the path variable courseId,
     *
     * @param user      requesting user
     * @param courseId  id of the course that is used as path variable
     * @param lectureId id of the lecture that is used as path variable
     */
    private void preCheckLecture(User user, Long courseId, Long lectureId) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, lecture.getCourse(), user);
        if (!lecture.getCourse().getId().equals(courseId)) {
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Lecture", METIS_POST_ENTITY_NAME, "400");
        }
    }

    /**
     * Helper method to retrieve the entity name used in ResponseEntity
     */
    @Override
    public String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }

}
