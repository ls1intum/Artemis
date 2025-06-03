package de.tum.cit.aet.artemis.calendar;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.calendar.domain.CoursewideCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CoursewideCalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.repository.CoursewideCalendarEventRepository;
import de.tum.cit.aet.artemis.calendar.util.CoursewideCalendarEventUtilService;
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

    @Autowired
    private CoursewideCalendarEventUtilService coursewideCalendarEventUtilService;

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

    @Autowired
    CoursewideCalendarEventRepository coursewideCalendarEventRepository;

    @Autowired
    CourseUtilService courseUtilService;

    static final String TEST_PREFIX = "calendarevent";

    static final String STUDENT_LOGIN = TEST_PREFIX + "student";

    static final String TUTOR_LOGIN = TEST_PREFIX + "tutor";

    static final String EDITOR_LOGIN = TEST_PREFIX + "editor";

    static final String INSTRUCTOR_LOGIN = TEST_PREFIX + "instructor";

    static final String NOT_EDITOR_LOGIN = TEST_PREFIX + "noteditor";

    static final String NOT_INSTRUCTOR_LOGIN = TEST_PREFIX + "notinstructor";

    static final String TEST_TIMEZONE_STRING = "Europe/Berlin";

    static final ZoneId TEST_TIMEZONE = ZoneId.of(TEST_TIMEZONE_STRING);

    static final Comparator<ZonedDateTime> TIMESTAMP_COMPARATOR = Comparator.comparing(zdt -> zdt.toInstant().truncatedTo(ChronoUnit.MILLIS));

    static final TypeReference<Map<String, List<CalendarEventDTO>>> GET_CALENDAR_EVENTS_RETURN_TYPE = new TypeReference<Map<String, List<CalendarEventDTO>>>() {
    };

    static final TypeReference<List<CoursewideCalendarEventDTO>> GET_COURSE_CALENDAR_EVENTS_RETURN_TYPE = new TypeReference<List<CoursewideCalendarEventDTO>>() {
    };

    static final String COURSEWIDE_CALENDAR_EVENT_PUT_REQUEST_URL = "/api/calendar/coursewide-calendar-event";

    Course course;

    TutorialGroup tutorialGroup;

    List<TutorialGroupSession> tutorialGroupSessions;

    List<CoursewideCalendarEvent> coursewideCalendarEvents;

    User student;

    User tutor;

    User editor;

    User instructor;

    @BeforeEach
    void createUsers() {
        userUtilService.addStudent("tumuser", STUDENT_LOGIN);
        userUtilService.addTeachingAssistant("tutor", TUTOR_LOGIN);
        userUtilService.addEditor("editor", EDITOR_LOGIN);
        userUtilService.addInstructor("instructor", INSTRUCTOR_LOGIN);
        student = userUtilService.getUserByLogin(STUDENT_LOGIN);
        tutor = userUtilService.getUserByLogin(TUTOR_LOGIN);
        editor = userUtilService.getUserByLogin(EDITOR_LOGIN);
        instructor = userUtilService.getUserByLogin(INSTRUCTOR_LOGIN);
    }

    @AfterEach
    void cleanUp() {
        if (coursewideCalendarEvents != null) {
            coursewideCalendarEventRepository.deleteAll(coursewideCalendarEvents);
            coursewideCalendarEvents = null;
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
     * <li>a series of weekly {@link CoursewideCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);

        coursewideCalendarEvents = coursewideCalendarEventUtilService.createCoursewideCalendarEvents(course);
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
     * <li>two {@link CoursewideCalendarEvent}s spanning multiple days (first: 2 days | second: 3 days) that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithCoursewideCalendarEventsSpanningMultipleDaysScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 1);
        CoursewideCalendarEvent event1 = new CoursewideCalendarEvent();
        event1.setCourse(course);
        event1.setTitle("Session 1");
        event1.setStartDate(course.getStartDate());
        event1.setEndDate(course.getStartDate().plusDays(1));
        event1.setVisibleToStudents(true);
        event1.setVisibleToTutors(true);
        event1.setVisibleToEditors(true);
        event1.setVisibleToInstructors(true);
        CoursewideCalendarEvent event2 = new CoursewideCalendarEvent();
        event2.setCourse(course);
        event2.setTitle("Session 1");
        event2.setStartDate(course.getStartDate().plusDays(5));
        event2.setEndDate(course.getStartDate().plusDays(7));
        event2.setVisibleToStudents(true);
        event2.setVisibleToTutors(true);
        event2.setVisibleToEditors(true);
        event2.setVisibleToInstructors(true);
        coursewideCalendarEventRepository.saveAll(Set.of(event1, event2));
        coursewideCalendarEvents = List.of(event1, event2);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course} of which {@code student}, {@code tutor}, {@code editor}, and {@code instructor} are part representing the user group their name indicates.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are part of.</li>
     * <li>a series of weekly {@link CoursewideCalendarEvent}s with visibility alternating between the different user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithMutualExclusiveVisibilityForCoursewideCalendarEventScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);

        coursewideCalendarEvents = coursewideCalendarEventUtilService.createCoursewideCalendarEventsWithMutuallyExclusiveVisibility(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course}.</li>
     * <li>an editor and instructor with usernames {@code NOT_EDITOR_LOGIN} and {@code NOT_INSTRUCTOR_LOGIN} that are not part of the course.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are part of.</li>
     * <li>a series of weekly {@link CoursewideCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupUserNotPartOfAnyCourseScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        userUtilService.addEditor("noteditor", NOT_EDITOR_LOGIN);
        userUtilService.addInstructor("notinstructor", NOT_INSTRUCTOR_LOGIN);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);
        coursewideCalendarEvents = coursewideCalendarEventUtilService.createCoursewideCalendarEvents(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an inactive {@link Course}.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are part of.</li>
     * <li>a series of weekly {@link CoursewideCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupNonActiveCourseScenario() {
        course = courseUtilService.createNonActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 6, 2);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);
        coursewideCalendarEvents = coursewideCalendarEventUtilService.createCoursewideCalendarEvents(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course}.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which {@code tutor} and {@code student} are <u><b>not</b></u> part of.</li>
     * <li>a series of weekly {@link CoursewideCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithoutParticipatedTutorialGroupScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        userUtilService.addTeachingAssistant("tutor", TEST_PREFIX + "othertutor");
        User otherTutor = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "othertutor");
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", otherTutor, new HashSet<>());
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, false);
        coursewideCalendarEvents = coursewideCalendarEventUtilService.createCoursewideCalendarEvents(course);
    }

    /**
     * Creates a scenario that includes:
     * <ul>
     * <li>an active {@link Course}.</li>
     * <li>one {@link TutorialGroup} with weekly {@link TutorialGroupSession}s of which <u><b>the first two are cancelled</b></u>. The {@code tutor} and {@code student} are part of
     * the group.</li>
     * <li>a series of weekly {@link CoursewideCalendarEvent}s that are visible to all user groups of the course.</li>
     * </ul>
     */
    void setupActiveCourseWithCancelledTutorialGroupSessionsScenario() {
        course = courseUtilService.createActiveCourseInTimezone(ZoneId.of(TEST_TIMEZONE_STRING), 1, 3);
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        tutorialGroupSessions = tutorialGroupUtilService.createTutorialGroupSessions(tutorialGroup, course, true);
        coursewideCalendarEvents = coursewideCalendarEventUtilService.createCoursewideCalendarEvents(course);
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

    String assembleURLForCalendarEventsGetRequest(String monthKeys, String timeZone) {
        return "/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone;
    }

    String assembleURLForCoursewideCalendarEventsGetRequest(Long courseId) {
        return "/api/calendar/courses/" + courseId + "/coursewide-calendar-events";
    }

    String assembleURLForCoursewideCalendarEventPostRequest(Long courseId) {
        return "/api/calendar/courses/" + courseId + "/coursewide-calendar-events";
    }

    String assembleURLForCoursewideCalendarEventDeleteRequest(String eventId) {
        return "/api/calendar/coursewide-calendar-event/" + eventId;
    }
}
