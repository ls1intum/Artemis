package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.localvc.service.AbstractGitService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCReceivePackLimits;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit test that an online editor commit is checked against the limits for the files of a repository before it is created.
 */
class RepositoryServiceCommitLimitsTest {

    @TempDir
    Path workingTree;

    @Test
    void commitChangesRefusesAFileAboveTheSizeLimitWithoutCommitting() throws Exception {
        Git.init().setDirectory(workingTree.toFile()).setInitialBranch("main").call().close();
        FileUtils.writeByteArrayToFile(workingTree.resolve("big.bin").toFile(), new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        GitService gitService = new GitService();
        BareGitRepositoryService bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getMaxRepositorySizeBytes(any())).thenReturn(20L * 1024 * 1024);
        RepositoryService repositoryService = new RepositoryService(gitService, bareGitRepositoryService, Optional.empty());

        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(workingTree,
                new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise"), "main", false, true)) {
            assertThatThrownBy(() -> repositoryService.commitChanges(repository, new User())).isInstanceOf(BadRequestAlertException.class);
            assertThat(repository.resolve(org.eclipse.jgit.lib.Constants.HEAD)).as("no commit is created for rejected content").isNull();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "stage", "commit", "commitStagedChanges", "commitAndPush" })
    void competingGitOperationsWaitUntilTheValidatedOnlineEditorCommitCompletes(String operation) throws Exception {
        Path barePath = workingTree.resolve("ABC/abc-exercise.git");
        Git.init().setDirectory(barePath.toFile()).setBare(true).setInitialBranch("main").call().close();
        Path checkout = workingTree.resolve("checkout");
        Git.init().setDirectory(checkout.toFile()).setInitialBranch("main").call().close();
        FileUtils.writeStringToFile(checkout.resolve("safe.txt").toFile(), "safe", StandardCharsets.UTF_8);
        GitService gitService = spy(new GitService());
        ReflectionTestUtils.setField(gitService, "localVCBasePath", workingTree);
        ReflectionTestUtils.setField(gitService, "artemisGitName", "Artemis");
        ReflectionTestUtils.setField(gitService, "artemisGitEmail", "artemis@example.com");
        BareGitRepositoryService bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getMaxRepositorySizeBytes(any())).thenReturn(10L);
        RepositoryService repositoryService = new RepositoryService(gitService, bareGitRepositoryService, Optional.empty());
        CountDownLatch validated = new CountDownLatch(1);
        CountDownLatch finishCommit = new CountDownLatch(1);
        CountDownLatch competingOperationStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            if ("Changes by Online Editor".equals(invocation.getArgument(1))) {
                validated.countDown();
                assertThat(finishCommit.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.callRealMethod();
        }).when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(checkout, uri, "main", false, true);
                Repository competingRepository = AbstractGitService.linkRepositoryForExistingGit(checkout, uri, "main", false, true);
                var executor = Executors.newFixedThreadPool(2)) {
            var commit = executor.submit(() -> repositoryService.commitChanges(repository, null));
            try {
                assertThat(validated.await(10, TimeUnit.SECONDS)).isTrue();
                FileUtils.writeStringToFile(checkout.resolve("rejected.txt").toFile(), "This exceeds the repository size limit", StandardCharsets.UTF_8);
                var competingOperation = executor.submit(() -> {
                    competingOperationStarted.countDown();
                    switch (operation) {
                        case "stage" -> gitService.stageAllChanges(competingRepository);
                        case "commit" -> gitService.commit(competingRepository, "Concurrent commit");
                        case "commitStagedChanges" -> gitService.commitStagedChanges(competingRepository, "Concurrent commit", null);
                        case "commitAndPush" -> gitService.commitAndPush(competingRepository, "Concurrent commit", true, null);
                        default -> throw new IllegalArgumentException(operation);
                    }
                    return null;
                });
                assertThat(competingOperationStarted.await(10, TimeUnit.SECONDS)).isTrue();
                boolean competingOperationWaited = false;
                try {
                    competingOperation.get(250, TimeUnit.MILLISECONDS);
                }
                catch (TimeoutException expected) {
                    competingOperationWaited = true;
                }
                finishCommit.countDown();
                String hash = commit.get(10, TimeUnit.SECONDS);
                competingOperation.get(10, TimeUnit.SECONDS);
                try (RevWalk walk = new RevWalk(repository)) {
                    var committed = walk.parseCommit(repository.resolve(hash));
                    try (TreeWalk rejected = TreeWalk.forPath(repository, "rejected.txt", committed.getTree())) {
                        assertThat(rejected).as("the commit contains only the validated index").isNull();
                    }
                }
                assertThat(competingOperationWaited).as("another handle to the same working copy must wait until the validated commit completes").isTrue();
            }
            finally {
                finishCommit.countDown();
            }
        }
    }
}
