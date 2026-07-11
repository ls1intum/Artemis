package de.tum.cit.aet.artemis.iris.service.pyris.dto.data;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * @param jsonContent the JSON payload as a string. Pyris declares this field as pydantic {@code Json[Any]}, which only accepts a JSON <em>string</em> and parses it — a raw JSON
 *                        object here fails validation on the Pyris side and rejects the whole pipeline request.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisJsonMessageContentDTO(String jsonContent) implements PyrisMessageContentBaseDTO {
}
