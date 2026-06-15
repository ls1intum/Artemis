// src/main/java/de/tum/cit/aet/artemis/iris/dto/IrisStruggleInterventionRequestDTO.java
package de.tum.cit.aet.artemis.iris.dto;

import java.util.Map;

import org.jspecify.annotations.NonNull;

import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;

/**
 * Body of {@code POST /api/iris/chat/exercises/{exerciseId}/struggle-intervention} (spec §5.2). The
 * exercise is the path key; the body carries the struggle signal + the exercise-scoped uncommitted-files
 * snapshot collected by the extension (spec §7).
 * <p>
 * This DTO is inbound (deserialization) only, so it carries NO {@code @JsonInclude} — that annotation governs
 * serialization and would be dead noise here. (It would also contradict Task 1's must-keep note for the nested
 * signal, where {@code NON_EMPTY} must NOT be used so empty inner collections still serialize for Pyris.)
 */
public record IrisStruggleInterventionRequestDTO(PyrisStruggleSignalDTO struggleSignal, @NonNull Map<String, String> uncommittedFiles) {

    public IrisStruggleInterventionRequestDTO {
        uncommittedFiles = uncommittedFiles != null ? uncommittedFiles : Map.of();
    }
}
