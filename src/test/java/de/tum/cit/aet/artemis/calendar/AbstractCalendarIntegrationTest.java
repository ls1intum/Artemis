package de.tum.cit.aet.artemis.calendar;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CourseCalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.repository.CourseCalendarEventRepository;
import de.tum.cit.aet.artemis.calendar.util.CourseCalendarEventUtilService;
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

    static final String EDITOR_LOGIN = TEST_PREFIX + "editor";

    static final String INSTRUCTOR_LOGIN = TEST_PREFIX + "instructor";

    static final String NOT_EDITOR_LOGIN = TEST_PREFIX + "noteditor";

    static final String NOT_INSTRUCTOR_LOGIN = TEST_PREFIX + "notinstructor";

    static final String TEST_TIMEZONE_STRING = "Europe/Berlin";

    static final ZoneId TEST_TIMEZONE = ZoneId.of(TEST_TIMEZONE_STRING);

    static final TypeReference<Map<String, List<CalendarEventDTO>>> GET_CALENDAR_EVENTS_RETURN_TYPE = new TypeReference<Map<String, List<CalendarEventDTO>>>() {
    };

    static final TypeReference<List<CourseCalendarEventDTO>> GET_COURSE_CALENDAR_EVENTS_RETURN_TYPE = new TypeReference<List<CourseCalendarEventDTO>>() {
    };

    static final String PUT_REQUEST_URL = "/api/calendar/course-calendar-event";

    Course course;

    TutorialGroup tutorialGroup;

    List<TutorialGroupSession> tutorialGroupSessions;

    List<CourseCalendarEvent> courseCalendarEvents;

    User student;

    User tutor;

    User editor;

    User instructor;

    @Autowired
    CourseCalendarEventRepository courseCalendarEventRepository;

    @Autowired
    CourseUtilService courseUtilService;

    @Autowired
    private CourseCalendarEventUtilService courseCalendarEventUtilService;

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
        userUtilService.addEditor("editor", TEST_PREFIX + "editor");
        userUtilService.addInstructor("instructor", TEST_PREFIX + "instructor");
        student = userUtilService.getUserByLogin(STUDENT_LOGIN);
        tutor = userUtilService.getUserByLogin(TUTOR_LOGIN);
        editor = userUtilService.getUserByLogin(EDITOR_LOGIN);
        instructor = userUtilService.getUserByLogin(INSTRUCTOR_LOGIN);
    }

    @AfterEach
    void cleanUp() {
        if (courseCalendarEvents != null) {
            courseCalendarEventRepository.deleteAll(courseCalendarEvents);
            courseCalendarEvents = null;
        }
        if (tutorialGroupSessions != null) {
            tutorialGroupSessionRepository.deleteAll(tutorialGroupSessions);
            tutorialGroupSessions = null;
        }
        if (tutorialGroup != null) {
            tutorialGroupRepository.deleteById(tutorialGroup.getId());
            tutorialGroup = null;
        }
        if (course != null) {
            courseRepository.deleteById(course.getId());
            course = null;
        }
        if (student != null) {
            userRepository.deleteById(student.getId());
            student = null;
        }
        if (tutor != null) {
            userRepository.deleteById(tutor.getId());
            tutor = null;
        }
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course} of which {@code student}, {@code tutor}, {@code editor}, and {@code instructor} are part representing the user group their name indicates.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are part of.</li>
     * <li>a series of weekly {@link CourseCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);

        courseCalendarEvents = courseCalendarEventUtilService.createCourseCalendarEvents(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course} of which {@code student}, {@code tutor}, {@code editor}, and {@code instructor} are part representing the user group their name indicates.</li>
     * </ul>
     */
    void setupActiveCourseWithoutCourseWideEventsScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course} of which {@code student}, {@code tutor}, {@code editor}, and {@code instructor} are part representing the user group their name indicates.</li>
     * <li>two {@link CourseCalendarEvent}s spanning multiple days (first: 2 days | second: 3 days) that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithCourseCalendarEventsSpanningMultipleDaysScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 1);
        CourseCalendarEvent event1 = new CourseCalendarEvent();
        event1.setCourse(course);
        event1.setTitle("Session 1");
        event1.setStartDate(course.getStartDate());
        event1.setEndDate(course.getStartDate().plusDays(1));
        event1.setVisibleToStudents(true);
        event1.setVisibleToTutors(true);
        event1.setVisibleToEditors(true);
        event1.setVisibleToInstructors(true);
        CourseCalendarEvent event2 = new CourseCalendarEvent();
        event2.setCourse(course);
        event2.setTitle("Session 1");
        event2.setStartDate(course.getStartDate().plusDays(5));
        event2.setEndDate(course.getStartDate().plusDays(7));
        event2.setVisibleToStudents(true);
        event2.setVisibleToTutors(true);
        event2.setVisibleToEditors(true);
        event2.setVisibleToInstructors(true);
        courseCalendarEventRepository.saveAll(Set.of(event1, event2));
        courseCalendarEvents = List.of(event1, event2);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course} of which {@code student}, {@code tutor}, {@code editor}, and {@code instructor} are part representing the user group their name indicates.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are part of.</li>
     * <li>a series of weekly {@link CourseCalendarEvent}s with visibility alternating between the different user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithMutualExclusiveVisibilityForCourseCalendarEventScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);

        courseCalendarEvents = courseCalendarEventUtilService.createCourseCalendarEventsWithMutuallyExclusiveVisibility(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course}.</li>
     * <li>an editor and instructor with usernames {@code NOT_EDITOR_LOGIN} and {@code NOT_INSTRUCTOR_LOGIN} that are not part of the course.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are part of.</li>
     * <li>a series of weekly {@link CourseCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupUserNotPartOfAnyCourseScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        userUtilService.addEditor("noteditor", TEST_PREFIX + "noteditor");
        userUtilService.addInstructor("notinstructor", TEST_PREFIX + "notinstructor");
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);
        courseCalendarEvents = courseCalendarEventUtilService.createCourseCalendarEvents(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an inactive {@link Course}.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are part of.</li>
     * <li>a series of weekly {@link CourseCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupNonActiveCourseScenario() {
        course = courseUtilService.createNonActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 6, 2);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);
        courseCalendarEvents = courseCalendarEventUtilService.createCourseCalendarEvents(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course}.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are <u><b>not</b></u> part of.</li>
     * <li>a series of weekly {@link CourseCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithoutParticipatedTutorialGroupScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        userUtilService.addTeachingAssistant("tutor", TEST_PREFIX + "othertutor");
        User otherTutor = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "othertutor");
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", otherTutor, new HashSet<>());
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);
        courseCalendarEvents = courseCalendarEventUtilService.createCourseCalendarEvents(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course}.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which <u><b>the first two are cancelled</b></u>. The {@code tutor} and {@code student} are part of
     * the group.</li>
     * <li>a series of weekly {@link CourseCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithCancelledTutorialGroupSessionsScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, true);
        courseCalendarEvents = courseCalendarEventUtilService.createCourseCalendarEvents(course);
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

    String assembleURLForCourseCalendarEventsGetRequest(Long courseId) {
        return "/api/calendar/courses/" + courseId + "/course-calendar-events";
    }

    String assembleURLForCalendarEventsGetRequest(String monthKeys, String timeZone) {
        return "/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone;
    }

    String assembleURLForPostRequest(Long courseId) {
        return "/api/calendar/courses/" + courseId + "/course-calendar-events";
    }

    String assembleURLForDeleteRequest(String eventId) {
        return "/api/calendar/course-calendar-event/" + eventId;
    }
}
