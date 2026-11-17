package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

class VcsAccessLogOperationsTest {

    private static final ZonedDateTime START = ZonedDateTime.parse("2026-11-17T10:00:00Z");

    private final User alice = user(1L);

    private final User bob = user(2L);

    private final List<VcsAccessLog> log = new ArrayList<>();

    private static User user(long id) {
        var user = new User();
        user.setId(id);
        return user;
    }

    private VcsAccessLog add(User user, RepositoryActionType action, AuthenticationMechanism mechanism, String ip, long secondsAfterStart) {
        var entry = new VcsAccessLog(user, null, "name", "mail", action, mechanism, "hash", ip);
        ReflectionTestUtils.setField(entry, "timestamp", START.plusSeconds(secondsAfterStart));
        log.add(entry);
        return entry;
    }

    private VcsAccessLog add(User user, RepositoryActionType action, long secondsAfterStart) {
        return add(user, action, AuthenticationMechanism.PASSWORD, "10.0.0.1", secondsAfterStart);
    }

    @Test
    void anAdvertisementIsLeftOutWhenItsTransferFollows() {
        var advertisement = add(alice, RepositoryActionType.READ, 0);
        var clone = add(alice, RepositoryActionType.CLONE, 1);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).containsExactly(clone).doesNotContain(advertisement);
    }

    @Test
    void anAdvertisementWithoutATransferIsAnAccessOfItsOwn() {
        add(alice, RepositoryActionType.READ, 0);
        add(alice, RepositoryActionType.WRITE, 5);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).hasSize(2);
    }

    @Test
    void aReadIsJoinedToAFetchAndAWriteToAPushOrAFailedPush() {
        add(alice, RepositoryActionType.READ, 0);
        add(alice, RepositoryActionType.PULL, 1);
        add(alice, RepositoryActionType.WRITE, 2);
        add(alice, RepositoryActionType.PUSH, 3);
        add(alice, RepositoryActionType.WRITE, 4);
        add(alice, RepositoryActionType.PUSH_FAIL, 5);
        // a push does not take the advertisement of a fetch
        var read = add(alice, RepositoryActionType.READ, 6);
        add(alice, RepositoryActionType.PUSH, 7);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.PULL,
                RepositoryActionType.PUSH, RepositoryActionType.PUSH_FAIL, RepositoryActionType.READ, RepositoryActionType.PUSH);
        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).contains(read);
    }

    /**
     * Two clones at the same moment from the same user are four requests, in any order, and one up-to-date fetch is hidden among them. Each transfer takes one advertisement, so
     * what remains is the fetch that is up to date, whichever advertisement it was.
     */
    @Test
    void everyTransferTakesAwayOneAdvertisementWhateverTheOrder() {
        add(alice, RepositoryActionType.READ, 0);
        add(alice, RepositoryActionType.READ, 1);
        add(alice, RepositoryActionType.READ, 2);
        add(alice, RepositoryActionType.CLONE, 3);
        add(alice, RepositoryActionType.CLONE, 4);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.READ,
                RepositoryActionType.CLONE, RepositoryActionType.CLONE);
    }

    @Test
    void aTransferOnlyTakesTheAdvertisementOfTheSameAccess() {
        add(bob, RepositoryActionType.READ, 0);
        add(alice, RepositoryActionType.READ, AuthenticationMechanism.USER_VCS_ACCESS_TOKEN, "10.0.0.1", 1);
        add(alice, RepositoryActionType.READ, AuthenticationMechanism.PASSWORD, "10.0.0.9", 2);
        add(alice, RepositoryActionType.CLONE, 3);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).hasSize(4);
    }

    @Test
    void anAdvertisementThatIsTooOldBelongsToNoTransfer() {
        add(alice, RepositoryActionType.READ, 0);
        add(alice, RepositoryActionType.CLONE, VcsAccessLogOperations.MAX_TIME_BETWEEN_ADVERTISEMENT_AND_TRANSFER.toSeconds() + 1);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).hasSize(2);
    }

    /**
     * Over SSH one command is one entry, so a read of a command that moved nothing is an access even if a pull of another command follows it. A build agent has no user.
     */
    @Test
    void entriesOverSshAndOfBuildAgentsAreNeverJoined() {
        add(alice, RepositoryActionType.READ, AuthenticationMechanism.SSH, "10.0.0.1", 0);
        add(alice, RepositoryActionType.PULL, AuthenticationMechanism.SSH, "10.0.0.1", 1);
        add(null, RepositoryActionType.PULL, AuthenticationMechanism.BUILD_JOB_TOKEN, "10.0.0.1", 2);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).hasSize(3);
    }

    @Test
    void theStoredEntriesAreNotChanged() {
        add(alice, RepositoryActionType.READ, 0);
        add(alice, RepositoryActionType.CLONE, 1);

        VcsAccessLogOperations.oneEntryPerOperation(log);

        assertThat(log).hasSize(2);
        assertThat(log).extracting(VcsAccessLog::getRepositoryActionType).containsExactly(RepositoryActionType.READ, RepositoryActionType.CLONE);
    }
}
