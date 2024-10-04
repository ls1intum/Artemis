package de.tum.cit.aet.artemis.programming.service.localvc;

import java.nio.file.Path;
import java.util.Collection;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;

public class LocalVCFetchPreUploadHookSSH implements PreUploadHook {

    private final LocalVCServletService localVCServletService;

    private final ServerSession serverSession;

    private final Path rootDir;

    public LocalVCFetchPreUploadHookSSH(LocalVCServletService localVCServletService, ServerSession serverSession, Path rootDir) {
        this.localVCServletService = localVCServletService;
        this.serverSession = serverSession;
        this.rootDir = rootDir;
    }

    @Override
    public void onBeginNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int cntOffered) {
        localVCServletService.addVCSAccessLogForCloneAndPulloverSSH(serverSession, rootDir, cntOffered);
    }

    @Override
    public void onEndNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int i, int i1, boolean b) {

    }

    @Override
    public void onSendPack(UploadPack uploadPack, Collection<? extends ObjectId> collection, Collection<? extends ObjectId> collection1) {

    }
}
