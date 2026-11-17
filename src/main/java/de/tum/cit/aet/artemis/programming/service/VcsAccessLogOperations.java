package de.tum.cit.aet.artemis.programming.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

/**
 * Turns the stored access log into one entry per operation.
 * <p>
 * Over HTTPS, a git operation is several requests that nothing links. The first one, which advertises the references, is stored as a read or a write, because it is all there is
 * of a fetch or push that is up to date. When data follows, a second request stores the clone, pull or push. The advertisement of that operation is then not an access of its
 * own, so it is left out here: every clone, pull or push takes away the newest preceding read or write of the same user, over the same mechanism and from the same address.
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
            AuthenticationMechanism.AUTH_HEADER_MISSING);

    private VcsAccessLogOperations() {
    }

    /**
     * Removes the advertisements that belong to an operation that moved data.
     *
     * @param entries the stored entries of one repository, oldest first
     * @return the entries that are operations or accesses of their own, in the same order
     */
    public static List<VcsAccessLog> oneEntryPerOperation(List<VcsAccessLog> entries) {
        Set<VcsAccessLog> advertisementsOfTransfers = new HashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            VcsAccessLog transfer = entries.get(index);
            RepositoryActionType advertisement = advertisementOf(transfer.getRepositoryActionType());
            if (advertisement == null || transfer.getUser() == null || !HTTPS_MECHANISMS.contains(transfer.getAuthenticationMechanism())) {
                continue;
            }
            // The newest advertisement before the transfer that no transfer has taken yet
            for (int candidateIndex = index - 1; candidateIndex >= 0; candidateIndex--) {
                VcsAccessLog candidate = entries.get(candidateIndex);
                if (candidate.getRepositoryActionType() != advertisement || advertisementsOfTransfers.contains(candidate) || !isOfSameAccess(candidate, transfer)) {
                    continue;
                }
                if (Duration.between(candidate.getTimestamp(), transfer.getTimestamp()).compareTo(MAX_TIME_BETWEEN_ADVERTISEMENT_AND_TRANSFER) <= 0) {
                    advertisementsOfTransfers.add(candidate);
                }
                break;
            }
        }
        List<VcsAccessLog> result = new ArrayList<>(entries);
        result.removeIf(advertisementsOfTransfers::contains);
        return result;
    }

    /** Which stored kind advertises a transfer of this kind, null if the kind is not a transfer. */
    private static RepositoryActionType advertisementOf(RepositoryActionType transfer) {
        return switch (transfer) {
            case CLONE, PULL -> RepositoryActionType.READ;
            case PUSH, PUSH_FAIL -> RepositoryActionType.WRITE;
            default -> null;
        };
    }

    private static boolean isOfSameAccess(VcsAccessLog advertisement, VcsAccessLog transfer) {
        return advertisement.getUser() != null && Objects.equals(advertisement.getUser().getId(), transfer.getUser().getId())
                && advertisement.getAuthenticationMechanism() == transfer.getAuthenticationMechanism() && Objects.equals(advertisement.getIpAddress(), transfer.getIpAddress());
    }
}
