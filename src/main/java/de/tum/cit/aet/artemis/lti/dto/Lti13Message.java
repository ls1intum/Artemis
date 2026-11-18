package de.tum.cit.aet.artemis.lti.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inner class representing a message in LTI 1.3 tool configuration.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Lti13Message(String type, @JsonProperty("target_link_uri") String targetLinkUri) {

}
