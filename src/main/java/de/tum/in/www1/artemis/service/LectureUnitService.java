package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;

@Service
public class LectureUnitService {

    private final LectureRepository lectureRepository;

    private final LearningGoalRepository learningGoalRepository;

    public LectureUnitService(LectureRepository lectureRepository, LearningGoalRepository learningGoalRepository) {
        this.lectureRepository = lectureRepository;
        this.learningGoalRepository = learningGoalRepository;
    }

    @Transactional
    public void removeLectureUnit(LectureUnit lectureUnit) {

        // we have to get the lecture from the db so that that the lecture units are included
        Optional<Lecture> lectureOptional = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lectureUnit.getLecture().getId());
        if (lectureOptional.isEmpty()) {
            throw new IllegalArgumentException();
        }
        Lecture lecture = lectureOptional.get();

        // update associated learning goals
        Set<LearningGoal> associatedLearningGoals = new HashSet<>(lectureUnit.getLearningGoals());
        for (LearningGoal learningGoal : associatedLearningGoals) {
            Optional<LearningGoal> learningGoalFromDbOptional = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoal.getId());
            if (learningGoalFromDbOptional.isPresent()) {
                LearningGoal learningGoalFromDb = learningGoalFromDbOptional.get();
                learningGoalFromDb.removeLectureUnit(lectureUnit);
                learningGoalRepository.save(learningGoalFromDb);
            }
        }

        List<LectureUnit> filteredLectureUnits = lecture.getLectureUnits();
        filteredLectureUnits.removeIf(lu -> lu.getId().equals(lectureUnit.getId()));
        lecture.setLectureUnits(filteredLectureUnits);
        lectureRepository.save(lecture);
    }
}
