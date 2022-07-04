package de.tum.in.www1.artemis.web.rest.dto;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ExamScoresDTO {

    public Long examId;

    public String title;

    public Integer maxPoints;

    public Double averagePointsAchieved = null;

    public Boolean hasSecondCorrectionAndStarted;

    public List<ExerciseGroup> exerciseGroups = new ArrayList<>();

    public List<StudentResult> studentResults = new ArrayList<>();

    public ExamScoresDTO() {
        // default constructor for our beloved Jackson :-*
    }

    public ExamScoresDTO(Long examId, String title, Integer maxPoints) {
        this.examId = examId;
        this.title = title;
        this.maxPoints = maxPoints;
    }

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
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

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        public static class ExerciseInfo {

            public Long exerciseId;

            public String title;

            public Double maxPoints;

            public Long numberOfParticipants;

            public String exerciseType;

            public ExerciseInfo() {
                // default constructor for our beloved Jackson :-*
            }

            public ExerciseInfo(Long exerciseId, String title, Double maxPoints, Long numberOfParticipants, String exerciseType) {
                this.exerciseId = exerciseId;
                this.title = title;
                this.maxPoints = maxPoints;
                this.numberOfParticipants = numberOfParticipants;
                this.exerciseType = exerciseType;
            }
        }
    }

    // Inner DTO
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class StudentResult {

        public Long userId;

        public String name;

        public String eMail;

        public String login;

        public String registrationNumber;

        public Double overallPointsAchieved = null;

        public Double overallScoreAchieved = null;

        public String overallGrade = null;

        public String overallGradeInFirstCorrection = null;

        public Boolean hasPassed = null;

        public Double overallPointsAchievedInFirstCorrection = null;

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
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ExerciseResult {

        public Long exerciseId;

        public String title;

        public Double maxScore;

        public Double achievedScore;

        public Double achievedPoints;

        // Indicates that the student attempted to solve the exercise
        public Boolean hasNonEmptySubmission;

        public ExerciseResult() {
            // default constructor for our beloved Jackson :-*
        }

        public ExerciseResult(Long exerciseId, String title, Double maxScore, Double achievedScore, Double achievedPoints, Boolean hasNonEmptySubmission) {
            this.exerciseId = exerciseId;
            this.title = title;
            this.maxScore = maxScore;
            this.achievedScore = achievedScore;
            this.achievedPoints = achievedPoints;
            this.hasNonEmptySubmission = hasNonEmptySubmission;
        }
    }

}
