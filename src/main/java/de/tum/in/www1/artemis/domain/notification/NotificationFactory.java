package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;

import org.apache.commons.lang3.ArrayUtils;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.metis.AnswerPost;
import de.tum.in.www1.artemis.domain.metis.Post;

/**
 * Class that shares common logic between {@link GroupNotificationFactory} and {@link SingleUserNotificationFactory}
 */
public class NotificationFactory {

    public static String[] generatePlaceholderValuesForMessageNotifications(Course course, Post post) {
        return new String[] { course.getTitle(), post.getTitle(), post.getContent(), post.getCreationDate().toString(), post.getAuthor().getName() };
    }

    public static String[] generatePlaceholderValuesForMessageNotificationsWithAnswers(Course course, Post post, AnswerPost answerPost) {
        return ArrayUtils.addAll(generatePlaceholderValuesForMessageNotifications(course, post), answerPost.getContent(), answerPost.getCreationDate().toString(),
                answerPost.getAuthor().getName());
    }

    public static <N extends Notification> N createNotificationImplementation(Post post, AnswerPost answerPost, NotificationType notificationType, Course course,
            NewReplyBuilder<N> newReplyBuilder) {
        String title;
        String[] placeholderValues;
        NotificationTarget target;

        switch (notificationType) {
            case NEW_REPLY_FOR_EXERCISE_POST -> {
                Exercise exercise = post.getExercise();
                title = NEW_REPLY_FOR_EXERCISE_POST_TITLE;
                placeholderValues = ArrayUtils.addAll(NotificationFactory.generatePlaceholderValuesForMessageNotificationsWithAnswers(course, post, answerPost),
                        exercise.getTitle());
                target = createExercisePostTarget(post, course);
            }
            case NEW_REPLY_FOR_LECTURE_POST -> {
                Lecture lecture = post.getLecture();
                title = NEW_REPLY_FOR_LECTURE_POST_TITLE;
                placeholderValues = ArrayUtils.addAll(NotificationFactory.generatePlaceholderValuesForMessageNotificationsWithAnswers(course, post, answerPost),
                        lecture.getTitle());
                target = createLecturePostTarget(post, course);
            }
            case NEW_REPLY_FOR_COURSE_POST -> {
                title = NEW_REPLY_FOR_COURSE_POST_TITLE;
                placeholderValues = ArrayUtils.addAll(NotificationFactory.generatePlaceholderValuesForMessageNotificationsWithAnswers(course, post, answerPost));
                target = createCoursePostTarget(post, course);
            }
            default -> throw new UnsupportedOperationException("Unsupported NotificationType: " + notificationType);
        }

        return newReplyBuilder.buildAndApplyTarget(title, placeholderValues, target);
    }

    public interface NewReplyBuilder<N extends Notification> {

        N build(String title, String[] placeholderValues);

        default N buildAndApplyTarget(String title, String[] placeholderValues, NotificationTarget target) {
            N notification = build(title, placeholderValues);
            notification.setTransientAndStringTarget(target);
            return notification;
        }
    }
}
