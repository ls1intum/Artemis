package de.tum.cit.aet.artemis.localvc.service;

import java.util.Collection;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;
import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

public class LocalVCFetchPreUploadHookSSH implements PreUploadHook {

    private final LocalVCAccessLogRecorderService accessLogRecorder;

    private final @Nullable VcsAccessLog accessLog;

    /**
     * Captures the entry of this authorized command before another command can replace the session attributes.
     *
     * @param accessLogRecorder records the negotiated fetch action
     * @param serverSession     the session holding this command's authorization state
     */
    public LocalVCFetchPreUploadHookSSH(LocalVCAccessLogRecorderService accessLogRecorder, ServerSession serverSession) {
        this.accessLogRecorder = accessLogRecorder;
        this.accessLog = serverSession.getAttribute(SshConstants.USER_KEY) == null ? null : serverSession.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY);
    }

    @Override
    public void onBeginNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int clientOffered) {
        accessLogRecorder.markFetchAsClone(accessLog, clientOffered);
    }

    @Override
    public void onEndNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int i, int i1, boolean b) {
    }

    @Override
    public void onSendPack(UploadPack uploadPack, Collection<? extends ObjectId> collection, Collection<? extends ObjectId> collection1) {
    }
}
