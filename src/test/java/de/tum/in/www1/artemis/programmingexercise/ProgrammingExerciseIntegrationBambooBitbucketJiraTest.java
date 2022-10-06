package de.tum.in.www1.artemis.programmingexercise;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.util.HestiaUtilTestService;
import de.tum.in.www1.artemis.util.LocalRepository;

class ProgrammingExerciseIntegrationBambooBitbucketJiraTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseIntegrationTestService programmingExerciseIntegrationTestService;

    @Autowired
    private HestiaUtilTestService hestiaUtilTestService;

    @BeforeEach
    void initTestCase() throws Exception {
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bambooRequestMockProvider.enableMockingOfRequests(true);
        programmingExerciseIntegrationTestService.setup(this, versionControlService);
    }

    @AfterEach
    void tearDown() throws IOException {
        programmingExerciseIntegrationTestService.tearDown();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
        programmingExerciseIntegrationTestService.testProgrammingExerciseIsReleased_IsReleasedAndHasResults();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseIsReleased_IsNotReleasedAndHasResults() throws Exception {
        programmingExerciseIntegrationTestService.testProgrammingExerciseIsReleased_IsNotReleasedAndHasResults();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasNoResults() throws Exception {
        programmingExerciseIntegrationTestService.checkIfProgrammingExerciseIsReleased_IsReleasedAndHasNoResults();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testProgrammingExerciseIsReleased_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testProgrammingExerciseIsReleased_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectName() throws Exception {
        programmingExerciseIntegrationTestService.testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectName();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectNameError() throws Exception {
        programmingExerciseIntegrationTestService.testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectNameError();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // file locking issues
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportSubmissionsByParticipationIds() throws Exception {
        programmingExerciseIntegrationTestService.testExportSubmissionsByParticipationIds();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // git file locking issues
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportSubmissionAnonymizationCombining() throws Exception {
        programmingExerciseIntegrationTestService.testExportSubmissionAnonymizationCombining();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // git file locking issues
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportSubmissionsByParticipationIds_invalidParticipationId_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.testExportSubmissionsByParticipationIds_invalidParticipationId_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testExportSubmissionsByParticipationIds_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testExportSubmissionsByParticipationIds_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportSubmissionsByStudentLogins() throws Exception {
        programmingExerciseIntegrationTestService.testExportSubmissionsByStudentLogins();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testExportSubmissionsByStudentLogins_failToCreateZip() throws Exception {
        doThrow(IOException.class).when(zipFileService).createZipFile(any(Path.class), any(), eq(false));
        programmingExerciseIntegrationTestService.testExportSubmissionsByStudentLogins_failToCreateZip();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete() throws Exception {
        programmingExerciseIntegrationTestService.testProgrammingExerciseDelete();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete_invalidId_notFound() throws Exception {
        programmingExerciseIntegrationTestService.testProgrammingExerciseDelete_invalidId_notFound();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testProgrammingExerciseDelete_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testProgrammingExerciseDelete_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercise() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExercise();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithStructuredGradingInstruction() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExerciseWithStructuredGradingInstruction();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExercise_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExerciseWithSetupParticipations();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetProgrammingExerciseWithJustTemplateAndSolutionParticipation() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExerciseWithJustTemplateAndSolutionParticipation();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExerciseWithSetupParticipations_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExerciseWithSetupParticipations_invalidId_notFound() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExerciseWithSetupParticipations_invalidId_notFound();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercisesForCourse() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExercisesForCourse();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testGetProgrammingExercisesForCourse_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testGetProgrammingExercisesForCourse_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGenerateStructureOracle() throws Exception {
        programmingExerciseIntegrationTestService.testGenerateStructureOracle();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_invalidTemplateBuildPlan_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_invalidTemplateBuildPlan_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_idIsNull_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_idIsNull_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_falseToTrue_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_staticCodeAnalysisMustNotChange_falseToTrue_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_trueToFalse_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_staticCodeAnalysisMustNotChange_trueToFalse_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_invalidTemplateVcs_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_invalidTemplateVcs_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_invalidSolutionBuildPlan_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_invalidSolutionBuildPlan_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_invalidSolutionRepository_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_invalidSolutionRepository_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_checkIfBuildPlanExistsFails_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExercise_checkIfBuildPlanExistsFails_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_updatingCourseId_conflict() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExerciseShouldFailWithConflictWhenUpdatingCourseId();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_updatingSCAEnabledOption_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExerciseShouldFailWithBadRequestWhenUpdatingSCAOption();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProgrammingExercise_updatingCoverageOption_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateProgrammingExerciseShouldFailWithBadRequestWhenUpdatingCoverageOption();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateExerciseDueDateWithIndividualDueDateUpdate() throws Exception {
        programmingExerciseIntegrationTestService.updateExerciseDueDateWithIndividualDueDateUpdate();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateExerciseRemoveDueDate() throws Exception {
        programmingExerciseIntegrationTestService.updateExerciseRemoveDueDate();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void updateTimeline_intructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.updateTimeline_intructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void updateTimeline_invalidId_notFound() throws Exception {
        programmingExerciseIntegrationTestService.updateTimeline_invalidId_notFound();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTimeline_ok() throws Exception {
        programmingExerciseIntegrationTestService.updateTimeline_ok();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void updateProblemStatement_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.updateProblemStatement_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateProblemStatement_invalidId_notFound() throws Exception {
        programmingExerciseIntegrationTestService.updateProblemStatement_invalidId_notFound();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_exerciseIsNull_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_exerciseIsNull_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_idIsNotNull_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_idIsNotNull_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void createProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_titleNull_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_titleNull_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_titleContainsBadCharacter_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_titleContainsBadCharacter_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_invalidShortName_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_invalidShortName_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_invalidCourseShortName_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_invalidCourseShortName_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_sameShortNameInCourse_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_shortNameContainsBadCharacters_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_shortNameContainsBadCharacters_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_noProgrammingLanguageSet_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_noProgrammingLanguageSet_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_packageNameContainsBadCharacters_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_packageNameContainsBadCharacters_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_packageNameContainsKeyword_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_packageNameContainsKeyword_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_packageNameElementBeginsWithDigit_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_packageNameElementBeginsWithDigit_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_packageNameIsNull_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_packageNameIsNull_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_maxScoreIsNull_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_maxScoreIsNull_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_noParticipationModeSelected_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_staticCodeAnalysisAndSequential_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_staticCodeAnalysisAndSequential_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_unsupportedProgrammingLanguageForStaticCodeAnalysis_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_unsupportedProgrammingLanguageForStaticCodeAnalysis_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_noStaticCodeAnalysisButMaxPenalty_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_noStaticCodeAnalysisButMaxPenalty_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_maxStaticCodePenaltyNegative_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_maxStaticCodePenaltyNegative_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_failToCheckIfProjectExistsInCi() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_failToCheckIfProjectExistsInCi();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_projectTypeMissing_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_projectTypeMissing_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_projectTypeNotExpected_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_projectTypeNotExpected_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_onlineCodeEditorNotExpected_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_onlineCodeEditorNotExpected_badRequest();
    }

    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    // It should fail for all ProgrammingExercises except Haskell and ocaml
    @EnumSource(value = ProgrammingLanguage.class, names = { "HASKELL", "OCAML" }, mode = EnumSource.Mode.EXCLUDE)
    void createProgrammingExercise_checkoutSolutionRepositoryProgrammingLanguageNotSupported_badRequest(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_checkoutSolutionRepositoryProgrammingLanguageNotSupported_badRequest(programmingLanguage);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_invalidMaxScore_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_invalidMaxScore_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_includedAsBonus_invalidBonusPoints_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_includedAsBonus_invalidBonusPoints_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_notIncluded_invalidBonusPoints_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.createProgrammingExercise_notIncluded_invalidBonusPoints_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void importProgrammingExercise_sourceExerciseIdNegative_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_sourceExerciseIdNegative_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void importProgrammingExerciseMaxScoreNullBadRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExerciseMaxScoreNullBadRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_noParticipationModeSelected_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_noProgrammingLanguage_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_noProgrammingLanguage_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void importProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_templateIdDoesNotExist_notFound() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_templateIdDoesNotExist_notFound();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_sameShortNameInCourse_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_sameTitleInCourse_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_sameTitleInCourse_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @CsvSource({ "false, false", "true, false", "false, true", })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_scaChanged_badRequest(boolean recreateBuildPlan, boolean updateTemplate) throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_scaChanged_badRequest(recreateBuildPlan, updateTemplate);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // git file locking issues
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_bambooProjectWithSameKeyAlreadyExists_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.importProgrammingExercise_bambooProjectWithSameTitleAlreadyExists_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void exportSubmissionsByStudentLogins_notInstructorForExercise_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.exportSubmissionsByStudentLogins_notInstructorForExercise_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void exportSubmissionsByStudentLogins_exportAllAsTutor_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.exportSubmissionsByStudentLogins_exportAllAsTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void generateStructureOracleForExercise_exerciseDoesNotExist_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.generateStructureOracleForExercise_exerciseDoesNotExist_badRequest();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void generateStructureOracleForExercise_userIsNotAdminInCourse_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.generateStructureOracleForExercise_userIsNotAdminInCourse_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void generateStructureOracleForExercise_invalidPackageName_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.generateStructureOracleForExercise_invalidPackageName_badRequest();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void hasAtLeastOneStudentResult_exerciseDoesNotExist_notFound() throws Exception {
        programmingExerciseIntegrationTestService.hasAtLeastOneStudentResult_exerciseDoesNotExist_notFound();
    }

    @Test
    @WithMockUser(username = "tutoralt1", roles = "TA")
    void hasAtLeastOneStudentResult_isNotTeachingAssistant_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.hasAtLeastOneStudentResult_isNotTeachingAssistant_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void getTestCases_asTutor() throws Exception {
        programmingExerciseIntegrationTestService.getTestCases_asTutor();
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void getTestCases_asStudent_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.getTestCases_asStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "other-teaching-assistant1", roles = "TA")
    void getTestCases_tutorInOtherCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.getTestCases_tutorInOtherCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTestCases_asInstrutor() throws Exception {
        programmingExerciseIntegrationTestService.updateTestCases_asInstrutor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTestCases_asInstrutor_triggerBuildFails() throws Exception {
        programmingExerciseIntegrationTestService.updateTestCases_asInstrutor_triggerBuildFails();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTestCases_nonExistingExercise_notFound() throws Exception {
        programmingExerciseIntegrationTestService.updateTestCases_nonExistingExercise_notFound();
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    void updateTestCases_instructorInWrongCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.updateTestCases_instructorInWrongCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTestCases_testCaseWeightSmallerThanZero_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateTestCases_testCaseWeightSmallerThanZero_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTestCases_testCaseMultiplierSmallerThanZero_badRequest() throws Exception {
        programmingExerciseIntegrationTestService.updateTestCases_testCaseMultiplierSmallerThanZero_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateTestCases_testCaseBonusPointsNull() throws Exception {
        programmingExerciseIntegrationTestService.updateTestCases_testCaseBonusPointsNull();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS) // git file locking issues
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void resetTestCaseWeights_asInstructor() throws Exception {
        programmingExerciseIntegrationTestService.resetTestCaseWeights_asInstructor();
    }

    @Test
    @WithMockUser(username = "other-instructor1", roles = "INSTRUCTOR")
    void resetTestCaseWeights_instructorInWrongCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.resetTestCaseWeights_instructorInWrongCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void lockAllRepositories_asStudent_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.lockAllRepositories_asStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void lockAllRepositories_asTutor_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.lockAllRepositories_asTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void lockAllRepositories() throws Exception {
        programmingExerciseIntegrationTestService.lockAllRepositories();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void unlockAllRepositories_asStudent_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.unlockAllRepositories_asStudent_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void unlockAllRepositories_asTutor_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.unlockAllRepositories_asTutor_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void unlockAllRepositories() throws Exception {
        programmingExerciseIntegrationTestService.unlockAllRepositories();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarism() throws Exception {
        programmingExerciseIntegrationTestService.testCheckPlagiarism();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testCheckPlagiarismJplagReport() throws Exception {
        programmingExerciseIntegrationTestService.testCheckPlagiarismJplagReport();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResult() throws Exception {
        programmingExerciseIntegrationTestService.testGetPlagiarismResult();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutResult() throws Exception {
        programmingExerciseIntegrationTestService.testGetPlagiarismResultWithoutResult();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismResultWithoutExercise() throws Exception {
        programmingExerciseIntegrationTestService.testGetPlagiarismResultWithoutExercise();
    }

    // Auxiliary Repository Tests

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateValidAuxiliaryRepository() throws Exception {
        programmingExerciseIntegrationTestService.testValidateValidAuxiliaryRepository();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryIdSetOnRequest() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryIdSetOnRequest();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithoutName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutName();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithTooLongName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongName();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithDuplicatedName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithDuplicatedName();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithRestrictedName() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithRestrictedName();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithoutCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithNullCheckoutDirectory() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithNullCheckoutDirectory();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithTooLongDescription() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithTooLongDescription();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testValidateAuxiliaryRepositoryWithoutDescription() throws Exception {
        programmingExerciseIntegrationTestService.testValidateAuxiliaryRepositoryWithoutDescription();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAuxiliaryRepositoriesMissingExercise() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesMissingExercise();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAuxiliaryRepositoriesOk() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesOk();
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testGetAuxiliaryRepositoriesForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesForbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetAuxiliaryRepositoriesEmptyOk() throws Exception {
        programmingExerciseIntegrationTestService.testGetAuxiliaryRepositoriesEmptyOk();
    }

    // Tests for recreate build plan endpoint

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testRecreateBuildPlansForbiddenStudent() throws Exception {
        programmingExerciseIntegrationTestService.testRecreateBuildPlansForbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testRecreateBuildPlansForbiddenTutor() throws Exception {
        programmingExerciseIntegrationTestService.testRecreateBuildPlansForbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testRecreateBuildPlansExerciseNotFound() throws Exception {
        programmingExerciseIntegrationTestService.testRecreateBuildPlansExerciseNotFound();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testRecreateBuildPlansSuccess() throws Exception {
        programmingExerciseIntegrationTestService.testRecreateBuildPlansExerciseSuccess();
    }

    // Tests for export auxiliary repository for exercise endpoint

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testExportAuxiliaryRepositoryForbidden() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryForbidden();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testExportEmptyAuxiliaryRepository() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryBadRequest();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testExportAuxiliaryRepositoryExerciseNotFound() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryExerciseNotFound();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testExportAuxiliaryRepositoryRepositoryNotFound() throws Exception {
        programmingExerciseIntegrationTestService.testExportAuxiliaryRepositoryRepositoryNotFound();
    }

    @Test
    @WithMockUser(username = "instructoralt1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateProgrammingExercise_notFound() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_notFound();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        programmingExerciseIntegrationTestService.testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetSolutionFilesShouldRedirect() throws Exception {
        programmingExerciseIntegrationTestService.test_redirectGetSolutionRepositoryFilesWithoutContent((exercise, files) -> {
            LocalRepository localRepository = new LocalRepository("main");
            assertDoesNotThrow(() -> hestiaUtilTestService.setupSolution(files, exercise, localRepository));
            return localRepository;
        });
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetTemplateFilesWithContentShouldRedirect() throws Exception {
        programmingExerciseIntegrationTestService.test_redirectGetTemplateRepositoryFilesWithContent((exercise, files) -> {
            LocalRepository localRepository = new LocalRepository("main");
            assertDoesNotThrow(() -> hestiaUtilTestService.setupTemplate(files, exercise, localRepository));
            return localRepository;
        });
    }
}
