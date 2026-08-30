package de.tum.cit.aet.artemis.account.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminAuthorizationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "adminauthorization";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(username = TEST_PREFIX + "activeadmin", roles = "ADMIN")
    void shouldAuthorizeActiveAdmin() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "active");

        mockMvc.perform(get("/api/account/admin/users/authorities")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "inactiveadmin", roles = "ADMIN")
    void shouldRejectInactiveAdmin() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "inactive");
        User admin = userUtilService.getUserByLogin(TEST_PREFIX + "inactiveadmin");
        admin.setActivated(false);
        userTestRepository.saveAndFlush(admin);

        mockMvc.perform(get("/api/account/admin/users/authorities")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "updatedadmin", roles = "ADMIN")
    void shouldRejectAdminWithoutCurrentAuthority() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "updated");
        User admin = userUtilService.getUserByLogin(TEST_PREFIX + "updatedadmin");
        admin.setAuthorities(Set.of(Authority.USER_AUTHORITY));
        userTestRepository.saveAndFlush(admin);

        mockMvc.perform(get("/api/account/admin/users/authorities")).andExpect(status().isForbidden());
    }
}
