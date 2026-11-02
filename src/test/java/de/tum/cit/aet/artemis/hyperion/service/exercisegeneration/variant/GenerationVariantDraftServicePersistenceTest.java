package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmittedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationCapabilityService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationRequestService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationSeedService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@WithMockUser(username = "hypvariantdraftinstructor1", roles = "USER")
class GenerationVariantDraftServicePersistenceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private GenerationVariantDraftService drafts;

    @Autowired
    private ProgrammingExerciseUtilService programmingExercises;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository configurations;

    @Value("${artemis.version-control.default-branch}")
    private String defaultBranch;

    @Autowired
    private TeamAssignmentConfigRepository teamConfigurations;

    @Autowired
    private PlagiarismDetectionConfigRepository plagiarismConfigurations;

    @Autowired
    private de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService imports;

    @Autowired
    private GenerationRequestService generationRequests;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaries;

    @Autowired
    private GenerationJobService generationJobs;

    private GenerationCapabilityService capabilities;

    @Autowired
    private tools.jackson.databind.json.JsonMapper mapper;

    private ProgrammingExercise source;

    private final VariantGenerationRequestDTO request = new VariantGenerationRequestDTO(DifficultyLevel.HARD, "Library", null, null, null);

    @BeforeEach
    void setup() {
        userUtilService.addUsers("hypvariantdraft", 0, 0, 0, 1);
        var course = programmingExercises.addCourseWithOneProgrammingExercise();
        userUtilService.addInstructorToCourse("hypvariantdraftinstructor1", course);
        source = (ProgrammingExercise) course.getExercises().iterator().next();
        source.setProjectType(ProjectType.PLAIN_GRADLE);
        source.setStaticCodeAnalysisEnabled(false);
        programmingExerciseRepository.save(source);
        var configuration = configurations.getProgrammingExerciseBuildConfigElseThrow(source.getId());
        configuration.setSequentialTestRuns(false);
        configuration.setBranch("teaching");
        configurations.save(configuration);
        // This fixture exercises real database imports without creating Git repositories.
        var seeds = org.mockito.Mockito.mock(GenerationSeedService.class);
        org.mockito.Mockito.when(seeds.readTestHarness(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of("build.gradle", "testImplementation 'de.tum.cit.ase:ares:2.2.1'\napply from: 'gradle/AresReservedPackages.gradle'",
                        "gradle/AresReservedPackages.gradle", "de/tum/cit/ase/ares/** org/junit/**", "SecurityPolicy.yaml", "trustedPackages: []"));
        capabilities = new GenerationCapabilityService(generationRequests, auxiliaries, generationJobs, workerRegistry, seeds);
        drafts = new GenerationVariantDraftService(programmingExerciseRepository, configurations, imports, capabilities, teamConfigurations, plagiarismConfigurations,
                gitServiceSpy, mapper, defaultBranch);
    }

    @Test
    void sourceBaselineSurvivesRealDraftImportAndRejectsLaterMetadataChanges() {
        var git = org.mockito.Mockito.mock(de.tum.cit.aet.artemis.localvc.service.GitService.class);
        org.mockito.Mockito.when(git.getLastCommitHash(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn("source-head");
        var optimisticDrafts = new GenerationVariantDraftService(programmingExerciseRepository, configurations, imports, capabilities, teamConfigurations, plagiarismConfigurations,
                git, mapper, defaultBranch);
        var baseline = optimisticDrafts.captureSource(source.getId());

        var destinationId = optimisticDrafts.prepare(source.getId(), request, ProgrammingExercise::getId);

        assertThat(destinationId).isNotEqualTo(source.getId());
        optimisticDrafts.verifySource(source.getId(), baseline);
        var edited = programmingExerciseRepository.findByIdElseThrow(source.getId());
        edited.setTitle("An intervening instructor edit");
        programmingExerciseRepository.saveAndFlush(edited);
        assertThatThrownBy(() -> optimisticDrafts.verifySource(source.getId(), baseline)).isInstanceOf(de.tum.cit.aet.artemis.core.exception.ConflictException.class);
    }

    @Test
    void changedSourceAtReservationRollsBackTheImportedDraft() {
        var git = org.mockito.Mockito.mock(de.tum.cit.aet.artemis.localvc.service.GitService.class);
        org.mockito.Mockito.when(git.getLastCommitHash(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn("source-head");
        var optimisticDrafts = new GenerationVariantDraftService(programmingExerciseRepository, configurations, imports, capabilities, teamConfigurations, plagiarismConfigurations,
                git, mapper, defaultBranch);
        var baseline = optimisticDrafts.captureSource(source.getId());
        var destinationId = new AtomicLong();
        org.mockito.Mockito.when(git.getLastCommitHash(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString())).thenReturn("changed-head");

        assertThatThrownBy(() -> optimisticDrafts.prepare(source.getId(), request, destination -> {
            destinationId.set(destination.getId());
            optimisticDrafts.verifySource(source.getId(), baseline);
            return destination.getId();
        })).isInstanceOf(de.tum.cit.aet.artemis.core.exception.ConflictException.class);

        assertThat(destinationId.get()).isPositive();
        assertThat(programmingExerciseRepository.existsById(destinationId.get())).isFalse();
        assertThat(configurations.findById(destinationId.get())).isEmpty();
    }

    @Test
    void realImportStaysInvisibleUntilReservationReturnsAndTransactionCommits() {
        source.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(source);
        var teamConfig = teamConfigurations.findByExerciseId(source.getId()).orElseThrow();
        teamConfig.setMinTeamSize(2);
        teamConfig.setMaxTeamSize(4);
        teamConfigurations.save(teamConfig);
        source.setProblemStatement("Solve the tasks.");
        var sourceTestCases = programmingExercises.addTestCasesToProgrammingExercise(source);
        source.setTestCases(new HashSet<>(sourceTestCases));
        programmingExercises.addTasksToProgrammingExercise(source);
        var result = drafts.prepare(source.getId(), request, destination -> {
            programmingExerciseRepository.flush();
            try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                assertThat(executor.submit(() -> programmingExerciseRepository.existsById(destination.getId())).get(20, TimeUnit.SECONDS)).isFalse();
            }
            catch (Exception exception) {
                throw new AssertionError("Could not read the uncommitted destination through a separate database connection", exception);
            }
            return destination.getId();
        });
        var destination = programmingExerciseRepository.findByIdElseThrow(result);
        assertThat(destination.getId()).isNotEqualTo(source.getId());
        assertThat(destination.getDifficulty()).isEqualTo(DifficultyLevel.HARD);
        var imported = programmingExerciseRepository.findForAuthoringImportById(result).orElseThrow();
        assertThat(imported.getTestCases()).hasSize(3).extracting(ProgrammingExerciseTestCase::getId)
                .doesNotContainAnyElementsOf(sourceTestCases.stream().map(ProgrammingExerciseTestCase::getId).toList());
        assertThat(imported.getTasks()).hasSize(3).flatExtracting(ProgrammingExerciseTask::getTestCases)
                .allSatisfy(testCase -> assertThat(testCase.getExercise().getId()).isEqualTo(result));
        var unchangedSource = programmingExerciseRepository.findForAuthoringImportById(source.getId()).orElseThrow();
        assertThat(unchangedSource.getTestCases()).hasSize(3);
        assertThat(unchangedSource.getTasks()).hasSize(3);
        var destinationTeamConfig = teamConfigurations.findByExerciseId(result).orElseThrow();
        assertThat(destinationTeamConfig.getMinTeamSize()).isEqualTo(2);
        assertThat(destinationTeamConfig.getMaxTeamSize()).isEqualTo(4);
        assertThat(teamConfigurations.findByExerciseId(source.getId()).orElseThrow().getMaxTeamSize()).isEqualTo(4);
        assertThat(destination.getReleaseDate()).isAfter(java.time.ZonedDateTime.now());
        assertThat(configurations.getProgrammingExerciseBuildConfigElseThrow(result).getBranch()).isEqualTo(defaultBranch);
        assertThat(configurations.getProgrammingExerciseBuildConfigElseThrow(source.getId()).getBranch()).isEqualTo("teaching");
        assertThat(programmingExerciseRepository.findByIdElseThrow(source.getId()).getTitle()).isEqualTo(source.getTitle());
    }

    @Test
    void variantPersistsCustomPlagiarismSettingsWithoutChangingTheSource() {
        var sourceConfig = plagiarismConfigurations.findByExerciseId(source.getId()).orElseThrow();
        sourceConfig.setContinuousPlagiarismControlEnabled(true);
        sourceConfig.setContinuousPlagiarismControlPostDueDateChecksEnabled(true);
        sourceConfig.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(13);
        sourceConfig.setSimilarityThreshold(73);
        sourceConfig.setMinimumScore(21);
        sourceConfig.setMinimumSize(37);
        plagiarismConfigurations.saveAndFlush(sourceConfig);
        Long sourceConfigId = sourceConfig.getId();

        Long destinationId = drafts.prepare(source.getId(), request, ProgrammingExercise::getId);

        var destinationConfig = plagiarismConfigurations.findByExerciseId(destinationId).orElseThrow();
        assertThat(destinationConfig.getId()).isNotEqualTo(sourceConfigId);
        assertThat(destinationConfig.getExerciseId()).isEqualTo(destinationId);
        assertThat(destinationConfig.isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(destinationConfig.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isTrue();
        assertThat(destinationConfig.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(13);
        assertThat(destinationConfig.getSimilarityThreshold()).isEqualTo(73);
        assertThat(destinationConfig.getMinimumScore()).isEqualTo(21);
        assertThat(destinationConfig.getMinimumSize()).isEqualTo(37);
        var unchangedSource = plagiarismConfigurations.findByExerciseId(source.getId()).orElseThrow();
        assertThat(unchangedSource.getId()).isEqualTo(sourceConfigId);
        assertThat(unchangedSource.getExerciseId()).isEqualTo(source.getId());
        assertThat(unchangedSource.isContinuousPlagiarismControlEnabled()).isTrue();
        assertThat(unchangedSource.isContinuousPlagiarismControlPostDueDateChecksEnabled()).isTrue();
        assertThat(unchangedSource.getContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod()).isEqualTo(13);
        assertThat(unchangedSource.getSimilarityThreshold()).isEqualTo(73);
        assertThat(unchangedSource.getMinimumScore()).isEqualTo(21);
        assertThat(unchangedSource.getMinimumSize()).isEqualTo(37);
    }

    @Test
    void activityStoreFailureAbortsAdmissionBeforeTheDraftCommits() {
        var destinationId = new AtomicLong();
        var rejected = new java.util.concurrent.atomic.AtomicReference<String>();
        var storeFailure = new IllegalStateException("activity store unavailable");
        var jobs = new GenerationJobService(new de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService(), event -> {
            if (event instanceof GenerationAdmittedEvent admitted) {
                rejected.set(admitted.run().jobId());
                throw storeFailure;
            }
            if (event instanceof GenerationStartedEvent) {
                throw new AssertionError("An untracked variant must never dispatch");
            }
        }, org.mockito.Mockito.mock(GenerationTokenUsageService.class), null, java.time.Duration.ofMinutes(35), java.time.Duration.ofMinutes(30), Runnable::run);
        jobs.init();
        var user = userTestRepository.findOneByLogin("hypvariantdraftinstructor1").orElseThrow();

        assertThatThrownBy(() -> drafts.prepare(source.getId(), request, destination -> {
            destinationId.set(destination.getId());
            return jobs.prepareVariantJob(user, destination, "Variant", null, null, null,
                    new GenerationVariantPreparation(source.getId(), org.mockito.Mockito.mock(GenerationVariantPreparation.SourceSnapshot.class), request));
        })).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class).hasRootCauseMessage("activity store unavailable");

        assertThat(rejected.get()).isNotBlank();
        assertThat(destinationId.get()).isPositive();
        assertThat(programmingExerciseRepository.existsById(destinationId.get())).isFalse();
        assertThat(configurations.findById(destinationId.get())).isEmpty();
        assertThat(jobs.hasActiveJob(destinationId.get())).isFalse();
    }

    @Test
    void reservationFailureRollsBackTheEntireImportedDatabaseGraph() {
        var destinationId = new AtomicLong();
        var rejection = new RuntimeException("reservation rejected");
        assertThatThrownBy(() -> drafts.prepare(source.getId(), request, destination -> {
            destinationId.set(destination.getId());
            programmingExerciseRepository.flush();
            throw rejection;
        })).isSameAs(rejection);

        assertThat(destinationId.get()).isPositive();
        assertThat(programmingExerciseRepository.existsById(destinationId.get())).isFalse();
        assertThat(configurations.findById(destinationId.get())).isEmpty();
    }
}
