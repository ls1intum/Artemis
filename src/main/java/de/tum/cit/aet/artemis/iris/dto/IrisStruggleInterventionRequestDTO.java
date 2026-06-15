package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

/**
 * Body of {@code POST /api/iris/chat/exercises/{exerciseId}/struggle-intervention} (spec §5.2). The
 * exercise is the path key; the body carries the struggle signal + the exercise-scoped uncommitted-files
 * snapshot collected by the extension (spec §7).
 * <p>
 * This DTO is inbound (deserialization) only, so {@code @JsonInclude} has no functional effect here; it is
 * present to satisfy the iris-DTO {@code @JsonInclude} architecture rule. The nested signal keeps its own
 * {@code @JsonInclude(ALWAYS)} so its empty inner collections still serialize for Pyris.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisStruggleInterventionRequestDTO(PyrisStruggleSignalDTO struggleSignal, @NonNull Map<String, String> uncommittedFiles) {

    public IrisStruggleInterventionRequestDTO {
        uncommittedFiles = uncommittedFiles != null ? uncommittedFiles : Map.of();
    }
}
