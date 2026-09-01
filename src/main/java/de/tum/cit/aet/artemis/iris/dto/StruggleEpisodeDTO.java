package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Client-allocated slot episode, carried on both the inbound extension→Artemis request and the outbound
 * Artemis→Pyris execution payload. Uses bare {@code @JsonInclude()} (i.e. Include.ALWAYS) deliberately --
 * the global architecture rule forbids any explicit value other than NON_EMPTY, and the contract requires
 * a first FREE-slot {@code decide} to serialize {@code "hints":[]} with the empty list PRESENT (NON_EMPTY
 * would drop it, causing a cross-repo break).
 *
 * <p>
 * {@code episodeId} is the identity every episode-scoped lookup keys on (the terminal-outcome gate, the ambient
 * decision record, {@code iris_message.proactive_episode_id}). A blank id is therefore rejected rather than merely
 * bounded in width: an active decision would persist that blank value, and once one such row carried a terminal
 * outcome, every later blank-id intervention for the same student would be read as that same finished episode and
 * silently suppressed. The episode object as a whole stays optional (a client that sends none keeps the legacy
 * no-episode behaviour); only a supplied id has to carry an actual value.
 */
@JsonInclude
public record StruggleEpisodeDTO(@Size(max = 64) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String episodeId, boolean isNew, List<StruggleEpisodeHintDTO> hints) {

    public StruggleEpisodeDTO {
        hints = hints != null ? hints : List.of();
    }
}
