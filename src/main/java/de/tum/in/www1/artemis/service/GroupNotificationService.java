package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory.createNotification;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class GroupNotificationService {

    private final GroupNotificationRepository groupNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final UserRepository userRepository;

    private MailService mailService;

    private NotificationSettingsService notificationSettingsService;

    public GroupNotificationService(GroupNotificationRepository groupNotificationRepository, SimpMessageSendingOperations messagingTemplate, UserRepository userRepository,
            MailService mailService, NotificationSettingsService notificationSettingsService) {
        this.groupNotificationRepository = groupNotificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.mailService = mailService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Notify student groups about an attachment change.
     *
     * @param attachment       that has been changed
     * @param notificationText that should be displayed
     */
    public void notifyStudentGroupAboutAttachmentChange(Attachment attachment, String notificationText) {
        // Do not send a notification before the release date of the attachment.
        if (attachment.getReleaseDate() != null && attachment.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }
        // Create and send the notification.
        saveAndSend(createNotification(attachment, userRepository.getUser(), GroupNotificationType.STUDENT, NotificationType.ATTACHMENT_CHANGE, notificationText));
    }

    /**
     * Notify students groups about an exercise opened for practice.
     *
     * @param exercise that has been opened for practice
     */
    public void notifyStudentGroupAboutExercisePractice(Exercise exercise) {
        saveAndSend(createNotification(exercise, userRepository.getUser(), GroupNotificationType.STUDENT, NotificationType.EXERCISE_PRACTICE, null));
    }

    /**
     * Notify student groups about a started quiz exercise. The notification is not sent via websocket.
     *
     * @param quizExercise that has been started
     */
    public void notifyStudentGroupAboutQuizExerciseStart(QuizExercise quizExercise) {
        groupNotificationRepository.save(createNotification(quizExercise, null, GroupNotificationType.STUDENT, NotificationType.QUIZ_EXERCISE_STARTED, null));
    }

    /**
     * Notify student groups about an exercise update.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyStudentGroupAboutExerciseUpdate(Exercise exercise, String notificationText) {
        // Do not send a notification before the release date of the exercise.
        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }
        // Create and send the notification.
        saveAndSend(createNotification(exercise, userRepository.getUser(), GroupNotificationType.STUDENT, NotificationType.EXERCISE_UPDATED, notificationText));
    }

    /**
     * Notify tutor groups about the creation of an exercise.
     *
     * @param exercise that has been created
     */
    public void notifyTutorGroupAboutExerciseCreated(Exercise exercise) {
        saveAndSend(createNotification(exercise, userRepository.getUser(), GroupNotificationType.TA, NotificationType.EXERCISE_CREATED, null));
    }

    /**
     * Notify editor and instructor groups about an exercise update.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyEditorAndInstructorGroupAboutExerciseUpdate(Exercise exercise, String notificationText) {
        saveAndSend(createNotification(exercise, null, GroupNotificationType.EDITOR, NotificationType.EXERCISE_UPDATED, notificationText));
        saveAndSend(createNotification(exercise, null, GroupNotificationType.INSTRUCTOR, NotificationType.EXERCISE_UPDATED, notificationText));
    }

    /**
     * Notify tutor, editor and instructor groups about a new post in an exercise.
     *
     * @param post that has been posted
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewPostForExercise(Post post) {
        saveAndSend(createNotification(post, userRepository.getUser(), GroupNotificationType.TA, NotificationType.NEW_POST_FOR_EXERCISE));
        saveAndSend(createNotification(post, userRepository.getUser(), GroupNotificationType.EDITOR, NotificationType.NEW_POST_FOR_EXERCISE));
        saveAndSend(createNotification(post, userRepository.getUser(), GroupNotificationType.INSTRUCTOR, NotificationType.NEW_POST_FOR_EXERCISE));
    }

    /**
     * Notify editor and instructor groups about duplicate test cases.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(Exercise exercise, String notificationText) {
        saveAndSend(createNotification(exercise, null, GroupNotificationType.EDITOR, NotificationType.DUPLICATE_TEST_CASE, notificationText));
        saveAndSend(createNotification(exercise, null, GroupNotificationType.INSTRUCTOR, NotificationType.DUPLICATE_TEST_CASE, notificationText));
    }

    /**
     * Notify instructor groups about illegal submissions. In case a student has submitted after the individual end date or exam end date,
     * the submission is not valid and therefore marked as illegal. We notify the instructor about this cheating attempt.
     *
     * @param exercise         that has been affected
     * @param notificationText that should be displayed
     */
    public void notifyInstructorGroupAboutIllegalSubmissionsForExercise(Exercise exercise, String notificationText) {
        saveAndSend(createNotification(exercise, null, GroupNotificationType.INSTRUCTOR, NotificationType.ILLEGAL_SUBMISSION, notificationText));
    }

    /**
     * Notify tutor, editor and instructor groups about a new post in a lecture.
     *
     * @param post that has been posted
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewPostForLecture(Post post) {
        saveAndSend(createNotification(post, userRepository.getUser(), GroupNotificationType.TA, NotificationType.NEW_POST_FOR_LECTURE));
        saveAndSend(createNotification(post, userRepository.getUser(), GroupNotificationType.EDITOR, NotificationType.NEW_POST_FOR_LECTURE));
        saveAndSend(createNotification(post, userRepository.getUser(), GroupNotificationType.INSTRUCTOR, NotificationType.NEW_POST_FOR_LECTURE));
    }

    /**
     * Notify tutor, editor and instructor groups about a new answer post for an exercise.
     *
     * @param answerPost that has been submitted for a post
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise(AnswerPost answerPost) {
        saveAndSend(createNotification(answerPost, userRepository.getUser(), GroupNotificationType.TA, NotificationType.NEW_ANSWER_POST_FOR_EXERCISE));
        saveAndSend(createNotification(answerPost, userRepository.getUser(), GroupNotificationType.EDITOR, NotificationType.NEW_ANSWER_POST_FOR_EXERCISE));
        saveAndSend(createNotification(answerPost, userRepository.getUser(), GroupNotificationType.INSTRUCTOR, NotificationType.NEW_ANSWER_POST_FOR_EXERCISE));
    }

    /**
     * Notify tutor, editor and instructor groups about a new answer post for a lecture.
     *
     * @param answerPost that has been submitted for a post
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(AnswerPost answerPost) {
        saveAndSend(createNotification(answerPost, userRepository.getUser(), GroupNotificationType.TA, NotificationType.NEW_ANSWER_POST_FOR_LECTURE));
        saveAndSend(createNotification(answerPost, userRepository.getUser(), GroupNotificationType.EDITOR, NotificationType.NEW_ANSWER_POST_FOR_LECTURE));
        saveAndSend(createNotification(answerPost, userRepository.getUser(), GroupNotificationType.INSTRUCTOR, NotificationType.NEW_ANSWER_POST_FOR_LECTURE));
    }

    /**
     * Notify tutor and instructor groups about a new answer post for a lecture.
     *
     * @param course           The course
     * @param notificationType The state of the archiving process
     * @param archiveErrors    a list of errors that happened during archiving
     */
    public void notifyInstructorGroupAboutCourseArchiveState(Course course, NotificationType notificationType, List<String> archiveErrors) {
        saveAndSend(createNotification(course, null, GroupNotificationType.INSTRUCTOR, notificationType, archiveErrors));
    }

    /**
     * Notify instructor groups about the archive state of the exam.
     *
     * @param exam           The exam
     * @param notificationType The state of the archiving process
     * @param archiveErrors    a list of errors that happened during archiving
     */
    public void notifyInstructorGroupAboutExamArchiveState(Exam exam, NotificationType notificationType, List<String> archiveErrors) {
        saveAndSend(createNotification(exam, null, GroupNotificationType.INSTRUCTOR, notificationType, archiveErrors));
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also starts the process of sending the information contained in the notification via email.
     *
     * @param notification that should be saved and sent
     */
    private void saveAndSend(GroupNotification notification) {
        groupNotificationRepository.save(notification);
        messagingTemplate.convertAndSend(notification.getTopic(), notification);

        boolean hasEmailSupport = notificationSettingsService.checkNotificationTypeForEmailSupport(notification.getOriginalNotificationType());
        if (hasEmailSupport) {
            prepareSendingGroupEmail(notification);
        }
    }

    /**
     * Prepares sending an email based on a GroupNotification by finding the relevant users
     * @param notification which information should also be propagated via email
     */
    private void prepareSendingGroupEmail(GroupNotification notification) {
        Course course = notification.getCourse();
        GroupNotificationType groupType = notification.getType();
        List<User> foundUsers = new ArrayList<>();
        switch (groupType) {
            case STUDENT -> {
                foundUsers = userRepository.getStudents(course);
                break;
            }
            case INSTRUCTOR -> {
                foundUsers = userRepository.getInstructors(course);
                break;
            }
            case EDITOR -> {
                foundUsers = userRepository.getEditors(course);
                break;
            }
            case TA -> {
                foundUsers = userRepository.getTutors(course);
                break;
            }
        }
        prepareGroupNotificationEmail(notification, foundUsers);
    }

    /**
     * Checks if an email should be created based on the provided notification, users, notification settings and type for GroupNotifications
     * If the checks are successful creates and sends a corresponding email
     * If the notification type indicates an urgent (critical) email it will be send to all users (regardless of settings)
     * @param notification that should be checked
     * @param users which will be filtered based on their notification (email) settings
     */
    public void prepareGroupNotificationEmail(GroupNotification notification, List<User> users) {
        boolean isUrgentEmail = notificationSettingsService.checkNotificationTypeForEmailUrgency(notification.getOriginalNotificationType());
        if (isUrgentEmail) {
            mailService.sendNotificationEmail(notification, users);
            return;
        }

        List<User> usersThatShouldReceiveAnEmail = users.stream()
                .filter(user -> notificationSettingsService.checkIfNotificationEmailIsAllowedBySettingsForGivenUser(notification, user)).collect(Collectors.toList());

        if (!usersThatShouldReceiveAnEmail.isEmpty()) {
            mailService.sendNotificationEmail(notification, usersThatShouldReceiveAnEmail);
        }
    }
}
