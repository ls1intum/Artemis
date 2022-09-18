package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.*;

public class TutorialGroupsConfigurationIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    @BeforeEach
    void deleteExistingConfiguration() {
        tutorialGroupsConfigurationRepository.deleteAll();
    }

    private void testJustForInstructorEndpoints() throws Exception {
        // Todo
    }

    @Test
    @WithMockUser(value = "instructor42", roles = "INSTRUCTOR")
    void request_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void request_asTutor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void request_asStudent_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void request_asEditor_shouldReturnForbidden() throws Exception {
        this.testJustForInstructorEndpoints();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void getOneOfCourse_asInstructor_shouldReturnTutorialGroupsConfiguration() throws Exception {
        // given
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, exampleTimeZone, firstAugustMonday, firstSeptemberMonday);
        // when
        var configurationFromRequest = request.get(this.getTutorialGroupsConfigurationPath() + configuration.getId(), HttpStatus.OK, TutorialGroupsConfiguration.class);
        // then
        assertThat(configurationFromRequest).isEqualTo(configuration);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupsConfiguration() throws Exception {
        // when
        var configurationFromRequest = request.postWithResponseBody(getTutorialGroupsConfigurationPath(), buildExampleConfiguration(), TutorialGroupsConfiguration.class,
                HttpStatus.CREATED);
        // then
        assertThat(configurationFromRequest).isNotNull();
        this.assertConfigurationStructure(configurationFromRequest, firstAugustMonday, firstSeptemberMonday, exampleTimeZone);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_configurationAlreadyExists_shouldReturnBadRequest() throws Exception {
        // given
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, exampleTimeZone, firstAugustMonday, firstSeptemberMonday);
        // when
        request.postWithResponseBody(getTutorialGroupsConfigurationPath(), buildExampleConfiguration(), TutorialGroupsConfiguration.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupsConfigurationRepository.findAll()).hasSize(1);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_timeZoneChange_deleteTutorialGroupFreePeriodsAndIndividualSessionsAndRecreateScheduledSessions() throws Exception {
        // given
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, exampleTimeZone, firstAugustMonday, firstSeptemberMonday);
        var tutorialGroupWithSchedule = setUpTutorialGroupWithSchedule();
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroup_Id(tutorialGroupWithSchedule.getId()).get();
        this.buildAndSaveExampleIndividualTutorialGroupSession(tutorialGroupWithSchedule.getId(), firstSeptemberMonday);
        databaseUtilService.addTutorialGroupFreeDay(configuration.getId(), fourthAugustMonday, "Holiday");

        var sessions = this.getTutorialGroupSessionsAscending(tutorialGroupWithSchedule.getId());
        assertThat(sessions).hasSize(3);
        var firstAugustMondaySession = sessions.get(0);
        var secondAugustMondaySession = sessions.get(1);
        var firstSeptemberMondaySession = sessions.get(2);
        this.assertScheduledSessionIsActiveOnDate(firstAugustMondaySession, firstAugustMonday, tutorialGroupWithSchedule.getId(), persistedSchedule);
        this.assertScheduledSessionIsActiveOnDate(secondAugustMondaySession, secondAugustMonday, tutorialGroupWithSchedule.getId(), persistedSchedule);
        this.assertIndividualSessionIsActiveOnDate(firstSeptemberMondaySession, firstSeptemberMonday, tutorialGroupWithSchedule.getId());
        assertThat(tutorialGroupFreePeriodRepository.findAll()).hasSize(1);

        // when
        // change time zone to berlin and change end period
        configuration.setTimeZone("Europe/Berlin");
        configuration.setTutorialPeriodEndInclusive(firstSeptemberMonday.toString());
        request.putWithResponseBody(getTutorialGroupsConfigurationPath() + configuration.getId(), configuration, TutorialGroupsConfiguration.class, HttpStatus.OK);
        configuration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(configuration.getId());
        this.assertConfigurationStructure(configuration, firstAugustMonday, firstSeptemberMonday, "Europe/Berlin");

        sessions = this.getTutorialGroupSessionsAscending(tutorialGroupWithSchedule.getId());
        assertThat(sessions).hasSize(2);
        firstAugustMondaySession = sessions.get(0);
        secondAugustMondaySession = sessions.get(1);

        this.assertTutorialGroupSessionProperties(firstAugustMondaySession, Optional.of(persistedSchedule.getId()), tutorialGroupWithSchedule.getId(),
                getDateTimeInBerlinTimeZone(firstAugustMonday, defaultSessionStartHour), getDateTimeInBerlinTimeZone(firstAugustMonday, defaultSessionEndHour),
                persistedSchedule.getLocation(), TutorialGroupSessionStatus.ACTIVE, null);

        this.assertTutorialGroupSessionProperties(secondAugustMondaySession, Optional.of(persistedSchedule.getId()), tutorialGroupWithSchedule.getId(),
                getDateTimeInBerlinTimeZone(secondAugustMonday, defaultSessionStartHour), getDateTimeInBerlinTimeZone(secondAugustMonday, defaultSessionEndHour),
                persistedSchedule.getLocation(), TutorialGroupSessionStatus.ACTIVE, null);
        assertThat(tutorialGroupFreePeriodRepository.findAll()).hasSize(0);
    }

}
