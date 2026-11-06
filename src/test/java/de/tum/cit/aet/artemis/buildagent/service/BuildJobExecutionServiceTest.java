package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.LocalCITestJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunnerResult;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildRunnerType;
import de.tum.cit.aet.artemis.buildagent.service.runner.PreparedBuildJob;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.exception.GitException;

class BuildJobExecutionServiceTest {

    @TempDir
    private Path temporaryDirectory;

    private final BuildJobGitService git = mock(BuildJobGitService.class);

    private final BuildJobRunner runner = mock(BuildJobRunner.class);

    private final BuildLogsMap logs = mock(BuildLogsMap.class);

    private final BuildJobExecutionService service = new BuildJobExecutionService(git, runner, logs);

    private final ZonedDateTime completedAt = ZonedDateTime.parse("2026-08-01T12:00:00Z");

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "checkedOutReposPath", temporaryDirectory.toString());
        ReflectionTestUtils.setField(service, "defaultBranch", "main");
        when(runner.type()).thenReturn(BuildRunnerType.DOCKER);
        when(git.cloneRepository(any(), any())).thenAnswer(call -> repository(call.getArgument(1)));
        when(git.getExistingCheckedOutRepositoryByLocalPath(any(), any(), eq("main"))).thenAnswer(call -> repository(call.getArgument(0)));
        when(git.getLastCommitHash(any())).thenAnswer(call -> ((LocalVCRepositoryUri) call.getArgument(0)).repositorySlug() + "-head");
    }

    @Test
    void parsesMixedResultArchiveAndAlwaysReleasesRunnerAndCloneToken() throws Exception {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("results/", "");
        entries.put("pom.xml", "ignored invalid xml");
        entries.put("README.md", "ignored");
        entries.put("results/blank.xml", " \n\t ");
        entries.put("results/broken.xml", "not XML");
        entries.put("TEST-result.xml",
                "<testsuite name=\"suite\"><testcase name=\"pass\" classname=\"Example\"/><testcase name=\"fail\" classname=\"Example\"><failure message=\"wrong\">details</failure></testcase></testsuite>");
        entries.put("results/feedback.json", "{\"name\":\"custom\",\"successful\":false,\"message\":\"custom failure\"}");
        AtomicBoolean released = new AtomicBoolean();
        List<BuildLogDTO> buildLogs = List.of(new BuildLogDTO(completedAt, "compiler log"));
        when(logs.getAndTruncateBuildLogs("job")).thenReturn(buildLogs);
        when(runner.execute(any(), any())).thenReturn(new BuildJobRunnerResult(new ByteArrayInputStream(archive(entries)), 1, completedAt, () -> released.set(true)));
        var job = buildJob(RepositoryType.USER, true, uri("course-solution"), new String[] { uri("course-auxiliary") });

        var result = service.runBuildJob(job);

        assertThat(result.assignmentRepoCommitHash()).isEqualTo("assignment-commit");
        assertThat(result.testsRepoCommitHash()).isEqualTo("test-commit");
        assertThat(result.assignmentRepoBranchName()).isEqualTo("main");
        assertThat(result.buildRunDate()).isEqualTo(completedAt);
        assertThat(result.buildScriptExitCode()).isEqualTo(1);
        assertThat(result.jobs()).hasSize(1);
        assertThat(result.jobs().getFirst().successfulTests()).extracting(LocalCITestJobDTO::name).containsExactly("pass");
        assertThat(result.jobs().getFirst().failedTests()).extracting(LocalCITestJobDTO::name).containsExactly("fail", "custom");
        assertThat(result.buildLogEntries()).isEqualTo(buildLogs);
        assertThat(released).isTrue();
        var prepared = org.mockito.ArgumentCaptor.forClass(PreparedBuildJob.class);
        verify(runner).execute(eq(job), prepared.capture());
        assertThat(prepared.getValue().solutionRepository()).isNotNull();
        assertThat(prepared.getValue().auxiliaryRepositories()).hasSize(1);
        verify(git).checkoutRepositoryAtCommit(any(), eq("assignment-commit"));
        verify(git, times(4)).deleteLocalRepository(any());
        verify(git).setCloneTokenForCurrentThread("job-clone-token");
        verify(git).clearCloneTokenForCurrentThread();
        verify(logs).appendBuildLogEntry("job", "The file blank.xml does not contain any testcases.");
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryType.class, names = { "TESTS", "AUXILIARY", "USER" })
    void missingArchivePreservesCommitMetadataAndReusesAssignmentAsSolution(RepositoryType trigger) throws Exception {
        when(runner.execute(any(), any())).thenReturn(new BuildJobRunnerResult(null, 7, completedAt, () -> {
            throw new IllegalStateException("cleanup failed");
        }));
        var job = buildJob(trigger, false, uri("course-student"), new String[0]);
        var result = service.runBuildJob(job);
        assertThat(result.assignmentRepoCommitHash()).isEqualTo("course-student-head");
        assertThat(result.testsRepoCommitHash()).isEqualTo("course-tests-head");
        assertThat(result.jobs().getFirst().failedTests()).isEmpty();
        assertThat(result.jobs().getFirst().successfulTests()).isEmpty();
        assertThat(result.isBuildSuccessful()).isFalse();
        assertThat(result.buildScriptExitCode()).isEqualTo(7);
        verify(git, times(2)).cloneRepository(any(), any());
        verify(git, never()).checkoutRepositoryAtCommit(any(), any());
        verify(git, times(2)).deleteLocalRepository(any());
        var prepared = org.mockito.ArgumentCaptor.forClass(PreparedBuildJob.class);
        verify(runner).execute(eq(job), prepared.capture());
        assertThat(prepared.getValue().solutionRepository()).isEqualTo(prepared.getValue().assignmentRepository());
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryType.class, names = { "TESTS", "AUXILIARY" })
    void pushesToSharedRepositoriesDoNotCheckOutAssignmentCommit(RepositoryType trigger) throws Exception {
        when(runner.execute(any(), any())).thenReturn(new BuildJobRunnerResult(new TarArchiveInputStream(new ByteArrayInputStream(archive(Map.of()))), 0, completedAt, () -> {
        }));
        var result = service.runBuildJob(buildJob(trigger, true, null, new String[0]));
        assertThat(result.assignmentRepoCommitHash()).isEqualTo("assignment-commit");
        verify(git, never()).checkoutRepositoryAtCommit(any(), any());
        verify(git).clearCloneTokenForCurrentThread();
    }

    @Test
    void retriesTransientCloneFailureAndCleansUpAfterRunnerFailure() throws Exception {
        when(git.cloneRepository(any(), any())).thenThrow(new IOException("connection reset")).thenAnswer(call -> repository(call.getArgument(1)));
        when(runner.execute(any(), any())).thenThrow(new LocalCIException("runner rejected build"));
        assertThatThrownBy(() -> service.runBuildJob(buildJob(RepositoryType.USER, true, null, new String[0]))).isInstanceOf(LocalCIException.class)
                .hasMessage("runner rejected build");
        verify(git, times(3)).cloneRepository(any(), any());
        verify(git, times(2)).deleteLocalRepository(any());
        verify(git).clearCloneTokenForCurrentThread();
    }

    @Test
    void exhaustedCloneAndCheckoutFailuresClearCredentials() throws Exception {
        when(git.cloneRepository(any(), any())).thenThrow(new IOException("unreachable"));
        assertThatThrownBy(() -> service.runBuildJob(buildJob(RepositoryType.USER, true, null, new String[0]))).isInstanceOf(LocalCIException.class)
                .hasMessageContaining("after 3 attempts");
        verify(git, times(3)).cloneRepository(any(), any());
        verify(git).clearCloneTokenForCurrentThread();
    }

    @Test
    void checkoutFailureDoesNotStartRunnerAndStillClearsCredentials() throws Exception {
        doThrow(new GitException("missing revision")).when(git).checkoutRepositoryAtCommit(any(), any());
        assertThatThrownBy(() -> service.runBuildJob(buildJob(RepositoryType.USER, true, null, new String[0]))).isInstanceOf(LocalCIException.class)
                .hasMessageContaining("Error while checking out commit assignment-commit");
        verify(runner, never()).execute(any(), any());
        verify(git).clearCloneTokenForCurrentThread();
    }

    @Test
    void missingHeadReportsRepositoryKindAndAlwaysClearsCredentials() {
        doThrow(new EntityNotFoundException("missing")).when(git).getLastCommitHash(any());
        assertThatThrownBy(() -> service.runBuildJob(buildJob(RepositoryType.USER, false, null, new String[0]))).isInstanceOf(LocalCIException.class)
                .hasMessageContaining("Could not find last commit hash for assignment repository course-student");
        verify(git).clearCloneTokenForCurrentThread();
    }

    @Test
    void repositoryCleanupFailuresDoNotDiscardResult() throws Exception {
        when(runner.execute(any(), any())).thenReturn(new BuildJobRunnerResult(null, 0, completedAt, () -> {
        }));
        when(git.getExistingCheckedOutRepositoryByLocalPath(any(), any(), eq("main"))).thenReturn(null);
        var result = service.runBuildJob(buildJob(RepositoryType.USER, true, null, new String[0]));
        assertThat(result.buildScriptExitCode()).isZero();
        verify(logs, times(2)).appendBuildLogEntry("job", "Repository for build job job not found");
        verify(git).clearCloneTokenForCurrentThread();
    }

    @Test
    void startupCleanupRemovesOnlyExpiredDirectories() throws Exception {
        Path old = Files.createDirectory(temporaryDirectory.resolve("old"));
        Path recent = Files.createDirectory(temporaryDirectory.resolve("recent"));
        Path regularFile = temporaryDirectory.resolve("keep.txt");
        FileUtils.writeStringToFile(regularFile.toFile(), "keep", StandardCharsets.UTF_8);
        FileTime expired = FileTime.from(Instant.now().minusSeconds(600));
        Files.setLastModifiedTime(old, expired);
        Files.setLastModifiedTime(regularFile, expired);
        service.initAsync();
        assertThat(old).doesNotExist();
        assertThat(recent).isDirectory();
        assertThat(regularFile).hasContent("keep");
        ReflectionTestUtils.setField(service, "checkedOutReposPath", temporaryDirectory.resolve("absent").toString());
        service.initAsync();
        assertThat(temporaryDirectory.resolve("absent")).doesNotExist();
    }

    @Test
    void malformedArchiveIsReportedAsParsingFailureAndRunnerCleanupStillRuns() throws Exception {
        AtomicBoolean released = new AtomicBoolean();
        var broken = new java.io.InputStream() {

            @Override
            public int read() throws IOException {
                throw new IOException("archive stream interrupted");
            }
        };
        when(runner.execute(any(), any())).thenReturn(new BuildJobRunnerResult(broken, 0, completedAt, () -> released.set(true)));
        assertThatThrownBy(() -> service.runBuildJob(buildJob(RepositoryType.USER, true, null, new String[0]))).isInstanceOf(LocalCIException.class)
                .hasMessage("Error while parsing build results");
        assertThat(released).isTrue();
        verify(git, times(2)).deleteLocalRepository(any());
        verify(git).clearCloneTokenForCurrentThread();
    }

    @Test
    void ioFailureDuringRepositoryDeletionDoesNotDiscardResult() throws Exception {
        when(runner.execute(any(), any())).thenReturn(new BuildJobRunnerResult(null, 0, completedAt, () -> {
        }));
        doThrow(new IOException("directory locked")).when(git).deleteLocalRepository(any());
        var result = service.runBuildJob(buildJob(RepositoryType.USER, true, null, new String[0]));
        assertThat(result.buildScriptExitCode()).isZero();
        verify(logs, times(2)).appendBuildLogEntry("job", "Error while deleting repository");
        verify(git).clearCloneTokenForCurrentThread();
    }

    private Repository repository(Path path) {
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(path);
        return repository;
    }

    private String uri(String slug) {
        return "https://vc.invalid/git/COURSE/" + slug + ".git";
    }

    private BuildJobQueueItem buildJob(RepositoryType trigger, boolean explicitCommits, String solution, String[] auxiliary) {
        var repositories = new RepositoryInfo("repository", RepositoryType.USER, trigger, uri("course-student"), uri("course-tests"), solution, auxiliary,
                new String[auxiliary.length]);
        var timing = new JobTimingInfo(completedAt.minusMinutes(1), null, null, null, 60);
        var config = new BuildConfig("echo test", "ubuntu:24.04", "commit", explicitCommits ? "assignment-commit" : null, explicitCommits ? "test-commit" : null, "main",
                ProgrammingLanguage.JAVA, null, false, false, List.of("results/*.xml"), 60, "assignment", "tests", "solution", new DockerRunConfig(List.of(), null, 0, 0, 0));
        return new BuildJobQueueItem("job", "job", new BuildAgentDTO(null, null, null), 1, 2, 3, 0, 1, null, repositories, timing, config, null, null, "job-clone-token");
    }

    private byte[] archive(Map<String, String> entries) throws IOException {
        try (var output = new ByteArrayOutputStream(); var tar = new TarArchiveOutputStream(output)) {
            for (var item : entries.entrySet()) {
                byte[] bytes = item.getValue().getBytes(StandardCharsets.UTF_8);
                var entry = new TarArchiveEntry(item.getKey());
                entry.setSize(bytes.length);
                tar.putArchiveEntry(entry);
                tar.write(bytes);
                tar.closeArchiveEntry();
            }
            tar.finish();
            return output.toByteArray();
        }
    }
}
