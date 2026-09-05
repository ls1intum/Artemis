package de.tum.cit.aet.artemis.account.authentication;

import static de.tum.cit.aet.artemis.account.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.account.security.LdapAuthenticationProvider;
import de.tum.cit.aet.artemis.account.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class LdapAuthenticationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "ldapauthintegration";

    private static final String INCORRECT_PASSWORD = "incorrectPassword123";

    @Autowired
    protected UserTestRepository userRepository;

    @Autowired
    protected AuthorityRepository authorityRepository;

    @Autowired
    protected LdapAuthenticationProvider ldapAuthenticationProvider;

    private static final String NON_EXISTING_LOGIN = TEST_PREFIX + "na";

    private static final String EMAIL = TEST_PREFIX + "student1@test.de";

    private static final String LOGIN = TEST_PREFIX + "student1";

    private static final String NONEXISTENT_LOGIN = TEST_PREFIX + "student2";

    @BeforeEach
    void setUp() throws InvalidNameException {
        final var userAuthority = new Authority(Role.STUDENT.getAuthority());
        final var instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());
        final var adminAuthority = new Authority(Role.ADMIN.getAuthority());
        final var taAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));

        // The admin endpoints resolve the authenticated login against the database, so the account the tests
        // authenticate as has to exist there with the admin authority rather than only in the mock security context.
        userUtilService.addAdmin(TEST_PREFIX);

        userRepository.findOneByLogin(LOGIN).ifPresent(userRepository::delete);
        userRepository.findOneByLogin(NONEXISTENT_LOGIN).ifPresent(userRepository::delete);
        userRepository.findOneByLogin(TEST_PREFIX + "emailowner").ifPresent(userRepository::delete);

        var ldapUserDTO = new LdapUserDto().login(LOGIN).firstName("Test").lastName("User").email(EMAIL).registrationNumber("12345678");
        ldapUserDTO.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        doReturn(Optional.of(ldapUserDTO)).when(ldapUserService).findByLogin(LOGIN);
        doReturn(Optional.empty()).when(ldapUserService).findByLogin(NONEXISTENT_LOGIN);
        doReturn(Optional.of(ldapUserDTO)).when(ldapUserService).findByAnyEmail(EMAIL);
        doReturn(Optional.empty()).when(ldapUserService).findByAnyEmail("nonexistent@test.de");
        doReturn(true).when(ldapTemplate).authenticate("", "(uid=%s)".formatted(ldapUserDTO.getLogin()), USER_PASSWORD);
        doReturn(false).when(ldapTemplate).authenticate("", "(uid=%s)".formatted(ldapUserDTO.getLogin()), INCORRECT_PASSWORD);
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthentication() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(LOGIN);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), false);
    }

    /**
     * Correct LDAP credentials must not be enough on their own. This provider was the only one that did not consult the
     * account state, so an account an administrator had deactivated could still obtain a web session here while being
     * refused by every other provider and by both git paths.
     */
    @Test
    @WithAnonymousUser
    void testDeactivatedUserCannotAuthenticate() throws Exception {
        // Sign in once so the account exists locally, then deactivate it and retry with the same correct credentials.
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(LOGIN);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");
        request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);

        User user = userRepository.findOneByLogin(LOGIN).orElseThrow();
        user.setActivated(false);
        userRepository.save(user);

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.UNAUTHORIZED, httpHeaders);
        assertThat(response.getCookie("jwt")).as("no session for a deactivated account").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = { "ADMIN" })
    void testImportUsers() throws Exception {
        StudentDTO existingUser = new StudentDTO(new User((long) 1, LOGIN, "", "", "de", ""));
        StudentDTO nonExistingUser = new StudentDTO(new User((long) 1, NON_EXISTING_LOGIN, "", "", "de", ""));
        var output = request.postListWithResponseBody("/api/account/admin/users/import", List.of(existingUser, nonExistingUser), StudentDTO.class, HttpStatus.OK);
        assertThat(output).hasSize(1);
        assertThat(output.getFirst().login()).isEqualTo(NON_EXISTING_LOGIN);
    }

    @Test
    @WithAnonymousUser
    void testIncorrectPasswordAttempt() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(LOGIN);
        loginVM.setPassword(INCORRECT_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.UNAUTHORIZED, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }

    @Test
    @WithAnonymousUser
    void testNonExistentUserNameAttempt() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(NONEXISTENT_LOGIN);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.UNAUTHORIZED, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }

    @Test
    @WithAnonymousUser
    void testEmptyPasswordAttempt() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(LOGIN);
        loginVM.setPassword("");
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        // validation fails due to empty password is validated against min size
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.BAD_REQUEST, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthenticationWithEmail() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(EMAIL);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/core/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), false);
    }

    @Test
    void testAuthenticateInternalUserSkipsLdap() {
        // Create an internal user first
        User internalUser = userUtilService.createAndSaveUser(TEST_PREFIX + "internal");
        internalUser.setInternal(true);
        userRepository.save(internalUser);

        var authentication = new UsernamePasswordAuthenticationToken(internalUser.getLogin(), USER_PASSWORD);
        var result = ldapAuthenticationProvider.authenticate(authentication);

        // Should return null for internal user (skip LDAP, let internal provider handle it)
        assertThat(result).isNull();
    }

    @Test
    void testAuthenticateUpdatesExistingUserInfo() throws InvalidNameException {
        // First authenticate to create the user
        var authentication = new UsernamePasswordAuthenticationToken(LOGIN, USER_PASSWORD);
        var result = ldapAuthenticationProvider.authenticate(authentication);
        assertThat(result).isNotNull();

        // Verify user was created
        var createdUser = userRepository.findOneByLogin(LOGIN);
        assertThat(createdUser).isPresent();
        assertThat(createdUser.get().getFirstName()).isEqualTo("Test");

        // Now update LDAP data and authenticate again
        var updatedLdapUserDTO = new LdapUserDto().login(LOGIN).firstName("UpdatedFirst").lastName("UpdatedLast").email("updated@test.de").registrationNumber("87654321");
        updatedLdapUserDTO.setUid(new LdapName("cn=student1,ou=test,o=lab"));
        doReturn(Optional.of(updatedLdapUserDTO)).when(ldapUserService).findByLogin(LOGIN);

        result = ldapAuthenticationProvider.authenticate(authentication);
        assertThat(result).isNotNull();

        // Verify user info was updated
        var updatedUser = userRepository.findOneByLogin(LOGIN);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("UpdatedFirst");
        assertThat(updatedUser.get().getLastName()).isEqualTo("UpdatedLast");
        assertThat(updatedUser.get().getEmail()).isEqualTo("updated@test.de");
        assertThat(updatedUser.get().getRegistrationNumber()).isEqualTo("87654321");
    }

    @Test
    void testGetUsernameForEmail() {
        var username = ldapAuthenticationProvider.getUsernameForEmail(EMAIL);
        assertThat(username).isPresent();
        assertThat(username.get()).isEqualTo(LOGIN);
    }

    @Test
    void testGetUsernameForNonExistentEmail() {
        var username = ldapAuthenticationProvider.getUsernameForEmail("nonexistent@test.de");
        assertThat(username).isEmpty();
    }

    @Test
    void testSupportsUsernamePasswordAuthenticationToken() {
        assertThat(ldapAuthenticationProvider.supports(UsernamePasswordAuthenticationToken.class)).isTrue();
        assertThat(ldapAuthenticationProvider.supports(Object.class)).isFalse();
    }

    @Test
    void testAuthenticateUpdatesUserWithEmptyRegistrationNumber() throws InvalidNameException {
        // First authenticate to create the user
        var authentication = new UsernamePasswordAuthenticationToken(LOGIN, USER_PASSWORD);
        ldapAuthenticationProvider.authenticate(authentication);

        // Now update LDAP with empty registration number
        var updatedLdapUserDTO = new LdapUserDto().login(LOGIN).firstName("Test").lastName("User").email(EMAIL).registrationNumber("");
        updatedLdapUserDTO.setUid(new LdapName("cn=student1,ou=test,o=lab"));
        doReturn(Optional.of(updatedLdapUserDTO)).when(ldapUserService).findByLogin(LOGIN);

        ldapAuthenticationProvider.authenticate(authentication);

        // Verify registration number was NOT updated (empty string should not override)
        var updatedUser = userRepository.findOneByLogin(LOGIN);
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getRegistrationNumber()).isEqualTo("12345678");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = { "ADMIN" })
    void testManualLdapSyncRejectsEmailUsedByAnotherAccount() throws Exception {
        String originalEmail = TEST_PREFIX + "original@test.de";
        User user = userUtilService.createAndSaveUser(LOGIN);
        user.setEmail(originalEmail);
        user = userRepository.save(user);

        User emailOwner = userUtilService.createAndSaveUser(TEST_PREFIX + "emailowner");
        emailOwner.setEmail(EMAIL);
        userRepository.save(emailOwner);

        request.put("/api/account/admin/users/" + user.getId() + "/sync-ldap", null, HttpStatus.BAD_REQUEST);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isEqualTo(originalEmail);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = { "ADMIN" })
    void testManualLdapSyncClearsEmailWhenDirectoryEmailIsNull() throws Exception {
        User user = userUtilService.createAndSaveUser(LOGIN);
        user.setEmail(TEST_PREFIX + "stale@test.de");
        user = userRepository.save(user);

        var ldapUserWithoutEmail = new LdapUserDto().login(LOGIN).firstName("Test").lastName("User").registrationNumber("12345678");
        doReturn(Optional.of(ldapUserWithoutEmail)).when(ldapUserService).findByLogin(LOGIN);

        request.put("/api/account/admin/users/" + user.getId() + "/sync-ldap", null, HttpStatus.OK);

        assertThat(userRepository.findById(user.getId()).orElseThrow().getEmail()).isNull();
    }
}
