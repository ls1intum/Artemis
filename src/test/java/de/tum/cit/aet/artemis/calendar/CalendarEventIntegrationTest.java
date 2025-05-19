package de.tum.cit.aet.artemis.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.calendar.dto.CalendarEventDTO;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;

class CalendarEventIntegrationTest extends AbstractCalendarIntegrationTest {

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnBadRequestForStudentWhenMonthsHaveWrongFormat() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act & Assert
        request.get("/api/calendar/calendar-events?monthKeys=11-2025&timeZone=" + timeZone, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnBadRequestForTutorWhenMonthsHaveWrongFormat() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act & Assert
        request.get("/api/calendar/calendar-events?monthKeys=11-2025&timeZone=" + timeZone, HttpStatus.BAD_REQUEST, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "notstudent1", roles = "USER")
    void shouldReturnEmptyMapForStudentWhenUserNotPartOfAnyCourse() throws Exception {
        // Arrange
        setupUserNotPartOfAnyCourseScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK, Map.class);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "nottutor1", roles = "USER")
    void shouldReturnEmptyMapForTutorWhenUserNotPartOfAnyCourse() throws Exception {
        // Arrange
        setupUserNotPartOfAnyCourseScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK, Map.class);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyMapForStudentWhenUserOnlyPartOfNonActiveCourse() throws Exception {
        // Arrange
        setupOnlyNonActiveCourseScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK, Map.class);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnEmptyMapForTutorWhenUserOnlyPartOfNonActiveCourse() throws Exception {
        // Arrange
        setupOnlyNonActiveCourseScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK, Map.class);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyMapForStudentWhenNotPartOfAnyTutorialGroup() throws Exception {
        // Arrange
        setupActiveCourseWithoutParticipatedTutorialGroupScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK, Map.class);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnEmptyMapForTutorWhenNotPartOfAnyTutorialGroup() throws Exception {
        // Arrange
        setupActiveCourseWithoutParticipatedTutorialGroupScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK, Map.class);

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnEmptyMapForStudentWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String monthKeys = YearMonth.now().minusMonths(3).toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnEmptyMapForTutorWhenQueriedForMonthWithoutOverlappingEvents() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String monthKeys = YearMonth.now().minusMonths(3).toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldIncludeOnlyNonCancelledSessionsForStudent() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveAndCancelledSessionsScenario();
        String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE).map(CalendarEventDTO::new)
                .collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldIncludeOnlyNonCancelledSessionsForTutor() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveAndCancelledSessionsScenario();
        String monthKeys = getMonthsSpanningCurrentTestCourseAsMonthKeys();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().filter(session -> session.getStatus() == TutorialGroupSessionStatus.ACTIVE).map(CalendarEventDTO::new)
                .collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCorrectMapForStudentWhenQueriedForOneMonth() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String monthKeys = YearMonth.now().minusMonths(1).toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().limit(4).map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnCorrectMapForTutorWhenQueriedForOneMonth() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String monthKeys = YearMonth.now().minusMonths(1).toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().limit(4).map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCorrectMapForStudentWhenQueriedForMultipleMonths() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().limit(8).map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnCorrectMapForTutorWhenQueriedForMultipleMonths() throws Exception {
        // Arrange
        setupActiveCourseWithParticipatedGroupAndActiveSessionsScenario();
        String monthKeys = Stream.of(YearMonth.now().minusMonths(1), YearMonth.now()).map(YearMonth::toString).collect(Collectors.joining(","));
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().limit(8).map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldReturnCorrectMapForStudentWhenQueriedMonthIsPartiallyOverlappedBySessions() throws Exception {
        // Arrange
        setupPartiallyOverlappingScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "USER")
    void shouldReturnCorrectMapForTutorWhenQueriedMonthIsPartiallyOverlappedBySessions() throws Exception {
        // Arrange
        setupPartiallyOverlappingScenario();
        String monthKeys = YearMonth.now().toString();
        String timeZone = URLEncoder.encode(ZoneId.systemDefault().getId(), StandardCharsets.UTF_8);
        var expected = tutorialGroupSessions.stream().map(CalendarEventDTO::new).collect(Collectors.groupingBy(dto -> dto.start().truncatedTo(ChronoUnit.DAYS)));

        // Act
        var actual = request.get("/api/calendar/calendar-events?monthKeys=" + monthKeys + "&timeZone=" + timeZone, HttpStatus.OK,
                new TypeReference<Map<ZonedDateTime, List<CalendarEventDTO>>>() {
                });

        // Assert
        assertThat(actual).isEqualTo(expected);
    }
}
