package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

/**
 * Body of {@code POST /api/iris/chat/exercises/{exerciseId}/struggle-intervention} (spec §5.2). The
 * exercise is the path key; the body carries the struggle signal, uncommitted-files snapshot, the slot
 * intent, the live episode, the close-mode discriminator, and the scoped-cancel identity.
 * <p>
 * This DTO is inbound (deserialization) only, so {@code @JsonInclude} has no functional effect here; it is
 * present to satisfy the iris-DTO {@code @JsonInclude} architecture rule. The nested signal keeps its own
 * {@code @JsonInclude(ALWAYS)} so its empty inner collections still serialize for Pyris.
 * <p>
 * {@code intent} values: {@code decide} (default) | {@code confirm_close} | {@code stale_check}
 * (snake-case wire values, spec §17). {@code confirmReason} values: {@code progress} | {@code stale_solved}
 * | {@code parked_progress} (A11 close-mode discriminator). {@code requestToken} is a client-minted UUID
 * used as the scoped-cancel identity (A10).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStruggleInterventionRequestDTO(@NotNull PyrisStruggleSignalDTO struggleSignal, @NonNull Map<String, String> uncommittedFiles, @Nullable String intent,
        @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken) {

    public IrisStruggleInterventionRequestDTO {
        uncommittedFiles = uncommittedFiles != null ? uncommittedFiles : Map.of();
        intent = intent != null ? intent : "decide";
    }
}
