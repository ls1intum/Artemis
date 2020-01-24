package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.security.ArtemisInternalAuthenticationProvider;
import de.tum.in.www1.artemis.service.UserService;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.vm.ManagedUserVM;

@ActiveProfiles("artemis")
@TestPropertySource(properties = "artemis.user-management.use-external=false")
public class InternalAuthenticationIntegrationTest extends AuthenticationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ArtemisInternalAuthenticationProvider artemisInternalAuthenticationProvider;

    @Autowired
    private CourseRepository courseRepository;

    private User student;

    @Override
    @BeforeEach
    public void setUp() {
        database.addUsers(1, 0, 0);
        super.setUp();

        student = userRepository.findOneWithGroupsAndAuthoritiesByLogin("student1").get();
        final var encrPassword = userService.passwordEncoder().encode("0000");
        student.setPassword(encrPassword);
        userRepository.save(student);
        ltiLaunchRequest.setLis_person_contact_email_primary(student.getEmail());
    }

    @Override
    @Test
    public void launchLtiRequest_authViaEmail_success() throws Exception {
        super.launchLtiRequest_authViaEmail_success();

        final var updatedStudent = userRepository.findOneWithGroupsAndAuthoritiesByLogin("student1").get();
        assertThat(student).isEqualTo(updatedStudent);
    }

    @Test
    @WithAnonymousUser
    public void authenticateAfterLtiRequest_success() throws Exception {
        super.launchLtiRequest_authViaEmail_success();

        final var auth = new TestingAuthenticationToken(student.getLogin(), "0000");
        final var authResponse = artemisInternalAuthenticationProvider.authenticate(auth);

        assertThat(authResponse.getCredentials().toString()).isEqualTo(student.getPassword());
        assertThat(authResponse.getPrincipal()).isEqualTo(student.getLogin());
    }

    @Test
    @WithMockUser(username = "ab123cd")
    public void registerForCourse_internalAuth_success() throws Exception {
        final var student = ModelFactory.generateActivatedUser("ab123cd");
        userRepository.save(student);

        final var pastTimestamp = ZonedDateTime.now().minusDays(5);
        final var futureTimestamp = ZonedDateTime.now().plusDays(5);
        var course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "instructor");
        course1.setRegistrationEnabled(true);

        course1 = courseRepository.save(course1);

        final var updatedStudent = request.postWithResponseBody("/api/courses/" + course1.getId() + "/register", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getGroups()).as("User is registered for course").contains(course1.getStudentGroupName());
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void updateUserWithRemovedGroups_internalAuth_successful() throws Exception {
        final var newGroups = Set.of("foo", "bar");
        student.setGroups(newGroups);
        final var managedUserVM = new ManagedUserVM(student);

        final var response = request.putWithResponseBody("/api/users", managedUserVM, User.class, HttpStatus.OK);
        final var updatedUserIndDB = userRepository.findOneWithGroupsAndAuthoritiesByLogin(student.getLogin()).get();
        updatedUserIndDB.setPassword(userService.decryptPasswordByLogin(updatedUserIndDB.getLogin()).get());

        assertThat(response).isNotNull();
        assertThat(student).as("Returned user is equal to sent update").isEqualTo(response);
        assertThat(student).as("Updated user in DB is equal to sent update").isEqualTo(updatedUserIndDB);
    }
}
