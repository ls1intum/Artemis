package de.tum.in.www1.artemis.exercise.programmingexercise;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.*;
import static de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseTestService.studentLogin;
import static de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingSubmissionConstants.GITLAB_PUSH_EVENT_REQUEST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;

class ProgrammingExerciseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    private static final String TEST_PREFIX = "progexgitlabjenkins";

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(TEST_PREFIX, 0, 0, 0, 0);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
        aeolusRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
        aeolusRequestMockProvider.reset();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "KOTLIN" }, mode = EnumSource.Mode.INCLUDE)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_sequential_validExercise_created(ProgrammingLanguage programmingLanguage) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_sequential_validExercise_created(programmingLanguage);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_created(ExerciseMode mode) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_mode_validExercise_created(mode);
    }

    // TODO: Add template for VHDL, Assembler, and Ocaml and activate those languages here again
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "VHDL", "ASSEMBLER", "OCAML" }, mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_programmingLanguage_validExercise_created(ProgrammingLanguage language) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_programmingLanguage_validExercise_created(language,
                programmingLanguageFeatureService.getProgrammingLanguageFeatures(language));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "VHDL", "ASSEMBLER", "OCAML", "C" }, mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_custom_build_plan_validExercise_created(ProgrammingLanguage language) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_custom_build_plan_validExercise_created(language, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "VHDL", "ASSEMBLER", "OCAML", "C" }, mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_failed_custom_build_plan_validExercise_created(ProgrammingLanguage language) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_custom_build_plan_validExercise_created(language, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_bonusPointsIsNull() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_validExercise_bonusPointsIsNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_withStaticCodeAnalysis(ProgrammingLanguage language) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_validExercise_withStaticCodeAnalysis(language,
                programmingLanguageFeatureService.getProgrammingLanguageFeatures(language));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseForExam_validExercise_created() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExerciseForExam_validExercise_created();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseForExam_datesSet() throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_DatesSet();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_invalidExercise_dates(dates);
    }

    private static Stream<Arguments> generateArgumentsForImportExercise() {
        // TODO: sync with JenkinsProgrammingLanguageFeatureService (as this is a static method here, not possible automatically, so we have to do it manually)
        var supportedLanguages = new ProgrammingLanguage[] { JAVA, PYTHON, C, HASKELL, KOTLIN, SWIFT, EMPTY };
        return Arrays.stream(supportedLanguages).flatMap(language -> Stream.of(Arguments.of(language, true, true), Arguments.of(language, false, true),
                Arguments.of(language, false, false), Arguments.of(language, true, false)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("generateArgumentsForImportExercise")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_created(ProgrammingLanguage programmingLanguage, boolean recreateBuildPlans, boolean addAuxRepos) throws Exception {
        programmingExerciseTestService.importExercise_created(programmingLanguage, recreateBuildPlans, addAuxRepos);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndImportJavaProgrammingExercise(boolean staticCodeAnalysisEnabled) throws Exception {
        forceDefaultBuildPlanCreation();
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createAndImportJavaProgrammingExercise(staticCodeAnalysisEnabled);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_enablePlanFails() throws Exception {
        programmingExerciseTestService.importExercise_enablePlanFails();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_planDoesntExist() throws Exception {
        programmingExerciseTestService.importExercise_planDoesntExist();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_sca_deactivated() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_scaChange();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_sca_activated() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_scaChange_activated();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseLockRepositorySubmissionPolicy() throws Exception {
        programmingExerciseTestService.testImportProgrammingExerciseLockRepositorySubmissionPolicyChange();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseNoneSubmissionPolicy() throws Exception {
        programmingExerciseTestService.testImportProgrammingExerciseNoneSubmissionPolicyChange();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_structureOracle() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_validExercise_structureOracle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_noTutors_created() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_noTutors_created();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseFromFileAsTutor_forbidden() throws Exception {
        programmingExerciseTestService.importFromFile_tutor_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseFromFileMissingExerciseDetailsJson_badRequest() throws Exception {
        programmingExerciseTestService.importFromFile_missingExerciseDetailsJson_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseFromFile_NoZip_badRequest() throws Exception {
        programmingExerciseTestService.importFromFile_fileNoZip_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseFromFile_exception_directoryDeleted() throws Exception {
        doThrow(new RuntimeException("Error")).when(zipFileService).extractZipFileRecursively(any(Path.class));
        programmingExerciseTestService.importFromFile_exception_DirectoryDeleted();
        verify(fileService).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseFromFileMissingRepository_badRequest() throws Exception {
        programmingExerciseTestService.importFromFile_missingRepository_BadRequest();
    }

    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    void importExerciseFromFile_valid_Java_Exercise_importSuccessful(boolean scaEnabled) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.importFromFile_validJavaExercise_isSuccessfullyImported(scaEnabled);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseFromFile_embeddedFiles_filesCopied() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.importFromFile_embeddedFiles_embeddedFilesCopied();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseFromFile_buildPlanPresent_buildPlanSet() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.importFromFile_buildPlanPresent_buildPlanUsed();
    }

    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "HASKELL", "PYTHON" }, mode = EnumSource.Mode.INCLUDE)
    void importExerciseFromFile_valid_Exercise_importSuccessful(ProgrammingLanguage language) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.importFromFile_validExercise_isSuccessfullyImported(language);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void startProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.startProgrammingExercise_correctInitializationState(exerciseMode);
    }

    @Test
    @WithMockUser(username = "edx_student1", roles = "USER")
    void startProgrammingExerciseEdxUser_correctInitializationState() throws Exception {
        programmingExerciseTestService.startProgrammingExercise_correctInitializationState();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void createProgrammingExercise_offlineMode(boolean offlineIde) throws Exception {
        programmingExerciseTestService.startProgrammingExercise(offlineIde);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void createProgrammingExercise_validExercise_noExplicitOfflineMode() throws Exception {
        programmingExerciseTestService.startProgrammingExercise(null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void resumeProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExercise_correctInitializationState(exerciseMode);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void resumeProgrammingExercise_doesNotExist(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExercise_doesNotExist(exerciseMode);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void resumeProgrammingExerciseByPushingIntoRepo_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        Object body = new ObjectMapper().readValue(GITLAB_PUSH_EVENT_REQUEST, Object.class);
        programmingExerciseTestService.resumeProgrammingExerciseByPushingIntoRepo_correctInitializationState(exerciseMode, body);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(exerciseMode, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void resumeProgrammingExerciseByTriggeringBuildAsInstructor_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(exerciseMode, SubmissionType.INSTRUCTOR);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void resumeProgrammingExerciseByRecreatingAndTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(exerciseMode, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(exerciseMode, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(exerciseMode);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + studentLogin, roles = "USER")
    void startProgrammingExerciseStudentRetrieveEmptyArtifactPage() throws Exception {
        programmingExerciseTestService.startProgrammingExerciseStudentRetrieveEmptyArtifactPage();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        programmingExerciseTestService.repositoryAccessIsAdded_whenStudentIsAddedToTeam();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
        programmingExerciseTestService.repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_mode_changedToIndividual() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_individual_modeChange();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_mode_changedToTeam() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_team_modeChange();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_asPartOfExamImport() throws Exception {
        programmingExerciseTestService.importProgrammingExerciseAsPartOfExamImport();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void configureRepository_throwExceptionWhenLtiUserIsNotExistent() throws Exception {
        programmingExerciseTestService.configureRepository_throwExceptionWhenLtiUserIsNotExistent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void copyRepository_testNotCreatedError() throws Exception {
        programmingExerciseTestService.copyRepository_testNotCreatedError();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void configureRepository_testBadRequestError() throws Exception {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        programmingExerciseTestService.configureRepository_testBadRequestError();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportInstructorRepositories() throws Exception {
        programmingExerciseTestService.exportInstructorRepositories_shouldReturnFile();
        // we export three repositories (template, solution, tests) and for each repository the temp directory and the directory with the zip file should be deleted
        verify(fileService, times(6)).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportAuxiliaryRepository_shouldReturnFile() throws Exception {
        programmingExerciseTestService.exportInstructorAuxiliaryRepository_shouldReturnFile();
        // once for the temp directory and once for the directory with the zip file
        verify(fileService, times(2)).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void exportAuxiliaryRepository_forbidden() throws Exception {
        programmingExerciseTestService.exportInstructorAuxiliaryRepository_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void exportInstructorRepositories_forbidden() throws Exception {
        programmingExerciseTestService.exportInstructorRepositories_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportProgrammingExerciseInstructorMaterial() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_shouldReturnFileWithBuildplan();
        // we have a working directory and one directory for each repository
        verify(fileService, times(4)).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
        verify(fileService).schedulePathForDeletion(any(Path.class), eq(5L));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportProgrammingExerciseInstructorMaterial_problemStatementNull() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_problemStatementNull_success();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void exportProgrammingExerciseInstructorMaterial_problemStatementShouldContainTestNames() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_problemStatementShouldContainTestNames();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseInstructorMaterial_failToCreateTempDir() throws Exception {
        try (MockedStatic<Files> mockedFiles = mockStatic(Files.class)) {
            mockedFiles.when(() -> Files.createTempDirectory(any(Path.class), any(String.class))).thenThrow(IOException.class);
            programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial(HttpStatus.INTERNAL_SERVER_ERROR, true, false, false, false);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseInstructorMaterial_embeddedFilesDontExist() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_shouldReturnFile(false, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void exportProgrammingExerciseInstructorMaterialAsTutor_forbidden() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void exportProgrammingExerciseInstructorMaterialAsStudent_forbidden() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testArchiveCourseWithProgrammingExercise() throws Exception {
        programmingExerciseTestService.testArchiveCourseWithProgrammingExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDownloadCourseArchiveAsInstructor() throws Exception {
        programmingExerciseTestService.testDownloadCourseArchiveAsInstructor();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_failToCreateProjectInCi() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_failToCreateProjectInCi();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAutomaticCleanUpBuildPlans() throws Exception {
        programmingExerciseTestService.automaticCleanupBuildPlans();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAutomaticCleanupGitRepositories() {
        programmingExerciseTestService.automaticCleanupGitRepositories();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        programmingExerciseTestService.importProgrammingExerciseFromCourseToCourse_exampleSolutionPublicationDate();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_setValidExampleSolutionPublicationDate() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_setValidExampleSolutionPublicationDate();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExercise_asStudent_exampleSolutionVisibility() throws Exception {
        programmingExerciseTestService.testGetProgrammingExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetProgrammingExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        programmingExerciseTestService.testGetProgrammingExercise_exampleSolutionVisibility(false, TEST_PREFIX + "instructor1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExportSolutionRepository_shouldReturnFileOrForbidden() throws Exception {
        programmingExerciseTestService.exportSolutionRepository_shouldReturnFileOrForbidden();
        // the test has two successful cases, the other times the operation is forbidden --> one successful case has one repository,
        // the other one has two because the tests repository is also included.
        verify(fileService, times(3)).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExportExamSolutionRepository_shouldReturnFileOrForbidden() throws Exception {
        programmingExerciseTestService.exportExamSolutionRepository_shouldReturnFileOrForbidden();
        // the test has two successful cases, the other times the operation is forbidden --> one successful case has one repository,
        // the other one has two because the tests repository is also included.
        verify(fileService, times(3)).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
    }

    // TODO: add startProgrammingExerciseStudentSubmissionFailedWithBuildlog & copyRepository_testConflictError

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testBuildLogStatistics_unauthorized() throws Exception {
        programmingExerciseTestService.buildLogStatistics_unauthorized();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBuildLogStatistics_noStatistics() throws Exception {
        programmingExerciseTestService.buildLogStatistics_noStatistics();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBuildLogStatistics() throws Exception {
        programmingExerciseTestService.buildLogStatistics();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateBuildPlanURL() throws Exception {
        programmingExerciseTestService.updateBuildPlanURL();
    }

    private void forceDefaultBuildPlanCreation() {
        aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.JENKINS);
        aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.JENKINS);
    }
}
