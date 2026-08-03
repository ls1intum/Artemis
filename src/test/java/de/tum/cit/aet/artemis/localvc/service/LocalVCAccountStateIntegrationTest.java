package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.account.util.UserFactory.USER_PASSWORD;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

/**
 * Verifies that git authentication honours the account state, for credentials that do not go through the
 * {@code AuthenticationManager}.
 * <p>
 * Web login rejects a deactivated or soft-deleted user on every attempt, because it always runs through the
 * authentication manager. The token branches of the git path did not: they compared the token and returned the user
 * directly, so an administrator deactivating an account - the standard response to a departure, a compromise, or an
 * academic-integrity investigation - left that user full read and write access to their repositories for as long as the
 * token they already held remained valid. During an exam that is also an integrity hole, because an excluded student
 * could still push.
 * <p>
 * Each test reactivates the account afterwards and shows the fetch working again, so a passing test cannot be explained
 * by the token itself having become invalid.
 */
class LocalVCAccountStateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcaccountstate";

    private static final String PERSONAL_TOKEN = "vcs-access-token-for-account-state-test";

    private LocalRepository assignmentRepository;

    @BeforeEach
    void initRepository() throws Exception {
        assignmentRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey1, assignmentRepositorySlug);
        // The student needs a participation for the repository, otherwise the request fails on authorization before the
        // account-state check under test is reached.
        localVCLocalCITestService.createParticipation(programmingExercise, student1Login);
        userUtilService.setUserVcsAccessTokenAndExpiryDateAndSave(student1, PERSONAL_TOKEN, ZonedDateTime.now().plusMonths(6));
    }

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @AfterEach
    void removeRepository() throws IOException {
        assignmentRepository.resetLocalRepo();
    }

    @Test
    void aValidPersonalTokenWorksWhileTheAccountIsActive() {
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void aDeactivatedAccountCannotUseItsPersonalToken() {
        student1.setActivated(false);
        userTestRepository.save(student1);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug,
                NOT_AUTHORIZED);

        // Reactivating restores access, which shows the rejection came from the account state and not from the token.
        student1.setActivated(true);
        userTestRepository.save(student1);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void aSoftDeletedAccountCannotUseItsPersonalToken() {
        student1.setDeleted(true);
        userTestRepository.save(student1);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug,
                NOT_AUTHORIZED);

        student1.setDeleted(false);
        userTestRepository.save(student1);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopyGitRepo, student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void aDeactivatedAccountCannotUseItsPasswordEither() {
        // The password path already went through the authentication manager, so this is a regression guard rather than a
        // new behaviour: the added state check must not have changed which error a password login gets.
        student1.setActivated(false);
        userTestRepository.save(student1);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopyGitRepo, student1Login, USER_PASSWORD, projectKey1, assignmentRepositorySlug,
                NOT_AUTHORIZED);
    }
}
