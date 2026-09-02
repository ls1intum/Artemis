package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;

/**
 * Unit test for the quiz counterpart of the programming provisioner's post-import cleanup (see
 * {@link ProgrammingVariantAdapterServiceProvisionCleanupTest}): {@code importQuizExercise} saves the new quiz
 * BEFORE creating its channel and updating competency progress. A failure after that save leaves a persisted
 * clone behind while {@code provision()} never returns, so the pipeline's own null-variant cleanup can never find
 * it. This test forces a post-save import failure and asserts the clone is deleted instead of leaked.
 */
class QuizVariantAdapterServiceProvisionCleanupTest {

    private static final long SOURCE_ID = 1L;

    private static final long PROVISIONED_ID = 99L;

    private QuizExerciseTestRepository quizExerciseRepository;

    private QuizExerciseImportService quizExerciseImportService;

    private ExerciseDeletionService exerciseDeletionService;

    private QuizVariantAdapterService adapters;

    private QuizExercise source;

    private VariantJob job;

    private VariantGenerationRequestDTO request;

    @BeforeEach
    void setUp() throws Exception {
        quizExerciseRepository = mock(QuizExerciseTestRepository.class);
        quizExerciseImportService = mock(QuizExerciseImportService.class);
        exerciseDeletionService = mock(ExerciseDeletionService.class);

        adapters = new QuizVariantAdapterService(quizExerciseRepository, quizExerciseImportService, mock(QuizExerciseService.class), mock(VariantPlacementService.class),
                mock(ExerciseVariantJobService.class), new ObjectMapper(), mock(HyperionPromptTemplateService.class), mock(LLMTokenUsageService.class), mock(UserRepository.class),
                exerciseDeletionService, null);

        QuizExercise original = new QuizExercise();
        original.setId(SOURCE_ID);
        when(quizExerciseRepository.findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(SOURCE_ID)).thenReturn(Optional.of(original));

        source = new QuizExercise();
        source.setId(SOURCE_ID);
        job = new VariantJob();
        job.setChangePlan(new ChangePlan("Variant Title", "Statement", List.of("change"), List.of("invariant")));
        request = new VariantGenerationRequestDTO(null, null, null, null, null);
    }

    @Test
    void shouldDeleteTheImportedQuizWhenTheImportFailsAfterTheSave() throws Exception {
        // The import's first save is identity-preserving, so the new id is already on the passed instance when a
        // later step (channel creation, competency progress) throws.
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenAnswer(invocation -> {
            invocation.getArgument(0, QuizExercise.class).setId(PROVISIONED_ID);
            throw new RuntimeException("channel creation blew up");
        });

        assertThatThrownBy(() -> adapters.provision(source, request, job)).isInstanceOf(RuntimeException.class).hasMessageContaining("Importing the quiz variant clone failed");

        verify(exerciseDeletionService).delete(PROVISIONED_ID, true);
    }

    /** When the cleanup itself fails the clone survives, so its id must reach the pipeline's id-preserving path. */
    @Test
    void shouldReportTheSurvivingCloneWhenTheCleanupDeletionThrows() throws Exception {
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenAnswer(invocation -> {
            invocation.getArgument(0, QuizExercise.class).setId(PROVISIONED_ID);
            throw new RuntimeException("channel creation blew up");
        });
        doThrow(new RuntimeException("deletion blew up")).when(exerciseDeletionService).delete(PROVISIONED_ID, true);

        assertThatThrownBy(() -> adapters.provision(source, request, job)).isInstanceOf(LeftoverVariantExerciseException.class)
                .hasMessageContaining("Importing the quiz variant clone failed").extracting(exception -> ((LeftoverVariantExerciseException) exception).getExerciseId())
                .isEqualTo(PROVISIONED_ID);
    }

    @Test
    void shouldNotDeleteTheSourceWhenTheImportFailsBeforeTheSave() throws Exception {
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenThrow(new RuntimeException("copying the questions blew up"));

        assertThatThrownBy(() -> adapters.provision(source, request, job)).isInstanceOf(RuntimeException.class).hasMessageContaining("Importing the quiz variant clone failed");

        verify(exerciseDeletionService, never()).delete(anyLong(), anyBoolean());
    }

    @Test
    void shouldNotDeleteAnythingWhenProvisioningSucceeds() throws Exception {
        QuizExercise imported = new QuizExercise();
        imported.setId(PROVISIONED_ID);
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenReturn(imported);
        when(quizExerciseRepository.findByIdElseThrow(PROVISIONED_ID)).thenReturn(imported);

        adapters.provision(source, request, job);

        verify(exerciseDeletionService, never()).delete(anyLong(), anyBoolean());
    }
}
