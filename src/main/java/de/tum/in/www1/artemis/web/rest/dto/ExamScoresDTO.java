package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamScoresDTO {

    // examId
    public Long id;

    public String title;

    public Integer maxPoints;

    public Double averagePointsAchieved = null;

    public ExamScoresDTO() {
        // default constructor for our beloved Jackson :-*
    }

    public ExamScoresDTO(Long id, String title, Integer maxPoints) {
        this.id = id;
        this.title = title;
        this.maxPoints = maxPoints;
    }

    public List<ExerciseGroup> exerciseGroups = new ArrayList<>();

    public List<StudentResult> studentResults = new ArrayList<>();

    // Inner DTO
    public static class ExerciseGroup {

        public Long id;

        public String title;

        public Double maxPoints;

        public Double averagePointsAchieved = null;

        public List<String> containedExercises;

        public ExerciseGroup() {
            // default constructor for our beloved Jackson :-*
        }

        public ExerciseGroup(Long id, String title, Double maxPoints, List<String> containedExercises) {
            this.id = id;
            this.title = title;
            this.maxPoints = maxPoints;
            this.containedExercises = containedExercises;
        }
    }

    // Inner DTO
    public static class StudentResult {

        // userId
        public Long id;

        public String name;

        public String eMail;

        public String login;

        public String registrationNumber;

        public Double overallPointsAchieved = null;

        public Double overallScoreAchieved = null;

        public Boolean submitted = false;

        public Map<Long, ExerciseResult> exerciseGroupIdToExerciseResult = new HashMap<>();

        public StudentResult() {
            // default constructor for our beloved Jackson :-*
        }

        public StudentResult(Long id, String name, String eMail, String login, String registrationNumber, Boolean submitted) {
            this.id = id;
            this.eMail = eMail;
            this.name = name;
            this.login = login;
            this.registrationNumber = registrationNumber;
            this.submitted = submitted;
        }
    }

    // Inner DTO
    public static class ExerciseResult {

        // exerciseId
        public Long id;

        public String title;

        public Double maxScore;

        public Long achievedScore;

        public Double achievedPoints;

        // Indicates that the student attempted to solve the exercise
        public Boolean hasNonEmptySubmission;

        public ExerciseResult() {
            // default constructor for our beloved Jackson :-*
        }

        public ExerciseResult(Long id, String title, Double maxScore, Long achievedScore, Double achievedPoints, Boolean hasNonEmptySubmission) {
            this.id = id;
            this.title = title;
            this.maxScore = maxScore;
            this.achievedScore = achievedScore;
            this.achievedPoints = achievedPoints;
            this.hasNonEmptySubmission = hasNonEmptySubmission;
        }
    }

}
