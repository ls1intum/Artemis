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
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
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
            assertThat(sessionDTO.dateChanged()).isFalse();
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
            assertThat(sessionDTO.dateChanged()).isFalse();
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

        // TODO: create group with sessions generated according to schedule -> assert session no longer follows schedule on update
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

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void cancelSession_asTutorOfGroupWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/sessions/" + session.getId() + "/cancel", null,
                    HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void cancelSession_asTutorOfOtherGroup_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/cancel", null,
                    HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void cancelSession_asEditorOfOtherCourse_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/cancel", null,
                    HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void cancelSession_asTutorOfGroupWithoutExistingSession_shouldReturnNotFound() throws Exception {
            request.patchWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/-1/cancel",
                    null, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void cancelSession_asTutorOfGroupWithSessionOverlappingFreePeriod_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.get(1);
            var freePeriod = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_AUGUST_MONDAY_13_00, FIRST_AUGUST_MONDAY_14_00, "Holiday");
            session.setTutorialGroupFreePeriod(freePeriod);
            tutorialGroupSessionRepository.save(session);
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/cancel", null,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void cancelSession_asTutorOfGroupWithNonMatchingGroup_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + secondCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/cancel", null,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void cancelSession_asTutorOfGroupWithNonMatchingCourse_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/cancel", null,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void cancelSession_asTutorOfGroup_shouldReturnNoContent() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/cancel", null,
                    HttpStatus.NO_CONTENT);
            TutorialGroupSession updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
            assertThat(updatedSession.getStatus()).isEqualTo(TutorialGroupSessionStatus.CANCELLED);
        }
    }

    @Nested
    class ActivateSessionTests {

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void activateSession_asTutorOfGroupWithoutExistingGroup_shouldReturnNotFound() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/-1/sessions/" + session.getId() + "/activate", null,
                    HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR2_LOGIN, roles = "TA")
        void activateSession_asTutorOfOtherGroup_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/activate", null,
                    HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_EDITOR1_LOGIN, roles = "EDITOR")
        void activateSession_asEditorOfOtherCourse_shouldReturnAccessForbidden() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/activate", null,
                    HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void activateSession_asTutorOfGroupWithoutExistingSession_shouldReturnNotFound() throws Exception {
            request.patchWithoutResponseBody("/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/-1/activate",
                    null, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void activateSession_asTutorOfGroupWithSessionOverlappingFreePeriod_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            var freePeriod = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, FIRST_AUGUST_MONDAY_13_00, FIRST_AUGUST_MONDAY_14_00, "Holiday");
            session.setTutorialGroupFreePeriod(freePeriod);
            tutorialGroupSessionRepository.save(session);

            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/activate", null,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = SECOND_COURSE_TUTOR1_LOGIN, roles = "TA")
        void activateSession_asTutorOfGroupWithNonMatchingGroup_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + secondCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/activate",
                    null, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void activateSession_asTutorOfGroupWithNonMatchingCourse_shouldReturnBadRequest() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId2 + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/activate", null,
                    HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = FIRST_COURSE_TUTOR1_LOGIN, roles = "TA")
        void activateSession_asTutorOfGroup_shouldReturnNoContent() throws Exception {
            var session = firstCourseTutorialGroup1Sessions.getFirst();
            request.patchWithoutResponseBody(
                    "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-groups/" + firstCourseTutorialGroup1.getId() + "/sessions/" + session.getId() + "/activate", null,
                    HttpStatus.NO_CONTENT);
            var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
            assertThat(updatedSession.getStatus()).isEqualTo(TutorialGroupSessionStatus.ACTIVE);
        }
    }
}
