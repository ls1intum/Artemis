package de.tum.in.www1.artemis.service.metis;

import java.util.Objects;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.AnswerPostRepository;
import de.tum.in.www1.artemis.repository.metis.MessageRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class AnswerMessageService extends PostingService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final AnswerPostRepository answerPostRepository;

    private final MessageRepository messageRepository;

    private final ConversationService conversationService;

    public AnswerMessageService(CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, UserRepository userRepository,
            AnswerPostRepository answerPostRepository, MessageRepository messageRepository, ConversationService conversationService, ExerciseRepository exerciseRepository,
            LectureRepository lectureRepository, SimpMessageSendingOperations messagingTemplate) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, messagingTemplate);
        this.answerPostRepository = answerPostRepository;
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
    }

    /**
     * Checks course, user and answer message and associated post validity,
     * determines the associated post, the answer message's author,
     * persists the answer message
     *
     * @param courseId   id of the course the answer post belongs to
     * @param answerMessage answer message to create
     * @return created answer message that was persisted
     */
    public AnswerPost createAnswerMessage(Long courseId, AnswerPost answerMessage) {
        final User user = this.userRepository.getUserWithGroupsAndAuthorities();

        // check
        if (answerMessage.getId() != null) {
            throw new BadRequestAlertException("A new answer post cannot already have an ID", METIS_ANSWER_POST_ENTITY_NAME, "idexists");
        }

        final Course course = preCheckUserAndCourse(user, courseId);
        Post post = messageRepository.findMessagePostByIdElseThrow(answerMessage.getPost().getId());
        conversationService.mayInteractWithConversationElseThrow(answerMessage.getPost().getConversation().getId(), user);

        // use post from database rather than user input
        answerMessage.setPost(post);
        // set author to current user
        answerMessage.setAuthor(user);
        // on creation of an answer message, we set the resolves_post field to false per default since this feature is not used for messages
        answerMessage.setResolvesPost(false);
        AnswerPost savedAnswerMessage = answerPostRepository.save(answerMessage);
        this.preparePostAndBroadcast(savedAnswerMessage, course);

        return savedAnswerMessage;
    }

    /**
     * Checks course, user and associated message validity,
     * updates non-restricted field of the answer message, persists the answer message,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId          id of the course the answer message belongs to
     * @param answerMessageId   id of the answer message to update
     * @param answerMessage     answer message to update
     * @return updated answer message that was persisted
     */
    public AnswerPost updateAnswerMessage(Long courseId, Long answerMessageId, AnswerPost answerMessage) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (answerMessage.getId() == null || !Objects.equals(answerMessage.getId(), answerMessageId)) {
            throw new BadRequestAlertException("Invalid id", METIS_ANSWER_POST_ENTITY_NAME, "idnull");
        }
        AnswerPost existingAnswerMessage = this.findById(answerMessageId);
        final Course course = preCheckUserAndCourse(user, courseId);

        AnswerPost updatedAnswerMessage;

        // check if requesting user is allowed to update the content, i.e. if user is author of answer post or at least tutor
        mayUpdateOrDeleteAnswerMessageElseThrow(existingAnswerMessage, user, course);
        // only the content of the message can be updated
        existingAnswerMessage.setContent(answerMessage.getContent());

        updatedAnswerMessage = answerPostRepository.save(existingAnswerMessage);
        this.preparePostAndBroadcast(updatedAnswerMessage, course);
        return updatedAnswerMessage;
    }

    private void mayUpdateOrDeleteAnswerMessageElseThrow(AnswerPost existingAnswerPost, User user, Course course) {
        // only the author of an answerMessage having postMessage with conversation context should edit or delete the entity
        if (existingAnswerPost.getPost().getConversation() != null && !existingAnswerPost.getAuthor().getId().equals(user.getId())) {
            throw new AccessForbiddenException("Answer Post", existingAnswerPost.getId());
        }
    }

    /**
     * Checks course and user validity,
     * determines authority to delete answer message and deletes the answer message
     *
     * @param courseId          id of the course the answer message belongs to
     * @param answerMessageId   id of the answer message to delete
     */
    public void deleteAnswerMessageById(Long courseId, Long answerMessageId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        final Course course = preCheckUserAndCourse(user, courseId);
        AnswerPost answerMessage = this.findById(answerMessageId);
        mayUpdateOrDeleteAnswerMessageElseThrow(answerMessage, user, course);

        // delete
        answerPostRepository.deleteById(answerMessageId);

        // we need to explicitly remove the answer post from the answers of the broadcast post to share up-to-date information
        Post updatedMessage = answerMessage.getPost();
        updatedMessage.removeAnswerPost(answerMessage);
        broadcastForPost(new PostDTO(updatedMessage, MetisCrudAction.UPDATE), course);
    }

    /**
     * Retrieve the entity name used in ResponseEntity
     */
    @Override
    public String getEntityName() {
        return METIS_ANSWER_POST_ENTITY_NAME;
    }

    /**
     * Retrieve answer message from database by id
     *
     * @param answerMessageId id of requested answer message
     * @return retrieved answer message
     */
    public AnswerPost findById(Long answerMessageId) {
        return answerPostRepository.findAnswerMessageByIdElseThrow(answerMessageId);
    }
}
