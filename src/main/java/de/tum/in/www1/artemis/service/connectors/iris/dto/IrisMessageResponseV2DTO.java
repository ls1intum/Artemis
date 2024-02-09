package de.tum.in.www1.artemis.service.connectors.iris.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

public record IrisMessageResponseV2DTO(String usedModel, ZonedDateTime sentAt, JsonNode content) {

    /**
     * Creates a new IrisMessageResponseV2DTO with the required fields.
     * This constructor is a workaround for a strange issue with Jackson, where invalid data such as "invalid": "invalid"
     * in the response from Iris would not result in an exception, but instead a successful deserialization of the response
     * into an object with null fields (!?).
     * We need the @JsonCreator annotation to tell Jackson to use this constructor, and the @JsonProperty annotations to
     * tell Jackson that the fields are required and it should throw an exception if they are missing.
     * See also: IrisMessageResponseDTO.
     * TODO: Investigate this issue further and remove this workaround if possible.
     */
    @JsonCreator // @formatter:off
    public IrisMessageResponseV2DTO(@JsonProperty(value = "usedModel", required = true) String usedModel,
                                    @JsonProperty(value = "sentAt", required = true) ZonedDateTime sentAt,
                                    @JsonProperty(value = "content", required = true) JsonNode content) {
        this.usedModel = usedModel;
        this.sentAt = sentAt;
        this.content = content;
    } // @formatter:on

}
