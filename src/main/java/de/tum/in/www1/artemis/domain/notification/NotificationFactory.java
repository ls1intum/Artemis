package de.tum.in.www1.artemis.domain.notification;

import org.jsoup.Jsoup;

import de.tum.in.www1.artemis.domain.Course;
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
}
