package de.tum.in.www1.artemis.domain.notification;

import com.google.gson.Gson;

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

    public String turnToUrl(String baseUrl) {
        return baseUrl + "/courses/" + course + "/" + entity + "/" + id;
        // return "http://" + baseUrl + "/courses/" + course + "/" + entity + "/" + id;
    }

    public static String extractNotificationUrl(Notification notification) {
        Gson gson = new Gson();
        NotificationTarget target = gson.fromJson(notification.getTarget(), NotificationTarget.class);
        // resultUrl = target.turnToUrl(BASE_URL);
        // TODO remove after testing is finished
        return target.turnToUrl("http://localhost:9000");
    }
}
