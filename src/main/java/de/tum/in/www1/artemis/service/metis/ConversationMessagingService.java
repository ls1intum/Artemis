package de.tum.in.www1.artemis.service.metis;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.metis.ConversationParticipant;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.service.notifications.ConversationNotificationService;
import de.tum.in.www1.artemis.web.rest.dto.PostContextFilter;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.in.www1.artemis.web.websocket.dto.metis.PostDTO;

@Service
public class ConversationMessagingService extends PostingService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ConversationService conversationService;

    private final ConversationNotificationService conversationNotificationService;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final ConversationRepository conversationRepository;

    protected ConversationMessagingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            ConversationMessageRepository conversationMessageRepository, AuthorizationCheckService authorizationCheckService, WebsocketMessagingService websocketMessagingService,
            UserRepository userRepository, ConversationService conversationService, ConversationParticipantRepository conversationParticipantRepository,
            ConversationNotificationService conversationNotificationService, ChannelAuthorizationService channelAuthorizationService,
            ConversationRepository conversationRepository) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository);
        this.conversationService = conversationService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationNotificationService = conversationNotificationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.conversationRepository = conversationRepository;
    }

    /**
     * Creates a new message in a conversation
     *
     * @param courseId   the id where the conversation is located
     * @param newMessage the message to be created includes the conversation id
     * @return the created message
     */
    public Post createMessage(Long courseId, Post newMessage) {
        if (newMessage.getId() != null) {
            throw new BadRequestAlertException("A new message post cannot already have an ID", METIS_POST_ENTITY_NAME, "idexists");
        }
        if (newMessage.getConversation() == null || newMessage.getConversation().getId() == null) {
            throw new BadRequestAlertException("A new message post must have a conversation", METIS_POST_ENTITY_NAME, "conversationnotset");
        }

        var author = this.userRepository.getUserWithGroupsAndAuthorities();
        newMessage.setAuthor(author);
        newMessage.setDisplayPriority(DisplayPriority.NONE);

        conversationService.isMemberElseThrow(newMessage.getConversation().getId(), author.getId());

        var conversation = conversationRepository.findByIdElseThrow(newMessage.getConversation().getId());
        // IMPORTANT we don't need it in the conversation any more, so we reduce the amount of data sent to clients
        conversation.setConversationParticipants(Set.of());
        var course = preCheckUserAndCourseForMessaging(author, courseId);

        // extra checks for channels
        if (conversation instanceof Channel channel) {
            channelAuthorizationService.isAllowedToCreateNewPostInChannel(channel, author);
        }

        // update last message date of conversation
        conversation.setLastMessageDate(ZonedDateTime.now());
        conversation.setCourse(course);
        Conversation savedConversation = conversationService.updateConversation(conversation);

        // update last read date and unread message count of author
        // invoke async due to db write access to avoid that the client has to wait
        conversationParticipantRepository.updateLastReadAsync(author.getId(), conversation.getId(), ZonedDateTime.now());

        var createdMessage = conversationMessageRepository.save(newMessage);
        // set the conversation again, because it might have been lost during save
        createdMessage.setConversation(conversation);
        // reduce the payload of the response / websocket message: this is important to avoid overloading the involved subsystems
        if (createdMessage.getConversation() != null) {
            createdMessage.getConversation().hideDetails();
        }

        // TODO: we should consider invoking the following method async to avoid that authors wait for the message creation if many notifications are sent
        notifyAboutMessageCreation(author, savedConversation, course, createdMessage);

        return createdMessage;
    }

    private void notifyAboutMessageCreation(User author, Conversation conversation, Course course, Post createdMessage) {
        Set<ConversationWebSocketRecipientSummary> webSocketRecipients = getWebSocketRecipients(conversation).collect(Collectors.toSet());
        Set<User> broadcastRecipients = webSocketRecipients.stream().map(ConversationWebSocketRecipientSummary::user).collect(Collectors.toSet());

        // Websocket notification 1: this notifies everyone including the author that there is a new message
        broadcastForPost(new PostDTO(createdMessage, MetisCrudAction.CREATE), course, broadcastRecipients);

        if (conversation instanceof OneToOneChat) {
            var getNumberOfPosts = conversationMessageRepository.countByConversationId(conversation.getId());
            if (getNumberOfPosts == 1) { // first message in one to one chat --> notify all participants that a conversation with them has been created
                // Another websocket notification
                conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, conversation, broadcastRecipients);
            }
        }
        conversationParticipantRepository.incrementUnreadMessagesCountOfParticipants(conversation.getId(), author.getId());
        // ToDo: Optimization Idea: Maybe we can save this websocket call and instead get the last message date from the conversation object in the post somehow?
        // send conversation with updated last message date to participants. This is necessary to show the unread messages badge in the client

        // TODO: why do we need notification 2 and 3? we should definitely re-work this!
        // Websocket notification 2
        conversationService.notifyAllConversationMembersAboutNewMessage(course, conversation, broadcastRecipients);

        // creation of message posts should not trigger entity creation alert
        // Websocket notification 3
        var notificationRecipients = filterNotificationRecipients(author, conversation, webSocketRecipients);
        conversationNotificationService.notifyAboutNewMessage(createdMessage, notificationRecipients, course);
    }

    /**
     * Filters the given list of recipients for users that should receive a notification about a new message.
     * <p>
     * In all cases, the author will be filtered out.
     * If the conversation is not an announcement channel, the method filters out participants, that have hidden the conversation.
     * If the conversation is not visible to students, the method also filters out students from the provided list of recipients.
     *
     * @param author              the author of the message
     * @param conversation        the conversation the new message has been written in
     * @param webSocketRecipients the list of users that should be filtered
     * @return filtered list of users that are supposed to receive a notification
     */
    private Set<User> filterNotificationRecipients(User author, Conversation conversation, Set<ConversationWebSocketRecipientSummary> webSocketRecipients) {
        // Initialize filter with check for author
        Predicate<ConversationWebSocketRecipientSummary> filter = recipientSummary -> !Objects.equals(recipientSummary.user().getId(), author.getId());

        if (conversation instanceof Channel channel) {
            // If a channel is not an announcement channel, filter out users, that hid the conversation
            if (!channel.getIsAnnouncementChannel()) {
                filter = filter.and(recipientSummary -> !recipientSummary.isConversationHidden());
            }

            // If a channel is not visible to students, filter out participants that are only students
            if (!conversationService.isChannelVisibleToStudents(channel)) {
                filter = filter.and(ConversationWebSocketRecipientSummary::isAtLeastTutorInCourse);
            }
        }
        else {
            filter = filter.and(recipientSummary -> !recipientSummary.isConversationHidden());
        }

        return webSocketRecipients.stream().filter(filter).map(ConversationWebSocketRecipientSummary::user).collect(Collectors.toSet());
    }

    /**
     * fetch posts from database by conversationId
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch posts
     * @return page of posts that match the given context
     */
    public Page<Post> getMessages(Pageable pageable, @Valid PostContextFilter postContextFilter) {

        if (postContextFilter.getConversationId() == null) {
            throw new BadRequestAlertException("Messages must be associated with a conversion", METIS_POST_ENTITY_NAME, "conversationMissing");
        }

        var requestingUser = userRepository.getUser();
        if (!conversationService.isMember(postContextFilter.getConversationId(), requestingUser.getId())) {
            Conversation conversation = conversationRepository.findByIdElseThrow(postContextFilter.getConversationId());

            if (conversation instanceof Channel channel && channel.getIsCourseWide()) {
                ConversationParticipant conversationParticipant = ConversationParticipant.createWithDefaultValues(requestingUser, channel);
                // Mark messages as read
                conversationParticipant.setLastRead(ZonedDateTime.now());
                conversationParticipantRepository.saveAndFlush(conversationParticipant);
            }
            else {
                throw new AccessForbiddenException("User not allowed to access this conversation!");
            }

        }

        // The following query loads posts, answerPosts and reactions to avoid too many database calls (due to eager references)
        Page<Post> conversationPosts = conversationMessageRepository.findMessages(postContextFilter, pageable, requestingUser.getId());

        // protect sample solution, grading instructions, etc.
        conversationPosts.stream().map(Post::getExercise).filter(Objects::nonNull).forEach(Exercise::filterSensitiveInformation);
        setAuthorRoleOfPostings(conversationPosts.getContent());

        // invoke async due to db write access to avoid that the client has to wait
        conversationParticipantRepository.updateLastReadAsync(requestingUser.getId(), postContextFilter.getConversationId(), ZonedDateTime.now());

        return conversationPosts;
    }

    /**
     * Checks course, user and post validity,
     * updates non-restricted field of the post, persists the post,
     * and ensures that sensitive information is filtered out
     *
     * @param courseId    id of the course the post belongs to
     * @param postId      id of the post to update
     * @param messagePost post to update
     * @return updated post that was persisted
     */
    public Post updateMessage(Long courseId, Long postId, Post messagePost) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        // check
        if (messagePost.getId() == null || !Objects.equals(messagePost.getId(), postId)) {
            throw new BadRequestAlertException("Invalid id", METIS_POST_ENTITY_NAME, "idnull");
        }

        Post existingMessage = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        Conversation conversation = mayUpdateOrDeleteMessageElseThrow(existingMessage, user);
        var course = preCheckUserAndCourseForMessaging(user, courseId);
        // update: allow overwriting of values only for depicted fields
        existingMessage.setContent(messagePost.getContent());
        existingMessage.setUpdatedDate(ZonedDateTime.now());

        Post updatedPost = conversationMessageRepository.save(existingMessage);
        updatedPost.setConversation(conversation);

        // emit a post update via websocket
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course, null);

        return updatedPost;
    }

    /**
     * Checks course, user and post validity,
     * determines authority to delete post and deletes the post
     *
     * @param courseId id of the course the post belongs to
     * @param postId   id of the message post to delete
     */
    public void deleteMessageById(Long courseId, Long postId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        Post post = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        var conversation = mayUpdateOrDeleteMessageElseThrow(post, user);
        var course = preCheckUserAndCourseForMessaging(user, courseId);
        post.setConversation(conversation);

        // delete
        conversationMessageRepository.deleteById(postId);
        conversationParticipantRepository.decrementUnreadMessagesCountOfParticipants(conversation.getId(), user.getId());
        conversation = conversationService.getConversationById(conversation.getId());

        conversationService.notifyAllConversationMembersAboutUpdate(conversation);

        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course, null);
    }

    private Conversation mayUpdateOrDeleteMessageElseThrow(Post existingMessagePost, User user) {
        if (existingMessagePost.getConversation() == null) {
            throw new BadRequestAlertException("The post does not belong to a conversation", METIS_POST_ENTITY_NAME, "conversationnotset");
        }

        var conversation = conversationService.getConversationById(existingMessagePost.getConversation().getId());
        if (existingMessagePost.getAuthor().getId().equals(user.getId())
                || (conversation instanceof Channel channel && channelAuthorizationService.isAllowedToEditOrDeleteMessagesOfOtherUsers(channel, user))) {
            if (conversation instanceof Channel channel && channel.getIsArchived()) {
                throw new BadRequestAlertException("A message cannot be created in an archived channel", METIS_POST_ENTITY_NAME, "channelarchived");
            }
            return conversation;
        }
        else {
            throw new AccessForbiddenException("You are not allowed to edit or delete this message");
        }
    }

    @Override
    public String getEntityName() {
        return METIS_POST_ENTITY_NAME;
    }
}
