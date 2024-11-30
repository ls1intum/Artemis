package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A physical location relevant to a result. Specifies a reference to a programming artifact together with a range of bytes or characters within that artifact.
 *
 * @param artifactLocation Specifies the location of an artifact.
 * @param region           A region within an artifact where a result was detected.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PhysicalLocation(ArtifactLocation artifactLocation, Region region) {

    /**
     * Specifies the location of an artifact.
     */
    public Optional<ArtifactLocation> getOptionalArtifactLocation() {
        return Optional.ofNullable(artifactLocation);
    }

    /**
     * A region within an artifact where a result was detected.
     */
    public Optional<Region> getOptionalRegion() {
        return Optional.ofNullable(region);
    }

}
