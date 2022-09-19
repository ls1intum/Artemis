package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupSessionResource;

public class TutorialGroupSessionIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    void testJustForInstructorEndpoints() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void getOneOfCourse_asInstructor_shouldReturnTutorialGroupSession() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        // when
        var sessionFromRequest = request.get(getSessionsPathOfDefaultTutorialGroup() + session.getId(), HttpStatus.OK, TutorialGroupSession.class);
        // then
        assertThat(sessionFromRequest).isEqualTo(session);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void createNewSession_asInstructor_shouldCreateSession() throws Exception {
        // given
        var dto = createSessionDTO(firstAugustMonday);
        // when
        var sessionId = request.postWithResponseBody(getSessionsPathOfDefaultTutorialGroup(), dto, TutorialGroupSession.class, HttpStatus.CREATED).getId();
        // then
        var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
        assertIndividualSessionIsActiveOnDate(persistedSession, firstAugustMonday, exampleOneTutorialGroupId);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void createNewSession_overlapsWithExistingSession_shouldReturnBadRequest() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        var dto = createSessionDTO(firstAugustMonday);
        // when
        request.postWithResponseBody(getSessionsPathOfDefaultTutorialGroup(), dto, TutorialGroupSession.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupSessionRepository.findAll()).containsExactly(session);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void createNewSession_onTutorialGroupFreeDay_shouldCreateAsCancelled() throws Exception {
        // given
        databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, firstAugustMonday, "Holiday");
        var dto = createSessionDTO(firstAugustMonday);
        // when
        var sessionId = request.postWithResponseBody(getSessionsPathOfDefaultTutorialGroup(), dto, TutorialGroupSession.class, HttpStatus.CREATED).getId();
        // then
        var persistedSession = tutorialGroupSessionRepository.findByIdElseThrow(sessionId);
        assertSessionCreatedCorrectlyFromDTO(persistedSession, dto);
        assertIndividualSessionIsCancelledOnDate(persistedSession, firstAugustMonday, exampleOneTutorialGroupId, "Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_scheduledSession_shouldBeDisconnectedFromSchedule() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule();
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroup.getId()).get();
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_individualSession_shouldStillBeDisconnectedFromSchedule() throws Exception {
        // given
        var session = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        var dto = createSessionDTO(secondAugustMonday);

        // when
        // change first august monday session to second monday august session
        var updatedSessionId = request.putWithResponseBody(getSessionsPathOfDefaultTutorialGroup() + session.getId(), dto, TutorialGroupSession.class, HttpStatus.OK).getId();

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getId()).isEqualTo(session.getId());
        session = tutorialGroupSessionRepository.findByIdElseThrow(session.getId());
        assertIndividualSessionIsActiveOnDate(session, secondAugustMonday, exampleOneTutorialGroupId);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOverlapsWithOtherSession_shouldReturnBadRequest() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        var secondAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, secondAugustMonday);

        var dto = createSessionDTO(secondAugustMonday);
        // when
        // change first august monday session to second monday august session
        request.putWithResponseBody(getSessionsPathOfDefaultTutorialGroup() + firstAugustMondaySession.getId(), dto, TutorialGroupSession.class, HttpStatus.BAD_REQUEST);

        // then
        var sessions = this.getTutorialGroupSessionsAscending(exampleOneTutorialGroupId);
        assertThat(sessions).containsExactly(firstAugustMondaySession, secondAugustMondaySession);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOverlapsWithPreviousTimeOfSameSession_shouldUpdateCorrectly() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        var dto = createSessionDTO(firstAugustMonday, LocalTime.of(this.defaultSessionStartHour - 1, 0, 0), LocalTime.of(this.defaultSessionEndHour + 1, 0, 0));

        // when
        var updatedSessionId = request
                .putWithResponseBody(getSessionsPathOfDefaultTutorialGroup() + firstAugustMondaySession.getId(), dto, TutorialGroupSession.class, HttpStatus.OK).getId();

        // then
        assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_wasCancelled_shouldNowBeActiveAgain() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        firstAugustMondaySession.setStatusExplanation("Cancelled");
        firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(firstAugustMondaySession);

        var dto = createSessionDTO(thirdAugustMonday);

        // when
        var updatedSessionId = request
                .putWithResponseBody(getSessionsPathOfDefaultTutorialGroup() + firstAugustMondaySession.getId(), dto, TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertIndividualSessionIsActiveOnDate(updatedSession, thirdAugustMonday, exampleOneTutorialGroupId);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateSession_nowOnTutorialGroupFreeDay_shouldUpdateAsCancelled() throws Exception {
        // given
        databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, thirdAugustMonday, "Holiday");
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);

        var dto = createSessionDTO(thirdAugustMonday);
        // when
        var updatedSessionId = request
                .putWithResponseBody(getSessionsPathOfDefaultTutorialGroup() + firstAugustMondaySession.getId(), dto, TutorialGroupSession.class, HttpStatus.OK).getId();
        assertThat(updatedSessionId).isEqualTo(firstAugustMondaySession.getId());

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(updatedSessionId);
        assertThat(updatedSession.getTutorialGroupSchedule()).isNull();
        assertSessionCreatedCorrectlyFromDTO(updatedSession, dto);
        assertIndividualSessionIsCancelledOnDate(updatedSession, thirdAugustMonday, exampleOneTutorialGroupId, "Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void deleteSession_individualSession_shouldBeDeleted() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isTrue();
        // when
        request.delete(getSessionsPathOfDefaultTutorialGroup() + firstAugustMondaySession.getId(), HttpStatus.NO_CONTENT);
        // then
        assertThat(tutorialGroupSessionRepository.existsById(firstAugustMondaySession.getId())).isFalse();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void deleteSession_scheduledSession_shouldBeDeleted() throws Exception {
        // given
        TutorialGroup tutorialGroup = this.setUpTutorialGroupWithSchedule();
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void cancelSession_asInstructor_shouldCancelSession() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        assertIndividualSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, exampleOneTutorialGroupId);
        var statusDTO = new TutorialGroupSessionResource.TutorialGroupStatusDTO("Holiday");
        // when
        request.postWithoutLocation(getSessionsPathOfDefaultTutorialGroup() + firstAugustMondaySession.getId() + "/cancel", statusDTO, HttpStatus.OK, null);
        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(firstAugustMondaySession.getId());
        assertIndividualSessionIsCancelledOnDate(updatedSession, firstAugustMonday, exampleOneTutorialGroupId, "Holiday");
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void activateCancelledSession_asInstructor_shouldActivateSession() throws Exception {
        // given
        var firstAugustMondaySession = this.buildAndSaveExampleIndividualTutorialGroupSession(exampleOneTutorialGroupId, firstAugustMonday);
        firstAugustMondaySession.setStatusExplanation("Cancelled");
        firstAugustMondaySession.setStatus(TutorialGroupSessionStatus.CANCELLED);
        tutorialGroupSessionRepository.save(firstAugustMondaySession);

        // when
        request.postWithoutLocation(getSessionsPathOfDefaultTutorialGroup() + firstAugustMondaySession.getId() + "/activate", null, HttpStatus.OK, null);

        // then
        var updatedSession = tutorialGroupSessionRepository.findByIdElseThrow(firstAugustMondaySession.getId());
        assertIndividualSessionIsActiveOnDate(updatedSession, firstAugustMonday, exampleOneTutorialGroupId);
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

}
