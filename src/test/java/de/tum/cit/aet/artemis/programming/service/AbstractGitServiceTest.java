package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.localvc.service.AbstractGitService;

class AbstractGitServiceTest {

    private static final String DEFAULT_BRANCH = "main";

    @TempDir
    private Path tempPath;

    @Test
    void linkRepositoryForExistingGitReturnsOpenRepository() throws Exception {
        Path repositoryPath = tempPath.resolve("repository");
        try (Git ignored = Git.init().setDirectory(repositoryPath.toFile()).setInitialBranch(DEFAULT_BRANCH).call()) {
            // Git instance automatically closed by try-with-resources
        }

        try (var repository = AbstractGitService.linkRepositoryForExistingGit(repositoryPath, null, DEFAULT_BRANCH, false, false)) {
            assertThat(repository.getLocalPath()).isEqualTo(repositoryPath.normalize());
            assertThat(repositoryUseCount(repository)).isEqualTo(1);
        }
    }

    @Test
    void getExistingBareRepositoryReturnsOpenRepository() throws Exception {
        Path repositoryPath = tempPath.resolve("repository.git");
        try (Git ignored = Git.init().setDirectory(repositoryPath.toFile()).setBare(true).setInitialBranch(DEFAULT_BRANCH).call()) {
            // Git instance automatically closed by try-with-resources
        }

        try (var repository = AbstractGitService.getExistingBareRepository(repositoryPath, null, DEFAULT_BRANCH)) {
            assertThat(repository.getLocalPath()).isEqualTo(repositoryPath.normalize());
            assertThat(repository.isBare()).isTrue();
            assertThat(repositoryUseCount(repository)).isEqualTo(1);
        }
    }

    private static int repositoryUseCount(org.eclipse.jgit.lib.Repository repository) throws ReflectiveOperationException {
        Field useCount = org.eclipse.jgit.lib.Repository.class.getDeclaredField("useCnt");
        useCount.setAccessible(true);
        return ((AtomicInteger) useCount.get(repository)).get();
    }
}
