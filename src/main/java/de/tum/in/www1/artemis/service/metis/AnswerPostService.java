package de.tum.in.www1.artemis.service.metis;

import java.util.Objects;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.MetisPostAction;
import de.tum.in.www1.artemis.web.websocket.dto.MetisPostDTO;

@Service
public class AnswerPostService extends PostingService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final UserRepository userRepository;

    private final AnswerPostRepository answerPostRepository;

    private final PostRepository postRepository;

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationService singleUserNotificationService;

    protected AnswerPostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            AnswerPostRepository answerPostRepository, PostRepository postRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            GroupNotificationService groupNotificationService, SingleUserNotificationService singleUserNotificationService, SimpMessageSendingOperations messagingTemplate) {
        super(courseRepository, exerciseRepository, lectureRepository, postRepository, authorizationCheckService, messagingTemplate);
        this.userRepository = userRepository;
        this.answerPostRepository = answerPostRepository;
        this.postRepository = postRepository;
        this.groupNotificationService = groupNotificationService;
        this.singleUserNotificationService = singleUserNotificationService;
    }

    /**
     * Checks course, user and answer post and associated post validity,
     * determines the associated post, the answer post's author,
     * sets to approved if author is at least a tutor,
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
            throw new BadRequestAlertException("A new answer post cannot already have an ID", METIS_ANSWER_POST_ENTITY_NAME, "idexists");
        }

        final Course course = preCheckUserAndCourse(user, courseId);
        Post post = postRepository.findByIdElseThrow(answerPost.getPost().getId());

        // use post from database rather than user input
        answerPost.setPost(post);
        // set author to current user
        answerPost.setAuthor(user);
        // on creation of an answer post, we set the resolves_post field to false per default
        answerPost.setResolvesPost(false);
        AnswerPost savedAnswerPost = answerPostRepository.save(answerPost);
        this.preparePostAndBroadcast(savedAnswerPost, course);
        sendNotification(post, answerPost, course);

        return savedAnswerPost;
    }

    /**
     * Checks course, user and associated post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId      id of the course the answer post belongs to
     * @param answerPostId  id of the answer post to update
     * @param answerPost    answer post to update
     * @return updated answer post that was persisted
     */
    public AnswerPost updateAnswerPost(Long courseId, Long answerPostId, AnswerPost answerPost) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (answerPost.getId() == null || !Objects.equals(answerPost.getId(), answerPostId)) {
            throw new BadRequestAlertException("Invalid id", METIS_ANSWER_POST_ENTITY_NAME, "idnull");
        }
        AnswerPost existingAnswerPost = answerPostRepository.findByIdElseThrow(answerPostId);
        final Course course = preCheckUserAndCourse(user, courseId);

        AnswerPost updatedAnswerPost;

        // determine if the update operation is to mark the answer post as resolving the original post
        if (existingAnswerPost.doesResolvePost() != answerPost.doesResolvePost()) {
            // check if requesting user is allowed to mark this answer post as resolving, i.e. if user is author or original post or at least tutor
            mayMarkAnswerPostAsResolvingElseThrow(existingAnswerPost, user, course);
            existingAnswerPost.setResolvesPost(answerPost.doesResolvePost());
        }
        else {
            // check if requesting user is allowed to update the content, i.e. if user is author of answer post or at least tutor
            mayUpdateOrDeletePostingElseThrow(existingAnswerPost, user, course);
            existingAnswerPost.setContent(answerPost.getContent());
        }
        updatedAnswerPost = answerPostRepository.save(existingAnswerPost);
        this.preparePostAndBroadcast(updatedAnswerPost, course);
        return updatedAnswerPost;
    }

    /**
     * Add reaction to an answer post and persist the answer post
     *
     * @param answerPost answer post that is reacted on
     * @param reaction   reaction that was added by a user
     * @param courseId   id of the course the answer post belongs to
     */
    public void updateWithReaction(AnswerPost answerPost, Reaction reaction, Long courseId) {
        final Course course = preCheckUserAndCourse(reaction.getUser(), courseId);
        answerPost.addReaction(reaction);
        AnswerPost updatedAnswerPost = answerPostRepository.save(answerPost);
        this.preparePostAndBroadcast(updatedAnswerPost, course);
    }

    /**
     * Checks course and user validity,
     * determines authority to delete post and deletes the post
     *
     * @param courseId     id of the course the answer post belongs to
     * @param answerPostId id of the answer post to delete
     */
    public void deleteAnswerPostById(Long courseId, Long answerPostId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        AnswerPost answerPost = answerPostRepository.findByIdElseThrow(answerPostId);
        mayUpdateOrDeletePostingElseThrow(answerPost, user, course);

        // delete
        answerPostRepository.deleteById(answerPostId);

        // we need to explicitly remove the answer post from the answers of the broadcast post to share up-to-date information
        Post updatedPost = answerPost.getPost();
        updatedPost.removeAnswerPost(answerPost);
        broadcastForPost(new MetisPostDTO(updatedPost, MetisPostAction.UPDATE_POST), course);
    }

    /**
     * Sends notification to affected groups
     *
     * @param post which is answered
     * @param answerPost which is created
     */
    void sendNotification(Post post, AnswerPost answerPost, Course course) {
        // notify via course
        if (post.getCourseWideContext() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost(post, answerPost, course);
            singleUserNotificationService.notifyUserAboutNewAnswerForCoursePost(post, course);
            return;
        }
        // notify via exercise
        if (post.getExercise() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise(post, answerPost, course);
            singleUserNotificationService.notifyUserAboutNewAnswerForExercise(post, course);
            // protect Sample Solution, Grading Instructions, etc.
            post.getExercise().filterSensitiveInformation();
            return;
        }
        // notify via lecture
        if (post.getLecture() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(post, answerPost, course);
            singleUserNotificationService.notifyUserAboutNewAnswerForLecture(post, course);
        }
    }

    /**
     * Helper method to prepare the post included in the websocket message and initiate the broadcasting
     *
     * @param updatedAnswerPost answer post that was updated
     * @param course            course the answer post belongs to
     */
    private void preparePostAndBroadcast(AnswerPost updatedAnswerPost, Course course) {
        // we need to explicitly (and newly) add the updated answer post to the answers of the broadcast post to share up-to-date information
        Post updatedPost = updatedAnswerPost.getPost();
        // remove and add operations on sets identify an AnswerPost by its id; to update a certain property of an existing answer post,
        // we need to remove the existing AnswerPost (based on unchanged id in updatedAnswerPost) and add the updatedAnswerPost afterwards
        updatedPost.removeAnswerPost(updatedAnswerPost);
        updatedPost.addAnswerPost(updatedAnswerPost);
        broadcastForPost(new MetisPostDTO(updatedPost, MetisPostAction.UPDATE_POST), course);
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
        return answerPostRepository.findByIdElseThrow(answerPostId);
    }

    /**
     * Checks if the requesting user is authorized in the course context,
     * i.e. user has to be author of original post associated with the answer post or at least teaching assistant
     *
     * @param answerPost    answer post that should be marked as resolving
     * @param user          requesting user
     */
    void mayMarkAnswerPostAsResolvingElseThrow(AnswerPost answerPost, User user, Course course) {
        if (!answerPost.getPost().getAuthor().equals(user)) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }
}
