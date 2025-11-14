package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.icl.LocalVCLocalCITestService;
import de.tum.cit.aet.artemis.programming.service.GitService;

/**
 * Shared helpers for LocalVC-backed repository export tests.
 *
 * Focus: seed bare repos into LocalVC structure and wire URIs to exercises.
 * Keep this utility independent of request/MockMvc to allow broad reuse.
 */
public final class RepositoryExportTestUtil {

    public record BaseRepositories(LocalRepository templateRepository, LocalRepository solutionRepository, LocalRepository testsRepository) {
    }

    private RepositoryExportTestUtil() {
    }

    /**
     * Test cleanup helpers (co-located for convenience).
     */
    public static void deleteDirectoryIfExists(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try {
                FileUtils.deleteDirectory(dir.toFile());
            }
            catch (IOException ignored) {
            }
        }
    }

    public static void resetRepos(LocalRepository... repos) {
        if (repos == null) {
            return;
        }
        for (LocalRepository repo : repos) {
            if (repo != null) {
                try {
                    repo.resetLocalRepo();
                }
                catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Create a new LocalVC-compatible bare repository and optionally initialize content in its working copy.
     *
     * @param localVCLocalCITestService LocalVC helper service
     * @param projectKey                target project key (UPPERCASE in URIs)
     * @param repositorySlug            final repository slug (lowercase + .git on disk)
     * @param contentInitializer        optional content initializer against the created working copy Git handle
     * @return configured LocalRepository (with remote bare repo placed under LocalVC folder structure)
     */
    public static LocalRepository seedBareRepository(LocalVCLocalCITestService localVCLocalCITestService, String projectKey, String repositorySlug,
            Consumer<Git> contentInitializer) throws Exception {
        LocalRepository target = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug);

        if (contentInitializer != null) {
            contentInitializer.accept(target.workingCopyGitRepo);
            // push initialized content so the bare repo has a default branch/history
            target.workingCopyGitRepo.push().setRemote("origin").call();
        }

        return target;
    }

    /**
     * Clone a prepared source bare repo into a new LocalVC-compatible bare repository.
     *
     * @param localVCLocalCITestService LocalVC helper service
     * @param projectKey                target project key
     * @param repositorySlug            target repository slug
     * @param source                    source repository providing the bare repo content
     * @return configured LocalRepository (target) seeded with source bare content
     */
    public static LocalRepository seedLocalVcBareFrom(LocalVCLocalCITestService localVCLocalCITestService, String projectKey, String repositorySlug, LocalRepository source)
            throws Exception {
        LocalRepository target = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug);
        File srcBareDir = source.remoteBareGitRepo.getRepository().getDirectory();
        File dstBareDir = target.remoteBareGitRepoFile;
        FileUtils.copyDirectory(srcBareDir, dstBareDir);
        return target;
    }

    /**
     * Create and wire a LocalVC student repository for a given participation.
     * Does not persist the participation; callers should save it via their repository.
     *
     * @param localVCLocalCITestService LocalVC helper service
     * @param participation             the student participation to seed a repository for
     * @return the configured LocalRepository
     */
    public static LocalRepository seedStudentRepositoryForParticipation(LocalVCLocalCITestService localVCLocalCITestService, ProgrammingExerciseStudentParticipation participation)
            throws Exception {
        String projectKey = participation.getProgrammingExercise().getProjectKey();
        String slug = localVCLocalCITestService.getRepositorySlug(projectKey, participation.getParticipantIdentifier());
        LocalRepository repo = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, slug);
        String uri = localVCLocalCITestService.buildLocalVCUri(null, null, projectKey, slug);
        participation.setRepositoryUri(uri);
        return repo;
    }

    /**
     * Build a LocalVC repository URI for the given project + slug and wire it to the exercise by repository type.
     * Persisting the exercise/participations is left to the caller.
     *
     * @param localVCLocalCITestService LocalVC helper for URI shape
     * @param exercise                  the exercise to update
     * @param type                      repository type (TEMPLATE/SOLUTION/TESTS)
     * @param repositorySlug            slug used in the LocalVC folder/URI
     * @return the URI that was applied
     */
    public static String wireRepositoryToExercise(LocalVCLocalCITestService localVCLocalCITestService, ProgrammingExercise exercise, RepositoryType type, String repositorySlug) {
        String uri = localVCLocalCITestService.buildLocalVCUri(null, null, exercise.getProjectKey(), repositorySlug);
        switch (type) {
            case TEMPLATE -> exercise.setTemplateRepositoryUri(uri);
            case SOLUTION -> exercise.setSolutionRepositoryUri(uri);
            case TESTS -> exercise.setTestRepositoryUri(uri);
            // AUXILIARY/USER are intentionally not handled here; those are wired via their respective entities.
            default -> {
                // no-op; return built URI for caller-side wiring
            }
        }
        return uri;
    }

    /**
     * Creates LocalVC repositories for template, solution, and tests, and wires their URIs on the exercise.
     * Does not persist the exercise; callers should save changes themselves.
     *
     * @param localVCLocalCITestService LocalVC helper service
     * @param exercise                  the exercise whose base repositories should be set up
     */
    public static void createAndWireBaseRepositories(LocalVCLocalCITestService localVCLocalCITestService, ProgrammingExercise exercise) throws Exception {
        createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, exercise);
    }

    /**
     * Variant of {@link #createAndWireBaseRepositories(LocalVCLocalCITestService, ProgrammingExercise)} that also returns
     * the working copy handles for the created template/solution/tests repositories.
     */
    public static BaseRepositories createAndWireBaseRepositoriesWithHandles(LocalVCLocalCITestService localVCLocalCITestService, ProgrammingExercise exercise) throws Exception {
        String projectKey = exercise.getProjectKey();
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        String solutionRepositorySlug = projectKey.toLowerCase() + "-solution";
        String testsRepositorySlug = projectKey.toLowerCase() + "-" + RepositoryType.TESTS.getName();

        wireRepositoryToExercise(localVCLocalCITestService, exercise, RepositoryType.TEMPLATE, templateRepositorySlug);
        wireRepositoryToExercise(localVCLocalCITestService, exercise, RepositoryType.SOLUTION, solutionRepositorySlug);
        wireRepositoryToExercise(localVCLocalCITestService, exercise, RepositoryType.TESTS, testsRepositorySlug);

        LocalRepository templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);
        LocalRepository solutionRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, solutionRepositorySlug);
        LocalRepository testsRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, testsRepositorySlug);

        return new BaseRepositories(templateRepository, solutionRepository, testsRepository);
    }

    /**
     * Verify that the given ZIP contains the expected paths and that each of them is non-empty.
     * Convenience wrapper used by export tests where only a subset check is needed.
     *
     * @param zipBytes      exported ZIP payload
     * @param expectedPaths set of expected path entries in the ZIP
     */
    public static void assertZipContainsFiles(byte[] zipBytes, Set<String> expectedPaths) throws IOException {
        Set<String> remaining = new HashSet<>(expectedPaths);

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (!e.isDirectory() && remaining.contains(e.getName())) {
                    byte[] content = zis.readAllBytes();
                    assertThat(content).isNotNull();
                    assertThat(content.length).isGreaterThan(0);
                    // basic sanity for text files
                    if (e.getName().endsWith(".java") || e.getName().endsWith(".md") || e.getName().endsWith(".xml")) {
                        assertThat(new String(content, StandardCharsets.UTF_8)).isNotBlank();
                    }
                    remaining.remove(e.getName());
                }
            }
        }

        assertThat(remaining).as("missing expected entries in export").isEmpty();
        assertThat(zipBytes.length).isGreaterThan(100);
    }

    /**
     * Convenience helper to write a simple file into a repo working copy and commit it.
     * Caller is responsible for pushing if needed.
     *
     * @param repo     the repository to modify
     * @param path     relative path inside working copy
     * @param contents text contents
     */
    public static void writeAndCommit(LocalRepository repo, String path, String contents) throws Exception {
        var file = repo.workingCopyGitRepoFile.toPath().resolve(path);
        FileUtils.forceMkdirParent(file.toFile());
        FileUtils.writeStringToFile(file.toFile(), contents, StandardCharsets.UTF_8);
        repo.workingCopyGitRepo.add().addFilepattern(path).call();
        GitService.commit(repo.workingCopyGitRepo).setMessage("add " + path).call();
    }

    /**
     * Writes a set of files into the repo working copy, commits them with the provided message, and pushes to origin.
     * Returns the created commit for callers that need the hash.
     */
    public static RevCommit writeFilesAndPush(LocalRepository repo, Map<String, String> files, String message) throws Exception {
        for (Map.Entry<String, String> e : files.entrySet()) {
            var p = repo.workingCopyGitRepoFile.toPath().resolve(e.getKey());
            FileUtils.forceMkdirParent(p.toFile());
            FileUtils.writeStringToFile(p.toFile(), e.getValue(), StandardCharsets.UTF_8);
        }
        repo.workingCopyGitRepo.add().addFilepattern(".").call();
        var commit = GitService.commit(repo.workingCopyGitRepo).setMessage(message).call();
        repo.workingCopyGitRepo.push().setRemote("origin").call();
        return commit;
    }

    /**
     * Returns the latest commit hash reachable from HEAD in the given working copy repository.
     *
     * @param repo LocalRepository whose working copy should be inspected
     * @return ObjectId of the latest commit
     */
    public static ObjectId getLatestCommit(LocalRepository repo) throws GitAPIException {
        Iterator<RevCommit> commits = repo.workingCopyGitRepo.log().setMaxCount(1).call().iterator();
        if (!commits.hasNext()) {
            throw new IllegalStateException("Repository has no commits yet");
        }
        return commits.next().getId();
    }

    /**
     * Creates and returns a working copy repository handle for the template repo of the given exercise.
     * Assumes base repos have been wired already (use createAndWireBaseRepositories beforehand if needed).
     */
    public static LocalRepository createTemplateWorkingCopy(LocalVCLocalCITestService localVCLocalCITestService, ProgrammingExercise exercise)
            throws GitAPIException, IOException, URISyntaxException {
        String projectKey = exercise.getProjectKey();
        String templateSlug = projectKey.toLowerCase() + "-exercise";
        return localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateSlug);
    }

    // ===========================================================================
    // Utilities for reducing code duplication across test suites
    // ===========================================================================

    /**
     * Delete a student's bare repository from the LocalVC file system.
     * Handles path construction: projectKey (uppercase) + "/" + repositorySlug (lowercase) + ".git"
     *
     * @param exercise        the programming exercise
     * @param username        the student username (used to derive slug)
     * @param localVCBasePath the base path for LocalVC repositories
     * @throws IOException if deletion fails
     */
    public static void deleteStudentBareRepo(ProgrammingExercise exercise, String username, Path localVCBasePath) throws IOException {
        String projectKey = exercise.getProjectKey().toUpperCase();
        String slug = (exercise.getShortName() + "-" + username).toLowerCase();
        Path bareRepoPath = localVCBasePath.resolve(projectKey).resolve(slug + ".git");
        if (Files.exists(bareRepoPath)) {
            FileUtils.deleteDirectory(bareRepoPath.toFile());
        }
    }

    /**
     * Safely delete a directory, ignoring errors if it doesn't exist.
     * Consolidates scattered FileUtils.deleteDirectory calls with consistent exception handling.
     *
     * @param directory the directory to delete
     */
    public static void safeDeleteDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try {
            FileUtils.deleteDirectory(directory.toFile());
        }
        catch (IOException e) {
            // Log and continue - cleanup failures shouldn't break tests
            // Silent failure acceptable in test cleanup
        }
    }

    /**
     * Delete a LocalVC project if it exists (project key → all repositories).
     * Moved from ProgrammingExerciseTestService to consolidate usage.
     *
     * @param localVCBasePath the base path for LocalVC repositories
     * @param projectKey      the project key (will be uppercased)
     * @throws IOException if deletion fails
     */
    public static void deleteLocalVcProjectIfPresent(Path localVCBasePath, String projectKey) throws IOException {
        String normalizedProjectKey = projectKey == null ? null : projectKey.toUpperCase();
        Path projectPath = localVCBasePath.resolve(normalizedProjectKey);
        if (Files.exists(projectPath)) {
            FileUtils.deleteDirectory(projectPath.toFile());
        }
    }
}
