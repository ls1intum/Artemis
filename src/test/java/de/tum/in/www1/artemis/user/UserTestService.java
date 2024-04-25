package de.tum.in.www1.artemis.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exercise.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.connectors.ci.CIUserManagementService;
import de.tum.in.www1.artemis.service.connectors.lti.LtiService;
import de.tum.in.www1.artemis.service.connectors.vcs.VcsUserManagementService;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.dto.UserInitializationDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for scenarios:
 * 1) Jenkins + Gitlab
 */
@Service
public class UserTestService {

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    private Optional<VcsUserManagementService> optionalVcsUserManagementService;

    @Autowired
    private Optional<CIUserManagementService> optionalCIUserManagementService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    private String TEST_PREFIX;

    private MockDelegate mockDelegate;

    public User student;

    private static final int numberOfStudents = 2;

    private static final int numberOfTutors = 1;

    private static final int numberOfEditors = 1;

    private static final int numberOfInstructors = 1;

    public void setup(String testPrefix, MockDelegate mockDelegate) throws Exception {
        this.TEST_PREFIX = testPrefix;
        this.mockDelegate = mockDelegate;

        List<User> users = userUtilService.addUsers(testPrefix, numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);
        student = userRepository.getUserByLoginElseThrow(testPrefix + "student1");
        student.setInternal(true);
        student = userRepository.save(student);
        student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();

        users.forEach(user -> cacheManager.getCache(UserRepository.USERS_CACHE).evict(user.getLogin()));
    }

    public void tearDown() throws IOException {
        userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
    }

    public User getStudent() {
        return student;
    }

    private void assertThatUserWasSoftDeleted(User originalUser, User deletedUser) throws Exception {
        assertThat(deletedUser.isDeleted()).isTrue();
        assertThat(deletedUser.getFirstName()).isEqualTo(Constants.USER_FIRST_NAME_AFTER_SOFT_DELETE);
        assertThat(deletedUser.getLastName()).isEqualTo(Constants.USER_LAST_NAME_AFTER_SOFT_DELETE);
        assertThat(deletedUser.getLogin()).isNotEqualTo(originalUser.getLogin());
        assertThat(deletedUser.getPassword()).isNotEqualTo(originalUser.getPassword());
        assertThat(deletedUser.getEmail()).endsWith(Constants.USER_EMAIL_DOMAIN_AFTER_SOFT_DELETE);
        assertThat(deletedUser.getRegistrationNumber()).isNull();
        assertThat(deletedUser.getImageUrl()).isNull();
        assertThat(deletedUser.getActivated()).isFalse();
    }

    private void assertThatUserWasNotSoftDeleted(User originalUser, User deletedUser) throws Exception {
        assertThat(deletedUser.isDeleted()).isFalse();
        assertThat(deletedUser.getFirstName()).isEqualTo(originalUser.getFirstName());
        assertThat(deletedUser.getLastName()).isEqualTo(originalUser.getLastName());
        assertThat(deletedUser.getLogin()).isEqualTo(originalUser.getLogin());
        assertThat(deletedUser.getPassword()).isEqualTo(originalUser.getPassword());
        assertThat(deletedUser.getEmail()).isEqualTo(originalUser.getEmail());
        assertThat(deletedUser.getRegistrationNumber()).isEqualTo(originalUser.getVisibleRegistrationNumber());
        assertThat(deletedUser.getImageUrl()).isEqualTo(originalUser.getImageUrl());
    }

    // Test
    public void deleteUser_isSuccessful() throws Exception {
        student.setRegistrationNumber("123");
        student.setImageUrl("https://www.somewebsite.com/image.jpg");
        userRepository.save(student);

        request.delete("/api/admin/users/" + student.getLogin(), HttpStatus.OK);

        final var deletedUser = userRepository.findById(student.getId()).orElseThrow();
        assertThatUserWasSoftDeleted(student, deletedUser);
    }

    // Test
    public void deleteSelf_isNotSuccessful(String currentUserLogin) throws Exception {
        request.delete("/api/admin/users/" + currentUserLogin, HttpStatus.BAD_REQUEST);
        final var deletedUser = userRepository.findById(student.getId()).orElseThrow();
        assertThatUserWasNotSoftDeleted(student, deletedUser);
    }

    // Test
    public void deleteUsers(String currentUserLogin) throws Exception {
        userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        var users = Set.of(userUtilService.getUserByLogin(TEST_PREFIX + "student1"), userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"),
                userUtilService.getUserByLogin(TEST_PREFIX + "editor1"), userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        users.stream().map(User::getLogin).forEach(login -> params.add("login", login));

        request.delete("/api/admin/users", HttpStatus.OK, params);

        for (var user : users) {
            final var deletedUser = userRepository.findById(user.getId()).orElseThrow();

            if (deletedUser.getLogin().equals(currentUserLogin)) {
                assertThatUserWasNotSoftDeleted(user, deletedUser);
            }
            else {
                assertThatUserWasSoftDeleted(user, deletedUser);
            }
        }
    }

    // Test
    public void updateUser_asAdmin_isSuccessful() throws Exception {
        student.setInternal(true);
        student = userRepository.save(student);
        final var newPassword = "bonobo42";
        final var newEmail = "bonobo42@tum.com";
        final var newFirstName = "Bruce";
        final var newGroups = Set.of("foo", "bar");
        final var newLastName = "Wayne";
        final var newImageUrl = "foobar.png";
        final var newLangKey = "DE";
        final var newAuthorities = Stream.of(Role.TEACHING_ASSISTANT.getAuthority()).map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get)
                .collect(Collectors.toSet());
        final var oldGroups = student.getGroups();
        student.setAuthorities(newAuthorities);
        student.setEmail(newEmail);
        student.setFirstName(newFirstName);
        student.setGroups(newGroups);
        student.setLastName(newLastName);
        student.setImageUrl(newImageUrl);
        student.setLangKey(newLangKey);

        student.setPassword(newPassword);
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, newPassword, oldGroups);

        var managedUserVM = new ManagedUserVM(student, newPassword);
        managedUserVM.setPassword(newPassword);
        final var response = request.putWithResponseBody("/api/admin/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();

        assertThat(response).isNotNull();
        assertThat(passwordService.checkPasswordMatch(newPassword, updatedUserIndDB.getPassword())).isTrue();

        // set passwords to null to exclude them from the comparison
        student.setPassword(null);
        updatedUserIndDB.setPassword(null);

        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }

    // Test
    public void updateUserWithEmptyRoles() throws Exception {
        student.setInternal(true);
        student.setAuthorities(null);

        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, "foobar1234", student.getGroups());

        var managedUserVM = new ManagedUserVM(student, "foobar1234");

        final var response = request.putWithResponseBody("/api/admin/users", managedUserVM, User.class, HttpStatus.OK);
        assertThat(response).isNotNull();

        // do not allow empty authorities
        final var updatedUserInDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).orElseThrow();
        assertThat(updatedUserInDB.getAuthorities()).containsExactly(new Authority(Role.STUDENT.getAuthority()));
    }

    // Test
    public void updateUser_withNullPassword_oldPasswordNotChanged() throws Exception {
        student.setPassword(null);
        final var oldPassword = userRepository.findById(student.getId()).orElseThrow().getPassword();
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userRepository.findById(student.getId()).orElseThrow();

        assertThat(oldPassword).as("Password did not change").isEqualTo(userInDB.getPassword());
    }

    // Test
    public void updateUserLogin() throws Exception {
        var oldLogin = student.getLogin();
        student.setLogin("new-login");
        mockDelegate.mockUpdateUserInUserManagement(oldLogin, student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userRepository.findById(student.getId()).orElseThrow();

        assertThat(userInDB.getLogin()).isEqualTo(student.getLogin());
        assertThat(userInDB.getId()).isEqualTo(student.getId());
    }

    // Test
    public void updateUserInvalidId() throws Exception {
        long oldId = student.getId();
        student.setId(oldId + 1);
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student, student.getPassword()), HttpStatus.BAD_REQUEST);
        final var userInDB = userRepository.findById(oldId).orElseThrow();
        assertThat(userInDB).isNotEqualTo(student);
        assertThat(userRepository.findById(oldId + 1)).isNotEqualTo(student);
    }

    // Test
    public void updateUserExistingEmail() throws Exception {
        long oldId = student.getId();
        student.setId(oldId + 1);
        student.setEmail("newEmail@testing.user");
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student, student.getPassword()), HttpStatus.BAD_REQUEST);
        final var userInDB = userRepository.findById(oldId).orElseThrow();
        assertThat(userInDB).isNotEqualTo(student);
        assertThat(userRepository.findById(oldId + 1)).isNotEqualTo(student);
    }

    // Test
    public void updateUser_withExternalUserManagement() throws Exception {
        student.setFirstName("changed");
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student), HttpStatus.OK);

        var updatedUser = userRepository.findById(student.getId());
        assertThat(updatedUser).isPresent();
        assertThat(updatedUser.get().getFirstName()).isEqualTo("changed");
    }

    // Test
    public void updateUserGroups() throws Exception {
        var course = courseUtilService.addEmptyCourse();
        programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        courseRepository.save(course);

        // First we create a new user with group
        student.setGroups(Set.of("instructor"));
        student = userRepository.save(student);

        // We will then update the user by modifying the groups
        var updatedUser = student;
        updatedUser.setGroups(Set.of("tutor"));
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), updatedUser, null, student.getGroups());
        request.put("/api/admin/users", new ManagedUserVM(updatedUser, "this is a password"), HttpStatus.OK);

        var updatedUserOrEmpty = userRepository.findOneWithGroupsAndAuthoritiesByLogin(updatedUser.getLogin());
        assertThat(updatedUserOrEmpty).isPresent();

        updatedUser = updatedUserOrEmpty.get();
        assertThat(updatedUser.getId()).isEqualTo(student.getId());
        assertThat(updatedUser.getGroups()).hasSize(1).contains("tutor");
    }

    // Test
    public User createExternalUser_asAdmin_isSuccessful() throws Exception {
        String password = "foobar1234";
        student.setId(null);
        student.setLogin("batman");
        student.setPassword(password);
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student, password), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).orElseThrow();
        assertThat(passwordService.checkPasswordMatch(password, userInDB.getPassword())).isTrue();
        student.setId(response.getId());

        // Exclude passwords from comparison
        response.setPassword(null);
        userInDB.setPassword(null);

        assertThat(student).as("New user is equal to request response").isEqualTo(response);
        assertThat(student).as("New user is equal to new user in DB").isEqualTo(userInDB);

        return userInDB;
    }

    // Test
    public void createExternalUser_asAdmin_withVcsToken_isSuccessful() throws Exception {
        var user = this.createExternalUser_asAdmin_isSuccessful();
        assertThat(user.getVcsAccessToken()).as("VCS Access token is set correctly").isEqualTo("acccess-token-value");
    }

    // Test
    public void createInternalUser_asAdmin_isSuccessful() throws Exception {
        createInternalUserIsSuccessful(Set.of(Role.STUDENT));
    }

    // Test
    public void createInternalUserWithoutRoles_asAdmin_isSuccessful() throws Exception {
        createInternalUserIsSuccessful(Collections.emptySet());
    }

    private void createInternalUserIsSuccessful(final Set<Role> roles) throws Exception {
        String password = "foobar1234";
        student.setId(null);
        student.setLogin("batman");
        student.setPassword(password);
        student.setEmail("batman@secret.invalid");
        student.setInternal(true);

        final Set<Authority> authorities = roles.stream().map(Role::getAuthority).map(auth -> authorityRepository.findById(auth).orElseThrow()).collect(Collectors.toSet());
        student.setAuthorities(authorities);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student, student.getPassword()), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).orElseThrow();
        userInDB.setPassword(password);
        student.setId(response.getId());
        response.setPassword(password);

        assertThat(student).as("New user is equal to request response").isEqualTo(response);
        assertThat(student).as("New user is equal to new user in DB").isEqualTo(userInDB);
    }

    // Test
    public void createUser_asAdmin_hasId() throws Exception {
        userRepository.findOneByLogin("batman").ifPresent(userRepository::delete);

        student.setId((long) 1337);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");
        student = userRepository.save(student);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.BAD_REQUEST);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_existingLogin() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();

        User student2 = new User();
        student2.setId(null);
        student2.setLogin("batman");
        student2.setPassword("barfoo");
        student2.setEmail("batman2@secret.stillinvalid");

        final var response2 = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student2), User.class, HttpStatus.BAD_REQUEST);
        assertThat(response2).isNull();
    }

    // Test
    public void createUser_asAdmin_existingEmail() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.BAD_REQUEST);
        assertThat(response).isNull();
    }

    // Test
    public void createUserAsAdminExistsInCi() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, true);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
    }

    // Test
    public void createUser_asAdmin_illegalLogin_internalError() throws Exception {
        student.setId(null);
        student.setLogin("@someusername");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalCiUserManagement_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExternalUserManagement(student, false, true, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalCiUserManagement_cannotGetCiUser_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExternalUserManagement(student, false, false, true);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalVcsUserManagement_internalError() throws Exception {
        userRepository.findOneByLogin("batman").ifPresent(userRepository::delete);

        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExternalUserManagement(student, true, false, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        userRepository.findOneByLogin("batman").ifPresent(userRepository::delete);

        student.setId(null);
        student.setEmail("batman@invalid.tum");
        student.setLogin("batman");
        student.setPassword(null);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).orElseThrow();

        assertThat(userInDB.getPassword()).isNotBlank();
    }

    // Test
    public void createUser_withExternalUserManagement() throws Exception {
        userRepository.findOneByLogin("batman").ifPresent(userRepository::delete);

        var newUser = student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");

        mockDelegate.mockCreateUserInUserManagement(newUser, false);

        request.post("/api/admin/users", new ManagedUserVM(newUser), HttpStatus.CREATED);

        var createdUser = userRepository.findOneByEmailIgnoreCase(newUser.getEmail());
        assertThat(createdUser).isPresent();
        assertThat(createdUser.get().getId()).isNotNull();
    }

    // Test
    public void createUserWithGroups() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> userRepository.findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(Long.MAX_VALUE));

        var course = courseUtilService.addEmptyCourse();
        programmingExerciseUtilService.addProgrammingExerciseToCourse(course);
        course = courseUtilService.addEmptyCourse();
        course.setInstructorGroupName("instructor2");
        courseRepository.save(course);

        userRepository.findOneByLogin("batman").ifPresent(userRepository::delete);

        var newUser = student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        newUser.setGroups(Set.of("tutor", "instructor2"));

        mockDelegate.mockCreateUserInUserManagement(newUser, false);

        request.post("/api/admin/users", new ManagedUserVM(newUser), HttpStatus.CREATED);

        var createdUserOrEmpty = userRepository.findOneWithGroupsAndAuthoritiesByLogin(newUser.getLogin());
        assertThat(createdUserOrEmpty).isPresent();

        var createdUser = createdUserOrEmpty.get();
        assertThat(createdUser.getId()).isNotNull();
        assertThat(createdUser.getGroups()).hasSize(2).isEqualTo(newUser.getGroups());
    }

    // Test
    public void getUsers_asAdmin_isSuccessful() throws Exception {
        var usersDb = userRepository.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalse().stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
        userRepository.saveAll(usersDb);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", TEST_PREFIX);
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", "");
        params.add("origins", "");
        params.add("registrationNumbers", "");
        params.add("status", "");
        params.add("courseIds", "");
        List<UserDTO> users = request.getList("/api/admin/users", HttpStatus.OK, UserDTO.class, params);
        assertThat(users).hasSize(numberOfStudents + numberOfTutors + numberOfEditors + numberOfInstructors); // admin is not returned
    }

    // Test
    public void searchUsers_asInstructor_isSuccessful() throws Exception {
        final String loginOrName = TEST_PREFIX + "student1";
        List<UserDTO> users = request.getList("/api/users/search?loginOrName=" + loginOrName, HttpStatus.OK, UserDTO.class);
        assertThat(users).hasSize(1); // size([student1]) = 1
    }

    // Test
    public void searchUsers_asAdmin_badRequest() throws Exception {
        final String loginOrName = "ab"; // too short (needs at least 3 characters)
        request.getList("/api/users/search?loginOrName=" + loginOrName, HttpStatus.BAD_REQUEST, UserDTO.class);
    }

    // Test
    public void searchUsers_asTutor_forbidden() throws Exception {
        final String loginOrName = "student";
        request.getList("/api/users/search?loginOrName=" + loginOrName, HttpStatus.FORBIDDEN, UserDTO.class);
    }

    // Test
    public void getUserViaFilter_asAdmin_isSuccessful() throws Exception {
        student.setGroups(Collections.emptySet());
        userRepository.save(student);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", student.getEmail());
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", "USER");
        params.add("origins", "");
        params.add("status", "");
        params.add("registrationNumbers", "");
        params.add("courseIds", "");
        List<User> users = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo(student.getEmail());
    }

    // Test
    public void getAuthorities_asAdmin_isSuccessful() throws Exception {
        List<String> authorities = request.getList("/api/admin/users/authorities", HttpStatus.OK, String.class);
        assertThat(authorities).hasSameElementsAs(List.of("ROLE_ADMIN", "ROLE_EDITOR", "ROLE_INSTRUCTOR", "ROLE_TA", "ROLE_USER"));
    }

    // Test
    public void getUsersOrAuthorities_asInstructor_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    // Test
    public void getUsersOrAuthorities_asTutor_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    // Test
    public void getUsersOrAuthorities_asStudent_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    private void getUsersOrAuthorities_forbidden() throws Exception {
        request.getList("/api/admin/users", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/admin/users/authorities", HttpStatus.FORBIDDEN, String.class);
    }

    // Test
    public void getUser_asAdmin_isSuccessful() throws Exception {
        final String userLogin = TEST_PREFIX + "student1";
        UserDTO userDTO = request.get("/api/admin/users/" + userLogin, HttpStatus.OK, UserDTO.class);
        assertThat(userDTO.getLogin()).isEqualTo(userLogin);
    }

    // Test
    public void updateUserNotificationDate_asStudent_isSuccessful() throws Exception {
        request.put("/api/users/notification-date", null, HttpStatus.OK);
        User userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getLastNotificationRead()).isAfterOrEqualTo(ZonedDateTime.now().minusSeconds(1));
    }

    // Test
    public void updateUserNotificationVisibilityShowAllAsStudentIsSuccessful() throws Exception {
        request.put("/api/users/notification-visibility", true, HttpStatus.OK);
        User userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getHideNotificationsUntil()).isNull();
    }

    // Test
    public void updateUserNotificationVisibilityHideUntilAsStudentIsSuccessful() throws Exception {
        request.put("/api/users/notification-visibility", false, HttpStatus.OK);
        User userInDB = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(userInDB.getHideNotificationsUntil()).isNotNull();
        assertThat(userInDB.getHideNotificationsUntil()).isStrictlyBetween(ZonedDateTime.now().minusSeconds(1), ZonedDateTime.now().plusSeconds(1));
    }

    // Test
    public void initializeUser(boolean mock) throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User repoUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        repoUser.setPassword(password);
        repoUser.setInternal(true);
        repoUser.setActivated(false);
        repoUser.setGroups(Set.of(LtiService.LTI_GROUP_NAME));
        final User user = userRepository.save(repoUser);

        if (mock) {
            // Mock user creation and update calls to prevent issues in GitLab/Jenkins tests
            mockDelegate.mockCreateUserInUserManagement(user, false);
            mockDelegate.mockUpdateUserInUserManagement(user.getLogin(), user, null, new HashSet<>());
        }

        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.createVcsUser(user, password));
        optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.createUser(user, password));

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.getPassword()).isNotEmpty();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        assertThat(passwordService.checkPasswordMatch(dto.getPassword(), currentUser.getPassword())).isTrue();
        assertThat(passwordService.checkPasswordMatch(password, currentUser.getPassword())).isFalse();
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserWithoutFlag() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setPassword(password);
        user.setInternal(true);
        user.setActivated(true);
        user.setGroups(Set.of(LtiService.LTI_GROUP_NAME));
        userRepository.save(user);

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.getPassword()).isNull();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserNonLTI() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setPassword(password);
        user.setInternal(true);
        user.setActivated(false);
        userRepository.save(user);

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);
        assertThat(dto.getPassword()).isNull();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserExternal() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        user.setPassword(password);
        user.setInternal(false);
        user.setActivated(false);
        userRepository.save(user);

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.getPassword()).isNull();

        User currentUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isFalse();
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    /**
     * Helper method to create the params.
     *
     * @param authorities         authorities of the users
     * @param origins             of the users
     * @param registrationNumbers of the users
     * @param status              of the users
     * @return params for request
     */
    private LinkedMultiValueMap<String, String> createParamsForPagingRequest(String authorities, String origins, String registrationNumbers, String status,
            boolean findWithoutUserGroups) {
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "1000");
        params.add("searchTerm", "");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", authorities);
        params.add("origins", origins);
        params.add("registrationNumbers", registrationNumbers);
        params.add("status", status);
        params.add("findWithoutUserGroups", Boolean.toString(findWithoutUserGroups));
        return params;
    }

    /**
     * Helper method to determine the user authority which is used most often in a user creation matrix
     *
     * @param userNumbers the user creation matrix
     * @return String of the user authority with the most users
     * @throws Exception
     */
    private String getMainUserAuthority(Integer[] userNumbers) throws Exception {
        List<Integer> userNumbersList = Arrays.asList(userNumbers);
        var authorityIndex = userNumbersList.indexOf(Collections.max(userNumbersList));
        return switch (authorityIndex) {
            case 0 -> "student";
            case 1 -> "tutor";
            case 2 -> "editor";
            case 3 -> "instructor";
            default -> throw new Exception("Couldn't match the input user array to an authority.");
        };
    }

    // Test
    public void testUser() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 }, };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setGroups(Collections.emptySet());
            user2.setGroups(Set.of("tumuser"));
            userRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1).contains(user2);
        }
    }

    // Test
    public void testUserWithoutGroups() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        courseRepository.save(course);

        final var params = createParamsForPagingRequest("USER", "", "", Long.toString(course.getId()), true);

        List<User> result;

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setGroups(Collections.emptySet());
            user2.setGroups(Set.of("tumuser"));
            userRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1).doesNotContain(user2);
        }
    }

    // Test
    public void testUserWithActivatedStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "ACTIVATED", false);

        List<User> result;

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            User admin = userRepository.getUserByLoginElseThrow("admin");
            user1.setActivated(true);
            user2.setActivated(false);
            userRepository.saveAll(List.of(user1, user2, admin));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1, admin).doesNotContain(user2);
        }
    }

    // Test
    public void testUserWithDeactivatedStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "DEACTIVATED", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setActivated(true);
            user2.setActivated(false);
            userRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user2).doesNotContain(user1);
        }
    }

    // Test
    public void testUserWithInternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            User admin = userRepository.getUserByLoginElseThrow("admin");
            user1.setInternal(true);
            user2.setInternal(false);
            userRepository.saveAll(List.of(user1, user2, admin));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user1, admin).doesNotContain(user2);
        }
    }

    // Test
    public void testUserWithExternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "EXTERNAL", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setInternal(true);
            user2.setInternal(false);
            userRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(user2).doesNotContain(user1);
        }
    }

    // Test
    public void testUserWithExternalAndInternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL,EXTERNAL", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setInternal(true);
            user2.setInternal(false);
            userRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).isEqualTo(Collections.emptyList());
        }
    }

    /**
     * Test for the user search with the following parameters:
     *
     * @throws Exception if the user is not the same as the expected user
     */
    public void testUserWithRegistrationNumber() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL,EXTERNAL", "WITH_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setRegistrationNumber(null);
            user2.setRegistrationNumber(null);
            userRepository.saveAll(List.of(user1, user2));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).isEqualTo(Collections.emptyList());
        }
    }

    /**
     * Test for the user search with the following parameters:
     *
     * @throws Exception if the user is not the same as the expected user
     */
    public void testUserWithoutRegistrationNumber() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "", false);

        List<User> result;

        courseUtilService.addEmptyCourse();

        Integer[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (Integer[] number : numbers) {
            userRepository.deleteAll(userRepository.searchAllByLoginOrName(Pageable.unpaged(), TEST_PREFIX));
            userUtilService.addUsers(TEST_PREFIX, number[0], number[1], number[2], number[3]);
            final var mainUserAuthority = getMainUserAuthority(number);
            User user1 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 1);
            User user2 = userRepository.getUserByLoginElseThrow(TEST_PREFIX + mainUserAuthority + 2);
            user1.setRegistrationNumber("5461351");
            user2.setRegistrationNumber("");
            User admin = userRepository.getUserByLoginElseThrow("admin");
            userRepository.saveAll(List.of(user1, user2, admin));
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).contains(admin).doesNotContain(user1, user2);
        }
    }
}
