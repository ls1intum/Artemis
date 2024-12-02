package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Key/value pairs that provide additional information about the object.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PropertyBag(@JsonAnySetter Map<String, Object> additionalProperties) {

}
