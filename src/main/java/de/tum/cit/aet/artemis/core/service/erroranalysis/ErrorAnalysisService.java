package de.tum.cit.aet.artemis.core.service.erroranalysis;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisBuffer.MAX_GROUPS;
import static de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisBuffer.MILLIS_PER_HOUR;
import static de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisBuffer.RETENTION;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSortField;
import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
import de.tum.cit.aet.artemis.core.dto.ErrorAnalysisPageDTO;
import de.tum.cit.aet.artemis.core.dto.ErrorGroupDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.erroranalysis.ErrorAnalysisBuffer.PendingGroup;

/**
 * The error analysis of the cluster: the errors of the servers and the clients that are alike are counted as one group.
 * <p>
 * Every core node collects what it sees in {@link ErrorAnalysisBuffer} and hands it to the distributed data in batches, see
 * {@link #flush()}. The groups and their counts live in two distributed maps, so every node answers with the errors of all
 * nodes, and a query adds what its own node has not handed over yet. Nothing is stored in the database.
 * <p>
 * The counts are per hour, which is as precise as the shortest time span needs. The two maps are bounded: at most
 * {@link ErrorAnalysisBuffer#MAX_GROUPS} groups, and an entry expires {@link ErrorAnalysisBuffer#RETENTION} after the last time
 * it was written. How long the data survives a restart depends on the provider: Hazelcast discards it when the whole cluster
 * stops, Redis keeps it. Only core nodes flush, so the errors of a build agent are not counted.
 * <p>
 * Several nodes write the same entries, so every write is additive or a merge, and it is retried if another node wrote first.
 * <p>
 * Every group belongs to a release, which is part of its key, so the errors of the running release can be told from the ones of
 * the releases before it without storing when a release was deployed. An error of the server belongs to the release of the node
 * that logged it. An error of a client belongs to the release of that client if it is the release that this node is running, and
 * to {@value #OTHER_VERSION} otherwise: the version in a report of a client that is not signed in cannot be trusted, and a client
 * that has been open since the last deployment is not an error of this release. The release of a group does not change when the
 * server restarts, so a restart of the same release keeps counting in the same groups. For a time span that counts back from now
 * the releases are added up again, so a group is one row whatever release it happened in.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ErrorAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ErrorAnalysisService.class);

    /** The entries live a little longer than the longest time span, and every write refreshes them. */
    private static final Duration ENTRY_TIME_TO_LIVE = RETENTION.plusHours(1);

    /** How often a write is repeated because another node wrote the same entry at the same time. */
    private static final int MAX_ATTEMPTS = 20;

    /** The release of a group of errors that a client reported with another release than the one that is running. */
    static final String OTHER_VERSION = "other";

    private final DistributedMap<String, ErrorGroupState> groups;

    /** The counts per hour and group, by {@code <hours since the epoch>:<group key>}. */
    private final DistributedMap<String, Long> counts;

    private final ErrorAnalysisBuffer buffer;

    private final Clock clock;

    /** The release that this node is running. */
    private final String currentVersion;

    @Autowired
    public ErrorAnalysisService(DistributedDataProvider distributedDataProvider, @Value("${artemis.version}") String artemisVersion) {
        this(distributedDataProvider, ErrorAnalysisBuffer.shared(), Clock.systemUTC(), artemisVersion);
    }

    ErrorAnalysisService(DistributedDataProvider distributedDataProvider, ErrorAnalysisBuffer buffer, Clock clock, String artemisVersion) {
        this.currentVersion = artemisVersion.strip();
        this.groups = distributedDataProvider.getExpiringMap("error-analysis-groups", ENTRY_TIME_TO_LIVE);
        this.counts = distributedDataProvider.getExpiringMap("error-analysis-counts", ENTRY_TIME_TO_LIVE);
        this.buffer = buffer;
        this.clock = clock;
    }

    /**
     * Counts an error that a client reported.
     *
     * @param severity how serious the client says the error is
     * @param version  the release of the client as it states it, or {@code null} if it does not
     * @param message  what went wrong, already cleaned of personal data
     */
    public void recordClientError(ErrorSeverity severity, String version, String message) {
        buffer.record(ErrorLocation.CLIENT, severity, version == null ? OTHER_VERSION : version.strip(), message);
    }

    /**
     * Hands what this node collected since the last run to the distributed data.
     * <p>
     * What cannot be written, for example while the cluster is not reachable, goes back to the buffer and is written by the next
     * run. A group that was written only in part goes back with just the hours that are missing, so nothing is counted twice.
     */
    @Scheduled(fixedRateString = "${artemis.error-analysis.flush-interval:10s}", initialDelayString = "${artemis.error-analysis.flush-interval:10s}")
    public void flush() {
        Map<String, PendingGroup> drained = buffer.drain();
        if (drained.isEmpty()) {
            return;
        }
        Map<String, PendingGroup> notWritten = new HashMap<>();
        for (Map.Entry<String, PendingGroup> entry : drained.entrySet()) {
            try {
                write(entry.getValue());
            }
            catch (RuntimeException exception) {
                notWritten.put(entry.getKey(), entry.getValue());
                log.warn("Could not hand the error analysis of a group to the distributed data, it is tried again with the next flush", exception);
            }
        }
        buffer.restore(notWritten);
    }

    /**
     * Hands over what was collected before the node stops, so that a restart does not discard the last seconds.
     */
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    /**
     * One page of the groups of the errors that happened in a time span, of all nodes.
     * <p>
     * The groups are sorted before the page is cut out of them, so that the pages follow the order that was asked for. Groups that
     * are equal in the field that is sorted by are in the order of the most recent first and then of the message, so that a group
     * cannot move to another page while somebody browses.
     *
     * @param span        how far to look back from now, or the release that is running
     * @param location    only the errors of this location, or {@code null} for all of them
     * @param minSeverity only the errors that are at least this serious
     * @param sortField   what to sort the groups by
     * @param ascending   whether the smallest value comes first
     * @param page        the number of the page to return, the first page is 0
     * @param pageSize    how many groups a page has at most
     * @return the groups of the page and the totals over all pages
     */
    public ErrorAnalysisPageDTO getGroups(ErrorAnalysisSpan span, ErrorLocation location, ErrorSeverity minSeverity, ErrorAnalysisSortField sortField, boolean ascending, int page,
            int pageSize) {
        List<ErrorGroupDTO> sorted = collectGroups(span, location, minSeverity);
        sorted.sort(comparatorOf(sortField, ascending));
        long totalErrors = sorted.stream().mapToLong(ErrorGroupDTO::count).sum();
        int from = (int) Math.min((long) page * pageSize, sorted.size());
        int to = (int) Math.min((long) from + pageSize, sorted.size());
        return new ErrorAnalysisPageDTO(new ArrayList<>(sorted.subList(from, to)), sorted.size(), totalErrors);
    }

    private List<ErrorGroupDTO> collectGroups(ErrorAnalysisSpan span, ErrorLocation location, ErrorSeverity minSeverity) {
        boolean boundedByTime = span.isBoundedByTime();
        long sinceMillis = boundedByTime ? clock.millis() - span.getDuration().toMillis() : Long.MIN_VALUE;
        // the hour that contains the start counts completely, so the count can be a little too high but never too low
        long sinceHour = boundedByTime ? sinceMillis / MILLIS_PER_HOUR : Long.MIN_VALUE;

        Map<String, ErrorGroupState> states = new HashMap<>(groups.getMapCopy());
        Map<String, Long> totals = new HashMap<>();
        counts.getMapCopy().forEach((countKey, count) -> {
            int separator = countKey.indexOf(':');
            if (Long.parseLong(countKey.substring(0, separator)) >= sinceHour) {
                totals.merge(countKey.substring(separator + 1), count, Long::sum);
            }
        });
        // what this node saw and has not handed over yet
        buffer.snapshot().forEach((rawKey, pendingGroup) -> {
            String version = effectiveVersion(pendingGroup.version);
            String key = ErrorAnalysisBuffer.keyOf(pendingGroup.location, pendingGroup.severity, version, pendingGroup.pattern);
            states.merge(key, pendingGroup.toState(version), ErrorGroupState::mergedWith);
            pendingGroup.perHour.tailMap(sinceHour, true).values().forEach(count -> totals.merge(key, count, Long::sum));
        });

        // a group is one row, whatever release it happened in, unless only the running release is asked for
        Map<String, ErrorGroupDTO> rows = new HashMap<>();
        states.forEach((key, state) -> {
            long count = totals.getOrDefault(key, 0L);
            boolean inRelease = span != ErrorAnalysisSpan.RELEASE || currentVersion.equals(state.version());
            if (count > 0 && inRelease && (location == null || state.location() == location) && state.severity().isAtLeast(minSeverity) && state.lastSeenMillis() >= sinceMillis) {
                rows.merge(state.location() + "|" + state.severity() + "|" + state.pattern(), state.toDto(count), ErrorAnalysisService::combine);
            }
        });
        return new ArrayList<>(rows.values());
    }

    /**
     * The release that a group counts as.
     */
    private String effectiveVersion(String reportedVersion) {
        return reportedVersion == null || currentVersion.equals(reportedVersion.strip()) ? currentVersion : OTHER_VERSION;
    }

    private static ErrorGroupDTO combine(ErrorGroupDTO first, ErrorGroupDTO second) {
        return new ErrorGroupDTO(first.location(), first.severity(), first.message(), first.count() + second.count(),
                first.firstSeen().isBefore(second.firstSeen()) ? first.firstSeen() : second.firstSeen(),
                first.lastSeen().isAfter(second.lastSeen()) ? first.lastSeen() : second.lastSeen());
    }

    private static Comparator<ErrorGroupDTO> comparatorOf(ErrorAnalysisSortField sortField, boolean ascending) {
        Comparator<ErrorGroupDTO> primary = switch (sortField) {
            case COUNT -> Comparator.comparingLong(ErrorGroupDTO::count);
            case SEVERITY -> Comparator.comparing(ErrorGroupDTO::severity);
            case LOCATION -> Comparator.comparing(ErrorGroupDTO::location);
            case MESSAGE -> Comparator.comparing(ErrorGroupDTO::message);
            case FIRST_SEEN -> Comparator.comparing(ErrorGroupDTO::firstSeen);
            case LAST_SEEN -> Comparator.comparing(ErrorGroupDTO::lastSeen);
        };
        // the rest of the order never depends on the direction, and it is total: the location, severity and message identify a group
        return (ascending ? primary : primary.reversed()).thenComparing(ErrorGroupDTO::lastSeen, Comparator.reverseOrder()).thenComparing(ErrorGroupDTO::message)
                .thenComparing(ErrorGroupDTO::location).thenComparing(ErrorGroupDTO::severity);
    }

    private void write(PendingGroup group) {
        String version = effectiveVersion(group.version);
        String key = ErrorAnalysisBuffer.keyOf(group.location, group.severity, version, group.pattern);
        registerGroup(key, group.toState(version));
        for (Iterator<Map.Entry<Long, Long>> hours = group.perHour.entrySet().iterator(); hours.hasNext();) {
            Map.Entry<Long, Long> hour = hours.next();
            add(hour.getKey() + ":" + key, hour.getValue());
            // written, so a retry of this group does not add it again
            hours.remove();
        }
    }

    /**
     * Creates the group or widens what the cluster knows about when it happened first and last.
     */
    private void registerGroup(String key, ErrorGroupState incoming) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            ErrorGroupState current = groups.get(key);
            if (current == null) {
                makeRoomForAnotherGroup();
                if (groups.putIfAbsent(key, incoming, ENTRY_TIME_TO_LIVE) == null) {
                    return;
                }
                continue;
            }
            ErrorGroupState merged = current.mergedWith(incoming);
            if (merged.equals(current) || groups.replace(key, current, merged)) {
                groups.refreshTimeToLive(key, ENTRY_TIME_TO_LIVE);
                return;
            }
        }
        throw new IllegalStateException("The group is written by other nodes all the time");
    }

    /**
     * Forgets the group that was seen the longest time ago if the cluster keeps as many groups as it may.
     */
    private void makeRoomForAnotherGroup() {
        if (groups.size() < MAX_GROUPS) {
            return;
        }
        groups.getMapCopy().entrySet().stream().min(Comparator.comparingLong(entry -> entry.getValue().lastSeenMillis()))
                .ifPresent(oldest -> groups.remove(oldest.getKey(), oldest.getValue()));
    }

    private void add(String countKey, long delta) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long current = counts.putIfAbsent(countKey, delta, ENTRY_TIME_TO_LIVE);
            if (current == null) {
                return;
            }
            if (counts.replace(countKey, current, current + delta)) {
                counts.refreshTimeToLive(countKey, ENTRY_TIME_TO_LIVE);
                return;
            }
        }
        throw new IllegalStateException("The count is written by other nodes all the time");
    }
}
