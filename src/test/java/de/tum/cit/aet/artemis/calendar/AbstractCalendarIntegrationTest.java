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
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createActiveTutorialGroupSessions(tutorialGroup, course, 12);
    }

    void setupUserNotPartOfAnyCourseScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE), 1, 3);
        userUtilService.addStudent("notstudent", TEST_PREFIX + "notstudent");
        userUtilService.addTeachingAssistant("nottutor", TEST_PREFIX + "nottutor");
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createActiveTutorialGroupSessions(tutorialGroup, course, 12);
    }

    void setupNonActiveCourseScenario() {
        course = courseUtilService.createNonActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE), 6, 2);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createActiveTutorialGroupSessions(tutorialGroup, course, 12);
    }

    void setupActiveCourseWithoutParticipatedTutorialGroupScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE), 1, 3);
        userUtilService.addTeachingAssistant("tutor", TEST_PREFIX + "othertutor");
        User otherTutor = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "othertutor");
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", otherTutor, new HashSet<>());
        tutorialGroupSessions = tutorialGroupUtilService.createActiveTutorialGroupSessions(tutorialGroup, course, 12);
    }

    void setupActiveCourseWithParticipatedGroupAndActiveAndCancelledSessionsScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createActiveAndCancelledTutorialGroupSessions(tutorialGroup, course, 12, 2);
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
}
