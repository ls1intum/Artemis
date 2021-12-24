package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.*;

import java.time.ZonedDateTime;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.messaging.InstanceMessageSendService;

@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final UserRepository userRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final MailService mailService;

    private final NotificationSettingsService notificationSettingsService;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, UserRepository userRepository,
            SimpMessageSendingOperations messagingTemplate, MailService mailService, NotificationSettingsService notificationSettingsService) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.mailService = mailService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     * @param notificationSubject is the subject of the notification (e.g. exercise, attachment)
     * @param notificationType is the discriminator for the factory
     * @param typeSpecificInformation is based on the current use case (e.g. POST -> course, Exercise -> user)
     */
    private void notifyRecipientWithNotificationType(Object notificationSubject, NotificationType notificationType, Object typeSpecificInformation, User author) {
        SingleUserNotification resultingGroupNotification;
        resultingGroupNotification = switch (notificationType) {
            // Post Types
            case NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST -> createNotification((Post) notificationSubject, notificationType,
                    (Course) typeSpecificInformation);
            // Exercise related
            case EXERCISE_SUBMISSION_ASSESSED, FILE_SUBMISSION_SUCCESSFUL -> createNotification((Exercise) notificationSubject, notificationType, (User) typeSpecificInformation);
            // Plagiarism related
            case NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT, PLAGIARISM_CASE_FINAL_STATE_STUDENT -> createNotification((PlagiarismComparison) notificationSubject, notificationType,
                    (User) typeSpecificInformation, author);

            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
        saveAndSend(resultingGroupNotification, notificationSubject);
    }

    /**
     * Notify author of a post for an exercise that there is a new answer.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForExercise(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_EXERCISE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a post for a lecture that there is a new answer.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForLecture(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_LECTURE_POST, course, post.getAuthor());
    }

    /**
     * Notify author of a course-wide that there is a new answer.
     * Also creates and sends an email.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForCoursePost(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_COURSE_POST, course, post.getAuthor());
    }

    /**
     * Notify student about the finished assessment for an exercise submission.
     * Also creates and sends an email.
     *
     * @param exercise that was assessed
     * @param recipient who should be notified
     */
    public void notifyUserAboutAssessedExerciseSubmission(Exercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, EXERCISE_SUBMISSION_ASSESSED, recipient, null);
    }

    /**
     * Checks if a new assessed-exercise-submission notification has to be created now or scheduled
     *
     * @param submission that is needed for potential scheduling
     * @param exercise which the submission is based on
     * @param recipient of the notification (i.e. the student)
     * @param instanceMessageSendService that will call the service to update the scheduled assessed-exercise-submission notification
     */
    public void checkNotificationForAssessmentExerciseSubmission(Submission submission, Exercise exercise, User recipient, InstanceMessageSendService instanceMessageSendService) {
        if (exercise.isCourseExercise()) {
            // only send the notification now if no assessment due date was set or if it is in the past
            if (exercise.getAssessmentDueDate() == null || !exercise.getAssessmentDueDate().isAfter(ZonedDateTime.now())) {
                notifyUserAboutAssessedExerciseSubmission(exercise, recipient);
            }
            else {
                instanceMessageSendService.sendAssessedExerciseSubmissionNotificationSchedule(submission.getId());
            }
        }
    }

    /**
     * Notify student about successful submission of file upload exercise.
     * Also creates and sends an email.
     *
     * @param exercise that was submitted
     * @param recipient that should be notified
     */
    public void notifyUserAboutSuccessfulFileUploadSubmission(FileUploadExercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, FILE_SUBMISSION_SUCCESSFUL, recipient, null);
    }

    /**
     * Notify student about possible plagiarism case.
     *
     * @param plagiarismComparison that hold the major information for the plagiarism case
     * @param student who should be notified
     */
    public void notifyUserAboutNewPossiblePlagiarismCase(PlagiarismComparison plagiarismComparison, User student) {
        notifyRecipientWithNotificationType(plagiarismComparison, NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT, student, userRepository.getUser());
    }

    /**
     * Notify student about plagiarism case update.
     *
     * @param plagiarismComparison that hold the major information for the plagiarism case
     * @param student who should be notified
     */
    public void notifyUserAboutFinalPlagiarismState(PlagiarismComparison plagiarismComparison, User student) {
        notifyRecipientWithNotificationType(plagiarismComparison, PLAGIARISM_CASE_FINAL_STATE_STUDENT, student, userRepository.getUser());
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also creates and sends an email.
     *
     * @param notification that should be saved and sent
     * @param notificationSubject which information will be extracted to create the email
     */
    private void saveAndSend(SingleUserNotification notification, Object notificationSubject) {
        singleUserNotificationRepository.save(notification);
        // we only want to notify one individual user therefore we can check the settings and filter preemptively
        boolean isAllowedBySettings = notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(notification, notification.getRecipient(), WEBAPP);
        if (isAllowedBySettings) {
            messagingTemplate.convertAndSend(notification.getTopic(), notification);
            prepareSingleUserNotificationEmail(notification, notificationSubject);
        }
    }

    /**
     * Checks if an email should be created based on the provided notification, user, notification settings and type for SingleUserNotifications
     * If the checks are successful creates and sends a corresponding email
     * @param notification that should be checked
     * @param notificationSubject which information will be extracted to create the email
     */
    private void prepareSingleUserNotificationEmail(SingleUserNotification notification, Object notificationSubject) {
        NotificationType type = NotificationTitleTypeConstants.findCorrespondingNotificationType(notification.getTitle());
        // checks if this notification type has email support
        if (notificationSettingsService.checkNotificationTypeForEmailSupport(type)) {
            boolean isAllowedBySettingsForEmail = notificationSettingsService.checkIfNotificationOrEmailIsAllowedBySettingsForGivenUser(notification, notification.getRecipient(),
                    EMAIL);
            if (isAllowedBySettingsForEmail) {
                mailService.sendNotificationEmail(notification, notification.getRecipient(), notificationSubject);
            }
        }
    }
}
