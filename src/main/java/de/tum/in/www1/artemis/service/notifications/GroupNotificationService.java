package de.tum.in.www1.artemis.service.notifications;

import static de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType.*;
import static de.tum.in.www1.artemis.domain.enumeration.NotificationType.*;
import static de.tum.in.www1.artemis.domain.notification.GroupNotificationFactory.createNotification;
import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.metis.Posting;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.notification.NotificationConstants;
import de.tum.in.www1.artemis.domain.notification.NotificationTarget;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.service.WebsocketMessagingService;

@Service
public class GroupNotificationService {

    private final GroupNotificationRepository groupNotificationRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final UserRepository userRepository;

    private final GeneralInstantNotificationService notificationService;

    private final NotificationSettingsService notificationSettingsService;

    public GroupNotificationService(GroupNotificationRepository groupNotificationRepository, WebsocketMessagingService websocketMessagingService, UserRepository userRepository,
            GeneralInstantNotificationService notificationService, NotificationSettingsService notificationSettingsService) {
        this.groupNotificationRepository = groupNotificationRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.notificationSettingsService = notificationSettingsService;
    }

    /**
     * Checks if a notification has to be created for this exercise update and creates one if the situation is appropriate
     *
     * @param exercise         that is updated
     * @param notificationText that is used for the notification process
     */
    public void notifyAboutExerciseUpdate(Exercise exercise, String notificationText) {
        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            // Do not send an exercise-update notification before the release date of the exercise.
            return;
        }

        if ((notificationText != null && exercise.isCourseExercise()) || exercise.isExamExercise()) {
            // sends an exercise-update notification
            notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(exercise, notificationText);
        }
    }

    /**
     * Auxiliary method to call the correct factory method and start the process to save & sent the notification
     *
     * @param groups                  is an array of GroupNotificationTypes that should be notified (e.g. STUDENTS, INSTRUCTORS)
     * @param notificationType        is the discriminator for the factory
     * @param notificationSubject     is the subject of the notification (e.g. exercise, attachment)
     * @param typeSpecificInformation is based on the current use case (e.g. POST -> course, ARCHIVE -> List<String> archiveErrors)
     * @param author                  is the user who initiated the process of the notifications. Can be null if not specified
     */
    private void notifyGroupsWithNotificationType(GroupNotificationType[] groups, NotificationType notificationType, Object notificationSubject, Object typeSpecificInformation,
            User author) {
        for (GroupNotificationType group : groups) {
            GroupNotification resultingGroupNotification = switch (notificationType) {
                // Post Types
                case NEW_EXERCISE_POST, NEW_LECTURE_POST, NEW_COURSE_POST, NEW_ANNOUNCEMENT_POST -> createNotification((Post) notificationSubject, author, group, notificationType,
                        (Course) typeSpecificInformation);
                // Post Reply Types
                case NEW_REPLY_FOR_EXERCISE_POST, NEW_REPLY_FOR_LECTURE_POST, NEW_REPLY_FOR_COURSE_POST -> createNotification((Post) ((List<Posting>) notificationSubject).get(0),
                        (AnswerPost) ((List<Posting>) notificationSubject).get(1), author, group, notificationType, (Course) typeSpecificInformation);
                // General Types
                case ATTACHMENT_CHANGE -> createNotification((Attachment) notificationSubject, author, group, notificationType, (String) typeSpecificInformation);
                case QUIZ_EXERCISE_STARTED -> createNotification((QuizExercise) notificationSubject, author, group, notificationType, (String) typeSpecificInformation);
                case EXERCISE_UPDATED, EXERCISE_RELEASED, EXERCISE_PRACTICE -> createNotification((Exercise) notificationSubject, author, group, notificationType,
                        (String) typeSpecificInformation);
                // Archive Types
                case COURSE_ARCHIVE_STARTED, COURSE_ARCHIVE_FINISHED, COURSE_ARCHIVE_FAILED -> createNotification((Course) notificationSubject, author, group, notificationType,
                        (List<String>) typeSpecificInformation);
                case EXAM_ARCHIVE_STARTED, EXAM_ARCHIVE_FINISHED, EXAM_ARCHIVE_FAILED -> createNotification((Exam) notificationSubject, author, group, notificationType,
                        (List<String>) typeSpecificInformation);
                // Critical Types
                case DUPLICATE_TEST_CASE, ILLEGAL_SUBMISSION -> createNotification((Exercise) notificationSubject, author, group, notificationType,
                        (String) typeSpecificInformation);
                // Additional Types
                case PROGRAMMING_TEST_CASES_CHANGED, NEW_MANUAL_FEEDBACK_REQUEST -> createNotification((Exercise) notificationSubject, author, group, notificationType,
                        (String) typeSpecificInformation);
                default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
            };
            saveAndSend(resultingGroupNotification, notificationSubject, author);
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
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT }, ATTACHMENT_CHANGE, attachment, notificationText, userRepository.getUser());
    }

    /**
     * Notify students groups about an exercise opened for practice.
     *
     * @param exercise that has been opened for practice
     */
    public void notifyStudentGroupAboutExercisePractice(Exercise exercise) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT }, EXERCISE_PRACTICE, exercise, null, userRepository.getUser());
    }

    /**
     * Notify student groups about a started quiz exercise. The notification is not sent via websocket.
     *
     * @param quizExercise that has been started
     */
    public void notifyStudentGroupAboutQuizExerciseStart(QuizExercise quizExercise) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT }, QUIZ_EXERCISE_STARTED, quizExercise, null, null);
    }

    /**
     * Notify all groups but tutors about an exercise update.
     * Tutors will only work on the exercise during the assessment therefore it is not urgent to inform them about changes beforehand.
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
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT, EDITOR, INSTRUCTOR }, EXERCISE_UPDATED, exercise, notificationText, userRepository.getUser());
    }

    /**
     * Notify all groups about a newly released exercise at the moment of its release date.
     * This notification can be deactivated in the notification settings
     *
     * @param exercise that has been created
     */
    public void notifyAllGroupsAboutReleasedExercise(Exercise exercise) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT, TA, EDITOR, INSTRUCTOR }, EXERCISE_RELEASED, exercise, null, null);
    }

    /**
     * Notify editor and instructor groups about an exercise update.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyEditorAndInstructorGroupAboutExerciseUpdate(Exercise exercise, String notificationText) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { EDITOR, INSTRUCTOR }, EXERCISE_UPDATED, exercise, notificationText, null);
    }

    /**
     * Notify editor and instructor groups about changed test cases for a programming exercise.
     *
     * @param exercise that has been updated
     */
    public void notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(ProgrammingExercise exercise) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { EDITOR, INSTRUCTOR }, PROGRAMMING_TEST_CASES_CHANGED, exercise, null, null);
    }

    /**
     * Notify all groups about a new post in an exercise.
     *
     * @param post   that has been posted
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewPostForExercise(Post post, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT, TA, EDITOR, INSTRUCTOR }, NEW_EXERCISE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify editor and instructor groups about duplicate test cases.
     *
     * @param exercise         that has been updated
     * @param notificationText that should be displayed
     */
    public void notifyEditorAndInstructorGroupAboutDuplicateTestCasesForExercise(Exercise exercise, String notificationText) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { EDITOR, INSTRUCTOR }, DUPLICATE_TEST_CASE, exercise, notificationText, null);
    }

    /**
     * Notify instructor groups about illegal submissions. In case a student has submitted after the individual end date or exam end date,
     * the submission is not valid and therefore marked as illegal. We notify the instructor about this cheating attempt.
     *
     * @param exercise         that has been affected
     * @param notificationText that should be displayed
     */
    public void notifyInstructorGroupAboutIllegalSubmissionsForExercise(Exercise exercise, String notificationText) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { INSTRUCTOR }, ILLEGAL_SUBMISSION, exercise, notificationText, null);
    }

    /**
     * Notify all groups about a new post in a lecture.
     *
     * @param post   that has been posted
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewPostForLecture(Post post, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT, TA, EDITOR, INSTRUCTOR }, NEW_LECTURE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify all groups about a new course-wide post.
     *
     * @param post   that has been posted
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewCoursePost(Post post, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT, TA, EDITOR, INSTRUCTOR }, NEW_COURSE_POST, post, course, post.getAuthor());
    }

    /**
     * Notify tutor, editor and instructor groups about a new reply post for an exercise.
     *
     * @param post       that has been answered
     * @param answerPost that has been created
     * @param course     that the post belongs to
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewReplyForCoursePost(Post post, AnswerPost answerPost, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { TA, EDITOR, INSTRUCTOR }, NEW_REPLY_FOR_COURSE_POST, Arrays.asList(post, answerPost), course,
                answerPost.getAuthor());
    }

    /**
     * Notify tutor, editor and instructor groups about a new reply post for an exercise.
     *
     * @param post       that has been answered
     * @param answerPost that has been created
     * @param course     that the post belongs to
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewReplyForExercise(Post post, AnswerPost answerPost, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { TA, EDITOR, INSTRUCTOR }, NEW_REPLY_FOR_EXERCISE_POST, Arrays.asList(post, answerPost), course,
                answerPost.getAuthor());
    }

    public void notifyTutorGroupAboutNewFeedbackRequest(Exercise exercise) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { TA }, NEW_MANUAL_FEEDBACK_REQUEST, exercise, null, null);
    }

    /**
     * Notify all groups about a new announcement in the course.
     *
     * @param post   that has been created as announcement
     * @param course that the post belongs to
     */
    public void notifyAllGroupsAboutNewAnnouncement(Post post, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { STUDENT, TA, EDITOR, INSTRUCTOR }, NEW_ANNOUNCEMENT_POST, post, course, post.getAuthor());
    }

    /**
     * Notify tutor, editor and instructor groups about a new answer post for a lecture.
     *
     * @param post       that has been answered
     * @param answerPost that has been created
     * @param course     that the post belongs to
     */
    public void notifyTutorAndEditorAndInstructorGroupAboutNewAnswerForLecture(Post post, AnswerPost answerPost, Course course) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { TA, EDITOR, INSTRUCTOR }, NEW_REPLY_FOR_LECTURE_POST, Arrays.asList(post, answerPost), course,
                answerPost.getAuthor());
    }

    /**
     * Notify tutor and instructor groups about course archival state.
     *
     * @param course           course that is archived
     * @param notificationType state of the archiving process
     * @param archiveErrors    list of errors that happened during archiving
     */
    public void notifyInstructorGroupAboutCourseArchiveState(Course course, NotificationType notificationType, List<String> archiveErrors) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { INSTRUCTOR }, notificationType, course, archiveErrors, null);
    }

    /**
     * Notify instructor groups about the archive state of the exam.
     *
     * @param exam             exam that is archived
     * @param notificationType state of the archiving process
     * @param archiveErrors    list of errors that happened during archiving
     */
    public void notifyInstructorGroupAboutExamArchiveState(Exam exam, NotificationType notificationType, List<String> archiveErrors) {
        notifyGroupsWithNotificationType(new GroupNotificationType[] { INSTRUCTOR }, notificationType, exam, archiveErrors, null);
    }

    /**
     * Saves the given notification in database and sends it to the client via websocket.
     * Also starts the process of sending the information contained in the notification via email.
     *
     * @param notification        that should be saved and sent
     * @param notificationSubject which information will be extracted to create the email
     * @param author              the author, if set, will not be notified via instant notification.
     */
    private void saveAndSend(GroupNotification notification, Object notificationSubject, User author) {
        if (LIVE_EXAM_EXERCISE_UPDATE_NOTIFICATION_TITLE.equals(notification.getTitle())) {
            saveExamNotification(notification);
            websocketMessagingService.sendMessage(notification.getTopic(), notification);
            return;
        }

        groupNotificationRepository.save(notification);
        websocketMessagingService.sendMessage(notification.getTopic(), notification);

        NotificationType type = NotificationConstants.findCorrespondingNotificationType(notification.getTitle());

        // checks if this notification type has email support
        if (notificationSettingsService.checkNotificationTypeForInstantNotificationSupport(type)) {
            Set<User> groupNotificationReceivers = findGroupNotificationReceivers(notification, author);

            if (!groupNotificationReceivers.isEmpty()) {
                notificationService.sendNotification(notification, groupNotificationReceivers, notificationSubject);
            }
        }
    }

    /**
     * Saves an exam notification by removing the problem statement message
     *
     * @param notification that should be saved (without the problem statement)
     */
    private void saveExamNotification(GroupNotification notification) {
        String originalTarget = notification.getTarget();
        NotificationTarget targetWithoutProblemStatement = notification.getTargetTransient();
        targetWithoutProblemStatement.setProblemStatement(null);
        notification.setTarget(targetWithoutProblemStatement.toJsonString());
        groupNotificationRepository.save(notification);
        notification.setTarget(originalTarget);
    }

    /**
     * Prepares sending an instant notification based on a GroupNotification by finding the relevant users
     *
     * @param notification which information should also be propagated via email
     * @param author       the author will be excluded if not null
     */
    private Set<User> findGroupNotificationReceivers(GroupNotification notification, User author) {
        Course course = notification.getCourse();
        GroupNotificationType groupType = notification.getType();
        Set<User> foundUsers;
        switch (groupType) {
            case STUDENT -> foundUsers = userRepository.getStudents(course);
            case INSTRUCTOR -> foundUsers = userRepository.getInstructors(course);
            case EDITOR -> foundUsers = userRepository.getEditors(course);
            case TA -> foundUsers = userRepository.getTutors(course);
            default -> foundUsers = Collections.emptySet();
        }

        if (author == null) {
            return foundUsers;
        }
        else {
            return foundUsers.stream().filter((user) -> !Objects.equals(user.getId(), author.getId())).collect(Collectors.toSet());
        }
    }
}
