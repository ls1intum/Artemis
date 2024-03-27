package de.tum.in.www1.artemis.authentication;

import static de.tum.in.www1.artemis.user.UserFactory.USER_PASSWORD;
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
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.exercise.ExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.repository.AuthorityRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.dto.StudentDTO;
import de.tum.in.www1.artemis.service.ldap.LdapUserDto;
import de.tum.in.www1.artemis.web.rest.vm.LoginVM;

class LdapAuthenticationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "ldapauthintegration";

    private static final String INCORRECT_PASSWORD = "incorrectPassword123";

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected AuthorityRepository authorityRepository;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected CourseUtilService courseUtilService;

    @Autowired
    protected ExerciseUtilService exerciseUtilService;

    private static final String NON_EXISTING_USERNAME = TEST_PREFIX + "na";

    private static final String USERNAME = TEST_PREFIX + "student1";

    private static final String NONEXISTENT_USERNAME = TEST_PREFIX + "student2";

    protected ProgrammingExercise programmingExercise;

    protected Course course;

    @BeforeEach
    void setUp() throws InvalidNameException {
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        courseUtilService.addOnlineCourseConfigurationToCourse(course);
        programmingExercise = exerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).orElseThrow();

        final var userAuthority = new Authority(Role.STUDENT.getAuthority());
        final var instructorAuthority = new Authority(Role.INSTRUCTOR.getAuthority());
        final var adminAuthority = new Authority(Role.ADMIN.getAuthority());
        final var taAuthority = new Authority(Role.TEACHING_ASSISTANT.getAuthority());
        authorityRepository.saveAll(List.of(userAuthority, instructorAuthority, adminAuthority, taAuthority));

        userRepository.findOneByLogin(USERNAME).ifPresent(userRepository::delete);
        userRepository.findOneByLogin(NONEXISTENT_USERNAME).ifPresent(userRepository::delete);

        var ldapUserDTO = new LdapUserDto().username(USERNAME);
        ldapUserDTO.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        doReturn(Optional.of(ldapUserDTO)).when(ldapUserService).findByUsername(USERNAME);
        doReturn(Optional.empty()).when(ldapUserService).findByUsername(NONEXISTENT_USERNAME);
        doReturn(true).when(ldapTemplate).compare(ldapUserDTO.getUid().toString(), "userPassword", Utf8.encode(USER_PASSWORD));
        doReturn(false).when(ldapTemplate).compare(ldapUserDTO.getUid().toString(), "userPassword", Utf8.encode(INCORRECT_PASSWORD));
    }

    @Test
    @WithAnonymousUser
    void testJWTAuthentication() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), false);
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void testImportUsers() throws Exception {
        StudentDTO existingUser = new StudentDTO(new User((long) 1, USERNAME, "", "", "de", ""));
        StudentDTO nonExistingUser = new StudentDTO(new User((long) 1, NON_EXISTING_USERNAME, "", "", "de", ""));
        var output = request.postListWithResponseBody("/api/admin/users/import", List.of(existingUser, nonExistingUser), StudentDTO.class, HttpStatus.OK);
        assertThat(output).hasSize(1);
        assertThat(output.get(0).login()).isEqualTo(NON_EXISTING_USERNAME);
    }

    @Test
    @WithAnonymousUser
    void testIncorrectPasswordAttempt() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword(INCORRECT_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.UNAUTHORIZED, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }

    @Test
    @WithAnonymousUser
    void testNonExistentUserNameAttempt() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(NONEXISTENT_USERNAME);
        loginVM.setPassword(USER_PASSWORD);
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.UNAUTHORIZED, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }

    @Test
    @WithAnonymousUser
    void testEmptyPasswordAttempt() throws Exception {
        LoginVM loginVM = new LoginVM();
        loginVM.setUsername(USERNAME);
        loginVM.setPassword("");
        loginVM.setRememberMe(true);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        // validation fails due to empty password is validated against min size
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.BAD_REQUEST, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }
}
