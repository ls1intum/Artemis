package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.gson.JsonObject;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;

@Service
@Transactional
public class GroupNotificationService {

    private GroupNotificationRepository groupNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private UserService userService;

    public GroupNotificationService(GroupNotificationRepository groupNotificationRepository, SimpMessageSendingOperations messagingTemplate, UserService userService) {
        this.groupNotificationRepository = groupNotificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
    }

    public void notifyGroupAboutExerciseStart(Exercise exercise) {
        String title = "Exercise started";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" just started.";
        notifyGroupAboutExercise(exercise, title, notificationText, "exerciseUpdated", GroupNotificationType.STUDENT);
    }

    public void notifyGroupAboutExerciseVisibility(Exercise exercise) {
        String title = "New exercise available";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" is now available.";
        notifyGroupAboutExercise(exercise, title, notificationText, "exerciseUpdated", GroupNotificationType.STUDENT);
    }

    public void notifyGroupAboutExercisePractice(Exercise exercise) {
        String title = "Exercise open for practice";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
        notifyGroupAboutExercise(exercise, title, notificationText, "exerciseUpdated", GroupNotificationType.STUDENT);
    }

    public void notifyGroupAboutExerciseChange(Exercise exercise) {
        String title = "Exercise updated";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" got updated.";
        notifyGroupAboutExercise(exercise, title, notificationText, "exerciseUpdated", GroupNotificationType.STUDENT);
    }

    public void notifyGroupAboutExerciseCreated(Exercise exercise) {
        String title = "Exercise created";
        String notificationText = "A new exercise \"" + exercise.getTitle() + "\" got created.";
        notifyGroupAboutExercise(exercise, title, notificationText, "exerciseCreated", GroupNotificationType.TA);
    }

    private void notifyGroupAboutExercise(Exercise exercise, String title, String notificationText, String message, GroupNotificationType type) {
        GroupNotification groupNotification = new GroupNotification();
        groupNotification.setCourse(exercise.getCourse());
        groupNotification.setType(type);
        groupNotification.setNotificationDate(ZonedDateTime.now());
        groupNotification.setTitle(title);
        groupNotification.setText(notificationText);
        JsonObject target = new JsonObject();
        target.addProperty("message", message);
        target.addProperty("id", exercise.getId());
        target.addProperty("entity", "exercises");
        target.addProperty("course", exercise.getCourse().getId());
        target.addProperty("mainPage", "overview");
        groupNotification.setTarget(target.toString());
        groupNotification.setAuthor(userService.getUser());
        String topic = "/topic/course/" + groupNotification.getCourse().getId() + "/" + groupNotification.getType();
        saveAndSendGroupNotification(topic, groupNotification);
    }

    public void notifyGroupAboutNewQuestion(StudentQuestion studentQuestion) {
        String title = "New Question";
        String notificationText = "Exercise \"" + studentQuestion.getExercise().getTitle() + "\" got a new question.";
        notifyGroupAboutExercise(studentQuestion.getExercise(), title, notificationText, "newQuestion", GroupNotificationType.TA);
        notifyGroupAboutExercise(studentQuestion.getExercise(), title, notificationText, "newQuestion", GroupNotificationType.INSTRUCTOR);
    }

    public void notifyGroupAboutNewAnswer(StudentQuestionAnswer studentQuestionAnswer) {
        String title = "New Answer";
        String notificationText = "Exercise \"" + studentQuestionAnswer.getQuestion().getExercise().getTitle() + "\" got a new answer.";
        notifyGroupAboutExercise(studentQuestionAnswer.getQuestion().getExercise(), title, notificationText, "newAnswer", GroupNotificationType.TA);
        notifyGroupAboutExercise(studentQuestionAnswer.getQuestion().getExercise(), title, notificationText, "newAnswer", GroupNotificationType.INSTRUCTOR);
    }

    public void notifyStudentGroupAboutAttachmentChange(Attachment attachment, String notificationText) {
        if (attachment.getReleaseDate() != null && !attachment.getReleaseDate().isBefore(ZonedDateTime.now())) {
            return;
        }
        GroupNotification groupNotification = new GroupNotification();
        groupNotification.setCourse(attachment.getLecture().getCourse());
        groupNotification.setType(GroupNotificationType.STUDENT);
        groupNotification.setNotificationDate(ZonedDateTime.now());
        groupNotification.setTitle("Attachment " + attachment.getName() + " updated");
        groupNotification.setText(notificationText);
        JsonObject target = new JsonObject();
        target.addProperty("message", "attachmentUpdated");
        target.addProperty("id", attachment.getLecture().getId());
        target.addProperty("entity", "lectures");
        target.addProperty("course", attachment.getLecture().getCourse().getId());
        target.addProperty("mainPage", "overview");
        groupNotification.setTarget(target.toString());
        groupNotification.setAuthor(userService.getUser());
        String topic = "/topic/course/" + groupNotification.getCourse().getId() + "/" + groupNotification.getType();
        saveAndSendGroupNotification(topic, groupNotification);
    }

    private void saveAndSendGroupNotification(String topic, GroupNotification groupNotification) {
        groupNotificationRepository.save(groupNotification);
        messagingTemplate.convertAndSend(topic, groupNotification);
    }

    public List<Notification> findAllNewNotificationsForCurrentUser(User currentUser) {
        List<String> userGroups = currentUser.getGroups();
        if (userGroups.size() == 0) {
            return new ArrayList<>();
        }
        return this.groupNotificationRepository.findAllNewNotificationsForCurrentUser(userGroups, currentUser.getLastNotificationRead());
    }
}
