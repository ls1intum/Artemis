package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStatusDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/** Stores the bounded reconnect replay for an exercise generation job. */
final class GenerationJobReplayStore {

    private static final String JOB_MAP_NAME = "hyperion-exercise-generation-jobs";

    private static final String CANCEL_MAP_NAME = "hyperion-exercise-generation-cancellations";

    private static final String TRANSCRIPT_MAP_NAME = "hyperion-exercise-generation-transcripts";

    static final String FILE_CHANGE_MAP_NAME = "hyperion-exercise-generation-file-changes";

    private static final int TERMINAL_REPLAY_TTL_SECONDS = 900;

    private static final int MAX_RETAINED_EVENTS = 500;

    static final int MAX_RETAINED_FILE_CHANGES = 300;

    /** Defensive cap so a large {@code SPEC.md} cannot grow the retained Hazelcast transcript without bound. */
    static final int MAX_SPEC_DOCUMENT_LENGTH = 20_000;

    private static final String SPEC_DOCUMENT_TRUNCATION_MARKER = "\n\n[... SPEC.md truncated to " + MAX_SPEC_DOCUMENT_LENGTH + " characters for the status API ...]";

    private IMap<String, GenerationJobService.JobInfo> jobMap;

    private IMap<String, Boolean> cancellationMap;

    private IMap<String, GenerationJobService.JobTranscript> transcriptMap;

    private IMap<String, GenerationJobService.JobFileChangeIndex> fileChangeMap;

    GenerationJobReplayStore(HazelcastInstance hazelcastInstance) {
        jobMap = hazelcastInstance.getMap(JOB_MAP_NAME);
        cancellationMap = hazelcastInstance.getMap(CANCEL_MAP_NAME);
        transcriptMap = hazelcastInstance.getMap(TRANSCRIPT_MAP_NAME);
        fileChangeMap = hazelcastInstance.getMap(FILE_CHANGE_MAP_NAME);
    }

    StartedReplay initializeStart(long exerciseId, String jobId, String userLogin, GenerationMode mode) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            GenerationJobService.JobTranscript previousTranscript = transcriptMap.get(key);
            GenerationJobService.JobFileChangeIndex previousFileChanges = fileChangeMap.get(key);
            GenerationJobService.JobTranscript currentTranscript = new GenerationJobService.JobTranscript(jobId, userLogin, exerciseId, mode, new ArrayList<>(), false, null);
            GenerationJobService.JobFileChangeIndex currentFileChanges = new GenerationJobService.JobFileChangeIndex(jobId, userLogin, new ArrayList<>());
            StartedReplay replay = new StartedReplay(currentTranscript, currentFileChanges, previousTranscript, previousFileChanges);
            try {
                transcriptMap.set(key, currentTranscript);
                fileChangeMap.set(key, currentFileChanges);
            }
            catch (RuntimeException e) {
                restoreReplayIfStillCurrent(key, replay);
                throw e;
            }
            return replay;
        }
        finally {
            jobMap.unlock(key);
        }
    }

    void restoreUnpublishedStart(long exerciseId, StartedReplay replay) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            restoreReplayIfStillCurrent(key, replay);
        }
        finally {
            jobMap.unlock(key);
        }
    }

    private void restoreReplayIfStillCurrent(String key, StartedReplay replay) {
        restoreTranscriptIfStillCurrent(key, replay.currentTranscript(), replay.previousTranscript());
        restoreFileChangesIfStillCurrent(key, replay.currentFileChanges(), replay.previousFileChanges());
    }

    private void restoreTranscriptIfStillCurrent(String key, GenerationJobService.JobTranscript current, GenerationJobService.@Nullable JobTranscript previous) {
        if (!current.equals(transcriptMap.get(key))) {
            return;
        }
        if (previous == null) {
            transcriptMap.remove(key, current);
        }
        else {
            transcriptMap.set(key, previous, TERMINAL_REPLAY_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void restoreFileChangesIfStillCurrent(String key, GenerationJobService.JobFileChangeIndex current, GenerationJobService.@Nullable JobFileChangeIndex previous) {
        if (!current.equals(fileChangeMap.get(key))) {
            return;
        }
        if (previous == null) {
            fileChangeMap.remove(key, current);
        }
        else {
            fileChangeMap.set(key, previous, TERMINAL_REPLAY_TTL_SECONDS, TimeUnit.SECONDS);
        }
    }

    boolean recordEvent(long exerciseId, String jobId, ExerciseGenerationEventDTO event, boolean terminal) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            if (!isActiveJob(key, jobId)) {
                return false;
            }
            GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
            if (transcript == null || !transcript.jobId().equals(jobId) || transcript.done()) {
                return false;
            }
            List<ExerciseGenerationEventDTO> events = new ArrayList<>(transcript.events());
            events.add(event);
            while (events.size() > MAX_RETAINED_EVENTS) {
                events.remove(1);
            }
            transcriptMap.set(key, new GenerationJobService.JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), events,
                    terminal || transcript.done(), transcript.specDocument()));
            return true;
        }
        finally {
            jobMap.unlock(key);
        }
    }

    private static String truncateSpecDocument(String specDocument) {
        return specDocument.length() <= MAX_SPEC_DOCUMENT_LENGTH ? specDocument : specDocument.substring(0, MAX_SPEC_DOCUMENT_LENGTH) + SPEC_DOCUMENT_TRUNCATION_MARKER;
    }

    boolean recordSpecDocument(long exerciseId, String jobId, String specDocument) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            if (!isActiveJob(key, jobId)) {
                return false;
            }
            GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
            if (transcript == null || !transcript.jobId().equals(jobId) || transcript.done()) {
                return false;
            }
            transcriptMap.set(key, new GenerationJobService.JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(),
                    transcript.events(), transcript.done(), truncateSpecDocument(specDocument)));
            return true;
        }
        finally {
            jobMap.unlock(key);
        }
    }

    boolean recordFileChange(long exerciseId, String jobId, ExerciseGenerationFileChangeDTO fileChange) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            GenerationJobService.JobFileChangeIndex index = fileChangeMap.get(key);
            if (index == null || !index.jobId().equals(jobId) || !isActiveJob(key, jobId) || Boolean.TRUE.equals(cancellationMap.get(jobId))) {
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
            fileChangeMap.set(key, new GenerationJobService.JobFileChangeIndex(index.jobId(), index.userLogin(), changes));
            return true;
        }
        finally {
            jobMap.unlock(key);
        }
    }

    Optional<ExerciseGenerationStatusDTO> getStatus(User user, ProgrammingExercise exercise) {
        String key = key(exercise.getId());
        jobMap.lock(key);
        try {
            GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
            GenerationJobService.JobInfo active = jobMap.get(key);
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
                        latestFileChangesFor(key, transcript.jobId()), false, null, null, true, !transcript.done() && active.cancellable(), transcript.specDocument()));
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
            return Optional.of(new ExerciseGenerationStatusDTO(transcript.jobId(), false, transcript.mode(), transcript.events(), latestFileChangesFor(key, transcript.jobId()),
                    false, null, null, true, false, transcript.specDocument()));
        }
        finally {
            jobMap.unlock(key);
        }
    }

    void discardRetainedRun(long exerciseId, String jobId) {
        String key = key(exerciseId);
        jobMap.lock(key);
        try {
            GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
            if (transcript != null && transcript.jobId().equals(jobId)) {
                transcriptMap.remove(key, transcript);
            }
            GenerationJobService.JobFileChangeIndex index = fileChangeMap.get(key);
            if (index != null && index.jobId().equals(jobId)) {
                fileChangeMap.remove(key, index);
            }
        }
        finally {
            jobMap.unlock(key);
        }
    }

    @Nullable
    CancellationReplayState cancellationReplayState(GenerationJobService.JobInfo job) {
        String key = key(job.exerciseId());
        GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
        if (transcript == null || !transcript.jobId().equals(job.jobId())) {
            return null;
        }
        return new CancellationReplayState(transcript.userLogin(), transcript.done());
    }

    @Nullable
    ExerciseGenerationEventDTO appendCancellation(GenerationJobService.JobInfo job, String message) {
        String key = key(job.exerciseId());
        GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
        if (transcript == null || !transcript.jobId().equals(job.jobId()) || transcript.done()) {
            return null;
        }
        List<ExerciseGenerationEventDTO> events = new ArrayList<>(transcript.events());
        ExerciseGenerationEventDTO cancellationEvent = ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.CANCELLED, message);
        events.add(cancellationEvent);
        while (events.size() > MAX_RETAINED_EVENTS) {
            events.remove(1);
        }
        transcriptMap.set(key, new GenerationJobService.JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), events, true,
                transcript.specDocument()));
        return cancellationEvent;
    }

    void retainAfterJobCleared(long exerciseId, String jobId) {
        String key = key(exerciseId);
        GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
        if (transcript != null && transcript.jobId().equals(jobId)) {
            GenerationJobService.JobTranscript retainedTranscript = transcript.done() ? transcript
                    : new GenerationJobService.JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), transcript.events(), true,
                            transcript.specDocument());
            transcriptMap.set(key, retainedTranscript, TERMINAL_REPLAY_TTL_SECONDS, TimeUnit.SECONDS);
        }
        retainFileChangesForTerminalReplay(key, jobId);
    }

    void terminalizeStoppedJob(GenerationJobService.JobInfo job, String message) {
        String key = key(job.exerciseId());
        GenerationJobService.JobTranscript transcript = transcriptMap.get(key);
        if (transcript != null && transcript.jobId().equals(job.jobId()) && !transcript.done()) {
            List<ExerciseGenerationEventDTO> events = new ArrayList<>(transcript.events());
            ExerciseGenerationEventDTO terminalEvent = job.cancellable() ? ExerciseGenerationEventDTO.of(ExerciseGenerationEventDTO.Type.ERROR, message)
                    : ExerciseGenerationEventDTO.done(message, ExerciseGenerationEventDTO.CompletionStatus.PARTIAL, null, true);
            events.add(terminalEvent);
            while (events.size() > MAX_RETAINED_EVENTS) {
                events.remove(1);
            }
            transcriptMap.set(key, new GenerationJobService.JobTranscript(transcript.jobId(), transcript.userLogin(), transcript.exerciseId(), transcript.mode(), events, true,
                    transcript.specDocument()));
        }
    }

    private boolean isActiveJob(String key, String jobId) {
        GenerationJobService.JobInfo job = jobMap.get(key);
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
        GenerationJobService.JobFileChangeIndex index = fileChangeMap.get(key);
        if (index == null || !index.jobId().equals(jobId)) {
            return List.of();
        }
        return index.changes();
    }

    private void retainFileChangesForTerminalReplay(String key, String jobId) {
        GenerationJobService.JobFileChangeIndex fileChangeIndex = fileChangeMap.get(key);
        if (fileChangeIndex != null && fileChangeIndex.jobId().equals(jobId)) {
            fileChangeMap.setTtl(key, TERMINAL_REPLAY_TTL_SECONDS, TimeUnit.SECONDS);
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
}
