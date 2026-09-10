package de.tum.cit.aet.artemis.tutorialgroup;

import static de.tum.cit.aet.artemis.tutorialgroup.AbstractTutorialGroupIntegrationTest.RandomTutorialGroupGenerator.generateRandomTitle;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupSessionStatus;
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
