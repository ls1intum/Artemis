package de.tum.in.www1.artemis.util;

import static de.tum.in.www1.artemis.repository.UserRepository.FILTER_WITHOUT_REG_NO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.LtiUserId;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LtiUserIdRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.connectors.CIUserManagementService;
import de.tum.in.www1.artemis.service.connectors.VcsUserManagementService;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.dto.UserInitializationDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for both scenarios:
 * 1) Bamboo + Bitbucket
 * 2) Jenkins + Gitlab
 */
@Service
public class UserTestService {

    @Autowired
    private DatabaseUtilService database;

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
    private LtiUserIdRepository ltiUserIdRepository;

    @Autowired
    private Optional<VcsUserManagementService> optionalVcsUserManagementService;

    @Autowired
    private Optional<CIUserManagementService> optionalCIUserManagementService;

    private MockDelegate mockDelegate;

    public User student;

    private final static int numberOfStudents = 50;

    private final static int numberOfTutors = 1;

    private final static int numberOfEditors = 1;

    private final static int numberOfInstructors = 1;

    public void setup(MockDelegate mockDelegate) throws Exception {
        this.mockDelegate = mockDelegate;

        List<User> users = database.addUsers(numberOfStudents, numberOfTutors, numberOfEditors, numberOfInstructors);
        student = users.get(0);
        student.setInternal(true);
        student = userRepository.save(student);
        users.forEach(user -> cacheManager.getCache(UserRepository.USERS_CACHE).evict(user.getLogin()));
    }

    public void tearDown() throws IOException {
        database.resetDatabase();
    }

    public User getStudent() {
        return student;
    }

    // Test
    public void deleteUser_isSuccessful() throws Exception {
        student.setInternal(true);
        userRepository.save(student);
        mockDelegate.mockDeleteUserInUserManagement(student, true, false, false);

        request.delete("/api/admin/users/" + student.getLogin(), HttpStatus.OK);

        var deletedUser = userRepository.findById(student.getId());
        assertThat(deletedUser).isEmpty();
    }

    // Test
    public void deleteUser_doesntExistInUserManagement_isSuccessful() throws Exception {
        mockDelegate.mockDeleteUserInUserManagement(student, false, true, true);

        request.delete("/api/admin/users/" + student.getLogin(), HttpStatus.OK);

        var deletedUser = userRepository.findById(student.getId());
        assertThat(deletedUser).isEmpty();
    }

    // Test
    public void deleteUser_FailsInExternalCiUserManagement_isNotSuccessful() throws Exception {
        mockDelegate.mockDeleteUserInUserManagement(student, true, false, true);

        request.delete("/api/admin/users/" + student.getLogin(), HttpStatus.INTERNAL_SERVER_ERROR);

        var deletedUser = userRepository.findById(student.getId());
        assertThat(deletedUser).isNotEmpty();
    }

    // Test
    public void deleteUser_FailsInExternalVcsUserManagement_isNotSuccessful() throws Exception {
        mockDelegate.mockDeleteUserInUserManagement(student, true, true, false);

        request.delete("/api/admin/users/" + student.getLogin(), HttpStatus.INTERNAL_SERVER_ERROR);

        var deletedUser = userRepository.findById(student.getId());
        assertThat(deletedUser).isNotEmpty();
    }

    // Test
    public void deleteUsers() throws Exception {
        userRepository.deleteAll();
        var users = database.addUsers(1, 1, 1, 1);

        for (var user : users) {
            mockDelegate.mockDeleteUserInUserManagement(user, true, false, false);
        }

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        users.stream().map(User::getLogin).forEach(login -> params.add("login", login));

        request.delete("/api/admin/users", HttpStatus.OK, params);

        for (var user : users) {
            var deletedUser = userRepository.findById(user.getId());
            if (deletedUser.isEmpty() || !"admin".equals(deletedUser.get().getLogin())) {
                assertThat(deletedUser).isEmpty();
            }
        }
    }

    // Test
    public void deleteUsersException() throws Exception {
        userRepository.deleteAll();
        var users = database.addUsers(1, 1, 1, 1);

        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        users.stream().map(User::getLogin).forEach(login -> params.add("login", login));

        for (var user : users) {
            mockDelegate.mockDeleteUserInUserManagement(user, true, true, true);
        }

        request.delete("/api/admin/users", HttpStatus.OK, params);
        for (var user : users) {
            var receivedUser = userRepository.findById(user.getId());
            assertThat(receivedUser.isPresent()).isTrue();
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
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).get();

        assertThat(response).isNotNull();
        assertThat(passwordService.checkPasswordMatch(newPassword, updatedUserIndDB.getPassword())).isTrue();

        // set passwords to null to exclude them from the comparison
        student.setPassword(null);
        updatedUserIndDB.setPassword(null);

        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }

    // Test
    public void updateUser_withNullPassword_oldPasswordNotChanged() throws Exception {
        student.setPassword(null);
        final var oldPassword = userRepository.findById(student.getId()).get().getPassword();
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userRepository.findById(student.getId()).get();

        assertThat(oldPassword).as("Password did not change").isEqualTo(userInDB.getPassword());
    }

    // Test
    public void updateUserLogin() throws Exception {
        var oldLogin = student.getLogin();
        student.setLogin("new-login");
        mockDelegate.mockUpdateUserInUserManagement(oldLogin, student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userRepository.findById(student.getId()).get();

        assertThat(userInDB.getLogin()).isEqualTo(student.getLogin());
        assertThat(userInDB.getId()).isEqualTo(student.getId());
    }

    // Test
    public void updateUserInvalidId() throws Exception {
        long oldId = student.getId();
        student.setId(oldId + 1);
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, null, student.getGroups());

        request.put("/api/admin/users", new ManagedUserVM(student, student.getPassword()), HttpStatus.BAD_REQUEST);
        final var userInDB = userRepository.findById(oldId).get();
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
        final var userInDB = userRepository.findById(oldId).get();
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
        var course = database.addEmptyCourse();
        database.addProgrammingExerciseToCourse(course, false);
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
        final var userInDB = userRepository.findById(response.getId()).get();
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
        String password = "foobar1234";
        student.setId(null);
        student.setLogin("batman");
        student.setPassword(password);
        student.setEmail("batman@secret.invalid");
        student.setInternal(true);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student, student.getPassword()), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).get();
        userInDB.setPassword(password);
        student.setId(response.getId());
        response.setPassword(password);

        assertThat(student).as("New user is equal to request response").isEqualTo(response);
        assertThat(student).as("New user is equal to new user in DB").isEqualTo(userInDB);
    }

    // Test
    public void createUser_asAdmin_hasId() throws Exception {
        student.setId((long) 1337);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

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
    public void createUser_asAdmin_existsInCi_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, true);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
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

        mockDelegate.mockFailToCreateUserInExernalUserManagement(student, false, true, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalCiUserManagement_cannotGetCiUser_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExernalUserManagement(student, false, false, true);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalVcsUserManagement_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExernalUserManagement(student, true, false, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        student.setId(null);
        student.setEmail("batman@invalid.tum");
        student.setLogin("batman");
        student.setPassword(null);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/admin/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).get();

        assertThat(userInDB.getPassword()).isNotBlank();
    }

    // Test
    public void createUser_withExternalUserManagement() throws Exception {
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
        assertThrows(EntityNotFoundException.class, () -> userRepository.findByIdWithGroupsAndAuthoritiesElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> userRepository.findByIdWithGroupsAndAuthoritiesAndOrganizationsElseThrow(Long.MAX_VALUE));

        var course = database.addEmptyCourse();
        database.addProgrammingExerciseToCourse(course, false);
        course = database.addEmptyCourse();
        course.setInstructorGroupName("instructor2");
        courseRepository.save(course);

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
        var usersDb = userRepository.findAllWithGroupsAndAuthorities().stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
        userRepository.saveAll(usersDb);
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", "");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", "");
        params.add("origins", "");
        params.add("registrationNumbers", "");
        params.add("status", "");
        params.add("courseIds", "");
        List<UserDTO> users = request.getList("/api/admin/users", HttpStatus.OK, UserDTO.class, params);
        assertThat(users).hasSize(numberOfStudents + numberOfTutors + numberOfEditors + numberOfInstructors + 1); // +1 for admin user himself
    }

    // Test
    public void searchUsers_asInstructor_isSuccessful() throws Exception {
        final String loginOrName = "student1";
        List<UserDTO> users = request.getList("/api/users/search?loginOrName=" + loginOrName, HttpStatus.OK, UserDTO.class);
        assertThat(users).hasSize(11); // size([student1, student10, ... student19]) = 11
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
        params.add("searchTerm", "student1@test.de");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", "USER");
        params.add("origins", "");
        params.add("status", "");
        params.add("registrationNumbers", "");
        params.add("courseIds", "");
        List<User> users = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("student1@test.de");
    }

    // Test
    public void getAuthorities_asAdmin_isSuccessful() throws Exception {
        List<String> authorities = request.getList("/api/admin/users/authorities", HttpStatus.OK, String.class);
        assertThat(authorities).isEqualTo(List.of("ROLE_ADMIN", "ROLE_EDITOR", "ROLE_INSTRUCTOR", "ROLE_TA", "ROLE_USER"));
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
        final String userLogin = "student1";
        UserDTO userDTO = request.get("/api/users/" + userLogin, HttpStatus.OK, UserDTO.class);
        assertThat(userDTO.getLogin()).isEqualTo(userLogin);
    }

    // Test
    public void updateUserNotificationDate_asStudent_isSuccessful() throws Exception {
        request.put("/api/users/notification-date", null, HttpStatus.OK);
        User userInDB = userRepository.findOneByLogin("student1").get();
        assertThat(userInDB.getLastNotificationRead()).isAfterOrEqualTo(ZonedDateTime.now().minusSeconds(1));
    }

    // Test
    public void updateUserNotificationVisibilityShowAllAsStudentIsSuccessful() throws Exception {
        request.put("/api/users/notification-visibility", true, HttpStatus.OK);
        User userInDB = userRepository.findOneByLogin("student1").get();
        assertThat(userInDB.getHideNotificationsUntil()).isNull();
    }

    // Test
    public void updateUserNotificationVisibilityHideUntilAsStudentIsSuccessful() throws Exception {
        request.put("/api/users/notification-visibility", false, HttpStatus.OK);
        User userInDB = userRepository.findOneByLogin("student1").get();
        assertThat(userInDB.getHideNotificationsUntil()).isNotNull();
        assertThat(userInDB.getHideNotificationsUntil()).isStrictlyBetween(ZonedDateTime.now().minusSeconds(1), ZonedDateTime.now().plusSeconds(1));
    }

    // Test
    public void initializeUser(boolean mock) throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User repoUser = userRepository.findOneByLogin("student1").get();
        repoUser.setPassword(password);
        repoUser.setInternal(true);
        repoUser.setActivated(false);
        repoUser.setGroups(new HashSet<>());
        final User user = userRepository.save(repoUser);
        LtiUserId ltiUserId = new LtiUserId();
        ltiUserId.setLtiUserId("1234");
        ltiUserId.setUser(repoUser);
        ltiUserIdRepository.save(ltiUserId);

        if (mock) {
            // Mock user creation and update calls to prevent issues in GitLab/Jenkins tests
            mockDelegate.mockCreateUserInUserManagement(user, false);
            mockDelegate.mockUpdateUserInUserManagement(user.getLogin(), user, null, new HashSet<>());
        }

        optionalVcsUserManagementService.ifPresent(vcsUserManagementService -> vcsUserManagementService.createVcsUser(user, password));
        optionalCIUserManagementService.ifPresent(ciUserManagementService -> ciUserManagementService.createUser(user, password));

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.getPassword()).isNotEmpty();

        User currentUser = userRepository.findOneByLogin("student1").get();

        assertThat(passwordService.checkPasswordMatch(dto.getPassword(), currentUser.getPassword())).isTrue();
        assertThat(passwordService.checkPasswordMatch(password, currentUser.getPassword())).isFalse();
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserWithoutFlag() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userRepository.findOneByLogin("student1").get();
        user.setPassword(password);
        user.setInternal(true);
        user.setActivated(true);
        user = userRepository.save(user);
        LtiUserId ltiUserId = new LtiUserId();
        ltiUserId.setLtiUserId("1234");
        ltiUserId.setUser(user);
        ltiUserIdRepository.save(ltiUserId);

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.getPassword()).isNull();

        User currentUser = userRepository.findOneByLogin("student1").get();

        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserNonLTI() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userRepository.findOneByLogin("student1").get();
        user.setPassword(password);
        user.setInternal(true);
        user.setActivated(false);
        userRepository.save(user);

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);
        assertThat(dto.getPassword()).isNull();

        User currentUser = userRepository.findOneByLogin("student1").get();

        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isTrue();
    }

    // Test
    public void initializeUserExternal() throws Exception {
        String password = passwordService.hashPassword("ThisIsAPassword");
        User user = userRepository.findOneByLogin("student1").get();
        user.setPassword(password);
        user.setInternal(false);
        user.setActivated(false);
        user = userRepository.save(user);
        LtiUserId ltiUserId = new LtiUserId();
        ltiUserId.setLtiUserId("1234");
        ltiUserId.setUser(user);
        ltiUserIdRepository.save(ltiUserId);

        UserInitializationDTO dto = request.putWithResponseBody("/api/users/initialize", false, UserInitializationDTO.class, HttpStatus.OK);

        assertThat(dto.getPassword()).isNull();

        User currentUser = userRepository.findOneByLogin("student1").get();

        assertThat(currentUser.getPassword()).isEqualTo(password);
        assertThat(currentUser.getActivated()).isTrue();
        assertThat(currentUser.isInternal()).isFalse();
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    /**
     * Helper method to create the params.
     * @param authorities authorities of the users
     * @param origins of the users
     * @param registrationNumbers of the users
     * @param status of the users
     * @param courseIds which the users are part
     * @return params for request
     */
    private LinkedMultiValueMap<String, String> createParamsForPagingRequest(String authorities, String origins, String registrationNumber, String status, String courseIds) {
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", "");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        params.add("authorities", authorities);
        params.add("origins", origins);
        params.add("registrationNumbers", registrationNumber);
        params.add("status", status);
        params.add("courseIds", courseIds);
        return params;
    }

    // Test
    public void testUserWithoutGroups() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "", "", "-1");

        List<User> result;
        List<User> users;

        database.addEmptyCourse();

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 }, };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]);
            users.get(0).setGroups(Collections.emptySet());
            users.get(1).setGroups(Set.of("tumuser"));
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).hasSize(1); // user
            assertThat(result.get(0)).isEqualTo(users.get(0));
        }
    }

    // Test
    public void testUserWithGroups() throws Exception {
        Course course = database.addEmptyCourse();
        courseRepository.save(course);

        final var params = createParamsForPagingRequest("USER", "", FILTER_WITHOUT_REG_NO, "", Long.toString(course.getId()));

        List<User> result;
        List<User> users;

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]);
            users.get(0).setGroups(Collections.emptySet());
            users.get(1).setGroups(Set.of("tumuser"));
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isEqualTo(users.get(1));
        }
    }

    // Test
    public void testUserWithActivatedStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "ACTIVATED", "");

        List<User> result;
        List<User> users;

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]).stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
            users.get(0).setActivated(true);
            users.get(1).setActivated(false);
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).hasSize(2); // admin and user
            assertThat(result.get(0)).isEqualTo(users.get(0));
        }
    }

    // Test
    public void testUserWithDeactivatedStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "DEACTIVATED", "");

        List<User> result;
        List<User> users;

        database.addEmptyCourse();

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]).stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
            users.get(0).setActivated(true);
            users.get(1).setActivated(false);
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).hasSize(1); // user
            assertThat(result.get(0)).isEqualTo(users.get(1));
        }
    }

    // Test
    public void testUserWithInternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL", "WITHOUT_REG_NO", "", "");

        List<User> result;
        List<User> users;

        database.addEmptyCourse();

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]).stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
            users.get(0).setInternal(true);
            users.get(1).setInternal(false);
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).hasSize(1); // user
            assertThat(result.get(0)).isEqualTo(users.get(0));
        }
    }

    // Test
    public void testUserWithExternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "EXTERNAL", "WITHOUT_REG_NO", "", "");

        List<User> result;
        List<User> users;

        database.addEmptyCourse();

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]).stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
            users.get(0).setInternal(true);
            users.get(1).setInternal(false);
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).hasSize(2); // user and admin
            assertThat(result.get(0)).isEqualTo(users.get(1));
        }
    }

    // Test
    public void testUserWithExternalAndInternalStatus() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL,EXTERNAL", "WITHOUT_REG_NO", "", "");

        List<User> result;
        List<User> users;

        database.addEmptyCourse();

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]).stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
            users.get(0).setInternal(true);
            users.get(1).setInternal(false);
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).isEqualTo(Collections.emptyList());
        }
    }

    /**
     * Test for the user search with the following parameters:
     * @Param registrationNumbers
     * @throws Exception if the user is not the same as the expected user
     */
    public void testUserWithRegistrationNumber() throws Exception {
        final var params = createParamsForPagingRequest("USER", "INTERNAL,EXTERNAL", "WITH_REG_NO", "", "");

        List<User> result;
        List<User> users;

        database.addEmptyCourse();

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]).stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
            users.get(0).setRegistrationNumber("");
            users.get(1).setRegistrationNumber("");
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result).isEqualTo(Collections.emptyList());
        }
    }

    /**
     * Test for the user search with the following parameters:
     * @Param registrationNumbers
     * @throws Exception if the user is not the same as the expected user
     */
    public void testUserWithoutRegistrationNumber() throws Exception {
        final var params = createParamsForPagingRequest("USER", "", "WITHOUT_REG_NO", "", "");

        List<User> result;
        List<User> users;

        database.addEmptyCourse();

        int[][] numbers = { { 2, 0, 0, 0 }, { 0, 2, 0, 0 }, { 0, 0, 2, 0 }, { 0, 0, 0, 2 } };
        for (int[] number : numbers) {
            userRepository.deleteAll();
            users = database.addUsers(number[0], number[1], number[2], number[3]).stream().peek(user -> user.setGroups(Collections.emptySet())).toList();
            users.get(0).setRegistrationNumber("5461351");
            users.get(1).setRegistrationNumber("");
            userRepository.saveAll(users);
            result = request.getList("/api/admin/users", HttpStatus.OK, User.class, params);
            assertThat(result.get(0)).isEqualTo(users.get(2));
        }
    }
}
