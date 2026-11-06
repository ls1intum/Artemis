package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.programming.domain.Repository;

class GitServiceTest {

    @TempDir
    private Path tempDirectory;

    @Test
    void getOrCheckoutRepositoryChecksOutRequestedBranch() throws Exception {
        Path remoteRoot = tempDirectory.resolve("remotes");
        Path bareRepositoryPath = remoteRoot.resolve("TEST").resolve("test-template.git");
        Files.createDirectories(bareRepositoryPath.getParent());

        try (Git ignored = Git.init().setBare(true).setInitialBranch("main").setDirectory(bareRepositoryPath.toFile()).call();
                Git source = Git.init().setInitialBranch("main").setDirectory(tempDirectory.resolve("source").toFile()).call()) {
            Path marker = source.getRepository().getWorkTree().toPath().resolve("branch.txt");
            FileUtils.writeStringToFile(marker.toFile(), "main", StandardCharsets.UTF_8);
            source.add().addFilepattern("branch.txt").call();
            GitService.commit(source).setMessage("Add main marker").setAuthor("Artemis", "artemis@example.com").call();
            source.push().setRemote(bareRepositoryPath.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();

            source.checkout().setCreateBranch(true).setName("release").call();
            FileUtils.writeStringToFile(marker.toFile(), "release", StandardCharsets.UTF_8);
            source.add().addFilepattern("branch.txt").call();
            GitService.commit(source).setMessage("Add release marker").setAuthor("Artemis", "artemis@example.com").call();
            source.push().setRemote(bareRepositoryPath.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/release:refs/heads/release")).call();
        }

        GitService service = new GitService();
        ReflectionTestUtils.setField(service, "localVCBasePath", remoteRoot);
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "test-template");

        try (var repository = service.getOrCheckoutRepositoryOnBranch(repositoryUri, tempDirectory.resolve("checkout"), "release")) {
            assertThat(repository.getBranch()).isEqualTo("release");
            assertThat(Files.readString(repository.getLocalPath().resolve("branch.txt"))).isEqualTo("release");
        }
    }

    /**
     * Several requests for the same repository (a page reload, a second tab, a status poll next to a file listing) each
     * check out and pull the same working copy. A pull that receives a pack takes a lock on that pack, so two pulls at the
     * same time make one of them fail, and a failed pull deletes the working copy it ran in. The pulls of one working copy
     * therefore must not overlap, and none of them may fail because another one is running.
     */
    @Test
    void concurrentPullsOfTheSameWorkingCopyDoNotFailEachOther() throws Exception {
        int concurrentRequests = 8;
        int rounds = 15;
        Path remoteRoot = tempDirectory.resolve("remotes");
        Path bareRepositoryPath = remoteRoot.resolve("TEST").resolve("test-student.git");
        Files.createDirectories(bareRepositoryPath.getParent());
        Path localPath = tempDirectory.resolve("checkout").resolve("test-student");

        GitService service = new GitService();
        ReflectionTestUtils.setField(service, "localVCBasePath", remoteRoot);
        ReflectionTestUtils.setField(service, "localVCBaseUri", URI.create("http://localhost"));
        ReflectionTestUtils.setField(service, "defaultBranch", "main");
        LocalVCRepositoryUri repositoryUri = new LocalVCRepositoryUri(URI.create("http://localhost"), "TEST", "test-student");

        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        try (Git ignored = Git.init().setBare(true).setInitialBranch("main").setDirectory(bareRepositoryPath.toFile()).call();
                Git source = Git.init().setInitialBranch("main").setDirectory(tempDirectory.resolve("source").toFile()).call()) {
            // Round zero checks concurrent first clones; later rounds check concurrent pulls of the same working copy.
            for (int round = 0; round <= rounds; round++) {
                // More new objects than JGit's default unpack limit, so that the fetch keeps a pack (and locks it) instead of writing loose objects
                pushCommitWithManyFiles(source, bareRepositoryPath, round);

                CyclicBarrier startTogether = new CyclicBarrier(concurrentRequests);
                List<Future<Void>> requests = new ArrayList<>();
                for (int request = 0; request < concurrentRequests; request++) {
                    Callable<Void> pull = () -> {
                        startTogether.await(10, TimeUnit.SECONDS);
                        service.getOrCheckoutRepository(repositoryUri, repositoryUri, localPath, true, "main", false).close();
                        return null;
                    };
                    requests.add(executor.submit(pull));
                }
                for (Future<Void> request : requests) {
                    try {
                        request.get(60, TimeUnit.SECONDS);
                    }
                    catch (Exception e) {
                        throw new AssertionError("A pull failed in round " + round, e);
                    }
                }
                assertThat(localPath.resolve(".git")).isDirectory();
                assertThat(Files.readString(localPath.resolve("file-" + round + "-0.txt"))).isEqualTo("round " + round);
            }
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void checkoutBlocksStrictDeletionUntilItsWorkingCopyLockIsReleased() throws Exception {
        GitService service = spy(new GitService());
        var uri = prepareEditorCheckout(service);
        Path checkout = service.getDefaultLocalCheckOutPathOfRepo(uri);
        service.deleteLocalRepositoryStrictly(uri);
        assertThat(checkout).doesNotExist();
        var checkoutEntered = new CountDownLatch(1);
        var releaseCheckout = new CountDownLatch(1);
        var firstLookup = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstLookup.compareAndSet(true, false)) {
                checkoutEntered.countDown();
                assertThat(releaseCheckout.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return invocation.callRealMethod();
        }).when(service).getExistingCheckedOutRepositoryByLocalPath(checkout, uri, "main", false);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var checkoutRequest = executor.submit(() -> {
                try (var repository = service.getOrCheckoutRepository(uri, false, false)) {
                    return repository != null;
                }
            });
            try {
                assertThat(checkoutEntered.await(5, TimeUnit.SECONDS)).isTrue();
                var deletionStarted = new CountDownLatch(1);
                var deletion = executor.submit(() -> {
                    deletionStarted.countDown();
                    service.deleteLocalRepositoryStrictly(uri);
                    return null;
                });
                assertThat(deletionStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> deletion.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(checkout).as("strict cleanup must wait even while the in-flight checkout has not cloned the path yet").doesNotExist();
                releaseCheckout.countDown();
                assertThat(checkoutRequest.get(5, TimeUnit.SECONDS)).isTrue();
                deletion.get(5, TimeUnit.SECONDS);
                assertThat(checkout).doesNotExist();
            }
            finally {
                releaseCheckout.countDown();
            }
        }
    }

    @Test
    void strictDeletionBlocksCheckoutUntilItsOriginalPathIsVacant() throws Exception {
        GitService service = spy(new GitService());
        var uri = prepareEditorCheckout(service);
        Path checkout = service.getDefaultLocalCheckOutPathOfRepo(uri);
        Path answer = checkout.resolve("private-editor-answer.txt");
        FileUtils.writeStringToFile(answer.toFile(), "Uncommitted private answer", StandardCharsets.UTF_8);
        var deletionEntered = new CountDownLatch(1);
        var releaseDeletion = new CountDownLatch(1);
        doAnswer(invocation -> {
            deletionEntered.countDown();
            assertThat(releaseDeletion.await(5, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(service).deleteLocalRepository(any(Repository.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var deletion = executor.submit(() -> {
                service.deleteLocalRepositoryStrictly(uri);
                return null;
            });
            try {
                assertThat(deletionEntered.await(5, TimeUnit.SECONDS)).isTrue();
                var checkoutStarted = new CountDownLatch(1);
                var checkoutRequest = executor.submit(() -> {
                    checkoutStarted.countDown();
                    try (var repository = service.getOrCheckoutRepository(uri, false, false)) {
                        return Files.exists(repository.getLocalPath().resolve("private-editor-answer.txt"));
                    }
                });
                assertThat(checkoutStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> checkoutRequest.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(Files.readString(answer)).isEqualTo("Uncommitted private answer");
                releaseDeletion.countDown();
                deletion.get(5, TimeUnit.SECONDS);
                assertThat(checkoutRequest.get(5, TimeUnit.SECONDS)).as("the fresh checkout must not contain the previous editor answer").isFalse();
                assertThat(checkout.resolve("template.txt")).hasContent("Exercise template");
            }
            finally {
                releaseDeletion.countDown();
            }
        }
    }

    @Test
    void strictDeletionRetainsEditorFilesOnUnderlyingIOExceptionAndRetriesWhileOrdinaryDeletionStaysBestEffort() throws Exception {
        GitService service = spy(new GitService());
        ReflectionTestUtils.setField(service, "repoClonePath", tempDirectory.resolve("checkouts"));
        ReflectionTestUtils.setField(service, "defaultBranch", "main");
        var uri = new LocalVCRepositoryUri(URI.create("http://localhost"), "STRICT", "strict-student");
        Path checkout = service.getDefaultLocalCheckOutPathOfRepo(uri);
        Path answer = checkout.resolve("private-editor-answer.txt");
        try (Git ignored = Git.init().setInitialBranch("main").setDirectory(checkout.toFile()).call()) {
            FileUtils.writeStringToFile(answer.toFile(), "Uncommitted private answer", StandardCharsets.UTF_8);
        }
        var failure = new IOException("Working copy cannot be renamed or deleted");
        doAnswer(invocation -> {
            Repository repository = invocation.getArgument(0);
            repository.closeBeforeDelete();
            throw failure;
        }).when(service).deleteLocalRepository(any(Repository.class));

        assertThatCode(() -> service.deleteLocalRepository(uri)).doesNotThrowAnyException();
        assertThat(Files.readString(answer)).isEqualTo("Uncommitted private answer");
        assertThatThrownBy(() -> service.deleteLocalRepositoryStrictly(uri)).isSameAs(failure);
        assertThat(Files.readString(answer)).isEqualTo("Uncommitted private answer");

        doCallRealMethod().when(service).deleteLocalRepository(any(Repository.class));
        service.deleteLocalRepositoryStrictly(uri);
        assertThat(checkout).doesNotExist();
        assertThatCode(() -> service.deleteLocalRepositoryStrictly(uri)).doesNotThrowAnyException();
        assertThatCode(() -> service.deleteLocalRepositoryStrictly(null)).doesNotThrowAnyException();
    }

    @Test
    void strictDeletionDoesNotClearAnUnopenableCheckoutContainingEditorFiles() throws Exception {
        GitService service = new GitService();
        ReflectionTestUtils.setField(service, "repoClonePath", tempDirectory.resolve("checkouts"));
        ReflectionTestUtils.setField(service, "defaultBranch", "main");
        var uri = new LocalVCRepositoryUri(URI.create("http://localhost"), "STRICT", "strict-student");
        Path checkout = service.getDefaultLocalCheckOutPathOfRepo(uri);
        Path answer = checkout.resolve("private-editor-answer.txt");
        try (Git ignored = Git.init().setInitialBranch("main").setDirectory(checkout.toFile()).call()) {
            FileUtils.writeStringToFile(answer.toFile(), "Answer surviving incomplete cleanup", StandardCharsets.UTF_8);
        }
        // The existing lookup converts an invalid configured branch into null; the editor files are still present.
        ReflectionTestUtils.setField(service, "defaultBranch", "invalid branch");
        assertThat(service.getExistingCheckedOutRepositoryByLocalPath(checkout, uri)).isNull();
        assertThatCode(() -> service.deleteLocalRepository(uri)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.deleteLocalRepositoryStrictly(uri)).isInstanceOf(IOException.class).hasMessageContaining(checkout.toString());
        assertThat(Files.readString(answer)).isEqualTo("Answer surviving incomplete cleanup");

        // JGit reports malformed metadata as an unchecked failure, which must also reach retained cleanup.
        ReflectionTestUtils.setField(service, "defaultBranch", "main");
        FileUtils.writeStringToFile(checkout.resolve(".git/config").toFile(), "[core\n", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.deleteLocalRepositoryStrictly(uri)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(answer)).isEqualTo("Answer surviving incomplete cleanup");

        Path quarantine = tempDirectory.resolve("operator-quarantine/strict-student");
        FileUtils.moveDirectory(checkout.toFile(), quarantine.toFile());
        assertThatCode(() -> service.deleteLocalRepositoryStrictly(uri)).doesNotThrowAnyException();
        assertThat(checkout).doesNotExist();
        assertThat(Files.readString(quarantine.resolve("private-editor-answer.txt"))).isEqualTo("Answer surviving incomplete cleanup");
        assertThat(quarantine.resolve(".git/config")).hasContent("[core\n");
    }

    private LocalVCRepositoryUri prepareEditorCheckout(GitService service) throws Exception {
        Path remoteRoot = tempDirectory.resolve("remotes");
        Path bareRepositoryPath = remoteRoot.resolve("LOCKED/locked-student.git");
        ReflectionTestUtils.setField(service, "localVCBasePath", remoteRoot);
        ReflectionTestUtils.setField(service, "repoClonePath", tempDirectory.resolve("checkouts"));
        ReflectionTestUtils.setField(service, "defaultBranch", "main");
        try (Git ignored = Git.init().setBare(true).setInitialBranch("main").setDirectory(bareRepositoryPath.toFile()).call();
                Git source = Git.init().setInitialBranch("main").setDirectory(tempDirectory.resolve("source").toFile()).call()) {
            FileUtils.writeStringToFile(source.getRepository().getWorkTree().toPath().resolve("template.txt").toFile(), "Exercise template", StandardCharsets.UTF_8);
            source.add().addFilepattern("template.txt").call();
            GitService.commit(source).setMessage("Add exercise template").setAuthor("Artemis", "artemis@example.com").call();
            source.push().setRemote(bareRepositoryPath.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
        }
        var uri = new LocalVCRepositoryUri(URI.create("http://localhost"), "LOCKED", "locked-student");
        try (var repository = service.getOrCheckoutRepository(uri, false, false)) {
            assertThat(repository.getLocalPath().resolve("template.txt")).hasContent("Exercise template");
        }
        return uri;
    }

    private static void pushCommitWithManyFiles(Git source, Path bareRepositoryPath, int round) throws Exception {
        Path workTree = source.getRepository().getWorkTree().toPath();
        for (int file = 0; file < 150; file++) {
            FileUtils.writeStringToFile(workTree.resolve("file-" + round + "-" + file + ".txt").toFile(), "round " + round, StandardCharsets.UTF_8);
        }
        source.add().addFilepattern(".").call();
        GitService.commit(source).setMessage("Round " + round).setAuthor("Artemis", "artemis@example.com").call();
        source.push().setRemote(bareRepositoryPath.toUri().toString()).setRefSpecs(new RefSpec("refs/heads/main:refs/heads/main")).call();
    }
}
