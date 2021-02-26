package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.AuthoritiesConstants;
import de.tum.in.www1.artemis.service.dto.UserDTO;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

public class UserIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    PasswordService passwordService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private JiraRequestMockProvider jiraRequestMockProvider;

    private User student;

    private final int numberOfStudents = 50;

    private final int numberOfTutors = 1;

    private final int numberOfInstructors = 1;

    @BeforeEach
    public void setUp() {
        List<User> users = database.addUsers(numberOfStudents, numberOfTutors, numberOfInstructors);
        student = users.get(0);
        users.forEach(user -> cacheManager.getCache(UserRepository.USERS_CACHE).evict(user.getLogin()));
        jiraRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void teardown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
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
        final var managedUserVM = new ManagedUserVM(student);
        managedUserVM.setPassword(newPassword);
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(managedUserVM.getGroups());
        jiraRequestMockProvider.mockRemoveUserFromGroup(oldGroups, student.getLogin());
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(newGroups);

        final var response = request.putWithResponseBody("/api/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).get();
        updatedUserIndDB.setPassword(passwordService.decryptPasswordByLogin(updatedUserIndDB.getLogin()).get());

        assertThat(response).isNotNull();
        response.setPassword(passwordService.decryptPasswordByLogin(response.getLogin()).get());
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void updateUser_withNullPassword_oldPasswordNotChanged() throws Exception {
        student.setPassword(null);
        final var oldPassword = userRepository.findById(student.getId()).get().getPassword();
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(student.getGroups());

        request.put("/api/users", new ManagedUserVM(student), HttpStatus.OK);
        final var userInDB = userRepository.findById(student.getId()).get();

        assertThat(oldPassword).as("Password did not change").isEqualTo(userInDB.getPassword());
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void updateUser_asInstructor_forbidden() throws Exception {
        request.put("/api/users", new ManagedUserVM(student), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void updateUser_asTutor_forbidden() throws Exception {
        request.put("/api/users", new ManagedUserVM(student), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void updateUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(student.getGroups());

        request.put("/api/users", new ManagedUserVM(student), HttpStatus.OK);

        verifyNoInteractions(versionControlService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_asAdmin_isSuccessful() throws Exception {
        student.setId(null);
        student.setLogin("batman");
        student.setPassword("foobar");
        student.setEmail("batman@secret.invalid");
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(student.getGroups());
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(student.getGroups());

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).get();
        userInDB.setPassword(passwordService.decryptPasswordByLogin(userInDB.getLogin()).get());
        student.setId(response.getId());
        response.setPassword("foobar");

        assertThat(student).as("New user is equal to request response").isEqualTo(response);
        assertThat(student).as("New user is equal to new user in DB").isEqualTo(userInDB);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void createUser_withNullAsPassword_generatesRandomPassword() throws Exception {
        student.setId(null);
        student.setEmail("batman@invalid.tum");
        student.setLogin("batman");
        student.setPassword(null);
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(student.getGroups());
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(student.getGroups());

        final var response = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(response).isNotNull();
        final var userInDB = userRepository.findById(response.getId()).get();

        assertThat(userInDB.getPassword()).isNotBlank();
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void createUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        final var newUser = new ManagedUserVM(student);
        newUser.setId(null);
        newUser.setLogin("batman");
        newUser.setEmail("foobar@tum.com");
        jiraRequestMockProvider.mockIsGroupAvailableForMultiple(student.getGroups());
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(student.getGroups());

        request.post("/api/users", newUser, HttpStatus.CREATED);

        verifyNoInteractions(versionControlService);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void deleteUser_withExternalUserManagement_vcsManagementHasNotBeenCalled() throws Exception {
        request.delete("/api/users/" + student.getLogin(), HttpStatus.OK);

        verifyNoInteractions(versionControlService);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void searchUsers_asInstructor_isSuccessful() throws Exception {
        final String loginOrName = "student1";
        List<UserDTO> users = request.getList("/api/users/search?loginOrName=" + loginOrName, HttpStatus.OK, UserDTO.class);
        assertThat(users).hasSize(11); // size([student1, student10, ... student19]) = 11
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void searchUsers_asAdmin_badRequest() throws Exception {
        final String loginOrName = "ab"; // too short (needs at least 3 characters)
        request.getList("/api/users/search?loginOrName=" + loginOrName, HttpStatus.BAD_REQUEST, UserDTO.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void searchUsers_asTutor_forbidden() throws Exception {
        final String loginOrName = "student";
        request.getList("/api/users/search?loginOrName=" + loginOrName, HttpStatus.FORBIDDEN, UserDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
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

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getAuthorities_asAdmin_isSuccessful() throws Exception {
        List<String> authorities = request.getList("/api/users/authorities", HttpStatus.OK, String.class);
        assertThat(authorities).isEqualTo(List.of("ROLE_ADMIN", "ROLE_INSTRUCTOR", "ROLE_TA", "ROLE_USER"));
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getUsersOrAuthorities_asInstructor_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void getUsersOrAuthorities_asTutor_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getUsersOrAuthorities_asStudent_forbidden() throws Exception {
        getUsersOrAuthorities_forbidden();
    }

    private void getUsersOrAuthorities_forbidden() throws Exception {
        request.getList("/api/users", HttpStatus.FORBIDDEN, User.class);
        request.getList("/api/users/authorities", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void getUser_asAdmin_isSuccessful() throws Exception {
        final String userLogin = "student1";
        UserDTO userDTO = request.get("/api/users/" + userLogin, HttpStatus.OK, UserDTO.class);
        assertThat(userDTO.getLogin()).isEqualTo(userLogin);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void updateUserNotificationDate_asStudent_isSuccessful() throws Exception {
        request.put("/api/users/notification-date", null, HttpStatus.OK);
        User userInDB = userRepository.findOneByLogin("student1").get();
        assertThat(userInDB.getLastNotificationRead()).isAfterOrEqualTo(ZonedDateTime.now().minusSeconds(1));
    }
}
