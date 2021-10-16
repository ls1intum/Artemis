package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

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
     * Notify author of a post for an exercise that there is a new answer.
     *
     * @param post that is answered
     */
    public void notifyUserAboutNewAnswerForExercise(Post post) {
        saveAndSend(createNotification(post, NotificationType.NEW_REPLY_FOR_EXERCISE_POST));
    }

    /**
     * Notify author of a post for a lecture that there is a new answer.
     *
     * @param post that is answe3red
     */
    public void notifyUserAboutNewAnswerForLecture(Post post) {
        saveAndSend(createNotification(post, NotificationType.NEW_REPLY_FOR_LECTURE_POST));
    }

    /**
     * Notify author of a course-wide that there is a new answer.
     *
     * @param post that is answered
     */
    public void notifyUserAboutNewAnswerForCoursePost(Post post) {
        saveAndSend(createNotification(post, NotificationType.NEW_REPLY_FOR_COURSE_POST));
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
