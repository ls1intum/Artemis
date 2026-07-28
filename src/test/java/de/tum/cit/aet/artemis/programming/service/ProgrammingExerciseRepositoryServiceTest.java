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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

@ExtendWith(MockitoExtension.class)
class ProgrammingExerciseRepositoryServiceTest {

    private static final String MIRROR_URL = "https://reposilite.aet.cit.tum.de/releases";

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

    @Test
    void setupBuildToolProjectFile_keepsTheMirrorDeclarationsWhenAMirrorIsConfigured() throws Exception {
        ReflectionTestUtils.setField(programmingExerciseRepositoryService, "mavenCentralMirrorUrl", MIRROR_URL);
        Path repoPath = gradleTestRepository();

        programmingExerciseRepositoryService.setupBuildToolProjectFile(repoPath, ProjectType.PLAIN_GRADLE, mirrorSections(true));

        // The mirror survives in build.gradle (dependencies) and in settings.gradle (plugins), and the markers are gone.
        assertThat(Files.readString(repoPath.resolve("build.gradle"))).contains("artemisMavenCentralMirror").doesNotContain("%maven-central-mirror");
        assertThat(Files.readString(repoPath.resolve("settings.gradle"))).contains("pluginManagement").contains("artemisMavenCentralMirror")
                .doesNotContain("%maven-central-mirror");
    }

    @Test
    void setupBuildToolProjectFile_removesTheMirrorDeclarationsWhenNoMirrorIsConfigured() throws Exception {
        Path repoPath = gradleTestRepository();

        programmingExerciseRepositoryService.setupBuildToolProjectFile(repoPath, ProjectType.PLAIN_GRADLE, mirrorSections(false));

        // Nothing mirror related may be left behind, in particular no unresolved placeholder that would break the build.
        String buildGradle = Files.readString(repoPath.resolve("build.gradle"));
        assertThat(buildGradle).doesNotContain("artemisMavenCentralMirror").doesNotContain("${mavenCentralMirrorUrl}").doesNotContain("%maven-central-mirror");
        assertThat(buildGradle).contains("mavenCentral()");
        String settingsGradle = Files.readString(repoPath.resolve("settings.gradle"));
        assertThat(settingsGradle).doesNotContain("pluginManagement").doesNotContain("${mavenCentralMirrorUrl}").doesNotContain("%maven-central-mirror");
        assertThat(settingsGradle).contains("rootProject.name");
    }

    @Test
    void replacePlaceholders_insertsTheConfiguredMirrorUrl() throws Exception {
        ReflectionTestUtils.setField(programmingExerciseRepositoryService, "mavenCentralMirrorUrl", MIRROR_URL);
        Path repoPath = gradleTestRepository();
        Repository repository = mockRepository(repoPath);

        programmingExerciseRepositoryService.replacePlaceholders(javaExercise(), repository);

        assertThat(Files.readString(repoPath.resolve("build.gradle"))).contains(MIRROR_URL).doesNotContain("${mavenCentralMirrorUrl}");
        assertThat(Files.readString(repoPath.resolve("settings.gradle"))).contains(MIRROR_URL).doesNotContain("${mavenCentralMirrorUrl}");
    }

    private static Map<String, Boolean> mirrorSections(boolean mirrorConfigured) {
        Map<String, Boolean> sections = new HashMap<>();
        sections.put("maven-central-mirror", mirrorConfigured);
        sections.put("static-code-analysis", false);
        sections.put("non-sequential", true);
        sections.put("sequential", false);
        return sections;
    }

    /**
     * Creates a repository with the mirror-relevant parts of the Gradle test template: the guarded mirror declarations in
     * build.gradle and settings.gradle.
     */
    private Path gradleTestRepository() throws Exception {
        Path repoPath = tempDir.resolve("gradle-repo");
        Files.createDirectories(repoPath);
        FileUtils.writeStringToFile(repoPath.resolve("build.gradle").toFile(), """
                repositories {
                    // %maven-central-mirror-start%
                    maven {
                        name 'artemisMavenCentralMirror'
                        url '${mavenCentralMirrorUrl}'
                    }
                    // %maven-central-mirror-stop%
                    mavenCentral()
                    mavenLocal()
                }
                """, StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repoPath.resolve("settings.gradle").toFile(), """
                // %maven-central-mirror-start%
                pluginManagement {
                    repositories {
                        maven {
                            name 'artemisMavenCentralMirror'
                            url '${mavenCentralMirrorUrl}'
                        }
                        gradlePluginPortal()
                    }
                }
                // %maven-central-mirror-stop%
                rootProject.name = 'Some-Exercise-Tests'
                """, StandardCharsets.UTF_8);
        return repoPath;
    }

    private static ProgrammingExercise javaExercise() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setTitle("Some Exercise");
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        exercise.setPackageName("de.tum.in.ase");
        exercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        return exercise;
    }

}
