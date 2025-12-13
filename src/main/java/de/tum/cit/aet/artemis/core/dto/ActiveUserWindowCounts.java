package de.tum.cit.aet.artemis.core.dto;

/**
 * Aggregated active user counts for several rolling windows.
 *
 * @param activeUsers1Day   users active within the last 1 day
 * @param activeUsers7Days  users active within the last 7 days
 * @param activeUsers14Days users active within the last 14 days
 * @param activeUsers30Days users active within the last 30 days
 */
public record ActiveUserWindowCounts(long activeUsers1Day, long activeUsers7Days, long activeUsers14Days, long activeUsers30Days) {
}
