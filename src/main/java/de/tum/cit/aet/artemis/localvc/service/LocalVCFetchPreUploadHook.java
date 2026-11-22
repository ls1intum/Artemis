package de.tum.cit.aet.artemis.localvc.service;

import java.io.IOException;
import java.util.Collection;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells the audit of a fetch what JGit learns while it serves the fetch: how many objects the client offers, and that the data is being sent. A request or command that sends
 * no data is not an operation, so only {@link #onSendPack} makes a fetch one. The same hook serves the requests over HTTPS and the commands over SSH.
 *
 * @param audit what is collected for the access log entry of the fetch
 */
public record LocalVCFetchPreUploadHook(GitOperationAudit audit) implements PreUploadHook {

    private static final Logger log = LoggerFactory.getLogger(LocalVCFetchPreUploadHook.class);

    @Override
    public void onBeginNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> wants, int clientOffered) {
        audit.onBeginNegotiateRound(clientOffered);
    }

    @Override
    public void onEndNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> wants, int havesTotal, int haveCommon, boolean sentReady) {
        // Nothing to learn: only the data that is sent counts.
    }

    @Override
    public void onSendPack(UploadPack uploadPack, Collection<? extends ObjectId> wants, Collection<? extends ObjectId> haves) {
        audit.onPackSent(headCommitOf(uploadPack.getRepository()));
    }

    /**
     * Reads the newest commit of a repository.
     *
     * @param repository the repository
     * @return the hash of the commit that HEAD points to, null if there is none or it cannot be read
     */
    public static @Nullable String headCommitOf(Repository repository) {
        try {
            ObjectId head = repository.resolve(Constants.HEAD);
            return head == null ? null : head.name();
        }
        catch (IOException e) {
            log.warn("Could not read the newest commit of {} for the access log", repository.getDirectory(), e);
            return null;
        }
    }
}
