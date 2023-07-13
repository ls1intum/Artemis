package de.tum.in.www1.artemis;

import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
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
import de.tum.in.www1.artemis.util.*;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@ResourceLock(value = "AbstractSpringIntegrationTest")
@AutoConfigureEmbeddedDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "scheduling" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationTest extends AbstractArtemisIntegrationTest {

    @Override
    public void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockConnectorRequestForImportFromFile(ProgrammingExercise exerciseForImport) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockFetchCommitInfo(String projectKey1, String repositorySlug, String hash) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void resetMockProvider() {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void verifyMocks() {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup,
            boolean failToAddUsers, boolean failToRemoveUsers) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockCreateUserInUserManagement(User user, boolean userExistsInCi) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockFailToCreateUserInExternalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockCreateGroupInUserManagement(String groupName) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockDeleteGroupInUserManagement(String groupName) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws Exception {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockDeleteRepository(String projectKey1, String repositoryName, boolean shouldFail) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockDeleteProjectInVcs(String projectKey1, boolean shouldFail) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockDeleteBuildPlan(String projectKey1, String planName, boolean shouldFail) throws Exception {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockDeleteBuildPlanProject(String projectKey1, boolean shouldFail) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockGetBuildPlan(String projectKey1, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockCheckIfBuildPlanExists(String projectKey1, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockRepositoryUrlIsValid(VcsRepositoryUrl vcsTemplateRepositoryUrl, String projectKey1, boolean b) throws Exception {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey1, Set<User> users) throws Exception {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockDefaultBranch(ProgrammingExercise programmingExercise) throws IOException, GitLabApiException {
        // Not implemented for AbstractSpringIntegrationTest
    }

    @Override
    public void mockUserExists(String username) throws Exception {
        // Not implemented for AbstractSpringIntegrationTest
    }
}
