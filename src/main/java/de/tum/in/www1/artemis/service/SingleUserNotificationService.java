package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.SingleUserNotification;
import de.tum.in.www1.artemis.domain.StudentQuestionAnswer;
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
        SingleUserNotification userNotification = new SingleUserNotification();
        userNotification.setRecipient(studentQuestionAnswer.getQuestion().getAuthor());
        userNotification.setAuthor(studentQuestionAnswer.getAuthor());
        userNotification.setNotificationDate(ZonedDateTime.now());
        userNotification.setTitle("New Answer");
        userNotification.setText("Your Question got answered!");
        JsonObject target = new JsonObject();
        target.addProperty("message", "newAnswer");
        target.addProperty("id", studentQuestionAnswer.getQuestion().getExercise().getId());
        target.addProperty("entity", "exercises");
        target.addProperty("course", studentQuestionAnswer.getQuestion().getExercise().getCourse().getId());
        target.addProperty("mainPage", "overview");
        userNotification.setTarget(target.toString());
        saveAndSendSingleUserNotification(userNotification);
    }

    private void saveAndSendSingleUserNotification(SingleUserNotification userNotification) {
        singleUserNotificationRepository.save(userNotification);
        String userTopic = "topic/user/" + userNotification.getRecipient().getId();
        messagingTemplate.convertAndSend(userTopic, userNotification);
    }

    public List<Notification> findAllNewNotificationsForCurrentUser() {
        return this.singleUserNotificationRepository.findAllNewNotificationsForCurrentUser();
    }

}
