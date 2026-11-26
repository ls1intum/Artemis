package de.tum.cit.aet.artemis.programming.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.domain.VcsStaffRepositoryAccessLog;
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
        return add(user, action, mechanism, ip, secondsAfterStart, "hash");
    }

    private VcsAccessLog add(User user, RepositoryActionType action, AuthenticationMechanism mechanism, String ip, long secondsAfterStart, String commitHash) {
        var entry = new VcsAccessLog(user, null, "name", "mail", action, mechanism, commitHash, ip);
        ReflectionTestUtils.setField(entry, "timestamp", START.plusSeconds(secondsAfterStart));
        log.add(entry);
        return entry;
    }

    private VcsAccessLog add(User user, RepositoryActionType action, long secondsAfterStart) {
        return add(user, action, AuthenticationMechanism.PASSWORD, "10.0.0.1", secondsAfterStart);
    }

    private static VcsStaffRepositoryAccessLog staffEntry(User user, RepositoryActionType action, long secondsAfterStart) {
        return new VcsStaffRepositoryAccessLog(new ProgrammingExercise(), RepositoryType.TESTS, null, null, user, "name", "mail", action, AuthenticationMechanism.PASSWORD, "hash",
                "10.0.0.1", START.plusSeconds(secondsAfterStart));
    }

    @Test
    void theEntriesOfTheTestsAndAuxiliaryRepositoriesAreJoinedToOperationsLikeThoseOfAParticipation() {
        var advertisement = staffEntry(alice, RepositoryActionType.READ, 0);
        var clone = staffEntry(alice, RepositoryActionType.CLONE, 1);
        var otherUsersAccess = staffEntry(bob, RepositoryActionType.READ, 2);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(List.of(advertisement, clone, otherUsersAccess))).containsExactly(clone, otherUsersAccess);
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

    /**
     * The credentials of a push that does not hold are checked before the references are advertised, so that attempt follows no advertisement. It must not take away the one of an
     * earlier push that was up to date.
     */
    @Test
    void aPushThatWasRefusedBeforeTheReferencesWereAdvertisedLeavesTheEarlierAdvertisementAlone() {
        var upToDatePush = add(alice, RepositoryActionType.WRITE, 0);
        var refusedPush = add(alice, RepositoryActionType.PUSH_FAIL, AuthenticationMechanism.PASSWORD, "10.0.0.1", 60, "");

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).containsExactly(upToDatePush, refusedPush);
    }

    @Test
    void aPushThatTheRepositoryRefusedTakesAwayItsAdvertisement() {
        add(alice, RepositoryActionType.WRITE, 0);
        var rejectedPush = add(alice, RepositoryActionType.PUSH_FAIL, AuthenticationMechanism.PASSWORD, "10.0.0.1", 1, "abc123");

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).containsExactly(rejectedPush);
    }

    /**
     * A failed push without a commit is not paired, but it does not stop the pushes that follow from being paired with their advertisements either.
     */
    @Test
    void aRefusedPushDoesNotChangeHowLaterPushesArePaired() {
        var upToDatePush = add(alice, RepositoryActionType.WRITE, 0);
        var refusedPush = add(alice, RepositoryActionType.PUSH_FAIL, AuthenticationMechanism.PASSWORD, "10.0.0.1", 1, "");
        add(alice, RepositoryActionType.WRITE, 2);
        var push = add(alice, RepositoryActionType.PUSH, 3);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).containsExactly(upToDatePush, refusedPush, push);
    }

    /**
     * Entries from before the advertisements were stored have none to pair with. Each of them is looked at once, and not compared with everything before it, which would take a
     * number of steps that grows with the square of the retained history.
     */
    @Test
    void aHistoryWithoutAdvertisementsIsReadOnceAndLeftAsItIs() {
        var counted = new CountingList();
        for (int i = 0; i < 2_000; i++) {
            var action = i % 3 == 0 ? RepositoryActionType.CLONE : i % 3 == 1 ? RepositoryActionType.PULL : RepositoryActionType.PUSH;
            var entry = new VcsAccessLog(alice, null, "name", "mail", action, AuthenticationMechanism.PASSWORD, "hash", "10.0.0.1");
            ReflectionTestUtils.setField(entry, "timestamp", START.plusSeconds(i));
            counted.add(entry);
        }
        counted.reads = 0;

        var result = VcsAccessLogOperations.oneEntryPerOperation(counted);

        assertThat(result).containsExactlyElementsOf(counted);
        assertThat(counted.reads).isLessThanOrEqualTo(2 * counted.size());
    }

    /** Counts how often an element is read by its position, which is what a scan back through the history does for every entry. */
    private static final class CountingList extends ArrayList<VcsAccessLog> {

        private int reads;

        @Override
        public VcsAccessLog get(int index) {
            reads++;
            return super.get(index);
        }
    }

    /**
     * The entry of an advertisement is written after its response was sent, so the transfer that the client started next can be stored before it. The two are joined by their
     * times, and not by the order in which they were stored.
     */
    @Test
    void aTransferThatWasStoredBeforeItsAdvertisementIsStillJoinedToIt() {
        var clone = add(alice, RepositoryActionType.CLONE, 1);
        var advertisement = add(alice, RepositoryActionType.READ, 0);

        var shown = VcsAccessLogOperations.oneEntryPerOperation(log);

        assertThat(shown).containsExactly(clone).doesNotContain(advertisement);
    }

    @Test
    void anAdvertisementStoredLateDoesNotTakeAwayAPushThatCameBeforeIt() {
        var earlierPush = add(alice, RepositoryActionType.PUSH, 0);
        var laterAdvertisement = add(alice, RepositoryActionType.WRITE, 60);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).containsExactly(earlierPush, laterAdvertisement);
    }

    @Test
    void entriesThatWereStoredOutOfOrderAreShownInTheOrderInWhichTheyWereStored() {
        var transfer = add(alice, RepositoryActionType.PULL, 5);
        var access = add(bob, RepositoryActionType.READ, 1);

        assertThat(VcsAccessLogOperations.oneEntryPerOperation(log)).containsExactly(transfer, access);
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
