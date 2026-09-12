package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchAnswerWebsocketDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchLectureRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;

class IrisGlobalSearchIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "globalsearchit";

    @Autowired
    private AuthorizationCheckService authCheckService;

    @BeforeEach
    void setupUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        activateIrisGlobally();
    }

    // ==================== /api/iris/lecture-search (synchronous) ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnResults() throws Exception {
        var results = List.of(
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(1L, "Introduction Slide", "/link/1", 3, "lecture_unit_slide", Map.of("unit", 1L, "page", 3), "p. 3"),
                        "supervised learning snippet"),
                new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(5L, "Machine Learning"), new PyrisLectureSearchResultDTO.LectureDTO(10L, "Intro to ML"),
                        new PyrisLectureSearchResultDTO.LectureUnitDTO(2L, "Neural Networks", "/link/2", 7, "lecture_unit_slide", Map.of("unit", 2L, "page", 7), "p. 7"),
                        "backpropagation snippet"));
        irisRequestMockProvider.mockSearchLectures(results);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).hasSize(2);
        assertThat(response.get(0).lectureUnit().id()).isEqualTo(1L);
        assertThat(response.get(0).snippet()).isEqualTo("supervised learning snippet");
        assertThat(response.get(1).lectureUnit().id()).isEqualTo(2L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldReturnEmptyList() throws Exception {
        irisRequestMockProvider.mockSearchLectures(List.of());

        var requestDTO = new GlobalSearchLectureRequestDTO("nonexistent topic", 5, null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * Instructors can switch Iris off per course, and content search has to honor that toggle like every other Iris feature. Disabling a course does not remove what was already
     * ingested, so the scope has to be narrowed on the way out rather than relying on the index being empty.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenIrisIsDisabledForTheOnlyRequestedCourse_shouldNotReachPyris() throws Exception {
        var course = courseUtilService.createCourse();
        disableIrisFor(course);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(course.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenIrisIsDisabledForOneOfTheRequestedCourses_shouldForwardOnlyTheEnabledCourse() throws Exception {
        var enabledCourse = courseUtilService.createCourse();
        var disabledCourse = courseUtilService.createCourse();
        enableIrisFor(enabledCourse);
        disableIrisFor(disabledCourse);
        irisRequestMockProvider.mockSearchLectures(List.of(), List.of(enabledCourse.getId()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(enabledCourse.getId(), disabledCourse.getId()));
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * A course that never saved Iris settings has no row at all, and the default settings enable Iris. Narrowing must therefore drop only the courses that were explicitly
     * switched off, otherwise content search would silently stop working for every course that never opened the Iris settings page.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenTheCourseHasNoIrisSettingsRow_shouldForwardTheCourse() throws Exception {
        var course = courseUtilService.createCourse();
        irisRequestMockProvider.mockSearchLectures(List.of(), List.of(course.getId()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(course.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockSearchLecturesError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void search_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_withCourseIdFilter_shouldReturnFilteredResults() throws Exception {
        var filteredCourseId = 42L;
        var results = List.of(new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(filteredCourseId, "Filtered Course"),
                new PyrisLectureSearchResultDTO.LectureDTO(10L, "Filtered Lecture"),
                new PyrisLectureSearchResultDTO.LectureUnitDTO(1L, "Filtered Unit", "/link/1", 1, "lecture_unit_slide", Map.of("unit", 1L, "page", 1), "p. 1"),
                "filtered snippet"));
        irisRequestMockProvider.mockSearchLectures(results, List.of(filteredCourseId));

        var requestDTO = new GlobalSearchLectureRequestDTO("filtered query", 5, List.of(filteredCourseId));
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().course().id()).isEqualTo(filteredCourseId);
        assertThat(response.getFirst().snippet()).isEqualTo("filtered snippet");
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

    /**
     * The decision selects which model may answer, so Pyris has to receive the one the account actually recorded. Asserting
     * on a decision other than the fixture default is what makes this test able to fail.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_shouldForwardTheRecordedDecisionToPyris() throws Exception {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userUtilService.setAiSelectionDecision(student, AiSelectionDecision.LOCAL_AI);
        AtomicReference<AiSelectionDecision> forwardedDecision = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> forwardedDecision.set(dto.settings().selection()));

        var requestDTO = new GlobalSearchAskRequestDTO("What is backpropagation?", 5, UUID.randomUUID());
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        assertThat(forwardedDecision.get()).isEqualTo(AiSelectionDecision.LOCAL_AI);
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

    // ==================== access context consistency with Artemis roles ====================

    @Test
    @WithMockUser(username = TEST_PREFIX + "multi", roles = "USER")
    void lectureSearch_sendsAccessContextConsistentWithArtemisRoles() throws Exception {
        // One user holding a different role in each course, plus a course they are not enrolled in.
        var studentCourse = courseUtilService.addEmptyCourse();
        var taCourse = courseUtilService.addEmptyCourse();
        var editorCourse = courseUtilService.addEmptyCourse();
        var instructorCourse = courseUtilService.addEmptyCourse();
        var foreignCourse = courseUtilService.addEmptyCourse();

        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "multi");
        userUtilService.enrollUserInCourse(user, studentCourse, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, taCourse, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(user, editorCourse, CourseRole.EDITOR);
        userUtilService.enrollUserInCourse(user, instructorCourse, CourseRole.INSTRUCTOR);
        // user is intentionally NOT enrolled in foreignCourse
        // Reload with course roles so the AuthorizationCheckService assertions see the enrollments.
        user = userTestRepository.getUserWithCourseRolesAndAuthorities(TEST_PREFIX + "multi");

        AtomicReference<PyrisAccessContextDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), dto -> sent.set(dto.accessContext()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        PyrisAccessContextDTO context = sent.get();
        assertThat(context).isNotNull();
        assertThat(context.unrestricted()).isFalse();
        assertThat(context.now()).isNotNull();

        // The context sent to Iris must match Artemis's own access decision for every course, so the Iris lane
        // scopes and bypasses exactly like the Artemis UI does. Asserted against AuthorizationCheckService, not literals.
        // Empty role lists are omitted on the wire (@JsonInclude NON_EMPTY) and arrive as null, which the contract
        // treats as empty; orEmpty() applies that same interpretation here.
        for (Course course : List.of(studentCourse, taCourse, editorCourse, instructorCourse, foreignCourse)) {
            long id = course.getId();
            assertThat(orEmpty(context.courseIds()).contains(id)).as("courseIds membership for course %d must match isAtLeastStudentInCourse", id)
                    .isEqualTo(authCheckService.isAtLeastStudentInCourse(course, user));
            assertThat(orEmpty(context.staffCourseIds()).contains(id))
                    .as("staffCourseIds (release/visibility bypass) for course %d must match isAtLeastTeachingAssistantInCourse", id)
                    .isEqualTo(authCheckService.isAtLeastTeachingAssistantInCourse(course, user));
            assertThat(orEmpty(context.studentCourseIds()).contains(id)).as("studentCourseIds for course %d must match isOnlyStudentInCourse", id)
                    .isEqualTo(authCheckService.isOnlyStudentInCourse(course, user));
        }

        // The course the user cannot access must never leak into any scope.
        assertThat(orEmpty(context.courseIds())).doesNotContain(foreignCourse.getId());
        assertThat(orEmpty(context.staffCourseIds())).doesNotContain(foreignCourse.getId());
        assertThat(orEmpty(context.studentCourseIds())).doesNotContain(foreignCourse.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void lectureSearch_forAdmin_sendsUnrestrictedContext() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX);

        AtomicReference<PyrisAccessContextDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), dto -> sent.set(dto.accessContext()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        PyrisAccessContextDTO context = sent.get();
        assertThat(context).isNotNull();
        // An Artemis admin sees everything, so Iris must receive a present, unrestricted context with empty role lists.
        assertThat(authCheckService.isAdmin(TEST_PREFIX + "admin")).as("the test user is an Artemis admin").isTrue();
        assertThat(context.unrestricted()).isTrue();
        assertThat(orEmpty(context.courseIds())).isEmpty();
        assertThat(orEmpty(context.staffCourseIds())).isEmpty();
        assertThat(orEmpty(context.studentCourseIds())).isEmpty();
        assertThat(context.now()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void lectureSearchAndAnswer_forwardTheSameAccessContext() throws Exception {
        AtomicReference<PyrisAccessContextDTO> fromLectureSearch = new AtomicReference<>();
        AtomicReference<PyrisAccessContextDTO> fromAnswer = new AtomicReference<>();
        // Declare both expectations up front; MockRestServiceServer matches them in declared order.
        irisRequestMockProvider.mockSearchLectures(List.of(), dto -> fromLectureSearch.set(dto.accessContext()));
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> fromAnswer.set(dto.accessContext()));

        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("backpropagation", 5, null), PyrisLectureSearchResultDTO.class,
                HttpStatus.OK);
        request.postWithoutResponseBody("/api/iris/search-answer", new GlobalSearchAskRequestDTO("backpropagation", 5, UUID.randomUUID()), HttpStatus.ACCEPTED);

        PyrisAccessContextDTO lectureContext = fromLectureSearch.get();
        PyrisAccessContextDTO answerContext = fromAnswer.get();
        assertThat(lectureContext).isNotNull();
        assertThat(answerContext).isNotNull();
        // Both endpoints resolve the same user through the same service, so the scoping they forward must agree;
        // otherwise the list results and the answer sources could enforce different access rights for one user.
        assertThat(orEmpty(answerContext.courseIds())).containsExactlyInAnyOrderElementsOf(orEmpty(lectureContext.courseIds()));
        assertThat(orEmpty(answerContext.staffCourseIds())).containsExactlyInAnyOrderElementsOf(orEmpty(lectureContext.staffCourseIds()));
        assertThat(orEmpty(answerContext.studentCourseIds())).containsExactlyInAnyOrderElementsOf(orEmpty(lectureContext.studentCourseIds()));
        assertThat(answerContext.unrestricted()).isEqualTo(lectureContext.unrestricted());
    }

    // ==================== helpers ====================

    /**
     * Absent role lists are omitted on the wire ({@code @JsonInclude(NON_EMPTY)}) and deserialize as {@code null};
     * the access-context contract treats an absent list as empty, so tests apply the same interpretation.
     */
    private static List<Long> orEmpty(List<Long> ids) {
        return ids == null ? List.of() : ids;
    }

    private void sendGlobalSearchAnswerStatus(String jobId, PyrisGlobalSearchAnswerStatusUpdateDTO statusUpdate) throws Exception {
        var headers = new HttpHeaders(new LinkedMultiValueMap<>(Map.of(HttpHeaders.AUTHORIZATION, List.of(Constants.BEARER_PREFIX + jobId))));
        request.postWithoutResponseBody("/api/iris/internal/pipelines/global-search/runs/" + jobId + "/status", statusUpdate, HttpStatus.OK, headers);
    }
}
