package de.tum.cit.aet.artemis.iris.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A single hint already delivered for a struggle episode, carried on the inbound request so Pyris can
 * avoid repeating identical advice. {@code NON_EMPTY} is safe here because hints are only emitted inside a
 * non-empty hints list.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleEpisodeHintDTO(String level, String text, double atSessionS) {
}
