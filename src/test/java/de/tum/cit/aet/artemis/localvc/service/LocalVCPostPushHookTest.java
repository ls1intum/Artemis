package de.tum.cit.aet.artemis.localvc.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;

class LocalVCPostPushHookTest {

    private final LocalVCServletService localVCServletService = mock(LocalVCServletService.class);

    private final LocalVCPushProcessingService pushProcessingService = mock(LocalVCPushProcessingService.class);

    private final PasswordAuthenticationNoticeService noticeService = mock(PasswordAuthenticationNoticeService.class);

    private final ReceivePack receivePack = mock(ReceivePack.class);

    @Test
    void aPushWithAPasswordPrintsTheNotice() {
        when(noticeService.getPasswordAuthenticationPushNotice()).thenReturn("please switch");

        // A push that updates nothing returns early, and must still tell the user.
        new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, new User(), () -> AuthenticationMechanism.PASSWORD).onPostReceive(receivePack,
                List.of());

        verify(receivePack).sendMessage("please switch");
    }

    @Test
    void aPushWithATokenPrintsNothing() {
        new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, new User(), () -> AuthenticationMechanism.USER_VCS_ACCESS_TOKEN)
                .onPostReceive(receivePack, List.of());

        verify(receivePack, never()).sendMessage(anyString());
    }

    @Test
    void readsTheMechanismOnlyOnceTheRequestWasAuthorized() {
        when(noticeService.getPasswordAuthenticationPushNotice()).thenReturn("please switch");
        AtomicReference<AuthenticationMechanism> mechanism = new AtomicReference<>();
        // JGit builds the hook before the push filter has recorded the mechanism on the request.
        LocalVCPostPushHook hook = new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, new User(), mechanism::get);
        mechanism.set(AuthenticationMechanism.PASSWORD);

        hook.onPostReceive(receivePack, List.of());

        verify(receivePack).sendMessage("please switch");
    }
}
