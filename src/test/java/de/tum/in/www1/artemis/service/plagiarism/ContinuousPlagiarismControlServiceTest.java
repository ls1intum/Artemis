package de.tum.in.www1.artemis.service.plagiarism;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.jplag.exceptions.ExitException;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseType;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.text.TextPlagiarismResult;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ModelingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.TextExerciseRepository;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismResultRepository;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeature;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;

class ContinuousPlagiarismControlServiceTest {

    private final ExerciseRepository exerciseRepository = mock();

    private final TextExerciseRepository textExerciseRepository = mock();

    private final TextPlagiarismDetectionService textPlagiarismDetectionService = mock();

    private final ProgrammingExerciseRepository programmingExerciseRepository = mock();

    private final ProgrammingLanguageFeatureService programmingLanguageFeatureService = mock();

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService = mock();

    private final ModelingExerciseRepository modelingExerciseRepository = mock();

    private final ModelingPlagiarismDetectionService modelingPlagiarismDetectionService = mock();

    private final PlagiarismResultRepository plagiarismResultRepository = mock();

    private final ContinuousPlagiarismControlService service = new ContinuousPlagiarismControlService(exerciseRepository, textExerciseRepository, textPlagiarismDetectionService,
            programmingExerciseRepository, programmingLanguageFeatureService, programmingPlagiarismDetectionService, modelingExerciseRepository, modelingPlagiarismDetectionService,
            plagiarismResultRepository);

    @Test
    void shouldExecuteChecks() throws ExitException, IOException {
        // given
        Set<Exercise> exercises = Set.of(new MockExercise(ExerciseType.TEXT, 1), new MockExercise(ExerciseType.PROGRAMMING, 2), new MockExercise(ExerciseType.MODELING, 3));
        when(exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // and
        var textExercise = new TextExercise();
        when(textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(1)).thenReturn(textExercise);
        var textPlagiarismResult = new TextPlagiarismResult();
        textPlagiarismResult.setComparisons(emptySet());
        when(textPlagiarismDetectionService.checkPlagiarism(eq(textExercise), anyFloat(), anyInt(), anyInt())).thenReturn(textPlagiarismResult);

        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setId(2L);
        var programmingLanguageFeature = new ProgrammingLanguageFeature(null, false, false, true, false, false, emptyList(), false, false, false);
        when(programmingLanguageFeatureService.getProgrammingLanguageFeatures(any())).thenReturn(programmingLanguageFeature);
        when(programmingExerciseRepository.findByIdWithStudentParticipationsAndLegalSubmissionsElseThrow(2)).thenReturn(programmingExercise);
        var programmingPlagiarismResult = new TextPlagiarismResult();
        programmingPlagiarismResult.setComparisons(emptySet());
        when(programmingPlagiarismDetectionService.checkPlagiarism(eq(2L), anyFloat(), anyInt())).thenReturn(programmingPlagiarismResult);

        var modelingExercise = new ModelingExercise();
        modelingExercise.setId(3L);
        when(modelingExerciseRepository.findByIdWithStudentParticipationsSubmissionsResultsElseThrow(3L)).thenReturn(modelingExercise);
        var modelingPlagiarismResult = new ModelingPlagiarismResult();
        modelingPlagiarismResult.setComparisons(emptySet());
        when(modelingPlagiarismDetectionService.checkPlagiarism(eq(modelingExercise), anyDouble(), anyInt(), anyInt())).thenReturn(modelingPlagiarismResult);

        // when
        service.executeChecks();

        // then
        verify(plagiarismResultRepository).savePlagiarismResultAndRemovePrevious(textPlagiarismResult);
        verify(plagiarismResultRepository).prepareResultForClient(textPlagiarismResult);

        verify(plagiarismResultRepository).prepareResultForClient(programmingPlagiarismResult);

        verify(plagiarismResultRepository).savePlagiarismResultAndRemovePrevious(modelingPlagiarismResult);
        verify(plagiarismResultRepository).prepareResultForClient(modelingPlagiarismResult);
    }

    @Test
    void shouldDoNothingForFileUploadAndQuizExercises() {
        // given
        Set<Exercise> exercises = Set.of(new MockExercise(ExerciseType.FILE_UPLOAD, 1), new MockExercise(ExerciseType.QUIZ, 2));
        when(exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // when
        service.executeChecks();

        // then
        verifyNoInteractions(textExerciseRepository, textPlagiarismDetectionService, programmingExerciseRepository, programmingLanguageFeatureService,
                programmingPlagiarismDetectionService, modelingExerciseRepository, modelingPlagiarismDetectionService, plagiarismResultRepository);
    }

    @Test
    void shouldSilentAnyExceptionsThrown() throws Exception {
        // given
        Set<Exercise> exercises = Set.of(new MockExercise(ExerciseType.TEXT, 1));
        when(exerciseRepository.findAllExercisesWithCurrentOrUpcomingDueDateAndContinuousPlagiarismControlEnabledIsTrue()).thenReturn(exercises);

        // and
        var textExercise = new TextExercise();
        textExercise.setId(1L);
        when(textExerciseRepository.findByIdWithStudentParticipationsAndSubmissionsElseThrow(1)).thenThrow(new IllegalStateException());

        // then: no exception thrown
        service.executeChecks();
    }

    private static class MockExercise extends Exercise {

        private final ExerciseType exerciseType;

        MockExercise(ExerciseType exerciseType, long id) {
            this.exerciseType = exerciseType;
            super.setId(id);
        }

        @Override
        public ExerciseType getExerciseType() {
            return exerciseType;
        }
    }
}
