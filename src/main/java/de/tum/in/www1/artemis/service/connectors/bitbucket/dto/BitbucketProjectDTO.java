package de.tum.in.www1.artemis.service.connectors.bitbucket.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BitbucketProjectDTO {

    private String key;

    private String name;

    private String description;

    private Long id;

    private String url;

    private String link;

    public BitbucketProjectDTO(String key) {
        this.key = key;
    }

    /**
     * needed for Jackson
     */
    public BitbucketProjectDTO() {
    }

    public BitbucketProjectDTO(String key, String name) {
        this.key = key;
        this.name = name;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
