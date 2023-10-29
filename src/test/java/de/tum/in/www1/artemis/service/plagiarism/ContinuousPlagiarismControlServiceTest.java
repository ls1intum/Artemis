package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.BasecodeException;
import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.plagiarism.*;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextSubmissionElement;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismComparisonRepository;
import de.tum.in.www1.artemis.service.metis.PostService;

class ContinuousPlagiarismControlServiceTest {

    private final ExerciseRepository exerciseRepository = mock();

    private final PlagiarismDetectionService plagiarismChecksService = mock();

    private final PlagiarismComparisonRepository plagiarismComparisonRepository = mock();

    private final PlagiarismCaseService plagiarismCaseService = mock();

    private final PlagiarismCaseRepository plagiarismCaseRepository = mock();

    private final PostService postService = mock();

    private final ContinuousPlagiarismControlService service = new ContinuousPlagiarismControlService(exerciseRepository, plagiarismChecksService, plagiarismComparisonRepository,
            plagiarismCaseService, plagiarismCaseRepository, postService);

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
        textPlagiarismResult.setComparisons(singleton(new PlagiarismComparison<>()));
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
        exercise.setTitle("Exercise 1");

        var participationText1 = createStudentParticipation(1);
        var participationText2 = createStudentParticipation(2);
        var participationText3 = createStudentParticipation(3);
        var participations = Set.of(participationText1, participationText2, participationText3);
        exercise.setStudentParticipations(participations);

        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(singleton(exercise));

        // and: results of plagiarism checks
        var textPlagiarismResult = new TextPlagiarismResult();
        var plagiarismComparison = createPlagiarismComparison(12, 1, 2);
        textPlagiarismResult.setComparisons(singleton(plagiarismComparison));
        when(plagiarismChecksService.checkTextExercise(exercise)).thenReturn(textPlagiarismResult);

        // and: plagiarism cases created implicitly
        var plagiarismCaseA = createPlagiarismCase(exercise);
        when(plagiarismCaseService.createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionA(), true)).thenReturn(plagiarismCaseA);
        var plagiarismCaseB = createPlagiarismCase(exercise);
        when(plagiarismCaseService.createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionB(), true)).thenReturn(plagiarismCaseB);

        // when
        service.executeChecks();

        // then
        verify(plagiarismComparisonRepository).updatePlagiarismComparisonStatus(12L, PlagiarismStatus.CONFIRMED);
        verify(plagiarismCaseService).createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionA(), true);
        verify(plagiarismCaseService).createOrAddToPlagiarismCaseForStudent(plagiarismComparison, plagiarismComparison.getSubmissionB(), true);
        verify(postService, times(2)).createContinuousPlagiarismControlPlagiarismCasePost(any());
        verifyNoMoreInteractions(plagiarismComparisonRepository, plagiarismCaseService, postService);
    }

    @Test
    void shouldRemoveStalePlagiarismCase() throws ExitException {
        // given: text exercise with cpc enabled
        var exercise = new TextExercise();
        exercise.setId(99L);
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(singleton(exercise));

        // and: results of plagiarism checks
        var textPlagiarismResult = new TextPlagiarismResult();
        textPlagiarismResult.setComparisons(emptySet());
        when(plagiarismChecksService.checkTextExercise(exercise)).thenReturn(textPlagiarismResult);

        // and: stale plagiarism case
        var plagiarismCase = createPlagiarismCase(exercise);
        plagiarismCase.setPlagiarismSubmissions(emptySet());
        when(plagiarismCaseRepository.findAllCreatedByContinuousPlagiarismControlByExerciseIdWithPlagiarismSubmissions(99L)).thenReturn(List.of(plagiarismCase));

        // when
        service.executeChecks();

        // then
        verify(plagiarismCaseRepository).delete(plagiarismCase);
        verifyNoMoreInteractions(plagiarismComparisonRepository, plagiarismCaseService, postService);
    }

    @Test
    void shouldNotExecuteChecksAfterDueDate() {
        // given: exercise with cpc enabled and post due data checks disabled
        var exercise = new TextExercise();
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        exercise.getPlagiarismDetectionConfig().setContinuousPlagiarismControlPostDueDateChecksEnabled(false);

        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(singleton(exercise));

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
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(singleton(textExercise));
        when(plagiarismChecksService.checkTextExercise(textExercise)).thenThrow(new BasecodeException("msg"));

        // then
        assertThatNoException().isThrownBy(service::executeChecks);
    }

    @Test
    void shouldSilentAnyUnknownExceptionsThrown() throws Exception {
        // given
        var textExercise = new TextExercise();
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(singleton(textExercise));
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

    private static PlagiarismCase createPlagiarismCase(Exercise exercise) {
        var plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(exercise);

        var student = new User();
        student.setFirstName("Student1");
        student.setLangKey("en");
        plagiarismCase.setStudent(student);

        var course = new Course();
        exercise.setCourse(course);

        return plagiarismCase;
    }
}
