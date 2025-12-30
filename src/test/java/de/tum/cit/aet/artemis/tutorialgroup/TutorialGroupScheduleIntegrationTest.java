package de.tum.cit.aet.artemis.tutorialgroup;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.web.TutorialGroupResource;

class TutorialGroupScheduleIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    @BeforeEach
    @Override
    void setupTestScenario() {
        super.setupTestScenario();
        userUtilService.addUsers(this.testPrefix, 0, 1, 0, 1);
    }

    private static final String TEST_PREFIX = "tutorialgroupschedule";

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_periodCoversDSTChange_shouldHandleDaylightSavingTimeSwitchCorrectly() throws Exception {
        // given
        // DST Switch occurs on Sunday, March 29, 2020, 3:00 am in Bucharest
        var mondayBeforeDSTSwitch = LocalDate.of(2020, 3, 23);
        var mondayAfterDSTSwitch = LocalDate.of(2020, 3, 30);

        // First create tutorial group without schedule
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");

        // Then add schedule via update
        var scheduleToCreate = this.buildExampleSchedule(mondayBeforeDSTSwitch, mondayAfterDSTSwitch);
        tutorialGroup.setTutorialGroupSchedule(scheduleToCreate);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var persistedTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroup.getId());
        this.assertTutorialGroupPersistedWithSchedule(persistedTutorialGroup, scheduleToCreate);

        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var standardTimeSession = sessions.getFirst();
        var daylightSavingsTimeSession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(persistedTutorialGroup.getId()).orElseThrow();

        this.assertScheduledSessionIsActiveOnDate(standardTimeSession, mondayBeforeDSTSwitch, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(daylightSavingsTimeSession, mondayAfterDSTSwitch, tutorialGroup.getId(), persistedSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_everyTwoWeeks_shouldCreateWithOneWeekPauseInBetween() throws Exception {
        // given - first create tutorial group without schedule
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");

        // Then add schedule via update with repetition every two weeks
        var scheduleToCreate = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, THIRD_AUGUST_MONDAY);
        scheduleToCreate.setRepetitionFrequency(2); // repeat every two weeks
        tutorialGroup.setTutorialGroupSchedule(scheduleToCreate);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var persistedTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroup.getId());

        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.getFirst();
        var thirdAugustMondaySession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(persistedTutorialGroup.getId()).orElseThrow();
        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(thirdAugustMondaySession, THIRD_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_sessionFallsOnTutorialGroupFreeDay_shouldCreateCancelledSession() throws Exception {
        // given
        var freeDay = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, SECOND_AUGUST_MONDAY_00_00, SECOND_AUGUST_MONDAY_23_59, "Holiday");

        // First create tutorial group without schedule
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");

        // Then add schedule via update
        var scheduleToCreate = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        tutorialGroup.setTutorialGroupSchedule(scheduleToCreate);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var persistedTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroup.getId());
        this.assertTutorialGroupPersistedWithSchedule(persistedTutorialGroup, scheduleToCreate);

        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(persistedTutorialGroup.getId()).orElseThrow();

        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var normalSession = sessions.getFirst();
        var holidaySession = sessions.get(1);

        this.assertScheduledSessionIsActiveOnDate(normalSession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsCancelledOnDate(holidaySession, SECOND_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(holidaySession.getId());
        tutorialGroupFreePeriodRepository.deleteById(freeDay.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTutorialGroupWithSchedule_wrongDateFormatInSchedule_shouldReturnBadRequest() throws Exception {
        // Create a tutorial group first
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");

        // Test wrong format for validFromInclusive
        var scheduleWithWrongFrom = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        scheduleWithWrongFrom.setValidFromInclusive("2022-11-25T23:00:00.000Z"); // wrong format as not uuuu-MM-dd
        tutorialGroup.setTutorialGroupSchedule(scheduleWithWrongFrom);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        // Test wrong format for validToInclusive
        var scheduleWithWrongTo = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        scheduleWithWrongTo.setValidToInclusive("2022-11-25T23:00:00.000Z"); // wrong format as not uuuu-MM-dd
        tutorialGroup.setTutorialGroupSchedule(scheduleWithWrongTo);
        dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        // Test wrong format for startTime
        var scheduleWithWrongStartTime = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        scheduleWithWrongStartTime.setStartTime("23:00:00.000Z"); // wrong format as not hh:mm:ss
        tutorialGroup.setTutorialGroupSchedule(scheduleWithWrongStartTime);
        dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        // Test wrong format for endTime
        var scheduleWithWrongEndTime = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        scheduleWithWrongEndTime.setEndTime("23:00:00.000Z"); // wrong format as not hh:mm:ss
        tutorialGroup.setTutorialGroupSchedule(scheduleWithWrongEndTime);
        dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addScheduleToTutorialGroupWithoutSchedule_asInstructor_shouldAddScheduleAndCreateSessions() throws Exception {
        // given
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");
        var newSchedule = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        tutorialGroup.setTutorialGroupSchedule(newSchedule);

        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        // when
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var persistedTutorialGroup = tutorialGroupTestRepository.findByIdElseThrow(tutorialGroup.getId());
        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.getFirst();
        var secondAugustMondaySession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(persistedTutorialGroup.getId()).orElseThrow();

        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, FIRST_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(secondAugustMondaySession, SECOND_AUGUST_MONDAY, tutorialGroup.getId(), persistedSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addScheduleToTutorialGroupWithoutSchedule_scheduledSessionFallsOnAlreadyExistingSession_shouldReturnBadRequest() throws Exception {
        // given
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");
        this.buildAndSaveExampleIndividualTutorialGroupSession(tutorialGroup.getId(), FIRST_AUGUST_MONDAY_00_00);
        var newSchedule = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        tutorialGroup.setTutorialGroupSchedule(newSchedule);

        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        // when
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        // then
        assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId())).hasSize(1);
        assertThat(tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroup.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTutorialGroupWithSchedule_NoChangesToSchedule_ShouldNotRecreateSessionsOrSchedule() throws Exception {
        // given - first create tutorial group without schedule
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");
        var tutorialGroupId = tutorialGroup.getId();

        // Add schedule via update
        var scheduleToCreate = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        tutorialGroup.setTutorialGroupSchedule(scheduleToCreate);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroupId), dto, TutorialGroup.class, HttpStatus.OK);

        // Get the persisted session and schedule IDs for later comparison
        var persistedTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroupId);
        var sessionIds = persistedTutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet());
        var scheduleId = persistedTutorialGroup.getTutorialGroupSchedule().getId();

        // when - update capacity without changing schedule (fetch without sessions to avoid serialization issues)
        tutorialGroup = tutorialGroupTestRepository.findByIdElseThrow(tutorialGroupId);
        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroupId).orElseThrow();
        tutorialGroup.setTutorialGroupSchedule(persistedSchedule);
        tutorialGroup.setCapacity(2000);
        dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroupId), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var resultTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroupId);
        assertThat(resultTutorialGroup.getCapacity()).isEqualTo(2000);
        assertThat(resultTutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet())).containsExactlyInAnyOrderElementsOf(sessionIds);
        assertThat(resultTutorialGroup.getTutorialGroupSchedule().getId()).isEqualTo(scheduleId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTutorialGroupWithSchedule_OnlyLocationChanged_ShouldNotRecreateSessionsOrScheduleButUpdateLocation() throws Exception {
        // given - first create tutorial group without schedule
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");
        var tutorialGroupId = tutorialGroup.getId();

        // Add schedule via update
        var scheduleToCreate = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        tutorialGroup.setTutorialGroupSchedule(scheduleToCreate);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroupId), dto, TutorialGroup.class, HttpStatus.OK);

        // Get the persisted session and schedule IDs for later comparison
        var persistedTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroupId);
        var sessionIds = persistedTutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet());
        var scheduleId = persistedTutorialGroup.getTutorialGroupSchedule().getId();

        // when - update only location (fetch without sessions to avoid serialization issues)
        tutorialGroup = tutorialGroupTestRepository.findByIdElseThrow(tutorialGroupId);
        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroupId).orElseThrow();
        persistedSchedule.setLocation("updated");
        tutorialGroup.setTutorialGroupSchedule(persistedSchedule);
        dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroupId), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var resultTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroupId);
        assertThat(resultTutorialGroup.getTutorialGroupSchedule().getLocation()).isEqualTo("updated");
        assertThat(resultTutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet())).containsExactlyInAnyOrderElementsOf(sessionIds);
        assertThat(resultTutorialGroup.getTutorialGroupSchedule().getId()).isEqualTo(scheduleId);
        resultTutorialGroup.getTutorialGroupSessions().forEach(tutorialGroupSession -> {
            assertThat(tutorialGroupSession.getLocation()).isEqualTo("updated");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void modifyExistingScheduleOfTutorialGroup_shouldRecreateScheduledSessionsButKeepIndividualSessions() throws Exception {
        // given - first create tutorial group without schedule
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");
        var tutorialGroupId = tutorialGroup.getId();

        // Add initial schedule via update
        var initialSchedule = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY);
        tutorialGroup.setTutorialGroupSchedule(initialSchedule);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroupId), dto, TutorialGroup.class, HttpStatus.OK);

        // Add an individual session
        this.buildAndSaveExampleIndividualTutorialGroupSession(tutorialGroupId, FOURTH_AUGUST_MONDAY_00_00);

        // Get the persisted schedule ID
        var persistedSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroupId).orElseThrow();

        // Modify the schedule (fetch without sessions to avoid serialization issues)
        tutorialGroup = tutorialGroupTestRepository.findByIdElseThrow(tutorialGroupId);
        var newSchedule = this.buildExampleSchedule(FIRST_AUGUST_MONDAY, THIRD_AUGUST_MONDAY);
        newSchedule.setId(persistedSchedule.getId());
        newSchedule.setRepetitionFrequency(2); // repeat every two weeks
        tutorialGroup.setTutorialGroupSchedule(newSchedule);

        dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        // when
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroupId), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var resultTutorialGroup = tutorialGroupTestRepository.findByIdWithTeachingAssistantAndRegistrationsAndSessionsElseThrow(tutorialGroupId);
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroupId);
        assertThat(sessions).hasSize(3);
        var firstAugustMondaySession = sessions.getFirst();
        var thirdAugustMondaySession = sessions.get(1);
        var fourthAugustMondaySession = sessions.get(2);

        var resultSchedule = tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroupId).orElseThrow();

        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, FIRST_AUGUST_MONDAY, tutorialGroupId, resultSchedule);
        this.assertScheduledSessionIsActiveOnDate(thirdAugustMondaySession, THIRD_AUGUST_MONDAY, tutorialGroupId, resultSchedule);
        // individual session
        this.assertIndividualSessionIsActiveOnDate(fourthAugustMondaySession, FOURTH_AUGUST_MONDAY_00_00, tutorialGroupId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteScheduleOfTutorialGroup_shouldDeleteAllScheduledSessionsButKeepIndividualSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY, "tutor1");
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, THIRD_AUGUST_MONDAY_00_00);
        tutorialGroup = tutorialGroupTestRepository.findByIdElseThrow(persistedTutorialGroupId);
        // when
        tutorialGroup.setTutorialGroupSchedule(null);

        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        tutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), dto, TutorialGroup.class, HttpStatus.OK);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(1);
        var thirdAugustMondaySession = sessions.getFirst();
        this.assertIndividualSessionIsActiveOnDate(thirdAugustMondaySession, THIRD_AUGUST_MONDAY_00_00, tutorialGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTutorialGroupWithSchedule_shouldDeleteScheduleAndSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(FIRST_AUGUST_MONDAY, SECOND_AUGUST_MONDAY, "tutor1");
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, THIRD_AUGUST_MONDAY_00_00);
        tutorialGroup = tutorialGroupTestRepository.findByIdElseThrow(persistedTutorialGroupId);

        // when
        request.delete(getTutorialGroupsPath(exampleCourseId, tutorialGroup.getId()), HttpStatus.NO_CONTENT);

        // then
        assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId())).isEmpty();
        assertThat(tutorialGroupScheduleTestRepository.findByTutorialGroupId(tutorialGroup.getId())).isEmpty();
        assertThat(tutorialGroupTestRepository.findById(tutorialGroup.getId())).isEmpty();
    }

}
