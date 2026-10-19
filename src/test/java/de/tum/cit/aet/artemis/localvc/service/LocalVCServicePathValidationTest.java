package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Unit tests for the path validation in {@link LocalVCService}. The service resolves repository and project directories
 * from project keys and repository slugs. These tests verify that a malformed value cannot make a file system operation
 * resolve to a directory outside the configured local VC base directory, and that valid values are accepted unchanged.
 */
class LocalVCServicePathValidationTest {

    @TempDir
    Path baseDir;

    private LocalVCService localVCService;

    @BeforeEach
    void setUp() {
        // None of the methods under test touch the injected collaborators, so passing null is safe and keeps the test fast.
        localVCService = new LocalVCService(null, null, null, null, null, null);
        ReflectionTestUtils.setField(localVCService, "localVCBasePath", baseDir);
        ReflectionTestUtils.setField(localVCService, "localVCBaseUri", URI.create("https://artemis.example.com"));
    }

    @Test
    void checkIfProjectExists_withProjectKeyEscapingBaseDirectory_throws() {
        assertThatThrownBy(() -> localVCService.checkIfProjectExists("../../../../../../etc", "someName")).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
    }

    @Test
    void checkIfProjectExists_withValidProjectKey_doesNotThrowAndReturnsFalse() {
        assertThatCode(() -> assertThat(localVCService.checkIfProjectExists("ABC", "someName")).isFalse()).doesNotThrowAnyException();
    }

    @Test
    void deleteProject_withProjectKeyEscapingBaseDirectory_throwsBeforeTouchingTheFileSystem() {
        assertThatThrownBy(() -> localVCService.deleteProject("../../../../../../etc")).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
    }

    @Test
    void deleteProject_withProjectKeyResolvingToBaseDirectory_throws() {
        // "", "." and "ABC/.." all normalize to the base directory itself; rejecting them prevents deletion of the entire base directory.
        for (String projectKey : new String[] { "", ".", "ABC/.." }) {
            assertThatThrownBy(() -> localVCService.deleteProject(projectKey)).as("project key '%s' must be rejected", projectKey).isInstanceOf(LocalVCInternalException.class)
                    .hasMessageContaining("outside the local VC base path");
        }
    }

    @Test
    void checkIfProjectExists_withProjectKeyResolvingToBaseDirectory_throws() {
        assertThatThrownBy(() -> localVCService.checkIfProjectExists("ABC/..", "someName")).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
    }

    @Test
    void checkIfProjectExists_withNestedProjectKey_throws() {
        // A nested key is not a direct child of the base directory and must be rejected.
        assertThatThrownBy(() -> localVCService.checkIfProjectExists("ABC/sub", "someName")).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
    }

    @Test
    void repositoryExists_withRepositorySlugEscapingBaseDirectory_throws() {
        // The three-argument constructor does not normalize its inputs, so a slug with ../ segments reaches getLocalRepositoryPath.
        LocalVCRepositoryUri unexpectedUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "x/../../../../../../../../../../etc/passwd");

        assertThatThrownBy(() -> localVCService.repositoryExists(unexpectedUri)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
    }

    @Test
    void repositoryExists_withRepositorySlugEscapingProjectDirectoryButWithinBase_throws() {
        // Slug "../EVIL" resolves to base/EVIL.git: still inside the base directory, but not a child of the expected base/ABC project directory.
        LocalVCRepositoryUri crossProjectUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "../EVIL");

        assertThatThrownBy(() -> localVCService.repositoryExists(crossProjectUri)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
    }

    @Test
    void isValidGitRepository_withPathEscapingBaseDirectory_returnsFalseWithoutTouchingTheFileSystem() {
        // A broken stored URI reaches this check, so it must apply the same containment validation as repositoryExists. It reports the repository as invalid rather
        // than throwing, because the caller uses the result to fall back to a repaired, canonical URI.
        LocalVCRepositoryUri escapingUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "x/../../../../../../../../../../etc/passwd");

        assertThat(localVCService.isValidGitRepository(escapingUri)).isFalse();
    }

    @Test
    void isValidGitRepository_withPathEscapingProjectDirectoryButWithinBase_returnsFalse() {
        LocalVCRepositoryUri crossProjectUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "../EVIL");

        assertThat(localVCService.isValidGitRepository(crossProjectUri)).isFalse();
    }

    @Test
    void isValidGitRepository_withValidUriButMissingRepository_returnsFalse() {
        LocalVCRepositoryUri validUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");

        assertThat(localVCService.isValidGitRepository(validUri)).isFalse();
    }

    @Test
    void createRepository_withRepositorySlugEscapingBaseDirectory_throwsBeforeTouchingTheFileSystem() {
        assertThatThrownBy(() -> localVCService.createRepository("ABC", "x/../../../../../../../../../../etc/passwd")).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
        assertThat(baseDir).as("nothing was created outside the base directory").isEmptyDirectory();
    }

    @Test
    void createRepository_withRepositorySlugEscapingProjectDirectoryButWithinBase_throws() {
        // Slug "../EVIL" resolves to base/EVIL.git: inside the base directory, but not a child of the expected base/ABC project directory.
        assertThatThrownBy(() -> localVCService.createRepository("ABC", "../EVIL")).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
        assertThat(baseDir).as("the sibling directory the slug pointed at was not created").isEmptyDirectory();
    }

    @Test
    void createRepository_withValidSlug_createsABareRepositoryWithTheDefaultBranchAsHead() throws Exception {
        ReflectionTestUtils.setField(localVCService, "defaultBranch", "main");

        localVCService.createRepository("ABC", "abc-exercise");

        Path repositoryPath = baseDir.resolve("ABC").resolve("abc-exercise.git");
        assertThat(repositoryPath).as("the repository is created inside its project directory").isDirectory();
        assertThat(repositoryPath.resolve("HEAD")).as("a bare repository has a HEAD file").isRegularFile();
        assertThat(Files.readString(repositoryPath.resolve("HEAD"))).as("HEAD points at the configured default branch").isEqualTo("ref: refs/heads/main\n");
        assertThat(repositoryPath.resolve("objects")).as("a bare repository has an object database").isDirectory();
        assertThat(repositoryPath.resolve(".git")).as("the repository is bare, so it has no nested .git directory").doesNotExist();
    }

    @Test
    void deleteRepository_withRepositorySlugEscapingBaseDirectory_throwsBeforeTouchingTheFileSystem() {
        LocalVCRepositoryUri escapingUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "x/../../../../../../../../../../etc/passwd");

        assertThatThrownBy(() -> localVCService.deleteRepository(escapingUri)).isInstanceOf(LocalVCInternalException.class).hasMessageContaining("outside the local VC base path");
    }

    @Test
    void deleteRepository_withRepositorySlugEscapingProjectDirectoryButWithinBase_leavesTheSiblingDirectoryAlone() throws Exception {
        Path sibling = Files.createDirectories(baseDir.resolve("EVIL.git"));
        LocalVCRepositoryUri crossProjectUri = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "../EVIL");

        assertThatThrownBy(() -> localVCService.deleteRepository(crossProjectUri)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
        assertThat(sibling).as("a repository of another project must not be deleted").isDirectory();
    }

    @Test
    void deleteRepository_withValidUri_removesOnlyThatRepository() throws Exception {
        Path deleted = Files.createDirectories(baseDir.resolve("ABC").resolve("abc-exercise.git"));
        Path kept = Files.createDirectories(baseDir.resolve("ABC").resolve("abc-solution.git"));

        localVCService.deleteRepository(new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise"));

        assertThat(deleted).as("the addressed repository is gone").doesNotExist();
        assertThat(kept).as("the sibling repository of the same project is untouched").isDirectory();
        assertThat(baseDir.resolve("ABC")).as("the project directory itself survives").isDirectory();
    }

    @Test
    void deleteProject_withValidProjectKey_removesTheProjectAndItsRepositories() throws Exception {
        Files.createDirectories(baseDir.resolve("ABC").resolve("abc-exercise.git"));
        Path otherProject = Files.createDirectories(baseDir.resolve("XYZ").resolve("xyz-exercise.git"));

        localVCService.deleteProject("ABC");

        assertThat(baseDir.resolve("ABC")).as("the project and everything under it is gone").doesNotExist();
        assertThat(otherProject).as("another project is untouched").isDirectory();
    }

    @Test
    void createProjectForExercise_withProjectKeyEscapingBaseDirectory_throwsBeforeTouchingTheFileSystem() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ReflectionTestUtils.setField(exercise, "projectKey", "../../../../../../etc");

        assertThatThrownBy(() -> localVCService.createProjectForExercise(exercise)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("outside the local VC base path");
        assertThat(baseDir).as("nothing was created outside the base directory").isEmptyDirectory();
    }

    @Test
    void createProjectForExercise_withValidProjectKey_createsTheProjectDirectory() {
        ProgrammingExercise exercise = new ProgrammingExercise();
        ReflectionTestUtils.setField(exercise, "projectKey", "ABC");

        localVCService.createProjectForExercise(exercise);

        assertThat(baseDir.resolve("ABC")).as("the project directory is created directly under the base directory").isDirectory();
    }

    @Test
    void repositoryUriIsValid_acceptsAWellFormedUri() {
        LocalVCRepositoryUri wellFormed = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");

        assertThat(localVCService.repositoryUriIsValid(wellFormed)).as("a URI built from a project key and slug is valid").isTrue();
    }

    @Test
    void repositoryUriIsValid_rejectsANullUri() {
        assertThat(localVCService.repositoryUriIsValid(null)).as("a null URI is not valid").isFalse();
    }

    @Test
    void repositoryUriIsValid_rejectsAUriThatCannotBeParsedBack() {
        // A URI stored before the LocalVC path layout was introduced has no "git" segment, so parsing it back fails and the repository must be reported as invalid
        // rather than used. The field is replaced directly because the constructors always produce a well-formed path.
        LocalVCRepositoryUri malformed = new LocalVCRepositoryUri(URI.create("https://artemis.example.com"), "ABC", "abc-exercise");
        ReflectionTestUtils.setField(malformed, "uri", URI.create("https://artemis.example.com/ABC/abc-exercise.git"));

        assertThat(localVCService.repositoryUriIsValid(malformed)).as("a URI that cannot be parsed back is not valid").isFalse();
    }

    @Test
    void createRepository_whenTheProjectDirectoryIsBlockedByAFile_reportsAnInternalException() throws Exception {
        // A regular file where the project directory belongs makes creating the directory fail, which has to surface as a LocalVC error rather than an IOException.
        FileUtils.write(baseDir.resolve("ABC").toFile(), "not a directory", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> localVCService.createRepository("ABC", "abc-exercise")).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("Error while creating local git project").hasCauseInstanceOf(java.io.IOException.class);
    }

    @Test
    void createProjectForExercise_whenTheProjectDirectoryIsBlockedByAFile_reportsAnInternalException() throws Exception {
        FileUtils.write(baseDir.resolve("ABC").toFile(), "not a directory", StandardCharsets.UTF_8);
        ProgrammingExercise exercise = new ProgrammingExercise();
        ReflectionTestUtils.setField(exercise, "projectKey", "ABC");

        assertThatThrownBy(() -> localVCService.createProjectForExercise(exercise)).isInstanceOf(LocalVCInternalException.class)
                .hasMessageContaining("Error while creating local VC project").hasCauseInstanceOf(java.io.IOException.class);
    }
}
