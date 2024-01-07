package de.tum.in.www1.artemis.service.connectors.aeolus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

/**
 * DTO for Aeolus generation responses
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AeolusTranslationResponseDTO {

    private String key;

    private Windfile result;

    /**
     * needed for Jackson
     */
    public AeolusTranslationResponseDTO() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Windfile getResult() {
        return result;
    }
}
