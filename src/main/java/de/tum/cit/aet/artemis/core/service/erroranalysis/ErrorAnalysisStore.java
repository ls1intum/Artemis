package de.tum.cit.aet.artemis.core.service.erroranalysis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;

/**
 * Counts the errors of the server and of the clients, grouped by what they have in common.
 * <p>
 * Nothing is stored in the database, so the analysis cannot fill it. The counts live in memory instead, and the memory is
 * bounded: at most {@link #MAX_GROUPS} groups, each with one counter per hour of the last {@link #RETENTION}. A group that
 * has not been seen for the longest time makes room for a new one. The consequences are part of the design: the counts
 * start again when the server restarts, and every node counts only what it saw itself.
 * <p>
 * Only warnings and more serious errors are counted, a hint is not an error. Two errors are in one group if they have the
 * same location and severity and the same message once the numbers, identifiers and email addresses are replaced by
 * placeholders, so that {@code Could not find exercise 17} and {@code Could not find exercise 42} count together.
 * <p>
 * The server logs through a Logback appender, which Logback creates outside of Spring. The appender and the Spring beans
 * therefore reach the one store through {@link #shared()}.
 */
public final class ErrorAnalysisStore {

    /** The most groups that are kept. */
    static final int MAX_GROUPS = 1_000;

    /** How long an error is counted: the longest time span of the analysis. */
    static final Duration RETENTION = ErrorAnalysisSpan.MONTH.getDuration();

    /** The most characters of a message that are kept, which is also what the groups are compared by. */
    static final int MAX_MESSAGE_LENGTH = 300;

    private static final long SECONDS_PER_HOUR = Duration.ofHours(1).toSeconds();

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern UUID = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final ErrorAnalysisStore SHARED = new ErrorAnalysisStore(Clock.systemUTC());

    private final Clock clock;

    private final Map<String, Group> groups = new HashMap<>();

    ErrorAnalysisStore(Clock clock) {
        this.clock = clock;
    }

    /**
     * The store of the application.
     *
     * @return the one store that the appender of the log and the Spring beans share
     */
    public static ErrorAnalysisStore shared() {
        return SHARED;
    }

    /**
     * Counts an error. An error that is less serious than a warning is ignored.
     *
     * @param location where the error happened
     * @param severity how serious the error is
     * @param message  what went wrong, the numbers and identifiers in it do not tell groups apart
     */
    public synchronized void record(ErrorLocation location, ErrorSeverity severity, String message) {
        if (!severity.isAtLeast(ErrorSeverity.WARN)) {
            return;
        }
        Instant now = clock.instant();
        String pattern = toPattern(message);
        Group group = groups.computeIfAbsent(location + "|" + severity + "|" + pattern, key -> new Group(location, severity, pattern, now));
        group.add(now);
        if (groups.size() > MAX_GROUPS) {
            evictLeastRecentlySeen();
        }
    }

    /**
     * The groups of the errors that happened in a time span.
     *
     * @param span        how far to look back from now
     * @param location    only the errors of this location, or {@code null} for all of them
     * @param minSeverity only the errors that are at least this serious
     * @return the groups, the most frequent first
     */
    public synchronized List<ErrorGroupDTO> query(ErrorAnalysisSpan span, ErrorLocation location, ErrorSeverity minSeverity) {
        Instant since = clock.instant().minus(span.getDuration());
        List<ErrorGroupDTO> result = new ArrayList<>();
        for (Group group : groups.values()) {
            if ((location == null || group.location == location) && group.severity.isAtLeast(minSeverity) && !group.lastSeen.isBefore(since)) {
                long count = group.countSince(since);
                if (count > 0) {
                    result.add(new ErrorGroupDTO(group.location, group.severity, group.pattern, count, group.firstSeen, group.lastSeen));
                }
            }
        }
        result.sort(Comparator.comparingLong(ErrorGroupDTO::count).reversed().thenComparing(ErrorGroupDTO::lastSeen, Comparator.reverseOrder()));
        return result;
    }

    /**
     * Forgets everything that was counted.
     */
    synchronized void clear() {
        groups.clear();
    }

    private void evictLeastRecentlySeen() {
        groups.values().stream().min(Comparator.comparing(group -> group.lastSeen)).ifPresent(oldest -> groups.values().remove(oldest));
    }

    /**
     * Replaces what tells two otherwise identical errors apart by placeholders, and shortens the message.
     */
    static String toPattern(String message) {
        String pattern = EMAIL.matcher(message == null ? "" : message).replaceAll("<email>");
        pattern = UUID.matcher(pattern).replaceAll("<uuid>");
        pattern = NUMBER.matcher(pattern).replaceAll("<n>");
        pattern = WHITESPACE.matcher(pattern).replaceAll(" ").strip();
        return pattern.length() <= MAX_MESSAGE_LENGTH ? pattern : pattern.substring(0, MAX_MESSAGE_LENGTH);
    }

    /**
     * The errors of one group, counted per hour.
     */
    private static final class Group {

        private final ErrorLocation location;

        private final ErrorSeverity severity;

        private final String pattern;

        private final Instant firstSeen;

        private Instant lastSeen;

        /** The number of errors per hour, by the hours since the epoch. */
        private final TreeMap<Long, Long> perHour = new TreeMap<>();

        private Group(ErrorLocation location, ErrorSeverity severity, String pattern, Instant now) {
            this.location = location;
            this.severity = severity;
            this.pattern = pattern;
            this.firstSeen = now;
            this.lastSeen = now;
        }

        private void add(Instant now) {
            lastSeen = now;
            perHour.merge(now.getEpochSecond() / SECONDS_PER_HOUR, 1L, Long::sum);
            // forget what is older than anybody can ask for
            perHour.headMap((now.minus(RETENTION).getEpochSecond() / SECONDS_PER_HOUR)).clear();
        }

        /** The hour that contains {@code since} counts completely, so the count can be a little too high but never too low. */
        private long countSince(Instant since) {
            return perHour.tailMap(since.getEpochSecond() / SECONDS_PER_HOUR, true).values().stream().mapToLong(Long::longValue).sum();
        }
    }
}
