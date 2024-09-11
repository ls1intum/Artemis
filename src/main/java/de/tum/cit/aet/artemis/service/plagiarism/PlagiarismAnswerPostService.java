package de.tum.cit.aet.artemis.service.plagiarism;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.metis.AnswerPost;
import de.tum.cit.aet.artemis.domain.metis.Post;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.repository.LectureRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.repository.metis.AnswerPostRepository;
import de.tum.cit.aet.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.repository.metis.PostRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.service.metis.PostingService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.PostDTO;

@Profile(PROFILE_CORE)
@Service
public class PlagiarismAnswerPostService extends PostingService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final AnswerPostRepository answerPostRepository;

    private final PostRepository postRepository;

    protected PlagiarismAnswerPostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            AnswerPostRepository answerPostRepository, PostRepository postRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            WebsocketMessagingService websocketMessagingService, ConversationParticipantRepository conversationParticipantRepository) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository);
        this.answerPostRepository = answerPostRepository;
        this.postRepository = postRepository;
    }

    /**
     * Checks course, user and answer post and associated post validity,
     * determines the associated post, the answer post's author,
     * sets resolves post to false by default,
     * persists the answer post, and sends a notification to affected user groups
     *
     * @param courseId   id of the course the answer post belongs to
     * @param answerPost answer post to create
     * @return created answer post that was persisted
     */
    public AnswerPost createAnswerPost(Long courseId, AnswerPost answerPost) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        // check
        if (answerPost.getId() != null) {
            throw new BadRequestAlertException("A new answer post cannot already have an ID", METIS_ANSWER_POST_ENTITY_NAME, "idExists");
        }

        final Course course = courseRepository.findByIdElseThrow(courseId);

        Post post = postRepository.findPostByIdElseThrow(answerPost.getPost().getId());
        parseUserMentions(course, answerPost.getContent());

        // increase answerCount of post needed for sorting
        post.setAnswerCount(post.getAnswerCount() + 1);

        // use post from database rather than user input
        answerPost.setPost(post);
        // set author to current user
        answerPost.setAuthor(user);
        setAuthorRoleForPosting(answerPost, course);
        // on creation of an answer post, we set the resolves_post field to false per default
        answerPost.setResolvesPost(false);
        AnswerPost savedAnswerPost = answerPostRepository.save(answerPost);
        postRepository.save(post);

        preparePostAndBroadcast(savedAnswerPost, course, null);

        return savedAnswerPost;
    }

    /**
     * Checks course, user and associated post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId     id of the course the answer post belongs to
     * @param answerPostId id of the answer post to update
     * @param answerPost   answer post to update
     * @return updated answer post that was persisted
     */
    public AnswerPost updateAnswerPost(Long courseId, Long answerPostId, AnswerPost answerPost) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (answerPost.getId() == null || !Objects.equals(answerPost.getId(), answerPostId)) {
            throw new BadRequestAlertException("Invalid id", METIS_ANSWER_POST_ENTITY_NAME, "idNull");
        }
        AnswerPost existingAnswerPost = this.findById(answerPostId);
        final Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        parseUserMentions(course, answerPost.getContent());

        AnswerPost updatedAnswerPost;

        // determine if the update operation is to mark the answer post as resolving the original post
        if (!Objects.equals(existingAnswerPost.doesResolvePost(), answerPost.doesResolvePost())) {
            // check if requesting user is allowed to mark this answer post as resolving, i.e. if user is author or original post or at least tutor
            mayMarkAnswerPostAsResolvingElseThrow(existingAnswerPost, user, course);
            existingAnswerPost.setResolvesPost(answerPost.doesResolvePost());
            // sets the post as resolved if there exists any resolving answer
            existingAnswerPost.getPost().setResolved(existingAnswerPost.getPost().getAnswers().stream().anyMatch(AnswerPost::doesResolvePost));
            postRepository.save(existingAnswerPost.getPost());
        }
        else {
            // check if requesting user is allowed to update the content, i.e. if user is author of answer post or at least tutor
            mayUpdateOrDeleteAnswerPostElseThrow(existingAnswerPost, user);
            existingAnswerPost.setContent(answerPost.getContent());
            existingAnswerPost.setUpdatedDate(ZonedDateTime.now());
        }
        updatedAnswerPost = answerPostRepository.save(existingAnswerPost);
        this.preparePostAndBroadcast(updatedAnswerPost, course, null);
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

        // decrease answerCount of post needed for sorting
        post.setAnswerCount(post.getAnswerCount() - 1);

        // sets the post as resolved if there exists any resolving answer
        post.setResolved(post.getAnswers().stream().anyMatch(AnswerPost::doesResolvePost));
        // deletes the answerPost from database and persists updates on the post properties
        postRepository.save(post);

        // delete
        answerPostRepository.deleteById(answerPostId);

        broadcastForPost(new PostDTO(post, MetisCrudAction.UPDATE), course.getId(), null, null);
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
