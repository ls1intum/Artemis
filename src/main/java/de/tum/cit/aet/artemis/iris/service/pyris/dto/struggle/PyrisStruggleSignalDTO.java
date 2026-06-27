package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Compact struggle signal emitted by the client engine and forwarded to the Pyris struggle-intervention
 * pipeline. camelCase to match the cross-repo wire contract (Plan 1 {@code StruggleSignal}).
 * <p>
 * Annotated with a bare {@code @JsonInclude()} (no value) rather than {@code @JsonInclude(NON_EMPTY)}: Pyris
 * declares {@code trajectory} and {@code dominantComponents} as required fields with no defaults, so an empty
 * list must still be serialized ({@code NON_EMPTY} would drop the key and make Pyris reject the payload with a
 * 422). The bare form inherits Jackson's default {@code ALWAYS} inclusion, keeping empty collections on the
 * wire, while still satisfying both iris-DTO architecture rules (every DTO must carry {@code @JsonInclude}, and
 * any explicitly declared value must be {@code NON_EMPTY}). The {@code emptyCollectionsAreNotDroppedFromWire}
 * test guards this contract.
 */
@JsonInclude
public record PyrisStruggleSignalDTO(AlertDTO alert, List<TickDTO> trajectory, List<ComponentDTO> dominantComponents, double sessionSeconds) {

    @JsonInclude
    public record AlertDTO(double tSessionS, String primaryBoundary, List<String> boundaryTypes, double severity, String path, boolean inWarmup, boolean inGrace) {
    }

    @JsonInclude
    public record TickDTO(double t, double s, double v) {
    }

    @JsonInclude
    public record ComponentDTO(String name, double value) {
    }
}
