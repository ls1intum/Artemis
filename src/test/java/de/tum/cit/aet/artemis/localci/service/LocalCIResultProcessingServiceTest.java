package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildResult;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.service.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalQueue;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.localci.domain.BuildJob;
import de.tum.cit.aet.artemis.localci.dto.BuildJobStatisticsDTO;
import de.tum.cit.aet.artemis.localci.test_repository.BuildJobTestRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildStatistics;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.exception.BuildTriggerWebsocketError;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildStatisticsRepository;
import de.tum.cit.aet.artemis.programming.service.BuildLogEntryService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseDeletionService.ResultProcessingPermit;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseGradingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingSubmissionMessagingService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingTriggerService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Unit tests for turning what a build agent reports back into a result and a row in the build history.
 * <p>
 * This is the last step of a build, and it is the only one that ever writes the outcome down. Whatever it decides is
 * what the student sees: a build that timed out has to be recorded as a timeout rather than a failure, a build whose
 * participation was deleted while it ran must not take the processing thread down with it, and a build that produced no
 * result at all has to end in an error message, because the client waits for a result that will otherwise never arrive.
 */
@ExtendWith(MockitoExtension.class)
class LocalCIResultProcessingServiceTest {

    private static final long PARTICIPATION_ID = 10L;

    private static final long EXERCISE_ID = 3L;

    private static final java.util.UUID LISTENER_ID = java.util.UUID.randomUUID();

    @Mock
    private ProgrammingExerciseGradingService programmingExerciseGradingService;

    @Mock
    private ProgrammingMessagingService programmingMessagingService;

    @Mock
    private BuildJobTestRepository buildJobRepository;

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private ProgrammingTriggerService programmingTriggerService;

    @Mock
    private BuildLogEntryService buildLogEntryService;

    @Mock
    private ProgrammingExerciseBuildStatisticsRepository programmingExerciseBuildStatisticsRepository;

    @Mock
    private DistributedDataAccessService distributedDataAccessService;

    @Mock
    private ProgrammingSubmissionMessagingService programmingSubmissionMessagingService;

    @Mock
    private LocalCIQueueWebsocketService localCIQueueWebsocketService;

    @Mock
    private ProgrammingExerciseDeletionService programmingExerciseDeletionService;

    @Mock
    private DistributedQueue<ResultQueueItem> resultQueue;

    @Mock
    private DistributedQueue<ResultQueueItem> deferredResultQueue;

    @Mock
    private ResultProcessingPermit resultProcessingPermit;

    @Mock
    private ResultProcessingPermit followUpProcessingPermit;

    @Mock
    private BuildResult buildResult;

    private LocalCIResultProcessingService resultProcessingService;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    @BeforeEach
    void setUp() throws InterruptedException {
        resultProcessingService = new LocalCIResultProcessingService(programmingExerciseGradingService, programmingMessagingService, buildJobRepository,
                programmingExerciseRepository, participationRepository, programmingTriggerService, buildLogEntryService, programmingExerciseBuildStatisticsRepository,
                distributedDataAccessService, programmingSubmissionMessagingService, Optional.of(localCIQueueWebsocketService), programmingExerciseDeletionService);
        // The production executor hands the work to a pool thread, which would make every assertion below a race. This one
        // runs the same task on the calling thread, so the test observes the finished work rather than polling for it.
        ReflectionTestUtils.setField(resultProcessingService, "resultProcessingExecutor", inlineExecutor());
        // init() rebuilds the executor from this property, which a plain unit test does not read; a size of zero would make
        // the pool refuse to be created at all.
        ReflectionTestUtils.setField(resultProcessingService, "concurrentResultProcessingSize", 1);
        exercise = new ProgrammingExercise();
        exercise.setId(EXERCISE_ID);
        participation = new ProgrammingExerciseStudentParticipation();
        participation.setId(PARTICIPATION_ID);
        participation.setProgrammingExercise(exercise);
        lenient().when(distributedDataAccessService.getDistributedBuildResultQueue()).thenReturn(resultQueue);
        lenient().when(programmingExerciseDeletionService.tryAdmitResultProcessing(EXERCISE_ID)).thenReturn(Optional.of(resultProcessingPermit));
        lenient().when(programmingExerciseDeletionService.awaitResultProcessing(EXERCISE_ID)).thenReturn(followUpProcessingPermit);
        lenient().when(programmingExerciseRepository.existsById(EXERCISE_ID)).thenReturn(true);
        lenient().when(distributedDataAccessService.getDistributedDeferredBuildResultQueue()).thenReturn(deferredResultQueue);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static ThreadPoolExecutor inlineExecutor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {

            @Override
            public void execute(Runnable command) {
                command.run();
            }
        };
    }

    private static BuildJobQueueItem buildJob(RepositoryType repositoryType, RepositoryType triggeredByPushTo) {
        return buildJob(repositoryType, triggeredByPushTo, EXERCISE_ID, PARTICIPATION_ID, "job-1");
    }

    private static BuildJobQueueItem buildJob(RepositoryType repositoryType, RepositoryType triggeredByPushTo, long exerciseId, long participationId, String id) {
        return new BuildJobQueueItem(id, id, new BuildAgentDTO("agent", "127.0.0.1:5701", "agent"), participationId, 1L, exerciseId, 0, 1, BuildStatus.BUILDING,
                new RepositoryInfo("repo", repositoryType, triggeredByPushTo, "assignment", "tests", "solution", new String[0], new String[0]),
                new JobTimingInfo(ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now().minusMinutes(1), ZonedDateTime.now(), null, 60),
                new BuildConfig(null, "ghcr.io/example/image:1", "commit", "commit", "test-commit", "main", null, null, false, false, List.of(), 0, null, null, null, null), null,
                null);
    }

    private void withQueuedResult(ResultQueueItem item) {
        when(resultQueue.poll()).thenReturn(item);
    }

    private void withParticipation() {
        when(participationRepository.findWithProgrammingExerciseById(PARTICIPATION_ID)).thenReturn(Optional.of(participation));
    }

    private void withSavedBuildJob() {
        lenient().when(buildJobRepository.findByBuildJobId(anyString())).thenReturn(Optional.empty());
        lenient().when(buildJobRepository.save(any(BuildJob.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private BuildStatus capturedBuildStatus() {
        ArgumentCaptor<BuildJob> saved = ArgumentCaptor.captor();
        verify(buildJobRepository).save(saved.capture());
        return saved.getValue().getBuildStatus();
    }

    // --- the average build duration behind the queue estimate ------------------------------------------------------

    /**
     * A successful build of a participation, which is the only path that updates the build duration statistics.
     */
    private void afterASuccessfulBuild() {
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();
    }

    @Test
    void aSuccessfulBuildRecordsTheAverageDurationOfAnExerciseThatHasNoneYet() {
        // The queue estimate a waiting student sees is computed from this average, so the first build has to establish it.
        afterASuccessfulBuild();
        when(buildJobRepository.findBuildJobStatisticsByExerciseId(EXERCISE_ID)).thenReturn(new BuildJobStatisticsDTO(42.4, 5L, EXERCISE_ID));
        when(programmingExerciseBuildStatisticsRepository.findByExerciseId(EXERCISE_ID)).thenReturn(Optional.empty());

        resultProcessingService.processResultAsync();

        ArgumentCaptor<ProgrammingExerciseBuildStatistics> saved = ArgumentCaptor.captor();
        verify(programmingExerciseBuildStatisticsRepository, org.mockito.Mockito.timeout(2000)).save(saved.capture());
        assertThat(saved.getValue().getBuildDurationSeconds()).as("the average is rounded to whole seconds").isEqualTo(42L);
        assertThat(saved.getValue().getBuildCountWhenUpdated()).isEqualTo(5L);
    }

    @Test
    void aSuccessfulBuildUpdatesAnAverageThatHasChanged() {
        afterASuccessfulBuild();
        var existing = new ProgrammingExerciseBuildStatistics(EXERCISE_ID, 30L, 5L);
        when(buildJobRepository.findBuildJobStatisticsByExerciseId(EXERCISE_ID)).thenReturn(new BuildJobStatisticsDTO(60.0, 6L, EXERCISE_ID));
        when(programmingExerciseBuildStatisticsRepository.findByExerciseId(EXERCISE_ID)).thenReturn(Optional.of(existing));

        resultProcessingService.processResultAsync();

        verify(programmingExerciseBuildStatisticsRepository, org.mockito.Mockito.timeout(2000)).updateStatistics(60L, 6L, EXERCISE_ID);
    }

    @Test
    void aSuccessfulBuildThatChangesNothingDoesNotWriteTheStatisticsAgain() {
        // Every build of every student would otherwise write this row, which is a write per build for no new information.
        afterASuccessfulBuild();
        var existing = new ProgrammingExerciseBuildStatistics(EXERCISE_ID, 60L, 5L);
        when(buildJobRepository.findBuildJobStatisticsByExerciseId(EXERCISE_ID)).thenReturn(new BuildJobStatisticsDTO(60.0, 6L, EXERCISE_ID));
        when(programmingExerciseBuildStatisticsRepository.findByExerciseId(EXERCISE_ID)).thenReturn(Optional.of(existing));

        resultProcessingService.processResultAsync();

        verify(programmingExerciseBuildStatisticsRepository, org.mockito.Mockito.after(500).never()).updateStatistics(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void anExerciseNobodyHasBuiltYetGetsNoStatisticsRow() {
        afterASuccessfulBuild();
        when(buildJobRepository.findBuildJobStatisticsByExerciseId(EXERCISE_ID)).thenReturn(new BuildJobStatisticsDTO(0.0, 0L, EXERCISE_ID));

        resultProcessingService.processResultAsync();

        verify(programmingExerciseBuildStatisticsRepository, org.mockito.Mockito.after(500).never()).save(any());
    }

    @Test
    void aFailureWhileUpdatingTheStatisticsDoesNotAffectTheResultThatWasJustSaved() {
        // The statistics are a convenience; losing them must not cost the student the result of their build.
        afterASuccessfulBuild();
        when(buildJobRepository.findBuildJobStatisticsByExerciseId(EXERCISE_ID)).thenThrow(new IllegalStateException("the database is gone"));

        resultProcessingService.processResultAsync();

        // The statistics update runs on its own thread, so the failure has to be observed before the build job is asserted on.
        verify(buildJobRepository, org.mockito.Mockito.timeout(2000)).findBuildJobStatisticsByExerciseId(EXERCISE_ID);
        verify(buildJobRepository).save(any(BuildJob.class));
    }

    @Test
    void aParticipationThatArrivesWithoutItsExerciseHasItLoadedFromTheDatabase() {
        // The grading needs the exercise and its build config; without them the result could not be scored at all.
        var withoutExercise = new ProgrammingExerciseStudentParticipation();
        withoutExercise.setId(PARTICIPATION_ID);
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        when(participationRepository.findWithProgrammingExerciseById(PARTICIPATION_ID)).thenReturn(Optional.of(withoutExercise));
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(withoutExercise)).thenReturn(exercise);
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        assertThat(withoutExercise.getProgrammingExercise()).isSameAs(exercise);
        verify(programmingExerciseGradingService).processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean());
    }

    // --- lifecycle and health --------------------------------------------------------------------------------------

    @Test
    void init_listensOnTheResultQueueSoResultsAreProcessedWhenTheyArrive() {
        // Without the listener nothing processes a result until the polling schedule fires, which delays every build result.
        when(resultQueue.addItemListener(any())).thenReturn(LISTENER_ID);

        resultProcessingService.init();

        verify(resultQueue).addItemListener(any());
    }

    @Test
    void removeListener_stopsListeningAndShutsTheExecutorDown() {
        when(resultQueue.addItemListener(any())).thenReturn(LISTENER_ID);
        resultProcessingService.init();
        when(distributedDataAccessService.isInstanceRunning()).thenReturn(true);

        resultProcessingService.removeListener();

        verify(resultQueue).removeListener(LISTENER_ID);
    }

    @Test
    void removeListener_whenTheClusterIsAlreadyGone_doesNotTryToDeregister() {
        // On shutdown the distributed instance can be down first; deregistering then would throw during shutdown.
        when(distributedDataAccessService.isInstanceRunning()).thenReturn(false);

        resultProcessingService.removeListener();

        verify(resultQueue, never()).removeListener(any(java.util.UUID.class));
    }

    @Test
    void logResultProcessorHealth_reportsTheQueueWithoutFailing() {
        // The health log is what surfaces a stuck processor, so it must survive being called when nothing has been processed.
        ReflectionTestUtils.setField(resultProcessingService, "resultProcessingExecutor", inlineExecutor());
        when(distributedDataAccessService.getResultQueueSize()).thenReturn(3);

        org.assertj.core.api.Assertions.assertThatCode(() -> resultProcessingService.logResultProcessorHealth()).doesNotThrowAnyException();
    }

    @Test
    void theQueueListenerProcessesAResultAsSoonAsItIsAdded() {
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();

        resultProcessingService.new ResultQueueListener().itemAdded(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));

        verify(buildJobRepository).save(any(BuildJob.class));
    }

    @Test
    void theQueueListenerIgnoresARemovedItem() {
        org.assertj.core.api.Assertions.assertThatCode(() -> resultProcessingService.new ResultQueueListener()
                .itemRemoved(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null))).doesNotThrowAnyException();
    }

    @Test
    void processResultAsync_whenTheExecutorIsSaturated_dropsTheAttemptInsteadOfFailing() {
        // The result stays in the queue and the next poll picks it up, so a rejected task is not a lost result.
        ThreadPoolExecutor saturated = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()) {

            @Override
            public void execute(Runnable command) {
                throw new java.util.concurrent.RejectedExecutionException("queue is full");
            }
        };
        ReflectionTestUtils.setField(resultProcessingService, "resultProcessingExecutor", saturated);

        org.assertj.core.api.Assertions.assertThatCode(() -> resultProcessingService.processResultAsync()).doesNotThrowAnyException();
    }

    @Test
    void aFinishedBuildIsAlsoPushedToTheBuildQueueView() {
        // The instructor's build queue page is driven by this notification; without it a finished build stays listed as running.
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();
        var finished = new BuildJob();
        finished.setBuildJobId("job-1");
        finished.setCourseId(1L);
        finished.setParticipationId(PARTICIPATION_ID);
        finished.setExerciseId(EXERCISE_ID);
        finished.setName("job-1");
        finished.setBuildStatus(BuildStatus.SUCCESSFUL);
        finished.setBuildSubmissionDate(ZonedDateTime.now().minusMinutes(1));
        finished.setBuildStartDate(ZonedDateTime.now().minusMinutes(1));
        finished.setBuildCompletionDate(ZonedDateTime.now());
        when(buildJobRepository.findWithDataByBuildJobId("job-1")).thenReturn(Optional.of(finished));

        resultProcessingService.processResultAsync();

        verify(localCIQueueWebsocketService).sendFinishedBuildJobOverWebsocket(any());
    }

    @Test
    void anExceptionWhilePushingTheFinishedBuildDoesNotLoseTheBuildJob() {
        // The row in the build history matters more than the live view, so the notification must not take the save down.
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();
        when(buildJobRepository.findWithDataByBuildJobId("job-1")).thenThrow(new IllegalStateException("the websocket broker is gone"));

        resultProcessingService.processResultAsync();

        verify(buildJobRepository).save(any(BuildJob.class));
    }

    @Test
    void anEmptyResultQueueIsNotAnError() {
        when(resultQueue.poll()).thenReturn(null);

        resultProcessingService.processResultAsync();

        verifyNoInteractions(participationRepository, buildJobRepository, programmingExerciseGradingService);
    }

    @Test
    void twoResultsOfTheSameExerciseCanBeGradedConcurrently() throws Exception {
        var lifecycle = new ProgrammingExerciseDeletionService(null, null, null, Optional.empty(), null, null, null, Optional.empty(), new LocalDataProviderService());
        var service = new LocalCIResultProcessingService(programmingExerciseGradingService, programmingMessagingService, buildJobRepository, programmingExerciseRepository,
                participationRepository, programmingTriggerService, buildLogEntryService, programmingExerciseBuildStatisticsRepository, distributedDataAccessService,
                programmingSubmissionMessagingService, Optional.of(localCIQueueWebsocketService), lifecycle);
        var executor = new ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        ReflectionTestUtils.setField(service, "resultProcessingExecutor", executor);
        withParticipation();
        withSavedBuildJob();
        when(resultQueue.poll()).thenReturn(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null),
                new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER, EXERCISE_ID, PARTICIPATION_ID, "job-2"), List.of(), null));
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
            entered.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return new Result();
        }).when(programmingExerciseGradingService).processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean());
        try {
            service.processResultAsync();
            service.processResultAsync();
            assertThat(entered.await(5, TimeUnit.SECONDS)).as("both same-exercise results enter grading before either finishes").isTrue();
            verify(deferredResultQueue, never()).add(any());
        }
        finally {
            release.countDown();
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void aBusyExerciseDoesNotConsumeAResultWorkerOrDiscardTheResult() {
        ResultQueueItem item = new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null);
        withQueuedResult(item);
        when(programmingExerciseDeletionService.tryAdmitResultProcessing(EXERCISE_ID)).thenReturn(Optional.empty());

        resultProcessingService.processResultAsync();

        verifyNoInteractions(participationRepository, buildJobRepository, programmingExerciseGradingService);
        verify(deferredResultQueue).add(item);
        verifyNoInteractions(resultProcessingPermit);
    }

    @Test
    void deferredResultIsGradedWhenTheExerciseRemainsAfterDeletionFails() throws InterruptedException {
        var permitClosures = new CountDownLatch(2);
        Answer<Void> recordPermitClosure = invocation -> {
            permitClosures.countDown();
            return null;
        };
        doAnswer(recordPermitClosure).when(resultProcessingPermit).close();
        doAnswer(recordPermitClosure).when(followUpProcessingPermit).close();
        ResultQueueItem item = new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null);
        when(deferredResultQueue.poll()).thenReturn(item);
        withParticipation();
        withSavedBuildJob();
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean())).thenReturn(new Result());

        resultProcessingService.processDeferredResultAsync();

        assertThat(permitClosures.await(5, TimeUnit.SECONDS)).as("result processing and its asynchronous build-statistics follow-up both release their permits").isTrue();
        verify(programmingExerciseGradingService).processNewProgrammingExerciseResult(participation, buildResult, false);
        verify(resultProcessingPermit).close();
        verify(followUpProcessingPermit).close();
        verify(deferredResultQueue, never()).add(any());
    }

    @Test
    void oneWorkerProcessesAnotherExerciseWhileDeletionHoldsTheFirstLock() {
        var ordinary = new LocalQueue<ResultQueueItem>(new LinkedBlockingQueue<>(), "ordinary-results");
        var deferred = new LocalQueue<ResultQueueItem>(new LinkedBlockingQueue<>(), "deferred-results");
        when(distributedDataAccessService.getDistributedBuildResultQueue()).thenReturn(ordinary);
        when(distributedDataAccessService.getDistributedDeferredBuildResultQueue()).thenReturn(deferred);
        var deleting = new java.util.concurrent.atomic.AtomicBoolean(true);
        when(programmingExerciseDeletionService.tryAdmitResultProcessing(EXERCISE_ID))
                .thenAnswer(invocation -> deleting.get() ? Optional.empty() : Optional.of(resultProcessingPermit));
        when(programmingExerciseDeletionService.tryAdmitResultProcessing(4L)).thenReturn(Optional.of(resultProcessingPermit));
        withParticipation();
        var otherExercise = new ProgrammingExercise();
        otherExercise.setId(4L);
        var otherParticipation = new ProgrammingExerciseStudentParticipation();
        otherParticipation.setId(11L);
        otherParticipation.setProgrammingExercise(otherExercise);
        when(participationRepository.findWithProgrammingExerciseById(11L)).thenReturn(Optional.of(otherParticipation));
        withSavedBuildJob();
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean())).thenReturn(new Result());
        ResultQueueItem busy = new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null);
        resultProcessingService.init();
        try {
            ordinary.add(busy);
            ordinary.add(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER, 4L, 11L, "job-2"), List.of(), null));
            verify(programmingExerciseGradingService, timeout(5000)).processNewProgrammingExerciseResult(otherParticipation, buildResult, false);
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(deferred.getAll()).containsExactly(busy));
            verify(participationRepository, never()).findWithProgrammingExerciseById(PARTICIPATION_ID);
            var scheduler = new LocalCIResultListenerService(distributedDataAccessService, resultProcessingService);
            scheduler.processQueuedResults();
            ThreadPoolExecutor executor = (ThreadPoolExecutor) ReflectionTestUtils.getField(resultProcessingService, "resultProcessingExecutor");
            await().atMost(Duration.ofSeconds(5)).until(() -> executor.getCompletedTaskCount() >= 3);
            assertThat(deferred.getAll()).containsExactly(busy);
            deleting.set(false);
            scheduler.processQueuedResults();
            verify(programmingExerciseGradingService, timeout(5000)).processNewProgrammingExerciseResult(participation, buildResult, false);
            verify(programmingExerciseGradingService, times(2)).processNewProgrammingExerciseResult(any(), any(), anyBoolean());
        }
        finally {
            resultProcessingService.removeListener();
        }
    }

    @Test
    void aQueueItemWithoutABuildResultIsSkipped() {
        // Without a build result there is nothing to grade and nothing to record, so the item is dropped rather than saved as failed.
        withQueuedResult(new ResultQueueItem(null, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));

        resultProcessingService.processResultAsync();

        verifyNoInteractions(buildJobRepository, programmingExerciseGradingService);
    }

    @Test
    void aSuccessfulBuildIsGradedRecordedAndPushedToTheStudent() {
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();
        Result result = new Result();
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean())).thenReturn(result);

        resultProcessingService.processResultAsync();

        assertThat(capturedBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        verify(programmingMessagingService).notifyUserAboutNewResult(result, participation);
    }

    @Test
    void aBuildThatWasCancelledIsRecordedAsCancelled() {
        // The build history is what an instructor reads when a student says their build never ran, so a cancelled build must
        // not be indistinguishable from one that failed on its own.
        var job = buildJob(RepositoryType.USER, RepositoryType.USER);
        var cancellation = new RuntimeException("Build job with id job-1 was cancelled.", new CancellationException());
        withQueuedResult(new ResultQueueItem(buildResult, job, List.of(), cancellation));
        withParticipation();
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        assertThat(capturedBuildStatus()).isEqualTo(BuildStatus.CANCELLED);
    }

    @Test
    void aBuildThatRanOutOfTimeIsRecordedAsATimeout() {
        var timeout = new RuntimeException("took too long", new TimeoutException());
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), timeout));
        withParticipation();
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        assertThat(capturedBuildStatus()).isEqualTo(BuildStatus.TIMEOUT);
    }

    @Test
    void aBuildThatFailedForAnyOtherReasonIsRecordedAsFailed() {
        var failure = new RuntimeException("the container died", new IllegalStateException());
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), failure));
        withParticipation();
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        assertThat(capturedBuildStatus()).isEqualTo(BuildStatus.FAILED);
    }

    @Test
    void aBuildWhoseResultCouldNotBeProducedEndsInAnErrorForTheStudent() {
        // Nothing else will arrive for this submission, so without the error the client waits for a result forever.
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean())).thenReturn(null);

        resultProcessingService.processResultAsync();

        verify(programmingSubmissionMessagingService).notifyUserAboutSubmissionError(any(Participation.class), any(BuildTriggerWebsocketError.class));
        verify(programmingMessagingService, never()).notifyUserAboutNewResult(any(), any());
    }

    @Test
    void aBuildWhoseParticipationWasDeletedWhileItRanIsStillRecorded() {
        // The build still happened, and dropping it would leave a job in the history that never finished.
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        when(participationRepository.findWithProgrammingExerciseById(PARTICIPATION_ID)).thenReturn(Optional.empty());
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        assertThat(capturedBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        verify(programmingMessagingService, never()).notifyUserAboutNewResult(any(), any());
        verify(programmingSubmissionMessagingService, never()).notifyUserAboutSubmissionError(any(Participation.class), any());
    }

    @Test
    void anAdmittedResultFinishesWhenDeletionStartsBeforeGrading() {
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();
        // The marker can appear after admission. Deletion must drain this writer instead of discarding its result.
        lenient().when(programmingExerciseDeletionService.isBeingDeleted(EXERCISE_ID)).thenReturn(true);
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean())).thenReturn(new Result());

        resultProcessingService.processResultAsync();

        verify(programmingExerciseGradingService).processNewProgrammingExerciseResult(participation, buildResult, false);
        verify(programmingMessagingService).notifyUserAboutNewResult(any(), any());
    }

    @Test
    void aSolutionBuildDuringDeletionIsDeferredWithoutStartingFollowUps() {
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.SOLUTION, RepositoryType.TESTS), List.of(), null));
        when(programmingExerciseDeletionService.tryAdmitResultProcessing(EXERCISE_ID)).thenReturn(Optional.empty());

        resultProcessingService.processResultAsync();

        verifyNoInteractions(programmingTriggerService, programmingExerciseGradingService, buildJobRepository);
        verify(deferredResultQueue).add(any());
    }

    @Test
    void aResultThatCannotBeWrittenBecauseTheParticipationWasDeletedMeanwhileIsDropped() {
        // The deletion removed the participation between loading it and saving the result, so the foreign key rejects the insert. This must not take the thread down.
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean()))
                .thenThrow(new DataIntegrityViolationException("fk_result_exercise_id"));
        when(participationRepository.existsById(PARTICIPATION_ID)).thenReturn(false);

        resultProcessingService.processResultAsync();

        assertThat(capturedBuildStatus()).isEqualTo(BuildStatus.SUCCESSFUL);
        verify(programmingMessagingService, never()).notifyUserAboutNewResult(any(), any());
        verify(programmingSubmissionMessagingService, never()).notifyUserAboutSubmissionError(any(Participation.class), any());
    }

    @Test
    void deletedExerciseDoesNotGetStatisticsFromAsyncFollowUp() throws InterruptedException {
        afterASuccessfulBuild();
        CountDownLatch existenceChecked = new CountDownLatch(1);
        when(programmingExerciseRepository.existsById(EXERCISE_ID)).thenAnswer(invocation -> {
            existenceChecked.countDown();
            return false;
        });

        resultProcessingService.processResultAsync();

        assertThat(existenceChecked.await(5, TimeUnit.SECONDS)).isTrue();
        verifyNoInteractions(programmingExerciseBuildStatisticsRepository);
    }

    @Test
    void deletedExerciseDoesNotGetAnotherTemplateBuildFromAsyncFollowUp() throws InterruptedException {
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.SOLUTION, RepositoryType.TESTS), List.of(), null));
        withSavedBuildJob();
        CountDownLatch existenceChecked = new CountDownLatch(1);
        when(programmingExerciseRepository.existsById(EXERCISE_ID)).thenAnswer(invocation -> {
            existenceChecked.countDown();
            return false;
        });

        resultProcessingService.processResultAsync();

        assertThat(existenceChecked.await(5, TimeUnit.SECONDS)).isTrue();
        verifyNoInteractions(programmingTriggerService);
    }

    @Test
    void aConstraintViolationOfAnExistingParticipationIsNotHidden() {
        // Only the loss of the participation explains the violation. Any other one is a defect and must stay visible.
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        when(programmingExerciseGradingService.processNewProgrammingExerciseResult(any(), any(BuildResult.class), anyBoolean()))
                .thenThrow(new DataIntegrityViolationException("not null"));
        when(participationRepository.existsById(PARTICIPATION_ID)).thenReturn(true);
        withSavedBuildJob();

        assertThatThrownBy(() -> resultProcessingService.processResultAsync()).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theBuildLogsOfAFinishedBuildAreKept() {
        // The logs are the only way an instructor can see why a build failed, and they are not in the result.
        var logs = List.of(new BuildLogDTO(ZonedDateTime.now(), "compilation failed"));
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), logs, null));
        withParticipation();
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        verify(buildLogEntryService).saveBuildLogsToFile(logs, "job-1", exercise);
    }

    @Test
    void aSolutionBuildCausedByAPushToTheTestsRebuildsTheTemplate() throws Exception {
        // The template is expected to fail the new tests; without this rebuild its old result keeps claiming it passes them.
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.SOLUTION, RepositoryType.TESTS), List.of(), null));
        withParticipation();
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        verify(programmingTriggerService, org.mockito.Mockito.timeout(2000)).triggerTemplateBuildAndNotifyUser(EXERCISE_ID, "test-commit",
                de.tum.cit.aet.artemis.exercise.domain.SubmissionType.TEST, RepositoryType.TESTS);
    }

    @Test
    void aBuildOfAStudentRepositoryDoesNotRebuildTheTemplate() {
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        withSavedBuildJob();

        resultProcessingService.processResultAsync();

        verify(programmingTriggerService, never()).triggerTemplateBuildAndNotifyUser(org.mockito.ArgumentMatchers.anyLong(), anyString(), any(), any());
    }

    @Test
    void aFinishedBuildReplacesTheRowOfAnEarlierAttemptWithTheSameId() {
        // A retried job keeps its id, so saving it as a new row would leave the build history with two entries for one build.
        var existing = new BuildJob();
        existing.setId(55L);
        withQueuedResult(new ResultQueueItem(buildResult, buildJob(RepositoryType.USER, RepositoryType.USER), List.of(), null));
        withParticipation();
        when(buildJobRepository.findByBuildJobId("job-1")).thenReturn(Optional.of(existing));
        when(buildJobRepository.save(any(BuildJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        resultProcessingService.processResultAsync();

        ArgumentCaptor<BuildJob> saved = ArgumentCaptor.captor();
        verify(buildJobRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(55L);
    }
}
