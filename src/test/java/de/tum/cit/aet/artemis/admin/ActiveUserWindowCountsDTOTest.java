package de.tum.cit.aet.artemis.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.admin.dto.ActiveUserLastSubmissionDTO;
import de.tum.cit.aet.artemis.admin.dto.ActiveUserWindowCountsDTO;

/**
 * Tests the bucketing of active users into the rolling 1/7/14/30 day windows.
 * <p>
 * The windows used to be counted by the database with four {@code COUNT(DISTINCT CASE WHEN …)} expressions over a
 * {@code submission → participation → jhi_user} join. That aggregation is what made the query the single most
 * expensive statement in production, so the database now only reports the last submission date per active user and
 * the bucketing happens here.
 */
class ActiveUserWindowCountsDTOTest {

    private static final ZonedDateTime NOW = ZonedDateTime.parse("2026-08-17T12:00:00Z");

    @Test
    void shouldCountAUserInEveryWindowContainingItsLastSubmission() {
        var lastSubmissions = List.of(new ActiveUserLastSubmissionDTO(1L, NOW.minusDays(10)));

        var counts = ActiveUserWindowCountsDTO.of(lastSubmissions, Set.of(), NOW);

        assertThat(counts).isEqualTo(new ActiveUserWindowCountsDTO(0, 0, 1, 1));
    }

    @Test
    void shouldCountEveryActiveUserExactlyOncePerWindow() {
        var lastSubmissions = List.of(new ActiveUserLastSubmissionDTO(1L, NOW.minusHours(2)), new ActiveUserLastSubmissionDTO(2L, NOW.minusHours(3)));

        var counts = ActiveUserWindowCountsDTO.of(lastSubmissions, Set.of(), NOW);

        assertThat(counts).isEqualTo(new ActiveUserWindowCountsDTO(2, 2, 2, 2));
    }

    @Test
    void shouldExcludeTestUsers() {
        var lastSubmissions = List.of(new ActiveUserLastSubmissionDTO(1L, NOW.minusHours(2)), new ActiveUserLastSubmissionDTO(2L, NOW.minusHours(2)));

        var counts = ActiveUserWindowCountsDTO.of(lastSubmissions, Set.of(2L), NOW);

        assertThat(counts).isEqualTo(new ActiveUserWindowCountsDTO(1, 1, 1, 1));
    }

    @Test
    void shouldCountASubmissionExactlyOnAWindowBoundaryAsInsideThatWindow() {
        // the replaced SQL used "submission_date >= now - 7 days", so a submission exactly 7 days old counted for the 7 day window
        var lastSubmissions = List.of(new ActiveUserLastSubmissionDTO(1L, NOW.minusDays(7)));

        var counts = ActiveUserWindowCountsDTO.of(lastSubmissions, Set.of(), NOW);

        assertThat(counts).isEqualTo(new ActiveUserWindowCountsDTO(0, 1, 1, 1));
    }

    @Test
    void shouldReportZeroForEveryWindowWithoutActiveUsers() {
        var counts = ActiveUserWindowCountsDTO.of(List.of(), Set.of(), NOW);

        assertThat(counts).isEqualTo(new ActiveUserWindowCountsDTO(0, 0, 0, 0));
    }
}
