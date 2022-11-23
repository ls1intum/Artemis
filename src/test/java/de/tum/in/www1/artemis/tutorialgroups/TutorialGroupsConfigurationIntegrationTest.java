package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.*;

class TutorialGroupsConfigurationIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    @BeforeEach
    void deleteExistingConfiguration() {
        deleteExampleConfiguration();
    }

    private void deleteExampleConfiguration() {
        Course course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(exampleCourseId);
        TutorialGroupsConfiguration configuration = course.getTutorialGroupsConfiguration();
        if (configuration != null) {
            course.setTutorialGroupsConfiguration(null);
            configuration.setCourse(null);
            courseRepository.save(course);
            tutorialGroupsConfigurationRepository.delete(configuration);
        }
    }

    @Override
    void testJustForInstructorEndpoints() throws Exception {
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, firstAugustMonday, firstSeptemberMonday);
        request.putWithResponseBody(getTutorialGroupsConfigurationPath() + configuration.getId(), configuration, TutorialGroupsConfiguration.class, HttpStatus.FORBIDDEN);
        this.deleteExampleConfiguration();
        request.postWithResponseBody(getTutorialGroupsConfigurationPath(), buildExampleConfiguration(), TutorialGroupsConfiguration.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    void getOneOfCourse_asStudent_shouldReturnTutorialGroupsConfiguration() throws Exception {
        // given
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, firstAugustMonday, firstSeptemberMonday);
        // when
        var configurationFromRequest = request.get(this.getTutorialGroupsConfigurationPath(), HttpStatus.OK, TutorialGroupsConfiguration.class);
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
        this.assertConfigurationStructure(configurationFromRequest, firstAugustMonday, firstSeptemberMonday);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_configurationAlreadyExists_shouldReturnBadRequest() throws Exception {
        // given
        databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, firstAugustMonday, firstSeptemberMonday);
        // when
        request.postWithResponseBody(getTutorialGroupsConfigurationPath(), buildExampleConfiguration(), TutorialGroupsConfiguration.class, HttpStatus.BAD_REQUEST);
        // then
        assertThat(tutorialGroupsConfigurationRepository.findByCourseIdWithEagerTutorialGroupFreePeriods(exampleCourseId)).isNotEmpty();
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_periodChange_deleteTutorialGroupFreePeriodsAndIndividualSessionsAndRecreateScheduledSessions() throws Exception {
        // given
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, firstAugustMonday, firstSeptemberMonday);

        // when
        configuration.setTutorialPeriodEndInclusive(firstSeptemberMonday.toString());
        request.putWithResponseBody(getTutorialGroupsConfigurationPath() + configuration.getId(), configuration, TutorialGroupsConfiguration.class, HttpStatus.OK);
        // then
        configuration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(configuration.getId());
        this.assertConfigurationStructure(configuration, firstAugustMonday, firstSeptemberMonday);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void updateCourse_timeZoneChange_deleteTutorialGroupFreePeriodsAndIndividualSessionsAndRecreateScheduledSessions() throws Exception {
        // given
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, firstAugustMonday, firstSeptemberMonday);
        var tutorialGroupWithSchedule = setUpTutorialGroupWithSchedule();
        var persistedSchedule = tutorialGroupScheduleRepository.findByTutorialGroupId(tutorialGroupWithSchedule.getId()).get();
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
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).hasSize(1);

        // when
        // change time zone to berlin and change end period
        var course = courseRepository.findByIdWithOrganizationsAndLearningGoalsAndOnlineConfigurationElseThrow(exampleCourseId);
        course.setTimeZone("Europe/Berlin");
        course.setTutorialGroupsConfiguration(null);

        request.getMvc().perform(courseTestService.buildUpdateCourse(course.getId(), course)).andExpect(status().isOk()).andReturn();
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());

        course = courseRepository.findByIdWithEagerTutorialGroupConfigurationElseThrow(exampleCourseId);
        assertThat(course.getTutorialGroupsConfiguration()).isNotNull();

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
        assertThat(tutorialGroupFreePeriodRepository.findAllByTutorialGroupsConfigurationCourseId(exampleCourseId)).hasSize(0);

    }

}
