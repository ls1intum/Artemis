package de.tum.cit.aet.artemis.programming.dto;

import java.util.Map;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The Docker flags a container of a build plan sets for its own job. Every field is optional: a field the container
 * leaves unset takes the exercise's value, so a container overrides only what it needs. The network and the resource
 * limits replace the exercise's value; the environment variables are merged by name on top of the exercise's, the
 * container's value winning, so a container adds or overrides a variable without repeating the exercise's. The flags of
 * one container never reach the job of another.
 *
 * @param network    the Docker network the container joins, or null for the exercise's
 * @param env        environment variables set in the container on top of the exercise's, or null for none
 * @param cpuCount   the number of CPUs available to the container, or null for the exercise's limit
 * @param memory     the memory limit in MB, or null for the exercise's limit
 * @param memorySwap the memory swap limit in MB, or null for the exercise's limit
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record BuildContainerDockerFlagsDTO(@Nullable String network, @Nullable Map<String, String> env, @Nullable @Positive Integer cpuCount, @Nullable @Positive Integer memory,
        @Nullable @PositiveOrZero Integer memorySwap) {
}
