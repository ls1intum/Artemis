package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.nebula.config.NebulaEnabled;

@Conditional(NebulaEnabled.class)
@Lazy
@Component
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class TranscriptionPollingScheduler {

    private final LectureTranscriptionRepository transcriptionRepository;

    private final LectureTranscriptionService transcriptionService;

    public TranscriptionPollingScheduler(LectureTranscriptionRepository transcriptionRepository, LectureTranscriptionService transcriptionService) {

        this.transcriptionRepository = transcriptionRepository;
        this.transcriptionService = transcriptionService;
    }

    /**
     * Scheduled method that polls all transcription jobs currently marked as PENDING.
     * For each job, calls the transcription service to fetch status and update accordingly.
     * Runs every 30 seconds, but only if Nebula is enabled.
     */
    @Scheduled(fixedRate = 30000)
    public void pollPendingTranscriptions() {
        List<LectureTranscription> pendingTranscriptions = transcriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);

        for (LectureTranscription transcription : pendingTranscriptions) {
            transcriptionService.processTranscription(transcription);
        }
    }
}
