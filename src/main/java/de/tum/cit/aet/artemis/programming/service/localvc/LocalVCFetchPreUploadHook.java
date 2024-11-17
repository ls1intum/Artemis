package de.tum.cit.aet.artemis.programming.service.localvc;

import java.util.Collection;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;

import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

public class LocalVCFetchPreUploadHook implements PreUploadHook {

    private final LocalVCServletService localVCServletService;

    private final HttpServletRequest request;

    public LocalVCFetchPreUploadHook(LocalVCServletService localVCServletService, HttpServletRequest request) {
        this.localVCServletService = localVCServletService;
        this.request = request;
    }

    @Override
    public void onBeginNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int clientOffered) {
        var vcsAccessLog = request.getAttribute(HttpsConstants.VCS_ACCESS_LOG_KEY);
        String authorizationHeader = request.getHeader(LocalVCServletService.AUTHORIZATION_HEADER);
        if (vcsAccessLog instanceof VcsAccessLog log) {
            localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(log, authorizationHeader, clientOffered);
        }
    }

    @Override
    public void onEndNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int i, int i1, boolean b) {
    }

    @Override
    public void onSendPack(UploadPack uploadPack, Collection<? extends ObjectId> collection, Collection<? extends ObjectId> collection1) {
    }
}
