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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.ErrorAnalysisSpan;
import de.tum.cit.aet.artemis.core.domain.ErrorLocation;
import de.tum.cit.aet.artemis.core.domain.ErrorSeverity;
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

    private final DistributedMap<String, ErrorGroupState> groups;

    /** The counts per hour and group, by {@code <hours since the epoch>:<group key>}. */
    private final DistributedMap<String, Long> counts;

    private final ErrorAnalysisBuffer buffer;

    private final Clock clock;

    @Autowired
    public ErrorAnalysisService(DistributedDataProvider distributedDataProvider) {
        this(distributedDataProvider, ErrorAnalysisBuffer.shared(), Clock.systemUTC());
    }

    ErrorAnalysisService(DistributedDataProvider distributedDataProvider, ErrorAnalysisBuffer buffer, Clock clock) {
        this.groups = distributedDataProvider.getExpiringMap("error-analysis-groups", ENTRY_TIME_TO_LIVE);
        this.counts = distributedDataProvider.getExpiringMap("error-analysis-counts", ENTRY_TIME_TO_LIVE);
        this.buffer = buffer;
        this.clock = clock;
    }

    /**
     * Counts an error that a client reported.
     *
     * @param severity how serious the client says the error is
     * @param message  what went wrong, already cleaned of personal data
     */
    public void recordClientError(ErrorSeverity severity, String message) {
        buffer.record(ErrorLocation.CLIENT, severity, message);
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
                write(entry.getKey(), entry.getValue());
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
     * The groups of the errors that happened in a time span, of all nodes.
     *
     * @param span        how far to look back from now
     * @param location    only the errors of this location, or {@code null} for all of them
     * @param minSeverity only the errors that are at least this serious
     * @return the groups, the most frequent first
     */
    public List<ErrorGroupDTO> getGroups(ErrorAnalysisSpan span, ErrorLocation location, ErrorSeverity minSeverity) {
        long sinceMillis = clock.millis() - span.getDuration().toMillis();
        // the hour that contains the start counts completely, so the count can be a little too high but never too low
        long sinceHour = sinceMillis / MILLIS_PER_HOUR;

        Map<String, ErrorGroupState> states = new HashMap<>(groups.getMapCopy());
        Map<String, Long> totals = new HashMap<>();
        counts.getMapCopy().forEach((countKey, count) -> {
            int separator = countKey.indexOf(':');
            if (Long.parseLong(countKey.substring(0, separator)) >= sinceHour) {
                totals.merge(countKey.substring(separator + 1), count, Long::sum);
            }
        });
        // what this node saw and has not handed over yet
        buffer.snapshot().forEach((key, pendingGroup) -> {
            states.merge(key, pendingGroup.toState(), ErrorGroupState::mergedWith);
            pendingGroup.perHour.tailMap(sinceHour, true).values().forEach(count -> totals.merge(key, count, Long::sum));
        });

        List<ErrorGroupDTO> result = new ArrayList<>();
        states.forEach((key, state) -> {
            long count = totals.getOrDefault(key, 0L);
            if (count > 0 && (location == null || state.location() == location) && state.severity().isAtLeast(minSeverity) && state.lastSeenMillis() >= sinceMillis) {
                result.add(state.toDto(count));
            }
        });
        result.sort(Comparator.comparingLong(ErrorGroupDTO::count).reversed().thenComparing(ErrorGroupDTO::lastSeen, Comparator.reverseOrder()));
        return result;
    }

    private void write(String key, PendingGroup group) {
        registerGroup(key, group.toState());
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
