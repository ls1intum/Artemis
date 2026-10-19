package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.hyperion.config.HyperionAgentProperties;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.protocol.GenerationToolchain;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmissionService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionEffortProfileService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.HyperionGenerationBudgetService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationSeedService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

class GenerationCapabilityServiceTest {

    private final GenerationRequestService requests = mock();

    private final AuxiliaryRepositoryRepository auxiliaries = mock();

    private final GenerationJobService jobs = mock();

    private final GenerationWorkerRegistryService workers = mock();

    private final GenerationSeedService seeds = mock();

    private final GenerationCapabilityService service = new GenerationCapabilityService(requests, auxiliaries, jobs, workers, seeds);

    private final ProgrammingExercise exercise = new ProgrammingExercise();

    @BeforeEach
    void setup() {
        exercise.setId(1L);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        when(requests.isGenerationSupported(exercise)).thenReturn(true);
        when(auxiliaries.findByExerciseId(1L)).thenReturn(List.of());
        when(seeds.readTestHarness(exercise)).thenReturn(Map.of("build.gradle", "testImplementation 'de.tum.cit.ase:ares:2.2.1'\napply from: 'gradle/AresReservedPackages.gradle'",
                "gradle/AresReservedPackages.gradle", "de/tum/cit/ase/ares/** org/junit/**", "SecurityPolicy.yaml", "trustedPackages: []"));
    }

    @Test
    void unsupportedConfigurationIsHiddenAndDirectAdmissionFails() {
        when(requests.isGenerationSupported(exercise)).thenReturn(false);
        var result = service.describe(exercise);
        assertThat(result.supported()).isFalse();
        assertThat(result.capacityAvailable()).isFalse();
        verifyNoInteractions(workers);
        assertThat(result.canGenerate()).isFalse();
        assertThat(result.canAdapt()).isFalse();
        assertThat(result.canCreateVariant()).isFalse();
        assertThatThrownBy(() -> service.requireSupportedConfiguration(exercise)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void auxiliaryRepositoriesAreOutsideEveryProgrammingAction() {
        when(auxiliaries.findByExerciseId(1L)).thenReturn(List.of(new AuxiliaryRepository()));
        assertThat(service.describe(exercise).supported()).isFalse();
        assertThatThrownBy(() -> service.requireSupportedConfiguration(exercise)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void capacityDoesNotChangeSupportOrEraseTheAction() {
        when(jobs.hasActiveJob(1L)).thenReturn(true);
        var result = service.describe(exercise);
        assertThat(result.supported()).isTrue();
        assertThat(result.canGenerate()).isTrue();
        assertThat(result.canAdapt()).isTrue();
        assertThat(result.canCreateVariant()).isTrue();
        assertThat(result.busy()).isTrue();
        assertThat(result.capacityAvailable()).isFalse();
        assertThat(result.restriction()).isNull();
    }

    @Test
    void advertisesOnlyCapacityCompatibleWithTheExercise() {
        when(workers.hasAvailableGenerationSandboxSlot(GenerationToolchain.JAVA_GRADLE)).thenReturn(true);
        assertThat(service.describe(exercise).capacityAvailable()).isTrue();
        verify(workers).hasAvailableGenerationSandboxSlot(GenerationToolchain.JAVA_GRADLE);
    }

    @Test
    void releasedSourceMayBeClonedButNotModified() {
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exercise.setStudentParticipations(Set.of(new ProgrammingExerciseStudentParticipation()));
        var result = service.describe(exercise);
        assertThat(result.canGenerate()).isFalse();
        assertThat(result.canAdapt()).isFalse();
        assertThat(result.canCreateVariant()).isTrue();
        assertThat(result.restriction()).isEqualTo("exerciseAlreadyReleased");
        assertThatThrownBy(() -> service.requireMutable(exercise)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void datelessDraftWithParticipationCannotBeModified() {
        exercise.setStudentParticipations(Set.of(new ProgrammingExerciseStudentParticipation()));
        assertThat(service.describe(exercise).restriction()).isEqualTo("exerciseHasParticipations");
        assertThatThrownBy(() -> service.requireMutable(exercise)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void examActionsAreUnavailableEvenBeforeAssignment() {
        var exam = new Exam();
        exam.setStartDate(ZonedDateTime.now().plusDays(1));
        var group = new ExerciseGroup();
        group.setExam(exam);
        exercise.setExerciseGroup(group);
        var result = service.describe(exercise);
        assertThat(result.supported()).isFalse();
        assertThat(result.canGenerate()).isFalse();
        assertThat(result.canAdapt()).isFalse();
        assertThat(result.canCreateVariant()).isFalse();
        assertThat(result.capacityAvailable()).isFalse();
        assertThatThrownBy(() -> service.requireSupportedConfiguration(exercise)).isInstanceOf(BadRequestAlertException.class);
        assertThatThrownBy(() -> service.requireMutable(exercise)).isInstanceOf(BadRequestAlertException.class);
        verifyNoInteractions(workers);
    }

    @Test
    void legacyReleasedSourceIsUnavailableBeforeAnyAdmissionSideEffects() {
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        when(seeds.readTestHarness(exercise)).thenReturn(Map.of("build.gradle", "testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'"));
        var result = service.describe(exercise);
        assertThat(result.supported()).isFalse();
        assertThat(result.canCreateVariant()).isFalse();
        assertThat(result.restriction()).isEqualTo("generationHarnessUnsupported");
        assertThatThrownBy(() -> service.requireSupportedConfiguration(exercise)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Migrate the exercise harness manually");
        verifyNoInteractions(workers);
    }

    @Test
    void unreadableHarnessFailsClosedInCapabilitiesAndDirectAdmission() {
        when(seeds.readTestHarness(exercise)).thenThrow(new IllegalStateException("repository unavailable"));
        assertThat(service.describe(exercise).canCreateVariant()).isFalse();
        assertThat(service.describe(exercise).restriction()).isEqualTo("generationHarnessUnavailable");
        assertThatThrownBy(() -> service.requireSupportedConfiguration(exercise)).isInstanceOf(ServiceUnavailableAlertException.class)
                .hasMessageContaining("repository access is restored").hasMessageNotContaining("Migrate");
        verifyNoInteractions(workers);
    }

    @Test
    void variantAdmissionRejectsLegacyBeforeBudgetCapacityOrJobReservation() {
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        when(seeds.readTestHarness(exercise)).thenReturn(Map.of("build.gradle", "testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'"));
        var exercises = mock(ProgrammingExerciseTestRepository.class);
        when(exercises.findWithAllParticipationsById(1L)).thenReturn(Optional.of(exercise));
        var budget = mock(HyperionGenerationBudgetService.class);
        var profiles = new HyperionEffortProfileService(new HyperionAgentProperties(), List.of());
        var admission = new GenerationAdmissionService(exercises, service, jobs, requests, mock(), workers, budget, mock(), profiles);
        var request = new VariantGenerationRequestDTO(DifficultyLevel.EASY, null, null, null, null);
        assertThatThrownBy(() -> admission.reserveVariant(new User(), 1L, request)).isInstanceOf(BadRequestAlertException.class)
                .hasMessageContaining("Migrate the exercise harness manually");
        verifyNoInteractions(budget, workers, jobs);
    }

}
