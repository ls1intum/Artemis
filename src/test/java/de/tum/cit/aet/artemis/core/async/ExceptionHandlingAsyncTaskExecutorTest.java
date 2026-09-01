package de.tum.cit.aet.artemis.core.async;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
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
}
