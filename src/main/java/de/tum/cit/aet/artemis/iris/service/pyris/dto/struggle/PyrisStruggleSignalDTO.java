package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

/**
 * Compact struggle signal emitted by the client engine and forwarded to the Pyris struggle-intervention
 * pipeline. camelCase to match the cross-repo wire contract (Plan 1 {@code StruggleSignal}). Deliberately
 * NOT {@code @JsonInclude(NON_EMPTY)}: Pyris requires {@code trajectory} + {@code dominantComponents}, so
 * empty lists must stay on the wire.
 */
public record PyrisStruggleSignalDTO(Alert alert, List<Tick> trajectory, List<Component> dominantComponents, double sessionSeconds) {

    public record Alert(double tSessionS, String primaryBoundary, List<String> boundaryTypes, double severity, String path, boolean inWarmup, boolean inGrace) {
    }

    public record Tick(double t, double s, double v) {
    }

    public record Component(String name, double value) {
    }
}
