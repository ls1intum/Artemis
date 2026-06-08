package de.tum.cit.aet.artemis.plagiarism.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.communication.service.PostingService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseInformationSharingConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismPostCreationDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismPostCreationResponseDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismPostUpdateRequestDTO;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;

@Conditional(PlagiarismEnabled.class)
@Lazy
@Service
public class PlagiarismPostService extends PostingService {

    private final PostRepository postRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final PlagiarismCaseService plagiarismCaseService;

    protected PlagiarismPostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            SavedPostRepository savedPostRepository, PostRepository postRepository, ExerciseRepository exerciseRepository, WebsocketMessagingService websocketMessagingService,
            PlagiarismCaseService plagiarismCaseService, PlagiarismCaseRepository plagiarismCaseRepository, ConversationParticipantRepository conversationParticipantRepository) {
        super(courseRepository, userRepository, exerciseRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository, savedPostRepository);
        this.postRepository = postRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.plagiarismCaseService = plagiarismCaseService;
    }

    /**
     * Checks course, user, and post-validity,
     * determines the post's author, persists the post,
     * and sends a notification to affected user groups
     *
     * @param courseId id of course the post belongs to
     * @param postDto  post to create
     * @return the created post as {@link PlagiarismPostCreationResponseDTO}
     */
    public PlagiarismPostCreationResponseDTO createPost(Long courseId, PlagiarismPostCreationDTO postDto) {
        Post post = postDto.toEntity();
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);
        if (course.getCourseInformationSharingConfiguration() == CourseInformationSharingConfiguration.DISABLED) {
            throw new BadRequestAlertException("Posting is disabled for this course.", PlagiarismPostCreationDTO.PLAGIARISM_POST_ENTITY_NAME, "courseInformationSharingDisabled");
        }

        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        parseUserMentions(course, post.getContent());
        // set author to current user
        post.setAuthor(user);
        setAuthorRoleForPosting(post, course);
        // set default value display priority -> NONE
        post.setDisplayPriority(DisplayPriority.NONE);

        Post savedPost = postRepository.save(post);
        plagiarismCaseService.savePostForPlagiarismCaseAndNotifyStudent(savedPost.getPlagiarismCase().getId(), savedPost);
        PlagiarismCase fullCase = plagiarismCaseRepository.findByIdElseThrow(savedPost.getPlagiarismCase().getId());
        savedPost.setPlagiarismCase(fullCase);
        return PlagiarismPostCreationResponseDTO.of(savedPost);
    }

    /**
     * Persists the continuous plagiarism control plagiarism case post
     * and sends a notification to affected user groups
     *
     * @param post post to create
     */
    public void createContinuousPlagiarismControlPlagiarismCasePost(Post post) {
        var savedPost = postRepository.save(post);
        plagiarismCaseService.saveAnonymousPostForPlagiarismCaseAndNotifyStudent(savedPost.getPlagiarismCase().getId(), savedPost);
    }

    /**
     * Checks course, user, and post-validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out.
     *
     * @param courseId id of course the post belongs to
     * @param postId   id of the post to update
     * @param request  update payload carrying the fields the instructor may mutate
     * @return updated post that was persisted
     */
    public Post updatePost(Long courseId, Long postId, PlagiarismPostUpdateRequestDTO request) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);
        Post existingPost = postRepository.findPostByIdElseThrow(postId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        parseUserMentions(course, request.content());

        boolean hasContentChanged = request.content() != null && !Objects.equals(existingPost.getContent(), request.content());
        if (hasContentChanged) {
            existingPost.setUpdatedDate(ZonedDateTime.now());
        }

        // Partial-update semantics: only overwrite a field when the client explicitly sent a non-null value.
        // Omitting `title` from the JSON body leaves the existing title untouched; sending `"title": null` is
        // equivalent to omission here. Use a sentinel field on the DTO if a future caller needs to explicitly
        // clear the title.
        if (request.title() != null) {
            existingPost.setTitle(request.title());
        }
        if (request.content() != null) {
            existingPost.setContent(request.content());
        }

        Post updatedPost = postRepository.save(existingPost);

        preparePostForBroadcast(updatedPost);
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course.getId(), null);
        return updatedPost;
    }

    /**
     * Checks course, user, and post-validity,
     * retrieves and filters posts for a plagiarism case by its id
     * and ensures that sensitive information is filtered out
     *
     * @param postContextFilter filter object
     * @return page of posts that belong to the plagiarism case
     */
    public List<Post> getAllPlagiarismCasePosts(PostContextFilterDTO postContextFilter) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(postContextFilter.courseId());
        // the user has to be at least a student in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        final PlagiarismCase plagiarismCase = plagiarismCaseRepository.findByIdElseThrow(postContextFilter.plagiarismCaseId());

        // checks
        if (authorizationCheckService.isAtLeastInstructorInCourse(plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember(), user)
                || plagiarismCase.getStudent().getLogin().equals(user.getLogin())) {
            // retrieve posts
            List<Post> plagiarismCasePosts = postRepository.findPostsByPlagiarismCaseId(postContextFilter.plagiarismCaseId());

            // protect sample solution, grading instructions, etc.
            setAuthorRoleOfPostings(plagiarismCasePosts, course.getId());

            return plagiarismCasePosts;
        }
        else {
            throw new AccessForbiddenException("Only instructors in the course or the students affected by the plagiarism case are allowed to view its post");
        }
    }

    /**
     * Checks course, user, and post-validity,
     * determines authority to delete a post, and deletes the post
     *
     * @param courseId id of course the post belongs to
     * @param postId   id of the post to delete
     */
    public void deletePostById(Long courseId, Long postId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);

        Post post = postRepository.findPostByIdElseThrow(postId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);

        // Clear the bidirectional PlagiarismCase reference to avoid TransientObjectException in Hibernate 6.6
        PlagiarismCase plagiarismCase = post.getPlagiarismCase();
        if (plagiarismCase != null) {
            plagiarismCase.setPost(null);
            plagiarismCaseRepository.save(plagiarismCase);
        }

        // delete
        postRepository.deleteById(postId);
        preparePostForBroadcast(post);
        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course.getId(), null);
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     */
    @Override
    public String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }

    /**
     * Retrieve post from the database by id
     *
     * @param postId id of requested post
     * @return retrieved post
     */
    public Post findById(Long postId) {
        return postRepository.findPostByIdElseThrow(postId);
    }

    /**
     * Retrieve post or message post from the database by id
     *
     * @param postOrMessageId ID of requested post or message
     * @return retrieved post
     */
    public Post findPostOrMessagePostById(Long postOrMessageId) {
        return postRepository.findPostOrMessagePostByIdElseThrow(postOrMessageId);
    }
}
