package de.tum.cit.aet.artemis.localvc.service;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

/**
 * What the hooks of one git request (over HTTPS) or one git command (over SSH) learn about the operation, collected for the single access log entry that is written when the
 * operation is over.
 * <p>
 * Git over HTTPS is several requests that nothing links, so no step may look for the entry of another one. Each request or command owns one instance of this class. The
 * hooks fill it while the operation runs, and the code that served the request or command turns it into one entry afterwards, with everything known by then.
 * <p>
 * One instance is used by one thread at a time, the one that serves the request or command.
 */
public final class GitOperationAudit {

    private boolean packSent;

    private int objectsOffered;

    private @Nullable String commitHashWhenSent;

    private boolean accessed;

    private @Nullable String commitHashWhenAccessed;

    private @Nullable ZonedDateTime accessedAt;

    private boolean pushReceived;

    private @Nullable String commitHashBeforePush;

    private @Nullable String pushedCommitHash;

    /**
     * Remembers how many objects the client offered in a round of the negotiation of a fetch. The last round is the one that counts, because the client offers everything it
     * has in every round of an HTTPS fetch.
     *
     * @param objectsOffered the number of objects the client has offered
     */
    public void onBeginNegotiateRound(int objectsOffered) {
        this.objectsOffered = objectsOffered;
    }

    /**
     * Remembers that the data of a fetch is being sent, which makes the fetch a clone or a pull. A request that only negotiates or lists references sends no pack, so it is not
     * an operation of its own.
     *
     * @param commitHash the newest commit of the repository, null if it has none
     */
    public void onPackSent(@Nullable String commitHash) {
        this.packSent = true;
        this.commitHashWhenSent = commitHash;
    }

    /**
     * Remembers that the repository was reached, which is what the request that advertises its references is. Over HTTPS this is the first request of every fetch and push and,
     * if nothing follows it, all there is of one that is up to date.
     *
     * @param commitHash the newest commit of the repository, null if it has none
     * @param accessedAt when the request started, which is before its response could reach the client. The entry is written after the response, and the next request of the
     *                       operation can be written before it, so the entry carries this time.
     */
    public void onAccessed(@Nullable String commitHash, ZonedDateTime accessedAt) {
        this.accessed = true;
        this.commitHashWhenAccessed = commitHash;
        this.accessedAt = accessedAt;
    }

    /**
     * Remembers that a push brought commands, that is, that something was pushed rather than just announced. A request that carries no command, such as the check that git
     * sends before a large push, is not a push.
     *
     * @param commitHash the newest commit of the repository before the push changes it, null if it has none
     */
    public void onPushReceived(@Nullable String commitHash) {
        if (!pushReceived) {
            pushReceived = true;
            commitHashBeforePush = commitHash;
        }
    }

    /**
     * Remembers the commit of a push that the repository accepted.
     *
     * @param commitHash the hash of the pushed commit
     */
    public void onPushAccepted(String commitHash) {
        this.pushedCommitHash = commitHash;
    }

    /**
     * @return whether the data of a fetch was sent
     */
    public boolean isPackSent() {
        return packSent;
    }

    /**
     * @return the number of objects the client offered in the last round, 0 for a client that has nothing and therefore clones
     */
    public int getObjectsOffered() {
        return objectsOffered;
    }

    /**
     * @return the newest commit of the repository when the data of a fetch was sent
     */
    public @Nullable String getCommitHashWhenSent() {
        return commitHashWhenSent;
    }

    /**
     * @return whether the repository was reached by a request that advertises its references
     */
    public boolean isAccessed() {
        return accessed;
    }

    /**
     * @return the newest commit of the repository when it was reached
     */
    public @Nullable String getCommitHashWhenAccessed() {
        return commitHashWhenAccessed;
    }

    /**
     * @return when the request that reached the repository started, null if no request did
     */
    public @Nullable ZonedDateTime getAccessedAt() {
        return accessedAt;
    }

    /**
     * @return whether a push brought commands
     */
    public boolean isPushReceived() {
        return pushReceived;
    }

    /**
     * @return the newest commit of the repository before the push, which is what a push that was rejected leaves in the repository
     */
    public @Nullable String getCommitHashBeforePush() {
        return commitHashBeforePush;
    }

    /**
     * @return the pushed commit if the repository accepted the push, null if it did not
     */
    public @Nullable String getPushedCommitHash() {
        return pushedCommitHash;
    }
}
