package de.tum.cit.aet.artemis.localvc.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;

import org.eclipse.jgit.lib.RefUpdate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Unit tests for {@link GitService#verifyRefUpdateResult(RefUpdate.Result, String, LocalVCRepositoryUri)}.
 * <p>
 * A failed ref update during a bare-repository copy (e.g. LOCK_FAILURE or IO_FAILURE) does not throw on its own.
 * If the result is not checked, the copy returns an "unborn" repository without any branch, which breaks every
 * subsequent access to it with "Cannot check out from unborn branch" until the repository is deleted manually.
 */
class GitServiceRefUpdateResultTest {

    private static final String REF_NAME = "refs/heads/main";

    private static final LocalVCRepositoryUri TARGET_REPO_URI = new LocalVCRepositoryUri("https://artemis.tum.de/git/PROJECTKEY/projectkey-student1.git");

    @ParameterizedTest
    @EnumSource(value = RefUpdate.Result.class, names = { "NEW", "FORCED", "FAST_FORWARD", "NO_CHANGE" })
    void shouldAcceptSuccessfulRefUpdateResults(RefUpdate.Result result) {
        assertThatCode(() -> GitService.verifyRefUpdateResult(result, REF_NAME, TARGET_REPO_URI)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(value = RefUpdate.Result.class, names = { "NEW", "FORCED", "FAST_FORWARD", "NO_CHANGE" }, mode = EnumSource.Mode.EXCLUDE)
    void shouldRejectFailedRefUpdateResults(RefUpdate.Result result) {
        assertThatExceptionOfType(IOException.class).isThrownBy(() -> GitService.verifyRefUpdateResult(result, REF_NAME, TARGET_REPO_URI)).withMessageContaining(result.name())
                .withMessageContaining(REF_NAME);
    }
}
