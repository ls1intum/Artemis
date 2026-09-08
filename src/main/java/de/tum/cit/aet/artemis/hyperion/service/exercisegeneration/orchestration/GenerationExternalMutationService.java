package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

/** Protects writes on every writer node, without requiring generation, models, or a worker connection on that node. */
@Lazy
@Service
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
public class GenerationExternalMutationService {

    private final DistributedDataProvider distributedDataProvider;

    private final int expectedDataMemberCount;

    private final boolean coordinationSupported;

    public GenerationExternalMutationService(DistributedDataProvider distributedDataProvider,
            @Value("${jhipster.cache.hazelcast.expected-data-member-count:1}") int expectedDataMemberCount,
            @Value("${artemis.distributed-data.provider:Hazelcast}") String providerName) {
        this.distributedDataProvider = distributedDataProvider;
        this.expectedDataMemberCount = expectedDataMemberCount;
        this.coordinationSupported = !"Redis".equalsIgnoreCase(providerName);
    }

    /**
     * Claims a non-expiring slot; a crashed writer requires audited recovery rather than automatic reuse.
     *
     * @param exerciseId exercise to protect
     * @return exact ownership token
     */
    public String claimExternalMutationSlot(long exerciseId) {
        // Redis deployments cannot admit generation because they lack authoritative data-member topology.
        if (!coordinationSupported) {
            return "unsupported-provider";
        }
        String owner = distributedDataProvider.getLocalNodeId();
        if (!distributedDataProvider.getDataNodeIds().map(nodes -> nodes.contains(owner)).orElse(false)) {
            throw new ServiceUnavailableAlertException("Exercise writers must be connected data members for generation coordination.", "hyperionExerciseGeneration",
                    "hyperionDataMemberTopologyUnavailable");
        }
        return claim(distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME), owner, exerciseId);
    }

    /**
     * Releases only the exact caller's slot, leaving any replacement owner untouched.
     *
     * @param exerciseId protected exercise
     * @param token      caller's ownership token
     */
    public void clearExternalMutationSlot(long exerciseId, String token) {
        clear(distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME), exerciseId, token);
    }

    /**
     * Diagnoses external slots even when whole-exercise generation is disabled on the serving core node.
     *
     * @param exerciseId exercise to inspect
     * @return non-cancellable external slot, when present
     */
    public Optional<GenerationJobService.WedgedSlotInfo> getWedgedSlotInfo(long exerciseId) {
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        JobInfo job = jobs.get(String.valueOf(exerciseId));
        if (job == null || job.cancellable() || !GenerationJobService.isExternalMutationJob(job)) {
            return Optional.empty();
        }
        boolean ownerAbsent = job.ownerNodeId() != null && distributedDataProvider.getDataNodeIds().map(nodes -> !nodes.contains(job.ownerNodeId())).orElse(false);
        return Optional.of(new GenerationJobService.WedgedSlotInfo(exerciseId, job.jobId(), GenerationJobService.WedgedSlotKind.EXTERNAL_MUTATION, job.ownerNodeId(),
                job.startedAt(), ownerAbsent));
    }

    /**
     * Exact-token recovery only after an administrator has confirmed the departed owner's JVM is stopped.
     *
     * @param exerciseId exercise to recover
     * @param token      exact token confirmed by the administrator
     * @return whether the matching slot was removed
     */
    public boolean recoverWedgedSlot(long exerciseId, String token) {
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            Set<String> nodes = distributedDataProvider.getDataNodeIds()
                    .orElseThrow(() -> new ServiceUnavailableAlertException("The distributed provider cannot verify slot recovery topology.", "hyperionExerciseGeneration",
                            "hyperionDataMemberTopologyUnavailable"));
            if (nodes.size() <= expectedDataMemberCount / 2 || nodes.size() > expectedDataMemberCount) {
                throw new ServiceUnavailableAlertException("Slot recovery requires a majority of the configured data members.", "hyperionExerciseGeneration",
                        "hyperionDataMemberTopologyMismatch");
            }
            JobInfo current = jobs.get(key);
            return current != null && !current.cancellable() && GenerationJobService.isExternalMutationJob(current) && current.jobId().equals(token)
                    && current.ownerNodeId() != null && !nodes.contains(current.ownerNodeId()) && jobs.remove(key, current);
        }
        finally {
            jobs.unlock(key);
        }
    }

    static String claim(DistributedMap<String, JobInfo> jobs, String nodeId, long exerciseId) {
        String key = String.valueOf(exerciseId);
        String token = GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX + UUID.randomUUID();
        Instant now = Instant.now();
        JobInfo mutation = new JobInfo(token, "external", exerciseId, now, null, nodeId, now, false, null);
        jobs.lock(key);
        try {
            if (jobs.putIfAbsent(key, mutation) != null) {
                throw new ConflictException("Exercise generation or another mutation is running; wait for it to finish before making changes.", "hyperionExerciseGeneration",
                        "exerciseGenerationRunning");
            }
            return token;
        }
        finally {
            jobs.unlock(key);
        }
    }

    static void clear(DistributedMap<String, JobInfo> jobs, long exerciseId, String token) {
        if (!token.startsWith(GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX)) {
            return;
        }
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            JobInfo current = jobs.get(key);
            if (current != null && current.jobId().equals(token)) {
                jobs.remove(key, current);
            }
        }
        finally {
            jobs.unlock(key);
        }
    }
}
