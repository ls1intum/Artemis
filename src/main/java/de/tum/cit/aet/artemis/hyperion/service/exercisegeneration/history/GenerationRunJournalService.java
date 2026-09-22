package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmittedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationCancellationEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationDispatchFailedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationIncompleteException;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.GenerationPersistenceService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/** Mandatory version provenance before writes; bounded replay and bounded activity metadata have separate lifetimes. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationRunJournalService {

    private static final Logger log = LoggerFactory.getLogger(GenerationRunJournalService.class);

    private final GenerationRunStoreService runs;

    private final ExerciseVersionService versions;

    private final ExerciseVersionRepository versionRepository;

    public GenerationRunJournalService(GenerationRunStoreService runs, ExerciseVersionService versions, ExerciseVersionRepository versionRepository) {
        this.runs = runs;
        this.versions = versions;
        this.versionRepository = versionRepository;
    }

    /**
     * Creates the retained identity before the asynchronous listener can start work. Failure aborts dispatch.
     *
     * @param event the admitted run
     */
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void admitted(GenerationAdmittedEvent event) {
        started(event.run());
    }

    /** Records the admitted run before any asynchronous execution or variant draft commit. */
    public void started(GenerationStartedEvent event) {
        var run = new AuthoringRun();
        run.setJobId(event.jobId());
        run.setExerciseId(event.exercise().getId());
        run.setOwnerId(event.user().getId());
        run.setSourceExerciseId(event.variantPreparation() == null ? event.exercise().getId() : event.variantPreparation().sourceExerciseId());
        run.setKind(event.variantPreparation() != null ? AuthoringRun.Kind.VARIANT : event.mode() == GenerationMode.ADAPT ? AuthoringRun.Kind.ADAPT : AuthoringRun.Kind.CREATE);
        run.setStartedAt(Instant.now());
        runs.save(run);
    }

    /**
     * Records the complete prior version before the first Git or metadata mutation; failures propagate and prevent that mutation.
     *
     * @param jobId         admitted run identifier
     * @param exercise      mutation-guarded destination
     * @param user          author
     * @param baselineHeads verified seed commits for all three repositories
     * @param branch        actual branch used for persistence and restoration
     */
    public void beforeMutation(String jobId, ProgrammingExercise exercise, User user, Map<RepositoryType, String> baselineHeads, String branch) {
        var run = runs.findByJobId(jobId).orElseThrow(() -> new IllegalStateException("The authoring run was not recorded before dispatch"));
        if (!Objects.equals(run.getExerciseId(), exercise.getId()) || !Objects.equals(run.getOwnerId(), user.getId())) {
            throw new IllegalStateException("Authoring version provenance does not match the admitted destination and author");
        }
        if (run.getMutationStartedAt() != null) {
            if (run.getBeforeVersionId() == null || run.getFinishedAt() != null || !Objects.equals(branch, run.getRepositoryBranch())
                    || runs.findLatestMutation(exercise.getId()).stream().noneMatch(latest -> jobId.equals(latest.getJobId()))) {
                throw new IllegalStateException("The authoring recovery baseline is no longer usable");
            }
            return;
        }
        Long created = versions.createExerciseVersionOrThrow(exercise, user, baselineHeads);
        long versionId = resolveVersion(exercise.getId(), created);
        if (runs.linkBeforeVersion(jobId, versionId, branch, Instant.now()) != 1) {
            throw new IllegalStateException("The authoring run is no longer eligible to begin persistence");
        }
    }

    /**
     * Links the saved version before a success can be announced. A journal failure after saving is explicitly partial.
     *
     * @param jobId    admitted run
     * @param exercise mutation-guarded destination after placement
     * @param user     author
     * @param result   completed persistence result
     * @return the complete final version id, or null for a no-op run
     */
    public Long afterMutation(String jobId, ProgrammingExercise exercise, User user, GenerationPersistenceService.PersistResult result) {
        try {
            var run = runs.findByJobId(jobId).orElseThrow();
            if (run.getMutationStartedAt() == null) {
                return result.savedExerciseVersionId();
            }
            var heads = new EnumMap<RepositoryType, String>(RepositoryType.class);
            var previous = versionRepository.findById(run.getBeforeVersionId()).orElseThrow().getExerciseSnapshot().programmingData();
            heads.putAll(Map.of(RepositoryType.TEMPLATE, previous.templateParticipation().commitId(), RepositoryType.SOLUTION, previous.solutionParticipation().commitId(),
                    RepositoryType.TESTS, previous.testsCommitId()));
            heads.putAll(result.postPersistHeads());
            long versionId = resolveVersion(run.getExerciseId(), versions.createExerciseVersionOrThrow(exercise, user, heads));
            if (runs.linkAfterVersion(jobId, versionId) != 1) {
                throw new IllegalStateException("The saved authoring version could not be linked to its run");
            }
            return versionId;
        }
        catch (RuntimeException exception) {
            throw new GenerationIncompleteException(
                    "The exercise was saved, but its retained recovery reference could not be completed. Inspect the exercise versions before using it.", exception, true,
                    result.postPersistHeads());
        }
    }

    /**
     * Stores only terminal accounting of the outcome, not streamed model/tool content. Replay remains available if this write fails.
     *
     * @param jobId run whose terminal event was accepted
     * @param event terminal event
     */
    public void completed(String jobId, ExerciseGenerationEventDTO event) {
        AuthoringRun.Status status = switch (event.type()) {
            case DONE -> switch (event.completionStatus()) {
                case SUCCESS -> AuthoringRun.Status.SAVED;
                case NEEDS_REVIEW -> AuthoringRun.Status.NEEDS_REVIEW;
                case PARTIAL -> AuthoringRun.Status.PARTIAL;
                case null -> AuthoringRun.Status.UNKNOWN;
            };
            case ERROR -> AuthoringRun.Status.ERROR;
            case CANCELLED -> AuthoringRun.Status.CANCELLED;
            default -> null;
        };
        if (status == null) {
            return;
        }
        try {
            runs.complete(jobId, status, event.timestamp(), event.liveExerciseChanged());
        }
        catch (RuntimeException exception) {
            log.error("Could not retain the terminal outcome for authoring run {}; history must treat missing outcomes as unknown", jobId, exception);
        }
    }

    /**
     * Cancellation can terminalize replay before the worker exits; retain it even if the worker's duplicate event is rejected.
     *
     * @param cancellation accepted owner cancellation
     */
    @EventListener
    public void cancelled(GenerationCancellationEvent cancellation) {
        completed(cancellation.jobId(), cancellation.event());
    }

    /**
     * Retains dispatch failures even when no worker took responsibility and temporary replay was rolled back.
     *
     * @param failure run whose publication failed
     */
    @EventListener
    public void dispatchFailed(GenerationDispatchFailedEvent failure) {
        completed(failure.jobId(), ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, "The authoring executor could not start."));
    }

    private long resolveVersion(long exerciseId, Long createdVersionId) {
        return createdVersionId != null ? createdVersionId
                : versionRepository.findTopByExerciseIdOrderByCreatedDateDesc(exerciseId).orElseThrow(() -> new IllegalStateException("The complete exercise version is missing"))
                        .getId();
    }
}
