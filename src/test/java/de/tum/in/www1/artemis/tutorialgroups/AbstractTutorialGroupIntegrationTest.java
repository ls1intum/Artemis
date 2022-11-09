package de.tum.in.www1.artemis.tutorialgroups;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.google.common.collect.ImmutableSet;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupsConfiguration;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.*;
import de.tum.in.www1.artemis.service.tutorialgroups.TutorialGroupService;
import de.tum.in.www1.artemis.util.CourseTestService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.ModelFactory;

/**
 * Contains useful methods for testing the tutorial groups feature.
 */
abstract class AbstractTutorialGroupIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseTestService courseTestService;

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
    TutorialGroupFreePeriodRepository tutorialGroupFreePeriodRepository;

    @Autowired
    TutorialGroupsConfigurationRepository tutorialGroupsConfigurationRepository;

    @Autowired
    TutorialGroupRegistrationRepository tutorialGroupRegistrationRepository;

    @Autowired
    TutorialGroupService tutorialGroupService;

    Long exampleCourseId;

    Long exampleConfigurationId;

    Long exampleOneTutorialGroupId;

    Long exampleTwoTutorialGroupId;

    String exampleTimeZone = "Europe/Bucharest";

    Integer defaultSessionStartHour = 10;

    Integer defaultSessionEndHour = 12;

    LocalDate firstAugustMonday = LocalDate.of(2022, 8, 1);

    LocalDate secondAugustMonday = LocalDate.of(2022, 8, 8);

    LocalDate thirdAugustMonday = LocalDate.of(2022, 8, 15);

    LocalDate fourthAugustMonday = LocalDate.of(2022, 8, 22);

    LocalDate firstSeptemberMonday = LocalDate.of(2022, 9, 5);

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

        // Add registration number to student 8
        User student8 = userRepository.findOneByLogin("student8").get();
        student8.setRegistrationNumber("123456");
        userRepository.save(student8);
        // Add registration number to student 9
        User student9 = userRepository.findOneByLogin("student9").get();
        student9.setRegistrationNumber("654321");
        userRepository.save(student9);

        var course = this.database.createCourse();
        course.setTimeZone(exampleTimeZone);
        courseRepository.save(course);

        exampleCourseId = course.getId();

        exampleConfigurationId = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1)).getId();

        exampleOneTutorialGroupId = databaseUtilService
                .createTutorialGroup(exampleCourseId, "ExampleTitle1", "LoremIpsum1", 10, false, "LoremIpsum1", Language.ENGLISH, userRepository.findOneByLogin("tutor1").get(),
                        ImmutableSet.of(userRepository.findOneByLogin("student1").get(), userRepository.findOneByLogin("student2").get(),
                                userRepository.findOneByLogin("student3").get(), userRepository.findOneByLogin("student4").get(), userRepository.findOneByLogin("student5").get()))
                .getId();

        exampleTwoTutorialGroupId = databaseUtilService.createTutorialGroup(exampleCourseId, "ExampleTitle2", "LoremIpsum2", 10, true, "LoremIpsum2", Language.GERMAN,
                userRepository.findOneByLogin("tutor2").get(), ImmutableSet.of(userRepository.findOneByLogin("student6").get(), userRepository.findOneByLogin("student7").get()))
                .getId();

    }

    // === Abstract Methods ===

    abstract void testJustForInstructorEndpoints() throws Exception;

    // === Common Tests ===

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

    // === Paths ===
    String getTutorialGroupsPath() {
        return "/api/courses/" + exampleCourseId + "/tutorial-groups/";
    }

    String getTutorialGroupsConfigurationPath() {
        return "/api/courses/" + exampleCourseId + "/tutorial-groups-configuration/";
    }

    String getTutorialGroupFreePeriodsPath() {
        return this.getTutorialGroupsConfigurationPath() + exampleConfigurationId + "/tutorial-free-periods/";
    }

    String getSessionsPathOfDefaultTutorialGroup() {
        return this.getTutorialGroupsPath() + exampleOneTutorialGroupId + "/sessions/";
    }

    String getSessionsPathOfTutorialGroup(Long tutorialGroupId) {
        return this.getTutorialGroupsPath() + tutorialGroupId + "/sessions/";
    }

    // === UTILS ===
    TutorialGroupSession buildAndSaveExampleIndividualTutorialGroupSession(Long tutorialGroupId, LocalDate localDate) {
        return databaseUtilService.createIndividualTutorialGroupSession(tutorialGroupId, getExampleSessionStartOnDate(localDate), getExampleSessionEndOnDate(localDate));
    }

    TutorialGroupsConfiguration buildExampleConfiguration() {
        TutorialGroupsConfiguration tutorialGroupsConfiguration = new TutorialGroupsConfiguration();
        tutorialGroupsConfiguration.setCourse(courseRepository.findById(exampleCourseId).get());
        tutorialGroupsConfiguration.setTutorialPeriodStartInclusive(firstAugustMonday.toString());
        tutorialGroupsConfiguration.setTutorialPeriodEndInclusive(firstSeptemberMonday.toString());
        return tutorialGroupsConfiguration;
    }

    TutorialGroupSchedule buildExampleSchedule(LocalDate validFromInclusive, LocalDate validToInclusive) {
        TutorialGroupSchedule newTutorialGroupSchedule = new TutorialGroupSchedule();
        newTutorialGroupSchedule.setDayOfWeek(1);
        newTutorialGroupSchedule.setStartTime(LocalTime.of(defaultSessionStartHour, 0, 0).toString());
        newTutorialGroupSchedule.setEndTime(LocalTime.of(defaultSessionEndHour, 0, 0).toString());
        newTutorialGroupSchedule.setValidFromInclusive(validFromInclusive.toString());
        newTutorialGroupSchedule.setValidToInclusive(validToInclusive.toString());
        newTutorialGroupSchedule.setLocation("LoremIpsum");
        newTutorialGroupSchedule.setRepetitionFrequency(1);
        return newTutorialGroupSchedule;
    }

    TutorialGroup buildAndSaveTutorialGroupWithoutSchedule() {
        return databaseUtilService.createTutorialGroup(exampleCourseId, "LoremIpsum", "LoremIpsum", 10, false, "Garching", Language.ENGLISH,
                userRepository.findOneByLogin("tutor1").get(), Set.of(userRepository.findOneByLogin("student1").get(), userRepository.findOneByLogin("student2").get()));
    }

    TutorialGroup buildTutorialGroupWithoutSchedule() {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var tutorialGroup = new TutorialGroup();
        tutorialGroup.setCourse(course);
        tutorialGroup.setTitle("NewTitle");
        tutorialGroup.setTeachingAssistant(userRepository.findOneByLogin("tutor1").get());
        return tutorialGroup;
    }

    TutorialGroup buildTutorialGroupWithExampleSchedule(LocalDate validFromInclusive, LocalDate validToInclusive) {
        var course = courseRepository.findWithEagerLearningGoalsById(exampleCourseId).get();
        var newTutorialGroup = new TutorialGroup();
        newTutorialGroup.setCourse(course);
        newTutorialGroup.setTitle("NewTitle");
        newTutorialGroup.setTeachingAssistant(userRepository.findOneByLogin("tutor1").get());

        newTutorialGroup.setTutorialGroupSchedule(this.buildExampleSchedule(validFromInclusive, validToInclusive));

        return newTutorialGroup;
    }

    TutorialGroup setUpTutorialGroupWithSchedule() throws Exception {
        var newTutorialGroup = this.buildTutorialGroupWithExampleSchedule(firstAugustMonday, secondAugustMonday);
        var scheduleToCreate = newTutorialGroup.getTutorialGroupSchedule();
        var persistedTutorialGroupId = request.postWithResponseBody(getTutorialGroupsPath(), newTutorialGroup, TutorialGroup.class, HttpStatus.CREATED).getId();

        newTutorialGroup = tutorialGroupRepository.findByIdElseThrow(persistedTutorialGroupId);
        this.assertTutorialGroupPersistedWithSchedule(newTutorialGroup, scheduleToCreate);
        return newTutorialGroup;
    }

    List<TutorialGroupSession> getTutorialGroupSessionsAscending(Long tutorialGroupId) {
        var sessions = new ArrayList<>(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroupId).stream().toList());
        sessions.sort(Comparator.comparing(TutorialGroupSession::getStart));
        return sessions;
    }

    ZonedDateTime getExampleSessionStartOnDate(LocalDate date) {
        return getDateTimeInExampleTimeZone(date, defaultSessionStartHour);
    }

    ZonedDateTime getExampleSessionEndOnDate(LocalDate date) {
        return getDateTimeInExampleTimeZone(date, defaultSessionEndHour);
    }

    ZonedDateTime getDateTimeInExampleTimeZone(LocalDate date, int hour) {
        return ZonedDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, 0, 0, 0, ZoneId.of(this.exampleTimeZone));
    }

    ZonedDateTime getDateTimeInBerlinTimeZone(LocalDate date, int hour) {
        return ZonedDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, 0, 0, 0, ZoneId.of("Europe/Berlin"));
    }

    // === ASSERTIONS ===

    void assertIndividualSessionIsActiveOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.empty(), tutorialGroupId, getExampleSessionStartOnDate(date), getExampleSessionEndOnDate(date),
                "LoremIpsum", TutorialGroupSessionStatus.ACTIVE, null);
    }

    void assertIndividualSessionIsCancelledOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, String statusExplanation) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.empty(), tutorialGroupId, getExampleSessionStartOnDate(date), getExampleSessionEndOnDate(date),
                "LoremIpsum", TutorialGroupSessionStatus.CANCELLED, statusExplanation);
    }

    void assertScheduledSessionIsActiveOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, TutorialGroupSchedule schedule) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.of(schedule.getId()), tutorialGroupId, getExampleSessionStartOnDate(date),
                getExampleSessionEndOnDate(date), schedule.getLocation(), TutorialGroupSessionStatus.ACTIVE, null);
    }

    void assertScheduledSessionIsCancelledOnDate(TutorialGroupSession sessionToCheck, LocalDate date, Long tutorialGroupId, TutorialGroupSchedule schedule) {
        this.assertTutorialGroupSessionProperties(sessionToCheck, Optional.of(schedule.getId()), tutorialGroupId, getExampleSessionStartOnDate(date),
                getExampleSessionEndOnDate(date), schedule.getLocation(), TutorialGroupSessionStatus.CANCELLED, "Holiday");
    }

    void assertTutorialGroupPersistedWithSchedule(TutorialGroup tutorialGroupToCheck, TutorialGroupSchedule expectedSchedule) {
        assertThat(tutorialGroupToCheck.getId()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule().getId()).isNotNull();
        assertThat(tutorialGroupToCheck.getTutorialGroupSchedule().sameSchedule(expectedSchedule)).isTrue();
    }

    void assertTutorialGroupSessionProperties(TutorialGroupSession tutorialGroupSessionToCheck, Optional<Long> expectedScheduleId, Long expectedTutorialGroupId,
            ZonedDateTime expectedStart, ZonedDateTime expectedEnd, String expectedLocation, TutorialGroupSessionStatus expectedStatus, String expectedStatusExplanation) {
        assertThat(tutorialGroupSessionToCheck.getStart()).isEqualTo(expectedStart);
        assertThat(tutorialGroupSessionToCheck.getEnd()).isEqualTo(expectedEnd);
        assertThat(tutorialGroupSessionToCheck.getTutorialGroup().getId()).isEqualTo(expectedTutorialGroupId);
        expectedScheduleId.ifPresent(scheduleId -> assertThat(tutorialGroupSessionToCheck.getTutorialGroupSchedule().getId()).isEqualTo(scheduleId));
        assertThat(tutorialGroupSessionToCheck.getLocation()).isEqualTo(expectedLocation);
        assertThat(tutorialGroupSessionToCheck.getStatus()).isEqualTo(expectedStatus);
        assertThat(tutorialGroupSessionToCheck.getStatusExplanation()).isEqualTo(expectedStatusExplanation);
    }

    void assertConfigurationStructure(TutorialGroupsConfiguration configuration, LocalDate expectedPeriodStart, LocalDate expectedPeriodEnd) {
        assertThat(configuration.getCourse().getId()).isEqualTo(exampleCourseId);
        assertThat(LocalDate.parse(configuration.getTutorialPeriodStartInclusive())).isEqualTo(expectedPeriodStart);
        assertThat(LocalDate.parse(configuration.getTutorialPeriodEndInclusive())).isEqualTo(expectedPeriodEnd);
    }

}
