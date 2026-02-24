package de.tum.cit.aet.artemis.core.organization;

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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Organization;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.OrganizationCountDTO;
import de.tum.cit.aet.artemis.core.dto.OrganizationDTO;
import de.tum.cit.aet.artemis.core.dto.SortingOrder;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.organization.util.OrganizationUtilService;
import de.tum.cit.aet.artemis.core.repository.OrganizationRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class OrganizationIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "organizationtest";

    @Autowired
    private OrganizationRepository organizationRepo;

    @Autowired
    private OrganizationUtilService organizationUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    /**
     * Builds a search DTO with the given search term and page size.
     * Page index 0 is the first page (0-indexed as used by PageRequest.of in the
     * repository).
     */
    private SearchTermPageableSearchDTO<String> buildSearch(String searchTerm, int page, int pageSize, String sortedColumn, SortingOrder sortingOrder) {
        SearchTermPageableSearchDTO<String> search = new SearchTermPageableSearchDTO<>();
        search.setPage(page);
        search.setPageSize(pageSize);
        search.setSearchTerm(searchTerm);
        search.setSortedColumn(sortedColumn);
        search.setSortingOrder(sortingOrder);
        return search;
    }

    private SearchTermPageableSearchDTO<String> buildSearch(String searchTerm) {
        return buildSearch(searchTerm, 0, 100, "id", SortingOrder.ASCENDING);
    }

    /**
     * Test if getting courses a user can enroll in works with multi organization
     * and
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
        userTestRepository.save(student);

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

        course1 = courseRepository.save(course1);
        course2 = courseRepository.save(course2);

        List<Course> coursesToEnroll = request.getList("/api/core/courses/for-enrollment", HttpStatus.OK, Course.class);
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
        userTestRepository.save(student);

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

        course1 = courseRepository.save(course1);
        course2 = courseRepository.save(course2);
        course3 = courseRepository.save(course3);

        Set<String> updatedGroups = request.postWithResponseBody("/api/core/courses/" + course1.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is enrolled in course").contains(course1.getStudentGroupName());

        updatedGroups = request.postWithResponseBody("/api/core/courses/" + course2.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        assertThat(updatedGroups).as("User is enrolled in course").contains(course2.getStudentGroupName());

        request.postWithResponseBody("/api/core/courses/" + course3.getId() + "/enroll", null, Set.class, HttpStatus.FORBIDDEN);
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
        course1 = courseRepository.save(course1);

        request.postWithoutLocation("/api/core/admin/organizations/" + organization.getId() + "/courses/" + course1.getId(), null, HttpStatus.OK, null);

        Organization updatedOrganization = request.get("/api/core/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(updatedOrganization.getCourses()).contains(course1);
    }

    /**
     * Test removing a course from a given organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testRemoveCourseToOrganization() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepository.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        courseRepository.addOrganizationToCourse(course1.getId(), organization);

        Organization originalOrganization = request.get("/api/core/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
        assertThat(originalOrganization.getCourses()).contains(course1);

        request.delete("/api/core/admin/organizations/" + organization.getId() + "/courses/" + course1.getId(), HttpStatus.OK);

        Organization updatedOrganization = request.get("/api/core/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
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

        request.postWithoutLocation("/api/core/admin/organizations/" + organization.getId() + "/users/" + student.getLogin(), null, HttpStatus.OK, null);
        Organization updatedOrganization = request.get("/api/core/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);
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

        request.delete("/api/core/admin/organizations/" + organization.getId() + "/users/" + student.getLogin(), HttpStatus.OK);
        Organization updatedOrganization = request.get("/api/core/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

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

        Organization updatedOrganization = request.postWithResponseBody("/api/core/admin/organizations", organization, Organization.class, HttpStatus.OK);
        Organization updatedOrganization2 = request.get("/api/core/admin/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
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

        Organization updatedOrganization = request.putWithResponseBody("/api/core/admin/organizations/" + organization.getId(), organization, Organization.class, HttpStatus.OK);
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

        Organization updatedOrganization = request.putWithResponseBody("/api/core/admin/organizations/" + initialOrganizationId, initialOrganization, Organization.class,
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

        Organization updatedOrganization = request.putWithResponseBody("/api/core/admin/organizations/" + wrongId, organization, Organization.class, HttpStatus.BAD_REQUEST);
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
        course1 = courseRepository.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepository.addOrganizationToCourse(course1.getId(), organization);

        request.delete("/api/core/admin/organizations/" + organization.getId(), HttpStatus.OK);
        request.get("/api/core/admin/organizations/" + organization.getId(), HttpStatus.NOT_FOUND, Organization.class);
    }

    /**
     * Test get all organizations without pagination
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAllOrganizations() throws Exception {
        Organization organization = organizationUtilService.createOrganization();
        Organization organization2 = organizationUtilService.createOrganization();

        List<Organization> result = request.getList("/api/core/admin/organizations/all", HttpStatus.OK, Organization.class);
        assertThat(result).contains(organization, organization2);
    }

    /**
     * Test get organizations with pagination
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationsPaginated() throws Exception {
        String prefix = "paginationTest";
        Organization organizationA = organizationUtilService.createOrganization(prefix + "_A Org", "shortname", "url", "desc", null, "emailpattern");
        Organization organizationB = organizationUtilService.createOrganization(prefix + "_B Org", "shortname2", "url2", "desc2", null, "emailpattern2");
        Organization organizationC = organizationUtilService.createOrganization(prefix + "_C Org", "shortname3", "url3", "desc3", null, "emailpattern3");

        // Page 0 with size 2 returns orgs A and B; page 1 with size 2 returns org C
        SearchTermPageableSearchDTO<String> firstPageSearch = buildSearch(prefix, 0, 2, "name", SortingOrder.ASCENDING);
        List<OrganizationDTO> firstPage = request.getList("/api/core/admin/organizations", HttpStatus.OK, OrganizationDTO.class,
                pageableSearchUtilService.searchMapping(firstPageSearch));

        SearchTermPageableSearchDTO<String> secondPageSearch = buildSearch(prefix, 1, 2, "name", SortingOrder.ASCENDING);
        List<OrganizationDTO> secondPage = request.getList("/api/core/admin/organizations", HttpStatus.OK, OrganizationDTO.class,
                pageableSearchUtilService.searchMapping(secondPageSearch));

        assertThat(firstPage).hasSize(2);
        assertThat(firstPage.get(0).id()).isEqualTo(organizationA.getId());
        assertThat(firstPage.get(1).id()).isEqualTo(organizationB.getId());
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).id()).isEqualTo(organizationC.getId());
    }

    /**
     * Test filtering organizations by search term across name, shortName, and
     * emailPattern
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationsPaginated_filterBySearchTerm() throws Exception {
        String searchTerm = "uniqueSearchTerm";

        // Organization where search term appears in name
        Organization orgWithName = organizationUtilService.createOrganization(searchTerm + "_Name", "shortname1", "url1", "desc1", null, "pattern1");

        // Organization where search term appears in shortName
        Organization orgWithShortName = organizationUtilService.createOrganization("name2", searchTerm + "_Short", "url2", "desc2", null, "pattern2");

        // Organization where search term appears in emailPattern
        Organization orgWithEmailPattern = organizationUtilService.createOrganization("name3", "shortname3", "url3", "desc3", null, searchTerm + "_Email");

        // Two organizations that do not contain the search term anywhere
        organizationUtilService.createOrganization("name4", "shortname4", "url4", "desc4", null, "pattern4");
        organizationUtilService.createOrganization("name5", "shortname5", "url5", "desc5", null, "pattern5");

        SearchTermPageableSearchDTO<String> search = buildSearch(searchTerm);
        List<OrganizationDTO> result = request.getList("/api/core/admin/organizations", HttpStatus.OK, OrganizationDTO.class, pageableSearchUtilService.searchMapping(search));

        assertThat(result).hasSize(3);
        List<Long> resultIds = result.stream().map(OrganizationDTO::id).toList();
        assertThat(resultIds).contains(orgWithName.getId(), orgWithShortName.getId(), orgWithEmailPattern.getId());
    }

    /**
     * Test that a search term matching nothing returns an empty list
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationsPaginated_noMatchReturnsEmptyList() throws Exception {
        organizationUtilService.createOrganization();

        SearchTermPageableSearchDTO<String> search = buildSearch("nonExistentSearchTerm");
        List<OrganizationDTO> result = request.getList("/api/core/admin/organizations", HttpStatus.OK, OrganizationDTO.class, pageableSearchUtilService.searchMapping(search));

        assertThat(result).isEmpty();
    }

    /**
     * Test sorting organizations by numberOfUsers: org with more users appears
     * first when descending
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationsPaginated_sortByNumberOfUsersDescending() throws Exception {
        String prefix = "sortByUsers";
        Organization orgNoUsers = organizationUtilService.createOrganization(prefix + "_NoUsers", "shortname", "url", "desc", null, "emailpattern");
        Organization orgWithUsers = organizationUtilService.createOrganization(prefix + "_WithUsers", "shortname", "url", "desc", null, "emailpattern");

        User student1 = userUtilService.createAndSaveUser(TEST_PREFIX + "user1");
        User student2 = userUtilService.createAndSaveUser(TEST_PREFIX + "user2");
        userTestRepository.addOrganizationToUser(student1.getId(), orgWithUsers);
        userTestRepository.addOrganizationToUser(student2.getId(), orgWithUsers);

        SearchTermPageableSearchDTO<String> search = buildSearch(prefix, 0, 10, "numberOfUsers", SortingOrder.DESCENDING);
        List<OrganizationDTO> result = request.getList("/api/core/admin/organizations", HttpStatus.OK, OrganizationDTO.class, pageableSearchUtilService.searchMapping(search));

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(orgWithUsers.getId());
        assertThat(result.get(0).numberOfUsers()).isEqualTo(2);
        assertThat(result.get(1).id()).isEqualTo(orgNoUsers.getId());
        assertThat(result.get(1).numberOfUsers()).isEqualTo(0);
    }

    /**
     * Test that the OrganizationDTO returned by the paginated endpoint contains
     * the correct aggregated user and course counts (replaces the removed count-all
     * endpoint)
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetOrganizationsPaginated_dtoContainsUserAndCourseCounts() throws Exception {
        String uniqueName = "userAndCourseCount";
        Organization organization = organizationUtilService.createOrganization(uniqueName, "shortname", "url", "desc", null, "emailpattern");

        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "name", "tutor", "editor", "instructor");
        course1 = courseRepository.save(course1);
        courseRepository.addOrganizationToCourse(course1.getId(), organization);

        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "user");
        userTestRepository.addOrganizationToUser(student.getId(), organization);

        SearchTermPageableSearchDTO<String> search = buildSearch(uniqueName);
        List<OrganizationDTO> result = request.getList("/api/core/admin/organizations", HttpStatus.OK, OrganizationDTO.class, pageableSearchUtilService.searchMapping(search));

        assertThat(result).hasSize(1);
        OrganizationDTO dto = result.get(0);
        assertThat(dto.id()).isEqualTo(organization.getId());
        assertThat(dto.numberOfUsers()).isEqualTo(1);
        assertThat(dto.numberOfCourses()).isEqualTo(1);
    }

    /**
     * Test get number of users and courses of a given organization
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetNumberOfUsersAndCoursesOfOrganization() throws Exception {
        Course course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "testcourse1", "tutor", "editor", "instructor");
        course1 = courseRepository.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepository.addOrganizationToCourse(course1.getId(), organization);
        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testGetNumberOfUsers_");

        userTestRepository.addOrganizationToUser(student.getId(), organization);

        OrganizationCountDTO result = request.get("/api/core/admin/organizations/" + organization.getId() + "/count", HttpStatus.OK, OrganizationCountDTO.class);

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
        course1 = courseRepository.save(course1);
        courseRepository.addOrganizationToCourse(course1.getId(), organization);

        User student = userUtilService.createAndSaveUser(TEST_PREFIX + "testGetOrganizationById");

        userTestRepository.addOrganizationToUser(student.getId(), organization);
        // invoked remove to make sure it works correctly
        userTestRepository.removeOrganizationFromUser(student.getId(), organization);
        userTestRepository.addOrganizationToUser(student.getId(), organization);

        Organization result = request.get("/api/core/admin/organizations/" + organization.getId(), HttpStatus.OK, Organization.class);
        Organization resultWithCoursesAndUsers = request.get("/api/core/admin/organizations/" + organization.getId() + "/full", HttpStatus.OK, Organization.class);

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
        course1 = courseRepository.save(course1);

        Organization organization = organizationUtilService.createOrganization();
        organization = organizationRepo.save(organization);

        courseRepository.addOrganizationToCourse(course1.getId(), organization);

        List<Organization> result = request.getList("/api/core/organizations/courses/" + course1.getId(), HttpStatus.OK, Organization.class);
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

        userTestRepository.addOrganizationToUser(student.getId(), organization);

        List<Organization> result = request.getList("/api/core/admin/organizations/users/" + student.getId(), HttpStatus.OK, Organization.class);

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

        Organization updatedOrganization = request.putWithResponseBody("/api/core/admin/organizations/" + organization.getId(), organization, Organization.class, HttpStatus.OK);
        updatedOrganization = request.get("/api/core/admin/organizations/" + updatedOrganization.getId() + "/full", HttpStatus.OK, Organization.class);

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

        final var title = request.get("/api/core/admin/organizations/" + organization.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(organization.getName());
    }
}
