package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory.createNotification;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.ExamNotificationTargetWithoutProblemStatement;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;

@Service
public class GroupNotificationService {

    private final GroupNotificationRepository groupNotificationRepository;

    private final SimpMessageSendingOperations messagingTemplate;

    private final UserRepository userRepository;

    public GroupNotificationService(GroupNotificationRepository groupNotificationRepository, SimpMessageSendingOperations messagingTemplate, UserRepository userRepository) {
        this.groupNotificationRepository = groupNotificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     * @param groups is an array of GroupNotificationTypes that should be notified (e.g. STUDENTS, INSTRUCTORS)
     * @param notificationType is the discriminator for the factory
     * @param notificationSubject is the subject of the notification (e.g. exercise, attachment)
     * @param typeSpecificInformation is based on the current use case (e.g. POST -> course, ARCHIVE -> List<String> archiveErrors)
     */
    public void notifyGroupsWithNotificationType(GroupNotificationType[] groups, NotificationType notificationType, Object notificationSubject, Object typeSpecificInformation,
            User author) {
        for (GroupNotificationType group : groups) {
            GroupNotification resultingGroupNotification;
            resultingGroupNotification = switch (notificationType) {
                // Post Types
                case NEW_EXERCISE_POST -> createNotification((Post) notificationSubject, author, group, NotificationType.NEW_EXERCISE_POST, (Course) typeSpecificInformation);
                case NEW_REPLY_FOR_EXERCISE_POST -> createNotification((Post) notificationSubject, author, group, NotificationType.NEW_REPLY_FOR_EXERCISE_POST,
                        (Course) typeSpecificInformation);
                case NEW_LECTURE_POST -> createNotification((Post) notificationSubject, author, group, NotificationType.NEW_LECTURE_POST, (Course) typeSpecificInformation);
                case NEW_REPLY_FOR_LECTURE_POST -> createNotification((Post) notificationSubject, author, group, NotificationType.NEW_REPLY_FOR_LECTURE_POST,
                        (Course) typeSpecificInformation);
                case NEW_COURSE_POST -> createNotification((Post) notificationSubject, author, group, NotificationType.NEW_COURSE_POST, (Course) typeSpecificInformation);
                case NEW_REPLY_FOR_COURSE_POST -> createNotification((Post) notificationSubject, author, group, NotificationType.NEW_REPLY_FOR_COURSE_POST,
                        (Course) typeSpecificInformation);
                case NEW_ANNOUNCEMENT_POST -> createNotification((Post) notificationSubject, author, group, NotificationType.NEW_ANNOUNCEMENT_POST,
                        (Course) typeSpecificInformation);
                // General Types
                case ATTACHMENT_CHANGE -> createNotification((Attachment) notificationSubject, author, group, NotificationType.ATTACHMENT_CHANGE, (String) typeSpecificInformation);
                case EXERCISE_PRACTICE -> createNotification((Exercise) notificationSubject, author, group, NotificationType.EXERCISE_PRACTICE, (String) typeSpecificInformation);
                case QUIZ_EXERCISE_STARTED -> createNotification((QuizExercise) notificationSubject, author, group, NotificationType.QUIZ_EXERCISE_STARTED,
                        (String) typeSpecificInformation);
                case EXERCISE_UPDATED -> createNotification((Exercise) notificationSubject, author, group, NotificationType.EXERCISE_UPDATED, (String) typeSpecificInformation);
                case EXERCISE_CREATED -> createNotification((Exercise) notificationSubject, author, group, NotificationType.EXERCISE_CREATED, (String) typeSpecificInformation);
                // Archive Types
                case COURSE_ARCHIVE_STARTED -> createNotification((Course) notificationSubject, author, group, NotificationType.COURSE_ARCHIVE_STARTED,
                        (List<String>) typeSpecificInformation);
                case COURSE_ARCHIVE_FINISHED -> createNotification((Course) notificationSubject, author, group, NotificationType.COURSE_ARCHIVE_FINISHED,
                        (List<String>) typeSpecificInformation);
                case COURSE_ARCHIVE_FAILED -> createNotification((Course) notificationSubject, author, group, NotificationType.COURSE_ARCHIVE_FAILED,
                        (List<String>) typeSpecificInformation);
                case EXAM_ARCHIVE_STARTED -> createNotification((Exam) notificationSubject, author, group, NotificationType.EXAM_ARCHIVE_STARTED,
                        (List<String>) typeSpecificInformation);
                case EXAM_ARCHIVE_FINISHED -> createNotification((Exam) notificationSubject, author, group, NotificationType.EXAM_ARCHIVE_FINISHED,
                        (List<String>) typeSpecificInformation);
                case EXAM_ARCHIVE_FAILED -> createNotification((Exam) notificationSubject, author, group, NotificationType.EXAM_ARCHIVE_FAILED,
                        (List<String>) typeSpecificInformation);
                // Critical Types
                case DUPLICATE_TEST_CASE -> createNotification((Exercise) notificationSubject, author, group, NotificationType.DUPLICATE_TEST_CASE,
                        (String) typeSpecificInformation);
                case ILLEGAL_SUBMISSION -> createNotification((Exercise) notificationSubject, author, group, NotificationType.ILLEGAL_SUBMISSION, (String) typeSpecificInformation);
            };
            saveAndSend(resultingGroupNotification);
        }
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
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.STUDENT }, NotificationType.ATTACHMENT_CHANGE, attachment, notificationText,
                userRepository.getUser());
    }

    /**
     * Notify students groups about an exercise opened for practice.
     *
     * @param exercise that has been opened for practice
     */
    public void notifyStudentGroupAboutExercisePractice(Exercise exercise) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.STUDENT }, NotificationType.EXERCISE_PRACTICE, exercise, null,
                userRepository.getUser());
    }

    /**
     * Notify student groups about a started quiz exercise. The notification is not sent via websocket.
     *
     * @param quizExercise that has been started
     */
    public void notifyStudentGroupAboutQuizExerciseStart(QuizExercise quizExercise) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.STUDENT }, NotificationType.QUIZ_EXERCISE_STARTED, quizExercise, null, null);
    }

    /**
     * Notify all groups but tutors about an exercise update.
     * Tutors will only work on the exercise during the assesment therefore it is not urgent to inform them about changes beforehand.
     * Students, instructors, and editors should be notified about changed as quickly as possible.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(Exercise exercise, String notificationText) {
        // Do not send a notification before the release date of the exercise.
        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            return;
        }
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.STUDENT, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.EXERCISE_UPDATED, exercise, notificationText, userRepository.getUser());
    }

    /**
     * Notify student and tutor groups about the creation/start of an exercise at the moment of its release date.
     *
     * @param exercise that has been created
     */
    public void notifyStudentAndTutorGroupAboutStartedExercise(Exercise exercise) {
        // only send notification if ReleaseDate is now (i.e. in the range [now-2 minutes, now]) (due to possible delays in scheduling)
        if (!exercise.getReleaseDate().isBefore(ZonedDateTime.now().minusMinutes(2)) && !exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.STUDENT, GroupNotificationType.TA }, NotificationType.EXERCISE_CREATED, exercise,
                    null, null);
        }
    }

    /**
     * Notify editor and instructor groups about an exercise update.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyEditorAndInstructorGroupAboutExerciseUpdate(Exercise exercise, String notificationText) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR }, NotificationType.EXERCISE_UPDATED,
                exercise, notificationText, userRepository.getUser());
    }

    /**
     * Notify all groups about a new post in an exercise.
     *
     * @param post that has been posted
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewPostForExercise(Post post, Course course) {
        notifyGroupsWithNotificationType(
                new GroupNotificationType[] { GroupNotificationType.STUDENT, GroupNotificationType.TA, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.NEW_EXERCISE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify editor and instructor groups about duplicate test cases.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(Exercise exercise, String notificationText) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR }, NotificationType.DUPLICATE_TEST_CASE,
                exercise, notificationText, null);
    }

    /**
     * Notify instructor groups about illegal submissions. In case a student has submitted after the individual end date or exam end date,
     * the submission is not valid and therefore marked as illegal. We notify the instructor about this cheating attempt.
     *
     * @param exercise         that has been affected
     * @param notificationText that should be displayed
     */
    public void notifyInstructorGroupAboutIllegalSubmissionsForExercise(Exercise exercise, String notificationText) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.INSTRUCTOR }, NotificationType.ILLEGAL_SUBMISSION, exercise, notificationText, null);
    }

    /**
     * Notify all groups about a new post in a lecture.
     *
     * @param post that has been posted
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewPostForLecture(Post post, Course course) {
        notifyGroupsWithNotificationType(
                new GroupNotificationType[] { GroupNotificationType.STUDENT, GroupNotificationType.TA, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.NEW_LECTURE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify all groups about a new course-wide post.
     *
     * @param post that has been posted
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewCoursePost(Post post, Course course) {
        notifyGroupsWithNotificationType(
                new GroupNotificationType[] { GroupNotificationType.STUDENT, GroupNotificationType.TA, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.NEW_COURSE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify tutor, editor and instructor groups about a new answer post for an exercise.
     *
     * @param post that has been answered
     * @param course that the post belongs to
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForCoursePost(Post post, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.TA, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.NEW_REPLY_FOR_COURSE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify tutor, editor and instructor groups about a new answer post for an exercise.
     *
     * @param post that has been answered
     * @param course that the post belongs to
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForExercise(Post post, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.TA, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.NEW_REPLY_FOR_EXERCISE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify all groups about a new announcement in the course.
     *
     * @param post that has been created as announcement
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewAnnouncement(Post post, Course course) {
        notifyGroupsWithNotificationType(
                new GroupNotificationType[] { GroupNotificationType.STUDENT, GroupNotificationType.TA, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.NEW_ANNOUNCEMENT_POST, post, course, post.getAuthor());
    }

    /**
     * Notify tutor, editor and instructor groups about a new answer post for a lecture.
     *
     * @param post that has been answered
     * @param course that the post belongs to
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(Post post, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.TA, GroupNotificationType.EDITOR, GroupNotificationType.INSTRUCTOR },
                NotificationType.NEW_REPLY_FOR_LECTURE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify tutor and instructor groups about a new answer post for a lecture.
     *
     * @param course           course the answered post belongs to
     * @param notificationType state of the archiving process
     * @param archiveErrors    list of errors that happened during archiving
     */
    public void notifyInstructorGroupAboutCourseArchiveState(Course course, NotificationType notificationType, List<String> archiveErrors) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.INSTRUCTOR }, notificationType, course, archiveErrors, null);
    }

    /**
     * Notify instructor groups about the archive state of the exam.
     *
     * @param exam             exam that is archived
     * @param notificationType state of the archiving process
     * @param archiveErrors    list of errors that happened during archiving
     */
    public void notifyInstructorGroupAboutExamArchiveState(Exam exam, NotificationType notificationType, List<String> archiveErrors) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { GroupNotificationType.INSTRUCTOR }, notificationType, exam, archiveErrors, null);
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     *
     * @param notification that should be saved and sent
     */
    private void saveAndSend(GroupNotification notification) {
        if (NotificationTitleTypeConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(notification.getTitle())) {
            saveExamNotification(notification);
        }
        else {
            groupNotificationRepository.save(notification);
        }
        messagingTemplate.convertAndSend(notification.getTopic(), notification);
    }

    /**
     * Saves an exam notification by removing the problem statement message
     * @param notification that should be saved (without the problem statement)
     */
    private void saveExamNotification(GroupNotification notification) {
        String originalTarget = notification.getTarget();
        String targetWithoutProblemStatement = ExamNotificationTargetWithoutProblemStatement.getTargetWithoutProblemStatement(notification.getTarget());
        notification.setTarget(targetWithoutProblemStatement);
        groupNotificationRepository.save(notification);
        notification.setTarget(originalTarget);
    }
}
