package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVariantGroupRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history.GenerationVersionRecoveryService.Recovery;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTimelineUpdateDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCreationUpdateService;

/** Restores placement-owned destination metadata without changing the source or other members of a variant group. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationRestoreMetadataService {

    private final ProgrammingExerciseRepository exercises;

    private final ExerciseVariantGroupRepository groups;

    private final ProgrammingExerciseCreationUpdateService updates;

    public GenerationRestoreMetadataService(ProgrammingExerciseRepository exercises, ExerciseVariantGroupRepository groups, ProgrammingExerciseCreationUpdateService updates) {
        this.exercises = exercises;
        this.groups = groups;
        this.updates = updates;
    }

    /**
     * Checks fields that variant placement can change before any repository is reset.
     *
     * @param exerciseId destination
     * @param pair       canonical recovery versions
     * @return whether no later instructor change would be overwritten
     */
    public boolean canRestore(long exerciseId, Recovery pair) {
        return Placement.of(exercises.findByIdElseThrow(exerciseId)).matchesEither(Placement.of(pair.before()), Placement.of(pair.after()));
    }

    /**
     * Restores membership and scheduling under the existing mutation guard. Each step is retryable after a partial undo.
     *
     * @param exerciseId destination
     * @param pair       canonical recovery versions
     * @param ownsSlot   exact mutation ownership check
     */
    public void restore(long exerciseId, Recovery pair, BooleanSupplier ownsSlot) {
        ProgrammingExercise current = exercises.findByIdElseThrow(exerciseId);
        Placement before = Placement.of(pair.before());
        Placement actual = Placement.of(current);
        if (!actual.matchesEither(before, Placement.of(pair.after()))) {
            throw new IllegalStateException("The exercise placement or timeline was edited after authoring");
        }
        if (actual.equals(before)) {
            return;
        }
        requireOwnership(ownsSlot);
        if (!Objects.equals(actual.groupId(), before.groupId())) {
            current.setExerciseVariantGroup(before.groupId() == null ? null : groups.findByIdElseThrow(before.groupId()));
            exercises.save(current);
        }
        requireOwnership(ownsSlot);
        ExerciseSnapshotDTO previous = pair.before();
        ZonedDateTime buildAfter = previous.programmingData().buildAndTestStudentSubmissionsAfterDueDate();
        Duration offset = previous.dueDate() == null || buildAfter == null ? null : Duration.between(previous.dueDate(), buildAfter);
        updates.updateTimeline(new ProgrammingExerciseTimelineUpdateDTO(exerciseId, previous.releaseDate(), previous.startDate(), previous.dueDate(), current.getAssessmentType(),
                previous.assessmentDueDate(), previous.exampleSolutionPublicationDate(), buildAfter), null, offset);
    }

    private static void requireOwnership(BooleanSupplier ownsSlot) {
        if (!ownsSlot.getAsBoolean()) {
            throw new IllegalStateException("Authoring restore no longer owns the exercise mutation guard");
        }
    }

    private record Placement(Long groupId, Instant release, Instant start, Instant due, Instant assessmentDue, Instant solutionPublication, Instant buildAfterDue) {

        private static Placement of(ProgrammingExercise exercise) {
            return new Placement(exercise.getExerciseVariantGroup() == null ? null : exercise.getExerciseVariantGroup().getId(), instant(exercise.getReleaseDate()),
                    instant(exercise.getStartDate()), instant(exercise.getDueDate()), instant(exercise.getAssessmentDueDate()),
                    instant(exercise.getExampleSolutionPublicationDate()), instant(exercise.getBuildAndTestStudentSubmissionsAfterDueDate()));
        }

        private static Placement of(ExerciseSnapshotDTO snapshot) {
            return new Placement(snapshot.variantGroupId(), instant(snapshot.releaseDate()), instant(snapshot.startDate()), instant(snapshot.dueDate()),
                    instant(snapshot.assessmentDueDate()), instant(snapshot.exampleSolutionPublicationDate()),
                    instant(snapshot.programmingData().buildAndTestStudentSubmissionsAfterDueDate()));
        }

        private boolean matchesEither(Placement before, Placement after) {
            return matches(groupId, before.groupId, after.groupId) && matches(release, before.release, after.release) && matches(start, before.start, after.start)
                    && matches(due, before.due, after.due) && matches(assessmentDue, before.assessmentDue, after.assessmentDue)
                    && matches(solutionPublication, before.solutionPublication, after.solutionPublication) && matches(buildAfterDue, before.buildAfterDue, after.buildAfterDue);
        }

        private static boolean matches(Object actual, Object before, Object after) {
            return Objects.equals(actual, before) || Objects.equals(actual, after);
        }

        private static Instant instant(ZonedDateTime value) {
            return value == null ? null : value.toInstant();
        }
    }
}
