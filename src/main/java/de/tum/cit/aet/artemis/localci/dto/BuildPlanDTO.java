package de.tum.cit.aet.artemis.localci.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.build.BuildPlan;

/**
 * Build plan script of a programming exercise as shown in the build plan editor.
 *
 * @param id        the id of the build plan (null in write requests)
 * @param buildPlan the build plan script
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildPlanDTO(@Nullable Long id, @Nullable String buildPlan) {

    /**
     * Maps the build plan entity to the DTO.
     *
     * @param buildPlan the entity
     * @return the DTO with id and script
     */
    public static BuildPlanDTO of(BuildPlan buildPlan) {
        return new BuildPlanDTO(buildPlan.getId(), buildPlan.getBuildPlan());
    }
}
