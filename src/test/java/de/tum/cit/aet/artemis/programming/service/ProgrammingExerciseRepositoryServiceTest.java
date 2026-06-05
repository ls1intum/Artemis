package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
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

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseRepositoryServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private GitService gitService;

    private ProgrammingExerciseRepositorySourceCleanupService repositorySourceCleaner;

    @BeforeEach
    void setUp() {
        repositorySourceCleaner = new ProgrammingExerciseRepositorySourceCleanupService(gitService);
    }

    @Test
    void clearRepositorySources_removesConventionalSrcAndAddsGitkeep() throws Exception {
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve("src"));
        FileUtils.writeStringToFile(repoPath.resolve("src/Main.java").toFile(), "class Main {}", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        repositorySourceCleaner.clearRepositorySources(ProgrammingLanguage.JAVA, repository, RepositoryType.TEMPLATE, user);

        try (var files = Files.list(repoPath.resolve("src"))) {
            assertThat(files.map(path -> path.getFileName().toString()).sorted().toList()).containsExactly(".gitkeep");
        }
        verify(gitService).stageAllChanges(repository);
        verify(gitService).commitAndPush(eq(repository), eq("Cleared template sources for AI generation"), eq(true), same(user));
    }

    @Test
    void clearRepositorySources_doesNothingWhenNoSourceFilesOfTheLanguageExist() throws Exception {
        // A repository with no conventional source directory and no language source files (only build manifests/dotfiles) has nothing to clear: no throw, no commit.
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        FileUtils.writeStringToFile(repoPath.resolve(".gitignore").toFile(), "target/", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);

        repositorySourceCleaner.clearRepositorySources(ProgrammingLanguage.JAVA, repository, RepositoryType.SOLUTION, new User());

        verifyNoInteractions(gitService);
    }

    @Test
    void clearRepositorySources_rootSourceLanguage_deletesLooseSourcesButKeepsBuildManifest() throws Exception {
        // Go keeps sources at the repository root; the loose-source sweep must delete the .go files but keep go.mod so the scaffold stays buildable.
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve("client"));
        FileUtils.writeStringToFile(repoPath.resolve("bubblesort.go").toFile(), "package x", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("client/client.go").toFile(), "package main", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("go.mod").toFile(), "module artemis/x", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve(".gitignore").toFile(), "bin/", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        repositorySourceCleaner.clearRepositorySources(ProgrammingLanguage.GO, repository, RepositoryType.SOLUTION, user);

        assertThat(repoPath.resolve("go.mod")).exists();
        assertThat(repoPath.resolve(".gitignore")).exists();
        assertThat(repoPath.resolve("bubblesort.go")).doesNotExist();
        assertThat(repoPath.resolve("client/client.go")).doesNotExist();
        verify(gitService).commitAndPush(eq(repository), eq("Cleared solution sources for AI generation"), eq(true), same(user));
    }

    @Test
    void clearRepositorySources_swift_clearsSourcesDirectoryButKeepsPackageSwiftManifest() throws Exception {
        // Swift sources live under Sources/; Package.swift is a manifest that must survive even though it has a .swift extension (it is on the keep-list).
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve("Sources/AppLib"));
        FileUtils.writeStringToFile(repoPath.resolve("Sources/AppLib/Sorter.swift").toFile(), "struct Sorter {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("Package.swift").toFile(), "// swift-tools-version:5.2", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        repositorySourceCleaner.clearRepositorySources(ProgrammingLanguage.SWIFT, repository, RepositoryType.SOLUTION, user);

        assertThat(repoPath.resolve("Package.swift")).exists();
        assertThat(repoPath.resolve("Sources/AppLib/Sorter.swift")).doesNotExist();
        verify(gitService).commitAndPush(eq(repository), eq("Cleared solution sources for AI generation"), eq(true), same(user));
    }

    @Test
    void clearRepositorySources_keepsBuildManifestInsideSourceDirectory_ocamlDune() throws Exception {
        // OCaml keeps a `dune` build file INSIDE src/ next to the sources; clearing must remove the .ml/.mli sources but KEEP src/dune (a blunt "empty the src/ dir" would break
        // the build).
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath.resolve("src"));
        FileUtils.writeStringToFile(repoPath.resolve("src/assignment.ml").toFile(), "let add a b = a + b", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("src/assignment.mli").toFile(), "val add : int -> int -> int", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("src/dune").toFile(), "(library (name assignment))", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        repositorySourceCleaner.clearRepositorySources(ProgrammingLanguage.OCAML, repository, RepositoryType.SOLUTION, user);

        assertThat(repoPath.resolve("src/dune")).as("the in-directory build manifest is kept").exists();
        assertThat(repoPath.resolve("src/assignment.ml")).doesNotExist();
        assertThat(repoPath.resolve("src/assignment.mli")).doesNotExist();
        verify(gitService).commitAndPush(eq(repository), eq("Cleared solution sources for AI generation"), eq(true), same(user));
    }

    @Test
    void clearRepositorySources_emptiedRepository_getsRootGitkeepFloor() throws Exception {
        // A C/FACT solution is a single root exercise.c with no manifest; after clearing, a root .gitkeep keeps the repo a valid clone target.
        Path repoPath = tempDir.resolve("repo");
        Files.createDirectories(repoPath);
        FileUtils.writeStringToFile(repoPath.resolve("exercise.c").toFile(), "int main(){return 0;}", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);

        repositorySourceCleaner.clearRepositorySources(ProgrammingLanguage.C, repository, RepositoryType.SOLUTION, new User());

        assertThat(repoPath.resolve("exercise.c")).doesNotExist();
        assertThat(repoPath.resolve(".gitkeep")).exists();
    }

    private Repository mockRepository(Path repoPath) {
        Repository repository = mock(Repository.class);
        when(repository.getLocalPath()).thenReturn(repoPath);
        lenient().when(repository.getRemoteRepositoryUri()).thenReturn(new LocalVCRepositoryUri("https://example.com/git/TEST/TEST-exercise.git"));
        return repository;
    }
}
