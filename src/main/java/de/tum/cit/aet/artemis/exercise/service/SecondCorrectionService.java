package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.OptionalLong;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseMutationGuardService;

/**
 * Toggles an exercise's second-correction round, holding Hyperion's mutation guard for the whole operation and recording the resulting version.
 * <p>
 * A service of its own rather than a method on {@link ExerciseVersionService}: that service is reachable from the LocalVC git servlet and therefore instantiated during startup, so
 * giving it the mutation guard would pull the guard — and Hazelcast behind it — into the startup graph of every node to serve one REST endpoint.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class SecondCorrectionService {

    private final ExerciseRepository exerciseRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final ProgrammingExerciseMutationGuardService mutationGuard;

    public SecondCorrectionService(ExerciseRepository exerciseRepository, ExerciseVersionService exerciseVersionService, ProgrammingExerciseMutationGuardService mutationGuard) {
        this.exerciseRepository = exerciseRepository;
        this.exerciseVersionService = exerciseVersionService;
        this.mutationGuard = mutationGuard;
    }

    /**
     * Toggles second correction and records the new state as an exercise version.
     * <p>
     * The guard matters because a Hyperion generation may own the exercise: it rewrites repositories and metadata, and a concurrent toggle would race that. Only programming
     * exercises can be owned, and only they version synchronously, because the caller's response asserts the change is already durable.
     *
     * @param exerciseId the exercise to toggle
     * @param user       the user performing the toggle, recorded as the version's author
     * @return the new second-correction state
     */
    public boolean toggle(long exerciseId, User user) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        OptionalLong guardedExercise = exercise instanceof ProgrammingExercise ? OptionalLong.of(exerciseId) : OptionalLong.empty();
        try (var lease = mutationGuard.claimExternalMutation(guardedExercise)) {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
            boolean secondCorrectionEnabled = exerciseRepository.toggleSecondCorrection(exercise);
            if (exercise instanceof ProgrammingExercise) {
                exerciseVersionService.createExerciseVersionSynchronously(exercise, user);
            }
            else {
                exerciseVersionService.createExerciseVersion(exercise, user);
            }
            return secondCorrectionEnabled;
        }
    }
}
