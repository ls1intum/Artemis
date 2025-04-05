package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Specifies the location of an artifact.
 *
 * @param uri A string containing a valid relative or absolute URI.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ArtifactLocation(String uri) {

    /**
     * A string containing a valid relative or absolute URI.
     */
    public Optional<String> getOptionalUri() {
        return Optional.ofNullable(uri);
    }
}
