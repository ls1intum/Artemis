package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.account.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

import de.tum.cit.aet.artemis.account.repository.PersonalAccessTokenRepository;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil;
import de.tum.cit.aet.artemis.account.util.PersonalAccessTokenTestUtil.SavedToken;
import de.tum.cit.aet.artemis.core.security.allowedScopes.AgentScope;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCAuthException;
import de.tum.cit.aet.artemis.localvc.exception.LocalVCForbiddenException;
import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Git with a personal access token: what it opens and what it never does, however its owner is allowed to use git themselves.
 * <p>
 * The token is the password of the HTTPS credentials. The tests run the same authentication and authorization as a clone or a push, without
 * the transfer.
 */
class LocalVCAgentTokenIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcagenttoken";

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @AfterEach
    void removeTokens() {
        personalAccessTokenRepository.deleteAll();
    }

    @Test
    void aTokenWithTheScopeReadsTheStaffRepositoriesOfAPublishedExercise() throws Exception {
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);

        for (String slug : new String[] { templateRepositorySlug, solutionRepositorySlug, testsRepositorySlug }) {
            localVCServletService.authenticateAndAuthorizeGitRequest(gitRequest(slug, "git-upload-pack", instructor1Login, token.secret()), RepositoryActionType.READ);
        }
    }

    @Test
    void aTokenCannotPushToAPublishedExercise() {
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);

        for (String slug : new String[] { templateRepositorySlug, solutionRepositorySlug, testsRepositorySlug }) {
            var request = gitRequest(slug, "git-receive-pack", instructor1Login, token.secret());
            assertThatExceptionOfType(LocalVCForbiddenException.class)
                    .isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE)).withMessageContaining("visible to students");
        }
    }

    @Test
    void theSameOwnerStillPushesWithAPassword() throws Exception {
        localVCServletService.authenticateAndAuthorizeGitRequest(gitRequest(solutionRepositorySlug, "git-receive-pack", instructor1Login, USER_PASSWORD),
                RepositoryActionType.WRITE);
    }

    @Test
    void aTokenPushesToADraftExercise() throws Exception {
        makeDraft();
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);

        for (String slug : new String[] { templateRepositorySlug, solutionRepositorySlug, testsRepositorySlug }) {
            localVCServletService.authenticateAndAuthorizeGitRequest(gitRequest(slug, "git-receive-pack", instructor1Login, token.secret()), RepositoryActionType.WRITE);
        }
    }

    @Test
    void aTokenCannotPushOnceStudentsHaveStarted() {
        makeDraft();
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        var request = gitRequest(testsRepositorySlug, "git-receive-pack", instructor1Login, token.secret());

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.WRITE))
                .withMessageContaining("participations");
    }

    @Test
    void aTokenWithoutTheScopeOpensNothing() {
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_DRAFT, AgentScope.COURSES_READ);
        var request = gitRequest(solutionRepositorySlug, "git-upload-pack", instructor1Login, token.secret());

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining(AgentScope.PROGRAMMING_REPOSITORIES.getValue());
    }

    @Test
    void aTokenNeverOpensTheRepositoryOfAStudent() {
        makeDraft();
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        var request = gitRequest(assignmentRepositorySlug, "git-upload-pack", instructor1Login, token.secret());

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("not those of students");
    }

    @Test
    void aTokenIsOnlyAcceptedForItsOwner() {
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        var request = gitRequest(solutionRepositorySlug, "git-upload-pack", tutor1Login, token.secret());

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void anUnknownTokenIsNotTriedAsAPassword() {
        var request = gitRequest(solutionRepositorySlug, "git-upload-pack", instructor1Login, "art_pat_" + "x".repeat(43));

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void anExpiredTokenIsRejected() {
        long userId = userUtilService.getUserByLogin(instructor1Login).getId();
        SavedToken token = PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(AgentScope.PROGRAMMING_REPOSITORIES), Duration.ofDays(-1));
        var request = gitRequest(solutionRepositorySlug, "git-upload-pack", instructor1Login, token.secret());

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void aRevokedTokenIsRejected() {
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        assertThat(personalAccessTokenRepository.revoke(token.token().getId(), token.token().getUserId(), Instant.now())).isOne();
        var request = gitRequest(solutionRepositorySlug, "git-upload-pack", instructor1Login, token.secret());

        assertThatExceptionOfType(LocalVCAuthException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void aTokenOfATutorOpensNothingEvenForReading() {
        SavedToken token = tokenOf(tutor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        var request = gitRequest(solutionRepositorySlug, "git-upload-pack", tutor1Login, token.secret());

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("editor");
    }

    @Test
    void aTokenDoesNotWidenTheRoleOfItsOwner() {
        SavedToken token = tokenOf(instructor2Login, AgentScope.PROGRAMMING_REPOSITORIES);
        var request = gitRequest(solutionRepositorySlug, "git-upload-pack", instructor2Login, token.secret());

        // instructor2 is not in the course, so the role of the owner refuses what the scope allows.
        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ));
    }

    @Test
    void gitClonesWithTheTokenAsThePasswordAndRefusesItsPushOnAPublishedExercise() throws Exception {
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        LocalVCTestRepository tests = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug);
        try {
            localVCLocalCITestService.testFetchSuccessful(tests.workingCopy(), instructor1Login, token.secret(), projectKey1, testsRepositorySlug);
            localVCLocalCITestService.testPushReturnsError(tests.workingCopy(), instructor1Login, token.secret(), projectKey1, testsRepositorySlug, FORBIDDEN);
        }
        finally {
            tests.deleteWorkingCopy();
        }
    }

    @Test
    void gitPushesWithTheTokenToADraftExercise() throws Exception {
        makeDraft();
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        LocalVCTestRepository tests = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug);
        try {
            localVCLocalCITestService.testPushSuccessful(tests.workingCopy(), instructor1Login, token.secret(), projectKey1, testsRepositorySlug);
        }
        finally {
            tests.deleteWorkingCopy();
        }
    }

    @Test
    void gitRefusesAForcePushWithTheTokenWhileItsOwnerMayForcePushWithAPassword() throws Exception {
        makeDraft();
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        LocalVCTestRepository tests = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, testsRepositorySlug);
        try {
            localVCLocalCITestService.commitFile(tests.workingCopyPath(), tests.workingCopy(), "first.txt");
            localVCLocalCITestService.testPushSuccessful(tests.workingCopy(), instructor1Login, token.secret(), projectKey1, testsRepositorySlug);
            // Go back before the pushed commit and commit something else: the history now differs from the one on the server
            tests.workingCopy().reset().setMode(ResetCommand.ResetType.HARD).setRef("HEAD~1").call();
            localVCLocalCITestService.commitFile(tests.workingCopyPath(), tests.workingCopy(), "second.txt");

            RemoteRefUpdate withToken = forcePush(tests, instructor1Login, token.secret());
            assertThat(withToken.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
            assertThat(withToken.getMessage()).isEqualTo("A personal access token cannot force push.");

            RemoteRefUpdate withPassword = forcePush(tests, instructor1Login, USER_PASSWORD);
            assertThat(withPassword.getStatus()).isEqualTo(RemoteRefUpdate.Status.OK);
        }
        finally {
            tests.deleteWorkingCopy();
        }
    }

    @Test
    void theRepositoryOfAStudentWhoseLoginIsTheNameOfAStaffRepositoryIsNotOpenedByTheToken() {
        SavedToken token = tokenOf(instructor1Login, AgentScope.PROGRAMMING_REPOSITORIES);
        String practiceOfStudentNamedTests = projectKey1.toLowerCase(Locale.ROOT) + "-practice-tests";
        var request = gitRequest(practiceOfStudentNamedTests, "git-upload-pack", instructor1Login, token.secret());

        assertThatExceptionOfType(LocalVCForbiddenException.class).isThrownBy(() -> localVCServletService.authenticateAndAuthorizeGitRequest(request, RepositoryActionType.READ))
                .withMessageContaining("not those of students");
    }

    private RemoteRefUpdate forcePush(LocalVCTestRepository repository, String login, String password) throws Exception {
        String uri = localVCLocalCITestService.buildLocalVCUri(login, password, projectKey1, testsRepositorySlug);
        PushResult result = repository.workingCopy().push().setForce(true).setRemote(uri).setCredentialsProvider(new UsernamePasswordCredentialsProvider(login, password)).call()
                .iterator().next();
        return result.getRemoteUpdates().iterator().next();
    }

    private void makeDraft() {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusDays(7));
        programmingExerciseRepository.save(programmingExercise);
    }

    private SavedToken tokenOf(String login, AgentScope... scopes) {
        long userId = userUtilService.getUserByLogin(login).getId();
        return PersonalAccessTokenTestUtil.saveToken(personalAccessTokenRepository, userId, Set.of(scopes), Duration.ofDays(1));
    }

    private MockHttpServletRequest gitRequest(String repositorySlug, String service, String username, String password) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/git/" + projectKey1 + "/" + repositorySlug + ".git/" + service);
        request.setRemoteAddr("127.0.0.1");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8)));
        return request;
    }
}
