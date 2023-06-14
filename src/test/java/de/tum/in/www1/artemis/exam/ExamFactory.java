package de.tum.in.www1.artemis.exam;

import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;

public class ExamFactory {

    /**
     * Generates a real exam with student review dates set
     *
     * @param course the associated course
     * @return the created exam
     */
    public static Exam generateExamWithStudentReviewDates(Course course) {
        Exam exam = generateExamHelper(course, false);
        ZonedDateTime currentTime = now();
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setExamStudentReviewStart(currentTime);
        exam.setExamStudentReviewEnd(currentTime.plusMinutes(60));
        return exam;
    }

    /**
     * Generates a real exam without student review dates set
     *
     * @param course the associated course
     * @return the created exam
     */
    public static Exam generateExam(Course course) {
        return generateExamHelper(course, false);
    }

    /**
     * Generates an exam
     *
     * @param course      the associated course
     * @param visibleDate the visible date of the exam
     * @param startDate   the start date of the exam
     * @param endDate     the end date of the exam
     * @param testExam    if the exam is a test exam
     * @return the created exam
     */
    public static Exam generateExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam) {
        Exam exam = new Exam();
        exam.setTitle((testExam ? "Test" : "Real") + " exam 1");
        exam.setTestExam(testExam);
        exam.setVisibleDate(visibleDate);
        exam.setStartDate(startDate);
        exam.setEndDate(endDate);
        exam.setWorkingTime(3000);
        exam.setStartText("Start Text");
        exam.setEndText("End Text");
        exam.setConfirmationStartText("Confirmation Start Text");
        exam.setConfirmationEndText("Confirmation End Text");
        exam.setExamMaxPoints(90);
        exam.setNumberOfExercisesInExam(1);
        exam.setRandomizeExerciseOrder(false);
        exam.setNumberOfCorrectionRoundsInExam(testExam ? 0 : 1);
        exam.setCourse(course);
        return exam;
    }

    /**
     * Generates a test exam (test exams have no student review dates)
     *
     * @param course the associated course
     * @return the created exam
     */
    public static Exam generateTestExam(Course course) {
        return generateExamHelper(course, true);
    }

    /**
     * Helper method to create an exam
     *
     * @param course   the associated course
     * @param testExam Boolean flag to determine whether it is a test exam
     * @return the created Exam
     */
    private static Exam generateExamHelper(Course course, boolean testExam) {
        ZonedDateTime currentTime = now();
        return generateExam(course, currentTime, currentTime.plusMinutes(10), currentTime.plusMinutes(testExam ? 80 : 60), testExam);
    }

    public static ExerciseGroup generateExerciseGroup(boolean mandatory, Exam exam) {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle("Exercise group title");
        exerciseGroup.setIsMandatory(mandatory);
        exam.addExerciseGroup(exerciseGroup);
        return exerciseGroup;
    }

    public static StudentExam generateStudentExam(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(false);
        return studentExam;
    }

    /**
     * Helper Method to generate a studentExam for a test exam
     *
     * @param exam the exam to be linked to the studentExam
     * @return a StudentExam for a test exam
     */
    public static StudentExam generateStudentExamForTestExam(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setWorkingTime(exam.getWorkingTime());
        studentExam.setTestRun(false);
        return studentExam;
    }

    public static StudentExam generateExamTestRun(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(true);
        return studentExam;
    }
}
