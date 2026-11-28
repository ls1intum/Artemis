package de.tum.cit.aet.artemis.iris.service.pyris.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The error body Pyris answers a failed request with: {@code {"detail": {"errorMessage": "..."}}}.
 *
 * @param detail the error details, or {@code null} when the body does not carry them
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisErrorResponseDTO(@Nullable PyrisErrorDetailDTO detail) {
}
