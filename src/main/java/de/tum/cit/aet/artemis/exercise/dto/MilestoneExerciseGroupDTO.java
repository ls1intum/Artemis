package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;

/**
 * DTO returned for a {@link MilestoneExerciseGroup}. The {@code exerciseIds} expose the group's current members so the
 * client can render them without serializing the full {@link Exercise} graph, and {@code milestoneExerciseId} points at
 * the group's anchor exercise.
 * <p>
 * {@code maxPoints} is always the sum of the members' points (a milestone group never stores a cap of its own - see
 * {@link MilestoneExerciseGroup#setMaxPoints}); it is purely a display value for the instructor UI. The timeline fields
 * are read through the group, which delegates them to its anchor exercise.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MilestoneExerciseGroupDTO(Long id, String title, @Nullable Long milestoneExerciseId, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate,
        @Nullable ZonedDateTime startDate, @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate,
        Set<Long> exerciseIds) {

    public MilestoneExerciseGroupDTO(MilestoneExerciseGroup group) {
        this(group.getId(), group.getTitle(), group.getMilestoneExercise() != null ? group.getMilestoneExercise().getId() : null, sumOfMemberPoints(group), group.getReleaseDate(),
                group.getStartDate(), group.getDueDate(), group.getAssessmentDueDate(), group.getExampleSolutionPublicationDate(),
                group.getExercises().stream().map(DomainObject::getId).collect(Collectors.toSet()));
    }

    private static Double sumOfMemberPoints(MilestoneExerciseGroup group) {
        return group.getExercises().stream().map(Exercise::getMaxPoints).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }
}
