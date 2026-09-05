package de.tum.cit.aet.artemis.iris.dto;

import java.util.List;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

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
public record StruggleEpisodeDTO(@Size(max = MAX_EPISODE_ID_LENGTH) @Pattern(regexp = ".*\\S.*", message = "must not be blank") String episodeId, boolean isNew,
        List<StruggleEpisodeHintDTO> hints) {

    /**
     * Width of {@code iris_proactive_episode.episode_id} and {@code iris_message.proactive_episode_id}. Both columns
     * are varchar(64); an episode id is a client-generated UUID, so 64 is generous.
     */
    public static final int MAX_EPISODE_ID_LENGTH = 64;

    public StruggleEpisodeDTO {
        hints = hints != null ? hints : List.of();
    }

    /**
     * The episode id a caller may actually use as an identity, or null when the given one cannot serve as one.
     *
     * <p>
     * A blank id is not an identity: persisted on a message it would key every episode-scoped lookup, so the first
     * blank-id episode to end would make every later one read as that same finished episode. An over-long id does not
     * fit the column either. The trigger endpoint rejects both through the bean validation above, but a job minted
     * before that validation existed can still have its callback handled after a deployment, and a {@code
     * {episodeId}} path variable is not covered by it at all. Treating such an id as "no episode" degrades to the
     * well-defined legacy path instead of corrupting the episode keyspace.
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
