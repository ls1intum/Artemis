package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Compact struggle signal emitted by the client engine and forwarded to the Pyris struggle-intervention
 * pipeline. camelCase to match the cross-repo wire contract (Plan 1 {@code StruggleSignal}). Annotated
 * {@code @JsonInclude(ALWAYS)} (NOT {@code NON_EMPTY}): Pyris requires {@code trajectory} +
 * {@code dominantComponents}, so empty lists must stay on the wire, while the annotation still satisfies the
 * iris-DTO {@code @JsonInclude} architecture rule.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record PyrisStruggleSignalDTO(AlertDTO alert, List<TickDTO> trajectory, List<ComponentDTO> dominantComponents, double sessionSeconds) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record AlertDTO(double tSessionS, String primaryBoundary, List<String> boundaryTypes, double severity, String path, boolean inWarmup, boolean inGrace) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record TickDTO(double t, double s, double v) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ComponentDTO(String name, double value) {
    }
}
