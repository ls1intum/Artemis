package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
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
import de.tum.cit.aet.artemis.communication.domain.course_notifications.IrisResponseNeedsReviewNotification;
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
 *
 * <p>
 * Artemis gates Iris responses solely on the confidence score supplied by Pyris:
 * <ul>
 * <li>confidence &ge; {@link #AUTO_VERIFY_CONFIDENCE_THRESHOLD}: the answer is published
 * immediately (verified = true) and broadcast to every participant.</li>
 * <li>{@link #REVIEW_MIN_CONFIDENCE_THRESHOLD} &le; confidence &lt;
 * {@link #AUTO_VERIFY_CONFIDENCE_THRESHOLD}: the answer is stored as unverified.
 * Only tutors/editors/instructors see it (via REST and WebSocket) and receive a
 * review notification; students only see it after a tutor approves it.</li>
 * <li>confidence &lt; {@link #REVIEW_MIN_CONFIDENCE_THRESHOLD} or missing: the response
 * is discarded.</li>
 * </ul>
 */
@Service
@Lazy
@Conditional(IrisEnabled.class)
public class AutonomousTutorService {

    private static final Logger log = LoggerFactory.getLogger(AutonomousTutorService.class);

    private static final String METIS_WEBSOCKET_CHANNEL_PREFIX = "/topic/metis/";

    /** Iris replies at or above this confidence are auto-verified and visible to students. */
    public static final double AUTO_VERIFY_CONFIDENCE_THRESHOLD = 0.95;

    /** Iris replies below this confidence are never posted (a tutor cannot rescue something Pyris itself is very unsure about). */
    public static final double REVIEW_MIN_CONFIDENCE_THRESHOLD = 0.80;

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
     * Handles the status update from the autonomous tutor pipeline. Artemis decides whether to
     * publish, hold for review, or discard the response based solely on the confidence score:
     * {@code >= 0.95} auto-publishes, {@code [0.80, 0.95)} stores the reply as unverified for tutor
     * review, {@code < 0.80} or missing confidence is discarded.
     *
     * @param job          the autonomous tutor job containing post and course IDs
     * @param statusUpdate the status update from Pyris containing the generated response
     */
    public void handleStatusUpdate(AutonomousTutorJob job, PyrisAutonomousTutorPipelineStatusUpdateDTO statusUpdate) {
        if (!featureToggleService.isFeatureEnabled(Feature.AutonomousTutor)) {
            log.debug("AutonomousTutor feature is disabled, skipping status update for job {}", job.jobId());
            return;
        }
        if (statusUpdate.result() == null) {
            log.debug("Skipping autonomous tutor post for job {}: no result in status update", job.jobId());
            return;
        }
        Double confidence = statusUpdate.confidence();
        if (confidence == null) {
            log.warn("Discarding autonomous tutor response for job {}: no confidence score supplied by Pyris", job.jobId());
            return;
        }
        if (confidence < REVIEW_MIN_CONFIDENCE_THRESHOLD) {
            log.debug("Discarding autonomous tutor response for job {}: confidence {} below review threshold {}", job.jobId(), confidence, REVIEW_MIN_CONFIDENCE_THRESHOLD);
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

        boolean isVerified = confidence >= AUTO_VERIFY_CONFIDENCE_THRESHOLD;

        AnswerPost answerPost = createAndSaveAnswerPost(statusUpdate.result(), botUser, originalPost, confidence, isVerified);
        Set<ConversationNotificationRecipientSummary> recipientSummaries = getNotificationRecipients(conversation, course);

        if (isVerified) {
            sendNewAnswerNotifications(answerPost, originalPost, conversation, course, botUser, recipientSummaries);
            broadcastAnswer(answerPost, originalPost, conversation, course.getId(), recipientSummaries, true);
        }
        else {
            sendReviewNotifications(answerPost, originalPost, conversation, course, recipientSummaries);
            broadcastAnswer(answerPost, originalPost, conversation, course.getId(), recipientSummaries, false);
        }

        log.info("Autonomous tutor posted answer {} (verified={}, confidence={}) to post {} in course {}", answerPost.getId(), isVerified, confidence, job.postId(),
                job.courseId());
    }

    private void ensureBotIsParticipant(User botUser, Conversation conversation) {
        var existingParticipant = conversationParticipantRepository.findConversationParticipantByConversationIdAndUserId(conversation.getId(), botUser.getId());
        if (existingParticipant.isEmpty()) {
            var participant = ConversationParticipant.createWithDefaultValues(botUser, conversation);
            conversationParticipantRepository.save(participant);
            log.debug("Added Iris bot as participant to conversation {}", conversation.getId());
        }
    }

    private AnswerPost createAndSaveAnswerPost(String content, User botUser, Post originalPost, Double confidence, boolean isVerified) {
        AnswerPost answerPost = new AnswerPost();
        answerPost.setContent(content);
        answerPost.setAuthor(botUser);
        answerPost.setPost(originalPost);
        answerPost.setResolvesPost(false);
        answerPost.setConfidenceScore(confidence);
        answerPost.setVerified(isVerified);
        if (isVerified) {
            // auto-verified answers are implicitly approved by the system, there is no human reviewer
            answerPost.setVerifiedAt(ZonedDateTime.now());
        }
        AnswerPost savedAnswer = answerPostRepository.save(answerPost);
        savedAnswer.setAuthorRole(UserRole.USER);
        savedAnswer.setIsSaved(false);
        return savedAnswer;
    }

    private void sendNewAnswerNotifications(AnswerPost answerPost, Post originalPost, Conversation conversation, Course course, User botUser,
            Set<ConversationNotificationRecipientSummary> recipientSummaries) {
        var usersInvolved = conversationMessageRepository.findUsersWhoRepliedInMessage(originalPost.getId());
        usersInvolved.add(originalPost.getAuthor());

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

    private void sendReviewNotifications(AnswerPost answerPost, Post originalPost, Conversation conversation, Course course,
            Set<ConversationNotificationRecipientSummary> recipientSummaries) {
        List<User> tutors = recipientSummaries.stream().filter(ConversationNotificationRecipientSummary::isAtLeastTutorInCourse).map(this::toUserReference)
                .collect(Collectors.toCollection(ArrayList::new));
        if (tutors.isEmpty()) {
            log.debug("No tutors available to review unverified Iris answer {} in course {}", answerPost.getId(), course.getId());
            return;
        }
        var notification = new IrisResponseNeedsReviewNotification(course.getId(), course.getTitle(), course.getCourseIcon(), originalPost.getContent(),
                originalPost.getCreationDate().toString(), originalPost.getAuthor().getName(), originalPost.getId(), answerPost.getContent(),
                answerPost.getCreationDate().toString(), answerPost.getId(), answerPost.getConfidenceScore(), conversation.getHumanReadableNameForReceiver(answerPost.getAuthor()),
                conversation.getId());
        courseNotificationService.sendCourseNotification(notification, tutors);
    }

    private User toUserReference(ConversationNotificationRecipientSummary summary) {
        // CourseNotificationService only needs the user id for delivery; a light reference avoids extra DB reads
        User user = new User();
        user.setId(summary.userId());
        user.setLogin(summary.userLogin());
        user.setFirstName(summary.firstName());
        user.setLastName(summary.lastName());
        user.setLangKey(summary.userLangKey());
        user.setEmail(summary.userEmail());
        return user;
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

    private void broadcastAnswer(AnswerPost answerPost, Post originalPost, Conversation conversation, Long courseId,
            Set<ConversationNotificationRecipientSummary> recipientSummaries, boolean broadcastToStudents) {
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

        if (broadcastToStudents && conversation instanceof Channel channel && channel.getIsCourseWide()) {
            String courseConversationTopic = METIS_WEBSOCKET_CHANNEL_PREFIX + "courses/" + courseId;
            websocketMessagingService.sendMessage(courseConversationTopic, postDTO);
            return;
        }

        // For private channels OR unverified Iris replies: send per-user, optionally skipping students
        recipientSummaries.stream().filter(recipient -> broadcastToStudents || recipient.isAtLeastTutorInCourse())
                .forEach(recipient -> websocketMessagingService.sendMessage("/topic/user/" + recipient.userId() + "/notifications/conversations", postDTO));
    }
}
