package de.tum.cit.aet.artemis.programming.service;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import de.tum.cit.aet.artemis.programming.domain.AbstractVcsAccessLogEntry;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Turns the stored access log into one entry per operation.
 * <p>
 * Over HTTPS, a git operation is several requests that nothing links. The first one, which advertises the references, is stored as a read or a write, because it is all there is
 * of a fetch or push that is up to date. When data follows, a second request stores the clone, pull or push. The advertisement of that operation is then not an access of its
 * own, so it is left out here: every clone, pull or push takes away the newest preceding read or write of the same user, over the same mechanism and from the same address.
 * <p>
 * A failed push takes away an advertisement only if the repository was reached, which is when the entry carries the commit that the repository had. An attempt that was refused
 * before the references were advertised, because of the credentials or the authorization, is stored without a commit and is an access of its own, so it must not hide the
 * advertisement of an earlier push that was up to date.
 * <p>
 * The entries are joined by the times of the accesses, not by the order in which they were stored: the entry of an advertisement is written after its response was sent, so
 * the transfer that the client started next can be stored before it.
 * <p>
 * Entries that are interchangeable cannot be told apart, and they do not need to be: with several operations at the same moment from the same user, address and mechanism, each
 * transfer takes away one advertisement, so the count of what is shown is right even if it is not known which advertisement belonged to which transfer. Stored entries are never
 * changed.
 */
public final class VcsAccessLogOperations {

    /**
     * The longest time between the advertisement and the end of the transfer that follows it. A transfer is stored when it is over, and a large push over a slow connection takes
     * a while.
     */
    static final Duration MAX_TIME_BETWEEN_ADVERTISEMENT_AND_TRANSFER = Duration.ofMinutes(30);

    /**
     * The mechanisms of git requests over HTTPS, the only ones whose advertisement is a request of its own. Over SSH one command is one entry, and a build agent is not a person.
     */
    private static final Set<AuthenticationMechanism> HTTPS_MECHANISMS = Set.of(AuthenticationMechanism.PASSWORD, AuthenticationMechanism.USER_VCS_ACCESS_TOKEN,
            AuthenticationMechanism.PARTICIPATION_VCS_ACCESS_TOKEN, AuthenticationMechanism.REPOSITORY_VCS_ACCESS_TOKEN, AuthenticationMechanism.VCS_ACCESS_TOKEN,
            AuthenticationMechanism.AGENT_ACCESS_TOKEN, AuthenticationMechanism.AUTH_HEADER_MISSING);

    /** Who advertised the references of a repository, how, from where, and for which kind of transfer. Advertisements with the same key are interchangeable. */
    private record AdvertisementKey(Long userId, AuthenticationMechanism mechanism, String ipAddress, RepositoryActionType advertisement) {
    }

    private VcsAccessLogOperations() {
    }

    /**
     * Removes the advertisements that belong to an operation that moved data. The entries are read once: the advertisements that no transfer has taken yet are kept per key, the
     * newest of them on top, so the time that this takes grows with the number of entries and not with the number of pairs among them.
     *
     * @param <T>     the kind of entry, from the access log of a participation or of the tests or an auxiliary repository
     * @param entries the stored entries of one repository, in the order in which they were stored
     * @return the entries that are operations or accesses of their own, in the same order
     */
    public static <T extends AbstractVcsAccessLogEntry> List<T> oneEntryPerOperation(List<T> entries) {
        Map<AdvertisementKey, Deque<T>> untaken = new HashMap<>();
        Set<T> advertisementsOfTransfers = Collections.newSetFromMap(new IdentityHashMap<>());
        // An advertisement can be stored after the transfer that follows it, so the entries are read in the order of their times. Entries of the same time keep their order.
        List<T> byTime = new ArrayList<>(entries);
        byTime.sort(Comparator.comparing(AbstractVcsAccessLogEntry::getTimestamp, Comparator.nullsFirst(Comparator.naturalOrder())));
        for (T entry : byTime) {
            if (entry.getUser() == null || !HTTPS_MECHANISMS.contains(entry.getAuthenticationMechanism())) {
                continue;
            }
            RepositoryActionType type = entry.getRepositoryActionType();
            if (type == RepositoryActionType.READ || type == RepositoryActionType.WRITE) {
                untaken.computeIfAbsent(keyOf(entry, type), key -> new ArrayDeque<>()).push(entry);
                continue;
            }
            RepositoryActionType advertisement = advertisementOf(entry);
            if (advertisement == null) {
                continue;
            }
            // The newest advertisement before the transfer that no transfer has taken yet. If it is too old, so are all before it.
            Deque<T> candidates = untaken.get(keyOf(entry, advertisement));
            if (candidates != null && !candidates.isEmpty() && isCloseEnough(candidates.peek(), entry)) {
                advertisementsOfTransfers.add(candidates.pop());
            }
        }
        List<T> result = new ArrayList<>(entries);
        result.removeIf(advertisementsOfTransfers::contains);
        return result;
    }

    /** Which stored kind advertises a transfer of this kind, null if the entry is not a transfer that was preceded by an advertisement. */
    private static RepositoryActionType advertisementOf(AbstractVcsAccessLogEntry entry) {
        return switch (entry.getRepositoryActionType()) {
            case CLONE, PULL -> RepositoryActionType.READ;
            case PUSH -> RepositoryActionType.WRITE;
            // A failed push that names no commit was refused before the references were advertised, so there is no advertisement that belongs to it
            case PUSH_FAIL -> StringUtils.isEmpty(entry.getCommitHash()) ? null : RepositoryActionType.WRITE;
            default -> null;
        };
    }

    private static AdvertisementKey keyOf(AbstractVcsAccessLogEntry entry, RepositoryActionType advertisement) {
        return new AdvertisementKey(entry.getUser().getId(), entry.getAuthenticationMechanism(), entry.getIpAddress(), advertisement);
    }

    private static boolean isCloseEnough(AbstractVcsAccessLogEntry advertisement, AbstractVcsAccessLogEntry transfer) {
        return Duration.between(advertisement.getTimestamp(), transfer.getTimestamp()).compareTo(MAX_TIME_BETWEEN_ADVERTISEMENT_AND_TRANSFER) <= 0;
    }
}
