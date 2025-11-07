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

    @BeforeEach
    void initTestCase() throws Exception {
        programmingExerciseIntegrationTestService.setup(TEST_PREFIX, this, versionControlService, continuousIntegrationService);
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateValidAuxiliaryRepository() throws Exception {
        programmingExerciseIntegrationTestService.testValidateValidAuxiliaryRepository();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryIdSetOnRequest() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryIdSetOnRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithoutName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithTooLongName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithDuplicatedName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithDuplicatedName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithRestrictedName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithRestrictedName();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithoutCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutCheckoutDirectory();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithNullCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithNullCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithTooLongDescription() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongDescription();
    }

    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testValidateAuxiliaryRepositoryWithoutDescription() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutDescription();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAuxiliaryRepositoriesMissingExercise() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesMissingExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAuxiliaryRepositoriesOk() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesOk();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportAuxiliaryRepositoryUnprocessableEntity() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryUnprocessableEntity();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportAuxiliaryRepositoryExerciseAccessForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryExerciseAccessForbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportAuxiliaryRepositoryRepositoryNotFound() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryRepositoryNotFound();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructoralt1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateProgrammingExercise_notFound() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_notFound();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void test_redirectGetTemplateRepositoryFilesWithContent() throws Exception {
        programmingExerciseIntegrationTestService.test_redirectGetTemplateRepositoryFilesWithContent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void test_redirectGetTemplateRepositoryFilesWithContentAndOmitBinaries() throws Exception {
        programmingExerciseIntegrationTestService.test_redirectGetTemplateRepositoryFilesWithContentOmitBinaries();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetParticipationFilesWithContentAtCommitShouldRedirect() throws Exception {
        programmingExerciseIntegrationTestService.testRedirectGetParticipationRepositoryFilesWithContentAtCommit(TEST_PREFIX);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetParticipationFilesWithContentAtCommitEditorForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testRedirectGetParticipationRepositoryFilesWithContentAtCommitForbidden(TEST_PREFIX);
    }

    @ParameterizedTest
    @EnumSource(value = RepositoryType.class, names = { "TEMPLATE", "SOLUTION", "TESTS" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportInstructorRepositories(RepositoryType repositoryType) throws Exception {
        programmingExerciseIntegrationTestService.exportInstructorRepositories_shouldReturnFile(repositoryType);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportAuxiliaryRepository_shouldReturnFile() throws Exception {
        programmingExerciseIntegrationTestService.exportInstructorAuxiliaryRepository_shouldReturnFile();
    }
}
