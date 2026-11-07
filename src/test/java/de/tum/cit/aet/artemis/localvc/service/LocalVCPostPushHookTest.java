package de.tum.cit.aet.artemis.localvc.service;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.localvc.service.ssh.SshConstants;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;

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

    @Test
    void anAcceptedUpdateOverHttpsIsHandedToThePushProcessingWithoutCachedState() {
        ObjectId newId = ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");
        ReceiveCommand update = new ReceiveCommand(ObjectId.fromString("89abcdef0123456789abcdef0123456789abcdef"), newId, "refs/heads/main");
        Repository repository = mock(Repository.class);
        when(receivePack.getRepository()).thenReturn(repository);
        User user = new User();

        new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, user, () -> AuthenticationMechanism.USER_VCS_ACCESS_TOKEN).onPostReceive(receivePack,
                List.of(update));

        verify(pushProcessingService).processNewPush(newId.name(), repository, user, Optional.empty(), Optional.empty(), Optional.empty());
    }

    @Test
    void anAcceptedUpdateOverSshIsHandedToThePushProcessingWithTheStateOfTheSession() {
        ObjectId newId = ObjectId.fromString("0123456789abcdef0123456789abcdef01234567");
        ReceiveCommand update = new ReceiveCommand(ObjectId.fromString("89abcdef0123456789abcdef0123456789abcdef"), newId, "refs/heads/main");
        Repository repository = mock(Repository.class);
        when(receivePack.getRepository()).thenReturn(repository);
        User user = new User();
        var participation = new ProgrammingExerciseStudentParticipation();
        var exercise = new ProgrammingExercise();
        var accessLog = new VcsAccessLog();
        ServerSession session = mock(ServerSession.class);
        when(session.getAttribute(SshConstants.PARTICIPATION_KEY)).thenReturn(participation);
        when(session.getAttribute(SshConstants.REPOSITORY_EXERCISE_KEY)).thenReturn(exercise);
        when(session.getAttribute(SshConstants.VCS_ACCESS_LOG_KEY)).thenReturn(accessLog);

        new LocalVCPostPushHook(localVCServletService, pushProcessingService, noticeService, session, user).onPostReceive(receivePack, List.of(update));

        verify(pushProcessingService).processNewPush(newId.name(), repository, user, Optional.of(exercise), Optional.of(participation), Optional.of(accessLog));
    }
}
