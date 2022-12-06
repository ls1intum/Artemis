package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * This DTO contains the information for progress of a whole course in achieving a learning goal
 * The learning goal progress is calculated from the course progress in a subset of the connected lecture units
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CourseLearningGoalProgress {

    public Long courseId;

    public Long learningGoalId;

    public String learningGoalTitle;

    public Double averageScoreAchievedInLearningGoal;

    /**
     * the progress in the connected lecture units that were used for the progress calculation
     */
    public List<CourseLectureUnitProgress> progressInLectureUnits = new ArrayList<>();

    /**
     * This DTO contains the information for progress of a whole in completing a lecture unit
     */
    public static class CourseLectureUnitProgress {

        public Long lectureUnitId;

        public Double averageScoreAchievedByStudentInLectureUnit;

        public Double totalPointsAchievableByStudentsInLectureUnit;

        public Integer noOfParticipants;

        public Double participationRate;

    }
}
