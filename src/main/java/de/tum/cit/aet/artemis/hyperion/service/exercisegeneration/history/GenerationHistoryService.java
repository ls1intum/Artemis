package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.dto.AuthoringRunDTO;
import de.tum.cit.aet.artemis.hyperion.dto.AuthoringRunPageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.repository.AuthoringRunRepository;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/** Owner-scoped, bounded history with current course authorization; snapshots and model content are never loaded for listing. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationHistoryService {

    private static final int PAGE_SIZE = 50;

    private final AuthoringRunRepository runs;

    private final ProgrammingExerciseRepository exercises;

    private final AuthorizationCheckService authorization;

    private final GenerationJobService jobs;

    private final ExerciseGenerationRevertService recovery;

    public GenerationHistoryService(AuthoringRunRepository runs, ProgrammingExerciseRepository exercises, AuthorizationCheckService authorization, GenerationJobService jobs,
            ExerciseGenerationRevertService recovery) {
        this.runs = runs;
        this.exercises = exercises;
        this.authorization = authorization;
        this.jobs = jobs;
        this.recovery = recovery;
    }

    /**
     * Reads one bounded page without fetching version JSON or making one exercise query per row.
     *
     * @param user     current authenticated owner
     * @param beforeId exclusive cursor, or null for the newest page
     * @return currently authorized runs and an opaque next-page position
     */
    public AuthoringRunPageDTO history(User user, Long beforeId) {
        List<AuthoringRun> page = runs.findOwnedBefore(user.getId(), beforeId, PageRequest.of(0, PAGE_SIZE + 1));
        List<AuthoringRun> visiblePage = page.stream().limit(PAGE_SIZE).toList();
        if (visiblePage.isEmpty()) {
            return new AuthoringRunPageDTO(List.of(), null);
        }
        Map<Long, ProgrammingExercise> destinations = authorizedDestinations(user, visiblePage);
        List<AuthoringRunDTO> authorized = visiblePage.stream().filter(run -> destinations.containsKey(run.getExerciseId()))
                .map(run -> summary(run, destinations.get(run.getExerciseId()))).toList();
        return new AuthoringRunPageDTO(authorized, page.size() > PAGE_SIZE ? visiblePage.getLast().getId() : null);
    }

    /**
     * Reauthorizes a bounded batch of retained identities without loading replay, versions or model content.
     *
     * @param user   current authenticated owner
     * @param jobIds at most 500 requested run identities, validated by the resource
     * @return only requested identities still owned by this user and accessible with their current course roles
     */
    public List<String> authorizedRunIds(User user, List<String> jobIds) {
        List<AuthoringRun> owned = runs.findByOwnerIdAndJobIdIn(user.getId(), jobIds);
        Map<Long, ProgrammingExercise> destinations = authorizedDestinations(user, owned);
        return owned.stream().filter(run -> destinations.containsKey(run.getExerciseId())).map(AuthoringRun::getJobId).toList();
    }

    private Map<Long, ProgrammingExercise> authorizedDestinations(User user, List<AuthoringRun> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        return exercises.findAllWithEagerCourseAndExamByIdIn(candidates.stream().map(AuthoringRun::getExerciseId).collect(Collectors.toSet())).stream()
                .filter(exercise -> authorization.isAtLeastEditorForExercise(exercise, user)).collect(Collectors.toMap(ProgrammingExercise::getId, Function.identity()));
    }

    /**
     * Looks up a single immutable run, never substitutes the latest run on the same exercise.
     *
     * @param exerciseId authorized destination
     * @param jobId      canonical run identifier
     * @param user       current owner
     * @return replay when retained, otherwise the durable outcome with explicitly unavailable accounting
     */
    public ExerciseGenerationStatusDTO status(long exerciseId, String jobId, User user) {
        AuthoringRun run = runs.findByJobId(jobId).filter(candidate -> Objects.equals(candidate.getOwnerId(), user.getId()) && candidate.getExerciseId() == exerciseId)
                .orElseThrow(() -> new EntityNotFoundException("AuthoringRun", jobId));
        ProgrammingExercise exercise = exercises.findWithEagerCourseAndExamById(exerciseId).orElseThrow(() -> new EntityNotFoundException("ProgrammingExercise", exerciseId));
        if (!authorization.isAtLeastEditorForExercise(exercise, user)) {
            throw new EntityNotFoundException("AuthoringRun", jobId);
        }
        var revertible = recovery.findRevertibleRun(exerciseId).filter(candidate -> run.getRevertedAt() == null && jobId.equals(candidate.jobId()))
                .filter(candidate -> exercises.isUnreleasedAndWithoutStudentParticipations(exerciseId))
                .filter(candidate -> !jobs.hasActiveJob(exerciseId) || jobs.isRevertRecoveryPending(exerciseId));
        var replay = jobs.getStatus(user, exercise);
        if (run.getRevertedAt() == null && replay.isPresent() && jobId.equals(replay.get().jobId())) {
            var retained = replay.get();
            if (restorePending(run)) {
                retained = retained.withEvents(java.util.stream.Stream.concat(retained.events().stream(), terminalEvents(run).stream()).toList());
            }
            return retained.withRun(summary(run, exercise)).withRevertAvailability(revertible.isPresent(),
                    revertible.map(ExerciseGenerationRevertService.RevertibleRun::jobId).orElse(null),
                    revertible.map(ExerciseGenerationRevertService.RevertibleRun::mode).orElse(null));
        }
        boolean active = jobs.isActiveJob(exerciseId, jobId);
        return new ExerciseGenerationStatusDTO(jobId, active, mode(run), terminalEvents(run), List.of(), revertible.isPresent(),
                revertible.map(ExerciseGenerationRevertService.RevertibleRun::jobId).orElse(null), revertible.map(ExerciseGenerationRevertService.RevertibleRun::mode).orElse(null),
                true, false, null, null, ExerciseGenerationAccountingState.INCOMPLETE, null, false, null, summary(run, exercise));
    }

    private AuthoringRunDTO summary(AuthoringRun run, ProgrammingExercise exercise) {
        boolean active = jobs.isActiveJob(run.getExerciseId(), run.getJobId());
        AuthoringRun.Status status = restorePending(run) ? AuthoringRun.Status.PARTIAL : run.getFinishedAt() == null && !active ? AuthoringRun.Status.UNKNOWN : run.getStatus();
        return new AuthoringRunDTO(run.getJobId(), run.getExerciseId(), run.getSourceExerciseId(), exercise.getCourseViaExerciseGroupOrCourseMember().getId(), exercise.getTitle(),
                run.getKind(), status, active, run.getStartedAt(), run.getFinishedAt(), run.getBeforeVersionId(), run.getAfterVersionId(), run.getRevertedAt());
    }

    private static GenerationMode mode(AuthoringRun run) {
        return run.getKind() == AuthoringRun.Kind.CREATE ? GenerationMode.GENERATE : GenerationMode.ADAPT;
    }

    private static boolean restorePending(AuthoringRun run) {
        return run.getRestoreStartedAt() != null && run.getRevertedAt() == null;
    }

    private static List<ExerciseGenerationEventDTO> terminalEvents(AuthoringRun run) {
        if (restorePending(run)) {
            return List.of(new ExerciseGenerationEventDTO(ExerciseGenerationEventDTO.Type.DONE,
                    "Restoration is incomplete. Retry the selected undo or request administrator reconciliation.", null, ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, null,
                    true, null, run.getAfterVersionId(), null, null, null, null, run.getRestoreStartedAt()));
        }
        if (run.getFinishedAt() == null || run.getStatus() == AuthoringRun.Status.UNKNOWN || run.getStatus() == AuthoringRun.Status.QUEUED) {
            return List.of();
        }
        var type = switch (run.getStatus()) {
            case SAVED, NEEDS_REVIEW, PARTIAL -> ExerciseGenerationEventDTO.Type.DONE;
            case CANCELLED -> ExerciseGenerationEventDTO.Type.CANCELLED;
            default -> ExerciseGenerationEventDTO.Type.ERROR;
        };
        var completion = switch (run.getStatus()) {
            case SAVED -> ExerciseGenerationEventDTO.CompletionStatus.SUCCESS;
            case NEEDS_REVIEW -> ExerciseGenerationEventDTO.CompletionStatus.NEEDS_REVIEW;
            case PARTIAL -> ExerciseGenerationEventDTO.CompletionStatus.PARTIAL;
            default -> null;
        };
        return List.of(new ExerciseGenerationEventDTO(type, "The durable outcome is available. Detailed progress and usage are no longer retained.", null, completion, null,
                run.getLiveExerciseChanged(), null, run.getAfterVersionId(), null, null, null, null, run.getFinishedAt()));
    }
}
