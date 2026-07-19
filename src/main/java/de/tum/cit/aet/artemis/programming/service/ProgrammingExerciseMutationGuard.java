package de.tum.cit.aet.artemis.programming.service;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;

import de.tum.cit.aet.artemis.core.config.HazelcastConfiguration;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.hyperion.api.HyperionExerciseMutationApi;

/** Acquires Hyperion's distributed exercise mutation slot for external programming REST mutations. */
@Lazy
@Service
public class ProgrammingExerciseMutationGuard {

    private static final String ENTITY_NAME = "programmingExercise";

    private static final MutationLease NO_OP_LEASE = new MutationLease(() -> {
    });

    private final Optional<HyperionExerciseMutationApi> hyperionExerciseMutationApi;

    private final HazelcastInstance hazelcastInstance;

    private final int expectedDataMemberCount;

    @Autowired
    public ProgrammingExerciseMutationGuard(Optional<HyperionExerciseMutationApi> hyperionExerciseMutationApi, @Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance,
            @Value("${jhipster.cache.hazelcast.expected-data-member-count:1}") int expectedDataMemberCount) {
        this.hyperionExerciseMutationApi = hyperionExerciseMutationApi;
        this.hazelcastInstance = hazelcastInstance;
        this.expectedDataMemberCount = expectedDataMemberCount;
    }

    public ProgrammingExerciseMutationGuard(Optional<HyperionExerciseMutationApi> hyperionExerciseMutationApi, HazelcastInstance hazelcastInstance) {
        this(hyperionExerciseMutationApi, hazelcastInstance, 1);
    }

    /**
     * Claims the distributed mutation slot for an exercise, or a no-op lease when Hyperion is disabled.
     *
     * @param exerciseId the exercise whose generated artifacts will be mutated
     * @return a lease that releases the slot when closed
     */
    public MutationLease claimExternalMutation(long exerciseId) {
        if (hyperionExerciseMutationApi.isEmpty()) {
            rejectGenerationProfileSkew();
            return NO_OP_LEASE;
        }
        HyperionExerciseMutationApi api = hyperionExerciseMutationApi.get();
        String token = api.claimExternalMutationSlot(exerciseId);
        return new MutationLease(() -> api.clearExternalMutationSlot(exerciseId, token));
    }

    public MutationLease claimExternalMutation(OptionalLong exerciseId) {
        return exerciseId.isPresent() ? claimExternalMutation(exerciseId.getAsLong()) : NO_OP_LEASE;
    }

    private void rejectGenerationProfileSkew() {
        final List<Member> dataMembers;
        try {
            dataMembers = hazelcastInstance.getCluster().getMembers().stream().filter(member -> !member.isLiteMember()).toList();
        }
        catch (RuntimeException e) {
            throw capabilityUnavailable();
        }

        if (dataMembers.size() != expectedDataMemberCount) {
            throw new ServiceUnavailableAlertException(
                    "Hyperion coordination is configured for expected " + expectedDataMemberCount + " Hazelcast data members, but observed " + dataMembers.size()
                            + ". Set jhipster.cache.hazelcast.expected-data-member-count to the number of core/data members on every node.",
                    ENTITY_NAME, "hyperionDataMemberTopologyMismatch");
        }

        final List<String> capabilities;
        try {
            capabilities = dataMembers.stream().map(member -> member.getAttribute(HazelcastConfiguration.HYPERION_EXERCISE_GENERATION_CAPABLE_MEMBER_ATTRIBUTE)).toList();
        }
        catch (RuntimeException e) {
            throw capabilityUnavailable();
        }
        if (capabilities.stream().anyMatch(capability -> !Boolean.TRUE.toString().equals(capability) && !Boolean.FALSE.toString().equals(capability))) {
            throw capabilityUnavailable();
        }
        if (capabilities.contains(Boolean.TRUE.toString())) {
            throw new ServiceUnavailableAlertException(
                    "Hyperion exercise generation is enabled on another cluster member but unavailable on this node. Align the Hyperion feature flag and Spring profiles across core nodes.",
                    ENTITY_NAME, "hyperionExerciseGenerationProfileSkew");
        }
    }

    private static ServiceUnavailableAlertException capabilityUnavailable() {
        return new ServiceUnavailableAlertException("Cannot verify whether Hyperion exercise generation is enabled on every Hazelcast data member.", ENTITY_NAME,
                "hyperionExerciseGenerationCapabilityUnavailable");
    }

    public record MutationLease(Runnable release) implements AutoCloseable {

        @Override
        public void close() {
            release.run();
        }
    }
}
