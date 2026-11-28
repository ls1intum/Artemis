package de.tum.cit.aet.artemis.localci.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.dao.DataAccessResourceFailureException;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.BuildJobGitService;
import de.tum.cit.aet.artemis.core.config.LoggingAspect;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Tests for {@link BuildJobCloneTokenService}.
 * <p>
 * The scope calculation is the security-relevant half: it is what turns "this caller is a build agent" into "this
 * caller is the build of this participation", so a repository missing from it would be one the token wrongly opens,
 * and one wrongly included would be one it should not.
 */
class BuildJobCloneTokenServiceTest {

    private final ParticipationTestRepository participationRepository = mock(ParticipationTestRepository.class);

    private final ProgrammingExerciseTestRepository programmingExerciseRepository = mock(ProgrammingExerciseTestRepository.class);

    private final BuildJobCloneTokenService service = BuildJobCloneTokenServiceFixture.service(participationRepository, programmingExerciseRepository);

    private static BuildJobQueueItem buildJob(RepositoryInfo repositoryInfo, String cloneToken) {
        var jobTimingInfo = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, null, null, null, null);
        return new BuildJobQueueItem("job-1", "name", new BuildAgentDTO("agent-1", "address", "display"), 1L, 2L, 3L, 0, 1, null, repositoryInfo, jobTimingInfo, buildConfig, null,
                null, cloneToken);
    }

    private static String uri(String slug) {
        return "http://localhost:8000/git/TESTEXERCISE/testexercise-" + slug + ".git";
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
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student1"), uri("tests"), uri("solution"), uri("aux1"), uri("aux2")), "bjct-x");

        assertThat(service.getRepositoryIdentities(job)).hasSize(5);
        for (String slug : List.of("student1", "tests", "solution", "aux1", "aux2")) {
            assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri(slug)))).as(slug).isTrue();
        }
    }

    @Test
    void shouldOmitRepositoriesTheJobDoesNotUse() {
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student1"), uri("tests"), null), "bjct-x");

        assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("solution")))).as("a job that does not check out the solution must not be able to read it").isFalse();
        assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("student2")))).as("another participant's repository must never be covered").isFalse();
    }

    @Test
    void shouldLeaveOutADeclaredRepositoryThatTheDatabaseDoesNotHoldForTheJob() {
        // the project key and the slug of the declared repositories are data of the job, so they are compared with the database
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student2"), "http://localhost:8000/git/OTHER/other-tests.git", uri("solution")), "bjct-x");

        assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("student2")))).as("the repository of another participation").isFalse();
        assertThat(service.coversRepository(job, new LocalVCRepositoryUri("http://localhost:8000/git/OTHER/other-tests.git"))).as("a repository of another project").isFalse();
        assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("solution")))).as("a repository of the exercise of the job").isTrue();
    }

    @Test
    void shouldCoverNothingWhenTheParticipationDoesNotBelongToTheExerciseOfTheJob() {
        var otherExerciseInfo = repositoryInfo(uri("student1"), uri("tests"), null);
        var timing = new JobTimingInfo(ZonedDateTime.now(), null, null, null, 60);
        var config = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, null, null, null, null);
        BuildJobQueueItem job = new BuildJobQueueItem("job-2", "name", new BuildAgentDTO("agent-1", "address", "display"), BuildJobCloneTokenServiceFixture.PARTICIPATION_ID, 2L,
                99L, 0, 1, null, otherExerciseInfo, timing, config, null, null, "bjct-x");
        when(programmingExerciseRepository.getProgrammingExerciseFromParticipation(any())).thenReturn(exerciseWithId(3L));

        assertThat(service.getRepositoryIdentities(job)).isEmpty();
    }

    @Test
    void shouldReadTheDatabaseOncePerJob() {
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student1"), uri("tests"), null), "bjct-x");

        for (int i = 0; i < 5; i++) {
            assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("student1")))).isTrue();
        }

        verify(participationRepository, times(1)).findWithProgrammingExerciseById(BuildJobCloneTokenServiceFixture.PARTICIPATION_ID);
    }

    @Test
    void shouldNotReadTheDatabaseForARepositoryTheJobDoesNotDeclare() {
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student1"), uri("tests"), null), "bjct-x");

        assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("student2")))).isFalse();

        verify(participationRepository, never()).findWithProgrammingExerciseById(anyLong());
    }

    @Test
    void shouldCoverNothingWhenTheDatabaseCannotBeRead() {
        when(participationRepository.findWithProgrammingExerciseById(anyLong())).thenThrow(new DataAccessResourceFailureException("no connection"));
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student1"), uri("tests"), null), "bjct-x");

        assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("student1")))).isFalse();
    }

    private static ProgrammingExercise exerciseWithId(long id) {
        ProgrammingExercise exercise = new ProgrammingExercise();
        exercise.setId(id);
        return exercise;
    }

    @Test
    void shouldIgnoreBlankRepositoryUris() {
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student1"), "", "   "), "bjct-x");

        assertThat(service.getRepositoryIdentities(job)).hasSize(1);
    }

    @Test
    void shouldReturnNoRepositoriesForAMissingJob() {
        assertThat(service.getRepositoryIdentities(null)).isEmpty();
        assertThat(service.coversRepository(null, new LocalVCRepositoryUri(uri("student1")))).isFalse();
        verifyNoInteractions(participationRepository, programmingExerciseRepository);
    }

    /**
     * The reason the comparison is on project key and slug rather than the full URI: an installation that changed
     * artemis.version-control.url after its participations were created holds persisted URIs with the old base, and a
     * string comparison would deny every clone.
     */
    @Test
    void shouldMatchAcrossAChangedBaseUrl() {
        BuildJobQueueItem job = buildJob(repositoryInfo("http://old-host:8000/git/TESTEXERCISE/testexercise-student1.git", null, null), "bjct-x");

        assertThat(service.coversRepository(job, new LocalVCRepositoryUri("https://new-host/git/TESTEXERCISE/testexercise-student1.git")))
                .as("the same repository behind a renamed server must still be covered").isTrue();
    }

    /**
     * A repository uri that cannot be parsed must cost only itself, not the whole job.
     */
    @Test
    void shouldIgnoreAnUnparsableRepositoryUri() {
        BuildJobQueueItem job = buildJob(repositoryInfo(uri("student1"), "not-a-uri", null), "bjct-x");

        assertThat(service.coversRepository(job, new LocalVCRepositoryUri(uri("student1")))).isTrue();
    }

    /**
     * {@link LoggingAspect} prints every argument and every return value of every {@code @Service} in the application
     * packages, so under the development profile it would write a live clone token into the log twice per build job:
     * once as the result of {@link BuildJobCloneTokenService#generateCloneToken()} and once as the presented
     * credential passed to {@link BuildJobCloneTokenService#tokenMatches}.
     * <p>
     * That is the same exit the masked {@code BuildJobQueueItem.toString()} closes, reached by a different route, and
     * nothing at either end makes the connection visible - which is why the aspect's own pointcut is evaluated here
     * rather than assumed. The expression is read from the advice rather than restated, so a change to it is what this
     * test sees.
     */
    @Test
    void shouldBeExcludedFromTheArgumentLoggingAspect() throws NoSuchMethodException {
        String expression = LoggingAspect.class.getMethod("logAround", ProceedingJoinPoint.class).getAnnotation(Around.class).value();
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setPointcutDeclarationScope(LoggingAspect.class);
        pointcut.setExpression(expression);

        assertThat(pointcut.matches(BuildJobCloneTokenService.class.getMethod("generateCloneToken"), BuildJobCloneTokenService.class))
                .as("a freshly minted clone token must not be logged as the return value of the method that mints it").isFalse();
        assertThat(pointcut.matches(BuildJobCloneTokenService.class.getMethod("tokenMatches", BuildJobQueueItem.class, String.class), BuildJobCloneTokenService.class))
                .as("the presented clone token must not be logged as an argument of the comparison").isFalse();
        assertThat(pointcut.matches(BuildJobGitService.class.getMethod("setCloneTokenForCurrentThread", String.class), BuildJobGitService.class))
                .as("the agent binds the token to its thread once per build job, which would otherwise log it as an argument").isFalse();
        assertThat(pointcut.matches(DistributedDataAccessService.class.getMethod("getProcessingJobs"), DistributedDataAccessService.class))
                .as("the exclusion has to be specific to these services, or it would silently switch off logging for the whole module").isTrue();
    }
}
