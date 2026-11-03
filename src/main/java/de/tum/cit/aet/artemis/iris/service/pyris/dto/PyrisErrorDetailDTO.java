package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The details of a Pyris error, see {@link PyrisErrorResponseDTO}.
 *
 * @param errorMessage the human-readable message Pyris reports, or {@code null} when absent
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisErrorDetailDTO(@Nullable String errorMessage) {
}
