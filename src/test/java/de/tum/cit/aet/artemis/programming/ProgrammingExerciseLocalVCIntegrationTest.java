package de.tum.cit.aet.artemis.programming;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

class ProgrammingExerciseLocalVCIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "programmingexerciselocalvc";

    /** A user with INSTRUCTOR authority that is intentionally NOT enrolled in the test course (different prefix). */
    private static final String OTHER_PREFIX = "other";

    @BeforeEach
    void initTestCase() throws Exception {
        programmingExerciseIntegrationTestService.setup(TEST_PREFIX, this, versionControlService, continuousIntegrationService);
        // Pre-create an instructor who is not enrolled in any course — used by *_instructorNotInCourse_forbidden tests.
        userUtilService.addInstructor(OTHER_PREFIX + "instructor42");
    }

    @AfterEach
    void tearDown() throws IOException {
        programmingExerciseIntegrationTestService.tearDown();
    }

    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Disabled // TODO figure out a way to correctly mock repo URLs, alternatively: implement https://github.com/ls1intum/Artemis/issues/10318 which should fix the core issue too
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateValidAuxiliaryRepository() throws Exception {
        programmingExerciseIntegrationTestService.testValidateValidAuxiliaryRepository();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryIdSetOnRequest() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryIdSetOnRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithoutName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithTooLongName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithDuplicatedName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithDuplicatedName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithRestrictedName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithRestrictedName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithoutCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutCheckoutDirectory();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithNullCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithNullCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithTooLongDescription() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongDescription();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testValidateAuxiliaryRepositoryWithoutDescription() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutDescription();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetAuxiliaryRepositoriesMissingExercise() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesMissingExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetAuxiliaryRepositoriesOk() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesOk();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetAuxiliaryRepositoriesEmptyOk() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesEmptyOk();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetAuxiliaryRepositoriesForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExportAuxiliaryRepositoryForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testExportAuxiliaryRepositoryUnprocessableContent() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryUnprocessableContent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testExportAuxiliaryRepositoryExerciseAccessForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryExerciseAccessForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testExportAuxiliaryRepositoryRepositoryNotFound() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryRepositoryNotFound();
    }

    @Test
    @WithMockUser(username = OTHER_PREFIX + "instructor42", roles = "USER")
    void testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testReEvaluateAndUpdateProgrammingExercise_notFound() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_notFound();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetTemplateRepositoryFilesWithContent() throws Exception {
        programmingExerciseIntegrationTestService.testGetTemplateRepositoryFilesWithContent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetTemplateRepositoryFilesWithContentAndOmitBinaries() throws Exception {
        programmingExerciseIntegrationTestService.testGetTemplateRepositoryFilesWithContentOmitBinaries();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetTemplateRepositoryFilesWithContentReflectsLatestCommit() throws Exception {
        programmingExerciseIntegrationTestService.testGetTemplateRepositoryFilesWithContentReflectsLatestCommit();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetSolutionRepositoryFilesWithContent() throws Exception {
        programmingExerciseIntegrationTestService.testGetSolutionRepositoryFilesWithContent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void testGetParticipationFilesWithContentAtCommitShouldRedirect() throws Exception {
        programmingExerciseIntegrationTestService.testRedirectGetParticipationRepositoryFilesWithContentAtCommit(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "USER")
    void testGetParticipationFilesWithContentAtCommitEditorForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testRedirectGetParticipationRepositoryFilesWithContentAtCommitForbidden(TEST_PREFIX);
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryType.class, names = { "TEMPLATE", "SOLUTION", "TESTS" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void exportInstructorRepositories(RepositoryType repositoryType) throws Exception {
        programmingExerciseIntegrationTestService.exportInstructorRepositories_shouldReturnFile(repositoryType);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "USER")
    void exportAuxiliaryRepository_shouldReturnFile() throws Exception {
        programmingExerciseIntegrationTestService.exportInstructorAuxiliaryRepository_shouldReturnFile();
    }
}
