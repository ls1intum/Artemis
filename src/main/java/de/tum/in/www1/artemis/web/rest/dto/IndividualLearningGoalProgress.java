package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This DTO contains the information for a students progress in achieving a learning goal
 * The learning goal progress is calculated from the performance in a subset of the connected lecture units
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IndividualLearningGoalProgress {

    public Long studentId;

    public Long learningGoalId;

    public String learningGoalTitle;

    public Double pointsAchievedByStudentInLearningGoal;

    public Double totalPointsAchievableByStudentsInLearningGoal;

    /**
     * the students progress in the connected lecture units that were used for the progress calculation
     */
    public List<IndividualLectureUnitProgress> progressInLectureUnits = new ArrayList<>();

    /**
     * This DTO contains the information for a students progress in completing a lecture unit
     */
    public static class IndividualLectureUnitProgress {

        public Long lectureUnitId;

        public Double scoreAchievedByStudentInLectureUnit;

        public Double totalPointsAchievableByStudentsInLectureUnit;
    }
}
