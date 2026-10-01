package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
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

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
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

        var requestDTO = new GlobalSearchLectureRequestDTO("nonexistent topic", 5, null, null);
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

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(course.getId()), null);
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

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(enabledCourse.getId(), disabledCourse.getId()), null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * The normal Lectures selection sends no course filter at all. Pyris then falls back to the access context, which is
     * built from course roles and knows nothing about Iris settings, so an unscoped search has to be narrowed here or a
     * course whose instructor switched Iris off would still return its already-ingested content.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "unscoped", roles = "USER")
    void search_whenUnscopedAndAnAccessibleCourseHasIrisDisabled_shouldNotForwardThatCourse() throws Exception {
        var enabledCourse = courseUtilService.addEmptyCourse();
        var disabledCourse = courseUtilService.addEmptyCourse();
        enableIrisFor(enabledCourse);
        disableIrisFor(disabledCourse);

        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "unscoped");
        userUtilService.enrollUserInCourse(user, enabledCourse, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, disabledCourse, CourseRole.STUDENT);

        AtomicReference<List<Long>> forwardedCourseIds = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), dto -> forwardedCourseIds.set(dto.courseIds()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(forwardedCourseIds.get()).as("an unscoped search must carry an explicit, Iris-enabled scope instead of null").isNotNull().contains(enabledCourse.getId())
                .doesNotContain(disabledCourse.getId());
    }

    /**
     * When every accessible course has Iris switched off, the narrowed scope is empty. An empty list is omitted on the wire and Pyris reads an absent list as unscoped, which
     * would search exactly the disabled courses through the access context, so the request has to be refused before it reaches Pyris.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "allirisoff", roles = "USER")
    void search_whenUnscopedAndEveryAccessibleCourseHasIrisDisabled_shouldNotReachPyris() throws Exception {
        var firstDisabledCourse = courseUtilService.addEmptyCourse();
        var secondDisabledCourse = courseUtilService.addEmptyCourse();
        disableIrisFor(firstDisabledCourse);
        disableIrisFor(secondDisabledCourse);

        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "allirisoff");
        userUtilService.enrollUserInCourse(user, firstDisabledCourse, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, secondDisabledCourse, CourseRole.STUDENT);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.FORBIDDEN);
    }

    /**
     * A course hidden by a chip must not be searched. Every caller that travels with a course ceiling has its exclusions subtracted here, so Pyris is never told the course
     * exists: the exclusion and the Iris-settings narrowing act on the same list, one after the other.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenARequestedCourseIsExcluded_shouldNotForwardThatCourse() throws Exception {
        var searchedCourse = courseUtilService.createCourse();
        var hiddenCourse = courseUtilService.createCourse();

        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(searchedCourse.getId(), hiddenCourse.getId()), List.of(hiddenCourse.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(sent.get().courseIds()).containsExactly(searchedCourse.getId());
        assertThat(sent.get().excludeCourseIds()).as("a caller with a ceiling has its exclusions applied here, not by Pyris").isNull();
    }

    /**
     * The same for an unscoped search, where the ceiling is every course the caller can access rather than a requested list.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "excluding", roles = "USER")
    void search_whenUnscopedAndACourseIsExcluded_shouldNotForwardThatCourse() throws Exception {
        var searchedCourse = courseUtilService.addEmptyCourse();
        var hiddenCourse = courseUtilService.addEmptyCourse();
        enableIrisFor(searchedCourse);
        enableIrisFor(hiddenCourse);

        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "excluding");
        userUtilService.enrollUserInCourse(user, searchedCourse, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, hiddenCourse, CourseRole.STUDENT);

        AtomicReference<List<Long>> forwardedCourseIds = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), dto -> forwardedCourseIds.set(dto.courseIds()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, List.of(hiddenCourse.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(forwardedCourseIds.get()).contains(searchedCourse.getId()).doesNotContain(hiddenCourse.getId());
    }

    /**
     * Hiding every course in scope leaves nothing to search. The narrowed list would be empty, and an empty list is omitted on the wire and read by Pyris as unscoped, so
     * forwarding it would search exactly the courses the caller hid. The answer is given here instead.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenEveryCourseInScopeIsExcluded_shouldNotReachPyris() throws Exception {
        var hiddenCourse = courseUtilService.createCourse();

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(hiddenCourse.getId()), List.of(hiddenCourse.getId()));
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * An unrestricted caller is sent without a course ceiling, so there is no list to subtract the exclusion from. It is the one caller whose exclusions have to travel to
     * Pyris and be applied by the query itself.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void search_asAdminWithAnExclusion_shouldForwardTheExclusionToPyris() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX);
        var hiddenCourse = courseUtilService.createCourse();

        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, List.of(hiddenCourse.getId()));
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(sent.get().courseIds()).as("an unrestricted caller keeps its no-ceiling search").isNull();
        assertThat(sent.get().excludeCourseIds()).containsExactly(hiddenCourse.getId());
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

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(course.getId()), null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);
    }

    /**
     * Both course lists are client-controlled, and an unrestricted caller's exclusions travel on to Pyris, where an
     * unbounded list would become an unbounded query filter. The endpoint refuses an oversized list instead.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_withMoreCourseIdsThanAllowed_shouldReturnBadRequest() throws Exception {
        var tooManyCourseIds = LongStream.rangeClosed(1, GlobalSearchLectureRequestDTO.MAX_COURSE_ID_FILTERS + 1).boxed().toList();

        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("machine learning", 5, tooManyCourseIds, null),
                PyrisLectureSearchResultDTO.class, HttpStatus.BAD_REQUEST);
        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("machine learning", 5, null, tooManyCourseIds),
                PyrisLectureSearchResultDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenPyrisFails_shouldReturnInternalServerError() throws Exception {
        irisRequestMockProvider.mockSearchLecturesError(HttpStatus.INTERNAL_SERVER_ERROR);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void search_asUnauthenticated_shouldReturnUnauthorized() throws Exception {
        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
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

        var requestDTO = new GlobalSearchLectureRequestDTO("filtered query", 5, List.of(filteredCourseId), null);
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

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
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

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
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

        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("backpropagation", 5, null, null), PyrisLectureSearchResultDTO.class,
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
