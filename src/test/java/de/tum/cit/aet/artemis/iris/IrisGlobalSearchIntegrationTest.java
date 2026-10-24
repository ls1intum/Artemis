package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;

class IrisGlobalSearchIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "globalsearchit";

    @BeforeEach
    void setupUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        activateIrisGlobally();
    }

    /**
     * Enrolls the standard test student in the given courses. The lecture search only covers the courses the caller can access.
     */
    private void enrollStudent1(Course... courses) {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        for (Course course : courses) {
            userUtilService.enrollUserInCourse(student, course, CourseRole.STUDENT);
        }
    }

    // ==================== /api/iris/lecture-search (synchronous) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnResults() throws Exception {
        enrollStudent1(courseUtilService.createCourse());
        var results = List.of(
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(1L, "Introduction Slide", "/link/1", 3, "lecture_unit_slide", Map.of("unit", 1L, "page", 3), "p. 3"),
                        "supervised learning snippet"),
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(2L, "Neural Networks", "/link/2", 7, "lecture_unit_slide", Map.of("unit", 2L, "page", 7), "p. 7"),
                        "backpropagation snippet"));
        irisRequestMockProvider.mockSearchLectures(results);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).lectureUnit().id()).isEqualTo(1L);
        assertThat(response.get(0).snippet()).isEqualTo("supervised learning snippet");
        assertThat(response.get(1).lectureUnit().id()).isEqualTo(2L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnEmptyList() throws Exception {
        enrollStudent1(courseUtilService.createCourse());
        irisRequestMockProvider.mockSearchLectures(List.of());

        var requestDTO = new PyrisLectureSearchRequestDTO("nonexistent topic", 5, null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        enrollStudent1(courseUtilService.createCourse());
        irisRequestMockProvider.mockSearchLecturesError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void search_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_withCourseIdFilter_shouldReturnFilteredResults() throws Exception {
        var filteredCourse = courseUtilService.createCourse();
        enrollStudent1(filteredCourse);
        var filteredCourseId = filteredCourse.getId();
        var results = List.of(new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(filteredCourseId, "Filtered Course"),
                new PyrisLectureSearchResultDTO.LectureDTO(10L, "Filtered Lecture"),
                new PyrisLectureSearchResultDTO.LectureUnitDTO(1L, "Filtered Unit", "/link/1", 1, "lecture_unit_slide", Map.of("unit", 1L, "page", 1), "p. 1"),
                "filtered snippet"));
        irisRequestMockProvider.mockSearchLectures(results, List.of(filteredCourseId));

        var requestDTO = new PyrisLectureSearchRequestDTO("filtered query", 5, List.of(filteredCourseId));
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().course().id()).isEqualTo(filteredCourseId);
        assertThat(response.getFirst().snippet()).isEqualTo("filtered snippet");
    }

    /**
     * The course filter is client input, so a requested course the caller cannot access is dropped before the request is sent to Pyris.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenARequestedCourseIsNotAccessible_shouldNotForwardThatCourse() throws Exception {
        var accessibleCourse = courseUtilService.createCourse();
        var inaccessibleCourse = courseUtilService.createCourse();
        enrollStudent1(accessibleCourse);

        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, List.of(accessibleCourse.getId(), inaccessibleCourse.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(sent.get().courseIds()).containsExactly(accessibleCourse.getId());
    }

    /**
     * Dropping every requested course leaves nothing to search. An empty list is omitted on the wire and read by Pyris as "all courses", so the
     * answer is given without asking Pyris.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenNoRequestedCourseIsAccessible_shouldNotReachPyris() throws Exception {
        var accessibleCourse = courseUtilService.createCourse();
        var inaccessibleCourse = courseUtilService.createCourse();
        enrollStudent1(accessibleCourse);

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, List.of(inaccessibleCourse.getId()));
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * A course id that does not exist is just as inaccessible as one the caller is not enrolled in.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenARequestedCourseDoesNotExist_shouldNotReachPyris() throws Exception {
        enrollStudent1(courseUtilService.createCourse());

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, List.of(Long.MAX_VALUE));
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * An unscoped request carries no course ids of its own, so Pyris would search every course. It is narrowed to the courses of the caller.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenUnscoped_shouldOnlyForwardTheAccessibleCourses() throws Exception {
        var accessibleCourse = courseUtilService.createCourse();
        var inaccessibleCourse = courseUtilService.createCourse();
        enrollStudent1(accessibleCourse);

        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        request.postListWithResponseBody("/api/iris/lecture-search", new PyrisLectureSearchRequestDTO("machine learning", 5, null), PyrisLectureSearchResultDTO.class,
                HttpStatus.OK);

        assertThat(sent.get().courseIds()).containsExactly(accessibleCourse.getId()).doesNotContain(inaccessibleCourse.getId());
    }

    /**
     * A caller without any course has nothing to search, which is answered without asking Pyris: an empty course list would be read as "all courses".
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenUnscopedAndTheCallerHasNoCourse_shouldNotReachPyris() throws Exception {
        courseUtilService.createCourse();

        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", new PyrisLectureSearchRequestDTO("machine learning", 5, null),
                PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * Every role keeps the courses it has access to: a teaching assistant and an instructor are not limited to the courses they study in.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "staff", roles = "USER")
    void search_whenTheCallerIsStaffInTheRequestedCourses_shouldForwardThem() throws Exception {
        var taCourse = courseUtilService.addEmptyCourse();
        var instructorCourse = courseUtilService.addEmptyCourse();
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "staff");
        userUtilService.enrollUserInCourse(user, taCourse, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(user, instructorCourse, CourseRole.INSTRUCTOR);
        irisRequestMockProvider.mockSearchLectures(List.of(), List.of(taCourse.getId(), instructorCourse.getId()));

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, List.of(taCourse.getId(), instructorCourse.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);
    }

    /**
     * An administrator can access every course, so the courses it asked for are kept.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void search_asAdminWithRequestedCourses_shouldForwardThem() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX);
        var firstCourse = courseUtilService.createCourse();
        var secondCourse = courseUtilService.createCourse();
        irisRequestMockProvider.mockSearchLectures(List.of(), List.of(firstCourse.getId(), secondCourse.getId()));

        var requestDTO = new PyrisLectureSearchRequestDTO("machine learning", 5, List.of(firstCourse.getId(), secondCourse.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);
    }

    /**
     * An unscoped administrator search stays unscoped: the administrator can access every course, and a list of all courses would be an unbounded filter.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void search_asAdminUnscoped_shouldStayUnscoped() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX);
        courseUtilService.createCourse();

        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        request.postListWithResponseBody("/api/iris/lecture-search", new PyrisLectureSearchRequestDTO("machine learning", 5, null), PyrisLectureSearchResultDTO.class,
                HttpStatus.OK);

        assertThat(sent.get().courseIds()).isNullOrEmpty();
    }

    // ==================== /api/iris/search-answer (async, webhook-based) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_shouldReturnAccepted() throws Exception {
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> {
            // no assertions needed here; just confirm the mock is consumed
        });

        var requestDTO = new GlobalSearchAskRequestDTO("What is backpropagation?", 5, UUID.randomUUID());
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_thinkingWebhook_shouldForwardThinkingToWebSocket() throws Exception {
        AtomicReference<String> jobIdRef = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> jobIdRef.set(dto.settings().authenticationToken()));

        var requestDTO = new GlobalSearchAskRequestDTO("What is backpropagation?", 5, UUID.randomUUID());
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        sendGlobalSearchAnswerStatus(jobIdRef.get(), new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.RUNNING, null, null, null));

        verifyMessageWasSentOverWebsocket(TEST_PREFIX + "student1", "global-search-answer",
                obj -> obj instanceof IrisGlobalSearchAnswerWebsocketDTO dto && dto.isThinking() && dto.answer() == null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_resultWebhookWithAnswer_shouldForwardAnswerToWebSocket() throws Exception {
        AtomicReference<String> jobIdRef = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> jobIdRef.set(dto.settings().authenticationToken()));

        var requestDTO = new GlobalSearchAskRequestDTO("What is backpropagation?", 5, UUID.randomUUID());
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        var source = new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(1L, "ML"), new PyrisLectureSearchResultDTO.LectureDTO(2L, "Intro"),
                new PyrisLectureSearchResultDTO.LectureUnitDTO(3L, "Neural Nets", "/link/3", 5, "lecture_unit_slide", Map.of("unit", 3L, "page", 5), "p. 5"), "backprop snippet");
        sendGlobalSearchAnswerStatus(jobIdRef.get(),
                new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FINISHED, null, "Neural networks learn via backpropagation.", List.of(source)));

        verifyMessageWasSentOverWebsocket(TEST_PREFIX + "student1", "global-search-answer",
                obj -> obj instanceof IrisGlobalSearchAnswerWebsocketDTO dto && !dto.isThinking() && "Neural networks learn via backpropagation.".equals(dto.answer()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_resultWebhookWithNullAnswer_shouldSendCompletionWithNoAnswer() throws Exception {
        AtomicReference<String> jobIdRef = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> jobIdRef.set(dto.settings().authenticationToken()));

        var requestDTO = new GlobalSearchAskRequestDTO("Go to course overview", 5, UUID.randomUUID());
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        sendGlobalSearchAnswerStatus(jobIdRef.get(), new PyrisGlobalSearchAnswerStatusUpdateDTO(PyrisRunState.FINISHED, null, null, null));

        verifyMessageWasSentOverWebsocket(TEST_PREFIX + "student1", "global-search-answer",
                obj -> obj instanceof IrisGlobalSearchAnswerWebsocketDTO dto && !dto.isThinking() && dto.answer() == null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockGlobalSearchIrisAnswerError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID());
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void ask_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID());
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.UNAUTHORIZED);
    }

    // ==================== helpers ====================

    private void sendGlobalSearchAnswerStatus(String jobId, PyrisGlobalSearchAnswerStatusUpdateDTO statusUpdate) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/global-search/runs/" + jobId + "/status", statusUpdate, HttpStatus.OK, headers);
    }
}
