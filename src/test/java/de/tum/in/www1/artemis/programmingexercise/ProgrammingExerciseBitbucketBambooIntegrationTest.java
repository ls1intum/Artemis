package de.tum.in.www1.artemis.programmingexercise;

import static de.tum.in.www1.artemis.util.ProgrammingExerciseTestService.studentLogin;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.ProgrammingExerciseTestService;

public class ProgrammingExerciseBitbucketBambooIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @BeforeEach
    public void setup() throws Exception {
        programmingExerciseTestService.setupTestUsers(0, 0, 0);
        programmingExerciseTestService.setup(this, versionControlService, continuousIntegrationService);
        bambooRequestMockProvider.enableMockingOfRequests(true);
        bitbucketRequestMockProvider.enableMockingOfRequests(true);
    }

    @AfterEach
    public void tearDown() throws IOException {
        programmingExerciseTestService.tearDown();
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_sequential_validExercise_created() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_sequential_validExercise_created();
    }

    @ParameterizedTest
    @EnumSource(ExerciseMode.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_mode_validExercise_created(ExerciseMode mode) throws Exception {
        programmingExerciseTestService.createProgrammingExercise_mode_validExercise_created(mode);
    }

    @ParameterizedTest
    // TODO René Lalla: incldue Swift again as soon as it is fully supported
    @EnumSource(value = ProgrammingLanguage.class, names = { "SWIFT" }, mode = EnumSource.Mode.EXCLUDE)
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExercise_validExercise_withStaticCodeAnalysis() throws Exception {
        programmingExerciseTestService.createProgrammingExercise_validExercise_withStaticCodeAnalysis();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createProgrammingExerciseForExam_validExercise_created() throws Exception {
        programmingExerciseTestService.createProgrammingExerciseForExam_validExercise_created();
    }

    @ParameterizedTest
    // TODO René Lalla: incldue Swift again as soon as it is fully supported
    @EnumSource(value = ProgrammingLanguage.class, names = { "SWIFT" }, mode = EnumSource.Mode.EXCLUDE)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importExercise_created(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExerciseTestService.importExercise_created(programmingLanguage);
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

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExercise_student_correctInitializationState() throws Exception {
        programmingExerciseTestService.startProgrammingExercise_student_correctInitializationState();
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExercise_team_correctInitializationState() throws Exception {
        programmingExerciseTestService.startProgrammingExercise_team_correctInitializationState();
    }

    @Test
    @WithMockUser(username = studentLogin, roles = "USER")
    public void startProgrammingExerciseStudentSubmissionFailedWithBuildlog() throws Exception {
        programmingExerciseTestService.startProgrammingExerciseStudentSubmissionFailedWithBuildlog();
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_mode_changedToIndividual() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_individual_modeChange();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void importProgrammingExercise_mode_changedToTeam() throws Exception {
        programmingExerciseTestService.testImportProgrammingExercise_team_modeChange();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void configureRepository_createTeamUserWhenLtiUserIsNotExistent() throws Exception {
        programmingExerciseTestService.configureRepository_createTeamUserWhenLtiUserIsNotExistent();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void copyRepository_testInternalServerError() throws Exception {
        programmingExerciseTestService.copyRepository_testInternalServerError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void copyRepository_testBadRequestError() throws Exception {
        programmingExerciseTestService.copyRepository_testBadRequestError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void copyRepository_testConflictError() throws Exception {
        programmingExerciseTestService.copyRepository_testConflictError();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void configureRepository_testBadRequestError() throws Exception {
        programmingExerciseTestService.configureRepository_testBadRequestError();
    }

}
