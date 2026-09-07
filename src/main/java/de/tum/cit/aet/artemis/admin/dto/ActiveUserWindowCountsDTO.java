package de.tum.cit.aet.artemis.admin.dto;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated active user counts for several rolling windows.
 *
 * @param activeUsers1Day   users active within the last 1 day
 * @param activeUsers7Days  users active within the last 7 days
 * @param activeUsers14Days users active within the last 14 days
 * @param activeUsers30Days users active within the last 30 days
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ActiveUserWindowCountsDTO(long activeUsers1Day, long activeUsers7Days, long activeUsers14Days, long activeUsers30Days) {

    /**
     * Buckets the last submission date of every active user into the rolling 1/7/14/30 day windows.
     * <p>
     * The windows are deliberately derived in Java: letting the database count them with four
     * {@code COUNT(DISTINCT CASE WHEN …)} expressions over a {@code submission → participation → jhi_user} join made
     * that statement the most expensive one in production, because the optimizer stopped driving from the
     * {@code submission_date} range. A user counts for a window if their most recent submission is not older than the
     * window, matching the {@code submission_date >= now - n days} predicates that were replaced.
     *
     * @param lastSubmissions the most recent submission per user inside the widest (30 day) window
     * @param testUserIds     the ids of all test users, which are never counted as active users
     * @param now             the reference point the windows are measured back from
     * @return the number of distinct active users per window
     */
    public static ActiveUserWindowCountsDTO of(Collection<ActiveUserLastSubmissionDTO> lastSubmissions, Set<Long> testUserIds, ZonedDateTime now) {
        long activeUsers1Day = 0;
        long activeUsers7Days = 0;
        long activeUsers14Days = 0;
        long activeUsers30Days = 0;
        final ZonedDateTime oneDayAgo = now.minusDays(1);
        final ZonedDateTime sevenDaysAgo = now.minusDays(7);
        final ZonedDateTime fourteenDaysAgo = now.minusDays(14);
        final ZonedDateTime thirtyDaysAgo = now.minusDays(30);

        for (var lastSubmission : lastSubmissions) {
            if (testUserIds.contains(lastSubmission.userId())) {
                continue;
            }
            final ZonedDateTime lastSubmissionDate = lastSubmission.lastSubmissionDate();
            if (lastSubmissionDate == null || lastSubmissionDate.isBefore(thirtyDaysAgo)) {
                continue;
            }
            activeUsers30Days++;
            if (!lastSubmissionDate.isBefore(fourteenDaysAgo)) {
                activeUsers14Days++;
            }
            if (!lastSubmissionDate.isBefore(sevenDaysAgo)) {
                activeUsers7Days++;
            }
            if (!lastSubmissionDate.isBefore(oneDayAgo)) {
                activeUsers1Day++;
            }
        }

        return new ActiveUserWindowCountsDTO(activeUsers1Day, activeUsers7Days, activeUsers14Days, activeUsers30Days);
    }
}
