package de.tum.cit.aet.artemis.plagiarism.service;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.communication.service.PostingService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostCreateRequestDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismAnswerPostUpdateRequestDTO;

@Conditional(PlagiarismEnabled.class)
@Lazy
@Service
public class PlagiarismAnswerPostService extends PostingService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final AnswerPostRepository answerPostRepository;

    private final PostRepository postRepository;

    protected PlagiarismAnswerPostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            AnswerPostRepository answerPostRepository, PostRepository postRepository, ExerciseRepository exerciseRepository, WebsocketMessagingService websocketMessagingService,
            ConversationParticipantRepository conversationParticipantRepository, SavedPostRepository savedPostRepository) {
        super(courseRepository, userRepository, exerciseRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository, savedPostRepository);
        this.answerPostRepository = answerPostRepository;
        this.postRepository = postRepository;
    }

    /**
     * Checks course, user and associated post validity,
     * determines the answer post's author and persists the answer post on the parent post resolved
     * by {@code request.postId()}, then broadcasts the update.
     *
     * @param courseId id of the course the answer post belongs to
     * @param request  payload carrying the parent post id and the new answer's content
     * @return created answer post that was persisted
     */
    public AnswerPost createAnswerPost(Long courseId, PlagiarismAnswerPostCreateRequestDTO request) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);

        Post post = postRepository.findPostByIdElseThrow(request.postId());
        parseUserMentions(course, request.content());

        AnswerPost answerPost = new AnswerPost();
        // use post from database rather than user input
        answerPost.setPost(post);
        answerPost.setContent(request.content());
        // set author to current user
        answerPost.setAuthor(user);
        setAuthorRoleForPosting(answerPost, course);
        // on creation of an answer post, we set the resolves_post field to false per default
        answerPost.setResolvesPost(false);
        AnswerPost savedAnswerPost = answerPostRepository.save(answerPost);
        postRepository.save(post);

        preparePostAndBroadcast(savedAnswerPost, course);

        return savedAnswerPost;
    }

    /**
     * Checks course, user and associated post validity,
     * updates non-restricted fields of the answer post, persists it,
     * and ensures that sensitive information is filtered out.
     *
     * @param courseId     id of the course the answer post belongs to
     * @param answerPostId id of the answer post to update
     * @param request      update payload carrying the fields the caller may mutate
     * @return updated answer post that was persisted
     */
    public AnswerPost updateAnswerPost(Long courseId, Long answerPostId, PlagiarismAnswerPostUpdateRequestDTO request) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        AnswerPost existingAnswerPost = this.findById(answerPostId);
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        parseUserMentions(course, request.content());

        // Authorization model preserves the pre-refactor contract:
        //
        // * Resolve-flag change and content edit are authorization-distinct. The resolving permission
        // (`mayMarkAnswerPostAsResolvingElseThrow` — parent-post author or instructor) is orthogonal to
        // the content-edit permission (`mayUpdateOrDeleteAnswerPostElseThrow` — answer author). An
        // instructor can toggle resolve on someone else's answer without being allowed to rewrite its
        // content.
        // * If the resolve flag is actually changing, the request is treated primarily as a resolve
        // operation. Sending the existing content alongside (a common frontend pattern) does not trigger
        // the content-edit authorization. If the request additionally carries *different* content, the
        // content edit is independently authorized — this closes the CodeRabbit-flagged corner case where
        // a single PUT that changed both fields silently dropped the content because only the resolve
        // branch ran.
        // * If the resolve flag is unchanged, the request is treated as a content edit; the ownership
        // check fires even for no-op edits, matching the original `else` branch that always asserted
        // ownership before writing content back, so a non-author cannot probe the endpoint with a
        // same-content PUT.
        // `resolvesPost` is a boxed Boolean on the DTO; null means "field not provided, do not toggle".

        boolean resolveFlagChanging = request.resolvesPost() != null && !Objects.equals(existingAnswerPost.doesResolvePost(), request.resolvesPost());
        if (resolveFlagChanging) {
            mayMarkAnswerPostAsResolvingElseThrow(existingAnswerPost, user, course);
            existingAnswerPost.setResolvesPost(request.resolvesPost());
            // re-evaluate the parent post's resolved status — any resolving answer keeps the post marked as resolved
            existingAnswerPost.getPost().setResolved(existingAnswerPost.getPost().getAnswers().stream().anyMatch(AnswerPost::doesResolvePost));
            postRepository.save(existingAnswerPost.getPost());

            if (request.content() != null && !Objects.equals(existingAnswerPost.getContent(), request.content())) {
                mayUpdateOrDeleteAnswerPostElseThrow(existingAnswerPost, user);
                existingAnswerPost.setContent(request.content());
                existingAnswerPost.setUpdatedDate(ZonedDateTime.now());
            }
        }
        else {
            mayUpdateOrDeleteAnswerPostElseThrow(existingAnswerPost, user);
            if (request.content() != null && !Objects.equals(existingAnswerPost.getContent(), request.content())) {
                existingAnswerPost.setContent(request.content());
                existingAnswerPost.setUpdatedDate(ZonedDateTime.now());
            }
        }

        AnswerPost updatedAnswerPost = answerPostRepository.save(existingAnswerPost);
        this.preparePostAndBroadcast(updatedAnswerPost, course);
        return updatedAnswerPost;
    }

    /**
     * Checks course and user validity,
     * determines authority to delete and deletes the answer post
     * reduces answerCount of post and updates resolved status
     *
     * @param courseId     id of the course the answer post belongs to
     * @param answerPostId id of the answer post to delete
     */
    public void deleteAnswerPostById(Long courseId, Long answerPostId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = courseRepository.findByIdElseThrow(courseId);
        // user has to be at least student in the course
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        AnswerPost answerPost = this.findById(answerPostId);
        Post post = postRepository.findPostByIdElseThrow(answerPost.getPost().getId());

        mayUpdateOrDeleteAnswerPostElseThrow(answerPost, user);

        // we need to explicitly remove the answer post from the answers of the broadcast post to share up-to-date information
        post.removeAnswerPost(answerPost);

        // sets the post as resolved if there exists any resolving answer
        post.setResolved(post.getAnswers().stream().anyMatch(AnswerPost::doesResolvePost));
        // deletes the answerPost from database and persists updates on the post properties
        postRepository.save(post);

        // delete
        answerPostRepository.deleteById(answerPostId);
        preparePostForBroadcast(post);
        broadcastForPost(new PostDTO(post, MetisCrudAction.UPDATE), course.getId(), null);
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     */
    @Override
    public String getEntityName() {
        return METIS_ANSWER_POST_ENTITY_NAME;
    }

    /**
     * Retrieve answer post from database by id
     *
     * @param answerPostId id of requested answer post
     * @return retrieved answer post
     */
    public AnswerPost findById(Long answerPostId) {
        return answerPostRepository.findAnswerPostByIdElseThrow(answerPostId);
    }

    /**
     * Retrieve answer post or answer message from database by id
     *
     * @param messageId ID of requested answer post
     * @return retrieved answer post
     */
    public AnswerPost findAnswerPostOrAnswerMessageById(Long messageId) {
        return answerPostRepository.findByIdElseThrow(messageId);
    }

    /**
     * Checks if the requesting user is authorized in the course context,
     * i.e. user has to be author of original post associated with the answer post or at least teaching assistant
     *
     * @param answerPost answer post that should be marked as resolving
     * @param user       requesting user
     */
    void mayMarkAnswerPostAsResolvingElseThrow(AnswerPost answerPost, User user, Course course) {
        if (!answerPost.getPost().getAuthor().equals(user)) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
        }
    }

    /**
     * Checks if the requesting user is authorized, i.e. a user has to be the author of the answer post
     *
     * @param answerPost answer that is requested
     * @param user       requesting user
     */
    protected void mayUpdateOrDeleteAnswerPostElseThrow(AnswerPost answerPost, User user) {
        if (!user.getId().equals(answerPost.getAuthor().getId())) {
            throw new AccessForbiddenException("You are not allowed to edit this post");
        }
    }
}
