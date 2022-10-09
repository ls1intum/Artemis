package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.enumeration.NotificationPriority.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTitleTypeConstants.*;

import java.util.Objects;

import org.springframework.util.StringUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

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
            case EXERCISE_SUBMISSION_ASSESSED -> {
                title = EXERCISE_SUBMISSION_ASSESSED_TITLE;
                notificationText = "Your submission for the " + exercise.getExerciseType().getExerciseTypeAsReadableString() + " exercise \"" + exercise.getTitle()
                        + "\" has been assessed.";
            }
            case FILE_SUBMISSION_SUCCESSFUL -> {
                title = FILE_SUBMISSION_SUCCESSFUL_TITLE;
                notificationText = "Your file for the exercise \"" + exercise.getTitle() + "\" was successfully submitted.";
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }
        notification = new SingleUserNotification(recipient, title, notificationText);
        notification.setTransientAndStringTarget(createExerciseTarget(exercise, title));
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification based on plagiarisms.
     *
     * @param plagiarismCase that hold the major information for the plagiarism case
     * @param notificationType type of the notification that should be created
     * @param student who should be notified or is the author (depends if the student or instructor should be notified)
     * @param instructor who should be notified or is the author
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(PlagiarismCase plagiarismCase, NotificationType notificationType, User student, User instructor) {
        String title;
        String notificationText;
        SingleUserNotification notification;
        Long courseId;
        Exercise affectedExercise = plagiarismCase.getExercise();

        switch (notificationType) {
            case NEW_PLAGIARISM_CASE_STUDENT -> {
                title = NEW_PLAGIARISM_CASE_STUDENT_TITLE;
                notificationText = "New plagiarism case concerning the " + affectedExercise.getExerciseType().toString().toLowerCase() + " exercise \""
                        + affectedExercise.getTitle() + "\".";
            }
            case PLAGIARISM_CASE_VERDICT_STUDENT -> {
                title = PLAGIARISM_CASE_VERDICT_STUDENT_TITLE;
                notificationText = "Your plagiarism case concerning the " + affectedExercise.getExerciseType().toString().toLowerCase() + " exercise \""
                        + affectedExercise.getTitle() + "\"" + " has a verdict.";
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        courseId = affectedExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        notification = new SingleUserNotification(student, title, notificationText);
        notification.setPriority(HIGH);
        notification.setAuthor(instructor);
        notification.setTransientAndStringTarget(createPlagiarismCaseTarget(plagiarismCase.getId(), courseId));
        return notification;
    }

    /**
     * Creates an instance of SingleUserNotification for tutorial groups.
     *
     * @param tutorialGroup        to which the notification is related
     * @param notificationType     type of the notification that should be created
     * @param student              who should be notified or is related to the notification
     * @param responsibleForAction the user who is responsible for the action that triggered the notification
     * @return an instance of SingleUserNotification
     */
    public static SingleUserNotification createNotification(TutorialGroup tutorialGroup, NotificationType notificationType, User student, User responsibleForAction) {
        var title = findCorrespondingNotificationTitle(notificationType);
        if (!StringUtils.hasText(title)) {
            throw new UnsupportedOperationException("No matching title found for: " + notificationType);
        }
        SingleUserNotification notification;
        switch (notificationType) {
            case TUTORIAL_GROUP_REGISTRATION_STUDENT -> {
                notification = new SingleUserNotification(student, title,
                        "You have been registered to the tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_STUDENT -> {
                notification = new SingleUserNotification(student, title,
                        "You have been deregistered from the tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), false));
            }
            case TUTORIAL_GROUP_REGISTRATION_TUTOR -> {
                if (Objects.isNull(tutorialGroup.getTeachingAssistant())) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, "The student " + student.getName()
                        + " has been registered to your tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true));
            }
            case TUTORIAL_GROUP_DEREGISTRATION_TUTOR -> {
                if (Objects.isNull(tutorialGroup.getTeachingAssistant())) {
                    throw new IllegalArgumentException("The tutorial group " + tutorialGroup.getTitle() + " does not have a tutor to which a notification could be sent.");
                }
                notification = new SingleUserNotification(tutorialGroup.getTeachingAssistant(), title, "The student " + student.getName()
                        + " has been deregistered from your tutorial group " + tutorialGroup.getTitle() + " by " + responsibleForAction.getName() + ".");
                notification.setTransientAndStringTarget(createTutorialGroupTarget(tutorialGroup, tutorialGroup.getCourse().getId(), true));
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType for tutorial groups: " + notificationType);
        }
        return notification;
    }

}
