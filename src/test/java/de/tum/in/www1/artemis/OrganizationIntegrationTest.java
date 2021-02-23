package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.connector.jira.JiraRequestMockProvider;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Organization;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.OrganizationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;

import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = { "{artemis.user-management.organizations.enable-multiple-organizations=true",
"artemis.user-management.course-registration.allowed-username-pattern=.*"})
public class OrganizationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseRepository courseRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    OrganizationRepository organizationRepo;

    @Autowired
    JiraRequestMockProvider jiraRequestMockProvider;

    private List<User> users;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(1,1,1);

        bitbucketRequestMockProvider.enableMockingOfRequests();
        bambooRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "ab12cde")
    public void testGetCoursesToRegister_withOrganizationsEnabled() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        Set<Organization> organizations = new HashSet<>();
        organizations.add(organization);

        User student = ModelFactory.generateActivatedUser("ab12cde");
        student.setOrganizations(organizations);
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "instructor");
        course1.setRegistrationEnabled(true);
        course2.setRegistrationEnabled(true);
        course1.setOrganizations(organizations);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course1.getStudentGroupName()));
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course2.getStudentGroupName()));

        List<Course> coursesToRegister = request.get("/api/courses/to-register", HttpStatus.OK, List.class);
        assertThat(coursesToRegister.contains(course1));
        assertThat(!coursesToRegister.contains(course2));
    }

    @Test
    @WithMockUser(username = "ab12cde")
    public void testRegisterForCourse_withOrganizationsEnabled() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        Set<Organization> organizations = new HashSet<>();
        organizations.add(organization);

        User student = ModelFactory.generateActivatedUser("ab12cde");
        student.setOrganizations(organizations);
        userRepo.save(student);

        ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(5);
        ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(5);
        Course course1 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse1", "tutor", "instructor");
        Course course2 = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "testcourse2", "tutor", "instructor");
        course1.setRegistrationEnabled(true);
        course2.setRegistrationEnabled(true);
        course1.setOrganizations(organizations);

        course1 = courseRepo.save(course1);
        course2 = courseRepo.save(course2);
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course1.getStudentGroupName()));
        jiraRequestMockProvider.mockAddUserToGroupForMultipleGroups(Set.of(course2.getStudentGroupName()));

        User updatedStudent = request.postWithResponseBody("/api/courses/" + course1.getId() + "/register", null, User.class, HttpStatus.OK);
        assertThat(updatedStudent.getGroups()).as("User is registered for course").contains(course1.getStudentGroupName());

        request.postWithResponseBody("/api/courses/" + course2.getId() + "/register", null, User.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAddCourseToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "instructor");
        course1 = courseRepo.save(course1);

        request.postWithoutLocation("/api/organizations/course/" + course1.getId() + "/organization/" + organization.getId(), null, HttpStatus.OK, null);

        Organization updatedOrganization = request.get("/api/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getCourses().contains(course1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testRemoveCourseToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization.getCourses().add(course1);
        organization = organizationRepo.save(organization);

        assertThat(organization.getCourses().contains(course1));

        request.delete("/api/organizations/course/" + course1.getId() + "/organization/" + organization.getId(), HttpStatus.OK);
        Organization updatedOrganization = request.get("/api/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(!updatedOrganization.getCourses().contains(course1));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAddUserToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        request.postWithoutLocation("/api/organizations/user/" + users.get(0).getLogin() + "/organization/" + organization.getId(), null, HttpStatus.OK, null);
        Organization updatedOrganization = request.get("/api/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getUsers().contains(users.get(0)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testRemoveUserToOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization.getUsers().add(users.get(0));
        organization = organizationRepo.save(organization);

        assertThat(organization.getUsers().contains(users.get(0)));

        request.delete("/api/organizations/user/" + users.get(0).getLogin() + "/organization/" + organization.getId(), HttpStatus.OK);
        Organization updatedOrganization = request.get("/api/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(!updatedOrganization.getUsers().contains(users.get(0)));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAddOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();

        Organization updatedOrganization = request.postWithResponseBody("/api/organizations/add", organization, Organization.class, HttpStatus.OK);
        Organization updatedOrganization2 = request.get("/api/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getId() != null);
        assertThat(updatedOrganization2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testUpdateOrganization() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization.setName("UpdatedName");

        Organization updatedOrganization = request.putWithResponseBody("/api/organizations/update", organization, Organization.class, HttpStatus.OK);
        assertThat(updatedOrganization.getName().compareTo("UpdatedName") == 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testDeleteOrganization() throws Exception{
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        request.delete("/api/organizations/delete/" + organization.getId(), HttpStatus.OK);

        request.get("/api/organizations/" + organization.getId(), HttpStatus.INTERNAL_SERVER_ERROR, Organization.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetAllOrganizations() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        Organization organization2 = database.createOrganization();
        organization2.setName("Org2");
        organizationRepo.save(organization);
        organizationRepo.save(organization2);

        List<Organization> result = request.get("/api/organizations/all", HttpStatus.OK, List.class);

        assertThat(result.size() == 2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetNumberOfUsersAndCoursesOfAllOrganizations() throws Exception {
        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization.getCourses().add(course1);
        organization.getUsers().add(users.get(0));
        organization = organizationRepo.save(organization);

        Map<Long, Map> result = request.getMap("/api/organizations/allCount", HttpStatus.OK, Long.class, Map.class);

        assertThat(result.size() == 1);

        Map<String, Integer> resultEntry = (Map<String, Integer>) result.get(organization.getId());
        assertThat(resultEntry.get("users") == 1);
        assertThat(resultEntry.get("courses") == 1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetNumberOfUsersAndCoursesOfOrganization() throws Exception {
        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization.getCourses().add(course1);
        organization.getUsers().add(users.get(0));
        organization = organizationRepo.save(organization);

        Map<String, Long> result = request.getMap("/api/organizations/" + organization.getId() + "/count", HttpStatus.OK, String.class, Long.class);

        assertThat(result.get("users") == 1L);
        assertThat(result.get("courses") == 1L);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetOrganizationById() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization = organizationRepo.save(organization);

        Organization result = request.get("/api/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
        Organization resultWithCoursesAndUsers = request.get("/api/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(result.getId() == organization.getId());
        assertThat(result.getName() == organization.getName());

        assertThat(resultWithCoursesAndUsers.getCourses() != null);
        assertThat(resultWithCoursesAndUsers.getUsers() != null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetAllOrganizationByCourse() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Course course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "instructor");
        course1 = courseRepo.save(course1);

        Organization organization = database.createOrganization();
        organization.getCourses().add(course1);
        organization = organizationRepo.save(organization);

        Set<Organization> result = request.get("/api/organizations/course/" + course1.getId(), HttpStatus.OK, Set.class);
        assertThat(result.contains(organization));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testGetAllOrganizationByUser() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization.getUsers().add(users.get(0));
        organization = organizationRepo.save(organization);

        Set<Organization> result = request.get("/api/organizations/user/" + users.get(0).getId(), HttpStatus.OK, Set.class);
        assertThat(result.contains(organization));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testOrganizationIndexing() throws Exception {
        jiraRequestMockProvider.enableMockingOfRequests();

        Organization organization = database.createOrganization();
        organization.getUsers().add(users.get(1));
        organization = organizationRepo.save(organization);

        organization.setEmailPattern("^" + users.get(0).getEmail() + "$");

        Organization updatedOrganization = request.putWithResponseBody("/api/organizations/update", organization, Organization.class, HttpStatus.OK);
        updatedOrganization = request.get("/api/organizations/" + updatedOrganization.getId() + "/full", HttpStatus.OK, Organization.class);

        assertThat(updatedOrganization.getUsers().size() == 1);
        assertThat(updatedOrganization.getUsers().contains(users.get(0)));
    }
}
