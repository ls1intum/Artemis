package de.tum.cit.aet.artemis.calendar;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

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
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        tutor = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
    }

    @AfterEach
    void cleanUp() {
        tutorialGroupSessionRepository.deleteAll(tutorialGroupSessions);
        tutorialGroupSessionRepository.flush();
        tutorialGroupRepository.deleteById(tutorialGroup.getId());
        tutorialGroupRepository.flush();
        courseRepository.deleteById(course.getId());
        courseRepository.flush();
        userRepository.deleteById(student.getId());
        userRepository.deleteById(tutor.getId());
        userRepository.flush();
    }

    void setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario() {
        createActiveCourse();
        createParticipatedTutorialGroup();
        createActiveTutorialGroupSessions(this.course);
    }

    void setupUserNotPartOfAnyCourseScenario() {
        createActiveCourse();
        createUsersNotPartOfCourse();
        createParticipatedTutorialGroup();
        createActiveTutorialGroupSessions(this.course);
    }

    void setupOnlyNonActiveCourseScenario() {
        createNonActiveCourse();
        createParticipatedTutorialGroup();
        createActiveTutorialGroupSessions(this.course);
    }

    void setupActiveCourseWithoutParticipatedTutorialGroupScenario() {
        createActiveCourse();
        createNonParticipatedTutorialGroup();
        createActiveTutorialGroupSessions(this.course);
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

    private void createNonActiveCourse() {
        ZonedDateTime now = ZonedDateTime.now().withDayOfMonth(1);
        ZonedDateTime courseStart = now.minusMonths(6);
        ZonedDateTime courseEnd = now.minusMonths(2);
        course = courseUtilService.createCourseWithStartDateAndEndDate(courseStart, courseEnd);
    }

    private void createActiveCourse() {
        ZonedDateTime now = ZonedDateTime.now().withDayOfMonth(1);
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
        User otherTutor = userRepository.getUserByLoginElseThrow(TEST_PREFIX + "othertutor1");
        tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", otherTutor, new HashSet<>());
    }

    private void createActiveTutorialGroupSessions(Course course) {
        ZonedDateTime courseStart = course.getStartDate();
        ZonedDateTime firstSessionStart = courseStart.plusWeeks(1).withHour(12).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime firstSessionEnd = firstSessionStart.plusHours(2);
        tutorialGroupSessions = createWeeklyTutorialGroupSessions(tutorialGroup.getId(), firstSessionStart, firstSessionEnd, 12, 0);
    }

    private void createActiveAndCancelledTutorialGroupSessions() {
        ZonedDateTime courseStart = course.getStartDate();
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
