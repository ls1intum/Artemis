package de.tum.in.www1.artemis.exam;

import static java.time.ZonedDateTime.now;

import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.*;
import de.tum.in.www1.artemis.web.rest.dto.*;

/**
 * Factory for creating Exams and related objects.
 */
public class ExamFactory {

    /**
     * Creates an Exam with student review dates set [now; now + 60min]
     *
     * @param course The associated course
     * @return The newly created Exam
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
     * Creates an Exam without student review dates set
     *
     * @param course The associated course
     * @return The newly created Exam
     */
    public static Exam generateExam(Course course) {
        return generateExamHelper(course, false);
    }

    /**
     * Creates an Exam with a Channel and without student review dates set
     *
     * @param course      The associated course
     * @param channelName The channel name
     * @return The newly created Exam
     */
    public static Exam generateExam(Course course, String channelName) {
        return generateExamHelper(course, false, channelName);
    }

    /**
     * Creates an Exam without a Channel
     *
     * @param course      The associated course
     * @param visibleDate The visible date of the Exam
     * @param startDate   The start date of the Exam
     * @param endDate     The end date of the Exam
     * @param testExam    True, if the Exam is a test exam
     * @return The newly created Exam
     */
    public static Exam generateExam(Course course, ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate, boolean testExam) {
        return generateExam(course, visibleDate, startDate, endDate, testExam, null);
    }

    /**
     * Creates an Exam with a Channel
     *
     * @param course      The associated course
     * @param visibleDate The visible date of the Exam
     * @param startDate   The start date of the Exam
     * @param endDate     The end date of the Exam
     * @param testExam    True, if the Exam is a test exam
     * @param channelName The channel name
     * @return The newly created Exam
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
     * Creates a test Exam (test exams have no student review dates)
     *
     * @param course The associated course
     * @return The newly created Exam
     */
    public static Exam generateTestExam(Course course) {
        return generateExamHelper(course, true);
    }

    /**
     * Creates an exam without a Channel
     *
     * @param course   The associated course
     * @param testExam True, if the Exam is a test exam
     * @return The newly created Exam
     */
    private static Exam generateExamHelper(Course course, boolean testExam) {
        return generateExamHelper(course, testExam, null);
    }

    /**
     * Creates an Exam with a Channel
     *
     * @param course   The associated course
     * @param testExam True, if the Exam is a test exam
     * @return The newly created Exam
     */
    private static Exam generateExamHelper(Course course, boolean testExam, String channelName) {
        ZonedDateTime currentTime = now();
        return generateExam(course, currentTime, currentTime.plusMinutes(10), currentTime.plusMinutes(testExam ? 80 : 60), testExam, channelName);
    }

    /**
     * Creates an ExerciseGroup and adds it to the given exam
     *
     * @param mandatory True, if the exercise group is mandatory
     * @param exam      The exam that this exercise group should be added to
     * @return The newly created ExerciseGroup
     */
    public static ExerciseGroup generateExerciseGroup(boolean mandatory, Exam exam) {
        return generateExerciseGroupWithTitle(mandatory, exam, "Exercise group title");
    }

    /**
     * Creates an ExerciseGroup for an Exam
     *
     * @param mandatory True, if the exercise group is mandatory
     * @param exam      The exam that this exercise group should be added to
     * @param title     The title of the exercise group
     * @return The newly created ExerciseGroup
     */
    public static ExerciseGroup generateExerciseGroupWithTitle(boolean mandatory, Exam exam, String title) {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setTitle(title);
        exerciseGroup.setIsMandatory(mandatory);
        exam.addExerciseGroup(exerciseGroup);
        return exerciseGroup;
    }

    /**
     * Creates a StudentExam that is linked to the given exam
     *
     * @param exam The exam to be linked to the studentExam
     * @return The newly created StudentExam
     */
    public static StudentExam generateStudentExam(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(false);
        return studentExam;
    }

    /**
     * Creates a StudentExam for a test exam
     *
     * @param exam The exam to be linked to the studentExam
     * @return The newly created StudentExam
     */
    public static StudentExam generateStudentExamForTestExam(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setWorkingTime(exam.getWorkingTime());
        studentExam.setTestRun(false);
        return studentExam;
    }

    /**
     * Creates a StudentExam that is linked to the given exam and is a test run
     *
     * @param exam The exam to be linked to the studentExam
     * @return The newly created StudentExam
     */
    public static StudentExam generateExamTestRun(Exam exam) {
        StudentExam studentExam = new StudentExam();
        studentExam.setExam(exam);
        studentExam.setTestRun(true);
        return studentExam;
    }

    /**
     * Creates an Exam with an ExerciseGroup
     *
     * @param course    The associated course
     * @param mandatory True, if the exercise group is mandatory
     * @return The newly created Exam
     */
    public static Exam generateExamWithExerciseGroup(Course course, boolean mandatory) {
        Exam exam = generateExam(course);
        generateExerciseGroup(mandatory, exam);

        return exam;
    }

    /**
     * Creates a Set of ExamSessionDTOs from the given exam sessions
     *
     * @param session1 The first exam session
     * @param session2 The second exam session
     * @return The created set of ExamSessionDTOs
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
