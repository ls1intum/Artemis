package de.tum.in.www1.artemis.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.programmingexercise.MockDelegate;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
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
    PasswordService passwordService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    protected RequestUtilService request;

    private MockDelegate mockDelegate;

    private User student;

    private final int numberOfStudents = 50;

    private final int numberOfTutors = 1;

    private final int numberOfInstructors = 1;

    public void setup(MockDelegate mockDelegate) throws Exception {
        this.mockDelegate = mockDelegate;

        List<User> users = database.addUsers(numberOfStudents, numberOfTutors, numberOfInstructors);
        student = users.get(0);
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
        mockDelegate.mockDeleteUserInUserManagement(student, true);

        request.delete("/api/users/" + student.getLogin(), HttpStatus.OK);

        var deletedUser = userRepository.findById(student.getId());
        assertThat(deletedUser).isEmpty();
    }

    // Test
    public void deleteUser_doesntExistInUserManagement_isSuccessful() throws Exception {
        mockDelegate.mockDeleteUserInUserManagement(student, false);

        request.delete("/api/users/" + student.getLogin(), HttpStatus.OK);

        var deletedUser = userRepository.findById(student.getId());
        assertThat(deletedUser).isEmpty();
    }

    // Test
    public void updateUser_asAdmin_isSuccessful() throws Exception {
        final var newPassword = "bonobo42";
        final var newEmail = "bonobo42@tum.com";
        final var newFirstName = "Bruce";
        final var newGroups = Set.of("foo", "bar");
        final var newLastName = "Wayne";
        final var newImageUrl = "foobar.png";
        final var newLangKey = "DE";
        final var newAuthorities = Set.of(AuthoritiesConstants.TEACHING_ASSISTANT).stream().map(authorityRepository::findById).filter(Optional::isPresent).map(Optional::get)
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
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, oldGroups);

        var managedUserVM = new ManagedUserVM(student);
        managedUserVM.setPassword(newPassword);
        final var response = request.putWithResponseBody("/api/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).get();
        updatedUserIndDB.setPassword(passwordService.decryptPasswordByLogin(updatedUserIndDB.getLogin()).get());

        assertThat(response).isNotNull();
        response.setPassword(passwordService.decryptPasswordByLogin(response.getLogin()).get());
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }

    // Test
    public void updateUser_withNullPassword_oldPasswordNotChanged() throws Exception {
        student.setPassword(null);
        final var oldPassword = userRepository.findById(student.getId()).get().getPassword();
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, student.getGroups());

        request.put("/api/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userRepository.findById(student.getId()).get();

        assertThat(oldPassword).as("Password did not change").isEqualTo(userInDB.getPassword());
    }

    // Test
    public void updateUserLogin() throws Exception {
        var oldLogin = student.getLogin();
        student.setLogin("new-login");
        mockDelegate.mockUpdateUserInUserManagement(oldLogin, student, student.getGroups());

        request.put("/api/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userRepository.findById(student.getId()).get();

        assertThat(userInDB.getLogin()).isEqualTo(student.getLogin());
        assertThat(userInDB.getId()).isEqualTo(student.getId());
    }

    // Test
    public void updateUser_withExternalUserManagement() throws Exception {
        student.setFirstName("changed");
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), student, student.getGroups());

        request.put("/api/users", new ManagedUserVM(student), HttpStatus.OK);

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
        mockDelegate.mockUpdateUserInUserManagement(student.getLogin(), updatedUser, student.getGroups());
        request.put("/api/users", new ManagedUserVM(updatedUser), HttpStatus.OK);

        var updatedUserOrEmpty = userRepository.findOneWithGroupsAndAuthoritiesByLogin(updatedUser.getLogin());
        assertThat(updatedUserOrEmpty).isPresent();

        updatedUser = updatedUserOrEmpty.get();
        assertThat(updatedUser.getId()).isEqualTo(student.getId());
        assertThat(updatedUser.getGroups().size()).isEqualTo(1);
        assertThat(updatedUser.getGroups()).contains("tutor");
    }

    // Test
    public void createUser_asAdmin_isSuccessful() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).get();
        userInDB.setPassword(passwordService.decryptPasswordByLogin(userInDB.getLogin()).get());
        student.setId(response.getId());
        response.setPassword("foobar");

        assertThat(student).as("New user is equal to request response").isEqualTo(response);
        assertThat(student).as("New user is equal to new user in DB").isEqualTo(userInDB);
    }

    // Test
    public void createUser_asAdmin_existsInCi_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, true);

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_illegalLogin_internalError() throws Exception {
        student.setId(null);
        student.setLogin("@someusername");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalCiUserManagement_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExernalUserManagement(student, false, true);

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_asAdmin_failInExternalVcsUserManagement_internalError() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");

        mockDelegate.mockFailToCreateUserInExernalUserManagement(student, true, false);

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response).isNull();
    }

    // Test
    public void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        student.setId(null);
        student.setEmail("batman@invalid.tum");
        student.setLogin("batman");
        student.setPassword(null);

        mockDelegate.mockCreateUserInUserManagement(student, false);

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
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

        request.post("/api/users", new ManagedUserVM(newUser), HttpStatus.CREATED);

        var createdUser = userRepository.findOneByEmailIgnoreCase(newUser.getEmail());
        assertThat(createdUser).isPresent();
        assertThat(createdUser.get().getId()).isNotNull();
    }

    // Test
    public void createUserWithGroups() throws Exception {
        var course = database.addEmptyCourse();
        database.addProgrammingExerciseToCourse(course, false);
        courseRepository.save(course);

        var newUser = student;
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        newUser.setGroups(Set.of("tutor", "instructor"));

        mockDelegate.mockCreateUserInUserManagement(newUser, false);

        request.post("/api/users", new ManagedUserVM(newUser), HttpStatus.CREATED);

        var createdUserOrEmpty = userRepository.findOneWithGroupsAndAuthoritiesByLogin(newUser.getLogin());
        assertThat(createdUserOrEmpty).isPresent();

        var createdUser = createdUserOrEmpty.get();
        assertThat(createdUser.getId()).isNotNull();
        assertThat(createdUser.getGroups().size()).isEqualTo(2);
        assertThat(createdUser.getGroups()).isEqualTo(newUser.getGroups());
    }

    // Test
    public void getUsers_asAdmin_isSuccessful() throws Exception {
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", "");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        List<UserDTO> users = request.getList("/api/users", HttpStatus.OK, UserDTO.class, params);
        assertThat(users).hasSize(numberOfStudents + numberOfTutors + numberOfInstructors + 1); // +1 for admin user himself
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
        final var params = new LinkedMultiValueMap<String, String>();
        params.add("page", "0");
        params.add("pageSize", "100");
        params.add("searchTerm", "student1@test.de");
        params.add("sortingOrder", "ASCENDING");
        params.add("sortedColumn", "id");
        List<User> users = request.getList("/api/users", HttpStatus.OK, User.class, params);
        assertThat(users).hasSize(1);
        assertThat(users.get(0).getEmail()).isEqualTo("student1@test.de");
    }

    // Test
    public void getAuthorities_asAdmin_isSuccessful() throws Exception {
        List<String> authorities = request.getList("/api/users/authorities", HttpStatus.OK, String.class);
        assertThat(authorities).isEqualTo(List.of("ROLE_ADMIN", "ROLE_INSTRUCTOR", "ROLE_TA", "ROLE_USER"));
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
        request.getList("/api/users", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/users/authorities", HttpStatus.FORBIDDEN, String.class);
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
}
