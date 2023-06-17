package de.tum.in.www1.artemis.tutorialgroups;

import static de.tum.in.www1.artemis.tutorialgroups.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupSessionResource;

class TutorialGroupSessionIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    Long exampleTutorialGroupId;

    @BeforeEach
    void setupTestScenario() {
        super.setupTestScenario();
        userUtilService.addUsers(this.testPrefix, 1, 2, 1, 1);
        exampleTutorialGroupId = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1",
                Language.ENGLISH.name(), userRepository.findOneByLogin(testPrefix + "tutor1").get(), Set.of(userRepository.findOneByLogin(testPrefix + "student1").get())).getId();

    }

    private static final String TEST_PREFIX = "tutorialgroupsession";

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getOneOfCourse_asUser_shouldReturnTutorialGroupSession() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        // when
        var sessionFromRequest = request.get(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + session.getId(), HttpStatus.OK, TutorialGroupSession.class);
        // then
        assertThat(sessionFromRequest).isEqualTo(session);

        // cleanup
        tutorialGroupSessionRepository.deleteById(session.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewSession_asInstructor_shouldCreateSession() throws Exception {
        createNewSessionAllowedTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void createNewSession_asTutorOfGroup_shouldCreateSession() throws Exception {
        createNewSessionAllowedTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void createNewSession_asNotTutorOfGroup_shouldForbidSession() throws Exception {
        createNewSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITO")
    void createNewSession_asEditor_shouldForbidSession() throws Exception {
        createNewSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewSession_overlapsWithExistingSession_shouldReturnBadRequest() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        var dto = createSessionDTO(firstAugustMonday);
        // when
        request.postWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(exampleTutorialGroupId)).containsExactly(session);

        // cleanup
        tutorialGroupSessionRepository.deleteById(session.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewSession_onTutorialGroupFreeDay_shouldCreateAsCancelled() throws Exception {
        // given
        tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var dto = createSessionDTO(firstAugustMonday);
        // when
        var sessionId = request.postWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.CREATED).getId();
        // then
        var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        assertThat(persistedSession.getTutorialGroupFreePeriod()).isNotNull();
        assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
        assertIndividualSessionIsCancelledOnDate(persistedSession, firstAugustMonday, exampleTutorialGroupId, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateSession_scheduledSession_shouldBeDisconnectedFromSchedule() throws Exception {
        // given
        userUtilService.changeUser(testPrefix + "instructor1");
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        userUtilService.changeUser(testPrefix + "tutor1");
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).get();
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.get(0);
        var secondAugustMondaySession = sessions.get(1);
        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(secondAugustMondaySession, secondAugustMonday, tutorialGroup.getId(), persistedSchedule);
        var dto = createSessionDTO(fourthAugustMonday);

        // when
        // change first august monday session to fourth monday august session
        var updatedSessionId = request
                .putWithResponseBody(getSessionsPathOfTutorialGroup(tutorialGroup.getId()) + firstAugustMondaySession.getId(), dto, TutorialGroupSession.class, HttpStatus.OK)
                .getId();

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getId()).isEqualTo(firstAugustMondaySession.getId());
        this.assertIndividualSessionIsActiveOnDate(updatedSession, fourthAugustMonday, tutorialGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSession_individualSession_shouldStillBeDisconnectedFromSchedule() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        var dto = createSessionDTO(secondAugustMonday);

        // when
        // change first august monday session to second monday august session
        var updatedSessionId = request
                .putWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + session.getId(), dto, TutorialGroupSession.class, HttpStatus.OK).getId();

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getId()).isEqualTo(session.getId());
        session = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
        assertIndividualSessionIsActiveOnDate(session, secondAugustMonday, exampleTutorialGroupId);

        // cleanup
        tutorialGroupSessionRepository.deleteById(session.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateAttendanceCount_asTutor_shouldUpdateAttendanceCount() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        assertThat(session.getAttendanceCount()).isNull();

        // when
        var updatedSessionId = request
                .patchWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + session.getId() + "/attendance-count" + "?attendanceCount=" + 20, null,
                        TutorialGroupSession.class, HttpStatus.OK)
                .getId();

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getId()).isEqualTo(session.getId());
        assertThat(updatedSession.getAttendanceCount()).isEqualTo(20);

        // when
        request.patchWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + session.getId() + "/attendance-count", null, TutorialGroupSession.class,
                HttpStatus.OK).getId();
        updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getAttendanceCount()).isNull();

        // cleanup
        tutorialGroupSessionRepository.deleteById(session.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateAttendanceCount_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        assertThat(session.getAttendanceCount()).isNull();
        // when
        request.patchWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + session.getId() + "/attendance-count" + "?attendanceCount=" + 20, null,
                TutorialGroupSession.class, HttpStatus.FORBIDDEN);
        // then
        session = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
        assertThat(session.getAttendanceCount()).isNull();

        // cleanup
        tutorialGroupSessionRepository.deleteById(session.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void updateAttendanceCount_belowMinOrAboveMax_shouldReturnBadRequest() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        assertThat(session.getAttendanceCount()).isNull();

        // when
        request.patchWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + session.getId() + "/attendance-count" + "?attendanceCount=" + 3001, null,
                TutorialGroupSession.class, HttpStatus.INTERNAL_SERVER_ERROR);
        // then
        session = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
        assertThat(session.getAttendanceCount()).isNull();

        // when
        request.patchWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + session.getId() + "/attendance-count" + "?attendanceCount=" + -1, null,
                TutorialGroupSession.class, HttpStatus.INTERNAL_SERVER_ERROR);
        // then
        session = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
        assertThat(session.getAttendanceCount()).isNull();

        // cleanup
        tutorialGroupSessionRepository.deleteById(session.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOverlapsWithOtherSession_shouldReturnBadRequest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        var secondAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, secondAugustMonday);

        var dto = createSessionDTO(secondAugustMonday);
        // when
        // change first august monday session to second monday august session
        request.putWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId(), dto, TutorialGroupSession.class,
                HttpStatus.BAD_REQUEST);

        // then
        var sessions = this.getTutorialGroupSessionsAscending(exampleTutorialGroupId);
        assertThat(sessions).containsExactly(firstAugustMondaySession, secondAugustMondaySession);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
        tutorialGroupSessionRepository.deleteById(secondAugustMondaySession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOverlapsWithPreviousTimeOfSameSession_shouldUpdateCorrectly() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        var dto = createSessionDTO(firstAugustMonday, LocalTime.of(this.defaultSessionStartHour - 1, 0, 0), LocalTime.of(this.defaultSessionEndHour + 1, 0, 0));

        // when
        var updatedSessionId = request.putWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId(), dto,
                TutorialGroupSession.class, HttpStatus.OK).getId();

        // then
        assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSession_wasCancelled_shouldNowBeActiveAgain() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        firstAugustMondaySession.setStatusExplanation("Cancelled");
        firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(firstAugustMondaySession);

        var dto = createSessionDTO(thirdAugustMonday);

        // when
        var updatedSessionId = request.putWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId(), dto,
                TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertIndividualSessionIsActiveOnDate(updatedSession, thirdAugustMonday, exampleTutorialGroupId);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOnTutorialGroupFreeDay_shouldUpdateAsCancelled() throws Exception {
        // given
        var freeDay = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, thirdAugustMonday, "Holiday");
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);

        var dto = createSessionDTO(thirdAugustMonday);
        // when
        var updatedSessionId = request.putWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId(), dto,
                TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupFreePeriod()).isNotNull();
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertIndividualSessionIsCancelledOnDate(updatedSession, thirdAugustMonday, exampleTutorialGroupId, null);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
        tutorialGroupFreePeriodRepository.deleteById(freeDay.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void updateSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
        updateSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void updateSession_asEditor_shouldReturnForbidden() throws Exception {
        updateSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void deleteSession_individualSession_shouldBeDeleted() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isTrue();
        // when
        request.delete(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId(), HttpStatus.NO_CONTENT);
        // then
        assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteSession_scheduledSession_shouldBeDeleted() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule(this.exampleCourseId, "tutor1");
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.get(0);
        assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isTrue();

        // when
        request.delete(getSessionsPathOfTutorialGroup(tutorialGroup.getId()) + firstAugustMondaySession.getId(), HttpStatus.NO_CONTENT);

        // then
        assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isFalse();
        sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void deleteSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
        deleteSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void deleteSession_asEditor_shouldReturnForbidden() throws Exception {
        deleteSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void cancelSession_asInstructor_shouldCancelSession() throws Exception {
        cancelSessionAllowedTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void cancelSession_asTutorOfGroup_shouldCancelSession() throws Exception {
        cancelSessionAllowedTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void cancelSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
        cancelSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void cancelSession_asEditor_shouldReturnForbidden() throws Exception {
        cancelSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void activateCancelledSession_asInstructor_shouldActivateSession() throws Exception {
        activateSessionAllowedTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void activateCancelledSession_asTutorOfGroup_shouldActivateSession() throws Exception {
        activateSessionAllowedTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void activateCancelledSession_asNotTutorOfGroup_shouldReturnForbidden() throws Exception {
        activateSessionForbiddenTest();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void activateCancelledSession_asEditor_shouldReturnForbidden() throws Exception {
        activateSessionForbiddenTest();
    }

    private void activateSessionAllowedTest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        firstAugustMondaySession.setStatusExplanation("Cancelled");
        firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(firstAugustMondaySession);

        // when
        request.postWithoutLocation(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId() + "/activate", null, HttpStatus.OK, null);

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(firstAugustMondaySession.getId());
        assertIndividualSessionIsActiveOnDate(updatedSession, firstAugustMonday, exampleTutorialGroupId);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

    private void activateSessionForbiddenTest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        firstAugustMondaySession.setStatusExplanation("Cancelled");
        firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(firstAugustMondaySession);

        // when
        request.postWithoutLocation(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId() + "/activate", null, HttpStatus.FORBIDDEN,
                null);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

    private TutorialGroupSessionResource.TutorialGroupSessionDTO createSessionDTO(LocalDate date) {
        return new TutorialGroupSessionResource.TutorialGroupSessionDTO(date, LocalTime.of(defaultSessionStartHour, 0, 0), LocalTime.of(defaultSessionEndHour, 0, 0), "LoremIpsum");
    }

    private TutorialGroupSessionResource.TutorialGroupSessionDTO createSessionDTO(LocalDate date, LocalTime startTime, LocalTime endTime) {
        return new TutorialGroupSessionResource.TutorialGroupSessionDTO(date, startTime, endTime, "LoremIpsum");
    }

    private void assertSessionCreatedCorrectlyFromDTO(TutorialGroupSession session, TutorialGroupSessionResource.TutorialGroupSessionDTO dto) {
        assertThat(session.getStart()).isEqualTo(ZonedDateTime.of(dto.date(), dto.startTime(), ZoneId.of(this.exampleTimeZone)));
        assertThat(session.getEnd()).isEqualTo(ZonedDateTime.of(dto.date(), dto.endTime(), ZoneId.of(this.exampleTimeZone)));
        assertThat(session.getLocation()).isEqualTo(dto.location());
        assertThat(session.getTutorialGroupSchedule()).isNull(); // individual session so not connected to a schedule
    }

    private void updateSessionForbiddenTest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        var dto = createSessionDTO(thirdAugustMonday);

        // when
        request.put(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId(), dto, HttpStatus.FORBIDDEN);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

    private void createNewSessionAllowedTest() throws Exception {
        // given
        var dto = createSessionDTO(firstAugustMonday);
        // when
        var sessionId = request.postWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.CREATED).getId();
        // then
        var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
        assertIndividualSessionIsActiveOnDate(persistedSession, firstAugustMonday, exampleTutorialGroupId);

        // cleanup
        tutorialGroupSessionRepository.deleteById(persistedSession.getId());
    }

    private void createNewSessionForbiddenTest() throws Exception {
        // given
        var dto = createSessionDTO(firstAugustMonday);
        // when
        request.postWithResponseBody(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId), dto, TutorialGroupSession.class, HttpStatus.FORBIDDEN);
    }

    private void deleteSessionForbiddenTest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        // when
        request.delete(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId(), HttpStatus.FORBIDDEN);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

    private void cancelSessionAllowedTest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        assertIndividualSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, exampleTutorialGroupId);
        var statusDTO = new TutorialGroupSessionResource.TutorialGroupStatusDTO("Holiday");
        // when
        request.postWithoutLocation(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId() + "/cancel", statusDTO, HttpStatus.OK, null);
        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(firstAugustMondaySession.getId());
        assertIndividualSessionIsCancelledOnDate(updatedSession, firstAugustMonday, exampleTutorialGroupId, "Holiday");

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

    private void cancelSessionForbiddenTest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleTutorialGroupId, firstAugustMonday);
        assertIndividualSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, exampleTutorialGroupId);
        var statusDTO = new TutorialGroupSessionResource.TutorialGroupStatusDTO("Holiday");
        // when
        request.postWithoutLocation(getSessionsPathOfDefaultTutorialGroup(exampleTutorialGroupId) + firstAugustMondaySession.getId() + "/cancel", statusDTO, HttpStatus.FORBIDDEN,
                null);

        // cleanup
        tutorialGroupSessionRepository.deleteById(firstAugustMondaySession.getId());
    }

}
