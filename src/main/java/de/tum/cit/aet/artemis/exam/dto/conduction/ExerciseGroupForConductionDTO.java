package de.tum.cit.aet.artemis.exam.dto.conduction;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Projection of an {@link ExerciseGroup} as it appears nested inside an exam exercise during conduction.
 * <p>
 * The student-facing conduction payload masks the exercise group down to its identity: the back-references
 * ({@code exercises}, {@code exam}) are intentionally not carried, mirroring the masked entity wire where the
 * exercise group's {@code exam} is nulled and its {@code exercises} collection is empty.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseGroupForConductionDTO(long id, String title, @JsonProperty("isMandatory") Boolean isMandatory) {

    /**
     * Converts an ExerciseGroup into an ExerciseGroupForConductionDTO.
     *
     * @param exerciseGroup the exercise group to convert
     * @return the converted DTO, or null if the exercise group is null
     */
    public static ExerciseGroupForConductionDTO of(ExerciseGroup exerciseGroup) {
        if (exerciseGroup == null) {
            return null;
        }
        return new ExerciseGroupForConductionDTO(exerciseGroup.getId(), exerciseGroup.getTitle(), exerciseGroup.getIsMandatory());
    }
}
