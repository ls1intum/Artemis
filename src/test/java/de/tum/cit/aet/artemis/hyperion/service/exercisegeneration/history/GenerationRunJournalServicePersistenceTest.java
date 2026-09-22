package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseVersion;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseVersionTestRepository;
import de.tum.cit.aet.artemis.hyperion.domain.AuthoringRun;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

@WithMockUser(username = "hypjournalinstructor1", roles = "INSTRUCTOR")
class GenerationRunJournalServicePersistenceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    private GenerationRunStoreService runs;

    @Autowired
    private ExerciseVersionTestRepository versions;

    @Autowired
    private GenerationRunJournalService journal;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private JsonMapper mapper;

    private ProgrammingExercise exercise;

    private User user;

    private String jobId;

    @BeforeEach
    void setup() {
        jobId = UUID.randomUUID().toString();
        userUtilService.addUsers("hypjournal", 0, 0, 0, 1);
        user = userTestRepository.findOneByLogin("hypjournalinstructor1").orElseThrow();
        exercise = (ProgrammingExercise) programmingExerciseUtilService.addCourseWithOneProgrammingExercise().getExercises().iterator().next();
        journal.started(new GenerationStartedEvent(jobId, user, exercise, "brief", GenerationMode.ADAPT));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({ "false,true", "true,true", "false,false", "true,false" })
    void dispatchFailureRetainsItsKnownOutcomeInHistory(boolean prepared, boolean executorRejected) {
        var data = new de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService();
        var rejectedJob = new java.util.concurrent.atomic.AtomicReference<String>();
        org.springframework.context.ApplicationEventPublisher publisher = event -> {
            if (event instanceof GenerationStartedEvent started) {
                journal.started(started);
                rejectedJob.set(started.jobId());
                if (executorRejected) {
                    throw new org.springframework.core.task.TaskRejectedException("Queue full");
                }
                throw new IllegalStateException("Dispatch unavailable");
            }
            if (event instanceof de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationDispatchFailedEvent failed) {
                journal.dispatchFailed(failed);
            }
        };
        var jobs = new de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService(data, publisher,
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService.class), null, java.time.Duration.ofMinutes(35), java.time.Duration.ofMinutes(30),
                Runnable::run);
        jobs.init();
        if (prepared) {
            var event = jobs.prepareVariantJob(user, exercise, "Variant", null, null, null,
                    new de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation(exercise.getId(), "source", null));
            assertThat(jobs.dispatchPreparedJob(event)).isFalse();
        }
        else {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jobs.startJob(user, exercise, "Create", GenerationMode.GENERATE)).isInstanceOf(RuntimeException.class);
        }
        var failed = runs.findByJobId(rejectedJob.get()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AuthoringRun.Status.ERROR);
        assertThat(failed.getFinishedAt()).isNotNull();
        assertThat(jobs.hasActiveJob(exercise.getId())).isFalse();
        var authorization = org.mockito.Mockito.mock(de.tum.cit.aet.artemis.core.service.AuthorizationCheckService.class);
        org.mockito.Mockito.when(authorization.isAtLeastEditorForExercise(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(user))).thenReturn(true);
        var history = new GenerationHistoryService(runs, programmingExerciseRepository, authorization, jobs,
                org.mockito.Mockito.mock(de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence.ExerciseGenerationRevertService.class));
        assertThat(history.history(user, null).runs()).filteredOn(run -> run.jobId().equals(rejectedJob.get())).singleElement().satisfies(run -> {
            assertThat(run.status()).isEqualTo(AuthoringRun.Status.ERROR);
            assertThat(run.finishedAt()).isNotNull();
            assertThat(run.running()).isFalse();
        });
    }

    private long version() throws Exception {
        var version = new ExerciseVersion();
        version.setExerciseId(exercise.getId());
        version.setAuthorId(user.getId());
        version.setExerciseSnapshot(mapper.readValue("{\"id\":" + exercise.getId() + ",\"title\":\"A version\"}", ExerciseSnapshotDTO.class));
        return versions.saveAndFlush(version).getId();
    }

    @Test
    void retainsVersionLinksAndDoesNotLetALateFailureReplaceSuccess() throws Exception {
        long before = version();
        long after = version();
        assertThat(runs.linkBeforeVersion(jobId, before, "teaching", Instant.now())).isOne();
        assertThat(runs.linkAfterVersion(jobId, after)).isOne();
        assertThat(runs.complete(jobId, AuthoringRun.Status.SAVED, Instant.now(), true)).isOne();
        assertThat(runs.complete(jobId, AuthoringRun.Status.ERROR, Instant.now(), false)).isZero();
        var restored = runs.findByJobId(jobId).orElseThrow();
        assertThat(restored.getBeforeVersionId()).isEqualTo(before);
        assertThat(restored.getAfterVersionId()).isEqualTo(after);
        assertThat(restored.getRepositoryBranch()).isEqualTo("teaching");
        assertThat(restored.getStatus()).isEqualTo(AuthoringRun.Status.SAVED);
        assertThat(restored.getLiveExerciseChanged()).isTrue();
    }

    @Test
    void concurrentCallersCannotReplaceTheFirstBeforeVersion() throws Exception {
        long before = version();
        long competing = version();
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return runs.linkBeforeVersion(jobId, before, "teaching", Instant.now());
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return runs.linkBeforeVersion(jobId, competing, "teaching", Instant.now());
            });
            assertThat(first.get(20, TimeUnit.SECONDS) + second.get(20, TimeUnit.SECONDS)).isOne();
        }
        assertThat(runs.findByJobId(jobId).orElseThrow().getBeforeVersionId()).isIn(before, competing);
    }

    @Test
    void aMissingVersionCannotMakeAnUnfinishedMutationDisappearFromRecovery() throws Exception {
        long before = version();
        runs.linkBeforeVersion(jobId, before, "teaching", Instant.now());
        versions.deleteById(before);
        versions.flush();
        var latest = runs.findLatestMutation(exercise.getId());
        assertThat(latest).singleElement().satisfies(run -> {
            assertThat(run.getJobId()).isEqualTo(jobId);
            assertThat(run.getBeforeVersionId()).isEqualTo(before);
            assertThat(run.getMutationStartedAt()).isNotNull();
            assertThat(runs.linkBeforeVersion(jobId, version(), "other", Instant.now())).isZero();
        });
    }

    @Test
    void deletingTheOwnerCannotExposeAnOlderRecoveryCandidate() throws Exception {
        long before = version();
        runs.linkBeforeVersion(jobId, before, "teaching", Instant.now());
        // The existing exercise-version author FK restricts deletion until those versions are removed.
        versions.deleteById(before);
        versions.flush();
        userTestRepository.deleteById(user.getId());
        userTestRepository.flush();
        assertThat(runs.findLatestMutation(exercise.getId())).singleElement().satisfies(run -> {
            assertThat(run.getJobId()).isEqualTo(jobId);
            assertThat(run.getOwnerId()).isEqualTo(user.getId());
            assertThat(run.getMutationStartedAt()).isNotNull();
        });
    }

    @Test
    void restoreConsumptionChecksTheExactSavedVersion() throws Exception {
        long before = version();
        long after = version();
        runs.linkBeforeVersion(jobId, before, "teaching", Instant.now());
        runs.linkAfterVersion(jobId, after);
        assertThat(runs.markReverted(jobId, before, Instant.now())).isZero();
        assertThat(runs.markReverted(jobId, after, Instant.now())).isOne();
        assertThat(runs.markReverted(jobId, after, Instant.now())).isZero();
    }
}
