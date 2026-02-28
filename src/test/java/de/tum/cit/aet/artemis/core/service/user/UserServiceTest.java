package de.tum.cit.aet.artemis.core.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;

class UserServiceTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final String TEST_PREFIX = "userservice";

    @Autowired
    private UserService userService;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

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
        Optional<User> createdAdmin = userRepository.findOneWithGroupsAndAuthoritiesByLogin(testAdminUsername);
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
        Optional<User> updatedAdmin = userRepository.findOneWithGroupsAndAuthoritiesByLogin(testAdminUsername);
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
