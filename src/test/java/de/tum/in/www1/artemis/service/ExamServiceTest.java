package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.web.rest.dto.ExamChecklistDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;

public class ExamServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ExamService examService;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    private Exam exam1;

    private Exam examInThePast;

    private Exam examInTheFuture;

    private ExerciseGroup exerciseGroup1;

    @BeforeEach
    void init() {
        Course course1 = database.addEmptyCourse();
        exam1 = database.addExamWithExerciseGroup(course1, true);
        examInThePast = database.addExam(course1, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1));
        examInTheFuture = database.addExam(course1, ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(2), ZonedDateTime.now().plusDays(1));
        exerciseGroup1 = exam1.getExerciseGroups().get(0);
        examRepository.save(exam1);
    }

    @AfterEach
    public void resetDatabase() {
        exam1.removeExerciseGroup(exerciseGroup1);
        examRepository.save(exam1);
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testSetExamProperties() {
        StudentParticipation studentParticipation = new StudentParticipation();
        studentParticipation.setTestRun(true);
        QuizExercise exercise = new QuizExercise();
        exercise.setStudentParticipations(Set.of(studentParticipation));
        studentParticipation.setExercise(exercise);
        exerciseGroup1.addExercise(exercise);
        exerciseRepository.save(exercise);
        studentParticipationRepository.save(studentParticipation);

        examService.setExamProperties(exam1);

        assertThat(exercise.getTestRunParticipationsExist()).isEqualTo(true);
        exam1.getExerciseGroups().forEach(exerciseGroup -> {
            exerciseGroup.getExercises().forEach(exercise1 -> {
                assertThat(exercise.getNumberOfParticipations()).isNotNull();
            });
        });
        assertThat(exam1.getNumberOfRegisteredUsers()).isNotNull();
        assertThat(exam1.getNumberOfRegisteredUsers()).isEqualTo(0);
    }

    @Test
    public void testForNullIndexColumnError() {
        Exam examResult = examRepository.findByIdElseThrow(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        examResult = examRepository.findByIdWithExerciseGroupsElseThrow(exam1.getId());
        assertThat(examResult).isEqualTo(exam1);
        assertThat(examResult.getExerciseGroups().get(0)).isEqualTo(exerciseGroup1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testCanGetCurrentAndUpcomingExams() {
        List<Exam> exams = examRepository.findAllCurrentAndUpcomingExams();
        assertThat(exams.size()).isEqualTo(2);
        assertThat(exams).contains(exam1, examInTheFuture);
        assertThat(exams).doesNotContain(examInThePast);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void validateForStudentExamGeneration_differentCalculationTypesInExerciseGroup_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise includedTextExercise = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise notIncludedTextExercise = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 5.0, IncludedInOverallScore.NOT_INCLUDED);

        exerciseGroup.setExercises(Set.of(includedTextExercise, notIncludedTextExercise));

        BadRequestAlertException thrown = assertThrows(BadRequestAlertException.class, () -> examService.validateForStudentExamGeneration(exam),
                "Expected to throw bad request alert exception, but it didn't");

        assertTrue(thrown.getMessage().contains("All exercises in an exercise group must have the same meaning for the exam score"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void validateForStudentExamGeneration_differentPointsInExerciseGroup_shouldThrowException() {
        Exam exam = createExam(1, 1L, 9);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 4.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        BadRequestAlertException thrown = assertThrows(BadRequestAlertException.class, () -> examService.validateForStudentExamGeneration(exam),
                "Expected to throw bad request alert exception, but it didn't");

        assertTrue(thrown.getMessage().contains("All exercises in an exercise group need to give the same amount of points"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void validateForStudentExamGeneration_differentBonusInExerciseGroup_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 4.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        BadRequestAlertException thrown = assertThrows(BadRequestAlertException.class, () -> examService.validateForStudentExamGeneration(exam),
                "Expected to throw bad request alert exception, but it didn't");

        assertTrue(thrown.getMessage().contains("All exercises in an exercise group need to give the same amount of points"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void validateForStudentExamGeneration_tooManyPointsInMandatoryExercises_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 20.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 20.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        BadRequestAlertException thrown = assertThrows(BadRequestAlertException.class, () -> examService.validateForStudentExamGeneration(exam),
                "Expected to throw bad request alert exception, but it didn't");

        assertTrue(thrown.getMessage().contains("Check that you set the exam max points correctly! The max points a student can earn in the mandatory exercise groups is too big"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void validateForStudentExamGeneration_tooFewPointsInExercisesGroups_shouldThrowException() {
        Exam exam = createExam(1, 1L, 10);
        ExerciseGroup exerciseGroup = addExerciseGroupToExam(exam, 1L, true);
        TextExercise exercise1 = addNewTextExerciseToExerciseGroup(exerciseGroup, 1L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);
        TextExercise exercise2 = addNewTextExerciseToExerciseGroup(exerciseGroup, 2L, 5.0, 5.0, IncludedInOverallScore.INCLUDED_COMPLETELY);

        exerciseGroup.setExercises(Set.of(exercise1, exercise2));

        BadRequestAlertException thrown = assertThrows(BadRequestAlertException.class, () -> examService.validateForStudentExamGeneration(exam),
                "Expected to throw bad request alert exception, but it didn't");

        assertTrue(thrown.getMessage().contains("Check that you set the exam max points correctly! The max points a student can earn in the exercise groups is too low"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getChecklistStatsEmpty() {
        // check if general method works. More sophisticated test are within the ExamIntegrationTests
        ExamChecklistDTO examChecklistDTO = examService.getStatsForChecklist(exam1);
        assertThat(examChecklistDTO).isNotEqualTo(null);
        assertThat(examChecklistDTO.getNumberOfTestRuns()).isEqualTo(0);
        assertThat(examChecklistDTO.getNumberOfGeneratedStudentExams()).isEqualTo(0);
        assertThat(examChecklistDTO.getNumberOfExamsSubmitted()).isEqualTo(0);
        assertThat(examChecklistDTO.getNumberOfExamsStarted()).isEqualTo(0);
        assertThat(examChecklistDTO.getNumberOfAllComplaints()).isEqualTo(0);
        assertThat(examChecklistDTO.getNumberOfAllComplaintsDone()).isEqualTo(0);
        assertThat(examChecklistDTO.getAllExamExercisesAllStudentsPrepared()).isEqualTo(false);
    }

    private Exam createExam(int numberOfExercisesInExam, Long id, Integer maxPoints) {
        Exam exam = new Exam();
        exam.setMaxPoints(maxPoints);
        exam.setId(id);
        exam.setNumberOfExercisesInExam(numberOfExercisesInExam);
        exam.setStartDate(ZonedDateTime.now().plusDays(1));
        exam.setEndDate(ZonedDateTime.now().plusDays(2));
        exam.setNumberOfCorrectionRoundsInExam(1);
        exam.setModuleNumber("IN0001");
        exam.setNumberOfRegisteredUsers(10L);
        assertThat(exam.getNumberOfRegisteredUsers()).isEqualTo(10);
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
