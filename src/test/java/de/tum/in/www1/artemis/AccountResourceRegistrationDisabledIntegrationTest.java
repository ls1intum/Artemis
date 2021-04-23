package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.dto.PasswordChangeDTO;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.service.user.UserCreationService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Executes all relevant tests from {@link AccountResourceIntegrationTest} but with registration disabled, expecting Forbidden as response
 */
@SpringBootTest(properties = {"artemis.user-management.registration.enabled=false"})
public class AccountResourceRegistrationDisabledIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {
    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private PasswordService passwordService;

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    public void registerAccount() throws Exception {
        // setup user
        User user = ModelFactory.generateActivatedUser("ab123cd");
        ManagedUserVM userVM = new ManagedUserVM(user);
        userVM.setPassword("password");

        // make request
        request.postWithoutLocation("/api/register", userVM, HttpStatus.FORBIDDEN, null);
    }

    @Test
    public void activateAccount() throws Exception {
        String testActivationKey = "testActivationKey";

        // make request
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("key", testActivationKey);
        request.get("/api/activate", HttpStatus.FORBIDDEN, String.class, params);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void saveAccount() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        // update FirstName
        String updatedFirstName = "UpdatedFirstName";
        createdUser.setFirstName(updatedFirstName);

        // make request
        request.put("/api/account", new UserDTO(createdUser), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "authenticateduser")
    public void changePassword() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        // Password Data
        String updatedPassword = "12345678password-reset-init.component.spec.ts";

        PasswordChangeDTO pwChange = new PasswordChangeDTO(passwordService.decryptPassword(createdUser.getPassword()), updatedPassword);
        // make request
        request.postWithoutLocation("/api/account/change-password", pwChange, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser("authenticateduser")
    public void passwordReset() throws Exception {
        // create user in repo
        User user = ModelFactory.generateActivatedUser("authenticateduser");
        User createdUser = userCreationService.createUser(new ManagedUserVM(user));
        // attempt password reset
        request.postWithoutLocation("/api/account/reset-password/init", createdUser.getEmail(), HttpStatus.FORBIDDEN, null);
    }
}
