package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.domain.metis.conversation.Conversation;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.ExerciseDateService;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final UserRepository userRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final GeneralInstantNotificationService notificationService;

    private final NotificationSettingsService notificationSettingsService;

    private final StudentParticipationRepository studentParticipationRepository;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, UserRepository userRepository,
            WebsocketMessagingService websocketMessagingService, GeneralInstantNotificationService notificationService, NotificationSettingsService notificationSettingsService,
            StudentParticipationRepository studentParticipationRepository) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.userRepository = userRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.notificationService = notificationService;
        this.notificationSettingsService = notificationSettingsService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     *
     * @param notificationSubject     is the subject of the notification (e.g. exercise, attachment)
     * @param notificationType        is the discriminator for the factory
     * @param typeSpecificInformation is based on the current use case (e.g. POST -> course, Exercise -> user)
     */
    private void notifyRecipientWithNotificationType(Object notificationSubject, NotificationType notificationType, Object typeSpecificInformation, User author) {
        var singleUserNotification = switch (notificationType) {
            // Post Types
            case NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST -> createNotification((Post) ((List<Posting>) notificationSubject).get(0),
                    (AnswerPost) ((List<Posting>) notificationSubject).get(1), notificationType, (Course) typeSpecificInformation);
            // Exercise related
            case EXERCISE_SUBMISSION_ASSESSED, FILE_SUBMISSION_SUCCESSFUL -> createNotification((Exercise) notificationSubject, notificationType, (User) typeSpecificInformation);
            // Plagiarism related
            case NEW_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_VERDICT_STUDENT -> createNotification((PlagiarismCase) notificationSubject, notificationType,
                    (User) typeSpecificInformation, author);
            // Tutorial Group related
            case TUTORIAL_GROUP_REGISTRATION_STUDENT, TUTORIAL_GROUP_DEREGISTRATION_STUDENT, TUTORIAL_GROUP_REGISTRATION_TUTOR, TUTORIAL_GROUP_DEREGISTRATION_TUTOR, TUTORIAL_GROUP_MULTIPLE_REGISTRATION_TUTOR, TUTORIAL_GROUP_ASSIGNED, TUTORIAL_GROUP_UNASSIGNED -> createNotification(
                    ((TutorialGroupNotificationSubject) notificationSubject).tutorialGroup, notificationType, ((TutorialGroupNotificationSubject) notificationSubject).users,
                    ((TutorialGroupNotificationSubject) notificationSubject).responsibleUser);
            // Conversation creation related
            case CONVERSATION_CREATE_ONE_TO_ONE_CHAT, CONVERSATION_CREATE_GROUP_CHAT, CONVERSATION_ADD_USER_GROUP_CHAT, CONVERSATION_ADD_USER_CHANNEL, CONVERSATION_REMOVE_USER_GROUP_CHAT, CONVERSATION_REMOVE_USER_CHANNEL, CONVERSATION_DELETE_CHANNEL -> createNotification(
                    ((ConversationNotificationSubject) notificationSubject).conversation, notificationType, ((ConversationNotificationSubject) notificationSubject).user,
                    ((ConversationNotificationSubject) notificationSubject).responsibleUser);
            case CONVERSATION_NEW_REPLY_MESSAGE -> createNotification(((NewReplyNotificationSubject) notificationSubject).answerPost, notificationType,
                    ((NewReplyNotificationSubject) notificationSubject).user, ((NewReplyNotificationSubject) notificationSubject).responsibleUser);
            case DATA_EXPORT_CREATED, DATA_EXPORT_FAILED -> createNotification((DataExport) notificationSubject, notificationType, (User) typeSpecificInformation);
            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
        saveAndSend(singleUserNotification, notificationSubject, author);
    }

    /**
     * Notify all users with available assessments about the finished assessment for an exercise submission.
     * This is an auxiliary method that finds all relevant users and initiates the process for sending SingleUserNotifications and emails
     *
     * @param exercise which assessmentDueDate is the trigger for the notification process
     */
    public void notifyUsersAboutAssessedExerciseSubmission(Exercise exercise) {
        // This process can not be replaces via a GroupNotification (can only notify ALL students of the course)
        // because we want to notify only the students that have a valid assessed submission.

        // Find student participations with eager legal submissions and latest results that have a completion date
        Set<StudentParticipation> filteredStudentParticipations = Set
                .copyOf(studentParticipationRepository.findByExerciseIdAndTestRunWithEagerLegalSubmissionsAndLatestResultWithCompletionDate(exercise.getId(), false));

        // Load and assign all studentParticipations with results (this information is needed for the emails later)
        exercise.setStudentParticipations(filteredStudentParticipations);

        // Extract all users that should be notified from the previously loaded student participations
        Set<User> relevantStudents = filteredStudentParticipations.stream().map(participation -> participation.getStudent().orElseThrow()).collect(Collectors.toSet());

        // notify all relevant users
        relevantStudents.forEach(student -> notifyUserAboutAssessedExerciseSubmission(exercise, student));
    }

    /**
     * Notify author of a post for an exercise that there is a new reply.
     *
     * @param post       that is replied
     * @param answerPost that is replied with
     * @param course     that the post belongs to
     */
    public void notifyUserAboutNewReplyForExercise(Post post, AnswerPost answerPost, Course course) {
        notifyRecipientWithNotificationType(Arrays.asList(post, answerPost), NEW_REPLY_FOR_EXERCISE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a post for a lecture that there is a new reply.
     *
     * @param post       that is replied
     * @param answerPost that is replied with
     * @param course     that the post belongs to
     */
    public void notifyUserAboutNewReplyForLecture(Post post, AnswerPost answerPost, Course course) {
        notifyRecipientWithNotificationType(Arrays.asList(post, answerPost), NEW_REPLY_FOR_LECTURE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a course-wide that there is a new reply.
     * Also creates and sends an email.
     *
     * @param post       that is replied
     * @param answerPost that is replied with
     * @param course     that the post belongs to
     */
    public void notifyUserAboutNewReplyForCoursePost(Post post, AnswerPost answerPost, Course course) {
        notifyRecipientWithNotificationType(Arrays.asList(post, answerPost), NEW_REPLY_FOR_COURSE_POST, course, post.getAuthor());
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
     * @param answerPost      the answerPost of the user involved
     * @param user            the user that is involved in the message reply
     * @param responsibleUser the responsibleUser sending the message reply
     */
    public void notifyUserAboutNewMessageReply(AnswerPost answerPost, User user, User responsibleUser) {
        notifyRecipientWithNotificationType(new NewReplyNotificationSubject(answerPost, user, responsibleUser), CONVERSATION_NEW_REPLY_MESSAGE, null, responsibleUser);
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also creates and sends an instant notification.
     *
     * @param notification        that should be saved and sent
     * @param notificationSubject which information will be extracted to create the email
     */
    private void saveAndSend(SingleUserNotification notification, Object notificationSubject, User author) {
        // do not save notifications that are not relevant for the user
        if (shouldNotificationBeSaved(notification)) {
            singleUserNotificationRepository.save(notification);
        }
        // we only want to notify one individual user therefore we can check the settings and filter preemptively
        boolean isWebappNotificationAllowed = notificationSettingsService.checkIfNotificationIsAllowedInCommunicationChannelBySettingsForGivenUser(notification,
                notification.getRecipient(), WEBAPP);
        if (isWebappNotificationAllowed) {
            websocketMessagingService.sendMessage(notification.getTopic(), notification);
        }

        prepareSingleUserInstantNotification(notification, notificationSubject, author);
    }

    private boolean shouldNotificationBeSaved(SingleUserNotification notification) {
        if (Objects.equals(notification.getTitle(), CONVERSATION_CREATE_ONE_TO_ONE_CHAT_TITLE)) {
            return false;
        }
        else if (Objects.equals(notification.getTitle(), CONVERSATION_CREATE_GROUP_CHAT_TITLE) || Objects.equals(notification.getTitle(), CONVERSATION_DELETE_CHANNEL_TITLE)
                || Objects.equals(notification.getTitle(), CONVERSATION_ADD_USER_CHANNEL_TITLE) || Objects.equals(notification.getTitle(), CONVERSATION_ADD_USER_GROUP_CHAT_TITLE)
                || Objects.equals(notification.getTitle(), CONVERSATION_REMOVE_USER_CHANNEL_TITLE)
                || Objects.equals(notification.getTitle(), CONVERSATION_REMOVE_USER_GROUP_CHAT_TITLE)
                || Objects.equals(notification.getTitle(), MESSAGE_REPLY_IN_CONVERSATION_TITLE)) {
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
