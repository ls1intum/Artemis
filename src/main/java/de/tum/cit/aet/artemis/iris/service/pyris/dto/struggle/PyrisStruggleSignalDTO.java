package de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Compact struggle signal emitted by the client engine and forwarded to the Pyris struggle-intervention
 * pipeline. camelCase to match the cross-repo wire contract (Plan 1 {@code StruggleSignal}).
 * <p>
 * Boundary and path values are forwarded opaquely (plain strings, no enum): the edit path sends
 * {@code primaryBoundary} in {FM, E4, N1, STATE} with {@code path} in {armed, e6}; the discrete
 * test-stagnation path sends {@code primaryBoundary="TPS"} with {@code path="discrete"}. Pyris owns the
 * value validation, so new client-side values must be introduced Pyris-first.
 * <p>
 * Annotated with a bare {@code @JsonInclude()} (no value) rather than {@code @JsonInclude(NON_EMPTY)}: Pyris
 * declares {@code trajectory} as a required field with no default, so an empty list must still be serialized
 * ({@code NON_EMPTY} would drop the key and make Pyris reject the payload with a 422). The bare form inherits
 * Jackson's default {@code ALWAYS} inclusion, keeping the empty collection on the wire, while still satisfying
 * both iris-DTO architecture rules (every DTO must carry {@code @JsonInclude}, and any explicitly declared value
 * must be {@code NON_EMPTY}). The {@code emptyCollectionsAreNotDroppedFromWire} test guards this contract.
 */
@JsonInclude
public record PyrisStruggleSignalDTO(AlertDTO alert, List<TickDTO> trajectory, double sessionSeconds) {

    @JsonInclude
    public record AlertDTO(double tSessionS, String primaryBoundary, List<String> boundaryTypes, double severity, String path, boolean inWarmup, boolean inGrace) {
    }

    /**
     * One 10-s engine tick of the severity trajectory: {@code s} is the severity sBase at tick {@code t}.
     */
    @JsonInclude
    public record TickDTO(double t, double s) {
    }
}
