package de.tum.in.www1.artemis.service.plagiarism;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismChecksConfig;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

class ContinuousPlagiarismControlServiceTest {

    private final ExerciseRepository exerciseRepository = mock();

    private final PlagiarismChecksService plagiarismChecksService = mock();

    private final ContinuousPlagiarismControlResultsService continuousPlagiarismControlResultsService = mock();

    private final ContinuousPlagiarismControlService service = new ContinuousPlagiarismControlService(exerciseRepository, plagiarismChecksService,
            continuousPlagiarismControlResultsService);

    @Test
    void shouldExecuteChecks() throws ExitException, IOException, ProgrammingLanguageNotSupportedForPlagiarismChecksException {
        // given: exercises with cpc enabled
        var textExercise = new TextExercise();
        textExercise.setDueDate(null);
        var modelingExercise = new ModelingExercise();
        modelingExercise.setDueDate(ZonedDateTime.now().minusDays(1));
        modelingExercise.setPlagiarismChecksConfig(PlagiarismChecksConfig.createDefault());
        modelingExercise.getPlagiarismChecksConfig().setContinuousPlagiarismControlPostDueDateChecksEnabled(true);
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(1));
        var exercises = Set.of(textExercise, modelingExercise, programmingExercise);
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // and: results of plagiarism checks
        var textPlagiarismResult = new TextPlagiarismResult();
        when(plagiarismChecksService.checkTextExercise(textExercise)).thenReturn(textPlagiarismResult);
        var modelingPlagiarismResult = new ModelingPlagiarismResult();
        when(plagiarismChecksService.checkModelingExercise(modelingExercise)).thenReturn(modelingPlagiarismResult);
        var programmingPlagiarismResult = new TextPlagiarismResult();
        when(plagiarismChecksService.checkProgrammingExercise(programmingExercise)).thenReturn(programmingPlagiarismResult);

        // when
        service.executeChecks();

        // then
        verify(plagiarismChecksService).checkTextExercise(textExercise);
        verify(continuousPlagiarismControlResultsService).handleCpcResult(textPlagiarismResult);
        verify(plagiarismChecksService).checkModelingExercise(modelingExercise);
        verify(continuousPlagiarismControlResultsService).handleCpcResult(modelingPlagiarismResult);
        verify(plagiarismChecksService).checkProgrammingExercise(programmingExercise);
        verify(continuousPlagiarismControlResultsService).handleCpcResult(programmingPlagiarismResult);
    }

    @Test
    void shouldNotExecuteChecksAfterDueDate() {
        // given: exercises with cpc enabled
        var exercise = new TextExercise();
        exercise.setDueDate(ZonedDateTime.now().minusDays(1));
        exercise.setPlagiarismChecksConfig(PlagiarismChecksConfig.createDefault());
        exercise.getPlagiarismChecksConfig().setContinuousPlagiarismControlPostDueDateChecksEnabled(false);

        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(Set.of(exercise));

        // when
        service.executeChecks();

        // then
        verifyNoInteractions(plagiarismChecksService, continuousPlagiarismControlResultsService);
    }

    @Test
    void shouldDoNothingForFileUploadAndQuizExercises() {
        // given
        var exercises = Set.of(new FileUploadExercise(), new QuizExercise());
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // when
        service.executeChecks();

        // then
        verifyNoInteractions(plagiarismChecksService, continuousPlagiarismControlResultsService);
    }

    @Test
    void shouldSilentAnyExceptionsThrown() throws Exception {
        // given
        var textExercise = new TextExercise();
        Set<Exercise> exercises = Set.of(textExercise);
        when(exerciseRepository.findAllExercisesWithDueDateOnOrAfterYesterdayAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);
        when(plagiarismChecksService.checkTextExercise(textExercise)).thenThrow(new IllegalStateException());

        // then
        assertThatNoException().isThrownBy(service::executeChecks);
    }
}
