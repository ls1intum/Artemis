package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private GroupNotification createExerciseCreatedGroupNotificationForTutors(Exercise exercise) {
        String title = "Exercise created";
        String notificationText = "A new exercise \"" + exercise.getTitle() + "\" got created.";
        User user = userService.getUser();
        GroupNotificationType type = GroupNotificationType.TA;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), title, notificationText, user, type);
        groupNotification.setTarget(groupNotification.getExerciseCreatedTarget(exercise));
        return groupNotification;
    }

    private GroupNotification createExerciseUpdatedGroupNotificationForStudents(Exercise exercise, String title, String notificationText) {
        User user = userService.getUser();
        GroupNotificationType type = GroupNotificationType.STUDENT;
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), title, notificationText, user, type);
        groupNotification.setTarget(groupNotification.getExerciseUpdatedTarget(exercise));
        return groupNotification;
    }

    private GroupNotification createExerciseQuestionCreatedGroupNotification(StudentQuestion studentQuestion, GroupNotificationType type) {
        Exercise exercise = studentQuestion.getExercise();
        String title = "New Question";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" got a new question.";
        User user = userService.getUser();
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), title, notificationText, user, type);
        groupNotification.setTarget(groupNotification.getExerciseQuestionTarget(exercise));
        return groupNotification;
    }

    private GroupNotification createLectureQuestionCreatedGroupNotification(StudentQuestion studentQuestion, GroupNotificationType type) {
        Lecture lecture = studentQuestion.getLecture();
        String title = "New Question";
        String notificationText = "Lecture \"" + lecture.getTitle() + "\" got a new question.";
        User user = userService.getUser();
        GroupNotification groupNotification = new GroupNotification(lecture.getCourse(), title, notificationText, user, type);
        groupNotification.setTarget(groupNotification.getLectureQuestionTarget(lecture));
        return groupNotification;
    }

    private GroupNotification createExerciseAnswerCreatedGroupNotification(StudentQuestionAnswer studentQuestionAnswer, GroupNotificationType type) {
        Exercise exercise = studentQuestionAnswer.getQuestion().getExercise();
        String title = "New Answer";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" got a new answer.";
        User user = userService.getUser();
        GroupNotification groupNotification = new GroupNotification(exercise.getCourse(), title, notificationText, user, type);
        groupNotification.setTarget(groupNotification.getExerciseAnswerTarget(exercise));
        return groupNotification;
    }

    private GroupNotification createLectureAnswerCreatedGroupNotification(StudentQuestionAnswer studentQuestionAnswer, GroupNotificationType type) {
        Lecture lecture = studentQuestionAnswer.getQuestion().getLecture();
        String title = "New Answer";
        String notificationText = "Lecture \"" + lecture.getTitle() + "\" got a new answer.";
        User user = userService.getUser();
        GroupNotification groupNotification = new GroupNotification(lecture.getCourse(), title, notificationText, user, type);
        groupNotification.setTarget(groupNotification.getLectureAnswerTarget(lecture));
        return groupNotification;
    }

    private GroupNotification createAttachmentUpdatedGroupNotification(Attachment attachment, String notificationText) {
        Course course = attachment.getLecture().getCourse();
        String title = "Attachment " + attachment.getName() + " updated";
        User user = userService.getUser();
        GroupNotificationType type = GroupNotificationType.STUDENT;
        GroupNotification groupNotification = new GroupNotification(course, title, notificationText, user, type);
        groupNotification.setTarget(groupNotification.getAttachmentUpdated(attachment.getLecture()));
        return groupNotification;
    }

    public void notifyStudentGroupAboutExerciseStart(Exercise exercise) {
        String title = "Exercise started";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" just started.";
        notifyStudentGroupAboutExerciseChange(exercise, title, notificationText);
    }

    public void notifyStudentGroupAboutExerciseVisibility(Exercise exercise) {
        String title = "New exercise available";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" is now available.";
        notifyStudentGroupAboutExerciseChange(exercise, title, notificationText);
    }

    public void notifyStudentGroupAboutExercisePractice(Exercise exercise) {
        String title = "Exercise open for practice";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" is now open for practice.";
        notifyStudentGroupAboutExerciseChange(exercise, title, notificationText);
    }

    public void notifyStudentGroupAboutExerciseUpdate(Exercise exercise) {
        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }
        String title = "Exercise updated";
        String notificationText = "Exercise \"" + exercise.getTitle() + "\" was updated.";
        notifyStudentGroupAboutExerciseChange(exercise, title, notificationText);
    }

    private void notifyStudentGroupAboutExerciseChange(Exercise exercise, String title, String notificationText) {
        GroupNotification groupNotification = createExerciseUpdatedGroupNotificationForStudents(exercise, title, notificationText);
        saveAndSendGroupNotification(groupNotification);
    }

    public void notifyTutorGroupAboutExerciseCreated(Exercise exercise) {
        GroupNotification groupNotification = createExerciseCreatedGroupNotificationForTutors(exercise);
        saveAndSendGroupNotification(groupNotification);
    }

    public void notifyTutorAndInstructorGroupAboutNewQuestionForExercise(StudentQuestion studentQuestion) {
        GroupNotification tutorNotification = createExerciseQuestionCreatedGroupNotification(studentQuestion, GroupNotificationType.TA);
        GroupNotification instructorNotification = createExerciseQuestionCreatedGroupNotification(studentQuestion, GroupNotificationType.INSTRUCTOR);
        saveAndSendGroupNotification(tutorNotification);
        saveAndSendGroupNotification(instructorNotification);
    }

    public void notifyTutorAndInstructorGroupAboutNewQuestionForLecture(StudentQuestion studentQuestion) {
        GroupNotification tutorNotification = createLectureQuestionCreatedGroupNotification(studentQuestion, GroupNotificationType.TA);
        GroupNotification instructorNotification = createLectureQuestionCreatedGroupNotification(studentQuestion, GroupNotificationType.INSTRUCTOR);
        saveAndSendGroupNotification(tutorNotification);
        saveAndSendGroupNotification(instructorNotification);
    }

    public void notifyTutorAndInstructorGroupAboutNewAnswerForExercise(StudentQuestionAnswer studentQuestionAnswer) {
        GroupNotification tutorNotification = createExerciseAnswerCreatedGroupNotification(studentQuestionAnswer, GroupNotificationType.TA);
        GroupNotification instructorNotification = createExerciseAnswerCreatedGroupNotification(studentQuestionAnswer, GroupNotificationType.INSTRUCTOR);
        saveAndSendGroupNotification(tutorNotification);
        saveAndSendGroupNotification(instructorNotification);
    }

    public void notifyTutorAndInstructorGroupAboutNewAnswerForLecture(StudentQuestionAnswer studentQuestionAnswer) {
        GroupNotification tutorNotification = createLectureAnswerCreatedGroupNotification(studentQuestionAnswer, GroupNotificationType.TA);
        GroupNotification instructorNotification = createLectureAnswerCreatedGroupNotification(studentQuestionAnswer, GroupNotificationType.INSTRUCTOR);
        saveAndSendGroupNotification(tutorNotification);
        saveAndSendGroupNotification(instructorNotification);
    }

    public void notifyStudentGroupAboutAttachmentChange(Attachment attachment, String notificationText) {
        if (attachment.getReleaseDate() != null && !attachment.getReleaseDate().isBefore(ZonedDateTime.now())) {
            return;
        }
        GroupNotification groupNotification = createAttachmentUpdatedGroupNotification(attachment, notificationText);
        saveAndSendGroupNotification(groupNotification);
    }

    private void saveAndSendGroupNotification(GroupNotification groupNotification) {
        groupNotificationRepository.save(groupNotification);
        messagingTemplate.convertAndSend(groupNotification.getTopic(), groupNotification);
    }

    public List<Notification> findAllRecentNewNotificationsForCurrentUser(User currentUser) {
        List<String> userGroups = currentUser.getGroups();
        if (userGroups.size() == 0) {
            return new ArrayList<>();
        }
        return this.groupNotificationRepository.findAllRecentNewNotificationsForCurrentUser(userGroups, currentUser.getLastNotificationRead());
    }
}
