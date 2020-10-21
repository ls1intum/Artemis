package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketWebHookDTO {

    private Integer id;

    private String webHookName;

    private String notificationUrl;

    private ArrayList<String> events;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getWebHookName() {
        return webHookName;
    }

    public void setWebHookName(String webHookName) {
        this.webHookName = webHookName;
    }

    public String getNotificationUrl() {
        return notificationUrl;
    }

    public void setNotificationUrl(String notificationUrl) {
        this.notificationUrl = notificationUrl;
    }

    public ArrayList<String> getEvents() {
        return events;
    }

    public void setEvents(ArrayList<String> events) {
        this.events = events;
    }

    /**
     * needed for Jackson
     */
    public BitbucketWebHookDTO() {
    }

    public BitbucketWebHookDTO(String webHookName, String notificationUrl, ArrayList<String> events) {
        this.webHookName = webHookName;
        this.notificationUrl = notificationUrl;
        this.events = events;
    }
}
