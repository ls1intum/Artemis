package de.tum.in.www1.artemis.service.metis;

import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.*;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.service.notifications.SingleUserNotificationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class AnswerMessageService extends PostingService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final AnswerPostRepository answerPostRepository;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ConversationService conversationService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final SingleUserNotificationService singleUserNotificationService;

    private final PostRepository postRepository;

    private final ConversationRepository conversationRepository;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public AnswerMessageService(SingleUserNotificationService singleUserNotificationService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, AnswerPostRepository answerPostRepository, ConversationMessageRepository conversationMessageRepository,
            ConversationService conversationService, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            WebsocketMessagingService websocketMessagingService, ConversationParticipantRepository conversationParticipantRepository,
            ChannelAuthorizationService channelAuthorizationService, PostRepository postRepository, ConversationRepository conversationRepository) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository);
        this.answerPostRepository = answerPostRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationService = conversationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.postRepository = postRepository;
        this.conversationRepository = conversationRepository;
    }

    /**
     * Checks course, user and answer message and associated post validity,
     * determines the associated post, the answer message's author,
     * persists the answer message
     *
     * @param courseId      id of the course the answer post belongs to
     * @param answerMessage answer message to create
     * @return created answer message that was persisted
     */
    public AnswerPost createAnswerMessage(Long courseId, AnswerPost answerMessage) {
        final User author = this.userRepository.getUserWithGroupsAndAuthorities();

        // check
        if (answerMessage.getId() != null) {
            throw new BadRequestAlertException("A new answer post cannot already have an ID", METIS_ANSWER_POST_ENTITY_NAME, "idexists");
        }
        conversationService.isMemberElseThrow(answerMessage.getPost().getConversation().getId(), author.getId());

        Conversation conversation = conversationRepository.findByIdElseThrow(answerMessage.getPost().getConversation().getId());

        Post post = conversationMessageRepository.findMessagePostByIdElseThrow(answerMessage.getPost().getId());
        var course = preCheckUserAndCourseForMessaging(author, courseId);

        if (conversation instanceof Channel channel) {
            channelAuthorizationService.isAllowedToCreateNewAnswerPostInChannel(channel, author);
        }

        // use post from database rather than user input
        answerMessage.setPost(post);
        // set author to current user
        answerMessage.setAuthor(author);
        // on creation of an answer message, we set the resolves_post field to false per default since this feature is not used for messages
        answerMessage.setResolvesPost(false);
        AnswerPost savedAnswerMessage = answerPostRepository.save(answerMessage);
        savedAnswerMessage.getPost().setConversation(conversation);
        this.preparePostAndBroadcast(savedAnswerMessage, course);
        Set<User> usersInvolved = conversationMessageRepository.findUsersWhoRepliedInMessage(post.getId());
        // do not notify the author of the post if they are not part of the conversation (e.g. if they left or have been removed from the conversation)
        if (conversationService.isMember(post.getConversation().getId(), post.getAuthor().getId())) {
            usersInvolved.add(post.getAuthor());
        }
        usersInvolved.forEach(userInvolved -> singleUserNotificationService.notifyUserAboutNewMessageReply(savedAnswerMessage, userInvolved, author));
        return savedAnswerMessage;
    }

    /**
     * Checks course, user and associated message validity,
     * updates non-restricted field of the answer message, persists the answer message,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId        id of the course the answer message belongs to
     * @param answerMessageId id of the answer message to update
     * @param answerMessage   answer message to update
     * @return updated answer message that was persisted
     */
    public AnswerPost updateAnswerMessage(Long courseId, Long answerMessageId, AnswerPost answerMessage) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (answerMessage.getId() == null || !Objects.equals(answerMessage.getId(), answerMessageId)) {
            throw new BadRequestAlertException("Invalid id", METIS_ANSWER_POST_ENTITY_NAME, "idnull");
        }
        AnswerPost existingAnswerMessage = this.findById(answerMessageId);

        AnswerPost updatedAnswerMessage;

        Conversation conversation = conversationService.getConversationById(existingAnswerMessage.getPost().getConversation().getId());
        var course = preCheckUserAndCourseForMessaging(user, courseId);
        // only the content of the message can be updated
        existingAnswerMessage.setContent(answerMessage.getContent());

        // determine if the update operation is to mark the answer message as resolving the original post
        if (existingAnswerMessage.doesResolvePost() != answerMessage.doesResolvePost()) {
            // check if requesting user is allowed to mark this answer message as resolving, i.e. if user is author or original message or at least tutor
            mayMarkAnswerMessageAsResolvingElseThrow(existingAnswerMessage, user, course);
            existingAnswerMessage.setResolvesPost(answerMessage.doesResolvePost());
            // sets the message as resolved if there exists any resolving answer
            existingAnswerMessage.getPost().setResolved(existingAnswerMessage.getPost().getAnswers().stream().anyMatch(AnswerPost::doesResolvePost));
            postRepository.save(existingAnswerMessage.getPost());
        }
        else {
            // check if requesting user is allowed to update the content, i.e. if user is author of answer message or at least tutor
            mayUpdateOrDeleteAnswerMessageElseThrow(existingAnswerMessage, user);
            existingAnswerMessage.setContent(answerMessage.getContent());
        }

        updatedAnswerMessage = answerPostRepository.save(existingAnswerMessage);
        updatedAnswerMessage.getPost().setConversation(conversation);

        this.preparePostAndBroadcast(updatedAnswerMessage, course);
        return updatedAnswerMessage;
    }

    private Conversation mayUpdateOrDeleteAnswerMessageElseThrow(AnswerPost existingAnswerPost, User user) {
        // only the author of an answerMessage having postMessage with conversation context should edit or delete the entity
        if (existingAnswerPost.getPost().getConversation() != null && !existingAnswerPost.getAuthor().getId().equals(user.getId())) {
            throw new AccessForbiddenException("Answer Post", existingAnswerPost.getId());
        }
        else {
            return conversationService.getConversationById(existingAnswerPost.getPost().getConversation().getId());
        }
    }

    /**
     * Checks course and user validity,
     * determines authority to delete answer message and deletes the answer message
     *
     * @param courseId        id of the course the answer message belongs to
     * @param answerMessageId id of the answer message to delete
     */
    public void deleteAnswerMessageById(Long courseId, Long answerMessageId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        AnswerPost answerMessage = this.findById(answerMessageId);
        Conversation conversation = mayUpdateOrDeleteAnswerMessageElseThrow(answerMessage, user);
        var course = preCheckUserAndCourseForMessaging(user, courseId);

        // delete
        answerPostRepository.deleteById(answerMessageId);

        // we need to explicitly remove the answer post from the answers of the broadcast post to share up-to-date information
        Post updatedMessage = answerMessage.getPost();
        updatedMessage.removeAnswerPost(answerMessage);
        updatedMessage.setConversation(conversation);
        broadcastForPost(new PostDTO(updatedMessage, MetisCrudAction.UPDATE), course, null);
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

    /**
     * Checks if the requesting user is authorized in the course context,
     * i.e. user has to be the author of original message associated with the answer message or at least teaching assistant
     *
     * @param answerMessage answer message that should be marked as resolving
     * @param user          requesting user
     */
    void mayMarkAnswerMessageAsResolvingElseThrow(AnswerPost answerMessage, User user, Course course) {
        if (!answerMessage.getPost().getAuthor().equals(user)) {
            authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }
}
