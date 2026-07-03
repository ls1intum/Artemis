package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.hazelcast.config.MapConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Provides the safety net for {@code ADAPT} runs: capture each repository's pre-run commit HEAD at job start, and later reset the template/solution/tests repositories back to that
 * captured state ("revert this adaptation"). This is the deliberately simple alternative to a staging/approval state machine — an accepted adaptation is applied to the live
 * exercise immediately (like a manual instructor edit), and this service lets the instructor undo it in one click.
 * <p>
 * The captured baselines live in a TTL-bounded Hazelcast map keyed by exercise id (the most recent adaptation wins), so a revert works from any node and after the generation job's
 * slot is gone.
 */
@Lazy
@Service
@Conditional(HyperionEnabled.class)
public class ExerciseAdaptationRevertService {

    private static final Logger log = LoggerFactory.getLogger(ExerciseAdaptationRevertService.class);

    private static final String BASELINE_MAP_NAME = "hyperion-exercise-adaptation-baselines";

    /**
     * How long a captured baseline stays revertible; generous so an instructor can undo an adaptation well after the run finished, but bounded so the map never grows unbounded.
     */
    private static final int BASELINE_TTL_SECONDS = 7 * 24 * 60 * 60;

    /** The repositories reset by a revert, in the same order the persist commits them (tests last so the re-sync build sees the reverted solution). */
    private static final RepositoryType[] REVERT_ORDER = { RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS };

    private final HazelcastInstance hazelcastInstance;

    private final GitService gitService;

    private final GenerationPersistenceService persistenceService;

    private final String defaultBranch;

    private IMap<Long, AdaptationBaseline> baselineMap;

    public ExerciseAdaptationRevertService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance, GitService gitService,
            GenerationPersistenceService persistenceService, @Value("${artemis.version-control.default-branch:main}") String defaultBranch) {
        this.hazelcastInstance = hazelcastInstance;
        this.gitService = gitService;
        this.persistenceService = persistenceService;
        this.defaultBranch = defaultBranch;
    }

    @PostConstruct
    public void init() {
        MapConfig baselineMapConfig = hazelcastInstance.getConfig().getMapConfig(BASELINE_MAP_NAME);
        baselineMapConfig.setTimeToLiveSeconds(BASELINE_TTL_SECONDS);
        baselineMap = hazelcastInstance.getMap(BASELINE_MAP_NAME);
    }

    /**
     * Records a revertible baseline for an adaptation that was ACCEPTED and applied to the live repositories in place, from the pre-persist commit HEADs the persist captured just
     * before it overwrote each repository. It is written ONLY on that accepted-and-applied path (never at job start), so a cancelled, rejected, or errored run — which leaves the
     * live repositories unchanged — can never overwrite a prior accepted adaptation's baseline with the current (post-adaptation) HEAD and make that earlier change non-revertible.
     * A repository with no captured pre-persist HEAD (nothing was committed to it, or it had no prior commit) is omitted and not reverted. Best-effort: a failure only means the
     * run
     * cannot be reverted, never that the persisted adaptation is unsound — so it never throws.
     *
     * @param exercise           the exercise that was adapted
     * @param jobId              the adaptation job id
     * @param preAdaptationHeads the per-repository commit HEAD captured immediately before the accepted adaptation was committed in place
     */
    public void recordBaseline(ProgrammingExercise exercise, String jobId, Map<RepositoryType, String> preAdaptationHeads) {
        try {
            Map<RepositoryType, String> heads = new LinkedHashMap<>();
            for (RepositoryType repositoryType : REVERT_ORDER) {
                String head = preAdaptationHeads.get(repositoryType);
                if (head != null) {
                    heads.put(repositoryType, head);
                }
            }
            baselineMap.put(exercise.getId(), new AdaptationBaseline(jobId, heads));
            log.info("Recorded revertible adaptation baseline for exercise {} (job {}): {} repository head(s)", exercise.getId(), jobId, heads.size());
        }
        catch (RuntimeException e) {
            log.warn("Could not record the adaptation baseline for exercise {} (job {}); this run will not be revertible: {}", exercise.getId(), jobId, e.getMessage());
        }
    }

    /**
     * Reverts the most recent adaptation of the exercise: resets template/solution/tests back to the commits captured at that run's start, then re-synchronises grading. Idempotent
     * against a missing baseline (returns {@code false}); the baseline is consumed on a successful revert so it is not offered twice.
     *
     * @param exercise the exercise to revert
     * @param user     the instructor performing the revert (exercise-version author)
     * @return the revert result, or empty when there is no retained baseline to revert to
     */
    public Optional<RevertResult> revert(ProgrammingExercise exercise, User user) {
        AdaptationBaseline baseline = baselineMap.get(exercise.getId());
        if (baseline == null) {
            return Optional.empty();
        }
        RevertResult result = revertToBaseline(exercise, user, baseline);
        // Consume the baseline so the same adaptation cannot be reverted twice (a second revert would reset onto an unrelated state).
        baselineMap.remove(exercise.getId());
        return Optional.of(result);
    }

    /**
     * Resets the exercise's repositories back to the captured baseline commits and re-synchronises grading. Package-private and baseline-driven (no Hazelcast) so the git behaviour
     * is unit-testable with a mocked {@link GitService}.
     *
     * @param exercise the exercise to revert
     * @param user     the instructor performing the revert
     * @param baseline the captured pre-run baseline
     * @return which repositories were reverted and whether every captured repository was reverted successfully
     */
    RevertResult revertToBaseline(ProgrammingExercise exercise, User user, AdaptationBaseline baseline) {
        List<RepositoryType> reverted = new ArrayList<>();
        boolean fullyReverted = true;
        for (RepositoryType repositoryType : REVERT_ORDER) {
            String head = baseline.headFor(repositoryType);
            LocalVCRepositoryUri uri = exercise.getRepositoryURI(repositoryType);
            if (head == null || uri == null) {
                continue;
            }
            try {
                Repository repository = gitService.getOrCheckoutRepository(uri, true, defaultBranch, false);
                if (repository == null) {
                    throw new IllegalStateException("Could not check out the repository to revert it");
                }
                gitService.resetToCommitAndForcePush(repository, head, defaultBranch);
                reverted.add(repositoryType);
                log.info("Reverted the {} repository of exercise {} back to its pre-adaptation commit {}", repositoryType, exercise.getId(), head);
            }
            catch (Exception e) {
                fullyReverted = false;
                log.error("Failed to revert the {} repository of exercise {} back to {}; the exercise may be inconsistent", repositoryType, exercise.getId(), head, e);
            }
        }
        // Re-sync grading to the reverted tests (best-effort); the tests HEAD is the captured baseline commit we just reset to.
        persistenceService.resyncAfterRevert(exercise, user, baseline.headFor(RepositoryType.TESTS));
        return new RevertResult(fullyReverted, List.copyOf(reverted));
    }

    /**
     * The outcome of reverting an adaptation.
     *
     * @param fullyReverted        {@code true} if every captured repository was reset successfully; {@code false} if any could not be (needs manual review)
     * @param revertedRepositories the repositories that were reset back to their baseline commit
     */
    public record RevertResult(boolean fullyReverted, List<RepositoryType> revertedRepositories) {
    }
}
