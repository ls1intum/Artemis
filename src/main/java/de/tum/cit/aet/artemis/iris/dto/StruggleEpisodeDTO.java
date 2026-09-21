package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Client-allocated slot episode, carried on both the inbound request and the outbound Pyris execution payload. A
 * bare {@code @JsonInclude()} deliberately, because the architecture rule forbids any explicit value other than
 * NON_EMPTY and the contract needs a first {@code decide} to serialize {@code "hints":[]} with the empty list
 * present.
 *
 * <p>
 * {@code episodeId} is the identity every episode-scoped lookup keys on, so a blank id is rejected rather than
 * merely bounded in width: once one blank-id row carried a terminal outcome, every later blank-id intervention for
 * that student would read as the same finished episode. The episode object itself stays optional.
 */
@JsonInclude
public record StruggleEpisodeDTO(@Size(max = MAX_EPISODE_ID_LENGTH) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String episodeId, boolean isNew,
        List<StruggleEpisodeHintDTO> hints) {

    /** Width of both episode-id columns, which are varchar(64); a client-generated UUID fits comfortably. */
    public static final int MAX_EPISODE_ID_LENGTH = 64;

    public StruggleEpisodeDTO {
        hints = hints != null ? hints : List.of();
    }

    /**
     * The episode id a caller may use as an identity, or null when the given one cannot serve as one. A blank id
     * would key every episode-scoped lookup, and an over-long one does not fit the column. The trigger endpoint
     * rejects both, but the {@code {episodeId}} path variable is not covered by that validation, so treating such an
     * id as "no episode" degrades to the legacy path instead of corrupting the keyspace.
     *
     * @param episodeId the id to check, possibly null
     * @return the id when it can serve as an identity, null otherwise
     */
    public static @Nullable String usableEpisodeId(@Nullable String episodeId) {
        if (episodeId == null || episodeId.isBlank() || episodeId.length() > MAX_EPISODE_ID_LENGTH) {
            return null;
        }
        return episodeId;
    }
}
