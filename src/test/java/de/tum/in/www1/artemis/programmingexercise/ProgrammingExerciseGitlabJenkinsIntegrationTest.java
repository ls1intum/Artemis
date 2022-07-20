package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage.*;
import static de.tum.in.www1.artemis.programmingexercise.ProgrammingExerciseTestService.studentLogin;
import static de.tum.in.www1.artemis.programmingexercise.ProgrammingSubmissionConstants.GITLAB_PUSH_EVENT_REQUEST;

import java.util.Arrays;
import java.util.stream.Stream;

import org.json.simple.parser.JSONParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.enumeration.SubmissionType;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;

class ProgrammingExerciseGitlabJenkinsIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @BeforeEach
    void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(0, 0, 0, 0);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);
        gitlabRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
        gitlabRequestMockProvider.reset();
        jenkinsRequestMockProvider.reset();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "KOTLIN" }, mode = EnumSource.Mode.INCLUDE)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_sequential_validExercise_created(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_sequential_validExercise_created(programmingLanguage);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createProgrammingExercise_validExercise_created(ExerciseMode mode) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_mode_validExercise_created(mode);
    }

    // TODO: Add template for VHDL, Assembler, Haskell and Ocaml and activate those languages here again
    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(value = ProgrammingLanguage.class, names = { "VHDL", "ASSEMBLER", "HASKELL", "OCAML" }, mode = EnumSource.Mode.EXCLUDE)
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
    @EnumSource(value = ProgrammingLanguage.class, names = { "JAVA", "SWIFT" })
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExerciseForExam_datesSet() throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_DatesSet();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
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
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_created(ProgrammingLanguage programmingLanguage, boolean recreateBuildPlans, boolean addAuxRepos) throws Exception {
        programmingExerciseTestService.importExercise_created(programmingLanguage, recreateBuildPlans, addAuxRepos);
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
        programmingExerciseTestService.startProgrammingExercise_correctInitializationState(exerciseMode);
    }

    @Test
    @WithMockUser(username = "edx_student1", roles = "USER")
    public void startProgrammingExerciseEdxUser_correctInitializationState() throws Exception {
        programmingExerciseTestService.startProgrammingExercise_correctInitializationState();
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
        Object body = new JSONParser().parse(GITLAB_PUSH_EVENT_REQUEST);
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
    public void startProgrammingExerciseStudentRetrieveEmptyArtifactPage() throws Exception {
        programmingExerciseTestService.startProgrammingExerciseStudentRetrieveEmptyArtifactPage();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void repositoryAccessIsAdded_whenStudentIsAddedToTeam() throws Exception {
        programmingExerciseTestService.repositoryAccessIsAdded_whenStudentIsAddedToTeam();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void repositoryAccessIsRemoved_whenStudentIsRemovedFromTeam() throws Exception {
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
    public void importProgrammingExercise_asPartOfExamImport() throws Exception {
        programmingExerciseTestService.importProgrammingExerciseAsPartOfExamImport();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void configureRepository_throwExceptionWhenLtiUserIsNotExistent() throws Exception {
        programmingExerciseTestService.configureRepository_throwExceptionWhenLtiUserIsNotExistent();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void copyRepository_testNotCreatedError() throws Exception {
        programmingExerciseTestService.copyRepository_testNotCreatedError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void configureRepository_testBadRequestError() throws Exception {
        gitlabRequestMockProvider.mockGetDefaultBranch(defaultBranch);
        programmingExerciseTestService.configureRepository_testBadRequestError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void exportInstructorRepositories() throws Exception {
        programmingExerciseTestService.exportInstructorRepositories_shouldReturnFile();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void exportInstructorRepositories_forbidden() throws Exception {
        programmingExerciseTestService.exportInstructorRepositories_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void exportProgrammingExerciseInstructorMaterial() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_shouldReturnFile();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void exportProgrammingExerciseInstructorMaterialAsTutor_forbidden() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_forbidden();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void exportProgrammingExerciseInstructorMaterialAsStudent_forbidden() throws Exception {
        programmingExerciseTestService.exportProgrammingExerciseInstructorMaterial_forbidden();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testArchiveCourseWithProgrammingExercise() throws Exception {
        programmingExerciseTestService.testArchiveCourseWithProgrammingExercise();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDownloadCourseArchiveAsInstructor() throws Exception {
        programmingExerciseTestService.testDownloadCourseArchiveAsInstructor();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_failToCreateProjectInCi() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_failToCreateProjectInCi();
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

    // TODO: add startProgrammingExerciseStudentSubmissionFailedWithBuildlog & copyRepository_testConflictError

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testBuildLogStatistics_unauthorized() throws Exception {
        programmingExerciseTestService.buildLogStatistics_unauthorized();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testBuildLogStatistics_noStatistics() throws Exception {
        programmingExerciseTestService.buildLogStatistics_noStatistics();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testBuildLogStatistics() throws Exception {
        programmingExerciseTestService.buildLogStatistics();
    }
}
