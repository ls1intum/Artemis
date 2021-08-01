package de.tum.in.www1.artemis.service.metis;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
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

    private static final String METIS_POST_ENTITY_NAME = "metis.post";

    private final UserRepository userRepository;

    private final PostRepository postRepository;

    private final LectureRepository lectureRepository;

    private final GroupNotificationService groupNotificationService;

    protected PostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository, PostRepository postRepository,
            ExerciseRepository exerciseRepository, LectureRepository lectureRepository, GroupNotificationService groupNotificationService) {
        super(courseRepository, exerciseRepository, postRepository, authorizationCheckService);
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.lectureRepository = lectureRepository;
        this.groupNotificationService = groupNotificationService;
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
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }
        preCheckUserAndCourse(user, courseId);
        preCheckPostValidity(post, courseId);

        // set author to current user
        post.setAuthor(user);
        Post savedPost = postRepository.save(post);

        sendNotification(savedPost);

        return savedPost;
    }

    /**
     * Checks course, user and post validity,
     * updates non-restricted field of the post, persists the post,
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
        preCheckUserAndCourse(user, courseId);
        Post existingPost = postRepository.findByIdElseThrow(post.getId());
        preCheckPostValidity(existingPost, courseId);
        mayUpdateOrDeletePostingElseThrow(existingPost, user);

        // update: allow overwriting of values only for depicted fields
        existingPost.setTitle(post.getTitle());
        existingPost.setContent(post.getContent());
        existingPost.setVisibleForStudents(post.isVisibleForStudents());
        existingPost.setTags(post.getTags());
        existingPost.setIsArchived(post.isArchived());
        existingPost.setIsPinned(post.isPinned());
        Post updatedPost = postRepository.save(existingPost);

        if (updatedPost.getExercise() != null) {
            // protect sample solution, grading instructions, etc.
            updatedPost.getExercise().filterSensitiveInformation();
        }

        return updatedPost;
    }

    /**
     * Checks course, user and post validity,
     * updates the votes, persists the post,
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
        preCheckUserAndCourse(user, courseId);
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(post, courseId);
        if (voteChange < -2 || voteChange > 2) {
            throw new BadRequestAlertException("VoteChange can only be changed +1 or -1", METIS_POST_ENTITY_NAME, "400", true);
        }

        // update votes
        Integer newVotes = post.getVotes() + voteChange;
        post.setVotes(newVotes);
        Post updatedPost = postRepository.save(post);

        if (updatedPost.getExercise() != null) {
            // protect sample solution, grading instructions, etc.
            updatedPost.getExercise().filterSensitiveInformation();
        }

        return updatedPost;
    }

    /**
     * Checks course, user and post validity,
     * updates the pin state, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId  id of the course the post belongs to
     * @param postId    id of the post to change the pin state for
     * @param pinState  new boolean value of the isPinned property for the given post
     * @return updated post that was persisted
     */
    public Post updatePinState(Long courseId, Long postId, Boolean pinState) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(post, courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        // update pin state
        post.setIsPinned(pinState);
        Post updatedPost = postRepository.save(post);

        if (updatedPost.getExercise() != null) {
            // protect sample solution, grading instructions, etc.
            updatedPost.getExercise().filterSensitiveInformation();
        }

        return updatedPost;
    }

    /**
     * Checks course, user and post validity,
     * updates the archive state, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId      id of the course the post belongs to
     * @param postId        id of the post to change the archive state for
     * @param archiveState  new boolean value of the isArchived property for the given post
     * @return updated post that was persisted
     */
    public Post updateArchiveState(Long courseId, Long postId, Boolean archiveState) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(post, courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        // update pin state
        post.setIsArchived(archiveState);
        Post updatedPost = postRepository.save(post);

        if (updatedPost.getExercise() != null) {
            // protect sample solution, grading instructions, etc.
            updatedPost.getExercise().filterSensitiveInformation();
        }

        return updatedPost;
    }

    /**
     * Add reaction to a post and persist the post
     *
     * @param post     post that is reacted on
     * @param reaction reaction that was added by a user
     */
    public void updateWithReaction(Post post, Reaction reaction) {
        post.addReaction(reaction);
        postRepository.save(post);
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
        List<Post> exercisePosts = postRepository.findPostsByExerciseId(exerciseId);
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
        List<Post> lecturePosts = postRepository.findPostsByLectureId(lectureId);
        // protect sample solution, grading instructions, etc.
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
        Course course = preCheckUserAndCourse(user, courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);

        // retrieve posts
        List<Post> coursePosts = postRepository.findPostsForCourse(courseId);
        // protect sample solution, grading instructions, etc.
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
        preCheckUserAndCourse(user, courseId);
        Post post = postRepository.findByIdElseThrow(postId);
        preCheckPostValidity(post, courseId);
        mayUpdateOrDeletePostingElseThrow(post, user);

        // delete
        postRepository.deleteById(postId);
    }

    /**
     * Checks course and user validity,
     * retrieves all tags for posts in a certain course
     *
     * @param courseId  id of the course the tags belongs to
     * @return tags of all posts that belong to the course
     */
    public List<String> getAllCourseTags(Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourse(user, courseId);
        return postRepository.findPostTagsForCourse(courseId);
    }

    /**
     * Method to (i) check if the exercise exists, (ii) check if requesting user is authorized in the exercise context,
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
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Exercise", METIS_POST_ENTITY_NAME, "idnull");
        }
    }

    /**
     * Method to (i) check if the lecture exists, (ii) check if requesting user is authorized in the lecture context,
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
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Lecture", METIS_POST_ENTITY_NAME, "idnull");
        }
    }

    /**
     * Sends notification to affected groups
     *
     * @param post post that triggered the notification
     */
    void sendNotification(Post post) {
        // notify via exercise
        if (post.getExercise() != null) {
            // set exercise retrieved from database to show title in notification
            Exercise exercise = exerciseRepository.findByIdElseThrow(post.getExercise().getId());
            post.setExercise(exercise);
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForExercise(post);
            // protect sample solution, grading instructions, etc.
            post.getExercise().filterSensitiveInformation();
        }
        // notify via lecture
        if (post.getLecture() != null) {
            // set lecture retrieved from database to show title in notification
            Lecture lecture = lectureRepository.findByIdElseThrow(post.getLecture().getId());
            post.setLecture(lecture);
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewPostForLecture(post);
        }
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     */
    @Override
    public String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }

    /**
     * Retrieve post from database by id
     *
     * @param postId id of requested post
     * @return retrieved post
     */
    public Post findById(Long postId) {
        return postRepository.findByIdElseThrow(postId);
    }
}
