package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.BasecodeException;
import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismComparison;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismDetectionConfig;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.*;

class ContinuousPlagiarismControlServiceTest {

    private final ExerciseRepository exerciseRepository = mock();

    private final PlagiarismDetectionService plagiarismChecksService = mock();

    private final PlagiarismComparisonRepository plagiarismComparisonRepository = mock();

    private final PlagiarismCaseService plagiarismCaseService = mock();

    private final ContinuousPlagiarismControlService service = new ContinuousPlagiarismControlService(exerciseRepository, plagiarismChecksService, plagiarismComparisonRepository,
            plagiarismCaseService);

    @Test
    void shouldExecuteChecks() throws ExitException, IOException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        // given: text exercise with cpc enabled
        var textExercise = new TextExercise();
        textExercise.setDueDate(null);

        // and: modeling exercise with cpc and post due date checks enabled
        var modelingExercise = new ModelingExercise();
        modelingExercise.setDueDate(ZonedDateTime.now().minusDays(1));
        modelingExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        modelingExercise.getPlagiarismDetectionConfig().setContinuousPlagiarismControlPostDueDateChecksEnabled(true);

        // and: programing exercise with cpc enabled
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(1));

        // and: all exercises returned by query
        var exercises = Set.of(textExercise, modelingExercise, programmingExercise);
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // and: results of plagiarism checks
        var textPlagiarismResult = new TextPlagiarismResult();
        textPlagiarismResult.setComparisons(Set.of(new PlagiarismComparison<>()));
        when(plagiarismChecksService.checkTextExercise(textExercise)).thenReturn(textPlagiarismResult);
        var modelingPlagiarismResult = new ModelingPlagiarismResult();
        when(plagiarismChecksService.checkModelingExercise(modelingExercise)).thenReturn(modelingPlagiarismResult);
        var programmingPlagiarismResult = new TextPlagiarismResult();
        when(plagiarismChecksService.checkProgrammingExercise(programmingExercise)).thenReturn(programmingPlagiarismResult);

        // when
        service.executeChecks();

        // then
        verify(plagiarismChecksService).checkTextExercise(textExercise);
        verify(plagiarismChecksService).checkModelingExercise(modelingExercise);
        verify(plagiarismChecksService).checkProgrammingExercise(programmingExercise);
    }

    @Test
    void shouldAddSubmissionsToPlagiarismCase() throws ExitException {
        // given: text exercise with cpc enabled
        var exercise = new TextExercise();
        exercise.setId(99L);

        var participationText1 = createStudentParticipation(1);
        var participationText2 = createStudentParticipation(2);
        var participationText3 = createStudentParticipation(3);
        var participations = Set.of(participationText1, participationText2, participationText3);
        exercise.setStudentParticipations(participations);

        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(Set.of(exercise));

        // and: results of plagiarism checks
        var textPlagiarismResult = new TextPlagiarismResult();
        var plagiarismComparison = createPlagiarismComparison(12, 1, 2);
        textPlagiarismResult.setComparisons(singleton(plagiarismComparison));
        when(plagiarismChecksService.checkTextExercise(exercise)).thenReturn(textPlagiarismResult);

        // and: one existing plagiarism comparison
        var existingPlagiarismComparison = createPlagiarismComparison(23, 2, 3);
        when(plagiarismComparisonRepository.findAllByPlagiarismResultExerciseId(exercise.getId())).thenReturn(singleton(existingPlagiarismComparison));

        // when
        service.executeChecks();

        // then
        var inOrder = inOrder(plagiarismCaseService);
        inOrder.verify(plagiarismCaseService).createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionA());
        inOrder.verify(plagiarismCaseService).createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionB());
        inOrder.verify(plagiarismCaseService).removeSubmissionsInPlagiarismCasesForComparison(existingPlagiarismComparison.getId());
        verifyNoMoreInteractions(plagiarismCaseService);
    }

    @Test
    void shouldNotExecuteChecksAfterDueDate() {
        // given: exercise with cpc enabled and post due data checks disabled
        var exercise = new TextExercise();
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        exercise.getPlagiarismDetectionConfig().setContinuousPlagiarismControlPostDueDateChecksEnabled(false);

        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(Set.of(exercise));

        // when
        service.executeChecks();

        // then
        verifyNoInteractions(plagiarismChecksService, plagiarismComparisonRepository, plagiarismCaseService);
    }

    @Test
    void shouldDoNothingForFileUploadAndQuizExercises() {
        // given
        var exercises = Set.of(new FileUploadExercise(), new QuizExercise());
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // when
        service.executeChecks();

        // then
        verifyNoInteractions(plagiarismChecksService, plagiarismComparisonRepository, plagiarismCaseService);
    }

    @Test
    void shouldSilentAnyJPlagExceptionsThrown() throws Exception {
        // given
        var textExercise = new TextExercise();
        Set<Exercise> exercises = Set.of(textExercise);
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);
        when(plagiarismChecksService.checkTextExercise(textExercise)).thenThrow(new BasecodeException("msg"));

        // then
        assertThatNoException().isThrownBy(service::executeChecks);
    }

    @Test
    void shouldSilentAnyUnknownExceptionsThrown() throws Exception {
        // given
        var textExercise = new TextExercise();
        Set<Exercise> exercises = Set.of(textExercise);
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);
        when(plagiarismChecksService.checkTextExercise(textExercise)).thenThrow(new IllegalStateException());

        // then
        assertThatNoException().isThrownBy(service::executeChecks);
    }

    private static StudentParticipation createStudentParticipation(long submissionId) {
        var participation = new StudentParticipation();
        var submission = new TextSubmission();
        submission.setId(submissionId);
        participation.setSubmissions(singleton(submission));
        return participation;
    }

    @SuppressWarnings("SameParameterValue")
    private static PlagiarismComparison<TextSubmissionElement> createPlagiarismComparison(long comparisonId, long submissionIdA, long submissionIdB) {
        var comparison = new PlagiarismComparison<TextSubmissionElement>();
        comparison.setId(comparisonId);

        var submissionA = new PlagiarismSubmission<>();
        submissionA.setId(100 + submissionIdA);
        submissionA.setSubmissionId(submissionIdA);
        comparison.setSubmissionA(submissionA);

        var submissionB = new PlagiarismSubmission<>();
        submissionA.setId(100 + submissionIdB);
        submissionB.setSubmissionId(submissionIdB);
        comparison.setSubmissionB(submissionB);

        return comparison;
    }
}
