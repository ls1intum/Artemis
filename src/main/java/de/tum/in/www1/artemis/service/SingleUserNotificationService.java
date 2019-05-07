package de.tum.in.www1.artemis.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
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

    private SingleUserNotification createUserNotificationForNewAnswer(StudentQuestionAnswer studentQuestionAnswer) {
        User recipient = studentQuestionAnswer.getQuestion().getAuthor();
        User author = studentQuestionAnswer.getAuthor();
        String title = "New Answer";
        String text = "Your Question got answered!";
        return new SingleUserNotification(recipient, author, title, text);
    }

    public SingleUserNotification createTutorNotificationForConflictingResult(Result conflictingResult) {
        User recipient = conflictingResult.getAssessor();
        User author = conflictingResult.getAssessor();
        String title = "New Conflict";
        String text = "A coflict with one of your assessments has been escalated!";
        return new SingleUserNotification(recipient, author, title, text);
    }

    public void notifyUserAboutNewAnswerForExercise(StudentQuestionAnswer studentQuestionAnswer) {
        SingleUserNotification userNotification = createUserNotificationForNewAnswer(studentQuestionAnswer);
        userNotification.setTarget(userNotification.studentQuestionAnswerTargetForExercise(studentQuestionAnswer));
        saveAndSendSingleUserNotification(userNotification);
    }

    public void notifyUserAboutNewAnswerForLecture(StudentQuestionAnswer studentQuestionAnswer) {
        SingleUserNotification userNotification = createUserNotificationForNewAnswer(studentQuestionAnswer);
        userNotification.setTarget(userNotification.studentQuestionAnswerTargetForLecture(studentQuestionAnswer));
        saveAndSendSingleUserNotification(userNotification);
    }

    public void notifyTutorAboutNewConflictForResult(Result conflictingResult) {
        SingleUserNotification userNotification = createTutorNotificationForConflictingResult(conflictingResult);
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
