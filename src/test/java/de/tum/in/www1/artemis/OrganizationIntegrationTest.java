package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.OrganizationCountDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class OrganizationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private OrganizationRepository organizationRepo;

    private List<User> users;

    @BeforeEach
    void initTestCase() {
        users = database.addUsers(1, 1, 0, 1);
        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void resetDatabase() {
        bitbucketRequestMockProvider.reset();
        bambooRequestMockProvider.reset();
        database.resetDatabase();
    }

    /**
     * Test if getting courses a user can register to works with multi organization and
     * filters out basing on user's organizations
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "ab12cde")
    void testGetCoursesToRegisterWithOrganizationsEnabled() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        Set<Organization> organizations = new HashSet<>();
        organizations.add(organization);

        User student = ModelFactory.generateActivatedUser("ab12cde");
        student.setOrganizations(organizations);
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
        course1.setRegistrationEnabled(true);
        course2.setRegistrationEnabled(true);
        course1.setOrganizations(organizations);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course1.getStudentGroupName()));
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course2.getStudentGroupName()));

        List<Course> coursesToRegister = request.getList("/api/courses/for-registration", HttpStatus.OK, Course.class);
        assertThat(coursesToRegister).contains(course1).contains(course2);
    }

    @Test
    @WithMockUser(username = "ab12cde")
    void testRegisterForCourseWithOrganizationsEnabled() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        Set<Organization> organizations = new HashSet<>();
        organizations.add(organization);

        Organization otherOrganization = database.createOrganization();
        otherOrganization.setName("other");
        otherOrganization.setShortName("other");
        otherOrganization.setEmailPattern("other");
        Set<Organization> otherOrganizations = new HashSet<>();
        otherOrganizations.add(otherOrganization);

        User student = ModelFactory.generateActivatedUser("ab12cde");
        student.setOrganizations(organizations);
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");
        Course course3 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "editor", "instructor");

        course1.setRegistrationEnabled(true);
        course2.setRegistrationEnabled(true);
        course1.setOrganizations(organizations);
        course3.setOrganizations(otherOrganizations);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);
        course3 = courseRepo.save(course3);

        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course1.getStudentGroupName()));
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course2.getStudentGroupName()));
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course3.getStudentGroupName()));
        bitbucketRequestMockProvider.mockUpdateUserDetails(student.getLogin(), student.getEmail(), student.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(student.getLogin(), student.getEmail(), student.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();
        bitbucketRequestMockProvider.mockUpdateUserDetails(student.getLogin(), student.getEmail(), student.getName());
        bitbucketRequestMockProvider.mockAddUserToGroups();

        User updatedStudent = request.postWithResponseBody("/api/courses/" + course1.getId() + "/register", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getGroups()).as("User is registered for course").contains(course1.getStudentGroupName());

        updatedStudent = request.postWithResponseBody("/api/courses/" + course2.getId() + "/register", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getGroups()).as("User is registered for course").contains(course2.getStudentGroupName());

        request.postWithResponseBody("/api/courses/" + course3.getId() + "/register", null, User.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * Test adding a course to a given organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAddCourseToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        request.postWithoutLocation("/api/admin/organizations/" + organization.getId() + "/courses/" + course1.getId(), null, HttpStatus.OK, null);

        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getCourses()).contains(course1);
    }

    /**
     * Test removing a course from a given organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemoveCourseToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization.getCourses().add(course1);
        organization = organizationRepo.save(organization);

        assertThat(organization.getCourses()).contains(course1);

        request.delete("/api/admin/organizations/course/" + course1.getId() + "/organization/" + organization.getId(), HttpStatus.OK);
        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(updatedOrganization.getCourses()).doesNotContain(course1);
    }

    /**
     * Test adding user to a given organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAddUserToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        request.postWithoutLocation("/api/admin/organizations/" + organization.getId() + "/users/" + users.get(0).getLogin(), null, HttpStatus.OK, null);
        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getUsers()).contains(users.get(0));
    }

    /**
     * Test removing user from a given organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemoveUserToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization.getUsers().add(users.get(0));
        organization = organizationRepo.save(organization);

        assertThat(organization.getUsers()).contains(users.get(0));

        request.delete("/api/admin/organizations/" + organization.getId() + "/users/" + users.get(0).getLogin(), HttpStatus.OK);
        Organization updatedOrganization = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(updatedOrganization.getUsers()).doesNotContain(users.get(0));
    }

    /**
     * Test adding a new organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAddOrganization() throws Exception {
        assertThrows(EntityNotFoundException.class, () -> organizationRepo.findByIdElseThrow(Long.MAX_VALUE));
        assertThrows(EntityNotFoundException.class, () -> organizationRepo.findByIdWithEagerUsersAndCoursesElseThrow(Long.MAX_VALUE));

        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();

        Organization updatedOrganization = request.postWithResponseBody("/api/admin/organizations", organization, Organization.class, HttpStatus.OK);
        Organization updatedOrganization2 = request.get("/api/admin/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization2).isNotNull();
        assertThat(updatedOrganization.getId()).isNotNull();
    }

    /**
     * Test updating an existing organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization.setName("UpdatedName");

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + organization.getId(), organization, Organization.class, HttpStatus.OK);
        assertThat(updatedOrganization.getName()).isEqualTo("UpdatedName");
    }

    /**
     * Test updating an existing organization when the Id in the RequestBody is null
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOrganization_idInBodyNull() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization initialOrganization = database.createOrganization();
        long initialOrganizationId = initialOrganization.getId();
        organizationRepo.save(initialOrganization);
        initialOrganization.setId(null);

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + initialOrganizationId, initialOrganization, Organization.class,
                HttpStatus.BAD_REQUEST);
        assertThat(updatedOrganization).isNull();
    }

    /**
     * Test updating an existing organization when the Id in the path doesn't match the one in the Body
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateOrganization_IdInPathWrong() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organizationRepo.save(organization);
        String initialName = organization.getName();
        organization.setName("UpdatedName");
        long randomId = 1337420;

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + randomId, organization, Organization.class, HttpStatus.BAD_REQUEST);
        organization.setName(initialName);
        assertThat(updatedOrganization).isNull();
    }

    /**
     * Test delete an organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteOrganization() throws Exception {
        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);

        request.delete("/api/admin/organizations/" + organization.getId(), HttpStatus.OK);
        request.get("/api/admin/organizations/" + organization.getId(), HttpStatus.NOT_FOUND, Organization.class);
    }

    /**
     * Test get all organizations
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllOrganizations() throws Exception {
        Organization organization = database.createOrganization();
        Organization organization2 = database.createOrganization();
        organization2.setName("Org2");
        organizationRepo.save(organization);
        organizationRepo.save(organization2);

        List<Organization> result = request.getList("/api/admin/organizations", HttpStatus.OK, Organization.class);
        assertThat(result).hasSize(2);
    }

    /**
     * Test get number of users and courses of all organizations
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetNumberOfUsersAndCoursesOfAllOrganizations() throws Exception {
        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);
        userRepo.addOrganizationToUser(users.get(0).getId(), organization);

        List<OrganizationCountDTO> result = request.getList("/api/admin/organizations/count-all", HttpStatus.OK, OrganizationCountDTO.class);

        assertThat(result).hasSize(1);

        assertThat(result.get(0).numberOfCourses()).isEqualTo(1);
        assertThat(result.get(0).numberOfUsers()).isEqualTo(1);
    }

    /**
     * Test get number of users and courses of a given organization
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetNumberOfUsersAndCoursesOfOrganization() throws Exception {
        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);
        userRepo.addOrganizationToUser(users.get(0).getId(), organization);

        OrganizationCountDTO result = request.get("/api/admin/organizations/" + organization.getId() + "/count", HttpStatus.OK, OrganizationCountDTO.class);

        assertThat(result.numberOfUsers()).isEqualTo(1);
        assertThat(result.numberOfCourses()).isEqualTo(1);
    }

    /**
     * Test retrieving an organization by its id
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationById() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);
        courseRepo.addOrganizationToCourse(course1.getId(), organization);

        userRepo.addOrganizationToUser(users.get(0).getId(), organization);
        // invoked remove to make sure it works correctly
        userRepo.removeOrganizationFromUser(users.get(0).getId(), organization);
        userRepo.addOrganizationToUser(users.get(0).getId(), organization);

        Organization result = request.get("/api/admin/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
        Organization resultWithCoursesAndUsers = request.get("/api/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(result.getId()).isEqualTo(organization.getId());
        assertThat(result.getName()).isEqualTo(organization.getName());

        assertThat(resultWithCoursesAndUsers.getCourses()).contains(course1);
        assertThat(resultWithCoursesAndUsers.getUsers()).contains(users.get(0));
    }

    /**
     * Test retriving all organizations containing a given course
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetAllOrganizationByCourse() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepo.addOrganizationToCourse(course1.getId(), organization);

        List<Organization> result = request.getList("/api/organizations/courses/" + course1.getId(), HttpStatus.OK, Organization.class);
        assertThat(result).contains(organization);
    }

    /**
     * Test retrieve all organization containing a given user
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllOrganizationByUser() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        userRepo.addOrganizationToUser(users.get(0).getId(), organization);

        List<Organization> result = request.getList("/api/admin/organizations/users/" + users.get(0).getId(), HttpStatus.OK, Organization.class);

        assertThat(result).contains(organization);
    }

    /**
     * Test indexing of organization over all users
     * @throws Exception exception
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testOrganizationIndexing() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization.getUsers().add(users.get(1));
        organization = organizationRepo.save(organization);

        organization.setEmailPattern("^" + users.get(0).getEmail() + "$");

        Organization updatedOrganization = request.putWithResponseBody("/api/admin/organizations/" + organization.getId(), organization, Organization.class, HttpStatus.OK);
        updatedOrganization = request.get("/api/admin/organizations/" + updatedOrganization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(updatedOrganization.getUsers()).hasSize(1);
        assertThat(updatedOrganization.getUsers()).contains(users.get(0));

        var organizations = organizationRepo.getAllMatchingOrganizationsByUserEmail(users.get(0).getEmail());
        assertThat(organizations).containsExactly(organization);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationTitle() throws Exception {
        Organization organization = database.createOrganization();
        organization.setName("Test Organization");
        organization = organizationRepo.save(organization);

        final var title = request.get("/api/admin/organizations/" + organization.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(organization.getName());
    }
}
