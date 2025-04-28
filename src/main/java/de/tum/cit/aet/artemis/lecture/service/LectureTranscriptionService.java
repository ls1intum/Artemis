package de.tum.cit.aet.artemis.lecture.service;

import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
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

    public LectureTranscription saveTranscription(Long lectureId, Long lectureUnitId, LectureTranscriptionDTO transcriptionDTO) {
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);

        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            throw new IllegalArgumentException("Lecture Unit does not belong to the Lecture.");
        }

        lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId).ifPresent(existing -> lectureTranscriptionRepository.deleteById(existing.getId()));

        LectureTranscription newTranscription = new LectureTranscription(transcriptionDTO.language(), transcriptionDTO.segments(), lectureUnit);
        return lectureTranscriptionRepository.save(newTranscription);
    }
}
