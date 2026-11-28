package de.tum.cit.aet.artemis.lecture.service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.lecture.config.LectureWithIrisEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitProcessingState;
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitProcessingStateRepository;

/**
 * Saves the transcription checkpoints that Iris sends during a run and moves the run from TRANSCRIBING to INGESTING once the
 * enriched transcript is stored. Every write is bound to the job token of the run that sent the checkpoint.
 */
@Conditional(LectureWithIrisEnabled.class)
@Service
@Lazy
public class TranscriptionCheckpointService {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionCheckpointService.class);

    private final LectureUnitProcessingStateRepository processingStateRepository;

    private final LectureTranscriptionRepository transcriptionRepository;

    private final ProcessingStateNotificationService notificationService;

    private final JsonMapper objectMapper;

    public TranscriptionCheckpointService(LectureUnitProcessingStateRepository processingStateRepository, LectureTranscriptionRepository transcriptionRepository,
            ProcessingStateNotificationService notificationService, JsonMapper objectMapper) {
        this.processingStateRepository = processingStateRepository;
        this.transcriptionRepository = transcriptionRepository;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handle checkpoint data from Iris callbacks (e.g., transcription results). Iris sends transcription data in the {@code result} field of status callbacks; this parses the
     * transcript JSON, saves it, and transitions TRANSCRIBING → INGESTING once the enriched transcript arrives. Checkpoint types, distinguished by segment content: raw (all
     * slideNumber=0) saved as PENDING, staying in TRANSCRIBING; enriched (some slideNumber≠0) saved as COMPLETED, transitioning to INGESTING.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @param jobToken      the job token for validation
     * @param resultJson    the JSON string containing transcription data
     */
    public void handleCheckpointData(long lectureUnitId, String jobToken, String resultJson) {
        if (resultJson == null || resultJson.isBlank()) {
            return;
        }

        Optional<LectureUnitProcessingState> stateOpt = processingStateRepository.findByLectureUnit_Id(lectureUnitId);
        if (stateOpt.isEmpty()) {
            log.warn("Received checkpoint for unit {} but no processing state exists", lectureUnitId);
            return;
        }

        LectureUnitProcessingState state = stateOpt.get();

        if (!Objects.equals(jobToken, state.getIngestionJobToken())) {
            log.info("Ignoring stale checkpoint for unit {} (token mismatch)", lectureUnitId);
            return;
        }

        if (state.getPhase() != ProcessingPhase.TRANSCRIBING) {
            log.debug("Ignoring checkpoint for unit {} in phase {} (expected TRANSCRIBING)", lectureUnitId, state.getPhase());
            return;
        }

        try {
            TranscriptionCheckpoint checkpoint = parseTranscriptionCheckpoint(resultJson);
            if (checkpoint == null) {
                return;
            }

            saveTranscription(lectureUnitId, state, checkpoint);
        }
        catch (JacksonException e) {
            log.warn("Failed to parse checkpoint data for unit {}: {}", lectureUnitId, e.getMessage());
        }
    }

    /** Parse transcription checkpoint data from JSON, expected format {@code {"language": "en", "segments": [...]}}. */
    private TranscriptionCheckpoint parseTranscriptionCheckpoint(String resultJson) {
        CheckpointPayload payload = objectMapper.readValue(resultJson, CheckpointPayload.class);

        if (payload == null || payload.segments() == null || payload.segments().isEmpty()) {
            log.debug("Checkpoint has no segments, ignoring");
            return null;
        }

        String language = payload.language() != null ? payload.language() : "en";
        List<LectureTranscriptionSegment> segments = payload.segments();

        boolean isEnriched = segments.stream().anyMatch(seg -> seg.slideNumber() != 0);
        return new TranscriptionCheckpoint(language, segments, isEnriched);
    }

    /**
     * Save transcription data and optionally transition from TRANSCRIBING to INGESTING.
     */
    private void saveTranscription(long lectureUnitId, LectureUnitProcessingState state, TranscriptionCheckpoint checkpoint) {
        LectureUnit unit = state.getLectureUnit();

        if (checkpoint.isEnriched()) {
            // Version, then transcript, then TRANSCRIBING → INGESTING, each guarded on its own: if any step fails, the row is
            // still TRANSCRIBING and Iris's redelivery replays the rest, the hash keeping the version from moving twice. The
            // version goes first so an interruption can only over-count it, never leave a new transcript under the old version.
            String jobToken = state.getIngestionJobToken();
            LectureTranscriptionVersioning.bumpTranscriptionVersionIfContentChanged(state, checkpoint.segments());
            if (processingStateRepository.recordTranscriptionVersionIfTranscribing(state.getId(), jobToken, state.getTranscriptionVersion(),
                    state.getTranscriptionContentHash()) == 0) {
                log.debug("Ignoring enriched checkpoint for unit {}: the run is no longer TRANSCRIBING under this token", lectureUnitId);
                return;
            }
            if (!persistTranscription(unit, jobToken, checkpoint, TranscriptionStatus.COMPLETED)) {
                return;
            }
            if (processingStateRepository.transitionToIngestingIfTranscribing(state.getId(), jobToken, ZonedDateTime.now(), state.getTranscriptionVersion(),
                    state.getTranscriptionContentHash()) == 0) {
                log.debug("Ignoring enriched checkpoint for unit {}: the run is no longer TRANSCRIBING under this token", lectureUnitId);
                return;
            }
            log.info("Enriched transcription saved for unit {}, transitioning to INGESTING", lectureUnitId);

            // Notify the client with the row as the transition wrote it. Mirroring the transition in memory would drop the fields the
            // statement keeps, such as the worker lease, and the badge would briefly show the run without it.
            processingStateRepository.findById(state.getId())
                    .ifPresent(transitioned -> notificationService.notifyProcessingStateChange(transitioned, TranscriptionStatus.COMPLETED));
        }
        else {
            // The row stays TRANSCRIBING either way, so a write that fails after this check is replayed by the redelivery.
            String jobToken = state.getIngestionJobToken();
            if (processingStateRepository.touchLastUpdated(state.getId(), jobToken, ZonedDateTime.now()) == 0) {
                log.debug("Ignoring raw checkpoint for unit {}: the run is no longer in flight under this token", lectureUnitId);
                return;
            }

            log.info("Raw transcription checkpoint saved for unit {}, staying in TRANSCRIBING", lectureUnitId);
            persistTranscription(unit, jobToken, checkpoint, TranscriptionStatus.PENDING);
        }
    }

    /**
     * The JSON Iris sends in the {@code result} field of a transcription checkpoint.
     *
     * @param language the language of the transcript, absent when Iris does not detect one
     * @param segments the transcript segments, absent or empty when the checkpoint carries none
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CheckpointPayload(@Nullable String language, @Nullable List<LectureTranscriptionSegment> segments) {
    }

    /**
     * Write the checkpoint's transcription only while its run still owns the unit; see
     * {@link LectureTranscriptionRepository#saveCheckpointIfTokenMatches} for how the ownership check and the write commit together, so a
     * superseded run can neither recreate a deleted transcript nor overwrite the transcript of the newer run that kept it.
     *
     * @return whether the transcription was written; false when the run no longer owns the unit or a completed transcript is kept
     */
    private boolean persistTranscription(LectureUnit unit, String expectedToken, TranscriptionCheckpoint checkpoint, TranscriptionStatus status) {
        if (!transcriptionRepository.saveCheckpointIfTokenMatches(unit, expectedToken, checkpoint.language(), checkpoint.segments(), status)) {
            log.debug("Skipping transcription write for unit {}: the run no longer owns the unit or a completed transcript is kept", unit.getId());
            return false;
        }
        return true;
    }
}
