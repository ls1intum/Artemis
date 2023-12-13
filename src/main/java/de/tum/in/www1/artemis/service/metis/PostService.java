package de.tum.in.www1.artemis.service.metis;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.repository.metis.ReactionRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.metis.similarity.PostSimilarityComparisonStrategy;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.plagiarism.PlagiarismCaseService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class PostService extends PostingService {

    public static final int TOP_K_SIMILARITY_RESULTS = 5;

    private final PostRepository postRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final GroupNotificationService groupNotificationService;

    private final PlagiarismCaseService plagiarismCaseService;

    private final PostSimilarityComparisonStrategy postContentCompareStrategy;

    private final ReactionRepository reactionRepository;

    protected PostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository, PostRepository postRepository,
            ExerciseRepository exerciseRepository, LectureRepository lectureRepository, GroupNotificationService groupNotificationService,
            PostSimilarityComparisonStrategy postContentCompareStrategy, WebsocketMessagingService websocketMessagingService, PlagiarismCaseService plagiarismCaseService,
            PlagiarismCaseRepository plagiarismCaseRepository, ConversationParticipantRepository conversationParticipantRepository, ReactionRepository reactionRepository) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository);
        this.postRepository = postRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.groupNotificationService = groupNotificationService;
        this.postContentCompareStrategy = postContentCompareStrategy;
        this.plagiarismCaseService = plagiarismCaseService;
        this.reactionRepository = reactionRepository;
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
        // checks
        if (post.getId() != null) {
            throw new BadRequestAlertException("A new post cannot already have an ID", METIS_POST_ENTITY_NAME, "idExists");
        }

        if (post.getPlagiarismCase() == null) {
            throw new BadRequestAlertException("A new post must belong to a plagiarism case", METIS_POST_ENTITY_NAME, "noPlagiarismCase");
        }

        final User user = this.userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        parseUserMentions(course, post.getContent());
        // set author to current user
        post.setAuthor(user);
        setAuthorRoleForPosting(post, course);
        // set default value display priority -> NONE
        post.setDisplayPriority(DisplayPriority.NONE);

        Post savedPost = postRepository.save(post);
        plagiarismCaseService.savePostForPlagiarismCaseAndNotifyStudent(savedPost.getPlagiarismCase().getId(), savedPost);

        return savedPost;
    }

    /**
     * Persists the continuous plagiarism control plagiarism case post,
     * and sends a notification to affected user groups
     *
     * @param post post to create
     */
    public void createContinuousPlagiarismControlPlagiarismCasePost(Post post) {
        var savedPost = postRepository.save(post);
        plagiarismCaseService.saveAnonymousPostForPlagiarismCaseAndNotifyStudent(savedPost.getPlagiarismCase().getId(), savedPost);
    }

    /**
     * Checks course, user and post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the post to update
     * @param post     post to update
     * @return updated post that was persisted
     */
    public Post updatePost(Long courseId, Long postId, Post post) {
        // check
        if (post.getId() == null || !Objects.equals(post.getId(), postId)) {
            throw new BadRequestAlertException("Invalid id", METIS_POST_ENTITY_NAME, "idNull");
        }

        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);
        Post existingPost = postRepository.findPostByIdElseThrow(postId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        parseUserMentions(course, post.getContent());

        boolean hasContentChanged = !existingPost.getContent().equals(post.getContent());
        if (hasContentChanged) {
            existingPost.setUpdatedDate(ZonedDateTime.now());
        }

        // update: allow overwriting of values only for depicted fields if user is at least student
        existingPost.setTitle(post.getTitle());
        existingPost.setContent(post.getContent());

        Post updatedPost = postRepository.save(existingPost);

        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course.getId(), null, null);
        return updatedPost;
    }

    /**
     * Checks course, user and post validity,
     * retrieves and filters posts for a plagiarism case by its id
     * and ensures that sensitive information is filtered out
     *
     * @param postContextFilter filter object
     * @return page of posts that belong to the plagiarism case
     */
    public List<Post> getAllPlagiarismCasePosts(PostContextFilter postContextFilter) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = preCheckUserAndCourseForCommunication(user, postContextFilter.getCourseId());
        final PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdElseThrow(postContextFilter.getPlagiarismCaseId());

        // checks
        if (authorizationCheckService.isAtLeastInstructorInCourse(plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember(), user)
                || plagiarismCase.getStudent().getLogin().equals(user.getLogin())) {
            // retrieve posts
            List<Post> plagiarismCasePosts;
            plagiarismCasePosts = postRepository.findPostsByPlagiarismCaseId(postContextFilter.getPlagiarismCaseId());

            // protect sample solution, grading instructions, etc.
            plagiarismCasePosts.forEach(post -> post.setCourse(course));
            setAuthorRoleOfPostings(plagiarismCasePosts);

            return plagiarismCasePosts;
        }
        else {
            throw new AccessForbiddenException("Only instructors in the course or the students affected by the plagiarism case are allowed to view its post");
        }
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
        final Course course = courseRepository.findByIdElseThrow(courseId);

        Post post = postRepository.findPostByIdElseThrow(postId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        // delete
        postRepository.deleteById(postId);
        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course.getId(), null, null);
    }

    /**
     * Checks course and user validity,
     * retrieves all tags for posts in a certain course
     *
     * @param courseId id of the course the tags belongs to
     * @return tags of all posts that belong to the course
     */
    public List<String> getAllCourseTags(Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourseForCommunication(user, courseId);
        return postRepository.findPostTagsForCourse(courseId);
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
        return postRepository.findPostByIdElseThrow(postId);
    }

    /**
     * Retrieve post or message post from database by id
     *
     * @param postOrMessageId ID of requested post or message
     * @return retrieved post
     */
    public Post findPostOrMessagePostById(Long postOrMessageId) {
        return postRepository.findPostOrMessagePostByIdElseThrow(postOrMessageId);
    }

    /**
     * Calculates k similar posts based on the underlying content comparison strategy
     *
     * @param courseId id of the course in which similar posts are searched for
     * @param post     post that is to be created and check for similar posts beforehand
     * @return list of similar posts
     */
    public List<Post> getSimilarPosts(Long courseId, Post post) {
        PostContextFilter postContextFilter = new PostContextFilter(courseId);
        List<Post> coursePosts = this.getCoursePosts(postContextFilter).stream()
                .sorted(Comparator.comparing(coursePost -> postContentCompareStrategy.performSimilarityCheck(post, coursePost))).toList();

        // sort course posts by calculated similarity scores
        setAuthorRoleOfPostings(coursePosts);
        return Lists.reverse(coursePosts).stream().limit(TOP_K_SIMILARITY_RESULTS).toList();
    }

    /**
     * Checks course, user and post validity,
     * retrieves and filters posts for a course by its id and optionally by its course-wide context
     * and ensures that sensitive information is filtered out
     *
     * @param postContextFilter filter object
     * @return page of posts that belong to the course
     */
    private Page<Post> getCoursePosts(PostContextFilter postContextFilter) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        preCheckUserAndCourseForCommunication(user, postContextFilter.getCourseId());

        // retrieve posts
        Page<Post> coursePosts = postRepository.findPosts(postContextFilter, user.getId(), false, null);

        // protect sample solution, grading instructions, etc.
        coursePosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);

        return coursePosts;
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
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Exercise", METIS_POST_ENTITY_NAME, "idNull");
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
            throw new BadRequestAlertException("PathVariable courseId doesn't match the courseId of the Lecture", METIS_POST_ENTITY_NAME, "idNull");
        }
    }
}
