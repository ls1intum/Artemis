package de.tum.in.www1.artemis.localvcci;

import org.junit.jupiter.api.Test;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;

class LocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Test
    void testCommitHashNull() {
        // Get latest commit from repository.

    }

    @Test
    void testInvalidLocalVCRepositoryUrl() {

    }

    @Test
    void testExceptionWhenResolvingCommit() {
        // Author name, email, and message are set to null. This should not cause problems for the result.
    }

    @Test
    void testNoParticipation() {
        // Should throw a LocalCIException if there is no paricipation for the exercise and the repositoryTypeOrUserName.

        // solution participation

        // template participation

        // team participation

        // student participation
    }

    @Test
    void testCannotRetrieveBuildScriptPath() {
        // Should throw a LocalCIException
    }

    @Test
    void testProjectTypeIsNull() {

    }

    @Test
    void testImageNotFound() {
        // dockerClient.inspectImageCmd().exec() throws NotFoundException.
    }

    @Test
    void testCannotRetrieveCommitHash() {
        // Should stop the container and return a build result that indicates that the build failed.
    }

    @Test
    void testCannotFindResults() {
        // Should stop the container and return a build result that indicates that the build failed.

    }

    @Test
    void testExceptionWhenParsingTestResults() {

    }

    @Test
    void testBuildFailed() {

    }
}
