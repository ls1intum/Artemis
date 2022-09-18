package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

public class TutorialGroupsConfigurationIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    DatabaseUtilService databaseUtilService;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    TutorialGroupRepository tutorialGroupRepository;

    @Autowired
    TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    TutorialGroupScheduleRepository tutorialGroupScheduleRepository;

    @Autowired
    TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    Long exampleCourseId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        // creating the users student1-student10, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(10, 10, 10, 10);

        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("editor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        var course = this.database.createCourse();
        exampleCourseId = course.getId();
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
    void getOneOfCourse_asInstructor_shouldReturnTutorialGroupFreePeriod() throws Exception {
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, "Europe/Bucharest", LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1));
        var configurationFromRequest = request.get("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + configuration.getId(), HttpStatus.OK,
                TutorialGroupsConfiguration.class);
        assertThat(configurationFromRequest).isEqualTo(configuration);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_asInstructor_shouldCreateTutorialGroupsConfiguration() throws Exception {
        var configurationFromRequest = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/", createConfiguration(),
                TutorialGroupsConfiguration.class, HttpStatus.CREATED);
        assertThat(configurationFromRequest).isNotNull();
        this.assertConfigurationStructure(configurationFromRequest);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void create_configurationAlreadyExists_shouldReturnBadRequest() throws Exception {
        databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, "Europe/Bucharest", LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1));
        request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/", createConfiguration(), TutorialGroupsConfiguration.class,
                HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_timeZoneChange_deleteTutorialGroupFreePeriodsAndIndividualSessionsAndRecreateScheduledSessions() throws Exception {
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, "Europe/Bucharest", LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1));
        var newTutorialGroup = createTutorialGroupWithMondaySchedule(LocalDate.of(2020, 3, 23), LocalDate.of(2020, 3, 30));
        var persistedTutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", newTutorialGroup, TutorialGroup.class,
                HttpStatus.CREATED);
        databaseUtilService.createIndividualTutorialGroupSession(persistedTutorialGroup.getId(), ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("UTC"))).getId();
        databaseUtilService.addTutorialGroupFreeDay(configuration.getId(), LocalDate.of(2022, 8, 8), "Holiday");

        var sessions = getSessionAscending(persistedTutorialGroup);
        assertThat(sessions).hasSize(3);
        var standardTimeSession = sessions.get(0);
        var daylightSavingsTimeSession = sessions.get(1);
        var individualSession = sessions.get(2);
        assertSessionStructure(standardTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 23, 10),
                getUTCZonedDate(2020, 3, 23, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(daylightSavingsTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 30, 9),
                getUTCZonedDate(2020, 3, 30, 10), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(individualSession, null, persistedTutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.ACTIVE,
                null);

        assertThat(tutorialGroupFreePeriodRepository.findAll()).hasSize(1);

        // change time zone to berlin
        configuration.setTimeZone("Europe/Berlin");
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + configuration.getId(), configuration, TutorialGroupsConfiguration.class,
                HttpStatus.OK);
        configuration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(configuration.getId());
        assertThat(configuration.getTimeZone()).isEqualTo("Europe/Berlin");

        sessions = getSessionAscending(persistedTutorialGroup);
        assertThat(sessions).hasSize(2);
        assertSessionStructure(standardTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 23, 10),
                getUTCZonedDate(2020, 3, 23, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(daylightSavingsTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 30, 9),
                getUTCZonedDate(2020, 3, 30, 10), TutorialGroupSessionStatus.ACTIVE, null);
        assertThat(tutorialGroupFreePeriodRepository.findAll()).hasSize(0);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    void update_justPeriodChange_updatePeriodOfConfiguration() throws Exception {
        var configuration = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, "Europe/Bucharest", LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1));
        var newTutorialGroup = createTutorialGroupWithMondaySchedule(LocalDate.of(2020, 3, 23), LocalDate.of(2020, 3, 30));
        var persistedTutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", newTutorialGroup, TutorialGroup.class,
                HttpStatus.CREATED);
        databaseUtilService.createIndividualTutorialGroupSession(persistedTutorialGroup.getId(), ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("UTC"))).getId();
        databaseUtilService.addTutorialGroupFreeDay(configuration.getId(), LocalDate.of(2022, 8, 8), "Holiday");

        var sessions = getSessionAscending(persistedTutorialGroup);
        assertThat(sessions).hasSize(3);
        var standardTimeSession = sessions.get(0);
        var daylightSavingsTimeSession = sessions.get(1);
        var individualSession = sessions.get(2);
        assertSessionStructure(standardTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 23, 10),
                getUTCZonedDate(2020, 3, 23, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(daylightSavingsTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 30, 9),
                getUTCZonedDate(2020, 3, 30, 10), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(individualSession, null, persistedTutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.ACTIVE,
                null);

        assertThat(tutorialGroupFreePeriodRepository.findAll()).hasSize(1);

        // change time zone to berlin
        configuration.setTutorialPeriodStartInclusive(LocalDate.of(2022, 9, 1).toString());
        configuration.setTutorialPeriodEndInclusive(LocalDate.of(2022, 10, 1).toString());
        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/" + configuration.getId(), configuration, TutorialGroupsConfiguration.class,
                HttpStatus.OK);
        configuration = tutorialGroupsConfigurationRepository.findByIdWithEagerTutorialGroupFreePeriodsElseThrow(configuration.getId());
        assertThat(configuration.getTimeZone()).isEqualTo("Europe/Bucharest");
        assertThat(configuration.getTutorialPeriodStartInclusive()).isEqualTo(LocalDate.of(2022, 9, 1).toString());
        assertThat(configuration.getTutorialPeriodEndInclusive()).isEqualTo(LocalDate.of(2022, 10, 1).toString());

        sessions = getSessionAscending(persistedTutorialGroup);
        assertThat(sessions).hasSize(3);
        standardTimeSession = sessions.get(0);
        daylightSavingsTimeSession = sessions.get(1);
        individualSession = sessions.get(2);
        assertSessionStructure(standardTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 23, 10),
                getUTCZonedDate(2020, 3, 23, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(daylightSavingsTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 30, 9),
                getUTCZonedDate(2020, 3, 30, 10), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(individualSession, null, persistedTutorialGroup, getUTCZonedDate(2022, 8, 1, 10), getUTCZonedDate(2022, 8, 1, 11), TutorialGroupSessionStatus.ACTIVE,
                null);

        assertThat(tutorialGroupFreePeriodRepository.findAll()).hasSize(1);
    }

    private TutorialGroupsConfiguration createConfiguration() {
        TutorialGroupsConfiguration tutorialGroupsConfiguration = new TutorialGroupsConfiguration();
        tutorialGroupsConfiguration.setCourse(courseRepository.findById(exampleCourseId).get());
        tutorialGroupsConfiguration.setTutorialPeriodStartInclusive(LocalDate.of(2022, 8, 1).toString());
        tutorialGroupsConfiguration.setTutorialPeriodEndInclusive(LocalDate.of(2022, 9, 1).toString());
        tutorialGroupsConfiguration.setTimeZone("Europe/Bucharest");
        return tutorialGroupsConfiguration;
    }

    private void assertConfigurationStructure(TutorialGroupsConfiguration configuration) {
        assertThat(configuration.getCourse().getId()).isEqualTo(exampleCourseId);
        assertThat(LocalDate.parse(configuration.getTutorialPeriodStartInclusive())).isEqualTo(LocalDate.of(2022, 8, 1));
        assertThat(LocalDate.parse(configuration.getTutorialPeriodEndInclusive())).isEqualTo(LocalDate.of(2022, 9, 1));
        assertThat(configuration.getTimeZone()).isEqualTo("Europe/Bucharest");
    }

    private TutorialGroup createTutorialGroupWithMondaySchedule(LocalDate validFromInclusive, LocalDate validToInclusive) throws Exception {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var newTutorialGroup = new TutorialGroup();
        newTutorialGroup.setCourse(course);
        newTutorialGroup.setTitle("NewTitle");
        newTutorialGroup.setTeachingAssistant(userRepository.findOneByLogin("tutor1").get());

        newTutorialGroup.setTutorialGroupSchedule(createSchedule(validFromInclusive, validToInclusive, 1));

        return newTutorialGroup;
    }

    private TutorialGroupSchedule createSchedule(LocalDate validFromInclusive, LocalDate validToInclusive, Integer weekday) {
        TutorialGroupSchedule newTutorialGroupSchedule = new TutorialGroupSchedule();
        newTutorialGroupSchedule.setDayOfWeek(weekday); // Monday
        newTutorialGroupSchedule.setStartTime(LocalTime.of(12, 0, 0).toString());
        newTutorialGroupSchedule.setEndTime(LocalTime.of(13, 0, 0).toString());
        // monday before dst
        newTutorialGroupSchedule.setValidFromInclusive(validFromInclusive.toString());
        // monday after dst
        newTutorialGroupSchedule.setValidToInclusive(validToInclusive.toString());
        newTutorialGroupSchedule.setLocation("LoremIpsum");
        // every week
        newTutorialGroupSchedule.setRepetitionFrequency(1);
        return newTutorialGroupSchedule;
    }

    private static ZonedDateTime getUTCZonedDate(int year, int month, int dayOfMonth, int hour) {
        return ZonedDateTime.of(year, month, dayOfMonth, hour, 0, 0, 0, ZoneId.of("UTC"));
    }

    private List<TutorialGroupSession> getSessionAscending(TutorialGroup tutorialGroup) {
        var sessions = new ArrayList<>(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId()).stream().toList());
        sessions.sort(Comparator.comparing(TutorialGroupSession::getStart));
        return sessions;
    }

    private static void assertSessionStructure(TutorialGroupSession session, TutorialGroupSchedule schedule, TutorialGroup tutorialGroup, ZonedDateTime start, ZonedDateTime end,
            TutorialGroupSessionStatus status, String status_explanation) {
        assertThat(session.getStart()).isEqualTo(start);
        assertThat(session.getEnd()).isEqualTo(end);
        assertThat(session.getTutorialGroup().getId()).isEqualTo(tutorialGroup.getId());

        if (schedule != null) {
            assertThat(session.getLocation()).isEqualTo(schedule.getLocation());
            assertThat(session.getTutorialGroupSchedule().getId()).isEqualTo(schedule.getId());
        }
        else {
            assertThat(session.getTutorialGroupSchedule()).isNull();
        }
        assertThat(session.getStatus()).isEqualTo(status);
        assertThat(session.getStatusExplanation()).isEqualTo(status_explanation);
    }

}
