package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExamScoresDTO {

    public Long id;

    public String title;

    public Integer maxPoints;

    public Double averagePointsAchieved = null;

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

        public ExerciseGroup(Long id, String title, Double maxPoints, List<String> containedExercises) {
            this.id = id;
            this.title = title;
            this.maxPoints = maxPoints;
            this.containedExercises = containedExercises;
        }
    }

    // Inner DTO
    public static class StudentResult {

        public Long id;

        public String name;

        public String eMail;

        public String login;

        public String registrationNumber;

        public Double overallPointsAchieved = null;

        public Map<Long, ExerciseResult> exerciseGroupIdToExerciseResult = new HashMap<>();

        public StudentResult(Long id, String name, String eMail, String login, String registrationNumber) {
            this.id = id;
            this.eMail = eMail;
            this.name = name;
            this.login = login;
            this.registrationNumber = registrationNumber;
        }
    }

    // Inner DTO
    public static class ExerciseResult {

        public Long id;

        public String title;

        public Double maxScore;

        public Long achievedScore;

        public Double achievedPoints;

        public ExerciseResult(Long id, String title, Double maxScore, Long achievedScore, Double achievedPoints) {
            this.id = id;
            this.title = title;
            this.maxScore = maxScore;
            this.achievedScore = achievedScore;
            this.achievedPoints = achievedPoints;
        }
    }

}
