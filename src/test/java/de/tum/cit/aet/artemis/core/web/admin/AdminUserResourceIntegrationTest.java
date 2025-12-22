package de.tum.cit.aet.artemis.core.web.admin;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminUserResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    // @WithMockUser(username = "superadmin", roles = "SUPER_ADMIN")
    @WithMockUser(roles = "ADMIN")
    void updateUser_setSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        String validPassword = "a".repeat(Math.max(1, Constants.PASSWORD_MIN_LENGTH));

        var managedUserVm = new java.util.HashMap<String, Object>();
        managedUserVm.put("id", 1L);
        managedUserVm.put("login", "user1");
        managedUserVm.put("email", "user1@example.com");
        managedUserVm.put("password", validPassword);
        managedUserVm.put("activated", true);
        managedUserVm.put("authorities", List.of(Authority.SUPER_ADMIN_AUTHORITY.toString()));

        mockMvc.perform(
                put("/api/core/admin/users").with(user("nonSuper").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVm)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUser_revokeSuperAdminByNonSuperAdmin_forbidden() throws Exception {
        String validPassword = "a".repeat(Math.max(1, Constants.PASSWORD_MIN_LENGTH));

        // create and persist an existing super-admin user so controller can load it
        User superUser = userUtilService.createAndSaveUser("superuser");
        superUser.getAuthorities().add(Authority.SUPER_ADMIN_AUTHORITY);
        userTestRepository.saveAndFlush(superUser);

        var managedUserVm = new java.util.HashMap<String, Object>();
        managedUserVm.put("id", superUser.getId());
        managedUserVm.put("login", superUser.getLogin());
        managedUserVm.put("email", superUser.getEmail());
        managedUserVm.put("password", validPassword);
        managedUserVm.put("activated", true);
        managedUserVm.put("authorities", List.of()); // removed super-admin role

        mockMvc.perform(
                put("/api/core/admin/users").with(user("nonSuper").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(managedUserVm)))
                .andExpect(status().isForbidden());
    }
}
