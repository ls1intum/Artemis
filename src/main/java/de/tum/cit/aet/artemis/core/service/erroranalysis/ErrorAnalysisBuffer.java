package de.tum.cit.aet.artemis.core.service.erroranalysis;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;

/**
 * Collects the errors that this node saw since it last handed them to the cluster, grouped by what they have in common.
 * <p>
 * The server logs through a Logback appender, which Logback creates outside of Spring, and a log call must never wait for the
 * cluster. The appender therefore only adds to this buffer, in memory, and {@link ErrorAnalysisService} writes what was
 * collected to the distributed data in batches. The buffer is bounded, so a node that nobody flushes, such as a build agent,
 * cannot grow it without limit. The appender and the Spring beans reach the one buffer through {@link #shared()}.
 * <p>
 * Only warnings and more serious errors are counted, a hint is not an error. Two errors are in one group if they have the same
 * location and severity and the same message once the numbers, identifiers and email addresses are replaced by placeholders,
 * so that {@code Could not find exercise 17} and {@code Could not find exercise 42} count together.
 */
public final class ErrorAnalysisBuffer {

    /** The most groups that are kept, on this node and in the cluster. */
    static final int MAX_GROUPS = 1_000;

    /** How long an error is counted: the longest time span of the analysis. */
    static final Duration RETENTION = Duration.ofDays(30);

    /** The most characters of a message that are kept, which is also what the groups are compared by. */
    static final int MAX_MESSAGE_LENGTH = 300;

    static final long MILLIS_PER_HOUR = Duration.ofHours(1).toMillis();

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private static final Pattern UUID = Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final ErrorAnalysisBuffer SHARED = new ErrorAnalysisBuffer(Clock.systemUTC());

    private final Clock clock;

    private Map<String, PendingGroup> pending = new HashMap<>();

    ErrorAnalysisBuffer(Clock clock) {
        this.clock = clock;
    }

    /**
     * The buffer of this node.
     *
     * @return the one buffer that the appender of the log and the Spring beans share
     */
    public static ErrorAnalysisBuffer shared() {
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
        long now = clock.millis();
        String pattern = toPattern(message);
        pending.computeIfAbsent(keyOf(location, severity, pattern), key -> new PendingGroup(location, severity, pattern, now)).add(now);
        if (pending.size() > MAX_GROUPS) {
            pending.values().stream().min(Comparator.comparingLong(group -> group.lastSeenMillis)).ifPresent(oldest -> pending.values().remove(oldest));
        }
    }

    /**
     * Takes everything that was collected and starts again with an empty buffer.
     *
     * @return the groups that were collected, by their key
     */
    synchronized Map<String, PendingGroup> drain() {
        Map<String, PendingGroup> drained = pending;
        pending = new HashMap<>();
        return drained;
    }

    /**
     * Gives back what could not be handed to the cluster, so that the next flush tries again.
     *
     * @param notWritten the groups, with only the hours that were not written
     */
    synchronized void restore(Map<String, PendingGroup> notWritten) {
        notWritten.forEach((key, group) -> pending.merge(key, group, PendingGroup::mergedWith));
    }

    /**
     * A copy of what was collected and not handed to the cluster yet, so that a query also sees the latest errors of this node.
     *
     * @return the groups that were collected, by their key
     */
    synchronized Map<String, PendingGroup> snapshot() {
        Map<String, PendingGroup> copy = new HashMap<>();
        pending.forEach((key, group) -> copy.put(key, group.copy()));
        return copy;
    }

    /**
     * Forgets everything that was collected.
     */
    synchronized void clear() {
        pending.clear();
    }

    static String keyOf(ErrorLocation location, ErrorSeverity severity, String pattern) {
        return location + "|" + severity + "|" + pattern;
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
     * The errors of one group that were collected, counted per hour.
     */
    static final class PendingGroup {

        final ErrorLocation location;

        final ErrorSeverity severity;

        final String pattern;

        long firstSeenMillis;

        long lastSeenMillis;

        /** The number of errors per hour, by the hours since the epoch. */
        final TreeMap<Long, Long> perHour = new TreeMap<>();

        PendingGroup(ErrorLocation location, ErrorSeverity severity, String pattern, long now) {
            this.location = location;
            this.severity = severity;
            this.pattern = pattern;
            this.firstSeenMillis = now;
            this.lastSeenMillis = now;
        }

        void add(long now) {
            lastSeenMillis = now;
            perHour.merge(now / MILLIS_PER_HOUR, 1L, Long::sum);
            // forget what is older than anybody can ask for, which only matters on a node that nobody flushes
            perHour.headMap((now - RETENTION.toMillis()) / MILLIS_PER_HOUR).clear();
        }

        PendingGroup copy() {
            PendingGroup copy = new PendingGroup(location, severity, pattern, firstSeenMillis);
            copy.lastSeenMillis = lastSeenMillis;
            copy.perHour.putAll(perHour);
            return copy;
        }

        PendingGroup mergedWith(PendingGroup other) {
            firstSeenMillis = Math.min(firstSeenMillis, other.firstSeenMillis);
            lastSeenMillis = Math.max(lastSeenMillis, other.lastSeenMillis);
            other.perHour.forEach((hour, count) -> perHour.merge(hour, count, Long::sum));
            return this;
        }

        ErrorGroupState toState() {
            return new ErrorGroupState(location, severity, pattern, firstSeenMillis, lastSeenMillis);
        }
    }
}
