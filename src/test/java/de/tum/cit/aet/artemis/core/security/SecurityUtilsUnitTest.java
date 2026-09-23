package de.tum.cit.aet.artemis.core.security;

import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.PASSWORD_MIN_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MAX_LENGTH;
import static de.tum.cit.aet.artemis.core.config.Constants.USERNAME_MIN_LENGTH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;

/**
 * Test class for the {@link SecurityUtils} utility class.
 */
class SecurityUtilsUnitTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testGetCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("admin", "admin"));
        SecurityContextHolder.setContext(securityContext);
        Optional<String> login = SecurityUtils.getCurrentUserLogin();
        assertThat(login).contains("admin");
    }

    @Test
    void testIsAuthenticated() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("admin", "admin"));
        SecurityContextHolder.setContext(securityContext);
        boolean isAuthenticated = SecurityUtils.isAuthenticated();
        assertThat(isAuthenticated).isTrue();
    }

    @Test
    void testAnonymousIsNotAuthenticated() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()));
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("anonymous", "anonymous", authorities));
        SecurityContextHolder.setContext(securityContext);
        boolean isAuthenticated = SecurityUtils.isAuthenticated();
        assertThat(isAuthenticated).isFalse();
    }

    @Test
    void testHasCurrentUserThisAuthority() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("user", "user", authorities));
        SecurityContextHolder.setContext(securityContext);

        assertThat(SecurityUtils.hasCurrentUserThisAuthority(Role.STUDENT.getAuthority())).isTrue();
        assertThat(SecurityUtils.hasCurrentUserThisAuthority(Role.ADMIN.getAuthority())).isFalse();
    }

    @Test
    void testHasCurrentUserAnyOfAuthorities() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("user", "user", authorities));
        SecurityContextHolder.setContext(securityContext);

        assertThat(SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.STUDENT.getAuthority(), Role.ADMIN.getAuthority())).isTrue();
        assertThat(SecurityUtils.hasCurrentUserAnyOfAuthorities(Role.ANONYMOUS.getAuthority(), Role.ADMIN.getAuthority())).isFalse();
    }

    @Test
    void testHasCurrentUserNoneOfAuthorities() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(Role.STUDENT.getAuthority()));
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("user", "user", authorities));
        SecurityContextHolder.setContext(securityContext);

        assertThat(SecurityUtils.hasCurrentUserNoneOfAuthorities(Role.STUDENT.getAuthority(), Role.ADMIN.getAuthority())).isFalse();
        assertThat(SecurityUtils.hasCurrentUserNoneOfAuthorities(Role.ANONYMOUS.getAuthority(), Role.ADMIN.getAuthority())).isTrue();
    }

    @Test
    void testCheckUsernameAndPasswordValidity_validCredentials_doesNotThrow() {
        assertThatCode(() -> SecurityUtils.checkUsernameAndPasswordValidity("validUser", "validPassword123")).doesNotThrowAnyException();
    }

    @Test
    void testCheckUsernameAndPasswordValidity_emptyUsername_isReportedAsMissing() {
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> SecurityUtils.checkUsernameAndPasswordValidity("", "validPassword123"))
                .withMessage("No username provided");
    }

    @Test
    void testCheckUsernameAndPasswordValidity_tooShortUsername_reportsLengthPolicy() {
        String tooShortUsername = "a".repeat(USERNAME_MIN_LENGTH - 1);
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> SecurityUtils.checkUsernameAndPasswordValidity(tooShortUsername, "validPassword123"))
                .withMessage("The username has to be at least " + USERNAME_MIN_LENGTH + " characters long");
    }

    @Test
    void testCheckUsernameAndPasswordValidity_tooLongUsername_reportsLengthPolicy() {
        String tooLongUsername = "a".repeat(USERNAME_MAX_LENGTH + 1);
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> SecurityUtils.checkUsernameAndPasswordValidity(tooLongUsername, "validPassword123"))
                .withMessage("The username has to be less than " + USERNAME_MAX_LENGTH + " characters long");
    }

    @Test
    void testCheckUsernameAndPasswordValidity_emptyPassword_isReportedAsMissing() {
        // An empty/missing password must be reported separately from a too-short one: a credential that is simply absent (e.g. a git client sending a username with no password)
        // is a different situation from one that violates the length policy, and conflating them produces misleading "password too short" log entries for empty requests.
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> SecurityUtils.checkUsernameAndPasswordValidity("validUser", ""))
                .withMessage("No password provided");
    }

    @Test
    void testCheckUsernameAndPasswordValidity_tooShortPassword_reportsLengthPolicy() {
        String tooShortPassword = "a".repeat(PASSWORD_MIN_LENGTH - 1);
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> SecurityUtils.checkUsernameAndPasswordValidity("validUser", tooShortPassword))
                .withMessage("The password has to be at least " + PASSWORD_MIN_LENGTH + " characters long");
    }

    @Test
    void testCheckUsernameAndPasswordValidity_tooLongPassword_reportsLengthPolicy() {
        String tooLongPassword = "a".repeat(PASSWORD_MAX_LENGTH + 1);
        assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> SecurityUtils.checkUsernameAndPasswordValidity("validUser", tooLongPassword))
                .withMessage("The password has to be less than " + PASSWORD_MAX_LENGTH + " characters long");
    }

    @Test
    void testSetAuthorizationObjectKeepsAnAuthenticatedPrincipal() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("instructor1", "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(securityContext);

        SecurityUtils.setAuthorizationObject();

        // The stand-in carries no login, so replacing a real principal would silently strip the identity that
        // auditing and any authorization rule resolving authentication.name rely on for the rest of the request.
        assertThat(SecurityUtils.getCurrentUserLogin()).contains("instructor1");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("instructor1");
    }

    @Test
    void testSetAuthorizationObjectReplacesAnAnonymousPrincipal() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority(Role.ANONYMOUS.getAuthority()))));
        SecurityContextHolder.setContext(securityContext);

        SecurityUtils.setAuthorizationObject();

        // An anonymous request is exactly the case the stand-in exists for.
        assertThat(SecurityUtils.isAuthenticated()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_ADMIN");
    }

    @Test
    void testSetAuthorizationObjectSetsAStandInWhenNothingIsAuthenticated() {
        SecurityContextHolder.clearContext();

        SecurityUtils.setAuthorizationObject();

        assertThat(SecurityUtils.isAuthenticated()).isTrue();
    }

    @Test
    void testSetAuthorizationObjectIsIdempotent() {
        SecurityContextHolder.clearContext();
        SecurityUtils.setAuthorizationObject();
        var firstStandIn = SecurityContextHolder.getContext().getAuthentication();

        SecurityUtils.setAuthorizationObject();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(firstStandIn);
    }

    @Test
    void testMakeAuthorizationObjectReportsTheImpersonatedLogin() {
        // getName() used to be null regardless of the argument, which made the object useless to anything reading
        // authentication.name, including Spring Security expressions.
        assertThat(SecurityUtils.makeAuthorizationObject("student1").getName()).isEqualTo("student1");
        assertThat(SecurityUtils.makeAuthorizationObject("student1").getPrincipal()).isEqualTo("student1");
    }

    @Test
    void testRunAsSystemReplacesAStalePrincipalAndRestoresIt() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Authentication stale = new UsernamePasswordAuthenticationToken("leftover-from-a-previous-task", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        securityContext.setAuthentication(stale);
        SecurityContextHolder.setContext(securityContext);
        List<String> seen = new ArrayList<>();

        SecurityUtils.runAsSystem(() -> seen.add(String.valueOf(SecurityUtils.getCurrentUserLogin().orElse("system"))));

        // A pooled background thread carries leftovers, which are not an identity: the work runs as the system.
        assertThat(seen).containsExactly("system");
        // Restored, so calling this part way through a call chain does not disturb the caller.
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(stale);
    }

    @Test
    void testRunAsSystemRestoresEvenWhenTheWorkThrows() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        Authentication previous = new UsernamePasswordAuthenticationToken("instructor1", "password", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        securityContext.setAuthentication(previous);
        SecurityContextHolder.setContext(securityContext);

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> SecurityUtils.runAsSystem(() -> {
            throw new IllegalStateException("boom");
        }));

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    @Test
    void testSetSystemAuthorizationObjectDiscardsAStalePrincipal() {
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("leftover", "password", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(securityContext);

        SecurityUtils.setSystemAuthorizationObject();

        assertThat(SecurityUtils.getCurrentUserLogin()).isEmpty();
        assertThat(SecurityUtils.isAuthenticated()).isTrue();
    }

    /**
     * Called on every API request by the feature usage interceptor, so it is worth pinning both the precedence order and the
     * cases that must not walk into a wrong answer.
     */
    @Test
    void testGetCurrentUserHighestRolePicksTheHighestPrecedence() {
        authenticateWith(Role.STUDENT.getAuthority(), Role.INSTRUCTOR.getAuthority(), Role.TEACHING_ASSISTANT.getAuthority());

        assertThat(SecurityUtils.getCurrentUserHighestRole()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void testGetCurrentUserHighestRoleRanksSuperAdminAboveAdmin() {
        authenticateWith(Role.ADMIN.getAuthority(), Role.SUPER_ADMIN.getAuthority(), Role.STUDENT.getAuthority());

        assertThat(SecurityUtils.getCurrentUserHighestRole()).isEqualTo(Role.SUPER_ADMIN);
    }

    @Test
    void testGetCurrentUserHighestRoleIgnoresAuthoritiesThatAreNotRoles() {
        authenticateWith("SOMETHING_ELSE", Role.EDITOR.getAuthority());

        assertThat(SecurityUtils.getCurrentUserHighestRole()).isEqualTo(Role.EDITOR);
    }

    @Test
    void testGetCurrentUserHighestRoleFallsBackToAnonymous() {
        assertThat(SecurityUtils.getCurrentUserHighestRole()).isEqualTo(Role.ANONYMOUS);

        authenticateWith("SOMETHING_ELSE");
        assertThat(SecurityUtils.getCurrentUserHighestRole()).isEqualTo(Role.ANONYMOUS);
    }

    private static void authenticateWith(String... authorities) {
        Collection<GrantedAuthority> granted = new ArrayList<>();
        for (String authority : authorities) {
            granted.add(new SimpleGrantedAuthority(authority));
        }
        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(new UsernamePasswordAuthenticationToken("user", "user", granted));
        SecurityContextHolder.setContext(securityContext);
    }
}
