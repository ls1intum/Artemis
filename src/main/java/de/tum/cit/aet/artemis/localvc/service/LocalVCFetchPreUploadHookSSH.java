package de.tum.cit.aet.artemis.localvc.service;

import java.util.Collection;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

/**
 * Marks the access log entry of an SSH fetch as a clone when the client offers nothing.
 *
 * @param accessLogRecorder the recorder that stores the change
 * @param serverSession     the session of the connection
 * @param accessLog         the entry that was written when this command started, null if none could be written
 */
public record LocalVCFetchPreUploadHookSSH(LocalVCAccessLogRecorderService accessLogRecorder, ServerSession serverSession, @Nullable VcsAccessLog accessLog)
        implements PreUploadHook {

    @Override
    public void onBeginNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int clientOffered) {
        accessLogRecorder.markFetchAsCloneSSH(serverSession, accessLog, clientOffered);
    }

    @Override
    public void onEndNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int i, int i1, boolean b) {
    }

    @Override
    public void onSendPack(UploadPack uploadPack, Collection<? extends ObjectId> collection, Collection<? extends ObjectId> collection1) {
    }
}
