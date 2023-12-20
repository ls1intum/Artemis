package de.tum.in.www1.artemis.service.connectors.aeolus.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AeolusGenerationResponseDTO {

    private String key;

    private String result;

    /**
     * needed for Jackson
     */
    public AeolusGenerationResponseDTO() {
    }

    public AeolusGenerationResponseDTO(String key, String result) {
        this.key = key;
        this.result = result;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }
}
