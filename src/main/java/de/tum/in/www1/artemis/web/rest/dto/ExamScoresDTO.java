package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamScoresDTO {

    public Long examId;

    public String title;

    public Integer maxPoints;

    public Double averagePointsAchieved = null;

    public ExamScoresDTO() {
        // default constructor for our beloved Jackson :-*
    }

    public ExamScoresDTO(Long examId, String title, Integer maxPoints) {
        this.examId = examId;
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

        public Long numberOfParticipants;

        public List<ExerciseInfo> containedExercises = new ArrayList<>();

        public ExerciseGroup() {
            // default constructor for our beloved Jackson :-*
        }

        public ExerciseGroup(Long id, String title, Double maxPoints) {
            this.id = id;
            this.title = title;
            this.maxPoints = maxPoints;
        }

        public static class ExerciseInfo {

            public Long exerciseId;

            public String title;

            public Double maxPoints;

            public Long numberOfParticipants;

            public ExerciseInfo() {
                // default constructor for our beloved Jackson :-*
            }

            public ExerciseInfo(Long exerciseId, String title, Double maxPoints, Long numberOfParticipants) {
                this.exerciseId = exerciseId;
                this.title = title;
                this.maxPoints = maxPoints;
                this.numberOfParticipants = numberOfParticipants;
            }
        }
    }

    // Inner DTO
    public static class StudentResult {

        public Long userId;

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

        public StudentResult(Long userId, String name, String eMail, String login, String registrationNumber, Boolean submitted) {
            this.userId = userId;
            this.eMail = eMail;
            this.name = name;
            this.login = login;
            this.registrationNumber = registrationNumber;
            this.submitted = submitted;
        }
    }

    // Inner DTO
    public static class ExerciseResult {

        public Long exerciseId;

        public String title;

        public Double maxPoints;

        public Long achievedScore;

        public Double achievedPoints;

        // Indicates that the student attempted to solve the exercise
        public Boolean hasNonEmptySubmission;

        public ExerciseResult() {
            // default constructor for our beloved Jackson :-*
        }

        public ExerciseResult(Long exerciseId, String title, Double maxPoints, Long achievedScore, Double achievedPoints, Boolean hasNonEmptySubmission) {
            this.exerciseId = exerciseId;
            this.title = title;
            this.maxPoints = maxPoints;
            this.achievedScore = achievedScore;
            this.achievedPoints = achievedPoints;
            this.hasNonEmptySubmission = hasNonEmptySubmission;
        }
    }

}
