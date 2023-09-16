package de.tum.in.www1.artemis.exam;

import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.web.rest.dto.*;

/**
 * Factory for creating Exams and related objects.
 */
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
     * Generates a real exam without student review dates set and attaches a channel
     *
     * @param course      the associated course
     * @param channelName the channel name
     * @return the created exam
     */
    public static Exam generateExam(Course course, String channelName) {
        return generateExamHelper(course, false, channelName);
    }

    /**
     * Generates an exam without channel
     *
     * @param course      the associated course
     * @param visibleDate the visible date of the exam
     * @param startDate   the start date of the exam
     * @param endDate     the end date of the exam
     * @param testExam    if the exam is a test exam
     * @return the created exam
     */
    public static Exam generateExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam) {
        return generateExam(course, visibleDate, startDate, endDate, testExam, null);
    }

    /**
     * Generates an exam with channel
     *
     * @param course      the associated course
     * @param visibleDate the visible date of the exam
     * @param startDate   the start date of the exam
     * @param endDate     the end date of the exam
     * @param testExam    if the exam is a test exam
     * @param channelName the channel name
     * @return the created exam
     */
    public static Exam generateExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam, String channelName) {
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
        exam.setChannelName(channelName);
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
     * Helper method to create an exam without a channel
     *
     * @param course   the associated course
     * @param testExam Boolean flag to determine whether it is a test exam
     * @return the created Exam
     */
    private static Exam generateExamHelper(Course course, boolean testExam) {
        return generateExamHelper(course, testExam, null);
    }

    /**
     * Helper method to create an exam with a channel
     *
     * @param course   the associated course
     * @param testExam Boolean flag to determine whether it is a test exam
     * @return the created Exam
     */
    private static Exam generateExamHelper(Course course, boolean testExam, String channelName) {
        ZonedDateTime currentTime = now();
        return generateExam(course, currentTime, currentTime.plusMinutes(10), currentTime.plusMinutes(testExam ? 80 : 60), testExam, channelName);
    }

    /**
     * generates an exercise group for an exam
     *
     * @param mandatory if the exercise group is mandatory
     * @param exam      the exam that this exercise group should be added to
     *
     * @return the newly created exercise
     */
    public static ExerciseGroup generateExerciseGroup(boolean mandatory, Exam exam) {
        return generateExerciseGroupWithTitle(mandatory, exam, "Exercise group title");
    }

    /**
     * generates an exercise group for an exam with the given title
     *
     * @param mandatory if the exercise group is mandatory
     * @param exam      the exam that this exercise group should be added to
     * @param title     title of the exercise group
     *
     * @return the newly created exercise
     */
    public static ExerciseGroup generateExerciseGroupWithTitle(boolean mandatory, Exam exam, String title) {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle(title);
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

    /**
     * generates an exam with one exercise group
     *
     * @param course    course of the exam
     * @param mandatory if the exercise group is mandatory
     * @return newly generated exam
     */
    public static Exam generateExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = generateExam(course);
        generateExerciseGroup(mandatory, exam);

        return exam;
    }

    /**
     * creates exam session DTOs
     *
     * @param session1 firts exam session
     * @param session2 second exam session
     * @return set of exam session DTOs
     */
    public static Set<ExamSessionDTO> createExpectedExamSessionDTOs(ExamSession session1, ExamSession session2) {
        var expectedDTOs = new HashSet<ExamSessionDTO>();
        var firstStudentExamDTO = new StudentExamWithIdAndExamAndUserDTO(session1.getStudentExam().getId(),
                new ExamWithIdAndCourseDTO(session1.getStudentExam().getExam().getId(), new CourseWithIdDTO(session1.getStudentExam().getExam().getCourse().getId())),
                new UserWithIdAndLoginDTO(session1.getStudentExam().getUser().getId(), session1.getStudentExam().getUser().getLogin()));
        var secondStudentExamDTO = new StudentExamWithIdAndExamAndUserDTO(session2.getStudentExam().getId(),
                new ExamWithIdAndCourseDTO(session2.getStudentExam().getExam().getId(), new CourseWithIdDTO(session2.getStudentExam().getExam().getCourse().getId())),
                new UserWithIdAndLoginDTO(session2.getStudentExam().getUser().getId(), session2.getStudentExam().getUser().getLogin()));
        var firstExamSessionDTO = new ExamSessionDTO(session1.getId(), session1.getBrowserFingerprintHash(), session1.getIpAddress(), session1.getSuspiciousReasons(),
                session1.getCreatedDate(), firstStudentExamDTO);
        var secondExamSessionDTO = new ExamSessionDTO(session2.getId(), session2.getBrowserFingerprintHash(), session2.getIpAddress(), session2.getSuspiciousReasons(),
                session2.getCreatedDate(), secondStudentExamDTO);
        expectedDTOs.add(firstExamSessionDTO);
        expectedDTOs.add(secondExamSessionDTO);
        return expectedDTOs;
    }
}
