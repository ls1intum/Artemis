package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService.JobInfo;

/** Protects writes on every writer node, without requiring generation, models, or a worker connection on that node. */
@Lazy
@Service
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
public class GenerationExternalMutationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationExternalMutationService.class);

    private static final String DISABLED_TOKEN = "fencing-disabled";

    private static final String PARTICIPATION_PREFIX = GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX + "participation:";

    private final DistributedDataProvider distributedDataProvider;

    private final int expectedDataMemberCount;

    private final boolean clusterFencingEnabled;

    private final Set<PendingRelease> pendingReleases = ConcurrentHashMap.newKeySet();

    public GenerationExternalMutationService(DistributedDataProvider distributedDataProvider, int expectedDataMemberCount) {
        this(distributedDataProvider, expectedDataMemberCount, false);
    }

    @Autowired
    public GenerationExternalMutationService(DistributedDataProvider distributedDataProvider,
            @Value("${jhipster.cache.hazelcast.expected-data-member-count:1}") int expectedDataMemberCount,
            @Value("${artemis.hyperion.generation.cluster-fencing-enabled:${artemis.hyperion.exercise-generation.enabled:false}}") boolean clusterFencingEnabled) {
        this.distributedDataProvider = distributedDataProvider;
        this.expectedDataMemberCount = expectedDataMemberCount;
        this.clusterFencingEnabled = clusterFencingEnabled;
    }

    /**
     * Claims a non-expiring slot when deployment-wide fencing is enabled; otherwise performs no coordination.
     *
     * @param exerciseId exercise to protect
     * @return exact ownership token
     */
    public String claimExternalMutationSlot(long exerciseId) {
        if (!clusterFencingEnabled) {
            return DISABLED_TOKEN;
        }
        String owner = requireWriterNode();
        String token = GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX + UUID.randomUUID();
        try {
            return claim(distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME), owner, exerciseId, token, this::requireWriterNode);
        }
        catch (RuntimeException failedClaim) {
            clearExternalMutationSlot(exerciseId, token);
            throw failedClaim;
        }
    }

    /** @return whether exercise writers participate in deployment-wide generation coordination */
    public boolean isCoordinationEnabled() {
        return clusterFencingEnabled;
    }

    private String requireWriterNode() {
        new GenerationClusterTopology(distributedDataProvider, expectedDataMemberCount).verifyAllMembers();
        return distributedDataProvider.getLocalNodeId();
    }

    /**
     * Reserves a template copy when fencing is enabled. Readers may coexist; the shared slot excludes writers until the last copy finishes.
     *
     * @param exerciseId exercise to reserve
     * @return exact reservation token
     */
    public String claimParticipationSlot(long exerciseId) {
        if (!clusterFencingEnabled) {
            return DISABLED_TOKEN;
        }
        String owner = requireWriterNode();
        String token = UUID.randomUUID().toString();
        try {
            return claimParticipation(exerciseId, owner, token);
        }
        catch (RuntimeException failedClaim) {
            clearParticipationSlot(exerciseId, token);
            throw failedClaim;
        }
    }

    private String claimParticipation(long exerciseId, String owner, String token) {
        String key = String.valueOf(exerciseId);
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        jobs.lock(key);
        try {
            requireWriterNode();
            JobInfo current = jobs.get(key);
            if (current != null && (!current.jobId().startsWith(PARTICIPATION_PREFIX) || current.participationOwners() == null)) {
                throw new ConflictException("Exercise authoring or recovery is running; wait before starting the exercise.", "hyperionExerciseGeneration",
                        "exerciseGenerationRunning");
            }
            var owners = new HashMap<String, String>();
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
        release(new PendingRelease(exerciseId, token, true, null, false));
    }

    private void clearParticipation(long exerciseId, String token) {
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            JobInfo current = jobs.get(key);
            if (current == null || current.participationOwners() == null || !current.participationOwners().containsKey(token)) {
                return;
            }
            var owners = new HashMap<>(current.participationOwners());
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
        if (!token.startsWith(GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX)) {
            return;
        }
        release(new PendingRelease(exerciseId, token, false, null, false));
    }

    void releaseRevertSlot(long exerciseId, String token, @Nullable JobInfo previousRecovery) {
        if (!token.startsWith(GenerationJobService.REVERT_JOB_PREFIX)) {
            return;
        }
        release(new PendingRelease(exerciseId, token, false, previousRecovery, false));
    }

    void retainRevertRecoverySlot(long exerciseId, String token) {
        if (!token.startsWith(GenerationJobService.REVERT_JOB_PREFIX)) {
            return;
        }
        release(new PendingRelease(exerciseId, token, false, null, true));
    }

    private void release(PendingRelease release) {
        if (!clusterFencingEnabled) {
            return;
        }
        try {
            if (release.retainRecovery()) {
                DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
                GenerationRecoverySlots.retain(jobs, release.exerciseId(), release.token());
                JobInfo current = jobs.get(String.valueOf(release.exerciseId()));
                if (current != null && current.jobId().equals(release.token())) {
                    throw new IllegalStateException("The completed undo reservation was not converted to a recovery guard");
                }
            }
            else if (release.participation()) {
                clearParticipation(release.exerciseId(), release.token());
            }
            else {
                clear(distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME), release.exerciseId(), release.token(), release.previousRecovery());
            }
            pendingReleases.remove(release);
        }
        catch (RuntimeException failure) {
            if (pendingReleases.add(release)) {
                log.warn("Finished exercise operation could not release its slot for exercise {}; exact-token release will be retried.", release.exerciseId(), failure);
            }
        }
    }

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    void retryPendingReleases() {
        pendingReleases.forEach(this::release);
    }

    private record PendingRelease(long exerciseId, String token, boolean participation, @Nullable JobInfo previousRecovery, boolean retainRecovery) {
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
        if (!clusterFencingEnabled) {
            return false;
        }
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
        if (!clusterFencingEnabled) {
            return Optional.empty();
        }
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        JobInfo job = jobs.get(String.valueOf(exerciseId));
        if (job == null || job.cancellable() || !GenerationJobService.isExternalMutationJob(job)) {
            return Optional.empty();
        }
        boolean ownerAbsent = distributedDataProvider.getCoordinationSnapshot().map(snapshot -> job.ownersAbsentFrom(snapshot.ownerNodeIds())).orElse(false);
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
        if (!clusterFencingEnabled) {
            return false;
        }
        DistributedMap<String, JobInfo> jobs = distributedDataProvider.getMap(GenerationJobService.JOB_MAP_NAME);
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            Set<String> nodes = new GenerationClusterTopology(distributedDataProvider, expectedDataMemberCount).verifyMajority().ownerNodeIds();
            JobInfo current = jobs.get(key);
            return current != null && !current.cancellable() && GenerationJobService.isExternalMutationJob(current) && current.jobId().equals(token)
                    && current.ownersAbsentFrom(nodes) && jobs.remove(key, current);
        }
        finally {
            jobs.unlock(key);
        }
    }

    private static String claim(DistributedMap<String, JobInfo> jobs, String nodeId, long exerciseId, String token, Runnable validateTopology) {
        String key = String.valueOf(exerciseId);
        Instant now = Instant.now();
        JobInfo mutation = new JobInfo(token, "external", exerciseId, now, null, nodeId, now, false, null);
        jobs.lock(key);
        try {
            validateTopology.run();
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

    private static void clear(DistributedMap<String, JobInfo> jobs, long exerciseId, String token, @Nullable JobInfo previousRecovery) {
        String key = String.valueOf(exerciseId);
        jobs.lock(key);
        try {
            JobInfo current = jobs.get(key);
            if (current != null && current.jobId().equals(token)) {
                boolean released = previousRecovery == null ? jobs.remove(key, current) : jobs.replace(key, current, previousRecovery);
                if (!released) {
                    throw new IllegalStateException("The reservation changed during exact-token cleanup");
                }
            }
        }
        finally {
            jobs.unlock(key);
        }
    }
}
