package de.tum.cit.aet.artemis.tutorialgroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.dto.CreateOrUpdateTutorialGroupSessionDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionDTO;

class TutorialGroupSessionIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    private static final String TEST_PREFIX = "tutorialgroupsession";

    private static final LocalTime SESSION_START_10_00 = LocalTime.of(10, 0);

    private static final LocalTime SESSION_END_12_00 = LocalTime.of(12, 0);

    private static final String SESSION_LOCATION = "01.05.12";

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    private User firstCourseTutor1;

    private User firstCourseTutor2;

    private TutorialGroup firstCourseTutorialGroup1;

    private List<TutorialGroupSession> firstCourseTutorialGroup1Sessions;

    private User secondCourseTutor1;

    private TutorialGroup secondCourseTutorialGroup1;

    private List<TutorialGroupSession> secondCourseTutorialGroup1Sessions;

    @BeforeEach
    @Override
    void setupTestScenario() {
        super.setupTestScenario();

        TestCourseOneUsers testCourseOneUsers = createAndSaveTestCourseOneUsers();
        firstCourseTutor1 = testCourseOneUsers.tutor1();
        firstCourseTutor2 = testCourseOneUsers.tutor2();
        var firstCourseStudent1 = testCourseOneUsers.student1();
        var firstCourseStudent2 = testCourseOneUsers.student2();

        TestTutorialGroupOneData testTutorialGroupOneData = createAndSaveTestTutorialGroupOneData(firstCourseTutor1, firstCourseStudent1);
        firstCourseTutorialGroup1 = testTutorialGroupOneData.group();
        firstCourseTutorialGroup1Sessions = testTutorialGroupOneData.sessions();
        TestTutorialGroupTwoData testTutorialGroupTwoData = createAndSaveTestTutorialGroupTwoData(firstCourseTutor2, firstCourseStudent2);

        TestCourseTwoUsers testCourseTwoUsers = createAndSaveTestCourseTwoUsers();
        secondCourseTutor1 = testCourseTwoUsers.tutor();

        TestTutorialGroupThreeData testTutorialGroupThreeData = createAndSaveTestTutorialGroupThreeData(secondCourseTutor1);
        secondCourseTutorialGroup1 = testTutorialGroupThreeData.group();
        secondCourseTutorialGroup1Sessions = testTutorialGroupThreeData.sessions();
    }

    @Nested
    class CreateSessionTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/sessions", tutorialGroupSessionDTO, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void createSession_asTutorOfOtherGroup_shouldReturnAccessForbidden() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void createSession_asEditorOfOtherCourse_shouldReturnAccessForbidden() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithSessionWithStartNotBeforeEnd_shouldReturnBadRequest() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_END_12_00,
                    SESSION_START_10_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithoutConfiguration_shouldReturnBadRequest() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + secondCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithoutTimeZone_shouldReturnBadRequest() throws Exception {
            Course course = courseRepository.findByIdElseThrow(exampleCourseId);
            course.setTimeZone(null);
            courseRepository.save(course);

            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithNonMatchingCourse_shouldReturnBadRequest() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + secondCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithNonMatchingTutorialGroup_shouldReturnBadRequest() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + secondCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithOverlappingSession_shouldReturnBadRequest() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_AUGUST_MONDAY, LocalTime.of(13, 30),
                    LocalTime.of(14, 30), SESSION_LOCATION, null);
            request.postWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions",
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithoutOverlappingFreePeriod_shouldReturnCreated() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            TutorialGroupSessionDTO sessionDTO = request.postWithResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions", tutorialGroupSessionDTO,
                    TutorialGroupSessionDTO.class, HttpStatus.CREATED);

            assertThat(sessionDTO.id()).isNotNull();
            assertThat(sessionDTO.start()).isEqualTo(getExampleSessionStartOnDate(FIRST_SEPTEMBER_MONDAY));
            assertThat(sessionDTO.end()).isEqualTo(getExampleSessionEndOnDate(FIRST_SEPTEMBER_MONDAY));
            assertThat(sessionDTO.location()).isEqualTo(SESSION_LOCATION);
            assertThat(sessionDTO.isCancelled()).isFalse();
            assertThat(sessionDTO.locationChanged()).isTrue();
            assertThat(sessionDTO.timeChanged()).isTrue();
            assertThat(sessionDTO.dateChanged()).isTrue();
            assertThat(sessionDTO.attendanceCount()).isNull();
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void createSession_asTutorOfGroupWithOverlappingFreePeriod_shouldReturnCreated() throws Exception {
            tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_SEPTEMBER_MONDAY_10_00, FIRST_SEPTEMBER_MONDAY_12_00, "Holiday");
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            TutorialGroupSessionDTO sessionDTO = request.postWithResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions", tutorialGroupSessionDTO,
                    TutorialGroupSessionDTO.class, HttpStatus.CREATED);

            assertThat(sessionDTO.id()).isNotNull();
            assertThat(sessionDTO.start()).isEqualTo(getExampleSessionStartOnDate(FIRST_SEPTEMBER_MONDAY));
            assertThat(sessionDTO.end()).isEqualTo(getExampleSessionEndOnDate(FIRST_SEPTEMBER_MONDAY));
            assertThat(sessionDTO.location()).isEqualTo(SESSION_LOCATION);
            assertThat(sessionDTO.isCancelled()).isTrue();
            assertThat(sessionDTO.locationChanged()).isTrue();
            assertThat(sessionDTO.timeChanged()).isTrue();
            assertThat(sessionDTO.dateChanged()).isTrue();
            assertThat(sessionDTO.attendanceCount()).isNull();
        }
    }

    @Nested
    class UpdateSessionTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/sessions/" + session.getId(), tutorialGroupSessionDTO,
                    HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void updateSession_asTutorOfOtherGroup_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.putWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void updateSession_asEditorOfOtherCourse_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.putWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithoutExistingSession_shouldReturnNotFound() throws Exception {
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.putWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/-1",
                    tutorialGroupSessionDTO, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithNonMatchingGroup_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.putWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + secondCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithNonMatchingCourse_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.putWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithoutConfiguration_shouldReturnBadRequest() throws Exception {
            var session = secondCourseTutorialGroup1Sessions.getFirst();
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_AUGUST_MONDAY, SESSION_START_10_00, SESSION_END_12_00,
                    SESSION_LOCATION, null);
            request.putWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + secondCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithoutTimeZone_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            Course course = courseRepository.findByIdElseThrow(exampleCourseId);
            course.setTimeZone(null);
            courseRepository.save(course);

            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            request.putWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithOverlappingSession_shouldReturnBadRequest() throws Exception {
            var firstSession = firstCourseTutorialGroup1Sessions.getFirst();
            var secondSession = firstCourseTutorialGroup1Sessions.get(1);
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(secondSession.getStart().toLocalDate(),
                    secondSession.getStart().toLocalTime(), secondSession.getEnd().toLocalTime(), secondSession.getLocation(), null);
            request.putWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + firstSession.getId(),
                    tutorialGroupSessionDTO, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithoutOverlappingFreePeriod_shouldReturnOk() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            TutorialGroupSessionDTO sessionDTO = request.putWithResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, TutorialGroupSessionDTO.class, HttpStatus.OK);

            assertThat(sessionDTO.id()).isEqualTo(session.getId());
            assertThat(sessionDTO.start()).isEqualTo(ZonedDateTime.of(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00, ZoneId.of(exampleTimeZone)));
            assertThat(sessionDTO.end()).isEqualTo(ZonedDateTime.of(FIRST_SEPTEMBER_MONDAY, SESSION_END_12_00, ZoneId.of(exampleTimeZone)));
            assertThat(sessionDTO.location()).isEqualTo(SESSION_LOCATION);
            assertThat(sessionDTO.isCancelled()).isFalse();
            assertThat(sessionDTO.locationChanged()).isTrue();
            assertThat(sessionDTO.timeChanged()).isTrue();
            assertThat(sessionDTO.dateChanged()).isFalse();
            assertThat(sessionDTO.attendanceCount()).isNull();
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void updateSession_asTutorOfGroupWithOverlappingFreePeriod_shouldReturnOk() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_SEPTEMBER_MONDAY_10_00, FIRST_SEPTEMBER_MONDAY_12_00, "Holiday");
            CreateOrUpdateTutorialGroupSessionDTO tutorialGroupSessionDTO = new CreateOrUpdateTutorialGroupSessionDTO(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00,
                    SESSION_END_12_00, SESSION_LOCATION, null);
            TutorialGroupSessionDTO sessionDTO = request.putWithResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    tutorialGroupSessionDTO, TutorialGroupSessionDTO.class, HttpStatus.OK);

            assertThat(sessionDTO.id()).isEqualTo(session.getId());
            assertThat(sessionDTO.start()).isEqualTo(ZonedDateTime.of(FIRST_SEPTEMBER_MONDAY, SESSION_START_10_00, ZoneId.of(exampleTimeZone)));
            assertThat(sessionDTO.end()).isEqualTo(ZonedDateTime.of(FIRST_SEPTEMBER_MONDAY, SESSION_END_12_00, ZoneId.of(exampleTimeZone)));
            assertThat(sessionDTO.location()).isEqualTo(SESSION_LOCATION);
            assertThat(sessionDTO.isCancelled()).isTrue();
            assertThat(sessionDTO.locationChanged()).isTrue();
            assertThat(sessionDTO.timeChanged()).isTrue();
            assertThat(sessionDTO.dateChanged()).isFalse();
            assertThat(sessionDTO.attendanceCount()).isNull();
        }
    }

    @Nested
    class DeleteSessionTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void deleteSession_asTutorOfGroupWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/sessions/" + session.getId(), HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void deleteSession_asTutorOfOtherGroup_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void deleteSession_asEditorOfOtherCourse_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void deleteSession_asTutorOfGroupWithoutExistingSession_shouldReturnNotFound() throws Exception {
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/-1", HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void deleteSession_asTutorOfGroupWithNonMatchingGroup_shouldReturnBadRequest() throws Exception {
            var session = secondCourseTutorialGroup1Sessions.getFirst();
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void deleteSession_asTutorOfGroupWithNonMatchingCourse_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void deleteSession_asTutorOfGroup_shouldReturnNoContent() throws Exception {
            var session = buildAndSaveExampleIndividualTutorialGroupSession(firstCourseTutorialGroup1.getId(), FIRST_SEPTEMBER_MONDAY_00_00);
            request.delete("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId(),
                    HttpStatus.NO_CONTENT);
            assertThat(tutorialGroupSessionRepository.existsById(session.getId())).isFalse();
        }
    }

    @Nested
    class CancelSessionTests {

    }

    @Nested
    class ActivateSessionTests {

    }

    /*
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
     * void getOneOfCourse_asUser_shouldReturnTutorialGroupSession() throws Exception {
     * // given
     * var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * // when
     * var sessionFromRequest = request.get(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, session.getId()), HttpStatus.OK, TutorialGroupSession.class);
     * // then
     * assertThat(sessionFromRequest).isEqualTo(session);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(session.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void createNewSession_asInstructor_shouldCreateSession() throws Exception {
     * createNewSessionAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
     * void createNewSession_asTutorOfGroup_shouldCreateSession() throws Exception {
     * createNewSessionAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
     * void createNewSession_asNotTutorOfGroup_shouldForbidSession() throws Exception {
     * createNewSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITO")
     * void createNewSession_asEditor_shouldForbidSession() throws Exception {
     * createNewSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void createNewSession_overlapsWithExistingSession_shouldReturnBadRequest() throws Exception {
     * // given
     * var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * var dto = createSessionDTO(AUGUST_FIRST_MONDAY);
     * // when
     * request.postWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.BAD_REQUEST);
     * // then
     * assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(exampleTutorialGroupId)).containsExactly(session);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(session.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void createNewSession_onTutorialGroupFreeDay_shouldCreateAsCancelled() throws Exception {
     * // given
     * tutorialGroupUtilService.addTutorialGroupFreePeriod(configurationId, AUGUST_FIRST_MONDAY_00_00, AUGUST_FIRST_MONDAY_23_59, "Holiday");
     * var dto = createSessionDTO(AUGUST_FIRST_MONDAY);
     * // when
     * var sessionId = request.postWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.CREATED).getId();
     * // then
     * var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
     * assertThat(persistedSession.getTutorialGroupFreePeriod()).isNotNull();
     * assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
     * assertIndividualSessionIsCancelledOnDate(persistedSession, AUGUST_FIRST_MONDAY_00_00, exampleTutorialGroupId, null);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
     * void updateSession_scheduledSession_shouldBeDisconnectedFromSchedule() throws Exception {
     * // given
     * userUtilService.changeUser(testPrefix + "instructor1");
     * TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.courseId, "tutor1");
     * userUtilService.changeUser(testPrefix + "tutor1");
     * var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroup.getId()).orElseThrow();
     * var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
     * assertThat(sessions).hasSize(2);
     * var firstAugustMondaySession = sessions.getFirst();
     * var secondAugustMondaySession = sessions.get(1);
     * this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, AUGUST_FIRST_MONDAY, tutorialGroup.getId(), persistedSchedule);
     * this.assertScheduledSessionIsActiveOnDate(secondAugustMondaySession, AUGUST_SECOND_MONDAY, tutorialGroup.getId(), persistedSchedule);
     * var dto = createSessionDTO(AUGUST_FOURTH_MONDAY);
     * // when
     * // change first august monday session to fourth monday august session
     * var updatedSessionId = request
     * .putWithResponseBody(getSessionsPathOfTutorialGroup(tutorialGroup.getId(), firstAugustMondaySession.getId()), dto, TutorialGroupSession.class, HttpStatus.OK)
     * .getId();
     * // then
     * var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
     * assertThat(updatedSession.getId()).isEqualTo(firstAugustMondaySession.getId());
     * this.assertIndividualSessionIsActiveOnDate(updatedSession, AUGUST_FOURTH_MONDAY_00_00, tutorialGroup.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void updateSession_individualSession_shouldStillBeDisconnectedFromSchedule() throws Exception {
     * // given
     * var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * var dto = createSessionDTO(AUGUST_SECOND_MONDAY);
     * // when
     * // change first august monday session to second monday august session
     * var updatedSessionId = request.putWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, session.getId()), dto, TutorialGroupSession.class, HttpStatus.OK)
     * .getId();
     * // then
     * var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
     * assertThat(updatedSession.getId()).isEqualTo(session.getId());
     * session = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
     * assertIndividualSessionIsActiveOnDate(session, AUGUST_SECOND_MONDAY_00_00, exampleTutorialGroupId);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(session.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void updateSession_nowOverlapsWithOtherSession_shouldReturnBadRequest() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * var secondAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_SECOND_MONDAY_00_00);
     * var dto = createSessionDTO(AUGUST_SECOND_MONDAY);
     * // when
     * // change first august monday session to second monday august session
     * request.putWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()), dto, TutorialGroupSession.class,
     * HttpStatus.BAD_REQUEST);
     * // then
     * var sessions = this.getTutorialGroupSessionsAscending(exampleTutorialGroupId);
     * assertThat(sessions).containsExactly(firstAugustMondaySession, secondAugustMondaySession);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * tutorialGroupSessionRepository.deleteById(secondAugustMondaySession.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void updateSession_nowOverlapsWithPreviousTimeOfSameSession_shouldUpdateCorrectly() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * var dto = createSessionDTO(AUGUST_FIRST_MONDAY, LocalTime.of(this.defaultSessionStartHour - 1, 0, 0), LocalTime.of(this.defaultSessionEndHour + 1, 0, 0));
     * // when
     * var updatedSessionId = request
     * .putWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()), dto, TutorialGroupSession.class, HttpStatus.OK)
     * .getId();
     * // then
     * assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());
     * var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
     * assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
     * assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void updateSession_wasCancelled_shouldNowBeActiveAgain() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * firstAugustMondaySession.setStatusExplanation("Cancelled");
     * firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
     * tutorialGroupSessionRepository.save(firstAugustMondaySession);
     * var dto = createSessionDTO(AUGUST_THIRD_MONDAY);
     * // when
     * var updatedSessionId = request
     * .putWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()), dto, TutorialGroupSession.class, HttpStatus.OK)
     * .getId();
     * assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());
     * // then
     * var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
     * assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
     * assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
     * assertIndividualSessionIsActiveOnDate(updatedSession, AUGUST_THIRD_MONDAY_00_00, exampleTutorialGroupId);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void updateSession_nowOnTutorialGroupFreeDay_shouldUpdateAsCancelled() throws Exception {
     * // given
     * var freeDay = tutorialGroupUtilService.addTutorialGroupFreePeriod(configurationId, AUGUST_THIRD_MONDAY_00_00, AUGUST_THIRD_MONDAY_23_59, "Holiday");
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * var dto = createSessionDTO(AUGUST_THIRD_MONDAY);
     * // when
     * var updatedSessionId = request
     * .putWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()), dto, TutorialGroupSession.class, HttpStatus.OK)
     * .getId();
     * assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());
     * // then
     * var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
     * assertThat(updatedSession.getTutorialGroupFreePeriod()).isNotNull();
     * assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
     * assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
     * assertIndividualSessionIsCancelledOnDate(updatedSession, AUGUST_THIRD_MONDAY_00_00, exampleTutorialGroupId, null);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * tutorialGroupFreePeriodRepository.deleteById(freeDay.getId());
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
     * void updateSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
     * updateSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
     * void updateSession_asEditor_shouldReturnForbidden() throws Exception {
     * updateSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
     * void deleteSession_individualSession_shouldBeDeleted() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isTrue();
     * // when
     * request.delete(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()), HttpStatus.NO_CONTENT);
     * // then
     * assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isFalse();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void deleteSession_scheduledSession_shouldBeDeleted() throws Exception {
     * // given
     * TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.courseId, "tutor1");
     * var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
     * assertThat(sessions).hasSize(2);
     * var firstAugustMondaySession = sessions.getFirst();
     * assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isTrue();
     * // when
     * request.delete(getSessionsPathOfTutorialGroup(tutorialGroup.getId(), firstAugustMondaySession.getId()), HttpStatus.NO_CONTENT);
     * // then
     * assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isFalse();
     * sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
     * assertThat(sessions).hasSize(1);
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
     * void deleteSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
     * deleteSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
     * void deleteSession_asEditor_shouldReturnForbidden() throws Exception {
     * deleteSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void cancelSession_asInstructor_shouldCancelSession() throws Exception {
     * cancelSessionAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
     * void cancelSession_asTutorOfGroup_shouldCancelSession() throws Exception {
     * cancelSessionAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
     * void cancelSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
     * cancelSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
     * void cancelSession_asEditor_shouldReturnForbidden() throws Exception {
     * cancelSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
     * void activateCancelledSession_asInstructor_shouldActivateSession() throws Exception {
     * activateSessionAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
     * void activateCancelledSession_asTutorOfGroup_shouldActivateSession() throws Exception {
     * activateSessionAllowedTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
     * void activateCancelledSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
     * activateSessionForbiddenTest();
     * }
     * @Test
     * @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
     * void activateCancelledSession_asEditor_shouldReturnForbidden() throws Exception {
     * activateSessionForbiddenTest();
     * }
     * private void activateSessionAllowedTest() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * firstAugustMondaySession.setStatusExplanation("Cancelled");
     * firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
     * tutorialGroupSessionRepository.save(firstAugustMondaySession);
     * // when
     * request.postWithoutLocation(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()) + "/activate", null, HttpStatus.OK, null);
     * // then
     * var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(firstAugustMondaySession.getId());
     * assertIndividualSessionIsActiveOnDate(updatedSession, AUGUST_FIRST_MONDAY_00_00, exampleTutorialGroupId);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     * private void activateSessionForbiddenTest() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * firstAugustMondaySession.setStatusExplanation("Cancelled");
     * firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
     * tutorialGroupSessionRepository.save(firstAugustMondaySession);
     * // when
     * request.postWithoutLocation(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()) + "/activate", null, HttpStatus.FORBIDDEN, null);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     * private CreateOrUpdateTutorialGroupSessionDTO createSessionDTO(LocalDate date) {
     * return new CreateOrUpdateTutorialGroupSessionDTO(date, LocalTime.of(defaultSessionStartHour, 0, 0), LocalTime.of(defaultSessionEndHour, 0, 0), "LoremIpsum", null);
     * }
     * private CreateOrUpdateTutorialGroupSessionDTO createSessionDTO(LocalDate date, LocalTime startTime, LocalTime endTime) {
     * return new CreateOrUpdateTutorialGroupSessionDTO(date, startTime, endTime, "LoremIpsum", null);
     * }
     * private void assertSessionCreatedCorrectlyFromDTO(TutorialGroupSession session, CreateOrUpdateTutorialGroupSessionDTO dto) {
     * assertThat(session.getStart()).isEqualTo(ZonedDateTime.of(dto.date(), dto.startTime(), ZoneId.of(this.timeZone)));
     * assertThat(session.getEnd()).isEqualTo(ZonedDateTime.of(dto.date(), dto.endTime(), ZoneId.of(this.timeZone)));
     * assertThat(session.getLocation()).isEqualTo(dto.location());
     * assertThat(session.getTutorialGroupSchedule()).isNull(); // individual session so not connected to a schedule
     * }
     * private void updateSessionForbiddenTest() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * var dto = createSessionDTO(AUGUST_THIRD_MONDAY);
     * // when
     * request.put(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()), dto, HttpStatus.FORBIDDEN);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     * private void createNewSessionAllowedTest() throws Exception {
     * // given
     * var dto = createSessionDTO(AUGUST_FIRST_MONDAY);
     * // when
     * var sessionId = request.postWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.CREATED).getId();
     * // then
     * var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
     * assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
     * assertIndividualSessionIsActiveOnDate(persistedSession, AUGUST_FIRST_MONDAY_00_00, exampleTutorialGroupId);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(persistedSession.getId());
     * }
     * private void createNewSessionForbiddenTest() throws Exception {
     * // given
     * var dto = createSessionDTO(AUGUST_FIRST_MONDAY);
     * // when
     * request.postWithResponseBody(getSessionsPathOfTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.FORBIDDEN);
     * }
     * private void deleteSessionForbiddenTest() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * // when
     * request.delete(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()), HttpStatus.FORBIDDEN);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     * private void cancelSessionAllowedTest() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * assertIndividualSessionIsActiveOnDate(firstAugustMondaySession, AUGUST_FIRST_MONDAY_00_00, exampleTutorialGroupId);
     * var statusDTO = new TutorialGroupCancelExplanationDTO("Holiday");
     * // when
     * request.postWithoutLocation(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()) + "/cancel", statusDTO, HttpStatus.OK, null);
     * // then
     * var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(firstAugustMondaySession.getId());
     * assertIndividualSessionIsCancelledOnDate(updatedSession, AUGUST_FIRST_MONDAY_00_00, exampleTutorialGroupId, "Holiday");
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     * private void cancelSessionForbiddenTest() throws Exception {
     * // given
     * var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, AUGUST_FIRST_MONDAY_00_00);
     * assertIndividualSessionIsActiveOnDate(firstAugustMondaySession, AUGUST_FIRST_MONDAY_00_00, exampleTutorialGroupId);
     * var statusDTO = new TutorialGroupCancelExplanationDTO("Holiday");
     * // when
     * request.postWithoutLocation(getSessionsPathOfTutorialGroup(exampleTutorialGroupId, firstAugustMondaySession.getId()) + "/cancel", statusDTO, HttpStatus.FORBIDDEN, null);
     * // cleanup
     * tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
     * }
     */
}
