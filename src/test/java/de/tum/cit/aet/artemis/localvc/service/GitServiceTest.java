package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

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
            pushCommitWithManyFiles(source, bareRepositoryPath, 0);
            service.getOrCheckoutRepository(repositoryUri, repositoryUri, localPath, true, "main", false).close();

            for (int round = 1; round <= rounds; round++) {
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
