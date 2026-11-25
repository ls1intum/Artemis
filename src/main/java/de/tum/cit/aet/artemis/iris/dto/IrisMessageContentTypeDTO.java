package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Read only the discriminator before deserializing a structured message's type-specific fields. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageContentTypeDTO(String type) {
}
