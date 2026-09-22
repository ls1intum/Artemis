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

    private static final String PARTICIPATION_PREFIX = GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX + "participation:";

    private final DistributedDataProvider distributedDataProvider;

    private final int expectedDataMemberCount;

    public GenerationExternalMutationService(DistributedDataProvider distributedDataProvider,
            @Value("${jhipster.cache.hazelcast.expected-data-member-count:1}") int expectedDataMemberCount) {
        this.distributedDataProvider = distributedDataProvider;
        this.expectedDataMemberCount = expectedDataMemberCount;
    }

    /**
     * Claims a non-expiring slot; a crashed writer requires audited recovery rather than automatic reuse.
     *
     * @param exerciseId exercise to protect
     * @return exact ownership token
     */
    public String claimExternalMutationSlot(long exerciseId) {
        return claim(distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME), requireWriterNode(), exerciseId);
    }

    private String requireWriterNode() {
        GenerationRecoveryBootstrapService.requireInitialized(distributedDataProvider);
        String owner = distributedDataProvider.getLocalNodeId();
        if (!distributedDataProvider.getCoordinationSnapshot().map(snapshot -> snapshot.permitsAdmission(expectedDataMemberCount) && snapshot.ownerNodeIds().contains(owner))
                .orElse(false)) {
            throw new ServiceUnavailableAlertException("Exercise writers require a connected owner and a complete coordination view.", "hyperionExerciseGeneration",
                    "hyperionDataMemberTopologyUnavailable");
        }
        return owner;
    }

    /**
     * Reserves a template copy. Readers may coexist; the shared non-expiring slot excludes every writer until the last copy finishes.
     *
     * @param exerciseId exercise to reserve
     * @return exact reservation token
     */
    public String claimParticipationSlot(long exerciseId) {
        String owner = requireWriterNode();
        String token = UUID.randomUUID().toString();
        String key = String.valueOf(exerciseId);
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        jobs.lock(key);
        try {
            JobInfo current = jobs.get(key);
            if (current != null && (!current.jobId().startsWith(PARTICIPATION_PREFIX) || current.participationOwners() == null)) {
                throw new ConflictException("Exercise authoring or recovery is running; wait before starting the exercise.", "hyperionExerciseGeneration",
                        "exerciseGenerationRunning");
            }
            var owners = new java.util.HashMap<String, String>();
            if (current != null) {
                owners.putAll(current.participationOwners());
            }
            owners.put(token, owner);
            JobInfo updated = current == null
                    ? new JobInfo(PARTICIPATION_PREFIX + UUID.randomUUID(), "participations", exerciseId, Instant.now(), null, null, Instant.now(), false, null)
                            .withParticipationOwners(owners)
                    : current.withParticipationOwners(owners);
            if (current == null ? jobs.putIfAbsent(key, updated) != null : !jobs.replace(key, current, updated)) {
                throw new ConflictException("The exercise reservation changed; retry starting the exercise.", "hyperionExerciseGeneration", "exerciseGenerationRunning");
            }
            return token;
        }
        finally {
            jobs.unlock(key);
        }
    }

    /**
     * Releases exactly one copy reservation; a stale close cannot release another reader or a writer.
     *
     * @param exerciseId protected exercise
     * @param token      exact reservation token
     */
    public void clearParticipationSlot(long exerciseId, String token) {
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            JobInfo current = jobs.get(key);
            if (current == null || current.participationOwners() == null || !current.participationOwners().containsKey(token)) {
                return;
            }
            var owners = new java.util.HashMap<>(current.participationOwners());
            owners.remove(token);
            if (owners.isEmpty()) {
                jobs.remove(key, current);
            }
            else {
                jobs.replace(key, current, current.withParticipationOwners(owners));
            }
        }
        finally {
            jobs.unlock(key);
        }
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
     * Reports whether any slot (a generation run, a revert, or an external mutation) currently owns the exercise, with the same semantics as
     * {@link GenerationJobService#hasActiveJob(long)} but available on every writer node, including those where generation is disabled. Callers that only need to refuse a
     * read-modify-write against artifacts that are about to change (for example a student copying the template) consult this rather than claiming a slot of their own.
     *
     * @param exerciseId exercise to inspect
     * @return whether a slot is held for the exercise
     */
    public boolean isGenerationActive(long exerciseId) {
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        return jobs.get(String.valueOf(exerciseId)) != null;
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
        if (job == null || job.cancellable() || !GenerationJobService.isExternalMutationJob(job) && !GenerationRevertSlots.isPending(job)) {
            return Optional.empty();
        }
        boolean ownerAbsent = distributedDataProvider.getCoordinationSnapshot().map(snapshot -> job.ownersAbsentFrom(snapshot.ownerNodeIds())).orElse(false);
        return Optional.of(new GenerationJobService.WedgedSlotInfo(exerciseId, job.jobId(),
                GenerationRevertSlots.isPending(job) ? GenerationJobService.WedgedSlotKind.REVERT_RECOVERY : GenerationJobService.WedgedSlotKind.EXTERNAL_MUTATION,
                job.ownerNodeId(), job.startedAt(), ownerAbsent));
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
            Set<String> nodes = new GenerationClusterTopology(distributedDataProvider, expectedDataMemberCount).verifyMajority().ownerNodeIds();
            JobInfo current = jobs.get(key);
            return current != null && !current.cancellable() && (GenerationJobService.isExternalMutationJob(current) || GenerationRevertSlots.isPending(current))
                    && current.jobId().equals(token) && (GenerationRevertSlots.isPending(current) || current.ownersAbsentFrom(nodes)) && jobs.remove(key, current);
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
