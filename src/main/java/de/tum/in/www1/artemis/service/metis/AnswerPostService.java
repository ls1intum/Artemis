package de.tum.in.www1.artemis.service.metis;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Reaction;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.GroupNotificationService;
import de.tum.in.www1.artemis.service.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

@Service
public class AnswerPostService extends PostingService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final UserRepository userRepository;

    private final AnswerPostRepository answerPostRepository;

    private final PostRepository postRepository;

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationService singleUserNotificationService;

    protected AnswerPostService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            AnswerPostRepository answerPostRepository, PostRepository postRepository, ExerciseRepository exerciseRepository, GroupNotificationService groupNotificationService,
            SingleUserNotificationService singleUserNotificationService) {
        super(courseRepository, exerciseRepository, postRepository, authorizationCheckService);
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
        Course course = preCheckUserAndCourse(user, courseId);
        Post post = postRepository.findByIdElseThrow(answerPost.getPost().getId());
        preCheckPostValidity(answerPost.getPost(), courseId);

        // answer post is automatically approved if written by an instructor
        answerPost.setTutorApproved(this.authorizationCheckService.isAtLeastInstructorInCourse(course, user));
        // use post from database rather than user input
        answerPost.setPost(post);
        // set author to current user
        answerPost.setAuthor(user);
        AnswerPost savedAnswerPost = answerPostRepository.save(answerPost);

        sendNotification(savedAnswerPost);

        return savedAnswerPost;
    }

    /**
     * Checks course, user and associated post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId   id of the course the answer post belongs to
     * @param answerPost answer post to update
     * @return updated answer post that was persisted
     */
    public AnswerPost updateAnswerPost(Long courseId, AnswerPost answerPost) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (answerPost.getId() == null) {
            throw new BadRequestAlertException("Invalid id", METIS_ANSWER_POST_ENTITY_NAME, "idnull");
        }
        AnswerPost existingAnswerPost = answerPostRepository.findByIdElseThrow(answerPost.getId());
        Course course = preCheckUserAndCourse(user, courseId);
        preCheckPostValidity(answerPost.getPost(), courseId);
        mayUpdateOrDeletePostingElseThrow(existingAnswerPost, user);

        // update: allow overwriting of values only for depicted fields
        existingAnswerPost.setContent(answerPost.getContent());
        // tutor approval can only be toggled by a tutor
        if (this.authorizationCheckService.isAtLeastInstructorInCourse(course, user)) {
            existingAnswerPost.setTutorApproved(answerPost.isTutorApproved());
        }
        AnswerPost updatedAnswerPost = answerPostRepository.save(existingAnswerPost);

        if (updatedAnswerPost.getPost().getExercise() != null) {
            // protect sample solution, grading instructions, etc.
            updatedAnswerPost.getPost().getExercise().filterSensitiveInformation();
        }

        return updatedAnswerPost;
    }

    /**
     * Add reaction to an answer post and persist the answer post
     *
     * @param answerPost answer post that is reacted on
     * @param reaction   reaction that was added by a user
     */
    public void updateWithReaction(AnswerPost answerPost, Reaction reaction) {
        answerPost.addReaction(reaction);
        answerPostRepository.save(answerPost);
    }

    /**
     * Checks course and user validity,
     * determines authority to delete post and deletes the post
     *
     * @param courseId      id of the course the answer post belongs to
     * @param answerPostId  id of the answer post to delete
     */
    public void deleteAnswerPostById(Long courseId, Long answerPostId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        AnswerPost answerPost = answerPostRepository.findByIdElseThrow(answerPostId);
        preCheckUserAndCourse(user, courseId);
        preCheckPostValidity(answerPost.getPost(), courseId);
        mayUpdateOrDeletePostingElseThrow(answerPost, user);

        // delete
        answerPostRepository.deleteById(answerPostId);
    }

    /**
     * Sends notification to affected groups
     *
     * @param answerPost answer post that triggered the notification
     */
    void sendNotification(AnswerPost answerPost) {
        // notify via exercise
        if (answerPost.getPost().getExercise() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise(answerPost);
            singleUserNotificationService.notifyUserAboutNewAnswerForExercise(answerPost);

            // protect Sample Solution, Grading Instructions, etc.
            answerPost.getPost().getExercise().filterSensitiveInformation();
        }
        // notify via lecture
        if (answerPost.getPost().getLecture() != null) {
            groupNotificationService.notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(answerPost);
            singleUserNotificationService.notifyUserAboutNewAnswerForLecture(answerPost);
        }
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
}
