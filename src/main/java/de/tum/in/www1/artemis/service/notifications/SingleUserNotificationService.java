package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.service.notifications.NotificationSettingsCommunicationChannel.*;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;
import de.tum.in.www1.artemis.service.MailService;

@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final MailService mailService;

    private final NotificationSettingsService notificationSettingsService;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, SimpMessageSendingOperations messagingTemplate, MailService mailService,
            NotificationSettingsService notificationSettingsService) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
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
    private void notifyRecipientWithNotificationType(Object notificationSubject, NotificationType notificationType, Object typeSpecificInformation) {
        SingleUserNotification resultingGroupNotification;
        resultingGroupNotification = switch (notificationType) {
            // Post Types
            case NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST -> createNotification((Post) notificationSubject, notificationType,
                    (Course) typeSpecificInformation);
            // Exercise related
            case FILE_SUBMISSION_SUCCESSFUL -> createNotification((Exercise) notificationSubject, notificationType, (User) typeSpecificInformation);
            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
        saveAndSend(resultingGroupNotification, notificationSubject);
    }

    /**
     * Notify author of a post for an exercise that there is a new reply.
     *
     * @param post that is replied
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewReplyForExercise(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_EXERCISE_POST, course);
    }

    /**
     * Notify author of a post for a lecture that there is a new reply.
     *
     * @param post that is replied
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewReplyForLecture(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_LECTURE_POST, course);
    }

    /**
     * Notify author of a course-wide that there is a new reply.
     * Also creates and sends an email.
     *
     * @param post that is replied
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewReplyForCoursePost(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NEW_REPLY_FOR_COURSE_POST, course);
    }

    /**
     * Notify student about successful submission of file upload exercise.
     * Also creates and sends an email.
     *
     * @param exercise that was submitted
     * @param recipient that should be notified
     */
    public void notifyUserAboutSuccessfulFileUploadSubmission(FileUploadExercise exercise, User recipient) {
        notifyRecipientWithNotificationType(exercise, FILE_SUBMISSION_SUCCESSFUL, recipient);
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
