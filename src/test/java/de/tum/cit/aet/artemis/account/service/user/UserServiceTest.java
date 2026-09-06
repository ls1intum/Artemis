package de.tum.cit.aet.artemis.account.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.Authority;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserRecoveryKeyService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.vm.ManagedUserVM;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class UserServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "userservice";

    @Autowired
    private UserService userService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private UserCreationService userCreationService;

    @Autowired
    private UserRecoveryKeyService userRecoveryKeyService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
    }

    @Test
    void testApplicationReady_createsNewInternalAdminWithSuperAdminRights() {
        // Setup: Configure internal admin credentials
        String testAdminUsername = "test_new_internal_admin";
        String testAdminPassword = "test_password_123";
        String testAdminEmail = "test_admin@example.com";

        ReflectionTestUtils.setField(userService, "artemisInternalAdminUsername", Optional.of(testAdminUsername));
        ReflectionTestUtils.setField(userService, "artemisInternalAdminPassword", Optional.of(testAdminPassword));
        ReflectionTestUtils.setField(userService, "artemisInternalAdminEmail", Optional.of(testAdminEmail));

        // Ensure no user with this username exists
        userRepository.findOneByLogin(testAdminUsername).ifPresent(user -> userRepository.delete(user));

        // Execute: Call applicationReady which should create the internal admin
        userService.applicationReady();

        // Verify: Check that the user was created with SUPER_ADMIN authority
        Optional<User> createdAdmin = userRepository.findOneWithAuthoritiesByLogin(testAdminUsername);
        assertThat(createdAdmin).isPresent();

        User admin = createdAdmin.get();
        assertThat(admin.getLogin()).isEqualTo(testAdminUsername);
        assertThat(admin.getEmail()).isEqualTo(testAdminEmail);
        assertThat(admin.getFirstName()).isEqualTo("Administrator");
        assertThat(admin.getLastName()).isEqualTo("Administrator");
        assertThat(admin.getActivated()).isTrue();

        // Verify authorities contain SUPER_ADMIN
        Set<String> authorityNames = admin.getAuthorities().stream().map(Authority::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(authorityNames).contains(Role.SUPER_ADMIN.getAuthority());
        assertThat(authorityNames).contains(Role.STUDENT.getAuthority());

        // Cleanup
        userRepository.delete(admin);
    }

    @Test
    void testApplicationReady_updatesExistingInternalAdminWithSuperAdminRights() {
        // Setup: Create an existing admin user with only ADMIN rights
        String testAdminUsername = "test_existing_internal_admin";
        String testAdminPassword = "test_password_456";
        String testAdminEmail = "test_existing_admin@example.com";

        // Create a user with ADMIN authority (not SUPER_ADMIN)
        User existingAdmin = new User();
        existingAdmin.setLogin(testAdminUsername);
        existingAdmin.setPassword("old_password_hash");
        existingAdmin.setFirstName("Old");
        existingAdmin.setLastName("Admin");
        existingAdmin.setEmail("old_email@example.com");
        existingAdmin.setActivated(true);
        existingAdmin.setLangKey("en");
        existingAdmin.setInternal(true);

        // Set authorities to ADMIN only (not SUPER_ADMIN)
        Set<Authority> authorities = new HashSet<>();
        authorities.add(new Authority(Role.ADMIN.getAuthority()));
        authorities.add(new Authority(Role.STUDENT.getAuthority()));
        existingAdmin.setAuthorities(authorities);

        existingAdmin = userRepository.save(existingAdmin);

        // Verify initial state - should have ADMIN but not SUPER_ADMIN
        Set<String> initialAuthorityNames = existingAdmin.getAuthorities().stream().map(Authority::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(initialAuthorityNames).contains(Role.ADMIN.getAuthority());
        assertThat(initialAuthorityNames).doesNotContain(Role.SUPER_ADMIN.getAuthority());

        // Configure internal admin credentials
        ReflectionTestUtils.setField(userService, "artemisInternalAdminUsername", Optional.of(testAdminUsername));
        ReflectionTestUtils.setField(userService, "artemisInternalAdminPassword", Optional.of(testAdminPassword));
        ReflectionTestUtils.setField(userService, "artemisInternalAdminEmail", Optional.of(testAdminEmail));

        // Execute: Call applicationReady which should update the existing admin
        userService.applicationReady();

        // Verify: Check that the user was updated with SUPER_ADMIN authority
        Optional<User> updatedAdmin = userRepository.findOneWithAuthoritiesByLogin(testAdminUsername);
        assertThat(updatedAdmin).isPresent();

        User admin = updatedAdmin.get();
        assertThat(admin.getLogin()).isEqualTo(testAdminUsername);

        // Verify authorities now contain SUPER_ADMIN
        Set<String> updatedAuthorityNames = admin.getAuthorities().stream().map(Authority::getName).collect(java.util.stream.Collectors.toSet());
        assertThat(updatedAuthorityNames).contains(Role.SUPER_ADMIN.getAuthority());
        assertThat(updatedAuthorityNames).contains(Role.STUDENT.getAuthority());

        // The password should also be updated
        assertThat(admin.getPassword()).isNotEqualTo("old_password_hash");

        // Cleanup
        userRepository.delete(admin);
    }

    @Test
    void testImportUsersSetsTestUserFlagWhenProvided() {
        String login = TEST_PREFIX + "student1";
        assertThat(userRepository.findOneByLogin(login).orElseThrow().isTestUser()).as("existing user is not a test user by default").isFalse();

        // isTestUser = true -> the found user is flagged, and it is not reported as not-found
        List<StudentDTO> notFound = userService.importUsers(List.of(new StudentDTO(login, null, null, null, null, true)));
        assertThat(notFound).isEmpty();
        assertThat(userRepository.findOneByLogin(login).orElseThrow().isTestUser()).as("flag is set to true on import").isTrue();

        // isTestUser omitted (null) -> the flag is left unchanged
        userService.importUsers(List.of(new StudentDTO(login, null, null, null, null)));
        assertThat(userRepository.findOneByLogin(login).orElseThrow().isTestUser()).as("flag is unchanged when the column is absent").isTrue();

        // isTestUser = false -> the flag is cleared
        userService.importUsers(List.of(new StudentDTO(login, null, null, null, null, false)));
        assertThat(userRepository.findOneByLogin(login).orElseThrow().isTestUser()).as("flag is cleared when explicitly false").isFalse();
    }

    @Test
    void testCreateUser_withManagedUserVM_respectsIsInternalFlag() {
        String login = TEST_PREFIX + "external_user";
        ManagedUserVM externalUserDTO = new ManagedUserVM();
        externalUserDTO.setLogin(login);
        externalUserDTO.setFirstName("External");
        externalUserDTO.setLastName("User");
        externalUserDTO.setEmail("external_test@example.com");
        externalUserDTO.setInternal(false);

        userCreationService.createUser(externalUserDTO);

        // Reload the user from the repository to verify database persistence
        Optional<User> reloadedUser = userRepository.findOneByLogin(login);
        assertThat(reloadedUser).isPresent();
        assertThat(reloadedUser.get().isInternal()).as("persisted user should be external").isFalse();

        // Cleanup via reloaded entity
        reloadedUser.ifPresent(userRepository::delete);
    }

    @Test
    void testUpdateUser_externalToInternal_generatesPasswordIfNull() {
        String login = TEST_PREFIX + "ext_to_int";
        ManagedUserVM externalUserDTO = new ManagedUserVM();
        externalUserDTO.setLogin(login);
        externalUserDTO.setFirstName("External");
        externalUserDTO.setLastName("User");
        externalUserDTO.setEmail("ext_to_int@example.com");
        externalUserDTO.setInternal(false);

        User user = userCreationService.createUser(externalUserDTO);
        assertThat(user.isInternal()).isFalse();

        // Set external to internal and provide no password
        ManagedUserVM updateDTO = new ManagedUserVM(user);
        updateDTO.setInternal(true);
        updateDTO.setPassword(null);

        userCreationService.updateUser(user, updateDTO);

        User reloadedUser = userRepository.findOneByLogin(login).orElseThrow();
        assertThat(reloadedUser.isInternal()).isTrue();
        assertThat(reloadedUser.getPassword()).isNotNull().isNotEmpty();

        userRepository.delete(reloadedUser);
    }

    @Test
    void testUpdateUser_internalToExternal_reverseTransition() {
        String login = TEST_PREFIX + "int_to_ext";
        User user = userCreationService.createUser(login, "password123", "Internal", "User", "int_to_ext@example.com", null, null, "en", true);
        assertThat(user.isInternal()).isTrue();

        ManagedUserVM updateDTO = new ManagedUserVM(user);
        updateDTO.setInternal(false);

        userCreationService.updateUser(user, updateDTO);

        User reloadedUser = userRepository.findOneByLogin(login).orElseThrow();
        assertThat(reloadedUser.isInternal()).isFalse();

        userRepository.delete(reloadedUser);
    }

    @Test
    void testCreateUser_externalUser_isActivatedWithoutActivationKey() {
        String login = TEST_PREFIX + "ext_activated";
        User user = userCreationService.createUser(login, null, "External", "User", "ext_activated@example.com", null, null, "en", false);

        // An externally managed account never receives an activation mail and could never redeem a key, so it must not be
        // left waiting for one.
        assertThat(user.getActivated()).as("external user is created activated").isTrue();
        assertThat(userRecoveryKeyService.findActivationKey(user.getId())).as("external user gets no activation key").isNull();

        User reloadedUser = userRepository.findOneByLogin(login).orElseThrow();
        assertThat(reloadedUser.getActivated()).as("persisted external user is activated").isTrue();
        assertThat(userRecoveryKeyService.findActivationKey(reloadedUser.getId())).isNull();

        userRepository.delete(reloadedUser);
    }

    @Test
    void testCreateUser_internalUser_keepsAwaitingActivation() {
        String login = TEST_PREFIX + "int_unactivated";
        User user = userCreationService.createUser(login, "password123", "Internal", "User", "int_unactivated@example.com", null, null, "en", true);

        assertThat(user.getActivated()).as("internal user still awaits activation").isFalse();
        assertThat(userRecoveryKeyService.findActivationKey(user.getId())).as("internal user needs a key to activate with").isNotNull();

        userRepository.delete(userRepository.findOneByLogin(login).orElseThrow());
    }

    @Test
    void testApplicationReady_noActionWhenInternalAdminNotConfigured() {
        // Setup: Clear internal admin configuration
        ReflectionTestUtils.setField(userService, "artemisInternalAdminUsername", Optional.empty());
        ReflectionTestUtils.setField(userService, "artemisInternalAdminPassword", Optional.empty());

        // Get current user count
        long userCountBefore = userRepository.count();

        // Execute: Call applicationReady which should do nothing
        userService.applicationReady();

        // Verify: No new users should be created
        long userCountAfter = userRepository.count();
        assertThat(userCountAfter).isEqualTo(userCountBefore);
    }
}
