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

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.enumeration.TutorialGroupSessionStatus;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroup;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSchedule;
import de.tum.in.www1.artemis.domain.tutorialgroups.TutorialGroupSession;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupScheduleRepository;
import de.tum.in.www1.artemis.repository.tutorialgroups.TutorialGroupSessionRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;

public class TutorialGroupScheduleIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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

    Long exampleCourseId;

    Long exampleConfigurationId;

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @BeforeEach
    void setupTestScenario() {
        // creating the users student1-student10, tutor1-tutor10, editor1-editor10 and instructor1-instructor10
        this.database.addUsers(10, 10, 10, 10);

        var course = this.database.createCourse();
        exampleCourseId = course.getId();

        exampleConfigurationId = databaseUtilService.createTutorialGroupConfiguration(exampleCourseId, "Europe/Bucharest", LocalDate.of(2022, 8, 1), LocalDate.of(2022, 9, 1))
                .getId();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_periodCoversDSTChange_shouldHandleDaylightSavingTimeSwitchCorrectly() throws Exception {
        var newTutorialGroup = createTutorialGroupWithMondaySchedule(LocalDate.of(2020, 3, 23), LocalDate.of(2020, 3, 30));

        var persistedTutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", newTutorialGroup, TutorialGroup.class,
                HttpStatus.CREATED);

        assertTutorialGroupWasCreatedWithSchedule(newTutorialGroup, persistedTutorialGroup);

        var sessions = getSessionAscending(persistedTutorialGroup);
        var standardTimeSession = sessions.get(0);
        var daylightSavingsTimeSession = sessions.get(1);
        assertSessionStructure(standardTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 23, 10),
                getUTCZonedDate(2020, 3, 23, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(daylightSavingsTimeSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2020, 3, 30, 9),
                getUTCZonedDate(2020, 3, 30, 10), TutorialGroupSessionStatus.ACTIVE, null);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createNewTutorialGroupWithSchedule_sessionFallsOnTutorialGroupFreeDay_shouldSetSessionCancelled() throws Exception {
        databaseUtilService.createTutorialGroupFreeDay(exampleConfigurationId, LocalDate.of(2022, 8, 8), "Holiday");

        var newTutorialGroup = createTutorialGroupWithMondaySchedule(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 8));
        var persistedTutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", newTutorialGroup, TutorialGroup.class,
                HttpStatus.CREATED);

        assertTutorialGroupWasCreatedWithSchedule(newTutorialGroup, persistedTutorialGroup);

        var sessions = getSessionAscending(persistedTutorialGroup);
        var normalSession = sessions.get(0);
        var holidaySession = sessions.get(1);
        assertSessionStructure(normalSession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2022, 8, 1, 9),
                getUTCZonedDate(2022, 8, 1, 10), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(holidaySession, persistedTutorialGroup.getTutorialGroupSchedule(), persistedTutorialGroup, getUTCZonedDate(2022, 8, 8, 9),
                getUTCZonedDate(2022, 8, 8, 10), TutorialGroupSessionStatus.CANCELLED, "Holiday");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void addScheduleToTutorialGroupWithoutSchedule_scheduledSessionFallsOnAlreadyExistingSession_shouldReturnBadRequest() throws Exception {
        TutorialGroup tutorialGroup = createAndSaveTutorialGroupWithoutSchedule();
        databaseUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), ZonedDateTime.of(2022, 8, 1, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 1, 11, 0, 0, 0, ZoneId.of("UTC")));

        tutorialGroup.setTutorialGroupSchedule(createSchedule(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 8), 1));

        request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class, HttpStatus.BAD_REQUEST);
        assertThat(tutorialGroupSessionRepository.findAll()).hasSize(1);
        assertThat(tutorialGroupScheduleRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void modifyScheduleOfTutorialGroup_shouldRecreateScheduledSessionsButKeepIndividualSessions() throws Exception {
        TutorialGroup tutorialGroup = createTutorialGroupWithMondaySchedule(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 8));
        tutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.CREATED);

        databaseUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), ZonedDateTime.of(2022, 8, 5, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 5, 11, 0, 0, 0, ZoneId.of("UTC")));

        tutorialGroup.setTutorialGroupSchedule(createSchedule(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 17), 2));
        tutorialGroup = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class,
                HttpStatus.OK);
        assertThat(tutorialGroupSessionRepository.findAll()).hasSize(4);
        assertThat(tutorialGroupScheduleRepository.findAll()).hasSize(1);
        var sessions = getSessionAscending(tutorialGroup);
        var firstSession = sessions.get(0);
        var secondSession = sessions.get(1);
        var thirdSession = sessions.get(2);
        var fourthSession = sessions.get(3);

        assertSessionStructure(firstSession, tutorialGroup.getTutorialGroupSchedule(), tutorialGroup, getUTCZonedDate(2022, 8, 2, 9), getUTCZonedDate(2022, 8, 2, 10),
                TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(secondSession, null, tutorialGroup, getUTCZonedDate(2022, 8, 5, 10), getUTCZonedDate(2022, 8, 5, 11), TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(thirdSession, tutorialGroup.getTutorialGroupSchedule(), tutorialGroup, getUTCZonedDate(2022, 8, 9, 9), getUTCZonedDate(2022, 8, 9, 10),
                TutorialGroupSessionStatus.ACTIVE, null);
        assertSessionStructure(fourthSession, tutorialGroup.getTutorialGroupSchedule(), tutorialGroup, getUTCZonedDate(2022, 8, 16, 9), getUTCZonedDate(2022, 8, 16, 10),
                TutorialGroupSessionStatus.ACTIVE, null);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteScheduleOfTutorialGroup_shouldDeleteAllScheduledSessionsButKeepIndividualSessions() throws Exception {
        TutorialGroup tutorialGroup = createTutorialGroupWithMondaySchedule(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 8));
        tutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.CREATED);

        databaseUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), ZonedDateTime.of(2022, 8, 5, 10, 0, 0, 0, ZoneId.of("UTC")),
                ZonedDateTime.of(2022, 8, 5, 11, 0, 0, 0, ZoneId.of("UTC")));

        tutorialGroup.setTutorialGroupSchedule(null);
        tutorialGroup = request.putWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroup.getId(), tutorialGroup, TutorialGroup.class,
                HttpStatus.OK);
        assertThat(tutorialGroupSessionRepository.findAll()).hasSize(1);
        assertThat(tutorialGroupScheduleRepository.findAll()).isEmpty();
        var sessions = getSessionAscending(tutorialGroup);

        assertSessionStructure(sessions.get(0), null, tutorialGroup, getUTCZonedDate(2022, 8, 5, 10), getUTCZonedDate(2022, 8, 5, 11), TutorialGroupSessionStatus.ACTIVE, null);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteTutorialGroupWithSchedule_shouldDeleteScheduleAndSessions() throws Exception {
        TutorialGroup tutorialGroup = createTutorialGroupWithMondaySchedule(LocalDate.of(2022, 8, 1), LocalDate.of(2022, 8, 8));
        tutorialGroup = request.postWithResponseBody("/api/courses/" + exampleCourseId + "/tutorial-groups", tutorialGroup, TutorialGroup.class, HttpStatus.CREATED);

        request.delete("/api/courses/" + exampleCourseId + "/tutorial-groups/" + tutorialGroup.getId(), HttpStatus.NO_CONTENT);
        assertThat(tutorialGroupSessionRepository.findAll()).isEmpty();
        assertThat(tutorialGroupScheduleRepository.findAll()).isEmpty();
        assertThat(tutorialGroupRepository.findAll()).isEmpty();
    }

    private TutorialGroup createAndSaveTutorialGroupWithoutSchedule() {
        return databaseUtilService.createTutorialGroup(exampleCourseId, "LoremIpsum", "LoremIpsum", 10, false, "Garching", Language.ENGLISH,
                userRepository.findOneByLogin("tutor1").get(), Set.of(userRepository.findOneByLogin("student1").get(), userRepository.findOneByLogin("student2").get()));
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

    private List<TutorialGroupSession> getSessionAscending(TutorialGroup tutorialGroup) {
        var sessions = new ArrayList<>(tutorialGroupSessionRepository.findAllByTutorialGroupId(tutorialGroup.getId()).stream().toList());
        sessions.sort(Comparator.comparing(TutorialGroupSession::getStart));
        return sessions;
    }

    private static void assertTutorialGroupWasCreatedWithSchedule(TutorialGroup newTutorialGroup, TutorialGroup persistedTutorialGroup) {
        assertThat(persistedTutorialGroup.getId()).isNotNull();
        assertThat(persistedTutorialGroup.getTutorialGroupSchedule()).isNotNull();
        assertThat(persistedTutorialGroup.getTutorialGroupSchedule().getId()).isNotNull();
        assertThat(persistedTutorialGroup.getTutorialGroupSchedule().sameSchedule(newTutorialGroup.getTutorialGroupSchedule())).isTrue();
    }

    private static ZonedDateTime getUTCZonedDate(int year, int month, int dayOfMonth, int hour) {
        return ZonedDateTime.of(year, month, dayOfMonth, hour, 0, 0, 0, ZoneId.of("UTC"));
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
