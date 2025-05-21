package de.tum.cit.aet.artemis.calendar;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.repository.TutorialGroupSessionRepository;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

public abstract class AbstractCalendarIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    static final String TEST_PREFIX = "calendarevent";

    static final String STUDENT_LOGIN = TEST_PREFIX + "student";

    static final String TUTOR_LOGIN = TEST_PREFIX + "tutor";

    static final String NOT_STUDENT_LOGIN = TEST_PREFIX + "notstudent";

    static final String NOT_TUTOR_LOGIN = TEST_PREFIX + "nottutor";

    private static final String URL_WITHOUT_QUERY_PARAMETERS = "/api/calendar/calendar-events";

    static final String TEST_TIMEZONE = "Europe/Berlin";

    static final TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>> GET_EVENTS_RETURN_TYPE = new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
    };

    Course course;

    TutorialGroup tutorialGroup;

    List<TutorialGroupSession> tutorialGroupSessions;

    User student;

    User tutor;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private TutorialGroupSessionRepository tutorialGroupSessionRepository;

    @Autowired
    private TutorialGroupTestRepository tutorialGroupRepository;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private UserTestRepository userRepository;

    @BeforeEach
    void createUsers() {
        userUtilService.addStudent("tumuser", TEST_PREFIX + "student");
        userUtilService.addTeachingAssistant("tutor", TEST_PREFIX + "tutor");
        student = userUtilService.getUserByLogin(STUDENT_LOGIN);
        tutor = userUtilService.getUserByLogin(TUTOR_LOGIN);
    }

    @AfterEach
    void cleanUp() {
        tutorialGroupSessionRepository.deleteAll(tutorialGroupSessions);
        tutorialGroupRepository.deleteById(tutorialGroup.getId());
        courseRepository.deleteById(course.getId());
        userRepository.deleteById(student.getId());
        userRepository.deleteById(tutor.getId());
    }

    void setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario() {
        createActiveCourse();
        createParticipatedTutorialGroup();
        createActiveTutorialGroupSessions();
    }

    void setupUserNotPartOfAnyCourseScenario() {
        createActiveCourse();
        createUsersNotPartOfCourse();
        createParticipatedTutorialGroup();
        createActiveTutorialGroupSessions();
    }

    void setupOnlyNonActiveCourseScenario() {
        createNonActiveCourse();
        createParticipatedTutorialGroup();
        createActiveTutorialGroupSessions();
    }

    void setupActiveCourseWithoutParticipatedTutorialGroupScenario() {
        createActiveCourse();
        createNonParticipatedTutorialGroup();
        createActiveTutorialGroupSessions();
    }

    void setupActiveCourseWithParticipatedGroupAndActiveAndCancelledSessionsScenario() {
        createActiveCourse();
        createParticipatedTutorialGroup();
        createActiveAndCancelledTutorialGroupSessions();
    }

    String getMonthsSpanningCurrentTestCourseAsMonthKeys() {
        YearMonth startMonth = YearMonth.from(course.getStartDate());
        YearMonth endMonth = YearMonth.from(course.getEndDate());
        List<String> monthStrings = new LinkedList<>();
        YearMonth current = startMonth;
        while (!current.isAfter(endMonth)) {
            monthStrings.add(current.toString());
            current = current.plusMonths(1);
        }
        return String.join(",", monthStrings);
    }

    String assembleURL(String monthKeys) {
        return URL_WITHOUT_QUERY_PARAMETERS + "?monthKeys=" + monthKeys + "&timeZone=" + TEST_TIMEZONE;
    }

    private void createNonActiveCourse() {
        ZoneId testZone = ZoneId.of(TEST_TIMEZONE);
        ZonedDateTime now = ZonedDateTime.now(testZone).withDayOfMonth(1);
        ZonedDateTime courseStart = now.minusMonths(6);
        ZonedDateTime courseEnd = now.minusMonths(2);
        course = courseUtilService.createCourseWithStartDateAndEndDate(courseStart, courseEnd);
    }

    private void createActiveCourse() {
        ZoneId testZone = ZoneId.of(TEST_TIMEZONE);
        ZonedDateTime now = ZonedDateTime.now(testZone).withDayOfMonth(1);
        ZonedDateTime courseStart = now.minusMonths(1);
        ZonedDateTime courseEnd = now.plusMonths(3);
        course = courseUtilService.createCourseWithStartDateAndEndDate(courseStart, courseEnd);
    }

    private void createUsersNotPartOfCourse() {
        userUtilService.addStudent("notstudent", TEST_PREFIX + "notstudent");
        userUtilService.addTeachingAssistant("nottutor", TEST_PREFIX + "nottutor");
    }

    private void createParticipatedTutorialGroup() {
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
    }

    private void createNonParticipatedTutorialGroup() {
        userUtilService.addTeachingAssistant("tutor", TEST_PREFIX + "othertutor");
        User otherTutor = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "othertutor");
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", otherTutor, new HashSet<>());
    }

    private void createActiveTutorialGroupSessions() {
        ZoneId testZone = ZoneId.of(TEST_TIMEZONE);
        ZonedDateTime courseStart = course.getStartDate().withZoneSameInstant(testZone);
        ZonedDateTime firstSessionStart = courseStart.plusWeeks(1).withHour(12).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime firstSessionEnd = firstSessionStart.plusHours(2);
        tutorialGroupSessions = createWeeklyTutorialGroupSessions(tutorialGroup.getId(), firstSessionStart, firstSessionEnd, 12, 0);
    }

    private void createActiveAndCancelledTutorialGroupSessions() {
        ZoneId testZone = ZoneId.of(TEST_TIMEZONE);
        ZonedDateTime courseStart = course.getStartDate().withZoneSameInstant(testZone);
        ZonedDateTime firstSessionStart = courseStart.plusWeeks(1).withHour(12).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime firstSessionEnd = firstSessionStart.plusHours(2);
        tutorialGroupSessions = createWeeklyTutorialGroupSessions(tutorialGroup.getId(), firstSessionStart, firstSessionEnd, 12, 2);
    }

    private List<TutorialGroupSession> createWeeklyTutorialGroupSessions(Long tutorialGroupId, ZonedDateTime firstSessionStart, ZonedDateTime firstSessionEnd, int repetitionCount,
            int numberOfCancelledSessions) {
        List<TutorialGroupSession> sessions = new LinkedList<>();
        for (int i = 0; i < repetitionCount; i++) {
            ZonedDateTime sessionStart = firstSessionStart.plusWeeks(i);
            ZonedDateTime sessionEnd = firstSessionEnd.plusWeeks(i);

            boolean shouldCancel = i < numberOfCancelledSessions;
            TutorialGroupSession session = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroupId, sessionStart, sessionEnd, null,
                    shouldCancel ? TutorialGroupSessionStatus.CANCELLED : TutorialGroupSessionStatus.ACTIVE, shouldCancel ? "Cancelled for test purposes" : null);

            sessions.add(session);
        }
        return sessions;
    }
}
