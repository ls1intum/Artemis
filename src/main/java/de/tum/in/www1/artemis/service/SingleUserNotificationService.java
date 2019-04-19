package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.SingleUserNotification;
import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.SingleUserNotificationRepository;

@Service
@Transactional
public class SingleUserNotificationService {

    private SingleUserNotificationRepository singleUserNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    public SingleUserNotificationService(SingleUserNotificationRepository singleUserNotificationRepository, SimpMessageSendingOperations messagingTemplate) {
        this.singleUserNotificationRepository = singleUserNotificationRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyUserAboutNewAnswer(StudentQuestionAnswer studentQuestionAnswer) {
        User recipient = studentQuestionAnswer.getQuestion().getAuthor();
        User author = studentQuestionAnswer.getAuthor();
        String title = "New Answer";
        String text = "Your Question got answered!";
        SingleUserNotification userNotification = new SingleUserNotification(recipient, author, title, text);
        userNotification.setTarget(userNotification.studentQuestionAnswerTarget(studentQuestionAnswer));
        saveAndSendSingleUserNotification(userNotification);
    }

    private void saveAndSendSingleUserNotification(SingleUserNotification userNotification) {
        singleUserNotificationRepository.save(userNotification);
        messagingTemplate.convertAndSend(userNotification.getTopic(), userNotification);
    }

    public List<Notification> findAllNewNotificationsForCurrentUser() {
        return this.singleUserNotificationRepository.findAllNewNotificationsForCurrentUser();
    }

}
