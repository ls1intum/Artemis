package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.C;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.EMPTY;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.HASKELL;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.JAVA;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.KOTLIN;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.PYTHON;
import static de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage.SWIFT;
import static de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService.STUDENT_LOGIN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.cit.aet.artemis.exam.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.programming.domain.AeolusTarget;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

// TODO: rewrite this test to use LocalVC
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class ProgrammingExerciseLocalVCJenkinsIntegrationTest extends AbstractProgrammingIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "progexlocalvcjenkins";

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(TEST_PREFIX, 0, 0, 0, 0);
        programmingExerciseTestService.setup(this, versionControlService);
        jenkinsRequestMockProvider.enableMockingOfRequests();
        aeolusRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
        jenkinsRequestMockProvider.reset();
        aeolusRequestMockProvider.reset();
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "KOTLIN" }, mode = EnumSource.Mode.INCLUDE)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_sequential_validExercise_created(ProgrammingLanguage programmingLanguage) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_sequential_validExercise_created(programmingLanguage);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_created(ExerciseMode mode) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_mode_validExercise_created(mode);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("de.tum.cit.aet.artemis.programming.util.ArgumentSources#generateJenkinsSupportedLanguages")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_programmingLanguage_validExercise_created(ProgrammingLanguage language) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_programmingLanguage_validExercise_created(language,
                programmingLanguageFeatureService.getProgrammingLanguageFeatures(language));
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("de.tum.cit.aet.artemis.programming.util.ArgumentSources#generateJenkinsSupportedLanguages")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_custom_build_plan_validExercise_created(ProgrammingLanguage language) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_custom_build_plan_validExercise_created(language, true);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("de.tum.cit.aet.artemis.programming.util.ArgumentSources#generateJenkinsSupportedLanguages")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_failed_custom_build_plan_validExercise_created(ProgrammingLanguage language) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_custom_build_plan_validExercise_created(language, false);
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_bonusPointsIsNull() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_validExercise_bonusPointsIsNull();
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_withStaticCodeAnalysis(ProgrammingLanguage language) throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_validExercise_withStaticCodeAnalysis(language,
                programmingLanguageFeatureService.getProgrammingLanguageFeatures(language));
    }

    // TODO: enable or remove the test
    @Disabled
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

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("generateArgumentsForImportExercise")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_created(ProgrammingLanguage programmingLanguage, boolean recreateBuildPlans, boolean addAuxRepos) throws Exception {
        programmingExerciseTestService.importExercise_created(programmingLanguage, recreateBuildPlans, addAuxRepos);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createAndImportJavaProgrammingExercise(boolean staticCodeAnalysisEnabled) throws Exception {
        forceDefaultBuildPlanCreation();
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createAndImportJavaProgrammingExercise(staticCodeAnalysisEnabled);
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_enablePlanFails() throws Exception {
        programmingExerciseTestService.importExercise_enablePlanFails();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_planDoesntExist() throws Exception {
        programmingExerciseTestService.importExercise_planDoesntExist();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_sca_deactivated() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_scaChange();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExercise_sca_activated() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_scaChange_activated();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseLockRepositorySubmissionPolicy() throws Exception {
        programmingExerciseTestService.testImportProgrammingExerciseLockRepositorySubmissionPolicyChange();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importExerciseNoneSubmissionPolicy() throws Exception {
        programmingExerciseTestService.testImportProgrammingExerciseNoneSubmissionPolicyChange();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_structureOracle() throws Exception {
        forceDefaultBuildPlanCreation();
        programmingExerciseTestService.createProgrammingExercise_validExercise_structureOracle();
    }

    // TODO: enable or remove the test
    @Disabled
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

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void startProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.startProgrammingExercise_correctInitializationState(exerciseMode);
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = "edx_student1", roles = "USER")
    void startProgrammingExerciseEdxUser_correctInitializationState() throws Exception {
        programmingExerciseTestService.startProgrammingExercise_correctInitializationState();
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void createProgrammingExercise_offlineMode(boolean offlineIde) throws Exception {
        programmingExerciseTestService.startProgrammingExercise(offlineIde);
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void createProgrammingExercise_validExercise_noExplicitOfflineMode() throws Exception {
        programmingExerciseTestService.startProgrammingExercise(null);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void resumeProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExercise_correctInitializationState(exerciseMode);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void resumeProgrammingExercise_doesNotExist(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExercise_doesNotExist(exerciseMode);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(exerciseMode, null);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void resumeProgrammingExerciseByTriggeringBuildAsInstructor_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(exerciseMode, SubmissionType.INSTRUCTOR);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void resumeProgrammingExerciseByRecreatingAndTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(exerciseMode, true);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + STUDENT_LOGIN, roles = "USER")
    void resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(exerciseMode, false);
    }

    // TODO: enable or remove the test
    @Disabled
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(exerciseMode);
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        programmingExerciseTestService.repositoryAccessIsAdded_whenStudentIsAddedToTeam();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
        programmingExerciseTestService.repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_mode_changedToIndividual() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_individual_modeChange();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExercise_mode_changedToTeam() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_team_modeChange();
    }

    // TODO: enable or remove the test
    @Disabled
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
        programmingExerciseTestService.configureRepository_testBadRequestError();
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
        doThrow(new IOException("Failed to zip")).when(zipFileService).createTemporaryZipFile(any(Path.class), anyList(), anyLong());
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial(HttpStatus.INTERNAL_SERVER_ERROR, true, false, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseInstructorMaterial_embeddedFilesDontExist() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_shouldReturnFile(false, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExportProgrammingExerciseInstructorMaterial_withTeamConfig() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_withTeamConfig();
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

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void importProgrammingExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        programmingExerciseTestService.importProgrammingExerciseFromCourseToCourse_exampleSolutionPublicationDate();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest();
    }

    // TODO: enable or remove the test
    @Disabled
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
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExportExamSolutionRepository_shouldReturnFileOrForbidden() throws Exception {
        programmingExerciseTestService.exportExamSolutionRepository_shouldReturnFileOrForbidden();
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExportStudentRepository_asStudent_authorized() throws Exception {
        programmingExerciseTestService.exportStudentRepository(true);
        // Two invocations: one for the repository directory; one for the output.
        verify(fileService, times(2)).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testExportStudentRepository_asStudent_unauthorized() throws Exception {
        // The repository does not belong to this student.
        programmingExerciseTestService.exportStudentRepository(false);
    }

    // TODO: enable or remove the test
    @Disabled
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testExportStudentRepository_asTutor() throws Exception {
        programmingExerciseTestService.exportStudentRepository(true);
        verify(fileService, times(2)).scheduleDirectoryPathForRecursiveDeletion(any(Path.class), eq(5L));
    }

    // TODO: add startProgrammingExerciseStudentSubmissionFailedWithBuildlog & copyRepository_testConflictError

    // TODO: enable or remove the test
    @Disabled
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
