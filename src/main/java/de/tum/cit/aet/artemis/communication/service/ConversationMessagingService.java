package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getAnsweredOrReactedSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getConversationsSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getCourseWideChannelsSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getPinnedSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getSearchTextAndAuthorSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getSortSpecification;
import static de.tum.cit.aet.artemis.communication.repository.MessageSpecs.getUnresolvedSpecification;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.CreatedConversationMessage;
import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.Reaction;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.conversation.GroupChat;
import de.tum.cit.aet.artemis.communication.domain.conversation.OneToOneChat;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewAnnouncementNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewMentionNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewPostNotification;
import de.tum.cit.aet.artemis.communication.dto.CreatePostDTO;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostContextFilterDTO;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.communication.repository.ReactionRepository;
import de.tum.cit.aet.artemis.communication.repository.SavedPostRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;
import de.tum.cit.aet.artemis.communication.service.conversation.auth.ChannelAuthorizationService;
import de.tum.cit.aet.artemis.communication.service.notifications.SingleUserNotificationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ConversationMessagingService extends PostingService {

    private static final Logger log = LoggerFactory.getLogger(ConversationMessagingService.class);

    private final ConversationService conversationService;

    private final ConversationMessageRepository conversationMessageRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final CourseNotificationService courseNotificationService;

    private final PostRepository postRepository;

    private final SingleUserNotificationService singleUserNotificationService;

    private final ReactionRepository reactionRepository;

    protected ConversationMessagingService(CourseRepository courseRepository, ExerciseRepository exerciseRepository, ConversationMessageRepository conversationMessageRepository,
            AuthorizationCheckService authorizationCheckService, WebsocketMessagingService websocketMessagingService, UserRepository userRepository,
            ConversationService conversationService, ConversationParticipantRepository conversationParticipantRepository, AnswerPostRepository answerPostRepository,
            ChannelAuthorizationService channelAuthorizationService, SavedPostRepository savedPostRepository, CourseNotificationService courseNotificationService,
            PostRepository postRepository, SingleUserNotificationService singleUserNotificationService, ReactionRepository reactionRepository) {
        super(courseRepository, userRepository, exerciseRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository, savedPostRepository);
        this.conversationService = conversationService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.answerPostRepository = answerPostRepository;
        this.channelAuthorizationService = channelAuthorizationService;
        this.courseNotificationService = courseNotificationService;
        this.postRepository = postRepository;
        this.singleUserNotificationService = singleUserNotificationService;
        this.reactionRepository = reactionRepository;
    }

    /**
     * Creates a new message in a conversation
     *
     * @param courseId the id where the conversation is located
     * @param message  the post to be created includes the conversation id
     * @return the created message and associated data
     */
    public CreatedConversationMessage createMessage(Long courseId, CreatePostDTO message) {
        var author = this.userRepository.getUserWithGroupsAndAuthorities();

        var newMessage = message.toEntity();
        newMessage.setAuthor(author);
        newMessage.setDisplayPriority(DisplayPriority.NONE);

        var conversationId = message.conversation().id();

        var conversation = conversationService.isMemberOrCreateForCourseWideElseThrow(conversationId, author, Optional.empty())
                .orElse(conversationService.loadConversationWithParticipantsIfGroupChat(conversationId));
        log.debug("      createMessage:conversationService.isMemberOrCreateForCourseWideElseThrow DONE");

        newMessage.setConversation(conversation);

        var course = preCheckUserAndCourseForMessaging(author, courseId);

        // extra checks for channels
        if (conversation instanceof Channel channel) {
            channelAuthorizationService.isAllowedToCreateNewPostInChannel(channel, author);
        }
        log.debug("      createMessage:additional authorization DONE");
        Set<User> mentionedUsers = parseUserMentions(course, newMessage.getContent());
        log.debug("      createMessage:parseUserMentions DONE");

        // update last message date of conversation
        conversation.setCourse(course);
        conversationService.updateLastMessageDate(conversation);

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

        return new CreatedConversationMessage(createdMessage, conversation, mentionedUsers);
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
        preparePostForBroadcast(createdMessage);
        PostDTO postDTO = new PostDTO(createdMessage, MetisCrudAction.CREATE);
        createdMessage.getConversation().hideDetails();
        if (createdConversationMessage.completeConversation() instanceof Channel channel && channel.getIsCourseWide()) {
            // We don't need the list of participants for course-wide channels. We can delay the db query and send the WS messages first
            if (conversationService.isChannelVisibleToStudents(channel)) {
                broadcastForPost(postDTO, course.getId(), null);
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

            broadcastForPost(postDTO, course.getId(), recipientSummaries);

            log.debug("      broadcastForPost DONE");
        }

        var post = createdConversationMessage.messageWithHiddenDetails();
        var author = post.getAuthor();

        String channelType = switch (conversation) {
            case OneToOneChat ignored -> "oneToOneChat";
            case GroupChat ignored -> "groupChat";
            default -> "channel";
        };

        var mentionedUserRecipients = singleUserNotificationService.filterAllowedRecipientsInMentionedUsers(createdConversationMessage.mentionedUsers(), conversation)
                .filter((mentionedUser) -> !Objects.equals(mentionedUser.getId(), author.getId())).toList();

        if (conversation instanceof Channel channel && channel.getIsAnnouncementChannel()) {
            var newAnnouncementNotification = new NewAnnouncementNotification(course.getId(), course.getTitle(), course.getCourseIcon(), post.getId(), post.getTitle(),
                    post.getContent(), author.getName(), author.getImageUrl(), author.getId(), conversation.getId());

            // Announcements are always sent, even for hidden/muted channels
            courseNotificationService.sendCourseNotification(newAnnouncementNotification,
                    recipientSummaries.stream().filter((summary) -> summary.userId() != author.getId()).map((summary) -> {
                        var user = new User(summary.userId());
                        user.setLogin(summary.userLogin());
                        user.setEmail(summary.userEmail());
                        user.setFirstName(summary.firstName());
                        user.setLastName(summary.lastName());
                        user.setLangKey(summary.userLangKey());
                        return user;
                    }).toList());
        }
        else {
            var newPostNotification = new NewPostNotification(course.getId(), course.getTitle(), course.getCourseIcon(), post.getId(), post.getContent(), conversation.getId(),
                    conversation.getHumanReadableNameForReceiver(post.getAuthor()), channelType, author.getName(), author.getImageUrl(), author.getId());

            var isChannelVisibleForStudents = (conversation instanceof Channel channel) && conversationService.isChannelVisibleToStudents(channel);

            // We only send notifications to users that are not the author, that are part of the conversation, that have the role rights to see it,
            // that did not mute or hide it and if they were not mentioned (since they get a separate notification for that)
            courseNotificationService.sendCourseNotification(newPostNotification,
                    recipientSummaries.stream()
                            .filter((summary) -> summary.userId() != author.getId() && !summary.isConversationHidden() && !summary.isConversationMuted()
                                    && (isChannelVisibleForStudents || summary.isAtLeastTutorInCourse())
                                    && mentionedUserRecipients.stream().noneMatch((mentionedUser) -> summary.userId() == mentionedUser.getId()))
                            .map((summary) -> {
                                var user = new User(summary.userId());
                                user.setLogin(summary.userLogin());
                                return user;
                            }).toList());
        }

        var mentionCourseNotification = new NewMentionNotification(course.getId(), conversation.getCourse().getTitle(), conversation.getCourse().getCourseIcon(), post.getContent(),
                post.getCreationDate().toString(), post.getAuthor().getName(), post.getId(), null, null, post.getAuthor().getName(), post.getAuthor().getId(),
                post.getAuthor().getImageUrl(), null, conversation.getHumanReadableNameForReceiver(post.getAuthor()), conversation.getId());

        this.courseNotificationService.sendCourseNotification(mentionCourseNotification, mentionedUserRecipients);

        conversationParticipantRepository.incrementUnreadMessagesCountOfParticipants(conversation.getId(), author.getId());
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
     * fetch posts from database by conversationId
     *
     * @param pageable          requested page and page size
     * @param postContextFilter request object to fetch posts
     * @param requestingUser    the user requesting messages in course-wide channels
     * @param courseId          the id of the course the post belongs to
     * @return page of posts that match the given context
     */
    public Page<Post> getMessages(Pageable pageable, @Valid PostContextFilterDTO postContextFilter, User requestingUser, Long courseId) {
        List<Long> conversationIds = Arrays.stream(postContextFilter.conversationIds()).boxed().collect(Collectors.toCollection(ArrayList::new));
        conversationParticipantRepository.userHasAccessToAllConversationsElseThrow(conversationIds, requestingUser.getId(), courseId);

        Page<Post> conversationPosts = findMessages(postContextFilter, pageable, requestingUser.getId());
        setAuthorRoleOfPostings(conversationPosts.getContent(), courseId);

        // This check is needed to avoid resetting the unread count when searching
        if (postContextFilter.searchText() == null && postContextFilter.conversationIds().length == 1) {
            Long conversationId = conversationIds.getFirst();
            var participantSet = conversationParticipantRepository.findConversationParticipantsByConversationIdAndUserIds(conversationId, Set.of(requestingUser.getId()));

            // If there is no entry yet (e.g. for course-wide channels in which the user did not write a post yet,
            // we create the entry to be able to track the unread count)
            if (participantSet.isEmpty()) {
                var participant = ConversationParticipant.createWithDefaultValues(requestingUser, conversationService.getConversationById(conversationId));
                participant.setLastRead(ZonedDateTime.now());
                // We surround this with a try/catch to avoid errors in case there are multiple requests at the exact
                // same time and the select query on top was not aware of that yet. Therefore, we simply continue.
                try {
                    conversationParticipantRepository.save(participant);
                }
                catch (DataIntegrityViolationException e) {
                    // Continue
                }
            }
            else {
                // invoke async due to db write access to avoid that the client has to wait
                conversationParticipantRepository.updateLastReadAsync(requestingUser.getId(), conversationId, ZonedDateTime.now());
            }
        }

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
    public Post updateMessage(Long courseId, Long postId, UpdatePostingDTO messagePost) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();
        // check
        if (!Objects.equals(messagePost.id(), postId)) {
            throw new BadRequestAlertException("Invalid id", METIS_POST_ENTITY_NAME, "idnull");
        }

        Post existingMessage = conversationMessageRepository.findMessagePostByIdElseThrow(postId);
        Conversation conversation = mayUpdateOrDeleteMessageElseThrow(existingMessage, user);
        var course = preCheckUserAndCourseForMessaging(user, courseId);

        parseUserMentions(course, messagePost.content());

        // update: allow overwriting of values only for depicted fields
        existingMessage.setContent(messagePost.content());
        existingMessage.setTitle(messagePost.title());
        existingMessage.setUpdatedDate(ZonedDateTime.now());

        Post updatedPost = conversationMessageRepository.save(existingMessage);
        updatedPost.setConversation(conversation);

        // emit a post update via websocket
        preparePostForBroadcast(updatedPost);
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

        // Delete all connected saved posts
        var savedPosts = savedPostRepository.findSavedPostByPostIdAndPostType(postId, PostingType.POST);
        savedPostRepository.deleteAll(savedPosts);

        conversationService.notifyAllConversationMembersAboutUpdate(conversation);
        preparePostForBroadcast(post);
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
        final Course course = courseRepository.findByIdElseThrow(courseId);
        preCheckUserAndCourseForCommunicationOrMessaging(user, course);

        Post message = conversationMessageRepository.findMessagePostByIdElseThrow(postId);

        Conversation conversation = conversationService.isMemberOrCreateForCourseWideElseThrow(message.getConversation().getId(), user, Optional.empty())
                .orElse(message.getConversation());

        if (conversation instanceof Channel && !channelAuthorizationService.hasChannelModerationRights(conversation.getId(), user)
                || conversation instanceof GroupChat && !user.getId().equals(conversation.getCreator().getId())) {
            throw new AccessForbiddenException("You are not allowed to change the display priority of messages in this conversation");
        }

        message.setDisplayPriority(displayPriority);

        Post updatedMessage = conversationMessageRepository.save(message);
        message.getConversation().hideDetails();
        preparePostForBroadcast(message);
        preparePostForBroadcast(updatedMessage);
        broadcastForPost(new PostDTO(message, MetisCrudAction.UPDATE), course.getId(), null);
        return updatedMessage;
    }

    public List<Post> getMessageByIds(List<Long> sourcePostIds) {
        if (sourcePostIds == null || sourcePostIds.isEmpty()) {
            throw new BadRequestAlertException("Source post IDs cannot be null or empty", METIS_POST_ENTITY_NAME, "sourcepostidsinvalid");
        }
        return postRepository.findByIdIn(sourcePostIds);
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
     * Configures the search specifications based on the provided filter criteria.
     *
     * @param specification     The existing specification to be configured.
     * @param postContextFilter Filtering and sorting properties for post objects.
     * @param userId            The id of the user for which the messages should be returned.
     * @return A Specification object configured with search criteria.
     */
    private Specification<Post> configureSearchSpecification(Specification<Post> specification, PostContextFilterDTO postContextFilter, long userId) {
        return specification
        // @formatter:off
            .and(getSearchTextAndAuthorSpecification(postContextFilter.searchText(), postContextFilter.authorIds()))
            .and(getCourseWideChannelsSpecification(Boolean.TRUE.equals(postContextFilter.filterToCourseWide()), postContextFilter.courseId()))
            .and(getAnsweredOrReactedSpecification(Boolean.TRUE.equals(postContextFilter.filterToAnsweredOrReacted()), userId))
            .and(getUnresolvedSpecification(Boolean.TRUE.equals(postContextFilter.filterToUnresolved())))
            .and(getPinnedSpecification(Boolean.TRUE.equals(postContextFilter.pinnedOnly())))
            .and(getSortSpecification(true, postContextFilter.postSortCriterion(), postContextFilter.sortingOrder()));
        // @formatter:on
    }

    /**
     * Generates SQL Query via specifications to find and sort Messages
     *
     * @param postContextFilter filtering and sorting properties for post objects
     * @param pageable          paging object which contains the page number and number of records to fetch
     * @param userId            the id of the user for which the messages should be returned
     * @return returns a Page of Messages
     */
    public Page<Post> findMessages(PostContextFilterDTO postContextFilter, Pageable pageable, long userId) {
        var specification = getConversationsSpecification(postContextFilter.conversationIds());
        specification = configureSearchSpecification(specification, postContextFilter, userId);
        // Fetch all necessary attributes to avoid lazy loading (even though relations are defined as EAGER in the domain class, specification queries do not respect this)
        return findPostsWithSpecification(pageable, specification);
    }

    private PageImpl<Post> findPostsWithSpecification(Pageable pageable, Specification<Post> specification) {
        // Only fetch the postIds without any left joins to avoid that Hibernate loads all objects and creates the page in Java
        long start = System.nanoTime();
        Page<Long> postIds = conversationMessageRepository.findPostIdsWithSpecification(specification, pageable);
        log.debug("findPostIdsWithSpecification took {}", TimeLogUtil.formatDurationFrom(start));
        // Fetch all necessary attributes to avoid lazy loading (even though relations are defined as EAGER in the domain class, specification queries do not respect this)
        long start2 = System.nanoTime();

        // Step 1: Fetch only the post IDs for the page
        List<Long> ids = postIds.getContent();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), postIds.getPageable(), postIds.getTotalElements());
        }
        // Step 2: Fetch the base posts (with author & conversation)
        List<Post> posts = conversationMessageRepository.findByIdIn(ids);
        // Step 3: Fetch answer posts for those posts
        List<AnswerPost> allAnswers = answerPostRepository.findAnswerPostsByPostIdIn(ids);
        Map<Long, Set<AnswerPost>> answersByPostId = allAnswers.stream().collect(Collectors.groupingBy(a -> a.getPost().getId(), Collectors.toSet()));
        // Step 4: Fetch reactions for those posts
        List<Reaction> reactions = reactionRepository.findByPostIdIn(ids);
        Map<Long, Set<Reaction>> reactionsByPostId = reactions.stream().collect(Collectors.groupingBy(r -> r.getPost().getId(), Collectors.toSet()));
        // Step 5: Fetch tags for those posts
        List<Object[]> tagsRaw = conversationMessageRepository.findTagsByPostIds(ids);
        Map<Long, Set<String>> tagsByPostId = tagsRaw.stream().collect(Collectors.groupingBy(row -> (Long) row[0], Collectors.mapping(row -> (String) row[1], Collectors.toSet())));
        // Step 6: Attach everything to the posts
        for (Post post : posts) {
            post.setAnswers(answersByPostId.getOrDefault(post.getId(), Set.of()));
            post.setReactions(reactionsByPostId.getOrDefault(post.getId(), Set.of()));
            post.setTags(tagsByPostId.getOrDefault(post.getId(), Set.of()));
        }
        // Make sure to sort the posts in the same order as the postIds
        Map<Long, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, post -> post));
        posts = postIds.stream().map(postMap::get).toList();
        log.debug("findByPostIdsWithEagerRelationships took {}", TimeLogUtil.formatDurationFrom(start2));
        // Recreate the page with the fetched posts
        return new PageImpl<>(posts, postIds.getPageable(), postIds.getTotalElements());
    }

}
