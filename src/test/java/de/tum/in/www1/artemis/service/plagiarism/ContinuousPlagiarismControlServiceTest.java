package de.tum.in.www1.artemis.service.plagiarism;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.ExerciseRepository;

class ContinuousPlagiarismControlServiceTest {

    private final ExerciseRepository exerciseRepository = mock();

    private final PlagiarismChecksService plagiarismChecksService = mock();

    private final ContinuousPlagiarismControlService service = new ContinuousPlagiarismControlService(exerciseRepository, plagiarismChecksService);

    @Test
    void shouldExecuteChecks() throws ExitException, IOException, ProgrammingLanguageNotSupportedFroPlagiarismChecksException {
        // given
        var textExercise = new TextExercise();
        var modelingExercise = new ModelingExercise();
        var programmingExercise = new ProgrammingExercise();
        var exercises = Set.of(textExercise, modelingExercise, programmingExercise);
        when(exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // when
        service.executeChecks();

        // then
        verify(plagiarismChecksService).checkTextExercise(textExercise);
        verify(plagiarismChecksService).checkModelingExercise(modelingExercise);
        verify(plagiarismChecksService).checkProgrammingExercise(programmingExercise);
    }

    @Test
    void shouldDoNothingForFileUploadAndQuizExercises() {
        // given
        var exercises = Set.of(new FileUploadExercise(), new QuizExercise());
        when(exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // when
        service.executeChecks();

        // then
        verifyNoInteractions(plagiarismChecksService);
    }

    @Test
    void shouldSilentAnyExceptionsThrown() throws Exception {
        // given
        var textExercise = new TextExercise();
        Set<Exercise> exercises = Set.of(textExercise);
        when(exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);
        when(plagiarismChecksService.checkTextExercise(textExercise)).thenThrow(new IllegalStateException());

        // then: no exception thrown
        service.executeChecks();
    }
}
