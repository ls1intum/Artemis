package de.tum.in.www1.artemis.service.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.ExamChecklistDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

class ExamServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examservicetest";

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private Exam exam1;

    private Exam examInThePast;

    private Exam examInTheFuture;

    private ExerciseGroup exerciseGroup1;

    private int countExamsBeforeTests;

    @BeforeEach
    void init() {
        countExamsBeforeTests = examRepository.findAllCurrentAndUpcomingExams().size();
        Course course1 = courseUtilService.addEmptyCourse();
        exam1 = examUtilService.addExamWithExerciseGroup(course1, true);
        examInThePast = examUtilService.addExam(course1, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1));
        examInTheFuture = examUtilService.addExam(course1, ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(1));
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
        examRepository.save(exam1);
    }

    @AfterEach
    void tearDown() {
        exam1.removeExerciseGroup(exerciseGroup1);
        examRepository.save(exam1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testSetExamProperties() {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setTestRun(true);
        QuizExercise exercise = new QuizExercise();
        exercise.setStudentParticipations(Set.of(studentParticipation));
        studentParticipation.setExercise(exercise);
        exerciseGroup1.addExercise(exercise);
        exerciseRepository.save(exercise);
        studentParticipationRepository.save(studentParticipation);

        examService.setExamProperties(exam1);

        assertThat(exercise.getTestRunParticipationsExist()).isTrue();
        exam1.getExerciseGroups().forEach(exerciseGroup -> {
            exerciseGroup.getExercises().forEach(exercise1 -> {
                assertThat(exercise.getNumberOfParticipations()).isNotNull();
            });
        });
        assertThat(exam1.getNumberOfExamUsers()).isNotNull();
        assertThat(exam1.getNumberOfExamUsers()).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testForNullIndexColumnError() {
        Exam examResult = examRepository.findByIdElseThrow(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        examResult = examRepository.findByIdWithExerciseGroupsElseThrow(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        assertThat(examResult.getExerciseGroups().get(0)).isEqualTo(exerciseGroup1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCanGetCurrentAndUpcomingExams() {
        List<Exam> exams = examRepository.findAllCurrentAndUpcomingExams();
        assertThat(exams).hasSize(countExamsBeforeTests + 2).contains(exam1, examInTheFuture).doesNotContain(examInThePast);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void validateForStudentExamGeneration_differentCalculationTypesInExerciseGroup_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise includedTextExercise = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise notIncludedTextExercise = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 5.0, IncludedInOverallScore.NOT_INCLUDED);

        exerciseGroup.setExercises(Set.of(includedTextExercise, notIncludedTextExercise));

        assertThatExceptionOfType(BadRequestAlertException.class).as("Expected to throw bad request alert exception, but it didn't")
                .isThrownBy(() -> examService.validateForStudentExamGeneration(exam))
                .withMessageContaining("All exercises in an exercise group must have the same meaning for the exam score");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void validateForStudentExamGeneration_differentPointsInExerciseGroup_shouldThrowException() {
        Exam exam = createExam(1, 1L, 9);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 4.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        assertThatExceptionOfType(BadRequestAlertException.class).as("Expected to throw bad request alert exception, but it didn't")
                .isThrownBy(() -> examService.validateForStudentExamGeneration(exam))
                .withMessageContaining("All exercises in an exercise group need to give the same amount of points");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void validateForStudentExamGeneration_differentBonusInExerciseGroup_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 4.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        assertThatExceptionOfType(BadRequestAlertException.class).as("Expected to throw bad request alert exception, but it didn't")
                .isThrownBy(() -> examService.validateForStudentExamGeneration(exam))
                .withMessageContaining("All exercises in an exercise group need to give the same amount of points");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void validateForStudentExamGeneration_tooManyPointsInMandatoryExercises_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 20.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 20.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        assertThatExceptionOfType(BadRequestAlertException.class).as("Expected to throw bad request alert exception, but it didn't")
                .isThrownBy(() -> examService.validateForStudentExamGeneration(exam))
                .withMessageContaining("Check that you set the exam max points correctly! The max points a student can earn in the mandatory exercise groups is too big");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void validateForStudentExamGeneration_tooFewPointsInExercisesGroups_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        assertThatExceptionOfType(BadRequestAlertException.class).as("Expected to throw bad request alert exception, but it didn't")
                .isThrownBy(() -> examService.validateForStudentExamGeneration(exam))
                .withMessageContaining("Check that you set the exam max points correctly! The max points a student can earn in the exercise groups is too low");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getChecklistStatsEmpty() {
        // check if general method works. More sophisticated test are within the ExamIntegrationTests
        ExamChecklistDTO examChecklistDTO = examService.getStatsForChecklist(exam1, true);
        assertThat(examChecklistDTO).isNotNull();
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isZero();
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isZero();
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isZero();
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isZero();
        assertThat(examChecklistDTO.getNumberOfAllComplaints()).isZero();
        assertThat(examChecklistDTO.getNumberOfAllComplaintsDone()).isZero();
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isFalse();
    }

    @Nested
    class GetStudentExamGradesForSummaryTest {

        private static final int NUMBER_OF_STUDENTS = 1;

        private static final int NUMBER_OF_INSTRUCTORS = 1;

        private User instructor1;

        private User student1;

        private StudentExam studentExam;

        @BeforeEach
        void initializeTest() {
            userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 0, 0, NUMBER_OF_INSTRUCTORS);

            instructor1 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
            student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

            studentExam = new StudentExam();
            studentExam.setExam(exam1);
        }

        @Test
        @WithMockUser(username = "student1", roles = "STUDENT")
        void testThrowsExceptionIfNotSubmitted() {
            studentExam.setSubmitted(false);
            boolean isInstructor = false;

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> examService.getStudentExamGradesForSummary(student1, studentExam, isInstructor))
                    .withMessage("You are not allowed to access the grade summary of a student exam which was NOT submitted!");
        }

        @Test
        @WithMockUser(username = "student1", roles = "STUDENT")
        void testThrowsExceptionIfNotPublished() {
            studentExam.setSubmitted(true);
            studentExam.getExam().setPublishResultsDate(ZonedDateTime.now().plusDays(5));
            boolean isInstructor = false;

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> examService.getStudentExamGradesForSummary(student1, studentExam, isInstructor))
                    .withMessage("You are not allowed to access the grade summary of a student exam before the release date of results");
        }

        @Test
        @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
        void testDoesNotThrowExceptionForInstructors() {
            studentExam.setSubmitted(false);
            studentExam.getExam().setPublishResultsDate(ZonedDateTime.now().plusDays(5));
            studentExam.getExam().setTestExam(true); // test runs are an edge case where instructors want to have access before the publishing date of results
            studentExam.setUser(instructor1);
            boolean isInstructor = true;

            examService.getStudentExamGradesForSummary(student1, studentExam, isInstructor);
        }
    }

    private Exam createExam(int numberOfExercisesInExam, Long id, Integer maxPoints) {
        Exam exam = new Exam();
        exam.setExamMaxPoints(maxPoints);
        exam.setId(id);
        exam.setNumberOfExercisesInExam(numberOfExercisesInExam);
        exam.setStartDate(ZonedDateTime.now().plusDays(1));
        exam.setEndDate(ZonedDateTime.now().plusDays(2));
        exam.setNumberOfCorrectionRoundsInExam(1);
        exam.setModuleNumber("IN0001");
        exam.setNumberOfExamUsers(10L);
        assertThat(exam.getNumberOfExamUsers()).isEqualTo(10);
        assertThat(exam.getModuleNumber()).isEqualTo("IN0001");
        return exam;
    }

    private ExerciseGroup addExerciseGroupToExam(Exam exam, Long id, boolean isMandatory) {
        ExerciseGroup exerciseGroup = new ExerciseGroup();
        exerciseGroup.setId(id);
        exerciseGroup.setExam(exam);
        exam.addExerciseGroup(exerciseGroup);
        exerciseGroup.setIsMandatory(isMandatory);
        return exerciseGroup;
    }

    private TextExercise addNewTextExerciseToExerciseGroup(ExerciseGroup exerciseGroup, Long id, Double maxPoints, Double maxBonusPoints,
            IncludedInOverallScore includedInOverallScore) {
        TextExercise includedTextExercise = new TextExercise();
        includedTextExercise.setId(id);
        includedTextExercise.setMaxPoints(maxPoints);
        includedTextExercise.setBonusPoints(maxBonusPoints);
        includedTextExercise.setIncludedInOverallScore(includedInOverallScore);
        includedTextExercise.setExerciseGroup(exerciseGroup);
        return includedTextExercise;
    }

}
