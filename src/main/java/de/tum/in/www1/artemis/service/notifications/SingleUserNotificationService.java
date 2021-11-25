package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
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

    private MailService mailService;

    private NotificationSettingsService notificationSettingsService;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, SimpMessageSendingOperations messagingTemplate, MailService mailService,
            NotificationSettingsService notificationSettingsService) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.mailService = mailService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     * @param post that will be used to create the notification
     * @param notificationType is the discriminator for the factory
     * @param course that the post belongs to
     */
    private void notifyRecipientWithNotificationType(Post post, NotificationType notificationType, Course course) {
        SingleUserNotification resultingGroupNotification;
        resultingGroupNotification = switch (notificationType) {
            // Post Types
            case NEW_REPLY_FOR_EXERCISE_POST -> createNotification(post, NotificationType.NEW_REPLY_FOR_EXERCISE_POST, course);
            case NEW_REPLY_FOR_LECTURE_POST -> createNotification(post, NotificationType.NEW_REPLY_FOR_LECTURE_POST, course);
            case NEW_REPLY_FOR_COURSE_POST -> createNotification(post, NotificationType.NEW_REPLY_FOR_COURSE_POST, course);
            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
        saveAndSend(resultingGroupNotification, post);
    }

    /**
     * Notify author of a post for an exercise that there is a new answer.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForExercise(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NotificationType.NEW_REPLY_FOR_EXERCISE_POST, course);
    }

    /**
     * Notify author of a post for a lecture that there is a new answer.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForLecture(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NotificationType.NEW_REPLY_FOR_LECTURE_POST, course);
    }

    /**
     * Notify author of a course-wide that there is a new answer.
     * Also creates and sends an email.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForCoursePost(Post post, Course course) {
        notifyRecipientWithNotificationType(post, NotificationType.NEW_REPLY_FOR_COURSE_POST, course);
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
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
        prepareSingleUserNotificationEmail(notification, notificationSubject);
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
            boolean isAllowedBySettings = notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notification, notification.getRecipient());
            if (isAllowedBySettings) {
                mailService.sendNotificationEmail(notification, notification.getRecipient(), notificationSubject);
            }
        }
    }
}
