package de.tum.cit.aet.artemis.core.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;

/**
 * Covers the security context hand-over the executor performs, which is what lets asynchronous work keep the identity
 * of whoever triggered it instead of running with none.
 */
class ExceptionHandlingAsyncTaskExecutorTest {

    private static ExceptionHandlingAsyncTaskExecutor asyncExecutor;

    private static ThreadPoolTaskExecutor delegate;

    @BeforeAll
    static void beforeAll() {
        delegate = new ThreadPoolTaskExecutor();
        // A single worker, so a second task is guaranteed to run on the thread the first one used. That is what makes
        // the leak assertions meaningful rather than accidental.
        delegate.setCorePoolSize(1);
        delegate.setMaxPoolSize(1);
        delegate.afterPropertiesSet();
        asyncExecutor = new ExceptionHandlingAsyncTaskExecutor(delegate);
    }

    @AfterAll
    static void afterAll() {
        delegate.destroy();
    }

    @BeforeEach
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String login) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(login, "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
    }

    private static String loginSeenBy(AtomicReference<Authentication> seen) {
        return seen.get() == null ? null : seen.get().getName();
    }

    @Test
    void testRunnableRunsWithTheSubmittingUsersContext() throws Exception {
        authenticateAs("instructor1");
        AtomicReference<Authentication> seen = new AtomicReference<>();

        asyncExecutor.submit(() -> seen.set(SecurityContextHolder.getContext().getAuthentication())).get();

        assertThat(loginSeenBy(seen)).isEqualTo("instructor1");
    }

    @Test
    void testCallableRunsWithTheSubmittingUsersContext() throws Exception {
        authenticateAs("tutor1");

        Callable<String> task = () -> SecurityContextHolder.getContext().getAuthentication().getName();

        assertThat(asyncExecutor.submit(task).get()).isEqualTo("tutor1");
    }

    @Test
    void testTheWorkerThreadIsLeftWithNoContext() throws Exception {
        authenticateAs("instructor1");
        asyncExecutor.submit(() -> {
        }).get();

        // Submitted with nobody authenticated, so anything this task sees was left behind by the previous one.
        SecurityContextHolder.clearContext();
        AtomicReference<Authentication> seen = new AtomicReference<>();
        asyncExecutor.submit(() -> seen.set(SecurityContextHolder.getContext().getAuthentication())).get();

        assertThat(seen.get()).as("the previous task's principal must not survive on a pooled worker").isNull();
    }

    @Test
    void testAFailingTaskStillClearsTheWorkerThread() throws Exception {
        authenticateAs("instructor1");
        asyncExecutor.submit(() -> {
            throw new IllegalStateException("boom");
        });

        SecurityContextHolder.clearContext();
        AtomicReference<Authentication> seen = new AtomicReference<>();
        asyncExecutor.submit(() -> seen.set(SecurityContextHolder.getContext().getAuthentication())).get();

        assertThat(seen.get()).as("a task that threw must not leave its principal behind either").isNull();
    }

    @Test
    void testSubmittingWithoutAnAuthenticatedUserLeavesTheWorkerUnauthenticated() throws Exception {
        AtomicReference<Authentication> seen = new AtomicReference<>();

        asyncExecutor.submit(() -> seen.set(SecurityContextHolder.getContext().getAuthentication())).get();

        // The async method itself then decides, via SecurityUtils.setAuthorizationObject(), whether to stand in.
        assertThat(seen.get()).isNull();
    }

    @Test
    void testWhatTheTaskInstallsCannotReachTheSubmitter() throws Exception {
        // The push-triggered build path submits with nobody authenticated and then stands in as ROLE_ADMIN. If the two
        // threads shared one context object, that stand-in would land on the submitter as well.
        SecurityContextHolder.clearContext();
        SecurityContext submitterContext = SecurityContextHolder.getContext();

        asyncExecutor.submit(() -> SecurityUtils.setAuthorizationObject()).get();

        assertThat(submitterContext.getAuthentication()).as("the task's stand-in must not be written into the submitter's context").isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).as("the submitting thread must not gain an authentication").isNull();
    }

    @Test
    void testAnInlineTaskLeavesTheSubmittingRequestAuthenticated() throws Exception {
        // Four executors use CallerRunsPolicy, so a saturated pool runs the task on the submitting thread. Clearing
        // afterwards would strip the authentication of the request that submitted it.
        ThreadPoolTaskExecutor saturated = new ThreadPoolTaskExecutor();
        saturated.setCorePoolSize(1);
        saturated.setMaxPoolSize(1);
        saturated.setQueueCapacity(1);
        saturated.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        saturated.afterPropertiesSet();
        try {
            ExceptionHandlingAsyncTaskExecutor executor = new ExceptionHandlingAsyncTaskExecutor(saturated);
            authenticateAs("instructor1");
            CountDownLatch occupyWorker = new CountDownLatch(1);
            // One task holds the single worker, one fills the queue; the third is rejected and runs inline here.
            executor.execute(() -> awaitQuietly(occupyWorker));
            executor.execute(() -> awaitQuietly(occupyWorker));

            AtomicReference<Authentication> seenInline = new AtomicReference<>();
            executor.execute(() -> seenInline.set(SecurityContextHolder.getContext().getAuthentication()));

            assertThat(loginSeenBy(seenInline)).as("the inline task still runs as the submitting user").isEqualTo("instructor1");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).as("the submitting request keeps its authentication").isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("instructor1");
            occupyWorker.countDown();
        }
        finally {
            saturated.destroy();
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
