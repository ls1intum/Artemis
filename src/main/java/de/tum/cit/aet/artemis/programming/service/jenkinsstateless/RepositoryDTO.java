package de.tum.cit.aet.artemis.programming.service.jenkinsstateless;

import jakarta.validation.constraints.NotBlank;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing repository information for CI operations.
 * This abstraction can be reused for exercise, solution, test, and auxiliary
 * repositories.
 *
 * SHARED DTO - Used by both Artemis core and CI connector microservices
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RepositoryDTO(@NotBlank String url, String commitHash, String cloneLocation, String accessToken) {
}
