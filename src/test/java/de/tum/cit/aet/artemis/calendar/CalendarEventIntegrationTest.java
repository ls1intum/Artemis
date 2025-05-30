package de.tum.cit.aet.artemis.calendar;

import static org.assertj.core.api.Assertions.assertThat;

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

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
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
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenUserOnlyPartOfNonActiveCourse() throws Exception {
            setupNonActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(3).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldNotIncludeEventsRepresentingCancelledTutorialGroupSessions() throws Exception {
            setupActiveCourseWithCancelledTutorialGroupSessionsScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                    .map(session -> new CalendarEventDTO(session, ZoneId.of(TEST_TIMEZONE))).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
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
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnOnlyEventsRepresentingCourseCalendarEventsForTutorWhenNotPartOfAnyTutorialGroup() throws Exception {
            setupActiveCourseWithoutParticipatedTutorialGroupScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForStudentWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(4).map(session -> new CalendarEventDTO(session, ZoneId.of(TEST_TIMEZONE)))
                    .toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
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
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(4).map(session -> new CalendarEventDTO(session, ZoneId.of(TEST_TIMEZONE)))
                    .toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
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
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForInstructorWhenQueriedForOneMonth() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(1).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(4).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnCorrectEventsForStudentWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(8).map(session -> new CalendarEventDTO(session, ZoneId.of(TEST_TIMEZONE)))
                    .toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
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
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().limit(8).map(session -> new CalendarEventDTO(session, ZoneId.of(TEST_TIMEZONE)))
                    .toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
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
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnCorrectEventsForInstructorWhenQueriedForMultipleMonths() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().limit(8).map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = expectedCourseEvents.stream().collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringCollectionOrder()
                    .isEqualTo(expected);
        }

        // TODO: write a test that verifies correct format of response DTO-timestamps
    }

    @Nested
    class CreateCourseCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestForStudent() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventDTO> body = List
                    .of(new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));

            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnBadRequestForTutor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventDTO> body = List
                    .of(new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));

            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnBadRequestForEditorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventDTO> body = List
                    .of(new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));

            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestForInstructorWhenNotPartOfCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventDTO> body = List
                    .of(new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));

            request.postWithResponseBody(URL, body, Set.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldCreateEventForEditor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventDTO expected = new CalendarEventDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test");

            List<CalendarEventDTO> requestBody = List.of(expected);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventForInstructor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventDTO expected = new CalendarEventDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test");

            List<CalendarEventDTO> requestBody = List.of(expected);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldCreateMultipleEventsForEditor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventDTO expected1 = new CalendarEventDTO(null, "Lecture A", course.getTitle(), course.getStartDate().plusDays(1),
                    course.getStartDate().plusDays(1).plusHours(2), "Room 101", "Prof. A");

            CalendarEventDTO expected2 = new CalendarEventDTO(null, "Workshop B", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(3), "Room 102", "Prof. B");

            CalendarEventDTO expected3 = new CalendarEventDTO(null, "Q&A Session", course.getTitle(), course.getStartDate().plusDays(3),
                    course.getStartDate().plusDays(3).plusHours(1), "Room 103", "Prof. C");

            List<CalendarEventDTO> requestBody = List.of(expected1, expected2, expected3);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

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

            CalendarEventDTO expected1 = new CalendarEventDTO(null, "Lecture X", course.getTitle(), course.getStartDate().plusDays(4),
                    course.getStartDate().plusDays(4).plusHours(2), "Auditorium", "Dr. X");

            CalendarEventDTO expected2 = new CalendarEventDTO(null, "Lab Y", course.getTitle(), course.getStartDate().plusDays(5), course.getStartDate().plusDays(5).plusHours(3),
                    "Lab A", "Dr. Y");

            CalendarEventDTO expected3 = new CalendarEventDTO(null, "Panel Z", course.getTitle(), course.getStartDate().plusDays(6), course.getStartDate().plusDays(6).plusHours(1),
                    "Hall B", "Dr. Z");

            List<CalendarEventDTO> requestBody = List.of(expected1, expected2, expected3);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(3);

            assertThat(response)
                    .usingRecursiveFieldByFieldElementComparator(RecursiveComparisonConfiguration.builder().withIgnoredFields("id")
                            .withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).build())
                    .containsExactlyInAnyOrder(expected1, expected2, expected3);
        }

        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnNotFoundWhenCourseDoesNotExist() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(-1L);

            List<CalendarEventDTO> body = List
                    .of(new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.NOT_FOUND);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasId() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventDTO> body = List.of(new CalendarEventDTO("calendar-1", "Test Event", null, course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoTitle() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventDTO> body = List
                    .of(new CalendarEventDTO(null, null, null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldOverwriteWithActualCourseNameWhenEventHasNoCourseName() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventDTO expected = new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1",
                    "Dr. Test");

            List<CalendarEventDTO> requestBody = List.of(expected);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);

            CalendarEventDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class)
                    .ignoringFields("id", "courseName").isEqualTo(expected);

            assertThat(actual.courseName()).isEqualTo(course.getTitle());
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestWhenEventHasNoStartDate() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            List<CalendarEventDTO> body = List.of(new CalendarEventDTO(null, "Test Event", null, null, course.getStartDate().plusDays(2).plusHours(2), "Room A1", "Dr. Test"));
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoEndDate() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventDTO expected = new CalendarEventDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2), null, "Room A1", "Dr. Test");

            List<CalendarEventDTO> requestBody = List.of(expected);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);

            CalendarEventDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoLocation() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventDTO expected = new CalendarEventDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), null, "Dr. Test");

            List<CalendarEventDTO> requestBody = List.of(expected);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventWhenEventHasNoFacilitator() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId());

            CalendarEventDTO expected = new CalendarEventDTO(null, "Test Event", course.getTitle(), course.getStartDate().plusDays(2),
                    course.getStartDate().plusDays(2).plusHours(2), "Room A1", null);

            List<CalendarEventDTO> requestBody = List.of(expected);
            Set<CalendarEventDTO> response = request.postSetWithResponseBody(URL, requestBody, CalendarEventDTO.class, HttpStatus.OK);

            assertThat(response).hasSize(1);
            CalendarEventDTO actual = response.iterator().next();

            assertThat(actual).usingRecursiveComparison().withComparatorForType(Comparator.comparing(ZonedDateTime::toInstant), ZonedDateTime.class).ignoringFields("id")
                    .isEqualTo(expected);
        }

        // TODO: write a test that verifies correct format of response DTO-timestamps
    }

    @Nested
    class UpdateCourseCalendarEvents {

    }

    @Nested
    class DeleteCourseCalendarEvents {

    }
}
