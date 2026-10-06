package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.async.ExceptionHandlingAsyncTaskExecutor;
import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Tests that the auditor reports the account that actually acted.
 */
class SpringSecurityAuditorAwareTest {

    private final SpringSecurityAuditorAware auditorAware = new SpringSecurityAuditorAware();

    @BeforeEach
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String login) {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken(login, "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testReportsTheAuthenticatedUser() {
        authenticateAs("instructor1");

        assertThat(auditorAware.getCurrentAuditor()).contains("instructor1");
    }

    @Test
    void testFallsBackToTheSystemAccountWhenNobodyIsAuthenticated() {
        assertThat(auditorAware.getCurrentAuditor()).contains(Constants.SYSTEM_ACCOUNT);
    }

    @Test
    void testKeepsTheAuthenticatedUserAfterAStandInIsRequested() {
        authenticateAs("instructor1");

        // Server code reached from a request calls this to satisfy Spring Data on threads that carry no
        // authentication. It used to overwrite the real principal with a stand-in that has no login, so everything
        // saved afterwards in the same request was attributed to the system account instead of the user who acted.
        SecurityUtils.setAuthorizationObject();

        assertThat(auditorAware.getCurrentAuditor()).contains("instructor1");
    }

    @Test
    void testAttributesToTheSystemAccountWhenOnlyAStandInIsPresent() {
        SecurityUtils.setAuthorizationObject();

        assertThat(auditorAware.getCurrentAuditor()).contains(Constants.SYSTEM_ACCOUNT);
    }

    @Test
    void testWorkSubmittedByAUserIsAuditedAsThatUser() throws Exception {
        ThreadPoolTaskExecutor delegate = new ThreadPoolTaskExecutor();
        delegate.setCorePoolSize(1);
        delegate.setMaxPoolSize(1);
        delegate.afterPropertiesSet();
        try {
            ExceptionHandlingAsyncTaskExecutor executor = new ExceptionHandlingAsyncTaskExecutor(delegate);
            authenticateAs("instructor1");

            // The point of propagating the context: work a user triggered is attributed to that user rather than to
            // the system account, which is what an asynchronously saved audited entity would otherwise record.
            assertThat(executor.submit(() -> auditorAware.getCurrentAuditor().orElseThrow()).get()).isEqualTo("instructor1");
        }
        finally {
            delegate.destroy();
        }
    }

    @Test
    void testWorkWithNoSubmittingUserIsAuditedAsTheSystem() throws Exception {
        ThreadPoolTaskExecutor delegate = new ThreadPoolTaskExecutor();
        delegate.setCorePoolSize(1);
        delegate.setMaxPoolSize(1);
        delegate.afterPropertiesSet();
        try {
            ExceptionHandlingAsyncTaskExecutor executor = new ExceptionHandlingAsyncTaskExecutor(delegate);

            assertThat(executor.submit(() -> {
                SecurityUtils.setAuthorizationObject();
                return auditorAware.getCurrentAuditor().orElseThrow();
            }).get()).isEqualTo(Constants.SYSTEM_ACCOUNT);
        }
        finally {
            delegate.destroy();
        }
    }
}
