package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;

class TutorialGroupScheduleIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    @Override
    void testJustForInstructorEndpoints() throws Exception {
        // not needed as endpoints already tested in other tests
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_periodCoversDSTChange_shouldHandleDaylightSavingTimeSwitchCorrectly() throws Exception {
        // given
        // DST Switch occurs on Sunday, March 29, 2020, 3:00 am in Bucharest
        var mondayBeforeDSTSwitch = LocalDate.of(2020, 3, 23);
        var mondayAfterDSTSwitch = LocalDate.of(2020, 3, 30);

        var newTutorialGroupCoveringDST = this.buildTutorialGroupWithExampleSchedule(mondayBeforeDSTSwitch, mondayAfterDSTSwitch);
        var scheduleToCreate = newTutorialGroupCoveringDST.getTutorialGroupSchedule();

        // when
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(), newTutorialGroupCoveringDST, TutorialGroup.class, HttpStatus.CREATED).getId();

        // then
        newTutorialGroupCoveringDST = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        this.assertTutorialGroupPersistedWithSchedule(newTutorialGroupCoveringDST, scheduleToCreate);

        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroupId);
        assertThat(sessions).hasSize(2);
        var standardTimeSession = sessions.get(0);
        var daylightSavingsTimeSession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(newTutorialGroupCoveringDST.getId()).get();

        this.assertScheduledSessionIsActiveOnDate(standardTimeSession, mondayBeforeDSTSwitch, persistedTutorialGroupId, persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(daylightSavingsTimeSession, mondayAfterDSTSwitch, persistedTutorialGroupId, persistedSchedule);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_everyTwoWeeks_shouldCreateWithOneWeekPauseInBetween() throws Exception {
        // given
        var newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, thirdAugustMonday);
        newTutorialGroup.getTutorialGroupSchedule().setRepetitionFrequency(2); // repeat every two weeks

        // when
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(), newTutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();

        // then
        newTutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);

        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroupId);
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.get(0);
        var thirdAugustMondaySession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(newTutorialGroup.getId()).get();
        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, persistedTutorialGroupId, persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(thirdAugustMondaySession, thirdAugustMonday, persistedTutorialGroupId, persistedSchedule);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_sessionFallsOnTutorialGroupFreeDay_shouldCreateCancelledSession() throws Exception {
        // given
        databaseUtilService.addTutorialGroupFreeDay(exampleConfigurationId, secondAugustMonday, "Holiday");
        var newTutorialGroupCoveringHoliday = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday);
        var scheduleToCreate = newTutorialGroupCoveringHoliday.getTutorialGroupSchedule();

        // when
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(), newTutorialGroupCoveringHoliday, TutorialGroup.class, HttpStatus.CREATED).getId();

        // then
        newTutorialGroupCoveringHoliday = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        this.assertTutorialGroupPersistedWithSchedule(newTutorialGroupCoveringHoliday, scheduleToCreate);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(newTutorialGroupCoveringHoliday.getId()).get();

        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroupId);
        assertThat(sessions).hasSize(2);
        var normalSession = sessions.get(0);
        var holidaySession = sessions.get(1);

        this.assertScheduledSessionIsActiveOnDate(normalSession, firstAugustMonday, persistedTutorialGroupId, persistedSchedule);
        this.assertScheduledSessionIsCancelledOnDate(holidaySession, secondAugustMonday, persistedTutorialGroupId, persistedSchedule);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void addScheduleToTutorialGroupWithoutSchedule_asInstructor_shouldAddScheduleAndCreateSessions() throws Exception {
        // given
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule();
        var newSchedule = this.buildExampleSchedule(firstAugustMonday, secondAugustMonday);
        tutorialGroup.setTutorialGroupSchedule(newSchedule);
        // when
        request.putWithResponseBody(getTutorialGroupsPath() + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class, HttpStatus.OK);

        // then
        var persistedTutorialGroup = tutorialGroupRepository.findByIdElseThrow(tutorialGroup.getId());
        var sessions = this.getTutorialGroupSessionsAscending(persistedTutorialGroup.getId());
        assertThat(sessions).hasSize(2);
        var firstAugustMondaySession = sessions.get(0);
        var secondAugustMondaySession = sessions.get(1);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(persistedTutorialGroup.getId()).get();

        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(secondAugustMondaySession, secondAugustMonday, tutorialGroup.getId(), persistedSchedule);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void addScheduleToTutorialGroupWithoutSchedule_scheduledSessionFallsOnAlreadyExistingSession_shouldReturnBadRequest() throws Exception {
        // given
        var tutorialGroup = this.buildAndSaveTutorialGroupWithoutSchedule();
        this.buildAndSaveExampleIndividualTutorialGroupSession(tutorialGroup.getId(), firstAugustMonday);
        var newSchedule = this.buildExampleSchedule(firstAugustMonday, secondAugustMonday);
        tutorialGroup.setTutorialGroupSchedule(newSchedule);

        // when
        request.putWithResponseBody(getTutorialGroupsPath() + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);

        // then
        assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId())).hasSize(1);
        assertThat(tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroup.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void modifyExistingScheduleOfTutorialGroup_shouldRecreateScheduledSessionsButKeepIndividualSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday);
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);

        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, fourthAugustMonday);

        var newSchedule = this.buildExampleSchedule(firstAugustMonday, thirdAugustMonday);
        newSchedule.setRepetitionFrequency(2); // repeat every two weeks
        tutorialGroup.setTutorialGroupSchedule(newSchedule);
        // when
        request.putWithResponseBody(getTutorialGroupsPath() + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class, HttpStatus.OK);

        // then
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(3);
        var firstAugustMondaySession = sessions.get(0);
        var thirdAugustMondaySession = sessions.get(1);
        var fourthAugustMondaySession = sessions.get(2);

        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroup.getId()).get();

        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, tutorialGroup.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(thirdAugustMondaySession, thirdAugustMonday, tutorialGroup.getId(), persistedSchedule);
        // individual session
        this.assertIndividualSessionIsActiveOnDate(fourthAugustMondaySession, fourthAugustMonday, tutorialGroup.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteScheduleOfTutorialGroup_shouldDeleteAllScheduledSessionsButKeepIndividualSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday);
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, thirdAugustMonday);
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        // when
        tutorialGroup.setTutorialGroupSchedule(null);
        tutorialGroup = request.putWithResponseBody(getTutorialGroupsPath() + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class, HttpStatus.OK);
        // then
        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroup.getId());
        assertThat(sessions).hasSize(1);
        var thirdAugustMondaySession = sessions.get(0);
        this.assertIndividualSessionIsActiveOnDate(thirdAugustMondaySession, thirdAugustMonday, tutorialGroup.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteTutorialGroupWithSchedule_shouldDeleteScheduleAndSessions() throws Exception {
        // given
        var tutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday);
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(), tutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();
        this.buildAndSaveExampleIndividualTutorialGroupSession(persistedTutorialGroupId, thirdAugustMonday);
        tutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);

        // when
        request.delete(getTutorialGroupsPath() + tutorialGroup.getId(), HttpStatus.NO_CONTENT);

        // then
        assertThat(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId())).isEmpty();
        assertThat(tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroup.getId())).isEmpty();
        assertThat(tutorialGroupRepository.findById(tutorialGroup.getId())).isEmpty();
    }

}
