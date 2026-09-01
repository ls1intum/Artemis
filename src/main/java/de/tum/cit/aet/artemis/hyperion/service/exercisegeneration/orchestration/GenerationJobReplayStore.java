package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationAccountingState;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationUsageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Stores the bounded reconnect replay for an exercise generation job. */
final class GenerationJobReplayStore {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobReplayStore.class);

    private static final String JOB_MAP_NAME = "hyperion-exercise-generation-jobs";

    private static final String CANCEL_MAP_NAME = "hyperion-exercise-generation-cancellations";

    private static final String TRANSCRIPT_MAP_NAME = "hyperion-exercise-generation-transcripts";

    static final String FILE_CHANGE_MAP_NAME = "hyperion-exercise-generation-file-changes";

    private static final String USAGE_MAP_NAME = "hyperion-exercise-generation-usage";

    static final String ARTIFACT_MAP_NAME = "hyperion-exercise-generation-artifacts";

    static final int MAX_RETAINED_EVENTS = 500;

    static final int MAX_RETAINED_FILE_CHANGES = 300;

    /** Defensive cap so a large {@code SPEC.md} cannot grow the retained distributed transcript without bound. */
    static final int MAX_SPEC_DOCUMENT_LENGTH = 20_000;

    private static final String SPEC_DOCUMENT_TRUNCATION_MARKER = "\n\n[... SPEC.md truncated to " + MAX_SPEC_DOCUMENT_LENGTH + " characters for the status API ...]";

    private static final String EVENT_TRUNCATION_MESSAGE_SUFFIX = " earlier progress events are no longer retained.";

    /**
     * Recovers the running dropped-event count from the retained marker so repeated overflows update one marker instead of accumulating one per drop. The count lives in the
     * marker's message rather than in {@code JobTranscript}, so a purely presentational counter does not change the shape of an already distributed value.
     */
    private static final Pattern EVENT_TRUNCATION_MESSAGE_PATTERN = Pattern.compile("^(\\d+)" + Pattern.quote(EVENT_TRUNCATION_MESSAGE_SUFFIX) + "$");

    private final long terminalReplayTtlSeconds;

    private DistributedMap<String, GenerationJobService.JobInfo> jobMap;

    private DistributedMap<String, Boolean> cancellationMap;

    private DistributedMap<String, GenerationJobService.JobTranscript> transcriptMap;

    private DistributedMap<String, GenerationJobService.JobFileChangeIndex> fileChangeMap;

    private DistributedMap<String, JobUsage> usageMap;

    private DistributedMap<String, GenerationJobService.JobArtifacts> artifactMap;

    private final Set<String> usageWriteFailures = ConcurrentHashMap.newKeySet();

    private final DistributedDataProvider distributedDataProvider;

    GenerationJobReplayStore(DistributedDataProvider distributedDataProvider, Duration terminalReplayTtl) {
        if (terminalReplayTtl == null || terminalReplayTtl.isZero() || terminalReplayTtl.isNegative()) {
            throw new IllegalArgumentException("artemis.hyperion.generation.terminal-replay-ttl must be positive");
        }
        // The maps are resolved on first use, never here: DistributedDataProvider map access during bean construction forces the cluster to be ready before the context finishes
        // starting,
        // which inverts the intended startup ordering.
        this.distributedDataProvider = distributedDataProvider;
        this.terminalReplayTtlSeconds = terminalReplayTtl.toSeconds();
    }

    private DistributedMap<String, GenerationJobService.JobInfo> jobMap() {
        if (jobMap == null) {
            jobMap = distributedDataProvider.getMap(JOB_MAP_NAME);
        }
        return jobMap;
    }

    private DistributedMap<String, Boolean> cancellationMap() {
        if (cancellationMap == null) {
            cancellationMap = distributedDataProvider.getExpiringMap(CANCEL_MAP_NAME, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
        return cancellationMap;
    }

    private DistributedMap<String, GenerationJobService.JobTranscript> transcriptMap() {
        if (transcriptMap == null) {
            transcriptMap = distributedDataProvider.getExpiringMap(TRANSCRIPT_MAP_NAME, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
        return transcriptMap;
    }

    private DistributedMap<String, GenerationJobService.JobFileChangeIndex> fileChangeMap() {
        if (fileChangeMap == null) {
            fileChangeMap = distributedDataProvider.getExpiringMap(FILE_CHANGE_MAP_NAME, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
        return fileChangeMap;
    }

    private DistributedMap<String, GenerationJobService.JobArtifacts> artifactMap() {
        if (artifactMap == null) {
            artifactMap = distributedDataProvider.getExpiringMap(ARTIFACT_MAP_NAME, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
        return artifactMap;
    }

    private DistributedMap<String, JobUsage> usageMap() {
        if (usageMap == null) {
            usageMap = distributedDataProvider.getExpiringMap(USAGE_MAP_NAME, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
        return usageMap;
    }

    StartedReplay initializeStart(long exerciseId, String jobId, String userLogin, GenerationMode mode, @Nullable String effortProfile) {
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            GenerationJobService.JobTranscript previousTranscript = transcriptMap().get(key);
            GenerationJobService.JobFileChangeIndex previousFileChanges = fileChangeMap().get(key);
            GenerationJobService.JobTranscript currentTranscript = new GenerationJobService.JobTranscript(jobId, userLogin, exerciseId, mode, new ArrayList<>(), false, null,
                    effortProfile);
            GenerationJobService.JobFileChangeIndex currentFileChanges = new GenerationJobService.JobFileChangeIndex(jobId, userLogin, new ArrayList<>());
            StartedReplay replay = new StartedReplay(currentTranscript, currentFileChanges, previousTranscript, previousFileChanges);
            try {
                transcriptMap().put(key, currentTranscript);
                fileChangeMap().put(key, currentFileChanges);
                // The exercise retains one run, so a previous run's unsaved candidate must not outlive the start of a new one: leaving the old draft readable while a fresh run
                // produces another would mislead an instructor about which draft they are looking at.
                artifactMap().remove(key);
                writeUsage(jobId, JobUsage.empty());
            }
            catch (RuntimeException e) {
                usageMap().remove(jobId);
                restoreReplayIfStillCurrent(key, replay);
                throw e;
            }
            return replay;
        }
        finally {
            jobMap().unlock(key);
        }
    }

    void restoreUnpublishedStart(long exerciseId, StartedReplay replay) {
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            String jobId = replay.currentTranscript().jobId();
            usageMap().remove(jobId);
            usageWriteFailures.remove(jobId);
            restoreReplayIfStillCurrent(key, replay);
        }
        finally {
            jobMap().unlock(key);
        }
    }

    /** Adds durably recorded provider usage to the fail-soft transient aggregate. A missing accumulator is recreated as permanently incomplete. */
    void recordUsage(String jobId, LLMRequest request) {
        recordIntoUsage(jobId, usage -> usage.add(request));
    }

    void recordToolCalls(String jobId, long count) {
        if (count < 0) {
            throw new IllegalArgumentException("Tool call count cannot be negative");
        }
        recordIntoUsage(jobId, usage -> usage.addToolCalls(count));
    }

    void recordAgentTurn(String jobId) {
        recordIntoUsage(jobId, JobUsage::addAgentTurn);
    }

    void recordAttempt(String jobId) {
        recordIntoUsage(jobId, JobUsage::addAttempt);
    }

    /** Marks this run's accounting permanently incomplete after an admitted provider attempt whose usage could not be proved. Sticky: no later seal can undo it. */
    void markUsageIncomplete(String jobId) {
        if (transitionUsage(jobId, JobUsage::markIncomplete)) {
            usageWriteFailures.remove(jobId);
        }
    }

    /** Seals a pending account only after the caller can prove that no further provider call can add usage. */
    void sealUsage(String jobId) {
        if (usageWriteFailures.contains(jobId)) {
            markUsageIncomplete(jobId);
        }
        else {
            transitionUsage(jobId, JobUsage::seal);
        }
    }

    /** Freezes this run's accounting as permanently incomplete, for a worker that stops without ever proving that its provider spend was fully recorded. */
    void sealUsageIncomplete(String jobId) {
        markUsageIncomplete(jobId);
    }

    /** Reads usage and completeness together. Missing or unreadable evidence is permanently incomplete, never pending. */
    UsageSnapshot usageSnapshot(String jobId) {
        try {
            JobUsage usage = usageMap().get(jobId);
            if (usage == null) {
                return UsageSnapshot.EVIDENCE_GONE;
            }
            ExerciseGenerationAccountingState state = usageWriteFailures.contains(jobId) ? ExerciseGenerationAccountingState.INCOMPLETE : usage.accountingState();
            return new UsageSnapshot(usage.toDTO(), state);
        }
        catch (RuntimeException exception) {
            log.warn("Could not read exercise generation usage for job {}; reporting the account as incomplete", jobId, exception);
            return UsageSnapshot.EVIDENCE_GONE;
        }
    }

    private void recordIntoUsage(String jobId, UnaryOperator<JobUsage> record) {
        DistributedMap<String, JobUsage> map = null;
        boolean locked = false;
        try {
            map = usageMap();
            map.lock(jobId);
            locked = true;
            JobUsage current = map.get(jobId);
            if (current == null) {
                log.warn("Opening an unaccounted usage accumulator for exercise generation job {}: no accumulator was retained for it, so its aggregate cannot become a complete "
                        + "account of the run. The durable per-call token usage records are unaffected.", jobId);
                current = JobUsage.unaccounted();
            }
            map.put(jobId, record.apply(current), Duration.ofSeconds(terminalReplayTtlSeconds));
        }
        catch (RuntimeException exception) {
            usageWriteFailures.add(jobId);
            log.warn("Could not update transient exercise generation usage for job {}; the durable per-call usage record is unaffected", jobId, exception);
        }
        finally {
            unlockUsage(map, jobId, locked);
        }
    }

    /** Applies a completeness transition without inventing missing evidence. */
    private boolean transitionUsage(String jobId, UnaryOperator<JobUsage> transition) {
        DistributedMap<String, JobUsage> map = null;
        boolean locked = false;
        try {
            map = usageMap();
            map.lock(jobId);
            locked = true;
            JobUsage current = map.get(jobId);
            if (current == null) {
                log.debug("Skipped a usage completeness transition for exercise generation job {}: no accumulator is retained, so it already reads as incomplete", jobId);
                return false;
            }
            map.put(jobId, transition.apply(current), Duration.ofSeconds(terminalReplayTtlSeconds));
            return true;
        }
        catch (RuntimeException exception) {
            log.warn("Could not transition transient exercise generation usage for job {}", jobId, exception);
            return false;
        }
        finally {
            unlockUsage(map, jobId, locked);
        }
    }

    private static void unlockUsage(@Nullable DistributedMap<String, JobUsage> map, String jobId, boolean locked) {
        if (!locked || map == null) {
            return;
        }
        try {
            map.unlock(jobId);
        }
        catch (RuntimeException exception) {
            log.warn("Could not unlock transient exercise generation usage for job {}", jobId, exception);
        }
    }

    /** Applies the retention bound on every write, including runs whose worker never reaches normal cleanup. */
    private void writeUsage(String jobId, JobUsage usage) {
        usageMap().put(jobId, usage, Duration.ofSeconds(terminalReplayTtlSeconds));
    }

    private void restoreReplayIfStillCurrent(String key, StartedReplay replay) {
        restoreTranscriptIfStillCurrent(key, replay.currentTranscript(), replay.previousTranscript());
        restoreFileChangesIfStillCurrent(key, replay.currentFileChanges(), replay.previousFileChanges());
    }

    private void restoreTranscriptIfStillCurrent(String key, GenerationJobService.JobTranscript current, GenerationJobService.@Nullable JobTranscript previous) {
        if (!current.equals(transcriptMap().get(key))) {
            return;
        }
        if (previous == null) {
            transcriptMap().remove(key, current);
        }
        else {
            transcriptMap().put(key, previous, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
    }

    private void restoreFileChangesIfStillCurrent(String key, GenerationJobService.JobFileChangeIndex current, GenerationJobService.@Nullable JobFileChangeIndex previous) {
        if (!current.equals(fileChangeMap().get(key))) {
            return;
        }
        if (previous == null) {
            fileChangeMap().remove(key, current);
        }
        else {
            fileChangeMap().put(key, previous, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
    }

    /**
     * Appends an event to the running job's transcript for reconnect replay, bounded so a long run cannot grow the distributed map without limit. Dropped when {@code jobId} does
     * not match the retained transcript (a stale or older run); {@code terminal} marks the transcript done so a reconnecting client knows not to expect more.
     * <p>
     * A terminal event seals the run's token accounting inside this same lock, before the transcript is published and before the caller pushes the event over the websocket, so
     * that "the transcript is terminal" implies "the accounting is sealed" and no consumer can observe a finished run whose reported cost is still accumulating.
     */
    boolean recordEvent(long exerciseId, String jobId, ExerciseGenerationEventDTO event, boolean terminal) {
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            if (!isActiveJob(key, jobId)) {
                return false;
            }
            GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
            if (transcript == null || !transcript.jobId().equals(jobId) || transcript.done()) {
                return false;
            }
            List<ExerciseGenerationEventDTO> events = appendBounded(transcript.events(), event);
            if (terminal) {
                sealUsage(jobId);
            }
            transcriptMap().put(key, transcript.withEvents(events, terminal || transcript.done(), transcript.specDocument()));
            return true;
        }
        finally {
            jobMap().unlock(key);
        }
    }

    /**
     * Appends within the retention bound and keeps one updated marker for dropped events.
     * <p>
     * Eviction is not plain FIFO. The agent loop emits roughly one line per turn plus one before every provider call, so a long run overflows this bound with ordinary progress
     * prose; dropping oldest-first would take the run's phase ladder and repair-round bookkeeping with it and leave a replay that cannot say where the run has been. Structural
     * events — anything carrying a {@link ExerciseGenerationEventDTO#phase()} or a {@link ExerciseGenerationEventDTO#repairRound()}, and every non-{@code PROGRESS} event — are
     * therefore kept while any plain progress line older than them remains. The bound itself is unconditional: when only structural events are left, the oldest of those goes.
     */
    private static List<ExerciseGenerationEventDTO> appendBounded(List<ExerciseGenerationEventDTO> existing, ExerciseGenerationEventDTO event) {
        List<ExerciseGenerationEventDTO> events = new ArrayList<>(existing);
        events.add(event);
        if (events.size() <= MAX_RETAINED_EVENTS) {
            return events;
        }
        // Index 0 is the run's opening event, kept so the replay always starts somewhere meaningful; index 1 is the marker's reserved slot from the first drop onwards.
        long dropped = retainedDropCount(events);
        if (dropped == 0) {
            // First overflow: the marker claims index 1. A plain progress line there is simply dropped into it; a structural event is shifted out of the way instead, and the
            // eviction below then takes an evictable event. The count is corrected once the rest of the overflow has been removed.
            if (isEvictable(events.get(1))) {
                dropped = 1;
                events.set(1, truncationMarker(dropped));
            }
            else {
                events.add(1, truncationMarker(0));
            }
        }
        while (events.size() > MAX_RETAINED_EVENTS) {
            events.remove(firstEvictableIndex(events));
            dropped++;
        }
        events.set(1, truncationMarker(dropped));
        return events;
    }

    /** Whether an event is an ordinary progress line, which the replay can lose without losing the shape of the run. */
    private static boolean isEvictable(ExerciseGenerationEventDTO event) {
        return event.type() == ExerciseGenerationEventDTO.Type.PROGRESS && event.phase() == null && event.repairRound() == null;
    }

    /** The oldest evictable event after the reserved marker slot, or the oldest event at all when every retained event is structural. */
    private static int firstEvictableIndex(List<ExerciseGenerationEventDTO> events) {
        for (int index = 2; index < events.size(); index++) {
            if (isEvictable(events.get(index))) {
                return index;
            }
        }
        return 2;
    }

    private static long retainedDropCount(List<ExerciseGenerationEventDTO> events) {
        if (events.size() < 2) {
            return 0;
        }
        ExerciseGenerationEventDTO candidate = events.get(1);
        if (candidate.type() != ExerciseGenerationEventDTO.Type.PROGRESS || candidate.message() == null) {
            return 0;
        }
        Matcher matcher = EVENT_TRUNCATION_MESSAGE_PATTERN.matcher(candidate.message());
        return matcher.matches() ? Long.parseLong(matcher.group(1)) : 0;
    }

    private static ExerciseGenerationEventDTO truncationMarker(long droppedEvents) {
        return ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.PROGRESS, droppedEvents + EVENT_TRUNCATION_MESSAGE_SUFFIX);
    }

    private static String truncateSpecDocument(String specDocument) {
        return specDocument.length() <= MAX_SPEC_DOCUMENT_LENGTH ? specDocument : specDocument.substring(0, MAX_SPEC_DOCUMENT_LENGTH) + SPEC_DOCUMENT_TRUNCATION_MARKER;
    }

    /**
     * Records the gate-approved SPEC.md snapshot on the running job's transcript — the earliest meaningful intermediate result — capped so a large document cannot grow the
     * retained distributed transcript without bound, and dropped when {@code jobId} does not match the retained transcript.
     */
    boolean recordSpecDocument(long exerciseId, String jobId, String specDocument) {
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            if (!isActiveJob(key, jobId)) {
                return false;
            }
            GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
            if (transcript == null || !transcript.jobId().equals(jobId) || transcript.done()) {
                return false;
            }
            transcriptMap().put(key, transcript.withEvents(transcript.events(), transcript.done(), truncateSpecDocument(specDocument)));
            return true;
        }
        finally {
            jobMap().unlock(key);
        }
    }

    /** Records the latest lightweight change per path for reconnect replay; dropped when {@code jobId} does not match the retained store (a stale or older run). */
    boolean recordFileChange(long exerciseId, String jobId, ExerciseGenerationFileChangeDTO fileChange) {
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            GenerationJobService.JobFileChangeIndex index = fileChangeMap().get(key);
            if (index == null || !index.jobId().equals(jobId) || !isActiveJob(key, jobId) || Boolean.TRUE.equals(cancellationMap().get(jobId))) {
                return false;
            }
            List<ExerciseGenerationFileChangeDTO> changes = new ArrayList<>(index.changes());
            int existingIndex = -1;
            for (int i = 0; i < changes.size(); i++) {
                if (changes.get(i).path().equals(fileChange.path())) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex >= 0) {
                changes.set(existingIndex, fileChange);
            }
            else {
                changes.add(fileChange);
                while (changes.size() > MAX_RETAINED_FILE_CHANGES) {
                    changes.removeFirst();
                }
            }
            fileChangeMap().put(key, new GenerationJobService.JobFileChangeIndex(index.jobId(), index.userLogin(), changes));
            return true;
        }
        finally {
            jobMap().unlock(key);
        }
    }

    /**
     * The current or most-recent run's transcript for the exercise, for reconnection/replay, with a {@code running} flag derived from the live slot; empty unless a transcript
     * is retained for this user.
     */
    Optional<ExerciseGenerationStatusDTO> getStatus(User user, ProgrammingExercise exercise) {
        String key = key(exercise.getId());
        jobMap().lock(key);
        try {
            GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
            GenerationJobService.JobInfo active = jobMap().get(key);
            if (active != null && !GenerationJobService.isGenerationJob(active)) {
                active = null;
            }
            if (active != null) {
                boolean ownedByCaller = active.userLogin().equals(user.getLogin());
                GenerationMode mode = transcript != null && transcript.jobId().equals(active.jobId()) ? transcript.mode() : null;
                if (!ownedByCaller && transcript != null && transcript.jobId().equals(active.jobId()) && transcript.done()) {
                    ExerciseGenerationEventDTO terminal = terminalOutcome(transcript);
                    return terminal == null ? Optional.empty()
                            : Optional.of(
                                    new ExerciseGenerationStatusDTO(transcript.jobId(), false, transcript.mode(), List.of(terminal), List.of(), false, null, null, false, false));
                }
                if (!ownedByCaller || transcript == null || !transcript.jobId().equals(active.jobId()) || !transcript.userLogin().equals(user.getLogin())) {
                    return Optional.of(new ExerciseGenerationStatusDTO(active.jobId(), true, mode, List.of(), List.of(), false, null, null, ownedByCaller,
                            ownedByCaller && active.cancellable()));
                }
                return Optional.of(new ExerciseGenerationStatusDTO(transcript.jobId(), !transcript.done(), transcript.mode(), transcript.events(),
                        latestFileChangesFor(key, transcript.jobId()), false, null, null, true, !transcript.done() && active.cancellable(), transcript.specDocument())
                        .withEffortProfile(transcript.effortProfile()).withArtifactsRetained(hasRetainedArtifacts(key, transcript.jobId(), user)));
            }
            if (transcript == null) {
                return Optional.empty();
            }
            if (!transcript.userLogin().equals(user.getLogin())) {
                ExerciseGenerationEventDTO terminal = terminalOutcome(transcript);
                if (terminal == null) {
                    return Optional.empty();
                }
                return Optional.of(new ExerciseGenerationStatusDTO(transcript.jobId(), false, transcript.mode(), List.of(terminal), List.of(), false, null, null, false, false));
            }
            // Owner-only, like the usage aggregate: which configuration ran is part of the account a caller is asked to review, and a sanitized view carries none of it.
            return Optional.of(new ExerciseGenerationStatusDTO(transcript.jobId(), false, transcript.mode(), transcript.events(), latestFileChangesFor(key, transcript.jobId()),
                    false, null, null, true, false, transcript.specDocument()).withEffortProfile(transcript.effortProfile())
                    .withArtifactsRetained(hasRetainedArtifacts(key, transcript.jobId(), user)));
        }
        finally {
            jobMap().unlock(key);
        }
    }

    /**
     * Whether this run actually retained an unsaved candidate the caller may read back. Read from the retained snapshot rather than remembered from when the run ended, so the
     * status can never promise work that the retention bounds dropped, that a newer run superseded, or whose TTL has since expired.
     */
    private boolean hasRetainedArtifacts(String key, String jobId, User user) {
        GenerationJobService.JobArtifacts retained = artifactMap().get(key);
        return retained != null && retained.jobId().equals(jobId) && retained.userLogin().equals(user.getLogin()) && !retained.artifacts().isEmpty();
    }

    /** Removes a matching completed run's replay after its live changes were undone. */
    void discardRetainedRun(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
            if (transcript != null && transcript.jobId().equals(jobId)) {
                transcriptMap().remove(key, transcript);
            }
            GenerationJobService.JobFileChangeIndex index = fileChangeMap().get(key);
            if (index != null && index.jobId().equals(jobId)) {
                fileChangeMap().remove(key, index);
            }
            GenerationJobService.JobArtifacts retainedArtifacts = artifactMap().get(key);
            if (retainedArtifacts != null && retainedArtifacts.jobId().equals(jobId)) {
                artifactMap().remove(key, retainedArtifacts);
            }
            usageMap().remove(jobId);
            usageWriteFailures.remove(jobId);
        }
        finally {
            jobMap().unlock(key);
        }
    }

    @Nullable
    CancellationReplayState cancellationReplayState(GenerationJobService.JobInfo job) {
        String key = key(job.exerciseId());
        GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
        if (transcript == null || !transcript.jobId().equals(job.jobId())) {
            return null;
        }
        return new CancellationReplayState(transcript.userLogin(), transcript.done());
    }

    @Nullable
    ExerciseGenerationEventDTO appendCancellation(GenerationJobService.JobInfo job, String message) {
        String key = key(job.exerciseId());
        GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
        if (transcript == null || !transcript.jobId().equals(job.jobId()) || transcript.done()) {
            return null;
        }
        ExerciseGenerationEventDTO cancellationEvent = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, message)
                .withTerminationReason(ExerciseGenerationEventDTO.TerminationReason.CANCELLED);
        // Deliberately does not seal the accounting: cancellation is recorded by another thread while the worker is still winding down, so further provider usage can still be
        // recorded against this job. The worker seals when it can prove otherwise, and until then the state stays PENDING rather than claiming a total it does not have.
        List<ExerciseGenerationEventDTO> events = appendBounded(transcript.events(), cancellationEvent);
        transcriptMap().put(key, transcript.withEvents(events, true, transcript.specDocument()));
        return cancellationEvent;
    }

    /** Seals a terminal worker's account; a worker without a terminal transcript is incomplete. */
    void sealUsageOnWorkerExit(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
            if (transcript != null && transcript.jobId().equals(jobId) && transcript.done()) {
                sealUsage(jobId);
            }
            else {
                sealUsageIncomplete(jobId);
            }
        }
        finally {
            jobMap().unlock(key);
        }
    }

    /**
     * Retains the candidate a terminal run produced but never saved, so the work stays inspectable by the instructor who started the run — the only one allowed to read it back.
     * <p>
     * Written with the terminal-replay TTL immediately rather than promoted later, because unlike the transcript there is no live phase during which this value is useful.
     * Overwrites the exercise's older snapshot, matching the one-retained-run-per-exercise rule the transcript and file-change index already follow.
     * <p>
     * Only the exercise's current run may retain. A run that was reclaimed as stale can still be winding down while its replacement is under way, and without this guard that
     * straggler would overwrite the newer run's slot — leaving a stale draft exposed as the retained candidate once the newer run saves and retains nothing of its own.
     */
    boolean retainUnsavedArtifacts(long exerciseId, String jobId, String userLogin, ExerciseGenerationRetainedArtifactsDTO artifacts) {
        if (artifacts.isEmpty()) {
            return false;
        }
        String key = key(exerciseId);
        jobMap().lock(key);
        try {
            // Fails closed: the caller must still be the exercise's current run, evidenced either by the transcript naming it or by it still holding the job slot. Absence of
            // both is not permission — the transcript carries the same terminal-replay TTL as the artifact, so once it has expired or been discarded there is nothing left to
            // retain against, and treating that as "no newer run exists" would let a long-delayed superseded worker repopulate the slot.
            GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
            GenerationJobService.JobInfo activeJob = jobMap().get(key);
            boolean currentRun = transcript != null && transcript.jobId().equals(jobId) || activeJob != null && activeJob.jobId().equals(jobId);
            if (!currentRun) {
                return false;
            }
            artifactMap().put(key, new GenerationJobService.JobArtifacts(jobId, userLogin, artifacts), Duration.ofSeconds(terminalReplayTtlSeconds));
            return true;
        }
        finally {
            jobMap().unlock(key);
        }
    }

    /**
     * The unsaved candidate retained for this exercise's most recent run; empty when none is retained or the requester did not start the run.
     * <p>
     * Owner-only with no sanitized fallback, unlike the status transcript: a transcript is a progress narrative another instructor may reasonably watch, while this is the
     * verbatim content of an unreviewed, unverified draft.
     */
    Optional<ExerciseGenerationRetainedArtifactsDTO> getRetainedArtifacts(User user, ProgrammingExercise exercise) {
        GenerationJobService.JobArtifacts retained = artifactMap().get(key(exercise.getId()));
        if (retained == null || !retained.userLogin().equals(user.getLogin())) {
            return Optional.empty();
        }
        return Optional.of(retained.artifacts());
    }

    void retainAfterJobCleared(long exerciseId, String jobId) {
        String key = key(exerciseId);
        GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
        if (transcript != null && transcript.jobId().equals(jobId)) {
            GenerationJobService.JobTranscript retainedTranscript = transcript.done() ? transcript : transcript.withEvents(transcript.events(), true, transcript.specDocument());
            transcriptMap().put(key, retainedTranscript, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
        retainFileChangesForTerminalReplay(key, jobId);
        if (usageMap().containsKey(jobId)) {
            usageMap().refreshTimeToLive(jobId, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
    }

    @Nullable
    ExerciseGenerationEventDTO terminalizeStoppedJob(GenerationJobService.JobInfo job, String message, ExerciseGenerationEventDTO.TerminationReason terminationReason) {
        String key = key(job.exerciseId());
        GenerationJobService.JobTranscript transcript = transcriptMap().get(key);
        if (transcript != null && transcript.jobId().equals(job.jobId()) && !transcript.done()) {
            ExerciseGenerationEventDTO terminalEvent = job.cancellable()
                    ? ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, message).withTerminationReason(terminationReason)
                    : ExerciseGenerationEventDTO.done(message, ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, null, true).withTerminationReason(terminationReason);
            List<ExerciseGenerationEventDTO> events = appendBounded(transcript.events(), terminalEvent);
            transcriptMap().put(key, transcript.withEvents(events, true, transcript.specDocument()));
            return terminalEvent;
        }
        return null;
    }

    private boolean isActiveJob(String key, String jobId) {
        GenerationJobService.JobInfo job = jobMap().get(key);
        return job != null && job.jobId().equals(jobId);
    }

    @Nullable
    private ExerciseGenerationEventDTO terminalOutcome(GenerationJobService.JobTranscript transcript) {
        for (int index = transcript.events().size() - 1; index >= 0; index--) {
            ExerciseGenerationEventDTO event = transcript.events().get(index);
            if (event.type() == ExerciseGenerationEventDTO.Type.DONE || event.type() == ExerciseGenerationEventDTO.Type.CANCELLED
                    || event.type() == ExerciseGenerationEventDTO.Type.ERROR) {
                return event;
            }
        }
        return null;
    }

    private List<ExerciseGenerationFileChangeDTO> latestFileChangesFor(String key, String jobId) {
        GenerationJobService.JobFileChangeIndex index = fileChangeMap().get(key);
        if (index == null || !index.jobId().equals(jobId)) {
            return List.of();
        }
        return index.changes();
    }

    private void retainFileChangesForTerminalReplay(String key, String jobId) {
        GenerationJobService.JobFileChangeIndex fileChangeIndex = fileChangeMap().get(key);
        if (fileChangeIndex != null && fileChangeIndex.jobId().equals(jobId)) {
            fileChangeMap().refreshTimeToLive(key, Duration.ofSeconds(terminalReplayTtlSeconds));
        }
    }

    private static String key(long exerciseId) {
        return String.valueOf(exerciseId);
    }

    record StartedReplay(GenerationJobService.JobTranscript currentTranscript, GenerationJobService.JobFileChangeIndex currentFileChanges,
            GenerationJobService.@Nullable JobTranscript previousTranscript, GenerationJobService.@Nullable JobFileChangeIndex previousFileChanges) {
    }

    record CancellationReplayState(String userLogin, boolean done) {
    }

    /** The run's aggregate usage together with how complete that aggregate is, always read as one pair. */
    record UsageSnapshot(@Nullable ExerciseGenerationUsageDTO usage, ExerciseGenerationAccountingState accountingState) {

        static final UsageSnapshot EVIDENCE_GONE = new UsageSnapshot(null, ExerciseGenerationAccountingState.INCOMPLETE);
    }

    /**
     * The per-job usage accumulator. {@code accountingState} is a closed tri-state rather than a pair of booleans so that "not sealed yet" and "will never be complete" cannot be
     * conflated: {@link ExerciseGenerationAccountingState#INCOMPLETE} is absorbing, and only a caller that can prove no further provider call is possible may seal.
     */
    private record JobUsage(long modelCalls, long toolCalls, long agentTurns, long attempts, long inputTokens, long outputTokens, long cachedInputTokens,
            boolean cachedInputTokensComplete, double estimatedCostEur, boolean estimatedCostEurComplete, List<String> models, List<String> providerRequestIds,
            boolean providerRequestIdsComplete, ExerciseGenerationAccountingState accountingState) implements Serializable {

        @Serial
        private static final long serialVersionUID = 2L;

        static JobUsage empty() {
            return new JobUsage(0, 0, 0, 0, 0, 0, 0, true, 0, true, List.of(), List.of(), true, ExerciseGenerationAccountingState.PENDING);
        }

        /** Starts an accumulator after evidence was lost, so no later seal can make it complete. */
        static JobUsage unaccounted() {
            return empty().markIncomplete();
        }

        /** Recorded usage reopens a sealed account: more spend arrived than the seal claimed. An account already known to be incomplete stays incomplete. */
        private ExerciseGenerationAccountingState afterRecordedUsage() {
            return accountingState == ExerciseGenerationAccountingState.INCOMPLETE ? accountingState : ExerciseGenerationAccountingState.PENDING;
        }

        JobUsage add(LLMRequest request) {
            long cached = request.numCachedInputTokens() == null ? 0 : request.numCachedInputTokens();
            long uncached = request.numInputTokens() - cached;
            double cost = (uncached * request.costPerMillionInputToken() + cached * request.costPerMillionCachedInputToken()
                    + request.numOutputTokens() * request.costPerMillionOutputToken()) / 1_000_000.0;
            LinkedHashSet<String> nextModels = new LinkedHashSet<>(models);
            if (request.model() != null && !request.model().isBlank()) {
                nextModels.add(request.model());
            }
            LinkedHashSet<String> nextProviderRequestIds = new LinkedHashSet<>(providerRequestIds);
            boolean hasProviderRequestId = request.providerRequestId() != null && !request.providerRequestId().isBlank();
            if (hasProviderRequestId) {
                nextProviderRequestIds.add(request.providerRequestId());
            }
            return new JobUsage(modelCalls + 1, toolCalls, agentTurns, attempts, inputTokens + request.numInputTokens(), outputTokens + request.numOutputTokens(),
                    cachedInputTokens + cached, cachedInputTokensComplete && request.numCachedInputTokens() != null, estimatedCostEur + cost,
                    estimatedCostEurComplete && request.costEstimateComplete(), List.copyOf(nextModels), List.copyOf(nextProviderRequestIds),
                    providerRequestIdsComplete && hasProviderRequestId, afterRecordedUsage());
        }

        JobUsage addToolCalls(long count) {
            return new JobUsage(modelCalls, toolCalls + count, agentTurns, attempts, inputTokens, outputTokens, cachedInputTokens, cachedInputTokensComplete, estimatedCostEur,
                    estimatedCostEurComplete, models, providerRequestIds, providerRequestIdsComplete, afterRecordedUsage());
        }

        JobUsage addAgentTurn() {
            return new JobUsage(modelCalls, toolCalls, agentTurns + 1, attempts, inputTokens, outputTokens, cachedInputTokens, cachedInputTokensComplete, estimatedCostEur,
                    estimatedCostEurComplete, models, providerRequestIds, providerRequestIdsComplete, afterRecordedUsage());
        }

        JobUsage addAttempt() {
            return new JobUsage(modelCalls, toolCalls, agentTurns, attempts + 1, inputTokens, outputTokens, cachedInputTokens, cachedInputTokensComplete, estimatedCostEur,
                    estimatedCostEurComplete, models, providerRequestIds, providerRequestIdsComplete, afterRecordedUsage());
        }

        JobUsage markIncomplete() {
            return withAccountingState(ExerciseGenerationAccountingState.INCOMPLETE);
        }

        JobUsage seal() {
            return accountingState == ExerciseGenerationAccountingState.PENDING ? withAccountingState(ExerciseGenerationAccountingState.COMPLETE) : this;
        }

        private JobUsage withAccountingState(ExerciseGenerationAccountingState nextState) {
            return new JobUsage(modelCalls, toolCalls, agentTurns, attempts, inputTokens, outputTokens, cachedInputTokens, cachedInputTokensComplete, estimatedCostEur,
                    estimatedCostEurComplete, models, providerRequestIds, providerRequestIdsComplete, nextState);
        }

        ExerciseGenerationUsageDTO toDTO() {
            return new ExerciseGenerationUsageDTO(modelCalls, toolCalls, agentTurns, attempts, inputTokens, outputTokens, cachedInputTokens, cachedInputTokensComplete,
                    estimatedCostEur, estimatedCostEurComplete, models, providerRequestIds, providerRequestIdsComplete);
        }
    }
}
