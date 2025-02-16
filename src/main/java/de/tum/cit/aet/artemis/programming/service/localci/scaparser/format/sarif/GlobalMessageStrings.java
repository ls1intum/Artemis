package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/**
 * A dictionary, each of whose keys is a resource identifier and each of whose values is a multiformatMessageString object, which holds message strings in plain text and
 * (optionally) Markdown format. The strings can include placeholders, which can be used to construct a message in combination with an arbitrary number of additional string
 * arguments.
 */
public record GlobalMessageStrings(@JsonAnySetter Map<String, MultiformatMessageString> additionalProperties) {

}
