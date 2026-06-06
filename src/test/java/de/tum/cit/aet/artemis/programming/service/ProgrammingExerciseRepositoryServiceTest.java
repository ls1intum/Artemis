package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

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

    @Test
    void clearTestsSampleSources_java_removesSampleTestSourcesAndOracle_butKeepsHarness() throws Exception {
        // The materialized Java tests repo: build/report harness + SCA config (KEEP) alongside the SortingExample sample behaviour test, an Ares structure-oracle class, and the
        // sample test.json (STRIP — the agent authors its own tests and the oracle is regenerated from the classpath per exercise, so neither should linger as a sample).
        Path repoPath = tempDir.resolve("tests");
        FileUtils.writeStringToFile(repoPath.resolve("pom.xml").toFile(), "<project/>", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("stagePom.xml").toFile(), "<project/>", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("staticCodeAnalysisConfig/checkstyle-configuration.xml").toFile(), "<module/>", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/de/test/SortingExampleBehaviorTest.java").toFile(), "class SortingExampleBehaviorTest {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/de/test/ClassTest.java").toFile(), "class ClassTest {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/de/test/test.json").toFile(), "[]", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        User user = new User();

        repositorySourceCleaner.clearTestsSampleSources(ProgrammingExerciseRepositorySourceCleanupService.testsSampleFileNamesByLanguage().get(ProgrammingLanguage.JAVA),
                repository, user);

        // The harness + SCA config survive so the repo stays buildable and gradable.
        assertThat(repoPath.resolve("pom.xml")).exists();
        assertThat(repoPath.resolve("stagePom.xml")).exists();
        assertThat(repoPath.resolve("staticCodeAnalysisConfig/checkstyle-configuration.xml")).exists();
        // The sample test sources and the sample structure oracle are gone.
        assertThat(repoPath.resolve("test/de/test/SortingExampleBehaviorTest.java")).doesNotExist();
        assertThat(repoPath.resolve("test/de/test/ClassTest.java")).doesNotExist();
        assertThat(repoPath.resolve("test/de/test/test.json")).doesNotExist();
        verify(gitService).commitAndPush(eq(repository), eq("Cleared tests sample sources for AI generation"), eq(true), same(user));
    }

    @Test
    void clearRepositoriesForAiGeneration_unsupportedLanguage_keepsTheTestsSampleIntact() throws Exception {
        // Haskell is NOT yet supported for tests-stripping (its .cabal NAMES the sample test module, so it needs a stub, not a deletion), so its tests repo must be left untouched
        // while template/solution are still cleared. This pins the gating: a language is only stripped once it has a validated manifest.
        Path templatePath = tempDir.resolve("template");
        FileUtils.writeStringToFile(templatePath.resolve("src/Exercise.hs").toFile(), "module Exercise where", StandardCharsets.UTF_8);
        Path solutionPath = tempDir.resolve("solution");
        FileUtils.writeStringToFile(solutionPath.resolve("src/Exercise.hs").toFile(), "module Exercise where\nf = 1", StandardCharsets.UTF_8);
        Path testsPath = tempDir.resolve("tests");
        FileUtils.writeStringToFile(testsPath.resolve("test/Test.hs").toFile(), "module Test where", StandardCharsets.UTF_8);

        Repository templateRepo = mockRepository(templatePath);
        Repository solutionRepo = mockRepository(solutionPath);
        Repository testsRepo = mockRepository(testsPath);

        repositorySourceCleaner.clearRepositoriesForAiGeneration(ProgrammingLanguage.HASKELL, templateRepo, solutionRepo, testsRepo, new User());

        // Template/solution sources are cleared, but the tests repo's sample is preserved and never committed against.
        assertThat(templatePath.resolve("src/Exercise.hs")).doesNotExist();
        assertThat(solutionPath.resolve("src/Exercise.hs")).doesNotExist();
        assertThat(testsPath.resolve("test/Test.hs")).as("an unsupported language keeps its tests sample intact").exists();
        verify(gitService, never()).commitAndPush(same(testsRepo), anyString(), anyBoolean(), any());
    }

    @Test
    void clearTestsSampleSources_python_removesSampleTestsButKeepsHarnessInitAndConfig() throws Exception {
        // Python's harness sources share the .py extension with the sample, so a NAMED strip is essential: delete the example behaviour/structural tests + the structural helper,
        // but
        // keep every __init__.py (the package markers the harness imports) and the SCA config.
        Path repoPath = tempDir.resolve("tests");
        FileUtils.writeStringToFile(repoPath.resolve("test/__init__.py").toFile(), "", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/behavior/__init__.py").toFile(), "", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/behavior/behavior_test.py").toFile(), "def test_x(): pass", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/structural/structural_test.py").toFile(), "import structural_helpers", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/structural/structural_helpers.py").toFile(), "def helper(): pass", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("staticCodeAnalysis/ruff-student.toml").toFile(), "[tool.ruff]", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        repositorySourceCleaner.clearTestsSampleSources(ProgrammingExerciseRepositorySourceCleanupService.testsSampleFileNamesByLanguage().get(ProgrammingLanguage.PYTHON),
                repository, new User());

        assertThat(repoPath.resolve("test/__init__.py")).as("package markers are harness, kept").exists();
        assertThat(repoPath.resolve("test/behavior/__init__.py")).exists();
        assertThat(repoPath.resolve("staticCodeAnalysis/ruff-student.toml")).as("SCA config kept").exists();
        assertThat(repoPath.resolve("test/behavior/behavior_test.py")).doesNotExist();
        assertThat(repoPath.resolve("test/structural/structural_test.py")).doesNotExist();
        assertThat(repoPath.resolve("test/structural/structural_helpers.py")).doesNotExist();
    }

    @Test
    void clearTestsSampleSources_ruby_keepsHarnessHelperAndAssignmentPath() throws Exception {
        // Ruby's test_helper.rb + assignment_path.rb are .rb HARNESS the build requires; only the two sample test_*.rb files are stripped.
        Path repoPath = tempDir.resolve("tests");
        FileUtils.writeStringToFile(repoPath.resolve("assignment_path.rb").toFile(), "ASSIGNMENT='..'", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/test_helper.rb").toFile(), "require 'minitest'", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/test_behavior.rb").toFile(), "require_relative 'test_helper'", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/test_structural.rb").toFile(), "require_relative 'test_helper'", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        repositorySourceCleaner.clearTestsSampleSources(ProgrammingExerciseRepositorySourceCleanupService.testsSampleFileNamesByLanguage().get(ProgrammingLanguage.RUBY),
                repository, new User());

        assertThat(repoPath.resolve("assignment_path.rb")).as("harness require'd by the Rakefile, kept").exists();
        assertThat(repoPath.resolve("test/test_helper.rb")).as("harness require'd by every test, kept").exists();
        assertThat(repoPath.resolve("test/test_behavior.rb")).doesNotExist();
        assertThat(repoPath.resolve("test/test_structural.rb")).doesNotExist();
    }

    @Test
    void clearTestsSampleSources_kotlin_stripsTheJavaSampleNotKotlin() throws Exception {
        // The Kotlin tests repo is a JVM/Ares harness whose test sources are .java (NOT .kt). The named strip removes the .java sample + test.json; an extension sweep on .kt would
        // have missed it entirely.
        Path repoPath = tempDir.resolve("tests");
        FileUtils.writeStringToFile(repoPath.resolve("pom.xml").toFile(), "<project/>", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/de/test/SortingExampleBehaviorTest.java").toFile(), "class X {}", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("test/de/test/test.json").toFile(), "[]", StandardCharsets.UTF_8);

        Repository repository = mockRepository(repoPath);
        repositorySourceCleaner.clearTestsSampleSources(ProgrammingExerciseRepositorySourceCleanupService.testsSampleFileNamesByLanguage().get(ProgrammingLanguage.KOTLIN),
                repository, new User());

        assertThat(repoPath.resolve("pom.xml")).exists();
        assertThat(repoPath.resolve("test/de/test/SortingExampleBehaviorTest.java")).doesNotExist();
        assertThat(repoPath.resolve("test/de/test/test.json")).doesNotExist();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("supportedTestsStripLanguages")
    void everyConfiguredSampleFile_existsInTheLanguageTemplate_failClosedAgainstDrift(ProgrammingLanguage language, java.util.Set<String> sampleNames) throws Exception {
        // Fail-closed manifest guard: every configured sample file name must still exist in the language's tests template. If an upstream template change renames or removes a
        // sample
        // file, the configured strip silently becomes a no-op (the sample would survive into generated exercises) — this catches that so the manifest is updated deliberately.
        Path languageTemplate = Path.of("src/main/resources/templates", language.name().toLowerCase(java.util.Locale.ROOT));
        final java.util.Set<String> presentNames;
        try (Stream<Path> files = Files.walk(languageTemplate)) {
            presentNames = files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
        assertThat(presentNames).as("%s tests template must contain every configured sample file (rename/removal drift)", language).containsAll(sampleNames);
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> supportedTestsStripLanguages() {
        return ProgrammingExerciseRepositorySourceCleanupService.testsSampleFileNamesByLanguage().entrySet().stream()
                .map(entry -> org.junit.jupiter.params.provider.Arguments.of(entry.getKey(), entry.getValue()));
    }

    @Test
    void javaTestsTemplate_containsOnlyTheKnownSampleSources_soTheStripStaysComplete() throws Exception {
        // Fail-closed manifest: the Java tests template's ONLY .java/test.json files are the sample behaviour test + the four Ares structure-oracle classes + the sample oracle —
        // all
        // legitimate STRIP targets. If a future template change introduces a NEW .java/test.json (e.g. a harness class the build depends on), this fails so it is consciously
        // classified before the blanket "strip .java + test.json from the Java tests repo" rule would silently delete a file the build needs.
        Path javaTestTemplate = Path.of("src/main/resources/templates/java/test");
        final java.util.List<String> testSourceFileNames;
        try (Stream<Path> files = Files.walk(javaTestTemplate)) {
            testSourceFileNames = files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".java") || name.equals("test.json"))
                    .sorted().toList();
        }
        assertThat(testSourceFileNames).containsExactlyInAnyOrder("SortingExampleBehaviorTest.java", "AttributeTest.java", "ClassTest.java", "ConstructorTest.java",
                "MethodTest.java", "test.json");
    }

    private Repository mockRepository(Path repoPath) {
        Repository repository = mock(Repository.class);
        // Lenient: a repository that is gated out (e.g. a non-allowlisted language's tests repo) is never touched, so neither stub is exercised.
        lenient().when(repository.getLocalPath()).thenReturn(repoPath);
        lenient().when(repository.getRemoteRepositoryUri()).thenReturn(new LocalVCRepositoryUri("https://example.com/git/TEST/TEST-exercise.git"));
        return repository;
    }
}
