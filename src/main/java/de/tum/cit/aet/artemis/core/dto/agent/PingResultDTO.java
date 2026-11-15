package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The answer to {@code ping}, which has no content.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PingResultDTO() {
}
