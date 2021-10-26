package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.SingleUserNotification;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;

@Service
public class SingleUserNotificationService {

    private final SingleUserNotificationRepository singleUserNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, SimpMessageSendingOperations messagingTemplate) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     * @param post that will be used to create the notification
     * @param notificationType is the discriminator for the factory
     * @param course that the post belongs to
     */
    public void notifyGroupsWithNotificationType(Post post, NotificationType notificationType, Course course) {
        SingleUserNotification resultingGroupNotification;
        resultingGroupNotification = switch (notificationType) {
            // Post Types
            case NEW_REPLY_FOR_EXERCISE_POST -> createNotification(post, NotificationType.NEW_REPLY_FOR_EXERCISE_POST, course);
            case NEW_REPLY_FOR_LECTURE_POST -> createNotification(post, NotificationType.NEW_REPLY_FOR_LECTURE_POST, course);
            case NEW_REPLY_FOR_COURSE_POST -> createNotification(post, NotificationType.NEW_REPLY_FOR_COURSE_POST, course);
            default -> throw new UnsupportedOperationException("Can not create notification for type : " + notificationType);
        };
        saveAndSend(resultingGroupNotification);
    }

    /**
     * Notify author of a post for an exercise that there is a new answer.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForExercise(Post post, Course course) {
        notifyGroupsWithNotificationType(post, NotificationType.NEW_REPLY_FOR_EXERCISE_POST, course);
    }

    /**
     * Notify author of a post for a lecture that there is a new answer.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForLecture(Post post, Course course) {
        notifyGroupsWithNotificationType(post, NotificationType.NEW_REPLY_FOR_LECTURE_POST, course);
    }

    /**
     * Notify author of a course-wide that there is a new answer.
     *
     * @param post that is answered
     * @param course that the post belongs to
     */
    public void notifyUserAboutNewAnswerForCoursePost(Post post, Course course) {
        notifyGroupsWithNotificationType(post, NotificationType.NEW_REPLY_FOR_COURSE_POST, course);
    }

    /**
     * notifyUserAboutPlagiarismCase creates a plagiarismNotification saves it to the database and returns it.
     * @param plagiarismNotification A singleUserNotification
     * @return converted plagiarism notification
     */
    public SingleUserNotification notifyUserAboutPlagiarismCase(SingleUserNotification plagiarismNotification) {
        var res = singleUserNotificationRepository.save(plagiarismNotification);
        messagingTemplate.convertAndSend(plagiarismNotification.getTopic(), plagiarismNotification);
        return res;
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     *
     * @param notification that should be saved and sent
     */
    private void saveAndSend(SingleUserNotification notification) {
        singleUserNotificationRepository.save(notification);
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
    }
}
