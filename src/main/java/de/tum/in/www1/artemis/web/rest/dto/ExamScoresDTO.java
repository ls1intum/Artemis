package de.tum.in.www1.artemis.web.rest.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExamScoresDTO {

    public Long examId;

    public String examTitle;

    public List<ExerciseGroup> exerciseGroups = new ArrayList<>();

    public List<Student> students = new ArrayList<>();

    public static class ExerciseGroup {

        public Long id;

        public String title;

        public ExerciseGroup(Long id, String title) {
            this.id = id;
            this.title = title;
        }
    }

    public static class Student {

        public Long studentId;

        public String name;

        public String login;

        public String registrationNumber;

        // Mapping ExerciseGroupId to ExerciseResult
        public Map<Long, ExerciseResult> exerciseGroupToExerciseResult = new HashMap<>();

        public Student(Long studentId, String name, String login, String registrationNumber) {
            this.studentId = studentId;
            this.name = name;
            this.login = login;
            this.registrationNumber = registrationNumber;
        }
    }

    public static class ExerciseResult {

        public Long exerciseId;

        public String exerciseTitle;

        public Double exerciseMaxScore;

        public Long exerciseAchievedScore;

        public ExerciseResult(Long exerciseId, String exerciseTitle, Double exerciseMaxScore, Long exerciseAchievedScore) {
            this.exerciseId = exerciseId;
            this.exerciseTitle = exerciseTitle;
            this.exerciseMaxScore = exerciseMaxScore;
            this.exerciseAchievedScore = exerciseAchievedScore;
        }
    }

}
