package de.tum.cit.aet.artemis.service.notifications;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_NEW_REPLY_MESSAGE;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.CONVERSATION_USER_MENTIONED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.DATA_EXPORT_CREATED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.DATA_EXPORT_FAILED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.EXERCISE_SUBMISSION_ASSESSED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.FILE_SUBMISSION_SUCCESSFUL;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_CPC_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_PLAGIARISM_CASE_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_COURSE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_EXAM_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_EXERCISE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.NEW_REPLY_FOR_LECTURE_POST;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.PLAGIARISM_CASE_VERDICT_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_ASSIGNED;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_DEREGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_REGISTRATION_STUDENT;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_REGISTRATION_TUTOR;
import static de.tum.cit.aet.artemis.domain.enumeration.NotificationType.TUTORIAL_GROUP_UNASSIGNED;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_ADD_USER_CHANNEL_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_ADD_USER_GROUP_CHAT_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_CREATE_GROUP_CHAT_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_DELETE_CHANNEL_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_REMOVE_USER_CHANNEL_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.MENTIONED_IN_MESSAGE_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.MESSAGE_REPLY_IN_CONVERSATION_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_REPLY_FOR_COURSE_POST_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_REPLY_FOR_EXAM_POST_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_REPLY_FOR_EXERCISE_POST_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.NEW_REPLY_FOR_LECTURE_POST_TITLE;
import static de.tum.cit.aet.artemis.domain.notification.NotificationConstants.findCorrespondingNotificationTitleOrThrow;
import static de.tum.cit.aet.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static de.tum.cit.aet.artemis.service.notifications.NotificationSettingsCommunicationChannel.WEBAPP;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.SingleUserNotificationRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.domain.DataExport;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.domain.Result;
import de.tum.cit.aet.artemis.domain.Team;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.NotificationType;
import de.tum.cit.aet.artemis.domain.metis.AnswerPost;
import de.tum.cit.aet.artemis.domain.metis.Post;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.domain.metis.conversation.Conversation;
import de.tum.cit.aet.artemis.domain.notification.NotificationConstants;
import de.tum.cit.aet.artemis.domain.notification.SingleUserNotification;
import de.tum.cit.aet.artemis.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.cit.aet.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ExerciseDateService;
import de.tum.cit.aet.artemis.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.service.metis.conversation.ConversationService;

@Profile(PROFILE_CORE)
@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final UserRepository userRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final GeneralInstantNotificationService notificationService;

    private final NotificationSettingsService notificationSettingsService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ConversationService conversationService;

    private final AuthorizationCheckService authorizationCheckService;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, UserRepository userRepository,
            WebsocketMessagingService websocketMessagingService, GeneralInstantNotificationService notificationService, NotificationSettingsService notificationSettingsService,
            StudentParticipationRepository studentParticipationRepository, ConversationMessageRepository conversationMessageRepository, ConversationService conversationService,
            AuthorizationCheckService authorizationCheckService) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.userRepository = userRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.notificationService = notificationService;
        this.notificationSettingsService = notificationSettingsService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.conversationMessageRepository = conversationMessageRepository;
        this.conversationService = conversationService;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     *
     * @param notificationSubject     is the subject of the notification (e.g. exercise, attachment)
     * @param notificationType        is the discriminator for the factory
     * @param typeSpecificInformation is based on the current use case (e.g. POST -> course, Exercise -> user)
     */
    private void notifyRecipientWithNotificationType(Object notificationSubject, NotificationType notificationType, Object typeSpecificInformation, User author) {
        var singleUserNotification = createSingleUserNotification(notificationSubject, notificationType, (User) typeSpecificInformation, author);
        saveAndSend(singleUserNotification, notificationSubject, author, false);
    }

    private SingleUserNotification createSingleUserNotification(Object notificationSubject, NotificationType notificationType, User typeSpecificInformation, User author) {
        return switch (notificationType) {
            // Exercise related
            case EXERCISE_SUBMISSION_ASSESSED, FILE_SUBMISSION_SUCCESSFUL -> createNotification((Exercise) notificationSubject, notificationType, typeSpecificInformation);
            // Plagiarism related
            case NEW_PLAGIARISM_CASE_STUDENT, NEW_CPC_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT ->
                createNotification((PlagiarismCase) notificationSubject, notificationType, typeSpecificInformation, author);
            // Tutorial Group related
            case TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR,
                    TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_UNASSIGNED ->
                createNotification(((TutorialGroupNotificationSubject) notificationSubject).tutorialGroup, notificationType,
                        ((TutorialGroupNotificationSubject) notificationSubject).users, ((TutorialGroupNotificationSubject) notificationSubject).responsibleUser);
            // Conversation creation related
            case CONVERSATION_CREATE_ONE_TO_ONE_CHAT, CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT, CONVERSATION_ADD_USER_CHANNEL,
                    CONVERSATION_REMOVE_USER_GROUP_CHAT, CONVERSATION_REMOVE_USER_CHANNEL, CONVERSATION_DELETE_CHANNEL ->
                createNotification(((ConversationNotificationSubject) notificationSubject).conversation, notificationType,
                        ((ConversationNotificationSubject) notificationSubject).user, ((ConversationNotificationSubject) notificationSubject).responsibleUser);
            // Message reply related
            case NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST, NEW_REPLY_FOR_EXAM_POST, CONVERSATION_NEW_REPLY_MESSAGE,
                    CONVERSATION_USER_MENTIONED ->
                createNotification(((NewReplyNotificationSubject) notificationSubject).answerPost, notificationType, ((NewReplyNotificationSubject) notificationSubject).user,
                        ((NewReplyNotificationSubject) notificationSubject).responsibleUser);
            case DATA_EXPORT_CREATED, DATA_EXPORT_FAILED -> createNotification((DataExport) notificationSubject, notificationType, typeSpecificInformation);
            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
    }

    /**
     * Notify all users with available assessments about the finished assessment for an exercise submission.
     * This is an auxiliary method that finds all relevant users and initiates the process for sending SingleUserNotifications and emails
     *
     * @param exercise which assessmentDueDate is the trigger for the notification process
     */
    // TODO: Should by a general method and not be in the single user service
    public void notifyUsersAboutAssessedExerciseSubmission(Exercise exercise) {
        // This process can not be replaces via a GroupNotification (can only notify ALL students of the course)
        // because we want to notify only the students that have a valid assessed submission.

        // Find student participations with eager legal submissions and latest results that have a completion date
        Set<StudentParticipation> filteredStudentParticipations = Set
                .copyOf(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerLegalSubmissionsAndLatestResultWithCompletionDate(exercise.getId(), false));

        // Load and assign all studentParticipations with results (this information is needed for the emails later)
        exercise.setStudentParticipations(filteredStudentParticipations);

        // Extract all users that should be notified from the previously loaded student participations
        Set<User> relevantStudents = filteredStudentParticipations.stream().flatMap(participation -> {
            if (participation.getParticipant() instanceof Team team) {
                return team.getStudents().stream();
            }

            return Stream.of(participation.getStudent().orElseThrow());
        }).collect(Collectors.toSet());

        // notify all relevant users
        relevantStudents.forEach(student -> notifyUserAboutAssessedExerciseSubmission(exercise, student));
    }

    /**
     * Notify student about the finished assessment for an exercise submission.
     * Also creates and sends an email.
     * <p>
     * private because it is called by other methods that check e.g. if the time or results are correct
     *
     * @param exercise  that was assessed
     * @param recipient who should be notified
     */
    private void notifyUserAboutAssessedExerciseSubmission(Exercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, EXERCISE_SUBMISSION_ASSESSED, recipient, null);
    }

    /**
     * Checks if a new assessed-exercise-submission notification has to be created now
     *
     * @param exercise  which the submission is based on
     * @param recipient of the notification (i.e. the student)
     * @param result    containing information needed for the email
     */
    public void checkNotificationForAssessmentExerciseSubmission(Exercise exercise, User recipient, Result result) {
        // only send the notification now if no assessment due date was set or if it is in the past
        if (exercise.isCourseExercise() && ExerciseDateService.isAfterAssessmentDueDate(exercise)) {
            saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(exercise, recipient, result);
            notifyUserAboutAssessedExerciseSubmission(exercise, recipient);
        }
        // no scheduling needed because it is already part of updating/creating exercises
    }

    /**
     * Auxiliary method needed to create an email based on assessed exercises.
     * We saturate the wanted result information (e.g. score) in the exercise
     * This method is only called in those cases where no assessmentDueDate is set, i.e. individual/dynamic processes.
     *
     * @param exercise  that should contain information that is needed for emails
     * @param recipient who should be notified
     * @param result    that should be loaded as part of the exercise
     * @return the input exercise with information about a result
     */
    public Exercise saturateExerciseWithResultAndStudentParticipationForGivenUserForEmail(Exercise exercise, User recipient, Result result) {
        StudentParticipation studentParticipationForEmail = new StudentParticipation();
        studentParticipationForEmail.setResults(Set.of(result));
        studentParticipationForEmail.setParticipant(recipient);
        exercise.setStudentParticipations(Set.of(studentParticipationForEmail));
        return exercise;
    }

    /**
     * Notify student about successful submission of file upload exercise.
     * Also creates and sends an email.
     *
     * @param exercise  that was submitted
     * @param recipient that should be notified
     */
    public void notifyUserAboutSuccessfulFileUploadSubmission(FileUploadExercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, FILE_SUBMISSION_SUCCESSFUL, recipient, null);
    }

    /**
     * Notify user about the successful creation of a data export.
     *
     * @param dataExport the data export that was created
     */
    public void notifyUserAboutDataExportCreation(DataExport dataExport) {
        notifyRecipientWithNotificationType(dataExport, DATA_EXPORT_CREATED, dataExport.getUser(), null);
    }

    /**
     * Notify user about the failure of the creation of a data export.
     *
     * @param dataExport the data export that could not be created
     */
    public void notifyUserAboutDataExportFailure(DataExport dataExport) {
        notifyRecipientWithNotificationType(dataExport, DATA_EXPORT_FAILED, dataExport.getUser(), null);
    }

    /**
     * Notify student about possible plagiarism case.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutNewPlagiarismCase(PlagiarismCase plagiarismCase, User student) {
        notifyRecipientWithNotificationType(plagiarismCase, NEW_PLAGIARISM_CASE_STUDENT, student, userRepository.getUser());
    }

    /**
     * Notify student about possible plagiarism case opened by the continuous plagiarism control.
     * The notification is created without explicit notification author.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutNewContinuousPlagiarismControlPlagiarismCase(PlagiarismCase plagiarismCase, User student) {
        notifyRecipientWithNotificationType(plagiarismCase, NEW_CPC_PLAGIARISM_CASE_STUDENT, student, null);
    }

    /**
     * Notify student about plagiarism case verdict.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param student        who should be notified
     */
    public void notifyUserAboutPlagiarismCaseVerdict(PlagiarismCase plagiarismCase, User student) {
        notifyRecipientWithNotificationType(plagiarismCase, PLAGIARISM_CASE_VERDICT_STUDENT, student, userRepository.getUser());
    }

    /**
     * Record to store tutorial group, users and responsible user in one notification subject.
     */
    public record TutorialGroupNotificationSubject(TutorialGroup tutorialGroup, Set<User> users, User responsibleUser) {
    }

    /**
     * Notify a student that he or she has been registered for a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the student has been registered for
     * @param student         the student that has been registered for the tutorial group
     * @param responsibleUser the user that has registered the student for the tutorial group
     */
    public void notifyStudentAboutRegistrationToTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_REGISTRATION_STUDENT, null, null);
    }

    /**
     * Notify a student that he or she has been deregistered from a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the student has been deregistered from
     * @param student         the student that has been deregistered from the tutorial group
     * @param responsibleUser the user that has deregistered the student from the tutorial group
     */
    public void notifyStudentAboutDeregistrationFromTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_DEREGISTRATION_STUDENT, null,
                null);
    }

    /**
     * Notify a tutor of tutorial group that multiple students have been registered for the tutorial group he or she is responsible for.
     *
     * @param tutorialGroup   the tutorial group the students have been registered for (containing the tutor that should be notified)
     * @param students        the students that have been registered for the tutorial group
     * @param responsibleUser the user that has registered the student for the tutorial group
     */
    public void notifyTutorAboutMultipleRegistrationsToTutorialGroup(TutorialGroup tutorialGroup, Set<User> students, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, students, responsibleUser), TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, null, null);
    }

    /**
     * Notify a tutor of a tutorial group that a student has been registered for the tutorial group he or she is responsible for.
     *
     * @param tutorialGroup   the tutorial group the student has registered for (containing the tutor that should be notified)
     * @param student         the student that has been registered for the tutorial group
     * @param responsibleUser the user that has registered the student for the tutorial group
     */
    public void notifyTutorAboutRegistrationToTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_REGISTRATION_TUTOR, null, null);
    }

    /**
     * Notify a tutor of a tutorial group that a student has been deregistered from the tutorial group he or she is responsible for.
     *
     * @param tutorialGroup   the tutorial group the student has been deregistered from (containing the tutor that should be notified)
     * @param student         the student that has been deregistered from the tutorial group
     * @param responsibleUser the user that has deregistered the student from the tutorial group
     */
    public void notifyTutorAboutDeregistrationFromTutorialGroup(TutorialGroup tutorialGroup, User student, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(student), responsibleUser), TUTORIAL_GROUP_DEREGISTRATION_TUTOR, null, null);
    }

    /**
     * Notify a tutor that he or she has been assigned to lead a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the tutor has been assigned to lead
     * @param tutorToContact  the tutor that has been assigned to lead the tutorial group
     * @param responsibleUser the user that has assigned the tutor to lead the tutorial group
     */
    public void notifyTutorAboutAssignmentToTutorialGroup(TutorialGroup tutorialGroup, User tutorToContact, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(tutorToContact), responsibleUser), TUTORIAL_GROUP_ASSIGNED, null, null);
    }

    /**
     * Notify a tutor that he or she has been unassigned from the leadership of a tutorial group.
     *
     * @param tutorialGroup   the tutorial group the tutor has been unassigned from
     * @param tutorToContact  the tutor that has been unassigned
     * @param responsibleUser the user that has unassigned the tutor
     */
    public void notifyTutorAboutUnassignmentFromTutorialGroup(TutorialGroup tutorialGroup, User tutorToContact, User responsibleUser) {
        notifyRecipientWithNotificationType(new TutorialGroupNotificationSubject(tutorialGroup, Set.of(tutorToContact), responsibleUser), TUTORIAL_GROUP_UNASSIGNED, null, null);
    }

    /**
     * Record to store conversation, user and responsible user in one notification subject.
     */
    public record ConversationNotificationSubject(Conversation conversation, User user, User responsibleUser) {
    }

    /**
     * Record to store Answer post, users and responsible user in one notification subject.
     */
    public record NewReplyNotificationSubject(AnswerPost answerPost, User user, User responsibleUser) {
    }

    /**
     * Notify a user about new chat creation or conversation deletion.
     *
     * @param conversation     the conversation the student has been added for or removed from
     * @param user             the user that has been added for the conversation or removed from the conversation
     * @param responsibleUser  the responsibleUser that has registered/removed the user for the conversation
     * @param notificationType the type of notification to be sent
     */
    public void notifyClientAboutConversationCreationOrDeletion(Conversation conversation, User user, User responsibleUser, NotificationType notificationType) {
        notifyRecipientWithNotificationType(new ConversationNotificationSubject(conversation, user, responsibleUser), notificationType, null, null);
    }

    /**
     * Notify a user about new message reply in a conversation.
     *
     * @param answerPost       the answerPost of the user involved
     * @param notification     the notification template to be used
     * @param user             the user that is involved in the message reply
     * @param responsibleUser  the responsibleUser sending the message reply
     * @param notificationType the type for the conversation
     */
    public void notifyUserAboutNewMessageReply(AnswerPost answerPost, SingleUserNotification notification, User user, User responsibleUser, NotificationType notificationType) {
        notification.setRecipient(user);
        notification.setTitle(findCorrespondingNotificationTitleOrThrow(notificationType));
        notification.setAuthor(responsibleUser);
        saveAndSend(notification, new NewReplyNotificationSubject(answerPost, user, responsibleUser), responsibleUser, true);
    }

    /**
     * Create a notification for a message reply
     *
     * @param answerMessage the answerMessage of the user involved
     * @param author        the author of the message reply
     * @param conversation  conversation the message of the reply belongs to
     * @return notification
     */
    public SingleUserNotification createNotificationAboutNewMessageReply(AnswerPost answerMessage, User author, Conversation conversation) {
        User authorWithHiddenData = new User(author.getId(), null, author.getFirstName(), author.getLastName(), null, null);
        return createSingleUserNotification(new NewReplyNotificationSubject(answerMessage, authorWithHiddenData, authorWithHiddenData),
                getAnswerMessageNotificationType(conversation), null, author);
    }

    /**
     * Notifies involved users about the new answer message, i.e. the author of the original message, users that have also replied, and mentioned users
     *
     * @param post               the message the answer belongs to
     * @param notification       the notification template to be used
     * @param mentionedUsers     users mentioned in the answer message
     * @param savedAnswerMessage the answer message
     * @param author             the author of the answer message
     */
    @Async
    public void notifyInvolvedUsersAboutNewMessageReply(Post post, SingleUserNotification notification, Set<User> mentionedUsers, AnswerPost savedAnswerMessage, User author) {
        SecurityUtils.setAuthorizationObject(); // required for async
        Set<User> usersInvolved = conversationMessageRepository.findUsersWhoRepliedInMessage(post.getId());
        // do not notify the author of the post if they are not part of the conversation (e.g. if they left or have been removed from the conversation)
        if (conversationService.isMember(post.getConversation().getId(), post.getAuthor().getId())) {
            usersInvolved.add(post.getAuthor());
        }

        mentionedUsers.stream().filter(user -> {
            boolean isChannelAndCourseWide = post.getConversation() instanceof Channel channel && channel.getIsCourseWide();
            boolean isChannelVisibleToStudents = !(post.getConversation() instanceof Channel channel) || conversationService.isChannelVisibleToStudents(channel);
            boolean isChannelVisibleToMentionedUser = isChannelVisibleToStudents
                    || authorizationCheckService.isAtLeastTeachingAssistantInCourse(post.getConversation().getCourse(), user);

            // Only send a notification to the mentioned user if...
            // (for course-wide channels) ...the course-wide channel is visible
            // (for all other cases) ...the user is a member of the conversation
            return (isChannelAndCourseWide && isChannelVisibleToMentionedUser) || conversationService.isMember(post.getConversation().getId(), user.getId());
        }).forEach(mentionedUser -> notifyUserAboutNewMessageReply(savedAnswerMessage, notification, mentionedUser, author, CONVERSATION_USER_MENTIONED));

        Conversation conv = conversationService.getConversationById(post.getConversation().getId());
        usersInvolved.stream().filter(userInvolved -> !mentionedUsers.contains(userInvolved))
                .forEach(userInvolved -> notifyUserAboutNewMessageReply(savedAnswerMessage, notification, userInvolved, author, getAnswerMessageNotificationType(conv)));
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also creates and sends an instant notification.
     *
     * @param notification        that should be saved and sent
     * @param notificationSubject which information will be extracted to create the email
     * @param skipWebSocket       whether to skipWebSocket notifications
     */
    private void saveAndSend(SingleUserNotification notification, Object notificationSubject, User author, boolean skipWebSocket) {
        // do not save notifications that are not relevant for the user
        if (shouldNotificationBeSaved(notification)) {
            singleUserNotificationRepository.save(notification);
        }
        // we only want to notify one individual user therefore we can check the settings and filter preemptively
        boolean isWebappNotificationAllowed = !skipWebSocket
                && notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification, notification.getRecipient(), WEBAPP);
        if (isWebappNotificationAllowed) {
            websocketMessagingService.sendMessage(notification.getTopic(), notification);
        }

        prepareSingleUserInstantNotification(notification, notificationSubject, author);
    }

    /**
     * Determines the type of the notification based on the type of the conversation
     *
     * @param conversation the message the reply belongs to
     * @return notification type
     */
    private NotificationType getAnswerMessageNotificationType(Conversation conversation) {
        NotificationType answerMessageNotificationType;
        if (conversation instanceof Channel channel) {
            if (channel.getExercise() != null) {
                answerMessageNotificationType = NEW_REPLY_FOR_EXERCISE_POST;
            }
            else if (channel.getLecture() != null) {
                answerMessageNotificationType = NEW_REPLY_FOR_LECTURE_POST;
            }
            else if (channel.getExam() != null) {
                answerMessageNotificationType = NEW_REPLY_FOR_EXAM_POST;
            }
            else if (channel.getIsCourseWide()) {
                answerMessageNotificationType = NEW_REPLY_FOR_COURSE_POST;
            }
            else {
                answerMessageNotificationType = CONVERSATION_NEW_REPLY_MESSAGE;
            }
        }
        else {
            answerMessageNotificationType = CONVERSATION_NEW_REPLY_MESSAGE;
        }
        return answerMessageNotificationType;
    }

    private boolean shouldNotificationBeSaved(SingleUserNotification notification) {
        if (Objects.equals(notification.getTitle(), CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE)) {
            return false;
        }
        else if (Objects.equals(notification.getTitle(), CONVERSATION_CREATE_GROUP_CHAT_TITLE) || Objects.equals(notification.getTitle(), CONVERSATION_DELETE_CHANNEL_TITLE)
                || Objects.equals(notification.getTitle(), CONVERSATION_ADD_USER_CHANNEL_TITLE) || Objects.equals(notification.getTitle(), CONVERSATION_ADD_USER_GROUP_CHAT_TITLE)
                || Objects.equals(notification.getTitle(), CONVERSATION_REMOVE_USER_CHANNEL_TITLE)
                || Objects.equals(notification.getTitle(), CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE)
                || Objects.equals(notification.getTitle(), MESSAGE_REPLY_IN_CONVERSATION_TITLE) || Objects.equals(notification.getTitle(), MENTIONED_IN_MESSAGE_TITLE)
                || Objects.equals(notification.getTitle(), NEW_REPLY_FOR_COURSE_POST_TITLE) || Objects.equals(notification.getTitle(), NEW_REPLY_FOR_EXAM_POST_TITLE)
                || Objects.equals(notification.getTitle(), NEW_REPLY_FOR_LECTURE_POST_TITLE) || Objects.equals(notification.getTitle(), NEW_REPLY_FOR_EXERCISE_POST_TITLE)) {
            return (!Objects.equals(notification.getAuthor().getLogin(), notification.getRecipient().getLogin()));
        }
        return true;
    }

    /**
     * Checks if an instant notification should be created based on the provided notification, user, notification settings and type for SingleUserNotifications
     * If the checks are successful creates and sends a corresponding instant notification
     *
     * @param notification        that should be checked
     * @param notificationSubject which information will be extracted to create the email
     */
    private void prepareSingleUserInstantNotification(SingleUserNotification notification, Object notificationSubject, User author) {
        NotificationType type = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());

        // If the notification is about a reply and the author is also the recipient, we skip send. Do not notify the sender of the message about their own message!
        boolean skipSend = type == CONVERSATION_NEW_REPLY_MESSAGE && Objects.equals(notification.getRecipient().getId(), author.getId());

        // checks if this notification type has email support
        if (notificationSettingsService.checkNotificationTypeForInstantNotificationSupport(type) && !skipSend) {
            notificationService.sendNotification(notification, notification.getRecipient(), notificationSubject);
        }
    }
}
