package de.tum.cit.aet.artemis.core.service.erroranalysis;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToLongFunction;
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
     * @param version  the release that the error belongs to as the reporter states it, or {@code null} for the release of this node.
     *                     The service decides what it counts as, because only it knows the release that is running.
     * @param message  what went wrong, the numbers and identifiers in it do not tell groups apart
     */
    public void count(ErrorLocation location, ErrorSeverity severity, String version, String message) {
        if (!severity.isAtLeast(ErrorSeverity.WARN)) {
            return;
        }
        // the matching of the patterns can take a while for a long message, so it happens before the lock is taken: a log call must not wait for it
        String pattern = toPattern(message);
        add(location, severity, version, pattern);
    }

    private synchronized void add(ErrorLocation location, ErrorSeverity severity, String version, String pattern) {
        long now = clock.millis();
        pending.computeIfAbsent(keyOf(location, severity, version, pattern), key -> new PendingGroup(location, severity, version, pattern, now)).add(now);
        keepWithinLimit();
    }

    private void keepWithinLimit() {
        while (pending.size() > MAX_GROUPS) {
            Optional<PendingGroup> oldest = chooseGroupToForget(pending.values(), group -> group.location, group -> group.lastSeenMillis);
            if (oldest.isEmpty()) {
                return;
            }
            pending.values().remove(oldest.get());
        }
    }

    /**
     * Chooses the group to forget when there are more groups than allowed: the one seen longest ago among the groups that a client reported,
     * and only if there is none of them, the one seen longest ago of all. A client reports without signing in, so it must not be able to push
     * the errors of the server out by reporting many different errors.
     *
     * @param groups   the groups, including the one that was just added
     * @param location where the errors of a group happened
     * @param lastSeen when the last error of a group happened
     * @param <G>      the kind of group
     * @return the group to forget
     */
    static <G> Optional<G> chooseGroupToForget(Collection<G> groups, Function<G, ErrorLocation> location, ToLongFunction<G> lastSeen) {
        Comparator<G> longestAgo = Comparator.comparingLong(lastSeen);
        Optional<G> reportedByClient = groups.stream().filter(group -> location.apply(group) == ErrorLocation.CLIENT).min(longestAgo);
        return reportedByClient.isPresent() ? reportedByClient : groups.stream().min(longestAgo);
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
        // what was collected while the flush ran and what comes back together can be more than the buffer may hold
        keepWithinLimit();
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

    static String keyOf(ErrorLocation location, ErrorSeverity severity, String version, String pattern) {
        return location + "|" + severity + "|" + (version == null ? "" : version) + "|" + pattern;
    }

    /**
     * Replaces what tells two otherwise identical errors apart by placeholders, and shortens the message.
     */
    static String toPattern(String message) {
        String bounded = bound(message);
        String pattern = EMAIL.matcher(bounded).replaceAll("<email>");
        pattern = UUID.matcher(pattern).replaceAll("<uuid>");
        pattern = NUMBER.matcher(pattern).replaceAll("<n>");
        pattern = WHITESPACE.matcher(pattern).replaceAll(" ").strip();
        return pattern.length() <= MAX_MESSAGE_LENGTH ? pattern : pattern.substring(0, MAX_MESSAGE_LENGTH);
    }

    /**
     * Cuts a long message to what is looked at, without the word that the cut goes through: the end of an email address that was cut off
     * would not be recognized, and the start of it would stay in the pattern.
     */
    private static String bound(String message) {
        if (message == null) {
            return "";
        }
        int limit = MAX_MESSAGE_LENGTH * 4;
        if (message.length() <= limit) {
            return message;
        }
        String cut = message.substring(0, limit);
        if (!Character.isWhitespace(message.charAt(limit))) {
            int lastSpace = lastWhitespace(cut);
            cut = lastSpace < 0 ? "<too long>" : cut.substring(0, lastSpace);
        }
        return cut;
    }

    private static int lastWhitespace(String text) {
        for (int index = text.length() - 1; index >= 0; index--) {
            if (Character.isWhitespace(text.charAt(index))) {
                return index;
            }
        }
        return -1;
    }

    /**
     * The errors of one group that were collected, counted per hour.
     */
    static final class PendingGroup {

        final ErrorLocation location;

        final ErrorSeverity severity;

        /** The release as the reporter states it, {@code null} for the release of this node. */
        final String version;

        final String pattern;

        long firstSeenMillis;

        long lastSeenMillis;

        /** The number of errors per hour, by the hours since the epoch. */
        final TreeMap<Long, Long> perHour = new TreeMap<>();

        PendingGroup(ErrorLocation location, ErrorSeverity severity, String version, String pattern, long now) {
            this.location = location;
            this.severity = severity;
            this.version = version;
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
            PendingGroup copy = new PendingGroup(location, severity, version, pattern, firstSeenMillis);
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

        /**
         * What the cluster stores about this group.
         *
         * @param effectiveVersion the release that the group counts as, see {@link ErrorAnalysisService}
         * @return the state of the group
         */
        ErrorGroupState toState(String effectiveVersion) {
            return new ErrorGroupState(location, severity, effectiveVersion, pattern, firstSeenMillis, lastSeenMillis);
        }
    }
}
