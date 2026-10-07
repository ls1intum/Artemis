package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;

/**
 * Reference to an exam {@link ExerciseGroup} on request bodies. The client posts a nested exercise-group object; the
 * server re-loads the group by id, so only the id is bound.
 *
 * @param id the exercise group id
 */
@JsonIgnoreProperties(ignoreUnknown = true)
// bare @JsonInclude(): request bodies must keep nulls and empty collections on the wire, and the shared
// architecture rule forbids spelling out Include.ALWAYS (only NON_EMPTY or no explicit value are allowed)
@JsonInclude()
public record ExerciseGroupIdDTO(Long id) {

    /**
     * Builds a transient {@link ExerciseGroup} carrying only the id, the shape the create/import handlers need before
     * they resolve the real group from the database.
     *
     * @return the transient exercise group
     */
    public ExerciseGroup toEntity() {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setId(id);
        return exerciseGroup;
    }
}
