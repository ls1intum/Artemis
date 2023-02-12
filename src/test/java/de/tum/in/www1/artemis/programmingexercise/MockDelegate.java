package de.tum.in.www1.artemis.programmingexercise;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;

import org.gitlab4j.api.GitLabApiException;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.participation.AbstractBaseProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.service.connectors.bamboo.dto.BambooBuildResultDTO;

public interface MockDelegate {

    void mockConnectorRequestsForSetup(ProgrammingExercise exercise, boolean failToCreateCiProject) throws Exception;

    void mockConnectorRequestsForImport(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean recreateBuildPlans, boolean addAuxRepos)
            throws Exception;

    void mockImportProgrammingExerciseWithFailingEnablePlan(ProgrammingExercise sourceExercise, ProgrammingExercise exerciseToBeImported, boolean planExistsInCi,
            boolean shouldPlanEnableFail) throws Exception;

    void mockConnectorRequestsForStartParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists, HttpStatus status) throws Exception;

    void mockConnectorRequestsForResumeParticipation(ProgrammingExercise exercise, String username, Set<User> users, boolean ltiUserExists) throws Exception;

    void mockUpdatePlanRepositoryForParticipation(ProgrammingExercise exercise, String username) throws IOException, URISyntaxException;

    void mockUpdatePlanRepository(ProgrammingExercise exercise, String planName, String repoNameInCI, String repoNameInVcs, List<String> triggeredBy)
            throws IOException, URISyntaxException;

    void mockRemoveRepositoryAccess(ProgrammingExercise exercise, Team team, User firstStudent) throws Exception;

    void mockCopyRepositoryForParticipation(ProgrammingExercise exercise, String username) throws URISyntaxException, IOException, GitLabApiException;

    void mockRepositoryWritePermissionsForTeam(Team team, User newStudent, ProgrammingExercise exercise, HttpStatus status) throws Exception;

    void mockRepositoryWritePermissionsForStudent(User student, ProgrammingExercise exercise, HttpStatus status) throws Exception;

    void mockRetrieveArtifacts(ProgrammingExerciseStudentParticipation participation) throws MalformedURLException, URISyntaxException, JsonProcessingException;

    void mockGetBuildLogs(ProgrammingExerciseStudentParticipation participation, List<BambooBuildResultDTO.BambooBuildLogEntryDTO> logs)
            throws URISyntaxException, JsonProcessingException;

    void mockGetRepositorySlugFromRepositoryUrl(String repositorySlug, VcsRepositoryUrl repositoryUrl);

    void mockGetProjectKeyFromRepositoryUrl(String projectKey, VcsRepositoryUrl repositoryUrl);

    void mockGetRepositoryPathFromRepositoryUrl(String projectPath, VcsRepositoryUrl repositoryUrl);

    void mockGetProjectKeyFromAnyUrl(String projectKey);

    void mockFetchCommitInfo(String projectKey, String repositorySlug, String hash) throws URISyntaxException, JsonProcessingException;

    void mockCopyBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockConfigureBuildPlan(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerFailedBuild(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockGrantReadAccess(ProgrammingExerciseStudentParticipation participation) throws URISyntaxException;

    void mockNotifyPush(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerParticipationBuild(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void mockTriggerInstructorBuildAll(ProgrammingExerciseStudentParticipation participation) throws Exception;

    void resetMockProvider() throws Exception;

    void verifyMocks();

    void mockUpdateUserInUserManagement(String oldLogin, User user, String password, Set<String> oldGroups) throws Exception;

    void mockUpdateCoursePermissions(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup) throws Exception;

    void mockFailUpdateCoursePermissionsInCi(Course updatedCourse, String oldInstructorGroup, String oldEditorGroup, String oldTeachingAssistantGroup, boolean failToAddUsers,
            boolean failToRemoveUsers) throws Exception;

    void mockCreateUserInUserManagement(User user, boolean userExistsInCi) throws Exception;

    void mockFailToCreateUserInExernalUserManagement(User user, boolean failInVcs, boolean failInCi, boolean failToGetCiUser) throws Exception;

    void mockDeleteUserInUserManagement(User user, boolean userExistsInUserManagement, boolean failInVcs, boolean failInCi) throws Exception;

    void mockCreateGroupInUserManagement(String groupName) throws Exception;

    void mockDeleteGroupInUserManagement(String groupName) throws Exception;

    void mockAddUserToGroupInUserManagement(User user, String group, boolean failInCi) throws Exception;

    void mockRemoveUserFromGroup(User user, String group, boolean failInCi) throws Exception;

    void mockDeleteRepository(String projectKey, String repostoryName, boolean shouldFail) throws Exception;

    void mockDeleteProjectInVcs(String projectKey, boolean shouldFail) throws Exception;

    void mockDeleteBuildPlan(String projectKey, String planName, boolean shouldFail) throws Exception;

    void mockDeleteBuildPlanProject(String projectKey, boolean shouldFail) throws Exception;

    void mockGetBuildPlan(String projectKey, String planName, boolean planExistsInCi, boolean planIsActive, boolean planIsBuilding, boolean failToGetBuild) throws Exception;

    void mockHealthInCiService(boolean isRunning, HttpStatus httpStatus) throws Exception;

    void mockConfigureBuildPlan(ProgrammingExerciseParticipation participation, String defaultBranch) throws Exception;

    void mockCheckIfProjectExistsInVcs(ProgrammingExercise exercise, boolean existsInVcs) throws Exception;

    void mockCheckIfProjectExistsInCi(ProgrammingExercise exercise, boolean existsInCi, boolean shouldFail) throws Exception;

    void mockCheckIfBuildPlanExists(String projectKey, String templateBuildPlanId, boolean buildPlanExists, boolean shouldFail) throws Exception;

    void mockRepositoryUrlIsValid(VcsRepositoryUrl vcsTemplateRepositoryUrl, String projectKey, boolean b) throws Exception;

    void mockTriggerBuild(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception;

    void mockTriggerBuildFailed(AbstractBaseProgrammingExerciseParticipation solutionParticipation) throws Exception;

    void mockSetRepositoryPermissionsToReadOnly(VcsRepositoryUrl repositoryUrl, String projectKey, Set<User> users) throws Exception;

    void mockConfigureRepository(ProgrammingExercise exercise, String participantIdentifier, Set<User> students, boolean userExists) throws Exception;

    void mockDefaultBranch(ProgrammingExercise programmingExercise) throws IOException, GitLabApiException;
}
