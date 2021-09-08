package de.tum.in.www1.artemis.domain.notification;

import com.google.gson.Gson;

/**
 * Class representing the target property of an exam notification
 * e.g. used for JSON/GSON (de)serialization
 */
public class ExamNotificationTargetWithoutProblemStatement {

    private int exercise;

    private int exam;

    private String entity;

    private int course;

    private String mainPage;

    /**
     * Auxiliary class to convert and remove problem statement from strings in json format for saving
     */
    public ExamNotificationTargetWithoutProblemStatement(int exercise, int exam, String entity, int course, String mainPage) {
        this.exercise = exercise;
        this.exam = exam;
        this.entity = entity;
        this.course = course;
        this.mainPage = mainPage;
    }

    /**
     * @return the target of the exam notification without the problem statement (for saving)
     */
    public static String getTargetWithoutProblemStatement(String target) {
        Gson gson = new Gson();
        ExamNotificationTargetWithoutProblemStatement targetObjectWithoutProblemStatement = gson.fromJson(target, ExamNotificationTargetWithoutProblemStatement.class);
        return gson.toJson(targetObjectWithoutProblemStatement);
    }

    @Override
    public String toString() {
        return "ExamNotificationTargetWithoutProblemStatement{" + "exercise=" + exercise + ", exam=" + exam + ", entity='" + entity + '\'' + ", course=" + course + ", mainPage='"
                + mainPage + '\'' + '}';
    }
}
