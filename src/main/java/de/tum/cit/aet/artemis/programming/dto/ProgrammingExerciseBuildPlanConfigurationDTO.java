package de.tum.cit.aet.artemis.programming.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The stored build plan configuration of one exercise, with the identity needed to attribute it.
 * <p>
 * Recomputing the "Run Tests after Due Date" values of an exam reads the build plan configuration of every programming
 * exercise in it, and nothing else about the configuration. Reading the entity for that pulled all fourteen of its
 * columns per exercise, two of them {@code longtext} ({@code build_plan_configuration} and {@code docker_flags}), and
 * did so one query at a time. This projection is read for the whole exam in one query and carries the two values that
 * are actually used.
 *
 * @param buildConfigId          the configuration row, which the failure log names so a broken configuration can be found
 * @param exerciseId             the exercise the configuration belongs to, which is what callers key it by
 * @param buildPlanConfiguration the serialized build plan, or {@code null} when the exercise has none stored yet
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseBuildPlanConfigurationDTO(long buildConfigId, long exerciseId, @Nullable String buildPlanConfiguration) {
}
