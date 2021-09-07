package de.tum.in.www1.artemis.domain.notification;

import com.google.gson.Gson;

/**
 * Class representing the target property of a notification
 * e.g. used for JSON/GSON (de)serialization
 */
public class NotificationTarget {

    private String message;

    private int id;

    private String entity;

    private int course;

    private String mainPage;

    public NotificationTarget(String message, int id, String entity, int course, String mainPage) {
        this.message = message;
        this.id = id;
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
     * Turns the notification target into a viable URL
     * @param baseUrl is the prefix for the URL (e.g. "http://localhost:9000/courses")
     * @return the extracted viable URL
     */
    private String turnToUrl(String baseUrl) {
        return baseUrl + "/courses/" + course + "/" + entity + "/" + id;
    }
}
