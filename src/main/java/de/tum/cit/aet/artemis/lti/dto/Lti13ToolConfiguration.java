package de.tum.cit.aet.artemis.lti.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Inner class representing the LTI 1.3 tool configuration.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record Lti13ToolConfiguration(String domain, @JsonProperty("target_link_uri") String targetLinkUri, String description, List<Lti13Message> messages, List<String> claims) {

}
