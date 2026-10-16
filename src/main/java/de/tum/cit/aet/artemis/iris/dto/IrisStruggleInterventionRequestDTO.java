package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;
import java.util.Set;

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
 * {@code intent} values: {@code decide} (default) | {@code confirm_close} | {@code help_request}
 * (snake-case wire values). Anything else, {@code null} included, is normalised to {@code decide}, which is how
 * the routing reads it anyway, so the value stays bounded wherever it is later used as a key or sent to Pyris.
 * {@code confirmReason} values: {@code progress}
 * | {@code parked_progress} (close-mode discriminator). {@code requestToken} is a client-minted UUID
 * used as the scoped-cancel identity. {@code proactivityMode} values: {@code pull} (Less) |
 * {@code push} (More, default when absent); in {@code pull} the server deterministically forces an
 * {@code active} decision down to {@code ambient}.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStruggleInterventionRequestDTO(@NotNull PyrisStruggleSignalDTO struggleSignal, @NonNull Map<String, String> uncommittedFiles, @Nullable String intent,
        @Valid @Nullable StruggleEpisodeDTO episode, @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) {

    /** The default intent, and the fallback for any value the server does not recognise. */
    public static final String DECIDE_INTENT = "decide";

    /** The slot intent a trigger may carry. Everything else collapses to {@link #DECIDE_INTENT}. */
    private static final Set<String> KNOWN_INTENTS = Set.of(DECIDE_INTENT, "confirm_close", "help_request");

    public IrisStruggleInterventionRequestDTO {
        uncommittedFiles = uncommittedFiles != null ? uncommittedFiles : Map.of();
        intent = canonicalIntent(intent);
        proactivityMode = proactivityMode != null ? proactivityMode : "push";
    }

    /**
     * Collapses an inbound intent to one of the three the server routes on, so it is safe as a map key. Applied
     * again where it is keyed on, so a caller bypassing this DTO cannot widen the key space either.
     *
     * @param intent the raw intent, possibly {@code null} or unrecognised
     * @return the intent itself when known, {@link #DECIDE_INTENT} otherwise
     */
    public static String canonicalIntent(@Nullable String intent) {
        // Null-checked first: Set.of rejects a null lookup with an NPE rather than answering false.
        return intent != null && KNOWN_INTENTS.contains(intent) ? intent : DECIDE_INTENT;
    }
}
