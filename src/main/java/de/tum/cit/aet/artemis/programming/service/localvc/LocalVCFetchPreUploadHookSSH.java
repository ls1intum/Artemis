package de.tum.cit.aet.artemis.programming.service.localvc;

import java.util.Collection;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;

public class LocalVCFetchPreUploadHookSSH implements PreUploadHook {

    private final LocalVCServletService localVCServletService;

    private final ServerSession serverSession;

    public LocalVCFetchPreUploadHookSSH(LocalVCServletService localVCServletService, ServerSession serverSession) {
        this.localVCServletService = localVCServletService;
        this.serverSession = serverSession;
    }

    @Override
    public void onBeginNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int clientOffered) {
        localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullSSH(serverSession, clientOffered);
    }

    @Override
    public void onEndNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int i, int i1, boolean b) {
    }

    @Override
    public void onSendPack(UploadPack uploadPack, Collection<? extends ObjectId> collection, Collection<? extends ObjectId> collection1) {
    }
}
