package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.eclipse.jgit.api.Git;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.icl.LocalVCLocalCITestService;

/**
 * Shared helpers for LocalVC-backed repository export tests.
 *
 * Focus: seed bare repos into LocalVC structure and wire URIs to exercises.
 * Keep this utility independent of request/MockMvc to allow broad reuse.
 */
public final class RepositoryExportTestUtil {

    private RepositoryExportTestUtil() {
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
        org.apache.commons.io.FileUtils.copyDirectory(srcBareDir, dstBareDir);
        return target;
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
     * Verify that the given ZIP contains the expected paths and that each of them is non-empty.
     * Convenience wrapper used by export tests where only a subset check is needed.
     *
     * @param zipBytes      exported ZIP payload
     * @param expectedPaths set of expected path entries in the ZIP
     */
    public static void assertZipContainsFiles(byte[] zipBytes, Set<String> expectedPaths) throws IOException {
        Set<String> remaining = new java.util.HashSet<>(expectedPaths);

        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
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
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        repo.workingCopyGitRepo.add().addFilepattern(path).call();
        de.tum.cit.aet.artemis.programming.service.GitService.commit(repo.workingCopyGitRepo).setMessage("add " + path).call();
    }

    /**
     * Validate that a returned file map (e.g. from Athena) contains at least the expected subset and that values are non-empty.
     */
    public static void assertFileMapContains(Map<String, String> actual, Map<String, String> expectedSubset) {
        for (Map.Entry<String, String> e : expectedSubset.entrySet()) {
            assertThat(actual).containsKey(e.getKey());
            assertThat(actual.get(e.getKey())).isNotNull().isNotBlank();
        }
    }
}
