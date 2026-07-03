package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Result of synchronously executing a {@link PyrisCommandDTO}, returned to Pyris so the agent tool knows whether its action actually happened.
 *
 * @param applied whether the command was carried out on the client (e.g. the combined view was still open and the client navigated)
 * @param reason  optional short reason, mainly when not applied (e.g. "combinedViewClosed", "timeout")
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PyrisCommandResultDTO(boolean applied, @Nullable String reason) {

    public static PyrisCommandResultDTO success() {
        return new PyrisCommandResultDTO(true, null);
    }

    public static PyrisCommandResultDTO notApplied(String reason) {
        return new PyrisCommandResultDTO(false, reason);
    }
}
