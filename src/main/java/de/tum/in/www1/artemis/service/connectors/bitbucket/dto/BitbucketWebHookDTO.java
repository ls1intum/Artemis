package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketWebHookDTO {

    private Integer id;

    private String name;

    private String url;

    private ArrayList<String> events;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
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

    public BitbucketWebHookDTO(String name, String url, ArrayList<String> events) {
        this.name = name;
        this.url = url;
        this.events = events;
    }
}
