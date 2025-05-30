package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.StudentDTO;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserFactory;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class CourseLdapRegistrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "courseservice";

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private PasswordService passwordService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 0, 0, 1);
    }

    @ParameterizedTest(name = "{displayName} [{index}]")
    @ValueSource(strings = { "student", "tutor", "editor", "instructor" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterLDAPUsersInCourse(String user) throws Exception {
        Course course1 = courseUtilService.createCourse();
        course1.setStudentGroupName("student");
        courseRepository.save(course1);
        String userName = TEST_PREFIX + user + "100";

        // setup mocks
        var ldapUser1Dto = new LdapUserDto().firstName(userName).lastName(userName).login(userName).email(userName + "@tum.de");

        StudentDTO dto1 = switch (user) {
            case "tutor" -> new StudentDTO(null, userName, userName, "1000001", null);
            case "editor" -> new StudentDTO(null, userName, userName, null, userName + "@tum.de");
            case "instructor" -> new StudentDTO(userName, userName, userName, null, null);
            default -> new StudentDTO(userName, userName, userName, "1000002", userName + "@tum.de");
        };

        if (dto1.login() != null) {
            doReturn(Optional.of(ldapUser1Dto)).when(ldapUserService).findByLogin(dto1.login());
        }
        else if (dto1.email() != null) {
            doReturn(Optional.of(ldapUser1Dto)).when(ldapUserService).findByAnyEmail(dto1.email());
        }
        else {
            doReturn(Optional.of(ldapUser1Dto)).when(ldapUserService).findByRegistrationNumber(dto1.registrationNumber());
        }
        StudentDTO dto2 = new StudentDTO(null, null, null, null, null);

        var failures = request.postListWithResponseBody("/api/core/courses/" + course1.getId() + "/" + user + "s", List.of(dto1, dto2), StudentDTO.class, HttpStatus.OK);
        assertThat(failures).containsExactly(dto2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testRegisterLdapEdgeCaseUserInCourse() throws Exception {
        Course course1 = courseUtilService.createCourse();
        course1.setStudentGroupName("student");
        courseRepository.save(course1);

        // Setup: the user already exists in the database, but does not have a registration number
        String userName = "go42tum";
        String registrationNumber = "1234567";
        userUtilService.createAndSaveUser(userName, passwordService.hashPassword(UserFactory.USER_PASSWORD));

        // setup mocks
        var ldapUser1Dto = new LdapUserDto().firstName("Erika").lastName("Musterfrau").login(userName).email(userName + "@tum.de").registrationNumber(registrationNumber);

        // the instructor searches for a registration number, so the user is not found in the database, but in the LDAP service, this should still work
        StudentDTO dto1 = new StudentDTO(null, null, null, registrationNumber, null);
        doReturn(Optional.of(ldapUser1Dto)).when(ldapUserService).findByRegistrationNumber(dto1.registrationNumber());
        var failures = request.postListWithResponseBody("/api/core/courses/" + course1.getId() + "/students", List.of(dto1), StudentDTO.class, HttpStatus.OK);
        assertThat(failures).isEmpty();

        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin("go42tum");
        assertThat(student).isPresent();
        assertThat(student.get().getRegistrationNumber()).isEqualTo("1234567");
        assertThat(student.get().getFirstName()).isEqualTo("Erika");
        assertThat(student.get().getLastName()).isEqualTo("Musterfrau");
        assertThat(student.get().getEmail()).isEqualTo(userName + "@tum.de");
        assertThat(student.get().getGroups()).contains(course1.getStudentGroupName());
    }
}
