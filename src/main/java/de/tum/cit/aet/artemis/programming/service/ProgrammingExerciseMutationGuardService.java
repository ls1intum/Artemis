package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;

/** Acquires Hyperion's distributed exercise mutation slot for external programming REST mutations. */
@Lazy
@Service
// Both profiles are named, rather than relying on every deployment pairing localvc with core, so the bean exists wherever an injection point does.
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
public class ProgrammingExerciseMutationGuardService {

    private static final MutationLease NO_OP_LEASE = new MutationLease(() -> {
    });

    private final Optional<HyperionExerciseMutationApi> hyperionExerciseMutationApi;

    @Autowired
    public ProgrammingExerciseMutationGuardService(Optional<HyperionExerciseMutationApi> hyperionExerciseMutationApi) {
        this.hyperionExerciseMutationApi = hyperionExerciseMutationApi;
    }

    /**
     * Claims the distributed mutation slot for an exercise, or a no-op lease when Hyperion is disabled.
     *
     * @param exerciseId the exercise whose generated artifacts will be mutated
     * @return a lease that releases the slot when closed
     */
    public MutationLease claimExternalMutation(long exerciseId) {
        if (hyperionExerciseMutationApi.isEmpty()) {
            return NO_OP_LEASE;
        }
        HyperionExerciseMutationApi api = hyperionExerciseMutationApi.get();
        String token = api.claimExternalMutationSlot(exerciseId);
        return new MutationLease(() -> api.clearExternalMutationSlot(exerciseId, token));
    }

    public MutationLease claimExternalMutation(OptionalLong exerciseId) {
        return exerciseId.isPresent() ? claimExternalMutation(exerciseId.getAsLong()) : NO_OP_LEASE;
    }

    public record MutationLease(Runnable release) implements AutoCloseable {

        @Override
        public void close() {
            release.run();
        }
    }
}
