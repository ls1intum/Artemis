package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.web.rest.tutorialgroups.TutorialGroupResource;

class TutorialGroupScheduleIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    @BeforeEach
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

        var newTutorialGroupCoveringDST = this.buildTutorialGroupWithExampleSchedule(mondayBeforeDSTSwitch, mondayAfterDSTSwitch, "tutor1");
        var scheduleToCreate = newTutorialGroupCoveringDST.getTutorialGroupSchedule();

        // when
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), newTutorialGroupCoveringDST, TutorialGroup.class, HttpStatus.CREATED)
                .getId();

        // then
        newTutorialGroupCoveringDST = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        this.assertTutorialGroupPersistedWithSchedule(newTutorialGroupCoveringDST, scheduleToCreate);

        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroupId);
        assertThat(sessions).hasSize(2);
        var standardTimeSession = sessions.get(0);
        var daylightSavingsTimeSession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(newTutorialGroupCoveringDST.getId()).get();

        this.assertScheduledSessionIsActiveOnDate(standardTimeSession, mondayBeforeDSTSwitch, persistedTutorialGroupId, persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(daylightSavingsTimeSession, mondayAfterDSTSwitch, persistedTutorialGroupId, persistedSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_everyTwoWeeks_shouldCreateWithOneWeekPauseInBetween() throws Exception {
        // given
        var newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, thirdAugustMonday, "tutor1");
        newTutorialGroup.getTutorialGroupSchedule().setRepetitionFrequency(2); // repeat every two weeks

        // when
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), newTutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();

        // then
        newTutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);

        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroupId);
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.get(0);
        var thirdAugustMondaySession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(newTutorialGroup.getId()).get();
        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, persistedTutorialGroupId, persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(thirdAugustMondaySession, thirdAugustMonday, persistedTutorialGroupId, persistedSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_sessionFallsOnTutorialGroupFreeDay_shouldCreateCancelledSession() throws Exception {
        // given
        var freeDay = tutorialGroupUtilService.addTutorialGroupFreeDay(exampleConfigurationId, secondAugustMonday, "Holiday");
        var newTutorialGroupCoveringHoliday = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        var scheduleToCreate = newTutorialGroupCoveringHoliday.getTutorialGroupSchedule();

        // when
        var persistedTutorialGroupId = request
                .postWithResponseBody(getTutorialGroupsPath(exampleCourseId), newTutorialGroupCoveringHoliday, TutorialGroup.class, HttpStatus.CREATED).getId();

        // then
        newTutorialGroupCoveringHoliday = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        this.assertTutorialGroupPersistedWithSchedule(newTutorialGroupCoveringHoliday, scheduleToCreate);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(newTutorialGroupCoveringHoliday.getId()).get();

        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroupId);
        assertThat(sessions).hasSize(2);
        var normalSession = sessions.get(0);
        var holidaySession = sessions.get(1);

        this.assertScheduledSessionIsActiveOnDate(normalSession, firstAugustMonday, persistedTutorialGroupId, persistedSchedule);
        this.assertScheduledSessionIsCancelledOnDate(holidaySession, secondAugustMonday, persistedTutorialGroupId, persistedSchedule);

        // cleanup
        tutorialGroupSessionRepository.deleteById(holidaySession.getId());
        tutorialGroupFreePeriodRepository.deleteById(freeDay.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_wrongDateFormatInSchedule_shouldReturnBadRequest() throws Exception {
        // given
        var newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        var scheduleToCreate = newTutorialGroup.getTutorialGroupSchedule();
        // wrong format as not uuuu-MM-dd
        scheduleToCreate.setValidFromInclusive("2022-11-25T23:00:00.000Z");
        request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), newTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        scheduleToCreate = newTutorialGroup.getTutorialGroupSchedule();
        // wrong format as not uuuu-MM-dd
        scheduleToCreate.setValidToInclusive("2022-11-25T23:00:00.000Z");
        request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), newTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        scheduleToCreate = newTutorialGroup.getTutorialGroupSchedule();
        // wrong format as not hh:mm:ss
        scheduleToCreate.setStartTime("23:00:00.000Z");
        request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), newTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        scheduleToCreate = newTutorialGroup.getTutorialGroupSchedule();
        // wrong format as not hh:mm:ss
        scheduleToCreate.setEndTime("23:00:00.000Z");
        request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), newTutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addScheduleToTutorialGroupWithoutSchedule_asInstructor_shouldAddScheduleAndCreateSessions() throws Exception {
        // given
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");
        var newSchedule = this.buildExampleSchedule(firstAugustMonday, secondAugustMonday);
        tutorialGroup.setTutorialGroupSchedule(newSchedule);

        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        // when
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + tutorialGroup.getId(), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        var persistedTutorialGroup = tutorialGroupRepository.findByIdElseThrow(tutorialGroup.getId());
        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.get(0);
        var secondAugustMondaySession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(persistedTutorialGroup.getId()).get();

        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(secondAugustMondaySession, secondAugustMonday, tutorialGroup.getId(), persistedSchedule);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void addScheduleToTutorialGroupWithoutSchedule_scheduledSessionFallsOnAlreadyExistingSession_shouldReturnBadRequest() throws Exception {
        // given
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule("tutor1");
        this.buildAndSaveExampleIndividualTutorialGroupSession(tutorialGroup.getId(), firstAugustMonday);
        var newSchedule = this.buildExampleSchedule(firstAugustMonday, secondAugustMonday);
        tutorialGroup.setTutorialGroupSchedule(newSchedule);

        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        // when
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + tutorialGroup.getId(), dto, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        // then
        assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId())).hasSize(1);
        assertThat(tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTutorialGroupWithSchedule_NoChangesToSchedule_ShouldNotRecreateSessionsOrSchedule() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(persistedTutorialGroupId);
        var sessionIds = tutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet());
        var scheduleId = tutorialGroup.getTutorialGroupSchedule().getId();

        // when
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        tutorialGroup.setCapacity(2000);
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + tutorialGroup.getId(), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(persistedTutorialGroupId);
        assertThat(tutorialGroup.getCapacity()).isEqualTo(2000);
        assertThat(tutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet())).containsExactlyInAnyOrderElementsOf(sessionIds);
        assertThat(tutorialGroup.getTutorialGroupSchedule().getId()).isEqualTo(scheduleId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateTutorialGroupWithSchedule_OnlyLocationChanged_ShouldNotRecreateSessionsOrScheduleButUpdateLocation() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(persistedTutorialGroupId);
        var sessionIds = tutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet());
        var scheduleId = tutorialGroup.getTutorialGroupSchedule().getId();

        // when
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        tutorialGroup.getTutorialGroupSchedule().setLocation("updated");
        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + tutorialGroup.getId(), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        tutorialGroup = tutorialGroupRepository.findByIdWithSessionsElseThrow(persistedTutorialGroupId);
        assertThat(tutorialGroup.getTutorialGroupSchedule().getLocation()).isEqualTo("updated");
        assertThat(tutorialGroup.getTutorialGroupSessions().stream().map(DomainObject::getId).collect(Collectors.toSet())).containsExactlyInAnyOrderElementsOf(sessionIds);
        assertThat(tutorialGroup.getTutorialGroupSchedule().getId()).isEqualTo(scheduleId);
        tutorialGroup.getTutorialGroupSessions().forEach(tutorialGroupSession -> {
            assertThat(tutorialGroupSession.getLocation()).isEqualTo("updated");
        });
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void modifyExistingScheduleOfTutorialGroup_shouldRecreateScheduledSessionsButKeepIndividualSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);

        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, fourthAugustMonday);

        var newSchedule = this.buildExampleSchedule(firstAugustMonday, thirdAugustMonday);
        newSchedule.setId(tutorialGroup.getTutorialGroupSchedule().getId());
        newSchedule.setRepetitionFrequency(2); // repeat every two weeks
        tutorialGroup.setTutorialGroupSchedule(newSchedule);
        newSchedule.setTutorialGroup(tutorialGroup);

        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        // when
        request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + tutorialGroup.getId(), dto, TutorialGroup.class, HttpStatus.OK);

        // then
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(3);
        var firstAugustMondaySession = sessions.get(0);
        var thirdAugustMondaySession = sessions.get(1);
        var fourthAugustMondaySession = sessions.get(2);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId()).get();

        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(thirdAugustMondaySession, thirdAugustMonday, tutorialGroup.getId(), persistedSchedule);
        // individual session
        this.assertIndividualSessionIsActiveOnDate(fourthAugustMondaySession, fourthAugustMonday, tutorialGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteScheduleOfTutorialGroup_shouldDeleteAllScheduledSessionsButKeepIndividualSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, thirdAugustMonday);
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        // when
        tutorialGroup.setTutorialGroupSchedule(null);

        var dto = new TutorialGroupResource.TutorialGroupUpdateDTO(tutorialGroup, "Lorem Ipsum", true);
        tutorialGroup = request.putWithResponseBody(getTutorialGroupsPath(exampleCourseId) + tutorialGroup.getId(), dto, TutorialGroup.class, HttpStatus.OK);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(1);
        var thirdAugustMondaySession = sessions.get(0);
        this.assertIndividualSessionIsActiveOnDate(thirdAugustMondaySession, thirdAugustMonday, tutorialGroup.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTutorialGroupWithSchedule_shouldDeleteScheduleAndSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday, "tutor1");
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(exampleCourseId), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, thirdAugustMonday);
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);

        // when
        request.delete(getTutorialGroupsPath(exampleCourseId) + tutorialGroup.getId(), HttpStatus.NO_CONTENT);

        // then
        assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId())).isEmpty();
        assertThat(tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroup.getId())).isEmpty();
        assertThat(tutorialGroupRepository.findById(tutorialGroup.getId())).isEmpty();
    }

}
