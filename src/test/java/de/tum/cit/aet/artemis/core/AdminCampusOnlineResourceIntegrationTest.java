package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.connector.CampusOnlineRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.CampusOnlineConfiguration;
import de.tum.cit.aet.artemis.core.domain.CampusOnlineOrgUnit;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineCourseImportRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineLinkRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineOrgUnitDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineOrgUnitImportDTO;
import de.tum.cit.aet.artemis.core.dto.CampusOnlineSyncResultDTO;
import de.tum.cit.aet.artemis.core.repository.CampusOnlineConfigurationRepository;
import de.tum.cit.aet.artemis.core.repository.CampusOnlineOrgUnitRepository;
import de.tum.cit.aet.artemis.core.service.connectors.campusonline.dto.CampusOnlineOrgCourseDTO;
import de.tum.cit.aet.artemis.core.web.admin.AdminCampusOnlineResource;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Integration tests for {@link AdminCampusOnlineResource}.
 * <p>
 * These tests verify all REST endpoints for CAMPUSOnline course management,
 * including course search, link/unlink, import, and enrollment sync.
 * External CAMPUSOnline API calls are intercepted by {@link CampusOnlineRequestMockProvider}.
 */
class AdminCampusOnlineResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "campusonline";

    private static final String BASE_URL = "/api/core/admin/campus-online/";

    @Autowired
    private CampusOnlineRequestMockProvider campusOnlineMockProvider;

    @Autowired
    private CampusOnlineConfigurationRepository campusOnlineConfigRepository;

    @Autowired
    private CampusOnlineOrgUnitRepository orgUnitRepository;

    private Course testCourse;

    private CampusOnlineOrgUnit testOrgUnit;

    @BeforeEach
    void setUp() {
        campusOnlineMockProvider.enableMockingOfRequests();
        testCourse = courseUtilService.createCourse();

        testOrgUnit = new CampusOnlineOrgUnit();
        testOrgUnit.setExternalId("12345");
        testOrgUnit.setName("CIT - Computation, Information and Technology");
        testOrgUnit = orgUnitRepository.save(testOrgUnit);
    }

    @AfterEach
    void tearDown() {
        campusOnlineMockProvider.reset();
        // Unlink all courses from CAMPUSOnline to clean up test data
        for (Course course : courseRepository.findAllWithCampusOnlineConfiguration()) {
            course.setCampusOnlineConfiguration(null);
            courseRepository.save(course);
        }
        campusOnlineConfigRepository.deleteAll();
        orgUnitRepository.deleteAll();
    }

    // ==================== GET /org-units ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAllOrgUnits_returnsOk_withOrgUnits() throws Exception {
        List<CampusOnlineOrgUnitDTO> result = request.getList(BASE_URL + "org-units", HttpStatus.OK, CampusOnlineOrgUnitDTO.class);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().externalId()).isEqualTo("12345");
        assertThat(result.getFirst().name()).isEqualTo("CIT - Computation, Information and Technology");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getAllOrgUnits_returnsForbidden_whenNotAdmin() throws Exception {
        request.getList(BASE_URL + "org-units", HttpStatus.FORBIDDEN, CampusOnlineOrgUnitDTO.class);
    }

    // ==================== POST /org-units ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void createOrgUnit_returnsCreated() throws Exception {
        var newOrgUnit = new CampusOnlineOrgUnitDTO(null, "99999", "New Faculty");

        CampusOnlineOrgUnitDTO result = request.postWithResponseBody(BASE_URL + "org-units", newOrgUnit, CampusOnlineOrgUnitDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.externalId()).isEqualTo("99999");
        assertThat(result.name()).isEqualTo("New Faculty");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void createOrgUnit_returnsBadRequest_whenDuplicateExternalId() throws Exception {
        var newOrgUnit = new CampusOnlineOrgUnitDTO(null, "12345", "Duplicate Faculty"); // same as testOrgUnit

        request.postWithResponseBody(BASE_URL + "org-units", newOrgUnit, CampusOnlineOrgUnitDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void createOrgUnit_returnsBadRequest_whenIdExists() throws Exception {
        var newOrgUnit = new CampusOnlineOrgUnitDTO(1L, "99999", "New Faculty");

        request.postWithResponseBody(BASE_URL + "org-units", newOrgUnit, CampusOnlineOrgUnitDTO.class, HttpStatus.BAD_REQUEST);
    }

    // ==================== PUT /org-units/{orgUnitId} ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateOrgUnit_returnsOk() throws Exception {
        var update = new CampusOnlineOrgUnitDTO(null, "12345-updated", "Updated Name");

        CampusOnlineOrgUnitDTO result = request.putWithResponseBody(BASE_URL + "org-units/" + testOrgUnit.getId(), update, CampusOnlineOrgUnitDTO.class, HttpStatus.OK);

        assertThat(result.externalId()).isEqualTo("12345-updated");
        assertThat(result.name()).isEqualTo("Updated Name");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateOrgUnit_returnsBadRequest_whenDuplicateExternalId() throws Exception {
        // Create a second org unit
        CampusOnlineOrgUnit second = new CampusOnlineOrgUnit();
        second.setExternalId("67890");
        second.setName("Second Faculty");
        second = orgUnitRepository.save(second);

        // Try to update its externalId to match testOrgUnit's
        var update = new CampusOnlineOrgUnitDTO(null, "12345", "Second Faculty Updated"); // conflicts with testOrgUnit

        request.putWithResponseBody(BASE_URL + "org-units/" + second.getId(), update, CampusOnlineOrgUnitDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateOrgUnit_returnsNotFound_whenNotExists() throws Exception {
        var update = new CampusOnlineOrgUnitDTO(null, "99999", "Non-existent");

        request.putWithResponseBody(BASE_URL + "org-units/999999", update, CampusOnlineOrgUnitDTO.class, HttpStatus.NOT_FOUND);
    }

    // ==================== DELETE /org-units/{orgUnitId} ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void deleteOrgUnit_returnsNoContent() throws Exception {
        request.delete(BASE_URL + "org-units/" + testOrgUnit.getId(), HttpStatus.NO_CONTENT);

        assertThat(orgUnitRepository.findById(testOrgUnit.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void deleteOrgUnit_returnsNotFound_whenNotExists() throws Exception {
        request.delete(BASE_URL + "org-units/999999", HttpStatus.NOT_FOUND);
    }

    // ==================== POST /org-units/import ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void importOrgUnits_returnsOk_withCreatedUnits() throws Exception {
        var importDTOs = List.of(new CampusOnlineOrgUnitImportDTO("99001", "Faculty A"), new CampusOnlineOrgUnitImportDTO("99002", "Faculty B"));

        List<CampusOnlineOrgUnitDTO> result = request.postListWithResponseBody(BASE_URL + "org-units/import", importDTOs, CampusOnlineOrgUnitDTO.class, HttpStatus.OK);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CampusOnlineOrgUnitDTO::externalId).containsExactlyInAnyOrder("99001", "99002");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void importOrgUnits_skipsDuplicates() throws Exception {
        // testOrgUnit has externalId "12345" — import should skip it
        var importDTOs = List.of(new CampusOnlineOrgUnitImportDTO("12345", "Duplicate"), new CampusOnlineOrgUnitImportDTO("99003", "New Faculty"));

        List<CampusOnlineOrgUnitDTO> result = request.postListWithResponseBody(BASE_URL + "org-units/import", importDTOs, CampusOnlineOrgUnitDTO.class, HttpStatus.OK);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().externalId()).isEqualTo("99003");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void importOrgUnits_returnsForbidden_whenNotAdmin() throws Exception {
        var importDTOs = List.of(new CampusOnlineOrgUnitImportDTO("99001", "Faculty A"));

        request.postListWithResponseBody(BASE_URL + "org-units/import", importDTOs, CampusOnlineOrgUnitDTO.class, HttpStatus.FORBIDDEN);
    }

    // ==================== GET /courses (search by org unit) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCourses_returnsOk_withMatchingCourses() throws Exception {
        var course1 = new CampusOnlineOrgCourseDTO("CO-101", "Introduction to Computer Science", "2025W");
        var course2 = new CampusOnlineOrgCourseDTO("CO-202", "Advanced Mathematics", "2025W");
        campusOnlineMockProvider.mockFetchCoursesForOrg("999", "2025-10-01", "2026-03-31", List.of(course1, course2));

        List<CampusOnlineCourseDTO> result = request.getList(BASE_URL + "courses?orgUnitId=999&from=2025-10-01&until=2026-03-31", HttpStatus.OK, CampusOnlineCourseDTO.class);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).campusOnlineCourseId()).isEqualTo("CO-101");
        assertThat(result.get(0).title()).isEqualTo("Introduction to Computer Science");
        assertThat(result.get(0).semester()).isEqualTo("2025W");
        assertThat(result.get(1).campusOnlineCourseId()).isEqualTo("CO-202");
        assertThat(result.get(1).title()).isEqualTo("Advanced Mathematics");

        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCourses_returnsOk_withEmptyResults() throws Exception {
        campusOnlineMockProvider.mockFetchCoursesForOrg("999", "2025-01-01", "2025-12-31", List.of());

        List<CampusOnlineCourseDTO> result = request.getList(BASE_URL + "courses?orgUnitId=999&from=2025-01-01&until=2025-12-31", HttpStatus.OK, CampusOnlineCourseDTO.class);

        assertThat(result).isEmpty();
        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCourses_marksAlreadyImportedCourses() throws Exception {
        // Link a course to CAMPUSOnline first
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-101");
        config = campusOnlineConfigRepository.save(config);
        testCourse.setCampusOnlineConfiguration(config);
        courseRepository.save(testCourse);

        var course1 = new CampusOnlineOrgCourseDTO("CO-101", "Already Imported Course", "2025W");
        var course2 = new CampusOnlineOrgCourseDTO("CO-202", "New Course", "2025W");
        campusOnlineMockProvider.mockFetchCoursesForOrg("999", "2025-01-01", "2025-12-31", List.of(course1, course2));

        List<CampusOnlineCourseDTO> result = request.getList(BASE_URL + "courses?orgUnitId=999&from=2025-01-01&until=2025-12-31", HttpStatus.OK, CampusOnlineCourseDTO.class);

        assertThat(result).hasSize(2);
        CampusOnlineCourseDTO imported = result.stream().filter(c -> "CO-101".equals(c.campusOnlineCourseId())).findFirst().orElseThrow();
        CampusOnlineCourseDTO notImported = result.stream().filter(c -> "CO-202".equals(c.campusOnlineCourseId())).findFirst().orElseThrow();
        assertThat(imported.alreadyImported()).isTrue();
        assertThat(notImported.alreadyImported()).isFalse();

        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void searchCourses_returnsForbidden_whenNotAdmin() throws Exception {
        request.getList(BASE_URL + "courses?orgUnitId=999&from=2025-01-01&until=2025-12-31", HttpStatus.FORBIDDEN, CampusOnlineCourseDTO.class);
    }

    @Test
    void searchCourses_returnsUnauthorized_whenNotLoggedIn() throws Exception {
        request.getList(BASE_URL + "courses?orgUnitId=999&from=2025-01-01&until=2025-12-31", HttpStatus.UNAUTHORIZED, CampusOnlineCourseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCourses_returnsBadRequest_whenInvalidDateFormat() throws Exception {
        request.getList(BASE_URL + "courses?orgUnitId=999&from=invalid&until=2025-12-31", HttpStatus.BAD_REQUEST, CampusOnlineCourseDTO.class);
    }

    // ==================== GET /courses/search (typeahead) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCoursesByName_returnsOk_withFilteredResults() throws Exception {
        var course1 = new CampusOnlineOrgCourseDTO("CO-901", "Introduction to Computer Science", "2025W");
        var course2 = new CampusOnlineOrgCourseDTO("CO-902", "Advanced Mathematics", "2025W");
        var course3 = new CampusOnlineOrgCourseDTO("CO-903", "Computer Networks", "2025W");
        campusOnlineMockProvider.mockFetchCoursesForOrg("12345", "2025-10-01", "2026-03-31", List.of(course1, course2, course3));

        List<CampusOnlineCourseDTO> result = request.getList(BASE_URL + "courses/search?query=Computer&orgUnitId=12345&semester=2025W", HttpStatus.OK, CampusOnlineCourseDTO.class);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(CampusOnlineCourseDTO::title).containsExactlyInAnyOrder("Introduction to Computer Science", "Computer Networks");
        assertThat(result).allMatch(c -> !c.alreadyImported());

        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCoursesByName_returnsOk_caseInsensitive() throws Exception {
        var course1 = new CampusOnlineOrgCourseDTO("CO-101", "COMPUTER SCIENCE", "2025W");
        campusOnlineMockProvider.mockFetchCoursesForOrg("12345", "2025-10-01", "2026-03-31", List.of(course1));

        List<CampusOnlineCourseDTO> result = request.getList(BASE_URL + "courses/search?query=computer&orgUnitId=12345&semester=2025W", HttpStatus.OK, CampusOnlineCourseDTO.class);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().title()).isEqualTo("COMPUTER SCIENCE");

        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCoursesByName_returnsOk_withNoMatchingResults() throws Exception {
        var course1 = new CampusOnlineOrgCourseDTO("CO-101", "Physics", "2025W");
        campusOnlineMockProvider.mockFetchCoursesForOrg("12345", "2025-10-01", "2026-03-31", List.of(course1));

        List<CampusOnlineCourseDTO> result = request.getList(BASE_URL + "courses/search?query=Chemistry&orgUnitId=12345&semester=2025W", HttpStatus.OK,
                CampusOnlineCourseDTO.class);

        assertThat(result).isEmpty();
        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void searchCoursesByName_returnsForbidden_whenNotAdmin() throws Exception {
        request.getList(BASE_URL + "courses/search?query=Test&orgUnitId=12345&semester=2025W", HttpStatus.FORBIDDEN, CampusOnlineCourseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCoursesByName_returnsBadRequest_whenQueryTooShort() throws Exception {
        request.getList(BASE_URL + "courses/search?query=ab&orgUnitId=12345&semester=2025W", HttpStatus.BAD_REQUEST, CampusOnlineCourseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void searchCoursesByName_returnsBadRequest_whenOrgUnitIdMissing() throws Exception {
        request.getList(BASE_URL + "courses/search?query=Computer&semester=2025W", HttpStatus.BAD_REQUEST, CampusOnlineCourseDTO.class);
    }

    // ==================== PUT /courses/{courseId}/link ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void linkCourse_returnsOk_withUpdatedCourse() throws Exception {
        var linkRequest = new CampusOnlineLinkRequestDTO("CO-101", "Prof. Smith", "CS Department", "Informatik BSc");

        CampusOnlineCourseDTO result = request.putWithResponseBody(BASE_URL + "courses/" + testCourse.getId() + "/link", linkRequest, CampusOnlineCourseDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.campusOnlineCourseId()).isEqualTo("CO-101");
        assertThat(result.responsibleInstructor()).isEqualTo("Prof. Smith");
        assertThat(result.department()).isEqualTo("CS Department");
        assertThat(result.studyProgram()).isEqualTo("Informatik BSc");

        // Verify it was persisted in the database
        Course fromDb = courseRepository.findWithEagerCampusOnlineConfigurationById(testCourse.getId()).orElseThrow();
        assertThat(fromDb.getCampusOnlineConfiguration()).isNotNull();
        assertThat(fromDb.getCampusOnlineConfiguration().getCampusOnlineCourseId()).isEqualTo("CO-101");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void linkCourse_returnsBadRequest_whenCourseIdBlank() throws Exception {
        var linkRequest = new CampusOnlineLinkRequestDTO("", "Prof. Smith", "CS Department", "Informatik BSc");

        request.putWithResponseBody(BASE_URL + "courses/" + testCourse.getId() + "/link", linkRequest, CampusOnlineCourseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void linkCourse_returnsBadRequest_whenDuplicateLink() throws Exception {
        // First, link a course
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-DUPLICATE");
        config = campusOnlineConfigRepository.save(config);
        testCourse.setCampusOnlineConfiguration(config);
        courseRepository.save(testCourse);

        // Try to link another course with the same CAMPUSOnline ID
        Course secondCourse = courseUtilService.createCourse();
        var linkRequest = new CampusOnlineLinkRequestDTO("CO-DUPLICATE", "Prof. Other", "Other Dept", "Other Program");

        request.putWithResponseBody(BASE_URL + "courses/" + secondCourse.getId() + "/link", linkRequest, CampusOnlineCourseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void linkCourse_returnsNotFound_whenCourseDoesNotExist() throws Exception {
        var linkRequest = new CampusOnlineLinkRequestDTO("CO-101", "Prof. Smith", "CS Department", "Informatik BSc");

        request.putWithResponseBody(BASE_URL + "courses/999999/link", linkRequest, CampusOnlineCourseDTO.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void linkCourse_returnsForbidden_whenNotAdmin() throws Exception {
        var linkRequest = new CampusOnlineLinkRequestDTO("CO-101", "Prof. Smith", "CS Department", "Informatik BSc");

        request.putWithResponseBody(BASE_URL + "courses/" + testCourse.getId() + "/link", linkRequest, CampusOnlineCourseDTO.class, HttpStatus.FORBIDDEN);
    }

    // ==================== DELETE /courses/{courseId}/link ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void unlinkCourse_returnsNoContent_whenSuccessful() throws Exception {
        // First link the course
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-101");
        config.setResponsibleInstructor("Prof. Smith");
        config = campusOnlineConfigRepository.save(config);
        testCourse.setCampusOnlineConfiguration(config);
        courseRepository.save(testCourse);

        request.delete(BASE_URL + "courses/" + testCourse.getId() + "/link", HttpStatus.NO_CONTENT);

        // Verify it was removed from the database
        Course fromDb = courseRepository.findWithEagerCampusOnlineConfigurationById(testCourse.getId()).orElseThrow();
        assertThat(fromDb.getCampusOnlineConfiguration()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void unlinkCourse_returnsNoContent_whenNoPreviousLink() throws Exception {
        // Unlinking a course with no CAMPUSOnline config should still succeed
        request.delete(BASE_URL + "courses/" + testCourse.getId() + "/link", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void unlinkCourse_returnsForbidden_whenNotAdmin() throws Exception {
        request.delete(BASE_URL + "courses/" + testCourse.getId() + "/link", HttpStatus.FORBIDDEN);
    }

    // ==================== POST /courses/import ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void importCourse_returnsOk_withCreatedCourse() throws Exception {
        campusOnlineMockProvider.mockFetchCourseMetadata("CO-555", "Algorithms and Data Structures", "2025W");

        var importRequest = new CampusOnlineCourseImportRequestDTO("CO-555", "algods");
        CampusOnlineCourseDTO result = request.postWithResponseBody(BASE_URL + "courses/import", importRequest, CampusOnlineCourseDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.campusOnlineCourseId()).isEqualTo("CO-555");
        assertThat(result.title()).isEqualTo("Algorithms and Data Structures");
        assertThat(result.semester()).isEqualTo("2025W");

        // Verify persisted in DB by finding the course with this CAMPUSOnline config
        var courses = courseRepository.findAllWithCampusOnlineConfiguration();
        var importedCourse = courses.stream().filter(c -> "CO-555".equals(c.getCampusOnlineConfiguration().getCampusOnlineCourseId())).findFirst().orElseThrow();
        assertThat(importedCourse.getTitle()).isEqualTo("Algorithms and Data Structures");
        assertThat(importedCourse.getShortName()).isEqualTo("algods");
        assertThat(importedCourse.getStudentGroupName()).isEqualTo("artemis-algods-students");
        assertThat(importedCourse.getTeachingAssistantGroupName()).isEqualTo("artemis-algods-tutors");
        assertThat(importedCourse.getEditorGroupName()).isEqualTo("artemis-algods-editors");
        assertThat(importedCourse.getInstructorGroupName()).isEqualTo("artemis-algods-instructors");

        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void importCourse_returnsBadRequest_whenDuplicateCourseId() throws Exception {
        // First import a course
        campusOnlineMockProvider.mockFetchCourseMetadata("CO-DUP", "Duplicate Course", "2025W");
        var importRequest = new CampusOnlineCourseImportRequestDTO("CO-DUP", "duptest");
        request.postWithResponseBody(BASE_URL + "courses/import", importRequest, CampusOnlineCourseDTO.class, HttpStatus.CREATED);

        // Try to import again with same CAMPUSOnline course ID — should fail
        request.postWithResponseBody(BASE_URL + "courses/import", importRequest, CampusOnlineCourseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void importCourse_returnsBadRequest_whenInvalidShortName() throws Exception {
        // Short name with special characters violates @Pattern
        var importRequest = new CampusOnlineCourseImportRequestDTO("CO-555", "invalid-name!");
        request.postWithResponseBody(BASE_URL + "courses/import", importRequest, CampusOnlineCourseDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void importCourse_returnsForbidden_whenNotAdmin() throws Exception {
        var importRequest = new CampusOnlineCourseImportRequestDTO("CO-555", "algods");
        request.postWithResponseBody(BASE_URL + "courses/import", importRequest, CampusOnlineCourseDTO.class, HttpStatus.FORBIDDEN);
    }

    // ==================== POST /sync (all courses) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void syncAllCourses_returnsOk_withSyncResult() throws Exception {
        // Link a course so the sync has something to process
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-101");
        config = campusOnlineConfigRepository.save(config);
        testCourse.setCampusOnlineConfiguration(config);
        courseRepository.save(testCourse);

        String studentsXml = """
                <students>
                    <person ident="12345">
                        <name><given>Max</given><family>Mustermann</family></name>
                        <contactData><email>max@example.com</email></contactData>
                        <extension><registrationNumber>ab12cde</registrationNumber></extension>
                        <attendance confirmed="J"/>
                    </person>
                    <person ident="67890">
                        <name><given>Unknown</given><family>Student</family></name>
                        <contactData><email>unknown@example.com</email></contactData>
                        <extension><registrationNumber>xx99xxx</registrationNumber></extension>
                        <attendance confirmed="J"/>
                    </person>
                </students>
                """;
        campusOnlineMockProvider.mockFetchStudents("CO-101", studentsXml);

        CampusOnlineSyncResultDTO result = request.postWithResponseBody(BASE_URL + "sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.coursesSynced()).isEqualTo(1);
        assertThat(result.coursesFailed()).isZero();
        // Users won't be found in test DB, but sync should still complete
        assertThat(result.usersAdded() + result.usersNotFound()).isEqualTo(2);

        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void syncAllCourses_returnsOk_withNoConfiguredCourses() throws Exception {
        CampusOnlineSyncResultDTO result = request.postWithResponseBody(BASE_URL + "sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.coursesSynced()).isZero();
        assertThat(result.coursesFailed()).isZero();
        assertThat(result.usersAdded()).isZero();
        assertThat(result.usersNotFound()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void syncAllCourses_returnsForbidden_whenNotAdmin() throws Exception {
        request.postWithResponseBody(BASE_URL + "sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.FORBIDDEN);
    }

    // ==================== POST /courses/{courseId}/sync (single) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void syncSingleCourse_returnsOk_withSyncResult() throws Exception {
        CampusOnlineConfiguration config = new CampusOnlineConfiguration();
        config.setCampusOnlineCourseId("CO-101");
        config = campusOnlineConfigRepository.save(config);
        testCourse.setCampusOnlineConfiguration(config);
        courseRepository.save(testCourse);

        String studentsXml = """
                <students>
                    <person ident="12345">
                        <name><given>Max</given><family>Mustermann</family></name>
                        <contactData><email>max@example.com</email></contactData>
                        <extension><registrationNumber>ab12cde</registrationNumber></extension>
                        <attendance confirmed="J"/>
                    </person>
                    <person ident="99999">
                        <name><given>Not</given><family>Confirmed</family></name>
                        <contactData><email>nc@example.com</email></contactData>
                        <extension><registrationNumber>nc00noc</registrationNumber></extension>
                        <attendance confirmed="N"/>
                    </person>
                </students>
                """;
        campusOnlineMockProvider.mockFetchStudents("CO-101", studentsXml);

        CampusOnlineSyncResultDTO result = request.postWithResponseBody(BASE_URL + "courses/" + testCourse.getId() + "/sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.coursesSynced()).isEqualTo(1);
        assertThat(result.coursesFailed()).isZero();
        // Only 1 confirmed student (attendance confirmed="J"), the other is "N"
        assertThat(result.usersAdded() + result.usersNotFound()).isEqualTo(1);

        campusOnlineMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void syncSingleCourse_returnsNotFound_whenNoCampusOnlineConfig() throws Exception {
        // testCourse has no CAMPUSOnline configuration — EntityNotFoundException maps to 404
        request.postWithResponseBody(BASE_URL + "courses/" + testCourse.getId() + "/sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void syncSingleCourse_returnsNotFound_whenCourseDoesNotExist() throws Exception {
        request.postWithResponseBody(BASE_URL + "courses/999999/sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void syncSingleCourse_returnsForbidden_whenNotAdmin() throws Exception {
        request.postWithResponseBody(BASE_URL + "courses/" + testCourse.getId() + "/sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    void syncSingleCourse_returnsUnauthorized_whenNotLoggedIn() throws Exception {
        request.postWithResponseBody(BASE_URL + "courses/" + testCourse.getId() + "/sync", null, CampusOnlineSyncResultDTO.class, HttpStatus.UNAUTHORIZED);
    }
}
