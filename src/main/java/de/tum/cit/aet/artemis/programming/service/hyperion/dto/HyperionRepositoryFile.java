package de.tum.cit.aet.artemis.programming.service.hyperion.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing a file in the generated repository
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record HyperionRepositoryFile(String path, String content) {
}
