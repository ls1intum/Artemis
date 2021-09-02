package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.notification.SingleUserNotificationFactory.createNotification;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
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
     * Notify author of a question for an exercise that there is a new answer.
     *
     * @param answer for exercise that is new
     */
    public void notifyUserAboutNewAnswerForExercise(AnswerPost answer) {
        saveAndSend(createNotification(answer, NotificationType.NEW_ANSWER_POST_FOR_EXERCISE));
    }

    /**
     * Notify author of a question for a lecture that there is a new answer.
     *
     * @param answer for lecture that is new
     */
    public void notifyUserAboutNewAnswerForLecture(AnswerPost answer) {
        saveAndSend(createNotification(answer, NotificationType.NEW_ANSWER_POST_FOR_LECTURE));
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
