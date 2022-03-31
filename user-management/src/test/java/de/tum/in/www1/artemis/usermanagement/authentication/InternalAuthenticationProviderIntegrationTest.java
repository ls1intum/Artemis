package de.tum.in.www1.artemis.usermanagement.authentication;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.user.PasswordService;
import de.tum.in.www1.artemis.usermanagement.AbstractSpringIntegrationJenkinsGitlabTest;
import de.tum.in.www1.artemis.connector.GitlabRequestMockProvider;

import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.tum.in.www1.artemis.domain.Authority.*;
import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

public class InternalAuthenticationProviderIntegrationTest extends AbstractSpringIntegrationJenkinsGitlabTest {

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthorityRepository authorityRepository;

    @Autowired
    private GitlabRequestMockProvider gitlabRequestMockProvider;

    @Value("${info.guided-tour.course-group-students:#{null}}")
    private Optional<String> tutorialGroupStudents;

    @Value("${info.guided-tour.course-group-tutors:#{null}}")
    private Optional<String> tutorialGroupTutors;

    @Value("${info.guided-tour.course-group-editors:#{null}}")
    private Optional<String> tutorialGroupEditors;

    @Value("${info.guided-tour.course-group-instructors:#{null}}")
    private Optional<String> tutorialGroupInstructors;

    private User student;

    private static final String USERNAME = "student1";


    @BeforeEach
    public void setUp() {
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsServer);

        database.addUsers(1, 0, 0, 0);

        final var userAuthority = new Authority(Role.STUDENT.getAuthority());
        final var instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());
        final var adminAuthority = new Authority(Role.ADMIN.getAuthority());
        final var taAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));

        student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(USERNAME).get();
        final var encodedPassword = passwordService.hashPassword(USER_PASSWORD);
        student.setPassword(encodedPassword);
        userRepository.save(student);
    }

    @AfterEach
    public void teardown() {
        database.resetDatabase();
    }

    @NotNull
    private User createUserWithRestApi(Set<Authority> authorities) throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        gitlabRequestMockProvider.mockGetUserID();
        database.addTutorialCourse();

        student.setId(null);
        student.setLogin("user1");
        student.setPassword("foobar");
        student.setEmail("user1@secret.invalid");
        student.setAuthorities(authorities);

        var exercises = programmingExerciseRepository.findAllByInstructorOrEditorOrTAGroupNameIn(student.getGroups());
        assertThat(exercises).hasSize(0);
        jenkinsRequestMockProvider.mockCreateUser(student, false, false, false);

        final var user = request.postWithResponseBody("/api/users", new ManagedUserVM(student), User.class, HttpStatus.CREATED);
        assertThat(user).isNotNull();
        return user;
    }

    private void assertUserGroups(User user, boolean students, boolean tutors, boolean editors, boolean instructors) {
        if (students) {
            assertThat(user.getGroups()).contains(tutorialGroupStudents.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupStudents.get());
        }
        if (tutors) {
            assertThat(user.getGroups()).contains(tutorialGroupTutors.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupTutors.get());
        }
        if (editors) {
            assertThat(user.getGroups()).contains(tutorialGroupEditors.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupEditors.get());
        }
        if (instructors) {
            assertThat(user.getGroups()).contains(tutorialGroupInstructors.get());
        }
        else {
            assertThat(user.getGroups()).doesNotContain(tutorialGroupInstructors.get());
        }
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void createUserWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY));
        assertUserGroups(user, true, false, false, false);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void createTutorWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY, TA_AUTHORITY));
        assertUserGroups(user, true, true, false, false);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void createEditorWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY, TA_AUTHORITY, EDITOR_AUTHORITY));
        assertUserGroups(user, true, true, true, false);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void createInstructorWithInternalUserManagementAndAutomatedTutorialGroupsAssignment() throws Exception {
        final User user = createUserWithRestApi(Set.of(USER_AUTHORITY, TA_AUTHORITY, EDITOR_AUTHORITY, INSTRUCTOR_AUTHORITY));
        assertUserGroups(user, true, true, true, true);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void updateUserWithRemovedGroups_internalAuth_successful() throws Exception {
        gitlabRequestMockProvider.enableMockingOfRequests();
        gitlabRequestMockProvider.mockUpdateUser();

        final var oldGroups = student.getGroups();
        final var newGroups = Set.of("foo", "bar");
        student.setGroups(newGroups);
        final var managedUserVM = new ManagedUserVM(student);
        managedUserVM.setPassword("12345678");

        jenkinsRequestMockProvider.mockUpdateUserAndGroups(student.getLogin(), student, newGroups, oldGroups, false);

        final var response = request.putWithResponseBody("/api/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).get();
        assertThat(passwordService.checkPasswordMatch(managedUserVM.getPassword(), updatedUserIndDB.getPassword())).isTrue();

        // Skip passwords for comparison
        updatedUserIndDB.setPassword(null);
        student.setPassword(null);

        assertThat(response).isNotNull();
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }
}
