package de.tum.cit.aet.artemis.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
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
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventReadDTO;
import de.tum.cit.aet.artemis.calendar.dto.CalendarEventWriteDTO;
import de.tum.cit.aet.artemis.calendar.util.TimestampFormatAssert;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

class CalendarEventIntegrationTest extends AbstractCalendarIntegrationTest {

    @Nested
    class GetCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenMonthsHaveWrongFormat() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "11-2025";
            String URL = assembleURLForGetRequest(monthKeys);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenMonthsAreEmpty() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "";
            String URL = assembleURLForGetRequest(monthKeys);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestWhenTimeZoneFormattedIncorrectly() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String malformedTimeZone = "EST";
            String URL = assembleURLForGetRequest(monthKeys, malformedTimeZone);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = NOT_STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenUserNotPartOfAnyCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenUserOnlyPartOfNonActiveCourse() throws Exception {
            setupNonActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(3).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldNotIncludeEventsRepresentingCancelledTutorialGroupSessions() throws Exception {
            setupActiveCourseWithCancelledTutorialGroupSessionsScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedTutorialEvents = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                    .map(session -> new CalendarEventReadDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnOnlyEventsRepresentingCourseCalendarEventsForStudentWhenNotPartOfAnyTutorialGroup() throws Exception {
            setupActiveCourseWithoutParticipatedTutorialGroupScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnOnlyEventsRepresentingCourseCalendarEventsForTutorWhenNotPartOfAnyTutorialGroup() throws Exception {
            setupActiveCourseWithoutParticipatedTutorialGroupScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForStudentWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(4).map(session -> new CalendarEventReadDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForTutorWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(4).map(session -> new CalendarEventReadDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnCorrectEventsForEditorWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForInstructorWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForStudentWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(8).map(session -> new CalendarEventReadDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForTutorWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(8).map(session -> new CalendarEventReadDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "TA")
        void shouldReturnCorrectEventsForEditorWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForInstructorWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoCalendarEventsNotVisibleToStudentsForStudent() throws Exception {
            setupActiveCourseWithMutualExclusiveCourseCalendarEventVisibilityScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedTutorialEvents = tutorialGroupSessions.stream().map(session -> new CalendarEventReadDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToStudents)
                    .map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnNoCalendarEventsNotVisibleToTutorForTutor() throws Exception {
            setupActiveCourseWithMutualExclusiveCourseCalendarEventVisibilityScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedTutorialEvents = tutorialGroupSessions.stream().map(session -> new CalendarEventReadDTO(session, TEST_TIMEZONE)).toList();
            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToTutors)
                    .map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnNoCalendarEventsNotVisibleToEditorForEditor() throws Exception {
            setupActiveCourseWithMutualExclusiveCourseCalendarEventVisibilityScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToEditors)
                    .map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNoCalendarEventsNotVisibleToInstructorForInstructor() throws Exception {
            setupActiveCourseWithMutualExclusiveCourseCalendarEventVisibilityScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventReadDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventReadDTO> expectedCourseEvents = courseCalendarEvents.stream().filter(CourseCalendarEvent::isVisibleToInstructors)
                    .map(event -> new CalendarEventReadDTO(event, TEST_TIMEZONE)).toList();
            Map<String, List<CalendarEventReadDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnEventsWithIso8601ZonedTimestamps() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            String response = request.get(URL, HttpStatus.OK, String.class);

            TimestampFormatAssert.assertThat(response).hasIso8601OffsetTimestamps("startDate", "endDate");
        }
    }

    @Nested
    class CreateCourseCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudent() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventWriteDTO> body = List.of(new CalendarEventWriteDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));

            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForTutor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventWriteDTO> body = List.of(new CalendarEventWriteDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnForbiddenForEditorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventWriteDTO> body = List.of(new CalendarEventWriteDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventWriteDTO> body = List.of(new CalendarEventWriteDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldCreateEventForEditor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventWriteDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventForInstructor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventWriteDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldCreateMultipleEventsForEditor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected1 = new CalendarEventWriteDTO(null, "Lecture A", course.getTitle(), course.getStartDate().plusDays(1),
                    course.getStartDate().plusDays(1).plusHours(2), "Room 101", "Prof. A", true, true, true, true);

            CalendarEventWriteDTO expected2 = new CalendarEventWriteDTO(null, "Workshop B", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(3), "Room 102", "Prof. B", true, true, true, true);

            CalendarEventWriteDTO expected3 = new CalendarEventWriteDTO(null, "Q&A Session", course.getTitle(), course.getStartDate().plusDays(3),
                    course.getStartDate().plusDays(3).plusHours(1), "Room 103", "Prof. C", true, true, true, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected1, expected2, expected3);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(3);

            assertThat(response)
                    .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder().withIgnoredFields("id")
                            .withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).build())
                    .containsExactlyInAnyOrder(expected1, expected2, expected3);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateMultipleEventsForInstructor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected1 = new CalendarEventWriteDTO(null, "Lecture X", course.getTitle(), course.getStartDate().plusDays(4),
                    course.getStartDate().plusDays(4).plusHours(2), "Auditorium", "Dr. X", true, true, true, true);

            CalendarEventWriteDTO expected2 = new CalendarEventWriteDTO(null, "Lab Y", course.getTitle(), course.getStartDate().plusDays(5),
                    course.getStartDate().plusDays(5).plusHours(3), "Lab A", "Dr. Y", true, true, true, true);

            CalendarEventWriteDTO expected3 = new CalendarEventWriteDTO(null, "Panel Z", course.getTitle(), course.getStartDate().plusDays(6),
                    course.getStartDate().plusDays(6).plusHours(1), "Hall B", "Dr. Z", true, true, true, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected1, expected2, expected3);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(3);

            assertThat(response)
                    .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder().withIgnoredFields("id")
                            .withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).build())
                    .containsExactlyInAnyOrder(expected1, expected2, expected3);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(-1L);

            List<CalendarEventWriteDTO> body = List.of(new CalendarEventWriteDTO(null, "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasId() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventWriteDTO> body = List.of(new CalendarEventWriteDTO("calendar-1", "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoTitle() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventWriteDTO> body = List.of(new CalendarEventWriteDTO(null, null, null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, true, true, true));
            request.postWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldOverwriteWithActualCourseNameWhenEventHasNoCourseName() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2),
                    "Room A1", "Dr. Test", true, false, false, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);

            CalendarEventWriteDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class)
                    .ignoringFields("id", "courseName").isEqualTo(expected);

            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoStartDate() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventWriteDTO> body = List
                    .of(new CalendarEventWriteDTO(null, "Test Event", null, null, course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test", true, false, false, true));
            request.postWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoEndDate() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2), null, "Room A1", "Dr. Test", true,
                    false, false, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);

            CalendarEventWriteDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoLocation() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), null, "Dr. Test", true, false, false, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventWriteDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoFacilitator() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", null, true, false, false, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected);
            Set<CalendarEventWriteDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventWriteDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventNotVisibleToAtLeastOneUserGroup() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected1 = new CalendarEventWriteDTO(null, "Lecture X", course.getTitle(), course.getStartDate().plusDays(4),
                    course.getStartDate().plusDays(4).plusHours(2), "Auditorium", "Dr. X", true, true, true, true);

            CalendarEventWriteDTO expected2 = new CalendarEventWriteDTO(null, "Lab Y", course.getTitle(), course.getStartDate().plusDays(5),
                    course.getStartDate().plusDays(5).plusHours(3), "Lab A", "Dr. Y", false, false, false, false);

            CalendarEventWriteDTO expected3 = new CalendarEventWriteDTO(null, "Panel Z", course.getTitle(), course.getStartDate().plusDays(6),
                    course.getStartDate().plusDays(6).plusHours(1), "Hall B", "Dr. Z", true, true, true, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected1, expected2, expected3);
            request.postSetWithResponseBody(URL, requestBody, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnEventsWithIso8601UtcTimestamps() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Prof. Test", true, false, false, true);

            List<CalendarEventWriteDTO> requestBody = List.of(expected);
            String response = request.postWithResponseBodyString(URL, requestBody, HttpStatus.OK);

            TimestampFormatAssert.assertThat(response).hasIso8601UtcTimestamps("startDate", "endDate");
        }
    }

    @Nested
    class UpdateCourseCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudent() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", true, false, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForTutor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", true, false, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnForbiddenForEditorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", true, false, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldUpdateEventForEditor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            CalendarEventWriteDTO actual = request.putWithResponseBody(URL, expected, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventForInstructor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            CalendarEventWriteDTO actual = request.putWithResponseBody(URL, expected, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(-1L);

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventIdMalformed() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO(eventToChange.getId().toString(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, false);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundWhenEventDoesNotExist() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + -1, "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, false);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventNotVisibleToAtLeastOneUserGroup() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, false, false, false);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoId() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO(null, "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoTitle() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), null, course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldOverwriteWithActualCourseNameWhenEventHasNoCourseName() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", null, eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            CalendarEventWriteDTO actual = request.putWithResponseBody(URL, expected, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("courseName")
                    .isEqualTo(expected);

            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoStartDate() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO body = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), null, eventToChange.getEndDate().plusDays(1),
                    "New Room", "New Facilitator", false, true, false, true);

            request.putWithResponseBody(URL, body, CalendarEventWriteDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventWhenEventHasNoEndDate() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    null, "New Room", "New Facilitator", false, true, false, true);

            CalendarEventWriteDTO actual = request.putWithResponseBody(URL, expected, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventWhenEventHasNoLocation() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), null, "New Facilitator", false, true, false, true);

            CalendarEventWriteDTO actual = request.putWithResponseBody(URL, expected, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldUpdateEventWhenEventHasNoFacilitator() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", null, false, true, false, true);

            CalendarEventWriteDTO actual = request.putWithResponseBody(URL, expected, CalendarEventWriteDTO.class, HttpStatus.OK);

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnEventsWithIso8601UtcTimestamps() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToChange = courseCalendarEvents.getFirst();
            String URL = assembleURLForPutRequest(course.getId());

            CalendarEventWriteDTO expected = new CalendarEventWriteDTO("course-" + eventToChange.getId(), "New Title", course.getTitle(), eventToChange.getStartDate().plusDays(1),
                    eventToChange.getEndDate().plusDays(1), "New Room", "New Facilitator", false, true, false, true);

            String response = request.putWithResponseBody(URL, expected, String.class, HttpStatus.OK);

            TimestampFormatAssert.assertThat(response).hasIso8601UtcTimestamps("startDate", "endDate");
        }
    }

    @Nested
    class DeleteCourseCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnForbiddenForStudent() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest(course.getId(), eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnForbiddenForTutor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest(course.getId(), eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnForbiddenForEditorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest(course.getId(), eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnForbiddenForInstructorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest(course.getId(), eventToDelete.getId());

            request.delete(URL, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldDeleteEventForEditor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest(course.getId(), eventToDelete.getId());

            request.delete(URL, HttpStatus.NO_CONTENT);

            assertThat(courseCalendarEventRepository.findById(eventToDelete.getId())).isEmpty();
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldDeleteEventForInstructor() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest(course.getId(), eventToDelete.getId());

            request.delete(URL, HttpStatus.NO_CONTENT);

            assertThat(courseCalendarEventRepository.findById(eventToDelete.getId())).isEmpty();
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundIfCourseDoesNotExist() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            String URL = assembleURLForDeleteRequest(-1L, eventToDelete.getId());

            request.delete(URL, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundIfEventDoesNotExist() throws Exception {
            setupActiveCourseScenario();

            String URL = assembleURLForDeleteRequest(course.getId(), -1L);

            request.delete(URL, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestIfEventDoesNotBelongToCourse() throws Exception {
            setupActiveCourseScenario();

            CourseCalendarEvent eventToDelete = courseCalendarEvents.getFirst();
            Course otherCourse = courseUtilService.createActiveCourseInTimezone(TEST_TIMEZONE, 2, 2);
            String URL = assembleURLForDeleteRequest(otherCourse.getId(), eventToDelete.getId());

            request.delete(URL, HttpStatus.BAD_REQUEST);
        }
    }
}
