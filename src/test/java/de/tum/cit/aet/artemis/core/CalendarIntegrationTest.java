package de.tum.cit.aet.artemis.core;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import de.tum.cit.aet.artemis.core.util.CalendarEventType;
import de.tum.cit.aet.artemis.core.util.CalendarSubscriptionFilterOption;
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
import de.tum.cit.aet.artemis.text.domain.TextExercise;
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

    static final TypeReference<Map<String, List<CalendarEventDTO>>> EVENT_MAP_RETURN_TYPE = new TypeReference<>() {
    };

    static final Comparator<ZonedDateTime> TIMESTAMP_COMPARATOR = Comparator.comparing(zdt -> zdt.toInstant().truncatedTo(ChronoUnit.MILLIS));

    static final String TEST_LANGUAGE_STRING = "ENGLISH";

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

    @Nested
    class GetCalendarEventsOverlappingMonthsTests {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
            int courseId = -3;
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.NOT_FOUND, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenMonthsHaveWrongFormat() throws Exception {
            Long courseId = course.getId();
            String monthKeys = "11-2025";
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenMonthsAreEmpty() throws Exception {
            Long courseId = course.getId();
            String monthKeys = "";
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenTimeZoneFormattedIncorrectly() throws Exception {
            Long courseId = course.getId();
            String malformedTimeZone = "EST";
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + malformedTimeZone + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = NOT_STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudentNotPartOfCourse() throws Exception {
            userUtilService.addStudent("notstudent", NOT_STUDENT_LOGIN);
            Long courseId = course.getId();
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = NOT_TUTOR_LOGIN, roles = "TUTOR")
        void shouldReturnForbiddenForTutorNotPartOfCourse() throws Exception {
            userUtilService.addStudent("nottutor", NOT_TUTOR_LOGIN);
            Long courseId = course.getId();
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForEditorNotPartOfCourse() throws Exception {
            userUtilService.addStudent("noteditor", NOT_EDITOR_LOGIN);
            Long courseId = course.getId();
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorNotPartOfCourse() throws Exception {
            userUtilService.addStudent("notinstructor", NOT_INSTRUCTOR_LOGIN);
            Long courseId = course.getId();
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Nested
        class TutorialSessionEventTests {

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnCorrectEventsForTutorialGroupSessionAsStudent() throws Exception {
                TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching",
                        Language.ENGLISH.name(), tutor, new HashSet<>(Set.of(student)));
                TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE,
                        FIXED_DATE.plusHours(2), 5);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(), tutorialGroupSession.getStart(),
                        tutorialGroupSession.getEnd(), tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
                expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
            void shouldReturnCorrectEventsForTutorialGroupSessionAsCourseStaffMember() throws Exception {
                TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching",
                        Language.ENGLISH.name(), tutor, new HashSet<>(Set.of(student)));
                TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE,
                        FIXED_DATE.plusHours(2), 5);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(), tutorialGroupSession.getStart(),
                        tutorialGroupSession.getEnd(), tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
                expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }
        }

        @Nested
        class LectureEventTests {

            private static final String START_DATE_TITLE_PREFIX = "Start: ";

            private static final String END_DATE_TITLE_PREFIX = "End: ";

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnCorrectEventForVisibleLectureWithStartButNoEndAsStudent() throws Exception {
                Lecture lecture = lectureUtilService.createLecture(course, FIXED_DATE, FIXED_DATE.plusDays(1), null);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.LECTURE, START_DATE_TITLE_PREFIX + lecture.getTitle(), lecture.getStartDate(), null,
                        null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.LECTURE, END_DATE_TITLE_PREFIX + lecture.getTitle(), lecture.getEndDate(), null, null,
                        null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.LECTURE, lecture.getTitle(), lecture.getStartDate(), lecture.getEndDate(), null,
                        null);
                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
                expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            /* The visibleDate property of the Lecture entity is deprecated. We’re keeping the related logic temporarily to monitor for user feedback before full removal */
            /* TODO: #11479 - remove the commented out code OR comment back in */
            // @Test
            // @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            // void shouldReturnNoEventForInvisibleLectureWithStartAndEndAsStudent() throws Exception {
            // Lecture lecture = lectureUtilService.createLecture(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(1).plusHours(2));
            // Long courseId = course.getId();
            // String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
            // + TEST_LANGUAGE_STRING;
            // Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);
            // Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            // assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR,
            // ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expectedResponse);
            // }

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnCorrectEventsForVisibleLectureTakingLongerThanTwelveHoursAsStudent() throws Exception {
                Lecture lecture = lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), PAST_DATE, PAST_DATE.plusDays(1));
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.LECTURE, "Start: " + lecture.getTitle(), lecture.getStartDate(), null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.LECTURE, "End: " + lecture.getTitle(), lecture.getEndDate(), null, null, null);
                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
                expectedResponse.put(expectedEvent1.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent1));
                expectedResponse.put(expectedEvent2.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent2));

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
            void shouldReturnCorrectEventForVisibleLectureWithStartButNoEndAsCourseStaffMember() throws Exception {
                Lecture lecture = lectureUtilService.createLecture(course, FIXED_DATE, FIXED_DATE.plusDays(1), null);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.LECTURE, START_DATE_TITLE_PREFIX + lecture.getTitle(), lecture.getStartDate(), null,
                        null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.LECTURE, END_DATE_TITLE_PREFIX + lecture.getTitle(), lecture.getEndDate(), null, null,
                        null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.LECTURE, lecture.getTitle(), lecture.getStartDate(), lecture.getEndDate(), null,
                        null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.LECTURE, lecture.getTitle(), lecture.getStartDate(), lecture.getEndDate(), null,
                        null);
                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
                expectedResponse.put(expectedEvent.startDate().withZoneSameInstant(TEST_TIMEZONE).toLocalDate().toString(), List.of(expectedEvent));

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }
        }

        @Nested
        class ExamEventTests {

            private static final String PUBLISH_RESULTS_TITLE_PREFIX = "Results Release: ";

            private static final String STUDENT_REVIEW_START_DATE_TITLE_PREFIX = "Review Start: ";

            private static final String STUDENT_REVIEW_END_DATE_TITLE_PREFIX = "Review End: ";

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnCorrectEventsForVisibleExamWithStartAndEndAndPublishResultsAndReviewStartAndEndAsStudent() throws Exception {
                Exam exam = examUtilService.addExam(course, PAST_DATE, PAST_DATE.plusHours(2), PAST_DATE.plusHours(3), PAST_DATE.plusDays(1), PAST_DATE.plusDays(2),
                        PAST_DATE.plusDays(3), "Test-Examiner");
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.EXAM, exam.getTitle(), exam.getStartDate(), exam.getEndDate(), null,
                        exam.getExaminer());
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.EXAM, PUBLISH_RESULTS_TITLE_PREFIX + exam.getTitle(), exam.getPublishResultsDate(),
                        null, null, null);
                CalendarEventDTO expectedEvent3 = new CalendarEventDTO(null, CalendarEventType.EXAM, STUDENT_REVIEW_START_DATE_TITLE_PREFIX + exam.getTitle(),
                        exam.getExamStudentReviewStart(), null, null, null);
                CalendarEventDTO expectedEvent4 = new CalendarEventDTO(null, CalendarEventType.EXAM, STUDENT_REVIEW_END_DATE_TITLE_PREFIX + exam.getTitle(),
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.EXAM, exam.getTitle(), exam.getStartDate(), exam.getEndDate(), null,
                        exam.getExaminer());
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.EXAM, exam.getTitle(), exam.getStartDate(), exam.getEndDate(), null,
                        exam.getExaminer());
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.EXAM, PUBLISH_RESULTS_TITLE_PREFIX + exam.getTitle(), exam.getPublishResultsDate(),
                        null, null, null);
                CalendarEventDTO expectedEvent3 = new CalendarEventDTO(null, CalendarEventType.EXAM, STUDENT_REVIEW_START_DATE_TITLE_PREFIX + exam.getTitle(),
                        exam.getExamStudentReviewStart(), null, null, null);
                CalendarEventDTO expectedEvent4 = new CalendarEventDTO(null, CalendarEventType.EXAM, STUDENT_REVIEW_END_DATE_TITLE_PREFIX + exam.getTitle(),
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.EXAM, exam.getTitle(), exam.getStartDate(), exam.getEndDate(), null,
                        exam.getExaminer());
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.EXAM, exam.getTitle(), exam.getStartDate(), exam.getEndDate(), null,
                        exam.getExaminer());
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.EXAM, PUBLISH_RESULTS_TITLE_PREFIX + exam.getTitle(), exam.getPublishResultsDate(),
                        null, null, null);
                CalendarEventDTO expectedEvent3 = new CalendarEventDTO(null, CalendarEventType.EXAM, STUDENT_REVIEW_START_DATE_TITLE_PREFIX + exam.getTitle(),
                        exam.getExamStudentReviewStart(), null, null, null);
                CalendarEventDTO expectedEvent4 = new CalendarEventDTO(null, CalendarEventType.EXAM, STUDENT_REVIEW_END_DATE_TITLE_PREFIX + exam.getTitle(),
                        exam.getExamStudentReviewEnd(), null, null, null);
                Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3, expectedEvent4)
                        .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }
        }

        @Nested
        class QuizExerciseEventTests {

            private static final String RELEASE_DATE_TITLE_PREFIX = "Release: ";

            private static final String DUE_DATE_TITLE_PREFIX = "Due: ";

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnCorrectEventForReleasedSynchronizedQuizWithStartTimeAsStudent() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, PAST_DATE, PAST_DATE.plusDays(1), 600);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, quizExercise.getTitle(), PAST_DATE.plusDays(1),
                        PAST_DATE.plusDays(1).plusSeconds(600), null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnNoEventForReleasedSynchronizedQuizWithoutStartTimeAsStudent() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, PAST_DATE, null, 600);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
            void shouldReturnCorrectEventForReleasedSynchronizedQuizWithStartTimeAsCourseStaffMember() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveSynchronizedQuiz(course, PAST_DATE, PAST_DATE.plusDays(1), 600);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, quizExercise.getTitle(), PAST_DATE.plusDays(1),
                        PAST_DATE.plusDays(1).plusSeconds(600), null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, quizExercise.getTitle(), FUTURE_DATE.plusHours(1),
                        FUTURE_DATE.plusHours(1).plusSeconds(600), null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnCorrectEventForReleasedIndividualQuizWithNoDueDateAsStudent() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.INDIVIDUAL);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null, null,
                        null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(),
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnNoEventForUnreleasedIndividualQuizWithDueDateAsStudent() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
            void shouldReturnCorrectEventForReleasedIndividualQuizWithNoDueDateAsCourseStaffMember() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.INDIVIDUAL);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null, null,
                        null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(),
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
            void shouldReturnCorrectEventsForUnreleasedIndividualQuizWithDueDateAsCourseStaffMember() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), FUTURE_DATE,
                        null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(),
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null, null,
                        null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(),
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
            void shouldReturnNoEventForUnreleasedBatchedQuizWithDueDateAsStudent() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.BATCHED);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
            void shouldReturnCorrectEventForReleasedBatchedQuizWithNoDueDateAsCourseStaffMember() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.BATCHED);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null, null,
                        null);
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), PAST_DATE, null,
                        null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(),
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
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }

            @Test
            @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
            void shouldReturnCorrectEventsForUnreleasedBatchedQuizWithDueDateAsCourseStaffMember() throws Exception {
                QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.BATCHED);
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, RELEASE_DATE_TITLE_PREFIX + quizExercise.getTitle(), FUTURE_DATE,
                        null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.QUIZ_EXERCISE, DUE_DATE_TITLE_PREFIX + quizExercise.getTitle(),
                        FUTURE_DATE.plusDays(1), null, null, null);
                Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2)
                        .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

                assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                        .isEqualTo(expectedResponse);
            }
        }

        @Nested
        class NonQuizExerciseEventTests {

            private static final String RELEASE_DATE_TITLE_PREFIX = "Release: ";

            private static final String START_DATE_TITLE_PREFIX = "Start: ";

            private static final String DUE_DATE_TITLE_PREFIX = "Due: ";

            private static final String ASSESSMENT_DUE_DATE_TITLE_PREFIX = "Assessment due: ";

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
                CalendarEventType eventType = switch (nonQuizExercise) {
                    case TEXT -> CalendarEventType.TEXT_EXERCISE;
                    case MODELING -> CalendarEventType.MODELING_EXERCISE;
                    case FILEUPLOAD -> CalendarEventType.FILE_UPLOAD_EXERCISE;
                    case PROGRAMMING -> CalendarEventType.PROGRAMMING_EXERCISE;
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, eventType, RELEASE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE, null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, eventType, START_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
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
                CalendarEventType eventType = switch (nonQuizExercise) {
                    case TEXT -> CalendarEventType.TEXT_EXERCISE;
                    case MODELING -> CalendarEventType.MODELING_EXERCISE;
                    case FILEUPLOAD -> CalendarEventType.FILE_UPLOAD_EXERCISE;
                    case PROGRAMMING -> CalendarEventType.PROGRAMMING_EXERCISE;
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, eventType, RELEASE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE, null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, eventType, DUE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
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
                CalendarEventType eventType = switch (nonQuizExercise) {
                    case TEXT -> CalendarEventType.TEXT_EXERCISE;
                    case MODELING -> CalendarEventType.MODELING_EXERCISE;
                    case FILEUPLOAD -> CalendarEventType.FILE_UPLOAD_EXERCISE;
                    case PROGRAMMING -> CalendarEventType.PROGRAMMING_EXERCISE;
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, eventType, RELEASE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE, null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, eventType, ASSESSMENT_DUE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE.plusDays(1), null, null,
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
                    case TEXT ->
                        textExerciseUtilService.createIndividualTextExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                    case MODELING ->
                        modelingExerciseUtilService.addModelingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                    case FILEUPLOAD ->
                        fileUploadExerciseUtilService.addFileUploadExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                    case PROGRAMMING ->
                        programmingExerciseUtilService.createProgrammingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

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
                CalendarEventType eventType = switch (nonQuizExercise) {
                    case TEXT -> CalendarEventType.TEXT_EXERCISE;
                    case MODELING -> CalendarEventType.MODELING_EXERCISE;
                    case FILEUPLOAD -> CalendarEventType.FILE_UPLOAD_EXERCISE;
                    case PROGRAMMING -> CalendarEventType.PROGRAMMING_EXERCISE;
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, eventType, RELEASE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE, null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, eventType, START_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
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
                CalendarEventType eventType = switch (nonQuizExercise) {
                    case TEXT -> CalendarEventType.TEXT_EXERCISE;
                    case MODELING -> CalendarEventType.MODELING_EXERCISE;
                    case FILEUPLOAD -> CalendarEventType.FILE_UPLOAD_EXERCISE;
                    case PROGRAMMING -> CalendarEventType.PROGRAMMING_EXERCISE;
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, eventType, RELEASE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE, null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, eventType, DUE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE.plusDays(1), null, null, null);
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
                CalendarEventType eventType = switch (nonQuizExercise) {
                    case TEXT -> CalendarEventType.TEXT_EXERCISE;
                    case MODELING -> CalendarEventType.MODELING_EXERCISE;
                    case FILEUPLOAD -> CalendarEventType.FILE_UPLOAD_EXERCISE;
                    case PROGRAMMING -> CalendarEventType.PROGRAMMING_EXERCISE;
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + PAST_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                        + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, eventType, RELEASE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE, null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, eventType, ASSESSMENT_DUE_DATE_TITLE_PREFIX + exercise.getTitle(), PAST_DATE.plusDays(1), null, null,
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
                    case TEXT ->
                        textExerciseUtilService.createIndividualTextExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                    case MODELING ->
                        modelingExerciseUtilService.addModelingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                    case FILEUPLOAD ->
                        fileUploadExerciseUtilService.addFileUploadExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                    case PROGRAMMING ->
                        programmingExerciseUtilService.createProgrammingExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), FUTURE_DATE.plusDays(2), FUTURE_DATE.plusDays(3));
                };
                CalendarEventType eventType = switch (nonQuizExercise) {
                    case TEXT -> CalendarEventType.TEXT_EXERCISE;
                    case MODELING -> CalendarEventType.MODELING_EXERCISE;
                    case FILEUPLOAD -> CalendarEventType.FILE_UPLOAD_EXERCISE;
                    case PROGRAMMING -> CalendarEventType.PROGRAMMING_EXERCISE;
                };
                Long courseId = course.getId();
                String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FUTURE_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING
                        + "&language=" + TEST_LANGUAGE_STRING;
                Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

                CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, eventType, RELEASE_DATE_TITLE_PREFIX + exercise.getTitle(), FUTURE_DATE, null, null, null);
                CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, eventType, START_DATE_TITLE_PREFIX + exercise.getTitle(), FUTURE_DATE.plusDays(1), null, null, null);
                CalendarEventDTO expectedEvent3 = new CalendarEventDTO(null, eventType, DUE_DATE_TITLE_PREFIX + exercise.getTitle(), FUTURE_DATE.plusDays(2), null, null, null);
                CalendarEventDTO expectedEvent4 = new CalendarEventDTO(null, eventType, ASSESSMENT_DUE_DATE_TITLE_PREFIX + exercise.getTitle(), FUTURE_DATE.plusDays(3), null, null,
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
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", Language.ENGLISH.name(),
                    tutor, new HashSet<>(Set.of(student)));
            TutorialGroupSession tutorialGroupSession1 = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE.minusMonths(1),
                    FIXED_DATE.minusMonths(1).plusHours(3), 5);
            TutorialGroupSession tutorialGroupSession2 = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusHours(2),
                    5);
            TutorialGroupSession tutorialGroupSession3 = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE.plusMonths(1),
                    FIXED_DATE.plusMonths(1).plusHours(1), 5);
            Long courseId = course.getId();
            String monthKeys = YearMonth.from(FIXED_DATE.minusMonths(1)) + "," + FIXED_DATE_MONTH_STRING + "," + YearMonth.from(FIXED_DATE.plusMonths(1));
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(), tutorialGroupSession1.getStart(),
                    tutorialGroupSession1.getEnd(), tutorialGroupSession1.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(), tutorialGroupSession2.getStart(),
                    tutorialGroupSession2.getEnd(), tutorialGroupSession2.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
            CalendarEventDTO expectedEvent3 = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(), tutorialGroupSession3.getStart(),
                    tutorialGroupSession3.getEnd(), tutorialGroupSession3.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
            Map<String, List<CalendarEventDTO>> expectedResponse = Stream.of(expectedEvent1, expectedEvent2, expectedEvent3)
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldSplitEventAcrossDaysWhenEventSpansMultipleDays() throws Exception {
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", Language.ENGLISH.name(),
                    tutor, new HashSet<>(Set.of(student)));
            TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusDays(2), 5);
            Long courseId = course.getId();
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=" + FIXED_DATE_MONTH_STRING + "&timeZone=" + TEST_TIMEZONE_STRING + "&language="
                    + TEST_LANGUAGE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            ZoneId timezone = tutorialGroupSession.getStart().getZone();
            CalendarEventDTO expectedEvent1 = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(), tutorialGroupSession.getStart(),
                    tutorialGroupSession.getStart().toLocalDate().atTime(DateUtil.END_OF_DAY).atZone(timezone),
                    tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
            CalendarEventDTO expectedEvent2 = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(),
                    tutorialGroupSession.getStart().plusDays(1).toLocalDate().atStartOfDay(timezone),
                    tutorialGroupSession.getStart().plusDays(1).toLocalDate().atTime(DateUtil.END_OF_DAY).atZone(timezone),
                    tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
            CalendarEventDTO expectedEvent3 = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(),
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
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Early Tutorial", "", 10, false, "Garching", Language.ENGLISH.name(), tutor,
                    new HashSet<>(Set.of(student)));
            TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), berlinStart, berlinEnd, 5);

            Long courseId = course.getId();
            String otherTimeZone = "America/Los_Angeles";
            String url = "/api/core/calendar/courses/" + courseId + "/calendar-events?monthKeys=2025-05&timeZone=" + otherTimeZone + "&language=" + TEST_LANGUAGE_STRING;
            Map<String, List<CalendarEventDTO>> actualResponse = request.get(url, HttpStatus.OK, EVENT_MAP_RETURN_TYPE);

            CalendarEventDTO expectedEvent = new CalendarEventDTO(null, CalendarEventType.TUTORIAL, tutorialGroup.getTitle(), tutorialGroupSession.getStart(),
                    tutorialGroupSession.getEnd(), tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus(), tutor.getFirstName() + " " + tutor.getLastName());
            Map<String, List<CalendarEventDTO>> expectedResponse = new HashMap<>();
            expectedResponse.put(tutorialGroupSession.getStart().withZoneSameInstant(ZoneId.of(otherTimeZone)).toLocalDate().toString(), List.of(expectedEvent));

            assertThat(actualResponse).usingRecursiveComparison().withComparatorForType(TIMESTAMP_COMPARATOR, ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expectedResponse);
        }
    }

    @Nested
    class GetCalendarEventSubscriptionFileTests {

        private static final DateTimeFormatter ICS_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

        // a regex that matches each line starting with "UID:"
        private static final String UID_PREFIX_REGEX = "(?m)^UID:";

        // a regex that matches each line starting with "UID:" and capturing the value that comes after this prefix within the line
        private static final String UID_PREFIX_AND_VALUE_REGEX = "(?m)^UID:(.+)$";

        private String buildUrl(long courseId, String token, CalendarSubscriptionFilterOption[] filterOptions, Language language) {
            String tokenParameter = "token=" + token;
            String filterOptionsParameter = "filterOptions=" + Arrays.stream(filterOptions).map(Enum::name).collect(Collectors.joining(","));
            String languageParameter = "language=" + language;
            return "/api/core/calendar/courses/" + courseId + "/calendar-events-ics?" + tokenParameter + "&" + filterOptionsParameter + "&" + languageParameter;
        }

        private String generateUniqueTestToken() {
            return UUID.randomUUID().toString().replace("-", "");
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = -3;
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);
            request.get(url, HttpStatus.NOT_FOUND, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenWhenUserWithTokenDoesNotExist() throws Exception {
            String expectedToken = generateUniqueTestToken();
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = NOT_STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenWhenUserNotParticipantOfCourse() throws Exception {
            userUtilService.addStudent("notstudent", NOT_STUDENT_LOGIN);
            User notStudent = userUtilService.getUserByLogin(NOT_STUDENT_LOGIN);
            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(notStudent, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);
            request.get(url, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldGenerateCorrectICSFileHeaders() throws Exception {
            String expectedProductId = "PRODID:-//TUM//Artemis//EN";
            String expectedICalendarSpecificationVersion = "VERSION:2.0";
            String expectedICalendarScale = "CALSCALE:GREGORIAN";
            String expectedMethod = "METHOD:PUBLISH";

            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);
            assertThat(result).contains(expectedProductId);
            assertThat(result).contains(expectedICalendarSpecificationVersion);
            assertThat(result).contains(expectedICalendarScale);
            assertThat(result).contains(expectedMethod);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldNotIncludeVeventsFromUnreleasedEntitiesInICSFileForStudents() throws Exception {
            textExerciseUtilService.createIndividualTextExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, null);

            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);

            assertThat(result).doesNotContain("UID");
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "USER")
        void shouldIncludeVeventsFromUnreleasedEntitiesInICSFileForCourseStaff() throws Exception {
            textExerciseUtilService.createIndividualTextExercise(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, null);

            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(instructor, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);

            long vEventCount = Pattern.compile(UID_PREFIX_REGEX).matcher(result).results().count();
            assertThat(vEventCount).isEqualTo(2);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldIncludeCorrectlyStructuredVeventsInICSFile() throws Exception {
            String tutorialGroupName = "Test Tutorial Group";
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), tutorialGroupName, "", 10, false, "Garching", Language.ENGLISH.name(), tutor,
                    new HashSet<>(Set.of(student)));
            TutorialGroupSession tutorialGroupSession = tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusHours(2),
                    5);

            String expectedSummary = "SUMMARY:" + course.getShortName() + " Tutorial | " + tutorialGroupName;
            String expectedStart = "DTSTART:" + ICS_TIMESTAMP_FORMATTER.format(tutorialGroupSession.getStart().toInstant());
            String expectedEnd = "DTEND:" + ICS_TIMESTAMP_FORMATTER.format(tutorialGroupSession.getEnd().toInstant());
            String expectedLocation = "LOCATION:" + tutorialGroupSession.getLocation() + " - " + tutorialGroup.getCampus();
            String expectedContact = "CONTACT:" + tutor.getFirstName() + " " + tutor.getLastName();

            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);

            long vEventCount = Pattern.compile(UID_PREFIX_REGEX).matcher(result).results().count();
            assertThat(vEventCount).isEqualTo(1);
            assertThat(result).contains(expectedSummary);
            assertThat(result).contains(expectedStart);
            assertThat(result).contains(expectedEnd);
            assertThat(result).contains(expectedLocation);
            assertThat(result).contains(expectedContact);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldIncludeEndEqualToStartForVeventsWithNoEndDate() throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, null, null, QuizMode.INDIVIDUAL);

            String expectedStart = "DTSTART:" + ICS_TIMESTAMP_FORMATTER.format(PAST_DATE.toInstant());
            String expectedEnd = "DTEND:" + ICS_TIMESTAMP_FORMATTER.format(PAST_DATE.toInstant());

            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);

            long vEventCount = Pattern.compile(UID_PREFIX_REGEX).matcher(result).results().count();
            assertThat(vEventCount).isEqualTo(1);
            assertThat(result).contains(expectedStart);
            assertThat(result).contains(expectedEnd);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldGenerateStableIdsForVeventsInICSFile() throws Exception {
            lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), PAST_DATE.minusHours(2), PAST_DATE);

            String expectedToken = UUID.randomUUID().toString().replace("-", "");
            ;
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);

            Pattern uidLinePattern = Pattern.compile(UID_PREFIX_AND_VALUE_REGEX);
            Matcher matcher = uidLinePattern.matcher(result);
            List<String> uids = matcher.results().map(m -> m.group(1)).toList();
            assertThat(uids).hasSize(1);
            String firstUid = uids.getFirst();

            for (int i = 0; i < 5; i++) {
                String nextResult = request.get(url, HttpStatus.OK, String.class);

                List<String> nextUids = uidLinePattern.matcher(nextResult).results().map(m -> m.group(1)).toList();

                assertThat(nextUids).hasSize(1);
                String nextUid = uids.getFirst();
                assertThat(nextUid).isEqualTo(firstUid);
            }
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldIncludeVeventsFromAllEntitiesInICSFile() throws Exception {
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", Language.ENGLISH.name(),
                    tutor, new HashSet<>(Set.of(student)));
            tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusHours(2), 5);
            Lecture lecture = lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), PAST_DATE.minusHours(2), PAST_DATE);
            Exam exam = examUtilService.addExam(course, PAST_DATE, PAST_DATE.plusHours(2), PAST_DATE.plusHours(3), PAST_DATE.plusDays(1), PAST_DATE.plusDays(2),
                    PAST_DATE.plusDays(3), "Test-Examiner");
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, PAST_DATE, PAST_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
            TextExercise nonQuizExercise = textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);

            String tutorialGroupTitle = tutorialGroup.getTitle();
            String lectureTitle = lecture.getTitle();
            String examTitle = exam.getTitle();
            String quizExerciseTitle = quizExercise.getTitle();
            String nonQuizExerciseTitle = nonQuizExercise.getTitle();

            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, CalendarSubscriptionFilterOption.values(), Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);
            assertThat(result).contains(tutorialGroupTitle);
            assertThat(result).contains(lectureTitle);
            assertThat(result).contains(examTitle);
            assertThat(result).contains(quizExerciseTitle);
            assertThat(result).contains(nonQuizExerciseTitle);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldIncludeVeventsFromFilteredEntitiesInICSFile() throws Exception {
            TutorialGroup tutorialGroup = tutorialGroupUtilService.createTutorialGroup(course.getId(), "Test Tutorial Group", "", 10, false, "Garching", Language.ENGLISH.name(),
                    tutor, new HashSet<>(Set.of(student)));
            tutorialGroupUtilService.createIndividualTutorialGroupSession(tutorialGroup.getId(), FIXED_DATE, FIXED_DATE.plusHours(2), 5);
            Lecture lecture = lectureUtilService.createLecture(course, PAST_DATE.minusDays(1), PAST_DATE.minusHours(2), PAST_DATE);
            Exam exam = examUtilService.addExam(course, PAST_DATE, PAST_DATE.plusHours(2), PAST_DATE.plusHours(3), PAST_DATE.plusDays(1), PAST_DATE.plusDays(2),
                    PAST_DATE.plusDays(3), "Test-Examiner");
            QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuizWithAllQuestionTypes(course, FUTURE_DATE, FUTURE_DATE.plusDays(1), null, QuizMode.INDIVIDUAL);
            TextExercise nonQuizExercise = textExerciseUtilService.createIndividualTextExercise(course, PAST_DATE, PAST_DATE.plusDays(1), null, null);

            String tutorialGroupTitle = tutorialGroup.getTitle();
            String lectureTitle = lecture.getTitle();
            String examTitle = exam.getTitle();
            String quizExerciseTitle = quizExercise.getTitle();
            String nonQuizExerciseTitle = nonQuizExercise.getTitle();

            String expectedToken = generateUniqueTestToken();
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);
            long courseId = course.getId();
            String url = buildUrl(courseId, expectedToken, new CalendarSubscriptionFilterOption[] { CalendarSubscriptionFilterOption.TUTORIALS,
                    CalendarSubscriptionFilterOption.LECTURES, CalendarSubscriptionFilterOption.EXAMS }, Language.ENGLISH);

            String result = request.get(url, HttpStatus.OK, String.class);
            assertThat(result).contains(tutorialGroupTitle);
            assertThat(result).contains(lectureTitle);
            assertThat(result).contains(examTitle);
            assertThat(result).doesNotContain(quizExerciseTitle);
            assertThat(result).doesNotContain(nonQuizExerciseTitle);
        }

    }

    @Nested
    class GetCalendarEventSubscriptionTokenTests {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReuseExistingToken() throws Exception {
            String expectedToken = "921651b1118f216d04b190fc0659386b";
            userUtilService.clearAllTokensAndSetTokenForUser(student, expectedToken);

            String url = "/api/core/calendar/subscription-token";
            String actualToken = request.get(url, HttpStatus.OK, String.class);

            assertThat(actualToken).isEqualTo(expectedToken);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldGenerateNewToken() throws Exception {
            String url = "/api/core/calendar/subscription-token";
            String actualToken = request.get(url, HttpStatus.OK, String.class);

            assertThat(actualToken).isNotNull();
            assertThat(actualToken.length()).isEqualTo(32);
            assertThat(actualToken).matches("^[0-9a-f]+$");
        }
    }
}
