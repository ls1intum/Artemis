package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisGlobalSearchAnswerStatusUpdateDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisRunState;

class IrisGlobalSearchIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "globalsearchit";

    @Autowired
    private AuthorizationCheckService authCheckService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    @BeforeEach
    void setupUsers() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        activateIrisGlobally();
    }

    /**
     * Enrolls the standard test student in the given courses. A requested course only reaches Pyris when the caller can access it.
     */
    private void enrollStudent1(Course... courses) {
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        for (Course course : courses) {
            userUtilService.enrollUserInCourse(student, course, CourseRole.STUDENT);
        }
    }

    // ==================== /api/iris/lecture-search (synchronous) ====================

    /**
     * The query is processed by AI models in Pyris, so content search needs the same AI opt-in as the Iris answer.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "nooptin", roles = "USER")
    void search_withoutAiOptIn_shouldBeForbidden() throws Exception {
        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "nooptin");
        userUtilService.setAiSelectionDecision(user, AiSelectionDecision.NO_AI);

        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("machine learning", 5, null, null), PyrisLectureSearchResultDTO.class,
                HttpStatus.FORBIDDEN);
    }

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
        enrollStudent1(courseUtilService.createCourse());
        irisRequestMockProvider.mockSearchLectures(List.of());

        var requestDTO = new GlobalSearchLectureRequestDTO("nonexistent topic", 5, null, null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_shouldForwardExactServerUrlAsInstanceIdentity() throws Exception {
        enrollStudent1(courseUtilService.createCourse());
        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("machine learning", 5, null, null), PyrisLectureSearchResultDTO.class,
                HttpStatus.OK);

        assertThat(sent.get().artemisBaseUrl()).isEqualTo(artemisBaseUrl);
    }

    /**
     * Switching Iris off in a course only switches off the Iris chat and the other interactive Iris features. Content search is search, so it
     * still covers the course.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenIrisIsDisabledForARequestedCourse_shouldStillSearchIt() throws Exception {
        var enabledCourse = courseUtilService.createCourse();
        var disabledCourse = courseUtilService.createCourse();
        enrollStudent1(enabledCourse, disabledCourse);
        enableIrisFor(enabledCourse);
        disableIrisFor(disabledCourse);
        irisRequestMockProvider.mockSearchLectures(List.of(), List.of(enabledCourse.getId(), disabledCourse.getId()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(enabledCourse.getId(), disabledCourse.getId()), null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
        irisRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "allirisoff", roles = "USER")
    void search_whenUnscopedAndEveryAccessibleCourseHasIrisDisabled_shouldSearchThoseCourses() throws Exception {
        var firstDisabledCourse = courseUtilService.addEmptyCourse();
        var secondDisabledCourse = courseUtilService.addEmptyCourse();
        disableIrisFor(firstDisabledCourse);
        disableIrisFor(secondDisabledCourse);

        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "allirisoff");
        userUtilService.enrollUserInCourse(user, firstDisabledCourse, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, secondDisabledCourse, CourseRole.STUDENT);

        AtomicReference<List<Long>> forwardedCourseIds = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), dto -> forwardedCourseIds.set(dto.courseIds()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, null, null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(forwardedCourseIds.get()).contains(firstDisabledCourse.getId(), secondDisabledCourse.getId());
    }

    /**
     * The Iris answer is an interactive Iris feature, like the chat it can be continued in, so it must not draw on a course whose instructor
     * switched Iris off. Switching Iris off does not remove what was already ingested, so the scope has to be narrowed on the way out.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_whenIrisIsDisabledForTheOnlyRequestedCourse_shouldNotReachPyris() throws Exception {
        var course = courseUtilService.createCourse();
        enrollStudent1(course);
        disableIrisFor(course);

        var requestDTO = new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID(), List.of(course.getId()), null);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.FORBIDDEN);
    }

    /**
     * An unscoped answer request sends no course filter. Pyris then falls back to the access context, which is built from course roles and knows
     * nothing about Iris settings, so the scope has to be narrowed here.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "unscoped", roles = "USER")
    void ask_whenUnscopedAndAnAccessibleCourseHasIrisDisabled_shouldNotForwardThatCourse() throws Exception {
        var enabledCourse = courseUtilService.addEmptyCourse();
        var disabledCourse = courseUtilService.addEmptyCourse();
        enableIrisFor(enabledCourse);
        disableIrisFor(disabledCourse);

        User user = userUtilService.createAndSaveUser(TEST_PREFIX + "unscoped");
        userUtilService.enrollUserInCourse(user, enabledCourse, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(user, disabledCourse, CourseRole.STUDENT);
        userUtilService.setAiSelectionDecision(user, AiSelectionDecision.CLOUD_AI);

        AtomicReference<List<Long>> forwardedCourseIds = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(dto -> forwardedCourseIds.set(dto.courseIds()));

        request.postWithoutResponseBody("/api/iris/search-answer", new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID()), HttpStatus.ACCEPTED);

        assertThat(forwardedCourseIds.get()).as("an unscoped answer must carry an explicit, Iris-enabled scope instead of null").isNotNull().contains(enabledCourse.getId())
                .doesNotContain(disabledCourse.getId());
    }

    /**
     * A course hidden by a chip must not be searched. Every caller that travels with a course ceiling has its exclusions subtracted here, so Pyris is never told the course
     * exists.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenARequestedCourseIsExcluded_shouldNotForwardThatCourse() throws Exception {
        var searchedCourse = courseUtilService.createCourse();
        var hiddenCourse = courseUtilService.createCourse();
        enrollStudent1(searchedCourse, hiddenCourse);

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
        enrollStudent1(hiddenCourse);

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
     * An unscoped administrator travels without a course list, so the courses with Iris switched off cannot be filtered out of one. They have to
     * travel as exclusions instead, or the Iris answer would draw on them.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void ask_asAdminUnscoped_shouldExcludeTheCoursesWithIrisDisabled() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX);
        userUtilService.setAiSelectionDecision(userUtilService.getUserByLogin(TEST_PREFIX + "admin"), AiSelectionDecision.CLOUD_AI);
        var enabledCourse = courseUtilService.createCourse();
        var disabledCourse = courseUtilService.createCourse();
        enableIrisFor(enabledCourse);
        disableIrisFor(disabledCourse);

        AtomicReference<PyrisGlobalSearchAnswerRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(sent::set);

        request.postWithoutResponseBody("/api/iris/search-answer", new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID()), HttpStatus.ACCEPTED);

        assertThat(sent.get().courseIds()).as("an unrestricted caller keeps its no-ceiling search").isNull();
        assertThat(sent.get().excludeCourseIds()).contains(disabledCourse.getId()).doesNotContain(enabledCourse.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void search_asAdminUnscoped_shouldNotExcludeTheCoursesWithIrisDisabled() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX);
        var disabledCourse = courseUtilService.createCourse();
        disableIrisFor(disabledCourse);

        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("machine learning", 5, null, null), PyrisLectureSearchResultDTO.class,
                HttpStatus.OK);

        assertThat(sent.get().excludeCourseIds()).isNullOrEmpty();
    }

    /**
     * A course that never saved Iris settings has no row at all, and the default settings enable Iris. Narrowing must therefore drop only the courses that were explicitly
     * switched off, otherwise content search would silently stop working for every course that never opened the Iris settings page.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenTheCourseHasNoIrisSettingsRow_shouldForwardTheCourse() throws Exception {
        var course = courseUtilService.createCourse();
        enrollStudent1(course);
        irisRequestMockProvider.mockSearchLectures(List.of(), List.of(course.getId()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(course.getId()), null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        irisRequestMockProvider.verify();
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
        enrollStudent1(courseUtilService.createCourse());
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
        var filteredCourse = courseUtilService.createCourse();
        enrollStudent1(filteredCourse);
        var filteredCourseId = filteredCourse.getId();
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

    /**
     * The course filter is client input, so a requested course the caller cannot access is dropped before the request is built, and Pyris is
     * never asked to search it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenARequestedCourseIsNotAccessible_shouldNotForwardThatCourse() throws Exception {
        var accessibleCourse = courseUtilService.createCourse();
        var inaccessibleCourse = courseUtilService.createCourse();
        enrollStudent1(accessibleCourse);

        AtomicReference<PyrisLectureSearchRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockSearchLectures(List.of(), sent::set);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(accessibleCourse.getId(), inaccessibleCourse.getId()), null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(sent.get().courseIds()).containsExactly(accessibleCourse.getId());
    }

    /**
     * Dropping every requested course leaves nothing to search. An empty list is omitted on the wire and read by Pyris as unscoped, which would
     * search every course the caller can access instead, so the answer is given here without asking Pyris.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenNoRequestedCourseIsAccessible_shouldNotReachPyris() throws Exception {
        var accessibleCourse = courseUtilService.createCourse();
        var inaccessibleCourse = courseUtilService.createCourse();
        enrollStudent1(accessibleCourse);

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(inaccessibleCourse.getId()), null);
        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * A caller without any course has nothing to search. The empty list is not forwarded: it is omitted on the wire and Pyris would have to
     * narrow the search itself.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenUnscopedAndTheCallerHasNoCourse_shouldNotReachPyris() throws Exception {
        courseUtilService.createCourse();

        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search",
                new GlobalSearchLectureRequestDTO("machine learning", 5, null, null), PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        assertThat(response).isEmpty();
    }

    /**
     * The course lists are client input: an entry without an id is refused instead of failing while the lists are compared.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_withANullCourseId_shouldReturnBadRequest() throws Exception {
        var courseIdsWithNull = Arrays.asList(1L, null);

        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("machine learning", 5, courseIdsWithNull, null),
                PyrisLectureSearchResultDTO.class, HttpStatus.BAD_REQUEST);
        request.postListWithResponseBody("/api/iris/lecture-search", new GlobalSearchLectureRequestDTO("machine learning", 5, null, courseIdsWithNull),
                PyrisLectureSearchResultDTO.class, HttpStatus.BAD_REQUEST);
    }

    /**
     * A course id that does not exist is just as inaccessible as one the caller is not enrolled in.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void search_whenARequestedCourseDoesNotExist_shouldNotReachPyris() throws Exception {
        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(Long.MAX_VALUE), null);

        List<PyrisLectureSearchResultDTO> response = request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

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

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(taCourse.getId(), instructorCourse.getId()), null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        irisRequestMockProvider.verify();
    }

    /**
     * An unrestricted caller lists no courses in its access context, but it is not limited to any, so the courses it asked for are kept.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void search_asAdminWithRequestedCourses_shouldForwardThem() throws Exception {
        userUtilService.addAdmin(TEST_PREFIX);
        var firstCourse = courseUtilService.createCourse();
        var secondCourse = courseUtilService.createCourse();
        irisRequestMockProvider.mockSearchLectures(List.of(), List.of(firstCourse.getId(), secondCourse.getId()));

        var requestDTO = new GlobalSearchLectureRequestDTO("machine learning", 5, List.of(firstCourse.getId(), secondCourse.getId()), null);
        request.postListWithResponseBody("/api/iris/lecture-search", requestDTO, PyrisLectureSearchResultDTO.class, HttpStatus.OK);

        irisRequestMockProvider.verify();
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
    void ask_shouldPreserveExactServerUrlInPipelineSettings() throws Exception {
        AtomicReference<PyrisGlobalSearchAnswerRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(sent::set);

        request.postWithoutResponseBody("/api/iris/search-answer", new GlobalSearchAskRequestDTO("What is backpropagation?", 5, UUID.randomUUID()), HttpStatus.ACCEPTED);

        assertThat(sent.get().settings().artemisBaseUrl()).isEqualTo(artemisBaseUrl);
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

    /**
     * Both course lists are client-controlled, and the access filter performs a repository lookup and role check
     * per included id. The endpoint refuses an oversized list instead, mirroring /lecture-search.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_withMoreCourseIdsThanAllowed_shouldReturnBadRequest() throws Exception {
        var tooManyCourseIds = LongStream.rangeClosed(1, GlobalSearchLectureRequestDTO.MAX_COURSE_ID_FILTERS + 1).boxed().toList();

        request.postWithoutResponseBody("/api/iris/search-answer", new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID(), tooManyCourseIds, null),
                HttpStatus.BAD_REQUEST);
        request.postWithoutResponseBody("/api/iris/search-answer", new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID(), null, tooManyCourseIds),
                HttpStatus.BAD_REQUEST);
    }

    /**
     * The Iris answer builds its scope with the same rules as the content search, so a requested course the caller cannot access is dropped from
     * the course list sent to Pyris.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_whenARequestedCourseIsNotAccessible_shouldNotForwardThatCourse() throws Exception {
        var accessibleCourse = courseUtilService.createCourse();
        var inaccessibleCourse = courseUtilService.createCourse();
        enrollStudent1(accessibleCourse);

        AtomicReference<PyrisGlobalSearchAnswerRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(sent::set);

        var requestDTO = new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID(), List.of(accessibleCourse.getId(), inaccessibleCourse.getId()), null);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        assertThat(sent.get().courseIds()).containsExactly(accessibleCourse.getId());
        assertThat(sent.get().searchesNothing()).isFalse();
    }

    /**
     * Nothing left to search is reported as such instead of an empty list, which Pyris would read as unscoped.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void ask_whenNoRequestedCourseIsAccessible_shouldAskPyrisToSearchNothing() throws Exception {
        var accessibleCourse = courseUtilService.createCourse();
        var inaccessibleCourse = courseUtilService.createCourse();
        enrollStudent1(accessibleCourse);

        AtomicReference<PyrisGlobalSearchAnswerRequestDTO> sent = new AtomicReference<>();
        irisRequestMockProvider.mockGlobalSearchIrisAnswer(sent::set);

        var requestDTO = new GlobalSearchAskRequestDTO("machine learning", 5, UUID.randomUUID(), List.of(inaccessibleCourse.getId()), null);
        request.postWithoutResponseBody("/api/iris/search-answer", requestDTO, HttpStatus.ACCEPTED);

        assertThat(sent.get().searchesNothing()).isTrue();
        assertThat(sent.get().courseIds()).isNull();
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
        var course = courseUtilService.createCourse();
        enrollStudent1(course);
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
        assertThat(orEmpty(lectureContext.courseIds())).containsExactly(course.getId());
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
