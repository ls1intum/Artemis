package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.account.util.UserFactory.USER_PASSWORD;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Locale;
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

import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.util.ConfigUtil;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.util.RepositoryExportTestUtil;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * This class contains integration tests for edge cases pertaining to the local VC system.
 */
class LocalVCIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcint";

    // Measured baselines for one authenticated git request; a clone or a push is two of these. Upper bounds, so a new
    // query fails the build.
    //
    // The participation-token counts are the ones that matter for exam load: that is what students use. Password
    // authentication is more expensive because it falls through to the authentication manager, which re-reads the user,
    // writes an audit event and stamps the last login date. It is measured too so that path cannot rot unnoticed.
    // Each count below includes one query for the personal VCS access token, which lives in user_vcs_access_token and is
    // compared before the other credentials. Raising any of these numbers means a new query on the git authentication path,
    // which is exactly what this test exists to catch, so establish where it comes from before changing them.
    private static final int GIT_AUTH_QUERY_COUNT = 9;

    private static final int GIT_PUSH_AUTH_QUERY_COUNT = 9;

    private static final int GIT_TOKEN_AUTH_QUERY_COUNT = 6;

    private static final int GIT_TOKEN_PUSH_QUERY_COUNT = 6;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    private LocalVCTestRepository assignmentRepository;

    private LocalVCTestRepository templateRepository;

    private LocalVCTestRepository solutionRepository;

    private LocalVCTestRepository testsRepository;

    @BeforeEach
    void initRepositories() throws Exception {
        // Create assignment repository
        assignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, assignmentRepositorySlug);

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
        assignmentRepository.deleteWorkingCopy();
        templateRepository.deleteWorkingCopy();
        solutionRepository.deleteWorkingCopy();
        testsRepository.deleteWorkingCopy();
        // The helpers above register every repository they hand out, so clear that registry as well instead of letting it grow for the lifetime of the thread.
        RepositoryExportTestUtil.cleanupTrackedRepositories();
    }

    @Test
    void testFetchPush_repositoryDoesNotExist() throws IOException, GitAPIException, URISyntaxException {
        // Create a new repository, delete the remote repository and try to fetch and push to the remote repository.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "some-repository-slug";
        // Create the repository under the same project key the assertions below use, so that deleting it is what makes them fail.
        LocalVCTestRepository someRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, repositorySlug);

        // Delete the remote repository.
        someRepository.bareRepository().close();
        try {
            RepositoryExportTestUtil.safeDeleteDirectory(someRepository.bareRepositoryPath());
        }
        catch (Exception exception) {
            // JGit creates a lock file in each repository that could cause deletion problems.
            if (exception.getMessage().contains("gc.log.lock")) {
                return;
            }
            throw exception;
        }

        // Try to fetch from the remote repository.
        localVCLocalCITestService.testFetchThrowsException(someRepository.workingCopy(), student1Login, USER_PASSWORD, projectKey, repositorySlug, InvalidRemoteException.class,
                "");

        // Try to push to the remote repository.
        localVCLocalCITestService.testPushReturnsError(someRepository.workingCopy(), student1Login, projectKey, repositorySlug, NOT_FOUND);

        // Cleanup
        someRepository.deleteWorkingCopy();
    }

    /**
     * Guards the git authentication and authorization path, which runs on every git request and is therefore the
     * highest-frequency database consumer of an exam.
     * <p>
     * The service method is called directly rather than through a real git fetch because the embedded git server handles
     * the request on its own thread, where the thread-local query interceptor would count nothing.
     * <p>
     * Measured baseline. It used to be eight queries higher: the user was loaded without its authorities and course
     * roles, so every one of the four course-role checks on this path both re-read the whole user row (through
     * AuthorizationCheckService#loadUserIfNeeded, because User#authorities is lazy) and issued its own EXISTS query.
     */
    @Test
    void testAuthenticateAndAuthorizeGitRequestQueryCount() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString((student1Login + ":" + USER_PASSWORD).getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/git/" + projectKey1 + "/" + assignmentRepositorySlug + ".git/info/refs");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);

        assertThatDb(() -> {
            localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
            return null;
        }).hasBeenCalledAtMostTimes(GIT_AUTH_QUERY_COUNT);
    }

    /**
     * The case the exam simulation actually drives: a participation-scoped token belonging to the repository's own
     * student. Password authentication takes a different and more expensive route (it reaches the authentication
     * manager, which re-reads the user, writes an audit event and stamps the last login date), so it is not
     * representative of exam load.
     */
    @Test
    void testAuthenticateAndAuthorizeGitRequestWithParticipationTokenQueryCount() throws Exception {
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        var student = userUtilService.getUserByLogin(student1Login);
        var token = localVCLocalCITestService.getParticipationVcsAccessToken(student, participation.getId()).getVcsAccessToken();
        String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString((student1Login + ":" + token).getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/git/" + projectKey1 + "/" + assignmentRepositorySlug + ".git/info/refs");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);

        assertThatDb(() -> {
            localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ);
            return null;
        }).hasBeenCalledAtMostTimes(GIT_TOKEN_AUTH_QUERY_COUNT);
    }

    /**
     * The push counterpart of the participation-token fetch above.
     */
    @Test
    void testAuthenticateAndAuthorizeGitPushWithParticipationTokenQueryCount() throws Exception {
        var participation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        var student = userUtilService.getUserByLogin(student1Login);
        var token = localVCLocalCITestService.getParticipationVcsAccessToken(student, participation.getId()).getVcsAccessToken();
        String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString((student1Login + ":" + token).getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/git/" + projectKey1 + "/" + assignmentRepositorySlug + ".git/git-receive-pack");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);

        assertThatDb(() -> {
            localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE);
            return null;
        }).hasBeenCalledAtMostTimes(GIT_TOKEN_PUSH_QUERY_COUNT);
    }

    /**
     * The push counterpart of {@link #testAuthenticateAndAuthorizeGitRequestQueryCount}. A push authorizes as WRITE,
     * which additionally resolves whether the participation is locked; measured, that lands on the same count as a
     * fetch, so both paths are pinned at the same number.
     */
    @Test
    void testAuthenticateAndAuthorizeGitPushQueryCount() throws Exception {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        String authorizationHeader = "Basic " + Base64.getEncoder().encodeToString((student1Login + ":" + USER_PASSWORD).getBytes(StandardCharsets.UTF_8));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/git/" + projectKey1 + "/" + assignmentRepositorySlug + ".git/git-receive-pack");
        request.addHeader(HttpHeaders.AUTHORIZATION, authorizationHeader);

        assertThatDb(() -> {
            localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE);
            return null;
        }).hasBeenCalledAtMostTimes(GIT_PUSH_AUTH_QUERY_COUNT);
    }

    @Test
    void testFetchPush_usingVcsAccessToken() {
        var programmingParticipation = localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        var student = userUtilService.getUserByLogin(student1Login);
        var participationVcsAccessToken = localVCLocalCITestService.getParticipationVcsAccessToken(student, programmingParticipation.getId());
        var token = participationVcsAccessToken.getVcsAccessToken();
        programmingExerciseRepository.save(programmingExercise);

        // Fetch from and push to the remote repository with participation VCS access token
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug);

        // Fetch from and push to the remote repository with user VCS access token
        var studentWithToken = userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(student, token, ZonedDateTime.now().plusDays(1));
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug);

        // Try to fetch and push, when token is removed and re-added, which makes the previous token invalid
        userUtilService.deleteUserVcsAccessToken(studentWithToken);
        localVCLocalCITestService.deleteParticipationVcsAccessToken(programmingParticipation.getId());
        localVCLocalCITestService.createParticipationVcsAccessToken(student, programmingParticipation.getId());
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Try to fetch and push with removed participation
        localVCLocalCITestService.deleteParticipationVcsAccessToken(programmingParticipation.getId());
        localVCLocalCITestService.deleteParticipation(programmingParticipation);
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
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
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopy(), student1Login, "wrong-password", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Try to access without a password.
        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopy(), student1Login, "", projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
    }

    @Test
    void testFetchPush_programmingExerciseDoesNotExist() throws GitAPIException, IOException, URISyntaxException {
        // Create a repository for an exercise that does not exist.
        String projectKey = "SOMEPROJECTKEY";
        String repositorySlug = "someprojectkey-some-repository-slug";
        LocalVCTestRepository someRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey, repositorySlug);

        localVCLocalCITestService.testFetchReturnsError(someRepository.workingCopy(), student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(someRepository.workingCopy(), student1Login, projectKey, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        someRepository.deleteWorkingCopy();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFetchPush_offlineIDENotAllowed() {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        programmingExercise.setAllowOfflineIde(false);
        programmingExerciseRepository.save(programmingExercise);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);
        localVCLocalCITestService.testPushReturnsError(assignmentRepository.workingCopy(), student1Login, projectKey1, assignmentRepositorySlug, FORBIDDEN);

        // Teaching assistants and higher should still be able to fetch and push.
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), tutor1Login, projectKey1, assignmentRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(assignmentRepository.workingCopy(), instructor1Login, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void testFetchPush_assignmentRepository_student_noParticipation() throws GitAPIException, IOException, URISyntaxException {
        // Create a new repository, but don't create a participation for student2.
        String repositorySlug = projectKey1.toLowerCase(Locale.ROOT) + "-" + student2Login;
        LocalVCTestRepository student2Repository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, repositorySlug);

        localVCLocalCITestService.testFetchReturnsError(student2Repository.workingCopy(), student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);
        localVCLocalCITestService.testPushReturnsError(student2Repository.workingCopy(), student2Login, projectKey1, repositorySlug, INTERNAL_SERVER_ERROR);

        // Cleanup
        student2Repository.deleteWorkingCopy();
    }

    @Test
    void testFetchPush_templateRepository_noParticipation() {
        // Remove the template participation from the programming exercise.
        programmingExercise.setTemplateParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        templateProgrammingExerciseParticipationRepository.delete(templateParticipation);

        // Instructors should still be able to access the template repository even if the participation record is missing.
        // Authorization is based on the user's course role, not on the existence of a participation.
        localVCLocalCITestService.testFetchSuccessful(templateRepository.workingCopy(), instructor1Login, projectKey1, templateRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(templateRepository.workingCopy(), instructor1Login, projectKey1, templateRepositorySlug);
    }

    @Test
    void testFetchPush_solutionRepository_noParticipation() {
        // Remove the solution participation from the programming exercise.
        programmingExercise.setSolutionParticipation(null);
        programmingExerciseRepository.save(programmingExercise);
        solutionProgrammingExerciseParticipationRepository.delete(solutionParticipation);

        // Instructors should still be able to access the solution repository even if the participation record is missing.
        // Authorization is based on the user's course role, not on the existence of a participation.
        localVCLocalCITestService.testFetchSuccessful(solutionRepository.workingCopy(), instructor1Login, projectKey1, solutionRepositorySlug);
        localVCLocalCITestService.testPushSuccessful(solutionRepository.workingCopy(), instructor1Login, projectKey1, solutionRepositorySlug);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUserTriesToDeleteBranch() throws GitAPIException, URISyntaxException {
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);

        // ":" prefix in the refspec means delete the branch in the remote repository.
        RefSpec refSpec = new RefSpec(":refs/heads/" + defaultBranch);
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.workingCopy().push().setRefSpecs(refSpec).setRemote(repositoryUri).call().iterator().next();
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

    private RemoteRefUpdate setupAndTryForcePush(LocalVCTestRepository originalRepository, String repositoryUri, String login, String projectKey, String repositorySlug)
            throws Exception {

        // Create a second local repository and push a file from there
        Path tempDirectory = tempFileUtilService.createTempDirectory(tempPath, "tempDirectory");
        Git secondLocalGit = Git.cloneRepository().setURI(repositoryUri).setDirectory(tempDirectory.toFile()).call();
        localVCLocalCITestService.commitFile(tempDirectory, secondLocalGit);
        localVCLocalCITestService.testPushSuccessful(secondLocalGit, login, projectKey, repositorySlug);

        // Commit a file to the original local repository
        localVCLocalCITestService.commitFile(originalRepository.workingCopyPath(), originalRepository.workingCopy(), "second-test.txt");

        // Try to push normally, should fail because the remote already contains work that does not exist locally
        PushResult pushResultNormal = originalRepository.workingCopy().push().setRemote(repositoryUri).call().iterator().next();
        RemoteRefUpdate remoteRefUpdateNormal = pushResultNormal.getRemoteUpdates().iterator().next();
        assertThat(remoteRefUpdateNormal.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD);

        // Force push from the original local repository
        PushResult pushResultForce = originalRepository.workingCopy().push().setForce(true).setRemote(repositoryUri).call().iterator().next();
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
        assignmentRepository.workingCopy().branchCreate().setName("new-branch").setStartPoint("refs/heads/" + defaultBranch).call();
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);

        // Push the new branch.
        PushResult pushResult = assignmentRepository.workingCopy().push().setRemote(repositoryUri).setRefSpecs(new RefSpec("refs/heads/new-branch:refs/heads/new-branch")).call()
                .iterator().next();
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
        await().until(() -> assignmentRepository.bareRepositoryPath().toFile().exists());
        await().until(() -> assignmentRepository.workingCopyPath().toFile().exists());

        assignmentRepository.workingCopy().branchCreate().setName("new-branch").setStartPoint("refs/heads/" + defaultBranch).call();
        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);

        // Push the new branch.
        PushResult pushResult = assignmentRepository.workingCopy().push().setRemote(repositoryUri).setRefSpecs(new RefSpec("refs/heads/new-branch:refs/heads/new-branch")).call()
                .iterator().next();
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

        LocalVCRepositoryUri studentAssignmentRepositoryUri1 = new LocalVCRepositoryUri(localVCBaseUri, projectKey1, projectKey1.toLowerCase(Locale.ROOT) + "-" + login1);
        LocalVCRepositoryUri studentAssignmentRepositoryUri2 = new LocalVCRepositoryUri(localVCBaseUri, projectKey1, projectKey1.toLowerCase(Locale.ROOT) + "-" + login2);

        // assert that the URIs are correct
        assertThat(studentAssignmentRepositoryUri1.getURI().toString())
                .isEqualTo(localVCBaseUri + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase(Locale.ROOT) + "-" + login1 + ".git");
        assertThat(studentAssignmentRepositoryUri2.getURI().toString())
                .isEqualTo(localVCBaseUri + "/git/" + projectKey1 + "/" + projectKey1.toLowerCase(Locale.ROOT) + "-" + login2 + ".git");

        // assert that the folder names are correct
        assertThat(studentAssignmentRepositoryUri1.folderNameForRepositoryUri()).isEqualTo(projectKey1 + "/" + projectKey1.toLowerCase(Locale.ROOT) + "-" + login1);
        assertThat(studentAssignmentRepositoryUri2.folderNameForRepositoryUri()).isEqualTo(projectKey1 + "/" + projectKey1.toLowerCase(Locale.ROOT) + "-" + login2);
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
     * A git client that sends a valid username but an empty password (e.g. a remote URL with the username baked in and no credential helper / access token supplying a password)
     * must be reported as a missing credential, not as a password-length violation. This is the most common cause of "Failed login attempt ... password has to be at least ..."
     * noise in production, where the external identity provider's own policy already rules out genuinely too-short passwords.
     */
    @Test
    void testFetch_emptyPassword_reportsNoPasswordProvided() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack", student1Login, "");

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("No password provided");
    }

    /**
     * A non-empty but too-short password is a genuine credential issue (as opposed to a missing one) and must still be reported with the length-policy message, so that it is
     * logged
     * at warn rather than being downgraded to the debug level used for empty-credential probes.
     */
    @Test
    void testFetch_tooShortPassword_reportsLengthPolicy() {
        String tooShortPassword = "a".repeat(PASSWORD_MIN_LENGTH - 1);
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-upload-pack", student1Login, tooShortPassword);

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("at least " + PASSWORD_MIN_LENGTH + " characters long");
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
     * The shared credential shortcut is unreachable on a node that runs local CI, whatever is configured.
     * <p>
     * {@code LocalVCBuildAgentCredentialsValidator} already refuses to start such a node with a credential pair set, so
     * this is the second half of the same guarantee: even a pair that arrives by some other route opens nothing. Every
     * build job here carries a token covering its own assignment, test, solution and auxiliary repositories, so no
     * Artemis build agent has a use for a credential that opens every repository in the installation.
     * <p>
     * The pair still works on a local VC node without local CI, which is the Jenkins with LocalVC setup; that case is
     * covered by {@code LocalVCBuildAgentCredentialsValidatorTest} rather than here, because this test context runs
     * local CI.
     */
    @Test
    void testFetch_buildAgentCredentials_isRejectedWithLocalCi() throws Throwable {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/info/refs", "buildjob_user", "buildjob_password");

        ConfigUtil.testWithChangedConfig(localVCServletService, "useSshForBuildAgent", false,
                () -> ConfigUtil.testWithChangedConfig(localVCServletService, "buildAgentGitUsername", "buildjob_user",
                        () -> ConfigUtil.testWithChangedConfig(localVCServletService, "buildAgentGitPassword", "buildjob_password",
                                () -> assertThatExceptionOfType(LocalVCAuthException.class)
                                        .isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ)))));
    }

    /**
     * Build agent credentials must be refused once the build agents authenticate with an ssh key. They never present
     * this credential pair then, so accepting it would leave a repository-wide read shortcut open that nothing uses.
     * <p>
     * The test context enables ssh for build agents, which is what makes this the ambient configuration here.
     */
    @Test
    void testFetch_buildAgentCredentials_isRejectedWhenBuildAgentsUseSsh() {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/info/refs", "buildjob_user", "buildjob_password");

        // Falls through to normal user authentication, where "buildjob_user" is not a real user
        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    /**
     * Build agent credentials must NOT bypass authentication for WRITE (push) operations.
     * The build agent check only applies to RepositoryActionType.READ.
     */
    @Test
    void testPush_buildAgentCredentials_isRejected() throws Throwable {
        MockHttpServletRequest request = createGitRequest("/git/" + projectKey1 + "/" + templateRepositorySlug + ".git/git-receive-pack", "buildjob_user", "buildjob_password");

        // Enabled deliberately, so that the push is rejected by the READ restriction under test rather than because the
        // ssh configuration of the test context closes the shortcut for every action anyway.
        // Build agent bypass does NOT apply to WRITE — "buildjob_user" is not a real user, so auth fails
        ConfigUtil.testWithChangedConfig(localVCServletService, "useSshForBuildAgent", false, () -> assertThatExceptionOfType(LocalVCAuthException.class)
                .isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE)));
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

        Path largeFile = assignmentRepository.workingCopyPath().resolve("large-file.txt");
        FileUtils.writeByteArrayToFile(largeFile.toFile(), new byte[11 * 1024 * 1024]); // 11 MB

        assignmentRepository.workingCopy().add().addFilepattern("large-file.txt").call();
        GitService.commit(assignmentRepository.workingCopy()).setMessage("Add large file").call();

        String repositoryUri = localVCLocalCITestService.buildLocalVCUri(student1Login, projectKey1, assignmentRepositorySlug);
        PushResult pushResult = assignmentRepository.workingCopy().push().setRemote(repositoryUri)
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

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, expiredToken, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
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

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, token, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
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
