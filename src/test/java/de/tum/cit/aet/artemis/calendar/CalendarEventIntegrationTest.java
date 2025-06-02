package de.tum.cit.aet.artemis.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.calendar.domain.CourseCalendarEvent;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventManageDTO;
import de.tum.cit.aet.artemis.calendar.util.TimestampFormatAssert;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

class CalendarEventIntegrationTest extends AbstractCalendarIntegrationTest {

    @Nested
    class GetCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenMonthsHaveWrongFormat() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "11-2025";
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenMonthsAreEmpty() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "";
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenTimeZoneFormattedIncorrectly() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String malformedTimeZone = "EST";
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, malformedTimeZone);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = NOT_STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenUserNotPartOfAnyCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenUserOnlyPartOfNonActiveCourse() throws Exception {
            setupNonActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(3).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldNotIncludeEventsRepresentingCancelledTutorialGroupSessions() throws Exception {
            setupActiveCourseWithCancelledTutorialGroupSessionsScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                    .map(session -> new CalendarEventDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnOnlyEventsRepresentingCourseCalendarEventsForStudentWhenNotPartOfAnyTutorialGroup() throws Exception {
            setupActiveCourseWithoutParticipatedTutorialGroupScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnOnlyEventsRepresentingCourseCalendarEventsForTutorWhenNotPartOfAnyTutorialGroup() throws Exception {
            setupActiveCourseWithoutParticipatedTutorialGroupScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForStudentWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(4).map(session -> new CalendarEventDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForTutorWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(4).map(session -> new CalendarEventDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnCorrectEventsForEditorWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForInstructorWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForStudentWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(8).map(session -> new CalendarEventDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForTutorWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(8).map(session -> new CalendarEventDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForEditorWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForInstructorWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoCalendarEventsNotVisibleToStudentsForStudent() throws Exception {
            setupActiveCourseWithMutualExclusiveVisibilityForCourseCalendarEventScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().map(session -> new CalendarEventDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToStudents)
                    .map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnNoCalendarEventsNotVisibleToTutorForTutor() throws Exception {
            setupActiveCourseWithMutualExclusiveVisibilityForCourseCalendarEventScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().map(session -> new CalendarEventDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToTutors)
                    .map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnNoCalendarEventsNotVisibleToEditorForEditor() throws Exception {
            setupActiveCourseWithMutualExclusiveVisibilityForCourseCalendarEventScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToEditors)
                    .map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNoCalendarEventsNotVisibleToInstructorForInstructor() throws Exception {
            setupActiveCourseWithMutualExclusiveVisibilityForCourseCalendarEventScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToInstructors)
                    .map(event -> new CalendarEventDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldSplitEventAcrossDaysWhenEventSpansMultipleDays() throws Exception {
            setupActiveCourseWithCourseCalendarEventsSpanningMultipleDaysScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_CALENDAR_EVENTS_RETURN_TYPE);

            CourseCalendarEvent firstEvent = courseCalendarEvents.get(0);
            ZoneId timezone = firstEvent.getStartDate().getZone();
            CalendarEventDTO expectedDTO1 = new CalendarEventDTO("course-" + firstEvent.getId() + "-0", firstEvent.getTitle(), course.getTitle(), firstEvent.getStartDate(),
                    firstEvent.getStartDate().toLocalDate().atTime(LocalTime.MAX).withNano(999_999_999).atZone(timezone), firstEvent.getLocation(), firstEvent.getFacilitator());
            CalendarEventDTO expectedDTO2 = new CalendarEventDTO("course-" + firstEvent.getId() + "-1", firstEvent.getTitle(), course.getTitle(),
                    firstEvent.getStartDate().plusDays(1).toLocalDate().atStartOfDay(timezone), firstEvent.getEndDate(), firstEvent.getLocation(), firstEvent.getFacilitator());
            CourseCalendarEvent secondEvent = courseCalendarEvents.get(1);
            System.out.println("second event start: " + secondEvent.getStartDate());
            CalendarEventDTO expectedDTO3 = new CalendarEventDTO("course-" + secondEvent.getId() + "-0", secondEvent.getTitle(), course.getTitle(), secondEvent.getStartDate(),
                    secondEvent.getStartDate().toLocalDate().atTime(LocalTime.MAX).withNano(999_999_999).atZone(timezone), secondEvent.getLocation(), secondEvent.getFacilitator());
            CalendarEventDTO expectedDTO4 = new CalendarEventDTO("course-" + secondEvent.getId() + "-1", secondEvent.getTitle(), course.getTitle(),
                    secondEvent.getStartDate().plusDays(1).toLocalDate().atStartOfDay(timezone),
                    secondEvent.getStartDate().plusDays(1).toLocalDate().atTime(LocalTime.MAX).withNano(999_999_999).atZone(timezone), secondEvent.getLocation(),
                    secondEvent.getFacilitator());
            CalendarEventDTO expectedDTO5 = new CalendarEventDTO("course-" + secondEvent.getId() + "-2", secondEvent.getTitle(), course.getTitle(),
                    secondEvent.getStartDate().plusDays(2).toLocalDate().atStartOfDay(timezone), secondEvent.getEndDate(), secondEvent.getLocation(), secondEvent.getFacilitator());

            List<CalendarEventDTO> expectedCourseEvents = List.of(expectedDTO1, expectedDTO2, expectedDTO3, expectedDTO4, expectedDTO5);
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnEventsWithIso8601ZonedTimestamps() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForCalendarEventsGetRequest(monthKeys, TEST_TIMEZONE_STRING);
            String response = request.get(URL, HttpStatus.OK, String.class);

            TimestampFormatAssert.assertThat(response).hasIso8601OffsetTimestamps("startDate", "endDate");
        }
    }

    @Nested
    class GetCourseCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudent() throws Exception {
            setupActiveCourseScenario();

            String URL = assembleURLForCourseCalendarEventsGetRequest(course.getId());
            request.get(URL, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForTutor() throws Exception {
            setupActiveCourseScenario();

            String URL = assembleURLForCourseCalendarEventsGetRequest(course.getId());
            request.get(URL, HttpStatus.FORBIDDEN, String.class);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnCorrectEventsForEditor() throws Exception {
            setupActiveCourseScenario();

            String URL = assembleURLForCourseCalendarEventsGetRequest(course.getId());
            List<CalendarEventManageDTO> actual = request.get(URL, HttpStatus.OK, GET_COURSE_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventManageDTO> expected = courseCalendarEvents.stream().map(CalendarEventManageDTO::new).toList();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForInstructor() throws Exception {
            setupActiveCourseScenario();

            String URL = assembleURLForCourseCalendarEventsGetRequest(course.getId());
            List<CalendarEventManageDTO> actual = request.get(URL, HttpStatus.OK, GET_COURSE_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventManageDTO> expected = courseCalendarEvents.stream().map(CalendarEventManageDTO::new).toList();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnEmptyListWhenCourseHasNoCourseCalendarEvents() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForCourseCalendarEventsGetRequest(course.getId());
            List<CalendarEventManageDTO> actual = request.get(URL, HttpStatus.OK, GET_COURSE_CALENDAR_EVENTS_RETURN_TYPE);

            List<CalendarEventManageDTO> expected = List.of();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
            setupActiveCourseScenario();

            String URL = assembleURLForCourseCalendarEventsGetRequest(-1L);
            request.get(URL, HttpStatus.NOT_FOUND, String.class);
        }
    }

    @Nested
    class CreateCourseCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudent() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventManageDTO> body = List.of(new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));

            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForTutor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventManageDTO> body = List.of(new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnForbiddenForEditorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventManageDTO> body = List.of(new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventManageDTO> body = List.of(new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldCreateEventForEditor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected = new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected);
            Set<CalendarEventManageDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventManageDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class)
                    .ignoringFields("id", "courseName").isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventForInstructor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected = new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected);
            Set<CalendarEventManageDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventManageDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class)
                    .ignoringFields("id", "courseName").isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldCreateMultipleEventsForEditor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected1 = new CalendarEventManageDTO(null, "Lecture A", null, course.getStartDate().plusDays(1),
                    course.getStartDate().plusDays(1).plusHours(2), "Room 101", "Prof. A", true, true, true, true);

            CalendarEventManageDTO expected2 = new CalendarEventManageDTO(null, "Workshop B", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(3), "Room 102", "Prof. B", true, true, true, true);

            CalendarEventManageDTO expected3 = new CalendarEventManageDTO(null, "Q&A Session", null, course.getStartDate().plusDays(3),
                    course.getStartDate().plusDays(3).plusHours(1), "Room 103", "Prof. C", true, true, true, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected1, expected2, expected3);
            Set<CalendarEventManageDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(3);

            assertThat(response)
                    .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder().withIgnoredFields("id", "courseName")
                            .withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).build())
                    .containsExactlyInAnyOrder(expected1, expected2, expected3);
            assertThat(response).extracting(CalendarEventManageDTO::courseName).containsOnly(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateMultipleEventsForInstructor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected1 = new CalendarEventManageDTO(null, "Lecture X", null, course.getStartDate().plusDays(4),
                    course.getStartDate().plusDays(4).plusHours(2), "Auditorium", "Dr. X", true, true, true, true);

            CalendarEventManageDTO expected2 = new CalendarEventManageDTO(null, "Lab Y", null, course.getStartDate().plusDays(5), course.getStartDate().plusDays(5).plusHours(3),
                    "Lab A", "Dr. Y", true, true, true, true);

            CalendarEventManageDTO expected3 = new CalendarEventManageDTO(null, "Panel Z", null, course.getStartDate().plusDays(6), course.getStartDate().plusDays(6).plusHours(1),
                    "Hall B", "Dr. Z", true, true, true, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected1, expected2, expected3);
            Set<CalendarEventManageDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(3);

            assertThat(response)
                    .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder().withIgnoredFields("id", "courseName")
                            .withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).build())
                    .containsExactlyInAnyOrder(expected1, expected2, expected3);
            assertThat(response).extracting(CalendarEventManageDTO::courseName).containsOnly(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(-1L);

            List<CalendarEventManageDTO> body = List.of(new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, CalendarEventManageDTO.class, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasId() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventManageDTO> body = List.of(new CalendarEventManageDTO("calendar-1", "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoTitle() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventManageDTO> body = List.of(new CalendarEventManageDTO(null, null, null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasCourseName() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected = new CalendarEventManageDTO(null, "Test Event", "Test Name", course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, false, false, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected);
            request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoStartDate() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventManageDTO> body = List.of(
                    new CalendarEventManageDTO(null, "Test Event", null, null, course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, false, false, true));
            request.postWithResponseBody(URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoEndDate() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected = new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2), null, "Room A1", "Dr. Test", true, false,
                    false, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected);
            Set<CalendarEventManageDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);

            CalendarEventManageDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class)
                    .ignoringFields("id", "courseName").isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoLocation() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected = new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), null, "Dr. Test", true, false, false, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected);
            Set<CalendarEventManageDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventManageDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class)
                    .ignoringFields("id", "courseName").isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoFacilitator() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected = new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", null, true, false, false, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected);
            Set<CalendarEventManageDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventManageDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class)
                    .ignoringFields("id", "courseName").isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventNotVisibleToAtLeastOneUserGroup() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected1 = new CalendarEventManageDTO(null, "Lecture X", null, course.getStartDate().plusDays(4),
                    course.getStartDate().plusDays(4).plusHours(2), "Auditorium", "Dr. X", true, true, true, true);

            CalendarEventManageDTO expected2 = new CalendarEventManageDTO(null, "Lab Y", null, course.getStartDate().plusDays(5), course.getStartDate().plusDays(5).plusHours(3),
                    "Lab A", "Dr. Y", false, false, false, false);

            CalendarEventManageDTO expected3 = new CalendarEventManageDTO(null, "Panel Z", null, course.getStartDate().plusDays(6), course.getStartDate().plusDays(6).plusHours(1),
                    "Hall B", "Dr. Z", true, true, true, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected1, expected2, expected3);
            request.postSetWithResponseBody(URL, requestBody, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnEventsWithIso8601UtcTimestamps() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventManageDTO expected = new CalendarEventManageDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Prof. Test", true, false, false, true);

            List<CalendarEventManageDTO> requestBody = List.of(expected);
            String response = request.postWithResponseBodyString(URL, requestBody, HttpStatus.OK);

            TimestampFormatAssert.assertThat(response).hasIso8601UtcTimestamps("startDate", "endDate");
        }
    }

    @Nested
    class UpdateCourseCalendarEvent {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudent() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", true, false, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForTutor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", true, false, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnForbiddenForEditorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", true, false, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldUpdateEventForEditor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO expected = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            CalendarEventManageDTO actual = request.putWithResponseBody(PUT_REQUEST_URL, expected, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("courseName")
                    .isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventForInstructor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO expected = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            CalendarEventManageDTO actual = request.putWithResponseBody(PUT_REQUEST_URL, expected, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("courseName")
                    .isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventIdMalformed() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO(eventToChange.getId().toString(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, false);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundWhenEventDoesNotExist() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + -1, "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, false);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventNotVisibleToAtLeastOneUserGroup() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, false);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoId() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO(null, "New Title", null, eventToChange.getStartDate().plusDays(1), eventToChange.getEndDate().plusDays(1),
                    "New Room", "New Facilitator", false, true, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoTitle() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), null, null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasCourseName() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", "New Name", eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoStartDate() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO body = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, null, eventToChange.getEndDate().plusDays(1), "New Room",
                    "New Facilitator", false, true, false, true);

            request.putWithResponseBody(PUT_REQUEST_URL, body, CalendarEventManageDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventWhenEventHasNoEndDate() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO expected = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1), null,
                    "New Room", "New Facilitator", false, true, false, true);

            CalendarEventManageDTO actual = request.putWithResponseBody(PUT_REQUEST_URL, expected, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("courseName")
                    .isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventWhenEventHasNoLocation() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO expected = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), null, "New Facilitator", false, true, false, true);

            CalendarEventManageDTO actual = request.putWithResponseBody(PUT_REQUEST_URL, expected, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("courseName")
                    .isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventWhenEventHasNoFacilitator() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO expected = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", null, false, true, false, true);

            CalendarEventManageDTO actual = request.putWithResponseBody(PUT_REQUEST_URL, expected, CalendarEventManageDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("courseName")
                    .isEqualTo(expected);
            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnEventsWithIso8601UtcTimestamps() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();

            CalendarEventManageDTO expected = new CalendarEventManageDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            String response = request.putWithResponseBody(PUT_REQUEST_URL, expected, String.class, HttpStatus.OK);

            TimestampFormatAssert.assertThat(response).hasIso8601UtcTimestamps("startDate", "endDate");
        }
    }

    @Nested
    class DeleteCourseCalendarEvent {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudent() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest("course-" + eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForTutor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest("course-" + eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnForbiddenForEditorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest("course-" + eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest("course-" + eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldDeleteEventForEditor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest("course-" + eventToDelete.getId());

            request.delete(URL, HttpStatus.NO_CONTENT);

            assertThat(courseCalendarEventRepository.findById(eventToDelete.getId())).isEmpty();
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldDeleteEventForInstructor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest("course-" + eventToDelete.getId());

            request.delete(URL, HttpStatus.NO_CONTENT);

            assertThat(courseCalendarEventRepository.findById(eventToDelete.getId())).isEmpty();
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundIfEventDoesNotExist() throws Exception {
            setupActiveCourseScenario();

            String URL = assembleURLForDeleteRequest("course-" + -1L);

            request.delete(URL, HttpStatus.NOT_FOUND);
        }
    }
}
