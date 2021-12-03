package de.tum.in.www1.artemis.domain.notification;

import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import de.tum.in.www1.artemis.domain.metis.Post;

/**
 * Class representing the target property of a notification
 * Do not convert it into a java record. This does not work currently and will break the (de)serialization
 * e.g. used for JSON/GSON (de)serialization
 */
public final class NotificationTarget {

    @SerializedName(value = "id")
    private final int notificationSubjectId;

    private final String entity;

    private final String mainPage;

    private final int course;

    public NotificationTarget(int notificationId, String entity, int course, String mainPage) {
        this.notificationSubjectId = notificationId;
        this.entity = entity;
        this.course = course;
        this.mainPage = mainPage;
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
        return baseUrl + "/" + mainPage + "/" + course + "/" + entity + "/" + notificationSubjectId;
    }
}
