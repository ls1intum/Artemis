package de.tum.cit.aet.artemis.iris.service;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.ConversationParticipant;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.UserRole;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.communication.domain.course_notifications.NewAnswerNotification;
import de.tum.cit.aet.artemis.communication.dto.MetisCrudAction;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.ConversationParticipantRepository;
import de.tum.cit.aet.artemis.communication.service.CourseNotificationService;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.autonomoustutor.PyrisAutonomousTutorPipelineStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.AutonomousTutorJob;

/**
 * Service that handles the autonomous tutor pipeline status updates.
 * When Pyris sends a response, this service creates an answer post as the Iris bot user,
 * broadcasts it via WebSocket, and sends notifications to thread participants.
 */
@Service
@Lazy
@Conditional(IrisEnabled.class)
public class AutonomousTutorService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousTutorService.class);

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    private final IrisBotUserService irisBotUserService;

    private final ConversationMessageRepository conversationMessageRepository;

    private final AnswerPostRepository answerPostRepository;

    private final ConversationParticipantRepository conversationParticipantRepository;

    private final FeatureToggleService featureToggleService;

    private final WebsocketMessagingService websocketMessagingService;

    private final CourseNotificationService courseNotificationService;

    private final UserRepository userRepository;

    public AutonomousTutorService(IrisBotUserService irisBotUserService, ConversationMessageRepository conversationMessageRepository, AnswerPostRepository answerPostRepository,
            ConversationParticipantRepository conversationParticipantRepository, FeatureToggleService featureToggleService, WebsocketMessagingService websocketMessagingService,
            CourseNotificationService courseNotificationService, UserRepository userRepository) {
        this.irisBotUserService = irisBotUserService;
        this.conversationMessageRepository = conversationMessageRepository;
        this.answerPostRepository = answerPostRepository;
        this.conversationParticipantRepository = conversationParticipantRepository;
        this.featureToggleService = featureToggleService;
        this.websocketMessagingService = websocketMessagingService;
        this.courseNotificationService = courseNotificationService;
        this.userRepository = userRepository;
    }

    /**
     * Handles the status update from the autonomous tutor pipeline.
     * If the result is available and should be posted directly, creates an answer post
     * as the Iris bot user, sends WebSocket broadcasts, and notifies thread participants.
     *
     * @param job          the autonomous tutor job containing post and course IDs
     * @param statusUpdate the status update from Pyris containing the generated response
     */
    public void handleStatusUpdate(AutonomousTutorJob job, PyrisAutonomousTutorPipelineStatusUpdateDTO statusUpdate) {
        if (!featureToggleService.isFeatureEnabled(Feature.AutonomousTutor)) {
            log.debug("AutonomousTutor feature is disabled, skipping status update for job {}", job.jobId());
            return;
        }
        if (statusUpdate.result() == null || !statusUpdate.shouldPostDirectly()) {
            log.debug("Skipping autonomous tutor post: result={}, shouldPostDirectly={}", statusUpdate.result() != null ? "present" : "null", statusUpdate.shouldPostDirectly());
            return;
        }

        Post originalPost = conversationMessageRepository.findMessagePostByIdElseThrow(job.postId());
        User botUser = irisBotUserService.getIrisBotUser();
        Conversation conversation = originalPost.getConversation();
        Course course = conversation.getCourse();
        if (!Objects.equals(course.getId(), job.courseId())) {
            log.warn("Skipping autonomous tutor job {} because post {} belongs to course {}, not {}", job.jobId(), job.postId(), course.getId(), job.courseId());
            return;
        }

        ensureBotIsParticipant(botUser, conversation);

        AnswerPost answerPost = createAndSaveAnswerPost(statusUpdate.result(), botUser, originalPost);
        sendNotifications(answerPost, originalPost, conversation, course, botUser);
        broadcastAnswer(answerPost, originalPost, conversation, course.getId());

        log.info("Autonomous tutor posted answer {} to post {} in course {}", answerPost.getId(), job.postId(), job.courseId());
    }

    private void ensureBotIsParticipant(User botUser, Conversation conversation) {
        var existingParticipant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversation.getId(), botUser.getId());
        if (existingParticipant.isEmpty()) {
            var participant = ConversationParticipant.createWithDefaultValues(botUser, conversation);
            conversationParticipantRepository.save(participant);
            log.debug("Added Iris bot as participant to conversation {}", conversation.getId());
        }
    }

    private AnswerPost createAndSaveAnswerPost(String content, User botUser, Post originalPost) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent(content);
        answerPost.setAuthor(botUser);
        answerPost.setPost(originalPost);
        answerPost.setResolvesPost(false);
        AnswerPost savedAnswer = answerPostRepository.save(answerPost);
        savedAnswer.setAuthorRole(UserRole.USER);
        savedAnswer.setIsSaved(false);
        return savedAnswer;
    }

    private void sendNotifications(AnswerPost answerPost, Post originalPost, Conversation conversation, Course course, User botUser) {
        var usersInvolved = conversationMessageRepository.findUsersWhoRepliedInMessage(originalPost.getId());
        usersInvolved.add(originalPost.getAuthor());

        Set<ConversationNotificationRecipientSummary> recipientSummaries = getNotificationRecipients(conversation, course);

        // Filter to users who replied or authored the post, are conversation participants, not muted, not hidden, and not the bot
        var filteredUsers = usersInvolved.stream().filter(user -> recipientSummaries.stream().anyMatch(recipient -> recipient.userId() == user.getId()
                && !Objects.equals(user.getId(), botUser.getId()) && !recipient.isConversationHidden() && !recipient.isConversationMuted()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (!filteredUsers.isEmpty()) {
            var notification = new NewAnswerNotification(course.getId(), course.getTitle(), course.getCourseIcon(), originalPost.getContent(),
                    originalPost.getCreationDate().toString(), originalPost.getAuthor().getName(), originalPost.getId(), answerPost.getContent(),
                    answerPost.getCreationDate().toString(), answerPost.getAuthor().getName(), answerPost.getAuthor().getId(), answerPost.getAuthor().getImageUrl(),
                    answerPost.getId(), conversation.getHumanReadableNameForReceiver(answerPost.getAuthor()), conversation.getId(), answerPost.getAuthor().isBot());
            courseNotificationService.sendCourseNotification(notification, filteredUsers);
        }
    }

    private Set<ConversationNotificationRecipientSummary> getNotificationRecipients(Conversation conversation, Course course) {
        if (conversation instanceof Channel channel && channel.getIsCourseWide()) {
            return userRepository.findAllNotificationRecipientsInCourseForConversation(conversation.getId(), course.getStudentGroupName(), course.getTeachingAssistantGroupName(),
                    course.getEditorGroupName(), course.getInstructorGroupName());
        }
        return conversationParticipantRepository.findConversationParticipantsByConversationId(conversation.getId()).stream()
                .map(participant -> new ConversationNotificationRecipientSummary(participant.getUser(), participant.getIsMuted(),
                        participant.getIsHidden() != null && participant.getIsHidden(), false))
                .collect(Collectors.toSet());
    }

    private void broadcastAnswer(AnswerPost answerPost, Post originalPost, Conversation conversation, Long courseId) {
        // Assemble the parent post with the new answer
        Post broadcastPost = originalPost;
        broadcastPost.removeAnswerPost(answerPost);
        broadcastPost.addAnswerPost(answerPost);
        broadcastPost.setIsSaved(false);
        broadcastPost.getAnswers().forEach(answer -> answer.setIsSaved(false));

        // Reduce payload for websocket
        conversation.hideDetails();
        broadcastPost.getAnswers().forEach(answer -> answer.setPost(new Post(answer.getPost().getId())));

        PostDTO postDTO = new PostDTO(broadcastPost, MetisCrudAction.UPDATE);
        String courseConversationTopic = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + courseId;

        if (conversation instanceof Channel channel && channel.getIsCourseWide()) {
            websocketMessagingService.sendMessage(courseConversationTopic, postDTO);
        }
        else {
            var participants = conversationParticipantRepository.findConversationParticipantsByConversationId(conversation.getId());
            participants.forEach(participant -> websocketMessagingService.sendMessage("/topic/user/" + participant.getUser().getId() + "/notifications/conversations", postDTO));
        }
    }
}
