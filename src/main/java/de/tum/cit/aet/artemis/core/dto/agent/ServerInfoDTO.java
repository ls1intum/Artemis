package de.tum.cit.aet.artemis.core.dto.agent;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Which server answers.
 *
 * @param name    the name of the server
 * @param version the version of Artemis
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ServerInfoDTO(String name, String version) {
}
