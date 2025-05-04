package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO for updating a Posting with only the necessary fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdatePostingDTO(Long id, String content, String title, Boolean resolvesPost) {

    public String getContent() {
        return content;
    }

    public String getTitle() {
        return title;
    }

    public Long getId() {
        return id;
    }

    public Boolean doesResolvePost() {
        return resolvesPost;
    }
}
