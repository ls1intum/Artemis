package de.tum.cit.aet.artemis.core;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.util.CalendarEventRelatedEntity;
import de.tum.cit.aet.artemis.core.util.CalendarEventSemantics;
import de.tum.cit.aet.artemis.core.util.DateUtil;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSession;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * Note: <br>
 * In the following test class tutors, editors and instructors are referred to as course staff members.
 */
class CalendarIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    static final TypeReference<Map<String, List<CalendarEventDTO>>> EVENT_MAP_RETURN_TYPE = new TypeReference<Map<String, List<CalendarEventDTO>>>() {
    };

    static final Comparator<ZonedDateTime> TIMESTAMP_COMPARATOR = Comparator.comparing(zdt -> zdt.toInstant().truncatedTo(ChronoUnit.MILLIS));

    static final String TEST_TIMEZONE_STRING = "Europe/Berlin";

    static final ZoneId TEST_TIMEZONE = ZoneId.of(TEST_TIMEZONE_STRING);

    static final ZonedDateTime FIXED_DATE = ZonedDateTime.of(2025, 5, 15, 12, 0, 0, 0, TEST_TIMEZONE);

    static final String FIXED_DATE_MONTH_STRING = YearMonth.from(FIXED_DATE).toString();

    static final ZonedDateTime PAST_DATE = now(TEST_TIMEZONE).minusMonths(1).withDayOfMonth(15).withHour(12);

    static final String PAST_DATE_MONTH_STRING = YearMonth.from(PAST_DATE).toString();

    static final ZonedDateTime FUTURE_DATE = now(TEST_TIMEZONE).plusMonths(1).withDayOfMonth(15).withHour(12);

    static final String FUTURE_DATE_MONTH_STRING = YearMonth.from(FUTURE_DATE).toString();

    static final String TEST_PREFIX = "calendarevent";

    static final String STUDENT_LOGIN = TEST_PREFIX + "student";

    static final String TUTOR_LOGIN = TEST_PREFIX + "tutor";

    static final String EDITOR_LOGIN = TEST_PREFIX + "editor";

    static final String INSTRUCTOR_LOGIN = TEST_PREFIX + "instructor";

    static final String NOT_STUDENT_LOGIN = TEST_PREFIX + "notstudent";

    static final String NOT_TUTOR_LOGIN = TEST_PREFIX + "nottutor";

    static final String NOT_EDITOR_LOGIN = TEST_PREFIX + "noteditor";

    static final String NOT_INSTRUCTOR_LOGIN = TEST_PREFIX + "notinstructor";

    User student;

    User tutor;

    User editor;

    User instructor;

    Course course;

    @BeforeEach
    void setUp() {
        userUtilService.addStudent("tumuser", STUDENT_LOGIN);
        userUtilService.addTeachingAssistant("tutor", TUTOR_LOGIN);
        userUtilService.addEditor("editor", EDITOR_LOGIN);
        userUtilService.addInstructor("instructor", INSTRUCTOR_LOGIN);
        student = userUtilService.getUserByLogin(STUDENT_LOGIN);
        tutor = userUtilService.getUserByLogin(TUTOR_LOGIN);
        editor = userUtilService.getUserByLogin(EDITOR_LOGIN);
        instructor = userUtilService.getUserByLogin(INSTRUCTOR_LOGIN);
        course = courseUtilService.createCourse();
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
        int courseId = -3;
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
        request.get(URL, HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnBadRequestWhenMonthsHaveWrongFormat() throws Exception {
        Long courseId = course.getId();
        String monthKeys = "11-2025";
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + TEST_TIMEZONE_STRING;
        request.get(URL, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnBadRequestWhenMonthsAreEmpty() throws Exception {
        Long courseId = course.getId();
        String monthKeys = "";
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + TEST_TIMEZONE_STRING;
        request.get(URL, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnBadRequestWhenTimeZoneFormattedIncorrectly() throws Exception {
        Long courseId = course.getId();
        String malformedTimeZone = "EST";
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + malformedTimeZone;
        request.get(URL, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = NOT_STUDENT_LOGIN, roles = "USER")
    void shouldReturnForbiddenForStudentNotPartOfCourse() throws Exception {
        userUtilService.addStudent("notstudent", NOT_STUDENT_LOGIN);
        Long courseId = course.getId();
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
        request.get(URL, HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = NOT_TUTOR_LOGIN, roles = "TUTOR")
    void shouldReturnForbiddenForTutorNotPartOfCourse() throws Exception {
        userUtilService.addStudent("nottutor", NOT_TUTOR_LOGIN);
        Long courseId = course.getId();
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
        request.get(URL, HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "TA")
    void shouldReturnForbiddenForEditorNotPartOfCourse() throws Exception {
        userUtilService.addStudent("noteditor", NOT_EDITOR_LOGIN);
        Long courseId = course.getId();
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
        request.get(URL, HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void shouldReturnForbiddenForInstructorNotPartOfCourse() throws Exception {
        userUtilService.addStudent("notinstructor", NOT_INSTRUCTOR_LOGIN);
        Long courseId = course.getId();
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
        request.get(URL, HttpStatus.FORBIDDEN, String.class);
    }

    @Nested
    class TutorialSessionEventTests {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForTutorialGroupSessionAsStudent() throws Exception {
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                    new HashSet<>(Set.of(student)));
            TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusHours(2),
                    5);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                    tutorialGroupSession.getStart(), tutorialGroupSession.getEnd(), tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(),
                    tutor.getFirstName() + " " + tutor.getLastName());
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForTutorialGroupSessionAsCourseStaffMember() throws Exception {
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                    new HashSet<>(Set.of(student)));
            TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusHours(2),
                    5);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                    tutorialGroupSession.getStart(), tutorialGroupSession.getEnd(), tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(),
                    tutor.getFirstName() + " " + tutor.getLastName());
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }
    }

    @Nested
    class LectureEventTests {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForVisibleLectureWithStartButNoEndAsStudent() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, FIXED_DATE, FIXED_DATE.plusDays(1), null);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.START_DATE, lecture.getTitle(), lecture.getStartDate(),
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForVisibleLectureWithEndButNoStartAsStudent() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), null, PAST_DATE);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.END_DATE, lecture.getTitle(), lecture.getEndDate(),
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForVisibleLectureWithStartAndEndAsStudent() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), PAST_DATE.minusHours(2), PAST_DATE);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.START_AND_END_DATE, lecture.getTitle(),
                    lecture.getStartDate(), lecture.getEndDate(), null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventForInvisibleLectureWithStartAndEndAsStudent() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(1).plusHours(2));
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForVisibleLectureWithStartButNoEndAsCourseStaffMember() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, FIXED_DATE, FIXED_DATE.plusDays(1), null);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.START_DATE, lecture.getTitle(), lecture.getStartDate(),
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForVisibleLectureWithEndButNoStartAsCourseStaffMember() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), null, PAST_DATE);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.END_DATE, lecture.getTitle(), lecture.getEndDate(),
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForVisibleLectureWithStartAndEndAsCourseStaffMember() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), PAST_DATE.minusHours(2), PAST_DATE);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.START_AND_END_DATE, lecture.getTitle(),
                    lecture.getStartDate(), lecture.getEndDate(), null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForInvisibleLectureWithStartAndEventEventAsCourseStaffMember() throws Exception {
            Lecture lecture = lectureUtilService.createLecture(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(1).plusHours(2));
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.LECTURE, CalendarEventSemantics.START_AND_END_DATE, lecture.getTitle(),
                    lecture.getStartDate(), lecture.getEndDate(), null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }
    }

    @Nested
    class ExamEventTests {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForVisibleExamWithStartAndEndAndPublishResultsAndReviewStartAndEndAsStudent() throws Exception {
            Exam exam = examUtilService.addExam(course, PAST_DATE, PAST_DATE.plusHours(2), PAST_DATE.plusHours(3), PAST_DATE.plusDays(1), PAST_DATE.plusDays(2),
                    PAST_DATE.plusDays(3), "Test-Examiner");
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.START_AND_END_DATE, exam.getTitle(), exam.getStartDate(),
                    exam.getEndDate(), null, exam.getExaminer());
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.PUBLISH_RESULTS_DATE, exam.getTitle(),
                    exam.getPublishResultsDate(), null, null, null);
            CalendarEventDTO expectedEvent3 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_START_DATE, exam.getTitle(),
                    exam.getExamStudentReviewStart(), null, null, null);
            CalendarEventDTO expectedEvent4 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_END_DATE, exam.getTitle(),
                    exam.getExamStudentReviewEnd(), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3, expectedEvent4)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForVisibleExamWithStartAndEndButNoPublishResultsAndReviewStartAndEndAsStudent() throws Exception {
            Exam exam = examUtilService.addExam(course, PAST_DATE, PAST_DATE.plusHours(2), PAST_DATE.plusHours(3), null, null, null, "Test-Examiner");
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.START_AND_END_DATE, exam.getTitle(), exam.getStartDate(),
                    exam.getEndDate(), null, exam.getExaminer());
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsForInvisibleExamWithStartAndEndAndPublishResultsAndReviewStartAndEndAsStudent() throws Exception {
            Exam exam = examUtilService.addExam(course, FUTURE_DATE, FUTURE_DATE.plusHours(2), FUTURE_DATE.plusHours(3), FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2),
                    FUTURE_DATE.plusDays(3), "Test-Examiner");
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForVisibleExamWithStartAndEndAndPublishResultsAndReviewStartAndEndAsCourseStaffMember() throws Exception {
            Exam exam = examUtilService.addExam(course, PAST_DATE, PAST_DATE.plusHours(2), PAST_DATE.plusHours(3), PAST_DATE.plusDays(1), PAST_DATE.plusDays(2),
                    PAST_DATE.plusDays(3), "Test-Examiner");
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.START_AND_END_DATE, exam.getTitle(), exam.getStartDate(),
                    exam.getEndDate(), null, exam.getExaminer());
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.PUBLISH_RESULTS_DATE, exam.getTitle(),
                    exam.getPublishResultsDate(), null, null, null);
            CalendarEventDTO expectedEvent3 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_START_DATE, exam.getTitle(),
                    exam.getExamStudentReviewStart(), null, null, null);
            CalendarEventDTO expectedEvent4 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_END_DATE, exam.getTitle(),
                    exam.getExamStudentReviewEnd(), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3, expectedEvent4)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForVisibleExamWithStartAndEndButNoPublishResultsAndReviewStartAndEndAsCourseStaffMember() throws Exception {
            Exam exam = examUtilService.addExam(course, PAST_DATE, PAST_DATE.plusHours(2), PAST_DATE.plusHours(3), null, null, null, "Test-Examiner");
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.START_AND_END_DATE, exam.getTitle(), exam.getStartDate(),
                    exam.getEndDate(), null, exam.getExaminer());
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnNoEventsForInvisibleExamWithStartAndEndAndPublishResultsAndReviewStartAndEndAsCourseStaffMember() throws Exception {
            Exam exam = examUtilService.addExam(course, FUTURE_DATE, FUTURE_DATE.plusHours(2), FUTURE_DATE.plusHours(3), FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2),
                    FUTURE_DATE.plusDays(3), "Test-Examiner");
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.START_AND_END_DATE, exam.getTitle(), exam.getStartDate(),
                    exam.getEndDate(), null, exam.getExaminer());
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.PUBLISH_RESULTS_DATE, exam.getTitle(),
                    exam.getPublishResultsDate(), null, null, null);
            CalendarEventDTO expectedEvent3 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_START_DATE, exam.getTitle(),
                    exam.getExamStudentReviewStart(), null, null, null);
            CalendarEventDTO expectedEvent4 = new CalendarEventDTO(CalendarEventRelatedEntity.EXAM, CalendarEventSemantics.STUDENT_REVIEW_END_DATE, exam.getTitle(),
                    exam.getExamStudentReviewEnd(), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3, expectedEvent4)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }
    }

    @Nested
    class QuizExerciseEventTests {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForReleasedSynchronizedQuizWithStartTimeAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, PAST_DATE, PAST_DATE.plusDays(1), 600);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.START_AND_END_DATE, quizExercise.getTitle(),
                    PAST_DATE.plusDays(1), PAST_DATE.plusDays(1).plusSeconds(600), null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventForUnreleasedSynchronizedQuizWithStartTimeAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, FUTURE_DATE, FUTURE_DATE.plusHours(1), 600);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventForReleasedSynchronizedQuizWithoutStartTimeAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, PAST_DATE, null, 600);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnCorrectEventForReleasedSynchronizedQuizWithStartTimeAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, PAST_DATE, PAST_DATE.plusDays(1), 600);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.START_AND_END_DATE, quizExercise.getTitle(),
                    PAST_DATE.plusDays(1), PAST_DATE.plusDays(1).plusSeconds(600), null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnCorrectEventForUnreleasedSynchronizedQuizWithStartTimeAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, FUTURE_DATE, FUTURE_DATE.plusHours(1), 600);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.START_AND_END_DATE, quizExercise.getTitle(),
                    FUTURE_DATE.plusHours(1), FUTURE_DATE.plusHours(1).plusSeconds(600), null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnNoEventForReleasedSynchronizedQuizWithoutStartTimeAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, PAST_DATE, null, 600);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForReleasedIndividualQuizWithNoDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForIndividualQuizWithDueDateButNoReleaseDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, PAST_DATE, null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForReleasedIndividualQuizWithDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, PAST_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(),
                    PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(),
                    PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventForIndividualQuizWithNoReleaseDateAndNoDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, null, null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventForUnreleasedIndividualQuizWithDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForReleasedIndividualQuizWithNoDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForIndividualQuizWithDueDateButNoReleaseDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, PAST_DATE, null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForReleasedIndividualQuizWithDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, PAST_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(),
                    PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(),
                    PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNoEventForIndividualQuizWithNoReleaseDateAndNoDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, null, null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForUnreleasedIndividualQuizWithDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(),
                    FUTURE_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(),
                    FUTURE_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForReleasedBatchedQuizWithNoDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForBatchedQuizWithDueDateButNoReleaseDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, PAST_DATE, null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForReleasedBatchedQuizWithDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, PAST_DATE.plusDays(1), null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(),
                    PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(),
                    PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventForBatchedQuizWithNoReleaseDateAndNoDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, null, null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventForUnreleasedBatchedQuizWithDueDateAsStudent() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForReleasedBatchedQuizWithNoDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventForBatchedQuizWithDueDateButNoReleaseDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, PAST_DATE, null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(), PAST_DATE,
                    null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForReleasedBatchedQuizWithDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, PAST_DATE.plusDays(1), null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(),
                    PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(),
                    PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNoEventForBatchedQuizWithNoReleaseDateAndNoDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, null, null, null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForUnreleasedBatchedQuizWithDueDateAsCourseStaffMember() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.BATCHED);
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, quizExercise.getTitle(),
                    FUTURE_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, quizExercise.getTitle(),
                    FUTURE_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }
    }

    @Nested
    class NonQuizExerciseEventTests {

        enum NonQuizExercise {
            TEXT, MODELING, FILEUPLOAD, PROGRAMMING,
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForReleasedExerciseWithStartDateAsStudent(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
                case FILEUPLOAD -> fileUploadExerciseUtilService.addFileUploadExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
                case PROGRAMMING -> programmingExerciseUtilService.createProgrammingExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
            };
            CalendarEventRelatedEntity eventType = switch (nonQuizExercise) {
                case TEXT -> CalendarEventRelatedEntity.TEXT_EXERCISE;
                case MODELING -> CalendarEventRelatedEntity.MODELING_EXERCISE;
                case FILEUPLOAD -> CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE;
                case PROGRAMMING -> CalendarEventRelatedEntity.PROGRAMMING_EXERCISE;
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(eventType, CalendarEventSemantics.RELEASE_DATE, exercise.getTitle(), PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(eventType, CalendarEventSemantics.START_DATE, exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForReleasedExerciseWithDueDateAsStudent(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
                case FILEUPLOAD -> fileUploadExerciseUtilService.addFileUploadExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
                case PROGRAMMING -> programmingExerciseUtilService.createProgrammingExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
            };
            CalendarEventRelatedEntity eventType = switch (nonQuizExercise) {
                case TEXT -> CalendarEventRelatedEntity.TEXT_EXERCISE;
                case MODELING -> CalendarEventRelatedEntity.MODELING_EXERCISE;
                case FILEUPLOAD -> CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE;
                case PROGRAMMING -> CalendarEventRelatedEntity.PROGRAMMING_EXERCISE;
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(eventType, CalendarEventSemantics.RELEASE_DATE, exercise.getTitle(), PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(eventType, CalendarEventSemantics.DUE_DATE, exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForReleasedExerciseWithAssessmentDueDateAsStudent(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
                case FILEUPLOAD -> fileUploadExerciseUtilService.addFileUploadExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
                case PROGRAMMING -> programmingExerciseUtilService.createProgrammingExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
            };
            CalendarEventRelatedEntity eventType = switch (nonQuizExercise) {
                case TEXT -> CalendarEventRelatedEntity.TEXT_EXERCISE;
                case MODELING -> CalendarEventRelatedEntity.MODELING_EXERCISE;
                case FILEUPLOAD -> CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE;
                case PROGRAMMING -> CalendarEventRelatedEntity.PROGRAMMING_EXERCISE;
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(eventType, CalendarEventSemantics.RELEASE_DATE, exercise.getTitle(), PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(eventType, CalendarEventSemantics.ASSESSMENT_DUE_DATE, exercise.getTitle(), PAST_DATE.plusDays(1), null, null,
                    null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventForUnreleasedExerciseAsStudent(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                case FILEUPLOAD ->
                    fileUploadExerciseUtilService.addFileUploadExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                case PROGRAMMING ->
                    programmingExerciseUtilService.createProgrammingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForReleasedExerciseWithStartDateAsCourseStaffMember(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
                case FILEUPLOAD -> fileUploadExerciseUtilService.addFileUploadExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
                case PROGRAMMING -> programmingExerciseUtilService.createProgrammingExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);
            };
            CalendarEventRelatedEntity eventType = switch (nonQuizExercise) {
                case TEXT -> CalendarEventRelatedEntity.TEXT_EXERCISE;
                case MODELING -> CalendarEventRelatedEntity.MODELING_EXERCISE;
                case FILEUPLOAD -> CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE;
                case PROGRAMMING -> CalendarEventRelatedEntity.PROGRAMMING_EXERCISE;
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(eventType, CalendarEventSemantics.RELEASE_DATE, exercise.getTitle(), PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(eventType, CalendarEventSemantics.START_DATE, exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventForReleasedExerciseWithDueDateAsCourseStaffMember(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
                case FILEUPLOAD -> fileUploadExerciseUtilService.addFileUploadExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
                case PROGRAMMING -> programmingExerciseUtilService.createProgrammingExercise(course, PAST_DATE, null, PAST_DATE.plusDays(1), null);
            };
            CalendarEventRelatedEntity eventType = switch (nonQuizExercise) {
                case TEXT -> CalendarEventRelatedEntity.TEXT_EXERCISE;
                case MODELING -> CalendarEventRelatedEntity.MODELING_EXERCISE;
                case FILEUPLOAD -> CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE;
                case PROGRAMMING -> CalendarEventRelatedEntity.PROGRAMMING_EXERCISE;
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(eventType, CalendarEventSemantics.RELEASE_DATE, exercise.getTitle(), PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(eventType, CalendarEventSemantics.DUE_DATE, exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventForReleasedExerciseWithAssessmentDueDateAsCourseStaffMember(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
                case FILEUPLOAD -> fileUploadExerciseUtilService.addFileUploadExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
                case PROGRAMMING -> programmingExerciseUtilService.createProgrammingExercise(course, PAST_DATE, null, null, PAST_DATE.plusDays(1));
            };
            CalendarEventRelatedEntity eventType = switch (nonQuizExercise) {
                case TEXT -> CalendarEventRelatedEntity.TEXT_EXERCISE;
                case MODELING -> CalendarEventRelatedEntity.MODELING_EXERCISE;
                case FILEUPLOAD -> CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE;
                case PROGRAMMING -> CalendarEventRelatedEntity.PROGRAMMING_EXERCISE;
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(eventType, CalendarEventSemantics.RELEASE_DATE, exercise.getTitle(), PAST_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(eventType, CalendarEventSemantics.ASSESSMENT_DUE_DATE, exercise.getTitle(), PAST_DATE.plusDays(1), null, null,
                    null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @ParameterizedTest
        @EnumSource(NonQuizExercise.class)
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForUnreleasedExerciseAsCourseStaffMember(NonQuizExercise nonQuizExercise) throws Exception {
            Exercise exercise = switch (nonQuizExercise) {
                case TEXT -> textExerciseUtilService.createIndividualTextExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                case MODELING -> modelingExerciseUtilService.addModelingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                case FILEUPLOAD ->
                    fileUploadExerciseUtilService.addFileUploadExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                case PROGRAMMING ->
                    programmingExerciseUtilService.createProgrammingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
            };
            CalendarEventRelatedEntity eventType = switch (nonQuizExercise) {
                case TEXT -> CalendarEventRelatedEntity.TEXT_EXERCISE;
                case MODELING -> CalendarEventRelatedEntity.MODELING_EXERCISE;
                case FILEUPLOAD -> CalendarEventRelatedEntity.FILE_UPLOAD_EXERCISE;
                case PROGRAMMING -> CalendarEventRelatedEntity.PROGRAMMING_EXERCISE;
            };
            Long courseId = course.getId();
            String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(eventType, CalendarEventSemantics.RELEASE_DATE, exercise.getTitle(), FUTURE_DATE, null, null, null);
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(eventType, CalendarEventSemantics.START_DATE, exercise.getTitle(), FUTURE_DATE.plusDays(1), null, null, null);
            CalendarEventDTO expectedEvent3 = new CalendarEventDTO(eventType, CalendarEventSemantics.DUE_DATE, exercise.getTitle(), FUTURE_DATE.plusDays(2), null, null, null);
            CalendarEventDTO expectedEvent4 = new CalendarEventDTO(eventType, CalendarEventSemantics.ASSESSMENT_DUE_DATE, exercise.getTitle(), FUTURE_DATE.plusDays(3), null, null,
                    null);
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3, expectedEvent4)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnCorrectEventsForStudentWhenQueriedForMultipleMonths() throws Exception {
        TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", Language.ENGLISH.name(), tutor,
                new HashSet<>(Set.of(student)));
        TutorialGroupSession tutorialGroupSession1 = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE.minusMonths(1),
                FIXED_DATE.minusMonths(1).plusHours(3), 5);
        TutorialGroupSession tutorialGroupSession2 = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusHours(2), 5);
        TutorialGroupSession tutorialGroupSession3 = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE.plusMonths(1),
                FIXED_DATE.plusMonths(1).plusHours(1), 5);
        Long courseId = course.getId();
        String monthKeys = YearMonth.from(FIXED_DATE.minusMonths(1)) + "," + FIXED_DATE_MONTH_STRING + "," + YearMonth.from(FIXED_DATE.plusMonths(1));
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + TEST_TIMEZONE_STRING;
        Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

        CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                tutorialGroupSession1.getStart(), tutorialGroupSession1.getEnd(), tutorialGroupSession1.getLocation() + " - " + tutorialGroup.getCampus(),
                tutor.getFirstName() + " " + tutor.getLastName());
        CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                tutorialGroupSession2.getStart(), tutorialGroupSession2.getEnd(), tutorialGroupSession2.getLocation() + " - " + tutorialGroup.getCampus(),
                tutor.getFirstName() + " " + tutor.getLastName());
        CalendarEventDTO expectedEvent3 = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                tutorialGroupSession3.getStart(), tutorialGroupSession3.getEnd(), tutorialGroupSession3.getLocation() + " - " + tutorialGroup.getCampus(),
                tutor.getFirstName() + " " + tutor.getLastName());
        Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3)
                .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

        assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                .isEqualTo(expectedResponse);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldSplitEventAcrossDaysWhenEventSpansMultipleDays() throws Exception {
        TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusDays(2), 5);
        Long courseId = course.getId();
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING;
        Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

        ZoneId timezone = tutorialGroupSession.getStart().getZone();
        CalendarEventDTO expectedEvent1 = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                tutorialGroupSession.getStart(), tutorialGroupSession.getStart().toLocalDate().atTime(DateUtil.END_OF_DAY).atZone(timezone),
                tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
        CalendarEventDTO expectedEvent2 = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                tutorialGroupSession.getStart().plusDays(1).toLocalDate().atStartOfDay(timezone),
                tutorialGroupSession.getStart().plusDays(1).toLocalDate().atTime(DateUtil.END_OF_DAY).atZone(timezone),
                tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
        CalendarEventDTO expectedEvent3 = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                tutorialGroupSession.getStart().plusDays(2).toLocalDate().atStartOfDay(timezone), tutorialGroupSession.getEnd(),
                tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
        Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3)
                .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

        assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                .isEqualTo(expectedResponse);
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldGroupEventsAccordingToClientTimeZone() throws Exception {
        ZonedDateTime berlinStart = ZonedDateTime.of(2025, 5, 15, 2, 30, 0, 0, TEST_TIMEZONE);
        ZonedDateTime berlinEnd = berlinStart.plusHours(2);
        TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Early Tutorial", "", 10, false, "Garching", "English", tutor,
                new HashSet<>(Set.of(student)));
        TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), berlinStart, berlinEnd, 5);

        Long courseId = course.getId();
        String otherTimeZone = "America/Los_Angeles";
        String URL = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=2025-05&timeZone=" + otherTimeZone;
        Map<String, List<CalendarEventDTO>> actualResponse = request.get(URL, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

        CalendarEventDTO expectedEvent = new CalendarEventDTO(CalendarEventRelatedEntity.TUTORIAL, CalendarEventSemantics.START_AND_END_DATE, "Tutorial Session",
                tutorialGroupSession.getStart(), tutorialGroupSession.getEnd(), tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(),
                tutor.getFirstName() + " " + tutor.getLastName());
        Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
        expectedResponse.put(tutorialGroupSession.getStart().withZoneSameInstant(ZoneId.of(otherTimeZone)).toLocalDate().toString(), List.of(expectedEvent));

        assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                .isEqualTo(expectedResponse);
    }
}
