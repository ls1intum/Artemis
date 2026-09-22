package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@WithMockUser(username = "hypvariantdraftinstructor1", roles = "INSTRUCTOR")
class GenerationVariantDraftServicePersistenceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private GenerationVariantDraftService drafts;

    @Autowired
    private ProgrammingExerciseUtilService programmingExercises;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository configurations;

    @Value("${artemis.version-control.default-branch}")
    private String defaultBranch;

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
    }

    @Test
    void realImportStaysInvisibleUntilReservationReturnsAndTransactionCommits() {
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
        assertThat(destination.getReleaseDate()).isAfter(java.time.ZonedDateTime.now());
        assertThat(configurations.getProgrammingExerciseBuildConfigElseThrow(result).getBranch()).isEqualTo(defaultBranch);
        assertThat(configurations.getProgrammingExerciseBuildConfigElseThrow(source.getId()).getBranch()).isEqualTo("teaching");
        assertThat(programmingExerciseRepository.findByIdElseThrow(source.getId()).getTitle()).isEqualTo(source.getTitle());
    }

    @Test
    void activityStoreFailureAbortsAdmissionBeforeTheDraftCommits() {
        var destinationId = new AtomicLong();
        var rejected = new java.util.concurrent.atomic.AtomicReference<String>();
        var storeFailure = new IllegalStateException("activity store unavailable");
        var jobs = new de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService(
                new de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService(), event -> {
                    if (event instanceof de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmittedEvent admitted) {
                        rejected.set(admitted.run().jobId());
                        throw storeFailure;
                    }
                    if (event instanceof de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent) {
                        throw new AssertionError("An untracked variant must never dispatch");
                    }
                }, org.mockito.Mockito.mock(de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService.class), null, java.time.Duration.ofMinutes(35),
                java.time.Duration.ofMinutes(30), Runnable::run);
        jobs.init();
        var user = userTestRepository.findOneByLogin("hypvariantdraftinstructor1").orElseThrow();

        assertThatThrownBy(() -> drafts.prepare(source.getId(), request, destination -> {
            destinationId.set(destination.getId());
            return jobs.prepareVariantJob(user, destination, "Variant", null, null, null,
                    new de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation(source.getId(), "source", request));
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
