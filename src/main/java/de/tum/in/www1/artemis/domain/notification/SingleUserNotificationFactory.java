package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;

public class SingleUserNotificationFactory {

    private static final String POST_NOTIFICATION_TEXT = "Your post got replied.";

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param post which is answered
     * @param notificationType type of the notification that should be created
     * @param course that the post belongs to
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Post post, NotificationType notificationType, Course course) {
        User recipient = post.getAuthor();
        String title;
        SingleUserNotification notification;
        switch (notificationType) {
            case NEW_REPLY_FOR_EXERCISE_POST -> {
                title = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTransientAndStringTarget(createExercisePostTarget(post, course));
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                title = NEW_REPLY_FOR_LECTURE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTransientAndStringTarget(createLecturePostTarget(post, course));
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NEW_REPLY_FOR_COURSE_POST_TITLE;
                notification = new SingleUserNotification(recipient, title, POST_NOTIFICATION_TEXT);
                notification.setTransientAndStringTarget(createCoursePostTarget(post, course));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification.
     *
     * @param exercise for which a notification should be created
     * @param notificationType type of the notification that should be created
     * @param recipient who should be notified
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(Exercise exercise, NotificationType notificationType, User recipient) {
        String title;
        String notificationText;
        SingleUserNotification notification;
        switch (notificationType) {
            case FILE_SUBMISSION_SUCCESSFUL -> {
                title = FILE_SUBMISSION_SUCCESSFUL_TITLE;
                notificationText = "Your file for the exercise \"" + exercise.getTitle() + "\" was successfully submitted.";
                notification = new SingleUserNotification(recipient, title, notificationText);
                notification.setTransientAndStringTarget(createExerciseTarget(exercise, title));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification based on plagiarisms.
     *
     * @param plagiarismComparison that hold the major information for the plagiarism case
     * @param notificationType type of the notification that should be created
     * @param student who should be notified or is the author (depends if the student or instructor should be notified)
     * @param instructor who should be notified or is the author
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(PlagiarismComparison plagiarismComparison, NotificationType notificationType, User student, User instructor) {
        String title;
        String notificationText;
        SingleUserNotification notification;
        Long courseId;
        Exercise affectedExercise = plagiarismComparison.getPlagiarismResult().getExercise();

        switch (notificationType) {
            case NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT -> {
                title = NEW_POSSIBLE_PLAGIARISM_CASE_STUDENT_TITLE;
                // pick the correct instructorStatement for the user that should be notified (A or B)
                notificationText = (plagiarismComparison.getSubmissionA().getStudentLogin().equals(student.getLogin())) ? plagiarismComparison.getInstructorStatementA()
                        : plagiarismComparison.getInstructorStatementB();
            }
            case PLAGIARISM_CASE_FINAL_STATE_STUDENT -> {
                title = PLAGIARISM_CASE_FINAL_STATE_STUDENT_TITLE;
                notificationText = "Your plagiarism case concerning the " + affectedExercise.getExerciseType().toString().toLowerCase() + " exercise \""
                        + affectedExercise.getTitle() + "\"" + " has a final verdict.";
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        courseId = affectedExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        notification = new SingleUserNotification(student, title, notificationText);
        notification.setPriority(HIGH);
        notification.setAuthor(instructor);
        notification.setTransientAndStringTarget(createPlagiarismCaseTarget(plagiarismComparison.getId(), courseId));
        return notification;
    }
}
