package de.tum.cit.aet.artemis.core.authentication;

import static de.tum.cit.aet.artemis.core.user.util.UserFactory.USER_PASSWORD;
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

import de.tum.cit.aet.artemis.core.domain.Authority;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.repository.AuthorityRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class LdapAuthenticationIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "ldapauthintegration";

    private static final String INCORRECT_PASSWORD = "incorrectPassword123";

    @Autowired
    protected ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    protected UserTestRepository userRepository;

    @Autowired
    protected AuthorityRepository authorityRepository;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    protected CourseUtilService courseUtilService;

    private static final String NON_EXISTING_LOGIN = TEST_PREFIX + "na";

    private static final String LOGIN = TEST_PREFIX + "student1";

    private static final String NONEXISTENT_LOGIN = TEST_PREFIX + "student2";

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

        userRepository.findOneByLogin(LOGIN).ifPresent(userRepository::delete);
        userRepository.findOneByLogin(NONEXISTENT_LOGIN).ifPresent(userRepository::delete);

        var ldapUserDTO = new LdapUserDto().login(LOGIN);
        ldapUserDTO.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        doReturn(Optional.of(ldapUserDTO)).when(ldapUserService).findByLogin(LOGIN);
        doReturn(Optional.empty()).when(ldapUserService).findByLogin(NONEXISTENT_LOGIN);
        doReturn(true).when(ldapTemplate).compare(ldapUserDTO.getUid().toString(), "userPassword", Utf8.encode(USER_PASSWORD));
        doReturn(false).when(ldapTemplate).compare(ldapUserDTO.getUid().toString(), "userPassword", Utf8.encode(INCORRECT_PASSWORD));
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

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.OK, httpHeaders);
        AuthenticationIntegrationTestHelper.authenticationCookieAssertions(response.getCookie("jwt"), false);
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void testImportUsers() throws Exception {
        StudentDTO existingUser = new StudentDTO(new User((long) 1, LOGIN, "", "", "de", ""));
        StudentDTO nonExistingUser = new StudentDTO(new User((long) 1, NON_EXISTING_LOGIN, "", "", "de", ""));
        var output = request.postListWithResponseBody("/api/admin/users/import", List.of(existingUser, nonExistingUser), StudentDTO.class, HttpStatus.OK);
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

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.UNAUTHORIZED, httpHeaders);
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

        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.UNAUTHORIZED, httpHeaders);
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
        MockHttpServletResponse response = request.postWithoutResponseBody("/api/public/authenticate", loginVM, HttpStatus.BAD_REQUEST, httpHeaders);
        assertThat(response.getCookie("jwt")).isNull();
    }
}
