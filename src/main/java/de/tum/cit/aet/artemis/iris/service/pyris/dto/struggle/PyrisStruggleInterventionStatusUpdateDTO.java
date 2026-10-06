package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStatusErrorDTO;

/**
 * Result of a struggle-intervention run, posted back by Pyris. Flat fields per the house style.
 * {@code action} is null on non-decision callbacks; the handler keys idempotency on {@code action != null}.
 * {@code result}/{@code confidence} are null when {@code action == "silent"} and on the trailing duplicate
 * callback.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStruggleInterventionStatusUpdateDTO(@Nullable String result, @Nullable String action, @Nullable Double confidence, @Nullable String rationale,
        @Nullable PyrisRunState runState, @Nullable PyrisStatusErrorDTO error, @NonNull List<LLMRequest> tokens, @JsonProperty("anchor_file") @Nullable String anchorFile,
        @JsonProperty("anchor_line") @Nullable Integer anchorLine, @JsonProperty("inline_hint") @Nullable String inlineHint, @Nullable Boolean resolved,
        @JsonProperty("closing_sentence") @Nullable String closingSentence, @JsonProperty("episode_label") @Nullable String episodeLabel) {

    public PyrisStruggleInterventionStatusUpdateDTO {
        tokens = tokens != null ? tokens : List.of();
    }
}
