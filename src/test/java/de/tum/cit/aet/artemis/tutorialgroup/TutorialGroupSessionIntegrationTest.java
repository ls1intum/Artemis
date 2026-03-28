package de.tum.cit.aet.artemis.tutorialgroup;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Language;

class TutorialGroupSessionIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    Long exampleTutorialGroupId;

    @BeforeEach
    @Override
    void setupTestScenario() {
        super.setupTestScenario();
        userUtilService.addUsers(this.testPrefix, 1, 2, 1, 1);
        var tutor1 = userRepository.findOneByLogin(testPrefix + "tutor1").orElseThrow();
        var student1 = userRepository.findOneByLogin(testPrefix + "student1").orElseThrow();
        exampleTutorialGroupId = tutorialGroupUtilService
                .createAndSaveTutorialGroup(exampleCourseId, "TG Mo 13", "SampleInfo1", 10, false, "Garching", Language.ENGLISH.name(), tutor1, Set.of(student1)).getId();
    }

    private static final String TEST_PREFIX = "tutorialgroupsession";

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Nested
    class CreateSessionTests {

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithoutExistingGroup_shouldReturnNotFound() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfOtherGroup_shouldReturnAccessForbidden() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asEditorOfOtherCourse_shouldReturnAccessForbidden() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithSessionWithStartNotBeforeEnd_shouldReturnBadRequest() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithoutConfiguration_shouldReturnBadRequest() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithoutTimeZone_shouldReturnBadRequest() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithOtherCourse_shouldReturnBadRequest() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithOtherTutorialGroup_shouldReturnBadRequest() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithOverlappingSession_shouldReturnBadRequest() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithOverlappingFreePeriod_shouldReturnCreated() throws Exception {

        }

        @Test
        @WithMockUser(username = "someLogin", roles = "TA")
        void createSession_asTutorOfGroupWithoutOverlappingFreePeriod_shouldReturnCreated() throws Exception {

        }
    }

    @Nested
    class UpdateSessionTests {

    }

    @Nested
    class DeleteSessionTests {

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
