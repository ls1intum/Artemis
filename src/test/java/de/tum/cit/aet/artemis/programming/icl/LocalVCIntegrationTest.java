package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.user.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCAuthException;
import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * This class contains integration tests for edge cases pertaining to the local VC system.
 */
class LocalVCIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcint";

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    private LocalRepository assignmentRepository;

    private LocalRepository templateRepository;

    private LocalRepository solutionRepository;

    private LocalRepository testsRepository;

    @BeforeEach
    void initRepositories() throws Exception {
        // Create assignment repository
        assignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);

        // Create and wire base repositories using the shared helper
        var baseRepositories = RepositoryExportTestUtil.createAndWireBaseRepositoriesWithHandles(localVCLocalCITestService, programmingExercise);
        templateRepository = baseRepositories.templateRepository();
        solutionRepository = baseRepositories.solutionRepository();
        testsRepository = baseRepositories.testsRepository();
    }

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @AfterEach
    void removeRepositories() throws IOException {
        assignmentRepository.resetLocalRepo();
        templateRepository.resetLocalRepo();
        solutionRepository.resetLocalRepo();
        testsRepository.resetLocalRepo();
    }

    @Test
    void testFetchPush_repositoryDoesNotExist() throws IOException, GitAPIException, URISyntaxException {
        // Create a new repository, delete the remote repository and try to fetch and push to the remote repository.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "some-repository-slug";
        LocalRepository someRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        // Delete the remote repository.
        someRepository.remoteBareGitRepo.close();
        try {
            RepositoryExportTestUtil.safeDeleteDirectory(someRepository.remoteBareGitRepoFile.toPath());
        }
        catch (Exception exception) {
            // JGit creates a lock file in each repository that could cause deletion problems.
            if (exception.getMessage().contains("gc.log.lock")) {
                return;
            }
            throw exception;
        }

        // Try to fetch from the remote repository.
        localVCLocalCITestService.testFetchThrowsException(someRepository.workingCopyGitRepo, student1Login, USER_PASSWORD, projectKey, repositorySlug,
                InvalidRemoteException.class, "");

        // Try to push to the remote repository.
        localVCLocalCITestService.testPushReturnsError(someRepository.workingCopyGitRepo, student1Login, projectKey, repositorySlug, NOT_FOUND);

        // Cleanup
        someRepository.resetLocalRepo();
    }

    @Test
    void testFetchPush_usingVcsAccessToken() {
        var programmingParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        var student = userUtilService.getUserByLogin(student1Login);
        var participationVcsAccessToken = localVCLocalCITestService.getParticipationVcsAccessToken(student, programmingParticipation.getId());
        var token = participationVcsAccessToken.getVcsAccessToken();
        programmingExerciseRepository.save(programmingExercise);

        // Fetch from and push to the remote repository with participation VCS access token
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug);

        // Fetch from and push to the remote repository with user VCS access token
        var studentWithToken = userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(student, token, ZonedDateTime.now().plusDays(1));
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug);

        // Try to fetch and push, when token is removed and re-added, which makes the previous token invalid
        userUtilService.deleteUserVcsAccessToken(studentWithToken);
        localVCLocalCITestService.deleteParticipationVcsAccessToken(programmingParticipation.getId());
        localVCLocalCITestService.createParticipationVcsAccessToken(student, programmingParticipation.getId());
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Try to fetch and push with removed participation
        localVCLocalCITestService.deleteParticipationVcsAccessToken(programmingParticipation.getId());
        localVCLocalCITestService.deleteParticipation(programmingParticipation);
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
    }

    @Test
    void testFetchPush_wrongCredentials() throws InvalidNameException {
        var student1 = new LdapUserDto().login(getTestPrefix() + "student1");
        student1.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        var fakeUser = new LdapUserDto().login(localVCBaseUsername);
        fakeUser.setUid(new LdapName("cn=" + localVCBaseUsername + ",ou=test,o=lab"));

        doReturn(Optional.of(student1)).when(ldapUserService).findByLogin(student1.getLogin());
        doReturn(Optional.of(fakeUser)).when(ldapUserService).findByLogin(localVCBaseUsername);

        doReturn(false).when(ldapTemplate).compare(anyString(), anyString(), any());

        // Try to access with the wrong password.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug,
                NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, "wrong-password", projectKey1, assignmentRepositorySlug,
                NOT_AUTHORIZED);

        // Try to access without a password.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
    }

    @Test
    void testFetchPush_programmingExerciseDoesNotExist() throws GitAPIException, IOException, URISyntaxException {
        // Create a repository for an exercise that does not exist.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "someprojectkey-some-repository-slug";
        LocalRepository someRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, repositorySlug);

        localVCLocalCITestService.testFetchReturnsError(someRepository.workingCopyGitRepo, student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(someRepository.workingCopyGitRepo, student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        someRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFetchPush_offlineIDENotAllowed() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        programmingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Teaching assistants and higher should still be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.workingCopyGitRepo, instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void testFetchPush_assignmentRepository_student_noParticipation() throws GitAPIException, IOException, URISyntaxException {
        // Create a new repository, but don't create a participation for student2.
        String repositorySlug = projectKey1.toLowerCase() + "-" + student2Login;
        LocalRepository student2Repository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, repositorySlug);

        localVCLocalCITestService.testFetchReturnsError(student2Repository.workingCopyGitRepo, student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(student2Repository.workingCopyGitRepo, student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        student2Repository.resetLocalRepo();
    }

    @Test
    void testFetchPush_templateRepository_noParticipation() {
        // Remove the template participation from the programming exercise.
        programmingExercise.setTemplateParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        templateProgrammingExerciseParticipationRepository.delete(templateParticipation);

        // Instructors should still be able to access the template repository even if the participation record is missing.
        // Authorization is based on the user's course role, not on the existence of a participation.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.workingCopyGitRepo, instructor1Login, projectKey1, templateRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(templateRepository.workingCopyGitRepo, instructor1Login, projectKey1, templateRepositorySlug);
    }

    @Test
    void testFetchPush_solutionRepository_noParticipation() {
        // Remove the solution participation from the programming exercise.
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        solutionProgrammingExerciseParticipationRepository.delete(solutionParticipation);

        // Instructors should still be able to access the solution repository even if the participation record is missing.
        // Authorization is based on the user's course role, not on the existence of a participation.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.workingCopyGitRepo, instructor1Login, projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(solutionRepository.workingCopyGitRepo, instructor1Login, projectKey1, solutionRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserTriesToDeleteBranch() throws GitAPIException, URISyntaxException {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // ":" prefix in the refspec means delete the branch in the remote repository.
        RefSpec refSpec = new RefSpec(":refs/heads/" + defaultBranch);
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.workingCopyGitRepo.push().setRefSpecs(refSpec).setRemote(repositoryUri).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot delete a branch.");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStudentTriesToForcePush() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);

        RemoteRefUpdate remoteRefUpdate = setupAndTryForcePush(assignmentRepository, repositoryUri, student1Login, projectKey1, assignmentRepositorySlug);

        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot force push.");
    }

    // TODO add test for force push over ssh, which should work
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorTriesToForcePushOverHttp() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        String assignmentRepoUri = localVCLocalCITestService.buildLocalVCUri(instructor1Login, projectKey1, assignmentRepositorySlug);
        String templateRepoUri = localVCLocalCITestService.buildLocalVCUri(instructor1Login, projectKey1, templateRepositorySlug);
        String solutionRepoUri = localVCLocalCITestService.buildLocalVCUri(instructor1Login, projectKey1, solutionRepositorySlug);
        String testsRepoUri = localVCLocalCITestService.buildLocalVCUri(instructor1Login, projectKey1, testsRepositorySlug);

        // Force push to assignment repository is allowed for instructors
        RemoteRefUpdate remoteRefUpdate = setupAndTryForcePush(assignmentRepository, assignmentRepoUri, instructor1Login, projectKey1, assignmentRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot force push.");

        // Force push to template repository is allowed for instructors
        remoteRefUpdate = setupAndTryForcePush(templateRepository, templateRepoUri, instructor1Login, projectKey1, templateRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);

        // Force push to solution repository is allowed for instructors
        remoteRefUpdate = setupAndTryForcePush(solutionRepository, solutionRepoUri, instructor1Login, projectKey1, solutionRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);

        // Force push to rests repository is allowed for instructors
        remoteRefUpdate = setupAndTryForcePush(testsRepository, testsRepoUri, instructor1Login, projectKey1, testsRepositorySlug);
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);
    }

    private RemoteRefUpdate setupAndTryForcePush(LocalRepository originalRepository, String repositoryUri, String login, String projectKey, String repositorySlug)
            throws Exception {

        // Create a second local repository and push a file from there
        Path tempDirectory = tempFileUtilService.createTempDirectory(tempPath, "tempDirectory");
        Git secondLocalGit = Git.cloneRepository().setURI(repositoryUri).setDirectory(tempDirectory.toFile()).call();
        localVCLocalCITestService.commitFile(tempDirectory, secondLocalGit);
        localVCLocalCITestService.testPushSuccessful(secondLocalGit, login, projectKey, repositorySlug);

        // Commit a file to the original local repository
        localVCLocalCITestService.commitFile(originalRepository.workingCopyGitRepoFile.toPath(), originalRepository.workingCopyGitRepo, "second-test.txt");

        // Try to push normally, should fail because the remote already contains work that does not exist locally
        PushResult pushResultNormal = originalRepository.workingCopyGitRepo.push().setRemote(repositoryUri).call().iterator().next();
        RemoteRefUpdate remoteRefUpdateNormal = pushResultNormal.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdateNormal.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD);

        // Force push from the original local repository
        PushResult pushResultForce = originalRepository.workingCopyGitRepo.push().setForce(true).setRemote(repositoryUri).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResultForce.getRemoteUpdates().iterator().next();

        // Cleanup
        secondLocalGit.close();
        RepositoryExportTestUtil.safeDeleteDirectory(tempDirectory);

        return remoteRefUpdate;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserCreatesNewBranchBranchingDisallowed() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // Users cannot create new branches.
        assignmentRepository.workingCopyGitRepo.branchCreate().setName("new-branch").setStartPoint("refs/heads/" + defaultBranch).call();
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);

        // Push the new branch.
        PushResult pushResult = assignmentRepository.workingCopyGitRepo.push().setRemote(repositoryUri).setRefSpecs(new RefSpec("refs/heads/new-branch:refs/heads/new-branch"))
                .call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("You cannot push to a branch other than the default branch.");
    }

    void customBranchTestHelper(boolean allowBranching, String regex, boolean shouldSucceed) throws Exception {
        var buildConfig = programmingExerciseBuildConfigRepository.findByProgrammingExerciseId(programmingExercise.getId()).orElseThrow();
        buildConfig.setAllowBranching(allowBranching);
        buildConfig.setBranchRegex(regex);
        programmingExerciseBuildConfigRepository.save(buildConfig);

        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        await().until(() -> programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(programmingExercise.getId(), student1Login).isPresent());
        await().until(() -> assignmentRepository.remoteBareGitRepoFile.exists());
        await().until(() -> assignmentRepository.workingCopyGitRepoFile.exists());

        assignmentRepository.workingCopyGitRepo.branchCreate().setName("new-branch").setStartPoint("refs/heads/" + defaultBranch).call();
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);

        // Push the new branch.
        PushResult pushResult = assignmentRepository.workingCopyGitRepo.push().setRemote(repositoryUri).setRefSpecs(new RefSpec("refs/heads/new-branch:refs/heads/new-branch"))
                .call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();

        if (shouldSucceed) {
            assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);
        }
        else {
            assertThat(remoteRefUpdate.getStatus()).isNotEqualTo(RemoteRefUpdate.Status.OK);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserCreatesCustomBranchAllowedMatchesRegex() throws Exception {
        customBranchTestHelper(true, "^new-branch$", true);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserCreatesCustomBranchDisallowedDoesntMatchRegex() throws Exception {
        customBranchTestHelper(true, "^old-branch$", false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserCreatesCustomBranchDisallowedBranchingDisabled() throws Exception {
        customBranchTestHelper(false, ".*", false);
    }

    @Test
    void testRepositoryFolderName() {
        // we specifically choose logins containing "git" to test it does not accidentally get replaced
        String login1 = "ab123git";
        String login2 = "git123ab";

        LocalVCRepositoryUri studentAssignmentRepositoryUri1 = new LocalVCRepositoryUri(localVCBaseUri, projectKey1, projectKey1.toLowerCase() + "-" + login1);
        LocalVCRepositoryUri studentAssignmentRepositoryUri2 = new LocalVCRepositoryUri(localVCBaseUri, projectKey1, projectKey1.toLowerCase() + "-" + login2);

        // assert that the URIs are correct
        assertThat(studentAssignmentRepositoryUri1.getURI().toString()).isEqualTo(localVCBaseUri + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login1 + ".git");
        assertThat(studentAssignmentRepositoryUri2.getURI().toString()).isEqualTo(localVCBaseUri + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login2 + ".git");

        // assert that the folder names are correct
        assertThat(studentAssignmentRepositoryUri1.folderNameForRepositoryUri()).isEqualTo(projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login1);
        assertThat(studentAssignmentRepositoryUri2.folderNameForRepositoryUri()).isEqualTo(projectKey1 + "/" + projectKey1.toLowerCase() + "-" + login2);
    }

    // --- Security tests: authentication and authorization for git operations ---
    // These tests directly exercise LocalVCServletService.authenticateAndAuthorizeGitRequest
    // with MockHttpServletRequest to verify every branch in the authentication and authorization flow.

    // == Authentication tests: verifying credential validation ==

    @Test
    void testFetch_nonExistentUser_isRejected() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack", "hacker", "randompassword");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void testPush_nonExistentUser_isRejected() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-receive-pack", "hacker", "randompassword");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE));
    }

    @Test
    void testFetch_noAuthorizationHeader_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/git-upload-pack");
        request.setRemoteAddr("127.0.0.1");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    /**
     * Verifies that the dumb HTTP protocol is disabled at the JGit servlet level by sending
     * real HTTP requests to dumb-protocol endpoints (/HEAD, /objects/info/packs).
     * These paths bypass our authentication filters entirely (they are served by JGit's
     * AsIsFileService), so we disable them via {@code setAsIsFileService(AsIsFileService.DISABLED)}
     * in ArtemisGitServletService. Without this, anyone could clone repositories anonymously.
     */
    @Test
    void testDumbHttpProtocol_isDisabledInServlet() throws Exception {
        // Send real HTTP requests to dumb-protocol endpoints on the running server.
        // These must return non-200 (JGit returns 403 when AsIsFileService is DISABLED).
        var headUrl = new java.net.URI("http://localhost:" + port + "/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/HEAD").toURL();
        var headConnection = (java.net.HttpURLConnection) headUrl.openConnection();
        headConnection.setRequestMethod("GET");
        assertThat(headConnection.getResponseCode()).as("Dumb HTTP /HEAD endpoint should be blocked").isGreaterThanOrEqualTo(400);
        headConnection.disconnect();

        var packsUrl = new java.net.URI("http://localhost:" + port + "/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/objects/info/packs").toURL();
        var packsConnection = (java.net.HttpURLConnection) packsUrl.openConnection();
        packsConnection.setRequestMethod("GET");
        assertThat(packsConnection.getResponseCode()).as("Dumb HTTP /objects/info/packs endpoint should be blocked").isGreaterThanOrEqualTo(400);
        packsConnection.disconnect();
    }

    /**
     * Build agent credentials should allow READ (fetch/clone) operations
     * without going through normal user authentication.
     */
    @Test
    void testFetch_buildAgentCredentials_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/info/refs", "buildjob_user", "buildjob_password");

        // Build agent bypass only applies to READ — should succeed without normal user auth
        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
    }

    /**
     * Build agent credentials must NOT bypass authentication for WRITE (push) operations.
     * The build agent check only applies to RepositoryActionType.READ.
     */
    @Test
    void testPush_buildAgentCredentials_isRejected() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-receive-pack", "buildjob_user", "buildjob_password");

        // Build agent bypass does NOT apply to WRITE — "buildjob_user" is not a real user, so auth fails
        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE));
    }

    // == Authorization tests: student access to staff repositories ==
    // Covers checkAccessToStaffRepository: student (not TA) + staff repo type → throws

    @Test
    void testFetch_studentAccessesSolutionRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/git-upload-pack", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("solution");
    }

    @Test
    void testFetch_studentAccessesTemplateRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("exercise");
    }

    @Test
    void testFetch_studentAccessesTestsRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + testsRepositorySlug + ".git/git-upload-pack", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("tests");
    }

    @Test
    void testPush_studentAccessesSolutionRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/git-receive-pack", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE))
                .withMessageContaining("solution");
    }

    @Test
    void testPush_studentAccessesTemplateRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-receive-pack", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE))
                .withMessageContaining("exercise");
    }

    @Test
    void testPush_studentAccessesTestsRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + testsRepositorySlug + ".git/git-receive-pack", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE))
                .withMessageContaining("tests");
    }

    // Verify /info/refs endpoint applies the same rules (consistent across all URL paths)

    @Test
    void testFetch_studentAccessesSolutionRepoViaInfoRefs_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/info/refs", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("solution");
    }

    @Test
    void testFetch_studentAccessesTemplateRepoViaInfoRefs_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/info/refs", student1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("exercise");
    }

    // == Authorization tests: TA access to staff repositories ==
    // Covers checkAccessToStaffRepository: TA + READ → succeeds, TA + WRITE → throws

    @Test
    void testFetch_tutorAccessesSolutionRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/git-upload-pack", tutor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
    }

    @Test
    void testFetch_tutorAccessesTemplateRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack", tutor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
    }

    @Test
    void testFetch_tutorAccessesTestsRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + testsRepositorySlug + ".git/git-upload-pack", tutor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
    }

    @Test
    void testPush_tutorAccessesSolutionRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/git-receive-pack", tutor1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE))
                .withMessageContaining("push");
    }

    @Test
    void testPush_tutorAccessesTemplateRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-receive-pack", tutor1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE))
                .withMessageContaining("push");
    }

    @Test
    void testPush_tutorAccessesTestsRepo_isForbidden() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + testsRepositorySlug + ".git/git-receive-pack", tutor1Login, USER_PASSWORD);

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE))
                .withMessageContaining("push");
    }

    // == Authorization tests: instructor/editor access to staff repositories ==
    // Covers checkAccessToStaffRepository: editor+ → succeeds for both READ and WRITE

    @Test
    void testFetch_instructorAccessesSolutionRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/git-upload-pack", instructor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
    }

    @Test
    void testPush_instructorAccessesSolutionRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + solutionRepositorySlug + ".git/git-receive-pack", instructor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE);
    }

    @Test
    void testFetch_instructorAccessesTemplateRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack", instructor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
    }

    @Test
    void testPush_instructorAccessesTemplateRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-receive-pack", instructor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE);
    }

    @Test
    void testFetch_instructorAccessesTestsRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + testsRepositorySlug + ".git/info/refs", instructor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
    }

    @Test
    void testPush_instructorAccessesTestsRepo_succeeds() throws Exception {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + testsRepositorySlug + ".git/git-receive-pack", instructor1Login, USER_PASSWORD);

        localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE);
    }

    // == Consistency: all URL patterns are authenticated the same way ==

    @Test
    void testFetch_nonExistentUser_isRejectedOnInfoRefs() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/info/refs", "hacker", "randompassword");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void testPush_nonExistentUser_isRejectedOnInfoRefs() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/info/refs", "hacker", "randompassword");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE));
    }

    /**
     * Creates a MockHttpServletRequest with Basic authentication for git endpoints.
     */
    private MockHttpServletRequest createGitRequest(String requestUri, String username, String password) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(requestUri);
        request.setRemoteAddr("127.0.0.1");
        String credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + credentials);
        return request;
    }

    @Test
    void testFilesLargerThan10MbAreRejected() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        Path largeFile = assignmentRepository.workingCopyGitRepoFile.toPath().resolve("large-file.txt");
        FileUtils.writeByteArrayToFile(largeFile.toFile(), new byte[11 * 1024 * 1024]); // 11 MB

        assignmentRepository.workingCopyGitRepo.add().addFilepattern("large-file.txt").call();
        GitService.commit(assignmentRepository.workingCopyGitRepo).setMessage("Add large file").call();

        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.workingCopyGitRepo.push().setRemote(repositoryUri)
                .setRefSpecs(new RefSpec("refs/heads/" + defaultBranch + ":refs/heads/" + defaultBranch)).call().iterator().next();
        RemoteRefUpdate remoteRefUpdate = pushResult.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdate.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
        assertThat(remoteRefUpdate.getMessage()).isEqualTo("File 'large-file.txt' exceeds 10MB size limit (11.00 MB)");
    }

    @Test
    void testFetch_expiredUserVcsAccessToken_isRejected() throws InvalidNameException {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        var student = userUtilService.getUserByLogin(student1Login);
        // Set a VCS access token with an expiry date in the past
        String expiredToken = "vcpat-expired-token-that-is-exactly-50chars-long12";
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(student, expiredToken, ZonedDateTime.now().minusDays(1));

        // Expired token fails user VCS token check, then falls through to participation token check
        // (no match), then to LDAP auth which is mocked to reject
        setupLdapToRejectAuth(student1Login);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, expiredToken, projectKey1, assignmentRepositorySlug,
                NOT_AUTHORIZED);
    }

    @Test
    void testFetch_userVcsAccessTokenWithNullExpiry_isRejected() throws InvalidNameException {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        var student = userUtilService.getUserByLogin(student1Login);
        // Set a VCS access token with null expiry date
        String token = "vcpat-null-expiry-token-exactly-50chars-long-here1";
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(student, token, null);

        // Null expiry date fails user VCS token check, then falls through to participation token check
        // (no match), then to LDAP auth which is mocked to reject
        setupLdapToRejectAuth(student1Login);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
    }

    @Test
    void testAuthHeader_basicWithoutPayload_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack");
        request.setRemoteAddr("127.0.0.1");
        // "Basic" without Base64 payload
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("Invalid authorization header format");
    }

    @Test
    void testAuthHeader_nonBasicScheme_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack");
        request.setRemoteAddr("127.0.0.1");
        // Bearer scheme instead of Basic
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer sometoken");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("Invalid authorization header format");
    }

    @Test
    void testAuthHeader_base64WithoutColon_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack");
        request.setRemoteAddr("127.0.0.1");
        // Base64-encoded string without colon separator
        String encoded = Base64.getEncoder().encodeToString("usernameonly".getBytes());
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("Missing colon");
    }

    @Test
    void testAuthHeader_invalidBase64_isRejected() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack");
        request.setRemoteAddr("127.0.0.1");
        // Invalid Base64 string
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic !!!not-base64!!!");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("Invalid Base64");
    }

    @Test
    void testGetHttpStatusForException_rateLimitExceeded() {
        int status = localVCServletService.getHttpStatusForException(new RateLimitExceededException(60), "/some-repo");
        assertThat(status).isEqualTo(429);
    }

    @Test
    void testGetHttpStatusForException_unknownException() {
        int status = localVCServletService.getHttpStatusForException(new RuntimeException("unexpected"), "/some-repo");
        assertThat(status).isEqualTo(500);
    }

    private void setupLdapToRejectAuth(String login) throws InvalidNameException {
        var ldapUser = new LdapUserDto().login(login);
        String cn = login.replace(TEST_PREFIX, "");
        ldapUser.setUid(new LdapName("cn=" + cn + ",ou=test,o=lab"));
        doReturn(Optional.of(ldapUser)).when(ldapUserService).findByLogin(login);
        doReturn(false).when(ldapTemplate).compare(anyString(), anyString(), any());
    }
}
