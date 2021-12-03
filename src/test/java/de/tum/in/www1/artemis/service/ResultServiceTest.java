package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.FeedbackType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

public class ResultServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ResultService resultService;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository participationRepository;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation examStudentParticipation;

    @BeforeEach
    public void reset() {
        database.addUsers(2, 1, 1, 1);
        Course course = database.addCourseWithOneProgrammingExercise();
        this.programmingExercise = (ProgrammingExercise) course.getExercises().stream().filter(exercise -> exercise instanceof ProgrammingExercise).findAny().orElseThrow();
        // This is done to avoid proxy issues in the processNewResult method of the ResultService.
        this.programmingExerciseStudentParticipation = database.addStudentParticipationForProgrammingExercise(this.programmingExercise, "student1");

        ProgrammingExercise examProgrammingExercise = database.addCourseExamExerciseGroupWithOneProgrammingExercise();
        this.examStudentParticipation = database.addStudentParticipationForProgrammingExercise(examProgrammingExercise, "student1");
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetFeedbacksForResultAsTA() {
        this.testGetFeedbacksForResultAsCurrentUser();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetFeedbacksForResultAsEditor() {
        this.testGetFeedbacksForResultAsCurrentUser();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetFeedbacksForResultAsInstructor() {
        this.testGetFeedbacksForResultAsCurrentUser();
    }

    private void testGetFeedbacksForResultAsCurrentUser() {
        Result result = database.addResultToParticipation(null, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudent_shouldFilterInExamsBeforePublish() {
        Exam exam = examStudentParticipation.getExercise().getExamViaExerciseGroupOrCourseMember();
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(2));
        examRepository.save(exam);
        Result result = database.addResultToParticipation(null, null, examStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).collect(Collectors.toList());

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudent_shouldFilterInExamsAfterPublish() {
        Exam exam = examStudentParticipation.getExercise().getExamViaExerciseGroupOrCourseMember();
        exam.setPublishResultsDate(ZonedDateTime.now().minusDays(2));
        examRepository.save(exam);
        Result result = database.addResultToParticipation(null, null, examStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).collect(Collectors.toList());

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudent_shouldFilterInCourseBeforeDueDate() {
        programmingExercise.dueDate(ZonedDateTime.now().plusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = database.addResultToParticipation(null, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).collect(Collectors.toList());

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudent_shouldFilterInCourseAfterDueDate() {
        programmingExercise.dueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = database.addResultToParticipation(null, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).collect(Collectors.toList());

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudent_shouldFilterInCourseBeforeAssessmentDueDateWithNonAutomaticResult() {
        programmingExercise.dueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.assessmentDueDate(ZonedDateTime.now().plusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);
        result = database.addFeedbackToResult(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream()
                .filter(feedback -> !feedback.isInvisible() && feedback.getType() != null && feedback.getType().equals(FeedbackType.AUTOMATIC)).collect(Collectors.toList());

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudent_shouldFilterInCourseAfterAssessmentDueDateWithNonAutomaticResult() {
        programmingExercise.dueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.assessmentDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);
        result = database.addFeedbackToResult(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).collect(Collectors.toList());

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudent_shouldFilterInCourseAfterAssessmentDueDateWithAutomaticResult() {
        programmingExercise.dueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.assessmentDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = database.addResultToParticipation(AssessmentType.AUTOMATIC, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);
        result = database.addFeedbackToResult(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).collect(Collectors.toList());

        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @ParameterizedTest
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudentShouldOnlyFilterAutomaticResultBeforeLastDueDate(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        final var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().plusDays(2));
        participationRepository.save(participation2);

        Result result = database.addResultToParticipation(assessmentType, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);
        result = database.addFeedbackToResult(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks;
        if (AssessmentType.AUTOMATIC == assessmentType) {
            expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).collect(Collectors.toList());
            assertThat(expectedFeedbacks).hasSize(2);
        }
        else {
            expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).collect(Collectors.toList());
            assertThat(expectedFeedbacks).hasSize(3);
        }
        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }

    @ParameterizedTest
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetFeedbacksForResultAsStudentShouldNotFilterAfterLatestDueDate(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        final var participation2 = database.addStudentParticipationForProgrammingExercise(programmingExercise, "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().minusHours(1));
        participationRepository.save(participation2);

        Result result = database.addResultToParticipation(assessmentType, null, programmingExerciseStudentParticipation);
        result = database.addVariousVisibilityFeedbackToResults(result);
        result = database.addFeedbackToResult(ModelFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks;
        expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).collect(Collectors.toList());
        assertThat(expectedFeedbacks).hasSize(3);
        assertThat(resultService.getFeedbacksForResult(result)).isEqualTo(expectedFeedbacks);
    }
}
