package de.tum.cit.aet.artemis.iris.service.pyris.dto.status;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Error details reported by Pyris for a failed run.
 *
 * @param message human-readable error message
 * @param code    optional machine-readable error code
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStatusErrorDTO(String message, String code) {
}
