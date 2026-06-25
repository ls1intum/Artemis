package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;

/**
 * DTO returned for an {@link ExerciseVariantGroup}. The {@code exerciseIds} expose the group's current members so the
 * client can render them without serializing the full {@link de.tum.cit.aet.artemis.exercise.domain.Exercise} graph.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVariantGroupDTO(Long id, String title, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate,
        @Nullable ZonedDateTime buildAndTestStudentSubmissionsAfterDueDate, Set<Long> exerciseIds) {

    public ExerciseVariantGroupDTO(ExerciseVariantGroup group) {
        this(group.getId(), group.getTitle(), group.getMaxPoints(), group.getReleaseDate(), group.getStartDate(), group.getDueDate(), group.getAssessmentDueDate(),
                group.getExampleSolutionPublicationDate(), group.getBuildAndTestStudentSubmissionsAfterDueDate(),
                group.getExercises().stream().map(DomainObject::getId).collect(Collectors.toSet()));
    }
}
