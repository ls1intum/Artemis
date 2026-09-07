package de.tum.cit.aet.artemis.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Documents the URL-level authorization contract for actuator ({@code /management/**}) endpoints: the general
 * {@code /management/**} rule should require {@code ROLE_ADMIN}, while {@code info} and the health group should
 * stay available to the client (including before login) and to probes. {@code /management/env} is used below as
 * a representative path for the general rule; all administrative endpoints share the same matcher.
 * <p>
 * The assertions read the HTTP status <em>before</em> dispatch: Spring Security's authorization filter runs
 * ahead of the DispatcherServlet, so an endpoint that is not mapped in this test slice would still return
 * 401/403 when a rule denies it, versus 404 when a rule permits it. That lets these tests assert the
 * authorization outcome without the actuator endpoints being registered here.
 */
class ManagementEndpointAccessTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private MockMvc mockMvc;

    private int status(String path) throws Exception {
        return mockMvc.perform(get(path)).andReturn().getResponse().getStatus();
    }

    @Test
    @WithMockUser(username = "management-contract-user", roles = { "USER" })
    void generalManagementRuleShouldRequireAdminForNonAdmins() throws Exception {
        // Representative of the general /management/** rule: a non-admin should be denied (403) before dispatch.
        assertThat(status("/management/env")).isEqualTo(403);
    }

    @Test
    @WithMockUser(username = "management-contract-user", roles = { "USER" })
    void infoAndHealthShouldRemainAccessibleForAuthenticatedUsers() throws Exception {
        // info and the health group should stay permitted (not forbidden). 404 here only because the endpoint
        // is unmapped in this slice; the point is that the authorization layer does not deny it.
        assertThat(status("/management/info")).isNotEqualTo(403);
        assertThat(status("/management/health")).isNotEqualTo(403);
    }

    @Test
    @WithAnonymousUser
    void infoShouldRemainAccessibleBeforeLogin() throws Exception {
        // The client fetches /management/info before login, so it should not require authentication.
        assertThat(status("/management/info")).isNotEqualTo(401);
        assertThat(status("/management/info")).isNotEqualTo(403);
    }

    @Test
    @WithAnonymousUser
    void healthGroupShouldRemainAccessibleBeforeLogin() throws Exception {
        // Kubernetes/monitoring probes and the status page hit the health group without a session, so it must stay
        // reachable anonymously. Asserting anonymously (not just for an authenticated user) guards against a regression
        // that silently changes the rule from permitAll() to authenticated().
        for (String path : new String[] { "/management/health", "/management/health/readiness", "/management/health/liveness" }) {
            assertThat(status(path)).isNotEqualTo(401);
            assertThat(status(path)).isNotEqualTo(403);
        }
    }

    /**
     * An issued token cannot be revoked, so the administrator authority it carries only says what was true when it was
     * signed. Actuator endpoints are not served by an annotated handler, so this rule is the only thing standing
     * between a deactivated, deleted or demoted administrator and the management endpoints, and it therefore has to
     * weigh the persisted account rather than the token alone. The mock account below exists in no database, which is
     * what an administrator whose role was revoked looks like to the check.
     */
    @Test
    @WithMockUser(username = "management-contract-revoked-admin", roles = { "ADMIN" })
    void generalManagementRuleShouldRejectAnAdministratorAuthorityThatTheAccountNoLongerHas() throws Exception {
        assertThat(status("/management/env")).isEqualTo(403);
    }

    @Test
    @WithAnonymousUser
    void generalManagementRuleShouldRequireAuthentication() throws Exception {
        // An anonymous caller should be challenged rather than let through.
        assertThat(status("/management/env")).isEqualTo(401);
    }
}
