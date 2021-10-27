package de.tum.in.www1.artemis.domain.notification;

import com.google.gson.Gson;

import de.tum.in.www1.artemis.domain.metis.Post;

/**
 * Class representing the target property of a notification
 * Do not convert it into a java record. This does not work currently and will break the (de)serialization
 * e.g. used for JSON/GSON (de)serialization
 */
public final class NotificationTarget {

    private final int id;

    private final String entity;

    private final int course;

    // public NotificationTarget(String message, int id, String entity, int course, String mainPage) {
    public NotificationTarget(int id, String entity, int course) {
        this.id = id;
        this.entity = entity;
        this.course = course;
    }

    /**
     * Extracts a viable URL from the provided notification and baseUrl
     * @param notification which target property will be used for creating the URL
     * @param baseUrl the prefix (depends on current set up (e.g. "http://localhost:9000/courses"))
     * @return viable URL to the notification related page
     */
    public static String extractNotificationUrl(Notification notification, String baseUrl) {
        Gson gson = new Gson();
        NotificationTarget target = gson.fromJson(notification.getTarget(), NotificationTarget.class);
        return target.turnToUrl(baseUrl);
    }

    /**
     * Extracts a viable URL from the provided notification that is based on a Post and baseUrl
     * @param post which information will be needed to created the URL
     * @param baseUrl the prefix (depends on current set up (e.g. "http://localhost:9000/courses"))
     * @return viable URL to the notification related page
     */
    public static String extractNotificationUrl(Post post, String baseUrl) {
        // e.g. http://localhost:8080/courses/1/discussion?searchText=%2382 for announcement post
        return baseUrl + "/courses/" + post.getCourse().getId() + "/discussion?searchText=%23" + post.getId();
    }

    /**
     * Turns the notification target into a viable URL
     * @param baseUrl is the prefix for the URL (e.g. "http://localhost:9000/courses")
     * @return the extracted viable URL
     */
    private String turnToUrl(String baseUrl) {
        return baseUrl + "/courses/" + course + "/" + entity + "/" + id;
    }
}
