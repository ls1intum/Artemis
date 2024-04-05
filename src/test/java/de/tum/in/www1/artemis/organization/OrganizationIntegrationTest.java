package de.tum.in.www1.artemis.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseFactory;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.web.rest.dto.OrganizationCountDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class OrganizationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "organizationtest";

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private OrganizationRepository organizationRepo;

    @Autowired
    private OrganizationUtilService organizationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    /**
     * Test if getting courses a user can enroll in works with multi organization and
     * filters out basing on user's organizations
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "login2")
    void testGetCoursesToEnrollWithOrganizationsEnabled() throws Exception {

        Organization organization = organizationUtilService.createOrganization();
        Set<Organization> organizations = new HashSet<>();
        organizations.add(organization);

        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "login2");
        student.setOrganizations(organizations);
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course course2 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
        course1.setEnrollmentEnabled(true);
        course1.setEnrollmentStartDate(pastTimestamp);
        course1.setEnrollmentEndDate(futureTimestamp);
        course2.setEnrollmentEnabled(true);
        course2.setEnrollmentStartDate(pastTimestamp);
        course2.setEnrollmentEndDate(futureTimestamp);
        course1.setOrganizations(organizations);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);

        List<Course> coursesToEnroll = request.getList("/api/courses/for-enrollment", HttpStatus.OK, Course.class);
        assertThat(coursesToEnroll).contains(course1).contains(course2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "login1")
    void testEnrollForCourseWithOrganizationsEnabled() throws Exception {

        Organization organization = organizationUtilService.createOrganization();
        Set<Organization> organizations = new HashSet<>();
        organizations.add(organization);

        Organization otherOrganization = organizationUtilService.createOrganization();
        Set<Organization> otherOrganizations = new HashSet<>();
        otherOrganizations.add(otherOrganization);

        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "login1");
        student.setOrganizations(organizations);
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course course2 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
        Course course3 = CourseFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");

        course1.setEnrollmentEnabled(true);
        course1.setEnrollmentStartDate(pastTimestamp);
        course1.setEnrollmentEndDate(futureTimestamp);
        course2.setEnrollmentEnabled(true);
        course2.setEnrollmentStartDate(pastTimestamp);
        course2.setEnrollmentEndDate(futureTimestamp);
        course1.setOrganizations(organizations);
        course3.setOrganizations(otherOrganizations);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);
        course3 = courseRepo.save(course3);

        Set<String> updatedGroups = request.postWithResponseBody("/api/courses/" + course1.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is enrolled in course").contains(course1.getStudentGroupName());

        updatedGroups = request.postWithResponseBody("/api/courses/" + course2.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is enrolled in course").contains(course2.getStudentGroupName());

        request.postWithResponseBody("/api/courses/" + course3.getId() + "/enroll", null, Set.class, HttpStatus.FORBIDDEN);
    }

    /**
     * Test adding a course to a given organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAddCourseToOrganization() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        request.postWithoutLocation("/api/admin/organizations/" + organization.getId() + "/courses/" + course1.getId(), null, HttpStatus.OK, null);

        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getCourses()).contains(course1);
    }

    /**
     * Test removing a course from a given organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemoveCourseToOrganization() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        courseRepo.addOrganizationToCourse(course1.getId(), organization);

        Organization originalOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(originalOrganization.getCourses()).contains(course1);

        request.delete("/api/admin/organizations/" + organization.getId() + "/courses/" + course1.getId(), HttpStatus.OK);

        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getCourses()).isEmpty();
    }

    /**
     * Test adding user to a given organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAddUserToOrganization() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testAddUserToOrganization");

        request.postWithoutLocation("/api/admin/organizations/" + organization.getId() + "/users/" + student.getLogin(), null, HttpStatus.OK, null);
        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getUsers()).contains(student);
    }

    /**
     * Test removing user from a given organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemoveUserFromOrganization() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testRemoveUser_");

        organization.getUsers().add(student);
        organization = organizationRepo.save(organization);

        assertThat(organization.getUsers()).contains(student);

        request.delete("/api/admin/organizations/" + organization.getId() + "/users/" + student.getLogin(), HttpStatus.OK);
        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(updatedOrganization.getUsers()).doesNotContain(student);
    }

    /**
     * Test adding a new organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAddOrganization() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> organizationRepo.findByIdElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> organizationRepo.findByIdWithEagerUsersAndCoursesElseThrow(Long.MAX_VALUE));

        Organization organization = organizationUtilService.createOrganization();

        Organization updatedOrganization = request.postWithResponseBody("/api/admin/organizations", organization, Organization.class, HttpStatus.OK);
        Organization updatedOrganization2 = request.get("/api/admin/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization2).isNotNull();
        assertThat(updatedOrganization.getId()).isNotNull();
    }

    /**
     * Test updating an existing organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOrganization() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        organization.setName("UpdatedName");

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + organization.getId(), organization, Organization.class, HttpStatus.OK);
        assertThat(updatedOrganization.getName()).isEqualTo("UpdatedName");
    }

    /**
     * Test updating an existing organization when the Id in the RequestBody is null
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOrganization_idInBodyNull() throws Exception {
        Organization initialOrganization = organizationUtilService.createOrganization();
        long initialOrganizationId = initialOrganization.getId();
        organizationRepo.save(initialOrganization);
        initialOrganization.setId(null);

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + initialOrganizationId, initialOrganization, Organization.class,
                HttpStatus.BAD_REQUEST);
        assertThat(updatedOrganization).isNull();
    }

    /**
     * Test updating an existing organization when the id in the path doesn't match the one in the Body
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOrganization_IdInPathWrong() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        organizationRepo.save(organization);
        String initialName = organization.getName();
        organization.setName("UpdatedName");
        long wrongId = 1337420;

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + wrongId, organization, Organization.class, HttpStatus.BAD_REQUEST);
        organization.setName(initialName);
        assertThat(updatedOrganization).isNull();
    }

    /**
     * Test delete an organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteOrganization() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);

        request.delete("/api/admin/organizations/" + organization.getId(), HttpStatus.OK);
        request.get("/api/admin/organizations/" + organization.getId(), HttpStatus.NOT_FOUND, Organization.class);
    }

    /**
     * Test get all organizations
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllOrganizations() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        Organization organization2 = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);
        organization2 = organizationRepo.save(organization2);

        List<Organization> result = request.getList("/api/admin/organizations", HttpStatus.OK, Organization.class);
        assertThat(result).contains(organization, organization2);
    }

    /**
     * Test get number of users and courses of all organizations
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetNumberOfUsersAndCoursesOfAllOrganizations() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testGetNumberOfUsersOfAll_");

        userRepo.addOrganizationToUser(student.getId(), organization);

        List<OrganizationCountDTO> result = request.getList("/api/admin/organizations/count-all", HttpStatus.OK, OrganizationCountDTO.class);

        assertThat(result).isNotEmpty();

        assertThat(result.get(result.size() - 1).numberOfCourses()).isEqualTo(1);
        assertThat(result.get(result.size() - 1).numberOfUsers()).isEqualTo(1);
    }

    /**
     * Test get number of users and courses of a given organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetNumberOfUsersAndCoursesOfOrganization() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testGetNumberOfUsers_");

        userRepo.addOrganizationToUser(student.getId(), organization);

        OrganizationCountDTO result = request.get("/api/admin/organizations/" + organization.getId() + "/count", HttpStatus.OK, OrganizationCountDTO.class);

        assertThat(result.numberOfUsers()).isEqualTo(1);
        assertThat(result.numberOfCourses()).isEqualTo(1);
    }

    /**
     * Test retrieving an organization by its id
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationById() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);
        courseRepo.addOrganizationToCourse(course1.getId(), organization);

        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testGetOrganizationById");

        userRepo.addOrganizationToUser(student.getId(), organization);
        // invoked remove to make sure it works correctly
        userRepo.removeOrganizationFromUser(student.getId(), organization);
        userRepo.addOrganizationToUser(student.getId(), organization);

        Organization result = request.get("/api/admin/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
        Organization resultWithCoursesAndUsers = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(result.getId()).isEqualTo(organization.getId());
        assertThat(result.getName()).isEqualTo(organization.getName());

        assertThat(resultWithCoursesAndUsers.getCourses()).contains(course1);

        assertThat(resultWithCoursesAndUsers.getUsers()).contains(student);
    }

    /**
     * Test retriving all organizations containing a given course
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllOrganizationByCourse() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);

        List<Organization> result = request.getList("/api/organizations/courses/" + course1.getId(), HttpStatus.OK, Organization.class);
        assertThat(result).contains(organization);
    }

    /**
     * Test retrieve all organization containing a given user
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllOrganizationByUser() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testGetAllOrganizationByUser");

        userRepo.addOrganizationToUser(student.getId(), organization);

        List<Organization> result = request.getList("/api/admin/organizations/users/" + student.getId(), HttpStatus.OK, Organization.class);

        assertThat(result).contains(organization);
    }

    /**
     * Test indexing of organization over all users
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testOrganizationIndexing() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testOrganizationIndexing");

        organization.getUsers().add(student);
        organization = organizationRepo.save(organization);

        organization.setEmailPattern("^" + student.getEmail() + "$");

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + organization.getId(), organization, Organization.class, HttpStatus.OK);
        updatedOrganization = request.get("/api/admin/organizations/" + updatedOrganization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(updatedOrganization.getUsers()).hasSize(1);
        assertThat(updatedOrganization.getUsers()).contains(student);

        var organizations = organizationRepo.getAllMatchingOrganizationsByUserEmail(student.getEmail());
        assertThat(organizations).containsExactly(organization);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationTitle() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        organization.setName("Test Organization");
        organization = organizationRepo.save(organization);

        final var title = request.get("/api/admin/organizations/" + organization.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(organization.getName());
    }
}
