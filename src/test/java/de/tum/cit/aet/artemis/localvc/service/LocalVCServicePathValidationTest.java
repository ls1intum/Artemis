package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.localvc.exception.LocalVCInternalException;

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
}
