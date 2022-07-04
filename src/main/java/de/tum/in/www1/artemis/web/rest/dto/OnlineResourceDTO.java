package de.tum.in.www1.artemis.web.rest.dto;

public class OnlineResourceDTO {

    public String url;

    public String title;

    public String description;

    public OnlineResourceDTO(String url, String title, String description) {
        this.url = url;
        this.title = title;
        this.description = description;
    }
}
