package de.tum.in.www1.artemis.service;

import java.time.ZonedDateTime;
import java.util.*;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.repository.LearningGoalRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.LectureUnitCompletionRepository;

@Service
public class LectureUnitService {

    private final LectureRepository lectureRepository;

    private final LearningGoalRepository learningGoalRepository;

    private final LectureUnitCompletionRepository lectureUnitCompletionRepository;

    public LectureUnitService(LectureRepository lectureRepository, LearningGoalRepository learningGoalRepository, LectureUnitCompletionRepository lectureUnitCompletionRepository) {
        this.lectureRepository = lectureRepository;
        this.learningGoalRepository = learningGoalRepository;
        this.lectureUnitCompletionRepository = lectureUnitCompletionRepository;
    }

    /**
     * Set the completion status of the lecture unit for the give user
     * If the user completed the unit and completion status already exists, nothing happens
     * @param lectureUnit The lecture unit for which set the completion flag
     * @param user The user that completed/uncompleted the lecture unit
     * @param completed True if the lecture unit was completed, false otherwise
     */
    public void setLectureUnitCompletion(@NotNull LectureUnit lectureUnit, @NotNull User user, boolean completed) {
        Optional<LectureUnitCompletion> existingCompletion = lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId());
        if (completed) {
            if (!existingCompletion.isPresent()) {
                // Create a completion status for this lecture unit (only if it does not exist)
                LectureUnitCompletion completion = new LectureUnitCompletion();
                completion.setLectureUnit(lectureUnit);
                completion.setUser(user);
                completion.setCompletedAt(ZonedDateTime.now());
                lectureUnitCompletionRepository.save(completion);
            }
        }
        else {
            // Delete the completion status for this lecture unit (if it exists)
            existingCompletion.ifPresent(lectureUnitCompletionRepository::delete);
        }
    }

    /**
     * Get the timestamp when the lecture unit was completed by the user
     * @param lectureUnit The lecture unit completed by the user
     * @param user The user that completed the lecture unit
     * @return The completion timestamp or null if not yet completed by the user
     */
    @Nullable
    public ZonedDateTime getLectureUnitCompletion(@NotNull LectureUnit lectureUnit, @NotNull User user) {
        Optional<LectureUnitCompletion> completion = lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId());
        return completion.map(LectureUnitCompletion::getCompletedAt).orElse(null);
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
        /*
         * Set<LearningGoal> associatedLearningGoals = new HashSet<>(lectureUnit.getLearningGoals()); for (LearningGoal learningGoal : associatedLearningGoals) {
         * disconnectLectureUnitAndLearningGoal(lectureUnit, learningGoal); }
         */
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lectureUnit.getLecture().getId());
        // Creating a new list of lecture units without the one we want to remove
        List<LectureUnit> lectureUnitsUpdated = new ArrayList<>();
        for (LectureUnit unit : lecture.getLectureUnits()) {
            if (Objects.nonNull(unit) && !unit.getId().equals(lectureUnit.getId())) {
                lectureUnitsUpdated.add(unit);
            }
        }
        lecture.getLectureUnits().clear();
        lecture.getLectureUnits().addAll(lectureUnitsUpdated);
        lectureRepository.save(lecture);
    }
}
