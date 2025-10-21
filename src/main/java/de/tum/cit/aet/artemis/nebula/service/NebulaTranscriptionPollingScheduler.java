package de.tum.cit.aet.artemis.nebula.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;

/**
 * Scheduler for polling Nebula transcription job status.
 * Only active when Nebula is enabled.
 */
@Conditional(NebulaEnabled.class)
@Lazy
@Component
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class NebulaTranscriptionPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(NebulaTranscriptionPollingScheduler.class);

    private final LectureTranscriptionRepository transcriptionRepository;

    private final LectureTranscriptionService transcriptionService;

    public NebulaTranscriptionPollingScheduler(LectureTranscriptionRepository transcriptionRepository, LectureTranscriptionService transcriptionService) {
        this.transcriptionRepository = transcriptionRepository;
        this.transcriptionService = transcriptionService;
    }

    /**
     * Scheduled method that polls all Nebula transcription jobs currently marked as PENDING.
     * For each job, calls the transcription service to fetch status and update accordingly.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedRate = 30000)
    public void pollPendingNebulaTranscriptions() {
        log.debug("Polling pending Nebula transcriptions...");

        List<LectureTranscription> pendingTranscriptions = transcriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);

        if (!pendingTranscriptions.isEmpty()) {
            log.info("Found {} pending Nebula transcriptions to process", pendingTranscriptions.size());
        }

        for (LectureTranscription transcription : pendingTranscriptions) {
            transcriptionService.processTranscription(transcription);
        }
    }
}
