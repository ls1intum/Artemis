package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;

@Service
public class GroupNotificationService {

    private final GroupNotificationRepository groupNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final UserService userService;

    public GroupNotificationService(GroupNotificationRepository groupNotificationRepository, SimpMessageSendingOperations messagingTemplate, UserService userService) {
        this.groupNotificationRepository = groupNotificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
    }

    /**
     * Notify student groups about an attachment change.
     *
     * @param attachment that has been changed
     * @param notificationText that should be displayed  TODO: can be removed in the future as notification's text attribute is not used in the client
     */
    public void notifyStudentGroupAboutAttachmentChange(Attachment attachment, String notificationText) {
        // Do not send a notification before the release date of the attachment.
        if (attachment.getReleaseDate() != null && attachment.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }
        // Create and send the notification.
        saveAndSend(GroupNotificationFactory.createNotification(attachment, userService.getUser(), GroupNotificationType.STUDENT, NotificationType.ATTACHMENT_CHANGE));
    }

    /**
     * Notify students groups about an exercise opened for practice.
     *
     * @param exercise that has been opened for practice
     */
    public void notifyStudentGroupAboutExercisePractice(Exercise exercise) {
        saveAndSend(GroupNotificationFactory.createNotification(exercise, userService.getUser(), GroupNotificationType.STUDENT, NotificationType.EXERCISE_PRACTICE));
    }

    /**
     * Notify student groups about an exercise started.
     *
     * @param exercise that has been started
     */
    public void notifyStudentGroupAboutExerciseStart(Exercise exercise) {
        saveAndSend(GroupNotificationFactory.createNotification(exercise, userService.getUser(), GroupNotificationType.STUDENT, NotificationType.EXERCISE_STARTED));
    }

    /**
     * Notify student groups about an exercise update.
     *
     * @param exercise that has been updated
     * @param notificationText that should be displayed  TODO: can be removed in the future as notification's text attribute is not used in the client
     */
    public void notifyStudentGroupAboutExerciseUpdate(Exercise exercise, String notificationText) {
        // Do not send a notification before the release date of the exercise.
        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }
        // Create and send the notification.
        saveAndSend(GroupNotificationFactory.createNotification(exercise, userService.getUser(), GroupNotificationType.STUDENT, NotificationType.EXERCISE_UPDATED));
    }

    /**
     * Notify tutor groups about the creation of an exercise.
     *
     * @param exercise that has been created
     */
    public void notifyTutorGroupAboutExerciseCreated(Exercise exercise) {
        saveAndSend(GroupNotificationFactory.createNotification(exercise, userService.getUser(), GroupNotificationType.TA, NotificationType.EXERCISE_CREATED));
    }

    /**
     * Notify instructor groups about an exercise update.
     *
     * @param exercise that has been updated
     * @param notificationText that should be displayed  TODO: can be removed in the future as notification's text attribute is not used in the client
     */
    public void notifyInstructorGroupAboutExerciseUpdate(Exercise exercise, String notificationText) {
        saveAndSend(GroupNotificationFactory.createNotification(exercise, userService.getUser(), GroupNotificationType.INSTRUCTOR, NotificationType.EXERCISE_UPDATED));
    }

    /**
     * Notify tutor and instructor groups about a new question in an exercise.
     *
     * @param studentQuestion that has been posted
     */
    public void notifyTutorAndInstructorGroupAboutNewQuestionForExercise(StudentQuestion studentQuestion) {
        saveAndSend(GroupNotificationFactory.createNotification(studentQuestion, userService.getUser(), GroupNotificationType.TA, NotificationType.NEW_QUESTION_FOR_EXERCISE));
        saveAndSend(
                GroupNotificationFactory.createNotification(studentQuestion, userService.getUser(), GroupNotificationType.INSTRUCTOR, NotificationType.NEW_QUESTION_FOR_EXERCISE));
    }

    /**
     * Notify tutor and instructor groups about a new question in a lecture.
     *
     * @param studentQuestion that has been posted
     */
    public void notifyTutorAndInstructorGroupAboutNewQuestionForLecture(StudentQuestion studentQuestion) {
        saveAndSend(GroupNotificationFactory.createNotification(studentQuestion, userService.getUser(), GroupNotificationType.TA, NotificationType.NEW_QUESTION_FOR_LECTURE));
        saveAndSend(
                GroupNotificationFactory.createNotification(studentQuestion, userService.getUser(), GroupNotificationType.INSTRUCTOR, NotificationType.NEW_QUESTION_FOR_LECTURE));
    }

    /**
     * Notify tutor and instructor groups about a new answer for an exercise.
     *
     * @param studentQuestionAnswer that has been submitted for a question
     */
    public void notifyTutorAndInstructorGroupAboutNewAnswerForExercise(StudentQuestionAnswer studentQuestionAnswer) {
        saveAndSend(GroupNotificationFactory.createNotification(studentQuestionAnswer, userService.getUser(), GroupNotificationType.TA, NotificationType.NEW_ANSWER_FOR_EXERCISE));
        saveAndSend(GroupNotificationFactory.createNotification(studentQuestionAnswer, userService.getUser(), GroupNotificationType.INSTRUCTOR,
                NotificationType.NEW_ANSWER_FOR_EXERCISE));
    }

    /**
     * Notify tutor and instructor groups about a new answer for a lecture.
     *
     * @param studentQuestionAnswer that has been submitted for a question
     */
    public void notifyTutorAndInstructorGroupAboutNewAnswerForLecture(StudentQuestionAnswer studentQuestionAnswer) {
        saveAndSend(GroupNotificationFactory.createNotification(studentQuestionAnswer, userService.getUser(), GroupNotificationType.TA, NotificationType.NEW_ANSWER_FOR_LECTURE));
        saveAndSend(GroupNotificationFactory.createNotification(studentQuestionAnswer, userService.getUser(), GroupNotificationType.INSTRUCTOR,
                NotificationType.NEW_ANSWER_FOR_LECTURE));
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     *
     * @param notification that should be saved and sent
     */
    private void saveAndSend(GroupNotification notification) {
        groupNotificationRepository.save(notification);
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
    }
}
