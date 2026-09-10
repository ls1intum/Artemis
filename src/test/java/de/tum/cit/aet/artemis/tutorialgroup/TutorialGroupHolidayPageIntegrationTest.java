package de.tum.cit.aet.artemis.tutorialgroup;

import static de.tum.cit.aet.artemis.tutorialgroup.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import de.tum.cit.aet.artemis.account.util.UserFactory;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupFreePeriod;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupFreePeriodSessionCountDTO;
import de.tum.cit.aet.artemis.tutorialgroup.dto.TutorialGroupSessionCountDTO;

/**
 * Covers the endpoint the holidays page added: the per-day session counts it labels the calendar and the holiday list
 * with.
 */
class TutorialGroupHolidayPageIntegrationTest extends AbstractTutorialGroupIntegrationTest {

    private static final String TEST_PREFIX = "tutorialgroupholidaypage";

    /** A Monday, so the sessions below land on weekdays that tutorial groups plausibly use. */
    private static final LocalDate MONDAY = LocalDate.of(2022, 9, 12);

    private TutorialGroup exampleTutorialGroup;

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    @Override
    void setupTestScenario() {
        super.setupTestScenario();
        userUtilService.addStudentToCourse(testPrefix + "student1", exampleCourse);
        userUtilService.addTeachingAssistantToCourse(testPrefix + "tutor1", exampleCourse);
        userUtilService.addEditorToCourse(testPrefix + "editor1", exampleCourse);
        userUtilService.addInstructorToCourse(testPrefix + "instructor1", exampleCourse);
        if (userRepository.findOneByLogin(testPrefix + "instructor42").isEmpty()) {
            userRepository.save(UserFactory.generateActivatedUser(testPrefix + "instructor42"));
        }
        exampleTutorialGroup = tutorialGroupUtilService.createTutorialGroup(exampleCourseId, generateRandomTitle(), "LoremIpsum1", 10, false, "LoremIpsum1",
                Language.ENGLISH.name(), userRepository.findOneByLogin(testPrefix + "tutor1").orElseThrow(), Set.of());
    }

    private String sessionCountsPath() {
        return "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-free-periods/session-counts";
    }

    private MultiValueMap<String, String> span(LocalDate from, LocalDate to) {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("from", from.toString());
        parameters.add("to", to.toString());
        return parameters;
    }

    /** A session already cancelled by a holiday, which is the state editing that holiday has to account for. */
    private void cancelSessionOn(LocalDate day, int hour, TutorialGroupFreePeriod cancelledBy) {
        ZoneId zone = ZoneId.of(exampleTimeZone);
        var session = tutorialGroupUtilService.createTutorialGroupSession(ZonedDateTime.of(day.atTime(hour, 0), zone), ZonedDateTime.of(day.atTime(hour + 1, 0), zone), "01.05.13",
                null, TutorialGroupSessionStatus.CANCELLED, null, exampleTutorialGroup);
        session.setTutorialGroupFreePeriod(cancelledBy);
        tutorialGroupSessionRepository.saveAndFlush(session);
    }

    private void createSessionOn(LocalDate day, int hour) {
        ZoneId zone = ZoneId.of(exampleTimeZone);
        tutorialGroupSessionRepository.saveAndFlush(tutorialGroupUtilService.createTutorialGroupSession(ZonedDateTime.of(day.atTime(hour, 0), zone),
                ZonedDateTime.of(day.atTime(hour + 2, 0), zone), "01.05.13", null, TutorialGroupSessionStatus.ACTIVE, null, exampleTutorialGroup));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_shouldCountSessionsPerDay() throws Exception {
        createSessionOn(MONDAY, 10);
        createSessionOn(MONDAY, 14);
        createSessionOn(MONDAY.plusDays(1), 10);

        List<TutorialGroupSessionCountDTO> counts = request.getList(sessionCountsPath(), HttpStatus.OK, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));

        assertThat(counts).contains(new TutorialGroupSessionCountDTO(MONDAY, 2), new TutorialGroupSessionCountDTO(MONDAY.plusDays(1), 1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_shouldOmitDaysWithoutSessions() throws Exception {
        createSessionOn(MONDAY, 10);

        List<TutorialGroupSessionCountDTO> counts = request.getList(sessionCountsPath(), HttpStatus.OK, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));

        // A week with one session answers with one entry rather than seven, six of which would be zero.
        assertThat(counts).extracting(TutorialGroupSessionCountDTO::date).containsExactly(MONDAY);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_shouldIncludeTheLastDayOfTheSpan() throws Exception {
        LocalDate lastDay = MONDAY.plusDays(6);
        createSessionOn(lastDay, 10);

        List<TutorialGroupSessionCountDTO> counts = request.getList(sessionCountsPath(), HttpStatus.OK, TutorialGroupSessionCountDTO.class, span(MONDAY, lastDay));

        assertThat(counts).extracting(TutorialGroupSessionCountDTO::date).contains(lastDay);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_shouldExcludeSessionsOutsideTheSpan() throws Exception {
        createSessionOn(MONDAY.minusDays(1), 10);
        createSessionOn(MONDAY.plusDays(7), 10);

        List<TutorialGroupSessionCountDTO> counts = request.getList(sessionCountsPath(), HttpStatus.OK, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));

        assertThat(counts).isEmpty();
    }

    /**
     * The point of the endpoint: a day is the course's day, not the server's.
     * <p>
     * {@code exampleTimeZone} is ahead of UTC, so a session in the small hours belongs to the previous day in UTC. It
     * has to be counted on the day the instructor sees it on, because that is the day a holiday would cancel it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_shouldGroupDaysInTheTimeZoneOfTheCourse() throws Exception {
        LocalDate day = MONDAY.plusDays(1);
        createSessionOn(day, 1);
        // Guards the premise: with a zone at or behind UTC the session would fall on the same day either way.
        assertThat(ZonedDateTime.of(day.atTime(1, 0), ZoneId.of(exampleTimeZone)).withZoneSameInstant(ZoneOffset.UTC).toLocalDate()).isEqualTo(day.minusDays(1));

        List<TutorialGroupSessionCountDTO> counts = request.getList(sessionCountsPath(), HttpStatus.OK, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));

        assertThat(counts).containsExactly(new TutorialGroupSessionCountDTO(day, 1));
    }

    /** The span's own ends are read in the same zone, so a session early on its first day is inside it. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_shouldIncludeASessionEarlyOnTheFirstDayOfTheSpan() throws Exception {
        createSessionOn(MONDAY, 1);

        List<TutorialGroupSessionCountDTO> counts = request.getList(sessionCountsPath(), HttpStatus.OK, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));

        assertThat(counts).containsExactly(new TutorialGroupSessionCountDTO(MONDAY, 1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_withInvertedSpan_shouldReturnBadRequest() throws Exception {
        request.getList(sessionCountsPath(), HttpStatus.BAD_REQUEST, TutorialGroupSessionCountDTO.class, span(MONDAY.plusDays(6), MONDAY));
    }

    /** A year is past anything the page asks for, so the bound has to leave the largest real request alone. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_withTheLongestAllowedSpan_shouldSucceed() throws Exception {
        createSessionOn(MONDAY, 10);

        List<TutorialGroupSessionCountDTO> counts = request.getList(sessionCountsPath(), HttpStatus.OK, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(366)));

        assertThat(counts).containsExactly(new TutorialGroupSessionCountDTO(MONDAY, 1));
    }

    /** The dates come straight from the request, and the count loads a row per session in the span. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_withAnOverlongSpan_shouldReturnBadRequest() throws Exception {
        request.getList(sessionCountsPath(), HttpStatus.BAD_REQUEST, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(367)));
    }

    /** Answering 400 rather than 500: LocalDate.MAX would otherwise overflow the exclusive end the count runs to. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_withAnExtremeEndDate_shouldReturnBadRequest() throws Exception {
        request.getList(sessionCountsPath(), HttpStatus.BAD_REQUEST, TutorialGroupSessionCountDTO.class, span(MONDAY, LocalDate.MAX));
    }

    /** A single day at the very end of the range is short enough to pass the length check, and still has no bound. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCounts_forTheSingleLastRepresentableDay_shouldReturnBadRequest() throws Exception {
        request.getList(sessionCountsPath(), HttpStatus.BAD_REQUEST, TutorialGroupSessionCountDTO.class, span(LocalDate.MAX, LocalDate.MAX));
    }

    private String overlapCountPath() {
        return "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-free-periods/overlapping-session-count";
    }

    private String countsPerPeriodPath() {
        return "/api/tutorialgroup/courses/" + exampleCourseId + "/tutorial-free-periods/session-counts-per-period";
    }

    private MultiValueMap<String, String> span(LocalDateTime from, LocalDateTime to) {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.add("from", from.toString());
        parameters.add("to", to.toString());
        return parameters;
    }

    /** The reason the per-day counts cannot answer this: a morning holiday must not be credited with the afternoon. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOverlappingSessionCount_shouldCountOnlyTheSessionsTheSpanCovers() throws Exception {
        // createSessionOn runs two hours, so these are 06:00-08:00, 09:00-11:00 and 14:00-16:00.
        createSessionOn(MONDAY, 6);
        createSessionOn(MONDAY, 9);
        createSessionOn(MONDAY, 14);

        Long morning = request.get(overlapCountPath(), HttpStatus.OK, Long.class, span(MONDAY.atTime(9, 0), MONDAY.atTime(10, 0)));
        Long wholeDay = request.get(overlapCountPath(), HttpStatus.OK, Long.class, span(MONDAY.atTime(0, 0), MONDAY.atTime(23, 59)));

        // Only the session running inside the span: the one before it has finished, the one after has not begun.
        assertThat(morning).isOne();
        assertThat(wholeDay).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOverlappingSessionCount_shouldReadTheSpanInTheTimeZoneOfTheCourse() throws Exception {
        createSessionOn(MONDAY.plusDays(1), 1);

        Long count = request.get(overlapCountPath(), HttpStatus.OK, Long.class, span(MONDAY.plusDays(1).atTime(0, 0), MONDAY.plusDays(1).atTime(2, 0)));

        assertThat(count).isOne();
    }

    /** Cancelling only takes active sessions, so counting one another holiday already took would promise too much. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOverlappingSessionCount_shouldLeaveOutSessionsThatAreAlreadyCancelled() throws Exception {
        createSessionOn(MONDAY, 9);
        var otherHoliday = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, MONDAY.atTime(9, 30), MONDAY.atTime(10, 30), "Another");
        cancelSessionOn(MONDAY, 10, otherHoliday);

        Long count = request.get(overlapCountPath(), HttpStatus.OK, Long.class, span(MONDAY.atTime(9, 0), MONDAY.atTime(12, 0)));

        // Only the active one: the cancelled session is already gone and saving would not take it again.
        assertThat(count).isOne();
    }

    /** Editing releases the sessions a holiday had taken before it takes them again, so its own still count. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOverlappingSessionCount_shouldCountTheSessionsTheEditedHolidayItselfCancelled() throws Exception {
        var holiday = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, MONDAY.atTime(0, 0), MONDAY.atTime(23, 59), "Whole day");
        cancelSessionOn(MONDAY, 9, holiday);

        var withoutTheHoliday = span(MONDAY.atTime(0, 0), MONDAY.atTime(23, 59));
        var whileEditingIt = span(MONDAY.atTime(0, 0), MONDAY.atTime(23, 59));
        whileEditingIt.add("editedFreePeriodId", holiday.getId().toString());

        // The session is cancelled already, so another holiday over it would cancel nothing more...
        assertThat(request.get(overlapCountPath(), HttpStatus.OK, Long.class, withoutTheHoliday)).isZero();
        // ...but reopening the one holding it has to report it, rather than claiming the holiday affects nothing.
        assertThat(request.get(overlapCountPath(), HttpStatus.OK, Long.class, whileEditingIt)).isOne();
    }

    /**
     * Navigating a nullable association in a query can force an inner join, which would drop every session that no
     * holiday has cancelled - that is, all the active ones the count exists to find.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOverlappingSessionCount_whileEditing_shouldStillCountActiveSessionsThatNoHolidayHasTouched() throws Exception {
        createSessionOn(MONDAY, 9);
        var holiday = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, MONDAY.atTime(0, 0), MONDAY.atTime(23, 59), "Whole day");

        var whileEditing = span(MONDAY.atTime(0, 0), MONDAY.atTime(23, 59));
        whileEditing.add("editedFreePeriodId", holiday.getId().toString());

        assertThat(request.get(overlapCountPath(), HttpStatus.OK, Long.class, whileEditing)).isOne();
    }

    /**
     * A wall clock inside a daylight saving gap moves forward when it is read in a zone, which can put a start after an
     * end that looked earlier. Bucharest loses 03:00 to 04:00 on the last Sunday of March.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOverlappingSessionCount_withASpanInvertedByADaylightSavingGap_shouldReturnBadRequest() throws Exception {
        LocalDate springForward = LocalDate.of(2025, 3, 30);

        request.get(overlapCountPath(), HttpStatus.BAD_REQUEST, Long.class, span(springForward.atTime(3, 30), springForward.atTime(4, 0)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getOverlappingSessionCount_withAnEndBeforeItsStart_shouldReturnBadRequest() throws Exception {
        request.get(overlapCountPath(), HttpStatus.BAD_REQUEST, Long.class, span(MONDAY.atTime(10, 0), MONDAY.atTime(9, 0)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getOverlappingSessionCount_asStudent_shouldReturnForbidden() throws Exception {
        request.get(overlapCountPath(), HttpStatus.FORBIDDEN, Long.class, span(MONDAY.atTime(9, 0), MONDAY.atTime(10, 0)));
    }

    /** The list beside the calendar shows one number per holiday, so it has to say what that holiday did. */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCountsPerFreePeriod_shouldCountWhatEachHolidayCancelledAndKeepEmptyPeriods() throws Exception {
        var morningOnly = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, MONDAY.atTime(9, 0), MONDAY.atTime(11, 0), "Morning");
        var quietDay = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, MONDAY.plusDays(3).atTime(0, 0), MONDAY.plusDays(3).atTime(23, 59), "Quiet");
        cancelSessionOn(MONDAY, 9, morningOnly);

        List<TutorialGroupFreePeriodSessionCountDTO> counts = request.getList(countsPerPeriodPath(), HttpStatus.OK, TutorialGroupFreePeriodSessionCountDTO.class);

        // Exactly these two, so a holiday holding nothing still answers - with zero - rather than dropping out and
        // leaving its row in the list without a number.
        assertThat(counts).containsExactlyInAnyOrder(new TutorialGroupFreePeriodSessionCountDTO(morningOnly.getId(), 1),
                new TutorialGroupFreePeriodSessionCountDTO(quietDay.getId(), 0));
    }

    /**
     * A session someone cancelled by hand overlaps the holiday but was never taken by it, because cancelling only takes
     * sessions that are still active. Counting by overlap would report it as cancelled by a holiday that never saw it,
     * and would contradict the zero the dialog gave for the same span.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getSessionCountsPerFreePeriod_shouldNotClaimASessionCancelledByHand() throws Exception {
        var holiday = tutorialGroupUtilService.addTutorialGroupFreePeriod(exampleConfigurationId, MONDAY.atTime(9, 0), MONDAY.atTime(11, 0), "Morning");
        tutorialGroupSessionRepository.saveAndFlush(tutorialGroupUtilService.createTutorialGroupSession(ZonedDateTime.of(MONDAY.atTime(9, 30), ZoneId.of(exampleTimeZone)),
                ZonedDateTime.of(MONDAY.atTime(10, 30), ZoneId.of(exampleTimeZone)), "01.05.13", null, TutorialGroupSessionStatus.CANCELLED, null, exampleTutorialGroup));

        List<TutorialGroupFreePeriodSessionCountDTO> counts = request.getList(countsPerPeriodPath(), HttpStatus.OK, TutorialGroupFreePeriodSessionCountDTO.class);

        assertThat(counts).containsExactly(new TutorialGroupFreePeriodSessionCountDTO(holiday.getId(), 0));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionCountsPerFreePeriod_asStudent_shouldReturnForbidden() throws Exception {
        request.getList(countsPerPeriodPath(), HttpStatus.FORBIDDEN, TutorialGroupFreePeriodSessionCountDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getSessionCounts_asTutor_shouldReturnForbidden() throws Exception {
        request.getList(sessionCountsPath(), HttpStatus.FORBIDDEN, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionCounts_asStudent_shouldReturnForbidden() throws Exception {
        request.getList(sessionCountsPath(), HttpStatus.FORBIDDEN, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void getSessionCounts_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.getList(sessionCountsPath(), HttpStatus.FORBIDDEN, TutorialGroupSessionCountDTO.class, span(MONDAY, MONDAY.plusDays(6)));
    }

}
