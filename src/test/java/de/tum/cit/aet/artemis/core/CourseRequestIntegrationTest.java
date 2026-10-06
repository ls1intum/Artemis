package de.tum.cit.aet.artemis.core;

import static de.tum.cit.aet.artemis.core.util.QueryCountAssert.assertThatDb;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.admin.dto.CourseRequestsAdminOverviewDTO;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseRequest;
import de.tum.cit.aet.artemis.course.domain.CourseRequestStatus;
import de.tum.cit.aet.artemis.course.dto.CourseRequestCreateDTO;
import de.tum.cit.aet.artemis.course.dto.CourseRequestDTO;
import de.tum.cit.aet.artemis.course.dto.CourseRequestDecisionDTO;
import de.tum.cit.aet.artemis.course.dto.CourseRequestInstructorCourseDTO;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRequestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class CourseRequestIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "courserequest";

    /** A fixed point in the past, so the start dates of the course fixtures and their ordering are repeatable. */
    private static final ZonedDateTime FIXED_START = ZonedDateTime.parse("2026-01-01T00:00:00Z");

    @Autowired
    private CourseRequestRepository courseRequestRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private CourseConfigurationRepository courseConfigurationRepository;

    private User student;

    @BeforeEach
    void setUp() {
        student = userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1");
        userUtilService.addAdmin(TEST_PREFIX);
    }

    // ==================== CourseRequestResource Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_asStudent_shouldSucceed() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "TSTCRS", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "I need this course for teaching purposes.");

        CourseRequestDTO result = request.postWithResponseBody("/api/course/course-requests", createDTO, CourseRequestDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo("Test Course");
        assertThat(result.shortName()).isEqualTo("TSTCRS");
        assertThat(result.status()).isEqualTo(CourseRequestStatus.PENDING);
        assertThat(result.requester()).isNotNull();
        assertThat(result.requester().login()).isEqualTo(TEST_PREFIX + "student1");
    }

    @Test
    void createCourseRequest_asAnonymous_shouldReturnUnauthorized() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "TSTCRS2", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for the request.");

        request.post("/api/course/course-requests", createDTO, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withBlankTitle_shouldReturnBadRequest() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("", "TSTCRS3", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for the request.");

        request.post("/api/course/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withBlankReason_shouldReturnBadRequest() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "TSTCRS4", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null, "");

        request.post("/api/course/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withBlankSemester_shouldReturnBadRequest() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "TSTCRS5", "", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for request.");

        request.post("/api/course/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    // ==================== AdminCourseRequestResource Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAdminOverview_asAdmin_shouldReturnPendingRequests() throws Exception {
        // Create a course request first
        createTestCourseRequest("Admin Test", "ADMTST");

        CourseRequestsAdminOverviewDTO result = request.get("/api/admin/course-requests/overview", HttpStatus.OK, CourseRequestsAdminOverviewDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.pendingRequests()).isNotEmpty();
        assertThat(result.pendingRequests()).anyMatch(dto -> dto.shortName().equals("ADMTST"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAdminOverview_asInstructor_shouldReturnForbidden() throws Exception {
        request.get("/api/admin/course-requests/overview", HttpStatus.FORBIDDEN, CourseRequestsAdminOverviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAdminOverview_asStudent_shouldReturnForbidden() throws Exception {
        request.get("/api/admin/course-requests/overview", HttpStatus.FORBIDDEN, CourseRequestsAdminOverviewDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_asAdmin_shouldSucceed() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Accept Test", "ACPTST");

        CourseRequestDTO result = request.postWithResponseBody("/api/admin/course-requests/" + courseRequest.getId() + "/accept", null, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(CourseRequestStatus.ACCEPTED);
        assertThat(result.createdCourseId()).isNotNull();
        assertThat(result.processedDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void acceptCourseRequest_asInstructor_shouldReturnForbidden() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Accept Forbidden Test", "ACPFBD");

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/accept", null, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_nonExistent_shouldReturnNotFound() throws Exception {
        request.post("/api/admin/course-requests/99999/accept", null, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_asAdmin_shouldSucceed() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Reject Test", "REJTST");
        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("The course already exists under a different name.");

        CourseRequestDTO result = request.postWithResponseBody("/api/admin/course-requests/" + courseRequest.getId() + "/reject", decision, CourseRequestDTO.class, HttpStatus.OK);

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

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/reject", decision, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_withBlankReason_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Reject Bad Request Test", "REJBAD");
        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("");

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/reject", decision, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_nonExistent_shouldReturnNotFound() throws Exception {
        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("Reason for rejection");

        request.post("/api/admin/course-requests/99999/reject", decision, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAdminOverview_asAdmin_shouldReturnBothPendingAndDecidedRequests() throws Exception {
        // Create pending and decided requests
        createTestCourseRequest("Pending Test", "PNDTST");

        CourseRequest acceptedRequest = createTestCourseRequest("Accepted Test", "ACPTST1");
        acceptedRequest.setStatus(CourseRequestStatus.ACCEPTED);
        acceptedRequest.setProcessedDate(ZonedDateTime.now());
        courseRequestRepository.save(acceptedRequest);

        CourseRequestsAdminOverviewDTO result = request.get("/api/admin/course-requests/overview", HttpStatus.OK, CourseRequestsAdminOverviewDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.pendingRequests()).anyMatch(dto -> dto.shortName().equals("PNDTST"));
        assertThat(result.decidedRequests()).anyMatch(dto -> dto.shortName().equals("ACPTST1"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withExistingCourseShortName_shouldReturnBadRequestWithSuggestion() throws Exception {
        // Create an existing course with the same short name
        Course existingCourse = CourseFactory.generateMinimalCourse();
        existingCourse.setShortName("EXISTCRS");
        existingCourse.setTitle("Existing Course");
        courseRepository.save(existingCourse);

        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("New Course", "EXISTCRS", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for request.");

        Map<String, Object> errorResponse = performPostAndGetErrorResponse(createDTO);

        assertThat(errorResponse).containsKey("errorKey");
        assertThat(errorResponse.get("errorKey")).isEqualTo("courseShortNameExists");
        assertThat(errorResponse).containsKey("params");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) errorResponse.get("params");
        assertThat(params).containsKey("suggestedShortName");
        String suggestedShortName = (String) params.get("suggestedShortName");
        // Suggested short name should be based on title "New Course" -> "NC" + semester "2025" -> "NC2025"
        assertThat(suggestedShortName).isEqualTo("NC2025");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withExistingRequestShortName_shouldReturnBadRequestWithSuggestion() throws Exception {
        // Create an existing course request with the same short name
        createTestCourseRequest("Existing Request", "EXISTREQ");

        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("My New Course", "EXISTREQ", "SS2024", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for request.");

        Map<String, Object> errorResponse = performPostAndGetErrorResponse(createDTO);

        assertThat(errorResponse).containsKey("errorKey");
        assertThat(errorResponse.get("errorKey")).isEqualTo("courseRequestShortNameExists");
        assertThat(errorResponse).containsKey("params");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) errorResponse.get("params");
        assertThat(params).containsKey("suggestedShortName");
        String suggestedShortName = (String) params.get("suggestedShortName");
        // Suggested short name should be based on title "My New Course" -> "MNC" + semester "2024" -> "MNC2024"
        assertThat(suggestedShortName).isEqualTo("MNC2024");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withConflict_shouldSuggestIncrementedShortName() throws Exception {
        // Create existing course that takes the first suggested name
        Course existingCourse = CourseFactory.generateMinimalCourse();
        existingCourse.setShortName("TC2025");
        existingCourse.setTitle("Some Course");
        courseRepository.save(existingCourse);

        // Create another existing course that takes the same requested short name
        Course conflictingCourse = CourseFactory.generateMinimalCourse();
        conflictingCourse.setShortName("CONFLICT");
        conflictingCourse.setTitle("Conflict Course");
        courseRepository.save(conflictingCourse);

        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "CONFLICT", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for request.");

        Map<String, Object> errorResponse = performPostAndGetErrorResponse(createDTO);

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) errorResponse.get("params");
        String suggestedShortName = (String) params.get("suggestedShortName");
        // TC2025 is taken, so should suggest TC20251
        assertThat(suggestedShortName).isEqualTo("TC20251");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> performPostAndGetErrorResponse(CourseRequestCreateDTO createDTO) throws Exception {
        JsonMapper mapper = request.getObjectMapper();
        String jsonBody = mapper.writeValueAsString(createDTO);
        MvcResult result = request.performMvcRequest(MockMvcRequestBuilders.post(new URI("/api/course/course-requests")).contentType(MediaType.APPLICATION_JSON).content(jsonBody))
                .andExpect(status().isBadRequest()).andReturn();
        return mapper.readValue(result.getResponse().getContentAsString(), Map.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withInvalidShortName_shouldReturnBadRequest() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "invalid-name!", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for request.");

        request.post("/api/course/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withTitleTooLong_shouldReturnBadRequest() throws Exception {
        String longTitle = "A".repeat(256);
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO(longTitle, "LNGTITLE", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Reason for request.");

        request.post("/api/course/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withInvalidDates_shouldReturnBadRequest() throws Exception {
        ZonedDateTime startDate = ZonedDateTime.now().plusMonths(3);
        ZonedDateTime endDate = ZonedDateTime.now(); // End before start

        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "INVDATES", "WS2025", startDate, endDate, false, null, "Reason for request.");

        request.post("/api/course/course-requests", createDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_alreadyAccepted_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Already Accepted", "ALRACPT");
        courseRequest.setStatus(CourseRequestStatus.ACCEPTED);
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequestRepository.save(courseRequest);

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/accept", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_alreadyRejected_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Already Rejected", "ALRREJT");
        courseRequest.setStatus(CourseRequestStatus.REJECTED);
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequest.setDecisionReason("Previous rejection");
        courseRequestRepository.save(courseRequest);

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/accept", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_alreadyAccepted_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Already Accepted For Reject", "ALRACPT2");
        courseRequest.setStatus(CourseRequestStatus.ACCEPTED);
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequestRepository.save(courseRequest);

        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("Late rejection reason");

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/reject", decision, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void rejectCourseRequest_alreadyRejected_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Already Rejected For Reject", "ALRREJ2");
        courseRequest.setStatus(CourseRequestStatus.REJECTED);
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequest.setDecisionReason("Previous rejection");
        courseRequestRepository.save(courseRequest);

        CourseRequestDecisionDTO decision = new CourseRequestDecisionDTO("Another rejection reason");

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/reject", decision, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_withShortNameConflict_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Conflicting Accept", "CNFLCT");

        // Create a course with the same short name after the request was created
        Course existingCourse = CourseFactory.generateMinimalCourse();
        existingCourse.setShortName("CNFLCT");
        existingCourse.setTitle("Conflicting Course");
        courseRepository.save(existingCourse);

        request.post("/api/admin/course-requests/" + courseRequest.getId() + "/accept", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withAllFields_shouldSucceed() throws Exception {
        ZonedDateTime startDate = ZonedDateTime.now();
        ZonedDateTime endDate = ZonedDateTime.now().plusMonths(6);

        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Complete Course", "CMPLCRS", "SS2025", startDate, endDate, true, null,
                "A comprehensive reason for requesting this test course.");

        CourseRequestDTO result = request.postWithResponseBody("/api/course/course-requests", createDTO, CourseRequestDTO.class, HttpStatus.CREATED);

        assertThat(result).isNotNull();
        assertThat(result.title()).isEqualTo("Complete Course");
        assertThat(result.shortName()).isEqualTo("CMPLCRS");
        assertThat(result.semester()).isEqualTo("SS2025");
        assertThat(result.testCourse()).isTrue();
        assertThat(result.startDate()).isNotNull();
        assertThat(result.endDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAdminOverview_withMultiplePendingRequests_shouldReturnAll() throws Exception {
        // Create multiple pending requests with different timestamps
        ZonedDateTime baseTime = ZonedDateTime.now();
        createTestCourseRequest("First Request", "FIRST1", baseTime);
        createTestCourseRequest("Second Request", "SECOND1", baseTime.plusSeconds(1));
        createTestCourseRequest("Third Request", "THIRD1", baseTime.plusSeconds(2));

        CourseRequestsAdminOverviewDTO result = request.get("/api/admin/course-requests/overview", HttpStatus.OK, CourseRequestsAdminOverviewDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.pendingRequests()).hasSizeGreaterThanOrEqualTo(3);
        List<String> shortNames = result.pendingRequests().stream().map(CourseRequestDTO::shortName).toList();
        assertThat(shortNames).containsAll(List.of("FIRST1", "SECOND1", "THIRD1"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAdminOverview_listsCoursesPerRequesterNewestFirst_andCountMatchesList() throws Exception {
        User firstRequester = userUtilService.createAndSaveUser(TEST_PREFIX + "previousinstructor1");
        User secondRequester = userUtilService.createAndSaveUser(TEST_PREFIX + "previousinstructor2");
        User otherInstructor = userUtilService.createAndSaveUser(TEST_PREFIX + "otherinstructor");
        ZonedDateTime now = FIXED_START;
        Course oldest = createCourseStartingAt("PREVINSA", now.minusYears(2));
        Course newest = createCourseStartingAt("PREVINSB", now.minusMonths(1));
        Course middle = createCourseStartingAt("PREVINSC", now.minusYears(1));
        Course ofSecondRequester = createCourseStartingAt("PREVINSD", now.minusMonths(6));
        Course taughtAsTutor = createCourseStartingAt("PREVTUTOR", now.minusMonths(2));
        Course ofSomeoneElse = createCourseStartingAt("PREVOTHER", now.minusMonths(3));
        userUtilService.enrollUserInCourse(firstRequester, oldest, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(firstRequester, newest, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(firstRequester, middle, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(firstRequester, taughtAsTutor, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(secondRequester, ofSecondRequester, CourseRole.INSTRUCTOR);
        userUtilService.enrollUserInCourse(otherInstructor, ofSomeoneElse, CourseRole.INSTRUCTOR);
        createTestCourseRequestFor(firstRequester, "Previous Instructor One", "PREVREQ1");
        createTestCourseRequestFor(secondRequester, "Previous Instructor Two", "PREVREQ2");

        // One batch query loads the instructor courses of all requesters: the pending list, that batch, and the decided page with its count make 4 queries however many requests
        // are pending.
        CourseRequestsAdminOverviewDTO result = assertThatDb(() -> request.get("/api/admin/course-requests/overview", HttpStatus.OK, CourseRequestsAdminOverviewDTO.class))
                .hasBeenCalledAtMostTimes(4);

        CourseRequestDTO first = findPending(result, "PREVREQ1");
        assertThat(first.instructorCourses()).extracting(CourseRequestInstructorCourseDTO::id).containsExactly(newest.getId(), middle.getId(), oldest.getId());
        assertThat(first.instructorCourses()).extracting(CourseRequestInstructorCourseDTO::shortName).containsExactly(newest.getShortName(), middle.getShortName(),
                oldest.getShortName());
        assertThat(first.instructorCourses()).allSatisfy(course -> {
            assertThat(course.title()).isNotBlank();
            assertThat(course.semester()).isNotBlank();
        });
        assertThat(first.instructorCourseCount()).isEqualTo(first.instructorCourses().size());
        CourseRequestDTO second = findPending(result, "PREVREQ2");
        assertThat(second.instructorCourses()).extracting(CourseRequestInstructorCourseDTO::id).containsExactly(ofSecondRequester.getId());
        assertThat(second.instructorCourseCount()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAdminOverview_requesterWithoutInstructorCourses_hasZeroCountAndNoCourses() throws Exception {
        User requester = userUtilService.createAndSaveUser(TEST_PREFIX + "noinstructor");
        Course tutorCourse = createCourseStartingAt("NOINSTTUT", FIXED_START.minusMonths(1));
        userUtilService.enrollUserInCourse(requester, tutorCourse, CourseRole.TEACHING_ASSISTANT);
        createTestCourseRequestFor(requester, "No Instructor", "NOINSREQ");

        CourseRequestsAdminOverviewDTO result = request.get("/api/admin/course-requests/overview", HttpStatus.OK, CourseRequestsAdminOverviewDTO.class);

        CourseRequestDTO dto = findPending(result, "NOINSREQ");
        assertThat(dto.instructorCourseCount()).isZero();
        assertThat(dto.instructorCourses()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_keepsTheInstructorCoursesOfTheRequester() throws Exception {
        User requester = userUtilService.createAndSaveUser(TEST_PREFIX + "updateinstructor");
        Course instructed = createCourseStartingAt("UPDINSA", FIXED_START.minusMonths(1));
        userUtilService.enrollUserInCourse(requester, instructed, CourseRole.INSTRUCTOR);
        CourseRequest pending = createTestCourseRequestFor(requester, "Update Instructor", "UPDINSREQ");
        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Updated Title", "UPDINSREQ", "SS2025", FIXED_START, FIXED_START.plusMonths(3), false, "Updated reason.");

        CourseRequestDTO result = request.putWithResponseBody("/api/admin/course-requests/" + pending.getId(), updateDTO, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result.title()).isEqualTo("Updated Title");
        assertThat(result.instructorCourseCount()).isEqualTo(1);
        assertThat(result.instructorCourses()).extracting(CourseRequestInstructorCourseDTO::id).containsExactly(instructed.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void getAdminOverview_withPagination_shouldReturnCorrectDecidedRequests() throws Exception {
        // Create decided requests
        for (int i = 0; i < 3; i++) {
            CourseRequest request = createTestCourseRequest("Decided " + i, "DCDD" + i);
            request.setStatus(CourseRequestStatus.REJECTED);
            request.setProcessedDate(ZonedDateTime.now());
            request.setDecisionReason("Rejection reason " + i);
            courseRequestRepository.save(request);
        }

        CourseRequestsAdminOverviewDTO result = request.get("/api/admin/course-requests/overview?decidedPage=0&decidedPageSize=2", HttpStatus.OK,
                CourseRequestsAdminOverviewDTO.class);

        assertThat(result).isNotNull();
        assertThat(result.decidedRequests()).hasSizeLessThanOrEqualTo(2);
        assertThat(result.totalDecidedCount()).isGreaterThanOrEqualTo(3);
    }

    // ==================== Update Endpoint Tests ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_asAdmin_shouldSucceed() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Original Title", "ORIGTIT");

        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Updated Title", "UPDTTIT", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), true, null,
                "Updated reason for the course request.");

        CourseRequestDTO result = request.putWithResponseBody("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(courseRequest.getId());
        assertThat(result.title()).isEqualTo("Updated Title");
        assertThat(result.shortName()).isEqualTo("UPDTTIT");
        assertThat(result.semester()).isEqualTo("SS2025");
        assertThat(result.testCourse()).isTrue();
        // the request was a regular, grade-relevant one; marking it as test course without a value makes it not grade relevant
        assertThat(result.gradeRelevant()).isFalse();
        assertThat(result.reason()).isEqualTo("Updated reason for the course request.");
        assertThat(result.status()).isEqualTo(CourseRequestStatus.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_alreadyProcessed_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Processed Request", "PROCSSD");
        courseRequest.setStatus(CourseRequestStatus.ACCEPTED);
        courseRequest.setProcessedDate(ZonedDateTime.now());
        courseRequestRepository.save(courseRequest);

        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Updated Title", "UPDTTIT2", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Updated reason.");

        request.put("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_withShortNameConflict_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Request to Update", "REQUPD");

        // Create a course with the same short name we want to update to
        Course existingCourse = CourseFactory.generateMinimalCourse();
        existingCourse.setShortName("UPDEXST");
        existingCourse.setTitle("Existing Course for Update Test");
        courseRepository.save(existingCourse);

        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Updated Title", "UPDEXST", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Updated reason.");

        request.put("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_keepSameShortName_shouldSucceed() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Original Title", "KEEPSN");

        // Update only title, keeping the same short name
        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Updated Title", "KEEPSN", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Updated reason.");

        CourseRequestDTO result = request.putWithResponseBody("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.shortName()).isEqualTo("KEEPSN");
        assertThat(result.title()).isEqualTo("Updated Title");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateCourseRequest_asInstructor_shouldReturnForbidden() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Request For Instructor", "REQINST");

        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Updated Title", "UPDTINST", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Updated reason.");

        request.put("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_withInvalidData_shouldReturnBadRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Request to Update Invalid", "REQINV");

        // Empty title should fail validation
        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("", "UPDTINV", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Updated reason.");

        request.put("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_notFound_shouldReturnNotFound() throws Exception {
        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Updated Title", "NOTFND", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Updated reason.");

        request.put("/api/admin/course-requests/999999", updateDTO, HttpStatus.NOT_FOUND);
    }

    // ==================== Grade relevance =============

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_withoutGradeRelevant_defaultsToGradeRelevant() throws Exception {
        CourseRequestDTO result = postCreateRequest("GRDDEF", false, null);

        assertThat(result.gradeRelevant()).isTrue();
        assertThat(courseRequestRepository.findById(result.id()).orElseThrow().isGradeRelevant()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_notGradeRelevant_isPersisted() throws Exception {
        CourseRequestDTO result = postCreateRequest("GRDOFF", false, false);

        assertThat(result.gradeRelevant()).isFalse();
        assertThat(courseRequestRepository.findById(result.id()).orElseThrow().isGradeRelevant()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_testCourseWithoutGradeRelevant_defaultsToNotGradeRelevant() throws Exception {
        CourseRequestDTO result = postCreateRequest("GRDTST", true, null);

        assertThat(result.testCourse()).isTrue();
        assertThat(result.gradeRelevant()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_testCourseThatIsGradeRelevant_shouldReturnBadRequest() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "GRDBAD", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), true, true,
                "Reason for request.");

        Map<String, Object> errorResponse = performPostAndGetErrorResponse(createDTO);

        assertThat(errorResponse.get("errorKey")).isEqualTo("testCourseNotGradeRelevant");
        assertThat(courseRequestRepository.findOneByShortNameIgnoreCase("GRDBAD")).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_changesGradeRelevant() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Grade Update", "GRDUPD");
        assertThat(courseRequest.isGradeRelevant()).isTrue();
        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Grade Update", "GRDUPD", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, false,
                "Updated reason.");

        CourseRequestDTO result = request.putWithResponseBody("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result.gradeRelevant()).isFalse();
        assertThat(courseRequestRepository.findById(courseRequest.getId()).orElseThrow().isGradeRelevant()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_testCourseThatIsGradeRelevant_shouldReturnBadRequestAndKeepRequest() throws Exception {
        CourseRequest courseRequest = createTestCourseRequest("Grade Update Bad", "GRDUBD");
        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Grade Update Bad", "GRDUBD", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), true, true,
                "Updated reason.");

        request.put("/api/admin/course-requests/" + courseRequest.getId(), updateDTO, HttpStatus.BAD_REQUEST);

        CourseRequest unchanged = courseRequestRepository.findById(courseRequest.getId()).orElseThrow();
        assertThat(unchanged.isTestCourse()).isFalse();
        assertThat(unchanged.isGradeRelevant()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_appliesNotGradeRelevantToCreatedRegularCourse() throws Exception {
        CourseRequest notGradeRelevant = createTestCourseRequest("Not Grade Relevant", "GRDNO");
        notGradeRelevant.setGradeRelevant(false);
        courseRequestRepository.save(notGradeRelevant);

        CourseRequestDTO result = request.postWithResponseBody("/api/admin/course-requests/" + notGradeRelevant.getId() + "/accept", null, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result.gradeRelevant()).isFalse();
        assertThat(courseConfigurationRepository.findByCourseId(result.createdCourseId()).orElseThrow().isGradeRelevant()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_appliesGradeRelevantToCreatedRegularCourse() throws Exception {
        CourseRequest gradeRelevant = createTestCourseRequest("Grade Relevant", "GRDYES");

        CourseRequestDTO result = request.postWithResponseBody("/api/admin/course-requests/" + gradeRelevant.getId() + "/accept", null, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result.gradeRelevant()).isTrue();
        assertThat(courseConfigurationRepository.findByCourseId(result.createdCourseId()).orElseThrow().isGradeRelevant()).isTrue();
    }

    /**
     * A test course request is stored as not grade relevant (the form never offers the option for a test course), but the created course keeps the safe default
     * of the regular course creation. Otherwise the data retention of a test course that is later turned into a regular course would silently drop.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void acceptCourseRequest_keepsSafeGradeRelevantDefaultForCreatedTestCourse() throws Exception {
        CourseRequest testCourseRequest = createTestCourseRequest("Test Course Request", "GRDTCR");
        testCourseRequest.setTestCourse(true);
        testCourseRequest.setGradeRelevant(false);
        courseRequestRepository.save(testCourseRequest);

        CourseRequestDTO result = request.postWithResponseBody("/api/admin/course-requests/" + testCourseRequest.getId() + "/accept", null, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result.testCourse()).isTrue();
        assertThat(result.gradeRelevant()).isFalse();
        Course createdCourse = courseRepository.findById(result.createdCourseId()).orElseThrow();
        assertThat(createdCourse.isTestCourse()).isTrue();
        assertThat(courseConfigurationRepository.findByCourseId(createdCourse.getId()).orElseThrow().isGradeRelevant()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void updateCourseRequest_fromTestCourseToRegularCourse_defaultsToGradeRelevant() throws Exception {
        CourseRequest testCourseRequest = createTestCourseRequest("Flip To Regular", "GRDFLP");
        testCourseRequest.setTestCourse(true);
        testCourseRequest.setGradeRelevant(false);
        courseRequestRepository.save(testCourseRequest);
        CourseRequestCreateDTO updateDTO = new CourseRequestCreateDTO("Flip To Regular", "GRDFLP", "SS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, null,
                "Updated reason.");

        CourseRequestDTO result = request.putWithResponseBody("/api/admin/course-requests/" + testCourseRequest.getId(), updateDTO, CourseRequestDTO.class, HttpStatus.OK);

        assertThat(result.testCourse()).isFalse();
        assertThat(result.gradeRelevant()).isTrue();
        assertThat(courseRequestRepository.findById(testCourseRequest.getId()).orElseThrow().isGradeRelevant()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createCourseRequest_responseSerializesNotGradeRelevant() throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", "GRDJSN", "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), false, false,
                "Reason for request.");
        MvcResult result = request.performMvcRequest(MockMvcRequestBuilders.post(new URI("/api/course/course-requests")).contentType(MediaType.APPLICATION_JSON)
                .content(request.getObjectMapper().writeValueAsString(createDTO))).andExpect(status().isCreated()).andReturn();

        // NON_EMPTY must not drop a false value: the client reads a missing gradeRelevant as grade relevant
        assertThat(result.getResponse().getContentAsString()).contains("\"gradeRelevant\":false");
    }

    private CourseRequestDTO postCreateRequest(String shortName, boolean testCourse, Boolean gradeRelevant) throws Exception {
        CourseRequestCreateDTO createDTO = new CourseRequestCreateDTO("Test Course", shortName, "WS2025", ZonedDateTime.now(), ZonedDateTime.now().plusMonths(3), testCourse,
                gradeRelevant, "Reason for request.");
        return request.postWithResponseBody("/api/course/course-requests", createDTO, CourseRequestDTO.class, HttpStatus.CREATED);
=======
    private Course createCourseStartingAt(String shortName, ZonedDateTime startDate) {
        Course course = courseUtilService.createCourseWithShortName(shortName);
        course.setStartDate(startDate);
        return courseRepository.save(course);
    }

    private CourseRequest createTestCourseRequestFor(User requester, String title, String shortName) {
        CourseRequest courseRequest = createTestCourseRequest(title, shortName);
        courseRequest.setRequester(requester);
        return courseRequestRepository.save(courseRequest);
    }

    private static CourseRequestDTO findPending(CourseRequestsAdminOverviewDTO overview, String shortName) {
        return overview.pendingRequests().stream().filter(candidate -> shortName.equals(candidate.shortName())).findFirst().orElseThrow();
    }

    private CourseRequest createTestCourseRequest(String title, String shortName) {
        return createTestCourseRequest(title, shortName, ZonedDateTime.now());
    }

    private CourseRequest createTestCourseRequest(String title, String shortName, ZonedDateTime createdDate) {
        CourseRequest courseRequest = new CourseRequest();
        courseRequest.setTitle(title);
        courseRequest.setShortName(shortName);
        courseRequest.setSemester("WS2025");
        courseRequest.setStartDate(ZonedDateTime.now());
        courseRequest.setEndDate(ZonedDateTime.now().plusMonths(3));
        courseRequest.setReason("Test reason for the course request");
        courseRequest.setStatus(CourseRequestStatus.PENDING);
        courseRequest.setCreatedDate(createdDate);
        courseRequest.setRequester(student);
        return courseRequestRepository.save(courseRequest);
    }
}
