package de.tum.cit.aet.artemis.programming.service.localvc;

import java.util.Collection;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;

public record LocalVCFetchPreUploadHookSSH(LocalVCServletService localVCServletService, ServerSession serverSession) implements PreUploadHook {

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
