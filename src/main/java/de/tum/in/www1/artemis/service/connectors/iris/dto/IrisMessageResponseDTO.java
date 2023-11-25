package de.tum.in.www1.artemis.service.connectors.iris.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.iris.message.IrisMessage;

public record IrisMessageResponseDTO(String usedModel, IrisMessage message) {

    /**
     * Creates a new IrisMessageResponseDTO with the required fields.
     * This constructor is a workaround for a strange issue with Jackson, where invalid data such as "invalid": "invalid"
     * in the response from Iris would not result in an exception, but instead a successful deserialization of the response
     * into an object with null fields (!?).
     * We need the @JsonCreator annotation to tell Jackson to use this constructor, and the @JsonProperty annotations to
     * tell Jackson that the fields are required and it should throw an exception if they are missing.
     * See also: IrisMessageResponseV2DTO.
     * TODO: Investigate this issue further and remove this workaround if possible.
     */
    @JsonCreator // @formatter:off
    public IrisMessageResponseDTO(@JsonProperty(value = "usedModel", required = true) String usedModel,
                                  @JsonProperty(value = "message", required = true) IrisMessage message) {
        this.usedModel = usedModel;
        this.message = message;
    } // @formatter:on
}
