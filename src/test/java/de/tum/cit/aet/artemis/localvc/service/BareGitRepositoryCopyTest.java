package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.programming.domain.Repository;

/**
 * Unit tests for the bare-repository copy in {@link BareGitRepositoryService}.
 * <p>
 * A student repository is copied from the template the first time the student starts a programming exercise, and two
 * requests can ask for the same copy at the same time (a double click on "Start exercise"). The copy therefore has to
 * hold two guarantees, both of which these tests pin down: the target path only ever appears once the repository behind
 * it is complete, so that a concurrent request never finds a half-built repository it might mistake for a broken
 * leftover and delete; and a target that already exists is kept rather than created a second time, so that whichever
 * request finishes second still ends up with a usable repository.
 */
class BareGitRepositoryCopyTest {

    private static final URI BASE_URI = URI.create("https://artemis.example.com");

    private static final String DEFAULT_BRANCH = "main";

    private static final String PROJECT_KEY = "ABC";

    @TempDir
    Path baseDir;

    private BareGitRepositoryService bareGitRepositoryService;

    @BeforeEach
    void setUp() {
        bareGitRepositoryService = new BareGitRepositoryService();
        ReflectionTestUtils.setField(bareGitRepositoryService, "localVCBasePath", baseDir);
        // Spring injects this in production, and the copy writes the repository configuration and HEAD from it.
        ReflectionTestUtils.setField(bareGitRepositoryService, "defaultBranch", DEFAULT_BRANCH);
    }

    @Test
    void copyBareRepositoryWithoutHistory_copiesTheStateOfTheSourceBranch() throws Exception {
        seedRepository("abc-exercise", "template");

        try (Repository copy = bareGitRepositoryService.copyBareRepositoryWithoutHistory(uriFor("abc-exercise"), uriFor("abc-student1"), DEFAULT_BRANCH)) {
            assertThat(copy.resolve(Constants.R_HEADS + DEFAULT_BRANCH)).as("the copy has the branch that was copied").isNotNull();
        }

        assertThat(readFileFromBranchHead("abc-student1")).as("the copy carries the content of the source").isEqualTo("template");
        // The configuration is applied to the copy before it is published, so a repository that arrives at its final path is already usable.
        assertThat(headTargetOf("abc-student1")).as("the copy is configured, so HEAD names the default branch").isEqualTo(Constants.R_HEADS + DEFAULT_BRANCH);
    }

    @ParameterizedTest
    @CsvSource({ "main, false", "master, false", "release/exercise, false", "main, true", "master, true", "release/exercise, true" })
    void copyRepository_publishesTheSourceBranchOnTheDefaultBranch(String sourceBranch, boolean withHistory) throws Exception {
        seedRepository("abc-exercise", "initial template", sourceBranch);
        Path sourceClone = baseDir.resolve("source-clone");
        ObjectId sourceHead;
        try (Git source = Git.cloneRepository().setURI(pathFor("abc-exercise").toUri().toString()).setDirectory(sourceClone.toFile()).call()) {
            FileUtils.write(sourceClone.resolve("README.md").toFile(), "updated template", StandardCharsets.UTF_8);
            source.add().addFilepattern(".").call();
            sourceHead = GitService.commit(source).setMessage("Update template").setAuthor("Artemis", "artemis@example.com").setCommitter("Artemis", "artemis@example.com").call()
                    .getId();
            source.push().call();
        }

        try (Repository copy = withHistory ? bareGitRepositoryService.copyBareRepositoryWithHistory(uriFor("abc-exercise"), uriFor("abc-student1"), sourceBranch)
                : bareGitRepositoryService.copyBareRepositoryWithoutHistory(uriFor("abc-exercise"), uriFor("abc-student1"), sourceBranch)) {
            assertThat(copy.resolve(Constants.HEAD)).as("HEAD resolves to the copied commit").isNotNull().isEqualTo(copy.resolve(Constants.R_HEADS + DEFAULT_BRANCH));
            assertThat(copy.getRefDatabase().getRefsByPrefix(Constants.R_HEADS)).extracting(ref -> ref.getName()).containsExactly(Constants.R_HEADS + DEFAULT_BRANCH);
        }

        Path checkout = baseDir.resolve("checkout");
        try (Git clone = Git.cloneRepository().setURI(pathFor("abc-student1").toUri().toString()).setDirectory(checkout.toFile()).call()) {
            assertThat(clone.getRepository().getBranch()).isEqualTo(DEFAULT_BRANCH);
            assertThat(checkout.resolve("README.md")).hasContent("updated template");
            assertThat(clone.log().call()).hasSize(withHistory ? 2 : 1);
            if (withHistory) {
                assertThat(clone.getRepository().resolve(Constants.HEAD)).isEqualTo(sourceHead);
            }
        }
        try (org.eclipse.jgit.lib.Repository source = open("abc-exercise")) {
            assertThat(source.resolve(Constants.R_HEADS + sourceBranch)).as("the source branch stays untouched").isEqualTo(sourceHead);
        }
    }

    @Test
    void copyBareRepositoryWithoutHistory_whenTheCopyFails_leavesNothingAtTheTargetPath() throws Exception {
        seedRepository("abc-exercise", "template");

        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> bareGitRepositoryService.copyBareRepositoryWithoutHistory(uriFor("abc-exercise"), uriFor("abc-student1"), "no-such-branch"))
                .withMessageContaining("no-such-branch");

        // A failed copy that leaves an unborn repository behind is what a concurrent request would delete while the copy
        // that created it is still writing into it.
        assertThat(pathFor("abc-student1")).as("a copy that failed leaves nothing behind at the target path").doesNotExist();
    }

    @Test
    void copyBareRepositoryWithoutHistory_whenTheTargetWasCreatedConcurrently_keepsTheExistingRepository() throws Exception {
        seedRepository("abc-exercise", "template");
        seedRepository("abc-student1", "already copied by the other request");
        ObjectId headBeforeTheCopy = branchHeadOf("abc-student1");

        try (Repository copy = bareGitRepositoryService.copyBareRepositoryWithoutHistory(uriFor("abc-exercise"), uriFor("abc-student1"), DEFAULT_BRANCH)) {
            assertThat(copy.resolve(Constants.R_HEADS + DEFAULT_BRANCH)).as("the repository that is already there is handed back").isEqualTo(headBeforeTheCopy);
        }

        assertThat(readFileFromBranchHead("abc-student1")).as("the repository of the request that finished first is left untouched")
                .isEqualTo("already copied by the other request");
    }

    @Test
    void copyBareRepositoryWithHistory_whenTheTargetWasCreatedConcurrently_keepsTheExistingRepository() throws Exception {
        seedRepository("abc-exercise", "template");
        seedRepository("abc-student1", "already copied by the other request");
        ObjectId headBeforeTheCopy = branchHeadOf("abc-student1");

        try (Repository copy = bareGitRepositoryService.copyBareRepositoryWithHistory(uriFor("abc-exercise"), uriFor("abc-student1"), DEFAULT_BRANCH)) {
            assertThat(copy.resolve(Constants.R_HEADS + DEFAULT_BRANCH)).as("the repository that is already there is handed back").isEqualTo(headBeforeTheCopy);
        }

        assertThat(readFileFromBranchHead("abc-student1")).as("the repository of the request that finished first is left untouched")
                .isEqualTo("already copied by the other request");
    }

    @Test
    void copyBareRepositoryWithoutHistory_leavesNothingBesidesTheRepositoriesInTheProject() throws Exception {
        seedRepository("abc-exercise", "template");

        bareGitRepositoryService.copyBareRepositoryWithoutHistory(uriFor("abc-exercise"), uriFor("abc-student1"), DEFAULT_BRANCH).close();

        try (var entries = Files.list(baseDir.resolve(PROJECT_KEY))) {
            assertThat(entries.map(entry -> entry.getFileName().toString())).as("a completed copy does not leave a working directory behind")
                    .containsExactlyInAnyOrder("abc-exercise.git", "abc-student1.git");
        }
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
        seedRepository(repositorySlug, content, DEFAULT_BRANCH);
    }

    private void seedRepository(String repositorySlug, String content, String branch) throws Exception {
        Path bareRepository = pathFor(repositorySlug);
        Files.createDirectories(bareRepository);
        Git.init().setDirectory(bareRepository.toFile()).setBare(true).setInitialBranch(branch).call().close();
        Path seed = baseDir.resolve("seed-" + repositorySlug);
        try (Git clone = Git.cloneRepository().setURI(bareRepository.toUri().toString()).setDirectory(seed.toFile()).call()) {
            FileUtils.write(seed.resolve("README.md").toFile(), content, StandardCharsets.UTF_8);
            clone.add().addFilepattern(".").call();
            GitService.commit(clone).setMessage("Initial commit").setAuthor("Artemis", "artemis@example.com").setCommitter("Artemis", "artemis@example.com").call();
            clone.push().setRefSpecs(new RefSpec("HEAD:" + Constants.R_HEADS + branch)).call();
        }
        FileUtils.deleteDirectory(seed.toFile());
    }

    private String headTargetOf(String repositorySlug) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = open(repositorySlug)) {
            return repository.getRefDatabase().exactRef(Constants.HEAD).getTarget().getName();
        }
    }

    private ObjectId branchHeadOf(String repositorySlug) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = open(repositorySlug)) {
            return repository.resolve(Constants.R_HEADS + DEFAULT_BRANCH);
        }
    }

    private String readFileFromBranchHead(String repositorySlug) throws IOException {
        try (org.eclipse.jgit.lib.Repository repository = open(repositorySlug); RevWalk walk = new RevWalk(repository)) {
            RevCommit head = walk.parseCommit(repository.resolve(Constants.R_HEADS + DEFAULT_BRANCH));
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, "README.md", head.getTree())) {
                return new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private org.eclipse.jgit.lib.Repository open(String repositorySlug) throws IOException {
        return new FileRepositoryBuilder().setBare().setGitDir(pathFor(repositorySlug).toFile()).setMustExist(true).build();
    }
}
