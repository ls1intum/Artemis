package de.tum.cit.aet.artemis.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

class CalendarEventIntegrationTest extends AbstractCalendarIntegrationTest {

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnBadRequestForStudentWhenMonthsHaveWrongFormat() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act & Assert
        String monthKeys = "11-2025";
        String URL = assembleURL(monthKeys);
        request.get(URL, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldReturnBadRequestForTutorWhenMonthsHaveWrongFormat() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act & Assert
        String monthKeys = "11-2025";
        String URL = assembleURL(monthKeys);
        request.get(URL, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = NOT_STUDENT_LOGIN, roles = "USER")
    void shouldReturnEmptyMapForStudentWhenUserNotPartOfAnyCourse() throws Exception {
        // Arrange
        setupUserNotPartOfAnyCourseScenario();

        // Act
        String monthKeys = YearMonth.now().toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = NOT_TUTOR_LOGIN, roles = "TA")
    void shouldReturnEmptyMapForTutorWhenUserNotPartOfAnyCourse() throws Exception {
        // Arrange
        setupUserNotPartOfAnyCourseScenario();

        // Act
        String monthKeys = YearMonth.now().toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnEmptyMapForStudentWhenUserOnlyPartOfNonActiveCourse() throws Exception {
        // Arrange
        setupOnlyNonActiveCourseScenario();

        // Act
        String monthKeys = YearMonth.now().toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldReturnEmptyMapForTutorWhenUserOnlyPartOfNonActiveCourse() throws Exception {
        // Arrange
        setupOnlyNonActiveCourseScenario();

        // Act
        String monthKeys = YearMonth.now().toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnEmptyMapForStudentWhenNotPartOfAnyTutorialGroup() throws Exception {
        // Arrange
        setupActiveCourseWithoutParticipatedTutorialGroupScenario();

        // Act
        String monthKeys = YearMonth.now().toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldReturnEmptyMapForTutorWhenNotPartOfAnyTutorialGroup() throws Exception {
        // Arrange
        setupActiveCourseWithoutParticipatedTutorialGroupScenario();

        // Act
        String monthKeys = YearMonth.now().toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnEmptyMapForStudentWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act
        String monthKeys = YearMonth.now().minusMonths(3).toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldReturnEmptyMapForTutorWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act
        String monthKeys = YearMonth.now().minusMonths(3).toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldIncludeOnlyNonCancelledSessionsForStudent() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveAndCancelledSessionsScenario();

        // Act
        String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        Map<ZonedDateTime, List<CalendarEventDTO>> expected = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                .map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldIncludeOnlyNonCancelledSessionsForTutor() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveAndCancelledSessionsScenario();

        // Act
        String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        Map<ZonedDateTime, List<CalendarEventDTO>> expected = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE)
                .map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnCorrectMapForStudentWhenQueriedForOneMonth() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act
        String monthKeys = YearMonth.now().minusMonths(1).toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        Map<ZonedDateTime, List<CalendarEventDTO>> expected = tutorialGroupSessions.stream().limit(4).map(CalendarEventDTO::new)
                .collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldReturnCorrectMapForTutorWhenQueriedForOneMonth() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act
        String monthKeys = YearMonth.now().minusMonths(1).toString();
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        Map<ZonedDateTime, List<CalendarEventDTO>> expected = tutorialGroupSessions.stream().limit(4).map(CalendarEventDTO::new)
                .collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void shouldReturnCorrectMapForStudentWhenQueriedForMultipleMonths() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act
        String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        Map<ZonedDateTime, List<CalendarEventDTO>> expected = tutorialGroupSessions.stream().limit(8).map(CalendarEventDTO::new)
                .collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void shouldReturnCorrectMapForTutorWhenQueriedForMultipleMonths() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();

        // Act
        String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
        String URL = assembleURL(monthKeys);
        Map<ZonedDateTime, List<CalendarEventDTO>> actual = request.get(URL, HttpStatus.OK, GET_EVENTS_RETURN_TYPE);

        // Assert
        Map<ZonedDateTime, List<CalendarEventDTO>> expected = tutorialGroupSessions.stream().limit(8).map(CalendarEventDTO::new)
                .collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));
        assertThat(actual).isEqualTo(expected);
    }
}
