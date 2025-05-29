package de.tum.cit.aet.artemis.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        void shouldReturnBadRequestForStudentWhenMonthsHaveWrongFormat() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "11-2025";
            String URL = assembleURLForGetRequest(monthKeys);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnBadRequestForTutorWhenMonthsHaveWrongFormat() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "11-2025";
            String URL = assembleURLForGetRequest(monthKeys);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestForStudentWhenMonthsAreEmpty() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "";
            String URL = assembleURLForGetRequest(monthKeys);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnBadRequestForTutorWhenMonthsAreEmpty() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = "";
            String URL = assembleURLForGetRequest(monthKeys);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestForStudentWhenTimeZoneFormattedIncorrectly() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String malformedTimeZone = "EST";
            String URL = assembleURLForGetRequest(monthKeys, malformedTimeZone);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnBadRequestForTutorWhenTimeZoneFormattedIncorrectly() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String malformedTimeZone = "EST";
            String URL = assembleURLForGetRequest(monthKeys, malformedTimeZone);
            request.get(URL, HttpStatus.BAD_REQUEST, String.class);
        }

        @Test
        @WithMockUser(username = NOT_STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsForStudentWhenUserNotPartOfAnyCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = NOT_TUTOR_LOGIN, roles = "TA")
        void shouldReturnNoEventsForTutorWhenUserNotPartOfAnyCourse() throws Exception {
            setupUserNotPartOfAnyCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsForStudentWhenUserOnlyPartOfNonActiveCourse() throws Exception {
            setupNonActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnNoEventsForTutorWhenUserOnlyPartOfNonActiveCourse() throws Exception {
            setupNonActiveCourseScenario();

            String monthKeys = YearMonth.now().toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnNoEventsForStudentWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(3).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnNoEventsForTutorWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
            setupActiveCourseScenario();

            String monthKeys = YearMonth.now().minusMonths(3).toString();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            assertThat(actual).isEmpty();
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

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
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

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
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

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
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

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
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

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
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

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldNotIncludeEventsRepresentingCancelledTutorialGroupSessionsForStudent() throws Exception {
            setupActiveCourseWithCancelledTutorialGroupSessionsScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                    .map(session -> new CalendarEventDTO(session, ZoneId.of(TEST_TIMEZONE))).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldNotIncludeEventsRepresentingCancelledTutorialGroupSessionsForTutor() throws Exception {
            setupActiveCourseWithCancelledTutorialGroupSessionsScenario();

            String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
            String URL = assembleURLForGetRequest(monthKeys);
            Map<String, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

            List<CalendarEventDTO> expectedTutorialEvents = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                    .map(session -> new CalendarEventDTO(session, ZoneId.of(TEST_TIMEZONE))).toList();
            List<CalendarEventDTO> expectedCourseEvents = courseCalendarEvents.stream().map(event -> new CalendarEventDTO(event, ZoneId.of(TEST_TIMEZONE))).toList();
            Map<String, List<CalendarEventDTO>> expected = Stream.concat(expectedTutorialEvents.stream(), expectedCourseEvents.stream())
                    .collect(Collectors.groupingBy(dto -> dto.startDate().toLocalDate().toString()));

            assertThat(actual).usingRecursiveComparison().withComparatorForType( // necessary because Jackson deserializes into UTC which can not be dynamically changed to
                                                                                 // TEST_TIMEZONE
                    (a, b) -> ((ZonedDateTime) a).toInstant().compareTo(((ZonedDateTime) b).toInstant()), ZonedDateTime.class).ignoringCollectionOrder().isEqualTo(expected);
        }

        // TODO: write a test that verifies correct format of response DTO-timestamps
    }

    @Nested
    class CreateCourseCalendarEvents {

        @Test
        @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
        void shouldReturnBadRequestForStudent() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId(), false);

            CalendarEventDTO body = new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1",
                    "Dr. Test");
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
        void shouldReturnBadRequestForTutor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId(), false);

            CalendarEventDTO body = new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1",
                    "Dr. Test");
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.FORBIDDEN);
        }

        // should throw if editor but not editor of course
        @Test
        @WithMockUser(username = NOT_EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnBadRequestForEditorNotPartOfCourse() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId(), false);

            CalendarEventDTO body = new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1",
                    "Dr. Test");
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.FORBIDDEN);
        }

        // should throw if instructor but not instructor of course
        @Test
        @WithMockUser(username = NOT_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldReturnBadRequestForInstructorNotPartOfCourse() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();

            String URL = assembleURLForPostRequest(course.getId(), false);

            CalendarEventDTO body = new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1",
                    "Dr. Test");
            request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.FORBIDDEN);
        }

        // should work fine for editor
        @Test
        @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
        void shouldReturnBadRequestForEditor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();
            System.out.println("Editors groups:" + editor.getGroups());
            System.out.println("Editors authorities:" + editor.getAuthorities());
            System.out.println("Course editor group:" + course.getEditorGroupName());

            String URL = assembleURLForPostRequest(course.getId(), false);

            CalendarEventDTO body = new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1",
                    "Dr. Test");
            CalendarEventDTO actual = request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.OK);
        }

        // should work fine for instructor
        @Test
        @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
        void shouldCreateEventForInstructor() throws Exception {
            setupActiveCourseWithoutCourseWideEventsScenario();
            System.out.println("Instructor groups:" + instructor.getGroups());
            System.out.println("Instructor authorities:" + instructor.getAuthorities());
            System.out.println("Course editor group:" + course.getInstructorGroupName());

            String URL = assembleURLForPostRequest(course.getId(), false);

            CalendarEventDTO body = new CalendarEventDTO(null, "Test Event", null, course.getStartDate().plusDays(2), course.getStartDate().plusDays(2).plusHours(2), "Room A1",
                    "Dr. Test");
            CalendarEventDTO actual = request.postWithResponseBody(URL, body, CalendarEventDTO.class, HttpStatus.OK);
        }

        // should throw if id present

        // should throw if no title present

        // should overwrite course name with name of course related to courseId

        // should throw if no startDate present

        // should accept no endDate, no location, no facilitator
    }

    @Nested
    class UpdateCourseCalendarEvents {

    }

    @Nested
    class DeleteCourseCalendarEvents {

    }
}
