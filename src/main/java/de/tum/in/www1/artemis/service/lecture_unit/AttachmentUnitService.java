package de.tum.in.www1.artemis.service.lecture_unit;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_unit.AttachmentUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;

@Service
public class AttachmentUnitService {

    private final LectureRepository lectureRepository;

    public AttachmentUnitService(LectureRepository lectureRepository) {
        this.lectureRepository = lectureRepository;
    }

    public AttachmentUnit createAttachmentUnit(Long lectureId, AttachmentUnit attachmentUnit) {
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            throw new IllegalArgumentException("Lecture with ID " + lectureId + " does not exist in the database");
        }
        Lecture lecture = lectureOptional.get();
        lecture.addLectureUnit(attachmentUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        return (AttachmentUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);
    }
}
