package de.tum.in.www1.artemis.service.lecture_unit;

import java.util.Optional;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture_unit.ExerciseUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.lecture_unit.ExerciseUnitRepository;

@Service
public class ExerciseUnitService {

    private final ExerciseUnitRepository exerciseUnitRepository;

    private final LectureRepository lectureRepository;

    public ExerciseUnitService(ExerciseUnitRepository exerciseUnitRepository, LectureRepository lectureRepository) {
        this.exerciseUnitRepository = exerciseUnitRepository;
        this.lectureRepository = lectureRepository;
    }

    public Optional<ExerciseUnit> findById(Long exerciseUnitId) {
        return this.exerciseUnitRepository.findById(exerciseUnitId);
    }

    public ExerciseUnit createExerciseUnit(Long lectureId, ExerciseUnit exerciseUnit) {
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnits(lectureId);
        if (lectureOptional.isEmpty()) {
            throw new IllegalArgumentException("Lecture with ID " + lectureId + " does not exist in the database");
        }
        Lecture lecture = lectureOptional.get();
        lecture.addLectureUnit(exerciseUnit);
        Lecture updatedLecture = lectureRepository.save(lecture);
        return (ExerciseUnit) updatedLecture.getLectureUnits().get(updatedLecture.getLectureUnits().size() - 1);
    }
}
