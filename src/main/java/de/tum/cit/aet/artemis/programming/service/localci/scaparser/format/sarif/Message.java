package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Encapsulates a message intended to be read by the end user.
 *
 * @param text A plain text message string.
 * @param id   The identifier for this message.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Message(String text, String id) {

    /**
     * A plain text message string.
     */
    public Optional<String> getOptionalText() {
        return Optional.ofNullable(text);
    }

    /**
     * The identifier for this message.
     */
    public Optional<String> getOptionalId() {
        return Optional.ofNullable(id);
    }

}
