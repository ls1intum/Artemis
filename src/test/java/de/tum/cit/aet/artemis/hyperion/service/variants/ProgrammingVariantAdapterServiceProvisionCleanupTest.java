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
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionConsistencyCheckService;
import de.tum.cit.aet.artemis.hyperion.service.HyperionProgrammingExerciseContextRendererService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseValidationService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit test for the P0-3 fix: {@code importProgrammingExercise} already persists the DB row + VCS repos + CI
 * build plans before {@code provision()} does any of its OWN post-import work (remapping test-case ids,
 * re-applying the plan's problem statement, saving, task sync). If any of THAT throws, {@code provision()} never
 * returns — so the pipeline's own null-variant cleanup path can never find the exercise to remove it. This test
 * forces a post-import failure and asserts the just-imported exercise gets deleted instead of leaked, and that
 * nothing gets deleted when provisioning succeeds normally.
 */
class ProgrammingVariantAdapterServiceProvisionCleanupTest {

    private ProgrammingExerciseImportService programmingExerciseImportService;

    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    private ProgrammingExerciseTaskService programmingExerciseTaskService;

    private ExerciseDeletionService exerciseDeletionService;

    private ProgrammingVariantAdapterService adapters;

    private ProgrammingExercise imported;

    private ProgrammingExercise source;

    private VariantJob job;

    private VariantGenerationRequestDTO request;

    @BeforeEach
    void setUp() throws Exception {
        programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);
        ProgrammingExerciseTaskRepository programmingExerciseTaskRepository = mock(ProgrammingExerciseTaskRepository.class);
        programmingExerciseImportService = mock(ProgrammingExerciseImportService.class);
        programmingExerciseTaskService = mock(ProgrammingExerciseTaskService.class);
        exerciseDeletionService = mock(ExerciseDeletionService.class);
        ProgrammingExerciseValidationService programmingExerciseValidationService = mock(ProgrammingExerciseValidationService.class);

        adapters = new ProgrammingVariantAdapterService(mock(HyperionProgrammingExerciseContextRendererService.class), programmingExerciseImportService,
                programmingExerciseValidationService, programmingExerciseRepository, programmingExerciseTaskRepository, programmingExerciseTaskService,
                mock(ProgrammingExerciseTestCaseRepository.class), mock(UserRepository.class), mock(ProgrammingVariantToolsetService.class),
                mock(VariantBuildVerificationService.class), mock(HyperionConsistencyCheckService.class), mock(VariantPlacementService.class),
                mock(ExerciseVariantJobService.class), exerciseDeletionService);

        ProgrammingExercise original = mock(ProgrammingExercise.class);
        when(original.getId()).thenReturn(1L);
        when(original.getCategories()).thenReturn(Set.of());
        when(original.getTestCases()).thenReturn(Set.of());
        when(programmingExerciseRepository
                .findByIdWithEagerBuildConfigTestCasesStaticCodeAnalysisCategoriesAndTemplateAndSolutionParticipationsAndAuxReposAndBuildConfigAndGradingCriteria(1L))
                .thenReturn(Optional.of(original));
        when(programmingExerciseTaskRepository.findByExerciseIdWithTestCases(1L)).thenReturn(Set.of());
        when(programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(1L)).thenReturn(Optional.empty());
        when(programmingExerciseValidationService.preCheckProjectExistsOnVCSOrCI(any(), any())).thenReturn(false);

        imported = mock(ProgrammingExercise.class);
        when(imported.getId()).thenReturn(99L);
        when(imported.getTestCases()).thenReturn(Set.of());
        when(programmingExerciseImportService.importProgrammingExercise(any(), any(), anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(imported);

        source = mock(ProgrammingExercise.class);
        when(source.getId()).thenReturn(1L);
        job = new VariantJob();
        job.setChangePlan(new ChangePlan("Variant Title", "Statement", List.of("change"), List.of("invariant")));
        request = new VariantGenerationRequestDTO(null, null, null, null, null);
    }

    @Test
    void shouldDeleteTheImportedExerciseWhenPostImportProcessingFails() {
        doThrow(new RuntimeException("test-case id remapping blew up")).when(programmingExerciseTaskService).updateTestIds(any(), any());

        assertThatThrownBy(() -> adapters.provision(source, request, job)).isInstanceOf(RuntimeException.class).hasMessageContaining("Importing the variant clone failed");

        verify(exerciseDeletionService).delete(99L, true);
    }

    /**
     * The import itself persists the exercise row before it copies the repositories, sets up the build plans and
     * schedules its operations. A throw in one of those steps never reaches the post-import handler, so the outer
     * path has to delete the row the import already assigned an id to.
     */
    @Test
    void shouldDeleteTheExerciseWhenTheImportThrowsAfterAssigningItsId() throws Exception {
        when(programmingExerciseImportService.importProgrammingExercise(any(), any(), anyBoolean(), anyBoolean(), anyBoolean())).thenAnswer(invocation -> {
            invocation.getArgument(1, ProgrammingExercise.class).setId(99L);
            throw new RuntimeException("copying the repositories blew up");
        });

        assertThatThrownBy(() -> adapters.provision(source, request, job)).isInstanceOf(RuntimeException.class).hasMessageContaining("Importing the variant clone failed");

        verify(exerciseDeletionService).delete(99L, true);
    }

    /** Nothing was persisted yet, so there is nothing to delete — and never the source. */
    @Test
    void shouldNotDeleteAnythingWhenTheImportThrowsBeforeAssigningAnId() throws Exception {
        when(programmingExerciseImportService.importProgrammingExercise(any(), any(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenThrow(new RuntimeException("the project key already exists"));

        assertThatThrownBy(() -> adapters.provision(source, request, job)).isInstanceOf(RuntimeException.class).hasMessageContaining("Importing the variant clone failed");

        verify(exerciseDeletionService, never()).delete(anyLong(), anyBoolean());
    }

    /** When the cleanup itself fails the clone survives, so its id must reach the pipeline's id-preserving path. */
    @Test
    void shouldReportTheSurvivingCloneWhenTheCleanupDeletionThrows() {
        doThrow(new RuntimeException("test-case id remapping blew up")).when(programmingExerciseTaskService).updateTestIds(any(), any());
        doThrow(new RuntimeException("deletion blew up")).when(exerciseDeletionService).delete(99L, true);

        assertThatThrownBy(() -> adapters.provision(source, request, job)).isInstanceOf(LeftoverVariantExerciseException.class)
                .hasMessageContaining("Importing the variant clone failed").extracting(exception -> ((LeftoverVariantExerciseException) exception).getExerciseId()).isEqualTo(99L);
    }

    @Test
    void shouldNotDeleteAnythingWhenProvisioningSucceeds() {
        when(programmingExerciseRepository.save(imported)).thenReturn(imported);
        when(programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(99L)).thenReturn(imported);

        adapters.provision(source, request, job);

        verify(exerciseDeletionService, never()).delete(anyLong(), anyBoolean());
    }
}
