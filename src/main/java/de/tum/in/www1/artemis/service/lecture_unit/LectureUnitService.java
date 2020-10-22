package de.tum.in.www1.artemis.service.lecture_unit;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_unit.LectureUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;

@Service
public class LectureUnitService {

    private final LectureRepository lectureRepository;

    public LectureUnitService(LectureRepository lectureRepository) {
        this.lectureRepository = lectureRepository;
    }

    public List<LectureUnit> updateLectureUnitsOrder(Long lectureId, List<LectureUnit> orderedLectureUnits) {
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            throw new IllegalArgumentException("Lecture with ID " + lectureId + " does not exist in the database");
        }
        Lecture lecture = lectureOptional.get();

        // Ensure that exactly as many exercise groups have been received as are currently related to the exam
        if (orderedLectureUnits.size() != lecture.getLectureUnits().size()) {
            throw new IllegalArgumentException("Size conflict between the ordered list of lecture units and the already" + "connected lecture units");
        }

        // Ensure that all received lecture units are already related to the lecture
        for (LectureUnit lectureUnit : orderedLectureUnits) {
            if (!lecture.getLectureUnits().contains(lectureUnit)) {
                throw new IllegalArgumentException("Not all lecture units in the ordered list are connected to the lecture");
            }
            // Set the lecture manually as it won't be included in orderedLectureUnits
            lectureUnit.setLecture(lecture);
        }

        lecture.setLectureUnits(orderedLectureUnits);
        Lecture persistedLecture = lectureRepository.save(lecture);
        return persistedLecture.getLectureUnits();
    }
}
