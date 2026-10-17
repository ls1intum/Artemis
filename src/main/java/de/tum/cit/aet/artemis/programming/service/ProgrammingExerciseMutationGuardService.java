package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/** Acquires the live-save coordination boundary for external programming REST mutations. */
@Lazy
@Service
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
public class ProgrammingExerciseMutationGuardService {

    private static final MutationLease NO_OP_LEASE = new MutationLease(() -> {
    }, false);

    private final Optional<HyperionExerciseMutationApi> hyperionExerciseMutationApi;

    private final ProgrammingExerciseRepository exercises;

    public ProgrammingExerciseMutationGuardService(Optional<HyperionExerciseMutationApi> hyperionExerciseMutationApi, ProgrammingExerciseRepository exercises) {
        this.exercises = exercises;
        this.hyperionExerciseMutationApi = hyperionExerciseMutationApi;
    }

    /** @return whether external mutations use the live-save coordination boundary */
    public boolean isCoordinationEnabled() {
        return hyperionExerciseMutationApi.map(HyperionExerciseMutationApi::isCoordinationEnabled).orElse(false);
    }

    /**
     * Claims the live-save boundary when generation is enabled consistently on all exercise writer nodes.
     *
     * @param exerciseId the exercise whose generated artifacts will be mutated
     * @return a lease that releases the slot when closed
     */
    public MutationLease claimExternalMutation(long exerciseId) {
        if (!isCoordinationEnabled()) {
            return NO_OP_LEASE;
        }
        if (exercises.existsByIdAndExerciseGroupIsNotNull(exerciseId)) {
            return NO_OP_LEASE;
        }
        HyperionExerciseMutationApi api = hyperionExerciseMutationApi.get();
        String token = api.claimExternalMutationSlot(exerciseId);
        return new MutationLease(() -> api.clearExternalMutationSlot(exerciseId, token));
    }

    public MutationLease claimExternalMutation(OptionalLong exerciseId) {
        return exerciseId.isPresent() ? claimExternalMutation(exerciseId.getAsLong()) : NO_OP_LEASE;
    }

    public record MutationLease(Runnable release, boolean coordinated) implements AutoCloseable {

        public MutationLease(Runnable release) {
            this(release, true);
        }

        @Override
        public void close() {
            release.run();
        }
    }
}
