package de.tum.cit.aet.artemis.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.CourseRequest;
import de.tum.cit.aet.artemis.core.domain.CourseRequestStatus;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.core.dto.CourseRequestDecisionDTO;
import de.tum.cit.aet.artemis.core.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseRequestIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courserequest";

    @Autowired
    private CourseRequestRepository courseRequestRepository;

    private User student;

    @BeforeEach
    void setUp() {
        student = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1");
        userUtilService.createAndSaveUser(TEST_PREFIX + "admin");
    }

    // ==================== CourseRequestResource Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_asStudent_shouldSucceed() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "TSTCRS", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false,
                "I need this course for teaching purposes.");

        CourseRequestDTO result = request.postWithResponseBody("/api/core/course-requests", createDTO, CourseRequestDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo("Test Course");
        assertThat(result.shortName()).isEqualTo("TSTCRS");
        assertThat(result.status()).isEqualTo(CourseRequestStatus.PENDING);
        assertThat(result.requester()).isNotNull();
        assertThat(result.requester().getLogin()).isEqualTo(TEST_PREFIX + "student1");
    }

    @Test
    void createCourseRequest_asAnonymous_shouldReturnUnauthorized() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "TSTCRS2", "WS2025", null, null, false, "Reason for the request.");

        request.post("/api/core/course-requests", createDTO, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withBlankTitle_shouldReturnBadRequest() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("", "TSTCRS3", "WS2025", null, null, false, "Reason for the request.");

        request.post("/api/core/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withBlankReason_shouldReturnBadRequest() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "TSTCRS4", "WS2025", null, null, false, "");

        request.post("/api/core/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    // ==================== AdminCourseRequestResource Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAllCourseRequests_asAdmin_shouldSucceed() throws Exception {
        // Create a course request first
        CourseRequest courseRequest = createTestCourseRequest("Admin Test", "ADMTST");

        List<CourseRequestDTO> result = request.getList("/api/core/admin/course-requests", HttpStatus.OK, CourseRequestDTO.class);

        assertThat(result).isNotEmpty();
        assertThat(result).anyMatch(dto -> dto.shortName().equals("ADMTST"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllCourseRequests_asInstructor_shouldReturnForbidden() throws Exception {
        request.get("/api/core/admin/course-requests", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllCourseRequests_asStudent_shouldReturnForbidden() throws Exception {
        request.get("/api/core/admin/course-requests", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_asAdmin_shouldSucceed() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Accept Test", "ACPTST");

        CourseRequestDTO result = request.postWithResponseBody("/api/core/admin/course-requests/" + courseRequest.getId() + "/accept", null, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(CourseRequestStatus.ACCEPTED);
        assertThat(result.createdCourseId()).isNotNull();
        assertThat(result.processedDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void acceptCourseRequest_asInstructor_shouldReturnForbidden() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Accept Forbidden Test", "ACPFBD");

        request.post("/api/core/admin/course-requests/" + courseRequest.getId() + "/accept", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_nonExistent_shouldReturnNotFound() throws Exception {
        request.post("/api/core/admin/course-requests/99999/accept", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_asAdmin_shouldSucceed() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Reject Test", "REJTST");
        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("The course already exists under a different name.");

        CourseRequestDTO result = request.postWithResponseBody("/api/core/admin/course-requests/" + courseRequest.getId() + "/reject", decision, CourseRequestDTO.class,
                HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(CourseRequestStatus.REJECTED);
        assertThat(result.decisionReason()).isEqualTo("The course already exists under a different name.");
        assertThat(result.processedDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void rejectCourseRequest_asInstructor_shouldReturnForbidden() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Reject Forbidden Test", "REJFBD");
        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("Rejection reason");

        request.post("/api/core/admin/course-requests/" + courseRequest.getId() + "/reject", decision, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_withBlankReason_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Reject Bad Request Test", "REJBAD");
        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("");

        request.post("/api/core/admin/course-requests/" + courseRequest.getId() + "/reject", decision, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_nonExistent_shouldReturnNotFound() throws Exception {
        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("Reason for rejection");

        request.post("/api/core/admin/course-requests/99999/reject", decision, HttpStatus.NOT_FOUND);
    }

    private CourseRequest createTestCourseRequest(String title, String shortName) {
        CourseRequest courseRequest = new CourseRequest();
        courseRequest.setTitle(title);
        courseRequest.setShortName(shortName);
        courseRequest.setReason("Test reason for the course request");
        courseRequest.setStatus(CourseRequestStatus.PENDING);
        courseRequest.setCreatedDate(ZonedDateTime.now());
        courseRequest.setRequester(student);
        return courseRequestRepository.save(courseRequest);
    }
}
