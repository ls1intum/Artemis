package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;

/**
 * Result of a struggle-intervention run, posted back by Pyris (spec §5.4). Flat fields per the house style.
 * {@code action} is null on non-decision callbacks; the handler keys idempotency on {@code action != null}.
 * {@code result}/{@code confidence} are null when {@code action == "silent"} and on the trailing duplicate
 * callback (Plan 1 §5.4).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisStruggleInterventionStatusUpdateDTO(@Nullable String result, @Nullable String action, @Nullable Double confidence, @Nullable String rationale,
        @NonNull List<PyrisStageDTO> stages, @NonNull List<LLMRequest> tokens) {

    public PyrisStruggleInterventionStatusUpdateDTO {
        stages = stages != null ? stages : List.of();
        tokens = tokens != null ? tokens : List.of();
    }
}
