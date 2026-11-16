package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
        Path barePath = workingTree.resolve("ABC/abc-exercise.git");
        Git.init().setDirectory(barePath.toFile()).setBare(true).setInitialBranch("main").call().close();
        Path checkout = workingTree.resolve("checkout");
        Git.init().setDirectory(checkout.toFile()).setInitialBranch("main").call().close();
        FileUtils.writeByteArrayToFile(checkout.resolve("big.bin").toFile(), new byte[(int) LocalVCReceivePackLimits.MAX_BLOB_SIZE_BYTES + 1]);
        GitService gitService = new GitService();
        BareGitRepositoryService bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(any(), anyBoolean()))
                .thenAnswer(invocation -> AbstractGitService.linkRepositoryForExistingGit(barePath, invocation.getArgument(0), "main", true, false));
        when(bareGitRepositoryService.getMaxRepositorySizeBytes(any())).thenReturn(20L * 1024 * 1024);
        RepositoryService repositoryService = new RepositoryService(gitService, bareGitRepositoryService, Optional.empty());

        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(checkout,
                new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise"), "main", false, true)) {
            assertThatThrownBy(() -> repositoryService.commitChanges(repository, new User())).isInstanceOf(BadRequestAlertException.class);
            assertThat(repository.resolve(org.eclipse.jgit.lib.Constants.HEAD)).as("no commit is created for rejected content").isNull();
        }
    }

    @Test
    void commitRetryAfterRejectedPushAndPullCannotPublishMergedContentAboveTheQuota() throws Exception {
        Path barePath = workingTree.resolve("ABC/abc-exercise.git");
        Git.init().setDirectory(barePath.toFile()).setBare(true).setInitialBranch("main").call().close();
        Path checkout = workingTree.resolve("checkout");
        try (Git seed = Git.init().setDirectory(checkout.toFile()).setInitialBranch("main").call()) {
            FileUtils.writeStringToFile(checkout.resolve("base.txt").toFile(), "0123456789", StandardCharsets.UTF_8);
            seed.add().addFilepattern(".").call();
            GitService.commit(seed).setMessage("Seed").setCommitter("Artemis", "artemis@example.com").call();
            seed.push().setRemote(barePath.toUri().toString()).call();
            seed.getRepository().getConfig().setString("user", null, "name", "Artemis");
            seed.getRepository().getConfig().setString("user", null, "email", "artemis@example.com");
            seed.getRepository().getConfig().setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
            seed.getRepository().getConfig().setString("branch", "main", "remote", "origin");
            seed.getRepository().getConfig().setString("branch", "main", "merge", "refs/heads/main");
            seed.getRepository().getConfig().save();
        }
        Path remoteCheckout = workingTree.resolve("remote-checkout");
        String remoteCommit;
        try (Git remote = Git.cloneRepository().setURI(barePath.toUri().toString()).setDirectory(remoteCheckout.toFile()).call()) {
            FileUtils.writeStringToFile(remoteCheckout.resolve("remote.txt").toFile(), "remote01", StandardCharsets.UTF_8);
            remote.add().addFilepattern(".").call();
            remoteCommit = GitService.commit(remote).setMessage("Remote addition").setCommitter("Artemis", "artemis@example.com").call().getName();
        }
        FileUtils.writeStringToFile(checkout.resolve("editor.txt").toFile(), "editor01", StandardCharsets.UTF_8);
        GitService gitService = spy(new GitService());
        ReflectionTestUtils.setField(gitService, "localVCBasePath", workingTree);
        ReflectionTestUtils.setField(gitService, "artemisGitName", "Artemis");
        ReflectionTestUtils.setField(gitService, "artemisGitEmail", "artemis@example.com");
        BareGitRepositoryService bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(any(), anyBoolean()))
                .thenAnswer(invocation -> AbstractGitService.linkRepositoryForExistingGit(barePath, invocation.getArgument(0), "main", true, false));
        when(bareGitRepositoryService.getMaxRepositorySizeBytes(any())).thenReturn(20L);
        RepositoryService repositoryService = new RepositoryService(gitService, bareGitRepositoryService, Optional.empty());
        AtomicBoolean publishRemoteChange = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (publishRemoteChange.getAndSet(false)) {
                // Publish after validation so the editor's independently valid 18-byte commit cannot fast-forward the destination.
                try (Git remote = Git.open(remoteCheckout.toFile())) {
                    remote.push().call();
                }
            }
            return invocation.callRealMethod();
        }).when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(checkout, uri, "main", false, true); Git destination = Git.open(barePath.toFile())) {
            repositoryService.commitChanges(repository, null);
            assertThat(destination.getRepository().resolve("refs/heads/main").getName()).as("the editor push was rejected after the remote advanced").isEqualTo(remoteCommit);
            assertThat(committedBytes(repository)).isEqualTo(18L);
            assertThat(committedBytes(destination.getRepository())).isEqualTo(18L);

            gitService.pullIgnoreConflicts(repository);
            String mergedCommit = repository.resolve(org.eclipse.jgit.lib.Constants.HEAD).getName();
            assertThat(committedBytes(repository)).as("the pull merges both independently valid additions").isEqualTo(26L);

            assertThatThrownBy(() -> repositoryService.commitChanges(repository, null)).isInstanceOf(BadRequestAlertException.class);

            assertThat(destination.getRepository().resolve("refs/heads/main").getName()).as("retrying must not publish the oversized merged tree").isEqualTo(remoteCommit);
            assertThat(committedBytes(destination.getRepository())).isEqualTo(18L);
            assertThat(repository.resolve(org.eclipse.jgit.lib.Constants.HEAD).getName()).as("the rejected merge remains available for correction").isEqualTo(mergedCommit);
            assertThat(committedBytes(repository)).isEqualTo(26L);
            assertThat(checkout.resolve("editor.txt")).hasContent("editor01");
            assertThat(checkout.resolve("remote.txt")).hasContent("remote01");
        }
    }

    private static long committedBytes(org.eclipse.jgit.lib.Repository repository) throws IOException {
        try (TreeWalk files = new TreeWalk(repository)) {
            files.addTree(repository.resolve("HEAD^{tree}"));
            files.setRecursive(true);
            long bytes = 0;
            while (files.next()) {
                bytes += repository.open(files.getObjectId(0)).getSize();
            }
            return bytes;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "stage", "commit", "commitStagedChanges", "commitAndPush", "reset" })
    void competingGitOperationsWaitUntilTheValidatedOnlineEditorCommitCompletes(String operation) throws Exception {
        Path barePath = workingTree.resolve("ABC/abc-exercise.git");
        Git.init().setDirectory(barePath.toFile()).setBare(true).setInitialBranch("main").call().close();
        Path checkout = workingTree.resolve("checkout");
        Git.init().setDirectory(checkout.toFile()).setInitialBranch("main").call().close();
        FileUtils.writeStringToFile(checkout.resolve("safe.txt").toFile(), "initial", StandardCharsets.UTF_8);
        try (Git seed = Git.open(checkout.toFile())) {
            seed.add().addFilepattern(".").call();
            GitService.commit(seed).setMessage("Seed").setCommitter("Artemis", "artemis@example.com").call();
            seed.push().setRemote(barePath.toUri().toString()).call();
        }
        FileUtils.writeStringToFile(checkout.resolve("safe.txt").toFile(), "safe", StandardCharsets.UTF_8);
        GitService gitService = spy(new GitService());
        ReflectionTestUtils.setField(gitService, "localVCBasePath", workingTree);
        ReflectionTestUtils.setField(gitService, "artemisGitName", "Artemis");
        ReflectionTestUtils.setField(gitService, "artemisGitEmail", "artemis@example.com");
        BareGitRepositoryService bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(any(), anyBoolean()))
                .thenAnswer(invocation -> AbstractGitService.linkRepositoryForExistingGit(barePath, invocation.getArgument(0), "main", true, false));
        when(bareGitRepositoryService.getMaxRepositorySizeBytes(any())).thenReturn(10L);
        RepositoryService repositoryService = new RepositoryService(gitService, bareGitRepositoryService, Optional.empty());
        CountDownLatch validated = new CountDownLatch(1);
        CountDownLatch finishCommit = new CountDownLatch(1);
        CountDownLatch competingOperationStarted = new CountDownLatch(1);
        AtomicReference<Thread> competingThread = new AtomicReference<>();
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
                    competingThread.set(Thread.currentThread());
                    competingOperationStarted.countDown();
                    switch (operation) {
                        case "reset" -> gitService.reset(competingRepository, "HEAD");
                        case "stage" -> gitService.stageAllChanges(competingRepository);
                        case "commit" -> gitService.commit(competingRepository, "Concurrent commit");
                        case "commitStagedChanges" -> gitService.commitStagedChanges(competingRepository, "Concurrent commit", null);
                        case "commitAndPush" -> gitService.commitAndPush(competingRepository, "Concurrent commit", true, null);
                        default -> throw new IllegalArgumentException(operation);
                    }
                    return null;
                });
                assertThat(competingOperationStarted.await(10, TimeUnit.SECONDS)).isTrue();
                await().atMost(Duration.ofSeconds(10)).until(() -> competingOperation.isDone() || competingThread.get().getState() == Thread.State.WAITING);
                boolean competingOperationWaited = !competingOperation.isDone();
                finishCommit.countDown();
                String hash = commit.get(10, TimeUnit.SECONDS);
                competingOperation.get(10, TimeUnit.SECONDS);
                try (RevWalk walk = new RevWalk(repository)) {
                    var committed = walk.parseCommit(repository.resolve(hash));
                    try (TreeWalk rejected = TreeWalk.forPath(repository, "rejected.txt", committed.getTree())) {
                        assertThat(rejected).as("the commit contains only the validated index").isNull();
                    }
                    try (TreeWalk safe = TreeWalk.forPath(repository, "safe.txt", committed.getTree())) {
                        assertThat(safe).isNotNull();
                        assertThat(new String(repository.open(safe.getObjectId(0)).getBytes(), StandardCharsets.UTF_8)).isEqualTo("safe");
                    }
                }
                assertThat(competingOperationWaited).as("another handle to the same working copy must wait until the validated commit completes").isTrue();
            }
            finally {
                finishCommit.countDown();
            }
        }
    }

    @Test
    void fastForwardPullWaitsUntilTheValidatedOnlineEditorCommitCompletes() throws Exception {
        Path barePath = workingTree.resolve("ABC/abc-exercise.git");
        Git.init().setDirectory(barePath.toFile()).setBare(true).setInitialBranch("main").call().close();
        Path checkout = workingTree.resolve("checkout");
        Path seedCheckout = workingTree.resolve("seed");
        try (Git seed = Git.init().setDirectory(seedCheckout.toFile()).setInitialBranch("main").call()) {
            FileUtils.writeStringToFile(seedCheckout.resolve("base.txt").toFile(), "0123456789", StandardCharsets.UTF_8);
            seed.add().addFilepattern(".").call();
            GitService.commit(seed).setMessage("Seed").setCommitter("Artemis", "artemis@example.com").call();
            seed.push().setRemote(barePath.toUri().toString()).call();
        }
        Git.cloneRepository().setURI(barePath.toUri().toString()).setDirectory(checkout.toFile()).call().close();
        Path remoteCheckout = workingTree.resolve("remote-checkout");
        try (Git remote = Git.cloneRepository().setURI(barePath.toUri().toString()).setDirectory(remoteCheckout.toFile()).call()) {
            FileUtils.writeStringToFile(remoteCheckout.resolve("remote.txt").toFile(), "remote01", StandardCharsets.UTF_8);
            remote.add().addFilepattern(".").call();
            GitService.commit(remote).setMessage("Remote addition").setCommitter("Artemis", "artemis@example.com").call();
            remote.push().call();
        }
        FileUtils.writeStringToFile(checkout.resolve("editor.txt").toFile(), "editor01", StandardCharsets.UTF_8);
        GitService gitService = spy(new GitService());
        ReflectionTestUtils.setField(gitService, "localVCBasePath", workingTree);
        ReflectionTestUtils.setField(gitService, "artemisGitName", "Artemis");
        ReflectionTestUtils.setField(gitService, "artemisGitEmail", "artemis@example.com");
        BareGitRepositoryService bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(any(), anyBoolean()))
                .thenAnswer(invocation -> AbstractGitService.linkRepositoryForExistingGit(barePath, invocation.getArgument(0), "main", true, false));
        when(bareGitRepositoryService.getMaxRepositorySizeBytes(any())).thenReturn(20L);
        RepositoryService repositoryService = new RepositoryService(gitService, bareGitRepositoryService, Optional.empty());
        CountDownLatch validated = new CountDownLatch(1);
        CountDownLatch finishCommit = new CountDownLatch(1);
        AtomicReference<Thread> pullingThread = new AtomicReference<>();
        doAnswer(invocation -> {
            validated.countDown();
            assertThat(finishCommit.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(gitService).commitAndPush(any(), anyString(), anyBoolean(), any());

        LocalVCRepositoryUri uri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        try (Repository repository = AbstractGitService.linkRepositoryForExistingGit(checkout, uri, "main", false, true);
                Repository pullingRepository = AbstractGitService.linkRepositoryForExistingGit(checkout, uri, "main", false, true);
                var executor = Executors.newFixedThreadPool(2)) {
            var commit = executor.submit(() -> repositoryService.commitChanges(repository, null));
            try {
                assertThat(validated.await(10, TimeUnit.SECONDS)).isTrue();
                var pull = executor.submit(() -> {
                    pullingThread.set(Thread.currentThread());
                    gitService.pullIgnoreConflicts(pullingRepository);
                });
                await().atMost(Duration.ofSeconds(10)).until(() -> pull.isDone() || pullingThread.get() != null && pullingThread.get().getState() == Thread.State.WAITING);
                boolean pullWaited = !pull.isDone();
                finishCommit.countDown();
                String hash = commit.get(10, TimeUnit.SECONDS);
                pull.get(10, TimeUnit.SECONDS);
                try (RevWalk walk = new RevWalk(repository)) {
                    var committed = walk.parseCommit(repository.resolve(hash));
                    try (TreeWalk files = new TreeWalk(repository)) {
                        files.addTree(committed.getTree());
                        files.setRecursive(true);
                        long bytes = 0;
                        while (files.next()) {
                            bytes += repository.open(files.getObjectId(0)).getSize();
                        }
                        assertThat(bytes).as("the editor commit contains the validated 18-byte tree, not the combined 26-byte tree").isEqualTo(18L);
                    }
                    try (TreeWalk remote = TreeWalk.forPath(repository, "remote.txt", committed.getTree())) {
                        assertThat(remote).as("the remote addition cannot enter the index between validation and commit").isNull();
                    }
                }
                try (RevWalk walk = new RevWalk(repository)) {
                    var merged = walk.parseCommit(repository.resolve(org.eclipse.jgit.lib.Constants.HEAD));
                    try (TreeWalk remote = TreeWalk.forPath(repository, "remote.txt", merged.getTree());
                            TreeWalk editor = TreeWalk.forPath(repository, "editor.txt", merged.getTree())) {
                        assertThat(remote).as("the pull succeeds after the editor commit releases the lock").isNotNull();
                        assertThat(editor).as("the pull preserves the editor addition").isNotNull();
                    }
                }
                assertThat(pullWaited).as("the actual pull waits until the validated commit completes").isTrue();
            }
            finally {
                finishCommit.countDown();
            }
        }
    }
}
