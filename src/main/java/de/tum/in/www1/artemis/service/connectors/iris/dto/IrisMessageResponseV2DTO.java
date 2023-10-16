package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record IrisMessageResponseV2DTO(String usedModel, ZonedDateTime sentAt, JsonNode content) {

    /**
     * Create a new IrisMessageResponseDTO. Jackson uses this constructor to create the object.
     * This is necessary because Jackson was not throwing an exception when the response from Iris did not contain
     * the expected fields, which resulted in a NullPointerException when trying to access the fields.
     * Not sure if this is a bug in Jackson or if it is intended behavior, either way this is a workaround.
     */
    @JsonCreator
    public IrisMessageResponseV2DTO(@JsonProperty(value = "usedModel", required = true) String usedModel, @JsonProperty(value = "sentAt", required = true) ZonedDateTime sentAt,
            @JsonProperty(value = "content", required = true) JsonNode content) {
        this.usedModel = usedModel;
        this.sentAt = sentAt;
        this.content = content;
    }

}
