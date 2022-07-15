package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.programmingexercise.ProgrammingExerciseTestService.studentLogin;
import static de.tum.in.www1.artemis.programmingexercise.ProgrammingSubmissionConstants.BITBUCKET_PUSH_EVENT_REQUEST;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;

import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;

public class ProgrammingExerciseBitbucketBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @BeforeEach
    public void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(0, 0, 0, 0);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.mockDefaultBranch(defaultBranch, programmingExerciseTestService.exercise.getProjectKey());
    }

    @AfterEach
    public void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "VHDL", "ASSEMBLER", "C", "OCAML" }, mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_sequential_validExercise_created(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_sequential_validExercise_created(programmingLanguage);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_mode_validExercise_created(ExerciseMode mode) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_mode_validExercise_created(mode);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ProgrammingLanguage.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_programmingLanguage_validExercise_created(ProgrammingLanguage language) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_programmingLanguage_validExercise_created(language,
                programmingLanguageFeatureService.getProgrammingLanguageFeatures(language));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_validExercise_bonusPointsIsNull() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_validExercise_bonusPointsIsNull();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT", "C" })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_validExercise_withStaticCodeAnalysis(ProgrammingLanguage language) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_validExercise_withStaticCodeAnalysis(language,
                programmingLanguageFeatureService.getProgrammingLanguageFeatures(language));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExerciseForExam_validExercise_created() throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_validExercise_created();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_invalidExercise_dates(dates);
    }

    private static Stream<Arguments> generateArgumentsForImportExercise() {
        return Arrays.stream(ProgrammingLanguage.values()).flatMap(language -> Stream.of(Arguments.of(language, true), Arguments.of(language, false)));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("generateArgumentsForImportExercise")
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_created(ProgrammingLanguage programmingLanguage, boolean recreateBuildPlans) throws Exception {
        programmingExerciseTestService.importExercise_created(programmingLanguage, recreateBuildPlans, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createAndImportJavaProgrammingExercise(boolean staticCodeAnalysisEnabled) throws Exception {
        programmingExerciseTestService.createAndImportJavaProgrammingExercise(staticCodeAnalysisEnabled);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_enablePlanFails() throws Exception {
        programmingExerciseTestService.importExercise_enablePlanFails();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_planDoesntExist() throws Exception {
        programmingExerciseTestService.importExercise_planDoesntExist();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_sca_deactivated() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_scaChange();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_sca_activated() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_scaChange_activated();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_validExercise_structureOracle() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_validExercise_structureOracle();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_noTutors_created() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_noTutors_created();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        for (int i = 1; i <= 12; i++) {
            bitbucketRequestMockProvider.mockUserExists("student" + i);
        }
        programmingExerciseTestService.startProgrammingExercise_correctInitializationState(exerciseMode);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = studentLogin, roles = "USER")
    public void resumeProgrammingExercise_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExercise_correctInitializationState(exerciseMode);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = studentLogin, roles = "USER")
    public void resumeProgrammingExercise_doesNotExist(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExercise_doesNotExist(exerciseMode);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = studentLogin, roles = "USER")
    public void resumeProgrammingExerciseByPushingIntoRepo_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        final String requestAsArtemisUser = BITBUCKET_PUSH_EVENT_REQUEST.replace("\"name\": \"admin\"", "\"name\": \"Artemis\"").replace("\"displayName\": \"Admin\"",
                "\"displayName\": \"Artemis\"");
        Object body = new JSONParser().parse(requestAsArtemisUser);
        programmingExerciseTestService.resumeProgrammingExerciseByPushingIntoRepo_correctInitializationState(exerciseMode, body);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = studentLogin, roles = "USER")
    public void resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(exerciseMode, null);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resumeProgrammingExerciseByTriggeringBuildAsInstructor_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringBuild_correctInitializationState(exerciseMode, SubmissionType.INSTRUCTOR);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = studentLogin, roles = "USER")
    public void resumeProgrammingExerciseByRecreatingAndTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(exerciseMode, true);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = studentLogin, roles = "USER")
    public void resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringFailedBuild_correctInitializationState(exerciseMode, false);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(ExerciseMode exerciseMode) throws Exception {
        programmingExerciseTestService.resumeProgrammingExerciseByTriggeringInstructorBuild_correctInitializationState(exerciseMode);
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExerciseStudentSubmissionFailedWithBuildlog() throws Exception {
        for (int i = 1; i <= 12; i++) {
            bitbucketRequestMockProvider.mockUserExists("student" + i);
        }
        programmingExerciseTestService.startProgrammingExerciseStudentSubmissionFailedWithBuildlog();
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExerciseStudentRetrieveEmptyArtifactPage() throws Exception {
        for (int i = 1; i <= 12; i++) {
            bitbucketRequestMockProvider.mockUserExists("student" + i);
        }
        programmingExerciseTestService.startProgrammingExerciseStudentRetrieveEmptyArtifactPage();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        for (int i = 1; i <= 12; i++) {
            bitbucketRequestMockProvider.mockUserExists("student" + i);
        }
        programmingExerciseTestService.repositoryAccessIsAdded_whenStudentIsAddedToTeam();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
        for (int i = 1; i <= 12; i++) {
            bitbucketRequestMockProvider.mockUserExists("student" + i);
        }
        programmingExerciseTestService.repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_mode_changedToIndividual() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_individual_modeChange();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_mode_changedToTeam() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_team_modeChange();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void copyRepository_testConflictError() throws Exception {
        for (int i = 1; i <= 12; i++) {
            bitbucketRequestMockProvider.mockUserExists("student" + i);
        }
        programmingExerciseTestService.copyRepository_testConflictError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void copyRepository_testNotCreatedError() throws Exception {
        programmingExerciseTestService.copyRepository_testNotCreatedError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void configureRepository_testBadRequestError() throws Exception {
        for (int i = 1; i <= 12; i++) {
            bitbucketRequestMockProvider.mockUserExists("student" + i);
        }
        programmingExerciseTestService.configureRepository_testBadRequestError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void exportInstructorRepositories() throws Exception {
        programmingExerciseTestService.exportInstructorRepositories_shouldReturnFile();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void exportInstructorRepositories_forbidden() throws Exception {
        programmingExerciseTestService.exportInstructorRepositories_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void exportProgrammingExerciseInstructorMaterial() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_shouldReturnFile();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void exportProgrammingExerciseInstructorMaterialAsTutor_forbidden() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testArchiveCourseWithProgrammingExercise() throws Exception {
        programmingExerciseTestService.testArchiveCourseWithProgrammingExercise();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportProgrammingExerciseInstructorMaterial_failToCreateZip() throws Exception {
        doThrow(IOException.class).when(zipFileService).createZipFile(any(Path.class), any(), eq(false));
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial(HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportProgrammingExerciseInstructorMaterial_failToExportRepository() throws Exception {
        doThrow(GitException.class).when(fileService).getUniquePathString(anyString());
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportCourseCannotExportSingleParticipationCanceledException() throws Exception {
        programmingExerciseTestService.testExportCourseCannotExportSingleParticipationCanceledException();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportCourseCannotExportSingleParticipationGitApiException() throws Exception {
        programmingExerciseTestService.testExportCourseCannotExportSingleParticipationGitApiException();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testExportCourseCannotSingleParticipationGitException() throws Exception {
        programmingExerciseTestService.testExportCourseCannotExportSingleParticipationGitException();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDownloadCourseArchiveAsInstructor() throws Exception {
        programmingExerciseTestService.testDownloadCourseArchiveAsInstructor();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAutomaticCleanUpBuildPlans() throws Exception {
        programmingExerciseTestService.automaticCleanupBuildPlans();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAutomaticCleanupGitRepositories() {
        programmingExerciseTestService.automaticCleanupGitRepositories();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExerciseFromCourseToCourse_exampleSolutionPublicationDate() throws Exception {
        programmingExerciseTestService.importProgrammingExerciseFromCourseToCourse_exampleSolutionPublicationDate();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_setInvalidExampleSolutionPublicationDate_badRequest();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_setValidExampleSolutionPublicationDate() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_setValidExampleSolutionPublicationDate();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetProgrammingExercise_asStudent_exampleSolutionVisibility() throws Exception {
        programmingExerciseTestService.testGetProgrammingExercise_exampleSolutionVisibility(true, "student1");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetProgrammingExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        programmingExerciseTestService.testGetProgrammingExercise_exampleSolutionVisibility(false, "instructor1");
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testExportSolutionRepository_shouldReturnFileOrForbidden() throws Exception {
        programmingExerciseTestService.exportSolutionRepository_shouldReturnFileOrForbidden();
    }

}
