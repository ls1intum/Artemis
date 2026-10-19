package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the repository copy in {@link LocalVCService}, i.e. the step that provisions the student repository when a programming exercise is started.
 * <p>
 * Two requests can reach this step for the same participation at the same time: the "Start exercise" button has no double-click guard, and while no participation
 * exists yet both requests read the same uninitialized state and both go on to create the repository. Whatever the interleaving, both have to end up with the same
 * usable repository rather than one of them destroying the other's work (issue #13870).
 */
class LocalVCRepositoryCopyTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    private static final String DEFAULT_BRANCH = "main";

    private static final String PROJECT_KEY = "ABC";

    @TempDir
    Path baseDir;

    private LocalVCService localVCService;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        BareGitRepositoryService bareGitRepositoryService = new BareGitRepositoryService();
        ReflectionTestUtils.setField(bareGitRepositoryService, "localVCBasePath", baseDir);
        // Spring injects this in production, and the copy writes the repository configuration and HEAD from it.
        ReflectionTestUtils.setField(bareGitRepositoryService, "defaultBranch", DEFAULT_BRANCH);
        // The copy under test touches neither the URI service nor any repository, so passing null for them keeps the test free of a Spring context.
        localVCService = new LocalVCService(null, bareGitRepositoryService, null, null, null, null);
        ReflectionTestUtils.setField(localVCService, "localVCBasePath", baseDir);
        ReflectionTestUtils.setField(localVCService, "localVCBaseUri", BASE_URI);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void copyRepositoryWithoutHistory_startedTwiceConcurrently_givesBothRequestsTheSameRepository() throws Exception {
        seedRepository("abc-exercise", "template");
        CyclicBarrier bothRequestsReady = new CyclicBarrier(2);
        Callable<LocalVCRepositoryUri> startExercise = () -> {
            bothRequestsReady.await(10, TimeUnit.SECONDS);
            return localVCService.copyRepositoryWithoutHistory(PROJECT_KEY, "abc-exercise", DEFAULT_BRANCH, PROJECT_KEY, "student1", null);
        };

        List<Future<LocalVCRepositoryUri>> responses = executor.invokeAll(List.of(startExercise, startExercise));

        for (Future<LocalVCRepositoryUri> response : responses) {
            // A failed copy surfaces here as an ExecutionException and fails the test, which is what the second request used to run into.
            assertThat(response.get().toString()).as("both requests are told about the same repository").isEqualTo(uriFor("abc-student1").toString());
        }
        assertThat(readFileFromBranchHead("abc-student1")).as("the repository both requests created is complete").isEqualTo("template");
    }

    @Test
    void copyRepositoryWithoutHistory_withARepositoryThatWasAlreadyCopied_keepsIt() throws Exception {
        seedRepository("abc-exercise", "template");
        seedRepository("abc-student1", "work the student already pushed");

        LocalVCRepositoryUri repositoryUri = localVCService.copyRepositoryWithoutHistory(PROJECT_KEY, "abc-exercise", DEFAULT_BRANCH, PROJECT_KEY, "student1", null);

        assertThat(repositoryUri.toString()).as("the repository that is already there is handed back").isEqualTo(uriFor("abc-student1").toString());
        assertThat(readFileFromBranchHead("abc-student1")).as("a repository that was already copied is never copied over").isEqualTo("work the student already pushed");
    }

    @Test
    void copyRepositoryWithoutHistory_withAnUnbornRepositoryLeftBehind_replacesIt() throws Exception {
        // A repository that was created but never received a branch is the skeleton a previously failed copy leaves behind. It holds no student work and cannot be
        // served, so it is deleted and copied again.
        seedRepository("abc-exercise", "template");
        Path unborn = pathFor("abc-student1");
        Files.createDirectories(unborn);
        Git.init().setDirectory(unborn.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().close();

        localVCService.copyRepositoryWithoutHistory(PROJECT_KEY, "abc-exercise", DEFAULT_BRANCH, PROJECT_KEY, "student1", null);

        assertThat(readFileFromBranchHead("abc-student1")).as("the broken leftover is replaced by a working copy of the template").isEqualTo("template");
    }

    @Test
    void copyRepositoryWithoutHistory_repairingTheSameBrokenRepositoryTwiceConcurrently_keepsTheRepairedOne() throws Exception {
        // Both requests find the same unborn leftover and both set out to repair it. Repairing by deleting it where it lies would let the slower request delete the
        // repository the faster one has published at that path in the meantime; moving it aside has one winner, so the repaired repository survives.
        seedRepository("abc-exercise", "template");
        Path unborn = pathFor("abc-student1");
        Files.createDirectories(unborn);
        Git.init().setDirectory(unborn.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().close();
        CyclicBarrier bothRequestsReady = new CyclicBarrier(2);
        Callable<LocalVCRepositoryUri> startExercise = () -> {
            bothRequestsReady.await(10, TimeUnit.SECONDS);
            return localVCService.copyRepositoryWithoutHistory(PROJECT_KEY, "abc-exercise", DEFAULT_BRANCH, PROJECT_KEY, "student1", null);
        };

        List<Future<LocalVCRepositoryUri>> responses = executor.invokeAll(List.of(startExercise, startExercise));

        for (Future<LocalVCRepositoryUri> response : responses) {
            assertThat(response.get().toString()).as("both requests are told about the same repository").isEqualTo(uriFor("abc-student1").toString());
        }
        assertThat(readFileFromBranchHead("abc-student1")).as("the repository that replaced the leftover is still there").isEqualTo("template");
    }

    @Test
    void copyRepositoryWithoutHistory_withABrokenRepository_leavesNoLeftoverBesideTheRepairedOne() throws Exception {
        seedRepository("abc-exercise", "template");
        Path corrupt = pathFor("abc-student1");
        Files.createDirectories(corrupt.resolve("refs").resolve("heads"));
        Files.createDirectories(corrupt.resolve("objects"));

        localVCService.copyRepositoryWithoutHistory(PROJECT_KEY, "abc-exercise", DEFAULT_BRANCH, PROJECT_KEY, "student1", null);

        try (var entries = Files.list(baseDir.resolve(PROJECT_KEY))) {
            assertThat(entries.map(entry -> entry.getFileName().toString())).as("the repository moved aside is removed rather than left next to the repaired one")
                    .containsExactlyInAnyOrder("abc-exercise.git", "abc-student1.git");
        }
        assertThat(readFileFromBranchHead("abc-student1")).as("the corrupt leftover is replaced by a working copy of the template").isEqualTo("template");
    }

    private LocalVCRepositoryUri uriFor(String repositorySlug) {
        return new LocalVCRepositoryUri(BASE_URI, PROJECT_KEY, repositorySlug);
    }

    private Path pathFor(String repositorySlug) {
        return baseDir.resolve(PROJECT_KEY).resolve(repositorySlug + ".git");
    }

    /**
     * Creates a bare repository with a single commit on {@value DEFAULT_BRANCH} that contains a README with the given content.
     * <p>
     * A bare repository only receives a branch once something is pushed to it, so the commit is pushed from a throwaway clone that is deleted again afterwards.
     */
    private void seedRepository(String repositorySlug, String content) throws Exception {
        Path bareRepository = pathFor(repositorySlug);
        Files.createDirectories(bareRepository);
        Git.init().setDirectory(bareRepository.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call().close();
        Path seed = baseDir.resolve("seed-" + repositorySlug);
        try (Git clone = Git.cloneRepository().setURI(bareRepository.toUri().toString()).setDirectory(seed.toFile()).call()) {
            FileUtils.write(seed.resolve("README.md").toFile(), content, StandardCharsets.UTF_8);
            clone.add().addFilepattern(".").call();
            GitService.commit(clone).setMessage("Initial commit").setAuthor("Artemis", "artemis@example.com").setCommitter("Artemis", "artemis@example.com").call();
            clone.push().setRefSpecs(new RefSpec("HEAD:" + Constants.R_HEADS + DEFAULT_BRANCH)).call();
        }
        FileUtils.deleteDirectory(seed.toFile());
    }

    private String readFileFromBranchHead(String repositorySlug) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = new FileRepositoryBuilder().setBare().setGitDir(pathFor(repositorySlug).toFile()).setMustExist(true).build();
                RevWalk walk = new RevWalk(repository)) {
            RevCommit head = walk.parseCommit(repository.resolve(Constants.R_HEADS + DEFAULT_BRANCH));
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, "README.md", head.getTree())) {
                return new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
            }
        }
    }
}
