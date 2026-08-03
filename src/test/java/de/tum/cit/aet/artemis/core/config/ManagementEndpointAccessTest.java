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
 * Documents the intended URL-level authorization contract for actuator ({@code /management/**}) endpoints:
 * the administrative ones should require {@code ROLE_ADMIN}, while {@code info} and the health group should
 * stay available to the client (including before login) and to probes.
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
    @WithMockUser(username = "student-mgmt-probe", roles = { "USER" })
    void administrativeActuatorEndpointsShouldRequireAdminForNonAdmins() throws Exception {
        // A non-admin should be denied (403) before dispatch rather than reaching the endpoint.
        assertThat(status("/management/env")).isEqualTo(403);
        assertThat(status("/management/configprops")).isEqualTo(403);
        assertThat(status("/management/loggers")).isEqualTo(403);
        assertThat(status("/management/threaddump")).isEqualTo(403);
        assertThat(status("/management/logfile")).isEqualTo(403);
    }

    @Test
    @WithMockUser(username = "student-mgmt-probe", roles = { "USER" })
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
    void administrativeActuatorEndpointsShouldRequireAuthentication() throws Exception {
        // An anonymous caller should be challenged rather than let through.
        assertThat(status("/management/env")).isEqualTo(401);
    }
}
