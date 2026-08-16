package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Tests for {@link BuildJobCloneTokenService}.
 * <p>
 * The scope calculation is the security-relevant half: it is what turns "this caller is a build agent" into "this
 * caller is the build of this participation", so a repository missing from it would be one the token wrongly opens,
 * and one wrongly included would be one it should not.
 */
class BuildJobCloneTokenServiceTest {

    private final BuildJobCloneTokenService service = new BuildJobCloneTokenService();

    private static BuildJobQueueItem buildJob(RepositoryInfo repositoryInfo, String cloneToken) {
        var jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, null, null, null, null);
        return new BuildJobQueueItem("job-1", "name", new BuildAgentDTO("agent-1", "address", "display"), 1L, 2L, 3L, 0, 1, null, repositoryInfo, jobTimingInfo, buildConfig, null,
                cloneToken);
    }

    private static RepositoryInfo repositoryInfo(String assignment, String tests, String solution, String... auxiliary) {
        return new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, assignment, tests, solution, auxiliary, new String[auxiliary.length]);
    }

    @Test
    void shouldGenerateDistinctPrefixedTokens() {
        Set<String> tokens = IntStream.range(0, 100).mapToObj(_ -> service.generateCloneToken()).collect(Collectors.toSet());

        assertThat(tokens).as("a token is per build job, so two jobs must never share one").hasSize(100);
        assertThat(tokens).allSatisfy(token -> {
            assertThat(token).startsWith(BuildJobCloneTokenService.CLONE_TOKEN_PREFIX);
            assertThat(token.length()).as("64 random bytes, not bound by the varchar(50) of the user-facing access tokens").isGreaterThan(80);
        });
    }

    @Test
    void shouldMatchOnlyTheExactToken() {
        String token = service.generateCloneToken();
        BuildJobQueueItem job = buildJob(repositoryInfo("a", "b", null), token);

        assertThat(service.tokenMatches(job, token)).isTrue();
        assertThat(service.tokenMatches(job, token + "x")).isFalse();
        assertThat(service.tokenMatches(job, token.substring(0, token.length() - 1))).isFalse();
        assertThat(service.tokenMatches(job, service.generateCloneToken())).isFalse();
    }

    /**
     * A job without a token must never be openable by presenting nothing, which a naive equality check on two nulls
     * would allow.
     */
    @Test
    void shouldNeverMatchWhenEitherSideIsAbsent() {
        BuildJobQueueItem withoutToken = buildJob(repositoryInfo("a", "b", null), null);

        assertThat(service.tokenMatches(withoutToken, "anything")).isFalse();
        assertThat(service.tokenMatches(withoutToken, null)).isFalse();
        assertThat(service.tokenMatches(buildJob(repositoryInfo("a", "b", null), "bjct-x"), null)).isFalse();
        assertThat(service.tokenMatches(null, "bjct-x")).isFalse();
    }

    @Test
    void shouldScopeToEveryRepositoryOfTheJob() {
        BuildJobQueueItem job = buildJob(repositoryInfo("assignment.git", "tests.git", "solution.git", "aux1.git", "aux2.git"), "bjct-x");

        assertThat(service.getRepositoryUris(job)).containsExactlyInAnyOrder("assignment.git", "tests.git", "solution.git", "aux1.git", "aux2.git");
    }

    @Test
    void shouldOmitRepositoriesTheJobDoesNotUse() {
        BuildJobQueueItem job = buildJob(repositoryInfo("assignment.git", "tests.git", null), "bjct-x");

        assertThat(service.getRepositoryUris(job)).as("a job that does not check out the solution must not be able to read it").containsExactlyInAnyOrder("assignment.git",
                "tests.git");
    }

    @Test
    void shouldIgnoreBlankRepositoryUris() {
        BuildJobQueueItem job = buildJob(repositoryInfo("assignment.git", "", "   "), "bjct-x");

        assertThat(service.getRepositoryUris(job)).containsExactly("assignment.git");
    }

    @Test
    void shouldReturnNoRepositoriesForAJobWithoutRepositoryInfo() {
        assertThat(service.getRepositoryUris(null)).isEmpty();
    }
}
