package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

/**
 * Body of {@code POST /api/iris/chat/exercises/{exerciseId}/struggle-intervention}. The
 * exercise is the path key; the body carries the struggle signal, uncommitted-files snapshot, the slot
 * intent, the live episode, the close-mode discriminator, and the scoped-cancel identity.
 * <p>
 * This DTO is inbound (deserialization) only, so {@code @JsonInclude} has no functional effect here; it is
 * present to satisfy the iris-DTO {@code @JsonInclude} architecture rule. The nested signal keeps its own
 * {@code @JsonInclude(ALWAYS)} so its empty inner collections still serialize for Pyris.
 * <p>
 * {@code intent} values: {@code decide} (default) | {@code confirm_close}
 * (snake-case wire values). {@code confirmReason} values: {@code progress}
 * | {@code parked_progress} (A11 close-mode discriminator). {@code requestToken} is a client-minted UUID
 * used as the scoped-cancel identity. {@code proactivityMode} values: {@code pull} (Less) |
 * {@code push} (More, default when absent); in {@code pull} the server deterministically forces an
 * {@code active} decision down to {@code ambient}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStruggleInterventionRequestDTO(@NotNull PyrisStruggleSignalDTO struggleSignal, @NonNull Map<String, String> uncommittedFiles, @Nullable String intent,
        @Valid @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) {

    public IrisStruggleInterventionRequestDTO {
        uncommittedFiles = uncommittedFiles != null ? uncommittedFiles : Map.of();
        intent = intent != null ? intent : "decide";
        proactivityMode = proactivityMode != null ? proactivityMode : "push";
    }
}
