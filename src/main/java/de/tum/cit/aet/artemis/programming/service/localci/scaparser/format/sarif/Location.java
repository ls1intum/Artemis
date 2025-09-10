package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A location within a programming artifact.
 *
 * @param physicalLocation A physical location relevant to a result. Specifies a reference to a programming artifact together with a range of bytes or characters within that
 *                             artifact.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Location(PhysicalLocation physicalLocation) {

    /**
     * A physical location relevant to a result. Specifies a reference to a programming artifact together with a range of bytes or characters within that artifact.
     */
    public Optional<PhysicalLocation> getOptionalPhysicalLocation() {
        return Optional.ofNullable(physicalLocation);
    }
}
