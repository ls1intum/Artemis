package de.tum.cit.aet.artemis.service.metis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.DisplayPriority;
import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.domain.metis.CreatedConversationMessage;
import de.tum.cit.aet.artemis.domain.metis.Post;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.domain.metis.conversation.Conversation;
import de.tum.cit.aet.artemis.domain.metis.conversation.GroupChat;
import de.tum.cit.aet.artemis.domain.metis.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.domain.notification.ConversationNotification;
import de.tum.cit.aet.artemis.domain.notification.NotificationConstants;
import de.tum.cit.aet.artemis.domain.notification.SingleUserNotification;
import de.tum.cit.aet.artemis.domain.notification.SingleUserNotificationFactory;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.repository.LectureRepository;
import de.tum.cit.aet.artemis.repository.SingleUserNotificationRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.repository.metis.ConversationMessageRepository;
import de.tum.cit.aet.artemis.repository.metis.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.service.metis.conversation.ConversationService;
import de.tum.cit.aet.artemis.service.metis.conversation.auth.ChannelAuthorizationService;
import de.tum.cit.aet.artemis.service.metis.similarity.PostSimilarityComparisonStrategy;
import de.tum.cit.aet.artemis.service.notifications.ConversationNotificationService;
import de.tum.cit.aet.artemis.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.web.rest.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.MetisCrudAction;
import de.tum.cit.aet.artemis.web.websocket.dto.metis.PostDTO;

@Profile(PROFILE_CORE)
@Service
public class ConversationMessagingService extends PostingService {

    private static final int TOP_K_SIMILARITY_RESULTS = 5;

    private static final Logger log = LoggerFactory.getLogger(ConversationMessagingService.class);

    private final ConversationService conversationService;

    private final ConversationNotificationService conversationNotificationService;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final GroupNotificationService groupNotificationService;

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final PostSimilarityComparisonStrategy postContentCompareStrategy;

    protected ConversationMessagingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, LectureRepository lectureRepository,
            ConversationMessageRepository conversationMessageRepository, AuthorizationCheckService authorizationCheckService, WebsocketMessagingService websocketMessagingService,
            UserRepository userRepository, ConversationService conversationService, ConversationParticipantRepository conversationParticipantRepository,
            ConversationNotificationService conversationNotificationService, ChannelAuthorizationService channelAuthorizationService,
            GroupNotificationService groupNotificationService, SingleUserNotificationRepository singleUserNotificationRepository,
            PostSimilarityComparisonStrategy postContentCompareStrategy) {
        super(courseRepository, userRepository, exerciseRepository, lectureRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository);
        this.conversationService = conversationService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationNotificationService = conversationNotificationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.groupNotificationService = groupNotificationService;
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.postContentCompareStrategy = postContentCompareStrategy;
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

        var conversationId = newMessage.getConversation().getId();

        var conversation = conversationService.isMemberOrCreateForCourseWideElseThrow(conversationId, author, Optional.empty())
                .orElse(conversationService.loadConversationWithParticipantsIfGroupChat(conversationId));
        log.debug("      createMessage:conversationService.isMemberOrCreateForCourseWideElseThrow DONE");

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
        log.debug("      conversationMessageRepository.save DONE");

        createdMessage.setAuthor(author);
        setAuthorRoleForPosting(createdMessage, course);

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
        ConversationNotification notification = conversationNotificationService.createNotification(createdMessage, conversation, course,
                createdConversationMessage.mentionedUsers());
        PostDTO postDTO = new PostDTO(createdMessage, MetisCrudAction.CREATE, notification);
        createdMessage.getConversation().hideDetails();
        if (createdConversationMessage.completeConversation() instanceof Channel channel && channel.getIsCourseWide()) {
            // We don't need the list of participants for course-wide channels. We can delay the db query and send the WS messages first
            if (conversationService.isChannelVisibleToStudents(channel)) {
                broadcastForPost(postDTO, course.getId(), null, null);
            }
            log.debug("      broadcastForPost DONE");

            recipientSummaries = getNotificationRecipients(conversation).collect(Collectors.toSet());
            log.debug("      getNotificationRecipients DONE");
        }
        else {
            // In all other cases we need the list of participants to send the WS messages to the correct topics. Hence, the db query has to be made before sending WS messages
            recipientSummaries = getNotificationRecipients(conversation).collect(Collectors.toSet());
            log.debug("      getNotificationRecipients DONE");

            if (conversation instanceof OneToOneChat) {
                var getNumberOfPosts = conversationMessageRepository.countByConversationId(conversation.getId());
                if (getNumberOfPosts == 1) { // first message in one to one chat --> notify all participants that a conversation with them has been created
                    // Another websocket notification
                    conversationService.broadcastOnConversationMembershipChannel(course, MetisCrudAction.CREATE, conversation, mapToUsers(recipientSummaries));
                }
            }

            broadcastForPost(postDTO, course.getId(), recipientSummaries, createdConversationMessage.mentionedUsers());

            log.debug("      broadcastForPost DONE");
        }

        sendAndSaveNotifications(notification, createdConversationMessage, recipientSummaries);
    }

    /**
     * Sends and saves notifications for users that have not already been notified via broadcast notifications
     *
     * @param notification               the notification for the message
     * @param createdConversationMessage the new message and associated data
     * @param recipientSummaries         set of setting summaries for the recipients
     */
    private void sendAndSaveNotifications(ConversationNotification notification, CreatedConversationMessage createdConversationMessage,
            Set<ConversationNotificationRecipientSummary> recipientSummaries) {
        Post createdMessage = createdConversationMessage.messageWithHiddenDetails();
        User author = createdMessage.getAuthor();
        Conversation conversation = createdConversationMessage.completeConversation();
        Course course = conversation.getCourse();

        Set<User> mentionedUsers = createdConversationMessage.mentionedUsers().stream()
                .map(user -> new User(user.getId(), user.getLogin(), user.getFirstName(), user.getLastName(), user.getLangKey(), user.getEmail())).collect(Collectors.toSet());

        Set<User> notificationRecipients = filterNotificationRecipients(author, conversation, recipientSummaries, mentionedUsers);
        // Add all mentioned users, including the author (if mentioned). Since working with sets, there are no duplicate user entries
        notificationRecipients.addAll(mentionedUsers);

        conversationNotificationService.notifyAboutNewMessage(createdMessage, notification, notificationRecipients);
        log.debug("      conversationNotificationService.notifyAboutNewMessage DONE");

        conversationParticipantRepository.incrementUnreadMessagesCountOfParticipants(conversation.getId(), author.getId());
        log.debug("      incrementUnreadMessagesCountOfParticipants DONE");

        if (conversation instanceof Channel channel && channel.getIsAnnouncementChannel()) {
            saveAnnouncementNotification(createdMessage, channel, course, notificationRecipients);
            log.debug("      saveAnnouncementNotification DONE");
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
     * If the conversation is not an announcement channel, the method filters out participants, that have muted or hidden the conversation.
     * If the conversation is not visible to students, the method also filters out students from the provided list of recipients.
     *
     * @param author                 the author of the message
     * @param conversation           the conversation the new message has been written in
     * @param notificationRecipients the list of users that should be filtered
     * @param mentionedUsers         users mentioned within the message
     * @return filtered list of users that are supposed to receive a notification
     */
    private Set<User> filterNotificationRecipients(User author, Conversation conversation, Set<ConversationNotificationRecipientSummary> notificationRecipients,
            Set<User> mentionedUsers) {
        // Initialize filter with check for author
        Predicate<ConversationNotificationRecipientSummary> filter = recipientSummary -> !Objects.equals(recipientSummary.userId(), author.getId());

        if (conversation instanceof Channel channel) {
            // If a channel is not an announcement channel, filter out users, that muted or hid the conversation
            if (!channel.getIsAnnouncementChannel()) {
                filter = filter.and(summary -> summary.shouldNotifyRecipient() || mentionedUsers
                        .contains(new User(summary.userId(), summary.userLogin(), summary.firstName(), summary.lastName(), summary.userLangKey(), summary.userEmail())));
            }

            // If a channel is not visible to students, filter out participants that are only students
            if (!conversationService.isChannelVisibleToStudents(channel)) {
                filter = filter.and(ConversationNotificationRecipientSummary::isAtLeastTutorInCourse);
            }
        }
        else {
            filter = filter.and(ConversationNotificationRecipientSummary::shouldNotifyRecipient);
        }

        return notificationRecipients.stream().filter(filter)
                .map(summary -> new User(summary.userId(), summary.userLogin(), summary.firstName(), summary.lastName(), summary.userLangKey(), summary.userEmail()))
                .collect(Collectors.toSet());
    }

    /**
     * fetch posts from database by conversationId
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch posts
     * @param requestingUser    the user requesting messages in course-wide channels
     * @param courseId          the id of the course the post belongs to
     * @return page of posts that match the given context
     */
    public Page<Post> getMessages(Pageable pageable, @Valid PostContextFilterDTO postContextFilter, User requestingUser, Long courseId) {
        conversationService.isMemberOrCreateForCourseWideElseThrow(postContextFilter.conversationId(), requestingUser, Optional.of(ZonedDateTime.now()));

        // The following query loads posts, answerPosts and reactions to avoid too many database calls (due to eager references)
        Page<Post> conversationPosts = conversationMessageRepository.findMessages(postContextFilter, pageable, requestingUser.getId());
        setAuthorRoleOfPostings(conversationPosts.getContent(), courseId);

        // invoke async due to db write access to avoid that the client has to wait
        conversationParticipantRepository.updateLastReadAsync(requestingUser.getId(), postContextFilter.conversationId(), ZonedDateTime.now());

        return conversationPosts;
    }

    /**
     * Fetch messages from database by a list of course-wide channels.
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch messages
     * @param requestingUser    the user requesting messages in course-wide channels
     * @param courseId          the id of the course the post belongs to
     * @return page of posts that match the given context
     */
    public Page<Post> getCourseWideMessages(Pageable pageable, @Valid PostContextFilterDTO postContextFilter, User requestingUser, Long courseId) {
        // The following query loads posts, answerPosts and reactions to avoid too many database calls (due to eager references)
        Page<Post> conversationPosts = conversationMessageRepository.findCourseWideMessages(postContextFilter, pageable, requestingUser.getId());
        setAuthorRoleOfPostings(conversationPosts.getContent(), courseId);
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
        broadcastForPost(new PostDTO(updatedPost, MetisCrudAction.UPDATE), course.getId(), null, null);

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

        broadcastForPost(new PostDTO(post, MetisCrudAction.DELETE), course.getId(), null, null);
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
        final Course course = courseRepository.findByIdElseThrow(courseId);
        preCheckUserAndCourseForCommunicationOrMessaging(user, course);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        Post message = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        message.setDisplayPriority(displayPriority);

        Conversation conversation = conversationService.isMemberOrCreateForCourseWideElseThrow(message.getConversation().getId(), user, Optional.empty())
                .orElse(message.getConversation());

        if (conversation instanceof Channel && !channelAuthorizationService.hasChannelModerationRights(conversation.getId(), user)
                || conversation instanceof GroupChat && !user.getId().equals(conversation.getCreator().getId())) {
            throw new AccessForbiddenException("You are not allowed to change the display priority of messages in this conversation");
        }

        Post updatedMessage = conversationMessageRepository.save(message);
        message.getConversation().hideDetails();
        broadcastForPost(new PostDTO(message, MetisCrudAction.UPDATE), course.getId(), null, null);
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

    /**
     * Calculates k similar posts based on the underlying content comparison strategy
     *
     * @param courseId id of the course in which similar posts are searched for
     * @param post     post that is to be created and check for similar posts beforehand
     * @return list of similar posts
     */
    // TODO: unused, remove
    public List<Post> getSimilarPosts(Long courseId, Post post) {
        PostContextFilterDTO postContextFilter = new PostContextFilterDTO(courseId, null, null, null, null, false, false, false, null, null);
        List<Post> coursePosts = this.getCourseWideMessages(Pageable.unpaged(), postContextFilter, userRepository.getUser(), courseId).stream()
                .sorted(Comparator.comparing(coursePost -> postContentCompareStrategy.performSimilarityCheck(post, coursePost))).toList();

        // sort course posts by calculated similarity scores
        setAuthorRoleOfPostings(coursePosts, courseId);
        return Lists.reverse(coursePosts).stream().limit(TOP_K_SIMILARITY_RESULTS).toList();
    }

    /**
     * Checks course and user validity,
     * retrieves all tags for posts in a certain course
     *
     * @param courseId id of the course the tags belongs to
     * @return tags of all posts that belong to the course
     */
    // TODO: unused, delete
    public List<String> getAllCourseTags(Long courseId) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        final Course course = courseRepository.findByIdElseThrow(courseId);

        // checks
        preCheckUserAndCourseForCommunicationOrMessaging(user, course);
        return conversationMessageRepository.findPostTagsForCourse(courseId);
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
}
