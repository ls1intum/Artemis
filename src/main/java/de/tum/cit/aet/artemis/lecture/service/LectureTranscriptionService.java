package de.tum.cit.aet.artemis.lecture.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Service
public class LectureTranscriptionService {

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    public LectureTranscriptionService(LectureTranscriptionRepository lectureTranscriptionRepository, LectureUnitRepository lectureUnitRepository) {
        this.lectureTranscriptionRepository = lectureTranscriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
    }

    @Transactional
    public LectureTranscription saveFinalTranscriptionResult(String jobId, LectureTranscriptionDTO dto) {
        LectureTranscription transcription = lectureTranscriptionRepository.findByJobId(jobId)
                .orElseThrow(() -> new IllegalStateException("No transcription found for jobId: " + jobId));

        transcription.setLanguage(dto.language());
        transcription.setSegments(dto.segments());
        transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);

        return lectureTranscriptionRepository.save(transcription);
    }

    @Transactional
    public void createEmptyTranscription(Long lectureId, Long lectureUnitId, String jobId) {
        LectureUnit lectureUnit = validateAndCleanup(lectureId, lectureUnitId);

        LectureTranscription t = new LectureTranscription();
        t.setLectureUnit(lectureUnit);
        t.setJobId(jobId);
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);

        lectureTranscriptionRepository.save(t);
    }

    private LectureUnit validateAndCleanup(Long lectureId, Long lectureUnitId) {
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);

        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new IllegalArgumentException("Lecture Unit does not belong to the Lecture.");
        }

        lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(existing -> {
            lectureTranscriptionRepository.deleteById(existing.getId());
            lectureTranscriptionRepository.flush(); // ensure immediate deletion
        });

        return lectureUnit;
    }
}
