package de.tum.cit.aet.artemis.exercise.service;

import java.util.LinkedHashMap;
import java.util.Map;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * The expected effort of the steps of an asynchronous deletion, relative to each other and not in seconds. A progress bar that moves by the weight of the finished steps
 * does not stall for minutes on the one step that deletes thousands of repositories, and does not jump when a cheap step ends.
 */
public final class EntityDeletionWeights {

    /**
     * The step keys of the deletion of an exercise, in the order they run.
     */
    public static final String STEP_CHANNEL = "channel";

    public static final String STEP_PLAGIARISM = "plagiarism";

    public static final String STEP_PARTICIPATIONS = "participations";

    public static final String STEP_EXAMPLE_SUBMISSIONS = "exampleSubmissions";

    public static final String STEP_STUDENT_EXAMS = "studentExams";

    public static final String STEP_IRIS_SESSIONS = "irisSessions";

    public static final String STEP_PROGRAMMING_ARTIFACTS = "programmingArtifacts";

    public static final String STEP_EXERCISE = "exercise";

    public static final String STEP_CLEANUP = "cleanup";

    /**
     * The step keys of the deletion of an exam, in the order they run.
     */
    public static final String STEP_TEST_RUNS = "testRuns";

    public static final String STEP_EXERCISES = "exercises";

    public static final String STEP_GRADING_SCALE = "gradingScale";

    public static final String STEP_EXAM = "exam";

    /**
     * The step key of the last step of the deletion of an exercise group.
     */
    public static final String STEP_EXERCISE_GROUP = "exerciseGroup";

    private static final double WEIGHT_SIMPLE_STEP = 1.0;

    private static final double WEIGHT_STUDENT_EXAMS_STEP = 2.0;

    private static final double WEIGHT_PROGRAMMING_ARTIFACTS = 20.0;

    private static final double WEIGHT_EXERCISE_ROW = 2.0;

    private static final double WEIGHT_PER_PARTICIPATION = 1.0;

    // A programming participation owns a repository and a build plan, which have to be deleted as well.
    private static final double WEIGHT_PER_PROGRAMMING_PARTICIPATION = 5.0;

    private static final double WEIGHT_PER_TEST_RUN = 2.0;

    private static final double WEIGHT_PER_STUDENT_EXAM = 0.2;

    private static final double WEIGHT_PER_EXAM_USER = 0.2;

    private static final double WEIGHT_EXAM_ROW = 5.0;

    private static final double WEIGHT_EXERCISE_GROUP_ROW = 2.0;

    private EntityDeletionWeights() {
    }

    /**
     * The weights of the steps that deleting one exercise runs. The map is in execution order and only holds the steps that apply to the exercise.
     *
     * @param exercise       the exercise that is deleted
     * @param examExercise   whether the exercise belongs to an exam
     * @param participations the number of participations of the exercise
     * @return the weight of every step by its key
     */
    public static Map<String, Double> exerciseStepWeights(Exercise exercise, boolean examExercise, long participations) {
        boolean programming = exercise instanceof ProgrammingExercise;
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put(STEP_CHANNEL, WEIGHT_SIMPLE_STEP);
        weights.put(STEP_PLAGIARISM, WEIGHT_SIMPLE_STEP);
        weights.put(STEP_PARTICIPATIONS, participationsWeight(programming, participations));
        weights.put(STEP_EXAMPLE_SUBMISSIONS, WEIGHT_SIMPLE_STEP);
        if (examExercise) {
            weights.put(STEP_STUDENT_EXAMS, WEIGHT_STUDENT_EXAMS_STEP);
        }
        if (programming || exercise instanceof TextExercise) {
            weights.put(STEP_IRIS_SESSIONS, WEIGHT_SIMPLE_STEP);
        }
        if (programming) {
            weights.put(STEP_PROGRAMMING_ARTIFACTS, WEIGHT_PROGRAMMING_ARTIFACTS);
        }
        else {
            weights.put(STEP_EXERCISE, WEIGHT_EXERCISE_ROW);
        }
        weights.put(STEP_CLEANUP, WEIGHT_SIMPLE_STEP);
        return weights;
    }

    /**
     * The weight of the participations of an exercise.
     *
     * @param programming    whether the exercise is a programming exercise
     * @param participations the number of participations
     * @return the expected effort to delete them
     */
    public static double participationsWeight(boolean programming, long participations) {
        return participations * (programming ? WEIGHT_PER_PROGRAMMING_PARTICIPATION : WEIGHT_PER_PARTICIPATION);
    }

    /**
     * The weight of deleting one exercise as part of an exam or an exercise group.
     *
     * @param programming    whether the exercise is a programming exercise
     * @param participations the number of participations of the exercise
     * @return the expected effort to delete the exercise
     */
    public static double exerciseWeight(boolean programming, long participations) {
        double fixedWeight = programming ? WEIGHT_PROGRAMMING_ARTIFACTS + 5 * WEIGHT_SIMPLE_STEP : WEIGHT_EXERCISE_ROW + 5 * WEIGHT_SIMPLE_STEP;
        return fixedWeight + participationsWeight(programming, participations);
    }

    /**
     * The weights of the steps of deleting an exam, in execution order.
     *
     * @param testRuns        the number of test runs of the exam
     * @param exercisesWeight the sum of the weights of all exercises of the exam
     * @param studentExams    the number of student exams, which the database removes with the exam
     * @param examUsers       the number of registered students, whose images are removed
     * @return the weight of every step by its key
     */
    public static Map<String, Double> examStepWeights(long testRuns, double exercisesWeight, long studentExams, long examUsers) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put(STEP_TEST_RUNS, WEIGHT_SIMPLE_STEP + testRuns * WEIGHT_PER_TEST_RUN);
        weights.put(STEP_EXERCISES, exercisesWeight);
        weights.put(STEP_GRADING_SCALE, WEIGHT_SIMPLE_STEP);
        weights.put(STEP_EXAM, WEIGHT_EXAM_ROW + studentExams * WEIGHT_PER_STUDENT_EXAM);
        weights.put(STEP_CLEANUP, WEIGHT_SIMPLE_STEP + examUsers * WEIGHT_PER_EXAM_USER);
        return weights;
    }

    /**
     * The weights of the steps of deleting an exercise group, in execution order.
     *
     * @param exercisesWeight the sum of the weights of all exercises of the group
     * @return the weight of every step by its key
     */
    public static Map<String, Double> exerciseGroupStepWeights(double exercisesWeight) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put(STEP_EXERCISES, exercisesWeight);
        weights.put(STEP_EXERCISE_GROUP, WEIGHT_EXERCISE_GROUP_ROW);
        return weights;
    }

    /**
     * Sums the weights of a plan.
     *
     * @param stepWeights the weights by step
     * @return the total weight
     */
    public static double total(Map<String, Double> stepWeights) {
        return stepWeights.values().stream().mapToDouble(Double::doubleValue).sum();
    }
}
