package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.PostingType;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewAnswerNotification;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewMentionNotification;
import de.tum.cit.aet.artemis.communication.dto.CreateAnswerPostDTO;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.dto.UpdatePostingDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.repository.PostRepository;
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
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class AnswerMessageService extends PostingService {

    private static final String METIS_ANSWER_POST_ENTITY_NAME = "metis.answerPost";

    private final AnswerPostRepository answerPostRepository;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ConversationService conversationService;

    private final ChannelAuthorizationService channelAuthorizationService;

    private final SingleUserNotificationService singleUserNotificationService;

    private final CourseNotificationService courseNotificationService;

    private final PostRepository postRepository;

    @SuppressWarnings("PMD.ExcessiveParameterList")
    public AnswerMessageService(SingleUserNotificationService singleUserNotificationService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            UserRepository userRepository, AnswerPostRepository answerPostRepository, ConversationMessageRepository conversationMessageRepository,
            ConversationService conversationService, ExerciseRepository exerciseRepository, SavedPostRepository savedPostRepository,
            WebsocketMessagingService websocketMessagingService, ConversationParticipantRepository conversationParticipantRepository,
            ChannelAuthorizationService channelAuthorizationService, PostRepository postRepository, CourseNotificationService courseNotificationService) {
        super(courseRepository, userRepository, exerciseRepository, authorizationCheckService, websocketMessagingService, conversationParticipantRepository, savedPostRepository);
        this.answerPostRepository = answerPostRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationService = conversationService;
        this.channelAuthorizationService = channelAuthorizationService;
        this.singleUserNotificationService = singleUserNotificationService;
        this.postRepository = postRepository;
        this.courseNotificationService = courseNotificationService;
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
    public AnswerPost createAnswerMessage(Long courseId, CreateAnswerPostDTO answerMessage) {
        final User author = this.userRepository.getUserWithGroupsAndAuthorities();

        var newAnswerMessage = new AnswerPost();
        newAnswerMessage.setContent(answerMessage.content());

        Post post = conversationMessageRepository.findMessagePostByIdElseThrow(answerMessage.post().id());
        var conversationId = post.getConversation().getId();
        // For group chats we need the participants to generate the conversation title
        var conversation = conversationService.isMemberOrCreateForCourseWideElseThrow(conversationId, author, Optional.empty())
                .orElse(conversationService.loadConversationWithParticipantsIfGroupChat(conversationId));

        var course = preCheckUserAndCourseForMessaging(author, courseId);

        if (conversation instanceof Channel channel) {
            channelAuthorizationService.isAllowedToCreateNewAnswerPostInChannel(channel, author);
        }

        Set<User> mentionedUsers = parseUserMentions(course, answerMessage.content());

        // use post from database rather than user input
        newAnswerMessage.setPost(post);
        // set author to current user
        newAnswerMessage.setAuthor(author);
        // on creation of an answer message, we set the resolves_post field to false per default since this feature is not used for messages
        newAnswerMessage.setResolvesPost(false);
        AnswerPost savedAnswerMessage = answerPostRepository.save(newAnswerMessage);
        savedAnswerMessage.getPost().setConversation(conversation);
        setAuthorRoleForPosting(savedAnswerMessage, course);

        var newAnswerNotification = new NewAnswerNotification(courseId, conversation.getCourse().getTitle(), conversation.getCourse().getCourseIcon(), post.getContent(),
                post.getCreationDate().toString(), post.getAuthor().getName(), post.getId(), newAnswerMessage.getContent(), newAnswerMessage.getCreationDate().toString(),
                newAnswerMessage.getAuthor().getName(), newAnswerMessage.getAuthor().getId(), newAnswerMessage.getAuthor().getImageUrl(), newAnswerMessage.getId(),
                conversation.getHumanReadableNameForReceiver(newAnswerMessage.getAuthor()), conversationId);

        var usersInvolved = conversationMessageRepository.findUsersWhoRepliedInMessage(post.getId());
        usersInvolved.add(post.getAuthor());

        var notificationRecipientsList = getNotificationRecipients(conversation).toList();

        var mentionedUserRecipients = singleUserNotificationService.filterAllowedRecipientsInMentionedUsers(mentionedUsers, conversation)
                .filter((mentionedUser) -> !Objects.equals(mentionedUser.getId(), newAnswerMessage.getAuthor().getId())).toList();

        // We only send notifications to users that are part of the conversation, did not mute or hide it and if they were not mentioned (since they get a separate notification
        // for that)
        var filteredUsersInvolved = usersInvolved.stream()
                .filter(user -> notificationRecipientsList.stream()
                        .anyMatch(recipient -> recipient.userId() == user.getId() && recipient.userId() != newAnswerMessage.getAuthor().getId() && !recipient.isConversationHidden()
                                && !recipient.isConversationMuted() && mentionedUserRecipients.stream().noneMatch((mentionedUser) -> recipient.userId() == mentionedUser.getId())))
                .collect(Collectors.toCollection(ArrayList::new));

        this.courseNotificationService.sendCourseNotification(newAnswerNotification, filteredUsersInvolved);

        var mentionCourseNotification = new NewMentionNotification(courseId, conversation.getCourse().getTitle(), conversation.getCourse().getCourseIcon(),
                newAnswerMessage.getContent(), post.getCreationDate().toString(), post.getAuthor().getName(), post.getId(), newAnswerMessage.getContent(),
                newAnswerMessage.getCreationDate().toString(), newAnswerMessage.getAuthor().getName(), newAnswerMessage.getAuthor().getId(),
                newAnswerMessage.getAuthor().getImageUrl(), newAnswerMessage.getId(), conversation.getHumanReadableNameForReceiver(newAnswerMessage.getAuthor()), conversationId);

        this.courseNotificationService.sendCourseNotification(mentionCourseNotification, mentionedUserRecipients);

        this.preparePostAndBroadcast(savedAnswerMessage, course);
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
    public AnswerPost updateAnswerMessage(Long courseId, Long answerMessageId, UpdatePostingDTO answerMessage) {
        final User user = userRepository.getUserWithGroupsAndAuthorities();

        // checks
        if (!Objects.equals(answerMessage.id(), answerMessageId)) {
            throw new BadRequestAlertException("Invalid id", METIS_ANSWER_POST_ENTITY_NAME, "idnull");
        }
        AnswerPost existingAnswerMessage = this.findById(answerMessageId);

        AnswerPost updatedAnswerMessage;

        Conversation conversation = conversationService.getConversationById(existingAnswerMessage.getPost().getConversation().getId());
        var course = preCheckUserAndCourseForMessaging(user, courseId);
        parseUserMentions(course, answerMessage.content());
        // only the content of the message can be updated
        existingAnswerMessage.setContent(answerMessage.content());

        // determine if the update operation is to mark the answer message as resolving the original post
        if (existingAnswerMessage.doesResolvePost() != answerMessage.resolvesPost()) {
            // check if requesting user is allowed to mark this answer message as resolving, i.e. if user is author or original message or at least tutor
            mayMarkAnswerMessageAsResolvingElseThrow(existingAnswerMessage, user, course);
            existingAnswerMessage.setResolvesPost(answerMessage.resolvesPost());
            // sets the message as resolved if there exists any resolving answer
            existingAnswerMessage.getPost().setResolved(existingAnswerMessage.getPost().getAnswers().stream().anyMatch(AnswerPost::doesResolvePost));
            postRepository.save(existingAnswerMessage.getPost());
        }
        else {
            // check if requesting user is allowed to update the content, i.e. if user is author of answer message or at least tutor
            mayUpdateOrDeleteAnswerMessageElseThrow(existingAnswerMessage, user);
            existingAnswerMessage.setContent(answerMessage.content());
            existingAnswerMessage.setUpdatedDate(ZonedDateTime.now());
        }

        updatedAnswerMessage = answerPostRepository.save(existingAnswerMessage);
        updatedAnswerMessage.getPost().setConversation(conversation);

        this.preparePostAndBroadcast(updatedAnswerMessage, course);
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
        preparePostForBroadcast(updatedMessage);

        // Delete all connected saved posts
        var savedPosts = savedPostRepository.findSavedPostByPostIdAndPostType(answerMessageId, PostingType.ANSWER);
        savedPostRepository.deleteAll(savedPosts);

        broadcastForPost(new PostDTO(updatedMessage, MetisCrudAction.UPDATE), course.getId(), null);
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

    public List<AnswerPost> findByIdIn(List<Long> answerMessageIds) {
        return answerPostRepository.findByIdIn(answerMessageIds);
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
