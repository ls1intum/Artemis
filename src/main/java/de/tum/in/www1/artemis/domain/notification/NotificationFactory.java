package de.tum.in.www1.artemis.domain.notification;

import static de.tum.in.www1.artemis.domain.notification.NotificationConstants.*;
import static de.tum.in.www1.artemis.domain.notification.NotificationTargetFactory.*;

import org.apache.commons.lang3.ArrayUtils;
import org.jsoup.Jsoup;

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

    /**
     * Gets the placeholderValues used for messageNotifications
     *
     * @param course where the post is published in
     * @param post   the user gets notified about
     * @return a String array containing all the placeholder values
     */
    public static String[] generatePlaceholderValuesForMessageNotifications(Course course, Post post) {
        return new String[] { course.getTitle(), post.getTitle(), Jsoup.parse(post.getContent()).text(), post.getCreationDate().toString(), post.getAuthor().getName() };
    }

    /**
     * Gets the placeholderValues used for messageNotifications triggered by a reply to a post
     *
     * @param course     where the post is published in
     * @param post       the user gets notified about
     * @param answerPost to the post that the user gets notified about
     * @return a String array containing all the placeholder values
     */
    public static String[] generatePlaceholderValuesForMessageNotificationsWithAnswers(Course course, Post post, AnswerPost answerPost) {
        return ArrayUtils.addAll(generatePlaceholderValuesForMessageNotifications(course, post), Jsoup.parse(answerPost.getContent()).text(),
                answerPost.getCreationDate().toString(), answerPost.getAuthor().getName());
    }

    /**
     * Creates an instance of GroupNotification based on the passed parameters.
     *
     * @param post             for which a notification should be created
     * @param answerPost       to the post for which the notification should be created
     * @param notificationType of the notification
     * @param course           the post belongs to
     * @param newReplyBuilder  to create the notification with
     * @param <N>              either a GroupNotification or SingleUserNotification
     * @return an instance of N
     */
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
