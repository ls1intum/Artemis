package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.account.util.UserFactory.USER_PASSWORD;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.localvc.util.LocalVCTestRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;

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
 * The token tests reactivate the account and show the fetch working again, so a passing test cannot be explained by the
 * token itself having become invalid. The SSH counterpart lives in {@link LocalVCSshIntegrationTest}, where the key-pair
 * and client setup it needs already exists.
 */
class LocalVCAccountStateIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcaccountstate";

    private static final String PERSONAL_TOKEN = "vcs-access-token-for-account-state-test";

    private LocalVCTestRepository assignmentRepository;

    @BeforeEach
    void initRepository() throws Exception {
        assignmentRepository = localVCLocalCITestService.createRepositoryWithWorkingCopy(projectKey1, assignmentRepositorySlug);
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
        // Guarded, because a failure inside initRepository leaves the field unassigned and the resulting
        // NullPointerException here would replace the real cause in the report.
        if (assignmentRepository != null) {
            assignmentRepository.deleteWorkingCopy();
        }
    }

    /**
     * The account state is fixture data, so it is restored unconditionally: the tests below deactivate or soft-delete
     * the user, and an assertion failing before they restore it themselves would otherwise hand a disabled user to every
     * later test in this class.
     */
    @AfterEach
    void restoreAccountState() {
        if (student1 == null) {
            return;
        }
        student1.setActivated(true);
        student1.setDeleted(false);
        userTestRepository.save(student1);
    }

    @Test
    void aValidPersonalTokenWorksWhileTheAccountIsActive() {
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void aDeactivatedAccountCannotUseItsPersonalToken() {
        student1.setActivated(false);
        userTestRepository.save(student1);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        // Reactivating restores access, which shows the rejection came from the account state and not from the token.
        student1.setActivated(true);
        userTestRepository.save(student1);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug);
    }

    @Test
    void aSoftDeletedAccountCannotUseItsPersonalToken() {
        student1.setDeleted(true);
        userTestRepository.save(student1);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        student1.setDeleted(false);
        userTestRepository.save(student1);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, PERSONAL_TOKEN, projectKey1, assignmentRepositorySlug);
    }

    /**
     * The password branch is the only one that already went through the {@code AuthenticationManager}, whose providers
     * reject a user with {@code activated = false}. So this is a regression guard rather than new behaviour: the check now
     * runs before any credential is compared, and a password login must still end in the same error.
     */
    @Test
    void aDeactivatedAccountCannotUseItsPasswordEither() {
        student1.setActivated(false);
        userTestRepository.save(student1);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, USER_PASSWORD, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);
    }

    /**
     * The deleted flag on its own, without deactivation. Today {@code softDeleteUser} anonymizes the account and thereby
     * also deactivates it, so the authentication providers would refuse this user anyway - but they only ever look at
     * {@code activated}, and nothing keeps the two flags tied together. Asserted separately so that the git path keeps
     * refusing a deleted account even if that coupling goes away.
     */
    @Test
    void aSoftDeletedAccountCannotUseItsPasswordEither() {
        student1.setDeleted(true);
        userTestRepository.save(student1);

        localVCLocalCITestService.testFetchReturnsError(assignmentRepository.workingCopy(), student1Login, USER_PASSWORD, projectKey1, assignmentRepositorySlug, NOT_AUTHORIZED);

        student1.setDeleted(false);
        userTestRepository.save(student1);
        localVCLocalCITestService.testFetchSuccessful(assignmentRepository.workingCopy(), student1Login, USER_PASSWORD, projectKey1, assignmentRepositorySlug);
    }
}
