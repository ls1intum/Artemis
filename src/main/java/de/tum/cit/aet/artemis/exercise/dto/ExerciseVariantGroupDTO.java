package de.tum.cit.aet.artemis.exercise.dto;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVariantGroup;
import de.tum.cit.aet.artemis.exercise.domain.MilestoneExerciseGroup;

/**
 * DTO returned for an {@link ExerciseVariantGroup}. The {@code exerciseIds} expose the group's current members so the
 * client can render them without serializing the full {@link de.tum.cit.aet.artemis.exercise.domain.Exercise} graph.
 * {@code type} mirrors the entity's discriminator ({@code "variant"} or {@code "milestone"}) so the client can branch
 * without an extra request; {@code milestoneExerciseId} is only set for a {@code "milestone"} group.
 * <p>
 * {@code maxPoints} is a plain stored cap for a {@code "variant"} group, but for a {@code "milestone"} group it is
 * always the sum of its members' points instead (the entity never stores one - see
 * {@link MilestoneExerciseGroup#setMaxPoints}) - this is purely a display value for the instructor UI.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExerciseVariantGroupDTO(Long id, String title, String type, @Nullable Double maxPoints, @Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate,
        @Nullable ZonedDateTime dueDate, @Nullable ZonedDateTime assessmentDueDate, @Nullable ZonedDateTime exampleSolutionPublicationDate, Set<Long> exerciseIds,
        @Nullable Long milestoneExerciseId) {

    public ExerciseVariantGroupDTO(ExerciseVariantGroup group) {
        this(group.getId(), group.getTitle(), group instanceof MilestoneExerciseGroup ? "milestone" : "variant",
                group instanceof MilestoneExerciseGroup ? sumOfMemberPoints(group) : group.getMaxPoints(), group.getReleaseDate(), group.getStartDate(), group.getDueDate(),
                group.getAssessmentDueDate(), group.getExampleSolutionPublicationDate(), group.getExercises().stream().map(DomainObject::getId).collect(Collectors.toSet()),
                group instanceof MilestoneExerciseGroup milestoneGroup && milestoneGroup.getMilestoneExercise() != null ? milestoneGroup.getMilestoneExercise().getId() : null);
    }

    // Boxed, not a primitive double: the ternary that calls this also has a group.getMaxPoints() (Double) branch, and a
    // mixed primitive/boxed ternary forces binary numeric promotion on BOTH branches (JLS 15.25) - unboxing a null
    // group.getMaxPoints() on the other side throws NullPointerException even when this branch is the one taken.
    private static Double sumOfMemberPoints(ExerciseVariantGroup group) {
        return group.getExercises().stream().map(Exercise::getMaxPoints).filter(Objects::nonNull).mapToDouble(Double::doubleValue).sum();
    }
}
