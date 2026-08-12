package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.exercise.dto.synchronization.ExerciseEditorSyncTarget;

/**
 * Tests which client a new commit alert may be attributed to.
 * <p>
 * Attribution is what stops an instructor from being warned about the commit they just made themselves. It has to be exact:
 * version jobs run asynchronously on several workers and read the repository refs when they execute, so a job queued by one
 * client can snapshot a commit that another client pushed in the meantime. Attributing that alert to the queueing client
 * would make its editor discard a warning about somebody else's commit, which is a worse outcome than the duplicate warning
 * attribution removes.
 */
class ExerciseVersionCommitAttributionTest {

    private static final String OWN_SESSION = "session-of-the-committer";

    private static final String OWN_COMMIT = "1111111111111111111111111111111111111111";

    private static final String OTHER_COMMIT = "2222222222222222222222222222222222222222";

    private static final ExerciseEditorSyncTarget TESTS = ExerciseEditorSyncTarget.TESTS_REPOSITORY;

    private static final ExerciseEditorSyncTarget TEMPLATE = ExerciseEditorSyncTarget.TEMPLATE_REPOSITORY;

    @Test
    void shouldAttributeAnAlertAboutTheClientsOwnCommit() {
        assertThat(ExerciseVersionService.sessionOwningCommit(OWN_SESSION, TESTS, OWN_COMMIT, TESTS, OWN_COMMIT)).isEqualTo(OWN_SESSION);
    }

    @Test
    void shouldNotAttributeAnAlertAboutSomebodyElsesCommit() {
        // the job was queued by our submit but ended up snapshotting a commit pushed by someone else in the meantime
        assertThat(ExerciseVersionService.sessionOwningCommit(OWN_SESSION, TESTS, OWN_COMMIT, TESTS, OTHER_COMMIT)).isNull();
    }

    @Test
    void shouldNotAttributeAnAlertAboutADifferentRepositoryAtTheSameCommit() {
        // a commit id identifies an object, not a place: repositories of one exercise are seeded from each other, so the same
        // id can stand in more than one of them, and matching on the id alone would attribute the wrong repository's alert
        assertThat(ExerciseVersionService.sessionOwningCommit(OWN_SESSION, TESTS, OWN_COMMIT, TEMPLATE, OWN_COMMIT)).isNull();
    }

    @Test
    void shouldNotAttributeWhenTheTriggeringRepositoryIsUnknown() {
        // an auxiliary repository commit, which cannot be matched to one specific auxiliary repository here
        assertThat(ExerciseVersionService.sessionOwningCommit(OWN_SESSION, null, OWN_COMMIT, TESTS, OWN_COMMIT)).isNull();
    }

    @Test
    void shouldNotAttributeWhenTheTriggeringCommitIsUnknown() {
        // a version created by a metadata change, which cannot own a repository commit
        assertThat(ExerciseVersionService.sessionOwningCommit(OWN_SESSION, TESTS, null, TESTS, OWN_COMMIT)).isNull();
    }

    @Test
    void shouldNotAttributeWhenTheSnapshotHasNoCommitForTheTarget() {
        assertThat(ExerciseVersionService.sessionOwningCommit(OWN_SESSION, TESTS, OWN_COMMIT, TESTS, null)).isNull();
    }

    @Test
    void shouldNotAttributeWithoutAClientSession() {
        // a plain git push from a clone, which has no editor session to filter by
        assertThat(ExerciseVersionService.sessionOwningCommit(null, TESTS, OWN_COMMIT, TESTS, OWN_COMMIT)).isNull();
    }
}
