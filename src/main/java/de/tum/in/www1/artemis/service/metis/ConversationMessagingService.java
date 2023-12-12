package de.tum.in.www1.artemis.service.metis;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.validation.Valid;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ConversationNotificationRecipientSummary;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.CreatedConversationMessage;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.in.www1.artemis.domain.notification.ConversationNotification;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.ConversationMessageRepository;
import de.tum.in.www1.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ConversationRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;
import de.tum.in.www1.artemis.service.metis.conversation.ConversationService;
import de.tum.in.www1.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.in.www1.artemis.service.notifications.ConversationNotificationService;
import de.tum.in.www1.artemis.service.notifications.GroupNotificationService;
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

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final SimpUserRegistry simpUserRegistry;

    protected ConversationMessagingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            ConversationMessageRepository conversationMessageRepository, AuthorizationCheckService authorizationCheckService, WebsocketMessagingService websocketMessagingService,
            UserRepository userRepository, ConversationService conversationService, ConversationParticipantRepository conversationParticipantRepository,
            ConversationNotificationService conversationNotificationService, ChannelAuthorizationService channelAuthorizationService, ConversationRepository conversationRepository,
            GroupNotificationService groupNotificationService, SingleUserNotificationRepository singleUserNotificationRepository, SimpUserRegistry simpUserRegistry) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository);
        this.conversationService = conversationService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationNotificationService = conversationNotificationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.conversationRepository = conversationRepository;
        this.groupNotificationService = groupNotificationService;
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * Creates a new message in a conversation
     *
     * @param courseId   the id where the conversation is located
     * @param newMessage the message to be created includes the conversation id
     * @return the created message and associated data
     */
    public CreatedConversationMessage createMessage(Long courseId, Post newMessage) {
        var author = this.userRepository.getUserWithGroupsAndAuthorities();
        newMessage.setAuthor(author);
        newMessage.setDisplayPriority(DisplayPriority.NONE);

        var conversation = conversationService.isMemberOrCreateForCourseWideElseThrow(newMessage.getConversation().getId(), author, Optional.empty())
                .orElse(conversationRepository.findByIdElseThrow(newMessage.getConversation().getId()));
        log.debug("      createMessage:conversationService.isMemberOrCreateForCourseWideElseThrow DONE");

        // IMPORTANT we don't need it in the conversation any more, so we reduce the amount of data sent to clients
        conversation.setConversationParticipants(Set.of());
        var course = preCheckUserAndCourseForMessaging(author, courseId);

        // extra checks for channels
        if (conversation instanceof Channel channel) {
            channelAuthorizationService.isAllowedToCreateNewPostInChannel(channel, author);
        }
        log.debug("      createMessage:additional authorization DONE");
        Set<User> mentionedUsers = parseUserMentions(course, newMessage.getContent());
        log.debug("      createMessage:parseUserMentions DONE");

        // update last message date of conversation
        conversation.setLastMessageDate(ZonedDateTime.now());
        conversation.setCourse(course);
        Conversation savedConversation = conversationService.updateConversation(conversation);

        // update last read date and unread message count of author
        // invoke async due to db write access to avoid that the client has to wait
        conversationParticipantRepository.updateLastReadAsync(author.getId(), conversation.getId(), ZonedDateTime.now());

        var createdMessage = conversationMessageRepository.save(newMessage);
        log.debug("      conversationMessageRepository.save DONE");
        // set the conversation again, because it might have been lost during save
        createdMessage.setConversation(conversation);
        // reduce the payload of the response / websocket message: this is important to avoid overloading the involved subsystems
        createdMessage.getConversation().hideDetails();
        log.debug("      conversationMessageRepository.save DONE");

        return new CreatedConversationMessage(createdMessage, savedConversation, mentionedUsers);
    }

    /**
     * Notifies conversation members and mentioned users about a new message in a conversation
     *
     * @param createdConversationMessage the new message and associated data
     */
    @Async
    public void notifyAboutMessageCreation(CreatedConversationMessage createdConversationMessage) {
        SecurityUtils.setAuthorizationObject(); // required for async
        Post createdMessage = createdConversationMessage.messageWithHiddenDetails();
        Conversation conversation = createdConversationMessage.completeConversation();
        Course course = conversation.getCourse();

        // Websocket notification 1: this notifies everyone including the author that there is a new message
        Set<ConversationNotificationRecipientSummary> recipientSummaries;
        Set<User> recipientUsers;
        ConversationNotification notification = conversationNotificationService.createNotification(createdMessage, conversation, course,
                createdConversationMessage.mentionedUsers());
        PostDTO postDTO = new PostDTO(createdMessage, MetisCrudAction.CREATE, null, notification);
        if (createdConversationMessage.completeConversation() instanceof Channel channel && channel.getIsCourseWide()) {
            // We don't need the list of participants for course-wide channels. We can delay the db query and send the WS messages first
            if (conversationService.isChannelVisibleToStudents(channel)) {
                broadcastForPost(postDTO, course.getId(), null);
            }
            log.debug("      broadcastForPost DONE");

            recipientSummaries = getNotificationRecipients(conversation).collect(Collectors.toSet());
            log.debug("      getNotificationRecipients DONE");
            recipientUsers = mapToUsers(recipientSummaries);
        }
        else {
            // In all other cases we need the list of participants to send the WS messages to the correct topics. Hence, the db query has to be made before sending WS messages
            recipientSummaries = getNotificationRecipients(conversation).collect(Collectors.toSet());
            log.debug("      getNotificationRecipients DONE");
            recipientUsers = mapToUsers(recipientSummaries);

            if (conversation instanceof OneToOneChat) {
                var getNumberOfPosts = conversationMessageRepository.countByConversationId(conversation.getId());
                if (getNumberOfPosts == 1) { // first message in one to one chat --> notify all participants that a conversation with them has been created
                    // Another websocket notification
                    conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, conversation, recipientUsers);
                }
            }

            broadcastForPost(postDTO, course.getId(), recipientUsers);
            log.debug("      broadcastForPost DONE");
        }

        sendAndSaveNotifications(notification, createdConversationMessage, recipientUsers, recipientSummaries);
    }

    /**
     * Sends and saves notifications for users that have not already been notified via broadcast notifications
     *
     * @param notification               the notification for the message
     * @param createdConversationMessage the new message and associated data
     * @param recipientUsers             set of users that should receive notifications
     * @param recipientSummaries         set of setting summaries for the recipients
     */
    private void sendAndSaveNotifications(ConversationNotification notification, CreatedConversationMessage createdConversationMessage, Set<User> recipientUsers,
            Set<ConversationNotificationRecipientSummary> recipientSummaries) {
        // Add all mentioned users, including the author (if mentioned). Since working with sets, there are no duplicate user entries
        Post createdMessage = createdConversationMessage.messageWithHiddenDetails();
        User author = createdMessage.getAuthor();
        Conversation conversation = createdConversationMessage.completeConversation();
        Course course = conversation.getCourse();

        Set<User> mentionedUsers = createdConversationMessage.mentionedUsers().stream()
                .map(user -> new User(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getLangKey(), user.getEmail())).collect(Collectors.toSet());
        recipientUsers.addAll(mentionedUsers);

        Set<User> notificationRecipients = filterNotificationRecipients(author, conversation, recipientSummaries, mentionedUsers);
        log.debug("      conversationNotificationService.notifyAboutNewMessage DONE");

        Set<String> onlineUserLogins = getLoginsOfSubscribedUsers("/topic/metis/" + "courses/" + course.getId());
        log.debug("      getLoginsOfSubscribedUsers DONE");

        // Websocket notification 1: this notifies everyone including the author that there is a new message
        // broadcastForPost(new PostDTO(createdMessage, MetisCrudAction.CREATE, null, notification), course, recipientUsers);
        log.debug("      broadcastForPost DONE");
        conversationParticipantRepository.incrementUnreadMessagesCountOfParticipants(conversation.getId(), author.getId());
        log.debug("      incrementUnreadMessagesCountOfParticipants DONE");
        // ToDo: Optimization Idea: Maybe we can save this websocket call and instead get the last message date from the conversation object in the post somehow?
        // send conversation with updated last message date to participants. This is necessary to show the unread messages badge in the client

        // TODO: why do we need notification 2 and 3? we should definitely re-work this!
        // Websocket notification 2
        // conversationService.notifyAllConversationMembersAboutNewMessage(course, conversation, recipientUsers);
        // log.debug(" conversationService.notifyAllConversationMembersAboutNewMessage DONE");

        // creation of message posts should not trigger entity creation alert
        // Websocket notification 3
        // Set<User> notificationRecipients = filterNotificationRecipients(author, conversation, recipientSummaries, mentionedUsers);
        conversationNotificationService.notifyAboutNewMessage(createdMessage, notification, notificationRecipients, course, mentionedUsers);
        // log.debug(" conversationNotificationService.notifyAboutNewMessage DONE");

        if (conversation instanceof Channel channel && channel.getIsAnnouncementChannel()) {
            saveAnnouncementNotification(createdMessage, channel, course, recipientUsers);
        }
        log.debug("      notifyAboutMessageCreation DONE");
    }

    /**
     * Maps a set of {@link ConversationNotificationRecipientSummary} to a set of {@link User}
     *
     * @param webSocketRecipients Set of recipient summaries
     * @return Set of users meant to receive WebSocket messages
     */
    private static Set<User> mapToUsers(Set<ConversationNotificationRecipientSummary> webSocketRecipients) {
        return webSocketRecipients.stream()
                .map(summary -> new User(summary.userId(), summary.userLogin(), summary.firstName(), summary.lastName(), summary.userLangKey(), summary.userEmail()))
                .collect(Collectors.toSet());
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
     * @param mentionedUsers      users mentioned within the message
     * @return filtered list of users that are supposed to receive a notification
     */
    private Set<User> filterNotificationRecipients(User author, Conversation conversation, Set<ConversationNotificationRecipientSummary> webSocketRecipients,
            Set<User> mentionedUsers) {
        // Initialize filter with check for author
        Predicate<ConversationNotificationRecipientSummary> filter = recipientSummary -> !Objects.equals(recipientSummary.userId(), author.getId());

        if (conversation instanceof Channel channel) {
            // If a channel is not an announcement channel, filter out users, that hid the conversation
            if (!channel.getIsAnnouncementChannel()) {
                filter = filter.and(summary -> !summary.isConversationHidden() || mentionedUsers
                        .contains(new User(summary.userId(), summary.userLogin(), summary.firstName(), summary.lastName(), summary.userLangKey(), summary.userEmail())));
            }

            // If a channel is not visible to students, filter out participants that are only students
            if (!conversationService.isChannelVisibleToStudents(channel)) {
                filter = filter.and(ConversationNotificationRecipientSummary::isAtLeastTutorInCourse);
            }
        }
        else {
            filter = filter.and(recipientSummary -> !recipientSummary.isConversationHidden());
        }

        return webSocketRecipients.stream().filter(filter)
                .map(summary -> new User(summary.userId(), summary.userLogin(), summary.firstName(), summary.lastName(), summary.userLangKey(), summary.userEmail()))
                .collect(Collectors.toSet());
    }

    /**
     * fetch posts from database by conversationId
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch posts
     * @param requestingUser    the user requesting messages in course-wide channels
     * @return page of posts that match the given context
     */
    public Page<Post> getMessages(Pageable pageable, @Valid PostContextFilter postContextFilter, User requestingUser) {
        conversationService.isMemberOrCreateForCourseWideElseThrow(postContextFilter.getConversationId(), requestingUser, Optional.of(ZonedDateTime.now()));

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
     * Fetch messages from database by a list of course-wide channels.
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch messages
     * @param requestingUser    the user requesting messages in course-wide channels
     * @return page of posts that match the given context
     */
    public Page<Post> getCourseWideMessages(Pageable pageable, @Valid PostContextFilter postContextFilter, User requestingUser) {
        // The following query loads posts, answerPosts and reactions to avoid too many database calls (due to eager references)
        Page<Post> conversationPosts = conversationMessageRepository.findCourseWideMessages(postContextFilter, pageable, requestingUser.getId());

        setAuthorRoleOfPostings(conversationPosts.getContent());

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

        parseUserMentions(course, messagePost.getContent());

        // update: allow overwriting of values only for depicted fields
        existingMessage.setContent(messagePost.getContent());
        existingMessage.setTitle(messagePost.getTitle());
        existingMessage.setUpdatedDate(ZonedDateTime.now());

        Post updatedPost = conversationMessageRepository.save(existingMessage);
        updatedPost.setConversation(conversation);

        // emit a post update via websocket
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course.getId(), null);

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

        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course.getId(), null);
    }

    /**
     * Invokes the updateMessage method to persist the change of displayPriority
     *
     * @param courseId        id of the course the post belongs to
     * @param postId          id of the message to change the pin state for
     * @param displayPriority new displayPriority
     * @return updated post that was persisted
     */
    public Post changeDisplayPriority(Long courseId, Long postId, DisplayPriority displayPriority) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = preCheckUserAndCourseForMessaging(user, courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);

        Post message = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        message.setDisplayPriority(displayPriority);

        conversationService.isMemberOrCreateForCourseWideElseThrow(message.getConversation().getId(), user, Optional.empty());

        Post updatedMessage = conversationMessageRepository.save(message);
        message.getConversation().hideDetails();
        broadcastForPost(new PostDTO(message, MetisCrudAction.UPDATE), course.getId(), null);
        return updatedMessage;
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

    /**
     * Saves announcement notifications for each course group
     *
     * @param message    message that triggered the notification
     * @param channel    announcement channel the message belongs to
     * @param course     course the channel belongs to
     * @param recipients channel members, if the channel is not course-wide
     */
    private void saveAnnouncementNotification(Post message, Channel channel, Course course, Set<User> recipients) {
        // create post for notification
        Post postForNotification = new Post();
        postForNotification.setId(message.getId());
        postForNotification.setAuthor(message.getAuthor());
        postForNotification.setCourse(course);
        postForNotification.setConversation(channel);
        postForNotification.setCreationDate(message.getCreationDate());
        postForNotification.setTitle(message.getTitle());

        // create html content
        Parser parser = Parser.builder().build();
        String htmlPostContent;
        try {
            Node document = parser.parse(message.getContent());
            HtmlRenderer renderer = HtmlRenderer.builder().build();
            htmlPostContent = renderer.render(document);
        }
        catch (Exception e) {
            htmlPostContent = "";
        }
        postForNotification.setContent(htmlPostContent);

        if (channel.getIsCourseWide()) {
            groupNotificationService.notifyAllGroupsAboutNewAnnouncement(postForNotification, course);
        }
        else {
            String[] placeholders = new String[] { course.getTitle(), message.getContent(), message.getCreationDate().toString(), channel.getName(), message.getAuthor().getName(),
                    "channel" };
            Set<SingleUserNotification> announcementNotifications = recipients.stream().map(recipient -> SingleUserNotificationFactory.createNotification(postForNotification,
                    NotificationType.NEW_ANNOUNCEMENT_POST, NotificationConstants.NEW_ANNOUNCEMENT_POST_TEXT, placeholders, recipient)).collect(Collectors.toSet());
            announcementNotifications.add(SingleUserNotificationFactory.createNotification(postForNotification, NotificationType.NEW_ANNOUNCEMENT_POST,
                    NotificationConstants.NEW_ANNOUNCEMENT_POST_TEXT, placeholders, postForNotification.getAuthor()));
            singleUserNotificationRepository.saveAll(announcementNotifications);
        }
    }

    /**
     * Finds all users that are currently subscribed to one the provided topic
     *
     * @param topic destination/topic for which to get the subscribers
     * @return an unmodifiable list of user logins subscribed to the provided topic
     */
    private Set<String> getLoginsOfSubscribedUsers(String topic) {
        return simpUserRegistry.findSubscriptions(subscription -> subscription.getDestination().equals(topic)).stream()
                .map(subscription -> subscription.getSession().getUser().getName()).collect(Collectors.toSet());
    }
}
