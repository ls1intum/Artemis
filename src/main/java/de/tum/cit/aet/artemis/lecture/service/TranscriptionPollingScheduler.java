package de.tum.cit.aet.artemis.lecture.service;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;

@Lazy
@Component
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
     * Runs every 60 seconds.
     */
    @Scheduled(fixedRate = 60000)
    public void pollPendingTranscriptions() {
        List<LectureTranscription> pendingTranscriptions = transcriptionRepository.findByTranscriptionStatusAndJobIdIsNotNull(TranscriptionStatus.PENDING);

        for (LectureTranscription transcription : pendingTranscriptions) {
            transcriptionService.processTranscriptionInTransaction(transcription);
        }
    }
}
