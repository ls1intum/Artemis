package de.tum.cit.aet.artemis.account.web.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.dto.UserImportDTO;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class AdminUserImportResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "adminuserimport";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordService passwordService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void importUsers_withoutCreateInternal_returnsNotFoundAsBefore() throws Exception {
        User existing = userUtilService.createAndSaveUser(TEST_PREFIX + "existing");
        UserImportDTO existingDto = new UserImportDTO(existing.getLogin(), null, null, null, null, null);
        UserImportDTO missingDto = new UserImportDTO(TEST_PREFIX + "missing", "Mary", "Missing", null, null, null);

        mockMvc.perform(post("/api/account/admin/users/import").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(List.of(existingDto, missingDto))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].login").value(TEST_PREFIX + "missing"));

        assertThat(userUtilService.userExistsWithLogin(TEST_PREFIX + "missing")).isFalse();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void importUsers_createInternalUsers_createsMissingUserWithPassword() throws Exception {
        String newLogin = TEST_PREFIX + "new";
        UserImportDTO newUserDto = new UserImportDTO(newLogin, "Ada", "Lovelace", null, newLogin + "@example.com", "secret123");

        mockMvc.perform(post("/api/account/admin/users/import").param("createInternalUsers", "true").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(newUserDto)))).andExpect(status().isOk()).andExpect(content().json("[]"));

        User created = userUtilService.getUserByLogin(newLogin);
        assertThat(created.isInternal()).isTrue();
        assertThat(created.getActivated()).isTrue();
        assertThat(created.getFirstName()).isEqualTo("Ada");
        assertThat(created.getLastName()).isEqualTo("Lovelace");
        assertThat(created.getEmail()).isEqualTo(newLogin + "@example.com");
        assertThat(passwordService.checkPasswordMatch("secret123", created.getPassword())).isTrue();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void importUsers_createInternalUsers_invalidPasswordReportsPasswordFreeNotImportedUser() throws Exception {
        String newLogin = TEST_PREFIX + "shortpw";
        UserImportDTO tooShortPasswordDto = new UserImportDTO(newLogin, "Short", "Password", null, newLogin + "@example.com", "short");

        mockMvc.perform(post("/api/account/admin/users/import").param("createInternalUsers", "true").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(tooShortPasswordDto)))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].login").value(newLogin)).andExpect(jsonPath("$[0].password").doesNotExist());

        assertThat(userUtilService.userExistsWithLogin(newLogin)).isFalse();
    }
}
