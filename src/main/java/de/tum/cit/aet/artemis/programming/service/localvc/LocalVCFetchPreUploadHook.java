package de.tum.cit.aet.artemis.programming.service.localvc;

import java.util.Collection;

import jakarta.servlet.http.HttpServletRequest;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PreUploadHook;
import org.eclipse.jgit.transport.UploadPack;
import org.springframework.http.HttpHeaders;

public record LocalVCFetchPreUploadHook(LocalVCServletService localVCServletService, HttpServletRequest request) implements PreUploadHook {

    @Override
    public void onBeginNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int clientOffered) {
        String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        localVCServletService.updateAndStoreVCSAccessLogForCloneAndPullHTTPS(request, authorizationHeader, clientOffered);
    }

    @Override
    public void onEndNegotiateRound(UploadPack uploadPack, Collection<? extends ObjectId> collection, int i, int i1, boolean b) {
    }

    @Override
    public void onSendPack(UploadPack uploadPack, Collection<? extends ObjectId> collection, Collection<? extends ObjectId> collection1) {
    }
}
