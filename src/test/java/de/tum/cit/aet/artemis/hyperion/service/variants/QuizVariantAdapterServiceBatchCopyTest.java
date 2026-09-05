package de.tum.cit.aet.artemis.hyperion.service.variants;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.dto.CreateExerciseVariantGroupDTO;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantPlacementDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionPromptTemplateService;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseImportService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;

/**
 * The import resets the target's batches before re-copying them from its source, which only works while the two are
 * different objects. Provisioning used to hand it a single instance for both roles, so the reset wiped the very
 * batches about to be copied and a SYNCHRONIZED or BATCHED source produced a variant with none — a quiz its
 * instructor cannot start. These tests pin that the import is given a source that still holds the batches, and that
 * the one case which legitimately drops them, a group placement forcing INDIVIDUAL mode, still does.
 */
class QuizVariantAdapterServiceBatchCopyTest {

    private static final long SOURCE_ID = 1L;

    private static final long PROVISIONED_ID = 99L;

    private QuizExerciseTestRepository quizExerciseRepository;

    private QuizExerciseImportService quizExerciseImportService;

    private QuizVariantAdapterService adapters;

    private QuizExercise source;

    private VariantJob job;

    @BeforeEach
    void setUp() throws Exception {
        quizExerciseRepository = mock(QuizExerciseTestRepository.class);
        quizExerciseImportService = mock(QuizExerciseImportService.class);

        adapters = new QuizVariantAdapterService(quizExerciseRepository, quizExerciseImportService, mock(QuizExerciseService.class), mock(VariantPlacementService.class),
                mock(ExerciseVariantJobService.class), new ObjectMapper(), mock(HyperionPromptTemplateService.class), mock(LLMTokenUsageService.class), mock(UserRepository.class),
                mock(ExerciseDeletionService.class), null);

        // Every load returns its own detached graph, which is what the repository does outside a transaction — the
        // whole point being that the two roles must not end up sharing one object.
        when(quizExerciseRepository.findWithEagerQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaById(SOURCE_ID))
                .thenAnswer(invocation -> Optional.of(synchronizedQuizWithTwoBatches()));

        QuizExercise provisioned = new QuizExercise();
        provisioned.setId(PROVISIONED_ID);
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenReturn(provisioned);
        when(quizExerciseRepository.findByIdElseThrow(anyLong())).thenReturn(provisioned);

        source = new QuizExercise();
        source.setId(SOURCE_ID);
        job = new VariantJob();
        job.setChangePlan(new ChangePlan("Variant Title", "Statement", List.of("change"), List.of("invariant")));
    }

    @Test
    void theVariantOfASynchronizedQuizKeepsItsBatches() throws Exception {
        // The stub replays what the import actually does — reset the target's batches, then copy the source's onto it
        // — because that order is the whole bug: with one object for both roles the reset empties what the copy reads.
        ArgumentCaptor<QuizExercise> target = ArgumentCaptor.forClass(QuizExercise.class);
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenAnswer(invocation -> {
            QuizExercise imported = invocation.getArgument(0, QuizExercise.class);
            QuizExercise importedFrom = invocation.getArgument(1, QuizExercise.class);
            imported.setQuizBatches(new HashSet<>());
            Set<QuizBatch> copied = new HashSet<>();
            importedFrom.getQuizBatches().forEach(batch -> copied.add(new QuizBatch()));
            imported.setQuizBatches(copied);
            imported.setId(PROVISIONED_ID);
            return imported;
        });

        adapters.provision(source, standaloneRequest(), job);

        verify(quizExerciseImportService).importQuizExercise(target.capture(), any(), any());
        assertThat(target.getValue().getQuizBatches()).hasSize(2);
    }

    @Test
    void doesNotHandTheImportTheSameInstanceForBothRoles() throws Exception {
        // The aliasing is the cause: the reset and the copy would act on one object.
        ArgumentCaptor<QuizExercise> target = ArgumentCaptor.forClass(QuizExercise.class);
        ArgumentCaptor<QuizExercise> importSource = ArgumentCaptor.forClass(QuizExercise.class);

        adapters.provision(source, standaloneRequest(), job);

        verify(quizExerciseImportService).importQuizExercise(target.capture(), importSource.capture(), any());
        assertThat(target.getValue()).isNotSameAs(importSource.getValue());
    }

    @Test
    void theVariantKeepsThePlannedMetadataRatherThanTheSourcesOwn() throws Exception {
        // Separating the two roles must not let the source's values win back. It does not: the import fills a field
        // from the source only when the target leaves it empty, so the stub replays that rule and the planned title,
        // difficulty and problem statement have to survive it.
        ArgumentCaptor<QuizExercise> target = ArgumentCaptor.forClass(QuizExercise.class);
        when(quizExerciseImportService.importQuizExercise(any(), any(), any())).thenAnswer(invocation -> {
            QuizExercise imported = invocation.getArgument(0, QuizExercise.class);
            QuizExercise importedFrom = invocation.getArgument(1, QuizExercise.class);
            imported.setTitle(imported.getTitle() != null ? imported.getTitle() : importedFrom.getTitle());
            imported.setDifficulty(imported.getDifficulty() != null ? imported.getDifficulty() : importedFrom.getDifficulty());
            imported.setProblemStatement(imported.getProblemStatement() != null ? imported.getProblemStatement() : importedFrom.getProblemStatement());
            imported.setId(PROVISIONED_ID);
            return imported;
        });

        adapters.provision(source, standaloneRequest(DifficultyLevel.HARD), job);

        verify(quizExerciseImportService).importQuizExercise(target.capture(), any(), any());
        assertThat(target.getValue().getTitle()).isEqualTo("Variant Title");
        assertThat(target.getValue().getDifficulty()).isEqualTo(DifficultyLevel.HARD);
        assertThat(target.getValue().getProblemStatement()).isEqualTo("Statement");
    }

    @Test
    void stillDropsTheBatchesWhenAGroupPlacementForcesIndividualMode() throws Exception {
        // A group's shared per-student timeline cannot host a synchronized run, so this variant legitimately loses
        // the batches — and the source must say so too, or the import would copy them straight back.
        adapters.provision(source, groupRequest(), job);

        assertThat(importSource().getQuizMode()).isEqualTo(QuizMode.INDIVIDUAL);
        assertThat(importSource().getQuizBatches()).isEmpty();
    }

    /** The instance the import was given as its content source. */
    private QuizExercise importSource() throws Exception {
        ArgumentCaptor<QuizExercise> captor = ArgumentCaptor.forClass(QuizExercise.class);
        verify(quizExerciseImportService).importQuizExercise(any(), captor.capture(), any());
        return captor.getValue();
    }

    private QuizExercise synchronizedQuizWithTwoBatches() {
        QuizExercise quiz = new QuizExercise();
        quiz.setId(SOURCE_ID);
        quiz.setQuizMode(QuizMode.SYNCHRONIZED);
        quiz.setTitle("Original quiz");
        quiz.setProblemStatement("Original statement");
        quiz.setDifficulty(DifficultyLevel.EASY);
        Set<QuizBatch> batches = new HashSet<>();
        batches.add(new QuizBatch());
        batches.add(new QuizBatch());
        quiz.setQuizBatches(batches);
        return quiz;
    }

    private static VariantGenerationRequestDTO standaloneRequest() {
        return standaloneRequest(null);
    }

    private static VariantGenerationRequestDTO standaloneRequest(DifficultyLevel targetDifficulty) {
        return new VariantGenerationRequestDTO(targetDifficulty, null, null, null, new VariantPlacementDTO(VariantPlacementDTO.PlacementType.STANDALONE, null, null));
    }

    private static VariantGenerationRequestDTO groupRequest() {
        CreateExerciseVariantGroupDTO group = new CreateExerciseVariantGroupDTO("Variants of the quiz", null, null, null, null, null, null);
        return new VariantGenerationRequestDTO(null, null, null, null, new VariantPlacementDTO(VariantPlacementDTO.PlacementType.NEW_GROUP, null, group));
    }
}
