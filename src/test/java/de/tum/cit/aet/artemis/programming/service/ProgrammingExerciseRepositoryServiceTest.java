package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseRepositoryServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private GitService gitService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ResourceLoaderService resourceLoaderService;

    private ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    @BeforeEach
    void setUp() {
        programmingExerciseRepositoryService = new ProgrammingExerciseRepositoryService(gitService, userRepository, resourceLoaderService, Optional.empty());
    }

    @Test
    void clearRepositorySources_removesSourcesAndAddsGitkeep() throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve("src"));
        FileUtils.writeStringToFile(repoPath.resolve("src/Main.java").toFile(), "class Main {}", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        programmingExerciseRepositoryService.clearRepositorySources(repository, RepositoryType.TEMPLATE, user);

        try (var files = Files.list(repoPath.resolve("src"))) {
            var filenames = files.map(path -> path.getFileName().toString()).sorted().toList();
            assertThat(filenames).containsExactly(".gitkeep");
        }

        verify(gitService).stageAllChanges(repository);
        verify(gitService).commitAndPush(eq(repository), eq("Cleared template sources for AI generation"), eq(true), same(user));
    }

    @Test
    void clearRepositorySources_skipsWhenNoSrcDirectory() throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        assertThatThrownBy(() -> programmingExerciseRepositoryService.clearRepositorySources(repository, RepositoryType.SOLUTION, user)).isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("no source directory found");

        verifyNoInteractions(gitService);
    }

    @Test
    void clearRepositorySources_testsRepository_removesTestDirectoriesAndAddsGitkeep() throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve("test"));
        Files.createDirectories(repoPath.resolve("behavior").resolve("test"));
        Files.createDirectories(repoPath.resolve("structural").resolve("test"));
        FileUtils.writeStringToFile(repoPath.resolve("test/SomeTest.java").toFile(), "class SomeTest {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("behavior/test/BehaviorTest.java").toFile(), "class BehaviorTest {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("structural/test/StructuralTest.java").toFile(), "class StructuralTest {}", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        programmingExerciseRepositoryService.clearRepositorySources(repository, RepositoryType.TESTS, user);

        try (var testFiles = Files.list(repoPath.resolve("test"));
                var behaviorTestFiles = Files.list(repoPath.resolve("behavior").resolve("test"));
                var structuralTestFiles = Files.list(repoPath.resolve("structural").resolve("test"))) {
            assertThat(testFiles.map(path -> path.getFileName().toString()).toList()).containsExactly(".gitkeep");
            assertThat(behaviorTestFiles.map(path -> path.getFileName().toString()).toList()).containsExactly(".gitkeep");
            assertThat(structuralTestFiles.map(path -> path.getFileName().toString()).toList()).containsExactly(".gitkeep");
        }

        verify(gitService).stageAllChanges(repository);
        verify(gitService).commitAndPush(eq(repository), eq("Cleared tests sources for AI generation"), eq(true), same(user));
    }

    @Test
    void clearRepositorySources_testsRepository_removesTestsuiteDirectoryAndAddsGitkeep() throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve("testsuite").resolve("sample.tests"));
        FileUtils.writeStringToFile(repoPath.resolve("testsuite/sample.tests/test.exp").toFile(), "pass", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        programmingExerciseRepositoryService.clearRepositorySources(repository, RepositoryType.TESTS, user);

        try (var testsuiteFiles = Files.list(repoPath.resolve("testsuite"))) {
            assertThat(testsuiteFiles.map(path -> path.getFileName().toString()).toList()).containsExactly(".gitkeep");
        }

        verify(gitService).stageAllChanges(repository);
        verify(gitService).commitAndPush(eq(repository), eq("Cleared tests sources for AI generation"), eq(true), same(user));
    }

    private Repository mockRepository(Path repoPath) {
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(repoPath);
        lenient().when(repository.getRemoteRepositoryUri()).thenReturn(new LocalVCRepositoryUri("https://example.com/git/TEST/TEST-exercise.git"));
        return repository;
    }
}
