package de.tum.cit.aet.artemis.account.web.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import de.tum.cit.aet.artemis.account.service.user.UserCreationService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminAuthorizationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "adminauthorization";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserCreationService userCreationService;

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
        userCreationService.deactivateUser(userUtilService.getUserByLogin(TEST_PREFIX + "inactiveadmin"));

        mockMvc.perform(get("/api/account/admin/users/authorities")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "updatedadmin", roles = "ADMIN")
    void shouldRejectAdminWithoutCurrentAuthority() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX + "updated");
        userUtilService.addStudent(TEST_PREFIX + "updatedadmin");

        mockMvc.perform(get("/api/account/admin/users/authorities")).andExpect(status().isForbidden());
    }
}
