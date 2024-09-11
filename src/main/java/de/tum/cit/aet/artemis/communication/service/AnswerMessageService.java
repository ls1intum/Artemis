package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.notification.SingleUserNotification;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ConversationRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.service.conversation.auth.ChannelAuthorizationService;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.PostDTO;

@Profile(PROFILE_CORE)
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

        Conversation conversation = conversationService.isMemberOrCreateForCourseWideElseThrow(answerMessage.getPost().getConversation().getId(), author, Optional.empty())
                .orElse(conversationRepository.findByIdElseThrow(answerMessage.getPost().getConversation().getId()));

        Post post = conversationMessageRepository.findMessagePostByIdElseThrow(answerMessage.getPost().getId());
        var course = preCheckUserAndCourseForMessaging(author, courseId);

        if (conversation instanceof Channel channel) {
            channelAuthorizationService.isAllowedToCreateNewAnswerPostInChannel(channel, author);
        }

        Set<User> mentionedUsers = parseUserMentions(course, answerMessage.getContent());

        // use post from database rather than user input
        answerMessage.setPost(post);
        // set author to current user
        answerMessage.setAuthor(author);
        // on creation of an answer message, we set the resolves_post field to false per default since this feature is not used for messages
        answerMessage.setResolvesPost(false);
        AnswerPost savedAnswerMessage = answerPostRepository.save(answerMessage);
        savedAnswerMessage.getPost().setConversation(conversation);
        setAuthorRoleForPosting(savedAnswerMessage, course);
        SingleUserNotification notification = singleUserNotificationService.createNotificationAboutNewMessageReply(savedAnswerMessage, author, conversation);
        this.preparePostAndBroadcast(savedAnswerMessage, course, notification);
        this.singleUserNotificationService.notifyInvolvedUsersAboutNewMessageReply(post, notification, mentionedUsers, savedAnswerMessage, author);
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
        parseUserMentions(course, answerMessage.getContent());
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
            existingAnswerMessage.setUpdatedDate(ZonedDateTime.now());
        }

        updatedAnswerMessage = answerPostRepository.save(existingAnswerMessage);
        updatedAnswerMessage.getPost().setConversation(conversation);

        this.preparePostAndBroadcast(updatedAnswerMessage, course, null);
        return updatedAnswerMessage;
    }

    private Conversation mayUpdateOrDeleteAnswerMessageElseThrow(AnswerPost existingAnswerPost, User user) {
        boolean userIsAuthor = existingAnswerPost.getAuthor().getId().equals(user.getId());
        Conversation conversation = existingAnswerPost.getPost().getConversation();
        boolean isAllowedToEditOrDeleteOtherUsersMessage = conversation instanceof Channel channel
                && this.channelAuthorizationService.isAllowedToEditOrDeleteMessagesOfOtherUsers(channel, user);
        boolean isArchivedChannel = conversation instanceof Channel channel && channel.getIsArchived();

        if ((!userIsAuthor && !isAllowedToEditOrDeleteOtherUsersMessage) || isArchivedChannel) {
            throw new AccessForbiddenException("Answer Post", existingAnswerPost.getId());
        }

        return conversationService.getConversationById(existingAnswerPost.getPost().getConversation().getId());
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

        // we need to explicitly remove the answer post from the answers of the broadcast post to share up-to-date information
        Post updatedMessage = answerMessage.getPost();
        updatedMessage.removeAnswerPost(answerMessage);
        updatedMessage.setResolved(updatedMessage.getAnswers().stream().anyMatch(AnswerPost::doesResolvePost));
        updatedMessage.setConversation(conversation);
        // update on the message properties
        conversationMessageRepository.save(updatedMessage);

        // delete
        answerPostRepository.deleteById(answerMessageId);

        broadcastForPost(new PostDTO(updatedMessage, MetisCrudAction.UPDATE), course.getId(), null, null);
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
