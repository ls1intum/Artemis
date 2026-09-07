package de.tum.cit.aet.artemis.buildagent.service.runner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.config.KubernetesBuildRunnerProperties;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.BuildLogsMap;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.JobList;
import io.fabric8.kubernetes.client.GracePeriodConfigurable;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.VersionInfo;
import io.fabric8.kubernetes.client.dsl.BatchAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.CopyOrReadable;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.FilterWatchListDeletable;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.ScalableResource;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorChannelable;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorable;
import io.fabric8.kubernetes.client.dsl.V1BatchAPIGroupDSL;

class KubernetesBuildJobRunnerTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void executesBuildAndReleasesArchivesAndJob() throws Exception {
        KubernetesClient client = mock(KubernetesClient.class);
        KubernetesBuildArchiveService archiveService = mock(KubernetesBuildArchiveService.class);
        BuildLogsMap buildLogsMap = mock(BuildLogsMap.class);
        TempFileUtilService tempFileUtilService = new TempFileUtilService(temporaryDirectory);
        BuildJobQueueItem buildJob = buildJob();
        String jobName = KubernetesBuildJobRunner.jobName(buildJob, "Agent One");
        Path inputArchive = tempFileUtilService.createTempFile("input-", ".tar");
        FileUtils.writeByteArrayToFile(inputArchive.toFile(), new byte[] { 1, 2, 3 });
        when(archiveService.createInputArchive(any(), any())).thenReturn(inputArchive);

        KubernetesBuildJobRunner runner = runner(client, archiveService, buildLogsMap, tempFileUtilService);
        configureSuccessfulExecution(client, jobName, resultArchive(), false);

        try (BuildJobRunnerResult result = runner.execute(buildJob, preparedBuildJob())) {
            assertThat(result.exitCode()).isZero();
            assertThat(result.completedAt().toInstant()).isEqualTo(ZonedDateTime.parse("2026-08-01T12:00:00Z").toInstant());
            assertThat(result.resultArchive()).isNotNull();
            assertThat(result.resultArchive().readAllBytes()).isNotEmpty();
            assertThat(Files.notExists(inputArchive)).isTrue();
        }

        assertTemporaryFilesAreDeleted();
        verify(buildLogsMap).appendBuildLogEntry(buildJob.id(), "Kubernetes build script finished with exit code 0");
    }

    @Test
    void keepsCollectedResultWhenStoppingHelperFails() throws Exception {
        KubernetesClient client = mock(KubernetesClient.class);
        KubernetesBuildArchiveService archiveService = mock(KubernetesBuildArchiveService.class);
        TempFileUtilService tempFileUtilService = new TempFileUtilService(temporaryDirectory);
        BuildJobQueueItem buildJob = buildJob();
        Path inputArchive = tempFileUtilService.createTempFile("input-", ".tar");
        when(archiveService.createInputArchive(any(), any())).thenReturn(inputArchive);

        KubernetesBuildJobRunner runner = runner(client, archiveService, mock(BuildLogsMap.class), tempFileUtilService);
        configureSuccessfulExecution(client, KubernetesBuildJobRunner.jobName(buildJob, "Agent One"), resultArchive(), true);

        // Stopping the helper happens after the result archive was collected and the Job is deleted right afterwards, so a failing stop signal must not fail the build.
        try (BuildJobRunnerResult result = runner.execute(buildJob, preparedBuildJob())) {
            assertThat(result.exitCode()).isZero();
            assertThat(result.resultArchive()).isNotNull();
            assertThat(result.resultArchive().readAllBytes()).isNotEmpty();
        }

        assertTemporaryFilesAreDeleted();
    }

    @Test
    void reportsKubernetesAvailabilityAndVersion() {
        KubernetesClient client = mock(KubernetesClient.class);
        VersionInfo versionInfo = mock(VersionInfo.class);
        when(versionInfo.getGitVersion()).thenReturn("v1.34.0");
        when(client.getKubernetesVersion()).thenReturn(versionInfo);
        KubernetesBuildJobRunner runner = runner(client, mock(KubernetesBuildArchiveService.class), mock(BuildLogsMap.class), new TempFileUtilService(temporaryDirectory));

        assertThat(runner.type()).isEqualTo(BuildRunnerType.KUBERNETES);
        assertThat(runner.status()).isEqualTo(BuildRunnerStatus.available("v1.34.0"));
    }

    @Test
    void reportsKubernetesApiFailures() {
        KubernetesClient client = mock(KubernetesClient.class);
        when(client.getKubernetesVersion()).thenThrow(new IllegalStateException("unreachable"));
        KubernetesBuildJobRunner runner = runner(client, mock(KubernetesBuildArchiveService.class), mock(BuildLogsMap.class), new TempFileUtilService(temporaryDirectory));

        assertThat(runner.status()).isEqualTo(BuildRunnerStatus.unavailable("unreachable"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cancellationOnlyDeletesJobsOwnedByThisBuildAgent() {
        KubernetesClient client = mock(KubernetesClient.class);
        Job ownJob = job("own-job", "agent-one", "job-id");
        Job otherAgentJob = job("other-job", "agent-two", "job-id");
        KubernetesApi api = kubernetesApi(client);
        FilterWatchListDeletable<Job, JobList, ScalableResource<Job>> managedJobs = mock(FilterWatchListDeletable.class);
        FilterWatchListDeletable<Job, JobList, ScalableResource<Job>> agentJobs = mock(FilterWatchListDeletable.class);
        when(api.jobs().withLabel(KubernetesBuildJobFactory.MANAGED_LABEL, "true")).thenReturn(managedJobs);
        when(managedJobs.withLabel(KubernetesBuildJobFactory.AGENT_LABEL, "agent-one")).thenReturn(agentJobs);
        when(agentJobs.list()).thenReturn(new io.fabric8.kubernetes.api.model.batch.v1.JobListBuilder().withItems(ownJob, otherAgentJob).build());
        ScalableResource<Job> ownJobResource = mock(ScalableResource.class);
        GracePeriodConfigurable<?> ownJobDeletion = mock(GracePeriodConfigurable.class);
        when(api.jobs().withName("own-job")).thenReturn(ownJobResource);
        doReturn(ownJobDeletion).when(ownJobResource).withPropagationPolicy(io.fabric8.kubernetes.api.model.DeletionPropagation.FOREGROUND);
        KubernetesBuildJobRunner runner = runner(client, mock(KubernetesBuildArchiveService.class), mock(BuildLogsMap.class), new TempFileUtilService(temporaryDirectory));

        assertThat(runner.isActive("job-id")).isTrue();
        runner.cancel("job-id");

        verify(ownJobDeletion).delete();
    }

    @Test
    void onlyMatchesJobsOwnedByThisBuildAgent() {
        Job ownJob = job("own-job", "agent-one", "job-id");
        Job otherAgentJob = job("other-job", "agent-two", "job-id");

        assertThat(KubernetesBuildJobRunner.belongsToBuildAgentExecution(ownJob, "job-id", "agent-one")).isTrue();
        assertThat(KubernetesBuildJobRunner.belongsToBuildAgentExecution(otherAgentJob, "job-id", "agent-one")).isFalse();
        assertThat(KubernetesBuildJobRunner.belongsToBuildAgentExecution(ownJob, "other-job-id", "agent-one")).isFalse();
        assertThat(KubernetesBuildJobRunner.belongsToBuildAgentExecution(new Job(), "job-id", "agent-one")).isFalse();
    }

    @Test
    void buildsSafeResultCollectionCommands() {
        String command = KubernetesBuildJobRunner.resultCollectionCommand(List.of("results/**/*.xml", "reports/*.sarif"));

        assertThat(command).contains("shopt -s globstar nullglob", "for source in results/**/*.xml", "for source in reports/*.sarif", "tar -cpf - -C /var/tmp results");
        // The build script runs in the testing directory, so the globs have to be expanded there rather than one level
        // up in the workspace: expanding them in /var/tmp matched nothing and still exited 0, producing an empty archive
        assertThat(command).contains("cd /var/tmp/testing-dir");
        assertThat(command.indexOf("cd /var/tmp/testing-dir")).isLessThan(command.indexOf("for source in"));
        // A miss is skipped, but a real mv failure must not be swallowed
        assertThat(command).contains("|| continue").doesNotContain("|| true");
        assertThatThrownBy(() -> KubernetesBuildJobRunner.resultCollectionCommand(List.of("results/../../secret"))).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> KubernetesBuildJobRunner.resultCollectionCommand(List.of("results/$(command)"))).isInstanceOf(LocalCIException.class);
        assertThatThrownBy(() -> KubernetesBuildJobRunner.resultCollectionCommand(List.of(""))).isInstanceOf(LocalCIException.class);
    }

    @Test
    void createsBoundedStableResourceNames() {
        assertThat(KubernetesBuildJobRunner.toDnsLabel("Agent One/@Main")).isEqualTo("agent-one-main");
        assertThat(KubernetesBuildJobRunner.toDnsLabel(" / ")).isEqualTo("unknown");
        assertThat(KubernetesBuildJobRunner.toDnsLabel(null)).isEqualTo("unknown");
        String shortenedLabel = KubernetesBuildJobRunner.toDnsLabel("A".repeat(100));
        assertThat(shortenedLabel).hasSizeLessThanOrEqualTo(63).isEqualTo(KubernetesBuildJobRunner.toDnsLabel("A".repeat(100)));
        String firstAgentJob = KubernetesBuildJobRunner.jobName(buildJob(), "agent-one");
        String secondAgentJob = KubernetesBuildJobRunner.jobName(buildJob(), "agent-two");
        assertThat(firstAgentJob).startsWith("local-ci-").contains("-r2-").hasSizeLessThanOrEqualTo(63).isNotEqualTo(secondAgentJob);
    }

    @Test
    void boundsBufferedLogLinesAndDiscardsOverflow() throws Exception {
        BuildLogsMap buildLogsMap = mock(BuildLogsMap.class);
        var output = new KubernetesBuildJobRunner.BuildLogOutputStream("job-id", buildLogsMap, 8);

        output.write("abcdefghijkl\r\nnext".getBytes(StandardCharsets.UTF_8));
        output.flushLine();

        verify(buildLogsMap).appendBuildLogEntry("job-id", "abcdefgh");
        verify(buildLogsMap).appendBuildLogEntry("job-id", "next");
    }

    @Test
    void parsesCompletionDatesAndAppliesExecutionGracePeriod() {
        KubernetesBuildJobRunner runner = runner(mock(KubernetesClient.class), mock(KubernetesBuildArchiveService.class), mock(BuildLogsMap.class),
                new TempFileUtilService(temporaryDirectory));

        ZonedDateTime completion = ReflectionTestUtils.invokeMethod(runner, "parseCompletionDate", "2026-08-01T12:00:00Z");
        ZonedDateTime fallback = ReflectionTestUtils.invokeMethod(runner, "parseCompletionDate", "not-a-date");
        Duration wait = ReflectionTestUtils.invokeMethod(runner, "effectiveExecutionWait", buildJob());

        assertThat(completion.toInstant()).isEqualTo(ZonedDateTime.parse("2026-08-01T12:00:00Z").toInstant());
        assertThat(fallback).isBetween(ZonedDateTime.now().minusSeconds(1), ZonedDateTime.now().plusSeconds(1));
        assertThat(wait).isEqualTo(Duration.ofSeconds(75));
    }

    @Test
    void waitsOnlyForTheTimeoutCappedByTheJobFactory() {
        // The Job factory caps the requested timeout with artemis.continuous-integration.build-timeout-seconds.max and uses it for activeDeadlineSeconds. Waiting longer
        // than that would keep the runner blocked after Kubernetes already terminated the Job.
        KubernetesBuildJobFactory factory = mock(KubernetesBuildJobFactory.class);
        when(factory.effectiveBuildTimeout(any())).thenReturn(30);
        var runner = new KubernetesBuildJobRunner(mock(KubernetesClient.class), properties(), factory, mock(KubernetesBuildArchiveService.class), mock(BuildLogsMap.class),
                new TempFileUtilService(temporaryDirectory), 1024);

        Duration wait = ReflectionTestUtils.invokeMethod(runner, "effectiveExecutionWait", buildJob());

        assertThat(wait).isEqualTo(Duration.ofSeconds(45));
    }

    private KubernetesBuildJobRunner runner(KubernetesClient client, KubernetesBuildArchiveService archiveService, BuildLogsMap buildLogsMap,
            TempFileUtilService tempFileUtilService) {
        KubernetesBuildJobFactory factory = mock(KubernetesBuildJobFactory.class);
        when(factory.createJob(any(), any(), any())).thenReturn(new JobBuilder().build());
        // The runner reuses the capping of the factory, which leaves the 60 seconds requested by the test build job untouched.
        when(factory.effectiveBuildTimeout(any())).thenReturn(60);
        var runner = new KubernetesBuildJobRunner(client, properties(), factory, archiveService, buildLogsMap, tempFileUtilService, 1024);
        ReflectionTestUtils.setField(runner, "buildAgentName", "Agent One");
        return runner;
    }

    @SuppressWarnings("unchecked")
    private void configureSuccessfulExecution(KubernetesClient client, String jobName, byte[] resultArchive, boolean failHelperStop) throws Exception {
        KubernetesApi api = kubernetesApi(client);
        ScalableResource<Job> namedJob = mock(ScalableResource.class);
        GracePeriodConfigurable<?> jobDeletion = mock(GracePeriodConfigurable.class);
        when(api.jobs().withName(jobName)).thenReturn(namedJob);
        when(namedJob.get()).thenReturn(null);
        doReturn(jobDeletion).when(namedJob).withPropagationPolicy(io.fabric8.kubernetes.api.model.DeletionPropagation.FOREGROUND);
        ScalableResource<Job> createdJob = mock(ScalableResource.class);
        when(api.jobs().resource(any(Job.class))).thenReturn(createdJob);
        when(createdJob.create()).thenReturn(new JobBuilder().build());

        Pod runningPod = runningPod();
        FilterWatchListDeletable<Pod, PodList, PodResource> jobPods = mock(FilterWatchListDeletable.class);
        when(api.pods().withLabel("job-name", jobName)).thenReturn(jobPods);
        when(jobPods.list()).thenReturn(new PodListBuilder().withItems(runningPod).build());
        PodResource buildPod = mock(PodResource.class);
        when(api.pods().withName("build-pod")).thenReturn(buildPod);
        when(buildPod.get()).thenReturn(completedPod());

        ContainerResource builder = mock(ContainerResource.class);
        when(buildPod.inContainer(KubernetesBuildJobFactory.BUILDER_CONTAINER)).thenReturn(builder);
        LogWatch logWatch = mock(LogWatch.class);
        when(logWatch.onClose()).thenReturn(CompletableFuture.completedFuture(null));
        when(builder.watchLog(any(OutputStream.class))).thenReturn(logWatch);

        ContainerResource helper = helper(resultArchive, failHelperStop);
        when(buildPod.inContainer(KubernetesBuildJobFactory.HELPER_CONTAINER)).thenReturn(helper);
    }

    @SuppressWarnings("unchecked")
    private static KubernetesApi kubernetesApi(KubernetesClient client) {
        BatchAPIGroupDSL batch = mock(BatchAPIGroupDSL.class);
        V1BatchAPIGroupDSL batchV1 = mock(V1BatchAPIGroupDSL.class);
        MixedOperation<Job, JobList, ScalableResource<Job>> jobs = mock(MixedOperation.class);
        NonNamespaceOperation<Job, JobList, ScalableResource<Job>> namespacedJobs = mock(NonNamespaceOperation.class);
        MixedOperation<Pod, PodList, PodResource> pods = mock(MixedOperation.class);
        NonNamespaceOperation<Pod, PodList, PodResource> namespacedPods = mock(NonNamespaceOperation.class);
        when(client.batch()).thenReturn(batch);
        when(batch.v1()).thenReturn(batchV1);
        when(batchV1.jobs()).thenReturn(jobs);
        when(jobs.inNamespace("builds")).thenReturn(namespacedJobs);
        when(client.pods()).thenReturn(pods);
        when(pods.inNamespace("builds")).thenReturn(namespacedPods);
        return new KubernetesApi(namespacedJobs, namespacedPods);
    }

    private static ContainerResource helper(byte[] resultArchive, boolean failHelperStop) throws Exception {
        ContainerResource helper = mock(ContainerResource.class);
        CopyOrReadable remoteFile = mock(CopyOrReadable.class);
        when(helper.file("/var/tmp/artemis-localci-input.tar")).thenReturn(remoteFile);
        when(remoteFile.upload(any(InputStream.class))).thenReturn(true);

        AtomicReference<OutputStream> output = new AtomicReference<>();
        TtyExecErrorable outputStage = mock(TtyExecErrorable.class);
        TtyExecErrorChannelable errorStage = mock(TtyExecErrorChannelable.class);
        ExecWatch watch = mock(ExecWatch.class);
        when(watch.exitCode()).thenReturn(CompletableFuture.completedFuture(0));
        when(helper.writingOutput(any(OutputStream.class))).thenAnswer(invocation -> {
            output.set(invocation.getArgument(0));
            return outputStage;
        });
        when(outputStage.writingError(any(OutputStream.class))).thenReturn(errorStage);
        when(errorStage.exec(any(String[].class))).thenAnswer(invocation -> {
            String command = java.util.Arrays.stream(invocation.getArguments()).map(Object::toString).collect(java.util.stream.Collectors.joining(" "));
            if (command.contains("tar -cpf -")) {
                output.get().write(resultArchive);
            }
            if (failHelperStop && command.contains(KubernetesBuildJobFactory.HELPER_STOP_FILE)) {
                throw new LocalCIException("helper stop failed");
            }
            return watch;
        });
        return helper;
    }

    private PreparedBuildJob preparedBuildJob() throws Exception {
        Path assignment = Files.createDirectory(temporaryDirectory.resolve("assignment"));
        Path tests = Files.createDirectory(temporaryDirectory.resolve("tests"));
        return new PreparedBuildJob(assignment, tests, null, List.of());
    }

    private void assertTemporaryFilesAreDeleted() throws Exception {
        try (var paths = Files.list(temporaryDirectory)) {
            assertThat(paths.filter(Files::isRegularFile)).isEmpty();
        }
    }

    private static Pod runningPod() {
        return new PodBuilder().withNewMetadata().withName("build-pod").endMetadata().withNewStatus().withPhase("Running").addNewContainerStatus().withName("builder")
                .withStarted(true).endContainerStatus().addNewContainerStatus().withName("helper").withStarted(true).endContainerStatus().endStatus().build();
    }

    private static Pod completedPod() {
        return new PodBuilder().withNewMetadata().withName("build-pod").endMetadata().withNewStatus().withPhase("Running").addNewContainerStatus().withName("builder")
                .withStarted(false).withNewState().withNewTerminated().withExitCode(0).withFinishedAt("2026-08-01T12:00:00Z").endTerminated().endState().endContainerStatus()
                .addNewContainerStatus().withName("helper").withStarted(true).endContainerStatus().endStatus().build();
    }

    private static byte[] resultArchive() throws Exception {
        byte[] result = "<testsuite tests=\"0\"/>".getBytes(StandardCharsets.UTF_8);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(output)) {
            TarArchiveEntry entry = new TarArchiveEntry("results/TEST-result.xml");
            entry.setSize(result.length);
            tar.putArchiveEntry(entry);
            tar.write(result);
            tar.closeArchiveEntry();
            tar.finish();
            return output.toByteArray();
        }
    }

    private static Job job(String name, String agentLabel, String buildJobId) {
        return new JobBuilder().withNewMetadata().withName(name).addToLabels(KubernetesBuildJobFactory.AGENT_LABEL, agentLabel)
                .addToAnnotations(KubernetesBuildJobFactory.BUILD_JOB_ANNOTATION, buildJobId).endMetadata().build();
    }

    private static KubernetesBuildRunnerProperties properties() {
        return new KubernetesBuildRunnerProperties("builds", "helper:test", "IfNotPresent", "workload", 1, 1, 60, 15, 30, "3Gi",
                new KubernetesBuildRunnerProperties.Resources("500m", "512Mi", "2Gi"), Map.of(), List.of(), List.of());
    }

    private static BuildJobQueueItem buildJob() {
        var repositoryInfo = new RepositoryInfo("repository", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", null, new String[0], new String[0]);
        var timing = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 60);
        var buildConfig = new BuildConfig("echo test", "ubuntu:24.04", "commit", "assignment-commit", "test-commit", "main", ProgrammingLanguage.C, null, false, false,
                List.of("results/*.xml"), 60, "assignment", "tests", "solution", new DockerRunConfig(List.of(), null, 0, 0, 0));
        return new BuildJobQueueItem("job/id", "job", new BuildAgentDTO(null, null, null), 1, 2, 3, 2, 1, null, repositoryInfo, timing, buildConfig, null);
    }

    private record KubernetesApi(NonNamespaceOperation<Job, JobList, ScalableResource<Job>> jobs, NonNamespaceOperation<Pod, PodList, PodResource> pods) {
    }
}
