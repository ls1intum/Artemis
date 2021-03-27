package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.LearningGoal;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class LectureUnitService {

    private final LectureRepository lectureRepository;

    private final LearningGoalRepository learningGoalRepository;

    public LectureUnitService(LectureRepository lectureRepository, LearningGoalRepository learningGoalRepository) {
        this.lectureRepository = lectureRepository;
        this.learningGoalRepository = learningGoalRepository;
    }

    /**
     * Deletes a lecture unit correctly in the database
     *
     * @param lectureUnit lecture unit to delete
     */
    @Transactional // ok because of delete
    public void removeLectureUnit(LectureUnit lectureUnit) {
        if (Objects.isNull(lectureUnit)) {
            return;
        }
        // update associated learning goals
        Set<LearningGoal> associatedLearningGoals = new HashSet<>(lectureUnit.getLearningGoals());
        for (LearningGoal learningGoal : associatedLearningGoals) {
            disconnectLectureUnitAndLearningGoal(lectureUnit, learningGoal);
        }
        Lecture lecture = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoalsElseThrow(lectureUnit.getLecture().getId());
        // Hibernate sometimes adds null into the list of lecture units to keep the order, to prevent a NullPointerException we have to filter
        List<LectureUnit> lectureUnits = lecture.getLectureUnits().stream().filter(Objects::nonNull).collect(Collectors.toList());
        // Creating a new list of lecture units without the one we want to remove
        List<LectureUnit> lectureUnitsUpdated = new ArrayList<>();
        for (LectureUnit unit : lectureUnits) {
            if (!unit.getId().equals(lectureUnit.getId())) {
                lectureUnitsUpdated.add(unit);
            }
        }
        lecture.setLectureUnits(lectureUnitsUpdated);
        lectureRepository.save(lecture);
    }

    /**
     * Remove connection between lecture unit and learning goal in the database
     *
     * @param lectureUnit  Lecture unit connected to learning goal
     * @param learningGoal Learning goal connected to lecture unit
     */
    public void disconnectLectureUnitAndLearningGoal(LectureUnit lectureUnit, LearningGoal learningGoal) {
        Optional<LearningGoal> learningGoalFromDbOptional = learningGoalRepository.findByIdWithLectureUnitsBidirectional(learningGoal.getId());
        if (learningGoalFromDbOptional.isPresent()) {
            LearningGoal learningGoalFromDb = learningGoalFromDbOptional.get();
            learningGoalFromDb.removeLectureUnit(lectureUnit);
            learningGoalRepository.save(learningGoalFromDb);
        }
    }
}
