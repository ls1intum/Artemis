package de.tum.cit.aet.artemis.service.plagiarism;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.DisplayPriority;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.service.metis.PostingService;
import de.tum.cit.aet.artemis.web.rest.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.PostDTO;

@Profile(PROFILE_CORE)
@Service
public class PlagiarismPostService extends PostingService {

    private final PostRepository postRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    protected PlagiarismPostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            PostRepository postRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository, WebsocketMessagingService websocketMessagingService,
            PlagiarismCaseService plagiarismCaseService, PlagiarismCaseRepository plagiarismCaseRepository, ConversationParticipantRepository conversationParticipantRepository) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository);
        this.postRepository = postRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.plagiarismCaseService = plagiarismCaseService;
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
    public List<Post> getAllPlagiarismCasePosts(PostContextFilterDTO postContextFilter) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(postContextFilter.courseId());
        // user has to be at least student in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        final PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdElseThrow(postContextFilter.plagiarismCaseId());

        // checks
        if (authorizationCheckService.isAtLeastInstructorInCourse(plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember(), user)
                || plagiarismCase.getStudent().getLogin().equals(user.getLogin())) {
            // retrieve posts
            List<Post> plagiarismCasePosts;
            plagiarismCasePosts = postRepository.findPostsByPlagiarismCaseId(postContextFilter.plagiarismCaseId());

            // protect sample solution, grading instructions, etc.
            setAuthorRoleOfPostings(plagiarismCasePosts, course.getId());

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
}
