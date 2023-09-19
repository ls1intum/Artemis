package de.tum.in.www1.artemis;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.util.AbstractArtemisIntegrationTest;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

/**
 * This SpringBootTest is used for tests that only require a minimal set of Active Spring Profiles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@Execution(ExecutionMode.CONCURRENT)
@ResourceLock("AbstractSpringIntegrationIndependentTest")
@AutoConfigureEmbeddedDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "scheduling" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationIndependentTest extends AbstractArtemisIntegrationTest {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @AfterEach
    protected void resetSpyBeans() {
        super.resetSpyBeans();
    }

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject) {
        log.debug("Called mockConnectorRequestsForSetup with args {}, {}", exercise, failToCreateCiProject);
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos) {
        log.debug("Called mockConnectorRequestsForImport with args {}, {}, {}, {}", sourceExercise, exerciseToBeImported, recreateBuildPlans, addAuxRepos);
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) {
        log.debug("Called mockConnectorRequestForImportFromFile with args {}", exerciseForImport);
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) {
        log.debug("Called mockImportProgrammingExerciseWithFailingEnablePlan with args {}, {}, {}, {}", sourceExercise, exerciseToBeImported, planExistsInCi, shouldPlanEnableFail);
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        log.debug("Called mockConnectorRequestsForStartParticipation with args {}, {}, {}, {}", exercise, username, users, ltiUserExists);
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        log.debug("Called mockConnectorRequestsForResumeParticipation with args {}, {}, {}, {}", exercise, username, users, ltiUserExists);
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        log.debug("Called mockUpdatePlanRepositoryForParticipation with args {}, {}", exercise, username);
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs) {
        log.debug("Called mockUpdatePlanRepository with args {}, {}, {}, {}", exercise, planName, repoNameInCI, repoNameInVcs);
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) {
        log.debug("Called mockRemoveRepositoryAccess with args {}, {}, {}", exercise, team, firstStudent);
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        log.debug("Called mockCopyRepositoryForParticipation with args {}, {}", exercise, username);
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) {
        log.debug("Called mockRepositoryWritePermissionsForTeam with args {}, {}, {}, {}", team, newStudent, exercise, status);
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) {
        log.debug("Called mockRepositoryWritePermissionsForStudent with args {}, {}, {}", student, exercise, status);
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockRetrieveArtifacts with args {}", participation);
    }

    @Override
    public void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) {
        log.debug("Called mockFetchCommitInfo with args {}, {}, {}", projectKey, repositorySlug, hash);
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockCopyBuildPlan with args {}", participation);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockConfigureBuildPlan with args {}", participation);
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockTriggerFailedBuild with args {}", participation);
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockGrantReadAccess with args {}", participation);
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockNotifyPush with args {}", participation);
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockTriggerParticipationBuild with args {}", participation);
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) {
        log.debug("Called mockTriggerInstructorBuildAll with args {}", participation);
    }

    @Override
    public void resetMockProvider() {
        log.debug("Called resetMockProvider");
    }

    @Override
    public void verifyMocks() {
        log.debug("Called verifyMocks");
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) {
        log.debug("Called mockUpdateUserInUserManagement with args {}, {}, {}, {}", oldLogin, user, password, oldGroups);
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        log.debug("Called mockUpdateCoursePermissions with args {}, {}, {}, {}", updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup);
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) {
        log.debug("Called mockFailUpdateCoursePermissionsInCi with args {}, {}, {}, {}, {}, {}", updatedCourse, oldInstructorGroup, oldEditorGroup, oldTeachingAssistantGroup,
                failToAddUsers, failToRemoveUsers);
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) {
        log.debug("Called mockCreateUserInUserManagement with args {}, {}", user, userExistsInCi);
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) {
        log.debug("Called mockFailToCreateUserInExternalUserManagement with args {}, {}, {}, {}", user, failInVcs, failInCi, failToGetCiUser);
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) {
        log.debug("Called mockDeleteUserInUserManagement with args {}, {}, {}, {}", user, userExistsInUserManagement, failInVcs, failInCi);
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) {
        log.debug("Called mockCreateGroupInUserManagement with args {}", groupName);
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) {
        log.debug("Called mockDeleteGroupInUserManagement with args {}", groupName);
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) {
        log.debug("Called mockAddUserToGroupInUserManagement with args {}, {}, {}", user, group, failInCi);
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) {
        log.debug("Called mockRemoveUserFromGroup with args {}, {}, {}", user, group, failInCi);
    }

    @Override
    public void mockDeleteRepository(String projectKey, String repositoryName, boolean shouldFail) {
        log.debug("Called mockDeleteRepository with args {}, {}, {}", projectKey, repositoryName, shouldFail);
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) {
        log.debug("Called mockDeleteProjectInVcs with args {}, {}", projectKey, shouldFail);
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) {
        log.debug("Called mockDeleteBuildPlan with args {}, {}, {}", projectKey, planName, shouldFail);
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) {
        log.debug("Called mockDeleteBuildPlanProject with args {}, {}", projectKey, shouldFail);
    }

    @Override
    public void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        log.debug("Called mockGetBuildPlan with args {}, {}, {}, {}, {}, {}", projectKey, planName, planExistsInCi, planIsActive, planIsBuilding, failToGetBuild);
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) {
        log.debug("Called mockHealthInCiService with args {}, {}", isRunning, httpStatus);
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        log.debug("Called mockConfigureBuildPlan with args {}, {}", participation, defaultBranch);
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) {
        log.debug("Called mockCheckIfProjectExistsInVcs with args {}, {}", exercise, existsInVcs);
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        log.debug("Called mockCheckIfProjectExistsInCi with args {}, {}, {}", exercise, existsInCi, shouldFail);
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) {
        log.debug("Called mockCheckIfBuildPlanExists with args {}, {}, {}, {}", projectKey, templateBuildPlanId, buildPlanExists, shouldFail);
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl vcsTemplateRepositoryUrl, String projectKey, boolean b) {
        log.debug("Called mockRepositoryUrlIsValid with args {}, {}, {}", vcsTemplateRepositoryUrl, projectKey, b);
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        log.debug("Called mockTriggerBuild with args {}", solutionParticipation);
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        log.debug("Called mockTriggerBuildFailed with args {}", solutionParticipation);
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) {
        log.debug("Called mockSetRepositoryPermissionsToReadOnly with args {}, {}, {}", repositoryUrl, projectKey, users);
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) {
        log.debug("Called mockConfigureRepository with args {}, {}, {}, {}", exercise, participantIdentifier, students, userExists);
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) {
        log.debug("Called mockDefaultBranch with args {}", programmingExercise);
    }

    @Override
    public void mockUserExists(String username) {
        log.debug("Called mockUserExists with args {}", username);
    }
}
